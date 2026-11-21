package com.railsafe.controller;

import com.railsafe.dto.MaintenanceRequestRequest;
import com.railsafe.dto.MaintenanceResponse;
import com.railsafe.dto.MaintenanceStatusRequest;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.MaintenanceService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Maintenance request workflow. Staff scope is enforced in the service layer. */
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public List<MaintenanceResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return maintenanceService.list(principal);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','SUPER_ADMIN')")
    public MaintenanceResponse create(@Valid @RequestBody MaintenanceRequestRequest request,
                                      @AuthenticationPrincipal AppUserPrincipal principal) {
        return maintenanceService.create(request, principal);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','SUPER_ADMIN')")
    public MaintenanceResponse updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody MaintenanceStatusRequest request,
                                            @AuthenticationPrincipal AppUserPrincipal principal) {
        return maintenanceService.updateStatus(id, request, principal);
    }
}
