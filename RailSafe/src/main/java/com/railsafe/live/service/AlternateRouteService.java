package com.railsafe.live.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railsafe.entity.GateStatus;
import com.railsafe.entity.RailwayGate;
import com.railsafe.service.GateMapper;
import com.railsafe.entity.RouteAlternative;
import com.railsafe.live.dto.AlternateRouteListResponse;
import com.railsafe.repository.RouteAlternativeRepository;
import com.railsafe.service.DemoDataClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Verified alternate routes calculated by OSRM for a closed crossing.
 *
 * Only genuine calculated rows are returned here. Managed test rows remain in the
 * database for the dedicated Super Admin review area but never reach normal views.
 */
@Service
public class AlternateRouteService {

    private static final Logger log = LoggerFactory.getLogger(AlternateRouteService.class);

    private final RouteAlternativeRepository routeRepository;
    private final ObjectMapper objectMapper;
    private final GateMapper gateMapper;

    public AlternateRouteService(RouteAlternativeRepository routeRepository,
                                 ObjectMapper objectMapper,
                                 GateMapper gateMapper) {
        this.routeRepository = routeRepository;
        this.objectMapper = objectMapper;
        this.gateMapper = gateMapper;
    }

    @Transactional(readOnly = true)
    public AlternateRouteListResponse forGate(RailwayGate gate) {
        List<RouteAlternative> routes = routeRepository
                .findTop50ByGateIdAndSyntheticFalseOrderByGeneratedAtDesc(gate.getId()).stream()
                .filter(route -> !DemoDataClassifier.isDemoRouteAlternative(route))
                .toList();

        if (routes.isEmpty()) {
            return new AlternateRouteListResponse(
                    false,
                    false,
                    "No verified alternate routes are stored for this crossing. "
                            + "Use the route finder, or wait for an official diversion.",
                    gate.getId(),
                    gate.getName(),
                    GateStatus.AWAITING_VERIFICATION.name(),
                    Instant.now(),
                    List.of()
            );
        }

        List<AlternateRouteListResponse.RouteSuggestion> suggestions = new ArrayList<>(routes.size());
        boolean first = true;
        for (RouteAlternative route : routes) {
            boolean recommended = first;
            first = false;

            List<AlternateRouteListResponse.RoutePoint> geometry = parseGeometry(route.getGeometryJson());

            suggestions.add(new AlternateRouteListResponse.RouteSuggestion(
                    route.getId(),
                    route.getRouteLabel() == null ? "Verified alternative" : route.getRouteLabel(),
                    round(route.getDistanceKm()),
                    round(route.getDurationMinutes()),
                    route.getExtraDistanceKm() == null ? null : round(route.getExtraDistanceKm()),
                    recommended,
                    true,
                    recommended
                            ? "Fastest verified alternative returned by the routing engine."
                            : "Verified alternative returned by the routing engine.",
                    route.getNavigationUrl(),
                    mapUrl(route),
                    geometry
            ));
        }

        return new AlternateRouteListResponse(
                true,
                false,
                "Routes verified against the crossing avoidance radius. Distances and travel times are "
                        + "estimates. Check local roads and official diversions before travelling.",
                gate.getId(),
                gate.getName(),
                gateMapper.publicStatus(gate).name(),
                Instant.now(),
                List.copyOf(suggestions)
        );
    }

    private List<AlternateRouteListResponse.RoutePoint> parseGeometry(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<AlternateRouteListResponse.RoutePoint> points = new ArrayList<>(root.size());
            for (JsonNode node : root) {
                if (node.isArray() && node.size() >= 2) {
                    points.add(new AlternateRouteListResponse.RoutePoint(
                            node.get(0).asDouble(), node.get(1).asDouble()));
                } else if (node.isObject() && node.has("lat") && node.has("lng")) {
                    points.add(new AlternateRouteListResponse.RoutePoint(
                            node.get("lat").asDouble(), node.get("lng").asDouble()));
                }
            }
            return List.copyOf(points);
        } catch (Exception ex) {
            log.warn("Could not parse route geometry: {}", ex.getMessage());
            return List.of();
        }
    }

    private static String mapUrl(RouteAlternative route) {
        return String.format(Locale.ROOT,
                "https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route=%.5f%%2C%.5f%%3B%.5f%%2C%.5f",
                route.getStartLat(), route.getStartLng(), route.getDestLat(), route.getDestLng());
    }

    private static double round(Double value) {
        if (value == null) {
            return 0.0;
        }
        return Math.round(value * 10.0) / 10.0;
    }
}
