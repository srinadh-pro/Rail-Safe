package com.railsafe.controller;

import com.railsafe.dto.NotificationPreferenceRequest;
import com.railsafe.dto.NotificationPreferenceResponse;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.NotificationPreferenceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications/preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public NotificationPreferenceResponse get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return preferenceService.get(principal.getId());
    }

    @PutMapping
    public NotificationPreferenceResponse update(@RequestBody NotificationPreferenceRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        return preferenceService.update(principal.getId(), request);
    }
}
