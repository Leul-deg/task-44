package com.shiftworks.jobops.service;

import com.shiftworks.jobops.entity.Alert;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.AlertSeverity;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.AlertRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock private AlertRepository alertRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks private AlertService alertService;

    private Alert buildAlert(long id, boolean read) {
        Alert alert = new Alert();
        alert.setId(id);
        alert.setAlertType("ANOMALY");
        alert.setMetricName("postVolume");
        alert.setMessage("Spike detected");
        alert.setSeverity(AlertSeverity.HIGH);
        alert.setRead(read);
        alert.setCreatedAt(Instant.now());
        return alert;
    }

    private AuthenticatedUser admin() {
        return new AuthenticatedUser(1L, "admin", UserRole.ADMIN);
    }

    @Test
    void listAll_returnsPageOfAlerts() {
        Alert a1 = buildAlert(1L, false);
        Alert a2 = buildAlert(2L, true);
        when(alertRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a1, a2)));

        Page<?> result = alertService.list(null, null, 0, 10);

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void listWithSeverityFilter_callsCorrectRepository() {
        when(alertRepository.findBySeverity(eq(AlertSeverity.HIGH), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildAlert(1L, false))));

        Page<?> result = alertService.list(AlertSeverity.HIGH, null, 0, 10);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(alertRepository).findBySeverity(eq(AlertSeverity.HIGH), any(Pageable.class));
    }

    @Test
    void listUnread_callsFindByReadFalse() {
        when(alertRepository.findByReadFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildAlert(1L, false))));

        alertService.list(null, false, 0, 10);

        verify(alertRepository).findByReadFalse(any(Pageable.class));
    }

    @Test
    void listBySeverityAndUnread_callsCombinedQuery() {
        when(alertRepository.findBySeverityAndReadFalse(eq(AlertSeverity.HIGH), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        alertService.list(AlertSeverity.HIGH, false, 0, 10);

        verify(alertRepository).findBySeverityAndReadFalse(eq(AlertSeverity.HIGH), any(Pageable.class));
    }

    @Test
    void unreadCount_delegatesToRepository() {
        when(alertRepository.countByReadFalse()).thenReturn(9L);

        assertThat(alertService.unreadCount()).isEqualTo(9L);
    }

    @Test
    void markRead_setsReadTrueAndAudits() {
        Alert alert = buildAlert(5L, false);
        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenReturn(alert);

        alertService.markRead(5L, admin());

        assertThat(alert.isRead()).isTrue();
        verify(auditService).log(eq(1L), eq("ALERT_READ"), eq("ALERT"), eq(5L), any(), any());
    }

    @Test
    void markRead_throwsWhenAlertNotFound() {
        when(alertRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.markRead(99L, admin()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void acknowledge_setsReadAndAcknowledgedByAndAudits() {
        Alert alert = buildAlert(5L, false);
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(alertRepository.save(any(Alert.class))).thenReturn(alert);

        alertService.acknowledge(5L, admin());

        assertThat(alert.isRead()).isTrue();
        assertThat(alert.getAcknowledgedBy()).isEqualTo(user);
        verify(auditService).log(eq(1L), eq("ALERT_ACKNOWLEDGED"), eq("ALERT"), eq(5L), any(), any());
    }

    @Test
    void acknowledge_throwsWhenUserNotFound() {
        Alert alert = buildAlert(5L, false);
        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.acknowledge(5L, admin()))
                .isInstanceOf(BusinessException.class);
    }
}
