package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.ScheduledReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ScheduledReportRepository extends JpaRepository<ScheduledReport, Long> {

    List<ScheduledReport> findByActiveTrue();
    List<ScheduledReport> findByUser_Id(Long userId);
    List<ScheduledReport> findByActiveTrueAndNextRunAtLessThanEqual(Instant now);
    void deleteByDashboardConfig_Id(Long dashboardConfigId);
}
