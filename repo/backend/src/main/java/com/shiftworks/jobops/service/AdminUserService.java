package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.AdminUserCreateRequest;
import com.shiftworks.jobops.dto.AdminUserResponse;
import com.shiftworks.jobops.dto.AdminUserRoleChangeRequest;
import com.shiftworks.jobops.dto.AdminUserUpdateRequest;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.dto.ResetPasswordResponse;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789!@#$%^&*";

    private final UserRepository userRepository;
    private final PasswordPolicyService passwordPolicyService;
    private final PasswordEncoder passwordEncoder;
    private final StepUpVerificationService stepUpVerificationService;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(String role, String status, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = userRepository.findAll((root, query, cb) -> {
            var predicate = cb.conjunction();
            if (role != null && !role.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("role"), UserRole.valueOf(role.toUpperCase(Locale.ROOT))));
            }
            if (status != null && !status.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), UserStatus.valueOf(status.toUpperCase(Locale.ROOT))));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicate = cb.and(predicate, cb.or(
                    cb.like(cb.lower(root.get("username")), like),
                    cb.like(cb.lower(root.get("email")), like)
                ));
            }
            return predicate;
        }, pageable);
        var items = result.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size, result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long id) {
        return userRepository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user: Not found"));
    }

    @Transactional
    public AdminUserResponse create(AdminUserCreateRequest request, AuthenticatedUser actor) {
        log.info("Admin creating user={} role={}", request.username(), request.role());
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "username: Already exists");
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "email: Already exists");
        }
        var violations = passwordPolicyService.validate(request.password());
        if (!violations.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, violations.get(0));
        }
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPasswordChangedAt(Instant.now());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.log(actor.id(), "USER_CREATED", "USER", user.getId(), null,
            java.util.Map.of("username", user.getUsername(), "role", user.getRole(), "status", user.getStatus()));
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse update(Long id, AdminUserUpdateRequest request, AuthenticatedUser actor) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user: Not found"));
        if (request.email() != null) {
            userRepository.findByEmail(request.email()).ifPresent(existing -> {
                if (!existing.getId().equals(user.getId())) {
                    throw new BusinessException(HttpStatus.CONFLICT, "email: Email already in use");
                }
            });
            user.setEmail(request.email());
        }
        if (request.status() != null) {
            if (actor.id().equals(user.getId()) && request.status() == UserStatus.DISABLED) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "status: Cannot disable your own account");
            }
            UserStatus oldStatus = user.getStatus();
            user.setStatus(request.status());
            if (oldStatus != request.status()) {
                auditService.log(actor.id(), "USER_STATUS_CHANGED", "USER", id, oldStatus, request.status());
            }
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public void changeRole(Long id, AdminUserRoleChangeRequest request, AuthenticatedUser actor) {
        log.info("Admin changing role for userId={} to {}", id, request.role());
        if (actor.id().equals(id)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "role: Cannot change your own role");
        }
        if (!stepUpVerificationService.verify(actor.id(), request.stepUpPassword())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "stepUpPassword: Verification failed");
        }
        User user = userRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user: Not found"));
        UserRole oldRole = user.getRole();
        user.setRole(request.role());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.log(actor.id(), "USER_ROLE_CHANGED", "USER", id, oldRole, request.role());
    }

    @Transactional
    public void unlock(Long id, AuthenticatedUser actor) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user: Not found"));
        if (user.getStatus() != UserStatus.LOCKED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "user: Account is not locked");
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.log(actor.id(), "USER_UNLOCKED", "USER", id,
            java.util.Map.of("status", "LOCKED"),
            java.util.Map.of("status", "ACTIVE"));
    }

    @Transactional
    public ResetPasswordResponse resetPassword(Long id, AuthenticatedUser actor) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user: Not found"));
        String password = generatePassword();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordChangedAt(Instant.ofEpochSecond(0));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.log(actor.id(), "USER_PASSWORD_RESET", "USER", id, null,
            java.util.Map.of("targetUserId", id));
        return new ResetPasswordResponse(password);
    }

    private String generatePassword() {
        String candidate;
        do {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                builder.append(PASSWORD_CHARS.charAt(secureRandom.nextInt(PASSWORD_CHARS.length())));
            }
            candidate = builder.toString();
        } while (!passwordPolicyService.isValid(candidate));
        return candidate;
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole(),
            user.getStatus(),
            user.getCreatedAt(),
            user.getPasswordChangedAt(),
            user.getFailedLoginAttempts(),
            user.getLockedUntil()
        );
    }
}
