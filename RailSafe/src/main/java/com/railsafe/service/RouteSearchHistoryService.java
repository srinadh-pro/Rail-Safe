package com.railsafe.service;

import com.railsafe.entity.DataClassification;
import com.railsafe.entity.RouteSearchHistory;
import com.railsafe.live.dto.AlternativeRouteRequest;
import com.railsafe.live.dto.AlternativeRouteResponse;
import com.railsafe.repository.RouteSearchHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records route searches for audit/analytics without inventing route data. */
@Service
public class RouteSearchHistoryService {

    private final RouteSearchHistoryRepository repository;

    public RouteSearchHistoryService(RouteSearchHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(Long userId, AlternativeRouteRequest request, AlternativeRouteResponse response) {
        RouteSearchHistory history = new RouteSearchHistory();
        history.setUserId(userId);
        history.setStartLat(request.startLat());
        history.setStartLng(request.startLng());
        history.setEndLat(request.destLat());
        history.setEndLng(request.destLng());
        history.setStartLabel("User supplied start");
        history.setEndLabel("User supplied destination");
        history.setGateIds(String.valueOf(request.gateId()));
        history.setRouteCount(response.available() && response.alternatives() != null
                ? response.alternatives().size() : 0);
        if (response.original() != null) {
            history.setDistanceKm(response.original().distanceKm());
            history.setDurationMinutes(response.original().durationMinutes());
        }
        history.setDataClassification(DataClassification.VERIFIED_REFERENCE);
        repository.save(history);
    }
}
