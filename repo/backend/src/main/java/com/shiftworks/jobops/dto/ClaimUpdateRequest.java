package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.ClaimStatus;

public record ClaimUpdateRequest(ClaimStatus status,
                                 String resolution,
                                 Long assignedTo) {
}
