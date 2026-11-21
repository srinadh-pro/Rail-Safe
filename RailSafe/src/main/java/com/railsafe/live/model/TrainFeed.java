package com.railsafe.live.model;

import java.time.Instant;
import java.util.List;

/**
 * A single provider response. available=false means RailSafe has no authorised
 * train data and must show "Live train data unavailable".
 */
public record TrainFeed(
        boolean available,
        LiveDataMode mode,
        String provider,
        Instant updatedAt,
        String message,
        List<TrainFeedTrain> trains,
        boolean stale
) {
    public static TrainFeed unavailable(String provider, String message) {
        return new TrainFeed(false, LiveDataMode.UNAVAILABLE, provider, Instant.now(), message, List.of(), true);
    }
}
