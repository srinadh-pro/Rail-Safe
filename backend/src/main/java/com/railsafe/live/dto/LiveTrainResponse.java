package com.railsafe.live.dto;

import com.railsafe.live.model.LiveTrainStatus;
import com.railsafe.live.model.TrainDirection;

import java.time.Instant;

/** A train as presented to the dashboard. Every timestamp is an estimate unless etaSource says LIVE. */
public record LiveTrainResponse(
        String trainNumber,
        String trainName,
        TrainDirection direction,
        String directionLabel,
        String source,
        String destination,
        String currentLocation,
        Double latitude,
        Double longitude,
        Double speedKmph,
        String gateCode,
        Double distanceToGateKm,
        Instant etaAtGate,
        String etaSource,
        boolean estimated,
        Instant scheduledArrival,
        Instant crossingStartedAt,
        Instant crossingEndsAt,
        LiveTrainStatus status,
        String statusLabel,
        Instant observedAt,
        String provider
) {
}
