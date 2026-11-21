package com.railsafe.entity;

import jakarta.persistence.*;

/** Links a persistent role to a capability. */
@Entity
@Table(name = "role_permissions")
public class RolePermission {

    @EmbeddedId
    private RolePermissionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleName")
    @JoinColumn(name = "role_name", nullable = false)
    private RoleDefinition role;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("permissionId")
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    public RolePermission() { }

    public RolePermission(RoleDefinition role, Permission permission) {
        this.role = role;
        this.permission = permission;
        this.id = new RolePermissionId(role.getName(), permission.getId());
    }

    public RolePermissionId getId() { return id; }
    public void setId(RolePermissionId id) { this.id = id; }
    public RoleDefinition getRole() { return role; }
    public void setRole(RoleDefinition role) { this.role = role; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
}
