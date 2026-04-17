package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.AdminUserController;
import com.shiftworks.jobops.dto.AdminUserResponse;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.AdminUserService;
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
import com.shiftworks.jobops.dto.ResetPasswordResponse;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class AdminUserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AdminUserService adminUserService;
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

    private AdminUserResponse buildAdminUserResponse() {
        return new AdminUserResponse(10L, "employer1", "emp@test.com", UserRole.EMPLOYER,
            UserStatus.ACTIVE, Instant.now(), Instant.now(), 0, null);
    }

    @Test
    void listUsersAsAdmin_returns200WithPageData() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(adminUserService.listUsers(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new PageResponse<>(List.of(), 0L, 0, 10, 0));

        mockMvc.perform(get("/api/admin/users")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    void getUserDetailAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(adminUserService.getUser(10L)).thenReturn(buildAdminUserResponse());

        mockMvc.perform(get("/api/admin/users/10")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username", is("employer1")));
    }

    @Test
    void createUserAsAdmin_returns200WithResponse() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(adminUserService.create(any(), any())).thenReturn(buildAdminUserResponse());

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"email\":\"new@test.com\",\"password\":\"StrongPass!1\",\"role\":\"EMPLOYER\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(10)));
    }

    @Test
    void unlockUserAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(adminUserService).unlock(anyLong(), any());

        mockMvc.perform(put("/api/admin/users/10/unlock")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void changeRoleAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        doNothing().when(adminUserService).changeRole(anyLong(), any(), any());

        mockMvc.perform(put("/api/admin/users/10/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"REVIEWER\",\"stepUpPassword\":\"StrongPass!1\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void resetPasswordAsAdmin_returns200WithTemporaryPassword() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(adminUserService.resetPassword(anyLong(), any()))
            .thenReturn(new ResetPasswordResponse("Temp!Pass123"));

        mockMvc.perform(put("/api/admin/users/10/reset-password")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.temporaryPassword", is("Temp!Pass123")));
    }

    @Test
    void reviewerCannotListUsers_returns403() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/admin/users")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_listUsers_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateUserAsAdmin_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(adminUserService.update(eq(10L), any(), any())).thenReturn(buildAdminUserResponse());

        mockMvc.perform(put("/api/admin/users/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"updated@test.com\",\"status\":\"ACTIVE\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(10)));
    }
}
