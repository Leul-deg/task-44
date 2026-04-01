package com.shiftworks.jobops.dto;

import java.time.Instant;

public record JobPostingHistoryResponse(
    String previousStatus,
    String newStatus,
    String changedBy,
    String changeReason,
    Instant createdAt
) {
}
