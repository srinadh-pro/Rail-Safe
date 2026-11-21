package com.railsafe.live.dto;

import com.railsafe.live.model.LiveDataMode;

import java.time.Instant;
import java.util.List;

/**
 * available=false is the only thing the UI may show when no authorised provider is
 * configured. stale=true means data exists but predictions must not be trusted.
 */
public record TrainTrackingResponse(
        boolean available,
        boolean stale,
        LiveDataMode mode,
        String modeLabel,
        String provider,
        Instant updatedAt,
        String message,
        List<LiveTrainResponse> trains
) {
}
