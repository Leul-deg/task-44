package com.shiftworks.jobops.dto;

public record DashboardConfigResponse(Long id, String name, Object metricsJson, Object dimensionsJson, Object filtersJson) {
}
