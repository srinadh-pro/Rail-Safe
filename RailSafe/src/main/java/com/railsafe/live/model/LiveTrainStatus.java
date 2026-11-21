package com.railsafe.live.model;

/** Movement state of a train relative to a crossing. */
public enum LiveTrainStatus {
    APPROACHING,
    NEAR_GATE,
    CROSSING,
    PASSED,
    UNKNOWN;

    public String label() {
        return switch (this) {
            case APPROACHING -> "Approaching";
            case NEAR_GATE -> "Near gate";
            case CROSSING -> "Crossing";
            case PASSED -> "Passed";
            default -> "Unknown";
        };
    }

    public static LiveTrainStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return LiveTrainStatus.valueOf(value.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
