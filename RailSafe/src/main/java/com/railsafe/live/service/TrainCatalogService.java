package com.railsafe.live.service;

import com.railsafe.entity.DataClassification;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.TrainMaster;
import com.railsafe.entity.TrainTrackingRecord;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.live.dto.LiveTrainResponse;
import com.railsafe.live.dto.TrainDetailResponse;
import com.railsafe.live.dto.TrainTrackingResponse;
import com.railsafe.live.model.LiveDataMode;
import com.railsafe.live.model.TrainFeed;
import com.railsafe.live.model.TrainFeedTrain;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.TrainMasterRepository;
import com.railsafe.repository.TrainTrackingRepository;
import com.railsafe.service.DemoDataClassifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Train catalogue lookup for /api/trains/{trainNumber}.
 *
 * The catalogue is synthetic demo data. Assignments are read from the simulated
 * provider so the returned ETA/status match what the dashboard is showing right now.
 */
@Service
public class TrainCatalogService {

    private final TrainMasterRepository trainRepository;
    private final TrainTrackingRepository trackingRepository;
    private final TrainTrackingService trackingService;
    private final RailwayGateRepository gateRepository;

    public TrainCatalogService(TrainMasterRepository trainRepository,
                               TrainTrackingRepository trackingRepository,
                               TrainTrackingService trackingService,
                               RailwayGateRepository gateRepository) {
        this.trainRepository = trainRepository;
        this.trackingRepository = trackingRepository;
        this.trackingService = trackingService;
        this.gateRepository = gateRepository;
    }

    @Transactional(readOnly = true)
    public TrainDetailResponse detail(String trainNumber) {
        if (trainNumber == null || trainNumber.isBlank()) {
            throw new ResourceNotFoundException("A train number is required.");
        }

        String requested = trainNumber.trim();

        // When a third-party provider is active, never mix its live values with the
        // synthetic demo assignments. Report the real train, clearly labelled.
        TrainFeed feed = trackingService.currentFeed();
        if (feed.available() && feed.mode() == LiveDataMode.THIRD_PARTY) {
            TrainDetailResponse live = thirdPartyDetail(requested, feed);
            if (live != null) {
                return live;
            }
        }

        TrainMaster master = trainRepository.findByTrainNumberIgnoreCase(requested)
                .filter(found -> !DemoDataClassifier.isDemoTrain(found))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Train " + trainNumber + " was not found."));

        // Verified reference rows must never inherit simulated gate assignments.
        // When no authorised live feed exists, return reference information only.
        if (master.getDataClassification() == DataClassification.VERIFIED_REFERENCE
                && !feed.available()) {
            return new TrainDetailResponse(
                    master.getTrainNumber(),
                    master.getTrainName(),
                    master.getDirection(),
                    master.getZone(),
                    false,
                    "Verified railway reference information. Live data unavailable; no current "
                            + "station, position, speed or ETA is presented.",
                    List.of()
            );
        }

        List<TrainDetailResponse.GateAssignment> assignments = new ArrayList<>();
        for (TrainTrackingRecord record : trackingRepository.findByTrainNumberWithGate(master.getTrainNumber())) {
            RailwayGate gate = record.getGate();
            if (gate == null || DemoDataClassifier.isDemoTracking(record)) {
                continue;
            }

            TrainTrackingResponse tracking = trackingService.trackingForGate(gate);
            LiveTrainResponse live = tracking.trains().stream()
                    .filter(train -> train.trainNumber() != null
                            && train.trainNumber().equalsIgnoreCase(master.getTrainNumber()))
                    .findFirst()
                    .orElse(null);

            if (live != null) {
                assignments.add(new TrainDetailResponse.GateAssignment(
                        gate.getId(),
                        gate.getGateCode(),
                        gate.getName(),
                        gate.getLocation(),
                        live.status() == null ? "UNKNOWN" : live.status().name(),
                        live.etaAtGate(),
                        live.distanceToGateKm(),
                        live.speedKmph(),
                        live.direction() == null ? record.getDirection() : live.direction().name(),
                        live.observedAt()
                ));
            } else {
                assignments.add(new TrainDetailResponse.GateAssignment(
                        gate.getId(),
                        gate.getGateCode(),
                        gate.getName(),
                        gate.getLocation(),
                        record.getTrainStatus() == null ? "UNKNOWN" : record.getTrainStatus().name(),
                        null,
                        record.getDistanceKm(),
                        record.getSpeedKmph(),
                        record.getDirection(),
                        record.getObservedAt()
                ));
            }
        }

        return new TrainDetailResponse(
                master.getTrainNumber(),
                master.getTrainName(),
                master.getDirection(),
                master.getZone(),
                master.isSynthetic(),
                "Authorised train details. ETAs and estimated states are advisory only; "
                        + "verify locally and obey physical barriers.",
                List.copyOf(assignments)
        );
    }

    /**
     * Builds a response from the third-party feed. Returns null when the provider
     * does not currently report the requested train, so the caller can fall back
     * to the explicitly labelled synthetic catalogue.
     */
    private TrainDetailResponse thirdPartyDetail(String trainNumber, TrainFeed feed) {
        List<TrainFeedTrain> rows = feed.trains().stream()
                .filter(train -> train.trainNumber() != null
                        && train.trainNumber().equalsIgnoreCase(trainNumber))
                .toList();
        if (rows.isEmpty()) {
            return null;
        }

        List<TrainDetailResponse.GateAssignment> assignments = new ArrayList<>();
        for (TrainFeedTrain row : rows) {
            if (row.gateCode() == null || row.gateCode().isBlank()) {
                continue;
            }
            RailwayGate gate = gateRepository.findByGateCodeIgnoreCase(row.gateCode()).orElse(null);
            if (gate == null || DemoDataClassifier.isDemoGate(gate)) {
                continue;
            }

            // Reuse the normal gate mapping so status and distance are derived in
            // exactly the same way as the dashboard.
            TrainTrackingResponse tracking = trackingService.trackingForGate(gate);
            LiveTrainResponse live = tracking.trains().stream()
                    .filter(train -> train.trainNumber() != null
                            && train.trainNumber().equalsIgnoreCase(trainNumber))
                    .findFirst()
                    .orElse(null);

            assignments.add(new TrainDetailResponse.GateAssignment(
                    gate.getId(),
                    gate.getGateCode(),
                    gate.getName(),
                    gate.getLocation(),
                    live == null || live.status() == null
                            ? (row.reportedStatus() == null ? "UNKNOWN" : row.reportedStatus().name())
                            : live.status().name(),
                    live == null ? row.etaAtGate() : live.etaAtGate(),
                    live == null ? null : live.distanceToGateKm(),
                    live == null ? row.speedKmph() : live.speedKmph(),
                    live == null || live.direction() == null
                            ? (row.direction() == null ? "UNKNOWN" : row.direction().name())
                            : live.direction().name(),
                    live == null ? row.observedAt() : live.observedAt()
            ));
        }

        TrainFeedTrain first = rows.get(0);
        Instant observedAt = first.observedAt();
        String message = "Third-party live data from " + feed.provider()
                + " - not official Indian Railways, IRCTC or NTES data."
                + (assignments.isEmpty()
                        ? " No configured RailSafe crossing is currently within range of this train."
                        : " Gate ETAs are derived estimates. Observed "
                                + (observedAt == null ? "time not reported" : observedAt.toString()) + ".");

        return new TrainDetailResponse(
                first.trainNumber(),
                first.trainName(),
                first.direction() == null ? "UNKNOWN" : first.direction().name(),
                null,
                false,
                message,
                List.copyOf(assignments)
        );
    }
}
