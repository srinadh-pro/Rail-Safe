package com.railsafe.entity;

import com.railsafe.live.model.LiveTrainStatus;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * A synthetic gate-to-train assignment used by the simulation provider.
 *
 * This is deliberately NOT the train_snapshots audit table: authorised provider
 * snapshots and simulation rows must never be mixed. Every row is synthetic=true
 * and carries a demo schedule offset rather than a real timetable.
 *
 * slotOffsetSeconds is an offset inside the repeating simulation cycle. The
 * provider turns it into a concrete ETA target for the current cycle, so the
 * browser can count down to an absolute timestamp instead of decrementing a value.
 */
@Entity
@Table(name = "train_tracking",
        uniqueConstraints = @UniqueConstraint(name = "uk_train_tracking_gate_train",
                columnNames = {"gate_id", "train_id"}),
        indexes = {
                @Index(name = "idx_train_tracking_gate", columnList = "gate_id"),
                @Index(name = "idx_train_tracking_train", columnList = "train_number"),
                @Index(name = "idx_train_tracking_status", columnList = "train_status")
        })
public class TrainTrackingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gate_id", nullable = false)
    private RailwayGate gate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id", nullable = false)
    private TrainMaster train;

    @Column(name = "train_number", nullable = false, length = 20)
    private String trainNumber;

    @Column(length = 20)
    private String direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "train_status", nullable = false, length = 20)
    private LiveTrainStatus trainStatus = LiveTrainStatus.APPROACHING;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "speed_kmph")
    private Double speedKmph;

    @Column(name = "eta_at_gate")
    private Instant etaAtGate;

    @Column(name = "crossing_started_at")
    private Instant crossingStartedAt;

    @Column(name = "crossing_ends_at")
    private Instant crossingEndsAt;

    /** Offset in seconds from the start of the repeating simulation cycle. */
    @Column(name = "slot_offset_seconds", nullable = false)
    private long slotOffsetSeconds;

    /** Order of this train within its gate (0 = first approaching). */
    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(nullable = false)
    private boolean synthetic = true;

    @Column(name = "dedupe_key", nullable = false, length = 120)
    private String dedupeKey;

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

    public TrainMaster getTrain() {
        return train;
    }

    public void setTrain(TrainMaster train) {
        this.train = train;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public LiveTrainStatus getTrainStatus() {
        return trainStatus;
    }

    public void setTrainStatus(LiveTrainStatus trainStatus) {
        this.trainStatus = trainStatus;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Double getSpeedKmph() {
        return speedKmph;
    }

    public void setSpeedKmph(Double speedKmph) {
        this.speedKmph = speedKmph;
    }

    public Instant getEtaAtGate() {
        return etaAtGate;
    }

    public void setEtaAtGate(Instant etaAtGate) {
        this.etaAtGate = etaAtGate;
    }

    public Instant getCrossingStartedAt() {
        return crossingStartedAt;
    }

    public void setCrossingStartedAt(Instant crossingStartedAt) {
        this.crossingStartedAt = crossingStartedAt;
    }

    public Instant getCrossingEndsAt() {
        return crossingEndsAt;
    }

    public void setCrossingEndsAt(Instant crossingEndsAt) {
        this.crossingEndsAt = crossingEndsAt;
    }

    public long getSlotOffsetSeconds() {
        return slotOffsetSeconds;
    }

    public void setSlotOffsetSeconds(long slotOffsetSeconds) {
        this.slotOffsetSeconds = slotOffsetSeconds;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public void setSynthetic(boolean synthetic) {
        this.synthetic = synthetic;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }
}
