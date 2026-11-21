package com.railsafe.entity;

import jakarta.persistence.*;

/** Reference row for a railway division within a zone. */
@Entity
@Table(name = "railway_divisions")
public class RailwayDivision {

    @Id
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "zone_code", nullable = false, length = 20)
    private String zoneCode;

    @Column(nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
