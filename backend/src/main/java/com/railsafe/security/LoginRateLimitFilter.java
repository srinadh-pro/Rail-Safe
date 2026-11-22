package com.railsafe.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Blocks login attempts from clients that have exceeded the failed-attempt
 * threshold, before their credentials reach the AuthenticationManager.
 *
 * Registered only inside the Spring Security chain (after CsrfFilter) so that
 * CSRF is validated first and the filter is never also auto-registered as a
 * plain servlet filter. When running behind a trusted reverse proxy, configure
 * server.forward-headers-strategy so getRemoteAddr() is the real client IP.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String STAFF_LOGIN_PATH = "/api/auth/staff-login";
    private static final String LOCKED_RESPONSE =
            "{\"error\":\"Too Many Requests\",\"message\":\"Too many failed login attempts. Please try again later.\"}";

    private final LoginAttemptService loginAttemptService;

    public LoginRateLimitFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = relativePath(request);
        return !HttpMethod.POST.matches(request.getMethod())
                || !(LOGIN_PATH.equals(path) || STAFF_LOGIN_PATH.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = clientKey(request);
        if (loginAttemptService.isLocked(key)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(LOCKED_RESPONSE);
            return;
        }

        loginAttemptService.beginAttempt(key);
        try {
            filterChain.doFilter(request, response);
        } finally {
            loginAttemptService.endAttempt();
        }
    }

    private static String relativePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path;
    }

    private static String clientKey(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }
}
