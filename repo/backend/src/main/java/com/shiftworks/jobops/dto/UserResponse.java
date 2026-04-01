package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.UserRole;

import java.time.Instant;

public record UserResponse(Long id, String username, String email, UserRole role, Instant passwordChangedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getPasswordChangedAt());
    }
}
