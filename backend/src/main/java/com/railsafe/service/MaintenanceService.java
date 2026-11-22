package com.railsafe.service;

import com.railsafe.dto.MaintenanceRequestRequest;
import com.railsafe.dto.MaintenanceResponse;
import com.railsafe.dto.MaintenanceStatusRequest;
import com.railsafe.entity.DataClassification;
import com.railsafe.entity.GateStatus;
import com.railsafe.entity.MaintenanceRequest;
import com.railsafe.entity.NotificationType;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.Role;
import com.railsafe.entity.User;
import com.railsafe.exception.BadRequestException;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.repository.MaintenanceRequestRepository;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.StaffAssignmentRepository;
import com.railsafe.repository.UserRepository;
import com.railsafe.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Maintenance workflow. Staff may only act on gates assigned to them. */
@Service
public class MaintenanceService {

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("OPEN", "IN_PROGRESS", "ON_HOLD", "RESOLVED", "CANCELLED");

    private final MaintenanceRequestRepository repository;
    private final RailwayGateRepository gateRepository;
    private final StaffAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public MaintenanceService(MaintenanceRequestRepository repository,
                              RailwayGateRepository gateRepository,
                              StaffAssignmentRepository assignmentRepository,
                              UserRepository userRepository,
                              NotificationService notificationService,
                              AuditLogService auditLogService) {
        this.repository = repository;
        this.gateRepository = gateRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceResponse> list(AppUserPrincipal principal) {
        List<MaintenanceRequest> requests;
        if (isAdmin(principal)) {
            requests = repository.findAllByOrderByRequestedAtDesc();
        } else if (principal != null && principal.getRole() == Role.STAFF) {
            Set<Long> gateIds = assignmentRepository.findByUserIdAndActiveTrueOrderByGateNameAsc(principal.getId())
                    .stream().map(a -> a.getGate().getId()).collect(Collectors.toSet());
            requests = repository.findAllByOrderByRequestedAtDesc().stream()
                    .filter(request -> gateIds.contains(request.getGateId()))
                    .toList();
        } else {
            requests = repository.findAllByOrderByRequestedAtDesc().stream()
                    .filter(request -> principal != null && principal.getId().equals(request.getRequesterId()))
                    .toList();
        }
        return requests.stream().map(this::toResponse).toList();
    }

    @Transactional
    public MaintenanceResponse create(MaintenanceRequestRequest request, AppUserPrincipal principal) {
        RailwayGate gate = gateRepository.findById(request.gateId())
                .orElseThrow(() -> new ResourceNotFoundException("No gate exists with id " + request.gateId() + "."));
        if (DemoDataClassifier.isDemoGate(gate)) {
            throw new BadRequestException("Maintenance requests cannot be created for demonstration data.");
        }
        if (principal != null && principal.getRole() == Role.STAFF) {
            requireAssigned(principal.getId(), gate);
        }

        User requester = principal == null ? null : userRepository.findById(principal.getId()).orElse(null);
        User assignee = principal != null && principal.getRole() == Role.STAFF ? requester : null;
        MaintenanceRequest entity = new MaintenanceRequest();
        entity.setGateId(gate.getId());
        entity.setRequesterId(requester == null ? null : requester.getId());
        entity.setAssignedToId(assignee == null ? null : assignee.getId());
        entity.setIssueType(request.issueType().trim());
        entity.setDescription(request.description().trim());
        entity.setPriority(normalizePriority(request.priority()));
        entity.setStatus("OPEN");
        MaintenanceRequest saved = repository.save(entity);

        notificationService.notifyLiveEvent(
                gate,
                NotificationType.GATE_MAINTENANCE,
                "Maintenance requested for " + gate.getName(),
                "A maintenance request (" + entity.getIssueType() + ") was recorded for this crossing. "
                        + "Reported information only; obey physical barriers and railway staff.",
                "maintenance:" + saved.getId() + ":open",
                gate.getStatus() == null ? GateStatus.AWAITING_VERIFICATION : gate.getStatus()
        );
        auditLogService.log(principal == null ? "SYSTEM" : principal.getUsername(),
                "MAINTENANCE_CREATED", "GATE", String.valueOf(gate.getId()),
                "Created maintenance request " + saved.getId() + " (" + entity.getIssueType() + ")");
        return toResponse(saved);
    }

    @Transactional
    public MaintenanceResponse updateStatus(Long id, MaintenanceStatusRequest request, AppUserPrincipal principal) {
        MaintenanceRequest entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No maintenance request exists with id " + id + "."));
        RailwayGate gate = gateRepository.findById(entity.getGateId())
                .orElseThrow(() -> new ResourceNotFoundException("The linked gate no longer exists."));
        if (!isAdmin(principal)) {
            requireAssigned(principal == null ? -1L : principal.getId(), gate);
        }

        String status = request.status() == null || request.status().isBlank()
                ? entity.getStatus() : request.status().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BadRequestException("Unsupported maintenance status: " + status);
        }
        entity.setStatus(status);
        if (request.resolutionNote() != null) {
            entity.setResolutionNote(request.resolutionNote().trim());
        }
        if (request.assignedToId() != null) {
            entity.setAssignedToId(request.assignedToId());
        }
        if ("RESOLVED".equals(status) || "CANCELLED".equals(status)) {
            entity.setResolvedAt(LocalDateTime.now());
        } else {
            entity.setResolvedAt(null);
        }
        MaintenanceRequest saved = repository.save(entity);

        notificationService.notifyLiveEvent(
                gate,
                NotificationType.GATE_MAINTENANCE,
                "Maintenance status updated for " + gate.getName(),
                "Maintenance request " + saved.getId() + " is now " + status + ". "
                        + "This is reported information, not a live railway signal.",
                "maintenance:" + saved.getId() + ":" + status,
                gate.getStatus() == null ? GateStatus.AWAITING_VERIFICATION : gate.getStatus()
        );
        auditLogService.log(principal == null ? "SYSTEM" : principal.getUsername(),
                "MAINTENANCE_STATUS_UPDATED", "MAINTENANCE", String.valueOf(saved.getId()),
                "Maintenance request " + saved.getId() + " changed to " + status);
        return toResponse(saved);
    }

    private void requireAssigned(Long userId, RailwayGate gate) {
        boolean assigned = gate.getId() != null
                && assignmentRepository.existsByUserIdAndGateIdAndActiveTrue(userId, gate.getId());
        if (!assigned) {
            throw new BadRequestException("This gate is not assigned to your staff account.");
        }
    }

    private static boolean isAdmin(AppUserPrincipal principal) {
        return principal != null
                && (principal.getRole() == Role.ADMIN || principal.getRole() == Role.SUPER_ADMIN);
    }

    private static String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) return "NORMAL";
        String normalized = priority.trim().toUpperCase(Locale.ROOT);
        return Set.of("LOW", "NORMAL", "HIGH", "URGENT").contains(normalized) ? normalized : "NORMAL";
    }

    private MaintenanceResponse toResponse(MaintenanceRequest request) {
        RailwayGate gate = gateRepository.findById(request.getGateId()).orElse(null);
        return new MaintenanceResponse(
                request.getId(), request.getGateId(),
                gate == null ? "Not Available" : gate.getGateCode(),
                gate == null ? "Not Available" : gate.getName(),
                request.getRequesterId(), userName(request.getRequesterId()),
                request.getAssignedToId(), userName(request.getAssignedToId()),
                request.getIssueType(), request.getDescription(), request.getPriority(), request.getStatus(),
                request.getResolutionNote(), request.getRequestedAt(), request.getUpdatedAt(), request.getResolvedAt()
        );
    }

    private String userName(Long id) {
        if (id == null) return "Not Available";
        return userRepository.findById(id).map(User::getFullName).orElse("Not Available");
    }
}
