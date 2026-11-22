package com.railsafe.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Audit trail for route assistance searches. Geometry is never invented here. */
@Entity
@Table(name = "route_search_history")
public class RouteSearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "start_lat")
    private Double startLat;

    @Column(name = "start_lng")
    private Double startLng;

    @Column(name = "end_lat")
    private Double endLat;

    @Column(name = "end_lng")
    private Double endLng;

    @Column(name = "start_label", length = 160)
    private String startLabel;

    @Column(name = "end_label", length = 160)
    private String endLabel;

    @Column(name = "gate_ids", length = 500)
    private String gateIds;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "duration_minutes")
    private Double durationMinutes;

    @Column(name = "route_count")
    private Integer routeCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_classification", length = 25)
    private DataClassification dataClassification = DataClassification.VERIFIED_REFERENCE;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Double getStartLat() { return startLat; }
    public void setStartLat(Double startLat) { this.startLat = startLat; }
    public Double getStartLng() { return startLng; }
    public void setStartLng(Double startLng) { this.startLng = startLng; }
    public Double getEndLat() { return endLat; }
    public void setEndLat(Double endLat) { this.endLat = endLat; }
    public Double getEndLng() { return endLng; }
    public void setEndLng(Double endLng) { this.endLng = endLng; }
    public String getStartLabel() { return startLabel; }
    public void setStartLabel(String startLabel) { this.startLabel = startLabel; }
    public String getEndLabel() { return endLabel; }
    public void setEndLabel(String endLabel) { this.endLabel = endLabel; }
    public String getGateIds() { return gateIds; }
    public void setGateIds(String gateIds) { this.gateIds = gateIds; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public Double getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Double durationMinutes) { this.durationMinutes = durationMinutes; }
    public Integer getRouteCount() { return routeCount; }
    public void setRouteCount(Integer routeCount) { this.routeCount = routeCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public DataClassification getDataClassification() { return dataClassification; }
    public void setDataClassification(DataClassification dataClassification) { this.dataClassification = dataClassification; }
}
