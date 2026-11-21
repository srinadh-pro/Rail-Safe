package com.railsafe.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Audit trail: one row every time a gate status (or verification) is updated.
 * oldStatus is null when the gate is first created.
 */
@Entity
@Table(name = "gate_status_history", indexes = {
        @Index(name = "idx_history_gate_time", columnList = "gate_id, changed_at")
})
public class GateStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gate_id", nullable = false)
    private RailwayGate gate;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 30)
    private GateStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private GateStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    @Column(length = 300)
    private String note;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @PrePersist
    void prePersist() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }

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

    public GateStatus getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(GateStatus oldStatus) {
        this.oldStatus = oldStatus;
    }

    public GateStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(GateStatus newStatus) {
        this.newStatus = newStatus;
    }

    public StatusSource getSource() {
        return source;
    }

    public void setSource(StatusSource source) {
        this.source = source;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(User changedBy) {
        this.changedBy = changedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
