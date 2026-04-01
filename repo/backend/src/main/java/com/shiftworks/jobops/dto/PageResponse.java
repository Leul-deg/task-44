package com.shiftworks.jobops.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, long totalElements, int page, int size, int totalPages) {
}
