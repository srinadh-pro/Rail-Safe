package com.railsafe.service;

import com.railsafe.dto.*;
import com.railsafe.entity.*;
import com.railsafe.exception.BadRequestException;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.config.LiveProperties;
import com.railsafe.live.service.LiveCountService;
import com.railsafe.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Super Admin system, security, role/permission, analytics and data-source overview.
 * Only safe configuration is editable; secrets and endpoints remain environment-owned.
 */
@Service
public class SuperAdminOperationsService {

    private final UserRepository userRepository;
    private final RailwayGateRepository gateRepository;
    private final TrainMasterRepository trainRepository;
    private final NotificationRepository notificationRepository;
    private final UserReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final ImportedSourceRowRepository importedSourceRepository;
    private final RouteSearchHistoryRepository routeSearchRepository;
    private final MaintenanceRequestRepository maintenanceRepository;
    private final RoleDefinitionRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final DataSourceRepository dataSourceRepository;
    private final LiveCountService countService;
    private final AuditLogService auditLogService;
    private final LiveProperties properties;

    @Value("${railsafe.gate.stale-minutes:120}")
    private long staleMinutes;

    @Value("${railsafe.notifications.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${railsafe.notifications.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${railsafe.restricted-dataset.import-enabled:true}")
    private boolean restrictedImportEnabled;

    @Value("${railsafe.super-admin.email:}")
    private String superAdminEmail;

    @Value("${railsafe.demo.accounts-enabled:false}")
    private boolean demoAccountsEnabled;

    @Value("${server.servlet.session.timeout:8h}")
    private String sessionTimeout;

    public SuperAdminOperationsService(UserRepository userRepository,
                                       RailwayGateRepository gateRepository,
                                       TrainMasterRepository trainRepository,
                                       NotificationRepository notificationRepository,
                                       UserReportRepository reportRepository,
                                       AuditLogRepository auditLogRepository,
                                       ImportedSourceRowRepository importedSourceRepository,
                                       RouteSearchHistoryRepository routeSearchRepository,
                                       MaintenanceRequestRepository maintenanceRepository,
                                       RoleDefinitionRepository roleRepository,
                                       PermissionRepository permissionRepository,
                                       RolePermissionRepository rolePermissionRepository,
                                       DataSourceRepository dataSourceRepository,
                                       LiveCountService countService,
                                       AuditLogService auditLogService,
                                       LiveProperties properties) {
        this.userRepository = userRepository;
        this.gateRepository = gateRepository;
        this.trainRepository = trainRepository;
        this.notificationRepository = notificationRepository;
        this.reportRepository = reportRepository;
        this.auditLogRepository = auditLogRepository;
        this.importedSourceRepository = importedSourceRepository;
        this.routeSearchRepository = routeSearchRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.countService = countService;
        this.auditLogService = auditLogService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public SystemHealthResponse health() {
        List<RailwayGate> gates = gateRepository.findAllByOrderByNameAsc().stream()
                .filter(gate -> !DemoDataClassifier.isDemoGate(gate)).toList();
        long verified = gates.stream()
                .filter(gate -> gate.getVerificationStatus() == VerificationStatus.VERIFIED).count();
        long simulated = gates.stream()
                .filter(gate -> gate.getDataClassification() == DataClassification.SIMULATED).count();
        long awaiting = countService.counts((Long) null).awaitingVerificationGates();
        long pendingReports = reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(report -> report.getStatus() == ReportStatus.PENDING)
                .filter(report -> !DemoDataClassifier.isDemoReport(report)).count();
        long unread = notificationRepository.findAll().stream()
                .filter(notification -> !notification.isRead())
                .filter(notification -> !DemoDataClassifier.isDemoNotification(notification)).count();

        List<String> warnings = new ArrayList<>();
        if (awaiting > 0) warnings.add(awaiting + " gate(s) are awaiting verification.");
        if (simulated > 0) warnings.add(simulated + " simulated gate row(s) are restricted to source review.");
        if (!properties.isAlertsEnabled()) warnings.add("Operational alerts are currently disabled.");
        if (properties.getProviderMode().equalsIgnoreCase("simulation")) {
            warnings.add("Simulation provider is enabled; no simulated position is presented as live tracking.");
        }
        warnings.add("CSRF protection is disabled for the local demo and must be enabled in production.");

        String status = warnings.isEmpty() ? "HEALTHY" : "ATTENTION_REQUIRED";
        return new SystemHealthResponse(
                status, Instant.now(), gates.size(), verified, awaiting, simulated,
                userRepository.count(), countRole(Role.STAFF), countRole(Role.ADMIN), countRole(Role.SUPER_ADMIN),
                trainRepository.findAllByOrderByTrainNumberAsc().stream()
                        .filter(train -> !train.isSynthetic()).count(),
                pendingReports, unread, auditLogRepository.count(),
                properties.getProviderMode(), properties.isAlertsEnabled(),
                properties.isSimulationAcknowledged(), properties.getProviderMode().toUpperCase(Locale.ROOT),
                "Counts are derived from current database state. Simulated records are excluded from operational views.",
                warnings
        );
    }

    @Transactional(readOnly = true)
    public List<DataSourceResponse> dataSources() {
        return dataSourceRepository.findAllByOrderByNameAsc().stream().map(source ->
                new DataSourceResponse(source.getId(), source.getName(), source.getSourceType(), source.getUrl(),
                        source.getDataMode(), source.getDataClassification(), source.isEnabled(),
                        source.getLastSyncAt(), source.getDescription())).toList();
    }

    @Transactional
    public DataSourceResponse setDataSourceEnabled(Long id, boolean enabled, String actor) {
        DataSourceEntity source = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No data source exists with id " + id + "."));
        source.setEnabled(enabled);
        dataSourceRepository.save(source);
        auditLogService.log(actor, "DATA_SOURCE_UPDATED", "DATA_SOURCE", String.valueOf(id),
                (enabled ? "Enabled" : "Disabled") + " data source " + source.getName());
        return new DataSourceResponse(source.getId(), source.getName(), source.getSourceType(), source.getUrl(),
                source.getDataMode(), source.getDataClassification(), source.isEnabled(),
                source.getLastSyncAt(), source.getDescription());
    }

    @Transactional(readOnly = true)
    public SecurityOverviewResponse security() {
        List<String> findings = new ArrayList<>();
        findings.add("CSRF is disabled for the local demo; enable it before production deployment.");
        findings.add("All operational pages and APIs require an authenticated session.");
        findings.add("SUPER_ADMIN creation is blocked by public registration and audited on administrative changes.");
        if (demoAccountsEnabled) findings.add("Demo credentials are exposed only by the h2/dev profile.");
        if (properties.isSimulationAcknowledged()) {
            findings.add("Simulation is explicitly acknowledged and excluded from operational alerts.");
        }
        return new SecurityOverviewResponse(
                Instant.now(), false, true, "Lax", parseSessionSeconds(sessionTimeout),
                "BCrypt", true, countRole(Role.SUPER_ADMIN), demoAccountsEnabled,
                properties.getProviderMode().equalsIgnoreCase("simulation"), findings
        );
    }

    @Transactional(readOnly = true)
    public ConfigOverviewResponse config() {
        return new ConfigOverviewResponse(
                staleMinutes, properties.getProviderMode(), properties.isAlertsEnabled(),
                properties.isSimulationAcknowledged(), properties.isSimulationNotifications(),
                properties.getRouteAvoidRadiusMeters(), properties.getRouteMaxAlternatives(),
                properties.getFeedStaleSeconds(), emailEnabled, smsEnabled, restrictedImportEnabled,
                maskEmail(superAdminEmail),
                "Secrets, datasource URLs and bootstrap credentials are environment-owned and are not editable here."
        );
    }

    @Transactional
    public ConfigOverviewResponse updateConfig(ConfigUpdateRequest request, String actor) {
        if (request.alertsEnabled() != null) {
            properties.setAlertsEnabled(request.alertsEnabled());
        }
        if (request.simulationNotifications() != null) {
            properties.setSimulationNotifications(request.simulationNotifications());
        }
        auditLogService.log(actor, "SYSTEM_CONFIG_UPDATED", "CONFIG", "live",
                "alertsEnabled=" + properties.isAlertsEnabled()
                        + ", simulationNotifications=" + properties.isSimulationNotifications());
        return config();
    }

    public BackupOverviewResponse backups() {
        Path directory = Path.of("data");
        List<String> names = new ArrayList<>();
        boolean available = Files.isDirectory(directory);
        if (available) {
            try (var stream = Files.list(directory)) {
                stream.filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.startsWith("backup-"))
                        .sorted(Comparator.reverseOrder())
                        .forEach(names::add);
            } catch (IOException ex) {
                available = false;
            }
        }
        return new BackupOverviewResponse(
                directory.toString(), names.size(), names, available,
                "Backups are retained for recovery only. Verify restores on a non-production instance before use.",
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public List<RolePermissionOverviewResponse> roles() {
        Map<Long, String> permissionNames = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(Permission::getId, Permission::getCode));
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(RoleDefinition::getName))
                .map(role -> new RolePermissionOverviewResponse(
                        role.getName(), role.getDescription(), role.isBuiltIn(),
                        rolePermissionRepository.findAll().stream()
                                .filter(link -> link.getId().getRoleName().equals(role.getName()))
                                .map(link -> permissionNames.getOrDefault(link.getId().getPermissionId(), "unknown"))
                                .sorted().toList()))
                .toList();
    }

    @Transactional
    public RolePermissionOverviewResponse updateRolePermissions(String roleName,
                                                                 UpdateRolePermissionsRequest request,
                                                                 String actor) {
        Role target;
        try {
            target = Role.valueOf(roleName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown role: " + roleName);
        }
        if (target == Role.SUPER_ADMIN && (request.permissions() == null
                || !request.permissions().contains("superadmin.access"))) {
            throw new BadRequestException("SUPER_ADMIN must retain the superadmin.access permission.");
        }

        Set<String> requested = request.permissions() == null
                ? Set.of() : Set.copyOf(request.permissions());
        for (String code : requested) {
            if (permissionRepository.findByCode(code).isEmpty()) {
                throw new BadRequestException("Unknown permission: " + code);
            }
        }

        rolePermissionRepository.findAll().stream()
                .filter(link -> link.getId().getRoleName().equals(target.name()))
                .forEach(rolePermissionRepository::delete);
        RoleDefinition role = roleRepository.findById(target.name()).orElseGet(RoleDefinition::new);
        role.setName(target.name());
        role.setBuiltIn(true);
        roleRepository.save(role);
        for (String code : requested) {
            Permission permission = permissionRepository.findByCode(code).orElseThrow();
            if (rolePermissionRepository.findById(new RolePermissionId(target.name(), permission.getId())).isEmpty()) {
                rolePermissionRepository.save(new RolePermission(role, permission));
            }
        }
        auditLogService.log(actor, "ROLE_PERMISSIONS_UPDATED", "ROLE", target.name(),
                "Permissions now: " + String.join(", ", new TreeSet<>(requested)));
        return roles().stream().filter(item -> item.role().equals(target.name())).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<UserAccountResponse> users() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getFullName))
                .map(user -> new UserAccountResponse(user.getId(), user.getFullName(), user.getEmail(),
                        user.getRole().name(), user.isEnabled(), user.getCreatedAt(), user.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public UserAccountResponse setUserEnabled(Long id, boolean enabled, String actorId) {
        User user = requireUser(id);
        Long actor = parseActor(actorId);
        if (actor != null && actor.equals(user.getId()) && !enabled) {
            throw new BadRequestException("You cannot disable your own account.");
        }
        user.setEnabled(enabled);
        userRepository.save(user);
        auditLogService.log(actorId, enabled ? "USER_ENABLED" : "USER_DISABLED", "USER", String.valueOf(id),
                (enabled ? "Enabled" : "Disabled") + " account " + user.getEmail());
        return toUserResponse(user);
    }

    @Transactional
    public UserAccountResponse setUserRole(Long id, UserRoleUpdateRequest request, String actorId) {
        User user = requireUser(id);
        Role role;
        try {
            role = Role.valueOf(request.role().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown role: " + request.role());
        }
        if (role == Role.SUPER_ADMIN) {
            throw new BadRequestException("SUPER_ADMIN accounts cannot be created through role assignment.");
        }
        Long actor = parseActor(actorId);
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new BadRequestException("Use Super Admin account management to change a SUPER_ADMIN account.");
        }
        if (actor != null && actor.equals(user.getId())) {
            throw new BadRequestException("You cannot change your own role.");
        }
        user.setRole(role);
        userRepository.save(user);
        auditLogService.log(actorId, "USER_ROLE_UPDATED", "USER", String.valueOf(id),
                "Role changed to " + role.name() + " for " + user.getEmail());
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics() {
        Map<String, Long> usersByRole = Arrays.stream(Role.values())
                .collect(Collectors.toMap(Enum::name, this::countRole, (a, b) -> b, LinkedHashMap::new));
        List<RailwayGate> gates = gateRepository.findAllByOrderByNameAsc().stream()
                .filter(gate -> !DemoDataClassifier.isDemoGate(gate)).toList();
        Map<String, Long> gatesByStatus = gates.stream()
                .collect(Collectors.groupingBy(gate -> gate.getStatus() == null
                        ? "AWAITING_VERIFICATION" : gate.getStatus().name(), Collectors.counting()));
        Map<String, Long> notificationsByType = notificationRepository.findAll().stream()
                .filter(notification -> !DemoDataClassifier.isDemoNotification(notification))
                .collect(Collectors.groupingBy(notification -> notification.getType().name(), Collectors.counting()));
        return new AnalyticsResponse(
                Instant.now(), usersByRole, gatesByStatus, notificationsByType,
                routeSearchRepository.count(), maintenanceRepository.count(), importedSourceRepository.count()
        );
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No account exists with id " + id + "."));
    }

    private UserAccountResponse toUserResponse(User user) {
        return new UserAccountResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getRole().name(), user.isEnabled(), user.getCreatedAt(), user.getUpdatedAt());
    }

    private long countRole(Role role) {
        return userRepository.findAll().stream().filter(user -> user.getRole() == role).count();
    }

    private static Long parseActor(String actor) {
        try {
            return actor == null ? null : Long.valueOf(actor);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static long parseSessionSeconds(String value) {
        if (value == null || value.isBlank()) return 28800L;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (trimmed.endsWith("h")) return Long.parseLong(trimmed.substring(0, trimmed.length() - 1)) * 3600L;
            if (trimmed.endsWith("m")) return Long.parseLong(trimmed.substring(0, trimmed.length() - 1)) * 60L;
            if (trimmed.endsWith("s")) return Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return 28800L;
        }
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "Not configured";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(0, at));
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}
