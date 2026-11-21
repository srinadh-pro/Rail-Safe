package com.railsafe.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SavedRouteResponse(
        Long id,
        String name,
        String startLabel,
        Double startLat,
        Double startLng,
        String destLabel,
        Double destLat,
        Double destLng,
        List<Long> gateIds,
        LocalDateTime createdAt
) {
}
