package com.railsafe.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Observed train position. Rows are stored only with an explicit provenance.
 * Synthetic rows are retained for review but never used by operational views.
 */
@Entity
@Table(name = "train_positions", indexes = @Index(name = "idx_train_position_number", columnList = "train_number"))
public class TrainPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_number", nullable = false, length = 20)
    private String trainNumber;

    private Double latitude;
    private Double longitude;

    @Column(name = "speed_kmph")
    private Double speedKmph;

    @Column(name = "observed_at")
    private LocalDateTime observedAt;

    @Column(length = 120)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_classification", nullable = false, length = 25)
    private DataClassification dataClassification = DataClassification.SIMULATED;

    @Column(nullable = false)
    private boolean synthetic = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getSpeedKmph() { return speedKmph; }
    public void setSpeedKmph(Double speedKmph) { this.speedKmph = speedKmph; }
    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime observedAt) { this.observedAt = observedAt; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public DataClassification getDataClassification() { return dataClassification; }
    public void setDataClassification(DataClassification dataClassification) { this.dataClassification = dataClassification; }
    public boolean isSynthetic() { return synthetic; }
    public void setSynthetic(boolean synthetic) { this.synthetic = synthetic; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
