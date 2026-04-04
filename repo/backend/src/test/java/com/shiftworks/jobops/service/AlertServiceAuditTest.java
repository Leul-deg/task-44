package com.shiftworks.jobops.service;

import com.shiftworks.jobops.entity.Alert;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.AlertSeverity;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.repository.AlertRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertServiceAuditTest {

    @Mock private AlertRepository alertRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @InjectMocks private AlertService alertService;

    private AuthenticatedUser admin;
    private User adminUser;

    @BeforeEach
    void setup() {
        admin = new AuthenticatedUser(1L, "admin", UserRole.ADMIN);
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Alert buildAlert(Long id, boolean read) {
        Alert alert = new Alert();
        alert.setId(id);
        alert.setAlertType("ANOMALY");
        alert.setMetricName("login_failures");
        alert.setMessage("Too many failures");
        alert.setSeverity(AlertSeverity.WARNING);
        alert.setRead(read);
        return alert;
    }

    @Test
    void markReadAuditsWithActor() {
        Alert alert = buildAlert(10L, false);
        when(alertRepository.findById(10L)).thenReturn(Optional.of(alert));

        alertService.markRead(10L, admin);

        verify(auditService).log(
            eq(admin.id()),
            eq("ALERT_READ"),
            eq("ALERT"),
            eq(10L),
            isNotNull(),
            isNotNull()
        );
    }

    @Test
    void acknowledgeAuditsWithActor() {
        Alert alert = buildAlert(11L, false);
        when(alertRepository.findById(11L)).thenReturn(Optional.of(alert));

        alertService.acknowledge(11L, admin);

        verify(auditService).log(
            eq(admin.id()),
            eq("ALERT_ACKNOWLEDGED"),
            eq("ALERT"),
            eq(11L),
            isNotNull(),
            isNotNull()
        );
    }
}
