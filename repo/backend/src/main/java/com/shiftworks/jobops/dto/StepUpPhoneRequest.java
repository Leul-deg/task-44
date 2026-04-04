package com.shiftworks.jobops.dto;

import jakarta.validation.constraints.NotBlank;

public record StepUpPhoneRequest(@NotBlank String stepUpPassword) {
}
