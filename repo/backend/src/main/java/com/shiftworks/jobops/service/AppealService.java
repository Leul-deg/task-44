package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.AppealCreateRequest;
import com.shiftworks.jobops.dto.AppealDetailResponse;
import com.shiftworks.jobops.dto.AppealProcessRequest;
import com.shiftworks.jobops.dto.AppealResponse;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.entity.Appeal;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.ReviewAction;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.AppealStatus;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.ReviewActionType;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.AppealRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.ReviewActionRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppealService {

    private final AppealRepository appealRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ReviewActionRepository reviewActionRepository;
    private final UserRepository userRepository;
    private final JobHistoryService jobHistoryService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<AppealResponse> listAppeals(AuthenticatedUser user, String statusValue, int page, int size) {
        if (user.role() != UserRole.REVIEWER && user.role() != UserRole.EMPLOYER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "appeal: Access denied");
        }
        final String effectiveStatus;
        if (user.role() == UserRole.REVIEWER && (statusValue == null || statusValue.isBlank())) {
            effectiveStatus = AppealStatus.PENDING.name();
        } else {
            effectiveStatus = statusValue;
        }
        AppealStatus parsedStatus = null;
        if (effectiveStatus != null && !effectiveStatus.isBlank()) {
            parsedStatus = parseStatus(effectiveStatus);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        AppealStatus finalParsedStatus = parsedStatus;
        Specification<Appeal> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (user.role() == UserRole.EMPLOYER) {
                predicate = cb.and(predicate, cb.equal(root.get("employer").get("id"), user.id()));
            }
            if (finalParsedStatus != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), finalParsedStatus));
            }
            return predicate;
        };
        Page<Appeal> result = appealRepository.findAll(spec, pageable);
        List<AppealResponse> items = result.getContent().stream()
            .map(this::toResponse)
            .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size, result.getTotalPages());
    }

    @Transactional
    public void createAppeal(AppealCreateRequest request, AuthenticatedUser employer) {
        log.info("Appeal created for jobId={} by employer={}", request.jobPostingId(), employer.username());
        if (employer.role() != UserRole.EMPLOYER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "appeal: Employers only");
        }
        JobPosting jobPosting = jobPostingRepository.findByIdAndEmployer_Id(request.jobPostingId(), employer.id())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "jobPosting: Not found"));
        if (jobPosting.getStatus() != JobStatus.TAKEN_DOWN) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "jobPosting: Only taken down postings can be appealed");
        }
        if (appealRepository.existsByJobPosting_IdAndStatus(jobPosting.getId(), AppealStatus.PENDING)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "appeal: Pending appeal already exists");
        }
        Appeal appeal = new Appeal();
        appeal.setJobPosting(jobPosting);
        appeal.setEmployer(jobPosting.getEmployer());
        appeal.setAppealReason(request.appealReason());
        appeal.setStatus(AppealStatus.PENDING);
        appealRepository.save(appeal);

        jobPosting.setStatus(JobStatus.APPEAL_PENDING);
        jobPostingRepository.save(jobPosting);
        jobHistoryService.record(jobPosting, JobStatus.TAKEN_DOWN, JobStatus.APPEAL_PENDING, jobPosting.getEmployer(), request.appealReason());
        auditService.log(employer.id(), "APPEAL_CREATED", "APPEAL", appeal.getId(), null, appeal);
    }

    @Transactional(readOnly = true)
    public AppealDetailResponse getAppeal(Long id, AuthenticatedUser user) {
        if (user.role() != UserRole.REVIEWER && user.role() != UserRole.EMPLOYER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "appeal: Access denied");
        }
        Appeal appeal = appealRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "appeal: Not found"));
        if (user.role() == UserRole.EMPLOYER && !appeal.getEmployer().getId().equals(user.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "appeal: Access denied");
        }
        ReviewAction takedown = reviewActionRepository.findTop1ByJobPosting_IdAndActionOrderByCreatedAtDesc(appeal.getJobPosting().getId(), ReviewActionType.TAKEDOWN)
            .orElse(null);
        return toDetailResponse(appeal, takedown);
    }

    @Transactional
    public void processAppeal(Long id, AppealProcessRequest request, AuthenticatedUser reviewer) {
        log.info("Processing appeal id={} decision={} by reviewer={}", id, request.decision(), reviewer.username());
        if (reviewer.role() != UserRole.REVIEWER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "appeal: Reviewers only");
        }
        Appeal appeal = appealRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "appeal: Not found"));
        if (appeal.getStatus() != AppealStatus.PENDING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "appeal: Already processed");
        }
        JobPosting jobPosting = appeal.getJobPosting();
        User reviewerUser = userRepository.findById(reviewer.id())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "reviewer: Not found"));
        String decision = request.decision().toUpperCase(Locale.ROOT);
        switch (decision) {
            case "GRANTED" -> {
                appeal.setStatus(AppealStatus.GRANTED);
                jobPosting.setStatus(JobStatus.PUBLISHED);
                jobPosting.setPublishedAt(Instant.now());
                jobHistoryService.record(jobPosting, JobStatus.APPEAL_PENDING, JobStatus.PUBLISHED, reviewerUser, request.reviewerRationale());
            }
            case "DENIED" -> {
                appeal.setStatus(AppealStatus.DENIED);
                jobPosting.setStatus(JobStatus.TAKEN_DOWN);
                jobHistoryService.record(jobPosting, JobStatus.APPEAL_PENDING, JobStatus.TAKEN_DOWN, reviewerUser, request.reviewerRationale());
            }
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "decision: Must be GRANTED or DENIED");
        }
        appeal.setReviewerRationale(request.reviewerRationale());
        appeal.setUpdatedAt(Instant.now());
        appealRepository.save(appeal);
        jobPostingRepository.save(jobPosting);
        auditService.log(reviewer.id(), "APPEAL_PROCESSED", "APPEAL", id, null, appeal);
    }

    private AppealResponse toResponse(Appeal appeal) {
        return new AppealResponse(
            appeal.getId(),
            appeal.getJobPosting().getId(),
            appeal.getJobPosting().getTitle(),
            appeal.getEmployer().getUsername(),
            appeal.getAppealReason(),
            appeal.getStatus(),
            appeal.getCreatedAt()
        );
    }

    private AppealDetailResponse toDetailResponse(Appeal appeal, ReviewAction takedownAction) {
        JobPosting job = appeal.getJobPosting();
        String location = job.getLocation().getCity() + ", " + job.getLocation().getState();
        String paySummary = "$" + job.getPayAmount().setScale(2, RoundingMode.HALF_UP) + (job.getPayType().name().equals("HOURLY") ? "/hour" : " flat");
        return new AppealDetailResponse(
            appeal.getId(),
            appeal.getStatus(),
            appeal.getAppealReason(),
            appeal.getCreatedAt(),
            appeal.getEmployer().getUsername(),
            job.getTitle(),
            job.getCategory().getName(),
            location,
            paySummary,
            job.getTakedownReason(),
            takedownAction != null ? takedownAction.getRationale() : null,
            takedownAction != null ? takedownAction.getCreatedAt() : null,
            appeal.getReviewerRationale(),
            appeal.getUpdatedAt()
        );
    }

    private AppealStatus parseStatus(String statusValue) {
        try {
            return AppealStatus.valueOf(statusValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "status: Invalid appeal status");
        }
    }
}
