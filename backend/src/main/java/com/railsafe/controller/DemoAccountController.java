package com.railsafe.controller;

import com.railsafe.dto.DemoAccountResponse;
import com.railsafe.service.DemoAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Demo sign-in shortcuts. The service refuses to expose credentials outside
 * h2/dev profiles unless explicitly enabled for a local demonstration.
 */
@RestController
@RequestMapping("/api/auth/demo-accounts")
public class DemoAccountController {

    private final DemoAccountService demoAccountService;

    public DemoAccountController(DemoAccountService demoAccountService) {
        this.demoAccountService = demoAccountService;
    }

    @GetMapping
    public List<DemoAccountResponse> accounts() {
        return demoAccountService.accounts();
    }
}
