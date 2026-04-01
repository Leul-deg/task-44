package com.shiftworks.jobops.dto;

import java.time.Instant;

public record AdminCategoryResponse(Long id,
                                    String name,
                                    String description,
                                    boolean active,
                                    long activePostings,
                                    Instant createdAt) {
}
