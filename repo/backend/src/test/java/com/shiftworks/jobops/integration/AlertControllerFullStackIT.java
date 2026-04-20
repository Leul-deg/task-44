package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.repository.UserSessionRepository;
import com.shiftworks.jobops.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack (no-mock) IT for {@code AlertController}.
 *
 * Mirrors {@code test_alerts.sh} in Java: real Spring context, real JPA, real
 * security filters — no {@code @MockBean}. Covers listing, unread-count, severity
 * filter enum binding, role denial, and missing-CSRF denial on mutating endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AlertControllerFullStackIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSessionRepository userSessionRepository;

    private SessionHandle admin;
    private SessionHandle employer;

    @BeforeEach
    void setup() {
        admin = createUserWithSession("alerts_admin_" + System.nanoTime(), UserRole.ADMIN);
        employer = createUserWithSession("alerts_emp_" + System.nanoTime(), UserRole.EMPLOYER);
    }

    @Test
    void list_asAdmin_returnsPageEnvelope() throws Exception {
        mockMvc.perform(get("/api/alerts")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void unreadCount_asAdmin_returnsOk() throws Exception {
        mockMvc.perform(get("/api/alerts/unread-count")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(status().isOk());
    }

    @Test
    void list_withValidSeverityEnum_returnsOk() throws Exception {
        mockMvc.perform(get("/api/alerts?severity=CRITICAL")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(status().isOk());
    }

    @Test
    void list_withInvalidSeverity_isRejected() throws Exception {
        mockMvc.perform(get("/api/alerts?severity=BOGUS")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(result -> {
                int st = result.getResponse().getStatus();
                if (st < 400 || st >= 600) {
                    throw new AssertionError(
                        "Invalid enum should fail binding, expected 4xx/5xx, got " + st);
                }
            });
    }

    @Test
    void anonymousRequest_isDenied() throws Exception {
        mockMvc.perform(get("/api/alerts"))
            .andExpect(status().isForbidden());
    }

    @Test
    void employerRole_isDenied() throws Exception {
        mockMvc.perform(get("/api/alerts")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, employer.sessionId)))
            .andExpect(status().isForbidden());
    }

    @Test
    void markRead_withoutCsrfHeader_returns403() throws Exception {
        mockMvc.perform(put("/api/alerts/1/read")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(status().isForbidden());
    }

    @Test
    void markRead_onMissingAlertWithValidCsrf_isNotServerError() throws Exception {
        mockMvc.perform(put("/api/alerts/999999999/read")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId))
                .header("X-XSRF-TOKEN", admin.csrfToken))
            .andExpect(result -> {
                int st = result.getResponse().getStatus();
                if (st >= 500) {
                    throw new AssertionError(
                        "Missing alert id should be a client error (or graceful 200), got " + st);
                }
            });
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private SessionHandle createUserWithSession(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.local");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordChangedAt(Instant.now());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user = userRepository.save(user);

        UserSession session = new UserSession();
        session.setId("alerts-it-" + username);
        session.setUser(user);
        session.setCreatedAt(Instant.now());
        session.setLastActiveAt(Instant.now());
        session.setAbsoluteExpiry(Instant.now().plusSeconds(3600));
        session.setValid(true);
        session.setCsrfToken("csrf-" + username);
        userSessionRepository.save(session);

        return new SessionHandle(user.getId(), session.getId(), session.getCsrfToken());
    }

    private record SessionHandle(Long userId, String sessionId, String csrfToken) {}
}
