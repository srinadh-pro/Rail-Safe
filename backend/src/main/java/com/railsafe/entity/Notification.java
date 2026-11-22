package com.railsafe.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * An in-app notification. dedupeKey is unique, so the same user can never receive
 * two notifications for the same gate status event or report decision.
 */
@Entity
@Table(name = "notifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_dedupe", columnNames = "dedupe_key"),
        indexes = @Index(name = "idx_notifications_user_read", columnList = "user_id, is_read"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_id")
    private RailwayGate gate;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_at_event", length = 30)
    private GateStatus statusAtEvent;

    @Column(name = "dedupe_key", nullable = false, length = 120)
    private String dedupeKey;

    /** Where the event originated, for example APPLICATION, STAFF or SENSOR. */
    @Column(name = "data_source", length = 30)
    private String dataSource = "APPLICATION";

    /** Operational mode of the event. Simulation events are never stored here. */
    @Column(name = "data_mode", length = 20)
    private String dataMode = "REAL_API";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public RailwayGate getGate() {
        return gate;
    }

    public void setGate(RailwayGate gate) {
        this.gate = gate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public GateStatus getStatusAtEvent() {
        return statusAtEvent;
    }

    public void setStatusAtEvent(GateStatus statusAtEvent) {
        this.statusAtEvent = statusAtEvent;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public String getDataMode() {
        return dataMode;
    }

    public void setDataMode(String dataMode) {
        this.dataMode = dataMode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
