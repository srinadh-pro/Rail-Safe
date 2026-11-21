package com.railsafe.controller;

import com.railsafe.dto.GateResponse;
import com.railsafe.dto.StatusHistoryResponse;
import com.railsafe.live.dto.LiveCountsResponse;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.StaffDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** STAFF-only view of actively assigned gates. */
@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasRole('STAFF')")
public class StaffController {

    private final StaffDashboardService dashboardService;

    public StaffController(StaffDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/gates")
    public List<GateResponse> gates(@AuthenticationPrincipal AppUserPrincipal principal) {
        return dashboardService.gates(principal.getId());
    }

    @GetMapping("/counts")
    public LiveCountsResponse counts(@AuthenticationPrincipal AppUserPrincipal principal) {
        return dashboardService.counts(principal.getId());
    }

    /** The signed-in STAFF member's own gate updates only. */
    @GetMapping("/activity")
    public List<StatusHistoryResponse> activity(@AuthenticationPrincipal AppUserPrincipal principal) {
        return dashboardService.activity(principal.getId());
    }
}
