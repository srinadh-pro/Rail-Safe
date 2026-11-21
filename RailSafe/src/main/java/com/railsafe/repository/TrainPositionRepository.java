package com.railsafe.repository;

import com.railsafe.entity.TrainPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainPositionRepository extends JpaRepository<TrainPosition, Long> {
    List<TrainPosition> findByTrainNumberIgnoreCaseOrderByObservedAtDesc(String trainNumber);
}
