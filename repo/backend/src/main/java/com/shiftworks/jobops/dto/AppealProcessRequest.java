package com.shiftworks.jobops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppealProcessRequest(@NotBlank String decision,
                                   @NotBlank @Size(min = 10) String reviewerRationale) {
}
