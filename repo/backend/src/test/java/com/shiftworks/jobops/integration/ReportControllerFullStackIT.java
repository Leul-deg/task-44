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
 * Full-stack (no-mock) IT for {@code ReportController}.
 *
 * Creates a real dashboard (via the running HTTP stack) to anchor a scheduled report,
 * then exercises scheduled-report CRUD + exports list against the real
 * {@code ScheduledReportService} and JPA — no {@code @MockBean}.
 *
 * Mirrors the {@code test_reports.sh} script that previously covered this surface.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportControllerFullStackIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSessionRepository userSessionRepository;

    private SessionHandle admin;

    @BeforeEach
    void setup() {
        admin = createUserWithSession("report_admin_" + System.nanoTime(), UserRole.ADMIN);
    }

    @Test
    void scheduledReport_crudRoundTrip() throws Exception {
        Long dashboardId = createDashboardAndReturnId("report_src");

        String createBody = String.format(
            "{\"dashboardConfigId\":%d,\"cronExpression\":\"0 0 3 * * *\"}", dashboardId);
        MvcResult createResult = mockMvc.perform(post("/api/reports/scheduled")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId))
                .header("X-XSRF-TOKEN", admin.csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();
        Long reportId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("id").asLong();

        mockMvc.perform(get("/api/reports/scheduled")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").exists());

        mockMvc.perform(put("/api/reports/scheduled/" + reportId)
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId))
                .header("X-XSRF-TOKEN", admin.csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cronExpression\":\"0 0 4 * * *\",\"isActive\":false}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/reports/exports")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        mockMvc.perform(delete("/api/reports/scheduled/" + reportId)
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId))
                .header("X-XSRF-TOKEN", admin.csrfToken))
            .andExpect(status().isOk());
    }

    @Test
    void anonymousRequest_isDenied() throws Exception {
        mockMvc.perform(get("/api/reports/scheduled"))
            .andExpect(status().isForbidden());
    }

    @Test
    void mutatingRequest_withoutCsrfHeader_isRejected() throws Exception {
        mockMvc.perform(post("/api/reports/scheduled")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboardConfigId\":1,\"cronExpression\":\"0 0 3 * * *\"}"))
            .andExpect(status().isForbidden());
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private Long createDashboardAndReturnId(String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name + "_" + System.nanoTime());
        body.put("metricsJson", List.of("post_volume"));
        body.put("dimensionsJson", "date_daily");
        body.put("filtersJson", Map.of());

        MvcResult result = mockMvc.perform(post("/api/dashboards")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, admin.sessionId))
                .header("X-XSRF-TOKEN", admin.csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
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
        session.setId("report-it-" + username);
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
