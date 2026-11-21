package com.railsafe.repository;

import com.railsafe.entity.RailwayCrossing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RailwayCrossingRepository extends JpaRepository<RailwayCrossing, Long> {
    List<RailwayCrossing> findAllByOrderByCrossingCodeAsc();

    Optional<RailwayCrossing> findByCrossingCodeIgnoreCase(String crossingCode);
}
