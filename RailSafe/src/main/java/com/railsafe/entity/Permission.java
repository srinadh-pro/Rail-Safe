package com.railsafe.entity;

import jakarta.persistence.*;

/** Named capability used by role/permission management. */
@Entity
@Table(name = "permissions", uniqueConstraints =
        @UniqueConstraint(name = "uk_permission_code", columnNames = "code"))
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(length = 255)
    private String description;

    @Column(length = 80)
    private String category;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
