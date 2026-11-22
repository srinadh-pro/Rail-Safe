package com.railsafe.config;

import com.railsafe.entity.Role;
import com.railsafe.entity.User;
import com.railsafe.repository.UserRepository;
import com.railsafe.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Secure bootstrap for the first SUPER_ADMIN account.
 *
 * There is no public endpoint that can create a SUPER_ADMIN. This runner creates
 * the account once, only when no SUPER_ADMIN exists, using credentials supplied by
 * the operator through RAILSAFE_SUPER_ADMIN_EMAIL / RAILSAFE_SUPER_ADMIN_PASSWORD.
 * Existing accounts, roles and passwords are never modified or reset.
 */
@Component
@Order(0)
public class SuperAdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final Environment environment;
    private final String configuredEmail;
    private final String configuredPassword;

    public SuperAdminBootstrap(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               AuditLogService auditLogService,
                               Environment environment,
                               @Value("${railsafe.super-admin.email:}") String configuredEmail,
                               @Value("${railsafe.super-admin.password:}") String configuredPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.environment = environment;
        this.configuredEmail = configuredEmail;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            log.info("SUPER_ADMIN account already exists; bootstrap skipped.");
            return;
        }

        boolean demoProfile = environment.acceptsProfiles(Profiles.of("h2", "dev"));
        if (configuredEmail == null || configuredEmail.isBlank()
                || configuredPassword == null || configuredPassword.isBlank()) {
            if (demoProfile) {
                log.warn("SUPER_ADMIN bootstrap skipped: set RAILSAFE_SUPER_ADMIN_EMAIL and "
                        + "RAILSAFE_SUPER_ADMIN_PASSWORD to create the first Super Admin.");
                return;
            }
            throw new IllegalStateException(
                    "The first SUPER_ADMIN requires RAILSAFE_SUPER_ADMIN_EMAIL and "
                            + "RAILSAFE_SUPER_ADMIN_PASSWORD outside demo profiles.");
        }

        if (configuredPassword.length() < 12) {
            if (demoProfile) {
                log.warn("SUPER_ADMIN bootstrap password is shorter than 12 characters. "
                        + "Use a longer password outside demo profiles.");
            } else {
                throw new IllegalStateException(
                        "RAILSAFE_SUPER_ADMIN_PASSWORD must be at least 12 characters.");
            }
        }

        String email = configuredEmail.trim().toLowerCase(Locale.ROOT);
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null) {
            // Never promote an existing account silently: that would grant SUPER_ADMIN
            // to an account whose password and intent we do not control.
            log.warn("SUPER_ADMIN bootstrap skipped: {} already exists with role {}. "
                    + "Use a different RAILSAFE_SUPER_ADMIN_EMAIL.", email, existing.getRole());
            return;
        }

        User superAdmin = new User();
        superAdmin.setFullName("RailSafe Super Admin");
        superAdmin.setEmail(email);
        superAdmin.setPasswordHash(passwordEncoder.encode(configuredPassword));
        superAdmin.setRole(Role.SUPER_ADMIN);
        superAdmin.setEnabled(true);
        User saved = userRepository.save(superAdmin);
        auditLogService.log("SYSTEM", "SUPER_ADMIN_BOOTSTRAPPED", "USER",
                String.valueOf(saved.getId()), "Created SUPER_ADMIN account " + email);

        log.info("================================================================");
        log.info(" SUPER_ADMIN account bootstrapped: {}", email);
        log.info(" Password comes from RAILSAFE_SUPER_ADMIN_PASSWORD (BCrypt-hashed).");
        log.info(" There is no public SUPER_ADMIN registration endpoint.");
        log.info("================================================================");
    }
}
