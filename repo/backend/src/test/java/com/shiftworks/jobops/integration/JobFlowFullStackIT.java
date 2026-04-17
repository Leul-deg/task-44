package com.shiftworks.jobops.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiftworks.jobops.entity.Category;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.Location;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.CategoryRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.LocationRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP integration test: MockMvc + real Spring Security filters + H2-backed JPA + encryption.
 *
 * Addresses Report-02 coverage gaps previously flagged as "Partial Pass":
 *   - Full HTTP MockMvc path for job validation + persistence + encryption (§8.2 row 1).
 *   - Full-stack CSRF enforcement on mutating POST (§8.2 row 3).
 *   - Cross-employer tenant isolation via HTTP (§8.2 row 5 / §6 object-level).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JobFlowFullStackIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserRepository userRepository;
    @Autowired private UserSessionRepository userSessionRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JobPostingRepository jobPostingRepository;

    private Long categoryId;
    private Long locationId;

    private SessionHandle employerA;
    private SessionHandle employerB;

    @BeforeEach
    void setup() {
        Category cat = new Category();
        cat.setName("FullStackCat_" + System.nanoTime());
        cat.setActive(true);
        categoryId = categoryRepository.save(cat).getId();

        Location loc = new Location();
        loc.setState("CA");
        loc.setCity("FullStackCity_" + System.nanoTime());
        loc.setActive(true);
        locationId = locationRepository.save(loc).getId();

        employerA = createEmployerWithSession("full_emp_a_" + System.nanoTime());
        employerB = createEmployerWithSession("full_emp_b_" + System.nanoTime());
    }

    @Test
    void createJob_overHttp_persistsDraftAndEncryptsPhone() throws Exception {
        String body = objectMapper.writeValueAsString(jobRequestBody("Full Stack Draft", "2025551111"));

        Long jobId = objectMapper.readTree(
                mockMvc.perform(post("/api/jobs")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, employerA.sessionId))
                        .header("X-XSRF-TOKEN", employerA.csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.status").value(JobStatus.DRAFT.name()))
                    .andReturn().getResponse().getContentAsString())
            .get("id").asLong();

        JobPosting persisted = jobPostingRepository.findById(jobId).orElseThrow();
        assertEquals(JobStatus.DRAFT, persisted.getStatus());
        assertEquals(employerA.userId, persisted.getEmployer().getId());

        // Phone is stored encrypted at the database column level: the raw column value must not equal
        // the plaintext we posted (entity getter transparently decrypts via @Convert).
        String rawEncrypted = jobPostingRepository.findRawContactPhoneById(jobId);
        assertNotNull(rawEncrypted);
        assertNotEquals("2025551111", rawEncrypted,
            "Expected contact_phone to be encrypted at rest, but raw column matched plaintext");
        assertEquals("2025551111", persisted.getContactPhone());
    }

    @Test
    void createJob_withoutCsrfHeader_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(jobRequestBody("CSRF Rejected", "2025551222"));

        mockMvc.perform(post("/api/jobs")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, employerA.sessionId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void createJob_withWrongCsrfHeader_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(jobRequestBody("Wrong CSRF", "2025551333"));

        mockMvc.perform(post("/api/jobs")
                .cookie(new Cookie(SessionService.SESSION_COOKIE, employerA.sessionId))
                .header("X-XSRF-TOKEN", "not-the-right-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void employerB_cannotReadEmployerA_jobById() throws Exception {
        String body = objectMapper.writeValueAsString(jobRequestBody("Tenant A Job", "2025551444"));
        Long jobId = objectMapper.readTree(
                mockMvc.perform(post("/api/jobs")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, employerA.sessionId))
                        .header("X-XSRF-TOKEN", employerA.csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString())
            .get("id").asLong();

        // Employer A sees their own job.
        mockMvc.perform(get("/api/jobs/" + jobId)
                .cookie(new Cookie(SessionService.SESSION_COOKIE, employerA.sessionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(jobId));

        // Employer B must NOT see Employer A's job — service-layer scoping should refuse the lookup.
        mockMvc.perform(get("/api/jobs/" + jobId)
                .cookie(new Cookie(SessionService.SESSION_COOKIE, employerB.sessionId)))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 403 && status != 404) {
                    throw new AssertionError(
                        "Cross-tenant GET should return 403 or 404, got " + status);
                }
            });
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private Map<String, Object> jobRequestBody(String title, String phone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", "This description is at least twenty characters long for validation.");
        body.put("categoryId", categoryId);
        body.put("locationId", locationId);
        body.put("payType", "HOURLY");
        body.put("settlementType", "WEEKLY");
        body.put("payAmount", 20);
        body.put("headcount", 3);
        body.put("weeklyHours", 40);
        body.put("contactPhone", phone);
        body.put("tags", List.of("fullstack-it"));
        body.put("validityStart", LocalDate.now().toString());
        body.put("validityEnd", LocalDate.now().plusDays(30).toString());
        return body;
    }

    private SessionHandle createEmployerWithSession(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.local");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setRole(UserRole.EMPLOYER);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordChangedAt(Instant.now());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user = userRepository.save(user);

        UserSession session = new UserSession();
        session.setId("full-stack-session-" + username);
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
