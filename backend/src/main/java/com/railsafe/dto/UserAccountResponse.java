package com.railsafe.dto;

import java.time.LocalDateTime;

public record UserAccountResponse(
        Long id,
        String fullName,
        String email,
        String role,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
