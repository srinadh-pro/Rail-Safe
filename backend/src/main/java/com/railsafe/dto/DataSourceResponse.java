package com.railsafe.dto;

import com.railsafe.entity.DataClassification;

import java.time.LocalDateTime;

public record DataSourceResponse(
        Long id,
        String name,
        String sourceType,
        String url,
        String dataMode,
        DataClassification dataClassification,
        boolean enabled,
        LocalDateTime lastSyncAt,
        String description
) {
}
