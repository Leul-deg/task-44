package com.shiftworks.jobops.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shiftworks.jobops.entity.AuditLog;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.repository.AuditLogRepository;
import com.shiftworks.jobops.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "contactPhone", "contact_phone", "passwordHash", "password_hash", "email"
    );

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String entityType, Long entityId, Object beforeValue, Object afterValue) {
        try {
            AuditLog entry = new AuditLog();
            if (userId != null) {
                userRepository.findById(userId).ifPresent(entry::setUser);
            }
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setBeforeValue(toJson(beforeValue));
            entry.setAfterValue(toJson(afterValue));
            entry.setIpAddress(resolveIp());
            entry.setCreatedAt(Instant.now());
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to write audit log: action={}, entity={}/{}", action, entityType, entityId, e);
            throw new RuntimeException("Audit trail write failed — refusing to proceed without audit record", e);
        }
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            JsonNode node = objectMapper.valueToTree(value);
            maskSensitiveNodes(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            try {
                String raw = objectMapper.writeValueAsString(value.toString());
                return "{\"_raw\":" + raw + "}";
            } catch (Exception e2) {
                return "{\"_error\":\"serialization failed\"}";
            }
        }
    }

    private void maskSensitiveNodes(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> fieldNames = obj.fieldNames();
            List<String> toMask = new ArrayList<>();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                if (SENSITIVE_FIELDS.contains(field)) {
                    toMask.add(field);
                }
            }
            for (String field : toMask) {
                String val = obj.has(field) && obj.get(field).isTextual() ? obj.get(field).asText() : "";
                if (val.length() > 4) {
                    obj.put(field, "***" + val.substring(val.length() - 4));
                } else {
                    obj.put(field, "***");
                }
            }
            obj.elements().forEachRemaining(this::maskSensitiveNodes);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(this::maskSensitiveNodes);
        }
    }

    private String resolveIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
