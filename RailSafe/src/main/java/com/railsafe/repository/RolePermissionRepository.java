package com.railsafe.repository;

import com.railsafe.entity.RolePermission;
import com.railsafe.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
}
