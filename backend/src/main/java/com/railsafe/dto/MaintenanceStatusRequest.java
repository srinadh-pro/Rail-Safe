package com.railsafe.dto;

import jakarta.validation.constraints.Size;

/** Status transition for a maintenance request. */
public record MaintenanceStatusRequest(
        @Size(max = 30) String status,
        @Size(max = 1000) String resolutionNote,
        Long assignedToId
) {
}
