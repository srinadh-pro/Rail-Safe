package com.railsafe.dto;

import com.railsafe.entity.DataClassification;
import com.railsafe.entity.GateStatus;
import com.railsafe.entity.StatusSource;
import com.railsafe.entity.VerificationStatus;

import java.time.LocalDateTime;

/**
 * Gate view used by all dashboards.
 *
 * {@code status} and {@code displayStatus} are both public, display-safe values.
 * Stored legacy {@code UNKNOWN} values and unverified records are mapped to
 * {@code AWAITING_VERIFICATION}. The raw stored value remains in the database and
 * in gate history.
 */
public record GateResponse(
        Long id,
        String gateCode,
        String name,
        Double latitude,
        Double longitude,
        String location,
        String railwayZone,
        String division,
        String station,
        String district,
        String state,
        String gateType,
        String associatedRoutes,
        GateStatus status,
        GateStatus displayStatus,
        boolean stale,
        DataClassification dataClassification,
        StatusSource statusSource,
        VerificationStatus verificationStatus,
        LocalDateTime lastUpdatedAt,
        LocalDateTime statusChangedAt,
        LocalDateTime expectedClosingAt,
        LocalDateTime expectedOpeningAt,
        LocalDateTime actualClosingAt,
        LocalDateTime actualOpeningAt,
        String lastUpdatedBy,
        String notes,
        Double distanceKm
) {
}
