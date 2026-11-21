package com.railsafe.dto;

import com.railsafe.entity.GateStatus;
import com.railsafe.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String message,
        NotificationType type,
        boolean read,
        Long gateId,
        String gateCode,
        String gateName,
        GateStatus statusAtEvent,
        LocalDateTime createdAt,
        String dataSource,
        String dataMode
) {
}
