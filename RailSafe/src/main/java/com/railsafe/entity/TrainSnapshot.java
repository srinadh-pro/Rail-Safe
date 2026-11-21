package com.railsafe.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * An audit row for an authorised live feed response.
 *
 * Simulation data is never written here. A snapshot is evidence of what an
 * authorised provider reported at a point in time; it is not used to invent data
 * when the provider disappears.
 */
@Entity
@Table(name = "train_snapshots",
        indexes = {
                @Index(name = "idx_train_snapshots_recorded", columnList = "recorded_at"),
                @Index(name = "idx_train_snapshots_gate", columnList = "gate_code"),
                @Index(name = "idx_train_snapshots_train", columnList = "train_number")
        })
public class TrainSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String provider;

    @Column(name = "data_mode", nullable = false, length = 20)
    private String dataMode;

    @Column(name = "train_number", nullable = false, length = 40)
    private String trainNumber;

    @Column(name = "train_name", nullable = false, length = 160)
    private String trainName;

    @Column(length = 20)
    private String direction;

    private Double latitude;

    private Double longitude;

    @Column(name = "speed_kmph")
    private Double speedKmph;

    @Column(name = "gate_code", length = 30)
    private String gateCode;

    @Column(name = "eta_at_gate")
    private Instant etaAtGate;

    @Column(name = "scheduled_arrival")
    private Instant scheduledArrival;

    @Column(name = "train_status", length = 20)
    private String trainStatus;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "feed_stale", nullable = false)
    private boolean feedStale;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDataMode() {
        return dataMode;
    }

    public void setDataMode(String dataMode) {
        this.dataMode = dataMode;
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

    public Double getSpeedKmph() {
        return speedKmph;
    }

    public void setSpeedKmph(Double speedKmph) {
        this.speedKmph = speedKmph;
    }

    public String getGateCode() {
        return gateCode;
    }

    public void setGateCode(String gateCode) {
        this.gateCode = gateCode;
    }

    public Instant getEtaAtGate() {
        return etaAtGate;
    }

    public void setEtaAtGate(Instant etaAtGate) {
        this.etaAtGate = etaAtGate;
    }

    public Instant getScheduledArrival() {
        return scheduledArrival;
    }

    public void setScheduledArrival(Instant scheduledArrival) {
        this.scheduledArrival = scheduledArrival;
    }

    public String getTrainStatus() {
        return trainStatus;
    }

    public void setTrainStatus(String trainStatus) {
        this.trainStatus = trainStatus;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public boolean isFeedStale() {
        return feedStale;
    }

    public void setFeedStale(boolean feedStale) {
        this.feedStale = feedStale;
    }
}
