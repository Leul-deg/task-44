package com.shiftworks.jobops.dto;

public record DashboardRequest(String name, Object metricsJson, Object dimensionsJson, Object filtersJson) {
}
