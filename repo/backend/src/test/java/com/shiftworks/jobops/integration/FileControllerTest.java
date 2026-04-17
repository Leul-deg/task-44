package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.FileController;
import com.shiftworks.jobops.dto.FileAttachmentResponse;
import com.shiftworks.jobops.entity.FileAttachment;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.AttachmentEntityType;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.FileService;
import com.shiftworks.jobops.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class FileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private FileService fileService;
    @MockBean private SessionService sessionService;

    private UserSession buildSession(UserRole role) {
        User user = new User();
        user.setId(1L);
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

    private FileAttachmentResponse sampleResponse() {
        return new FileAttachmentResponse(5L, "TICKET", 10L, "test.pdf", "pdf",
                1024L, "abc123", "ACTIVE", "testuser", Instant.now());
    }

    private FileAttachment sampleAttachment() {
        FileAttachment fa = new FileAttachment();
        fa.setId(5L);
        fa.setOriginalFilename("test.pdf");
        fa.setFileType("pdf");
        fa.setEntityType(AttachmentEntityType.TICKET);
        fa.setEntityId(10L);
        return fa;
    }

    @Test
    void uploadFileAsEmployer_returns200WithResponse() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.EMPLOYER)));
        when(fileService.upload(any(), any(), any(), any())).thenReturn(sampleResponse());

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf",
                "application/pdf", "content".getBytes());
        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("entityType", "TICKET")
                        .param("entityId", "10")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(5)));
    }

    @Test
    void downloadFileAsEmployer_returns200WithBytes() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.EMPLOYER)));
        when(fileService.getAttachment(5L)).thenReturn(sampleAttachment());
        when(fileService.download(anyLong(), anyBoolean(), any())).thenReturn("data".getBytes());

        mockMvc.perform(get("/api/files/5/download")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("test.pdf")));
    }

    @Test
    void listByEntityAsEmployer_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.EMPLOYER)));
        when(fileService.listByEntity(any(), anyLong(), any())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/files/entity/TICKET/10")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(5)));
    }

    @Test
    void listQuarantinedAsAdmin_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        when(fileService.listQuarantined()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/files/quarantined")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status", is("ACTIVE")));
    }

    @Test
    void quarantinedDeniedForEmployer_returns403() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.EMPLOYER)));

        mockMvc.perform(get("/api/files/quarantined")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void releaseFileAsAdmin_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        doNothing().when(fileService).release(anyLong(), any());

        mockMvc.perform(put("/api/files/5/release")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteFileAsAdmin_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        doNothing().when(fileService).delete(anyLong(), any());

        mockMvc.perform(delete("/api/files/5")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk());
    }
}
