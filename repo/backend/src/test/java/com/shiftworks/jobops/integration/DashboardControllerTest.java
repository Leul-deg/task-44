package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.DashboardController;
import com.shiftworks.jobops.dto.DashboardConfigResponse;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.DashboardService;
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

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DashboardService service;
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
    void listDashboardsAsAdmin_returns200WithList() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.list(any()))
            .thenReturn(List.of(new DashboardConfigResponse(1L, "My Dashboard", null, null, null)));

        mockMvc.perform(get("/api/dashboards")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name", is("My Dashboard")));
    }

    @Test
    void createDashboardAsReviewer_returns200WithResponse() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.create(any(), any()))
            .thenReturn(new DashboardConfigResponse(10L, "Reviewer Board", null, null, null));

        mockMvc.perform(post("/api/dashboards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Reviewer Board\",\"metricsJson\":null,\"dimensionsJson\":null,\"filtersJson\":null}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(10)));
    }

    @Test
    void getDashboardAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.get(anyLong(), any()))
            .thenReturn(new DashboardConfigResponse(3L, "Detail Board", null, null, null));

        mockMvc.perform(get("/api/dashboards/3")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").exists());
    }

    @Test
    void deleteDashboardAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(service).delete(anyLong(), any());

        mockMvc.perform(delete("/api/dashboards/5")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void employerCannotListDashboards_returns403() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/dashboards")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_dashboards_returns403() throws Exception {
        mockMvc.perform(get("/api/dashboards"))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateDashboardAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.update(anyLong(), any(), any()))
            .thenReturn(new DashboardConfigResponse(3L, "Updated Board", null, null, null));

        mockMvc.perform(put("/api/dashboards/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Board\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("Updated Board")));
    }

    @Test
    void getDashboardDataAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.data(anyLong(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/dashboards/3/data")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void previewDashboardAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.preview(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/dashboards/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Preview\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void exportDashboardAsAdmin_returns200WithCsv() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(service.export(anyLong(), anyBoolean(), any(), any())).thenReturn("col,val\n1,2".getBytes());

        mockMvc.perform(post("/api/dashboards/3/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("dashboard.csv")));
    }
}
