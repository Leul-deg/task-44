package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.AdminDictionaryController;
import com.shiftworks.jobops.dto.AdminCategoryResponse;
import com.shiftworks.jobops.dto.AdminLocationResponse;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.AdminDictionaryService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDictionaryController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class AdminDictionaryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AdminDictionaryService adminDictionaryService;
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

    private AdminCategoryResponse sampleCategory() {
        return new AdminCategoryResponse(1L, "Engineering", "Tech roles", true, 5L, Instant.now());
    }

    private AdminLocationResponse sampleLocation() {
        return new AdminLocationResponse(1L, "TX", "Austin", true, Instant.now());
    }

    @Test
    void listCategoriesAsAdmin_returns200WithList() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        when(adminDictionaryService.listCategories()).thenReturn(List.of(sampleCategory()));

        mockMvc.perform(get("/api/admin/categories")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Engineering")));
    }

    @Test
    void createCategoryAsAdmin_returns200WithCategory() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        when(adminDictionaryService.createCategory(any(), any())).thenReturn(sampleCategory());

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Engineering\",\"description\":\"Tech roles\"}")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)));
    }

    @Test
    void updateCategoryAsAdmin_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        when(adminDictionaryService.updateCategory(eq(1L), any(), any())).thenReturn(sampleCategory());

        mockMvc.perform(put("/api/admin/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Engineering\",\"description\":\"Updated\"}")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Engineering")));
    }

    @Test
    void deleteCategoryAsAdmin_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        doNothing().when(adminDictionaryService).deactivateCategory(eq(1L), any());

        mockMvc.perform(delete("/api/admin/categories/1")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk());
    }

    @Test
    void listLocationsAsAdmin_returns200WithList() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        when(adminDictionaryService.listLocations(any())).thenReturn(List.of(sampleLocation()));

        mockMvc.perform(get("/api/admin/locations")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city", is("Austin")));
    }

    @Test
    void createLocationAsAdmin_returns200WithLocation() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        when(adminDictionaryService.createLocation(any(), any())).thenReturn(sampleLocation());

        mockMvc.perform(post("/api/admin/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"TX\",\"city\":\"Austin\"}")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("TX")));
    }

    @Test
    void updateLocationAsAdmin_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        when(adminDictionaryService.updateLocation(eq(1L), any(), any())).thenReturn(sampleLocation());

        mockMvc.perform(put("/api/admin/locations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"TX\",\"city\":\"Dallas\"}")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("TX")));
    }

    @Test
    void deleteLocationAsAdmin_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        doNothing().when(adminDictionaryService).deactivateLocation(eq(1L), any());

        mockMvc.perform(delete("/api/admin/locations/1")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk());
    }

    @Test
    void adminDictionaryDeniedForEmployer_returns403() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.EMPLOYER)));

        mockMvc.perform(get("/api/admin/categories")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDictionaryUnauthenticated_returns403() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/categories"))
                .andExpect(status().isForbidden());
    }
}
