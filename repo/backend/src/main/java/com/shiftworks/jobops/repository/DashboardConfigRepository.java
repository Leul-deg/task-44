package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.DashboardConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardConfigRepository extends JpaRepository<DashboardConfig, Long> {
    List<DashboardConfig> findByUser_Id(Long userId);
}
