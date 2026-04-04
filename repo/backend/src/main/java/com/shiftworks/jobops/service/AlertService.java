package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.AlertPageResponse;
import com.shiftworks.jobops.entity.Alert;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.AlertSeverity;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.AlertRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public Page<AlertPageResponse> list(AlertSeverity severity, Boolean isRead, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Alert> alerts;
        if (severity != null && Boolean.FALSE.equals(isRead)) {
            alerts = alertRepository.findBySeverityAndReadFalse(severity, pageable);
        } else if (severity != null) {
            alerts = alertRepository.findBySeverity(severity, pageable);
        } else if (Boolean.FALSE.equals(isRead)) {
            alerts = alertRepository.findByReadFalse(pageable);
        } else {
            alerts = alertRepository.findAll(pageable);
        }
        return alerts.map(this::toDto);
    }

    public long unreadCount() {
        return alertRepository.countByReadFalse();
    }

    @Transactional
    public void markRead(Long id, AuthenticatedUser user) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "alert: Not found"));
        boolean wasRead = alert.isRead();
        alert.setRead(true);
        alertRepository.save(alert);
        auditService.log(user.id(), "ALERT_READ", "ALERT", id,
            java.util.Map.of("read", wasRead),
            java.util.Map.of("read", true));
    }

    @Transactional
    public void acknowledge(Long id, AuthenticatedUser user) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "alert: Not found"));
        User ackUser = userRepository.findById(user.id())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user: Not found"));
        boolean wasRead = alert.isRead();
        alert.setAcknowledgedBy(ackUser);
        alert.setRead(true);
        alertRepository.save(alert);
        auditService.log(user.id(), "ALERT_ACKNOWLEDGED", "ALERT", id,
            java.util.Map.of("read", wasRead),
            java.util.Map.of("read", true, "acknowledgedBy", ackUser.getUsername()));
    }

    private AlertPageResponse toDto(Alert alert) {
        return new AlertPageResponse(
            alert.getId(),
            alert.getAlertType(),
            alert.getMetricName(),
            alert.getMessage(),
            alert.getSeverity(),
            alert.isRead(),
            alert.getAcknowledgedBy() != null ? alert.getAcknowledgedBy().getUsername() : null,
            alert.getCreatedAt()
        );
    }
}
