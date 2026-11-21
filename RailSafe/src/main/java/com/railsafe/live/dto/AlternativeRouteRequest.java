package com.railsafe.live.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Start and destination are supplied by the user. RailSafe never substitutes a
 * made-up route; if OSRM cannot calculate one, the response says so.
 */
public record AlternativeRouteRequest(
        @NotNull(message = "gateId is required")
        Long gateId,

        @NotNull(message = "startLat is required")
        @DecimalMin(value = "-90.0", message = "startLat must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "startLat must be between -90 and 90")
        Double startLat,

        @NotNull(message = "startLng is required")
        @DecimalMin(value = "-180.0", message = "startLng must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "startLng must be between -180 and 180")
        Double startLng,

        @NotNull(message = "destLat is required")
        @DecimalMin(value = "-90.0", message = "destLat must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "destLat must be between -90 and 90")
        Double destLat,

        @NotNull(message = "destLng is required")
        @DecimalMin(value = "-180.0", message = "destLng must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "destLng must be between -180 and 180")
        Double destLng
) {
}
