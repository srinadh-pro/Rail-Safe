package com.railsafe.entity;

/** Categories used by the notification UI. Values are stored as strings. */
public enum NotificationType {
    GATE_CLOSED,
    GATE_OPENED,
    GATE_UNKNOWN,
    GATE_STATUS_UPDATED,
    GATE_MAINTENANCE,
    TRAIN_APPROACHING,
    TRAIN_CROSSING,
    TRAIN_PASSED,
    GATE_CLOSING_SOON,
    GATE_OPENING_SOON,
    ALTERNATIVE_ROUTE,
    REPORT,
    SYSTEM
}
