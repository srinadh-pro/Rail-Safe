package com.railsafe.dto;

import java.time.Instant;
import java.util.List;

public record BackupOverviewResponse(
        String directory,
        int backupCount,
        List<String> backups,
        boolean fileSystemAvailable,
        String recommendation,
        Instant generatedAt
) {
}
