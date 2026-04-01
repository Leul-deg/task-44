package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.ReviewActionType;

import java.time.Instant;

public record ReviewActionResponse(Long id, Long jobPostingId, String jobTitle, ReviewActionType action,
                                   String rationale, Instant createdAt) {
}
