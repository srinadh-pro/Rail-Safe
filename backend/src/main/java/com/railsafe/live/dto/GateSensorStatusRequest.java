package com.railsafe.live.dto;

import com.railsafe.entity.GateStatus;

/**
 * Optional authorised gate-sensor webhook. The endpoint is disabled unless
 * railsafe.live.sensor-api-key is configured, and the caller must send that key.
 */
public record GateSensorStatusRequest(
        Long gateId,
        String gateCode,
        GateStatus status,
        String note,
        String observedAt
) {
}
