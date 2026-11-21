package com.railsafe.live.dto;

import com.railsafe.entity.DataClassification;

import java.time.Instant;
import java.util.List;

/** Static, verified train information with no simulated live position. */
public record TrainReferenceResponse(
        String trainNumber,
        String trainName,
        String sourceStation,
        String destinationStation,
        String currentStation,
        String nextStation,
        String direction,
        String scheduledDeparture,
        String scheduledArrival,
        String estimatedArrival,
        String runningStatus,
        Double speedKmph,
        List<String> associatedGates,
        DataClassification dataClassification,
        String referenceSource,
        String referenceUrl,
        Instant lastVerifiedAt,
        boolean liveDataAvailable,
        String note
) {
}
