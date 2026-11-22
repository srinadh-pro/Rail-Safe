package com.railsafe.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Verified static timetable reference row. Never a live position or ETA. */
@Entity
@Table(name = "train_schedules", indexes = @Index(name = "idx_train_schedule_number", columnList = "train_number"))
public class TrainSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_number", nullable = false, length = 20)
    private String trainNumber;

    @Column(name = "station_name", nullable = false, length = 120)
    private String stationName;

    @Column(name = "station_code", length = 20)
    private String stationCode;

    private Integer sequence;

    @Column(name = "arrival_time", length = 20)
    private String arrivalTime;

    @Column(name = "departure_time", length = 20)
    private String departureTime;

    @Column(name = "day_offset")
    private Integer dayOffset = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_classification", length = 25)
    private DataClassification dataClassification = DataClassification.VERIFIED_REFERENCE;

    @Column(name = "reference_source", length = 120)
    private String referenceSource;

    @Column(name = "reference_url", length = 500)
    private String referenceUrl;

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
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    public String getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(String arrivalTime) { this.arrivalTime = arrivalTime; }
    public String getDepartureTime() { return departureTime; }
    public void setDepartureTime(String departureTime) { this.departureTime = departureTime; }
    public Integer getDayOffset() { return dayOffset; }
    public void setDayOffset(Integer dayOffset) { this.dayOffset = dayOffset; }
    public DataClassification getDataClassification() { return dataClassification; }
    public void setDataClassification(DataClassification dataClassification) { this.dataClassification = dataClassification; }
    public String getReferenceSource() { return referenceSource; }
    public void setReferenceSource(String referenceSource) { this.referenceSource = referenceSource; }
    public String getReferenceUrl() { return referenceUrl; }
    public void setReferenceUrl(String referenceUrl) { this.referenceUrl = referenceUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
