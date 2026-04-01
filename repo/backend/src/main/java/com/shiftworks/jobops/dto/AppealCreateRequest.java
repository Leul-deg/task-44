package com.shiftworks.jobops.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppealCreateRequest(@NotNull @Min(1) Long jobPostingId,
                                  @NotBlank @Size(min = 20) String appealReason) {
}
