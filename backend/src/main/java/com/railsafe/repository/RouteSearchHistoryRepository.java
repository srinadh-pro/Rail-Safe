package com.railsafe.repository;

import com.railsafe.entity.RouteSearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteSearchHistoryRepository extends JpaRepository<RouteSearchHistory, Long> {
}
