package com.railsafe.dto;

/** Safe user information returned after login or from /api/auth/me. */
public record AuthResponse(
        Long id,
        String fullName,
        String email,
        String role
) {
}
