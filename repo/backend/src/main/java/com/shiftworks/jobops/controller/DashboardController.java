package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.DashboardConfigResponse;
import com.shiftworks.jobops.dto.DashboardRequest;
import com.shiftworks.jobops.dto.DashboardPreviewRequest;
import com.shiftworks.jobops.dto.ExportRequest;
import com.shiftworks.jobops.service.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shiftworks.jobops.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @GetMapping
    public List<DashboardConfigResponse> list(Authentication authentication) {
        return service.list((AuthenticatedUser) authentication.getPrincipal());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @GetMapping("/{id}")
    public DashboardConfigResponse get(Authentication authentication, @PathVariable Long id) {
        return service.get(id, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping
    public DashboardConfigResponse create(Authentication authentication, @Valid @RequestBody DashboardRequest request) {
        return service.create(request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PutMapping("/{id}")
    public DashboardConfigResponse update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody DashboardRequest request) {
        return service.update(id, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @DeleteMapping("/{id}")
    public void delete(Authentication authentication, @PathVariable Long id) {
        service.delete(id, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @GetMapping("/{id}/data")
    public List<Map<String, Object>> data(Authentication authentication, @PathVariable Long id) {
        return service.data(id, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping("/preview")
    public List<Map<String, Object>> preview(@Valid @RequestBody DashboardPreviewRequest request) {
        return service.preview(request);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping("/{id}/export")
    public ResponseEntity<byte[]> export(Authentication authentication,
                                         @PathVariable Long id,
                                         @RequestParam(value = "masked", defaultValue = "true") boolean masked,
                                         @RequestBody(required = false) ExportRequest request) {
        byte[] csv = service.export(id, masked, request == null ? new ExportRequest(null) : request, (AuthenticatedUser) authentication.getPrincipal());
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dashboard.csv")
            .body(csv);
    }
}
