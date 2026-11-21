package com.railsafe.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A railway level crossing.
 *
 * Safety note: status is "last reported", not a guaranteed live railway signal.
 * The service layer converts stale statuses to UNKNOWN before sending them to the UI.
 */
@Entity
@Table(name = "railway_gates",
        uniqueConstraints = @UniqueConstraint(name = "uk_gate_code", columnNames = "gate_code"),
        indexes = {
                @Index(name = "idx_gate_lat_lng", columnList = "latitude, longitude"),
                @Index(name = "idx_gate_status", columnList = "status")
        })
public class RailwayGate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gate_code", nullable = false, length = 30)
    private String gateCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false, length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GateStatus status = GateStatus.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_source", nullable = false, length = 20)
    private StatusSource statusSource = StatusSource.STAFF;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;

    @Column(length = 500)
    private String notes;

    /* Additive reference metadata. Nullable so existing operational rows are preserved. */
    @Column(name = "railway_zone", length = 80)
    private String railwayZone;

    @Column(length = 80)
    private String division;

    @Column(length = 120)
    private String station;

    @Column(length = 120)
    private String district;

    @Column(length = 80)
    private String state;

    @Column(name = "gate_type", length = 40)
    private String gateType;

    @Column(name = "associated_routes", length = 300)
    private String associatedRoutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_classification", length = 25)
    private DataClassification dataClassification = DataClassification.VERIFIED_REFERENCE;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "expected_closing_at")
    private LocalDateTime expectedClosingAt;

    @Column(name = "expected_opening_at")
    private LocalDateTime expectedOpeningAt;

    @Column(name = "actual_closing_at")
    private LocalDateTime actualClosingAt;

    @Column(name = "actual_opening_at")
    private LocalDateTime actualOpeningAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGateCode() {
        return gateCode;
    }

    public void setGateCode(String gateCode) {
        this.gateCode = gateCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public GateStatus getStatus() {
        return status;
    }

    public void setStatus(GateStatus status) {
        this.status = status;
    }

    public StatusSource getStatusSource() {
        return statusSource;
    }

    public void setStatusSource(StatusSource statusSource) {
        this.statusSource = statusSource;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getRailwayZone() {
        return railwayZone;
    }

    public void setRailwayZone(String railwayZone) {
        this.railwayZone = railwayZone;
    }

    public String getDivision() {
        return division;
    }

    public void setDivision(String division) {
        this.division = division;
    }

    public String getStation() {
        return station;
    }

    public void setStation(String station) {
        this.station = station;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getGateType() {
        return gateType;
    }

    public void setGateType(String gateType) {
        this.gateType = gateType;
    }

    public String getAssociatedRoutes() {
        return associatedRoutes;
    }

    public void setAssociatedRoutes(String associatedRoutes) {
        this.associatedRoutes = associatedRoutes;
    }

    public DataClassification getDataClassification() {
        return dataClassification;
    }

    public void setDataClassification(DataClassification dataClassification) {
        this.dataClassification = dataClassification;
    }

    public LocalDateTime getStatusChangedAt() {
        return statusChangedAt;
    }

    public void setStatusChangedAt(LocalDateTime statusChangedAt) {
        this.statusChangedAt = statusChangedAt;
    }

    public LocalDateTime getExpectedClosingAt() {
        return expectedClosingAt;
    }

    public void setExpectedClosingAt(LocalDateTime expectedClosingAt) {
        this.expectedClosingAt = expectedClosingAt;
    }

    public LocalDateTime getExpectedOpeningAt() {
        return expectedOpeningAt;
    }

    public void setExpectedOpeningAt(LocalDateTime expectedOpeningAt) {
        this.expectedOpeningAt = expectedOpeningAt;
    }

    public LocalDateTime getActualClosingAt() {
        return actualClosingAt;
    }

    public void setActualClosingAt(LocalDateTime actualClosingAt) {
        this.actualClosingAt = actualClosingAt;
    }

    public LocalDateTime getActualOpeningAt() {
        return actualOpeningAt;
    }

    public void setActualOpeningAt(LocalDateTime actualOpeningAt) {
        this.actualOpeningAt = actualOpeningAt;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(User updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
