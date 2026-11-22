package com.railsafe.live.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railsafe.config.LiveProperties;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.RouteAlternative;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.live.dto.AlternativeRouteRequest;
import com.railsafe.live.dto.AlternativeRouteResponse;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.RouteAlternativeRepository;
import com.railsafe.service.DemoDataClassifier;
import com.railsafe.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Route Calculation Service.
 *
 * Every distance, duration and geometry comes from OSRM. RailSafe verifies the
 * returned polyline against the closed crossing and rejects any route that comes
 * within the configured avoid radius. No route is invented when OSRM fails.
 */
@Service
public class RouteCalculationService {

    private static final Logger log = LoggerFactory.getLogger(RouteCalculationService.class);

    private final RailwayGateRepository gateRepository;
    private final RouteAlternativeRepository routeAlternativeRepository;
    private final LiveProperties properties;
    private final RestClient routingRestClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public RouteCalculationService(RailwayGateRepository gateRepository,
                                   RouteAlternativeRepository routeAlternativeRepository,
                                   LiveProperties properties,
                                   @Qualifier("routingRestClient") RestClient routingRestClient,
                                   ObjectMapper objectMapper) {
        this.gateRepository = gateRepository;
        this.routeAlternativeRepository = routeAlternativeRepository;
        this.properties = properties;
        this.routingRestClient = routingRestClient;
        this.objectMapper = objectMapper;
    }

    public AlternativeRouteResponse calculate(AlternativeRouteRequest request) {
        RailwayGate gate = gateRepository.findById(request.gateId())
                .filter(found -> !DemoDataClassifier.isDemoGate(found))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Railway gate with ID " + request.gateId() + " was not found."));

        String cacheKey = gate.getId() + ":" + round(request.startLat(), 4) + ":"
                + round(request.startLng(), 4) + ":" + round(request.destLat(), 4) + ":"
                + round(request.destLng(), 4);
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.response();
        }

        AlternativeRouteResponse response = calculateNow(gate, request);
        persist(gate, request, response);
        cache.put(cacheKey, new CacheEntry(Instant.now().plusSeconds(
                Math.max(10, properties.getRouteCacheSeconds())), response));
        return response;
    }

    /**
     * Store what was offered for audit. Persistence must never break a route request,
     * so failures are logged and ignored.
     */
    private void persist(RailwayGate gate,
                         AlternativeRouteRequest request,
                         AlternativeRouteResponse response) {
        if (!response.available() || response.alternatives() == null
                || response.alternatives().isEmpty()) {
            return;
        }
        try {
            Instant now = Instant.now();
            List<RouteAlternative> rows = new ArrayList<>();
            for (AlternativeRouteResponse.RouteOption option : response.alternatives()) {
                RouteAlternative row = new RouteAlternative();
                row.setGate(gate);
                row.setRouteKey(option.id());
                row.setRouteLabel(option.label());
                row.setStartLat(request.startLat());
                row.setStartLng(request.startLng());
                row.setDestLat(request.destLat());
                row.setDestLng(request.destLng());
                row.setDistanceKm(option.distanceKm());
                row.setDurationMinutes(option.durationMinutes());
                row.setExtraDistanceKm(option.extraDistanceKm());
                row.setRecommended(option.recommended());
                row.setDistanceFromGateMeters(option.distanceFromGateMeters());
                row.setNavigationUrl(truncate(option.navigationUrl(), 1200));
                row.setGeometryJson(objectMapper.writeValueAsString(option.geometry()));
                row.setGeneratedAt(now);
                rows.add(row);
            }
            routeAlternativeRepository.saveAll(rows);
            routeAlternativeRepository.deleteOlderThan(now.minusSeconds(7L * 24 * 3600));
        } catch (Exception ex) {
            log.debug("Could not persist alternative route history: {}", ex.getMessage());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private AlternativeRouteResponse calculateNow(RailwayGate gate, AlternativeRouteRequest request) {
        List<OsrmRoute> primary;
        try {
            primary = requestRoutes(request.startLat(), request.startLng(),
                    request.destLat(), request.destLng(), null, true);
        } catch (Exception ex) {
            log.warn("OSRM route calculation failed: {}", ex.getMessage());
            return unavailable(gate, "Alternative route calculation is unavailable right now. "
                    + "Use an official map application and avoid the closed crossing. No route was invented.");
        }

        if (primary.isEmpty()) {
            return unavailable(gate, "OSRM returned no driving route between these points.");
        }

        OsrmRoute original = primary.get(0);
        double originalDistanceFromGate = GeoUtils.distancePointToPolylineMeters(
                gate.getLatitude(), gate.getLongitude(), original.geometry());
        double avoidRadius = Math.max(50, properties.getRouteAvoidRadiusMeters());

        AlternativeRouteResponse.OriginalRoute originalDto = new AlternativeRouteResponse.OriginalRoute(
                km(original.distanceMetres()),
                minutes(original.durationSeconds()),
                originalDistanceFromGate > avoidRadius,
                round(originalDistanceFromGate, 1),
                points(original.geometry())
        );

        if (originalDistanceFromGate > avoidRadius) {
            return new AlternativeRouteResponse(
                    true,
                    "The direct route does not pass within " + Math.round(avoidRadius)
                            + " m of this crossing, so no detour is needed.",
                    "OSRM / OpenStreetMap",
                    gate.getId(),
                    gate.getName(),
                    Instant.now(),
                    originalDto,
                    List.of(),
                    null,
                    true,
                    avoidRadius
            );
        }

        List<OsrmRoute> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 1; index < primary.size(); index++) {
            addCandidate(candidates, seen, primary.get(index), gate, avoidRadius);
        }

        if (candidates.size() < Math.max(2, properties.getRouteMaxAlternatives())) {
            // Public OSRM has no polygon-avoid parameter. Route via points around the
            // crossing and keep only those geometries that genuinely avoid it.
            double offset = Math.max(300, properties.getRouteDetourOffsetMeters());
            double[] bearings = {0, 90, 180, 270};
            for (double bearing : bearings) {
                if (candidates.size() >= Math.max(2, properties.getRouteMaxAlternatives())) {
                    break;
                }
                GeoUtils.GeoPoint via = GeoUtils.destinationPoint(
                        gate.getLatitude(), gate.getLongitude(), bearing, offset);
                try {
                    List<OsrmRoute> detour = requestRoutes(request.startLat(), request.startLng(),
                            request.destLat(), request.destLng(), via, false);
                    if (!detour.isEmpty()) {
                        addCandidate(candidates, seen, detour.get(0), gate, avoidRadius);
                    }
                } catch (Exception ex) {
                    log.debug("Detour via bearing {} failed: {}", bearing, ex.getMessage());
                }
            }
        }

        if (candidates.isEmpty()) {
            return new AlternativeRouteResponse(
                    false,
                    "No verified alternative route avoiding this crossing was found from OSRM. "
                            + "Do not drive through the closed crossing; wait or use an official diversion.",
                    "OSRM / OpenStreetMap",
                    gate.getId(),
                    gate.getName(),
                    Instant.now(),
                    originalDto,
                    List.of(),
                    null,
                    false,
                    avoidRadius
            );
        }

        candidates.sort(Comparator
                .comparingDouble(OsrmRoute::durationSeconds)
                .thenComparingDouble(OsrmRoute::distanceMetres));

        List<OsrmRoute> limited = candidates.stream()
                .limit(Math.max(2, properties.getRouteMaxAlternatives()))
                .toList();

        String recommendedId = "alt-1";
        List<AlternativeRouteResponse.RouteOption> options = new ArrayList<>();
        for (int index = 0; index < limited.size(); index++) {
            OsrmRoute route = limited.get(index);
            String id = "alt-" + (index + 1);
            boolean recommended = index == 0;
            double distanceFromGate = GeoUtils.distancePointToPolylineMeters(
                    gate.getLatitude(), gate.getLongitude(), route.geometry());
            List<AlternativeRouteResponse.RoutePoint> geometry = points(route.geometry());
            options.add(new AlternativeRouteResponse.RouteOption(
                    id,
                    recommended ? "Fastest alternative" : "Alternative " + (index + 1),
                    km(route.distanceMetres()),
                    minutes(route.durationSeconds()),
                    round(Math.max(0, (route.distanceMetres() - original.distanceMetres()) / 1000.0), 1),
                    round(Math.max(0, (route.durationSeconds() - original.durationSeconds()) / 60.0), 1),
                    recommended,
                    round(distanceFromGate, 1),
                    geometry,
                    navigationUrl(request, geometry, false),
                    navigationUrl(request, geometry, true)
            ));
        }

        return new AlternativeRouteResponse(
                true,
                options.size() == 1
                        ? "One verified alternative route avoids the crossing. No second safe route was returned by OSRM."
                        : "Alternative routes verified to stay outside the crossing avoidance radius.",
                "OSRM / OpenStreetMap",
                gate.getId(),
                gate.getName(),
                Instant.now(),
                originalDto,
                List.copyOf(options),
                recommendedId,
                false,
                avoidRadius
        );
    }

    private void addCandidate(List<OsrmRoute> candidates,
                              Set<String> seen,
                              OsrmRoute route,
                              RailwayGate gate,
                              double avoidRadius) {
        double distanceFromGate = GeoUtils.distancePointToPolylineMeters(
                gate.getLatitude(), gate.getLongitude(), route.geometry());
        if (distanceFromGate <= avoidRadius) {
            return;
        }
        String key = Math.round(route.distanceMetres() / 25.0) + ":"
                + Math.round(route.durationSeconds() / 30.0);
        if (seen.add(key)) {
            candidates.add(route);
        }
    }

    private List<OsrmRoute> requestRoutes(double startLat,
                                          double startLng,
                                          double destLat,
                                          double destLng,
                                          GeoUtils.GeoPoint via,
                                          boolean alternatives) throws Exception {
        StringBuilder coordinates = new StringBuilder();
        coordinates.append(coordinate(startLat, startLng));
        if (via != null) {
            coordinates.append(';').append(coordinate(via.lat(), via.lng()));
        }
        coordinates.append(';').append(coordinate(destLat, destLng));

        String base = properties.getOsrmBaseUrl() == null
                ? "https://router.project-osrm.org"
                : properties.getOsrmBaseUrl().replaceAll("/+$", "");
        String url = base + "/route/v1/driving/" + coordinates
                + "?overview=full&geometries=geojson&steps=false&alternatives="
                + (alternatives ? "true" : "false");

        String body = routingRestClient.get().uri(URI.create(url)).retrieve().body(String.class);
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(body);
        if (!"Ok".equalsIgnoreCase(root.path("code").asText())) {
            return List.of();
        }

        JsonNode routes = root.path("routes");
        if (!routes.isArray()) {
            return List.of();
        }

        List<OsrmRoute> result = new ArrayList<>();
        for (JsonNode node : routes) {
            double distance = node.path("distance").asDouble(Double.NaN);
            double duration = node.path("duration").asDouble(Double.NaN);
            JsonNode coordinatesNode = node.path("geometry").path("coordinates");
            if (!Double.isFinite(distance) || !Double.isFinite(duration) || !coordinatesNode.isArray()) {
                continue;
            }
            List<GeoUtils.GeoPoint> geometry = new ArrayList<>();
            for (JsonNode pair : coordinatesNode) {
                if (pair.isArray() && pair.size() >= 2) {
                    geometry.add(new GeoUtils.GeoPoint(pair.get(1).asDouble(), pair.get(0).asDouble()));
                }
            }
            if (geometry.size() >= 2) {
                result.add(new OsrmRoute(distance, duration, List.copyOf(geometry)));
            }
        }
        return result;
    }

    private static String coordinate(double lat, double lng) {
        return String.format(Locale.US, "%.6f,%.6f", lng, lat);
    }

    private static String navigationUrl(AlternativeRouteRequest request,
                                        List<AlternativeRouteResponse.RoutePoint> geometry,
                                        boolean osm) {
        List<AlternativeRouteResponse.RoutePoint> waypoints = sampleWaypoints(geometry);
        if (osm) {
            StringBuilder route = new StringBuilder();
            route.append(String.format(Locale.US, "%.6f,%.6f", request.startLat(), request.startLng()));
            for (AlternativeRouteResponse.RoutePoint point : waypoints) {
                route.append(';').append(String.format(Locale.US, "%.6f,%.6f", point.lat(), point.lng()));
            }
            route.append(';').append(String.format(Locale.US, "%.6f,%.6f", request.destLat(), request.destLng()));
            return "https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route=" + route;
        }

        StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving")
                .append("&origin=").append(String.format(Locale.US, "%.6f,%.6f", request.startLat(), request.startLng()))
                .append("&destination=").append(String.format(Locale.US, "%.6f,%.6f", request.destLat(), request.destLng()));
        if (!waypoints.isEmpty()) {
            url.append("&waypoints=");
            for (int index = 0; index < waypoints.size(); index++) {
                if (index > 0) {
                    url.append('|');
                }
                url.append(String.format(Locale.US, "%.6f,%.6f", waypoints.get(index).lat(), waypoints.get(index).lng()));
            }
        }
        return url.toString();
    }

    private static List<AlternativeRouteResponse.RoutePoint> sampleWaypoints(
            List<AlternativeRouteResponse.RoutePoint> geometry) {
        if (geometry.size() < 6) {
            return List.of();
        }
        List<AlternativeRouteResponse.RoutePoint> result = new ArrayList<>();
        for (double fraction : new double[]{0.28, 0.55, 0.8}) {
            int index = Math.min(geometry.size() - 2, Math.max(1, (int) Math.round(geometry.size() * fraction)));
            result.add(geometry.get(index));
        }
        return result;
    }

    private static List<AlternativeRouteResponse.RoutePoint> points(List<GeoUtils.GeoPoint> geometry) {
        return geometry.stream()
                .map(point -> new AlternativeRouteResponse.RoutePoint(
                        round(point.lat(), 6), round(point.lng(), 6)))
                .toList();
    }

    private static AlternativeRouteResponse unavailable(RailwayGate gate, String message) {
        return new AlternativeRouteResponse(
                false,
                message,
                "OSRM / OpenStreetMap",
                gate.getId(),
                gate.getName(),
                Instant.now(),
                null,
                List.of(),
                null,
                false,
                0
        );
    }

    private static double km(double metres) {
        return round(metres / 1000.0, 1);
    }

    private static double minutes(double seconds) {
        return round(seconds / 60.0, 0);
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private record OsrmRoute(double distanceMetres, double durationSeconds, List<GeoUtils.GeoPoint> geometry) {
    }

    private record CacheEntry(Instant expiresAt, AlternativeRouteResponse response) {
    }
}
