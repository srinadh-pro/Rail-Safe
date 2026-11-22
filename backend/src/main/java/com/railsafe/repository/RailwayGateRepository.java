package com.railsafe.repository;

import com.railsafe.entity.GateStatus;
import com.railsafe.entity.RailwayGate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RailwayGateRepository extends JpaRepository<RailwayGate, Long> {

    Optional<RailwayGate> findByGateCodeIgnoreCase(String gateCode);

    boolean existsByGateCodeIgnoreCase(String gateCode);

    List<RailwayGate> findAllByOrderByNameAsc();

    long countByStatus(GateStatus status);

    @Query("""
            select g from RailwayGate g
            where lower(g.name) like lower(concat('%', :q, '%'))
               or lower(g.location) like lower(concat('%', :q, '%'))
               or lower(g.gateCode) like lower(concat('%', :q, '%'))
            order by g.name asc
            """)
    List<RailwayGate> search(@Param("q") String q);

    /**
     * Cheap bounding-box search using the (latitude, longitude) index.
     * The service then calculates exact Haversine distances and filters by radius.
     */
    @Query(value = """
            SELECT * FROM railway_gates
            WHERE latitude BETWEEN :minLat AND :maxLat
              AND longitude BETWEEN :minLng AND :maxLng
            """, nativeQuery = true)
    List<RailwayGate> findInBoundingBox(@Param("minLat") double minLat,
                                        @Param("maxLat") double maxLat,
                                        @Param("minLng") double minLng,
                                        @Param("maxLng") double maxLng);
}
