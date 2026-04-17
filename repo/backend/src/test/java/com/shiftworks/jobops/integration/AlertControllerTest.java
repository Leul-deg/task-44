package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.AlertController;
import com.shiftworks.jobops.dto.AlertPageResponse;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.AlertSeverity;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.AlertService;
import com.shiftworks.jobops.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class AlertControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AlertService alertService;
    @MockBean private SessionService sessionService;

    private UserSession buildSession(UserRole role) {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
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

    private AlertPageResponse sampleAlert() {
        return new AlertPageResponse(5L, "ANOMALY", "postVolume", "Spike detected",
                AlertSeverity.CRITICAL, false, null, Instant.now());
    }

    @Test
    void listAlertsAsAdmin_returns200WithPage() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        when(alertService.list(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(sampleAlert()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/alerts")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].metricName", is("postVolume")));
    }

    @Test
    void unreadCountAsAdmin_returns200WithCount() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        when(alertService.unreadCount()).thenReturn(7L);

        mockMvc.perform(get("/api/alerts/unread-count")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(7)));
    }

    @Test
    void markReadAsAdmin_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        doNothing().when(alertService).markRead(eq(5L), any());

        mockMvc.perform(put("/api/alerts/5/read")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk());
    }

    @Test
    void acknowledgeAlertAsAdmin_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        doNothing().when(alertService).acknowledge(eq(5L), any());

        mockMvc.perform(put("/api/alerts/5/acknowledge")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk());
    }

    @Test
    void alertsDeniedForEmployer_returns403() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.EMPLOYER)));

        mockMvc.perform(get("/api/alerts")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void alertsUnauthenticated_returns403() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isForbidden());
    }
}
