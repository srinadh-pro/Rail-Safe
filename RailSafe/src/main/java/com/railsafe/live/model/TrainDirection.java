package com.railsafe.live.model;

/** Compass direction of a train as reported by the authorised feed. */
public enum TrainDirection {
    NORTH,
    SOUTH,
    EAST,
    WEST,
    UNKNOWN;

    public String label() {
        return switch (this) {
            case NORTH -> "North";
            case SOUTH -> "South";
            case EAST -> "East";
            case WEST -> "West";
            default -> "Not reported";
        };
    }

    public static TrainDirection parse(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "N", "NORTH", "NORTHBOUND", "UP" -> NORTH;
            case "S", "SOUTH", "SOUTHBOUND", "DOWN" -> SOUTH;
            case "E", "EAST", "EASTBOUND" -> EAST;
            case "W", "WEST", "WESTBOUND" -> WEST;
            default -> UNKNOWN;
        };
    }
}
