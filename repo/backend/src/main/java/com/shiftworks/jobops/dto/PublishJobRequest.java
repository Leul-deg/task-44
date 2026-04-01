package com.shiftworks.jobops.dto;

import jakarta.validation.constraints.NotBlank;

public record PublishJobRequest(@NotBlank String stepUpPassword) {
}
