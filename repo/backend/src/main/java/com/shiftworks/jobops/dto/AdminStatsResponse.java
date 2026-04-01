package com.shiftworks.jobops.dto;

public record AdminStatsResponse(long totalUsers,
                                 long activePostings,
                                 long pendingReviews,
                                 long openClaims,
                                 long openTickets,
                                 long unreadAlerts) {
}
