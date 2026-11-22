package com.railsafe.repository;

import com.railsafe.entity.TrainSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface TrainSnapshotRepository extends JpaRepository<TrainSnapshot, Long> {

    List<TrainSnapshot> findTop200ByOrderByRecordedAtDesc();

    long countByProvider(String provider);

    @Modifying
    @Transactional
    @Query("delete from TrainSnapshot snapshot where snapshot.recordedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
