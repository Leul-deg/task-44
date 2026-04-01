package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.AppealCreateRequest;
import com.shiftworks.jobops.dto.AppealDetailResponse;
import com.shiftworks.jobops.dto.AppealProcessRequest;
import com.shiftworks.jobops.dto.AppealResponse;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.AppealService;
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

@RestController
@RequestMapping("/api/appeals")
@RequiredArgsConstructor
public class AppealController {

    private final AppealService appealService;

    @GetMapping
    public PageResponse<AppealResponse> list(Authentication authentication,
                                             @RequestParam(value = "status", required = false) String status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return appealService.listAppeals(user, status, page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    public void create(Authentication authentication, @Valid @RequestBody AppealCreateRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        appealService.createAppeal(request, user);
    }

    @GetMapping("/{id}")
    public AppealDetailResponse detail(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return appealService.getAppeal(id, user);
    }

    @PostMapping("/{id}/process")
    @PreAuthorize("hasRole('REVIEWER')")
    public void process(Authentication authentication, @PathVariable Long id, @Valid @RequestBody AppealProcessRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        appealService.processAppeal(id, request, user);
    }
}
