package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.AdminStatsResponse;
import com.shiftworks.jobops.dto.PostStatusPoint;
import com.shiftworks.jobops.dto.PostVolumePoint;
import com.shiftworks.jobops.enums.ClaimStatus;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.TicketStatus;
import com.shiftworks.jobops.repository.AlertRepository;
import com.shiftworks.jobops.repository.ClaimRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.TicketRepository;
import com.shiftworks.jobops.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private ClaimRepository claimRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private AlertRepository alertRepository;

    @InjectMocks private AdminStatsService adminStatsService;

    @Test
    void counts_aggregatesAllRepositories() {
        when(userRepository.count()).thenReturn(50L);
        when(jobPostingRepository.count(any(Specification.class))).thenReturn(12L, 3L);
        when(claimRepository.countByStatus(ClaimStatus.OPEN)).thenReturn(7L);
        when(ticketRepository.countByStatus(TicketStatus.OPEN)).thenReturn(4L);
        when(alertRepository.countByReadFalse()).thenReturn(2L);

        AdminStatsResponse result = adminStatsService.counts();

        assertThat(result.totalUsers()).isEqualTo(50L);
        assertThat(result.activePostings()).isEqualTo(12L);
        assertThat(result.pendingReviews()).isEqualTo(3L);
        assertThat(result.openClaims()).isEqualTo(7L);
        assertThat(result.openTickets()).isEqualTo(4L);
        assertThat(result.unreadAlerts()).isEqualTo(2L);
    }

    @Test
    void postVolume_mapsRepositoryResultsToPoints() {
        Object[] row = new Object[]{Date.valueOf(LocalDate.of(2026, 4, 1)), 5};
        when(jobPostingRepository.findPostVolumeBetween(any(), any())).thenReturn(List.of(row));

        List<PostVolumePoint> result = adminStatsService.postVolume(30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).count()).isEqualTo(5L);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void postVolume_returnsEmptyListWhenNoData() {
        when(jobPostingRepository.findPostVolumeBetween(any(), any())).thenReturn(List.of());

        List<PostVolumePoint> result = adminStatsService.postVolume(7);

        assertThat(result).isEmpty();
    }

    @Test
    void postStatus_mapsRepositoryResultsToPoints() {
        Object[] row1 = new Object[]{"PUBLISHED", 8};
        Object[] row2 = new Object[]{"PENDING_REVIEW", 3};
        when(jobPostingRepository.findPostStatusCounts()).thenReturn(List.of(row1, row2));

        List<PostStatusPoint> result = adminStatsService.postStatus();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).status()).isEqualTo(JobStatus.PUBLISHED);
        assertThat(result.get(0).count()).isEqualTo(8L);
    }
}
