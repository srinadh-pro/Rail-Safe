package com.railsafe.live.dto;

import java.time.Instant;
import java.util.List;

/** Routes come from OSRM only. Distances and durations are never invented. */
public record AlternativeRouteResponse(
        boolean available,
        String message,
        String source,
        Long gateId,
        String gateName,
        Instant generatedAt,
        OriginalRoute original,
        List<RouteOption> alternatives,
        String recommendedRouteId,
        boolean directRouteAvoidsGate,
        double avoidRadiusMeters
) {

    public record OriginalRoute(
            double distanceKm,
            double durationMinutes,
            boolean avoidsGate,
            double distanceFromGateMeters,
            List<RoutePoint> geometry
    ) {
    }

    public record RouteOption(
            String id,
            String label,
            double distanceKm,
            double durationMinutes,
            double extraDistanceKm,
            double extraDurationMinutes,
            boolean recommended,
            double distanceFromGateMeters,
            List<RoutePoint> geometry,
            String navigationUrl,
            String mapUrl
    ) {
    }

    public record RoutePoint(double lat, double lng) {
    }
}
