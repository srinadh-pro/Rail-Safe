package com.railsafe.config;

import com.railsafe.service.AuditLogService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/** Records authentication outcomes without ever logging credentials. */
@Component
public class AuthenticationAuditListener {

    private final AuditLogService auditLogService;

    public AuthenticationAuditListener(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        auditLogService.log(principalName(event.getAuthentication()), "LOGIN_SUCCESS", "AUTHENTICATION", null, null);
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        auditLogService.log(principalName(event.getAuthentication()), "LOGIN_FAILURE", "AUTHENTICATION", null,
                "Bad credentials");
    }

    private static String principalName(org.springframework.security.core.Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails details) {
            return details.getUsername();
        }
        return principal == null ? "UNKNOWN" : String.valueOf(principal);
    }
}
