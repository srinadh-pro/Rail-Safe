package com.railsafe.live.service;

import com.railsafe.config.LiveProperties;
import com.railsafe.entity.RailwayGate;
import com.railsafe.live.dto.GateTrainResponse;
import com.railsafe.live.dto.LiveTrainResponse;
import com.railsafe.live.dto.ProviderStatusResponse;
import com.railsafe.live.dto.TrainTrackingResponse;
import com.railsafe.live.model.LiveDataMode;
import com.railsafe.live.model.LiveTrainStatus;
import com.railsafe.live.model.TrainFeed;
import com.railsafe.live.model.TrainFeedTrain;
import com.railsafe.live.provider.TrainDataProvider;
import com.railsafe.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Train Tracking Service.
 *
 * It only transforms data returned by the configured authorised provider. There is
 * no random train generator in this class. When the provider is unavailable the
 * response explicitly says so and contains no trains.
 */
@Service
public class TrainTrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrainTrackingService.class);

    private final TrainDataProvider provider;
    private final LiveProperties properties;

    private volatile TrainFeed cachedFeed;
    private volatile Instant cachedAt;

    public TrainTrackingService(TrainDataProvider provider, LiveProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    /** Cached provider response, refreshed at most every railsafe.live.cache-seconds. */
    public TrainFeed currentFeed() {
        long cacheSeconds = Math.max(0, properties.getCacheSeconds());
        TrainFeed current = cachedFeed;
        Instant currentAt = cachedAt;
        if (current != null && currentAt != null
                && currentAt.plusSeconds(cacheSeconds).isAfter(Instant.now())) {
            return current;
        }

        synchronized (this) {
            if (cachedFeed != null && cachedAt != null
                    && cachedAt.plusSeconds(cacheSeconds).isAfter(Instant.now())) {
                return cachedFeed;
            }
            try {
                cachedFeed = normalise(provider.fetch());
            } catch (RuntimeException ex) {
                log.warn("Train provider failed: {}", ex.getMessage());
                cachedFeed = unavailableFeed();
            }
            cachedAt = Instant.now();
            return cachedFeed;
        }
    }

    /**
     * Managed test feeds are never exposed through normal train or dashboard APIs.
     * They become an explicit unavailable response instead, so no test position or
     * ETA can be mistaken for live railway information.
     */
    private static TrainFeed normalise(TrainFeed feed) {
        if (feed == null || !feed.available() || feed.mode() == LiveDataMode.SIMULATION) {
            return unavailableFeed();
        }
        return feed;
    }

    private static TrainFeed unavailableFeed() {
        return TrainFeed.unavailable(
                "Unavailable",
                "Live train data is unavailable. Verify the crossing locally and obey physical barriers.");
    }

    public TrainTrackingResponse trackingForGate(RailwayGate gate) {
        TrainFeed feed = currentFeed();
        if (!feed.available()) {
            return new TrainTrackingResponse(
                    false,
                    true,
                    LiveDataMode.UNAVAILABLE,
                    LiveDataMode.UNAVAILABLE.label(),
                    feed.provider(),
                    feed.updatedAt(),
                    feed.message(),
                    List.of()
            );
        }

        Instant now = Instant.now();
        List<LiveTrainResponse> trains = new ArrayList<>();
        for (TrainFeedTrain train : feed.trains()) {
            LiveTrainResponse mapped = toResponse(gate, train, feed.mode(), now);
            if (mapped == null) {
                continue;
            }
            if (withinWindow(mapped, now)) {
                trains.add(mapped);
            }
        }

        trains.sort(Comparator
                .comparing(LiveTrainResponse::etaAtGate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LiveTrainResponse::trainNumber, Comparator.nullsLast(Comparator.naturalOrder())));

        String message = feed.message();
        if (feed.stale()) {
            message = message + " Data is older than " + properties.getFeedStaleSeconds()
                    + " seconds, so predictions are withheld.";
        }

        return new TrainTrackingResponse(
                true,
                feed.stale(),
                feed.mode(),
                feed.mode().label(),
                feed.provider(),
                feed.updatedAt(),
                message,
                List.copyOf(trains)
        );
    }

    /**
     * Flat projection of the trains matched to one gate, used by
     * {@code GET /api/gates/{id}/trains}. Shares the same provider feed and
     * visibility window as the dashboard so the two views cannot disagree.
     */
    public List<GateTrainResponse> trainsForGate(RailwayGate gate) {
        TrainFeed feed = currentFeed();
        if (!feed.available()) {
            return List.of();
        }

        Instant now = Instant.now();
        List<GateTrainResponse> rows = new ArrayList<>();
        for (TrainFeedTrain train : feed.trains()) {
            LiveTrainResponse mapped = toResponse(gate, train, feed.mode(), now);
            if (mapped == null || !withinWindow(mapped, now)) {
                continue;
            }
            rows.add(toGateTrainRow(gate, train, mapped, now));
        }

        rows.sort(Comparator
                .comparing(GateTrainResponse::etaMinutes, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(GateTrainResponse::trainNumber, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(rows);
    }

    public ProviderStatusResponse providerStatus() {
        TrainFeed feed = currentFeed();
        return new ProviderStatusResponse(
                provider.isConfigured(),
                feed.available(),
                feed.stale(),
                feed.provider(),
                feed.available() ? feed.mode() : LiveDataMode.UNAVAILABLE,
                feed.available() ? feed.mode().label() : LiveDataMode.UNAVAILABLE.label(),
                feed.updatedAt(),
                feed.message(),
                Math.max(5, properties.getCacheSeconds())
        );
    }

    private LiveTrainResponse toResponse(RailwayGate gate,
                                          TrainFeedTrain train,
                                          LiveDataMode mode,
                                          Instant now) {
        Double distance = null;
        if (train.latitude() != null && train.longitude() != null) {
            distance = GeoUtils.haversineKm(train.latitude(), train.longitude(),
                    gate.getLatitude(), gate.getLongitude());
        }

        boolean explicitGate = train.gateCode() != null && !train.gateCode().isBlank();
        if (explicitGate) {
            if (!train.gateCode().equalsIgnoreCase(gate.getGateCode())) {
                return null;
            }
        } else if (distance == null || distance > properties.getMatchRadiusKm()) {
            return null;
        }

        Instant eta = train.etaAtGate();
        String etaSource;
        boolean estimated = train.etaEstimated();

        if (mode == LiveDataMode.SIMULATION) {
            etaSource = "UNAVAILABLE";
            estimated = true;
        } else if (eta != null) {
            etaSource = mode == LiveDataMode.SCHEDULE ? "SCHEDULED"
                    : (train.etaEstimated() ? "DERIVED" : "LIVE");
        } else if (train.scheduledArrival() != null) {
            eta = train.scheduledArrival();
            etaSource = "SCHEDULED";
            estimated = false;
        } else if (distance != null && train.speedKmph() != null && train.speedKmph() > 5) {
            long seconds = Math.round(distance / train.speedKmph() * 3600.0);
            eta = now.plusSeconds(Math.max(0, seconds));
            etaSource = "DERIVED";
            estimated = true;
        } else {
            etaSource = "UNAVAILABLE";
            estimated = true;
        }

        LiveTrainStatus status = train.reportedStatus();
        if (status == null || status == LiveTrainStatus.UNKNOWN) {
            status = deriveStatus(eta, distance, now);
        }

        return new LiveTrainResponse(
                train.trainNumber(),
                train.trainName(),
                train.direction(),
                train.direction().label(),
                train.source(),
                train.destination(),
                train.currentLocation(),
                train.latitude(),
                train.longitude(),
                train.speedKmph(),
                train.gateCode(),
                distance == null ? null : round(distance, 2),
                eta,
                etaSource,
                estimated,
                train.scheduledArrival(),
                train.crossingStartedAt(),
                train.crossingEndsAt(),
                status,
                status.label(),
                train.observedAt(),
                train.provider()
        );
    }

    /** True when the ETA falls inside the dashboard's visible time window. */
    private boolean withinWindow(LiveTrainResponse mapped, Instant now) {
        if (mapped.etaAtGate() == null) {
            return true;
        }
        Instant earliest = now.minusSeconds(Math.max(30, properties.getPassedWindowSeconds())
                + Math.max(0, properties.getCrossingDurationSeconds()));
        Instant latest = now.plusSeconds(Math.max(60, properties.getLookaheadSeconds()));
        return !mapped.etaAtGate().isBefore(earliest) && !mapped.etaAtGate().isAfter(latest);
    }

    private GateTrainResponse toGateTrainRow(RailwayGate gate,
                                             TrainFeedTrain train,
                                             LiveTrainResponse mapped,
                                             Instant now) {
        Long etaMinutes = mapped.etaAtGate() == null
                ? null
                : Math.max(0, Math.round(
                        (mapped.etaAtGate().toEpochMilli() - now.toEpochMilli()) / 60000.0));
        return new GateTrainResponse(
                mapped.trainNumber(),
                mapped.trainName(),
                gate.getGateCode(),
                mapped.direction() == null ? null : mapped.direction().name(),
                train.source(),
                train.destination(),
                train.currentLocation(),
                mapped.distanceToGateKm(),
                mapped.speedKmph(),
                etaMinutes,
                mapped.status() == null ? null : mapped.status().name(),
                gateStatus(mapped.status())
        );
    }

    /** Expected barrier state for this train; not a verified sensor reading. */
    private static String gateStatus(LiveTrainStatus status) {
        if (status == null) {
            return "OPEN";
        }
        return switch (status) {
            case CROSSING -> "CLOSED";
            case NEAR_GATE -> "CLOSING";
            default -> "OPEN";
        };
    }

    private LiveTrainStatus deriveStatus(Instant eta, Double distance, Instant now) {
        if (eta == null) {
            return LiveTrainStatus.UNKNOWN;
        }
        if (eta.isBefore(now.minusSeconds(Math.max(60, properties.getCrossingDurationSeconds())))) {
            return LiveTrainStatus.PASSED;
        }
        if (!now.isBefore(eta.minusSeconds(properties.getCrossingWindowSeconds()))
                && !now.isAfter(eta.plusSeconds(properties.getCrossingDurationSeconds()))) {
            return LiveTrainStatus.CROSSING;
        }
        if (distance != null && distance <= properties.getNearGateKm()) {
            return LiveTrainStatus.NEAR_GATE;
        }
        if (eta.isBefore(now.plusSeconds(properties.getNearGateSeconds()))) {
            return LiveTrainStatus.NEAR_GATE;
        }
        return LiveTrainStatus.APPROACHING;
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
