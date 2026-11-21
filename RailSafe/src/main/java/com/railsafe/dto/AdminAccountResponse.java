package com.railsafe.dto;

import java.time.LocalDateTime;

/** Safe ADMIN/SUPER_ADMIN account information. The password hash is never returned. */
public record AdminAccountResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
