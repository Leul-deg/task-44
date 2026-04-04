package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.AppealCreateRequest;
import com.shiftworks.jobops.dto.AppealProcessRequest;
import com.shiftworks.jobops.entity.Appeal;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.AppealStatus;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.AppealRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppealServiceTest {

    @Mock private AppealRepository appealRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private ReviewActionRepository reviewActionRepository;
    @Mock private UserRepository userRepository;
    @Mock private JobHistoryService jobHistoryService;
    @Mock private AuditService auditService;
    @InjectMocks private AppealService appealService;

    private AuthenticatedUser employerUser;
    private AuthenticatedUser reviewerUser;
    private User employerEntity;
    private User reviewerEntity;

    @BeforeEach
    void setup() {
        employerUser = new AuthenticatedUser(1L, "employer1", UserRole.EMPLOYER);
        reviewerUser = new AuthenticatedUser(2L, "reviewer1", UserRole.REVIEWER);
        employerEntity = new User();
        employerEntity.setId(1L);
        employerEntity.setUsername("employer1");
        employerEntity.setRole(UserRole.EMPLOYER);
        reviewerEntity = new User();
        reviewerEntity.setId(2L);
        reviewerEntity.setUsername("reviewer1");
        reviewerEntity.setRole(UserRole.REVIEWER);
    }

    private JobPosting buildJob(JobStatus status) {
        JobPosting job = new JobPosting();
        job.setId(10L);
        job.setStatus(status);
        job.setEmployer(employerEntity);
        return job;
    }

    private Appeal buildAppeal(JobPosting job) {
        Appeal appeal = new Appeal();
        appeal.setId(100L);
        appeal.setJobPosting(job);
        appeal.setEmployer(employerEntity);
        appeal.setAppealReason("This was wrongly taken down and I want it reinstated");
        appeal.setStatus(AppealStatus.PENDING);
        return appeal;
    }

    @Test
    void createAppealSuccess() {
        JobPosting job = buildJob(JobStatus.TAKEN_DOWN);
        when(jobPostingRepository.findByIdAndEmployer_Id(10L, 1L)).thenReturn(Optional.of(job));
        when(appealRepository.existsByJobPosting_IdAndStatus(10L, AppealStatus.PENDING)).thenReturn(false);

        AppealCreateRequest request = new AppealCreateRequest(10L, "This was wrongly taken down and I want it reinstated");
        appealService.createAppeal(request, employerUser);

        verify(appealRepository).save(any(Appeal.class));
        assertEquals(JobStatus.APPEAL_PENDING, job.getStatus());
        verify(jobPostingRepository).save(job);
    }

    @Test
    void createAppealFailsIfNotTakenDown() {
        JobPosting job = buildJob(JobStatus.PUBLISHED);
        when(jobPostingRepository.findByIdAndEmployer_Id(10L, 1L)).thenReturn(Optional.of(job));

        AppealCreateRequest request = new AppealCreateRequest(10L, "This was wrongly taken down and I want it reinstated");
        var ex = assertThrows(BusinessException.class, () -> appealService.createAppeal(request, employerUser));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createAppealFailsIfPendingExists() {
        JobPosting job = buildJob(JobStatus.TAKEN_DOWN);
        when(jobPostingRepository.findByIdAndEmployer_Id(10L, 1L)).thenReturn(Optional.of(job));
        when(appealRepository.existsByJobPosting_IdAndStatus(10L, AppealStatus.PENDING)).thenReturn(true);

        AppealCreateRequest request = new AppealCreateRequest(10L, "This was wrongly taken down and I want it reinstated");
        var ex = assertThrows(BusinessException.class, () -> appealService.createAppeal(request, employerUser));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createAppealForbiddenForReviewer() {
        AppealCreateRequest request = new AppealCreateRequest(10L, "This was wrongly taken down and I want it reinstated");
        var ex = assertThrows(BusinessException.class, () -> appealService.createAppeal(request, reviewerUser));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void processAppealGranted() {
        JobPosting job = buildJob(JobStatus.APPEAL_PENDING);
        Appeal appeal = buildAppeal(job);
        when(appealRepository.findById(100L)).thenReturn(Optional.of(appeal));
        when(userRepository.findById(2L)).thenReturn(Optional.of(reviewerEntity));

        AppealProcessRequest request = new AppealProcessRequest("GRANTED", "The takedown was indeed wrong, reinstating");
        appealService.processAppeal(100L, request, reviewerUser);

        assertEquals(AppealStatus.GRANTED, appeal.getStatus());
        assertEquals(JobStatus.PUBLISHED, job.getStatus());
        verify(appealRepository).save(appeal);
    }

    @Test
    void processAppealDenied() {
        JobPosting job = buildJob(JobStatus.APPEAL_PENDING);
        Appeal appeal = buildAppeal(job);
        when(appealRepository.findById(100L)).thenReturn(Optional.of(appeal));
        when(userRepository.findById(2L)).thenReturn(Optional.of(reviewerEntity));

        AppealProcessRequest request = new AppealProcessRequest("DENIED", "The takedown was correct, violation confirmed");
        appealService.processAppeal(100L, request, reviewerUser);

        assertEquals(AppealStatus.DENIED, appeal.getStatus());
        assertEquals(JobStatus.TAKEN_DOWN, job.getStatus());
    }

    @Test
    void processAppealForbiddenForEmployer() {
        AppealProcessRequest request = new AppealProcessRequest("GRANTED", "The takedown was indeed wrong, reinstating");
        var ex = assertThrows(BusinessException.class, () -> appealService.processAppeal(100L, request, employerUser));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void processAlreadyProcessedAppealFails() {
        JobPosting job = buildJob(JobStatus.PUBLISHED);
        Appeal appeal = buildAppeal(job);
        appeal.setStatus(AppealStatus.GRANTED);
        when(appealRepository.findById(100L)).thenReturn(Optional.of(appeal));
        when(userRepository.findById(2L)).thenReturn(Optional.of(reviewerEntity));

        AppealProcessRequest request = new AppealProcessRequest("DENIED", "Trying to re-process this appeal after grant");
        var ex = assertThrows(BusinessException.class, () -> appealService.processAppeal(100L, request, reviewerUser));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void listAppealsWithInvalidStatusReturnsBadRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> appealService.listAppeals(reviewerUser, "INVALID_STATUS", 0, 10));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
