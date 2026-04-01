package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.TicketPriority;
import com.shiftworks.jobops.enums.TicketStatus;

public record TicketUpdateRequest(TicketStatus status,
                                  TicketPriority priority,
                                  String resolution,
                                  Long assignedTo) {
}
