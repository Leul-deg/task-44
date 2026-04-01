package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.JobStatus;

public record PostStatusPoint(JobStatus status, long count) {
}
