package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.ApprovalRateResponse;
import com.shiftworks.jobops.dto.AverageHandlingTimeResponse;
import com.shiftworks.jobops.dto.ClaimSuccessRateResponse;
import com.shiftworks.jobops.dto.PostStatusPoint;
import com.shiftworks.jobops.dto.PostVolumePoint;
import com.shiftworks.jobops.dto.ReviewerActivityPoint;
import com.shiftworks.jobops.service.AnalyticsService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/post-volume")
    public List<PostVolumePoint> postVolume(
        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return analyticsService.postVolume(from, to);
    }

    @GetMapping("/post-status-distribution")
    public List<PostStatusPoint> postStatus(
        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return analyticsService.postStatusDistribution(from, to);
    }

    @GetMapping("/claim-success-rate")
    @PreAuthorize("hasRole('ADMIN')")
    public ClaimSuccessRateResponse claimSuccess(
        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return analyticsService.claimSuccessRate(from, to);
    }

    @GetMapping("/avg-handling-time")
    public AverageHandlingTimeResponse avgHandlingTime(
        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return analyticsService.averageHandlingTime(from, to);
    }

    @GetMapping("/reviewer-activity")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReviewerActivityPoint> reviewerActivity(
        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return analyticsService.reviewerActivity(from, to);
    }

    @GetMapping("/approval-rate")
    public ApprovalRateResponse approvalRate(
        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return analyticsService.approvalRate(from, to);
    }

    @GetMapping("/takedown-trend")
    public List<PostVolumePoint> takedownTrend(
        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return analyticsService.takedownTrend(from, to);
    }
}
