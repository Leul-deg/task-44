package com.shiftworks.jobops.dto;

import java.time.LocalDate;

public record ReviewerActivityPoint(Long reviewerId, String username, LocalDate date, long actions) {
}
