package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.ApprovalRateResponse;
import com.shiftworks.jobops.dto.AverageHandlingTimeResponse;
import com.shiftworks.jobops.dto.ClaimSuccessRateResponse;
import com.shiftworks.jobops.dto.PostStatusPoint;
import com.shiftworks.jobops.dto.PostVolumePoint;
import com.shiftworks.jobops.dto.ReviewerActivityPoint;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.repository.ClaimRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.ReviewActionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private ClaimRepository claimRepository;
    @Mock private ReviewActionRepository reviewActionRepository;
    @InjectMocks private AnalyticsService analyticsService;

    @Test
    void postVolume_mapsRepositoryRowsToPoints() {
        Object[] row = {Date.valueOf(LocalDate.of(2026, 4, 10)), 5L};
        when(jobPostingRepository.findPostVolumeBetween(any(Instant.class), any(Instant.class)))
            .thenReturn(List.<Object[]>of(row));

        List<PostVolumePoint> result = analyticsService.postVolume(null, null);

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2026, 4, 10), result.get(0).date());
        assertEquals(5L, result.get(0).count());
    }

    @Test
    void postVolume_withExplicitDates_passesRangeToRepository() {
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 15);
        when(jobPostingRepository.findPostVolumeBetween(any(Instant.class), any(Instant.class)))
            .thenReturn(List.of());

        analyticsService.postVolume(from, to);

        verify(jobPostingRepository, times(1)).findPostVolumeBetween(any(Instant.class), any(Instant.class));
    }

    @Test
    void postStatusDistribution_withNullDates_callsAllCounts() {
        Object[] row1 = {"DRAFT", 3L};
        Object[] row2 = {"PUBLISHED", 10L};
        when(jobPostingRepository.findPostStatusCounts()).thenReturn(List.of(row1, row2));

        List<PostStatusPoint> result = analyticsService.postStatusDistribution(null, null);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(p -> p.status() == JobStatus.DRAFT));
        assertTrue(result.stream().anyMatch(p -> p.status() == JobStatus.PUBLISHED));
    }

    @Test
    void postStatusDistribution_withDateRange_callsBetween() {
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 15);
        when(jobPostingRepository.findPostStatusCountsBetween(any(Instant.class), any(Instant.class)))
            .thenReturn(List.of());

        analyticsService.postStatusDistribution(from, to);

        verify(jobPostingRepository, times(1)).findPostStatusCountsBetween(any(Instant.class), any(Instant.class));
        verify(jobPostingRepository, never()).findPostStatusCounts();
    }

    @Test
    void approvalRate_calculatesCorrectly() {
        when(reviewActionRepository.findApprovalStats(any(Instant.class), any(Instant.class)))
            .thenReturn(new Object[]{3L, 10L});

        ApprovalRateResponse result = analyticsService.approvalRate(null, null);

        assertEquals(0.3, result.rate(), 1e-9);
        assertEquals(3L, result.approved());
        assertEquals(10L, result.total());
    }

    @Test
    void approvalRate_whenTotalZero_returnsZeroRate() {
        when(reviewActionRepository.findApprovalStats(any(Instant.class), any(Instant.class)))
            .thenReturn(new Object[]{0L, 0L});

        ApprovalRateResponse result = analyticsService.approvalRate(null, null);

        assertEquals(0.0, result.rate(), 1e-9);
    }

    @Test
    @SuppressWarnings("unchecked")
    void claimSuccessRate_calculatesRateFromCounts() {
        when(claimRepository.count(any(Specification.class))).thenReturn(2L, 5L);

        ClaimSuccessRateResponse result = analyticsService.claimSuccessRate(null, null);

        assertEquals(0.4, result.rate(), 1e-9);
        assertEquals(2L, result.resolved());
        assertEquals(5L, result.total());
    }

    @Test
    @SuppressWarnings("unchecked")
    void claimSuccessRate_whenNoTotal_returnsZeroRate() {
        when(claimRepository.count(any(Specification.class))).thenReturn(0L, 0L);

        ClaimSuccessRateResponse result = analyticsService.claimSuccessRate(null, null);

        assertEquals(0.0, result.rate(), 1e-9);
    }

    @Test
    void averageHandlingTime_returnsRepositoryValue() {
        when(reviewActionRepository.findAverageHandlingHours(any(Instant.class), any(Instant.class)))
            .thenReturn(24.0);

        AverageHandlingTimeResponse result = analyticsService.averageHandlingTime(null, null);

        assertEquals(24.0, result.averageHours(), 1e-9);
    }

    @Test
    void averageHandlingTime_whenNullFromRepository_returnsZero() {
        when(reviewActionRepository.findAverageHandlingHours(any(Instant.class), any(Instant.class)))
            .thenReturn(null);

        AverageHandlingTimeResponse result = analyticsService.averageHandlingTime(null, null);

        assertEquals(0.0, result.averageHours(), 1e-9);
    }

    @Test
    void reviewerActivity_sortsByDateAscending() {
        Object[] row1 = {1L, "alice", Date.valueOf(LocalDate.of(2026, 4, 15)), 3L};
        Object[] row2 = {2L, "bob", Date.valueOf(LocalDate.of(2026, 4, 10)), 5L};
        when(reviewActionRepository.findReviewerActivityBetween(any(Instant.class), any(Instant.class)))
            .thenReturn(List.of(row1, row2));

        List<ReviewerActivityPoint> result = analyticsService.reviewerActivity(null, null);

        assertEquals(LocalDate.of(2026, 4, 10), result.get(0).date());
    }

    @Test
    void takedownTrend_mapsRepositoryRows() {
        Object[] row = {Date.valueOf(LocalDate.of(2026, 4, 12)), 7L};
        when(reviewActionRepository.findTakedownTrendBetween(any(Instant.class), any(Instant.class)))
            .thenReturn(List.<Object[]>of(row));

        List<PostVolumePoint> result = analyticsService.takedownTrend(null, null);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).count());
    }
}
