package com.railsafe.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Non-sensitive operational health summary for Super Admin. */
public record SystemHealthResponse(
        String status,
        Instant generatedAt,
        long totalGates,
        long verifiedGates,
        long awaitingVerificationGates,
        long simulatedGates,
        long totalUsers,
        long staffUsers,
        long adminUsers,
        long superAdminUsers,
        long trainReferences,
        long pendingReports,
        long unreadNotifications,
        long auditEvents,
        String liveProviderMode,
        boolean alertsEnabled,
        boolean simulationAcknowledged,
        String dataMode,
        String message,
        List<String> warnings
) {
}
