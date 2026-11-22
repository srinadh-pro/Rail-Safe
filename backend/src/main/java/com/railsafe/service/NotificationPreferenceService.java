package com.railsafe.service;

import com.railsafe.dto.NotificationPreferenceRequest;
import com.railsafe.dto.NotificationPreferenceResponse;
import com.railsafe.entity.NotificationPreference;
import com.railsafe.entity.User;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.repository.NotificationPreferenceRepository;
import com.railsafe.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final AlertDeliveryService alertDeliveryService;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository,
                                         UserRepository userRepository,
                                         AlertDeliveryService alertDeliveryService) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.alertDeliveryService = alertDeliveryService;
    }

    @Transactional
    public NotificationPreferenceResponse get(Long userId) {
        NotificationPreference preference = getOrCreate(userId);
        return toResponse(preference);
    }

    @Transactional
    public NotificationPreferenceResponse update(Long userId, NotificationPreferenceRequest request) {
        NotificationPreference preference = getOrCreate(userId);
        preference.setBrowserEnabled(request.browserEnabled());
        preference.setEmailEnabled(request.emailEnabled());
        preference.setSmsEnabled(request.smsEnabled());
        preferenceRepository.save(preference);
        return toResponse(preference);
    }

    @Transactional
    public NotificationPreference getOrCreate(Long userId) {
        return preferenceRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found."));
            NotificationPreference preference = new NotificationPreference();
            preference.setUser(user);
            return preferenceRepository.save(preference);
        });
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                preference.isBrowserEnabled(),
                preference.isEmailEnabled(),
                preference.isSmsEnabled(),
                alertDeliveryService.isEmailConfigured(),
                alertDeliveryService.isSmsConfigured()
        );
    }
}
