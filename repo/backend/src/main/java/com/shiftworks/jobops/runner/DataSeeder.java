package com.shiftworks.jobops.runner;

import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}")
    private String bootstrapAdminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            if (bootstrapAdminPassword == null || bootstrapAdminPassword.isBlank()) {
                log.warn("Skipping admin bootstrap because BOOTSTRAP_ADMIN_PASSWORD is not set");
                return;
            }
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@shiftworks.local");
            admin.setPasswordHash(passwordEncoder.encode(bootstrapAdminPassword));
            admin.setRole(UserRole.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            admin.setFailedLoginAttempts(0);
            admin.setCaptchaRequired(false);
            admin.setPasswordChangedAt(Instant.now());
            admin.setCreatedAt(Instant.now());
            admin.setUpdatedAt(Instant.now());
            userRepository.save(admin);
            log.info("Bootstrapped admin user from BOOTSTRAP_ADMIN_PASSWORD");
        }
    }
}
