package com.shiftworks.jobops.service;

import com.shiftworks.jobops.entity.Alert;
import com.shiftworks.jobops.repository.AlertRepository;
import com.shiftworks.jobops.repository.ClaimRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.ReviewActionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock private ReviewActionRepository reviewActionRepository;
    @Mock private ClaimRepository claimRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private AlertRepository alertRepository;
    @InjectMocks private AnomalyDetectionService anomalyDetectionService;

    private List<Object[]> buildDailyData(int days, long baseCount) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= days; i++) {
            long count = baseCount + (i % 3 == 0 ? 1 : 0);
            rows.add(new Object[]{Date.valueOf("2025-01-" + String.format("%02d", Math.min(i, 28))), count});
        }
        return rows;
    }

    @Test
    void takedownSpikeGeneratesAlert() {
        when(reviewActionRepository.countTodayTakedowns()).thenReturn(50L);
        when(reviewActionRepository.dailyTakedownsLast30Days()).thenReturn(buildDailyData(30, 2L));
        when(alertRepository.existsByAlertTypeAndCreatedAtAfter(eq("TAKEDOWN_SPIKE"), any())).thenReturn(false);

        when(reviewActionRepository.todayApprovalCounts()).thenReturn(null);
        when(claimRepository.countTodayClaims()).thenReturn(0L);
        when(jobPostingRepository.countByStatus(any())).thenReturn(0L);

        anomalyDetectionService.detectAnomalies();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        boolean hasTakedownSpike = captor.getAllValues().stream()
            .anyMatch(a -> "TAKEDOWN_SPIKE".equals(a.getAlertType()));
        assertTrue(hasTakedownSpike, "Should create TAKEDOWN_SPIKE alert");
    }

    @Test
    void normalCountsNoAlert() {
        when(reviewActionRepository.countTodayTakedowns()).thenReturn(2L);
        when(reviewActionRepository.dailyTakedownsLast30Days()).thenReturn(buildDailyData(30, 2L));

        when(reviewActionRepository.todayApprovalCounts()).thenReturn(null);
        when(claimRepository.countTodayClaims()).thenReturn(0L);
        when(jobPostingRepository.countByStatus(any())).thenReturn(0L);

        anomalyDetectionService.detectAnomalies();

        verify(alertRepository, never()).save(any());
    }

    @Test
    void duplicateAlertPrevented() {
        when(reviewActionRepository.countTodayTakedowns()).thenReturn(20L);
        when(reviewActionRepository.dailyTakedownsLast30Days()).thenReturn(buildDailyData(30, 2L));
        when(alertRepository.existsByAlertTypeAndCreatedAtAfter(eq("TAKEDOWN_SPIKE"), any())).thenReturn(true);

        when(reviewActionRepository.todayApprovalCounts()).thenReturn(null);
        when(claimRepository.countTodayClaims()).thenReturn(0L);
        when(jobPostingRepository.countByStatus(any())).thenReturn(0L);

        anomalyDetectionService.detectAnomalies();

        verify(alertRepository, never()).save(any());
    }

    @Test
    void malformedApprovalCountsDoNotCrash() {
        when(reviewActionRepository.countTodayTakedowns()).thenReturn(0L);
        when(reviewActionRepository.todayApprovalCounts()).thenReturn(new Object[]{0L});
        when(claimRepository.countTodayClaims()).thenReturn(0L);
        when(jobPostingRepository.countByStatus(any())).thenReturn(0L);

        assertDoesNotThrow(() -> anomalyDetectionService.detectAnomalies());
        verify(alertRepository, never()).save(any());
    }
}
