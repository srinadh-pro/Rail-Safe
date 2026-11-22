package com.railsafe.service;

import com.railsafe.dto.NotificationResponse;
import com.railsafe.entity.*;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.repository.NotificationPreferenceRepository;
import com.railsafe.repository.NotificationRepository;
import com.railsafe.repository.RouteSearchHistoryRepository;
import com.railsafe.repository.SavedRouteRepository;
import com.railsafe.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates in-app notifications and handles read/unread state.
 *
 * The dedupeKey unique constraint is the final protection against duplicate alerts.
 * Gate changes target users who saved or recently searched a route containing the
 * crossing; if nobody is watching it, all enabled recipients are notified.
 */
@Service
public class NotificationService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    /** All authenticated roles may receive operational notifications for watched gates. */
    private static final List<Role> NOTIFICATION_ROLES = List.of(
            Role.CUSTOMER, Role.USER, Role.STAFF, Role.ADMIN, Role.SUPER_ADMIN);
    private static final String OPERATIONAL_DATA_SOURCE = "APPLICATION";
    private static final String OPERATIONAL_DATA_MODE = "REAL_API";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SavedRouteRepository savedRouteRepository;
    private final RouteSearchHistoryRepository routeSearchHistoryRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final AlertDeliveryService alertDeliveryService;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               SavedRouteRepository savedRouteRepository,
                               RouteSearchHistoryRepository routeSearchHistoryRepository,
                               NotificationPreferenceRepository preferenceRepository,
                               AlertDeliveryService alertDeliveryService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.savedRouteRepository = savedRouteRepository;
        this.routeSearchHistoryRepository = routeSearchHistoryRepository;
        this.preferenceRepository = preferenceRepository;
        this.alertDeliveryService = alertDeliveryService;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(Long userId, boolean unreadOnly) {
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return notifications.stream()
                .filter(notification -> !DemoDataClassifier.isDemoNotification(notification))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId).stream()
                .filter(notification -> !DemoDataClassifier.isDemoNotification(notification))
                .count();
    }

    @Transactional
    public NotificationResponse markRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .filter(found -> !DemoDataClassifier.isDemoNotification(found))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        notification.setRead(true);
        notificationRepository.save(notification);
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        List<Notification> unread = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId).stream()
                .filter(notification -> !DemoDataClassifier.isDemoNotification(notification))
                .toList();
        unread.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public void notifyGateStatusChange(RailwayGate gate, GateStatus oldStatus, GateStatus newStatus) {
        if (DemoDataClassifier.isDemoGate(gate)) {
            return;
        }
        // Users who saved or recently searched a route through this crossing are
        // notified first. If nobody is watching the gate, keep the existing demo
        // behaviour and notify all enabled recipients.
        List<User> recipients = recipientsForGate(gate);
        if (recipients.isEmpty()) {
            return;
        }

        String source = sourceLabel(gate.getStatusSource());
        String updated = gate.getLastUpdatedAt() == null
                ? "time not available"
                : gate.getLastUpdatedAt().format(TIME_FORMAT);

        String title;
        NotificationType type;
        switch (newStatus) {
            case CLOSED -> {
                title = "Railway gate reported CLOSED";
                type = NotificationType.GATE_CLOSED;
            }
            case OPEN -> {
                title = "Railway gate reported OPEN";
                type = NotificationType.GATE_OPENED;
            }
            case CLOSING -> {
                title = "Railway gate is CLOSING";
                type = NotificationType.GATE_STATUS_UPDATED;
            }
            case OPENING -> {
                title = "Railway gate is OPENING";
                type = NotificationType.GATE_STATUS_UPDATED;
            }
            case MAINTENANCE -> {
                title = "Railway gate is under MAINTENANCE";
                type = NotificationType.GATE_MAINTENANCE;
            }
            default -> {
                title = "Railway gate status is awaiting verification";
                type = NotificationType.GATE_UNKNOWN;
            }
        }

        String message = gate.getName() + " (" + gate.getGateCode() + ") was reported "
                + newStatus + " via " + source + ". Last updated: " + updated
                + ". This is reported information, not a live railway signal. Always obey physical barriers.";
        if (message.length() > 500) {
            message = message.substring(0, 497) + "...";
        }

        long eventMillis = gate.getLastUpdatedAt() == null
                ? System.currentTimeMillis()
                : gate.getLastUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        for (User recipient : recipients) {
            String dedupeKey = "u" + recipient.getId() + ":gate:" + gate.getId()
                    + ":" + newStatus + ":" + eventMillis;
            if (notificationRepository.existsByDedupeKey(dedupeKey)) {
                continue;
            }
            Notification notification = new Notification();
            notification.setUser(recipient);
            notification.setGate(gate);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType(type);
            notification.setStatusAtEvent(newStatus);
            notification.setDedupeKey(dedupeKey);
            notification.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
            deliverOptionalChannels(recipient, title, message);
        }
    }

    /**
     * Creates a live-event notification for users watching this gate. If nobody has
     * saved a route containing the gate, the existing demo behaviour is kept and all
     * enabled USER accounts are notified.
     *
     * @return the number of new in-app notifications created
     */
    @Transactional
    public int notifyLiveEvent(RailwayGate gate,
                               NotificationType type,
                               String title,
                               String message,
                               String dedupeSuffix,
                               GateStatus statusAtEvent) {
        if (DemoDataClassifier.isDemoGate(gate)) {
            return 0;
        }
        List<User> recipients = recipientsForGate(gate);
        int created = 0;
        for (User recipient : recipients) {
            if (createLiveNotification(recipient, gate, type, title, message, dedupeSuffix, statusAtEvent)) {
                created++;
            }
        }
        return created;
    }

    @Transactional
    public boolean notifyLiveEventForUser(User user,
                                          RailwayGate gate,
                                          NotificationType type,
                                          String title,
                                          String message,
                                          String dedupeSuffix,
                                          GateStatus statusAtEvent) {
        if (DemoDataClassifier.isDemoGate(gate)) {
            return false;
        }
        return createLiveNotification(user, gate, type, title, message, dedupeSuffix, statusAtEvent);
    }

    @Transactional
    public boolean notifyLiveEventForUser(Long userId,
                                          RailwayGate gate,
                                          NotificationType type,
                                          String title,
                                          String message,
                                          String dedupeSuffix,
                                          GateStatus statusAtEvent) {
        if (userId == null || DemoDataClassifier.isDemoGate(gate)) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> createLiveNotification(user, gate, type, title, message,
                        dedupeSuffix, statusAtEvent))
                .orElse(false);
    }

    private boolean createLiveNotification(User recipient,
                                           RailwayGate gate,
                                           NotificationType type,
                                           String title,
                                           String message,
                                           String dedupeSuffix,
                                           GateStatus statusAtEvent) {
        if (DemoDataClassifier.isDemoGate(gate)) {
            return false;
        }
        String dedupeKey = truncate("u" + recipient.getId() + ":" + dedupeSuffix, 120);
        if (notificationRepository.existsByDedupeKey(dedupeKey)) {
            return false;
        }

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setGate(gate);
        notification.setType(type);
        notification.setTitle(truncate(title, 160));
        notification.setMessage(truncate(message, 500));
        notification.setStatusAtEvent(statusAtEvent);
        notification.setDedupeKey(dedupeKey);
        notification.setDataSource(OPERATIONAL_DATA_SOURCE);
        notification.setDataMode(OPERATIONAL_DATA_MODE);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
        deliverOptionalChannels(recipient, notification.getTitle(), notification.getMessage());
        return true;
    }

    private List<User> recipientsForGate(RailwayGate gate) {
        List<User> users = userRepository.findByRoleInAndEnabledTrueOrderByFullNameAsc(NOTIFICATION_ROLES);
        if (users.isEmpty() || gate.getId() == null) {
            return users;
        }

        Set<Long> watchers = new HashSet<>();
        for (SavedRoute route : savedRouteRepository.findAll()) {
            if (DemoDataClassifier.isDemoSavedRoute(route, Set.of(gate.getId()))) {
                continue;
            }
            if (route.getUser() != null && routeContainsGate(route.getGateIds(), gate.getId())) {
                watchers.add(route.getUser().getId());
            }
        }
        // Route-search users are notified when a crossing on their searched route
        // changes. These are real DB search records, never simulated positions.
        for (RouteSearchHistory search : routeSearchHistoryRepository.findAll()) {
            if (search.getUserId() != null && routeContainsGate(search.getGateIds(), gate.getId())) {
                watchers.add(search.getUserId());
            }
        }
        if (watchers.isEmpty()) {
            return users;
        }
        return users.stream().filter(user -> watchers.contains(user.getId())).toList();
    }

    private static boolean routeContainsGate(String gateIds, Long gateId) {
        if (gateIds == null || gateIds.isBlank()) {
            return false;
        }
        String target = String.valueOf(gateId);
        for (String value : gateIds.split(",")) {
            if (value.trim().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private void deliverOptionalChannels(User user, String title, String message) {
        preferenceRepository.findByUserId(user.getId())
                .ifPresent(preference -> alertDeliveryService.deliver(user, preference, title, message));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    @Transactional
    public void notifyReportDecision(UserReport report, boolean approved) {
        if (DemoDataClassifier.isDemoReport(report)) {
            return;
        }
        User recipient = report.getUser();
        String dedupeKey = "u" + recipient.getId() + ":report:" + report.getId() + ":" + report.getStatus().name();
        if (notificationRepository.existsByDedupeKey(dedupeKey)) {
            return;
        }

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setGate(report.getGate());
        notification.setType(NotificationType.REPORT);
        notification.setStatusAtEvent(report.getReportedStatus());
        notification.setDedupeKey(dedupeKey);
        notification.setDataSource(OPERATIONAL_DATA_SOURCE);
        notification.setDataMode(OPERATIONAL_DATA_MODE);
        notification.setCreatedAt(LocalDateTime.now());

        if (approved) {
            notification.setTitle("Your gate report was approved");
            notification.setMessage("Staff reviewed your report for " + report.getGate().getName()
                    + ". The gate status was updated to " + report.getReportedStatus()
                    + ". Thank you for helping other commuters.");
        } else {
            notification.setTitle("Your gate report was not approved");
            notification.setMessage("Staff reviewed your report for " + report.getGate().getName()
                    + " but did not apply it. The existing gate information remains unchanged.");
        }

        notificationRepository.save(notification);
    }

    private NotificationResponse toResponse(Notification notification) {
        RailwayGate gate = notification.getGate();
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.isRead(),
                gate == null ? null : gate.getId(),
                gate == null ? null : gate.getGateCode(),
                gate == null ? null : gate.getName(),
                notification.getStatusAtEvent(),
                notification.getCreatedAt(),
                notification.getDataSource() == null ? OPERATIONAL_DATA_SOURCE : notification.getDataSource(),
                notification.getDataMode() == null ? OPERATIONAL_DATA_MODE : notification.getDataMode()
        );
    }

    private static String sourceLabel(StatusSource source) {
        return switch (source) {
            case STAFF -> "authorised staff";
            case SENSOR -> "an authorised sensor";
            case REFERENCE -> "a static reference record";
            case DEMO -> "an unauthorised source";
        };
    }
}
