package com.shiftworks.jobops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewRationaleRequest(@NotBlank @Size(min = 10) String rationale) {
}
