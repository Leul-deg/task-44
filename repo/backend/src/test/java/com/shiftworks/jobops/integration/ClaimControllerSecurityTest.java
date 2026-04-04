package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.ClaimController;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.ClaimService;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClaimController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class ClaimControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ClaimService claimService;
    @MockBean private SessionService sessionService;

    private UserSession buildSession(long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordChangedAt(Instant.now());
        UserSession session = new UserSession();
        session.setId("tok-" + id);
        session.setUser(user);
        session.setValid(true);
        session.setLastActiveAt(Instant.now());
        session.setAbsoluteExpiry(Instant.now().plusSeconds(43200));
        session.setCsrfToken("csrf-" + id);
        return session;
    }

    @Test
    void claimsDeniedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/claims"))
            .andExpect(status().isForbidden());
    }

    @Test
    void claimsDeniedForReviewer() throws Exception {
        UserSession session = buildSession(20L, UserRole.REVIEWER);
        when(sessionService.findValidSession("tok-20")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/claims")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "tok-20"))
                .header("X-XSRF-TOKEN", "csrf-20"))
            .andExpect(status().isForbidden());
    }

    @Test
    void claimsAllowedForEmployer() throws Exception {
        UserSession session = buildSession(10L, UserRole.EMPLOYER);
        when(sessionService.findValidSession("tok-10")).thenReturn(Optional.of(session));
        when(claimService.listClaims(any(), eq(null), eq(0), eq(10)))
            .thenReturn(new PageResponse<>(List.of(), 0, 0, 10, 0));

        mockMvc.perform(get("/api/claims")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "tok-10"))
                .header("X-XSRF-TOKEN", "csrf-10"))
            .andExpect(status().isOk());
    }
}
