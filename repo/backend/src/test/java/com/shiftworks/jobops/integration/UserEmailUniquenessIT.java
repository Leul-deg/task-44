package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserEmailUniquenessIT {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmailReturnsSavedUser() {
        User user = new User();
        user.setUsername("emailtest_" + System.currentTimeMillis());
        user.setEmail("unique_" + System.currentTimeMillis() + "@test.com");
        user.setPasswordHash("$2a$10$dummyhash000000000000000000000000000000000000000000");
        user.setRole(UserRole.EMPLOYER);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordChangedAt(Instant.now());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);

        Optional<User> found = userRepository.findByEmail(saved.getEmail());
        assertTrue(found.isPresent(), "findByEmail must return the saved user");
        assertEquals(saved.getId(), found.get().getId());
    }

    @Test
    void duplicateEmailIsRejectedByRepository() {
        String email = "dup_" + System.currentTimeMillis() + "@test.com";

        User user1 = new User();
        user1.setUsername("duptest1_" + System.currentTimeMillis());
        user1.setEmail(email);
        user1.setPasswordHash("$2a$10$dummyhash000000000000000000000000000000000000000000");
        user1.setRole(UserRole.EMPLOYER);
        user1.setStatus(UserStatus.ACTIVE);
        user1.setPasswordChangedAt(Instant.now());
        user1.setCreatedAt(Instant.now());
        user1.setUpdatedAt(Instant.now());
        userRepository.save(user1);

        User user2 = new User();
        user2.setUsername("duptest2_" + System.currentTimeMillis());
        user2.setEmail(email);
        user2.setPasswordHash("$2a$10$dummyhash000000000000000000000000000000000000000000");
        user2.setRole(UserRole.EMPLOYER);
        user2.setStatus(UserStatus.ACTIVE);
        user2.setPasswordChangedAt(Instant.now());
        user2.setCreatedAt(Instant.now());
        user2.setUpdatedAt(Instant.now());

        assertThrows(Exception.class, () -> {
            userRepository.saveAndFlush(user2);
        }, "Saving a second user with the same email must throw a constraint violation");
    }
}
