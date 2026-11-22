package com.railsafe.dto;

import java.time.Instant;
import java.util.Map;

public record AnalyticsResponse(
        Instant generatedAt,
        Map<String, Long> usersByRole,
        Map<String, Long> gatesByStatus,
        Map<String, Long> notificationsByType,
        long routeSearches,
        long maintenanceRequests,
        long restrictedSourceRows
) {
}
