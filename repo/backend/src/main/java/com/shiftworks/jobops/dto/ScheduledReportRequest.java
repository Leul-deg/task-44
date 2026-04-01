package com.shiftworks.jobops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScheduledReportRequest(@NotNull Long dashboardConfigId,
                                     @NotBlank String cronExpression) {
}
