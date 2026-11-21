package com.railsafe.repository;

import com.railsafe.entity.SavedRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedRouteRepository extends JpaRepository<SavedRoute, Long> {

    List<SavedRoute> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SavedRoute> findByIdAndUserId(Long id, Long userId);
}
