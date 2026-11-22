package com.railsafe.dto;

import java.time.Instant;
import java.util.List;

/**
 * Clearly labelled demo-simulation snapshot.
 *
 * This response is deliberately separate from the operational LiveCountsResponse:
 * it must never set liveDataAvailable=true and must always carry a SIMULATION mode
 * and an explicit warning. Gates, train names and ETAs in this payload are fictional.
 */
public record SimulationSnapshotResponse(
        Instant generatedAt,
        String label,
        String dataMode,
        boolean liveDataAvailable,
        String warning,
        Counts counts,
        List<Gate> gates,
        List<Train> trains
) {
    public record Counts(long totalGates, long openGates, long closedGates, long closingGates,
                         long approachingTrains, long activeCrossings) {
    }

    public record Gate(String gateCode, String name, String location, String status,
                       String dataClassification) {
    }

    public record Train(String trainNumber, String trainName, String gateCode, String gateName,
                        String status, Instant expectedArrivalAt, Instant expectedDepartureAt,
                        long secondsUntilArrival, long secondsUntilDeparture,
                        String dataClassification) {
    }
}
