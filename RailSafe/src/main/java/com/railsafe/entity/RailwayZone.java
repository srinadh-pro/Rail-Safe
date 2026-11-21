package com.railsafe.entity;

import jakarta.persistence.*;

/** Reference row for an Indian Railways zone. */
@Entity
@Table(name = "railway_zones")
public class RailwayZone {

    @Id
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String headquarters;

    @Column(nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHeadquarters() { return headquarters; }
    public void setHeadquarters(String headquarters) { this.headquarters = headquarters; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
