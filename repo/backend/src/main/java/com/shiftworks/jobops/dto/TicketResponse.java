package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.TicketPriority;
import com.shiftworks.jobops.enums.TicketStatus;

import java.time.Instant;

public record TicketResponse(Long id,
                             String subject,
                             String description,
                             TicketStatus status,
                             TicketPriority priority,
                             Long reporterId,
                             String reporterUsername,
                             Long assignedTo,
                             String assignedToUsername,
                             String resolution,
                             Instant createdAt,
                             Instant updatedAt) {
}
