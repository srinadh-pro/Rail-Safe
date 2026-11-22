package com.railsafe.live.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railsafe.live.model.LiveDataMode;
import com.railsafe.live.model.LiveTrainStatus;
import com.railsafe.live.model.TrainDirection;
import com.railsafe.live.model.TrainFeed;
import com.railsafe.live.model.TrainFeedTrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an authorised provider over HTTP and maps its documented JSON shape into
 * RailSafe's internal model. RailSafe does not derive positions; it only reads them.
 *
 * Expected response (field names are case-sensitive):
 * {
 *   "provider": "Provider name",
 *   "mode": "LIVE",                         // LIVE or SCHEDULE (never SIMULATION)
 *   "updatedAt": "2026-11-05T15:00:00Z",
 *   "trains": [{
 *     "trainNumber": "12727",
 *     "trainName": "Godavari Express",
 *     "direction": "NORTH",
 *     "latitude": 17.39,
 *     "longitude": 78.55,
 *     "speedKmph": 62,
 *     "gateCode": "RS-DEMO-001",
 *     "etaAtGate": "2026-11-05T15:09:00Z",
 *     "etaEstimated": false,
 *     "crossingStartedAt": null,
 *     "crossingEndsAt": null,
 *     "status": "APPROACHING",
 *     "observedAt": "2026-11-05T15:00:00Z"
 *   }]
 * }
 *
 * If etaAtGate is missing, TrainTrackingService may derive a straight-line estimate
 * from a supplied position and speed. Such an ETA is always labelled ESTIMATED.
 */
public class HttpJsonTrainDataProvider implements TrainDataProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpJsonTrainDataProvider.class);

    private final String providerName;
    private final String feedUrl;
    private final String apiKey;
    private final String apiKeyHeader;
    private final long staleSeconds;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpJsonTrainDataProvider(String providerName,
                                     String feedUrl,
                                     String apiKey,
                                     String apiKeyHeader,
                                     long staleSeconds,
                                     RestClient restClient,
                                     ObjectMapper objectMapper) {
        this.providerName = providerName;
        this.feedUrl = feedUrl;
        this.apiKey = apiKey;
        this.apiKeyHeader = apiKeyHeader;
        this.staleSeconds = staleSeconds;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return providerName;
    }

    @Override
    public TrainFeed fetch() {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(feedUrl);
            if (apiKey != null && !apiKey.isBlank()) {
                request = request.header(apiKeyHeader, apiKey);
            }

            String body = request.retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                return TrainFeed.unavailable(providerName,
                        "Live train data unavailable: the authorised provider returned an empty response.");
            }
            JsonNode root = objectMapper.readTree(body);
            return parse(root, Instant.now());
        } catch (Exception ex) {
            log.warn("Authorised train feed could not be read: {}", ex.getMessage());
            return TrainFeed.unavailable(providerName,
                    "Live train data unavailable: the authorised provider could not be reached or returned invalid data.");
        }
    }

    private TrainFeed parse(JsonNode root, Instant now) {
        JsonNode trainsNode = root.isArray() ? root : root.path("trains");
        String reportedProvider = text(root, "provider");
        String effectiveProvider = reportedProvider == null ? providerName : reportedProvider;

        String modeText = text(root, "mode");
        LiveDataMode mode = "SCHEDULE".equalsIgnoreCase(modeText)
                ? LiveDataMode.SCHEDULE
                : LiveDataMode.LIVE;

        if ("SIMULATION".equalsIgnoreCase(modeText)) {
            return TrainFeed.unavailable(effectiveProvider,
                    "The HTTP provider may not supply simulation data. Enable the explicit simulation profile instead.");
        }

        Instant updatedAt = instant(root, "updatedAt", "generatedAt", "observedAt");
        boolean stale = updatedAt == null
                || updatedAt.isBefore(now.minusSeconds(Math.max(30, staleSeconds)));
        String message = updatedAt == null
                ? "Provider data has no timestamp, so predictions are withheld. Times shown are not guaranteed."
                : "Authorised provider data. Times are estimates unless the provider marks them as live.";

        List<TrainFeedTrain> trains = new ArrayList<>();
        if (trainsNode != null && trainsNode.isArray()) {
            for (JsonNode node : trainsNode) {
                String number = text(node, "trainNumber", "number");
                String name = text(node, "trainName", "name");
                if ((number == null || number.isBlank()) && (name == null || name.isBlank())) {
                    continue;
                }
                if (name == null || name.isBlank()) {
                    name = "Service " + number;
                }
                if (number == null || number.isBlank()) {
                    number = "Not reported";
                }
                trains.add(new TrainFeedTrain(
                        number,
                        name,
                        TrainDirection.parse(text(node, "direction")),
                        decimal(node, "latitude", "lat"),
                        decimal(node, "longitude", "lng", "lon"),
                        decimal(node, "speedKmph", "speed"),
                        text(node, "gateCode", "gate"),
                        instant(node, "etaAtGate", "arrivalAtGate", "eta"),
                        instant(node, "scheduledArrival", "scheduleArrival", "scheduledAt"),
                        bool(node, "etaEstimated", false),
                        instant(node, "crossingStartedAt"),
                        instant(node, "crossingEndsAt"),
                        LiveTrainStatus.parse(text(node, "status", "trainStatus")),
                        text(node, "source", "from"),
                        text(node, "destination", "to"),
                        text(node, "currentLocation", "location"),
                        instant(node, "observedAt", "updatedAt"),
                        effectiveProvider
                ));
            }
        }

        if (trains.isEmpty()) {
            message = "The authorised provider returned no train movements for this period.";
        }

        return new TrainFeed(true, mode, effectiveProvider, updatedAt == null ? now : updatedAt,
                message, List.copyOf(trains), stale);
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
                    // Try the next supported representation.
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
}
