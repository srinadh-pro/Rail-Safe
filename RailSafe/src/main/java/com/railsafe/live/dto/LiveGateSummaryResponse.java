package com.railsafe.live.dto;

import com.railsafe.entity.GateStatus;
import com.railsafe.live.model.LiveDataMode;

import java.time.Instant;

/** Compact row used by the gate selector on the React dashboard. */
public record LiveGateSummaryResponse(
        Long id,
        String gateCode,
        String name,
        String location,
        Double latitude,
        Double longitude,
        GateStatus reportedStatus,
        GateStatus verifiedStatus,
        String statusConfidence,
        boolean stale,
        boolean liveDataAvailable,
        LiveDataMode dataMode,
        int relevantTrains,
        int approachingTrains,
        Instant nextTrainEta,
        String nextTrainLabel,
        Instant expectedCloseAt,
        Instant expectedOpenAt
) {
}
