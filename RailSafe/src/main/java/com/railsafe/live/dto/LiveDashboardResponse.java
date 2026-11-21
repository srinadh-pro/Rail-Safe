package com.railsafe.live.dto;

import java.time.Instant;

/** Combined payload for one dashboard poll. */
public record LiveDashboardResponse(
        Instant generatedAt,
        long pollIntervalSeconds,
        com.railsafe.dto.GateResponse gate,
        TrainTrackingResponse tracking,
        GatePredictionResponse prediction,
        ProviderStatusResponse provider
) {
}
