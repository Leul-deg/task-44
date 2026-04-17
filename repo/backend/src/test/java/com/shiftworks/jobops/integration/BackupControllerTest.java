package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.BackupController;
import com.shiftworks.jobops.entity.BackupRecord;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.BackupStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.AuditService;
import com.shiftworks.jobops.service.BackupService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BackupController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class BackupControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private BackupService backupService;
    @MockBean private StepUpVerificationService stepUpVerificationService;
    @MockBean private AuditService auditService;
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

    private BackupRecord buildBackupRecord() {
        BackupRecord record = new BackupRecord();
        record.setId(7L);
        record.setFilename("backup-20260417-020000.sql.gz.enc");
        record.setFilePath("/app/backups/backup-20260417-020000.sql.gz.enc");
        record.setFileSize(1024L);
        record.setChecksum("abc123");
        record.setEncrypted(true);
        record.setCreatedAt(Instant.now());
        record.setExpiresAt(Instant.now().plusSeconds(86400));
        record.setStatus(BackupStatus.COMPLETED);
        return record;
    }

    @Test
    void listBackupsAsAdmin_returns200WithList() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        BackupRecord record = buildBackupRecord();
        when(backupService.listBackups()).thenReturn(List.of(record));

        mockMvc.perform(get("/api/admin/backup/list")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id", is(7)));
    }

    @Test
    void triggerBackupWithValidStepUp_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        BackupRecord record = buildBackupRecord();
        when(stepUpVerificationService.verify(eq(99L), eq("ValidPass!1"))).thenReturn(true);
        when(backupService.performBackup()).thenReturn(record);
        doNothing().when(auditService).log(anyLong(), anyString(), anyString(), anyLong(), any(), any());

        mockMvc.perform(post("/api/admin/backup/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stepUpPassword\":\"ValidPass!1\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filename").exists());
    }

    @Test
    void triggerBackupWithBadStepUp_returns403() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(stepUpVerificationService.verify(eq(99L), eq("bad"))).thenReturn(false);

        mockMvc.perform(post("/api/admin/backup/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stepUpPassword\":\"bad\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isForbidden());
    }

    @Test
    void restoreWithValidStepUpAndConfirm_returns200() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(stepUpVerificationService.verify(eq(99L), eq("ValidPass!1"))).thenReturn(true);
        doNothing().when(backupService).restore(anyLong());
        doNothing().when(auditService).log(anyLong(), anyString(), anyString(), anyLong(), any(), any());

        mockMvc.perform(post("/api/admin/backup/restore/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stepUpPassword\":\"ValidPass!1\",\"confirm\":\"RESTORE\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void restoreWithoutConfirm_returns400() throws Exception {
        UserSession session = buildSession(UserRole.ADMIN);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));
        when(stepUpVerificationService.verify(eq(99L), eq("ValidPass!1"))).thenReturn(true);

        mockMvc.perform(post("/api/admin/backup/restore/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stepUpPassword\":\"ValidPass!1\",\"confirm\":\"nope\"}")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void reviewerCannotAccessBackup_returns403() throws Exception {
        UserSession session = buildSession(UserRole.REVIEWER);
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/admin/backup/list")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                .header("X-XSRF-TOKEN", "test-csrf"))
            .andExpect(status().isForbidden());
    }
}
