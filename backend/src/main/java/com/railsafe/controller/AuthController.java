package com.railsafe.controller;

import com.railsafe.dto.AuthResponse;
import com.railsafe.dto.LoginRequest;
import com.railsafe.dto.MessageResponse;
import com.railsafe.dto.RegisterRequest;
import com.railsafe.entity.Role;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/**
 * JSON authentication endpoints.
 * A successful login stores the SecurityContext in the HTTP session and the browser
 * receives a JSESSIONID cookie. No password hash is ever returned.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthService authService,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Account created successfully. Please log in."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email().trim().toLowerCase(),
                        request.password()
                )
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Rotate the session id on login to prevent session fixation, then persist
        // the SecurityContext so later requests are authenticated.
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(new AuthResponse(
                principal.getId(),
                principal.getFullName(),
                principal.getUsername(),
                principal.getRole().name()
        ));
    }

    /**
     * Dedicated railway staff sign-in. Credentials that authenticate but do not
     * belong to a STAFF account are rejected before any session is established.
     */
    @PostMapping("/staff-login")
    public ResponseEntity<?> staffLogin(@Valid @RequestBody LoginRequest request,
                                        HttpServletRequest httpRequest,
                                        HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email().trim().toLowerCase(),
                        request.password()
                )
        );

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        if (principal.getRole() != Role.STAFF) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("These credentials are not authorised for the railway staff console."));
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.ok(new AuthResponse(
                principal.getId(),
                principal.getFullName(),
                principal.getUsername(),
                principal.getRole().name()
        ));
    }

    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return new AuthResponse(
                principal.getId(),
                principal.getFullName(),
                principal.getUsername(),
                principal.getRole().name()
        );
    }
}
