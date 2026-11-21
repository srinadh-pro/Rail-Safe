package com.railsafe.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StaffAccountResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        boolean enabled,
        List<Long> assignedGateIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
