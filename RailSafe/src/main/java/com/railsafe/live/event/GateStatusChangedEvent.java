package com.railsafe.live.event;

import java.time.Instant;

/** Published after a verified gate status change so SSE subscribers can refresh. */
public record GateStatusChangedEvent(
        Long gateId,
        String gateCode,
        String previousStatus,
        String newStatus,
        Instant changedAt
) {
}
