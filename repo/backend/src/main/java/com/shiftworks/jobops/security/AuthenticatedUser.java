package com.shiftworks.jobops.security;

import com.shiftworks.jobops.enums.UserRole;

public record AuthenticatedUser(Long id, String username, UserRole role) {
}
