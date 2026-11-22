package com.railsafe.service;

import com.railsafe.dto.GateResponse;
import com.railsafe.entity.GateStatus;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.VerificationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Converts a RailwayGate entity into the safe JSON object used by the frontend.
 * This class is the single place that decides whether a status is stale or
 * sufficiently verified to be shown as an operational gate state.
 */
@Component
public class GateMapper {

    @Value("${railsafe.gate.stale-minutes:120}")
    private long staleMinutes;

    public GateResponse toResponse(RailwayGate gate) {
        return toResponse(gate, null);
    }

    public GateResponse toResponse(RailwayGate gate, Double distanceKm) {
        boolean stale = isStale(gate);
        GateStatus publicStatus = publicStatus(gate, stale);
        String updatedBy = gate.getUpdatedBy() == null ? null : gate.getUpdatedBy().getFullName();

        return new GateResponse(
                gate.getId(),
                gate.getGateCode(),
                gate.getName(),
                gate.getLatitude(),
                gate.getLongitude(),
                gate.getLocation(),
                gate.getRailwayZone(),
                gate.getDivision(),
                gate.getStation(),
                gate.getDistrict(),
                gate.getState(),
                gate.getGateType(),
                gate.getAssociatedRoutes(),
                publicStatus,
                publicStatus,
                stale,
                gate.getDataClassification(),
                gate.getStatusSource(),
                gate.getVerificationStatus(),
                gate.getLastUpdatedAt(),
                gate.getStatusChangedAt(),
                gate.getExpectedClosingAt(),
                gate.getExpectedOpeningAt(),
                gate.getActualClosingAt(),
                gate.getActualOpeningAt(),
                updatedBy,
                gate.getNotes(),
                distanceKm
        );
    }

    /**
     * Public status never exposes the literal UNKNOWN value. A record is only shown
     * with an operational status when it is verified and fresh.
     */
    public GateStatus publicStatus(RailwayGate gate) {
        return publicStatus(gate, isStale(gate));
    }

    private GateStatus publicStatus(RailwayGate gate, boolean stale) {
        GateStatus stored = gate.getStatus();
        if (stored == null || stored == GateStatus.UNKNOWN
                || stored == GateStatus.AWAITING_VERIFICATION
                || gate.getVerificationStatus() != VerificationStatus.VERIFIED
                || stale) {
            return GateStatus.AWAITING_VERIFICATION;
        }
        return stored;
    }

    public boolean isStale(RailwayGate gate) {
        if (gate.getLastUpdatedAt() == null) {
            return true;
        }
        return gate.getLastUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(staleMinutes));
    }
}
