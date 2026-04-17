package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.DictionaryController;
import com.shiftworks.jobops.entity.Category;
import com.shiftworks.jobops.entity.Location;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.CategoryRepository;
import com.shiftworks.jobops.repository.LocationRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DictionaryController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class DictionaryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private CategoryRepository categoryRepository;
    @MockBean private LocationRepository locationRepository;
    @MockBean private SessionService sessionService;

    private UserSession buildSession() {
        User user = new User();
        user.setId(1L);
        user.setUsername("employer");
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

    private Category buildCategory(long id, String name) {
        Category cat = new Category();
        cat.setId(id);
        cat.setName(name);
        cat.setDescription("desc");
        cat.setActive(true);
        cat.setCreatedAt(Instant.now());
        return cat;
    }

    private Location buildLocation(long id, String state, String city) {
        Location loc = new Location();
        loc.setId(id);
        loc.setState(state);
        loc.setCity(city);
        loc.setActive(true);
        loc.setCreatedAt(Instant.now());
        return loc;
    }

    @Test
    void getCategories_returnsListForAuthenticatedUser() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession()));
        when(categoryRepository.findByActiveTrueOrderByNameAsc())
                .thenReturn(List.of(buildCategory(1L, "Engineering"), buildCategory(2L, "Retail")));

        mockMvc.perform(get("/api/categories")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Engineering")));
    }

    @Test
    void getLocations_returnsAllWhenNoStateFilter() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession()));
        when(locationRepository.findByActiveTrueOrderByStateAscCityAsc())
                .thenReturn(List.of(buildLocation(1L, "TX", "Austin")));

        mockMvc.perform(get("/api/locations")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city", is("Austin")));
    }

    @Test
    void getLocations_filtersByState() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession()));
        when(locationRepository.findByActiveTrueAndStateOrderByCityAsc("TX"))
                .thenReturn(List.of(buildLocation(1L, "TX", "Austin"), buildLocation(2L, "TX", "Houston")));

        mockMvc.perform(get("/api/locations").param("state", "TX")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].city", is("Houston")));
    }

    @Test
    void getStates_returnsDistinctStateList() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession()));
        when(locationRepository.findActiveStates()).thenReturn(List.of("CA", "TX", "WA"));

        mockMvc.perform(get("/api/locations/states")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0]", is("CA")));
    }

    @Test
    void categoriesUnauthenticated_returns403() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isForbidden());
    }
}
