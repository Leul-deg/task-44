package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.dto.ChangePasswordRequest;
import com.shiftworks.jobops.dto.LoginRequest;
import com.shiftworks.jobops.dto.LoginResponse;
import com.shiftworks.jobops.dto.RegisterRequest;
import com.shiftworks.jobops.dto.UserResponse;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String DUMMY_BCRYPT_HASH = "$2b$10$uhDKNM11S8f4yWwTnR7gp.iOQJmEa1DTZXeFE9HJZgigs/4A3DO7y";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    public static final String LOGIN_FAILURE = "Invalid username or password";

    @Transactional
    public LoginResult login(LoginRequest request, HttpServletRequest httpRequest, boolean captchaPassed) {
        log.info("Login attempt for user={}", request.username());
        Optional<User> userOptional = userRepository.findByUsername(request.username());
        if (userOptional.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            return LoginResult.failure(false);
        }
        User user = userOptional.get();
        validateLockState(user);
        boolean captchaRequired = user.getFailedLoginAttempts() >= appProperties.getSecurity().getCaptchaAfterFailures();
        if (captchaRequired && !captchaPassed) {
            return LoginResult.failure(true);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.log(user.getId(), "USER_LOGIN_FAILED", "USER", user.getId(), null, null);
            registerFailure(user);
            boolean requireCaptcha = user.getFailedLoginAttempts() >= appProperties.getSecurity().getCaptchaAfterFailures();
            return LoginResult.failure(requireCaptcha);
        }
        resetFailures(user);
        auditService.log(user.getId(), "USER_LOGIN", "USER", user.getId(), null, null);
        boolean passwordExpired = user.getPasswordChangedAt() != null && !user.getPasswordChangedAt()
            .isAfter(Instant.now().minus(appProperties.getSecurity().getPassword().getRotationDays(), ChronoUnit.DAYS));
        UserSession session = sessionService.createSession(user, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return LoginResult.success(new LoginResponse(UserResponse.from(user), session.getCsrfToken(), passwordExpired), session.getId());
    }

    private void validateLockState(User user) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Account disabled");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
                throw new BusinessException(HttpStatus.LOCKED, "Account locked until " + user.getLockedUntil());
            }
            user.setStatus(UserStatus.ACTIVE);
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }
    }

    private int registerFailure(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        boolean captchaToggle = attempts >= appProperties.getSecurity().getCaptchaAfterFailures();
        user.setCaptchaRequired(captchaToggle);
        if (attempts >= appProperties.getSecurity().getLockAfterFailures()) {
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(Instant.now().plus(appProperties.getSecurity().getLockDurationMinutes(), ChronoUnit.MINUTES));
        }
        userRepository.save(user);
        return appProperties.getSecurity().getLockAfterFailures() - attempts;
    }

    private void resetFailures(User user) {
        user.setFailedLoginAttempts(0);
        user.setCaptchaRequired(false);
        user.setStatus(UserStatus.ACTIVE);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    @Transactional
    public UserResponse register(RegisterRequest request, Long actorUserId) {
        log.info("Registration attempt for user={}", request.username());
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Username already taken");
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email already registered");
        }
        var violations = passwordPolicyService.validate(request.password());
        if (!violations.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, String.join("; ", violations));
        }
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setRole(UserRole.EMPLOYER);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPasswordChangedAt(Instant.now());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setCaptchaRequired(false);
        User saved = userRepository.save(user);
        auditService.log(actorUserId, "USER_CREATED", "USER", saved.getId(), null, saved);
        return UserResponse.from(saved);
    }

    @Transactional
    public void logout(Long userId) {
        auditService.log(userId, "USER_LOGOUT", "USER", userId, null, null);
        sessionService.invalidateAllSessions(userId);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
        log.info("Password change for userId={}", user.getId());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        var violations = passwordPolicyService.validate(request.newPassword());
        if (!violations.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, String.join("; ", violations));
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        auditService.log(userId, "PASSWORD_CHANGED", "USER", userId, null, null);
        sessionService.invalidateAllSessions(userId);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.from(user);
    }

    public record LoginResult(LoginResponse response, LoginFailure failure, String sessionToken) {
        public static LoginResult success(LoginResponse response, String sessionToken) {
            return new LoginResult(response, null, sessionToken);
        }

        public static LoginResult failure(boolean captchaRequired) {
            return new LoginResult(null, new LoginFailure(captchaRequired), null);
        }

        public boolean isSuccess() {
            return response != null;
        }
    }

    public record LoginFailure(boolean captchaRequired) {
    }
}
