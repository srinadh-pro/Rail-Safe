package com.railsafe.repository;

import com.railsafe.entity.TrainSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainScheduleRepository extends JpaRepository<TrainSchedule, Long> {
    List<TrainSchedule> findByTrainNumberIgnoreCaseOrderBySequenceAsc(String trainNumber);
}
