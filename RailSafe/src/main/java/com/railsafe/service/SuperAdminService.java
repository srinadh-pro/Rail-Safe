package com.railsafe.service;

import com.railsafe.dto.AdminAccountResponse;
import com.railsafe.dto.AuditLogResponse;
import com.railsafe.dto.CreateAdminRequest;
import com.railsafe.dto.UpdateAdminRequest;
import com.railsafe.entity.Role;
import com.railsafe.entity.User;
import com.railsafe.exception.BadRequestException;
import com.railsafe.exception.ConflictException;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Super Admin account management. This service can only create and edit ADMIN
 * accounts. It never changes a role, so a client cannot grant SUPER_ADMIN through
 * this API and an admin cannot promote themselves.
 */
@Service
public class SuperAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public SuperAdminService(UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> listAdminAccounts() {
        return userRepository.findByRoleInOrderByFullNameAsc(List.of(Role.ADMIN, Role.SUPER_ADMIN))
                .stream()
                .filter(user -> !DemoDataClassifier.isDemoUser(user))
                .map(SuperAdminService::toResponse)
                .toList();
    }

    @Transactional
    public AdminAccountResponse createAdmin(CreateAdminRequest request, String actor) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account already exists for " + email + ".");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(blankToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        AdminAccountResponse response = toResponse(userRepository.save(user));
        auditLogService.log(actor, "ADMIN_CREATED", "USER", String.valueOf(user.getId()),
                "Created ADMIN account " + email);
        return response;
    }

    @Transactional
    public AdminAccountResponse updateAdmin(Long id, UpdateAdminRequest request, String actor) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No account exists with id " + id + "."));

        if (DemoDataClassifier.isDemoUser(user)) {
            throw new ResourceNotFoundException("No account exists with id " + id + ".");
        }
        if (user.getRole() != Role.ADMIN) {
            throw new BadRequestException(
                    "Only ADMIN accounts can be edited here. "
                            + "SUPER_ADMIN accounts are controlled by the secure bootstrap process.");
        }

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
        AdminAccountResponse response = toResponse(userRepository.save(user));

        StringBuilder detail = new StringBuilder("Updated ADMIN account ").append(email);
        if (enabledChanged) {
            detail.append("; enabled=").append(user.isEnabled());
        }
        if (passwordChanged) {
            detail.append("; password reset");
        }
        auditLogService.log(actor, enabledChanged && !user.isEnabled()
                ? "ADMIN_DEACTIVATED" : "ADMIN_UPDATED", "USER", String.valueOf(user.getId()),
                detail.toString());
        return response;
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> listAuditLogs() {
        return auditLogService.recent();
    }

    private static AdminAccountResponse toResponse(User user) {
        return new AdminAccountResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
