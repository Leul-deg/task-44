package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminUserCreateRequest(
    @NotBlank String username,
    @Email String email,
    @NotBlank String password,
    @NotNull UserRole role
) {
}
