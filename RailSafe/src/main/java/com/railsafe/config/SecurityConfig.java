package com.railsafe.config;

import com.railsafe.security.LoginAttemptService;
import com.railsafe.security.LoginRateLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Session-based security with login-first access.
 *
 * Public: login/registration pages and their CSS/JS, the auth endpoints, and the
 * optional shared-secret sensor webhook. Every other page, asset bundle and API
 * requires an authenticated session, so gate and train information is never
 * exposed before login.
 *
 * USER: notifications, saved routes, reports and /me.
 * STAFF/ADMIN/SUPER_ADMIN: gate writes, report moderation and admin statistics.
 * SUPER_ADMIN: the /api/super-admin/** account-management API and its page.
 *
 * Unauthenticated API calls receive JSON 401; browser page requests are redirected
 * to the login page with a safe "next" target.
 *
 * CSRF protection is enabled with a double-submit cookie: the XSRF-TOKEN cookie
 * is readable by the same-origin frontend, which echoes it in X-XSRF-TOKEN on
 * every unsafe request. The shared-secret sensor webhook is exempt because it
 * is not a browser session.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final LoginAttemptService loginAttemptService;

    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean secureCookies;

    @Value("${railsafe.security.hsts-enabled:false}")
    private boolean hstsEnabled;

    @Value("${railsafe.security.hsts-max-age-seconds:31536000}")
    private long hstsMaxAgeSeconds;

    public SecurityConfig(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setSecure(secureCookies);
        // Keep the double-submit cookie restricted to same-site requests; tighten
        // to Strict only when the app is served over HTTPS.
        String csrfSameSite = secureCookies ? "Strict" : "Lax";
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite(csrfSameSite));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Machine-to-machine webhook authenticated by shared secret, not a browser cookie.
                        .ignoringRequestMatchers("/api/live/sensor/**"))
                // Runs after CSRF validation but before authentication, so locked-out
                // attempts never reach the AuthenticationManager. Constructed here so
                // it is not also auto-registered as a plain servlet filter.
                .addFilterAfter(new LoginRateLimitFilter(loginAttemptService), CsrfFilter.class)
                .headers(headers -> headers.httpStrictTransportSecurity(hsts -> {
                    if (hstsEnabled) {
                        hsts.maxAgeInSeconds(hstsMaxAgeSeconds).includeSubDomains(true);
                    } else {
                        hsts.disable();
                    }
                }))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // Public: the login/registration experience and its assets only.
                        .requestMatchers("/login.html", "/staff-login.html", "/register.html",
                                "/css/**", "/js/**", "/images/**", "/favicon.ico", "/favicon.svg", "/error")
                        .permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/staff-login", "/api/auth/register",
                                "/api/auth/logout", "/api/auth/csrf", "/api/auth/demo-accounts",
                                "/api/health").permitAll()
                        // Sensor webhook is authenticated by its shared secret, not a browser session.
                        .requestMatchers(HttpMethod.POST, "/api/live/sensor/**").permitAll()
                        // Super-admin account/system management is SUPER_ADMIN only (checked before generic pages).
                        .requestMatchers("/super-admin.html", "/api/super-admin/**").hasRole("SUPER_ADMIN")
                        // Staff account CRUD is admin-only even though /api/admin/** has legacy staff access.
                        .requestMatchers("/api/admin/staff/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Role-specific dashboards are blocked server-side on manual URL access.
                        .requestMatchers("/admin.html").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/staff.html").hasRole("STAFF")
                        .requestMatchers("/dashboard.html").hasAnyRole("CUSTOMER", "USER")
                        // Login-first: every page and data endpoint below requires a session.
                        .requestMatchers("/", "/*.html", "/live", "/live/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/gates/**", "/api/trains/**", "/api/live/**").authenticated()
                        .requestMatchers("/api/gates/**").hasAnyRole("ADMIN", "STAFF", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/reports").authenticated()
                        .requestMatchers("/api/reports/mine").authenticated()
                        .requestMatchers("/api/reports/**").hasAnyRole("ADMIN", "STAFF", "SUPER_ADMIN")
                        .requestMatchers("/api/routes/alternatives").authenticated()
                        .requestMatchers("/api/routes/**", "/api/notifications/**", "/api/auth/me").authenticated()
                        // Staff-only assigned-gate dashboard.
                        .requestMatchers("/api/staff/**").hasRole("STAFF")
                        // Maintenance requests are operational; reads are authenticated and writes are staff/admin.
                        .requestMatchers(HttpMethod.GET, "/api/maintenance").authenticated()
                        .requestMatchers("/api/maintenance/**").hasAnyRole("STAFF", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "STAFF", "SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (isApiRequest(request)) {
                                writeJson(response, 401, "Unauthorized", "Please log in to continue.");
                            } else {
                                redirectToLogin(request, response);
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (isApiRequest(request)) {
                                writeJson(response, 403, "Forbidden",
                                        "You do not have permission to perform this action.");
                            } else {
                                // Redirect to a page this role can actually open, otherwise a STAFF
                                // user denied /admin.html would loop back into /dashboard.html.
                                response.sendRedirect(request.getContextPath()
                                        + homeFor(SecurityContextHolder.getContext().getAuthentication())
                                        + "?denied=1");
                            }
                        })
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    /** Returns the dashboard this role is allowed to open, used after a page-level denial. */
    private static String homeFor(Authentication authentication) {
        if (authentication != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                switch (authority.getAuthority()) {
                    case "ROLE_SUPER_ADMIN" -> { return "/super-admin.html"; }
                    case "ROLE_ADMIN" -> { return "/admin.html"; }
                    case "ROLE_STAFF" -> { return "/staff.html"; }
                    case "ROLE_CUSTOMER", "ROLE_USER" -> { return "/dashboard.html"; }
                    default -> { /* keep looking */ }
                }
            }
        }
        return "/login.html";
    }

    private static boolean isApiRequest(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return request.getRequestURI().startsWith(contextPath + "/api/");
    }

    /** Redirects a browser page request to the login page, preserving a safe relative target. */
    private static void redirectToLogin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI().substring(contextPath.length());
        if (uri == null || uri.isBlank() || "/".equals(uri)) {
            // No target: let the login page choose the destination based on the role.
            response.sendRedirect(contextPath + "/login.html");
            return;
        }
        String next = uri.startsWith("/") ? uri.substring(1) : uri;
        if ("live".equals(next) || "live/index.html".equals(next)) {
            next = "live/";
        }
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            next = next + "?" + query;
        }
        // Staff-only page requests use the dedicated staff sign-in screen.
        String loginPage = next.equals("staff.html") || next.startsWith("staff.html?")
                ? "/staff-login.html" : "/login.html";
        response.sendRedirect(contextPath + loginPage + "?next="
                + URLEncoder.encode(next, StandardCharsets.UTF_8));
    }

    private static void writeJson(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":" + status
                + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
