package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.AnalyticsController;
import com.shiftworks.jobops.dto.ApprovalRateResponse;
import com.shiftworks.jobops.dto.AverageHandlingTimeResponse;
import com.shiftworks.jobops.dto.ClaimSuccessRateResponse;
import com.shiftworks.jobops.dto.PostStatusPoint;
import com.shiftworks.jobops.dto.PostVolumePoint;
import com.shiftworks.jobops.dto.ReviewerActivityPoint;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.JobStatus;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class AnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AnalyticsService analyticsService;
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
    void postVolumeAsAdmin_returns200WithList() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(any())).thenReturn(Optional.of(session));
        when(analyticsService.postVolume(any(), any()))
            .thenReturn(List.of(new PostVolumePoint(LocalDate.of(2026, 4, 10), 5L)));

        mockMvc.perform(get("/api/analytics/post-volume")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].count", is(5)));
    }

    @Test
    void postStatusDistributionAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(any())).thenReturn(Optional.of(session));
        when(analyticsService.postStatusDistribution(any(), any()))
            .thenReturn(List.of(new PostStatusPoint(JobStatus.DRAFT, 3L)));

        mockMvc.perform(get("/api/analytics/post-status-distribution")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThan(0))));
    }

    @Test
    void approvalRateAsAdmin_returns200WithRate() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(any())).thenReturn(Optional.of(session));
        when(analyticsService.approvalRate(any(), any()))
            .thenReturn(new ApprovalRateResponse(0.75, 3L, 4L));

        mockMvc.perform(get("/api/analytics/approval-rate")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rate").exists());
    }

    @Test
    void avgHandlingTimeAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(any())).thenReturn(Optional.of(session));
        when(analyticsService.averageHandlingTime(any(), any()))
            .thenReturn(new AverageHandlingTimeResponse(12.5));

        mockMvc.perform(get("/api/analytics/avg-handling-time")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.averageHours", is(12.5)));
    }

    @Test
    void claimSuccessRateDeniedForReviewer_returns403() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(any())).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/analytics/claim-success-rate")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isForbidden());
    }

    @Test
    void reviewerActivityDeniedForReviewer_returns403() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(any())).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/analytics/reviewer-activity")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isForbidden());
    }

    @Test
    void analyticsRequiresAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/analytics/post-volume"))
            .andExpect(status().isForbidden());
    }

    @Test
    void claimSuccessRateAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(any())).thenReturn(Optional.of(session));
        when(analyticsService.claimSuccessRate(any(), any()))
            .thenReturn(new ClaimSuccessRateResponse(0.8, 8L, 10L));

        mockMvc.perform(get("/api/analytics/claim-success-rate")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rate", is(0.8)));
    }

    @Test
    void reviewerActivityAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(any())).thenReturn(Optional.of(session));
        when(analyticsService.reviewerActivity(any(), any()))
            .thenReturn(List.of(new ReviewerActivityPoint(1L, "reviewer1", LocalDate.of(2026, 4, 1), 5L)));

        mockMvc.perform(get("/api/analytics/reviewer-activity")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username", is("reviewer1")));
    }

    @Test
    void takedownTrendAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(any())).thenReturn(Optional.of(session));
        when(analyticsService.takedownTrend(any(), any()))
            .thenReturn(List.of(new PostVolumePoint(LocalDate.of(2026, 4, 5), 2L)));

        mockMvc.perform(get("/api/analytics/takedown-trend")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].count", is(2)));
    }
}
