package com.railsafe.controller;

import com.railsafe.dto.AdminStatsResponse;
import com.railsafe.service.AdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','SUPER_ADMIN')")
    public AdminStatsResponse stats() {
        return adminService.stats();
    }
}
