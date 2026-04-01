package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TicketRequest(@NotBlank String subject,
                            @NotBlank String description,
                            @NotNull TicketPriority priority) {
}
