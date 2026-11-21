package com.railsafe.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete gate detail view. Timestamps are authoritative; the browser derives
 * per-second durations from {@code statusSince} rather than from a fake timer.
 */
public record GateDetailResponse(
        Long id,
        String gateId,
        String gateName,
        String railwayStation,
        String district,
        String state,
        String railwayZone,
        String division,
        Double latitude,
        Double longitude,
        String gateType,
        String status,
        String verificationStatus,
        String dataSource,
        String dataClassification,
        LocalDateTime lastUpdatedAt,
        LocalDateTime statusSince,
        long currentDurationSeconds,
        LocalDateTime expectedClosingAt,
        LocalDateTime expectedOpeningAt,
        LocalDateTime actualClosingAt,
        LocalDateTime actualOpeningAt,
        String expectedClosingSource,
        String expectedOpeningSource,
        String nextTrainNumber,
        String nextTrainName,
        String nextTrainDirection,
        LocalDateTime nextTrainEta,
        Double nextTrainDistanceKm,
        String nextTrainStatus,
        List<String> responsibleStaff,
        String associatedRoutes,
        String note
) {
}
