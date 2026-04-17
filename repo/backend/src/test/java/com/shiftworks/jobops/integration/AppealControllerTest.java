package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.AppealController;
import com.shiftworks.jobops.dto.AppealDetailResponse;
import com.shiftworks.jobops.dto.AppealResponse;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.AppealStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.AppealService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppealController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class AppealControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AppealService appealService;
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
    void listAppealsAsEmployer_returns200WithPageData() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(appealService.listAppeals(any(), any(), anyInt(), anyInt()))
            .thenReturn(new PageResponse<>(List.of(), 0L, 0, 10, 0));

        mockMvc.perform(get("/api/appeals")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    void getAppealDetailAsEmployer_returns200WithFields() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(appealService.getAppeal(anyLong(), any()))
            .thenReturn(new AppealDetailResponse(5L, AppealStatus.PENDING, "Reason", Instant.now(),
                "employer1", "Test Job", null, null, null, null, null, null, null, null));

        mockMvc.perform(get("/api/appeals/5")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(5)));
    }

    @Test
    void createAppealAsEmployer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(appealService).createAppeal(any(), any());

        mockMvc.perform(post("/api/appeals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobPostingId\":10,\"appealReason\":\"The job was unfairly taken down without justification\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void processAppealAsReviewer_returns200() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(appealService).processAppeal(anyLong(), any(), any());

        mockMvc.perform(post("/api/appeals/5/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"GRANTED\",\"reviewerRationale\":\"Appeal is well-founded and meets criteria\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void employerCannotProcessAppeal_returns403() throws Exception {
        UserSession session = buildSession(UserRole.EMPLOYER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));

        mockMvc.perform(post("/api/appeals/5/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"GRANTED\",\"reviewerRationale\":\"Appeal is well-founded and meets criteria\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_listAppeals_returns403() throws Exception {
        mockMvc.perform(get("/api/appeals"))
            .andExpect(status().isForbidden());
    }
}
