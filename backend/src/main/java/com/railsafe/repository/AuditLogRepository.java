package com.railsafe.repository;

import com.railsafe.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Append-only administrative action log. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();
}
