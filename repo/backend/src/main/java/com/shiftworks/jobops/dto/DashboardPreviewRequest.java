package com.shiftworks.jobops.dto;

public record DashboardPreviewRequest(String name, Object metricsJson, Object dimensionsJson, Object filtersJson) {
}
