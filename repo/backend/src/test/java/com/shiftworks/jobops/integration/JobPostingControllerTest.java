package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.JobPostingController;
import com.shiftworks.jobops.dto.JobPostingHistoryResponse;
import com.shiftworks.jobops.dto.JobPostingPreviewResponse;
import com.shiftworks.jobops.dto.JobPostingResponse;
import com.shiftworks.jobops.dto.JobPostingSummaryResponse;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.JobPostingService;
import com.shiftworks.jobops.service.SessionService;
import com.shiftworks.jobops.service.StepUpVerificationService;
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
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobPostingController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class JobPostingControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private JobPostingService jobPostingService;
    @MockBean private JobPostingRepository jobPostingRepository;
    @MockBean private StepUpVerificationService stepUpVerificationService;
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
    void listJobsAsEmployer_returns200WithPageData() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(jobPostingService.listJobs(any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(new PageResponse<>(List.of(), 0L, 0, 10, 0));

        mockMvc.perform(get("/api/jobs")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    void getJobSummaryAsEmployer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(jobPostingService.summary(any()))
            .thenReturn(new JobPostingSummaryResponse(5L, 2L, 1L, 0L, List.of()));

        mockMvc.perform(get("/api/jobs/summary")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").exists());
    }

    @Test
    void createJobAsEmployer_returns200WithJobResponse() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        JobPostingResponse response = new JobPostingResponse(
            1L, "Test Job", null, JobStatus.DRAFT, null, null, null, null, null,
            null, null, null, null, null, null, null,
            List.of(), null, null, null, Instant.now(), Instant.now(), null, null, null, null
        );
        when(jobPostingService.createJob(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test Job\",\"description\":\"desc\",\"categoryId\":1,\"locationId\":1," +
                         "\"payType\":\"HOURLY\",\"settlementType\":\"PER_HOUR\",\"payAmount\":15.00," +
                         "\"headcount\":2,\"weeklyHours\":20,\"contactPhone\":\"555-1234\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title", is("Test Job")));
    }

    @Test
    void submitJobAsEmployer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(jobPostingService).submitForReview(anyLong(), any());

        mockMvc.perform(post("/api/jobs/1/submit")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void getJobDetailAsEmployer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        JobPostingResponse response = new JobPostingResponse(
            42L, "Detail Job", null, JobStatus.DRAFT, null, null, null, null, null,
            null, null, null, null, null, null, null,
            List.of(), null, null, null, Instant.now(), Instant.now(), null, null, null, null
        );
        when(jobPostingService.getJob(anyLong(), any())).thenReturn(response);

        mockMvc.perform(get("/api/jobs/42")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(42)));
    }

    @Test
    void updateJobAsEmployer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        JobPostingResponse response = new JobPostingResponse(
            1L, "Updated Job", null, JobStatus.DRAFT, null, null, null, null, null,
            null, null, null, null, null, null, null,
            List.of(), null, null, null, Instant.now(), Instant.now(), null, null, null, null
        );
        when(jobPostingService.updateJob(anyLong(), any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/jobs/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated Job\",\"description\":\"desc\",\"categoryId\":1,\"locationId\":1," +
                         "\"payType\":\"HOURLY\",\"settlementType\":\"PER_HOUR\",\"payAmount\":20.00," +
                         "\"headcount\":3,\"weeklyHours\":25,\"contactPhone\":\"555-9999\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title", is("Updated Job")));
    }

    @Test
    void publishJobAsEmployer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(jobPostingService).publish(anyLong(), any(), any());

        mockMvc.perform(post("/api/jobs/1/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stepUpPassword\":\"StrongPass!1\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void unpublishJobAsEmployer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(jobPostingService).unpublish(anyLong(), any());

        mockMvc.perform(post("/api/jobs/1/unpublish")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void previewJobAsEmployer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(jobPostingService.preview(anyLong(), any()))
            .thenReturn(new JobPostingPreviewResponse("Preview Job", "desc", "Tech", "Austin, TX",
                "$20/hr", "Per Hour", 2, "20h/wk", null, null, "555-***-1234", List.of()));

        mockMvc.perform(get("/api/jobs/1/preview")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title", is("Preview Job")));
    }

    @Test
    void historyAsEmployer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(jobPostingService.history(anyLong(), any()))
            .thenReturn(List.of(new JobPostingHistoryResponse("DRAFT", "PENDING_REVIEW",
                "testuser", null, Instant.now())));

        mockMvc.perform(get("/api/jobs/1/history")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].newStatus", is("PENDING_REVIEW")));
    }

    @Test
    void contactPhoneAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(stepUpVerificationService.verify(anyLong(), anyString())).thenReturn(true);
        JobPosting job = new JobPosting();
        job.setContactPhone("555-1234");
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));

        mockMvc.perform(post("/api/jobs/1/contact-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stepUpPassword\":\"StrongPass!1\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contactPhone", is("555-1234")));
    }
}
