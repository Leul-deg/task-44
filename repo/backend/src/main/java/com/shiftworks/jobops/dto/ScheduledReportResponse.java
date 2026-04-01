package com.shiftworks.jobops.dto;

import java.time.Instant;

public record ScheduledReportResponse(Long id,
                                      Long dashboardConfigId,
                                      String dashboardName,
                                      String cronExpression,
                                      boolean isActive,
                                      Instant lastRunAt,
                                      Instant nextRunAt,
                                      Long userId) {
}
