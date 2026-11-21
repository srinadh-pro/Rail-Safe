package com.railsafe.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for editing or deactivating an existing ADMIN account.
 * A null or blank password keeps the current password. The role cannot be changed.
 */
public record UpdateAdminRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name is too long")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 160, message = "Email is too long")
        String email,

        @Size(max = 25, message = "Phone number is too long")
        String phone,

        Boolean enabled,

        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        String password
) {
}
