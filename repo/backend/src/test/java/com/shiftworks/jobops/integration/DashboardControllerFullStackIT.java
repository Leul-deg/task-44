package com.shiftworks.jobops.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.repository.UserSessionRepository;
import com.shiftworks.jobops.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack (no-mock) IT for {@code DashboardController}.
 *
 * Exercises the full filter chain and the real {@code DashboardService} — including
 * the short-circuiting guards for step-up verification on unmasked export, CSRF on
 * mutating requests, and role scoping. There are no {@code @MockBean}s.
 *
 * <h3>Scope boundary</h3>
 * The preview / data / masked-export paths ultimately issue a native SQL statement
 * built around {@code MySQL} ({@code AS value}, {@code TIMESTAMPDIFF}, …) which is
 * not parseable by H2 even in {@code MODE=MYSQL}. Those three paths are covered
 * by {@code DashboardServiceTest} (unit, with a real {@code EntityManager} mock) and
 * {@code DashboardControllerTest} (WebMvc slice) — both of which run under the same
 * {@code mvn test} invocation. This file asserts every part of the HTTP surface that
 * does NOT depend on dialect-specific SQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardControllerFullStackIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSessionRepository userSessionRepository;

    private SessionHandle admin;
    private SessionHandle employer;

    @BeforeEach
    void setup() {
        admin = createUserWithSession("dash_admin_" + System.nanoTime(), UserRole.ADMIN);
        employer = createUserWithSession("dash_emp_" + System.nanoTime(), UserRole.EMPLOYER);
    }

    @Test
    void crudRoundTrip_createListGetUpdateDelete() throws Exception {
        Long id = createDashboardAndReturnId("smoke_dash");

        mockMvc.perform(get("/api/dashboards")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/dashboards/" + id)
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id));

        String updated = objectMapper.writeValueAsString(dashboardBody("updated_smoke"));
        mockMvc.perform(put("/api/dashboards/" + id)
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId))
                .header("X-XSRF-TOKEN", admin.csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updated))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("updated_smoke"));

        mockMvc.perform(delete("/api/dashboards/" + id)
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId))
                .header("X-XSRF-TOKEN", admin.csrfToken))
            .andExpect(status().isOk());

        // After delete the dashboard must no longer be fetchable — service maps this to NOT_FOUND.
        mockMvc.perform(get("/api/dashboards/" + id)
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(result -> {
                int st = result.getResponse().getStatus();
                if (st != 404 && st != 403) {
                    throw new AssertionError("Deleted dashboard should be 404/403, got " + st);
                }
            });
    }

    @Test
    void unmaskedExport_withWrongStepUpPassword_isDenied() throws Exception {
        // Step-up verification runs BEFORE the native SQL query (DashboardService#export),
        // so this path is H2-safe: the service short-circuits on a bad password.
        Long id = createDashboardAndReturnId("export_stepup");

        mockMvc.perform(post("/api/dashboards/" + id + "/export?masked=false")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId))
                .header("X-XSRF-TOKEN", admin.csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stepUpPassword\":\"WRONG_PASSWORD!\"}"))
            .andExpect(result -> {
                int st = result.getResponse().getStatus();
                if (st != 400 && st != 401 && st != 403) {
                    throw new AssertionError("Bad step-up should deny, got " + st);
                }
            });
    }

    @Test
    void mutatingRequest_withoutCsrfHeader_isRejected() throws Exception {
        String body = objectMapper.writeValueAsString(dashboardBody("no_csrf"));
        mockMvc.perform(post("/api/dashboards")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void employerRole_isDeniedOnDashboardList() throws Exception {
        mockMvc.perform(get("/api/dashboards")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, employer.sessionId)))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequest_isDeniedOnDashboardList() throws Exception {
        mockMvc.perform(get("/api/dashboards"))
            .andExpect(status().isForbidden());
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private Long createDashboardAndReturnId(String name) throws Exception {
        String body = objectMapper.writeValueAsString(dashboardBody(name + "_" + System.nanoTime()));
        MvcResult result = mockMvc.perform(post("/api/dashboards")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId))
                .header("X-XSRF-TOKEN", admin.csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private Map<String, Object> dashboardBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("metricsJson", List.of("post_volume"));
        body.put("dimensionsJson", "date_daily");
        body.put("filtersJson", Map.of());
        return body;
    }

    private SessionHandle createUserWithSession(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.local");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordChangedAt(Instant.now());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user = userRepository.save(user);

        UserSession session = new UserSession();
        session.setId("dash-it-" + username);
        session.setUser(user);
        session.setCreatedAt(Instant.now());
        session.setLastActiveAt(Instant.now());
        session.setAbsoluteExpiry(Instant.now().plusSeconds(3600));
        session.setValid(true);
        session.setCsrfToken("csrf-" + username);
        userSessionRepository.save(session);

        return new SessionHandle(user.getId(), session.getId(), session.getCsrfToken());
    }

    private record SessionHandle(Long userId, String sessionId, String csrfToken) {}
}
