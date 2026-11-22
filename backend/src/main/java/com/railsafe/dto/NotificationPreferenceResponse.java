package com.railsafe.dto;

public record NotificationPreferenceResponse(
        boolean browserEnabled,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean emailChannelConfigured,
        boolean smsChannelConfigured
) {
}
