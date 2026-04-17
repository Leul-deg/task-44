# Fix Check — Report 02

**Source report:** `.tmp/audit_report-02.md`
**Scope:** For every issue enumerated in Report 02 §5, this document records the resolution applied and the concrete evidence of that fix in the repository.

---

## Issue-by-issue status

| # | Severity | Title (from Report 02) | Status | How it was resolved |
|---|----------|-------------------------|--------|---------------------|
| 1 | **Medium** | Slice-heavy integration suite | **Fixed** | Added full-context coverage for the most security-sensitive service paths. `repo/backend/src/test/java/com/shiftworks/jobops/integration/JobPostingServiceSpringIT.java:29-108` boots the real Spring context on H2 and exercises `JobPostingService` end-to-end (validation, JPA persistence, transparent AES-256-GCM column encryption). `repo/backend/src/test/java/com/shiftworks/jobops/integration/JobFlowFullStackIT.java` extends this over HTTP through the full security chain. Controller-level wiring, transaction boundaries, and encryption no longer rely solely on `@MockBean` stand-ins. |
| 2 | **Medium** | No smoke test for the public actuator endpoint | **Fixed** | `repo/backend/src/test/java/com/shiftworks/jobops/integration/ActuatorPublicHealthIT.java` performs an unauthenticated `GET /actuator/health` through `TestRestTemplate` against a real Spring Boot context and asserts `200 OK`. Any accidental tightening of `SecurityConfig` that re-hides `/actuator/health` behind authentication now fails CI immediately. |
| 3 | **Medium** | Actuator exposure surface is implicit | **Fixed** | `repo/backend/src/main/resources/application.yml` now pins `management.endpoints.web.exposure.include=health` and `management.endpoint.health.show-details=never`, so non-health actuator endpoints (`env`, `beans`, `heapdump`, `threaddump`, `shutdown`, …) are never web-exposed even if a transitive dependency reverts defaults. `ActuatorPublicHealthIT.otherActuatorEndpoints_areNotExposed` enumerates that list and asserts `4xx` for each; `ActuatorPublicHealthIT.actuatorHealth_doesNotLeakComponentDetails` asserts the `health` body carries no `components`/`details` keys. |
| 4 | **Medium** | No full-stack CSRF proof for mutating endpoints | **Fixed** | `repo/backend/src/test/java/com/shiftworks/jobops/integration/JobFlowFullStackIT.java` includes `createJob_withoutCsrfHeader_returns403` and `createJob_withWrongCsrfHeader_returns403`. Both perform a real `POST /api/jobs` through `MockMvc` with a valid session cookie but missing or wrong `X-XSRF-TOKEN`, and expect `403`. This exercises the real filter order (`SessionAuthFilter` → `CsrfValidationFilter` → controller) rather than the filter in isolation. |
| 5 | **Medium** | Cross-tenant isolation is only asserted with mocks | **Fixed** | `JobFlowFullStackIT.employerB_cannotReadEmployerA_jobById` creates two employer users and sessions, has Employer A create a job over HTTP, then asserts that Employer B receives `403`/`404` on `GET /api/jobs/{id}`. The test uses the real `JobPostingRepository` on H2, so service-layer scoping is proven against real persistence instead of Mockito stubs. A companion `findRawContactPhoneById` native query (`repo/backend/src/main/java/com/shiftworks/jobops/repository/JobPostingRepository.java`) also lets the happy-path assertion prove the `contact_phone` column is encrypted at rest. |
| 6 | **Low** | Operator `.env` friction | **Fixed** | A pre-compose helper was added at `repo/scripts/check-env.sh:1-45`. It confirms that `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`, `AES_SECRET_KEY`, and `BOOTSTRAP_ADMIN_PASSWORD` are set and that `AES_SECRET_KEY` is exactly 32 bytes (matching `EncryptionService`'s runtime check). It is wired into Quick Start: `repo/README.md:19-31` instructs operators to run `./scripts/check-env.sh` before `docker compose up`, so misconfigurations surface as a friendly shell message instead of a backend crash loop. |
| 7 | **Low** | `@Scheduled` jobs run during context-loaded tests | **Fixed** | `repo/backend/src/test/resources/application-test.properties:1` disables scheduling under the `test` profile (`spring.task.scheduling.enabled=false`), so `@Scheduled` jobs (e.g., `BackupService` nightly task, retention sweeps) do not fire during context-loaded tests. The same file also pins `management.endpoints.web.exposure.include=health` for the test profile, keeping the actuator contract consistent between main and test configurations. |

---

## Verification

- `mvn test` (via the project's Docker-based runner) executes the new `@SpringBootTest` classes alongside the existing unit/slice tests and returns exit **0**.
- `./scripts/check-env.sh` exits non-zero with actionable messages when any required variable is missing or when `AES_SECRET_KEY` is not 32 bytes, and exits **0** for a correctly populated `.env`.
- A fresh `docker compose up -d --build` followed by `./run_tests.sh` produces all green sections (Backend / Frontend / API) with no scheduler-induced noise.

---

*End of fix check for Report 02.*
