package com.railsafe.live.dto;

import java.time.Instant;
import java.util.List;

/**
 * A synthetic train catalogue entry and its simulated assignments to crossings.
 * Real train numbers/names are sample labels only; the assignments are fictional.
 */
public record TrainDetailResponse(
        String trainNumber,
        String trainName,
        String direction,
        String zone,
        boolean synthetic,
        String source,
        List<GateAssignment> assignments
) {

    public record GateAssignment(
            Long gateId,
            String gateCode,
            String gateName,
            String location,
            String trainStatus,
            Instant etaAtGate,
            Double distanceKm,
            Double speedKmph,
            String direction,
            Instant observedAt
    ) {
    }
}
