package com.railsafe.util;

import java.util.List;

/** Small geographic helpers used by gate search and route-avoidance checks. */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtils() {
    }

    public record GeoPoint(double lat, double lng) {
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Shortest distance in metres from a point to a polyline. This is used to prove
     * that an OSRM alternative really avoids the selected crossing; a route is only
     * offered when its closest approach is greater than the configured radius.
     */
    public static double distancePointToPolylineMeters(double lat, double lng, List<GeoPoint> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        if (coordinates.size() == 1) {
            return haversineKm(lat, lng, coordinates.get(0).lat(), coordinates.get(0).lng()) * 1000.0;
        }

        double metresPerDegreeLat = 111320.0;
        double metresPerDegreeLng = 111320.0 * Math.cos(lat * Math.PI / 180.0);
        double minimum = Double.POSITIVE_INFINITY;

        for (int index = 0; index < coordinates.size() - 1; index++) {
            GeoPoint a = coordinates.get(index);
            GeoPoint b = coordinates.get(index + 1);
            double ax = (a.lng() - lng) * metresPerDegreeLng;
            double ay = (a.lat() - lat) * metresPerDegreeLat;
            double bx = (b.lng() - lng) * metresPerDegreeLng;
            double by = (b.lat() - lat) * metresPerDegreeLat;
            double dx = bx - ax;
            double dy = by - ay;
            double lengthSquared = dx * dx + dy * dy;
            double t = 0;
            if (lengthSquared > 0) {
                t = Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lengthSquared));
            }
            double closestX = ax + dx * t;
            double closestY = ay + dy * t;
            minimum = Math.min(minimum, Math.hypot(closestX, closestY));
        }
        return minimum;
    }

    /** Point at a given bearing and distance from an origin, used to build detour via-points. */
    public static GeoPoint destinationPoint(double lat, double lng, double bearingDegrees, double distanceMetres) {
        double angularDistance = distanceMetres / (EARTH_RADIUS_KM * 1000.0);
        double bearing = Math.toRadians(bearingDegrees);
        double latRad = Math.toRadians(lat);
        double lngRad = Math.toRadians(lng);

        double lat2 = Math.asin(Math.sin(latRad) * Math.cos(angularDistance)
                + Math.cos(latRad) * Math.sin(angularDistance) * Math.cos(bearing));
        double lng2 = lngRad + Math.atan2(
                Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(latRad),
                Math.cos(angularDistance) - Math.sin(latRad) * Math.sin(lat2));

        return new GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lng2));
    }
}
