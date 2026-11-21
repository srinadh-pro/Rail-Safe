package com.railsafe.entity;

/**
 * Stored gate operating state.
 *
 * {@code UNKNOWN} and {@code AWAITING_VERIFICATION} are retained for legacy and
 * unverified records, but the public API maps both to {@code AWAITING_VERIFICATION}
 * so {@code UNKNOWN} is never displayed as a gate status.
 */
public enum GateStatus {
    OPEN,
    CLOSED,
    CLOSING,
    OPENING,
    MAINTENANCE,
    UNKNOWN,
    AWAITING_VERIFICATION
}
