package com.shiftworks.jobops.dto;

import java.time.LocalDate;
import java.util.List;

public record JobPostingPreviewResponse(
    String title,
    String description,
    String categoryName,
    String locationLabel,
    String paySummary,
    String settlementSummary,
    Integer headcount,
    String weeklyHours,
    LocalDate validityStart,
    LocalDate validityEnd,
    String contactPhoneMasked,
    List<String> tags
) {
}
