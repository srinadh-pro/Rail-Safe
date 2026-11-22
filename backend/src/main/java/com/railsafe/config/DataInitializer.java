package com.railsafe.config;

import com.railsafe.entity.*;
import com.railsafe.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Seeds demo accounts and exactly five fictional crossings on first start.
 * Every crossing is marked source=DEMO and verification=DEMO so the UI can never
 * present it as verified live railway information.
 *
 * Demo accounts (created with BCrypt hashes):
 *   admin@railsafe.demo / Admin@123  (ADMIN)
 *   staff@railsafe.demo / Staff@123  (STAFF)
 *   user@railsafe.demo  / User@123   (USER)
 */
@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final RailwayGateRepository gateRepository;
    private final GateStatusHistoryRepository historyRepository;
    private final SavedRouteRepository savedRouteRepository;
    private final UserReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           RailwayGateRepository gateRepository,
                           GateStatusHistoryRepository historyRepository,
                           SavedRouteRepository savedRouteRepository,
                           UserReportRepository reportRepository,
                           NotificationRepository notificationRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.gateRepository = gateRepository;
        this.historyRepository = historyRepository;
        this.savedRouteRepository = savedRouteRepository;
        this.reportRepository = reportRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        User admin = ensureUser("RailSafe Admin", "admin@railsafe.demo", "Admin@123", Role.ADMIN, "9000000001");
        User staff = ensureUser("RailSafe Staff", "staff@railsafe.demo", "Staff@123", Role.STAFF, "9000000002");
        User demoUser = ensureUser("Demo Commuter", "user@railsafe.demo", "User@123", Role.USER, "9000000003");

        LocalDateTime now = LocalDateTime.now();

        RailwayGate nagole = ensureGate("RS-DEMO-001", "Demo Crossing – Nagole Canal",
                17.3725, 78.5510, "Nagole, Hyderabad (fictional demo location)",
                GateStatus.OPEN, now.minusMinutes(8), staff, "DEMO DATA. Not live Indian Railways information.");
        RailwayGate uppal = ensureGate("RS-DEMO-002", "Demo Crossing – Uppal Junction",
                17.4010, 78.5590, "Uppal, Hyderabad (fictional demo location)",
                GateStatus.CLOSED, now.minusMinutes(12), staff, "DEMO DATA. Not live Indian Railways information.");
        RailwayGate lbNagar = ensureGate("RS-DEMO-003", "Demo Crossing – LB Nagar Ring Road",
                17.3456, 78.5525, "LB Nagar, Hyderabad (fictional demo location)",
                GateStatus.CLOSED, now.minusMinutes(18), staff, "DEMO DATA. Not live Indian Railways information.");
        RailwayGate dilsukhnagar = ensureGate("RS-DEMO-004", "Demo Crossing – Dilsukhnagar Market",
                17.3688, 78.5246, "Dilsukhnagar, Hyderabad (fictional demo location)",
                GateStatus.OPEN, now.minusMinutes(25), staff, "DEMO DATA. Not live Indian Railways information.");
        RailwayGate kothapet = ensureGate("RS-DEMO-005", "Demo Crossing – Kothapet Industrial Area",
                17.3560, 78.5400, "Kothapet, Hyderabad (fictional demo location)",
                GateStatus.UNKNOWN, now.minusMinutes(180), staff, "DEMO DATA. Status deliberately unknown for demonstration.");

        ensureDemoRoute(demoUser, nagole, uppal, lbNagar, dilsukhnagar, kothapet);
        ensureDemoReport(demoUser, uppal);
        ensureDemoNotifications(demoUser, uppal);

        log.info("================================================================");
        log.info(" RailSafe demo data is ready.");
        log.info(" DEMO accounts:");
        log.info("   admin@railsafe.demo / Admin@123  (ADMIN)");
        log.info("   staff@railsafe.demo / Staff@123  (STAFF)");
        log.info("   user@railsafe.demo  / User@123   (USER)");
        log.info(" Five fictional crossings are marked DEMO - not live railway data.");
        log.info(" Open http://localhost:8080");
        log.info("================================================================");
    }

    private User ensureUser(String fullName, String email, String rawPassword, Role role, String phone) {
        String normalized = email.toLowerCase(Locale.ROOT);
        return userRepository.findByEmailIgnoreCase(normalized).orElseGet(() -> {
            User user = new User();
            user.setFullName(fullName);
            user.setEmail(normalized);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setRole(role);
            user.setPhone(phone);
            user.setEnabled(true);
            return userRepository.save(user);
        });
    }

    private RailwayGate ensureGate(String code,
                                   String name,
                                   double latitude,
                                   double longitude,
                                   String location,
                                   GateStatus status,
                                   LocalDateTime statusTime,
                                   User actor,
                                   String notes) {
        RailwayGate gate = gateRepository.findByGateCodeIgnoreCase(code).orElseGet(() -> {
            RailwayGate created = new RailwayGate();
            created.setGateCode(code);
            created.setName(name);
            created.setLatitude(latitude);
            created.setLongitude(longitude);
            created.setLocation(location);
            created.setStatus(status);
            created.setStatusSource(StatusSource.DEMO);
            created.setVerificationStatus(VerificationStatus.DEMO);
            created.setLastUpdatedAt(statusTime);
            created.setUpdatedBy(actor);
            created.setNotes(notes);
            return gateRepository.save(created);
        });

        // Keep the fictional DEMO dataset fresh across restarts so the demo always
        // shows 2 OPEN, 2 CLOSED and 1 stale UNKNOWN. Gates that staff/admin have
        // changed are no longer DEMO-sourced and are never overwritten here.
        boolean untouchedDemo = gate.getStatusSource() == StatusSource.DEMO
                && gate.getVerificationStatus() == VerificationStatus.DEMO;
        if (untouchedDemo) {
            gate.setStatus(status);
            gate.setLastUpdatedAt(statusTime);
            gate.setUpdatedBy(actor);
            gateRepository.save(gate);
        }

        List<GateStatusHistory> history = historyRepository.findByGateIdOrderByChangedAtDesc(gate.getId());
        if (history.isEmpty()) {
            GateStatusHistory historyEntry = new GateStatusHistory();
            historyEntry.setGate(gate);
            historyEntry.setOldStatus(null);
            historyEntry.setNewStatus(gate.getStatus());
            historyEntry.setSource(StatusSource.DEMO);
            historyEntry.setChangedBy(actor);
            historyEntry.setChangedAt(gate.getLastUpdatedAt());
            historyEntry.setNote("Initial DEMO sample status. Fictional data, not live railway information.");
            historyRepository.save(historyEntry);
        } else if (untouchedDemo && history.size() == 1 && history.get(0).getSource() == StatusSource.DEMO) {
            // Only the initial DEMO history row exists, so keep it aligned with
            // the refreshed demo timestamp instead of adding a duplicate row.
            GateStatusHistory historyEntry = history.get(0);
            historyEntry.setNewStatus(status);
            historyEntry.setChangedAt(statusTime);
            historyRepository.save(historyEntry);
        }

        return gate;
    }

    private void ensureDemoRoute(User demoUser, RailwayGate... gates) {
        if (!savedRouteRepository.findByUserIdOrderByCreatedAtDesc(demoUser.getId()).isEmpty()) {
            return;
        }
        SavedRoute route = new SavedRoute();
        route.setUser(demoUser);
        route.setName("Home → Office (Demo)");
        route.setStartLabel("Demo start: Secunderabad area");
        route.setStartLat(17.4399);
        route.setStartLng(78.4983);
        route.setDestLabel("Demo destination: LB Nagar area");
        route.setDestLat(17.3456);
        route.setDestLng(78.5525);
        route.setGateIds(java.util.Arrays.stream(gates)
                .map(gate -> String.valueOf(gate.getId()))
                .reduce((left, right) -> left + "," + right)
                .orElse(""));
        route.setCreatedAt(LocalDateTime.now().minusHours(2));
        savedRouteRepository.save(route);
    }

    private void ensureDemoReport(User demoUser, RailwayGate gate) {
        if (reportRepository.countByStatus(ReportStatus.PENDING) > 0) {
            return;
        }
        UserReport report = new UserReport();
        report.setUser(demoUser);
        report.setGate(gate);
        report.setReportedStatus(GateStatus.OPEN);
        report.setComment("DEMO report: the barrier looked open when I passed this morning. Please verify physically.");
        report.setStatus(ReportStatus.PENDING);
        report.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        reportRepository.save(report);
    }

    private void ensureDemoNotifications(User demoUser, RailwayGate closedGate) {
        String welcomeKey = "seed:welcome:" + demoUser.getId();
        if (!notificationRepository.existsByDedupeKey(welcomeKey)) {
            Notification welcome = new Notification();
            welcome.setUser(demoUser);
            welcome.setTitle("Welcome to RailSafe");
            welcome.setMessage("RailSafe shows reported gate status, source and time. "
                    + "Demo data is simulated and is not live Indian Railways information.");
            welcome.setType(NotificationType.SYSTEM);
            welcome.setRead(false);
            welcome.setDedupeKey(welcomeKey);
            welcome.setCreatedAt(LocalDateTime.now().minusMinutes(20));
            notificationRepository.save(welcome);
        }

        String alertKey = "seed:demo-closed:" + demoUser.getId();
        if (!notificationRepository.existsByDedupeKey(alertKey)) {
            Notification alert = new Notification();
            alert.setUser(demoUser);
            alert.setGate(closedGate);
            alert.setTitle("Demo alert: gate reported CLOSED");
            alert.setMessage("DEMO DATA: " + closedGate.getName()
                    + " is marked CLOSED in the sample dataset. This is not a live railway signal. "
                    + "Always obey physical barriers and railway staff.");
            alert.setType(NotificationType.GATE_CLOSED);
            alert.setStatusAtEvent(GateStatus.CLOSED);
            alert.setRead(false);
            alert.setDedupeKey(alertKey);
            alert.setCreatedAt(LocalDateTime.now().minusMinutes(10));
            notificationRepository.save(alert);
        }
    }
}
