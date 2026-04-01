package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;

import java.time.Instant;

public record AdminUserResponse(Long id,
                                String username,
                                String email,
                                UserRole role,
                                UserStatus status,
                                Instant createdAt,
                                Instant passwordChangedAt,
                                int failedLoginAttempts,
                                Instant lockedUntil) {
}
