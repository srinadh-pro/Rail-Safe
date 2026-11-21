package com.railsafe.entity;

/**
 * Where a status came from. RailSafe never invents a live Indian Railways feed.
 * DEMO   - simulated sample data.
 * STAFF  - entered by an authorised staff member/admin (this includes approved reports).
 * SENSOR - reserved for a future authorised sensor integration.
 * REFERENCE - imported static reference data; no current status is asserted.
 */
public enum StatusSource {
    STAFF,
    SENSOR,
    REFERENCE,
    DEMO
}
