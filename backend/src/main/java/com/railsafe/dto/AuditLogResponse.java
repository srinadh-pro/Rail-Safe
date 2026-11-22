package com.railsafe.dto;

import java.time.LocalDateTime;

/** Read-only view of an administrative action for the Super Admin audit trail. */
public record AuditLogResponse(
        Long id,
        String actor,
        String action,
        String targetType,
        String targetId,
        String details,
        LocalDateTime createdAt
) {
}
