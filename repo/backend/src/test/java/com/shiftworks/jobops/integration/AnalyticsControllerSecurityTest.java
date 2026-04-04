package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.AnalyticsController;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.AnalyticsService;
import com.shiftworks.jobops.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that analytics endpoints enforce the correct role requirements:
 * - Most analytics: ADMIN or REVIEWER allowed, EMPLOYER denied.
 * - Admin-only subroutes (/claim-success-rate, /reviewer-activity): REVIEWER denied.
 */
@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class AnalyticsControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private AnalyticsService analyticsService;
    @MockBean  private SessionService sessionService;

    private UserSession buildSession(long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordChangedAt(Instant.now());
        UserSession session = new UserSession();
        session.setId("tok-" + id);
        session.setUser(user);
        session.setValid(true);
        session.setLastActiveAt(Instant.now());
        session.setAbsoluteExpiry(Instant.now().plusSeconds(43200));
        session.setCsrfToken("csrf-" + id);
        return session;
    }

    @Test
    void analyticsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics/post-volume"))
            .andExpect(status().isForbidden());
    }

    @Test
    void employerCannotAccessAnyAnalytics() throws Exception {
        UserSession session = buildSession(10L, UserRole.EMPLOYER);
        when(sessionService.findValidSession("tok-10")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/analytics/post-volume")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "tok-10"))
                .header("X-XSRF-TOKEN", "csrf-10"))
            .andExpect(status().isForbidden());
    }

    @Test
    void reviewerCanAccessPostVolume() throws Exception {
        UserSession session = buildSession(20L, UserRole.REVIEWER);
        when(sessionService.findValidSession("tok-20")).thenReturn(Optional.of(session));
        when(analyticsService.postVolume(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/post-volume")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "tok-20"))
                .header("X-XSRF-TOKEN", "csrf-20"))
            .andExpect(status().isOk());
    }

    @Test
    void reviewerCannotAccessClaimSuccessRate() throws Exception {
        // /claim-success-rate has @PreAuthorize("hasRole('ADMIN')") — reviewer must be denied
        UserSession session = buildSession(20L, UserRole.REVIEWER);
        when(sessionService.findValidSession("tok-20")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/analytics/claim-success-rate")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "tok-20"))
                .header("X-XSRF-TOKEN", "csrf-20"))
            .andExpect(status().isForbidden());
    }

    @Test
    void reviewerCannotAccessReviewerActivity() throws Exception {
        // /reviewer-activity has @PreAuthorize("hasRole('ADMIN')") — reviewer must be denied
        UserSession session = buildSession(20L, UserRole.REVIEWER);
        when(sessionService.findValidSession("tok-20")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/analytics/reviewer-activity")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "tok-20"))
                .header("X-XSRF-TOKEN", "csrf-20"))
            .andExpect(status().isForbidden());
    }
}
