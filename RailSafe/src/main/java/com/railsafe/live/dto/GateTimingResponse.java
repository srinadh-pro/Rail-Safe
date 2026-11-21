package com.railsafe.live.dto;

import com.railsafe.entity.GateStatus;

import java.time.Instant;
import java.util.List;

/**
 * Countdown payload for one crossing.
 *
 * Both countdowns are expressed as an absolute target timestamp plus the number of
 * seconds remaining at generatedAt. The browser must count down towards the target
 * and never treat these values as measured railway data.
 */
public record GateTimingResponse(
        Long gateId,
        String gateCode,
        String gateName,
        GateStatus reportedStatus,
        GateStatus verifiedStatus,
        String statusConfidence,
        boolean simulation,
        boolean syntheticData,
        Instant generatedAt,
        Instant estimatedCloseAt,
        Long closingCountdownSeconds,
        String closingMessage,
        Instant estimatedOpenAt,
        Long openingCountdownSeconds,
        String openingMessage,
        int approachingTrains,
        int trainsWaiting,
        LiveTrainResponse nextTrain,
        List<LiveTrainResponse> trains,
        long preClosingBufferSeconds,
        long crossingDurationSeconds,
        long safetyBufferSeconds,
        String disclaimer
) {
}
