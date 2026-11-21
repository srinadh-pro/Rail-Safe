package com.railsafe.controller;

import com.railsafe.dto.AdminAccountResponse;
import com.railsafe.dto.AuditLogResponse;
import com.railsafe.dto.CreateAdminRequest;
import com.railsafe.dto.DemoDataRecordResponse;
import com.railsafe.dto.SourceReviewSummaryResponse;
import com.railsafe.dto.UpdateAdminRequest;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.DemoDataService;
import com.railsafe.service.SuperAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SUPER_ADMIN-only account management. The role check is enforced on the server;
 * the hidden navigation link is only a convenience.
 */
@RestController
@RequestMapping("/api/super-admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final DemoDataService demoDataService;

    public SuperAdminController(SuperAdminService superAdminService,
                                DemoDataService demoDataService) {
        this.superAdminService = superAdminService;
        this.demoDataService = demoDataService;
    }

    /** Read-only inventory for the dedicated Super Admin review area. */
    @GetMapping("/demo-data")
    public List<DemoDataRecordResponse> demoData() {
        return demoDataService.list();
    }

    /** Aggregated restricted-source counts for the review header. */
    @GetMapping("/source-review-summary")
    public SourceReviewSummaryResponse sourceReviewSummary() {
        return demoDataService.sourceReviewSummary();
    }

    @GetMapping("/admins")
    public List<AdminAccountResponse> admins() {
        return superAdminService.listAdminAccounts();
    }

    /** Read-only administrative action trail for the Super Admin. */
    @GetMapping("/audit-logs")
    public List<AuditLogResponse> auditLogs() {
        return superAdminService.listAuditLogs();
    }

    @PostMapping("/admins")
    public ResponseEntity<AdminAccountResponse> createAdmin(
            @Valid @RequestBody CreateAdminRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(superAdminService.createAdmin(request, actorName(actor)));
    }

    @PutMapping("/admins/{id}")
    public AdminAccountResponse updateAdmin(@PathVariable Long id,
                                            @Valid @RequestBody UpdateAdminRequest request,
                                            @AuthenticationPrincipal AppUserPrincipal actor) {
        return superAdminService.updateAdmin(id, request, actorName(actor));
    }

    private static String actorName(AppUserPrincipal actor) {
        return actor == null ? "UNKNOWN" : actor.getUsername();
    }
}
