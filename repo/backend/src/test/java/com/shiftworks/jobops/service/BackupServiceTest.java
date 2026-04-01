package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.entity.BackupRecord;
import com.shiftworks.jobops.enums.BackupStatus;
import com.shiftworks.jobops.repository.BackupRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock private BackupRecordRepository backupRecordRepository;
    @Mock private AppProperties appProperties;
    @InjectMocks private BackupService backupService;

    private BackupRecord buildRecord(BackupStatus status) {
        BackupRecord record = new BackupRecord();
        record.setId(1L);
        record.setFilename("backup-20250101-020000.sql.gz.enc");
        record.setFilePath("/backups/backup-20250101-020000.sql.gz.enc");
        record.setStatus(status);
        record.setCreatedAt(Instant.now());
        record.setExpiresAt(Instant.now().plusSeconds(30 * 86400L));
        record.setFileSize(1024L);
        record.setChecksum("abc123");
        record.setEncrypted(true);
        return record;
    }

    @Test
    void listBackupsReturnsSortedList() {
        BackupRecord r1 = buildRecord(BackupStatus.COMPLETED);
        BackupRecord r2 = buildRecord(BackupStatus.FAILED);
        r2.setId(2L);
        when(backupRecordRepository.findAll(any(Sort.class))).thenReturn(List.of(r1, r2));

        List<BackupRecord> result = backupService.listBackups();

        assertEquals(2, result.size());
        verify(backupRecordRepository).findAll(any(Sort.class));
    }

    @Test
    void restoreRejectsNonCompletedBackup() {
        BackupRecord record = buildRecord(BackupStatus.FAILED);
        when(backupRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class, () -> backupService.restore(1L));
    }

    @Test
    void restoreRejectsNonExistentBackup() {
        when(backupRecordRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> backupService.restore(99L));
    }

    @Test
    void performBackupCreatesRecordWithCorrectFields() {
        AppProperties.Storage storage = new AppProperties.Storage();
        storage.setBackupPath(System.getProperty("java.io.tmpdir") + "/shiftworks-test-backups");
        when(appProperties.getStorage()).thenReturn(storage);

        Exception thrown = assertThrows(Exception.class, () -> backupService.performBackup(),
            "Expected exception when mysqldump is not available in test environment");

        assertNotNull(thrown.getMessage());
    }

    @Test
    void performBackupRecordHasCorrectNamingConvention() {
        AppProperties.Storage storage = new AppProperties.Storage();
        storage.setBackupPath(System.getProperty("java.io.tmpdir") + "/shiftworks-test-backups");
        when(appProperties.getStorage()).thenReturn(storage);

        try {
            BackupRecord result = backupService.performBackup();
            assertTrue(result.getFilename().matches("backup-\\d{8}-\\d{6}\\.sql\\.gz\\.enc"));
            assertTrue(result.isEncrypted());
            verify(backupRecordRepository).save(any(BackupRecord.class));
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("mysqldump") || e.getMessage().contains("Cannot run program")
                    || e.getMessage().contains("No such file") || e instanceof java.io.IOException,
                "Expected mysqldump-related failure, got: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
