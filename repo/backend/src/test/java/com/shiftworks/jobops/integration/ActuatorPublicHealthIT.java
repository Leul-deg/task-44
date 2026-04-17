package com.shiftworks.jobops.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Actuator exposure contract:
 *   - {@code /actuator/health} is reachable without authentication (liveness probe).
 *   - No other management endpoints are exposed over HTTP by default
 *     (closes the "other actuator endpoints" gap in Report-02 §8.2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuatorPublicHealthIT {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void actuatorHealth_isOkWithoutSession() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertTrue(response.getStatusCode() == HttpStatus.OK || response.getStatusCode().is2xxSuccessful(),
            "Expected 2xx from /actuator/health, got " + response.getStatusCode());
    }

    @Test
    void otherActuatorEndpoints_areNotExposed() {
        for (String path : new String[] {
                "/actuator",
                "/actuator/env",
                "/actuator/beans",
                "/actuator/mappings",
                "/actuator/configprops",
                "/actuator/loggers",
                "/actuator/metrics",
                "/actuator/heapdump",
                "/actuator/threaddump",
                "/actuator/shutdown"}) {
            ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
            HttpStatusCode status = response.getStatusCode();
            assertTrue(status.is4xxClientError(),
                "Actuator endpoint " + path + " must not be reachable (got " + status + ")");
        }
    }

    @Test
    void actuatorHealth_doesNotLeakComponentDetails() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody() == null ? "" : response.getBody();
        assertTrue(!body.contains("components") && !body.contains("diskSpace") && !body.contains("details"),
            "Health endpoint must not expose component details under production-like config, got: " + body);
    }
}
