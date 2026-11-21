package com.railsafe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for role_permissions. */
@Embeddable
public class RolePermissionId implements Serializable {

    @Column(name = "role_name", nullable = false, length = 40)
    private String roleName;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    public RolePermissionId() { }

    public RolePermissionId(String roleName, Long permissionId) {
        this.roleName = roleName;
        this.permissionId = permissionId;
    }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RolePermissionId id)) return false;
        return Objects.equals(roleName, id.roleName) && Objects.equals(permissionId, id.permissionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleName, permissionId);
    }
}
