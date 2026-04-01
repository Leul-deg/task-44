package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.ReviewRationaleRequest;
import com.shiftworks.jobops.dto.ReviewRejectRequest;
import com.shiftworks.jobops.dto.TakedownRequest;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.JobStatus;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
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
}
