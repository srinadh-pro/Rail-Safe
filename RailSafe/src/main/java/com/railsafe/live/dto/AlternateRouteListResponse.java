package com.railsafe.live.dto;

import java.time.Instant;
import java.util.List;

/**
 * Alternate routes for a closed crossing.
 *
 * Normal responses contain only routes verified by the routing engine. The
 * boolean field is retained for API compatibility and is always false in
 * normal-facing responses.
 */
public record AlternateRouteListResponse(
        boolean available,
        boolean synthetic,
        String message,
        Long gateId,
        String gateName,
        String gateStatus,
        Instant generatedAt,
        List<RouteSuggestion> routes
) {

    public record RouteSuggestion(
            Long id,
            String label,
            double distanceKm,
            double durationMinutes,
            Double extraDistanceKm,
            boolean recommended,
            boolean available,
            String availabilityNote,
            String navigationUrl,
            String mapUrl,
            List<RoutePoint> geometry
    ) {
    }

    public record RoutePoint(double lat, double lng) {
    }
}
