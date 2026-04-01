package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.PayType;
import com.shiftworks.jobops.enums.SettlementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JobPostingResponse(
    Long id,
    String title,
    String description,
    JobStatus status,
    Long categoryId,
    String categoryName,
    Long locationId,
    String locationState,
    String locationCity,
    PayType payType,
    SettlementType settlementType,
    BigDecimal payAmount,
    Integer headcount,
    BigDecimal weeklyHours,
    String contactPhone,
    String contactPhoneMasked,
    List<String> tags,
    LocalDate validityStart,
    LocalDate validityEnd,
    Instant publishedAt,
    Instant createdAt,
    Instant updatedAt,
    Long employerId,
    String employerUsername,
    String reviewerNotes,
    String takedownReason
) {
}
