package com.shiftworks.jobops.dto;

public record ScheduledReportUpdateRequest(String cronExpression, Boolean isActive) {
}
