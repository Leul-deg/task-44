package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.dto.JobPostingHistoryResponse;
import com.shiftworks.jobops.dto.JobPostingPreviewResponse;
import com.shiftworks.jobops.dto.JobPostingRequest;
import com.shiftworks.jobops.dto.JobPostingResponse;
import com.shiftworks.jobops.dto.JobPostingSummaryResponse;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.dto.PublishJobRequest;
import com.shiftworks.jobops.entity.Category;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.JobPostingTag;
import com.shiftworks.jobops.entity.Location;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.PayType;
import com.shiftworks.jobops.enums.SettlementType;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.CategoryRepository;
import com.shiftworks.jobops.repository.JobPostingHistoryRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.LocationRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.util.PhoneUtils;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobPostingService {

    private static final Set<JobStatus> EMPLOYER_EDITABLE_STATUSES = Set.of(JobStatus.DRAFT, JobStatus.REJECTED);

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingHistoryRepository jobPostingHistoryRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final StepUpVerificationService stepUpVerificationService;
    private final JobHistoryService jobHistoryService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public PageResponse<JobPostingResponse> listJobs(AuthenticatedUser user, String statusValue, Long categoryId, Long locationId, String search, int page, int size, String sort, String direction) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sort, direction));
        Specification<JobPosting> specification = buildSpecification(user, statusValue, categoryId, locationId, search);
        Page<JobPosting> result = jobPostingRepository.findAll(specification, pageable);
        List<JobPostingResponse> responses = result.getContent().stream()
            .map(job -> toResponse(job, user))
            .toList();
        return new PageResponse<>(responses, result.getTotalElements(), page, size, result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getJob(Long id, AuthenticatedUser user) {
        JobPosting jobPosting = loadJobForAccess(id, user);
        return toResponse(jobPosting, user);
    }

    @Transactional
    public JobPostingResponse createJob(JobPostingRequest request, AuthenticatedUser user) {
        log.info("Creating job draft for employerId={}", user.id());
        User employer = userRepository.findById(user.id())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Employer not found"));
        JobPosting jobPosting = new JobPosting();
        jobPosting.setEmployer(employer);
        jobPosting.setStatus(JobStatus.DRAFT);
        applyRequest(jobPosting, request, true);
        JobPosting saved = jobPostingRepository.save(jobPosting);
        recordHistory(saved, null, JobStatus.DRAFT, employer, "Draft created");
        auditService.log(user.id(), "JOB_CREATED", "JOB_POSTING", saved.getId(), null, saved);
        return toResponse(saved, user);
    }

    @Transactional
    public JobPostingResponse updateJob(Long id, JobPostingRequest request, AuthenticatedUser user) {
        JobPosting jobPosting = loadJobForEmployer(id, user);
        if (!EMPLOYER_EDITABLE_STATUSES.contains(jobPosting.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "jobPosting: Only draft or rejected postings can be edited");
        }
        applyRequest(jobPosting, request, true);
        JobStatus previousStatus = jobPosting.getStatus();
        if (previousStatus == JobStatus.REJECTED) {
            jobPosting.setStatus(JobStatus.DRAFT);
            recordHistory(jobPosting, previousStatus, JobStatus.DRAFT, jobPosting.getEmployer(), "Employer updated after rejection");
        } else {
            recordHistory(jobPosting, JobStatus.DRAFT, JobStatus.DRAFT, jobPosting.getEmployer(), "Employer updated draft");
        }
        jobPosting.setUpdatedAt(Instant.now());
        JobPosting saved = jobPostingRepository.save(jobPosting);
        auditService.log(user.id(), "JOB_EDITED", "JOB_POSTING", saved.getId(), null, saved);
        return toResponse(saved, user);
    }

    @Transactional
    public void submitForReview(Long id, AuthenticatedUser user) {
        log.info("Submitting job id={} for review", id);
        JobPosting jobPosting = loadJobForEmployer(id, user);
        if (jobPosting.getStatus() != JobStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "status: Only drafts can be submitted");
        }
        validateExistingJob(jobPosting);
        validateDates(jobPosting.getValidityStart(), jobPosting.getValidityEnd(), true);
        jobPosting.setStatus(JobStatus.PENDING_REVIEW);
        jobPosting.setUpdatedAt(Instant.now());
        jobPostingRepository.save(jobPosting);
        recordHistory(jobPosting, JobStatus.DRAFT, JobStatus.PENDING_REVIEW, jobPosting.getEmployer(), "Employer submitted for review");
        auditService.log(user.id(), "JOB_SUBMITTED", "JOB_POSTING", id, null, null);
    }

    @Transactional
    public void publish(Long id, PublishJobRequest request, AuthenticatedUser user) {
        log.info("Publishing job id={}", id);
        JobPosting jobPosting = loadJobForEmployer(id, user);
        if (jobPosting.getStatus() != JobStatus.APPROVED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "status: Posting must be approved before publishing");
        }
        if (!stepUpVerificationService.verify(user.id(), request.stepUpPassword())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "stepUpPassword: Verification failed");
        }
        jobPosting.setStatus(JobStatus.PUBLISHED);
        jobPosting.setPublishedAt(Instant.now());
        jobPosting.setUpdatedAt(Instant.now());
        jobPostingRepository.save(jobPosting);
        recordHistory(jobPosting, JobStatus.APPROVED, JobStatus.PUBLISHED, jobPosting.getEmployer(), "Employer published job");
        auditService.log(user.id(), "JOB_PUBLISHED", "JOB_POSTING", id, null, null);
    }

    @Transactional
    public void unpublish(Long id, AuthenticatedUser user) {
        JobPosting jobPosting = loadJobForEmployer(id, user);
        if (jobPosting.getStatus() != JobStatus.PUBLISHED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "status: Only published jobs can be unpublished");
        }
        jobPosting.setStatus(JobStatus.UNPUBLISHED);
        jobPosting.setUpdatedAt(Instant.now());
        jobPostingRepository.save(jobPosting);
        recordHistory(jobPosting, JobStatus.PUBLISHED, JobStatus.UNPUBLISHED, jobPosting.getEmployer(), "Employer unpublished job");
        auditService.log(user.id(), "JOB_UNPUBLISHED", "JOB_POSTING", id, null, null);
    }

    @Transactional(readOnly = true)
    public JobPostingPreviewResponse preview(Long id, AuthenticatedUser user) {
        JobPosting jobPosting = loadJobForEmployer(id, user);
        return buildPreview(jobPosting);
    }

    @Transactional(readOnly = true)
    public List<JobPostingHistoryResponse> history(Long id, AuthenticatedUser user) {
        JobPosting jobPosting = loadJobForAccess(id, user);
        return jobPostingHistoryRepository.findByJobPosting_IdOrderByCreatedAtDesc(jobPosting.getId())
            .stream()
            .map(entry -> new JobPostingHistoryResponse(
                entry.getPreviousStatus() != null ? entry.getPreviousStatus().name() : null,
                entry.getNewStatus().name(),
                entry.getChangedBy() != null ? entry.getChangedBy().getUsername() : "system",
                entry.getChangeReason(),
                entry.getCreatedAt()))
            .toList();
    }

    @Transactional(readOnly = true)
    public JobPostingSummaryResponse summary(AuthenticatedUser user) {
        if (user.role() != UserRole.EMPLOYER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "summary: Only employers can view this resource");
        }
        long total = jobPostingRepository.countByEmployer_Id(user.id());
        long published = jobPostingRepository.countByEmployer_IdAndStatus(user.id(), JobStatus.PUBLISHED);
        long pending = jobPostingRepository.countByEmployer_IdAndStatus(user.id(), JobStatus.PENDING_REVIEW);
        long rejected = jobPostingRepository.countByEmployer_IdAndStatus(user.id(), JobStatus.REJECTED);
        List<JobPostingResponse> recent = jobPostingRepository.findTop5ByEmployer_IdOrderByCreatedAtDesc(user.id()).stream()
            .map(job -> toResponse(job, user))
            .toList();
        return new JobPostingSummaryResponse(total, published, pending, rejected, recent);
    }

    private Specification<JobPosting> buildSpecification(AuthenticatedUser user, String statusValue, Long categoryId, Long locationId, String search) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            if (user.role() == UserRole.EMPLOYER) {
                predicates.add(cb.equal(root.get("employer").get("id"), user.id()));
            } else if (user.role() == UserRole.REVIEWER) {
                predicates.add(root.get("status").in(List.of(JobStatus.PENDING_REVIEW, JobStatus.PUBLISHED)));
            }
            if (statusValue != null && !statusValue.isBlank()) {
                JobStatus status = parseStatus(statusValue);
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (locationId != null) {
                predicates.add(cb.equal(root.get("location").get("id"), locationId));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort buildSort(String sort, String direction) {
        String sortField = (sort == null || sort.isBlank()) ? "createdAt" : sort;
        if (!List.of("createdAt", "payAmount").contains(sortField)) {
            sortField = "createdAt";
        }
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, sortField);
    }

    private void applyRequest(JobPosting jobPosting, JobPostingRequest request, boolean allowDefaultValidity) {
        validateBaseRequest(request);
        Category category = categoryRepository.findById(request.categoryId())
            .filter(Category::isActive)
            .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "categoryId: Category is inactive or missing"));
        Location location = locationRepository.findById(request.locationId())
            .filter(Location::isActive)
            .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "locationId: Location is inactive or missing"));
        jobPosting.setTitle(request.title().trim());
        jobPosting.setDescription(request.description().trim());
        jobPosting.setCategory(category);
        jobPosting.setLocation(location);
        jobPosting.setPayType(request.payType());
        jobPosting.setSettlementType(request.settlementType());
        jobPosting.setPayAmount(request.payAmount().setScale(2, RoundingMode.HALF_UP));
        jobPosting.setHeadcount(request.headcount());
        jobPosting.setWeeklyHours(request.weeklyHours().setScale(1, RoundingMode.HALF_UP));
        jobPosting.setContactPhone(request.contactPhone());
        LocalDate startDate = request.validityStart() != null ? request.validityStart() : LocalDate.now();
        LocalDate defaultEnd = startDate.plusDays(appProperties.getJobValidation().getValidityDays().getDefaultDays());
        LocalDate endDate = request.validityEnd() != null ? request.validityEnd() : defaultEnd;
        if (!allowDefaultValidity && request.validityEnd() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validityEnd: Validity end date is required");
        }
        validateDates(startDate, endDate, false);
        jobPosting.setValidityStart(startDate);
        jobPosting.setValidityEnd(endDate);
        applyTags(jobPosting, request.tags());
    }

    private void applyTags(JobPosting jobPosting, List<String> tags) {
        jobPosting.getTags().clear();
        if (tags == null) {
            return;
        }
        if (tags.size() > 10) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "tags: Maximum of 10 tags allowed");
        }
        tags.stream()
            .map(tag -> tag == null ? "" : tag.trim())
            .filter(tag -> !tag.isEmpty())
            .map(tag -> {
                if (tag.length() > 30) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "tags: Tag '" + tag + "' exceeds 30 characters");
                }
                return tag;
            })
            .forEach(tag -> {
                JobPostingTag tagEntity = new JobPostingTag();
                tagEntity.setJobPosting(jobPosting);
                tagEntity.setTagName(tag);
                jobPosting.getTags().add(tagEntity);
            });
    }

    private void validateBaseRequest(JobPostingRequest request) {
        if (request.title() == null || request.title().trim().length() < 5 || request.title().trim().length() > 200) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "title: Title must be between 5 and 200 characters");
        }
        if (request.description() == null || request.description().trim().length() < 20 || request.description().trim().length() > 5000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "description: Description must be between 20 and 5000 characters");
        }
        var jv = appProperties.getJobValidation();
        if (request.payType() == PayType.HOURLY) {
            validateRange("payAmount", request.payAmount(),
                BigDecimal.valueOf(jv.getHourlyPay().getMin()),
                BigDecimal.valueOf(jv.getHourlyPay().getMax()));
        } else {
            validateRange("payAmount", request.payAmount(),
                BigDecimal.valueOf(jv.getFlatPay().getMin()),
                BigDecimal.valueOf(jv.getFlatPay().getMax()));
        }
        validateIntRange("headcount", request.headcount(),
            jv.getHeadcount().getMin(), jv.getHeadcount().getMax());
        validateBigDecimalRange("weeklyHours", request.weeklyHours(),
            BigDecimal.valueOf(jv.getWeeklyHours().getMin()),
            BigDecimal.valueOf(jv.getWeeklyHours().getMax()));
        if (!PhoneUtils.isValidUsPhone(request.contactPhone())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "contactPhone: Must be a valid US phone number");
        }
    }

    private void validateExistingJob(JobPosting job) {
        if (job.getTitle() == null || job.getTitle().trim().length() < 5 || job.getTitle().trim().length() > 200) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "title: Title must be between 5 and 200 characters");
        }
        if (job.getDescription() == null || job.getDescription().trim().length() < 20 || job.getDescription().trim().length() > 5000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "description: Description must be between 20 and 5000 characters");
        }
        if (job.getCategory() == null || !job.getCategory().isActive()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "categoryId: Category is inactive or missing");
        }
        if (job.getLocation() == null || !job.getLocation().isActive()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "locationId: Location is inactive or missing");
        }
        var jv = appProperties.getJobValidation();
        BigDecimal pay = job.getPayAmount();
        if (job.getPayType() == PayType.HOURLY) {
            validateRange("payAmount", pay,
                BigDecimal.valueOf(jv.getHourlyPay().getMin()),
                BigDecimal.valueOf(jv.getHourlyPay().getMax()));
        } else {
            validateRange("payAmount", pay,
                BigDecimal.valueOf(jv.getFlatPay().getMin()),
                BigDecimal.valueOf(jv.getFlatPay().getMax()));
        }
        validateIntRange("headcount", job.getHeadcount(),
            jv.getHeadcount().getMin(), jv.getHeadcount().getMax());
        validateBigDecimalRange("weeklyHours", job.getWeeklyHours(),
            BigDecimal.valueOf(jv.getWeeklyHours().getMin()),
            BigDecimal.valueOf(jv.getWeeklyHours().getMax()));
    }

    private void validateDates(LocalDate start, LocalDate end, boolean requireEnd) {
        if (start == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validityStart: Start date is required");
        }
        if (end == null) {
            if (requireEnd) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "validityEnd: End date is required");
            }
            return;
        }
        if (end.isBefore(LocalDate.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validityEnd: End date must be today or later");
        }
        if (end.isBefore(start)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validityEnd: End date must be after start date");
        }
        LocalDate maxEnd = start.plusDays(appProperties.getJobValidation().getValidityDays().getMax());
        if (end.isAfter(maxEnd)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validityEnd: End date cannot exceed 90 days from start");
        }
    }

    private void validateRange(String field, BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, field + ": Value must be between " + min + " and " + max);
        }
    }

    private void validateIntRange(String field, Integer value, int min, int max) {
        if (value == null || value < min || value > max) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, field + ": Value must be between " + min + " and " + max);
        }
    }

    private void validateBigDecimalRange(String field, BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, field + ": Value must be between " + min + " and " + max);
        }
    }

    private JobPosting loadJobForEmployer(Long id, AuthenticatedUser user) {
        return jobPostingRepository.findByIdAndEmployer_Id(id, user.id())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "jobPosting: Not found"));
    }

    private JobPosting loadJobForAccess(Long id, AuthenticatedUser user) {
        JobPosting jobPosting = jobPostingRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "jobPosting: Not found"));
        if (user.role() == UserRole.ADMIN || Objects.equals(jobPosting.getEmployer().getId(), user.id())) {
            return jobPosting;
        }
        if (user.role() == UserRole.REVIEWER) {
            if (jobPosting.getStatus() == JobStatus.PENDING_REVIEW || jobPosting.getStatus() == JobStatus.PUBLISHED || jobPosting.getStatus() == JobStatus.APPEAL_PENDING) {
                return jobPosting;
            }
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "jobPosting: Access denied");
    }

    private void recordHistory(JobPosting jobPosting, JobStatus previousStatus, JobStatus newStatus, User actor, String reason) {
        jobHistoryService.record(jobPosting, previousStatus, newStatus, actor, reason);
    }

    public JobPostingResponse toResponse(JobPosting jobPosting, AuthenticatedUser user) {
        boolean revealPhone = Objects.equals(jobPosting.getEmployer().getId(), user.id());
        return new JobPostingResponse(
            jobPosting.getId(),
            jobPosting.getTitle(),
            jobPosting.getDescription(),
            jobPosting.getStatus(),
            jobPosting.getCategory().getId(),
            jobPosting.getCategory().getName(),
            jobPosting.getLocation().getId(),
            jobPosting.getLocation().getState(),
            jobPosting.getLocation().getCity(),
            jobPosting.getPayType(),
            jobPosting.getSettlementType(),
            jobPosting.getPayAmount(),
            jobPosting.getHeadcount(),
            jobPosting.getWeeklyHours(),
            revealPhone ? jobPosting.getContactPhone() : null,
            PhoneUtils.mask(jobPosting.getContactPhone()),
            jobPosting.getTags().stream().map(JobPostingTag::getTagName).toList(),
            jobPosting.getValidityStart(),
            jobPosting.getValidityEnd(),
            jobPosting.getPublishedAt(),
            jobPosting.getCreatedAt(),
            jobPosting.getUpdatedAt(),
            jobPosting.getEmployer().getId(),
            jobPosting.getEmployer().getUsername(),
            jobPosting.getReviewerNotes(),
            jobPosting.getTakedownReason()
        );
    }

    private JobPostingPreviewResponse buildPreview(JobPosting jobPosting) {
        String locationLabel = jobPosting.getLocation().getCity() + ", " + jobPosting.getLocation().getState();
        String paySummary = jobPosting.getPayAmount().setScale(2, RoundingMode.HALF_UP).toString();
        if (jobPosting.getPayType() == PayType.HOURLY) {
            paySummary = "$" + paySummary + "/hour";
        } else {
            paySummary = "$" + paySummary + " flat";
        }
        String settlementSummary = jobPosting.getSettlementType() == SettlementType.WEEKLY ? "Weekly" : "End of shift";
        return new JobPostingPreviewResponse(
            jobPosting.getTitle(),
            jobPosting.getDescription(),
            jobPosting.getCategory().getName(),
            locationLabel,
            paySummary,
            settlementSummary,
            jobPosting.getHeadcount(),
            jobPosting.getWeeklyHours().stripTrailingZeros().toPlainString(),
            jobPosting.getValidityStart(),
            jobPosting.getValidityEnd(),
            PhoneUtils.mask(jobPosting.getContactPhone()),
            jobPosting.getTags().stream().map(JobPostingTag::getTagName).toList()
        );
    }

    private JobStatus parseStatus(String statusValue) {
        try {
            return JobStatus.valueOf(statusValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "status: Unknown job status");
        }
    }
}
