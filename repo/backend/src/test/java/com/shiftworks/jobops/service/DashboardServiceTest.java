package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.dto.DashboardRequest;
import com.shiftworks.jobops.dto.ExportRequest;
import com.shiftworks.jobops.entity.DashboardConfig;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.DashboardConfigRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private DashboardConfigRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private StepUpVerificationService stepUpVerificationService;
    @Mock private FileStorageService fileStorageService;
    @Mock private jakarta.persistence.EntityManager entityManager;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock private AppProperties properties;
    @InjectMocks private DashboardService dashboardService;

    private AuthenticatedUser employerUser;
    private AuthenticatedUser adminUser;
    private User employerEntity;

    @BeforeEach
    void setup() {
        employerUser = new AuthenticatedUser(1L, "employer1", UserRole.EMPLOYER);
        adminUser = new AuthenticatedUser(2L, "admin1", UserRole.ADMIN);
        employerEntity = new User();
        employerEntity.setId(1L);
        employerEntity.setUsername("employer1");
    }

    private DashboardConfig buildConfig(Long ownerId) {
        DashboardConfig config = new DashboardConfig();
        config.setId(10L);
        config.setName("My Dashboard");
        User owner = new User();
        owner.setId(ownerId);
        config.setUser(owner);
        return config;
    }

    @Test
    void createDashboardSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(employerEntity));
        when(repository.save(any())).thenAnswer(inv -> {
            DashboardConfig c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        DashboardRequest request = new DashboardRequest("Test Dashboard", null, null, null);
        var response = dashboardService.create(request, employerUser);

        assertNotNull(response);
        assertEquals("Test Dashboard", response.name());
        verify(repository).save(any());
        verify(auditService).log(eq(1L), eq("CREATE_DASHBOARD"), any(), any(), any(), any());
    }

    @Test
    void deleteDashboardByNonOwnerFails() {
        DashboardConfig config = buildConfig(99L);
        when(repository.findById(10L)).thenReturn(Optional.of(config));

        var ex = assertThrows(BusinessException.class,
                () -> dashboardService.delete(10L, employerUser));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void deleteDashboardByOwnerSucceeds() {
        DashboardConfig config = buildConfig(1L);
        when(repository.findById(10L)).thenReturn(Optional.of(config));

        dashboardService.delete(10L, employerUser);

        verify(repository).delete(config);
        verify(auditService).log(eq(1L), eq("DELETE_DASHBOARD"), any(), any(), any(), any());
    }

    @Test
    void updateDashboardByNonOwnerFails() {
        DashboardConfig config = buildConfig(99L);
        when(repository.findById(10L)).thenReturn(Optional.of(config));

        DashboardRequest request = new DashboardRequest("Updated", null, null, null);
        var ex = assertThrows(BusinessException.class,
                () -> dashboardService.update(10L, request, employerUser));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void exportUnmaskedForbiddenForNonAdmin() {
        DashboardConfig config = buildConfig(1L);
        when(repository.findById(10L)).thenReturn(Optional.of(config));

        ExportRequest request = new ExportRequest("password123");
        var ex = assertThrows(BusinessException.class,
                () -> dashboardService.export(10L, false, request, employerUser));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void exportUnmaskedRequiresStepUp() {
        DashboardConfig config = buildConfig(2L);
        when(repository.findById(10L)).thenReturn(Optional.of(config));
        when(stepUpVerificationService.verify(2L, "wrongpass")).thenReturn(false);

        ExportRequest request = new ExportRequest("wrongpass");
        var ex = assertThrows(BusinessException.class,
                () -> dashboardService.export(10L, false, request, adminUser));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }
}
