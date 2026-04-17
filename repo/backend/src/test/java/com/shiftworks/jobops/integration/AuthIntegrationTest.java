package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.controller.AuthController;
import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import jakarta.servlet.http.Cookie;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.AuthService;
import com.shiftworks.jobops.service.CaptchaService;
import com.shiftworks.jobops.service.SessionService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AuthService authService;
    @MockBean private CaptchaService captchaService;
    @MockBean private SessionService sessionService;

    private UserSession buildSession() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole(UserRole.EMPLOYER);
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
    void loginEndpointIsAccessible() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test\",\"password\":\"test\"}"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                assertTrue(status != 401 && status != 403,
                    "Login endpoint should be public, got " + status);
            });
    }

    @Test
    void registerEndpointRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"new\",\"email\":\"a@b.com\",\"password\":\"StrongPass123!\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void meEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isForbidden());
    }

    @Test
    void responsesIncludeSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/auth/captcha"))
            .andExpect(header().exists("Content-Security-Policy"))
            .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")))
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void logoutWithValidSession_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession()));
        doNothing().when(authService).logout(anyLong());

        mockMvc.perform(post("/api/auth/logout")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void changePasswordWithValidSession_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession()));
        doNothing().when(authService).changePassword(anyLong(), any());

        mockMvc.perform(post("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"OldPass!1\",\"newPassword\":\"NewPass!2\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk());
    }
}
