package com.railsafe.dto;

import com.railsafe.entity.GateStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Supervisory override. A SUPER_ADMIN must state why an observed OPEN/CLOSED
 * status is being recorded; the reason is preserved in the audit log.
 */
public record GateOverrideRequest(
        @NotNull(message = "Status is required")
        GateStatus status,

        @NotBlank(message = "An authorisation reason is required")
        @Size(max = 300, message = "Reason is too long")
        String reason
) {
}
