package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.Alert;
import com.shiftworks.jobops.enums.AlertSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findBySeverity(AlertSeverity severity);

    List<Alert> findByReadFalse();

    long countByReadFalse();

    Page<Alert> findBySeverity(AlertSeverity severity, Pageable pageable);

    Page<Alert> findByReadFalse(Pageable pageable);

    Page<Alert> findBySeverityAndReadFalse(AlertSeverity severity, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Alert a WHERE a.alertType = :alertType AND a.createdAt >= :since")
    boolean existsByAlertTypeAndCreatedAtAfter(@Param("alertType") String alertType, @Param("since") Instant since);
}
