package com.railsafe.live.dto;

/**
 * Flat train record returned by {@code GET /api/gates/{id}/trains}.
 *
 * <p>This is a deliberately small projection of the richer tracking payload used
 * by the dashboard. Values come from the configured live provider (or the
 * explicitly enabled simulation provider); nothing is invented. Fields that the
 * provider does not report are {@code null} rather than guessed.
 *
 * <p>{@code gateStatus} is derived from the train's movement relative to the
 * crossing: a crossing train means CLOSED, a near train means CLOSING, anything
 * else is OPEN. It describes the expected barrier state for this train, not a
 * verified sensor reading.
 */
public record GateTrainResponse(
        String trainNumber,
        String trainName,
        String gateId,
        String direction,
        String source,
        String destination,
        String currentLocation,
        Double distanceFromGate,
        Double speed,
        Long etaMinutes,
        String status,
        String gateStatus
) {
}
