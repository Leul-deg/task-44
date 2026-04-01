package com.shiftworks.jobops.dto;

import java.util.List;

public record JobPostingSummaryResponse(
    long total,
    long published,
    long pendingReview,
    long rejected,
    List<JobPostingResponse> recent
) {
}
