package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.ReviewController;
import com.shiftworks.jobops.dto.FieldDiff;
import com.shiftworks.jobops.dto.JobPostingResponse;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.dto.ReviewActionResponse;
import com.shiftworks.jobops.dto.ReviewDashboardResponse;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.ReviewActionType;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.ReviewService;
import com.shiftworks.jobops.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class ReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ReviewService reviewService;
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
    void getDashboardAsReviewer_returns200WithMetrics() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(reviewService.dashboard(any()))
            .thenReturn(new ReviewDashboardResponse(5L, 2L, 3L, List.of()));

        mockMvc.perform(get("/api/review/dashboard")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingReviews", is(5)));
    }

    @Test
    void getQueueAsReviewer_returns200WithItems() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(reviewService.getQueue(any(), anyInt(), anyInt()))
            .thenReturn(new PageResponse<>(List.of(), 0L, 0, 10, 0));

        mockMvc.perform(get("/api/review/queue")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    void approveJobAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(reviewService).approve(anyLong(), any(), any());

        mockMvc.perform(post("/api/review/jobs/1/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rationale\":\"Looks compliant with policy\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void rejectJobAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(reviewService).reject(anyLong(), any(), any());

        mockMvc.perform(post("/api/review/jobs/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rationale\":\"Does not meet requirements\",\"reviewerNotes\":\"Missing contact info\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void queueDeniedForEmployer_returns403() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/review/queue")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isForbidden());
    }

    @Test
    void getJobDetailAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        JobPostingResponse response = new JobPostingResponse(
            1L, "Review Job", null, JobStatus.PENDING_REVIEW, null, null, null, null, null,
            null, null, null, null, null, null, null,
            List.of(), null, null, null, Instant.now(), Instant.now(), null, null, null, null
        );
        when(reviewService.getJob(eq(1L), any())).thenReturn(response);

        mockMvc.perform(get("/api/review/jobs/1")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title", is("Review Job")));
    }

    @Test
    void getDiffAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(reviewService.diff(eq(1L), any())).thenReturn(Map.of("title", new FieldDiff("Old Title", "New Title")));

        mockMvc.perform(get("/api/review/jobs/1/diff")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title.newValue", is("New Title")));
    }

    @Test
    void getActionsAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(reviewService.actionsForJob(eq(1L), any()))
            .thenReturn(List.of(new ReviewActionResponse(1L, 1L, "Test Job", ReviewActionType.APPROVE,
                "Looks good", Instant.now())));

        mockMvc.perform(get("/api/review/jobs/1/actions")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].action", is("APPROVE")));
    }

    @Test
    void takedownAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(reviewService).takedown(anyLong(), any(), any());

        mockMvc.perform(post("/api/review/jobs/1/takedown")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rationale\":\"Policy violation, content is illegal\",\"stepUpPassword\":\"StrongPass!1\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }
}
