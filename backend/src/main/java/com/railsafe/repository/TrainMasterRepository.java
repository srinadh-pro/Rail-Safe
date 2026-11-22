package com.railsafe.repository;

import com.railsafe.entity.TrainMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainMasterRepository extends JpaRepository<TrainMaster, Long> {

    Optional<TrainMaster> findByTrainNumberIgnoreCase(String trainNumber);

    boolean existsByTrainNumberIgnoreCase(String trainNumber);

    List<TrainMaster> findAllByOrderByTrainNumberAsc();
}
