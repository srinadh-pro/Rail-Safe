package com.railsafe.repository;

import com.railsafe.entity.TrainTrackingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrainTrackingRepository extends JpaRepository<TrainTrackingRecord, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    Optional<TrainTrackingRecord> findByDedupeKey(String dedupeKey);

    @Query("""
            select t from TrainTrackingRecord t
            join fetch t.gate g
            join fetch t.train m
            order by g.gateCode asc, t.slotIndex asc
            """)
    List<TrainTrackingRecord> findAllWithGateAndTrain();

    @Query("""
            select t from TrainTrackingRecord t
            join fetch t.gate g
            join fetch t.train m
            where lower(t.trainNumber) = lower(:trainNumber)
            order by g.gateCode asc, t.slotIndex asc
            """)
    List<TrainTrackingRecord> findByTrainNumberWithGate(@Param("trainNumber") String trainNumber);

    @Query("""
            select t from TrainTrackingRecord t
            join fetch t.gate g
            join fetch t.train m
            where g.id = :gateId
            order by t.slotIndex asc
            """)
    List<TrainTrackingRecord> findByGateIdWithTrain(@Param("gateId") Long gateId);

    long countByGateId(Long gateId);

    void deleteByGateId(Long gateId);

    void deleteByTrainId(Long trainId);
}
