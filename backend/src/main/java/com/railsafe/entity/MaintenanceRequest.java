package com.railsafe.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Operational maintenance workflow for a real, operational crossing. */
@Entity
@Table(name = "maintenance_requests", indexes = {
        @Index(name = "idx_maintenance_gate", columnList = "gate_id"),
        @Index(name = "idx_maintenance_status", columnList = "status")
})
public class MaintenanceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gate_id", nullable = false)
    private Long gateId;

    @Column(name = "requester_id")
    private Long requesterId;

    @Column(name = "assigned_to_id")
    private Long assignedToId;

    @Column(name = "issue_type", nullable = false, length = 60)
    private String issueType;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(length = 20)
    private String priority = "NORMAL";

    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (requestedAt == null) requestedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGateId() { return gateId; }
    public void setGateId(Long gateId) { this.gateId = gateId; }
    public Long getRequesterId() { return requesterId; }
    public void setRequesterId(Long requesterId) { this.requesterId = requesterId; }
    public Long getAssignedToId() { return assignedToId; }
    public void setAssignedToId(Long assignedToId) { this.assignedToId = assignedToId; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
