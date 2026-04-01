package com.shiftworks.jobops.dto;

import java.time.Instant;

public record FileAttachmentResponse(Long id,
                                     String entityType,
                                     Long entityId,
                                     String originalFilename,
                                     String fileType,
                                     long fileSize,
                                     String checksum,
                                     String status,
                                     String uploadedBy,
                                     Instant createdAt) {
}
