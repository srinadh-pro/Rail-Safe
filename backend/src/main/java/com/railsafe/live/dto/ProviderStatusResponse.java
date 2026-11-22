package com.railsafe.live.dto;

import com.railsafe.live.model.LiveDataMode;

import java.time.Instant;

public record ProviderStatusResponse(
        boolean configured,
        boolean available,
        boolean stale,
        String provider,
        LiveDataMode mode,
        String modeLabel,
        Instant updatedAt,
        String message,
        long pollIntervalSeconds
) {
}
