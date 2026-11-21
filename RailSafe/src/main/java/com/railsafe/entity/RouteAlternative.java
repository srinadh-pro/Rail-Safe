package com.railsafe.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A route calculation produced by OSRM and verified by
 * RouteCalculationService against the selected crossing. Persisting it gives an
 * auditable record of what was offered and avoids recalculating identical requests.
 */
@Entity
@Table(name = "route_alternatives",
        indexes = {
                @Index(name = "idx_route_alternatives_gate", columnList = "gate_id"),
                @Index(name = "idx_route_alternatives_generated", columnList = "generated_at")
        })
public class RouteAlternative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gate_id", nullable = false)
    private RailwayGate gate;

    @Column(name = "route_key", nullable = false, length = 40)
    private String routeKey;

    @Column(name = "route_label", length = 80)
    private String routeLabel;

    @Column(name = "start_lat", nullable = false)
    private Double startLat;

    @Column(name = "start_lng", nullable = false)
    private Double startLng;

    @Column(name = "dest_lat", nullable = false)
    private Double destLat;

    @Column(name = "dest_lng", nullable = false)
    private Double destLng;

    @Column(name = "distance_km", nullable = false)
    private Double distanceKm;

    @Column(name = "duration_minutes", nullable = false)
    private Double durationMinutes;

    @Column(name = "extra_distance_km")
    private Double extraDistanceKm;

    @Column(nullable = false)
    private boolean recommended;

    @Column(name = "distance_from_gate_meters")
    private Double distanceFromGateMeters;

    @Column(name = "navigation_url", length = 1200)
    private String navigationUrl;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "geometry_json")
    private String geometryJson;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    /**
     * True when the row was seeded by the synthetic demo dataset rather than
     * calculated by OSRM. Synthetic rows are never presented as verified routes.
     */
    @Column(nullable = false)
    private boolean synthetic = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RailwayGate getGate() {
        return gate;
    }

    public void setGate(RailwayGate gate) {
        this.gate = gate;
    }

    public String getRouteKey() {
        return routeKey;
    }

    public void setRouteKey(String routeKey) {
        this.routeKey = routeKey;
    }

    public String getRouteLabel() {
        return routeLabel;
    }

    public void setRouteLabel(String routeLabel) {
        this.routeLabel = routeLabel;
    }

    public Double getStartLat() {
        return startLat;
    }

    public void setStartLat(Double startLat) {
        this.startLat = startLat;
    }

    public Double getStartLng() {
        return startLng;
    }

    public void setStartLng(Double startLng) {
        this.startLng = startLng;
    }

    public Double getDestLat() {
        return destLat;
    }

    public void setDestLat(Double destLat) {
        this.destLat = destLat;
    }

    public Double getDestLng() {
        return destLng;
    }

    public void setDestLng(Double destLng) {
        this.destLng = destLng;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Double getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Double durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Double getExtraDistanceKm() {
        return extraDistanceKm;
    }

    public void setExtraDistanceKm(Double extraDistanceKm) {
        this.extraDistanceKm = extraDistanceKm;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
    }

    public Double getDistanceFromGateMeters() {
        return distanceFromGateMeters;
    }

    public void setDistanceFromGateMeters(Double distanceFromGateMeters) {
        this.distanceFromGateMeters = distanceFromGateMeters;
    }

    public String getNavigationUrl() {
        return navigationUrl;
    }

    public void setNavigationUrl(String navigationUrl) {
        this.navigationUrl = navigationUrl;
    }

    public String getGeometryJson() {
        return geometryJson;
    }

    public void setGeometryJson(String geometryJson) {
        this.geometryJson = geometryJson;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public void setSynthetic(boolean synthetic) {
        this.synthetic = synthetic;
    }
}
