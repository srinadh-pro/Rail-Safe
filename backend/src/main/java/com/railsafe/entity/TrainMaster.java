package com.railsafe.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Train reference catalogue entry.
 *
 * Rows with {@code dataClassification=VERIFIED_REFERENCE} contain checked static
 * railway information only. Rows with {@code synthetic=true} are demonstration data
 * and are never shown in normal operational views. A catalogue entry does not mean
 * the train is currently running or near a gate; live positions come only from an
 * authorised provider.
 */
@Entity
@Table(name = "train_master",
        uniqueConstraints = @UniqueConstraint(name = "uk_train_master_number", columnNames = "train_number"),
        indexes = @Index(name = "idx_train_master_name", columnList = "train_name"))
public class TrainMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_number", nullable = false, length = 20)
    private String trainNumber;

    @Column(name = "train_name", nullable = false, length = 160)
    private String trainName;

    /** NORTH | SOUTH | EAST | WEST | UNKNOWN */
    @Column(length = 20)
    private String direction;

    @Column(length = 80)
    private String zone;

    @Column(nullable = false)
    private boolean synthetic = true;

    /* Verified reference fields. Live positions/ETAs are never stored here. */
    @Column(name = "source_station", length = 120)
    private String sourceStation;

    @Column(name = "destination_station", length = 120)
    private String destinationStation;

    @Column(name = "current_station", length = 120)
    private String currentStation;

    @Column(name = "next_station", length = 120)
    private String nextStation;

    @Column(name = "scheduled_departure", length = 20)
    private String scheduledDeparture;

    @Column(name = "scheduled_arrival", length = 20)
    private String scheduledArrival;

    @Column(name = "estimated_arrival", length = 20)
    private String estimatedArrival;

    @Column(name = "running_status", length = 40)
    private String runningStatus = "Scheduled";

    @Column(name = "speed_kmph")
    private Double speedKmph;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_classification", length = 25)
    private DataClassification dataClassification = DataClassification.VERIFIED_REFERENCE;

    @Column(name = "reference_source", length = 120)
    private String referenceSource;

    @Column(name = "reference_url", length = 500)
    private String referenceUrl;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }

    public String getTrainName() {
        return trainName;
    }

    public void setTrainName(String trainName) {
        this.trainName = trainName;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public void setSynthetic(boolean synthetic) {
        this.synthetic = synthetic;
    }

    public String getSourceStation() { return sourceStation; }
    public void setSourceStation(String sourceStation) { this.sourceStation = sourceStation; }

    public String getDestinationStation() { return destinationStation; }
    public void setDestinationStation(String destinationStation) { this.destinationStation = destinationStation; }

    public String getCurrentStation() { return currentStation; }
    public void setCurrentStation(String currentStation) { this.currentStation = currentStation; }

    public String getNextStation() { return nextStation; }
    public void setNextStation(String nextStation) { this.nextStation = nextStation; }

    public String getScheduledDeparture() { return scheduledDeparture; }
    public void setScheduledDeparture(String scheduledDeparture) { this.scheduledDeparture = scheduledDeparture; }

    public String getScheduledArrival() { return scheduledArrival; }
    public void setScheduledArrival(String scheduledArrival) { this.scheduledArrival = scheduledArrival; }

    public String getEstimatedArrival() { return estimatedArrival; }
    public void setEstimatedArrival(String estimatedArrival) { this.estimatedArrival = estimatedArrival; }

    public String getRunningStatus() { return runningStatus; }
    public void setRunningStatus(String runningStatus) { this.runningStatus = runningStatus; }

    public Double getSpeedKmph() { return speedKmph; }
    public void setSpeedKmph(Double speedKmph) { this.speedKmph = speedKmph; }

    public DataClassification getDataClassification() { return dataClassification; }
    public void setDataClassification(DataClassification dataClassification) { this.dataClassification = dataClassification; }

    public String getReferenceSource() { return referenceSource; }
    public void setReferenceSource(String referenceSource) { this.referenceSource = referenceSource; }

    public String getReferenceUrl() { return referenceUrl; }
    public void setReferenceUrl(String referenceUrl) { this.referenceUrl = referenceUrl; }

    public Instant getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(Instant lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
