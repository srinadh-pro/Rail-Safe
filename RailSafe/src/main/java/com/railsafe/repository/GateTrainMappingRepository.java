package com.railsafe.repository;

import com.railsafe.entity.GateTrainMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GateTrainMappingRepository extends JpaRepository<GateTrainMapping, Long> {
    List<GateTrainMapping> findByGateIdAndActiveTrue(Long gateId);
}
