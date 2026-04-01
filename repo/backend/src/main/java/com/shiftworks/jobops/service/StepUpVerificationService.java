package com.shiftworks.jobops.service;

import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StepUpVerificationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean verify(Long userId, String password) {
        return userRepository.findById(userId)
            .map(User::getPasswordHash)
            .map(hash -> passwordEncoder.matches(password, hash))
            .map(result -> {
                log.info("Step-up verification for userId={} result={}", userId, result);
                return result;
            })
            .orElseGet(() -> {
                log.info("Step-up verification for userId={} result=false", userId);
                return false;
            });
    }
}
