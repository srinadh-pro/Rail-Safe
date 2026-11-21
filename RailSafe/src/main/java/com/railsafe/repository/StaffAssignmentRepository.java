package com.railsafe.repository;

import com.railsafe.entity.StaffAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {

    List<StaffAssignment> findByUserIdAndActiveTrueOrderByGateNameAsc(Long userId);

    List<StaffAssignment> findByGateIdAndActiveTrue(Long gateId);

    boolean existsByUserIdAndGateIdAndActiveTrue(Long userId, Long gateId);

    Optional<StaffAssignment> findByUserIdAndGateId(Long userId, Long gateId);
}
