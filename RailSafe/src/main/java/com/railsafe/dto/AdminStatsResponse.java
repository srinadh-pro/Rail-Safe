package com.railsafe.dto;

import java.time.Instant;

/**
 * Operations dashboard statistics. All gate/train values come from
 * {@code LiveCountService}, which derives them from current database and provider state.
 */
public record AdminStatsResponse(
        long totalGates,
        long openGates,
        long closedGates,
        long unknownGates,
        long staleGates,
        long closingGates,
        long openingGates,
        long maintenanceGates,
        long awaitingVerificationGates,
        long approachingTrains,
        long activeCrossings,
        boolean liveDataAvailable,
        String dataMode,
        Instant lastUpdatedAt,
        long pendingReports,
        long totalUsers
) {
}
