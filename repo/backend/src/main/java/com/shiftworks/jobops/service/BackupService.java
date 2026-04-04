package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.entity.BackupRecord;
import com.shiftworks.jobops.enums.BackupStatus;
import com.shiftworks.jobops.repository.BackupRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final BackupRecordRepository backupRecordRepository;
    private final AppProperties appProperties;

    @Value("${spring.datasource.url}")
    private String dbUrl;
    @Value("${spring.datasource.username}")
    private String dbUsername;
    @Value("${spring.datasource.password}")
    private String dbPassword;
    @Value("${app.backup.mysqldump-command:mysqldump}")
    private String mysqldumpCommand = "mysqldump";
    @Value("${app.backup.mysql-command:mysql}")
    private String mysqlCommand = "mysql";

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Scheduled(cron = "0 0 2 * * *")
    public void nightlyBackup() {
        log.info("Starting nightly backup");
        performBackup();
        cleanupExpired();
    }

    @Transactional
    public BackupRecord performBackup() {
        String timestamp = LocalDateTime.now().format(TS_FMT);
        String filename = "backup-" + timestamp + ".sql.gz.enc";
        Path backupDir = Path.of(appProperties.getStorage().getBackupPath());
        BackupRecord record = new BackupRecord();
        record.setFilename(filename);
        record.setCreatedAt(Instant.now());
        record.setExpiresAt(Instant.now().plusSeconds(30L * 24 * 60 * 60));
        record.setEncrypted(true);

        try {
            Files.createDirectories(backupDir);
            Path outPath = backupDir.resolve(filename);
            record.setFilePath(outPath.toString());

            String[] dbInfo = parseDatabaseInfo();
            String host = dbInfo[0];
            String port = dbInfo[1];
            String dbName = dbInfo[2];

            ProcessBuilder pb = new ProcessBuilder(
                mysqldumpCommand, "-h", host, "-P", port, "-u", dbUsername,
                "--single-transaction", dbName
            );
            pb.environment().put("MYSQL_PWD", dbPassword);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            SecretKeySpec keySpec = getKeySpec();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));

            try (InputStream sqlStream = process.getInputStream();
                 FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                fos.write(iv);
                try (CipherOutputStream cos = new CipherOutputStream(fos, cipher);
                     GZIPOutputStream gzip = new GZIPOutputStream(cos)) {
                    sqlStream.transferTo(gzip);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                record.setStatus(BackupStatus.FAILED);
                record.setFileSize(0L);
                record.setChecksum("");
                backupRecordRepository.save(record);
                log.error("mysqldump exited with code {}", exitCode);
                return record;
            }

            File outFile = outPath.toFile();
            record.setFileSize(outFile.length());
            record.setChecksum(sha256(outFile));
            record.setStatus(BackupStatus.COMPLETED);
            backupRecordRepository.save(record);
            log.info("Backup completed: {}", filename);
            return record;
        } catch (Exception e) {
            log.error("Backup failed", e);
            record.setStatus(BackupStatus.FAILED);
            record.setFileSize(0L);
            record.setChecksum("");
            record.setFilePath(backupDir.resolve(filename).toString());
            backupRecordRepository.save(record);
            return record;
        }
    }

    @Transactional
    public void restore(Long backupId) {
        BackupRecord record = backupRecordRepository.findById(backupId)
            .orElseThrow(() -> new IllegalArgumentException("Backup not found"));
        if (record.getStatus() != BackupStatus.COMPLETED) {
            throw new IllegalStateException("Only completed backups can be restored");
        }
        try {
            Path filePath = Path.of(record.getFilePath());
            String[] dbInfo = parseDatabaseInfo();
            String host = dbInfo[0];
            String port = dbInfo[1];
            String dbName = dbInfo[2];

            SecretKeySpec keySpec = getKeySpec();

            ProcessBuilder pb = new ProcessBuilder(
                mysqlCommand, "-h", host, "-P", port, "-u", dbUsername,
                dbName
            );
            pb.environment().put("MYSQL_PWD", dbPassword);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                byte[] iv = new byte[IV_BYTES];
                if (fis.read(iv) != IV_BYTES) throw new IllegalStateException("Invalid backup file");

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));

                try (javax.crypto.CipherInputStream cis = new javax.crypto.CipherInputStream(fis, cipher);
                     GZIPInputStream gzip = new GZIPInputStream(cis);
                     OutputStream mysqlIn = process.getOutputStream()) {
                    gzip.transferTo(mysqlIn);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("mysql restore exited with code " + exitCode);
            }
            log.info("Restore completed from backup {}", record.getFilename());
        } catch (Exception e) {
            log.error("Restore failed for backup {}", backupId, e);
            throw new IllegalStateException("Restore failed: " + e.getMessage(), e);
        }
    }

    public List<BackupRecord> listBackups() {
        return backupRecordRepository.findAll(
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        );
    }

    private void cleanupExpired() {
        List<BackupRecord> expired = backupRecordRepository.findByStatus(BackupStatus.COMPLETED)
            .stream()
            .filter(r -> r.getExpiresAt().isBefore(Instant.now()))
            .toList();
        for (BackupRecord record : expired) {
            try {
                Files.deleteIfExists(Path.of(record.getFilePath()));
            } catch (Exception e) {
                log.warn("Failed to delete expired backup file: {}", record.getFilePath(), e);
            }
            record.setStatus(BackupStatus.EXPIRED);
            backupRecordRepository.save(record);
        }
        if (!expired.isEmpty()) {
            log.info("Cleaned up {} expired backups", expired.size());
        }
    }

    private String[] parseDatabaseInfo() {
        String url = dbUrl;
        String cleaned = url.replaceFirst("jdbc:mysql://", "");
        String hostPort = cleaned.split("/")[0];
        String dbName = cleaned.split("/")[1].split("\\?")[0];
        String host = hostPort.contains(":") ? hostPort.split(":")[0] : hostPort;
        String port = hostPort.contains(":") ? hostPort.split(":")[1] : "3306";
        return new String[]{host, port, dbName};
    }

    private SecretKeySpec getKeySpec() {
        String raw = appProperties.getSecurity().getAesSecretKey();
        byte[] keyBytes = raw.length() == 32
            ? raw.getBytes(StandardCharsets.UTF_8)
            : java.util.Base64.getDecoder().decode(raw);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = bis.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
