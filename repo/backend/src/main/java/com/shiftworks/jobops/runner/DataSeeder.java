package com.shiftworks.jobops.runner;

import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@shiftworks.local");
            admin.setPasswordHash(passwordEncoder.encode("Admin@123456789"));
            admin.setRole(UserRole.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            admin.setFailedLoginAttempts(0);
            admin.setCaptchaRequired(false);
            admin.setPasswordChangedAt(Instant.now());
            admin.setCreatedAt(Instant.now());
            admin.setUpdatedAt(Instant.now());
            userRepository.save(admin);
        }

        if (userRepository.findByUsername("employer1").isEmpty()) {
            User employer = new User();
            employer.setUsername("employer1");
            employer.setEmail("employer1@shiftworks.local");
            employer.setPasswordHash(passwordEncoder.encode("Employer@12345"));
            employer.setRole(UserRole.EMPLOYER);
            employer.setStatus(UserStatus.ACTIVE);
            employer.setFailedLoginAttempts(0);
            employer.setCaptchaRequired(false);
            employer.setPasswordChangedAt(Instant.now());
            employer.setCreatedAt(Instant.now());
            employer.setUpdatedAt(Instant.now());
            userRepository.save(employer);
        }

        if (userRepository.findByUsername("reviewer1").isEmpty()) {
            User reviewer = new User();
            reviewer.setUsername("reviewer1");
            reviewer.setEmail("reviewer1@shiftworks.local");
            reviewer.setPasswordHash(passwordEncoder.encode("Reviewer@12345"));
            reviewer.setRole(UserRole.REVIEWER);
            reviewer.setStatus(UserStatus.ACTIVE);
            reviewer.setFailedLoginAttempts(0);
            reviewer.setCaptchaRequired(false);
            reviewer.setPasswordChangedAt(Instant.now());
            reviewer.setCreatedAt(Instant.now());
            reviewer.setUpdatedAt(Instant.now());
            userRepository.save(reviewer);
        }
    }
}
