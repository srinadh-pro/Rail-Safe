package com.railsafe.controller;

import com.railsafe.dto.*;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.SuperAdminOperationsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Super Admin system, security, role/permission, analytics and data-source APIs. */
@RestController
@RequestMapping("/api/super-admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminOperationsController {

    private final SuperAdminOperationsService service;

    public SuperAdminOperationsController(SuperAdminOperationsService service) {
        this.service = service;
    }

    @GetMapping("/system/health")
    public SystemHealthResponse health() { return service.health(); }

    @GetMapping("/data-sources")
    public List<DataSourceResponse> dataSources() { return service.dataSources(); }

    @PutMapping("/data-sources/{id}/enabled")
    public DataSourceResponse setDataSourceEnabled(@PathVariable Long id,
                                                   @RequestParam boolean enabled,
                                                   @AuthenticationPrincipal AppUserPrincipal principal) {
        return service.setDataSourceEnabled(id, enabled, principal.getUsername());
    }

    @GetMapping("/security")
    public SecurityOverviewResponse security() { return service.security(); }

    @GetMapping("/config")
    public ConfigOverviewResponse config() { return service.config(); }

    @PutMapping("/config")
    public ConfigOverviewResponse updateConfig(@Valid @RequestBody ConfigUpdateRequest request,
                                               @AuthenticationPrincipal AppUserPrincipal principal) {
        return service.updateConfig(request, principal.getUsername());
    }

    @GetMapping("/backups")
    public BackupOverviewResponse backups() { return service.backups(); }

    @GetMapping("/roles")
    public List<RolePermissionOverviewResponse> roles() { return service.roles(); }

    @PutMapping("/roles/{role}/permissions")
    public RolePermissionOverviewResponse updateRolePermissions(
            @PathVariable String role,
            @Valid @RequestBody UpdateRolePermissionsRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return service.updateRolePermissions(role, request, principal.getUsername());
    }

    @GetMapping("/users")
    public List<UserAccountResponse> users() { return service.users(); }

    @PutMapping("/users/{id}/enabled")
    public UserAccountResponse setUserEnabled(@PathVariable Long id,
                                              @RequestParam boolean enabled,
                                              @AuthenticationPrincipal AppUserPrincipal principal) {
        return service.setUserEnabled(id, enabled, String.valueOf(principal.getId()));
    }

    @PutMapping("/users/{id}/role")
    public UserAccountResponse setUserRole(@PathVariable Long id,
                                           @Valid @RequestBody UserRoleUpdateRequest request,
                                           @AuthenticationPrincipal AppUserPrincipal principal) {
        return service.setUserRole(id, request, String.valueOf(principal.getId()));
    }

    @GetMapping("/analytics")
    public AnalyticsResponse analytics() { return service.analytics(); }
}
