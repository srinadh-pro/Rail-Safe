package com.railsafe.config;

import com.railsafe.entity.*;
import com.railsafe.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds persistent role, permission, zone, division and data-source reference data.
 * All rows are additive and idempotent; the Java enums remain authoritative for
 * runtime authorisation.
 */
@Component
@Order(6)
public class ReferenceDataInitializer implements CommandLineRunner {

    private final RoleDefinitionRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RailwayZoneRepository zoneRepository;
    private final RailwayDivisionRepository divisionRepository;
    private final DataSourceRepository dataSourceRepository;
    private final LiveProperties liveProperties;

    @Value("${railsafe.live.sensor-api-key:}")
    private String sensorApiKey;

    @Value("${railsafe.restricted-dataset.path:data/railsafe-expanded-data.json}")
    private String restrictedDatasetPath;

    public ReferenceDataInitializer(RoleDefinitionRepository roleRepository,
                                    PermissionRepository permissionRepository,
                                    RolePermissionRepository rolePermissionRepository,
                                    RailwayZoneRepository zoneRepository,
                                    RailwayDivisionRepository divisionRepository,
                                    DataSourceRepository dataSourceRepository,
                                    LiveProperties liveProperties) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.zoneRepository = zoneRepository;
        this.divisionRepository = divisionRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.liveProperties = liveProperties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedRolesAndPermissions();
        seedZonesAndDivisions();
        seedDataSources();
    }

    private void seedRolesAndPermissions() {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put("CUSTOMER", "Authenticated commuter: views permitted gates, trains, alerts and routes.");
        roles.put("USER", "Legacy commuter role retained for existing accounts.");
        roles.put("STAFF", "Authorised railway staff: assigned gate updates and report review.");
        roles.put("ADMIN", "Operations administrator: all gates, staff accounts and reports.");
        roles.put("SUPER_ADMIN", "Highest access: roles, permissions, audit, restricted source review and system configuration.");
        roles.forEach((name, description) -> {
            RoleDefinition role = roleRepository.findById(name).orElseGet(RoleDefinition::new);
            role.setName(name);
            role.setDescription(description);
            role.setBuiltIn(true);
            roleRepository.save(role);
        });

        Map<String, String> permissions = new LinkedHashMap<>();
        permissions.put("gate.view", "View permitted gate information");
        permissions.put("gate.update", "Update an assigned or managed gate status");
        permissions.put("gate.manage", "Create, edit and remove operational gates");
        permissions.put("train.view", "View verified train reference and authorised train information");
        permissions.put("route.search", "Calculate verified alternative routes");
        permissions.put("report.view", "View commuter reports");
        permissions.put("report.moderate", "Approve or reject commuter reports");
        permissions.put("staff.manage", "Create, update and assign STAFF accounts");
        permissions.put("admin.manage", "Manage operational administrators");
        permissions.put("superadmin.access", "Access the Super Admin area");
        permissions.put("source.review", "Review restricted external source rows");
        permissions.put("audit.view", "View security and operational audit logs");
        permissions.put("system.configure", "View system health, configuration and data sources");
        permissions.forEach((code, description) -> {
            if (permissionRepository.findByCode(code).isEmpty()) {
                Permission permission = new Permission();
                permission.setCode(code);
                permission.setDescription(description);
                permission.setCategory(code.substring(0, code.indexOf('.')));
                permissionRepository.save(permission);
            }
        });

        Map<String, List<String>> grants = new LinkedHashMap<>();
        grants.put("CUSTOMER", List.of("gate.view", "train.view", "route.search"));
        grants.put("USER", List.of("gate.view", "train.view", "route.search"));
        grants.put("STAFF", List.of("gate.view", "gate.update", "train.view", "route.search",
                "report.view", "report.moderate"));
        grants.put("ADMIN", List.of("gate.view", "gate.update", "gate.manage", "train.view",
                "route.search", "report.view", "report.moderate", "staff.manage", "admin.manage"));
        grants.put("SUPER_ADMIN", List.of("gate.view", "gate.update", "gate.manage", "train.view",
                "route.search", "report.view", "report.moderate", "staff.manage", "admin.manage",
                "superadmin.access", "source.review", "audit.view", "system.configure"));
        grants.forEach((roleName, codes) -> {
            RoleDefinition role = roleRepository.findById(roleName).orElseThrow();
            for (String code : codes) {
                Permission permission = permissionRepository.findByCode(code).orElseThrow();
                RolePermissionId id = new RolePermissionId(roleName, permission.getId());
                if (rolePermissionRepository.findById(id).isEmpty()) {
                    rolePermissionRepository.save(new RolePermission(role, permission));
                }
            }
        });
    }

    private void seedZonesAndDivisions() {
        if (zoneRepository.count() == 0) {
            zoneRepository.saveAll(List.of(
                    zone("SCR", "South Central Railway", "Secunderabad"),
                    zone("NWR", "North Western Railway", "Jaipur"),
                    zone("SWR", "South Western Railway", "Hubballi"),
                    zone("ECoR", "East Coast Railway", "Bhubaneswar"),
                    zone("CR", "Central Railway", "Mumbai"),
                    zone("WR", "Western Railway", "Mumbai")
            ));
        }
        if (divisionRepository.count() == 0) {
            divisionRepository.saveAll(List.of(
                    division("HYB", "Hyderabad", "SCR"),
                    division("SC", "Secunderabad", "SCR"),
                    division("BZA", "Vijayawada", "SCR"),
                    division("GNT", "Guntur", "SCR"),
                    division("GTA", "Guntakal", "SCR")
            ));
        }
    }

    private void seedDataSources() {
        upsertSource("OpenStreetMap", "MAP", "https://www.openstreetmap.org", "REAL_API",
                DataClassification.VERIFIED_REFERENCE, true,
                "Map tiles and place reference data used by the route map.");
        upsertSource("OSRM", "ROUTING", liveProperties.getOsrmBaseUrl(), "REAL_API",
                DataClassification.VERIFIED_REFERENCE, true,
                "Calculates route geometry, distance and duration. No route is invented when unavailable.");
        upsertSource("Train Reference Catalogue", "REFERENCE", "https://www.railyatri.in/", "REFERENCE",
                DataClassification.VERIFIED_REFERENCE, true,
                "30 checked train names, numbers, routes and scheduled times. No live tracking claim.");
        upsertSource("Restricted Source Dataset", "IMPORT", restrictedDatasetPath, "SIMULATION",
                DataClassification.SIMULATED, true,
                "Retained for Super Admin source review only; never promoted to operational data.");
        upsertSource("Gate Sensor Webhook", "SENSOR", "/api/live/sensor", "OPERATIONAL",
                DataClassification.VERIFIED_LIVE, sensorApiKey != null && !sensorApiKey.isBlank(),
                "Optional authenticated gate-status sensor. Disabled until a shared secret is configured.");
        upsertSource("Simulation Provider", "SIMULATION", "internal", "SIMULATION",
                DataClassification.SIMULATED,
                "simulation".equalsIgnoreCase(liveProperties.getProviderMode()),
                "Acknowledged demonstration feed. Never presented as genuine live railway data.");
    }

    private void upsertSource(String name, String type, String url, String mode,
                              DataClassification classification, boolean enabled, String description) {
        DataSourceEntity source = dataSourceRepository.findAllByOrderByNameAsc().stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst().orElseGet(DataSourceEntity::new);
        source.setName(name);
        source.setSourceType(type);
        source.setUrl(url);
        source.setDataMode(mode);
        source.setDataClassification(classification);
        source.setEnabled(enabled);
        source.setDescription(description);
        dataSourceRepository.save(source);
    }

    private static RailwayZone zone(String code, String name, String headquarters) {
        RailwayZone zone = new RailwayZone();
        zone.setCode(code);
        zone.setName(name);
        zone.setHeadquarters(headquarters);
        zone.setActive(true);
        return zone;
    }

    private static RailwayDivision division(String code, String name, String zoneCode) {
        RailwayDivision division = new RailwayDivision();
        division.setCode(code);
        division.setName(name);
        division.setZoneCode(zoneCode);
        division.setActive(true);
        return division;
    }
}
