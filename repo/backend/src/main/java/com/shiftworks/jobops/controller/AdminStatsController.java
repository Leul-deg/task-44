package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.AdminStatsResponse;
import com.shiftworks.jobops.dto.PostStatusPoint;
import com.shiftworks.jobops.dto.PostVolumePoint;
import com.shiftworks.jobops.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/counts")
    public AdminStatsResponse counts() {
        return adminStatsService.counts();
    }

    @GetMapping("/post-volume")
    public List<PostVolumePoint> postVolume(@RequestParam(value = "days", defaultValue = "30") int days) {
        return adminStatsService.postVolume(days);
    }

    @GetMapping("/post-status")
    public List<PostStatusPoint> postStatus() {
        return adminStatsService.postStatus();
    }
}
