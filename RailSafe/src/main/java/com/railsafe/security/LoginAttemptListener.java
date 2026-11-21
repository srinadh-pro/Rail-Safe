package com.railsafe.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Feeds authentication outcomes into the login lockout tracker. The client key
 * is supplied by {@link LoginRateLimitFilter} through a thread-local, so the
 * outcome is attributed to the same client that started the attempt.
 */
@Component
public class LoginAttemptListener {

    private final LoginAttemptService loginAttemptService;

    public LoginAttemptListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        loginAttemptService.recordSuccessForCurrentRequest();
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        loginAttemptService.recordFailureForCurrentRequest();
    }
}
