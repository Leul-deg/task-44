package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.ReportController;
import com.shiftworks.jobops.dto.ReportExportResponse;
import com.shiftworks.jobops.dto.ScheduledReportResponse;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.ScheduledReportService;
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
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class ReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ScheduledReportService service;
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
    void listScheduledReportsAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.list(any()))
            .thenReturn(List.of(new ScheduledReportResponse(1L, 5L, "Board", "0 0 * * *", true, null, Instant.now(), 99L)));

        mockMvc.perform(get("/api/reports/scheduled")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].cronExpression", is("0 0 * * *")));
    }

    @Test
    void listScheduledReportsAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.list(any()))
            .thenReturn(List.of(new ScheduledReportResponse(1L, 5L, "Board", "0 0 * * *", true, null, Instant.now(), 99L)));

        mockMvc.perform(get("/api/reports/scheduled")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].cronExpression", is("0 0 * * *")));
    }

    @Test
    void createScheduledReportAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.create(any(), any()))
            .thenReturn(new ScheduledReportResponse(2L, 5L, "Board", "0 0 8 * *", true, null, null, 99L));

        mockMvc.perform(post("/api/reports/scheduled")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboardConfigId\":5,\"cronExpression\":\"0 0 8 * *\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(2)));
    }

    @Test
    void listExportsAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.listExports(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/reports/exports")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void employerCannotAccessReports_returns403() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/reports/scheduled")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateScheduledReportAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.update(anyLong(), any(), any()))
            .thenReturn(new ScheduledReportResponse(1L, 5L, "Board", "0 0 8 * *", false, null, null, 99L));

        mockMvc.perform(put("/api/reports/scheduled/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cronExpression\":\"0 0 8 * *\",\"isActive\":false}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(1)));
    }

    @Test
    void deleteScheduledReportAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(service).delete(anyLong(), any());

        mockMvc.perform(delete("/api/reports/scheduled/1")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void downloadExportAsAdmin_returns200WithCsv() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.downloadExport(anyLong(), any())).thenReturn("col,val\n1,2".getBytes());

        mockMvc.perform(get("/api/reports/exports/1/download")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("report.csv")));
    }
}
