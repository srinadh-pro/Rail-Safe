package com.railsafe.entity;

/**
 * Explicit provenance classes used across RailSafe.
 *
 * <ul>
 *   <li>{@code VERIFIED_LIVE} - received from a trusted operational source.</li>
 *   <li>{@code VERIFIED_REFERENCE} - checked static railway reference data.</li>
 *   <li>{@code SIMULATED} - demonstration or test data only.</li>
 * </ul>
 *
 * Simulated rows are never promoted into operational dashboards or alerts.
 */
public enum DataClassification {
    VERIFIED_LIVE,
    VERIFIED_REFERENCE,
    SIMULATED
}
