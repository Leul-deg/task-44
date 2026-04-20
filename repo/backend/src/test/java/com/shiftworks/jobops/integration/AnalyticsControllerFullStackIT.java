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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack (no-mock) IT for {@code AnalyticsController}.
 *
 * Replaces the bash-based {@code test_analytics.sh} harness with the Java/JUnit idiom
 * used elsewhere in this codebase ({@code JobFlowFullStackIT}). Runs against the real
 * Spring context, real JPA, and the real security filter chain — no {@code @MockBean}
 * on either the service or {@code SessionService}.
 *
 * Coverage:
 *   - All 7 {@code /api/analytics/*} endpoints return 200 for an ADMIN session
 *     (@code postVolumeEndpoints_returnJsonArrayForAdmin}).
 *   - REVIEWER role is allowed (hasAnyRole('ADMIN','REVIEWER')).
 *   - EMPLOYER role and anonymous requests are denied (403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnalyticsControllerFullStackIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSessionRepository userSessionRepository;

    private SessionHandle admin;
    private SessionHandle reviewer;
    private SessionHandle employer;

    @BeforeEach
    void setup() {
        admin = createUserWithSession("analytics_admin_" + System.nanoTime(), UserRole.ADMIN);
        reviewer = createUserWithSession("analytics_rev_" + System.nanoTime(), UserRole.REVIEWER);
        employer = createUserWithSession("analytics_emp_" + System.nanoTime(), UserRole.EMPLOYER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/analytics/post-volume",
        "/api/analytics/post-status-distribution",
        "/api/analytics/claim-success-rate",
        "/api/analytics/avg-handling-time",
        "/api/analytics/reviewer-activity",
        "/api/analytics/approval-rate",
        "/api/analytics/takedown-trend"
    })
    void everyAnalyticsEndpoint_returns200ForAdmin(String path) throws Exception {
        mockMvc.perform(get(path)
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void postVolume_returnsJsonArrayForAdmin() throws Exception {
        mockMvc.perform(get("/api/analytics/post-volume?from=2025-01-01&to=2025-12-31")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void reviewerRole_isAllowed() throws Exception {
        mockMvc.perform(get("/api/analytics/post-volume")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, reviewer.sessionId)))
            .andExpect(status().isOk());
    }

    @Test
    void employerRole_isDenied() throws Exception {
        mockMvc.perform(get("/api/analytics/post-volume")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, employer.sessionId)))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequest_isDenied() throws Exception {
        mockMvc.perform(get("/api/analytics/post-volume"))
            .andExpect(status().isForbidden());
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
        session.setId("analytics-it-" + username);
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
