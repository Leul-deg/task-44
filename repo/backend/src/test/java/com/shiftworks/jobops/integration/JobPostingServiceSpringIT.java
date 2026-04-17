package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.dto.JobPostingRequest;
import com.shiftworks.jobops.dto.JobPostingResponse;
import com.shiftworks.jobops.entity.Category;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.Location;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.PayType;
import com.shiftworks.jobops.enums.SettlementType;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.CategoryRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.LocationRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.JobPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Full application context + H2: exercises {@link JobPostingService} with real JPA and encryption.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobPostingServiceSpringIT {

    @Autowired private JobPostingService jobPostingService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JobPostingRepository jobPostingRepository;

    private AuthenticatedUser employerAuth;
    private Long categoryId;
    private Long locationId;

    @BeforeEach
    void setup() {
        User employer = new User();
        employer.setUsername("springit_emp_" + System.nanoTime());
        employer.setEmail(employer.getUsername() + "@test.local");
        employer.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        employer.setRole(UserRole.EMPLOYER);
        employer.setStatus(UserStatus.ACTIVE);
        employer.setPasswordChangedAt(Instant.now());
        employer.setCreatedAt(Instant.now());
        employer.setUpdatedAt(Instant.now());
        employer = userRepository.save(employer);
        employerAuth = new AuthenticatedUser(employer.getId(), employer.getUsername(), UserRole.EMPLOYER);

        Category cat = new Category();
        cat.setName("SpringITCat_" + System.nanoTime());
        cat.setActive(true);
        categoryId = categoryRepository.save(cat).getId();

        Location loc = new Location();
        loc.setState("CA");
        loc.setCity("SpringITCity_" + System.nanoTime());
        loc.setActive(true);
        locationId = locationRepository.save(loc).getId();
    }

    @Test
    void createJob_persistsDraftWithJpaAndEncryption() {
        JobPostingRequest request = new JobPostingRequest(
            "Spring IT Title",
            "This description is at least twenty characters long.",
            categoryId,
            locationId,
            PayType.HOURLY,
            SettlementType.WEEKLY,
            BigDecimal.valueOf(20),
            5,
            BigDecimal.valueOf(40),
            "2025551234",
            List.of("integration"),
            LocalDate.now(),
            LocalDate.now().plusDays(30)
        );

        JobPostingResponse response = jobPostingService.createJob(request, employerAuth);

        assertNotNull(response.id());
        assertEquals(JobStatus.DRAFT, response.status());
        assertEquals("2025551234", response.contactPhone());

        JobPosting loaded = jobPostingRepository.findById(response.id()).orElseThrow();
        assertEquals(JobStatus.DRAFT, loaded.getStatus());
        assertEquals("2025551234", loaded.getContactPhone());
    }
}
