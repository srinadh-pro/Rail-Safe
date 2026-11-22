package com.railsafe.live.model;

/** How trustworthy the live feed is. */
public enum LiveDataMode {
    LIVE,
    SCHEDULE,
    /** Independent third-party provider. Never presented as official railway data. */
    THIRD_PARTY,
    SIMULATION,
    UNAVAILABLE;

    public String label() {
        return switch (this) {
            case LIVE -> "Live provider";
            case SCHEDULE -> "Scheduled data";
            case THIRD_PARTY -> "Third-party live data - not official Indian Railways";
            case SIMULATION -> "Unavailable";
            default -> "Unavailable";
        };
    }
}
