package com.railsafe.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Supplies the CSRF token to same-origin browser clients. Accessing the token
 * causes Spring Security to write the XSRF-TOKEN cookie; clients echo the value
 * in the X-XSRF-TOKEN header on every POST/PUT/PATCH/DELETE request.
 */
@RestController
@RequestMapping("/api/auth/csrf")
public class CsrfController {

    @GetMapping
    public Map<String, String> token(CsrfToken token) {
        return Map.of("token", token.getToken());
    }
}
