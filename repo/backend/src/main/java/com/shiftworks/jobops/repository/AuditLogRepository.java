package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    @Override
    default void delete(AuditLog entity) {
        throw new UnsupportedOperationException("Audit logs are immutable");
    }

    @Override
    default void deleteById(Long id) {
        throw new UnsupportedOperationException("Audit logs are immutable");
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException("Audit logs are immutable");
    }
}
