package com.railsafe.controller;

import com.railsafe.dto.*;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.GateService;
import com.railsafe.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin/Super-Admin-only STAFF account management. */
@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminStaffController {

    private final StaffAccountService staffService;
    private final GateService gateService;

    public AdminStaffController(StaffAccountService staffService, GateService gateService) {
        this.staffService = staffService;
        this.gateService = gateService;
    }

    @GetMapping
    public List<StaffAccountResponse> list() {
        return staffService.list();
    }

    /** Complete staff status-update history, optionally narrowed to one staff account. */
    @GetMapping("/activity")
    public List<StatusHistoryResponse> activity(@RequestParam(required = false) Long staffId) {
        return staffId == null ? gateService.historyForStaffRole() : gateService.historyForStaff(staffId);
    }

    @PostMapping
    public StaffAccountResponse create(@Valid @RequestBody StaffAccountRequest request,
                                       @AuthenticationPrincipal AppUserPrincipal principal) {
        return staffService.create(request, principal.getUsername());
    }

    @PutMapping("/{id}")
    public StaffAccountResponse update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateStaffRequest request,
                                       @AuthenticationPrincipal AppUserPrincipal principal) {
        return staffService.update(id, request, principal.getUsername());
    }

    @PutMapping("/{id}/enabled")
    public StaffAccountResponse setEnabled(@PathVariable Long id,
                                           @RequestParam boolean enabled,
                                           @AuthenticationPrincipal AppUserPrincipal principal) {
        return staffService.setEnabled(id, enabled, principal.getUsername());
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable Long id,
                                  @AuthenticationPrincipal AppUserPrincipal principal) {
        staffService.delete(id, principal.getUsername());
        return new MessageResponse("STAFF account deactivated and assignments removed.");
    }

    @PutMapping("/{id}/assignments")
    public StaffAccountResponse assign(@PathVariable Long id,
                                       @RequestBody StaffAssignmentRequest request,
                                       @AuthenticationPrincipal AppUserPrincipal principal) {
        return staffService.assignGates(id, request, principal.getUsername());
    }
}
