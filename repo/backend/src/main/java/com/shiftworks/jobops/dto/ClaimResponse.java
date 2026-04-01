package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.ClaimStatus;

import java.time.Instant;

public record ClaimResponse(Long id,
                            Long jobPostingId,
                            String jobTitle,
                            Long claimantId,
                            String claimantUsername,
                            ClaimStatus status,
                            String description,
                            String resolution,
                            Long assignedTo,
                            String assignedToUsername,
                            Instant createdAt,
                            Instant updatedAt) {
}
