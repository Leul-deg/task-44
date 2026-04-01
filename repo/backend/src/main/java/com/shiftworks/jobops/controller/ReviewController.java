package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.FieldDiff;
import com.shiftworks.jobops.dto.JobPostingResponse;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.dto.ReviewActionResponse;
import com.shiftworks.jobops.dto.ReviewDashboardResponse;
import com.shiftworks.jobops.dto.ReviewRationaleRequest;
import com.shiftworks.jobops.dto.ReviewRejectRequest;
import com.shiftworks.jobops.dto.TakedownRequest;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
@PreAuthorize("hasRole('REVIEWER')")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/dashboard")
    public ReviewDashboardResponse dashboard(Authentication authentication) {
        AuthenticatedUser reviewer = (AuthenticatedUser) authentication.getPrincipal();
        return reviewService.dashboard(reviewer);
    }

    @GetMapping("/queue")
    public PageResponse<JobPostingResponse> queue(Authentication authentication,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        AuthenticatedUser reviewer = (AuthenticatedUser) authentication.getPrincipal();
        return reviewService.getQueue(reviewer, page, size);
    }

    @GetMapping("/jobs/{id}")
    public JobPostingResponse job(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser reviewer = (AuthenticatedUser) authentication.getPrincipal();
        return reviewService.getJob(id, reviewer);
    }

    @GetMapping("/jobs/{id}/diff")
    public Map<String, FieldDiff> diff(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser reviewer = (AuthenticatedUser) authentication.getPrincipal();
        return reviewService.diff(id, reviewer);
    }

    @GetMapping("/jobs/{id}/actions")
    public List<ReviewActionResponse> actions(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser reviewer = (AuthenticatedUser) authentication.getPrincipal();
        return reviewService.actionsForJob(id, reviewer);
    }

    @PostMapping("/jobs/{id}/approve")
    public void approve(Authentication authentication, @PathVariable Long id, @Valid @RequestBody ReviewRationaleRequest request) {
        AuthenticatedUser reviewer = (AuthenticatedUser) authentication.getPrincipal();
        reviewService.approve(id, request, reviewer);
    }

    @PostMapping("/jobs/{id}/reject")
    public void reject(Authentication authentication, @PathVariable Long id, @Valid @RequestBody ReviewRejectRequest request) {
        AuthenticatedUser reviewer = (AuthenticatedUser) authentication.getPrincipal();
        reviewService.reject(id, request, reviewer);
    }

    @PostMapping("/jobs/{id}/takedown")
    public void takedown(Authentication authentication, @PathVariable Long id, @Valid @RequestBody TakedownRequest request) {
        AuthenticatedUser reviewer = (AuthenticatedUser) authentication.getPrincipal();
        reviewService.takedown(id, request, reviewer);
    }
}
