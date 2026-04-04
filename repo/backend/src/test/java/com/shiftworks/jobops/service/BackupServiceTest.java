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
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock private BackupRecordRepository backupRecordRepository;
    @Mock private AppProperties appProperties;
    @InjectMocks private BackupService backupService;

    private static final String AES_KEY = "0123456789abcdef0123456789abcdef";

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
        when(backupRecordRepository.save(any(BackupRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.setField(backupService, "dbUrl", "jdbc:mysql://localhost:3306/shiftworks?useSSL=false");
        ReflectionTestUtils.setField(backupService, "dbUsername", "shiftworks");
        ReflectionTestUtils.setField(backupService, "dbPassword", "secret");

        BackupRecord result = backupService.performBackup();

        assertNotNull(result);
        assertEquals(BackupStatus.FAILED, result.getStatus());
        assertNotNull(result.getFilename());
        assertTrue(result.isEncrypted());
        verify(backupRecordRepository).save(any(BackupRecord.class));
    }

    @Test
    void performBackupRecordHasCorrectNamingConvention() {
        AppProperties.Storage storage = new AppProperties.Storage();
        storage.setBackupPath(System.getProperty("java.io.tmpdir") + "/shiftworks-test-backups");
        when(appProperties.getStorage()).thenReturn(storage);
        when(backupRecordRepository.save(any(BackupRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.setField(backupService, "dbUrl", "jdbc:mysql://localhost:3306/shiftworks?useSSL=false");
        ReflectionTestUtils.setField(backupService, "dbUsername", "shiftworks");
        ReflectionTestUtils.setField(backupService, "dbPassword", "secret");

        BackupRecord result = backupService.performBackup();

        assertNotNull(result);
        assertTrue(result.getFilename().matches("backup-\\d{8}-\\d{6}\\.sql\\.gz\\.enc"));
        assertTrue(result.isEncrypted());
        verify(backupRecordRepository).save(any(BackupRecord.class));
    }

    @Test
    void performBackupSuccessCreatesEncryptedArtifact() throws Exception {
        Path backupDir = Files.createTempDirectory("shiftworks-backup-success");
        AppProperties.Storage storage = new AppProperties.Storage();
        storage.setBackupPath(backupDir.toString());
        AppProperties.Security security = new AppProperties.Security();
        security.setAesSecretKey(AES_KEY);
        when(appProperties.getStorage()).thenReturn(storage);
        when(appProperties.getSecurity()).thenReturn(security);
        when(backupRecordRepository.save(any(BackupRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        Path dumpScript = Files.createTempFile("fake-mysqldump", ".sh");
        Files.writeString(dumpScript, "#!/bin/sh\nprintf 'CREATE TABLE demo(id INT);\\nINSERT INTO demo VALUES (1);\\n'\n");
        dumpScript.toFile().setExecutable(true);

        ReflectionTestUtils.setField(backupService, "dbUrl", "jdbc:mysql://localhost:3306/shiftworks?useSSL=false");
        ReflectionTestUtils.setField(backupService, "dbUsername", "shiftworks");
        ReflectionTestUtils.setField(backupService, "dbPassword", "secret");
        ReflectionTestUtils.setField(backupService, "mysqldumpCommand", dumpScript.toAbsolutePath().toString());

        BackupRecord result = backupService.performBackup();

        assertEquals(BackupStatus.COMPLETED, result.getStatus());
        assertTrue(Files.exists(Path.of(result.getFilePath())));
        assertTrue(result.getFileSize() > 12L);
        assertNotNull(result.getChecksum());
        assertFalse(result.getChecksum().isBlank());
    }

    @Test
    void restoreSuccessStreamsDecryptedSqlIntoMysqlProcess() throws Exception {
        Path tempDir = Files.createTempDirectory("shiftworks-restore-success");
        Path restoreCapture = tempDir.resolve("restored.sql");
        Path encryptedBackup = tempDir.resolve("backup.sql.gz.enc");

        AppProperties.Security security = new AppProperties.Security();
        security.setAesSecretKey(AES_KEY);
        when(appProperties.getSecurity()).thenReturn(security);

        String sql = "CREATE TABLE demo(id INT);\nINSERT INTO demo VALUES (42);\n";
        writeEncryptedBackup(encryptedBackup, sql);

        BackupRecord record = buildRecord(BackupStatus.COMPLETED);
        record.setFilePath(encryptedBackup.toString());
        when(backupRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        Path mysqlScript = Files.createTempFile("fake-mysql", ".sh");
        Files.writeString(mysqlScript, "#!/bin/sh\ncat > \"" + restoreCapture.toAbsolutePath() + "\"\n");
        mysqlScript.toFile().setExecutable(true);

        ReflectionTestUtils.setField(backupService, "dbUrl", "jdbc:mysql://localhost:3306/shiftworks?useSSL=false");
        ReflectionTestUtils.setField(backupService, "dbUsername", "shiftworks");
        ReflectionTestUtils.setField(backupService, "dbPassword", "secret");
        ReflectionTestUtils.setField(backupService, "mysqlCommand", mysqlScript.toAbsolutePath().toString());

        backupService.restore(1L);

        assertEquals(sql, Files.readString(restoreCapture));
    }

    private void writeEncryptedBackup(Path output, String sql) throws Exception {
        byte[] iv = new byte[12];
        for (int i = 0; i < iv.length; i++) {
            iv[i] = (byte) i;
        }
        SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));

        try (FileOutputStream fos = new FileOutputStream(output.toFile())) {
            fos.write(iv);
            try (CipherOutputStream cos = new CipherOutputStream(fos, cipher);
                 GZIPOutputStream gzip = new GZIPOutputStream(cos)) {
                gzip.write(sql.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
