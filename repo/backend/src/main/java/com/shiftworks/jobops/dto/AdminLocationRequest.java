package com.shiftworks.jobops.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLocationRequest(@NotBlank String state,
                                   @NotBlank String city) {
}
