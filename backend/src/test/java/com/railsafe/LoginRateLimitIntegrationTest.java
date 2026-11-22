package com.railsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railsafe.dto.LoginRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the lockout filter is wired into the Spring Security chain: after N
 * bad credentials (with a valid CSRF token) the next login is rejected with 429
 * before authentication is attempted.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:railsafe-ratelimit-test;MODE=Oracle;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "railsafe.super-admin.password=TestOnlySuperAdmin!2026",
        "railsafe.demo.super-admin.password=TestOnlySuperAdmin!2026",
        "railsafe.demo.accounts-enabled=true",
        "railsafe.security.login-max-attempts=2",
        "railsafe.security.login-lock-minutes=15"
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class LoginRateLimitIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void locksClientAfterRepeatedBadCredentials() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Cookie csrf = csrfCookie(session);

        // Two bad-credential attempts with a valid CSRF token are counted.
        postLogin(session, csrf, "wrong-one@example.test").andExpect(status().isUnauthorized());
        postLogin(session, csrf, "wrong-two@example.test").andExpect(status().isUnauthorized());

        // Third attempt is rejected by the rate-limit filter before authentication.
        postLogin(session, csrf, "wrong-three@example.test").andExpect(status().isTooManyRequests());

        // A different endpoint is unaffected: CSRF handshake still works.
        mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk());
    }

    private Cookie csrfCookie(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) {
            throw new AssertionError("CSRF endpoint did not issue a token cookie");
        }
        return cookie;
    }

    private org.springframework.test.web.servlet.ResultActions postLogin(MockHttpSession session,
                                                                          Cookie csrf,
                                                                          String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .session(session)
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, "DefinitelyWrong!123"))));
    }
}
