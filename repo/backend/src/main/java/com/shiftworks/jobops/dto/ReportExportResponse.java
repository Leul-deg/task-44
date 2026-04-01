package com.shiftworks.jobops.dto;

import java.time.Instant;

public record ReportExportResponse(Long id,
                                   Long dashboardConfigId,
                                   String dashboardName,
                                   Instant createdAt,
                                   long fileSize,
                                   boolean masked,
                                   Long userId) {
}
