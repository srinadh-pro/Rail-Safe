package com.railsafe.dto;

/** Safe runtime toggles. Secrets and network endpoints are not editable here. */
public record ConfigUpdateRequest(Boolean alertsEnabled, Boolean simulationNotifications) {
}
