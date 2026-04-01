package com.shiftworks.jobops.dto;

import java.time.Instant;

public record AuditLogResponse(Long id,
                                String username,
                                String action,
                                String entityType,
                                Long entityId,
                                String ipAddress,
                                Instant createdAt) {
}
