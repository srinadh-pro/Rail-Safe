package com.railsafe.dto;

public record NotificationPreferenceRequest(
        boolean browserEnabled,
        boolean emailEnabled,
        boolean smsEnabled
) {
}
