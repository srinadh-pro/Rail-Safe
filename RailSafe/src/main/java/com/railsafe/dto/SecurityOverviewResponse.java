package com.railsafe.dto;

import java.time.Instant;
import java.util.List;

public record SecurityOverviewResponse(
        Instant generatedAt,
        boolean csrfEnabled,
        boolean sessionCookieHttpOnly,
        String sessionCookieSameSite,
        long sessionTimeoutSeconds,
        String passwordHashing,
        boolean auditLoggingEnabled,
        long superAdminAccounts,
        boolean demoAccountsExposed,
        boolean simulationProviderEnabled,
        List<String> findings
) {
}
