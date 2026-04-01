package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.PayType;
import com.shiftworks.jobops.enums.SettlementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record JobPostingRequest(
    @NotBlank String title,
    @NotBlank String description,
    @NotNull Long categoryId,
    @NotNull Long locationId,
    @NotNull PayType payType,
    @NotNull SettlementType settlementType,
    @NotNull BigDecimal payAmount,
    @NotNull Integer headcount,
    @NotNull BigDecimal weeklyHours,
    String contactPhone,
    List<String> tags,
    LocalDate validityStart,
    LocalDate validityEnd
) {
}
