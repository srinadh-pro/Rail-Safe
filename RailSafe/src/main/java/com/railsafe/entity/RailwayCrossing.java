package com.railsafe.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Reference crossing record linked to an operational gate where one exists. */
@Entity
@Table(name = "railway_crossings", uniqueConstraints =
        @UniqueConstraint(name = "uk_crossing_code", columnNames = "crossing_code"))
public class RailwayCrossing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crossing_code", nullable = false, length = 30)
    private String crossingCode;

    @Column(length = 120)
    private String name;

    @Column(name = "gate_id")
    private Long gateId;

    private Double latitude;
    private Double longitude;

    @Column(length = 120)
    private String station;

    @Column(length = 120)
    private String district;

    @Column(length = 80)
    private String state;

    @Column(name = "railway_zone", length = 80)
    private String railwayZone;

    @Column(length = 80)
    private String division;

    @Column(length = 30)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_classification", length = 25)
    private DataClassification dataClassification = DataClassification.VERIFIED_REFERENCE;

    @Column(name = "source_key", length = 120)
    private String sourceKey;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCrossingCode() { return crossingCode; }
    public void setCrossingCode(String crossingCode) { this.crossingCode = crossingCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getGateId() { return gateId; }
    public void setGateId(Long gateId) { this.gateId = gateId; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getRailwayZone() { return railwayZone; }
    public void setRailwayZone(String railwayZone) { this.railwayZone = railwayZone; }
    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public DataClassification getDataClassification() { return dataClassification; }
    public void setDataClassification(DataClassification dataClassification) { this.dataClassification = dataClassification; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
