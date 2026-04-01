package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.ApprovalRateResponse;
import com.shiftworks.jobops.dto.AverageHandlingTimeResponse;
import com.shiftworks.jobops.dto.ClaimSuccessRateResponse;
import com.shiftworks.jobops.dto.PostStatusPoint;
import com.shiftworks.jobops.dto.PostVolumePoint;
import com.shiftworks.jobops.dto.ReviewerActivityPoint;
import com.shiftworks.jobops.entity.Claim;
import com.shiftworks.jobops.enums.ClaimStatus;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.repository.ClaimRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.ReviewActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final JobPostingRepository jobPostingRepository;
    private final ClaimRepository claimRepository;
    private final ReviewActionRepository reviewActionRepository;

    public List<PostVolumePoint> postVolume(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        return jobPostingRepository.findPostVolumeBetween(range.from(), range.to()).stream()
            .map(row -> new PostVolumePoint(extractDate(row[0]), ((Number) row[1]).longValue()))
            .toList();
    }

    public List<PostStatusPoint> postStatusDistribution(LocalDate from, LocalDate to) {
        List<Object[]> rows;
        if (from != null || to != null) {
            DateRange range = resolveRange(from, to);
            rows = jobPostingRepository.findPostStatusCountsBetween(range.from(), range.to());
        } else {
            rows = jobPostingRepository.findPostStatusCounts();
        }
        return rows.stream()
            .map(row -> new PostStatusPoint(JobStatus.valueOf(((String) row[0]).toUpperCase(Locale.ROOT)), ((Number) row[1]).longValue()))
            .toList();
    }

    public ClaimSuccessRateResponse claimSuccessRate(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        long resolved = claimRepository.count((root, query, cb) -> cb.and(
            cb.equal(root.get("status"), ClaimStatus.RESOLVED),
            cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()),
            cb.lessThan(root.get("createdAt"), range.to())
        ));
        long total = claimRepository.count((root, query, cb) -> cb.and(
            cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()),
            cb.lessThan(root.get("createdAt"), range.to())
        ));
        double rate = total == 0 ? 0.0 : (double) resolved / total;
        return new ClaimSuccessRateResponse(rate, resolved, total);
    }

    public AverageHandlingTimeResponse averageHandlingTime(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        Double avg = reviewActionRepository.findAverageHandlingHours(range.from(), range.to());
        double value = avg == null ? 0.0 : avg;
        return new AverageHandlingTimeResponse(value);
    }

    public List<ReviewerActivityPoint> reviewerActivity(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        return reviewActionRepository.findReviewerActivityBetween(range.from(), range.to()).stream()
            .map(row -> new ReviewerActivityPoint(
                ((Number) row[0]).longValue(),
                (String) row[1],
                extractDate(row[2]),
                ((Number) row[3]).longValue()
            ))
            .sorted(Comparator.comparing(ReviewerActivityPoint::date))
            .toList();
    }

    public ApprovalRateResponse approvalRate(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        Object result = reviewActionRepository.findApprovalStats(range.from(), range.to());
        Object[] row;
        if (result instanceof Object[] arr && arr.length > 0 && arr[0] instanceof Object[]) {
            row = (Object[]) arr[0];
        } else {
            row = result instanceof Object[] arr ? arr : null;
        }
        long approved = row != null && row.length > 0 && row[0] != null ? ((Number) row[0]).longValue() : 0L;
        long total = row != null && row.length > 1 && row[1] != null ? ((Number) row[1]).longValue() : 0L;
        double rate = total == 0 ? 0.0 : (double) approved / total;
        return new ApprovalRateResponse(rate, approved, total);
    }

    public List<PostVolumePoint> takedownTrend(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        return reviewActionRepository.findTakedownTrendBetween(range.from(), range.to()).stream()
            .map(row -> new PostVolumePoint(extractDate(row[0]), ((Number) row[1]).longValue()))
            .toList();
    }

    private DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        Instant fromInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new DateRange(fromInstant, toInstant);
    }

    private LocalDate extractDate(Object value) {
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return LocalDate.now();
    }

    private record DateRange(Instant from, Instant to) {
    }
}
