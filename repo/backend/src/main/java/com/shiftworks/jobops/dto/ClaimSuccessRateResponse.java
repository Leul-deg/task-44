package com.shiftworks.jobops.dto;

public record ClaimSuccessRateResponse(double rate, long resolved, long total) {
}
