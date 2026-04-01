package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminUserRoleChangeRequest(@NotNull UserRole role,
                                         @NotBlank String stepUpPassword) {
}
