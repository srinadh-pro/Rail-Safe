package com.railsafe.service;

import com.railsafe.dto.GateOverrideRequest;
import com.railsafe.dto.GateRequest;
import com.railsafe.dto.GateResponse;
import com.railsafe.dto.GateVerificationRequest;
import com.railsafe.dto.StatusHistoryResponse;
import com.railsafe.dto.StatusUpdateRequest;
import com.railsafe.config.LiveProperties;
import com.railsafe.entity.*;
import com.railsafe.exception.BadRequestException;
import com.railsafe.exception.ConflictException;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.live.dto.GatePredictionResponse;
import com.railsafe.live.dto.GateSensorStatusRequest;
import com.railsafe.live.dto.GateTrainResponse;
import com.railsafe.live.dto.TrainTrackingResponse;
import com.railsafe.live.event.GateStatusChangedEvent;
import com.railsafe.live.service.GatePredictionService;
import com.railsafe.live.service.TrainTrackingService;
import com.railsafe.repository.*;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.util.GeoUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core gate rules:
 *  - nearby search uses a bounding box plus exact Haversine distance;
 *  - metadata edits are separate from status updates;
 *  - every status change writes an audit history row;
 *  - a status change notifies users through NotificationService;
 *  - only statuses changed by STAFF/ADMIN can become VERIFIED.
 */
@Service
public class GateService {

    private final RailwayGateRepository gateRepository;
    private final GateStatusHistoryRepository historyRepository;
    private final NotificationRepository notificationRepository;
    private final UserReportRepository reportRepository;
    private final UserRepository userRepository;
    private final GateMapper gateMapper;
    private final NotificationService notificationService;
    private final TrainTrackingService trainTrackingService;
    private final GatePredictionService gatePredictionService;
    private final LiveProperties liveProperties;
    private final StaffAssignmentRepository assignmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    public GateService(RailwayGateRepository gateRepository,
                       GateStatusHistoryRepository historyRepository,
                       NotificationRepository notificationRepository,
                       UserReportRepository reportRepository,
                       UserRepository userRepository,
                       GateMapper gateMapper,
                       NotificationService notificationService,
                       TrainTrackingService trainTrackingService,
                       GatePredictionService gatePredictionService,
                       LiveProperties liveProperties,
                        StaffAssignmentRepository assignmentRepository,
                        ApplicationEventPublisher eventPublisher,
                        AuditLogService auditLogService) {
        this.gateRepository = gateRepository;
        this.historyRepository = historyRepository;
        this.notificationRepository = notificationRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.gateMapper = gateMapper;
        this.notificationService = notificationService;
        this.trainTrackingService = trainTrackingService;
        this.gatePredictionService = gatePredictionService;
        this.liveProperties = liveProperties;
        this.assignmentRepository = assignmentRepository;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<GateResponse> list(String search, GateStatus status) {
        return list(search, status, null);
    }

    /** Staff callers only ever receive their actively assigned crossings. */
    @Transactional(readOnly = true)
    public List<GateResponse> list(String search, GateStatus status, AppUserPrincipal principal) {
        List<RailwayGate> gates = (search == null || search.isBlank())
                ? gateRepository.findAllByOrderByNameAsc()
                : gateRepository.search(search.trim());

        Set<Long> assignedIds = assignedGateIds(principal);
        return gates.stream()
                .filter(gate -> !DemoDataClassifier.isDemoGate(gate))
                .filter(gate -> assignedIds == null || assignedIds.contains(gate.getId()))
                .map(gateMapper::toResponse)
                .filter(response -> status == null || response.displayStatus() == status)
                .toList();
    }

    @Transactional(readOnly = true)
    public GateResponse get(Long id) {
        return get(id, null);
    }

    /** Gate read with STAFF assignment scope enforced server-side. */
    @Transactional(readOnly = true)
    public GateResponse get(Long id, AppUserPrincipal principal) {
        ensureReadAccess(principal, id);
        return gateMapper.toResponse(findGate(id));
    }

    /** Read-only entity access for the additive live layer. */
    @Transactional(readOnly = true)
    public RailwayGate requireGate(Long id) {
        return findGate(id);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(Long id) {
        return history(id, null);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(Long id, AppUserPrincipal principal) {
        ensureReadAccess(principal, id);
        findGate(id);
        return toHistoryResponses(historyRepository.findByGateIdOrderByChangedAtDesc(id));
    }

    /** Only the signed-in STAFF member's own gate changes. */
    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> historyForStaff(Long staffUserId) {
        return toHistoryResponses(
                historyRepository.findByChangedByIdOrderByChangedAtDesc(staffUserId));
    }

    /** Complete status-change history recorded by any STAFF account. */
    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> historyForStaffRole() {
        return toHistoryResponses(
                historyRepository.findByChangedByRoleOrderByChangedAtDesc(Role.STAFF));
    }

    private List<StatusHistoryResponse> toHistoryResponses(List<GateStatusHistory> entries) {
        return entries.stream()
                .filter(history -> !DemoDataClassifier.isDemoHistory(history))
                .map(history -> new StatusHistoryResponse(
                        history.getId(),
                        history.getGate() == null ? null : history.getGate().getId(),
                        history.getGate() == null ? null : history.getGate().getGateCode(),
                        history.getOldStatus(),
                        history.getNewStatus(),
                        history.getSource(),
                        history.getNote(),
                        history.getChangedAt(),
                        history.getChangedBy() == null ? "System" : history.getChangedBy().getFullName(),
                        history.getChangedBy() == null ? null : history.getChangedBy().getId(),
                        historyAction(history)
                ))
                .toList();
    }

    private static String historyAction(GateStatusHistory history) {
        if (history.getSource() == StatusSource.SENSOR) {
            return "SENSOR_STATUS_UPDATE";
        }
        if (history.getSource() == StatusSource.DEMO) {
            return "DEMO_STATUS_UPDATE";
        }
        User actor = history.getChangedBy();
        if (actor != null && actor.getRole() == Role.STAFF) {
            return "STAFF_STATUS_UPDATE";
        }
        if (actor != null && actor.getRole() == Role.ADMIN) {
            return "ADMIN_STATUS_UPDATE";
        }
        if (actor != null && actor.getRole() == Role.SUPER_ADMIN) {
            return "SUPER_ADMIN_STATUS_UPDATE";
        }
        return "GATE_STATUS_UPDATE";
    }

    /** Live trains matched to this gate, flat shape for external consumers. */
    @Transactional(readOnly = true)
    public List<GateTrainResponse> trains(Long id) {
        return trainTrackingService.trainsForGate(findGate(id));
    }

    /** Countdown, next-train and verified-status prediction for one gate. */
    @Transactional(readOnly = true)
    public GatePredictionResponse prediction(Long id) {
        return gatePredictionService.predict(findGate(id));
    }

    /**
     * Optional authorised gate-sensor webhook.
     *
     * The endpoint stays disabled while railsafe.live.sensor-api-key is blank. A
     * reading older than 15 minutes is rejected instead of being presented as a
     * fresh gate status.
     */
    @Transactional
    public GateResponse ingestSensorStatus(Long id, GateSensorStatusRequest request, String suppliedApiKey) {
        String configuredKey = liveProperties.getSensorApiKey();
        if (configuredKey == null || configuredKey.isBlank()
                || suppliedApiKey == null || suppliedApiKey.isBlank()
                || !constantTimeEquals(configuredKey, suppliedApiKey)) {
            throw new ResourceNotFoundException("The gate sensor endpoint is not available.");
        }
        if (request == null || request.status() == null) {
            throw new BadRequestException("A sensor status of OPEN, CLOSED or UNKNOWN is required.");
        }

        RailwayGate gate = findGate(id);
        if (request.gateCode() != null && !request.gateCode().isBlank()
                && !request.gateCode().equalsIgnoreCase(gate.getGateCode())) {
            throw new BadRequestException("The supplied gateCode does not match this gate.");
        }

        Instant observedAt = parseObservedAt(request.observedAt());
        if (observedAt == null) {
            throw new BadRequestException("observedAt must be an ISO-8601 instant, for example 2026-11-06T10:15:30Z.");
        }
        if (observedAt.isBefore(Instant.now().minusSeconds(900))) {
            throw new BadRequestException("The sensor reading is older than 15 minutes and was not applied.");
        }
        if (observedAt.isAfter(Instant.now().plusSeconds(120))) {
            throw new BadRequestException("The sensor reading is in the future and was not applied.");
        }

        String note = request.note() == null || request.note().isBlank()
                ? "Authorised sensor reading."
                : "Authorised sensor reading: " + request.note().trim();
        applyStatusChange(gate, request.status(), StatusSource.SENSOR,
                VerificationStatus.VERIFIED, null, note);
        return gateMapper.toResponse(gate);
    }

    private static Instant parseObservedAt(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        String text = value.trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // Try the next supported representation.
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // Try a local timestamp with the server time zone.
        }
        try {
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(readOnly = true)
    public List<GateResponse> nearby(double lat, double lng, double radiusKm) {
        return nearby(lat, lng, radiusKm, null);
    }

    /** Nearby search stays scoped for STAFF callers. */
    @Transactional(readOnly = true)
    public List<GateResponse> nearby(double lat, double lng, double radiusKm, AppUserPrincipal principal) {
        double latDelta = radiusKm / 111.0;
        double cosLat = Math.max(0.01, Math.cos(Math.toRadians(lat)));
        double lngDelta = radiusKm / (111.0 * cosLat);

        List<RailwayGate> candidates = gateRepository.findInBoundingBox(
                Math.max(-90.0, lat - latDelta),
                Math.min(90.0, lat + latDelta),
                lng - lngDelta,
                lng + lngDelta
        );
        Set<Long> assignedIds = assignedGateIds(principal);

        return candidates.stream()
                .filter(gate -> !DemoDataClassifier.isDemoGate(gate))
                .filter(gate -> assignedIds == null || assignedIds.contains(gate.getId()))
                .map(gate -> Map.entry(gate, GeoUtils.haversineKm(lat, lng, gate.getLatitude(), gate.getLongitude())))
                .filter(entry -> entry.getValue() <= radiusKm)
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .map(entry -> gateMapper.toResponse(entry.getKey(),
                        Math.round(entry.getValue() * 100.0) / 100.0))
                .toList();
    }

    @Transactional
    public GateResponse create(GateRequest request, AppUserPrincipal principal) {
        String code = normalizeCode(request.gateCode());
        if (DemoDataClassifier.isDemoCode(code)) {
            throw new BadRequestException("This crossing code is reserved. Use a genuine crossing identifier.");
        }
        if (gateRepository.existsByGateCodeIgnoreCase(code)) {
            throw new ConflictException("A gate with code " + code + " already exists.");
        }

        User actor = findActor(principal);
        LocalDateTime now = LocalDateTime.now();

        RailwayGate gate = new RailwayGate();
        gate.setGateCode(code);
        gate.setName(request.name().trim());
        gate.setLatitude(request.latitude());
        gate.setLongitude(request.longitude());
        gate.setLocation(request.location().trim());
        gate.setNotes(trimToNull(request.notes()));
        gate.setRailwayZone(trimToNull(request.railwayZone()));
        gate.setDivision(trimToNull(request.division()));
        gate.setStation(trimToNull(request.station()));
        gate.setDistrict(trimToNull(request.district()));
        gate.setState(trimToNull(request.state()));
        gate.setGateType(trimToNull(request.gateType()));
        gate.setAssociatedRoutes(trimToNull(request.associatedRoutes()));
        gate.setStatus(GateStatus.AWAITING_VERIFICATION);
        gate.setStatusSource(StatusSource.STAFF);
        gate.setVerificationStatus(VerificationStatus.UNVERIFIED);
        gate.setLastUpdatedAt(now);
        gate.setUpdatedBy(actor);
        gateRepository.save(gate);

        saveHistory(gate, null, GateStatus.UNKNOWN, StatusSource.STAFF, actor,
                "Gate created by staff/admin. Status starts as UNKNOWN until verified.");

        return gateMapper.toResponse(gate);
    }

    @Transactional
    public GateResponse update(Long id, GateRequest request) {
        RailwayGate gate = findGate(id);
        String code = normalizeCode(request.gateCode());
        if (DemoDataClassifier.isDemoCode(code)) {
            throw new BadRequestException("This crossing code is reserved. Use a genuine crossing identifier.");
        }

        gateRepository.findByGateCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("A gate with code " + code + " already exists.");
                });

        gate.setGateCode(code);
        gate.setName(request.name().trim());
        gate.setLatitude(request.latitude());
        gate.setLongitude(request.longitude());
        gate.setLocation(request.location().trim());
        gate.setNotes(trimToNull(request.notes()));
        gate.setRailwayZone(trimToNull(request.railwayZone()));
        gate.setDivision(trimToNull(request.division()));
        gate.setStation(trimToNull(request.station()));
        gate.setDistrict(trimToNull(request.district()));
        gate.setState(trimToNull(request.state()));
        gate.setGateType(trimToNull(request.gateType()));
        gate.setAssociatedRoutes(trimToNull(request.associatedRoutes()));
        gateRepository.save(gate);

        return gateMapper.toResponse(gate);
    }

    @Transactional
    public GateResponse updateStatus(Long id, StatusUpdateRequest request, AppUserPrincipal principal) {
        RailwayGate gate = findGate(id);
        User actor = findActor(principal);
        requireOperationalAccess(principal, gate);

        StatusSource source = request.source() == null ? StatusSource.STAFF : request.source();
        if (source == StatusSource.DEMO) {
            throw new BadRequestException("Only authorised status sources are accepted.");
        }
        // Omission must not silently mark a status as verified. Authorised status
        // sources such as sensors pass VERIFIED explicitly.
        VerificationStatus verification = request.verificationStatus() == null
                ? VerificationStatus.UNVERIFIED
                : request.verificationStatus();
        if (verification == VerificationStatus.DEMO) {
            throw new BadRequestException("Only authorised verification states are accepted.");
        }

        applyStatusChange(gate, request.status(), source, verification, actor, trimToNull(request.note()));
        return gateMapper.toResponse(gate);
    }

    /**
     * Manual verification. The actor explicitly records what they observed at the
     * crossing; the recorded status becomes visible to commuters only once it is
     * VERIFIED and fresh. The gate's source is always STAFF so a verification can
     * never be confused with a sensor feed or with demo data.
     */
    @Transactional
    public GateResponse verifyGate(Long id, GateVerificationRequest request, AppUserPrincipal principal) {
        RailwayGate gate = findGate(id);
        User actor = findActor(principal);
        requireOperationalAccess(principal, gate);

        VerificationStatus verification = request.verificationStatus();
        if (verification == null || verification == VerificationStatus.DEMO) {
            throw new BadRequestException("Choose VERIFIED or UNVERIFIED.");
        }

        GateStatus observed = request.status() == null ? gate.getStatus() : request.status();
        if (verification == VerificationStatus.VERIFIED
                && (observed == null || observed == GateStatus.UNKNOWN
                    || observed == GateStatus.AWAITING_VERIFICATION)) {
            throw new BadRequestException(
                    "Select the observed gate status (OPEN, CLOSED, CLOSING, OPENING or MAINTENANCE) before verifying.");
        }

        String baseNote = trimToNull(request.note());
        String note = "Manual verification by " + actor.getFullName()
                + " — " + verification.name()
                + (baseNote == null ? "" : ": " + baseNote);
        applyStatusChange(gate, observed, StatusSource.STAFF, verification, actor, note);
        return gateMapper.toResponse(gate);
    }

    /**
     * Supervisory override for genuine OPEN/CLOSED corrections. The controller also
     * restricts this endpoint to SUPER_ADMIN; the reason is written to both gate
     * history and the security audit log.
     */
    @Transactional
    public GateResponse overrideGateStatus(Long id, GateOverrideRequest request, AppUserPrincipal principal) {
        if (principal == null || principal.getRole() != Role.SUPER_ADMIN) {
            throw new BadRequestException("Only a Super Admin may override a gate status.");
        }
        if (request.status() != GateStatus.OPEN && request.status() != GateStatus.CLOSED) {
            throw new BadRequestException("A supervisory override can only record OPEN or CLOSED.");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BadRequestException("An authorisation reason is required for an override.");
        }

        RailwayGate gate = findGate(id);
        User actor = findActor(principal);
        String reason = request.reason().trim();
        String note = "SUPER_ADMIN override by " + actor.getFullName() + " — " + reason;
        applyStatusChange(gate, request.status(), StatusSource.STAFF, VerificationStatus.VERIFIED,
                actor, note, "GATE_STATUS_OVERRIDE", "Authorization reason: " + reason);
        return gateMapper.toResponse(gate);
    }

    /**
     * Shared by staff status updates, manual verification and approved reports.
     * The caller must already be inside a transaction.
     */
    @Transactional
    public void applyStatusChange(RailwayGate gate,
                                  GateStatus newStatus,
                                  StatusSource source,
                                  VerificationStatus verification,
                                  User actor,
                                  String note) {
        applyStatusChange(gate, newStatus, source, verification, actor, note,
                "GATE_STATUS_UPDATED", null);
    }

    private void applyStatusChange(RailwayGate gate,
                                   GateStatus newStatus,
                                   StatusSource source,
                                   VerificationStatus verification,
                                   User actor,
                                   String note,
                                   String auditAction,
                                   String auditSuffix) {
        if (newStatus == null) {
            throw new BadRequestException("Status is required.");
        }

        GateStatus oldStatus = gate.getStatus();
        GateStatus oldPublicStatus = gateMapper.publicStatus(gate);
        LocalDateTime now = LocalDateTime.now();

        gate.setStatus(newStatus);
        gate.setStatusSource(source);
        gate.setVerificationStatus(verification);
        gate.setLastUpdatedAt(now);
        gate.setStatusChangedAt(now);
        if (newStatus == GateStatus.CLOSED || newStatus == GateStatus.CLOSING) {
            gate.setActualClosingAt(now);
        }
        if (newStatus == GateStatus.OPEN || newStatus == GateStatus.OPENING) {
            gate.setActualOpeningAt(now);
        }
        // Verification can be withdrawn; do not leave a stale VERIFIED_LIVE label.
        gate.setDataClassification(verification == VerificationStatus.VERIFIED
                ? DataClassification.VERIFIED_LIVE
                : DataClassification.VERIFIED_REFERENCE);
        gate.setUpdatedBy(actor);
        gateRepository.save(gate);

        saveHistory(gate, oldStatus, newStatus, source, actor, note);

        String auditDetails = "Gate " + gate.getGateCode() + ": " + oldStatus + " -> " + newStatus
                + " | source=" + source + " | verification=" + verification
                + (auditSuffix == null ? "" : " | " + auditSuffix);
        auditLogService.log(actor == null ? "SYSTEM" : actor.getEmail(), auditAction,
                "GATE", String.valueOf(gate.getId()), truncate(auditDetails, 400));

        // A verification-only edit can change what commuters see even when the raw
        // stored status is unchanged (AWAITING_VERIFICATION -> OPEN/CLOSED).
        GateStatus newPublicStatus = gateMapper.publicStatus(gate);
        if (oldStatus != newStatus || oldPublicStatus != newPublicStatus) {
            notificationService.notifyGateStatusChange(gate, oldPublicStatus, newPublicStatus);
            eventPublisher.publishEvent(new GateStatusChangedEvent(
                    gate.getId(),
                    gate.getGateCode(),
                    oldPublicStatus.name(),
                    newPublicStatus.name(),
                    now.atZone(ZoneId.systemDefault()).toInstant()
            ));
        }
    }

    /** Active gate IDs for STAFF; null means the caller is not assignment-scoped. */
    @Transactional(readOnly = true)
    public Set<Long> assignedGateIds(AppUserPrincipal principal) {
        if (principal == null || principal.getRole() != Role.STAFF) {
            return null;
        }
        if (principal.getId() == null) {
            return Set.of();
        }
        return assignmentRepository.findByUserIdAndActiveTrueOrderByGateNameAsc(principal.getId()).stream()
                .map(assignment -> assignment.getGate().getId())
                .collect(Collectors.toSet());
    }

    /** Server-side read guard used by every gate-facing controller method. */
    @Transactional(readOnly = true)
    public void ensureReadAccess(AppUserPrincipal principal, Long gateId) {
        if (principal == null || principal.getRole() != Role.STAFF || gateId == null) {
            return;
        }
        if (!assignmentRepository.existsByUserIdAndGateIdAndActiveTrue(principal.getId(), gateId)) {
            throw new ResourceNotFoundException("Railway gate with ID " + gateId + " was not found.");
        }
    }

    /** Staff are limited to their assigned gates; admins retain broad operations access. */
    private void requireOperationalAccess(AppUserPrincipal principal, RailwayGate gate) {
        if (principal == null || principal.getRole() != Role.STAFF || gate.getId() == null) {
            return;
        }
        if (!assignmentRepository.existsByUserIdAndGateIdAndActiveTrue(principal.getId(), gate.getId())) {
            throw new BadRequestException("This gate is not assigned to your staff account.");
        }
    }

    @Transactional
    public void delete(Long id) {
        RailwayGate gate = findGate(id);
        reportRepository.deleteByGateId(id);
        notificationRepository.deleteByGateId(id);
        historyRepository.deleteByGateId(id);
        gateRepository.delete(gate);
    }

    private void saveHistory(RailwayGate gate,
                             GateStatus oldStatus,
                             GateStatus newStatus,
                             StatusSource source,
                             User actor,
                             String note) {
        GateStatusHistory history = new GateStatusHistory();
        history.setGate(gate);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setSource(source);
        history.setChangedBy(actor);
        history.setNote(note != null && note.length() > 300 ? note.substring(0, 300) : note);
        history.setChangedAt(gate.getLastUpdatedAt());
        historyRepository.save(history);
    }

    private RailwayGate findGate(Long id) {
        RailwayGate gate = gateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Railway gate with ID " + id + " was not found."));
        if (DemoDataClassifier.isDemoGate(gate)) {
            throw new ResourceNotFoundException("Railway gate with ID " + id + " was not found.");
        }
        return gate;
    }

    private User findActor(AppUserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in user was not found."));
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null ? "" : (value.length() <= max ? value : value.substring(0, max));
    }
}
