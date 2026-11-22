package com.railsafe.dto;

import java.time.LocalDateTime;

public record MaintenanceResponse(
        Long id,
        Long gateId,
        String gateCode,
        String gateName,
        Long requesterId,
        String requesterName,
        Long assignedToId,
        String assignedToName,
        String issueType,
        String description,
        String priority,
        String status,
        String resolutionNote,
        LocalDateTime requestedAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt
) {
}
