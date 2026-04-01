package com.shiftworks.jobops.service;

import com.shiftworks.jobops.entity.Alert;
import com.shiftworks.jobops.enums.AlertSeverity;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.repository.AlertRepository;
import com.shiftworks.jobops.repository.ClaimRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.ReviewActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {

    private final ReviewActionRepository reviewActionRepository;
    private final ClaimRepository claimRepository;
    private final JobPostingRepository jobPostingRepository;
    private final AlertRepository alertRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void detectAnomalies() {
        log.debug("Running anomaly detection checks");
        checkTakedownSpike();
        checkLowApprovalRate();
        checkClaimSpike();
        checkReviewBacklog();
    }

    private void checkTakedownSpike() {
        long today = reviewActionRepository.countTodayTakedowns();
        if (today == 0) return;

        List<Double> daily = reviewActionRepository.dailyTakedownsLast30Days()
            .stream().map(row -> ((Number) row[1]).doubleValue()).toList();
        if (daily.isEmpty()) return;

        double mean = daily.stream().mapToDouble(d -> d).average().orElse(0);
        double stddev = Math.sqrt(daily.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0));
        if (stddev == 0) return;

        if (today > mean + 2 * stddev) {
            AlertSeverity severity = (today > mean + 3 * stddev) ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            createAlertIfNotExists("TAKEDOWN_SPIKE", "takedown_count", severity,
                String.format("Takedown count today (%d) exceeds 30-day avg (%.1f) by more than 2\u03c3", today, mean));
        }
    }

    private void checkClaimSpike() {
        long today = claimRepository.countTodayClaims();
        if (today == 0) return;

        List<Double> daily = claimRepository.dailyClaimsLast30Days()
            .stream().map(row -> ((Number) row[1]).doubleValue()).toList();
        if (daily.isEmpty()) return;

        double mean = daily.stream().mapToDouble(d -> d).average().orElse(0);
        double stddev = Math.sqrt(daily.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0));
        if (stddev == 0) return;

        if (today > mean + 2 * stddev) {
            AlertSeverity severity = (today > mean + 3 * stddev) ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            createAlertIfNotExists("CLAIM_SPIKE", "claim_count", severity,
                String.format("Claim count today (%d) exceeds 30-day avg (%.1f) by more than 2\u03c3", today, mean));
        }
    }

    private void checkLowApprovalRate() {
        Object[] counts = reviewActionRepository.todayApprovalCounts();
        if (counts == null || counts[1] == null) return;
        long approved = ((Number) counts[0]).longValue();
        long total = ((Number) counts[1]).longValue();
        if (total == 0) return;

        double todayRate = (double) approved / total;

        List<Double> dailyRates = reviewActionRepository.dailyApprovalStatsLast30Days()
            .stream().map(row -> {
                double a = ((Number) row[1]).doubleValue();
                double t = ((Number) row[2]).doubleValue();
                return t > 0 ? a / t : 0.0;
            }).toList();
        if (dailyRates.isEmpty()) return;

        double mean = dailyRates.stream().mapToDouble(d -> d).average().orElse(0);
        double stddev = Math.sqrt(dailyRates.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0));

        if (todayRate < 0.5 && todayRate < mean - 2 * stddev) {
            AlertSeverity severity = (todayRate < mean - 3 * stddev) ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            createAlertIfNotExists("LOW_APPROVAL_RATE", "approval_rate", severity,
                String.format("Approval rate today (%.0f%%) is below 50%% and below 30-day avg (%.0f%%)", todayRate * 100, mean * 100));
        }
    }

    private void checkReviewBacklog() {
        long pending = jobPostingRepository.countByStatus(JobStatus.PENDING_REVIEW);
        if (pending <= 5) return;

        Instant thirtyDaysAgo = LocalDate.now().minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long totalLast30 = jobPostingRepository.countCreatedSince(thirtyDaysAgo);
        double avgDaily = totalLast30 / 30.0;

        if (pending > 2 * avgDaily) {
            String message = String.format("Pending review backlog (%d) exceeds 2x daily avg submissions (%.1f)", pending, avgDaily);
            createAlertIfNotExists("REVIEW_BACKLOG", "pending_review_count", AlertSeverity.WARNING, message);
        }
    }

    private void createAlertIfNotExists(String alertType, String metricName, AlertSeverity severity, String message) {
        Instant startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        if (alertRepository.existsByAlertTypeAndCreatedAtAfter(alertType, startOfToday)) {
            return;
        }
        Alert alert = new Alert();
        alert.setAlertType(alertType);
        alert.setMetricName(metricName);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setRead(false);
        alert.setCreatedAt(Instant.now());
        alertRepository.save(alert);
        log.info("Anomaly alert created: type={}, severity={}", alertType, severity);
    }
}
