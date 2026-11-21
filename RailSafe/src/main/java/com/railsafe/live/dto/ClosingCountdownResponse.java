package com.railsafe.live.dto;

import java.time.Instant;

/** Closing countdown. Always an estimate, never a guarantee. */
public record ClosingCountdownResponse(
        boolean available,
        Instant estimatedCloseAt,
        long secondsUntilClose,
        String message,
        LiveTrainResponse train,
        boolean estimated
) {
}
