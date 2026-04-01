package com.shiftworks.jobops.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminCategoryRequest(@NotBlank String name,
                                   String description) {
}
