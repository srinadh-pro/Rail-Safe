package com.railsafe.repository;

import com.railsafe.entity.GateStatusHistory;
import com.railsafe.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GateStatusHistoryRepository extends JpaRepository<GateStatusHistory, Long> {

    List<GateStatusHistory> findByGateIdOrderByChangedAtDesc(Long gateId);

    List<GateStatusHistory> findByChangedByIdOrderByChangedAtDesc(Long staffUserId);

    List<GateStatusHistory> findByChangedByRoleOrderByChangedAtDesc(Role role);

    long countByGateId(Long gateId);

    void deleteByGateId(Long gateId);
}
