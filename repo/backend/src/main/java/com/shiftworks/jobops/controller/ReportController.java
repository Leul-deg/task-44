package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.ReportExportResponse;
import com.shiftworks.jobops.dto.ScheduledReportRequest;
import com.shiftworks.jobops.dto.ScheduledReportResponse;
import com.shiftworks.jobops.dto.ScheduledReportUpdateRequest;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.ScheduledReportService;
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
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
public class ReportController {

    private final ScheduledReportService service;

    @PostMapping("/scheduled")
    public ScheduledReportResponse create(@Valid @RequestBody ScheduledReportRequest request, Authentication authentication) {
        return service.create(request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping("/scheduled")
    public List<ScheduledReportResponse> list(Authentication authentication) {
        return service.list((AuthenticatedUser) authentication.getPrincipal());
    }

    @PutMapping("/scheduled/{id}")
    public ScheduledReportResponse update(@PathVariable Long id,
                                          @Valid @RequestBody ScheduledReportUpdateRequest request,
                                          Authentication authentication) {
        return service.update(id, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @DeleteMapping("/scheduled/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping("/exports")
    public List<ReportExportResponse> exports(Authentication authentication) {
        return service.listExports((AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping("/exports/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, Authentication authentication) {
        byte[] data = service.downloadExport(id, (AuthenticatedUser) authentication.getPrincipal());
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.csv")
            .body(data);
    }
}
