package com.railsafe.repository;

import com.railsafe.entity.MaintenanceRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {
    List<MaintenanceRequest> findAllByOrderByRequestedAtDesc();
    List<MaintenanceRequest> findByGateIdOrderByRequestedAtDesc(Long gateId);
    List<MaintenanceRequest> findByStatusIgnoreCaseOrderByRequestedAtDesc(String status);
}
