package com.railsafe.live.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railsafe.config.LiveProperties;
import com.railsafe.entity.RailwayGate;
import com.railsafe.live.model.LiveDataMode;
import com.railsafe.live.model.LiveTrainStatus;
import com.railsafe.live.model.TrainDirection;
import com.railsafe.live.model.TrainFeed;
import com.railsafe.live.model.TrainFeedTrain;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Third-party live-train provider backed by the independent RailRadar API.
 *
 * <h2>Data honesty</h2>
 * RailRadar is not Indian Railways, IRCTC or NTES. Everything returned by this
 * provider is labelled {@link LiveDataMode#THIRD_PARTY}; it is never presented as
 * an official railway feed. RailSafe only reproduces positions, route times and
 * delays reported by RailRadar, and it marks derived arrival times as estimates.
 *
 * <h2>Why the polling loop looks unusual</h2>
 * RailRadar exposes live status per train ({@code /v1/trains/{number}/live}), not
 * as one aggregate feed. Calling it for every gate on every dashboard refresh
 * would exhaust even a large plan, so this provider:
 * <ul>
 *   <li>polls the configured train numbers in rotation;</li>
 *   <li>treats the monthly request quota as a hard budget and spreads the
 *       remaining requests across the rest of the month;</li>
 *   <li>enforces RailRadar's per-minute limit;</li>
 *   <li>persists the quota counters to disk so a restart cannot reset them;</li>
 *   <li>keeps the last successful values and reports them as stale rather than
 *       inventing fresher data.</li>
 * </ul>
 *
 * <h2>Gate matching</h2>
 * A train is associated with a gate only when a stop on its reported route lies
 * within {@code railsafe.live.match-radius-km} of that gate. The ETA is taken from
 * RailRadar's route times when present, otherwise it is derived from the reported
 * speed and distance and is explicitly marked as an estimate.
 */
public class RailRadarTrainDataProvider implements TrainDataProvider {

    private static final Logger log = LoggerFactory.getLogger(RailRadarTrainDataProvider.class);

    private static final String PROVIDER_ID =
            "RailRadar (third-party; not official Indian Railways)";

    /** Stay below RailRadar's 10 requests/minute limit even during a burst. */
    private static final int MAX_REQUESTS_PER_MINUTE = 9;

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final ZoneId RAILRADAR_ZONE = ZoneId.of("Asia/Kolkata");

    private final LiveProperties properties;
    private final RailwayGateRepository gateRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final List<String> trainNumbers;

    /** Last successfully parsed values, keyed by train number. */
    private final Map<String, ParsedTrain> latest = new LinkedHashMap<>();
    private final Deque<Instant> recentRequests = new ArrayDeque<>();

    private int cursor;
    private Instant lastRequestAt;
    private Instant lastSuccessAt;
    private String lastError;
    private QuotaState quota;
    private Integer discoveredMonthlyLimit;

    public RailRadarTrainDataProvider(LiveProperties properties,
                                      RailwayGateRepository gateRepository,
                                      RestClient restClient,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.gateRepository = gateRepository;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.trainNumbers = Arrays.stream(
                        properties.getRailRadar().getTrainNumbers() == null
                                ? new String[0]
                                : properties.getRailRadar().getTrainNumbers().split(","))
                .map(String::trim)
                .filter(value -> value.matches("\\d{3,6}"))
                .distinct()
                .toList();
        restoreCache();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isConfigured() {
        return hasText(properties.getRailRadar().getApiKey()) && hasText(properties.getRailRadar().getBaseUrl());
    }

    @Override
    public TrainFeed fetch() {
        if (!hasText(properties.getRailRadar().getApiKey())) {
            return TrainFeed.unavailable(PROVIDER_ID,
                    "Third-party live data unavailable: no RailRadar API key is configured. "
                            + "Set RAILSAFE_RAILRADAR_KEY to a key from https://railradar.in/developers. "
                            + "RailRadar is an independent provider, not official Indian Railways data.");
        }
        if (!hasText(properties.getRailRadar().getBaseUrl())) {
            return TrainFeed.unavailable(PROVIDER_ID,
                    "Third-party live data unavailable: railsafe.live.railradar.base-url is not configured.");
        }
        if (trainNumbers.isEmpty()) {
            return TrainFeed.unavailable(PROVIDER_ID,
                    "Third-party live data unavailable: no valid train numbers are configured in "
                            + "railsafe.live.railradar.train-numbers.");
        }

        Instant now = Instant.now();
        pollIfAllowed(now);
        return buildFeed(now);
    }

    // ------------------------------------------------------------------
    // Polling and quota
    // ------------------------------------------------------------------

    private void pollIfAllowed(Instant now) {
        synchronized (this) {
            QuotaState state = quota();
            int remaining = remainingRequests(state);
            if (remaining <= 0) {
                return;
            }

            boolean firstRun = !state.primed();
            if (!firstRun && lastRequestAt != null
                    && now.isBefore(lastRequestAt.plusSeconds(effectiveIntervalSeconds(state)))) {
                return;
            }

            int budget = firstRun
                    ? Math.max(1, properties.getRailRadar().getInitialBurst())
                    : 1;
            budget = Math.min(budget, Math.min(trainNumbers.size(), remaining));

            for (int index = 0; index < budget; index++) {
                if (!perMinuteAllows(Instant.now())) {
                    break;
                }
                String number = trainNumbers.get(cursor % trainNumbers.size());
                cursor = (cursor + 1) % trainNumbers.size();
                requestTrain(number);
            }
        }
    }

    /** Requests are spread over the remaining days so a monthly plan lasts the month. */
    private long effectiveIntervalSeconds(QuotaState state) {
        long poll = Math.max(1, properties.getRailRadar().getPollIntervalSeconds());
        long min = Math.max(1, properties.getRailRadar().getMinRequestIntervalSeconds());
        long interval = Math.max(poll, min);

        int limit = effectiveMonthlyLimit();
        if (limit > 0) {
            int remaining = Math.max(0, limit - state.used());
            if (remaining == 0) {
                return Long.MAX_VALUE;
            }
            long secondsLeft = secondsUntilEndOfMonth();
            long spread = secondsLeft / remaining;
            interval = Math.max(interval, spread);
        }
        return interval;
    }

    private boolean perMinuteAllows(Instant now) {
        while (!recentRequests.isEmpty()
                && recentRequests.peekFirst().isBefore(now.minusSeconds(60))) {
            recentRequests.removeFirst();
        }
        return recentRequests.size() < MAX_REQUESTS_PER_MINUTE;
    }

    private int remainingRequests(QuotaState state) {
        int limit = effectiveMonthlyLimit();
        return limit <= 0 ? Integer.MAX_VALUE : Math.max(0, limit - state.used());
    }

    private int effectiveMonthlyLimit() {
        int configured = properties.getRailRadar().getMonthlyQuota();
        if (configured > 0 && discoveredMonthlyLimit != null) {
            return Math.min(configured, discoveredMonthlyLimit);
        }
        if (configured > 0) {
            return configured;
        }
        return discoveredMonthlyLimit == null ? 0 : discoveredMonthlyLimit;
    }

    private long secondsUntilEndOfMonth() {
        ZonedDateTime now = ZonedDateTime.now(RAILRADAR_ZONE);
        ZonedDateTime end = now.toLocalDate().withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .plusDays(1).atStartOfDay(RAILRADAR_ZONE);
        return Math.max(60, java.time.Duration.between(now, end).getSeconds());
    }

    private void requestTrain(String number) {
        String baseUrl = trimTrailingSlash(properties.getRailRadar().getBaseUrl());
        String url = baseUrl + "/v1/trains/{number}/live?includeCoordinates=true&haltsOnly=false";
        Instant requestedAt = Instant.now();

        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(url, number)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + properties.getRailRadar().getApiKey().trim())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .toEntity(String.class);

            recordAttempt(requestedAt, true, response.getHeaders());
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                lastError = "RailRadar returned an empty response for train " + number + ".";
                return;
            }

            JsonNode root = objectMapper.readTree(body);
            if (root.path("success").isBoolean() && !root.path("success").asBoolean()) {
                lastError = "RailRadar reported an unsuccessful response for train " + number + ".";
                return;
            }

            JsonNode data = root.path("data");
            ParsedTrain parsed = parseTrain(data);
            if (parsed == null) {
                lastError = "RailRadar returned no usable live data for train " + number + ".";
                return;
            }

            latest.put(parsed.number(), parsed);
            lastSuccessAt = Instant.now();
            lastError = null;
            persistCache();
            log.info("RailRadar live status cached for train {} ({})", parsed.number(), parsed.providerStatus());
        } catch (RestClientResponseException ex) {
            recordAttempt(requestedAt, false, ex.getResponseHeaders());
            lastError = describeHttpError(ex, number);
            log.warn("RailRadar request for train {} failed with HTTP {}: {}",
                    number, ex.getStatusCode().value(), ex.getStatusText());
        } catch (Exception ex) {
            recordAttempt(requestedAt, false, null);
            lastError = "RailRadar could not be reached for train " + number + ".";
            log.warn("RailRadar request for train {} failed: {}", number, ex.getMessage());
        }
    }

    private void recordAttempt(Instant requestedAt, boolean countUsage, HttpHeaders headers) {
        lastRequestAt = requestedAt;
        recentRequests.addLast(requestedAt);
        while (!recentRequests.isEmpty()
                && recentRequests.peekFirst().isBefore(requestedAt.minusSeconds(60))) {
            recentRequests.removeFirst();
        }

        QuotaState state = quota();
        int used = state.used() + (countUsage ? 1 : 0);

        // The provider's own headers are the source of truth when present; they
        // also cover usage made by other clients that share the same API key.
        if (headers != null) {
            Integer limit = integerHeader(headers, "X-RateLimit-Limit-Month");
            Integer remaining = integerHeader(headers, "X-RateLimit-Remaining-Month");
            if (limit != null) {
                discoveredMonthlyLimit = limit;
            }
            if (limit != null && remaining != null) {
                used = Math.max(0, limit - remaining);
            }
        }

        quota = new QuotaState(currentMonth(), Math.max(0, used), true);
        persistQuota(quota);
        persistCache();
    }

    private String describeHttpError(RestClientResponseException ex, String number) {
        int status = ex.getStatusCode().value();
        return switch (status) {
            case 401 -> "RailRadar rejected the API key while reading train " + number + ".";
            case 403 -> "RailRadar refused train " + number + " for this plan or API key.";
            case 404 -> "RailRadar has no live run for train " + number + ".";
            case 429 -> "RailRadar rate limit or monthly quota reached while reading train " + number + ".";
            default -> "RailRadar returned HTTP " + status + " for train " + number + ".";
        };
    }

    private static Integer integerHeader(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Quota persistence
    // ------------------------------------------------------------------

    private QuotaState quota() {
        if (quota == null) {
            quota = loadQuota();
        }
        return quota;
    }

    private QuotaState loadQuota() {
        String month = currentMonth();
        String file = properties.getRailRadar().getQuotaFile();
        if (file == null || file.isBlank()) {
            return new QuotaState(month, 0, false);
        }
        File target = new File(file);
        if (!target.isFile()) {
            return new QuotaState(month, 0, false);
        }
        try {
            QuotaState stored = objectMapper.readValue(target, QuotaState.class);
            if (stored == null || !month.equals(stored.month())) {
                return new QuotaState(month, 0, false);
            }
            return new QuotaState(month, Math.max(0, stored.used()), stored.primed());
        } catch (Exception ex) {
            log.warn("Could not read RailRadar quota file {}: {}", file, ex.getMessage());
            return new QuotaState(month, 0, false);
        }
    }

    private void persistQuota(QuotaState state) {
        String file = properties.getRailRadar().getQuotaFile();
        if (file == null || file.isBlank()) {
            return;
        }
        try {
            File target = new File(file);
            File parent = target.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target, state);
        } catch (Exception ex) {
            log.warn("Could not write RailRadar quota file {}: {}", file, ex.getMessage());
        }
    }

    private static String currentMonth() {
        return ZonedDateTime.now(RAILRADAR_ZONE).format(MONTH_FORMAT);
    }

    // ------------------------------------------------------------------
    // Restart-safe cache
    // ------------------------------------------------------------------

    /**
     * Loads the last successful trains so a restart neither loses real values nor
     * spends quota re-fetching them. Failure is non-fatal: the provider simply
     * starts empty and waits for its next allowed request.
     */
    private void restoreCache() {
        String file = properties.getRailRadar().getCacheFile();
        if (file == null || file.isBlank()) {
            return;
        }
        File target = new File(file);
        if (!target.isFile()) {
            return;
        }
        try {
            CacheState state = objectMapper.readValue(target, CacheState.class);
            if (state == null) {
                return;
            }
            if (state.lastRequestAt() != null) {
                lastRequestAt = state.lastRequestAt();
            }
            if (state.lastSuccessAt() != null) {
                lastSuccessAt = state.lastSuccessAt();
            }
            if (state.trains() != null) {
                latest.putAll(state.trains());
            }
            log.info("Restored {} cached RailRadar train(s) from {}", latest.size(), file);
        } catch (Exception ex) {
            log.warn("Could not read RailRadar cache file {}: {}", file, ex.getMessage());
        }
    }

    private void persistCache() {
        String file = properties.getRailRadar().getCacheFile();
        if (file == null || file.isBlank()) {
            return;
        }
        try {
            File target = new File(file);
            File parent = target.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            CacheState state = new CacheState(lastRequestAt, lastSuccessAt,
                    new LinkedHashMap<>(latest));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target, state);
        } catch (Exception ex) {
            log.warn("Could not write RailRadar cache file {}: {}", file, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Feed construction
    // ------------------------------------------------------------------

    private TrainFeed buildFeed(Instant now) {
        if (latest.isEmpty()) {
            String message = lastError == null
                    ? "RailRadar has not returned a running train for the configured numbers yet. "
                            + "RailRadar is a third-party provider, not official Indian Railways data."
                    : "Third-party live data unavailable: " + lastError
                            + " RailRadar is an independent provider, not official Indian Railways data.";
            return new TrainFeed(false, LiveDataMode.UNAVAILABLE, PROVIDER_ID, now,
                    message, List.of(), true);
        }

        List<RailwayGate> gates = gateRepository.findAll();
        List<TrainFeedTrain> trains = new ArrayList<>();

        for (ParsedTrain train : latest.values()) {
            if (!"running".equalsIgnoreCase(train.providerStatus())) {
                continue;
            }
            List<TrainFeedTrain> matched = mapToGates(train, gates, now);
            if (matched.isEmpty()) {
                trains.add(baseEntry(train));
            } else {
                trains.addAll(matched);
            }
        }

        Instant updatedAt = lastSuccessAt == null ? now : lastSuccessAt;
        boolean stale = updatedAt.isBefore(now.minusSeconds(Math.max(30, properties.getFeedStaleSeconds())));

        boolean realTime = latest.values().stream()
                .anyMatch(train -> train.live()
                        && "real-time".equalsIgnoreCase(train.trackingMode()));

        StringBuilder message = new StringBuilder()
                .append("Third-party live data from RailRadar, an independent provider. ")
                .append("Not official Indian Railways, IRCTC or NTES data. ")
                .append("ETAs are estimates derived from RailRadar route times and may be incomplete.");

        QuotaState state = quota();
        int limit = effectiveMonthlyLimit();
        if (limit > 0) {
            message.append(" RailRadar API budget used this month: ")
                    .append(state.used()).append('/').append(limit).append('.');
        }
        if (!realTime) {
            message.append(" RailRadar reported schedule-only tracking for the cached trains.");
        }
        if (lastError != null) {
            message.append(" Last provider message: ").append(lastError);
        }
        if (stale) {
            message.append(" Cached data is older than ")
                    .append(properties.getFeedStaleSeconds())
                    .append(" seconds, so predictions are withheld.");
        }

        return new TrainFeed(true, LiveDataMode.THIRD_PARTY, PROVIDER_ID, updatedAt,
                message.toString(), List.copyOf(trains), stale);
    }

    /**
     * Associates one real train with every RailSafe gate that lies within the
     * configured radius of a stop still ahead on its route.
     */
    private List<TrainFeedTrain> mapToGates(ParsedTrain train, List<RailwayGate> gates, Instant now) {
        if (train.latitude() == null || train.longitude() == null) {
            return List.of();
        }

        List<RouteStop> route = train.route();
        List<RouteStop> upcoming = upcomingStops(train, route);
        if (upcoming.isEmpty()) {
            return List.of();
        }

        double radius = Math.max(0.1, properties.getMatchRadiusKm());
        List<TrainFeedTrain> result = new ArrayList<>();

        for (RailwayGate gate : gates) {
            if (gate.getLatitude() == null || gate.getLongitude() == null) {
                continue;
            }
            RouteStop closest = null;
            double closestKm = radius;
            for (RouteStop stop : upcoming) {
                if (stop.latitude() == null || stop.longitude() == null) {
                    continue;
                }
                double distance = GeoUtils.haversineKm(stop.latitude(), stop.longitude(),
                        gate.getLatitude(), gate.getLongitude());
                if (distance <= closestKm) {
                    closestKm = distance;
                    closest = stop;
                }
            }
            if (closest == null) {
                continue;
            }

            Instant eta = etaFor(train, closest, gate, now);
            Instant scheduled = closest.scheduledArrival();
            boolean estimated = closest.actualArrival() == null
                    || !"observed".equalsIgnoreCase(closest.provenance());
            boolean atGate = GeoUtils.haversineKm(train.latitude(), train.longitude(),
                    gate.getLatitude(), gate.getLongitude()) <= properties.getNearGateKm();
            LiveTrainStatus hint = eta == null
                    ? (atGate ? LiveTrainStatus.NEAR_GATE : LiveTrainStatus.APPROACHING)
                    : LiveTrainStatus.UNKNOWN;

            result.add(new TrainFeedTrain(
                    train.number(),
                    train.name(),
                    directionOf(train, upcoming, closest),
                    train.latitude(),
                    train.longitude(),
                    train.speedKmph(),
                    gate.getGateCode(),
                    eta,
                    scheduled,
                    estimated,
                    null,
                    null,
                    hint,
                    originName(route),
                    terminusName(route),
                    currentStation(train, route),
                    train.updatedAt() == null ? now : train.updatedAt(),
                    PROVIDER_ID
            ));
        }
        return result;
    }

    /** Stops from the current position onwards, ordered by route sequence. */
    private static List<RouteStop> upcomingStops(ParsedTrain train, List<RouteStop> route) {
        if (route.isEmpty()) {
            return List.of();
        }
        int start = 0;
        if (train.sequence() != null) {
            for (int index = 0; index < route.size(); index++) {
                if (route.get(index).sequence() <= train.sequence() + 0.5) {
                    start = index;
                }
            }
        } else if (train.latitude() != null && train.longitude() != null) {
            double best = Double.POSITIVE_INFINITY;
            for (int index = 0; index < route.size(); index++) {
                RouteStop stop = route.get(index);
                if (stop.latitude() == null || stop.longitude() == null) {
                    continue;
                }
                double distance = GeoUtils.haversineKm(train.latitude(), train.longitude(),
                        stop.latitude(), stop.longitude());
                if (distance < best) {
                    best = distance;
                    start = index;
                }
            }
        }
        return route.subList(start, route.size());
    }

    /** First stop on the train's route, used as the reported origin. */
    private static String originName(List<RouteStop> route) {
        return route.isEmpty() ? null : route.get(0).stationName();
    }

    /** Last stop on the train's route, used as the reported destination. */
    private static String terminusName(List<RouteStop> route) {
        return route.isEmpty() ? null : route.get(route.size() - 1).stationName();
    }

    /** Station at, or immediately before, the train's current route sequence. */
    private static String currentStation(ParsedTrain train, List<RouteStop> route) {
        if (route.isEmpty()) {
            return null;
        }
        if (train.sequence() == null) {
            if (train.latitude() == null || train.longitude() == null) {
                return null;
            }
            RouteStop best = null;
            double bestKm = Double.POSITIVE_INFINITY;
            for (RouteStop stop : route) {
                if (stop.latitude() == null || stop.longitude() == null) {
                    continue;
                }
                double km = GeoUtils.haversineKm(train.latitude(), train.longitude(),
                        stop.latitude(), stop.longitude());
                if (km < bestKm) {
                    bestKm = km;
                    best = stop;
                }
            }
            return best == null ? null : best.stationName();
        }
        RouteStop current = route.get(0);
        for (RouteStop stop : route) {
            if (stop.sequence() <= train.sequence() + 0.5) {
                current = stop;
            }
        }
        return current.stationName();
    }

    private Instant etaFor(ParsedTrain train, RouteStop stop, RailwayGate gate, Instant now) {
        if (stop.actualArrival() != null) {
            return stop.actualArrival();
        }

        Integer delay = stop.delayArrival() == null ? train.delayMinutes() : stop.delayArrival();
        if (stop.scheduledArrival() != null) {
            return stop.scheduledArrival().plusSeconds(60L * Math.max(0, delay == null ? 0 : delay));
        }

        Double speed = train.speedKmph();
        if (speed == null || speed <= 5) {
            speed = stop.speedToNextKmph() != null && stop.speedToNextKmph() > 5
                    ? stop.speedToNextKmph()
                    : null;
        }
        if (speed == null || speed <= 5) {
            return null;
        }

        if (stop.distance() != null && train.distanceFromOriginKm() != null) {
            double remainingKm = stop.distance() - train.distanceFromOriginKm();
            if (remainingKm >= 0) {
                return now.plusSeconds(Math.round(remainingKm / speed * 3600.0));
            }
        }
        if (train.latitude() != null && train.longitude() != null) {
            double remainingKm = GeoUtils.haversineKm(train.latitude(), train.longitude(),
                    gate.getLatitude(), gate.getLongitude());
            return now.plusSeconds(Math.round(remainingKm / speed * 3600.0));
        }
        return null;
    }

    /** Position-only entry used when no gate is close enough to the route. */
    private TrainFeedTrain baseEntry(ParsedTrain train) {
        return new TrainFeedTrain(
                train.number(),
                train.name(),
                directionOf(train, train.route(), null),
                train.latitude(),
                train.longitude(),
                train.speedKmph(),
                null,
                null,
                null,
                true,
                null,
                null,
                LiveTrainStatus.UNKNOWN,
                originName(train.route()),
                terminusName(train.route()),
                currentStation(train, train.route()),
                train.updatedAt() == null ? Instant.now() : train.updatedAt(),
                PROVIDER_ID
        );
    }

    private static TrainDirection directionOf(ParsedTrain train,
                                              List<RouteStop> route,
                                              RouteStop matched) {
        Double bearing = train.bearingDegrees();
        if ((bearing == null || bearing < 0) && train.latitude() != null && train.longitude() != null) {
            RouteStop target = matched;
            if (target == null || target.latitude() == null || target.longitude() == null) {
                target = route.stream()
                        .filter(stop -> stop.latitude() != null && stop.longitude() != null)
                        .filter(stop -> train.sequence() == null || stop.sequence() > train.sequence())
                        .findFirst()
                        .orElse(null);
            }
            if (target != null && target.latitude() != null && target.longitude() != null) {
                bearing = bearingBetween(train.latitude(), train.longitude(),
                        target.latitude(), target.longitude());
            }
        }
        return directionFromBearing(bearing);
    }

    private static double bearingBetween(double lat1, double lng1, double lat2, double lng2) {
        double dLon = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private static TrainDirection directionFromBearing(Double bearing) {
        if (bearing == null || bearing.isNaN()) {
            return TrainDirection.UNKNOWN;
        }
        double value = ((bearing % 360.0) + 360.0) % 360.0;
        if (value < 45.0 || value >= 315.0) {
            return TrainDirection.NORTH;
        }
        if (value < 135.0) {
            return TrainDirection.EAST;
        }
        if (value < 225.0) {
            return TrainDirection.SOUTH;
        }
        return TrainDirection.WEST;
    }

    // ------------------------------------------------------------------
    // JSON parsing
    // ------------------------------------------------------------------

    private ParsedTrain parseTrain(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull() || !data.isObject()) {
            return null;
        }

        String number = text(data, "trainNumber");
        if (number == null || number.isBlank()) {
            return null;
        }
        String name = text(data, "trainName");
        if (name == null || name.isBlank()) {
            name = "Service " + number;
        }

        Integer topLevelDelay = integer(data, "delayMinutes");
        JsonNode location = data.path("currentLocation");

        Double latitude = null;
        Double longitude = null;
        Double sequence = null;
        Double speed = null;
        Double distanceFromOrigin = null;
        Double bearing = null;
        String locationStation = null;
        String locationStatus = null;
        Integer delay = topLevelDelay;

        if (location != null && location.isObject()) {
            JsonNode coordinates = location.path("coordinates");
            if (coordinates.isObject()) {
                latitude = decimal(coordinates, "lat");
                longitude = decimal(coordinates, "lng");
            }
            sequence = decimal(location, "sequence");
            speed = decimal(location, "speedKmh");
            distanceFromOrigin = decimal(location, "distanceFromOriginKm");
            bearing = decimal(location, "bearingDegrees");
            locationStation = text(location, "stationCode");
            locationStatus = text(location, "status");
            if (delay == null) {
                delay = integer(location, "delayMinutes");
            }
        }

        List<RouteStop> route = new ArrayList<>();
        JsonNode routeNode = data.path("route");
        if (routeNode.isArray()) {
            for (int index = 0; index < routeNode.size(); index++) {
                JsonNode node = routeNode.get(index);
                if (!node.isObject()) {
                    continue;
                }
                Double stopSequence = decimal(node, "sequence");
                if (stopSequence == null) {
                    stopSequence = (double) index;
                }
                route.add(new RouteStop(
                        stopSequence,
                        text(node, "stationCode"),
                        text(node, "stationName"),
                        bool(node, "isHalt", false),
                        decimal(node, "lat"),
                        decimal(node, "lng"),
                        instant(node, "scheduledArrival"),
                        instant(node, "scheduledDeparture"),
                        instant(node, "actualArrival"),
                        instant(node, "actualDeparture"),
                        text(node, "provenance"),
                        integer(node, "delayArrival"),
                        text(node, "status"),
                        decimal(node, "distance"),
                        decimal(node, "speedToNextStationKmph")
                ));
            }
        }
        route.sort(Comparator.comparingDouble(RouteStop::sequence));

        return new ParsedTrain(
                number.trim(),
                name,
                text(data, "status"),
                bool(data, "isLive", false),
                text(data, "trackingMode"),
                instant(data, "lastUpdatedAt"),
                locationStation,
                locationStatus,
                sequence,
                latitude,
                longitude,
                speed,
                distanceFromOrigin,
                bearing,
                delay,
                List.copyOf(route)
        );
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText();
                if (!text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private static Double decimal(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) {
                return value.asDouble();
            }
            if (value.isTextual()) {
                try {
                    return Double.parseDouble(value.asText());
                } catch (NumberFormatException ignored) {
                    // Keep looking at the next alias.
                }
            }
        }
        return null;
    }

    private static Integer integer(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // Keep looking at the next alias.
                }
            }
        }
        return null;
    }

    private static boolean bool(JsonNode node, String name, boolean fallback) {
        JsonNode value = node.path(name);
        return value.isBoolean() ? value.asBoolean() : fallback;
    }

    private static Instant instant(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) {
                long raw = value.asLong();
                return raw > 1_000_000_000_000L
                        ? Instant.ofEpochMilli(raw)
                        : Instant.ofEpochSecond(raw);
            }
            if (value.isTextual()) {
                String text = value.asText().trim();
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    return Instant.parse(text);
                } catch (DateTimeParseException ignored) {
                    // RailRadar uses ISO 8601 with the +05:30 offset.
                }
                try {
                    return OffsetDateTime.parse(text).toInstant();
                } catch (DateTimeParseException ignored) {
                    // Ignore invalid timestamps instead of inventing one.
                }
            }
        }
        return null;
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    // ------------------------------------------------------------------
    // Internal records
    // ------------------------------------------------------------------

    private record RouteStop(
            double sequence,
            String stationCode,
            String stationName,
            boolean halt,
            Double latitude,
            Double longitude,
            Instant scheduledArrival,
            Instant scheduledDeparture,
            Instant actualArrival,
            Instant actualDeparture,
            String provenance,
            Integer delayArrival,
            String status,
            Double distance,
            Double speedToNextKmph
    ) {
    }

    private record ParsedTrain(
            String number,
            String name,
            String providerStatus,
            boolean live,
            String trackingMode,
            Instant updatedAt,
            String locationStation,
            String locationStatus,
            Double sequence,
            Double latitude,
            Double longitude,
            Double speedKmph,
            Double distanceFromOriginKm,
            Double bearingDegrees,
            Integer delayMinutes,
            List<RouteStop> route
    ) {
    }

    private record QuotaState(String month, int used, boolean primed) {
    }

    /** Persisted provider cache. Keeps real values across application restarts. */
    private record CacheState(
            Instant lastRequestAt,
            Instant lastSuccessAt,
            Map<String, ParsedTrain> trains
    ) {
    }
}
