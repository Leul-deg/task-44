package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.BackupRecord;
import com.shiftworks.jobops.enums.BackupStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {

    List<BackupRecord> findByStatus(BackupStatus status);
}
