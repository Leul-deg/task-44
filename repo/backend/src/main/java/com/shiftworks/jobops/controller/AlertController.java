package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.AlertPageResponse;
import com.shiftworks.jobops.enums.AlertSeverity;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.AlertService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public Page<AlertPageResponse> list(@RequestParam(value = "severity", required = false) AlertSeverity severity,
                                        @RequestParam(value = "is_read", required = false) Boolean isRead,
                                        @RequestParam(value = "page", defaultValue = "0") int page,
                                        @RequestParam(value = "size", defaultValue = "10") int size) {
        return alertService.list(severity, isRead, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", alertService.unreadCount());
    }

    @PutMapping("/{id}/read")
    public void markRead(Authentication authentication, @PathVariable Long id) {
        alertService.markRead(id, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PutMapping("/{id}/acknowledge")
    public void acknowledge(Authentication authentication, @PathVariable Long id) {
        alertService.acknowledge(id, (AuthenticatedUser) authentication.getPrincipal());
    }
}
