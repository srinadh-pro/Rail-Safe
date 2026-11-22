package com.railsafe.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Explicit staff-to-gate responsibility record.
 *
 * Staff may only operate gates that are actively assigned to them. Administrators
 * and Super Administrators retain system-wide operational access.
 */
@Entity
@Table(name = "staff_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uk_staff_gate", columnNames = {"user_id", "gate_id"}),
        indexes = {
                @Index(name = "idx_staff_assignment_user", columnList = "user_id"),
                @Index(name = "idx_staff_assignment_gate", columnList = "gate_id")
        })
public class StaffAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_id", nullable = false)
    private RailwayGate gate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @Column(name = "unassigned_at")
    private LocalDateTime unassignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id")
    private User assignedBy;

    @PrePersist
    void prePersist() {
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public RailwayGate getGate() { return gate; }
    public void setGate(RailwayGate gate) { this.gate = gate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    public LocalDateTime getUnassignedAt() { return unassignedAt; }
    public void setUnassignedAt(LocalDateTime unassignedAt) { this.unassignedAt = unassignedAt; }
    public User getAssignedBy() { return assignedBy; }
    public void setAssignedBy(User assignedBy) { this.assignedBy = assignedBy; }
}
