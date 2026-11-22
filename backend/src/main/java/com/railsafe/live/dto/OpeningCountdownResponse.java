package com.railsafe.live.dto;

import java.time.Instant;

/** Opening countdown. Always an estimate and never a promise of reopening. */
public record OpeningCountdownResponse(
        boolean available,
        Instant estimatedOpenAt,
        long secondsUntilOpen,
        String message,
        LiveTrainResponse train,
        int trainsWaiting,
        boolean estimated,
        String basis
) {
}
