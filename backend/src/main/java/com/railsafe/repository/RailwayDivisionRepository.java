package com.railsafe.repository;

import com.railsafe.entity.RailwayDivision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RailwayDivisionRepository extends JpaRepository<RailwayDivision, String> {
    List<RailwayDivision> findByZoneCodeOrderByNameAsc(String zoneCode);
}
