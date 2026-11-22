package com.railsafe.live.dto;

import java.time.Instant;

/**
 * Aggregated dashboard statistics. Every value is calculated from current
 * database/provider state; no value is hardcoded or cached in the browser.
 */
public record LiveCountsResponse(
        Instant generatedAt,
        String scope,
        long totalGates,
        long openGates,
        long closedGates,
        long closingGates,
        long openingGates,
        long maintenanceGates,
        long awaitingVerificationGates,
        long approachingTrains,
        long activeCrossings,
        boolean liveDataAvailable,
        String dataSource,
        String dataMode,
        Instant lastSuccessfulUpdate,
        String message
) {
}
