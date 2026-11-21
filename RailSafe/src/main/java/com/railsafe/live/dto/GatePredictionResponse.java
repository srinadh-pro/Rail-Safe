package com.railsafe.live.dto;

import com.railsafe.entity.GateStatus;
import com.railsafe.live.model.LiveDataMode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The safety-critical distinction:
 *  - reportedStatus is what the database/staff last reported;
 *  - verifiedStatus is OPEN/CLOSED only when the status is fresh and verified by
 *    staff or an authorised sensor. Otherwise it is UNKNOWN.
 */
public record GatePredictionResponse(
        Long gateId,
        String gateCode,
        String gateName,
        GateStatus reportedStatus,
        GateStatus verifiedStatus,
        boolean statusConfirmed,
        String statusConfidence,
        String statusMessage,
        boolean stale,
        LocalDateTime lastUpdatedAt,
        boolean liveDataAvailable,
        boolean liveDataStale,
        LiveDataMode dataMode,
        Instant generatedAt,
        String dataMessage,
        LiveTrainResponse nextTrain,
        ClosingCountdownResponse closing,
        OpeningCountdownResponse opening,
        List<LiveTrainResponse> relevantTrains,
        String consistencyWarning
) {
}
