package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.controller.AdminUserController;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.AdminUserService;
import com.shiftworks.jobops.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
class RbacIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AdminUserService adminUserService;
    @MockBean private SessionService sessionService;

    private UserSession buildSession(UserRole role) {
        User user = new User();
        user.setId(99L);
        user.setUsername("testuser");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordChangedAt(Instant.now());
        UserSession session = new UserSession();
        session.setId("test-session-token");
        session.setUser(user);
        session.setValid(true);
        session.setLastActiveAt(Instant.now());
        session.setAbsoluteExpiry(Instant.now().plusSeconds(43200));
        session.setCsrfToken("test-csrf");
        return session;
    }

    @Test
    void adminUsersEndpointDeniedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminAuditEndpointDeniedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void employerCannotAccessAdminUsers() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/admin/users")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isForbidden());
    }

    @Test
    void reviewerCannotAccessAdminUsers() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/admin/users")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isForbidden());
    }
}
