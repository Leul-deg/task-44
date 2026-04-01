package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.AppealStatus;

import java.time.Instant;

public record AppealDetailResponse(
    Long id,
    AppealStatus status,
    String appealReason,
    Instant createdAt,
    String employerUsername,
    String jobTitle,
    String categoryName,
    String location,
    String paySummary,
    String takedownReason,
    String takedownRationale,
    Instant takedownAt,
    String reviewerRationale,
    Instant processedAt
) {
}
