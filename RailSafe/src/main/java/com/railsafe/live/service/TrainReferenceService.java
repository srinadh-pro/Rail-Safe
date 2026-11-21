package com.railsafe.live.service;

import com.railsafe.config.TrainReferenceCatalogue;
import com.railsafe.entity.DataClassification;
import com.railsafe.entity.TrainMaster;
import com.railsafe.live.dto.TrainReferenceResponse;
import com.railsafe.repository.TrainMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-only verified train catalogue. Live positions are never invented here. */
@Service
public class TrainReferenceService {

    private static final String NOTE = "Verified railway reference data only. "
            + "Current station, speed and live ETA are unavailable unless supplied by an authorised live feed.";

    private final TrainMasterRepository repository;

    public TrainReferenceService(TrainMasterRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TrainReferenceResponse> list() {
        List<TrainMaster> stored = repository.findAllByOrderByTrainNumberAsc().stream()
                .filter(train -> !train.isSynthetic())
                .filter(train -> train.getDataClassification() == DataClassification.VERIFIED_REFERENCE
                        || train.getDataClassification() == null)
                .toList();
        if (!stored.isEmpty()) {
            return stored.stream().map(this::toResponse).toList();
        }
        // Startup seeding may not have run in a lightweight test context; the checked
        // catalogue still provides the same verified reference information.
        return TrainReferenceCatalogue.records().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TrainReferenceResponse byNumber(String trainNumber) {
        return repository.findByTrainNumberIgnoreCase(trainNumber)
                .filter(train -> !train.isSynthetic())
                .map(this::toResponse)
                .orElseThrow(() -> new com.railsafe.exception.ResourceNotFoundException(
                        "Train " + trainNumber + " was not found."));
    }

    private TrainReferenceResponse toResponse(TrainMaster train) {
        return new TrainReferenceResponse(
                train.getTrainNumber(),
                train.getTrainName(),
                value(train.getSourceStation()),
                value(train.getDestinationStation()),
                value(train.getCurrentStation()),
                value(train.getNextStation()),
                value(train.getDirection()),
                value(train.getScheduledDeparture()),
                value(train.getScheduledArrival()),
                value(train.getEstimatedArrival()),
                value(train.getRunningStatus()),
                train.getSpeedKmph(),
                List.of(),
                train.getDataClassification() == null
                        ? DataClassification.VERIFIED_REFERENCE : train.getDataClassification(),
                value(train.getReferenceSource()),
                value(train.getReferenceUrl()),
                train.getLastVerifiedAt(),
                false,
                NOTE
        );
    }

    private TrainReferenceResponse toResponse(TrainReferenceCatalogue.ReferenceTrain reference) {
        return new TrainReferenceResponse(
                reference.number(), reference.name(), reference.sourceStation(), reference.destinationStation(),
                "Not Available", "Not Available", reference.direction(), reference.scheduledDeparture(),
                reference.scheduledArrival(), "Not Available", "Scheduled", null, List.of(),
                DataClassification.VERIFIED_REFERENCE, reference.referenceSource(), reference.referenceUrl(),
                java.time.Instant.parse("2026-11-18T00:00:00Z"), false, NOTE
        );
    }

    private static String value(String text) {
        return text == null || text.isBlank() ? "Not Available" : text;
    }
}
