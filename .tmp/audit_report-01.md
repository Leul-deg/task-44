# ShiftWorks JobOps — Static Delivery & Architecture Audit

**Audit date:** 2026-04-18  
**Scope:** `/home/leul/Documents/task-44` (primary deliverable under `repo/`)  
**Boundary:** Static analysis only. No project start, Docker, tests, or network calls. Runtime claims in third-party docs are not treated as proof.

---

## 1. Verdict

**Overall conclusion: Partial Pass**

The repository presents a coherent Spring Boot + Vue + MySQL product shape with RBAC hooks, session cookies, CSRF header validation, job validation ranges aligned to the prompt, reviewer diff endpoints, step-up for publish/takedown/role change/unmasked admin export, backups, file quarantine, and broad automated test *files*. Gaps that prevent a full Pass are mainly: **documented security claims vs. default compose secrets**, **AES key length not enforced as 256-bit-only**, **Docker image build skipping tests**, and **test strategy dominated by sliced `@WebMvcTest` / unit tests** so severe integration defects could still slip through.

---

## 2. Scope and Static Verification Boundary

**Reviewed**

- `repo/README.md`, `repo/docker-compose.yml`, `repo/.env.example`, `repo/run_tests.sh`
- Backend: `repo/backend/src/main/java/**` (security, auth, job postings, review, dashboards/reports, backup, files, audit)
- Backend tests: `repo/backend/src/test/**` (47 Java test sources)
- Frontend routing (static only): `repo/frontend/src/router/index.js` (for role gating vs. API)
- Schema references: `repo/init-db/01-schema.sql` (spot-checked via codebase references, not full line-by-line DDL audit)

**Not reviewed (or only superficially)**

- Full Vue component markup and styling (no browser; see §6)
- Every line of SQL seed data and all API shell scripts’ runtime behavior
- `repo/frontend/node_modules/**` (ignored)
- Untracked paths outside `repo/` except workspace root metadata

**Intentionally not executed**

- Application, Docker Compose, Maven, npm, and `API_tests/*.sh` (per audit rules)

**Manual verification required**

- End-to-end flows (login → draft → submit → approve → publish), file watermark appearance, backup restore with real `mysqldump`, CAPTCHA UX, rate-limit behavior under load, cookie flags behind HTTPS

---

## 3. Repository / Requirement Mapping Summary

**Prompt core:** Offline-capable JobOps loop (employer draft/review/publish/govern; reviewer queue, diff, takedown, appeals; admin users/dictionaries/claims/tickets/dashboards; analytics/scheduling/masked exports; strong auth, sessions, CSRF, rate limits, audits, encryption/masking, uploads, backups).

**Mapped implementation areas**

| Prompt area | Primary evidence |
|-------------|------------------|
| Spring REST + MySQL | `repo/README.md:7-12`, `repo/backend/pom.xml` (via structure), `repo/docker-compose.yml:21-39` |
| Vue employer/reviewer/admin + analytics routes | `repo/frontend/src/router/index.js:17-51` |
| Job validation (pay, headcount, hours, validity, dictionary location/category) | `repo/backend/src/main/resources/application.yml:43-58`, `repo/backend/src/main/java/com/shiftworks/jobops/service/JobPostingService.java:248-327` |
| Session + idle/absolute timeout + password rotation gate | `repo/backend/src/main/resources/application.yml:37-39`, `repo/backend/src/main/java/com/shiftworks/jobops/security/SessionAuthFilter.java:52-69` |
| CAPTCHA + lockout | `repo/backend/src/main/resources/application.yml:31-33`, `repo/backend/src/main/java/com/shiftworks/jobops/service/AuthService.java:52-92` |
| RBAC + CSRF + rate limit | `repo/backend/src/main/java/com/shiftworks/jobops/config/SecurityConfig.java:28-45`, `repo/backend/src/main/java/com/shiftworks/jobops/security/CsrfValidationFilter.java:17-44`, `repo/backend/src/main/java/com/shiftworks/jobops/security/RateLimitFilter.java:38-54` |
| Step-up publish / takedown / role change / unmasked export | `repo/backend/src/main/java/com/shiftworks/jobops/service/JobPostingService.java:140-147`, `repo/backend/src/main/java/com/shiftworks/jobops/service/ReviewService.java:136-142`, `repo/backend/src/main/java/com/shiftworks/jobops/service/AdminUserService.java:141-147`, `repo/backend/src/main/java/com/shiftworks/jobops/service/DashboardService.java:131-142` |
| Column encryption (phone) | `repo/backend/src/main/java/com/shiftworks/jobops/entity/JobPosting.java:77-79` |
| Reviewer side-by-side diff | `repo/backend/src/main/java/com/shiftworks/jobops/controller/ReviewController.java:56-59`, `repo/backend/src/main/java/com/shiftworks/jobops/service/ReviewService.java:73-95` |
| Nightly encrypted backup + 30-day retention | `repo/backend/src/main/java/com/shiftworks/jobops/service/BackupService.java:60-75` |
| File whitelist / size / magic bytes / quarantine / PDF watermark on export | `repo/backend/src/main/java/com/shiftworks/jobops/service/FileService.java:53-119`, `repo/backend/src/main/java/com/shiftworks/jobops/service/FileService.java:124-145` |
| Audit masking | `repo/backend/src/main/java/com/shiftworks/jobops/service/AuditService.java:31-93` |

---

## 4. Section-by-section Review

### Hard Gates

#### 1.1 Documentation and static verifiability

**Conclusion: Partial Pass**

**Rationale:** README documents Docker quick start, local run, structure, and test entry points. However, it states that no runtime secrets are committed while `docker-compose.yml` supplies default passwords and a default `AES_SECRET_KEY`, which is a static inconsistency (see Issues).

**Evidence:** `repo/README.md:19-38`, `repo/README.md:123-126`, `repo/README.md:174-184`, `repo/docker-compose.yml:6-34`

**Manual verification note:** “Verified Runtime Evidence” blocks in README describe past Docker output; **Cannot Confirm Statistically** for current workspace behavior.

#### 1.2 Deviation from Prompt

**Conclusion: Partial Pass**

**Rationale:** The codebase centers on the JobOps scenario (jobs, review, admin, analytics, security). Analytics/reporting and custom dashboards are implemented for **ADMIN and REVIEWER** only (API and router), while the prompt’s phrase “authorized users” could be read to include employers; the implementation **narrows** that interpretation but is internally FE/BE consistent.

**Evidence:** `repo/backend/src/main/java/com/shiftworks/jobops/controller/AnalyticsController.java:22-26`, `repo/frontend/src/router/index.js:47-51`

---

### 2. Delivery Completeness

#### 2.1 Core functional requirements

**Conclusion: Partial Pass**

**Rationale:** Major flows and controls are present in code (lifecycle, RBAC, validation ranges, dictionaries via FK to `Category`/`Location`, appeals/review controllers exist in tree). Some prompt details (e.g., every alert/anomaly nuance, “English-only” admin console) are **not** enforceable as static code guarantees or were not exhaustively traced file-by-file.

**Evidence (representative):** `repo/backend/src/main/java/com/shiftworks/jobops/service/JobPostingService.java:121-172`, `repo/backend/src/main/java/com/shiftworks/jobops/controller/ReviewController.java:28-84`

#### 2.2 End-to-end deliverable vs. fragment

**Conclusion: Pass**

**Rationale:** Full project layout (backend, frontend, init SQL, API scripts, compose) — not a single-file demo.

**Evidence:** `repo/README.md:161-171`

---

### 3. Engineering and Architecture Quality

#### 3.1 Structure and decomposition

**Conclusion: Pass**

**Rationale:** Layered packages (`controller`, `service`, `repository`, `security`, `config`) and bounded controllers per domain.

**Evidence:** `repo/backend/src/main/java/com/shiftworks/jobops/` (module tree from glob listing in working session)

#### 3.2 Maintainability / extensibility

**Conclusion: Partial Pass**

**Rationale:** Clear services and whitelisted dashboard SQL pattern with explicit security comment; CAPTCHA store is in-memory (`ConcurrentHashMap`), which is simple but limits horizontal scaling (not forbidden by prompt, but an architectural ceiling).

**Evidence:** `repo/backend/src/main/java/com/shiftworks/jobops/service/CaptchaService.java:26-32`, `repo/backend/src/main/java/com/shiftworks/jobops/service/DashboardService.java:200-205`

---

### 4. Engineering Details and Professionalism

#### 4.1 Errors, logging, validation, API design

**Conclusion: Partial Pass**

**Rationale:** `GlobalExceptionHandler` returns structured errors and avoids leaking stack traces in JSON; business rules are enforced in services. Bean validation on `JobPostingRequest` is minimal (`@NotNull` / `@NotBlank`); numeric boundaries are enforced in `JobPostingService` (acceptable pattern but split between layers).

**Evidence:** `repo/backend/src/main/java/com/shiftworks/jobops/exception/GlobalExceptionHandler.java:23-88`, `repo/backend/src/main/java/com/shiftworks/jobops/dto/JobPostingRequest.java:12-26`, `repo/backend/src/main/java/com/shiftworks/jobops/service/JobPostingService.java:303-327`

#### 4.2 Product-like vs. demo

**Conclusion: Pass (static shape)**

**Rationale:** Security filters, audit failures as hard errors, backup scheduler, and file quarantine resemble product concerns more than a toy CRUD sample.

**Evidence:** `repo/backend/src/main/java/com/shiftworks/jobops/service/AuditService.java:50-53`, `repo/backend/src/main/java/com/shiftworks/jobops/service/BackupService.java:60-65`

---

### 5. Prompt Understanding and Requirement Fit

**Conclusion: Partial Pass**

**Rationale:** Strong alignment on numeric validation, session timeouts, bcrypt, step-up for critical actions, reviewer diff endpoint, and offline-oriented stack. Ambiguous or narrowed items: self-service analytics scope (reviewer/admin vs. all roles), and documentation that overstates “no secrets committed” relative to compose defaults.

**Evidence:** Same as §3 mapping and Issues list.

---

### 6. Aesthetics (frontend)

**Conclusion: Not Applicable**

**Rationale:** This audit was executed under a **non-runtime, non-frontend-visual** mandate. Layout, typography, hover states, and rendering correctness **Cannot Confirm Statistically** without a browser.

**Evidence:** Audit boundary (user instructions); static route map only: `repo/frontend/src/router/index.js:19-52`

---

## 5. Issues / Suggestions (Severity-Rated)

| Severity | Title | Conclusion | Evidence | Impact | Minimum actionable fix |
|----------|-------|------------|----------|--------|-------------------------|
| **High** | Default secrets in `docker-compose.yml` | Compose provides default `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`, `BOOTSTRAP_ADMIN_PASSWORD`, and `AES_SECRET_KEY` if env vars are unset. | `repo/docker-compose.yml:6-34` | Anyone who runs compose without a proper `.env` uses **known** credentials and encryption key material — catastrophic if exposed beyond local dev. | Remove insecure defaults (require empty override / fail fast) or document explicitly as **unsafe dev-only** and align README (see next row). |
| **High** | README vs. compose secret claims | README states no runtime secrets are committed and points to `.env`, but compose file embeds fallback secrets and keys. | `repo/README.md:174-184`, `repo/docker-compose.yml:6-34` | Reviewers and operators get **false assurance**; compliance-style audits fail. | Make README and compose **mutually consistent** (either no defaults in compose, or README must state defaults are dev-only and unsafe). |
| **Medium** | AES “256” not strictly enforced | `EncryptionService` accepts 16/24/32-byte keys after decode; prompt calls out AES-256. | `repo/backend/src/main/java/com/shiftworks/jobops/service/EncryptionService.java:45-49` | Risk of **AES-128/192** if operators supply a shorter key while docs say 256. | Enforce 32-byte key only (or validate `keyBits == 256`) when policy requires AES-256-GCM. |
| **Medium** | Production JAR build skips tests | Backend image runs `mvn … -Dmaven.test.skip=true`, so container image is not proof-tested by that build step. | `repo/backend/Dockerfile:8` | Regressions can ship in **released images** if CI does not separately gate tests. | Use `-DskipTests` vs. tests in CI pipeline, or run tests in a prior stage and copy artifact. |
| **Medium** | Test runner embeds default admin password | When env is unset, `run_tests.sh` exports `BOOTSTRAP_ADMIN_PASSWORD` / `JOBOPS_ADMIN_PASSWORD` to a documented default. | `repo/run_tests.sh:13-17`, `repo/README.md:107-110` | Encourages reuse of a **known** password in automation shells. | Require explicit env vars (fail if unset) for any non-local profile. |
| **Low** | CAPTCHA storage is process-local | CAPTCHA answers live in an in-memory map with TTL cleanup. | `repo/backend/src/main/java/com/shiftworks/jobops/service/CaptchaService.java:26-32` | Restart clears CAPTCHAs; multi-instance deployments would be inconsistent. | Document single-node expectation or back CAPTCHA with shared store if scaled. |
| **Low** | Prompt nuance — analytics audience | Analytics/dashboard/report APIs are restricted to `ADMIN`/`REVIEWER`. | `repo/backend/src/main/java/com/shiftworks/jobops/controller/AnalyticsController.java:22-26`, `repo/frontend/src/router/index.js:47-51` | If employers were required to use the analytics center, this would be a functional gap; current FE/BE **agree** on exclusion. | Confirm product intent; if employers need analytics, extend RBAC + UI. |

---

## 6. Security Review Summary

| Dimension | Conclusion | Evidence & brief reasoning |
|-----------|------------|----------------------------|
| **Authentication entry points** | **Pass** (static) | Login and captcha are `permitAll`; other `/api/**` authenticated: `repo/backend/src/main/java/com/shiftworks/jobops/config/SecurityConfig.java:35-40`. `AuthController` issues session cookies: `repo/backend/src/main/java/com/shiftworks/jobops/controller/AuthController.java:42-55`, `100-117`. |
| **Route-level authorization** | **Partial Pass** | Class/method `@PreAuthorize` on sensitive controllers (e.g. `ReviewController`: `repo/backend/src/main/java/com/shiftworks/jobops/controller/ReviewController.java:28-32`). `JobPostingController` mixes annotated mutating routes with unannotated reads that rely on service-layer checks: `repo/backend/src/main/java/com/shiftworks/jobops/controller/JobPostingController.java:43-55`, `69-81`. **Cannot Confirm Statistically** that no endpoint escapes intended role matrix without full controller survey. |
| **Object-level authorization** | **Partial Pass** | Employer scoping via `findByIdAndEmployer_Id` / `loadJobForAccess`: `repo/backend/src/main/java/com/shiftworks/jobops/service/JobPostingService.java:401-417`. Dedicated tests: `repo/backend/src/test/java/com/shiftworks/jobops/service/ObjectLevelAuthorizationTest.java:48-54` (mocked service-level tests, not full HTTP stack). |
| **Function-level authorization** | **Partial Pass** | Step-up for sensitive operations (publish, takedown, role change, unmasked export): `JobPostingService.java:146-147`, `ReviewService.java:140-141`, `AdminUserService.java:146-147`, `DashboardService.java:136-141`. |
| **Tenant / user data isolation** | **Partial Pass** | Employer job list constrained in specification: `repo/backend/src/main/java/com/shiftworks/jobops/service/JobPostingService.java:213-215`. Full isolation across **all** entities was not exhaustively proven static-only. |
| **Admin / internal / debug protection** | **Partial Pass** | Only `/actuator/health` is permitAll in security config: `repo/backend/src/main/java/com/shiftworks/jobops/config/SecurityConfig.java:39-40`. **Cannot Confirm Statistically** that no other actuator endpoint is exposed without reading full `application*.yml` actuator management settings (not all files expanded in this pass). |

---

## 7. Tests and Logging Review

| Area | Conclusion | Evidence & notes |
|------|------------|------------------|
| **Unit tests** | **Pass** (existence / breadth) | 47 Java test sources under `repo/backend/src/test/java/`. Representative service tests: `JobPostingServiceTest`, `AuthServiceTest`, `ReviewServiceTest`, `FileServiceTest`, etc. |
| **API / integration tests** | **Partial Pass** | Many `*ControllerTest` classes use `@WebMvcTest` (MockMvc + `@MockBean`), e.g. `repo/backend/src/test/java/com/shiftworks/jobops/integration/JobPostingControllerTest.java:50-55` — **sliced** tests, not full Spring Boot + DB. True `@SpringBootTest` appears limited (e.g. `repo/backend/src/test/java/com/shiftworks/jobops/integration/EncryptionIT.java:27`). Shell API tests exist but were not run: `repo/API_tests/test_auth.sh` (directory per README `repo/README.md:126`). |
| **Logging / observability** | **Partial Pass** | SLF4J `@Slf4j` used in core services (`JobPostingService`, `AuthService`, `ReviewService`, `BackupService`, etc.). |
| **Sensitive data in logs / responses** | **Partial Pass** | `GlobalExceptionHandler` avoids echoing exception messages for generic errors: `repo/backend/src/main/java/com/shiftworks/jobops/exception/GlobalExceptionHandler.java:78-88`. `DashboardService.export` logs `masked` flag: `repo/backend/src/main/java/com/shiftworks/jobops/service/DashboardService.java:132`. Audit serialization masks configured fields: `repo/backend/src/main/java/com/shiftworks/jobops/service/AuditService.java:31-33`. **Cannot Confirm Statistically** for every `log.info` call site without full grep review. |

---

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview

- **Unit tests:** Yes — Mockito-based service tests and security filter tests.  
- **API / integration tests:** Mixed — extensive `@WebMvcTest`, fewer full `@SpringBootTest` ITs, plus bash `API_tests/` (manual / CI when server up).  
- **Frameworks:** JUnit 5 + Spring Test + Mockito (from test imports, e.g. `JobPostingControllerTest.java:26-34`).  
- **Test entry points:** `repo/run_tests.sh:34-103`, `repo/README.md:123-126`.  
- **Documentation of test commands:** `repo/README.md:123-126`.

### 8.2 Coverage Mapping Table

| Requirement / risk | Mapped test(s) | Key assertion / mock | Coverage | Gap | Minimum test addition |
|--------------------|----------------|-------------------------|----------|-----|------------------------|
| Hourly pay $12–$75, headcount 1–500, weekly hours 1–80 | `JobPostingServiceTest` (service) | Validates boundary throws | **Basically covered** (service-level) | Not always exercised through HTTP + JPA | Add `@SpringBootTest` cases hitting `/api/jobs` with invalid payloads |
| Validity default 30 / max 90 days | `application.yml` + `JobPostingService.validateDates` | `JobPostingServiceTest` scenarios (if present) | **Basically covered** / **Cannot Confirm** every branch without opening whole test file | End-to-end date edge cases | Integration test with real repository |
| Dictionary location/category (FK + active) | `JobPostingService.applyRequest` | `JobPostingServiceTest` | **Basically covered** | Inactive ID bypass attempts via API | Controller IT with repository fixtures |
| Employer cannot access other employer’s job | `ObjectLevelAuthorizationTest` | Mockito stubs, `assertThrows` | **Basically covered** (logic) | Not real DB isolation | Repository-backed IT |
| Reviewer queue & diff | `ReviewServiceTest`, `ReviewControllerTest` | MockMvc / mocks | **Basically covered** / **insufficient** for SQL | Full stack diff with history rows | `@SpringBootTest` + JDBC setup |
| Step-up on publish | `JobPostingControllerTest` + `StepUpVerificationService` mock | Publish endpoint | **Basically covered** | False positive if mock always returns true | Negative case with `verify` failure |
| CSRF on mutating API | `CsrfValidationFilterTest` | Header checks | **Sufficient** (filter-focused) | Interaction order with security | Optional full-stack POST without header |
| Rate limiting | `RateLimitFilterTest` | Token bucket behavior | **Basically covered** | Not under realistic concurrent load | Stress test (manual / perf env) |
| CAPTCHA after 3 failures | `AuthServiceTest` (if scenarios exist) | Failure counters | **Cannot Confirm** without reading entire test | — | Extend unit tests for threshold edges |
| Backup encrypt + retention | `BackupServiceTest`, `BackupControllerTest` | Mocked mysqldump / clock | **Basically covered** | Real `mysqldump`/`mysql` binaries | Containerized IT (manual gated) |
| File magic bytes & quarantine | `FileServiceTest` | Status `QUARANTINED` | **Basically covered** | Filesystem permissions | IT with temp dir |
| RBAC matrix | `RbacIntegrationTest`, `*SecurityTest` classes | Role denial tests | **Basically covered** | Not all endpoints enumerated | Parameterized route table test |
| Analytics SQL injection | `DashboardService` code review + tests | Whitelist `switch` | **Sufficient** (design) | Future metrics must stay whitelisted | Lint rule / code review checklist |

### 8.3 Security Coverage Audit

| Security theme | Test meaningfully reduces risk? | Notes |
|----------------|-----------------------------------|-------|
| Authentication | **Partial** | `AuthIntegrationTest` is `@WebMvcTest` (`repo/backend/src/test/java/com/shiftworks/jobops/integration/AuthIntegrationTest.java:42-47`), so login behavior against real `AuthService` + DB is **not** proven. |
| Route authorization | **Partial** | `RbacIntegrationTest` / `*SecurityTest` exist but scope must be maintained as controllers grow. |
| Object-level authorization | **Partial** | `ObjectLevelAuthorizationTest` covers service logic with mocks (`repo/backend/src/test/java/com/shiftworks/jobops/service/ObjectLevelAuthorizationTest.java:48-54`). |
| Tenant isolation | **Partial** | Same as object-level; no substitute for multi-user DB fixtures in IT. |
| Admin / internal protection | **Partial** | `BackupControllerSecurityTest`, `AnalyticsControllerSecurityTest` filenames indicate intent — **Cannot Confirm** depth without reading each file fully. |

### 8.4 Final Coverage Judgment

**Partial Pass**

Covered: filter-level CSRF, many service rules, RBAC-focused tests, and broad controller slice tests.  
Weak spot: **most “integration” controller tests are not full-stack**; therefore tests can pass while **wiring, transaction, and repository integration bugs** remain. API shell tests can cover live wiring but were **not executed** in this audit.

---

## 9. Final Notes

- Strong **static** alignment between the prompt’s security/validation themes and `application.yml` + core services.  
- The highest-impact findings are **operational/documentation honesty** (compose defaults vs. README) and **cryptographic key strength policy**, not missing Spring components.  
- **Cannot Confirm Statistically:** anything labeled “Verified Runtime Evidence” in `repo/README.md:128-148`, plus all browser/UI behavior.

---

*End of report.*
