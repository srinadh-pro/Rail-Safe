package com.railsafe.service;

import com.railsafe.config.LiveProperties;
import com.railsafe.dto.SimulationSnapshotResponse;
import com.railsafe.entity.RailwayGate;
import com.railsafe.live.model.LiveTrainStatus;
import com.railsafe.live.model.TrainFeed;
import com.railsafe.live.model.TrainFeedTrain;
import com.railsafe.live.provider.SimulationTrainDataProvider;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.TrainTrackingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the explicitly labelled demo simulation shown on dashboards.
 *
 * This service reads only the managed synthetic dataset and the simulation
 * provider. It never writes operational rows, never changes real gate status and
 * never enables a LIVE indicator. Every timestamp is a projected demo timetable
 * slot, not a genuine train prediction.
 */
@Service
public class SimulationService {

    public static final String SIMULATION_LABEL = "DEMO SIMULATION — NOT LIVE RAILWAY DATA";
    public static final String SIMULATION_WARNING =
            "Train names are sample labels. Gate status, train pairings and expected times are fictional. " +
            "Never use this view for a crossing decision.";

    private final RailwayGateRepository gateRepository;
    private final TrainTrackingRepository trackingRepository;
    private final LiveProperties properties;

    public SimulationService(RailwayGateRepository gateRepository,
                             TrainTrackingRepository trackingRepository,
                             LiveProperties properties) {
        this.gateRepository = gateRepository;
        this.trackingRepository = trackingRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public SimulationSnapshotResponse snapshot() {
        Instant now = Instant.now();
        List<RailwayGate> demoGates = gateRepository.findAllByOrderByNameAsc().stream()
                .filter(DemoDataClassifier::isDemoGate)
                .toList();

        Set<String> demoGateCodes = demoGates.stream()
                .map(RailwayGate::getGateCode)
                .filter(code -> code != null && !code.isBlank())
                .map(SimulationService::normaliseCode)
                .collect(Collectors.toSet());

        // Instantiate the managed simulation provider directly: it is intentionally
        // not the bean used by operational train APIs, which hides simulation feeds.
        // The provider reads every managed tracking row, so this board must still be
        // restricted to the demo crossings it advertises.
        TrainFeed feed = new SimulationTrainDataProvider(trackingRepository, properties).fetch();
        Map<String, List<TrainFeedTrain>> trainsByGate = feed.trains().stream()
                .filter(train -> demoGateCodes.contains(normaliseCode(train.gateCode())))
                .collect(Collectors.groupingBy(
                        train -> normaliseCode(train.gateCode())));

        Map<String, String> gateNames = demoGates.stream()
                .filter(gate -> gate.getGateCode() != null)
                .collect(Collectors.toMap(
                        gate -> gate.getGateCode().trim().toUpperCase(Locale.ROOT),
                        gate -> gate.getName() == null ? gate.getGateCode() : gate.getName(),
                        (first, second) -> first));

        List<SimulationSnapshotResponse.Gate> gates = new ArrayList<>();
        long open = 0;
        long closed = 0;
        long closing = 0;
        for (int index = 0; index < demoGates.size(); index++) {
            RailwayGate gate = demoGates.get(index);
            String code = normaliseCode(gate.getGateCode());
            List<TrainFeedTrain> gateTrains = trainsByGate.getOrDefault(code, List.of());
            boolean crossing = gateTrains.stream()
                    .anyMatch(train -> train.reportedStatus() == LiveTrainStatus.CROSSING);
            boolean near = gateTrains.stream()
                    .anyMatch(train -> train.reportedStatus() == LiveTrainStatus.NEAR_GATE);
            String status = crossing ? "CLOSED" : near ? "CLOSING" : (index % 4 == 0 ? "CLOSED" : "OPEN");
            switch (status) {
                case "OPEN" -> open++;
                case "CLOSED" -> closed++;
                default -> closing++;
            }
            gates.add(new SimulationSnapshotResponse.Gate(
                    gate.getGateCode(),
                    gate.getName(),
                    gate.getLocation(),
                    status,
                    "SIMULATED"
            ));
        }

        List<SimulationSnapshotResponse.Train> trains = feed.trains().stream()
                .filter(train -> demoGateCodes.contains(normaliseCode(train.gateCode())))
                .sorted(Comparator
                        .comparingInt((TrainFeedTrain train) -> trainRank(train.reportedStatus()))
                        .thenComparing(train -> train.etaAtGate() == null
                                ? Instant.MAX : train.etaAtGate()))
                .map(train -> {
                    String code = normaliseCode(train.gateCode());
                    Instant arrival = train.etaAtGate();
                    Instant departure = train.crossingEndsAt() == null
                            ? (arrival == null ? null : arrival.plusSeconds(240))
                            : train.crossingEndsAt();
                    return new SimulationSnapshotResponse.Train(
                            train.trainNumber(),
                            train.trainName(),
                            train.gateCode(),
                            gateNames.getOrDefault(code, train.gateCode()),
                            train.reportedStatus() == null ? "UNKNOWN" : train.reportedStatus().name(),
                            arrival,
                            departure,
                            secondsBetween(now, arrival),
                            secondsBetween(now, departure),
                            "SIMULATED"
                    );
                })
                .toList();

        long approaching = trains.stream()
                .filter(train -> "APPROACHING".equals(train.status()) || "NEAR_GATE".equals(train.status()))
                .map(SimulationSnapshotResponse.Train::trainNumber)
                .distinct()
                .count();
        long crossingTrains = trains.stream()
                .filter(train -> "CROSSING".equals(train.status()))
                .map(train -> train.trainNumber() + "|" + train.gateCode())
                .distinct()
                .count();

        SimulationSnapshotResponse.Counts counts = new SimulationSnapshotResponse.Counts(
                gates.size(),
                open,
                closed,
                closing,
                approaching,
                closed + closing + crossingTrains
        );

        return new SimulationSnapshotResponse(
                now,
                SIMULATION_LABEL,
                "SIMULATION",
                false,
                SIMULATION_WARNING,
                counts,
                List.copyOf(gates),
                List.copyOf(trains)
        );
    }

    private static String normaliseCode(String code) {
        return code == null || code.isBlank() ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /** Active demo movements first; already-passed sample rows must not fill the board. */
    private static int trainRank(LiveTrainStatus status) {
        if (status == null) return 4;
        return switch (status) {
            case APPROACHING -> 0;
            case NEAR_GATE -> 1;
            case CROSSING -> 2;
            case PASSED -> 3;
            case UNKNOWN -> 4;
        };
    }

    private static long secondsBetween(Instant from, Instant to) {
        if (from == null || to == null) return 0L;
        return Math.max(0L, to.getEpochSecond() - from.getEpochSecond());
    }
}
