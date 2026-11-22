package com.railsafe.dto;

import jakarta.validation.constraints.*;

/** Admin update for an existing STAFF account. Password is optional. */
public record UpdateStaffRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 160) String email,
        @Size(max = 25) String phone,
        Boolean enabled,
        @Size(min = 8, max = 100) String password
) {
}
