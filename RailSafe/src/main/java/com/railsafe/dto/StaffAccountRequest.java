package com.railsafe.dto;

import jakarta.validation.constraints.*;

/** Admin-created STAFF account. The role is fixed and cannot be supplied by a client. */
public record StaffAccountRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Size(max = 25) String phone
) {
}
