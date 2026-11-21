package com.railsafe.controller;

import com.railsafe.dto.MessageResponse;
import com.railsafe.dto.NotificationResponse;
import com.railsafe.dto.UnreadCountResponse;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                           @AuthenticationPrincipal AppUserPrincipal principal) {
        return notificationService.listForUser(principal.getId(), unreadOnly);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AppUserPrincipal principal) {
        return new UnreadCountResponse(notificationService.unreadCount(principal.getId()));
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id,
                                         @AuthenticationPrincipal AppUserPrincipal principal) {
        return notificationService.markRead(id, principal.getId());
    }

    @PutMapping("/read-all")
    public MessageResponse markAllRead(@AuthenticationPrincipal AppUserPrincipal principal) {
        int count = notificationService.markAllRead(principal.getId());
        return new MessageResponse(count + " notification(s) marked as read.");
    }
}
