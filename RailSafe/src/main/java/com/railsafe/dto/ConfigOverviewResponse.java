package com.railsafe.dto;

public record ConfigOverviewResponse(
        long gateStaleMinutes,
        String liveProviderMode,
        boolean alertsEnabled,
        boolean simulationAcknowledged,
        boolean simulationNotifications,
        double routeAvoidRadiusMeters,
        int maxAlternatives,
        long feedStaleSeconds,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean restrictedDatasetImportEnabled,
        String superAdminEmailMasked,
        String note
) {
}
