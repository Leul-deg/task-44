package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.FieldDiff;
import com.shiftworks.jobops.dto.ReviewRationaleRequest;
import com.shiftworks.jobops.dto.ReviewRejectRequest;
import com.shiftworks.jobops.dto.TakedownRequest;
import com.shiftworks.jobops.entity.Category;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.JobPostingHistory;
import com.shiftworks.jobops.entity.Location;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.PayType;
import com.shiftworks.jobops.enums.SettlementType;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.ReviewActionRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private ReviewActionRepository reviewActionRepository;
    @Mock private UserRepository userRepository;
    @Mock private JobHistoryService jobHistoryService;
    @Mock private JobPostingService jobPostingService;
    @Mock private StepUpVerificationService stepUpVerificationService;
    @Mock private AuditService auditService;
    @InjectMocks private ReviewService reviewService;

    private AuthenticatedUser reviewerUser;
    private User reviewerEntity;

    @BeforeEach
    void setup() {
        reviewerUser = new AuthenticatedUser(1L, "reviewer", UserRole.REVIEWER);
        reviewerEntity = new User();
        reviewerEntity.setId(reviewerUser.id());
        reviewerEntity.setRole(UserRole.REVIEWER);
        when(userRepository.findById(reviewerUser.id())).thenReturn(Optional.of(reviewerEntity));
    }

    private JobPosting createJob(JobStatus status) {
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setStatus(status);
        return job;
    }

    @Test
    void approveFromPendingReview() {
        JobPosting job = createJob(JobStatus.PENDING_REVIEW);
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));
        reviewService.approve(job.getId(), new ReviewRationaleRequest("Valid rationale"), reviewerUser);
        assertEquals(JobStatus.APPROVED, job.getStatus());
    }

    @Test
    void rejectStoresNotes() {
        JobPosting job = createJob(JobStatus.PENDING_REVIEW);
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));
        reviewService.reject(job.getId(), new ReviewRejectRequest("Valid rationale", "Needs work"), reviewerUser);
        assertEquals(JobStatus.REJECTED, job.getStatus());
        assertEquals("Needs work", job.getReviewerNotes());
    }

    @Test
    void takedownRequiresStepUp() {
        JobPosting job = createJob(JobStatus.PUBLISHED);
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(stepUpVerificationService.verify(eq(reviewerUser.id()), eq("bad"))).thenReturn(false);
        var ex = assertThrows(BusinessException.class, () -> reviewService.takedown(job.getId(), new TakedownRequest("Valid reason", "bad"), reviewerUser));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void takedownSucceedsWithValidStepUp() {
        JobPosting job = createJob(JobStatus.PUBLISHED);
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(stepUpVerificationService.verify(eq(reviewerUser.id()), eq("good"))).thenReturn(true);
        reviewService.takedown(job.getId(), new TakedownRequest("Valid reason", "good"), reviewerUser);
        assertEquals(JobStatus.TAKEN_DOWN, job.getStatus());
    }

    @Test
    void onlyReviewerCanAccess() {
        JobPosting job = createJob(JobStatus.PENDING_REVIEW);
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));
        AuthenticatedUser employerUser = new AuthenticatedUser(2L, "employer", UserRole.EMPLOYER);
        var ex = assertThrows(BusinessException.class, () -> reviewService.approve(job.getId(), new ReviewRationaleRequest("Valid rationale"), employerUser));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // ----- audit before/after tests -----

    @Test
    void approveAuditHasBeforeAndAfter() {
        JobPosting job = createJob(JobStatus.PENDING_REVIEW);
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));

        reviewService.approve(job.getId(), new ReviewRationaleRequest("Valid rationale"), reviewerUser);

        verify(auditService).log(
            eq(reviewerUser.id()),
            eq("JOB_APPROVED"),
            eq("JOB_POSTING"),
            eq(job.getId()),
            isNotNull(),
            isNotNull()
        );
    }

    @Test
    void rejectAuditHasBeforeAndAfter() {
        JobPosting job = createJob(JobStatus.PENDING_REVIEW);
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));

        reviewService.reject(job.getId(), new ReviewRejectRequest("Valid rationale", "Needs work"), reviewerUser);

        verify(auditService).log(
            eq(reviewerUser.id()),
            eq("JOB_REJECTED"),
            eq("JOB_POSTING"),
            eq(job.getId()),
            isNotNull(),
            isNotNull()
        );
    }

    @Test
    void takedownAuditHasBeforeAndAfter() {
        JobPosting job = createJob(JobStatus.PUBLISHED);
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(stepUpVerificationService.verify(eq(reviewerUser.id()), eq("goodpass"))).thenReturn(true);

        reviewService.takedown(job.getId(), new TakedownRequest("Valid reason here", "goodpass"), reviewerUser);

        verify(auditService).log(
            eq(reviewerUser.id()),
            eq("JOB_TAKEN_DOWN"),
            eq("JOB_POSTING"),
            eq(job.getId()),
            isNotNull(),
            isNotNull()
        );
    }

    // ----- diff() tests -----

    /** Builds a JobPosting with all fields required by buildFieldMap. */
    private JobPosting createJobWithFields(String title) {
        Category cat = new Category();
        cat.setName("Engineering");

        Location loc = new Location();
        loc.setCity("Austin");
        loc.setState("TX");

        JobPosting job = new JobPosting();
        job.setId(10L);
        job.setStatus(JobStatus.PENDING_REVIEW);
        job.setTitle(title);
        job.setDescription("Some description");
        job.setCategory(cat);
        job.setLocation(loc);
        job.setPayAmount(new BigDecimal("25.00"));
        job.setPayType(PayType.HOURLY);
        job.setSettlementType(SettlementType.WEEKLY);
        job.setHeadcount(2);
        job.setWeeklyHours(new BigDecimal("40.0"));
        job.setValidityStart(LocalDate.of(2026, 5, 1));
        job.setValidityEnd(LocalDate.of(2026, 7, 1));
        job.setTags(new ArrayList<>());
        return job;
    }

    /** Creates an old-snapshot map matching all fields in buildFieldMap. */
    private Map<String, Object> snapshotForTitle(String title) {
        Map<String, Object> snap = new HashMap<>();
        snap.put("title", title);
        snap.put("description", "Some description");
        snap.put("status", JobStatus.PENDING_REVIEW.name());
        snap.put("category", "Engineering");
        snap.put("location", "Austin, TX");
        snap.put("payAmount", "25.00");
        snap.put("payType", PayType.HOURLY.name());
        snap.put("settlementType", SettlementType.WEEKLY.name());
        snap.put("headcount", 2);
        snap.put("weeklyHours", "40.0");
        snap.put("validityStart", "2026-05-01");
        snap.put("validityEnd", "2026-07-01");
        snap.put("tags", new ArrayList<>());
        return snap;
    }

    @Test
    void diff_firstSubmission_returnsNull() {
        JobPosting job = createJobWithFields("Senior Dev");
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));
        // No prior submission exists
        when(jobHistoryService.findPreviousSubmissionSnapshot(job.getId())).thenReturn(null);

        Map<String, FieldDiff> result = reviewService.diff(job.getId(), reviewerUser);

        assertNull(result, "First submission should produce no diff");
    }

    @Test
    void diff_editedResubmission_showsChangedFields() {
        JobPosting job = createJobWithFields("Senior Dev (Updated)");
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));

        JobPostingHistory previousHistory = new JobPostingHistory();
        when(jobHistoryService.findPreviousSubmissionSnapshot(job.getId())).thenReturn(previousHistory);
        // Old snapshot had the original title
        when(jobHistoryService.readSnapshot(previousHistory)).thenReturn(snapshotForTitle("Senior Dev"));

        Map<String, FieldDiff> result = reviewService.diff(job.getId(), reviewerUser);

        assertNotNull(result, "Resubmission with changes should produce a diff");
        assertTrue(result.containsKey("title"), "Changed title should appear in diff");
        assertEquals("Senior Dev", result.get("title").oldValue());
        assertEquals("Senior Dev (Updated)", result.get("title").newValue());
    }

    @Test
    void diff_unchangedFields_omittedFromDiff() {
        JobPosting job = createJobWithFields("Same Title");
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));

        JobPostingHistory previousHistory = new JobPostingHistory();
        when(jobHistoryService.findPreviousSubmissionSnapshot(job.getId())).thenReturn(previousHistory);
        // Old snapshot has exactly the same values as the current posting
        when(jobHistoryService.readSnapshot(previousHistory)).thenReturn(snapshotForTitle("Same Title"));

        Map<String, FieldDiff> result = reviewService.diff(job.getId(), reviewerUser);

        assertNull(result, "Identical resubmission should produce no diff (null)");
    }

    @Test
    void diff_disallowedStatus_throwsBadRequest() {
        JobPosting job = createJob(JobStatus.DRAFT);
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> reviewService.diff(job.getId(), reviewerUser));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void actions_disallowedStatus_throwsBadRequest() {
        JobPosting job = createJob(JobStatus.REJECTED);
        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> reviewService.actionsForJob(job.getId(), reviewerUser));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
