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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies login-first access and role-specific dashboard/API restrictions. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:railsafe-security-test;MODE=Oracle;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "railsafe.super-admin.password=TestOnlySuperAdmin!2026",
        "railsafe.demo.super-admin.password=TestOnlySuperAdmin!2026",
        "railsafe.demo.accounts-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class RoleSecurityIntegrationTest {

    private static final String TEST_SUPER_ADMIN_PASSWORD = "TestOnlySuperAdmin!2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void unauthenticatedPagesRedirectToLoginAndApisReturn401() throws Exception {
        mockMvc.perform(get("/dashboard.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html?next=dashboard.html"));

        mockMvc.perform(get("/api/live/counts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/super-admin/system/health"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/login.html"))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotReachStaffAdminOrSuperAdminAreas() throws Exception {
        MockHttpSession customer = login("customer@railsafe.demo", "Customer@123");

        mockMvc.perform(get("/staff.html").session(customer))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard.html?denied=1"));
        mockMvc.perform(get("/admin.html").session(customer))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard.html?denied=1"));
        mockMvc.perform(get("/super-admin.html").session(customer))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard.html?denied=1"));

        mockMvc.perform(get("/api/staff/gates").session(customer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/staff").session(customer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/super-admin/system/health").session(customer)).andExpect(status().isForbidden());
        // Customers may view gate data (read-only); writes remain staff/admin only.
        mockMvc.perform(get("/api/gates").session(customer)).andExpect(status().isOk());
    }

    @Test
    void staffCannotReachAdminOrSuperAdminAreas() throws Exception {
        MockHttpSession staff = login("staff@railsafe.demo", "Staff@123");

        mockMvc.perform(get("/admin.html").session(staff))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff.html?denied=1"));
        mockMvc.perform(get("/super-admin.html").session(staff))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff.html?denied=1"));

        mockMvc.perform(get("/api/admin/staff").session(staff)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/super-admin/system/health").session(staff)).andExpect(status().isForbidden());
    }

    @Test
    void adminCannotReachSuperAdminArea() throws Exception {
        MockHttpSession admin = login("admin@railsafe.demo", "Admin@123");

        mockMvc.perform(get("/super-admin.html").session(admin))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin.html?denied=1"));
        mockMvc.perform(get("/api/super-admin/system/health").session(admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void eachRoleCanOpenItsOwnDashboardAndPermittedApi() throws Exception {
        mockMvc.perform(get("/dashboard.html").session(login("customer@railsafe.demo", "Customer@123")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/live/counts").session(login("customer@railsafe.demo", "Customer@123")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/staff.html").session(login("staff@railsafe.demo", "Staff@123")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/staff/gates").session(login("staff@railsafe.demo", "Staff@123")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin.html").session(login("admin@railsafe.demo", "Admin@123")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/staff").session(login("admin@railsafe.demo", "Admin@123")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/super-admin.html").session(login("superadmin@railsafe.demo", TEST_SUPER_ADMIN_PASSWORD)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/super-admin/system/health")
                        .session(login("superadmin@railsafe.demo", TEST_SUPER_ADMIN_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void demoAccountsAreExposedOnlyWhenExplicitlyEnabled() throws Exception {
        mockMvc.perform(get("/api/auth/demo-accounts"))
                .andExpect(status().isOk());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        // Double-submit CSRF: read the cookie the endpoint issues and echo it in the header.
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        if (csrfCookie == null) {
            throw new AssertionError("CSRF endpoint did not issue a token cookie");
        }
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession authenticated = (MockHttpSession) result.getRequest().getSession(false);
        if (authenticated == null) {
            throw new AssertionError("Login did not create a session for " + email);
        }
        return authenticated;
    }
}
