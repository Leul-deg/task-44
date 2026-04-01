package com.shiftworks.jobops.dto;

import java.time.Instant;

public record AuditLogDetailResponse(Long id,
                                      String username,
                                      String action,
                                      String entityType,
                                      Long entityId,
                                      String beforeValue,
                                      String afterValue,
                                      String ipAddress,
                                      Instant createdAt) {
}
