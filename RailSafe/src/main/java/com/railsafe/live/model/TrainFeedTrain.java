package com.railsafe.live.model;

import java.time.Instant;

/**
 * One train movement as supplied by an authorised provider (or by the explicitly
 * enabled simulation provider). Nullable values are kept nullable: RailSafe does
 * not fill in missing live data.
 */
public record TrainFeedTrain(
        String trainNumber,
        String trainName,
        TrainDirection direction,
        Double latitude,
        Double longitude,
        Double speedKmph,
        String gateCode,
        Instant etaAtGate,
        Instant scheduledArrival,
        boolean etaEstimated,
        Instant crossingStartedAt,
        Instant crossingEndsAt,
        LiveTrainStatus reportedStatus,
        String source,
        String destination,
        String currentLocation,
        Instant observedAt,
        String provider
) {
}
