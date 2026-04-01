package com.shiftworks.jobops.dto;

public record LoginResponse(UserResponse user, String csrfToken, boolean passwordExpired) {
}
