package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.AuditController;
import com.shiftworks.jobops.entity.AuditLog;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.AuditLogRepository;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class AuditControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AuditLogRepository auditLogRepository;
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

    private AuditLog buildLog() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setAction("USER_LOGIN");
        log.setEntityType("USER");
        log.setEntityId(10L);
        log.setIpAddress("127.0.0.1");
        log.setCreatedAt(Instant.now());
        return log;
    }

    @Test
    void listAuditLogsAsAdmin_returns200WithPage() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildLog())));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void getAuditLogDetailAsAdmin_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        when(auditLogRepository.findById(1L)).thenReturn(Optional.of(buildLog()));

        mockMvc.perform(get("/api/admin/audit-logs/1")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action", is("USER_LOGIN")));
    }

    @Test
    void auditLogsDeniedForReviewer_returns403() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.REVIEWER)));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLogsUnauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isForbidden());
    }
}
