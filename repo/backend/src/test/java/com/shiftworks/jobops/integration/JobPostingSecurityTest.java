package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.controller.JobPostingController;
import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.JobPostingService;
import com.shiftworks.jobops.service.StepUpVerificationService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobPostingController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class JobPostingSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private JobPostingService jobPostingService;
    @MockBean private JobPostingRepository jobPostingRepository;
    @MockBean private StepUpVerificationService stepUpVerificationService;
    @MockBean private SessionService sessionService;

    private UserSession buildSession(UserRole role) {
        User user = new User();
        user.setId(88L);
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
    void listJobsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/jobs"))
            .andExpect(status().isForbidden());
    }

    @Test
    void createJobByReviewerIsRejected() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));

        mockMvc.perform(post("/api/jobs")
                .contentType("application/json")
                .content("{}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                assertTrue(status == 400 || status == 403,
                    "Reviewer creating job should be rejected, got " + status);
            });
    }
}
