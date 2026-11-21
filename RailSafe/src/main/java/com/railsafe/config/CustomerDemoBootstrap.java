package com.railsafe.config;

import com.railsafe.entity.Role;
import com.railsafe.entity.User;
import com.railsafe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creates one customer demo account for the h2/dev profiles only.
 * It is idempotent and never changes an existing account or password.
 * Production deployments must create customers through the normal registration flow.
 */
@Component
@Profile({"h2", "dev"})
@Order(2)
public class CustomerDemoBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CustomerDemoBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String configuredEmail;
    private final String configuredPassword;

    public CustomerDemoBootstrap(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${railsafe.demo.customer.email:}") String configuredEmail,
                                 @Value("${railsafe.demo.customer.password:}") String configuredPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.configuredEmail = configuredEmail;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (configuredEmail == null || configuredEmail.isBlank()
                || configuredPassword == null || configuredPassword.isBlank()) {
            log.warn("Customer demo bootstrap skipped because no demo credentials are configured.");
            return;
        }

        String email = configuredEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        User customer = new User();
        customer.setFullName("RailSafe Customer");
        customer.setEmail(email);
        customer.setRole(Role.CUSTOMER);
        customer.setPasswordHash(passwordEncoder.encode(configuredPassword));
        customer.setEnabled(true);
        userRepository.save(customer);
        log.info("Created CUSTOMER demo account: {}", email);
    }
}
