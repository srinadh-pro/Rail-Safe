package com.railsafe.dto;

/** Demo sign-in shortcut. Passwords here are deliberately non-production credentials. */
public record DemoAccountResponse(
        String role,
        String label,
        String username,
        String email,
        String password,
        String destination,
        String description
) {
}
