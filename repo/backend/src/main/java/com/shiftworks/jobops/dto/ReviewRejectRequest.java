package com.shiftworks.jobops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewRejectRequest(@NotBlank @Size(min = 10) String rationale, String reviewerNotes) {
}
