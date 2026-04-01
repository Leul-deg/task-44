package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.dto.ScheduledReportRequest;
import com.shiftworks.jobops.dto.ScheduledReportUpdateRequest;
import com.shiftworks.jobops.entity.DashboardConfig;
import com.shiftworks.jobops.entity.ScheduledReport;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.DashboardConfigRepository;
import com.shiftworks.jobops.repository.ReportExportRepository;
import com.shiftworks.jobops.repository.ScheduledReportRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledReportServiceTest {

    @Mock private ScheduledReportRepository scheduledReportRepository;
    @Mock private DashboardConfigRepository dashboardConfigRepository;
    @Mock private ReportExportRepository reportExportRepository;
    @Mock private UserRepository userRepository;
    @Mock private DashboardService dashboardService;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditService auditService;
    @Mock private AppProperties appProperties;
    @InjectMocks private ScheduledReportService scheduledReportService;

    private AuthenticatedUser ownerUser;
    private AuthenticatedUser otherUser;
    private User ownerEntity;
    private DashboardConfig dashboardConfig;

    @BeforeEach
    void setup() {
        ownerUser = new AuthenticatedUser(1L, "owner", UserRole.EMPLOYER);
        otherUser = new AuthenticatedUser(2L, "other", UserRole.EMPLOYER);
        ownerEntity = new User();
        ownerEntity.setId(1L);
        ownerEntity.setUsername("owner");
        dashboardConfig = new DashboardConfig();
        dashboardConfig.setId(5L);
        dashboardConfig.setName("My Dashboard");
    }

    private ScheduledReport buildReport() {
        ScheduledReport report = new ScheduledReport();
        report.setId(20L);
        report.setDashboardConfig(dashboardConfig);
        report.setUser(ownerEntity);
        report.setCronExpression("0 0 2 * * *");
        report.setActive(true);
        report.setNextRunAt(Instant.now().minusSeconds(60));
        report.setCreatedAt(Instant.now());
        return report;
    }

    @Test
    void createScheduledReportSuccess() {
        when(dashboardConfigRepository.findById(5L)).thenReturn(Optional.of(dashboardConfig));
        when(userRepository.findById(1L)).thenReturn(Optional.of(ownerEntity));
        when(scheduledReportRepository.save(any())).thenAnswer(inv -> {
            ScheduledReport r = inv.getArgument(0);
            r.setId(20L);
            return r;
        });

        ScheduledReportRequest request = new ScheduledReportRequest(5L, "0 0 2 * * *");
        var response = scheduledReportService.create(request, ownerUser);

        assertNotNull(response);
        verify(scheduledReportRepository).save(any());
    }

    @Test
    void createWithInvalidCronFails() {
        when(dashboardConfigRepository.findById(5L)).thenReturn(Optional.of(dashboardConfig));
        when(userRepository.findById(1L)).thenReturn(Optional.of(ownerEntity));

        ScheduledReportRequest request = new ScheduledReportRequest(5L, "not-a-cron");
        var ex = assertThrows(BusinessException.class,
                () -> scheduledReportService.create(request, ownerUser));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void updateByNonOwnerFails() {
        ScheduledReport report = buildReport();
        when(scheduledReportRepository.findById(20L)).thenReturn(Optional.of(report));

        ScheduledReportUpdateRequest request = new ScheduledReportUpdateRequest("0 0 3 * * *", null);
        var ex = assertThrows(BusinessException.class,
                () -> scheduledReportService.update(20L, request, otherUser));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void deleteByNonOwnerFails() {
        ScheduledReport report = buildReport();
        when(scheduledReportRepository.findById(20L)).thenReturn(Optional.of(report));

        var ex = assertThrows(BusinessException.class,
                () -> scheduledReportService.delete(20L, otherUser));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void executeDueReportsGeneratesExport() {
        ScheduledReport report = buildReport();
        when(scheduledReportRepository.findByActiveTrueAndNextRunAtLessThanEqual(any()))
                .thenReturn(List.of(report));
        List<Map<String, Object>> rows = List.of(Map.of("dimension", "2025-01", "value", 42));
        when(dashboardService.execute(dashboardConfig)).thenReturn(rows);
        when(dashboardService.toCsv(rows)).thenReturn("dimension,value\n2025-01,42".getBytes());

        scheduledReportService.executeDueReports();

        verify(fileStorageService).save(any(), any());
        verify(reportExportRepository).save(any());
        verify(scheduledReportRepository).save(report);
        assertNotNull(report.getLastRunAt());
    }

    @Test
    void executeDueReportsSkipsEmptyResults() {
        ScheduledReport report = buildReport();
        when(scheduledReportRepository.findByActiveTrueAndNextRunAtLessThanEqual(any()))
                .thenReturn(List.of(report));
        when(dashboardService.execute(dashboardConfig)).thenReturn(List.of());

        scheduledReportService.executeDueReports();

        verify(fileStorageService, never()).save(any(), any());
        verify(reportExportRepository, never()).save(any());
    }
}
