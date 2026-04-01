package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.FieldDiff;
import com.shiftworks.jobops.dto.JobPostingResponse;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.dto.ReviewActionResponse;
import com.shiftworks.jobops.dto.ReviewDashboardResponse;
import com.shiftworks.jobops.dto.ReviewRationaleRequest;
import com.shiftworks.jobops.dto.ReviewRejectRequest;
import com.shiftworks.jobops.dto.TakedownRequest;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.JobPostingHistory;
import com.shiftworks.jobops.entity.ReviewAction;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.ReviewActionType;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.ReviewActionRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final JobPostingRepository jobPostingRepository;
    private final ReviewActionRepository reviewActionRepository;
    private final UserRepository userRepository;
    private final JobHistoryService jobHistoryService;
    private final JobPostingService jobPostingService;
    private final StepUpVerificationService stepUpVerificationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<JobPostingResponse> getQueue(AuthenticatedUser reviewer, int page, int size) {
        ensureReviewer(reviewer);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<JobPosting> pending = jobPostingRepository.findAll((root, query, cb) -> cb.equal(root.get("status"), JobStatus.PENDING_REVIEW), pageable);
        List<JobPostingResponse> responses = pending.getContent().stream()
            .map(job -> jobPostingService.toResponse(job, reviewer))
            .toList();
        return new PageResponse<>(responses, pending.getTotalElements(), page, size, pending.getTotalPages());
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getJob(Long id, AuthenticatedUser reviewer) {
        ensureReviewer(reviewer);
        JobPosting jobPosting = jobPostingRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "jobPosting: Not found"));
        if (jobPosting.getStatus() != JobStatus.PENDING_REVIEW && jobPosting.getStatus() != JobStatus.PUBLISHED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "jobPosting: Not available for review");
        }
        return jobPostingService.toResponse(jobPosting, reviewer);
    }

    @Transactional(readOnly = true)
    public Map<String, FieldDiff> diff(Long id, AuthenticatedUser reviewer) {
        ensureReviewer(reviewer);
        JobPosting jobPosting = jobPostingRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "jobPosting: Not found"));
        JobPostingHistory history = jobHistoryService.findLatestSnapshot(jobPosting.getId());
        if (history == null) {
            return null;
        }
        Map<String, Object> oldValues = jobHistoryService.readSnapshot(history);
        Map<String, Object> currentValues = buildFieldMap(jobPosting);
        Map<String, FieldDiff> diffs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : oldValues.entrySet()) {
            String field = entry.getKey();
            Object oldValue = entry.getValue();
            Object newValue = currentValues.get(field);
            if (!Objects.equals(formatValue(oldValue), formatValue(newValue))) {
                diffs.put(field, new FieldDiff(formatValue(oldValue), formatValue(newValue)));
            }
        }
        return diffs.isEmpty() ? null : diffs;
    }

    @Transactional
    public void approve(Long id, ReviewRationaleRequest request, AuthenticatedUser reviewer) {
        ensureReviewer(reviewer);
        log.info("Approving job id={} by reviewer={}", id, reviewer.username());
        JobPosting job = loadJobForStatus(id, JobStatus.PENDING_REVIEW);
        job.setStatus(JobStatus.APPROVED);
        job.setUpdatedAt(Instant.now());
        jobPostingRepository.save(job);
        saveReviewAction(job, reviewer, ReviewActionType.APPROVE, request.rationale());
        jobHistoryService.record(job, JobStatus.PENDING_REVIEW, JobStatus.APPROVED, getReviewerUser(reviewer), request.rationale());
        auditService.log(reviewer.id(), "JOB_APPROVED", "JOB_POSTING", id, null, null);
    }

    @Transactional
    public void reject(Long id, ReviewRejectRequest request, AuthenticatedUser reviewer) {
        ensureReviewer(reviewer);
        log.info("Rejecting job id={} by reviewer={}", id, reviewer.username());
        JobPosting job = loadJobForStatus(id, JobStatus.PENDING_REVIEW);
        job.setStatus(JobStatus.REJECTED);
        job.setReviewerNotes(request.reviewerNotes());
        job.setUpdatedAt(Instant.now());
        jobPostingRepository.save(job);
        saveReviewAction(job, reviewer, ReviewActionType.REJECT, request.rationale());
        jobHistoryService.record(job, JobStatus.PENDING_REVIEW, JobStatus.REJECTED, getReviewerUser(reviewer), request.rationale());
        auditService.log(reviewer.id(), "JOB_REJECTED", "JOB_POSTING", id, null, null);
    }

    @Transactional
    public void takedown(Long id, TakedownRequest request, AuthenticatedUser reviewer) {
        ensureReviewer(reviewer);
        log.info("Takedown of job id={} by reviewer={}", id, reviewer.username());
        JobPosting job = loadJobForStatus(id, JobStatus.PUBLISHED);
        if (!stepUpVerificationService.verify(reviewer.id(), request.stepUpPassword())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "stepUpPassword: Verification failed");
        }
        job.setStatus(JobStatus.TAKEN_DOWN);
        job.setTakedownReason(request.rationale());
        job.setUpdatedAt(Instant.now());
        jobPostingRepository.save(job);
        saveReviewAction(job, reviewer, ReviewActionType.TAKEDOWN, request.rationale());
        jobHistoryService.record(job, JobStatus.PUBLISHED, JobStatus.TAKEN_DOWN, getReviewerUser(reviewer), request.rationale());
        auditService.log(reviewer.id(), "JOB_TAKEN_DOWN", "JOB_POSTING", id, null, null);
    }

    @Transactional(readOnly = true)
    public List<ReviewActionResponse> actionsForJob(Long jobId, AuthenticatedUser reviewer) {
        ensureReviewer(reviewer);
        return reviewActionRepository.findByJobPosting_IdOrderByCreatedAtDesc(jobId).stream()
            .map(this::toActionResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ReviewDashboardResponse dashboard(AuthenticatedUser reviewer) {
        ensureReviewer(reviewer);
        long pendingReviews = jobPostingRepository.count((root, query, cb) -> cb.equal(root.get("status"), JobStatus.PENDING_REVIEW));
        long pendingAppeals = jobPostingRepository.count((root, query, cb) -> cb.equal(root.get("status"), JobStatus.APPEAL_PENDING));
        Instant startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86400);
        long reviewedToday = reviewActionRepository.countByReviewer_IdAndCreatedAtBetween(reviewer.id(), startOfDay, endOfDay);
        List<ReviewActionResponse> recent = reviewActionRepository.findTop10ByReviewer_IdOrderByCreatedAtDesc(reviewer.id()).stream()
            .map(this::toActionResponse)
            .toList();
        return new ReviewDashboardResponse(pendingReviews, pendingAppeals, reviewedToday, recent);
    }

    private JobPosting loadJobForStatus(Long id, JobStatus requiredStatus) {
        JobPosting job = jobPostingRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "jobPosting: Not found"));
        if (job.getStatus() != requiredStatus) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "jobPosting: Status must be " + requiredStatus);
        }
        return job;
    }

    private void saveReviewAction(JobPosting jobPosting, AuthenticatedUser reviewer, ReviewActionType action, String rationale) {
        User reviewerEntity = getReviewerUser(reviewer);
        ReviewAction reviewAction = new ReviewAction();
        reviewAction.setJobPosting(jobPosting);
        reviewAction.setReviewer(reviewerEntity);
        reviewAction.setAction(action);
        reviewAction.setRationale(rationale);
        reviewActionRepository.save(reviewAction);
    }

    private User getReviewerUser(AuthenticatedUser reviewer) {
        return userRepository.findById(reviewer.id())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "reviewer: Not found"));
    }

    private Map<String, Object> buildFieldMap(JobPosting job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", job.getTitle());
        map.put("description", job.getDescription());
        map.put("status", job.getStatus());
        map.put("category", job.getCategory().getName());
        map.put("location", job.getLocation().getCity() + ", " + job.getLocation().getState());
        map.put("payAmount", job.getPayAmount());
        map.put("payType", job.getPayType());
        map.put("settlementType", job.getSettlementType());
        map.put("headcount", job.getHeadcount());
        map.put("weeklyHours", job.getWeeklyHours());
        map.put("validityStart", job.getValidityStart());
        map.put("validityEnd", job.getValidityEnd());
        map.put("tags", job.getTags().stream().map(tag -> tag.getTagName()).collect(Collectors.toList()));
        return map;
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.joining(", "));
        }
        return value.toString();
    }

    private ReviewActionResponse toActionResponse(ReviewAction action) {
        return new ReviewActionResponse(
            action.getId(),
            action.getJobPosting().getId(),
            action.getJobPosting().getTitle(),
            action.getAction(),
            action.getRationale(),
            action.getCreatedAt()
        );
    }

    private void ensureReviewer(AuthenticatedUser user) {
        if (user.role() != UserRole.REVIEWER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "review: Reviewers only");
        }
    }
}
