package com.shiftworks.jobops.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.security.enforce-secret-policy", havingValue = "true", matchIfMissing = true)
public class SecretPolicyValidator {

    private static final Set<String> WEAK_VALUES = Set.of(
        "change-me",
        "changeme",
        "password",
        "password123",
        "admin123",
        "secret",
        "letmein",
        "123456",
        "123456789",
        "0123456789abcdef0123456789abcdef"
    );

    private final AppProperties appProperties;
    private final Environment environment;

    @PostConstruct
    void validateSecrets() {
        String aesSecret = appProperties.getSecurity().getAesSecretKey();
        requireStrong("AES_SECRET_KEY", aesSecret, 16);

        String datasourceUrl = safe(environment.getProperty("spring.datasource.url"));
        String datasourcePassword = environment.getProperty("spring.datasource.password");
        if (datasourceUrl.contains("jdbc:mysql:")) {
            requireStrong("SPRING_DATASOURCE_PASSWORD", datasourcePassword, 8);
        }

        String bootstrapAdminPassword = environment.getProperty("BOOTSTRAP_ADMIN_PASSWORD");
        if (bootstrapAdminPassword != null && !bootstrapAdminPassword.isBlank()) {
            int minLength = Math.max(12, appProperties.getSecurity().getPassword().getMinLength());
            requireStrong("BOOTSTRAP_ADMIN_PASSWORD", bootstrapAdminPassword, minLength);
        }
    }

    private void requireStrong(String name, String value, int minLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured and non-empty");
        }
        if (value.length() < minLength) {
            throw new IllegalStateException(name + " is too short; minimum length is " + minLength);
        }
        String normalized = value.trim().toLowerCase();
        if (WEAK_VALUES.contains(normalized)) {
            throw new IllegalStateException(name + " uses a known weak value");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
