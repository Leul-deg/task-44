package com.shiftworks.jobops.integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditSchemaImmutabilityTest {

    @Test
    void backendSchemaContainsAuditImmutabilityTriggers() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));

        assertTrue(schema.contains("prevent_audit_logs_update"));
        assertTrue(schema.contains("prevent_audit_logs_delete"));
        assertTrue(schema.contains("BEFORE UPDATE ON audit_logs"));
        assertTrue(schema.contains("BEFORE DELETE ON audit_logs"));
    }

    @Test
    void initDbSchemaContainsAuditImmutabilityTriggers() throws Exception {
        Path initDbSchema = Path.of("../init-db/01-schema.sql");
        if (!Files.exists(initDbSchema)) {
            return;
        }
        String schema = Files.readString(initDbSchema);

        assertTrue(schema.contains("prevent_audit_logs_update"));
        assertTrue(schema.contains("prevent_audit_logs_delete"));
        assertTrue(schema.contains("BEFORE UPDATE ON audit_logs"));
        assertTrue(schema.contains("BEFORE DELETE ON audit_logs"));
    }
}
