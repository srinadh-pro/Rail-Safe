package com.railsafe.service;

import com.railsafe.dto.DemoAccountResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Exposes demo sign-in shortcuts only in local/demo profiles or when explicitly
 * enabled. Passwords come from configuration so no password literal is stored
 * in source; when demo shortcuts are disabled the API returns an empty list.
 */
@Service
public class DemoAccountService {

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Value("${railsafe.demo.accounts-enabled:false}")
    private boolean explicitlyEnabled;

    @Value("${railsafe.demo.customer.email:}")
    private String customerEmail;

    @Value("${railsafe.demo.customer.password:}")
    private String customerPassword;

    @Value("${railsafe.demo.staff.password:}")
    private String staffPassword;

    @Value("${railsafe.demo.admin.password:}")
    private String adminPassword;

    @Value("${railsafe.demo.super-admin.password:}")
    private String superAdminPassword;

    public List<DemoAccountResponse> accounts() {
        if (!isDemoProfile() && !explicitlyEnabled) {
            return List.of();
        }

        String email = customerEmail.isBlank() ? "customer@railsafe.demo" : customerEmail;
        List<DemoAccountResponse> accounts = new ArrayList<>();
        accounts.add(new DemoAccountResponse("CUSTOMER", "Customer / commuter", email,
                email, customerPassword, "dashboard.html",
                "Demo credentials only. View permitted crossings, trains, alerts and verified routes."));
        accounts.add(new DemoAccountResponse("STAFF", "Railway staff", "staff@railsafe.demo",
                "staff@railsafe.demo", staffPassword, "staff.html",
                "Demo credentials only. Update assigned gates and review commuter reports."));
        accounts.add(new DemoAccountResponse("ADMIN", "Operations administrator", "admin@railsafe.demo",
                "admin@railsafe.demo", adminPassword, "admin.html",
                "Demo credentials only. Manage all gates, staff accounts and reports."));
        // SUPER_ADMIN shortcut is shown only when a demo password was configured.
        if (!superAdminPassword.isBlank()) {
            accounts.add(new DemoAccountResponse("SUPER_ADMIN", "Super administrator", "superadmin@railsafe.demo",
                    "superadmin@railsafe.demo", superAdminPassword, "super-admin.html",
                    "Demo credentials only. Highest access including audit and restricted source review."));
        }
        return List.copyOf(accounts);
    }

    private boolean isDemoProfile() {
        String profiles = activeProfiles == null ? "" : activeProfiles.toLowerCase(Locale.ROOT);
        return profiles.contains("h2") || profiles.contains("dev");
    }
}
