package com.shiftworks.jobops.dto;

import java.time.Instant;

public record AdminLocationResponse(Long id,
                                    String state,
                                    String city,
                                    boolean active,
                                    Instant createdAt) {
}
