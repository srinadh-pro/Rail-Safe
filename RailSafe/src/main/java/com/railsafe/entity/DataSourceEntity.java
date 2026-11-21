package com.railsafe.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Inventory of operational, reference, and restricted data sources. */
@Entity
@Table(name = "data_sources", uniqueConstraints =
        @UniqueConstraint(name = "uk_data_source_name", columnNames = "name"))
public class DataSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(length = 500)
    private String url;

    @Column(name = "data_mode", nullable = false, length = 30)
    private String dataMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_classification", nullable = false, length = 25)
    private DataClassification dataClassification;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getDataMode() { return dataMode; }
    public void setDataMode(String dataMode) { this.dataMode = dataMode; }
    public DataClassification getDataClassification() { return dataClassification; }
    public void setDataClassification(DataClassification dataClassification) { this.dataClassification = dataClassification; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
