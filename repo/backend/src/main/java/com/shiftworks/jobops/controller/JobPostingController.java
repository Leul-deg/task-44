package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.JobPostingHistoryResponse;
import com.shiftworks.jobops.dto.JobPostingPreviewResponse;
import com.shiftworks.jobops.dto.JobPostingRequest;
import com.shiftworks.jobops.dto.JobPostingResponse;
import com.shiftworks.jobops.dto.JobPostingSummaryResponse;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.dto.PublishJobRequest;
import com.shiftworks.jobops.dto.StepUpPhoneRequest;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.JobPostingService;
import com.shiftworks.jobops.service.StepUpVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;
    private final JobPostingRepository jobPostingRepository;
    private final StepUpVerificationService stepUpVerificationService;

    @GetMapping
    public PageResponse<JobPostingResponse> list(Authentication authentication,
                                                 @RequestParam(value = "status", required = false) String status,
                                                 @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                 @RequestParam(value = "locationId", required = false) Long locationId,
                                                 @RequestParam(value = "search", required = false) String search,
                                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                                 @RequestParam(value = "size", defaultValue = "10") int size,
                                                 @RequestParam(value = "sort", defaultValue = "createdAt") String sort,
                                                 @RequestParam(value = "direction", defaultValue = "desc") String direction) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return jobPostingService.listJobs(user, status, categoryId, locationId, search, page, size, sort, direction);
    }

    @GetMapping("/summary")
    public JobPostingSummaryResponse summary(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return jobPostingService.summary(user);
    }

    @GetMapping("/{id}")
    public JobPostingResponse detail(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return jobPostingService.getJob(id, user);
    }

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    public JobPostingResponse create(Authentication authentication, @Valid @RequestBody JobPostingRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return jobPostingService.createJob(request, user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYER')")
    public JobPostingResponse update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody JobPostingRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return jobPostingService.updateJob(id, request, user);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('EMPLOYER')")
    public void submit(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        jobPostingService.submitForReview(id, user);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('EMPLOYER')")
    public void publish(Authentication authentication, @PathVariable Long id, @Valid @RequestBody PublishJobRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        jobPostingService.publish(id, request, user);
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasRole('EMPLOYER')")
    public void unpublish(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        jobPostingService.unpublish(id, user);
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasRole('EMPLOYER')")
    public JobPostingPreviewResponse preview(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return jobPostingService.preview(id, user);
    }

    @GetMapping("/{id}/history")
    public List<JobPostingHistoryResponse> history(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return jobPostingService.history(id, user);
    }

    @PostMapping("/{id}/contact-phone")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> getContactPhone(Authentication authentication,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody StepUpPhoneRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        if (!stepUpVerificationService.verify(user.id(), request.stepUpPassword())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "stepUpPassword: Verification failed");
        }
        JobPosting job = jobPostingRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "jobPosting: Not found"));
        return Map.of("contactPhone", job.getContactPhone() != null ? job.getContactPhone() : "");
    }
}
