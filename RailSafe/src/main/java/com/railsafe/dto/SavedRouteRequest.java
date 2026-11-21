package com.railsafe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SavedRouteRequest(
        @NotBlank(message = "Route name is required")
        @Size(max = 100, message = "Route name is too long")
        String name,

        @NotBlank(message = "Start label is required")
        @Size(max = 180, message = "Start label is too long")
        String startLabel,

        @NotNull(message = "Start latitude is required")
        Double startLat,

        @NotNull(message = "Start longitude is required")
        Double startLng,

        @NotBlank(message = "Destination label is required")
        @Size(max = 180, message = "Destination label is too long")
        String destLabel,

        @NotNull(message = "Destination latitude is required")
        Double destLat,

        @NotNull(message = "Destination longitude is required")
        Double destLng,

        List<Long> gateIds
) {
}
