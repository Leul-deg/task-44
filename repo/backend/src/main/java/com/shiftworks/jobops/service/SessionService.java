package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    public static final String SESSION_COOKIE = "SESSION_TOKEN";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";

    private final UserSessionRepository userSessionRepository;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public UserSession createSession(User user, String ipAddress, String userAgent) {
        log.info("Session created for userId={}", user.getId());
        UserSession session = new UserSession();
        session.setId(UUID.randomUUID().toString());
        session.setUser(user);
        session.setCreatedAt(Instant.now());
        session.setLastActiveAt(Instant.now());
        session.setAbsoluteExpiry(Instant.now().plus(Duration.ofHours(appProperties.getSession().getAbsoluteTimeoutHours())));
        session.setValid(true);
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        session.setCsrfToken(generateCsrfToken());
        return userSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> findValidSession(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return userSessionRepository.findById(token)
            .filter(UserSession::isValid)
            .filter(session -> session.getAbsoluteExpiry().isAfter(Instant.now()));
    }

    @Transactional
    public void touchSession(UserSession session) {
        session.setLastActiveAt(Instant.now());
        userSessionRepository.save(session);
    }

    @Transactional
    public void invalidateSession(UserSession session) {
        log.info("Session invalidated id={}", session.getId());
        session.setValid(false);
        userSessionRepository.save(session);
    }

    @Transactional
    public void invalidateAllSessions(Long userId) {
        log.info("All sessions invalidated for userId={}", userId);
        List<UserSession> sessions = userSessionRepository.findByUser_Id(userId);
        if (sessions.isEmpty()) {
            return;
        }
        sessions.stream()
            .filter(UserSession::isValid)
            .forEach(session -> session.setValid(false));
        userSessionRepository.saveAll(sessions);
    }

    private String generateCsrfToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
