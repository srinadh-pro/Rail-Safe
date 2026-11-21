package com.railsafe.dto;

import jakarta.validation.constraints.*;

/** New maintenance request. The gate must be a real, operational crossing. */
public record MaintenanceRequestRequest(
        @NotNull Long gateId,
        @NotBlank @Size(max = 60) String issueType,
        @NotBlank @Size(max = 1000) String description,
        @Size(max = 20) String priority
) {
}
