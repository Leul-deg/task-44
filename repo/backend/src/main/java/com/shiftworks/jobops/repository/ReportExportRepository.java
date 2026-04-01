package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.ReportExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportExportRepository extends JpaRepository<ReportExport, Long> {

    List<ReportExport> findByUser_Id(Long userId);
}
