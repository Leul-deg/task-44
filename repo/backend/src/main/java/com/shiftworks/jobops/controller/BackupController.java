package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.entity.BackupRecord;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.AuditService;
import com.shiftworks.jobops.service.BackupService;
import com.shiftworks.jobops.service.StepUpVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/backup")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;
    private final StepUpVerificationService stepUpVerificationService;
    private final AuditService auditService;

    @PostMapping("/trigger")
    public BackupRecord trigger(Authentication authentication, @RequestBody Map<String, String> body) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        String stepUpPassword = body.get("stepUpPassword");
        if (!stepUpVerificationService.verify(user.id(), stepUpPassword)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "stepUpPassword: Verification failed");
        }
        BackupRecord record = backupService.performBackup();
        auditService.log(user.id(), "BACKUP_TRIGGERED", "BACKUP", record.getId(), null, record);
        return record;
    }

    @PostMapping("/restore/{id}")
    public Map<String, String> restore(Authentication authentication,
                                        @PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        String stepUpPassword = body.get("stepUpPassword");
        if (!stepUpVerificationService.verify(user.id(), stepUpPassword)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "stepUpPassword: Verification failed");
        }
        String confirm = body.get("confirm");
        if (!"RESTORE".equals(confirm)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "confirm: Must send {\"confirm\":\"RESTORE\"} to confirm");
        }
        backupService.restore(id);
        auditService.log(user.id(), "BACKUP_RESTORED", "BACKUP", id, null, null);
        return Map.of("message", "Restore completed successfully");
    }

    @GetMapping("/list")
    public List<BackupRecord> list() {
        return backupService.listBackups();
    }
}
