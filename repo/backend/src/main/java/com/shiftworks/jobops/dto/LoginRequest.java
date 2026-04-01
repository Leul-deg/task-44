package com.shiftworks.jobops.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String username,
    @NotBlank String password,
    String captchaId,
    String captchaAnswer
) {
}
