package com.railsafe.live.provider;

import com.railsafe.config.LiveProperties;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.TrainMaster;
import com.railsafe.entity.TrainTrackingRecord;
import com.railsafe.live.model.LiveDataMode;
import com.railsafe.live.model.LiveTrainStatus;
import com.railsafe.live.model.TrainDirection;
import com.railsafe.live.model.TrainFeed;
import com.railsafe.live.model.TrainFeedTrain;
import com.railsafe.repository.TrainTrackingRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Explicitly enabled managed test provider.
 *
 * This is the ONLY place where RailSafe creates train movements. Normal-facing
 * services replace this feed with an unavailable response, and no row produced here
 * is ever written to the authorised train_snapshots audit table.
 *
 * Movement model
 * --------------
 * The synthetic dataset stores one row per (gate, train) with a slot offset inside a
 * repeating cycle. On every request the offset is projected onto an absolute ETA
 * target for the current cycle, so:
 *   - the countdown always runs towards a timestamp, never a decrementing value;
 *   - a page refresh cannot reset the countdown;
 *   - several trains can approach the same crossing at once;
 *   - the schedule is deterministic and survives a server restart.
 *
 * The train names/numbers are real Indian Railways services used as sample labels.
 * The dataset does NOT claim that those services actually serve these crossings.
 */
public class SimulationTrainDataProvider implements TrainDataProvider {

    private final TrainTrackingRepository trackingRepository;
    private final LiveProperties properties;

    public SimulationTrainDataProvider(TrainTrackingRepository trackingRepository,
                                       LiveProperties properties) {
        this.trackingRepository = trackingRepository;
        this.properties = properties;
    }

    @Override
    public String providerId() {
        return "RailSafe train feed";
    }

    @Override
    public TrainFeed fetch() {
        Instant now = Instant.now();
        List<TrainTrackingRecord> records = trackingRepository.findAllWithGateAndTrain();

        if (records.isEmpty()) {
            return new TrainFeed(
                    true,
                    LiveDataMode.SIMULATION,
                    providerId(),
                    now,
                    "Train data is unavailable. Verify the crossing locally and obey physical barriers.",
                    List.of(),
                    false
            );
        }

        long cycle = Math.max(600L, properties.getSimulationCycleSeconds());
        long closeLead = Math.max(0L, properties.getCloseLeadSeconds());
        long crossingWindow = Math.max(30L, properties.getCrossingWindowSeconds());
        long crossingDuration = Math.max(60L, properties.getCrossingDurationSeconds());
        long visibleTail = crossingDuration + Math.max(0L, properties.getPassedWindowSeconds());

        List<TrainFeedTrain> trains = new ArrayList<>(records.size());
        for (TrainTrackingRecord record : records) {
            RailwayGate gate = record.getGate();
            TrainMaster master = record.getTrain();
            if (gate == null || master == null) {
                continue;
            }

            Instant eta = projectEta(now, record.getSlotOffsetSeconds(), cycle, visibleTail);
            Instant barrierClose = eta.minusSeconds(closeLead);
            Instant crossingStart = eta.minusSeconds(crossingWindow);
            Instant crossingEnd = eta.plusSeconds(crossingDuration);

            LiveTrainStatus status;
            if (now.isAfter(crossingEnd)) {
                status = LiveTrainStatus.PASSED;
            } else if (!now.isBefore(crossingStart)) {
                status = LiveTrainStatus.CROSSING;
            } else if (!now.isBefore(barrierClose)) {
                status = LiveTrainStatus.NEAR_GATE;
            } else {
                status = LiveTrainStatus.APPROACHING;
            }

            TrainDirection direction = TrainDirection.parse(record.getDirection());
            if (direction == TrainDirection.UNKNOWN) {
                direction = TrainDirection.parse(master.getDirection());
            }
            if (direction == TrainDirection.UNKNOWN) {
                direction = TrainDirection.EAST;
            }

            double speed = record.getSpeedKmph() == null || record.getSpeedKmph() <= 0
                    ? 55.0
                    : record.getSpeedKmph();
            long remaining = Math.max(0L, eta.getEpochSecond() - now.getEpochSecond());
            double distanceKm = Math.min(30.0, speed * remaining / 3600.0);
            if (status == LiveTrainStatus.CROSSING || status == LiveTrainStatus.PASSED) {
                distanceKm = 0.02;
            } else if (distanceKm < 0.05) {
                distanceKm = 0.05;
            }

            double[] position = offsetPosition(gate, direction, distanceKm);

            trains.add(new TrainFeedTrain(
                    master.getTrainNumber(),
                    master.getTrainName(),
                    direction,
                    round(position[0], 5),
                    round(position[1], 5),
                    round(speed, 1),
                    gate.getGateCode(),
                    eta,
                    null,
                    true,
                    barrierClose,
                    crossingEnd,
                    status,
                    null,
                    null,
                    null,
                    now,
                    providerId()
            ));
        }

        return new TrainFeed(
                true,
                LiveDataMode.SIMULATION,
                providerId(),
                now,
                "Managed test feed with "
                        + records.size() + " gate/train assignments across "
                        + trains.stream().map(TrainFeedTrain::gateCode).distinct().count()
                        + " crossings. This feed is not exposed to normal views.",
                List.copyOf(trains),
                false
        );
    }

    /** Projects a cycle slot onto the next absolute ETA that is still visible. */
    static Instant projectEta(Instant now, long offsetSeconds, long cycleSeconds, long visibleTailSeconds) {
        long cycle = Math.max(600L, cycleSeconds);
        long offset = Math.floorMod(offsetSeconds, cycle);
        long epoch = now.getEpochSecond();
        long cycleStart = Math.floorDiv(epoch, cycle) * cycle;
        Instant eta = Instant.ofEpochSecond(cycleStart + offset);
        while (eta.plusSeconds(Math.max(0L, visibleTailSeconds)).isBefore(now)) {
            eta = eta.plusSeconds(cycle);
        }
        return eta;
    }

    /** Approximate train position along the approach direction, in kilometres. */
    private static double[] offsetPosition(RailwayGate gate, TrainDirection direction, double distanceKm) {
        double bearing = switch (direction) {
            case NORTH -> 0.0;
            case EAST -> 90.0;
            case SOUTH -> 180.0;
            case WEST -> 270.0;
            default -> 90.0;
        };
        double radians = Math.toRadians(bearing);
        double lat = gate.getLatitude() == null ? 17.3850 : gate.getLatitude();
        double lng = gate.getLongitude() == null ? 78.4867 : gate.getLongitude();
        double latOffset = (distanceKm / 111.32) * Math.cos(radians);
        double cosLat = Math.max(0.2, Math.cos(Math.toRadians(lat)));
        double lngOffset = (distanceKm / (111.32 * cosLat)) * Math.sin(radians);
        return new double[]{lat + latOffset, lng + lngOffset};
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
