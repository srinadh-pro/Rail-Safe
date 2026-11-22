package com.railsafe.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Reference relationship between a crossing and a train service. */
@Entity
@Table(name = "gate_train_mapping", indexes = {
        @Index(name = "idx_gate_train_gate", columnList = "gate_id"),
        @Index(name = "idx_gate_train_number", columnList = "train_number")
})
public class GateTrainMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gate_id", nullable = false)
    private Long gateId;

    @Column(name = "train_number", nullable = false, length = 20)
    private String trainNumber;

    @Column(length = 20)
    private String direction;

    @Column(name = "scheduled_time", length = 20)
    private String scheduledTime;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_classification", length = 25)
    private DataClassification dataClassification = DataClassification.VERIFIED_REFERENCE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGateId() { return gateId; }
    public void setGateId(Long gateId) { this.gateId = gateId; }
    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public DataClassification getDataClassification() { return dataClassification; }
    public void setDataClassification(DataClassification dataClassification) { this.dataClassification = dataClassification; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
