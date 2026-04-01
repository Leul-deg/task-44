package com.shiftworks.jobops.dto;

import java.util.List;

public record ReviewDashboardResponse(long pendingReviews,
                                      long pendingAppeals,
                                      long reviewedToday,
                                      List<ReviewActionResponse> recentActions) {
}
