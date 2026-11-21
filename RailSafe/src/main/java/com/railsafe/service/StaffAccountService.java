package com.railsafe.service;

import com.railsafe.dto.StaffAccountRequest;
import com.railsafe.dto.StaffAccountResponse;
import com.railsafe.dto.StaffAssignmentRequest;
import com.railsafe.dto.UpdateStaffRequest;
import com.railsafe.entity.Role;
import com.railsafe.entity.StaffAssignment;
import com.railsafe.entity.User;
import com.railsafe.exception.BadRequestException;
import com.railsafe.exception.ConflictException;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.StaffAssignmentRepository;
import com.railsafe.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Admin/Super-Admin management of STAFF accounts and gate assignments. */
@Service
public class StaffAccountService {

    private final UserRepository userRepository;
    private final StaffAssignmentRepository assignmentRepository;
    private final RailwayGateRepository gateRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public StaffAccountService(UserRepository userRepository,
                               StaffAssignmentRepository assignmentRepository,
                               RailwayGateRepository gateRepository,
                               PasswordEncoder passwordEncoder,
                               AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.gateRepository = gateRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<StaffAccountResponse> list() {
        // This is an administrative management view: every STAFF account must be
        // visible here, including the seeded local demo account, otherwise an admin
        // cannot assign, reassign or deactivate the account used on the demo console.
        return userRepository.findByRoleInOrderByFullNameAsc(List.of(Role.STAFF)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StaffAccountResponse create(StaffAccountRequest request, String actor) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account already exists for " + email + ".");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(blankToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.STAFF);
        user.setEnabled(true);
        StaffAccountResponse response = toResponse(userRepository.save(user));
        auditLogService.log(actor, "STAFF_CREATED", "USER", String.valueOf(user.getId()),
                "Created STAFF account " + email);
        return response;
    }

    @Transactional
    public StaffAccountResponse update(Long id, UpdateStaffRequest request, String actor) {
        User user = requireStaff(id);
        String email = normalizeEmail(request.email());
        userRepository.findByEmailIgnoreCase(email)
                .filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("An account already exists for " + email + ".");
                });

        boolean enabledChanged = request.enabled() != null && request.enabled() != user.isEnabled();
        boolean passwordChanged = request.password() != null && !request.password().isBlank();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(blankToNull(request.phone()));
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (passwordChanged) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        StaffAccountResponse response = toResponse(userRepository.save(user));
        auditLogService.log(actor,
                enabledChanged && !user.isEnabled() ? "STAFF_DEACTIVATED" : "STAFF_UPDATED",
                "USER", String.valueOf(user.getId()), "Updated STAFF account " + email);
        return response;
    }

    @Transactional
    public StaffAccountResponse setEnabled(Long id, boolean enabled, String actor) {
        User user = requireStaff(id);
        user.setEnabled(enabled);
        StaffAccountResponse response = toResponse(userRepository.save(user));
        auditLogService.log(actor, enabled ? "STAFF_ACTIVATED" : "STAFF_DEACTIVATED",
                "USER", String.valueOf(user.getId()),
                (enabled ? "Activated" : "Deactivated") + " STAFF account " + user.getEmail());
        return response;
    }

    @Transactional
    public StaffAccountResponse assignGates(Long staffId, StaffAssignmentRequest request, String actor) {
        User staff = requireStaff(staffId);
        Set<Long> requested = new LinkedHashSet<>();
        if (request.gateIds() != null) {
            for (Long gateId : request.gateIds()) {
                if (gateId != null) {
                    requested.add(gateId);
                }
            }
        }

        // Only real, non-demo gates can be assigned.
        Set<Long> validGateIds = new LinkedHashSet<>(gateRepository.findAllById(requested).stream()
                .filter(gate -> !DemoDataClassifier.isDemoGate(gate))
                .map(gate -> gate.getId())
                .toList());
        requested.retainAll(validGateIds);

        LocalDateTime now = LocalDateTime.now();
        List<StaffAssignment> active = assignmentRepository.findByUserIdAndActiveTrueOrderByGateNameAsc(staffId);
        Set<Long> currentlyAssigned = new LinkedHashSet<>(
                active.stream().map(a -> a.getGate().getId()).toList());

        List<StaffAssignment> changes = new ArrayList<>();
        for (StaffAssignment assignment : active) {
            if (!requested.contains(assignment.getGate().getId())) {
                assignment.setActive(false);
                assignment.setUnassignedAt(now);
                changes.add(assignment);
            }
        }
        for (Long gateId : requested) {
            if (currentlyAssigned.contains(gateId)) {
                continue;
            }
            StaffAssignment assignment = assignmentRepository.findByUserIdAndGateId(staffId, gateId)
                    .orElseGet(StaffAssignment::new);
            if (assignment.getUser() == null) {
                assignment.setUser(staff);
                assignment.setAssignedBy(userRepository.findByEmailIgnoreCase(actor).orElse(null));
            }
            assignment.setGate(gateRepository.getReferenceById(gateId));
            assignment.setActive(true);
            assignment.setUnassignedAt(null);
            changes.add(assignment);
        }
        assignmentRepository.saveAll(changes);
        auditLogService.log(actor, "STAFF_ASSIGNMENTS_UPDATED", "USER", String.valueOf(staffId),
                "Assigned " + requested.size() + " gate(s) to STAFF account " + staff.getEmail());
        return toResponse(staff);
    }

    /**
     * Safe delete: the account is deactivated and unassigned rather than physically
     * removed, preserving historical gate/status/audit records.
     */
    @Transactional
    public StaffAccountResponse delete(Long id, String actor) {
        User user = requireStaff(id);
        user.setEnabled(false);
        LocalDateTime now = LocalDateTime.now();
        assignmentRepository.findByUserIdAndActiveTrueOrderByGateNameAsc(id).forEach(assignment -> {
            assignment.setActive(false);
            assignment.setUnassignedAt(now);
        });
        StaffAccountResponse response = toResponse(userRepository.save(user));
        auditLogService.log(actor, "STAFF_DELETED", "USER", String.valueOf(id),
                "Deactivated STAFF account " + user.getEmail() + " and removed active assignments.");
        return response;
    }

    @Transactional(readOnly = true)
    public StaffAccountResponse toResponse(User user) {
        List<Long> gateIds = assignmentRepository.findByUserIdAndActiveTrueOrderByGateNameAsc(user.getId())
                .stream().map(a -> a.getGate().getId()).toList();
        return new StaffAccountResponse(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getRole().name(), user.isEnabled(), gateIds,
                user.getCreatedAt(), user.getUpdatedAt()
        );
    }

    private User requireStaff(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No account exists with id " + id + "."));
        if (user.getRole() != Role.STAFF) {
            throw new ResourceNotFoundException("No STAFF account exists with id " + id + ".");
        }
        return user;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
