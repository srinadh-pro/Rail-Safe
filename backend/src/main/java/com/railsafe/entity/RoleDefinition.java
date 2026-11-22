package com.railsafe.entity;

import jakarta.persistence.*;

/** Persistent description of an application role. The enum remains authoritative. */
@Entity
@Table(name = "roles")
public class RoleDefinition {

    @Id
    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }
}
