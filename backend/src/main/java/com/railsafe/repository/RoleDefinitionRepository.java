package com.railsafe.repository;

import com.railsafe.entity.RoleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleDefinitionRepository extends JpaRepository<RoleDefinition, String> {
}
