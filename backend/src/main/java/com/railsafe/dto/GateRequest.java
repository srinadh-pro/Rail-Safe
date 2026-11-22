package com.railsafe.dto;

import jakarta.validation.constraints.*;

/**
 * Used when an admin/staff member creates or edits a crossing.
 * Status changes use a separate endpoint so that every change is written to history.
 * Reference metadata is optional; RailSafe never invents it.
 */
public record GateRequest(
        @NotBlank(message = "Gate code is required")
        @Size(max = 30, message = "Gate code is too long")
        String gateCode,

        @NotBlank(message = "Gate name is required")
        @Size(max = 120, message = "Gate name is too long")
        String name,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double longitude,

        @NotBlank(message = "Location description is required")
        @Size(max = 200, message = "Location description is too long")
        String location,

        @Size(max = 500, message = "Notes are too long")
        String notes,

        @Size(max = 80) String railwayZone,
        @Size(max = 80) String division,
        @Size(max = 120) String station,
        @Size(max = 120) String district,
        @Size(max = 80) String state,
        @Size(max = 40) String gateType,
        @Size(max = 300) String associatedRoutes
) {
}
