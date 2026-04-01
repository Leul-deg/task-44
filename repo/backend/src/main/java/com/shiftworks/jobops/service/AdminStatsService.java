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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository userRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ClaimRepository claimRepository;
    private final TicketRepository ticketRepository;
    private final AlertRepository alertRepository;

    public AdminStatsResponse counts() {
        long totalUsers = userRepository.count();
        long activePostings = jobPostingRepository.count((root, query, cb) -> cb.equal(root.get("status"), JobStatus.PUBLISHED));
        long pendingReviews = jobPostingRepository.count((root, query, cb) -> cb.equal(root.get("status"), JobStatus.PENDING_REVIEW));
        long openClaims = claimRepository.countByStatus(ClaimStatus.OPEN);
        long openTickets = ticketRepository.countByStatus(TicketStatus.OPEN);
        long unreadAlerts = alertRepository.countByReadFalse();
        return new AdminStatsResponse(totalUsers, activePostings, pendingReviews, openClaims, openTickets, unreadAlerts);
    }

    public List<PostVolumePoint> postVolume(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1);
        Instant from = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = Instant.now();
        return jobPostingRepository.findPostVolumeBetween(from, to).stream()
            .map(record -> {
                Object dateObj = record[0];
                LocalDate date = dateObj instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate() : ((java.util.Date) dateObj).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
                return new PostVolumePoint(date, ((Number) record[1]).longValue());
            })
            .toList();
    }

    public List<PostStatusPoint> postStatus() {
        return jobPostingRepository.findPostStatusCounts().stream()
            .map(record -> new PostStatusPoint(JobStatus.valueOf((String) record[0]), ((Number) record[1]).longValue()))
            .toList();
    }
}
