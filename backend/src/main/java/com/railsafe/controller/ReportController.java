package com.railsafe.controller;

import com.railsafe.dto.ReportDecisionRequest;
import com.railsafe.dto.ReportRequest;
import com.railsafe.dto.ReportResponse;
import com.railsafe.entity.ReportStatus;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReportResponse> create(@Valid @RequestBody ReportRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.create(request, principal));
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public List<ReportResponse> mine(@AuthenticationPrincipal AppUserPrincipal principal) {
        return reportService.mine(principal.getId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','SUPER_ADMIN')")
    public List<ReportResponse> list(@RequestParam(required = false) ReportStatus status,
                                     @AuthenticationPrincipal AppUserPrincipal principal) {
        return reportService.list(status, principal);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','SUPER_ADMIN')")
    public ReportResponse approve(@PathVariable Long id,
                                  @Valid @RequestBody(required = false) ReportDecisionRequest request,
                                  @AuthenticationPrincipal AppUserPrincipal principal) {
        return reportService.approve(id, request, principal);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','SUPER_ADMIN')")
    public ReportResponse reject(@PathVariable Long id,
                                 @Valid @RequestBody(required = false) ReportDecisionRequest request,
                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        return reportService.reject(id, request, principal);
    }
}
