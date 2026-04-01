package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.dto.ReportExportResponse;
import com.shiftworks.jobops.dto.ScheduledReportRequest;
import com.shiftworks.jobops.dto.ScheduledReportResponse;
import com.shiftworks.jobops.dto.ScheduledReportUpdateRequest;
import com.shiftworks.jobops.entity.DashboardConfig;
import com.shiftworks.jobops.entity.ReportExport;
import com.shiftworks.jobops.entity.ScheduledReport;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.DashboardConfigRepository;
import com.shiftworks.jobops.repository.ReportExportRepository;
import com.shiftworks.jobops.repository.ScheduledReportRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledReportService {

    private final ScheduledReportRepository scheduledReportRepository;
    private final DashboardConfigRepository dashboardConfigRepository;
    private final ReportExportRepository reportExportRepository;
    private final UserRepository userRepository;
    private final DashboardService dashboardService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    public ScheduledReportResponse create(ScheduledReportRequest request, AuthenticatedUser authUser) {
        DashboardConfig config = dashboardConfigRepository.findById(request.dashboardConfigId())
            .filter(d -> d.getUser().getId().equals(authUser.id()) || authUser.role() == com.shiftworks.jobops.enums.UserRole.ADMIN)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "dashboard: Not found or not owner"));
        User user = userRepository.findById(authUser.id())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user: Not found"));
        Instant nextRun = nextRun(request.cronExpression(), Instant.now());
        ScheduledReport report = new ScheduledReport();
        report.setDashboardConfig(config);
        report.setUser(user);
        report.setCronExpression(request.cronExpression());
        report.setActive(true);
        report.setNextRunAt(nextRun);
        report.setCreatedAt(Instant.now());
        scheduledReportRepository.save(report);
        auditService.log(authUser.id(), "CREATE_SCHEDULE", "ScheduledReport", report.getId(), null, report);
        return toResponse(report);
    }

    public List<ScheduledReportResponse> list(AuthenticatedUser user) {
        return scheduledReportRepository.findByUser_Id(user.id()).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ScheduledReportResponse update(Long id, ScheduledReportUpdateRequest request, AuthenticatedUser user) {
        ScheduledReport report = scheduledReportRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "schedule: Not found"));
        if (!report.getUser().getId().equals(user.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "schedule: Not owner");
        }
        if (request.cronExpression() != null) {
            report.setCronExpression(request.cronExpression());
            report.setNextRunAt(nextRun(request.cronExpression(), Instant.now()));
        }
        if (request.isActive() != null) {
            report.setActive(request.isActive());
        }
        scheduledReportRepository.save(report);
        auditService.log(user.id(), "UPDATE_SCHEDULE", "ScheduledReport", report.getId(), null, report);
        return toResponse(report);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser user) {
        ScheduledReport report = scheduledReportRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "schedule: Not found"));
        if (!report.getUser().getId().equals(user.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "schedule: Not owner");
        }
        scheduledReportRepository.delete(report);
        auditService.log(user.id(), "DELETE_SCHEDULE", "ScheduledReport", report.getId(), report, null);
    }

    public List<ReportExportResponse> listExports(AuthenticatedUser user) {
        return reportExportRepository.findByUser_Id(user.id()).stream()
            .map(this::toExportResponse)
            .toList();
    }

    public byte[] downloadExport(Long id, AuthenticatedUser user) {
        ReportExport export = reportExportRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "export: Not found"));
        if (!export.getUser().getId().equals(user.id()) && user.role() != UserRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "export: Access denied");
        }
        Path path = Paths.get(appProperties.getStorage().getFilePath()).resolve(export.getFilePath());
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void executeDueReports() {
        List<ScheduledReport> due = scheduledReportRepository.findByActiveTrueAndNextRunAtLessThanEqual(Instant.now());
        for (ScheduledReport report : due) {
            Instant runAt = Instant.now();
            DashboardConfig config = report.getDashboardConfig();
            if (config == null) {
                log.warn("Scheduled report {} is missing its dashboard config", report.getId());
                report.setActive(false);
            } else {
                try {
                    List<Map<String, Object>> rows = dashboardService.execute(config);
                    if (!rows.isEmpty()) {
                        List<Map<String, Object>> maskedRows = dashboardService.maskRows(rows);
                        byte[] csv = dashboardService.toCsv(maskedRows);
                        String relativePath = String.format("reports/%d-%d.csv", report.getId(), runAt.toEpochMilli());
                        fileStorageService.save(relativePath, csv);
                        ReportExport export = new ReportExport();
                        export.setUser(report.getUser());
                        export.setDashboardConfig(config);
                        export.setFilePath(relativePath);
                        export.setFileSize(csv.length);
                        export.setMasked(true);
                        export.setCreatedAt(runAt);
                        reportExportRepository.save(export);
                    }
                } catch (Exception ex) {
                    log.error("Unable to execute scheduled report {}", report.getId(), ex);
                }
            }
            report.setLastRunAt(runAt);
            if (report.isActive()) {
                try {
                    report.setNextRunAt(nextRun(report.getCronExpression(), runAt));
                } catch (BusinessException ex) {
                    log.error("Disabling scheduled report {} due to cron error: {}", report.getId(), ex.getMessage());
                    report.setActive(false);
                    report.setNextRunAt(null);
                }
            } else {
                report.setNextRunAt(null);
            }
            scheduledReportRepository.save(report);
        }
    }

    private Instant nextRun(String cronExpression, Instant after) {
        try {
            CronExpression expr = CronExpression.parse(cronExpression);
            LocalDateTime afterLdt = LocalDateTime.ofInstant(after, ZoneId.systemDefault());
            LocalDateTime next = expr.next(afterLdt);
            if (next == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "cronExpression: Cannot determine next run");
            }
            return next.atZone(ZoneId.systemDefault()).toInstant();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "cronExpression: Invalid format");
        }
    }

    private ScheduledReportResponse toResponse(ScheduledReport report) {
        DashboardConfig config = report.getDashboardConfig();
        return new ScheduledReportResponse(
            report.getId(),
            config != null ? config.getId() : null,
            config != null ? config.getName() : null,
            report.getCronExpression(),
            report.isActive(),
            report.getLastRunAt(),
            report.getNextRunAt(),
            report.getUser().getId()
        );
    }

    private ReportExportResponse toExportResponse(ReportExport export) {
        DashboardConfig config = export.getDashboardConfig();
        return new ReportExportResponse(
            export.getId(),
            config != null ? config.getId() : null,
            config != null ? config.getName() : null,
            export.getCreatedAt(),
            export.getFileSize(),
            export.isMasked(),
            export.getUser().getId()
        );
    }
}
