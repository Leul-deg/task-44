package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.AuditLogDetailResponse;
import com.shiftworks.jobops.dto.AuditLogResponse;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.entity.AuditLog;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public PageResponse<AuditLogResponse> list(@RequestParam(required = false) String entityType,
                                                @RequestParam(required = false) Long entityId,
                                                @RequestParam(required = false) Long userId,
                                                @RequestParam(required = false) String action,
                                                @RequestParam(required = false) Instant from,
                                                @RequestParam(required = false) Instant to,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<AuditLog> result = auditLogRepository.findAll(spec, pageable);
        List<AuditLogResponse> items = result.getContent().stream()
            .map(log -> new AuditLogResponse(
                log.getId(),
                log.getUser() != null ? log.getUser().getUsername() : null,
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getIpAddress(),
                log.getCreatedAt()))
            .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size, result.getTotalPages());
    }

    @GetMapping("/{id}")
    public AuditLogDetailResponse detail(@PathVariable Long id) {
        AuditLog log = auditLogRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "auditLog: Not found"));
        return new AuditLogDetailResponse(
            log.getId(),
            log.getUser() != null ? log.getUser().getUsername() : null,
            log.getAction(),
            log.getEntityType(),
            log.getEntityId(),
            log.getBeforeValue(),
            log.getAfterValue(),
            log.getIpAddress(),
            log.getCreatedAt());
    }
}
