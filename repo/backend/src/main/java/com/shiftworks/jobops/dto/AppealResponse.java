package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.AppealStatus;

import java.time.Instant;

public record AppealResponse(Long id,
                             Long jobPostingId,
                             String jobTitle,
                             String employerUsername,
                             String appealReason,
                             AppealStatus status,
                             Instant createdAt) {
}
