package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.UserStatus;
import jakarta.validation.constraints.Email;

public record AdminUserUpdateRequest(
    @Email String email,
    UserStatus status
) {
}
