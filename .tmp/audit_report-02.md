# ShiftWorks JobOps — Static Delivery & Architecture Assessment (Report 02)

**Assessment date:** 2026-04-18
**Workspace:** `/home/leul/Documents/task-44` (deliverable: `repo/`)
**Method:** Static review of source, configuration, and tests. A single backend `mvn test` run in Docker (`maven:3.9-eclipse-temurin-17-alpine`) was used to confirm that the `@SpringBootTest` contexts load; exit **0**.

---

## 1. Verdict

**Overall conclusion: Partial Pass**

The tree presents a full-stack JobOps product (Spring Boot API, Vue frontend, MySQL schema, compose orchestration, shell API tests) with layered security controls and domain services aligned to an employer–reviewer–admin job lifecycle. **Partial Pass** reflects limits that static analysis and a single backend test run cannot close entirely: browser and multi-container runtime behavior, exhaustive prompt-to-line traceability, and the proportion of full-stack versus sliced tests.

---

## 2. Scope and Static Verification Boundary

**Examined**

- Documentation: `repo/README.md`, `repo/.env.example`
- Runtime wiring: `repo/docker-compose.yml`, `repo/run_tests.sh`, `repo/scripts/check-env.sh`
- Backend: security configuration, encryption policy, representative controllers and services
- Tests: `repo/backend/src/test/java/**`, `repo/API_tests/**`, `repo/frontend/src/__tests__/**`

**Also executed**

- `mvn test` for `repo/backend` via Docker, full suite exit **0**.

**Not used as primary evidence**

- Live Docker Compose stack for the full three-tier system, frontend Vitest run, and browser UI.

**Explicit non-goals**

- Visual or interaction design judgment for the Vue UI (no rendered UI audit).

---

## 3. Repository / Requirement Mapping Summary

**Business goal (from delivery prompt):** Offline-oriented JobOps: employers draft and submit jobs with validation; reviewers approve, reject, diff, and takedown; admins govern users, dictionaries, claims, tickets, backups, and dashboards; security includes sessions, RBAC, CSRF, rate limits, audits, encryption/masking, uploads, and backups.

**Implementation mapping (representative)**

| Area | Evidence |
|------|----------|
| Compose + secrets from `.env` only | `repo/docker-compose.yml:2-36` |
| Startup + optional env validation | `repo/README.md:19-31`, `repo/scripts/check-env.sh:1-45` |
| AES-256-only key material | `repo/backend/src/main/java/com/shiftworks/jobops/service/EncryptionService.java:34-49` |
| Strong secret length for AES env | `repo/backend/src/main/java/com/shiftworks/jobops/config/SecretPolicyValidator.java:34-35` |
| Session + password rotation gate | `repo/backend/src/main/resources/application.yml:37-39`, `repo/backend/src/main/java/com/shiftworks/jobops/security/SessionAuthFilter.java:59-68` |
| Job bounds (pay, headcount, hours, validity) | `repo/backend/src/main/resources/application.yml:43-58`, `repo/backend/src/main/java/com/shiftworks/jobops/service/JobPostingService.java:303-380` |
| Reviewer diff API | `repo/backend/src/main/java/com/shiftworks/jobops/controller/ReviewController.java:56-59` |
| Image build runs Maven tests | `repo/backend/Dockerfile:8` |
| API tests require explicit admin password | `repo/API_tests/lib/test_env.sh:6-11`, `repo/run_tests.sh:7-12` |

---

## 4. Section-by-section Review

### Hard Gates

#### 1.1 Documentation and static verifiability

**Conclusion: Partial Pass**

**Rationale:** README documents Docker prerequisites, mandatory `.env`, `./scripts/check-env.sh`, URLs, roles, tests, and security notes that align with compose (no embedded secret defaults). Local MySQL and backend steps remain environment-dependent.

**Evidence:** `repo/README.md:19-41`, `repo/README.md:165-198`, `repo/docker-compose.yml:1-36`, `repo/scripts/check-env.sh:1-45`

#### 1.2 Deviation from prompt

**Conclusion: Partial Pass**

**Rationale:** Core loop and security themes are implemented in code. Analytics and custom dashboards remain restricted to `ADMIN` and `REVIEWER` in API and router metadata—a deliberate scope choice unless the product owner extends access to employers.

**Evidence:** `repo/backend/src/main/java/com/shiftworks/jobops/controller/AnalyticsController.java:22-26`, `repo/frontend/src/router/index.js:47-51`

---

### 2. Delivery Completeness

#### 2.1 Core functional requirements

**Conclusion: Partial Pass**

**Rationale:** Controllers and services exist for jobs, review, appeals, admin, tickets, files, backups, dashboards, reports, and alerts. Exhaustive trace of every prompt bullet was not repeated in this pass.

**Evidence (sample):** `repo/backend/src/main/java/com/shiftworks/jobops/controller/JobPostingController.java:34-129`, `repo/backend/src/main/java/com/shiftworks/jobops/service/BackupService.java:60-75`

#### 2.2 End-to-end deliverable

**Conclusion: Pass**

**Rationale:** Multi-module layout (backend, frontend, init-db, API_tests, scripts) is present.

**Evidence:** `repo/README.md:165-176`

---

### 3. Engineering and Architecture Quality

#### 3.1 Structure and decomposition

**Conclusion: Pass**

**Rationale:** Conventional Spring packages and separate Vue views per role.

#### 3.2 Maintainability

**Conclusion: Partial Pass**

**Rationale:** Dashboard SQL uses explicit whitelist commentary; CAPTCHA remains process-local by design, documented in README.

**Evidence:** `repo/backend/src/main/java/com/shiftworks/jobops/service/DashboardService.java:200-205`, `repo/README.md:191-193`

---

### 4. Engineering Details and Professionalism

#### 4.1 Errors, logging, validation

**Conclusion: Partial Pass**

**Rationale:** Central exception handler returns structured errors; generic failures omit stack traces in JSON. Validation spans DTO annotations and service rules.

**Evidence:** `repo/backend/src/main/java/com/shiftworks/jobops/exception/GlobalExceptionHandler.java:23-88`, `repo/backend/src/main/java/com/shiftworks/jobops/dto/JobPostingRequest.java:12-26`

#### 4.2 Product-like delivery

**Conclusion: Pass (static)**

**Rationale:** Audit write failures escalate; backups are scheduled; file quarantine paths exist.

**Evidence:** `repo/backend/src/main/java/com/shiftworks/jobops/service/AuditService.java:50-53`, `repo/backend/src/main/java/com/shiftworks/jobops/service/FileService.java:90-118`

---

### 5. Prompt Understanding and Requirement Fit

**Conclusion: Partial Pass**

**Rationale:** Strong fit on numeric validation, offline stack, RBAC, step-up for sensitive operations, and AES-256 key size enforcement. Residual ambiguity only where the prompt allows multiple interpretations (analytics audience).

**Evidence:** `repo/backend/src/main/java/com/shiftworks/jobops/service/JobPostingService.java:140-147`, `repo/backend/src/main/java/com/shiftworks/jobops/service/ReviewService.java:136-142`, `repo/backend/src/main/java/com/shiftworks/jobops/service/DashboardService.java:131-142`

---

### 6. Aesthetics (frontend)

**Conclusion: Not Applicable**

**Rationale:** No browser-based visual audit was performed.

---

## 5. Issues / Suggestions (Severity-Rated)

| Severity | Title | Conclusion | Evidence | Impact | Minimum actionable fix |
|----------|-------|------------|----------|--------|-------------------------|
| **Medium** | Slice-heavy integration suite | Controller-level coverage is dominated by `@WebMvcTest` slices backed by `@MockBean`s, so true wiring / JPA / encryption defects can slip past the "integration" layer. | `repo/backend/src/test/java/com/shiftworks/jobops/integration/*ControllerTest.java` | A `@WebMvcTest`-only matrix can stay green while real Spring context wiring is broken. | Add `@SpringBootTest` paths for at least one critical service (job creation end-to-end) using an H2 profile, and disable `@Scheduled` in that profile. |
| **Medium** | No smoke test for the public actuator endpoint | `SecurityConfig` permits `/actuator/health`, but no test exercises the anonymous GET path, so accidental tightening would not be caught. | `repo/backend/src/main/java/com/shiftworks/jobops/config/SecurityConfig.java:35-41` | Silent regressions on health-probe reachability break orchestration/monitoring. | Add a full-context IT that asserts `200` for anonymous `GET /actuator/health`. |
| **Medium** | Actuator exposure surface is implicit | Only Boot's default exposure (`health`) is in effect; there is no explicit `management.endpoints.web.exposure` policy, so a future dependency upgrade or profile change could silently expose `env`, `beans`, `heapdump`, etc. | `repo/backend/src/main/resources/application.yml` (no `management.*` section) | Unauthenticated exposure of diagnostic actuator endpoints leaks internals. | Pin `management.endpoints.web.exposure.include=health`, pin `management.endpoint.health.show-details=never`, and assert the negative cases in a test. |
| **Medium** | No full-stack CSRF proof for mutating endpoints | `CsrfValidationFilterTest` covers the filter in isolation, but there is no end-to-end test that issues a real HTTP POST through the full security chain without an `X-XSRF-TOKEN` and expects `403`. | `repo/backend/src/test/java/com/shiftworks/jobops/security/CsrfValidationFilterTest.java` | Order-of-filter regressions could let mutating requests through while the filter unit test still passes. | Add a `@SpringBootTest` + MockMvc case that performs `POST /api/jobs` with a valid session cookie but no/invalid CSRF header and expects `403`. |
| **Medium** | Cross-tenant isolation is only asserted with mocks | `ObjectLevelAuthorizationTest` covers service logic with Mockito; there is no real-DB assertion that employer B cannot read employer A's job over HTTP. | `repo/backend/src/test/java/com/shiftworks/jobops/service/ObjectLevelAuthorizationTest.java:48-54` | Repository-level scoping bugs could pass mock-based tests while leaking data across tenants at runtime. | Add an H2-backed `@SpringBootTest` that creates two employers, one job, and asserts the second employer receives `403`/`404` on `GET /api/jobs/{id}`. |
| **Low** | Operator `.env` friction | There is no pre-compose helper to verify that required variables are set and `AES_SECRET_KEY` decodes to exactly 32 bytes; operators first learn of a bad `.env` from a backend crash loop. | `repo/docker-compose.yml` (requires `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`, `AES_SECRET_KEY`, `BOOTSTRAP_ADMIN_PASSWORD` without any validator) | Onboarding/operational friction; first failure is a container crash rather than a clear message. | Ship a `scripts/check-env.sh` that validates presence and AES key length, and reference it from the Quick Start. |
| **Low** | `@Scheduled` jobs run during context-loaded tests | Scheduled backups and retention sweeps are not disabled in the test profile, so they can race the test's own repository interactions. | `repo/backend/src/main/java/com/shiftworks/jobops/service/BackupService.java:60-75` | Intermittent CI failures and noisy logs. | Disable scheduling via the `test` profile (`spring.task.scheduling.enabled=false`). |

---

## 6. Security Review Summary

| Dimension | Conclusion | Evidence |
|-----------|------------|----------|
| Authentication | **Pass** (static) | `SecurityConfig.java:35-41` |
| Route-level authorization | **Partial Pass** | `@PreAuthorize` on role-specific controllers; some job routes delegate to service filters: `ReviewController.java:28-32`, `JobPostingController.java:43-55` |
| Object-level authorization | **Partial Pass** | Employer scoping: `JobPostingService.java:401-417`; `ObjectLevelAuthorizationTest.java:48-54` — mock-only coverage of cross-tenant denial |
| Function-level authorization | **Pass** (static, sampled) | Step-up on publish, takedown, role change, unmasked export |
| Tenant isolation | **Partial Pass** | Employer listing predicate in `JobPostingService.buildSpecification`; no full-stack cross-tenant HTTP assertion |
| Admin / debug exposure | **Partial Pass** | `SecurityConfig` permits `/actuator/health`; the default Boot exposure policy is implicit and not asserted |

---

## 7. Tests and Logging Review

| Topic | Conclusion | Notes |
|-------|------------|-------|
| Unit tests | **Pass** | Broad service and filter tests under `repo/backend/src/test/java/`. |
| API / integration tests | **Partial Pass** | Heavy use of `@WebMvcTest`; few full-context `@SpringBootTest` ITs; bash `API_tests/` exist but require a running stack. |
| Logging | **Partial Pass** | SLF4j usage in services; no full PII audit of every log line. |
| Sensitive responses | **Pass** (sampled) | `GlobalExceptionHandler.java:78-88` |

---

## 8. Test Coverage Assessment (Static + spot execution)

### 8.1 Test Overview

- Backend: JUnit 5 + Spring Test + Mockito; entry via `repo/run_tests.sh` and `repo/README.md:127-131`.
- Frontend: Vitest specs under `repo/frontend/src/__tests__/`.
- API: `repo/API_tests/*.sh` with `repo/API_tests/lib/test_env.sh`.

### 8.2 Coverage Mapping (risk-focused)

| Risk / requirement | Mapped tests | Assessment | Gap |
|--------------------|--------------|------------|-----|
| Job validation + persistence + encryption | `JobPostingServiceTest` | Slice-only | Full context + real JPA + transparent encryption path |
| RBAC on endpoints | `RbacIntegrationTest`, `*SecurityTest` | Basically covered | Endpoint enumeration drift |
| CSRF | `CsrfValidationFilterTest` | Filter-level only | Full-stack POST through the security chain |
| Encryption roundtrip | `EncryptionServiceTest`, `EncryptionIT` | Good | — |
| Object isolation | `ObjectLevelAuthorizationTest` | Mock-only | Cross-tenant HTTP matrix with real DB |
| Public actuator health | — | Missing | Smoke test for anonymous `GET /actuator/health` and negative coverage on other actuator endpoints |

### 8.3 Security test depth

Authentication and authorization are covered mainly by unit and slice tests; **full HTTP + MySQL** production parity is the domain of `API_tests/` and manual checks.

### 8.4 Final coverage judgment

**Partial Pass** — mocks-based slices dominate "integration" coverage; full-context paths for mutating flows, CSRF, and cross-tenant reads are the main gaps.

---

## 9. Final Notes

- Compose, `.env.example`, encryption policy, `run_tests.sh`, and `scripts/check-env.sh` are aligned around operator-supplied secrets.
- **Cannot Confirm Statistically:** production traffic patterns, full three-container compose health, and UI correctness without a dedicated runbook execution.

---

*End of Report 02.*
