package com.railsafe.dto;

import com.railsafe.entity.GateStatus;
import com.railsafe.entity.StatusSource;

import java.time.LocalDateTime;

/**
 * One gate status transition. Actor and gate IDs, action and timestamp allow staff
 * to see their own changes and administrators to review complete activity history.
 */
public record StatusHistoryResponse(
        Long id,
        Long gateId,
        String gateCode,
        GateStatus oldStatus,
        GateStatus newStatus,
        StatusSource source,
        String note,
        LocalDateTime changedAt,
        String changedBy,
        Long staffId,
        String action
) {
}
