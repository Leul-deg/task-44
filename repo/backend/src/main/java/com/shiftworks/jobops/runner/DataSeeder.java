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
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }

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
}
