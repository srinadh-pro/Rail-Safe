package com.railsafe.dto;

import com.railsafe.entity.GateStatus;
import com.railsafe.entity.ReportStatus;

import java.time.LocalDateTime;

public record ReportResponse(
        Long id,
        Long gateId,
        String gateCode,
        String gateName,
        Long userId,
        String userName,
        GateStatus reportedStatus,
        String comment,
        ReportStatus status,
        LocalDateTime createdAt,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String reviewNote
) {
}
