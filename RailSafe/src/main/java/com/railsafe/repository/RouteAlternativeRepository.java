package com.railsafe.repository;

import com.railsafe.entity.RouteAlternative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface RouteAlternativeRepository extends JpaRepository<RouteAlternative, Long> {

    List<RouteAlternative> findTop50ByGateIdOrderByGeneratedAtDesc(Long gateId);

    /** Synthetic demo routes for a gate, shortest first. */
    List<RouteAlternative> findByGateIdAndSyntheticTrueOrderByDistanceKmAsc(Long gateId);

    /** Genuine calculated routes for a gate, newest first. */
    List<RouteAlternative> findTop50ByGateIdAndSyntheticFalseOrderByGeneratedAtDesc(Long gateId);

    boolean existsByRouteKey(String routeKey);

    void deleteByGateId(Long gateId);

    @Modifying
    @Transactional
    @Query("delete from RouteAlternative route where route.generatedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
