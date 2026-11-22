package com.railsafe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A retained row from an external restricted dataset.
 *
 * These rows are stored separately from the operational domain tables so they
 * can never be picked up by a normal gate, train, notification, report or user
 * query. They are exposed only through the SUPER_ADMIN source-review inventory.
 */
@Entity
@Table(name = "imported_source_rows", uniqueConstraints = @UniqueConstraint(
        name = "uk_imported_source_dedupe",
        columnNames = "dedupe_key"
))
public class ImportedSourceRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable natural key, e.g. import:expanded:gates:GATE001. */
    @Column(name = "dedupe_key", nullable = false, length = 180)
    private String dedupeKey;

    @Column(name = "category", nullable = false, length = 60)
    private String category;

    @Column(name = "source_key", nullable = false, length = 120)
    private String sourceKey;

    @Column(name = "record_type", nullable = false, length = 80)
    private String recordType;

    @Column(name = "record_label", length = 300)
    private String recordLabel;

    @Column(name = "status", length = 60)
    private String status;

    @Column(name = "classification", nullable = false, length = 30)
    private String classification;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "detail")
    private String detail;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @PrePersist
    void ensureImportedAt() {
        if (importedAt == null) {
            importedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public String getRecordLabel() {
        return recordLabel;
    }

    public void setRecordLabel(String recordLabel) {
        this.recordLabel = recordLabel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }
}
