package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.AlertSeverity;

import java.time.Instant;

public record AlertPageResponse(Long id,
                                String alertType,
                                String metricName,
                                String message,
                                AlertSeverity severity,
                                boolean read,
                                String acknowledgedBy,
                                Instant createdAt) {
}
