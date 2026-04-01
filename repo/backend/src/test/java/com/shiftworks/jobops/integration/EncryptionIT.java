package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.entity.Category;
import com.shiftworks.jobops.entity.Location;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.PayType;
import com.shiftworks.jobops.enums.SettlementType;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.CategoryRepository;
import com.shiftworks.jobops.repository.LocationRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class EncryptionIT {

    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;

    @Test
    void contactPhoneIsDecryptedTransparently() {
        User employer = new User();
        employer.setUsername("enctest_" + System.currentTimeMillis());
        employer.setEmail("enctest_" + System.currentTimeMillis() + "@test.com");
        employer.setPasswordHash("$2a$10$dummy");
        employer.setRole(UserRole.EMPLOYER);
        employer.setStatus(UserStatus.ACTIVE);
        employer.setPasswordChangedAt(Instant.now());
        employer.setCreatedAt(Instant.now());
        employer.setUpdatedAt(Instant.now());
        employer = userRepository.save(employer);

        Category cat = new Category();
        cat.setName("TestCat_" + System.currentTimeMillis());
        cat.setActive(true);
        cat = categoryRepository.save(cat);

        Location loc = new Location();
        loc.setState("CA");
        loc.setCity("TestCity_" + System.currentTimeMillis());
        loc.setActive(true);
        loc = locationRepository.save(loc);

        JobPosting job = new JobPosting();
        job.setTitle("Encryption Test Job");
        job.setDescription("Testing AES-256 column encryption");
        job.setStatus(JobStatus.DRAFT);
        job.setPayType(PayType.HOURLY);
        job.setSettlementType(SettlementType.WEEKLY);
        job.setPayAmount(BigDecimal.valueOf(25));
        job.setHeadcount(5);
        job.setWeeklyHours(BigDecimal.valueOf(40));
        job.setContactPhone("2025551234");
        job.setValidityStart(LocalDate.now());
        job.setValidityEnd(LocalDate.now().plusDays(30));
        job.setEmployer(employer);
        job.setCategory(cat);
        job.setLocation(loc);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        JobPosting saved = jobPostingRepository.save(job);

        JobPosting loaded = jobPostingRepository.findById(saved.getId()).orElseThrow();
        assertEquals("2025551234", loaded.getContactPhone(),
            "contactPhone must be decrypted transparently by JPA");
    }
}
