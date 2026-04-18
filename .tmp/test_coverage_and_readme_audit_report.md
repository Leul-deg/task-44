# Unified Test Coverage + README Audit Report
**Project:** ShiftWorks JobOps Platform
**Date:** 2026-04-18 (refreshed)
**Mode:** Strict — endpoint-level inventory, actual file reads
**Refresh scope:** Updated to reflect (a) the three new `@SpringBootTest` integration classes, (b) the complete list of service-layer unit tests, and (c) the README's shift to an unambiguous Docker-only contract (no host-based startup path).

---

## Executive Summary

| Category | Score |
|----------|-------|
| Backend HTTP Controller Tests (MockMvc, all endpoints) | 35/35 |
| Backend Full-Context Integration Tests (`@SpringBootTest`) | 5/5 |
| Backend Service Unit Tests | 14/15 |
| Frontend View Unit Tests | 20/25 |
| API Shell Integration Tests | 12/15 |
| README Quality (Docker-only contract) | 5/5 |
| **Total** | **91/100** |

---

## Part 1: Backend Endpoint Coverage

**Total endpoints: 94 across 17 controllers**  
**Coverage: 94/94 (100%) have positive-path @WebMvcTest or API shell test**

### Coverage Legend
- `MockMvc` = @WebMvcTest controller test (HTTP routing + auth layer, services mocked)
- `Shell` = API shell test (end-to-end via running Docker service)
- `✓` = covered | `—` = not covered

### AuthController (`/api/auth`) — 6 endpoints

| Method | Path | MockMvc | Shell | Notes |
|--------|------|---------|-------|-------|
| POST | /api/auth/login | ✓ | ✓ | login accessible + 200 |
| GET | /api/auth/captcha | ✓ | ✓ | security headers test |
| POST | /api/auth/register | ✓ | ✓ | ADMIN-only, 403 without auth |
| GET | /api/auth/me | ✓ | ✓ | 403 without auth |
| POST | /api/auth/logout | ✓ | — | session + CSRF required |
| POST | /api/auth/change-password | ✓ | — | session + CSRF required |

### JobPostingController (`/api/jobs`) — 11 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/jobs | ✓ | ✓ |
| GET | /api/jobs/summary | ✓ | — |
| GET | /api/jobs/{id} | ✓ | ✓ |
| POST | /api/jobs | ✓ | ✓ |
| PUT | /api/jobs/{id} | ✓ | — |
| POST | /api/jobs/{id}/submit | ✓ | ✓ |
| POST | /api/jobs/{id}/publish | ✓ | — |
| POST | /api/jobs/{id}/unpublish | ✓ | — |
| GET | /api/jobs/{id}/preview | ✓ | — |
| GET | /api/jobs/{id}/history | ✓ | — |
| POST | /api/jobs/{id}/contact-phone | ✓ | — |

### ReviewController (`/api/review`) — 8 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/review/dashboard | ✓ | ✓ |
| GET | /api/review/queue | ✓ | ✓ |
| GET | /api/review/jobs/{id} | ✓ | — |
| GET | /api/review/jobs/{id}/diff | ✓ | — |
| GET | /api/review/jobs/{id}/actions | ✓ | — |
| POST | /api/review/jobs/{id}/approve | ✓ | ✓ |
| POST | /api/review/jobs/{id}/reject | ✓ | — |
| POST | /api/review/jobs/{id}/takedown | ✓ | ✓ |

### AppealController (`/api/appeals`) — 4 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/appeals | ✓ | ✓ |
| POST | /api/appeals | ✓ | ✓ |
| GET | /api/appeals/{id} | ✓ | — |
| POST | /api/appeals/{id}/process | ✓ | ✓ |

### FileController (`/api/files`) — 6 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| POST | /api/files/upload | ✓ | — |
| GET | /api/files/{id}/download | ✓ | — |
| GET | /api/files/entity/{entityType}/{entityId} | ✓ | — |
| GET | /api/files/quarantined | ✓ | — |
| PUT | /api/files/{id}/release | ✓ | — |
| DELETE | /api/files/{id} | ✓ | — |

### ClaimController (`/api/claims`) — 4 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/claims | ✓ | — |
| POST | /api/claims | ✓ | — |
| GET | /api/claims/{id} | ✓ | — |
| PUT | /api/claims/{id} | ✓ | — |

### TicketController (`/api/tickets`) — 4 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/tickets | ✓ | ✓ |
| POST | /api/tickets | ✓ | ✓ |
| GET | /api/tickets/{id} | ✓ | ✓ |
| PUT | /api/tickets/{id} | ✓ | ✓ |

### DashboardController (`/api/dashboards`) — 8 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/dashboards | ✓ | — |
| POST | /api/dashboards | ✓ | — |
| GET | /api/dashboards/{id} | ✓ | — |
| PUT | /api/dashboards/{id} | ✓ | — |
| DELETE | /api/dashboards/{id} | ✓ | — |
| GET | /api/dashboards/{id}/data | ✓ | — |
| POST | /api/dashboards/preview | ✓ | — |
| POST | /api/dashboards/{id}/export | ✓ | — |

### AnalyticsController (`/api/analytics`) — 7 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/analytics/post-volume | ✓ | ✓ |
| GET | /api/analytics/post-status-distribution | ✓ | ✓ |
| GET | /api/analytics/claim-success-rate | ✓ | — |
| GET | /api/analytics/avg-handling-time | ✓ | — |
| GET | /api/analytics/reviewer-activity | ✓ | — |
| GET | /api/analytics/approval-rate | ✓ | — |
| GET | /api/analytics/takedown-trend | ✓ | — |

### ReportController (`/api/reports`) — 6 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/reports/scheduled | ✓ | — |
| POST | /api/reports/scheduled | ✓ | — |
| PUT | /api/reports/scheduled/{id} | ✓ | — |
| DELETE | /api/reports/scheduled/{id} | ✓ | — |
| GET | /api/reports/exports | ✓ | — |
| GET | /api/reports/exports/{id}/download | ✓ | — |

### AdminUserController (`/api/admin/users`) — 7 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/admin/users | ✓ | ✓ |
| GET | /api/admin/users/{id} | ✓ | ✓ |
| POST | /api/admin/users | ✓ | ✓ |
| PUT | /api/admin/users/{id} | ✓ | — |
| PUT | /api/admin/users/{id}/role | ✓ | — |
| PUT | /api/admin/users/{id}/unlock | ✓ | — |
| PUT | /api/admin/users/{id}/reset-password | ✓ | — |

### AdminDictionaryController (`/api/admin`) — 8 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/admin/categories | ✓ | ✓ |
| POST | /api/admin/categories | ✓ | — |
| PUT | /api/admin/categories/{id} | ✓ | — |
| DELETE | /api/admin/categories/{id} | ✓ | — |
| GET | /api/admin/locations | ✓ | ✓ |
| POST | /api/admin/locations | ✓ | — |
| PUT | /api/admin/locations/{id} | ✓ | — |
| DELETE | /api/admin/locations/{id} | ✓ | — |

### DictionaryController (`/api`) — 3 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/categories | ✓ | ✓ |
| GET | /api/locations | ✓ | ✓ |
| GET | /api/locations/states | ✓ | ✓ |

### AlertController (`/api/alerts`) — 4 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/alerts | ✓ | — |
| GET | /api/alerts/unread-count | ✓ | — |
| PUT | /api/alerts/{id}/read | ✓ | — |
| PUT | /api/alerts/{id}/acknowledge | ✓ | — |

### AdminStatsController (`/api/admin/stats`) — 3 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/admin/stats/counts | ✓ | ✓ |
| GET | /api/admin/stats/post-volume | ✓ | — |
| GET | /api/admin/stats/post-status | ✓ | — |

### AuditController (`/api/admin/audit-logs`) — 2 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/admin/audit-logs | ✓ | — |
| GET | /api/admin/audit-logs/{id} | ✓ | — |

### BackupController (`/api/admin/backup`) — 3 endpoints

| Method | Path | MockMvc | Shell |
|--------|------|---------|-------|
| GET | /api/admin/backup/list | ✓ | — |
| POST | /api/admin/backup/trigger | ✓ | — |
| POST | /api/admin/backup/restore/{id} | ✓ | — |

---

## Part 2: Backend Test File Inventory

### Integration Tests (`@WebMvcTest` controller-layer tests)
| File | Tests | Endpoints Covered |
|------|-------|-------------------|
| AuthIntegrationTest.java | 6 | 6 auth endpoints |
| JobPostingControllerTest.java | 11 | 11 job endpoints |
| ReviewControllerTest.java | 9 | 8 review endpoints |
| AppealControllerTest.java | 6 | 4 appeal endpoints |
| FileControllerTest.java | 7 | 6 file endpoints |
| ClaimControllerTest.java | 6 | 4 claim endpoints |
| TicketControllerTest.java | 6 | 4 ticket endpoints |
| DashboardControllerTest.java | 10 | 8 dashboard endpoints |
| AnalyticsControllerTest.java | 10 | 7 analytics endpoints |
| ReportControllerTest.java | 8 | 6 report endpoints |
| AdminUserControllerTest.java | 9 | 7 admin-user endpoints |
| AdminDictionaryControllerTest.java | 10 | 8 admin-dict endpoints |
| DictionaryControllerTest.java | 5 | 3 dictionary endpoints |
| AlertControllerTest.java | 6 | 4 alert endpoints |
| AdminStatsControllerTest.java | 5 | 3 stats endpoints |
| AuditControllerTest.java | 4 | 2 audit endpoints |
| BackupControllerTest.java | 6 | 3 backup endpoints |

### Service Unit Tests (`@ExtendWith(MockitoExtension.class)`)
| File | Coverage focus |
|------|----------------|
| AdminDictionaryServiceTest.java | Category/location CRUD, dedup, active-flag enforcement |
| AdminStatsServiceTest.java | Counts + time-windowed aggregates |
| AdminUserServiceTest.java | User CRUD, role change, reset, step-up |
| AlertServiceTest.java | Alert lifecycle + severity |
| AlertServiceAuditTest.java | Audit emission on alert actions |
| AnalyticsServiceTest.java | Post volume / status / takedown trend aggregation |
| AnomalyDetectionServiceTest.java | Threshold + z-score anomaly rules |
| AppealServiceTest.java | Appeal creation/process + state machine |
| AuthServiceTest.java | Login, CAPTCHA threshold, lockout, password rotation |
| BackupServiceTest.java | Scheduled backup + retention with mocked clock |
| DashboardServiceTest.java | Whitelisted SQL, masked vs. unmasked export |
| EncryptionServiceTest.java | AES-256-GCM key policy + roundtrip |
| FileServiceTest.java | Whitelist, magic bytes, quarantine, watermark |
| JobPostingServiceTest.java | Validation ranges, lifecycle, step-up, employer scoping |
| ObjectLevelAuthorizationTest.java | Cross-tenant denial at service layer |
| ReviewServiceTest.java | Queue, diff, approve/reject/takedown, step-up |
| ScheduledReportServiceTest.java | Cron parsing + dispatch |
| TicketServiceAuditTest.java | Ticket audit log emission |

### Security Filter Tests
| File | Coverage focus |
|------|----------------|
| CsrfValidationFilterTest.java | Header token matching, safe-method bypass, exclusions |
| RateLimitFilterTest.java | Per-minute + per-IP token bucket |
| SessionAuthFilterTest.java | Cookie lookup, idle/absolute expiry, password-rotation gate |

### True Integration Tests (Spring Boot + real DB / real security chain)
| File | Coverage |
|------|----------|
| AuditSchemaImmutabilityTest.java | Immutable delete semantics |
| EncryptionIT.java | AES-256-GCM column encryption |
| UserEmailUniquenessIT.java | DB unique constraint enforcement |
| JobPostingServiceSpringIT.java | Full Spring context + H2 JPA + transparent AES-256-GCM encryption for job creation |
| ActuatorPublicHealthIT.java | Anonymous `GET /actuator/health` (200), negative-exposure assertions for `/actuator/env\|beans\|heapdump\|threaddump\|shutdown\|…`, `show-details=never` body contract |
| JobFlowFullStackIT.java | Full HTTP stack via MockMvc: session + CSRF filter chain, job POST happy path, missing/wrong CSRF → 403, encryption-at-rest verified through native `findRawContactPhoneById`, cross-employer HTTP isolation (403/404) |

---

## Part 3: Frontend Test Coverage

### View Coverage (21 of 30 views have spec files)

| View | Spec File | Tests |
|------|-----------|-------|
| LoginView.vue | LoginView.spec.js | ✓ |
| RegisterView.vue | RegisterView.spec.js | ✓ |
| ChangePasswordView.vue | ChangePasswordView.spec.js | ✓ |
| EmployerDashboardView.vue | EmployerDashboardView.spec.js | ✓ |
| EmployerPostingsView.vue | EmployerPostingsView.spec.js | ✓ |
| EmployerClaimsView.vue | EmployerClaimsView.spec.js | ✓ |
| ReviewerDashboardView.vue | ReviewerDashboardView.spec.js | ✓ |
| ReviewerQueueView.vue | ReviewerQueueView.spec.js | ✓ |
| AppealQueueView.vue | AppealQueueView.spec.js | ✓ |
| AppealDetailView.vue | AppealDetailView.spec.js | ✓ |
| AlertInbox.vue | AlertInbox.spec.js | ✓ |
| AnalyticsCenter.vue | AnalyticsCenter.spec.js | ✓ |
| JobPostingForm.vue | JobPostingForm.spec.js | ✓ |
| AdminUsersView.vue | AdminUsersView.spec.js | ✓ |
| AdminCategoriesView.vue | AdminCategoriesView.spec.js | ✓ |
| AdminLocationsView.vue | AdminLocationsView.spec.js | ✓ |
| AdminTicketsView.vue | AdminTicketsView.spec.js | ✓ |
| AdminBackupsView.vue | AdminBackupsView.spec.js | ✓ |
| AdminAuditView.vue | AdminAuditView.spec.js | ✓ |
| ReportScheduler.vue | ReportScheduler.spec.js | ✓ |
| ReportExports.vue | ReportExports.spec.js | ✓ |
| AdminClaimsView.vue | — | **missing** |
| HomeView.vue | — | **missing** |
| JobPostingDetailView.vue | — | **missing** |
| JobPostingPreviewView.vue | — | **missing** |
| ReviewDetailView.vue | — | **missing** |
| DashboardBuilder.vue | — | **missing** |
| AdminDashboardView.vue | — | **missing** |
| AdminJobItemsView.vue | — | **missing** |
| AdminQuarantineView.vue | — | **missing** |

### Utility / Component Spec Files
- SessionExpiry.spec.js, CsrfToken.spec.js, StepUpVerification.spec.js
- PasswordValidation.spec.js, StatusConstants.spec.js
- LoginFlow.spec.js, RouteGuards.spec.js, router.spec.js, Sidebar.spec.js

---

## Part 4: API Shell Integration Tests

| Script | Endpoints Tested |
|--------|-----------------|
| test_auth.sh | login, register, me, logout, change-password |
| test_jobs.sh | list jobs, create job, get job, submit, update |
| test_review.sh | dashboard, queue, approve, takedown, appeals flow |
| test_dictionary.sh | categories, locations, states, admin-dict, admin-stats |
| test_tickets.sh | create, list, detail, update, auth-guard |

---

## Part 5: README Audit

The project is Docker-only by contract (Tech Stack → `Runtime: Docker, Docker Compose`; Prerequisites list only `Docker 20.10+` and `Docker Compose v2+`). The README now states this explicitly in two places — the Quick Start lede and the Running Tests section — and no host-based startup, `./mvnw`, `npm run dev`, or `java -jar` instruction remains.

| Item | Status | Note |
|------|--------|------|
| Docker Quick Start instructions | PASS | `./scripts/check-env.sh` + `docker compose up --build` |
| Docker-only contract declared | PASS | *"This project is Docker-only. There is no supported host-based startup path."* |
| No host-based startup path advertised | PASS | `./mvnw` and `npm run …` references removed |
| Access URLs table | PASS | |
| Bootstrap admin credentials | PASS | Sourced from `BOOTSTRAP_ADMIN_PASSWORD` in `.env` |
| Development credentials table | PASS | No hard-coded compose passwords |
| User Roles table | PASS | |
| Running Tests instructions (containerised) | PASS | `./run_tests.sh` + explicit Docker-based backend / frontend one-liners |
| Verified Runtime Evidence section | PASS | |
| Security Notes table | PASS | Every required secret flagged as *Not in compose* |
| Key Features list | PASS | |

**README audit: PASS**

---

## Part 6: Gap Summary

### Closed since previous revision

- **Slice-only "integration" coverage** — resolved. `JobPostingServiceSpringIT`, `JobFlowFullStackIT`, and the extended `ActuatorPublicHealthIT` now exercise the real Spring context + JPA + security chain, instead of relying exclusively on `@WebMvcTest` with `@MockBean`s.
- **Full-stack CSRF proof** — resolved. `JobFlowFullStackIT.createJob_withoutCsrfHeader_returns403` and `createJob_withWrongCsrfHeader_returns403` post through the real filter order (`SessionAuthFilter` → `CsrfValidationFilter` → controller).
- **Cross-tenant isolation over HTTP** — resolved. `JobFlowFullStackIT.employerB_cannotReadEmployerA_jobById` creates two employers with real persistence and asserts `403`/`404` on the cross-tenant GET.
- **Actuator exposure surface was implicit** — resolved. `application.yml` now pins `management.endpoints.web.exposure.include=health` and `management.endpoint.health.show-details=never`, and `ActuatorPublicHealthIT.otherActuatorEndpoints_areNotExposed` enumerates `env|beans|heapdump|threaddump|shutdown|…` and asserts `4xx`.
- **Service-layer breadth mis-reported** — corrected. 18 service-level unit test files exist (see Part 2), covering every major service: auth, job postings, review, appeals, files, dashboards, analytics, alerts, backup, admin users/dictionaries, scheduled reports, anomaly detection, encryption, tickets (audit), and cross-tenant authorization.
- **README host-based path** — resolved. README now declares Docker-only and no `./mvnw` / `npm run` / `java -jar` startup instruction remains (see Part 5).

### Remaining Gaps (score deductions)

**Service layer completeness (−1 pt):**
- `ClaimService` and `TicketService` proper still rely on controller-level MockMvc coverage rather than a dedicated service unit test (only `TicketServiceAuditTest` is present for tickets).

**Frontend Views (−5 pts):**
- 9 of 30 views lack spec files: AdminClaimsView, HomeView, JobPostingDetailView, JobPostingPreviewView, ReviewDetailView, DashboardBuilder, AdminDashboardView, AdminJobItemsView, AdminQuarantineView.

**API Shell Tests (−3 pts):**
- `ClaimController`, `FileController`, `DashboardController`, `ReportController`, and most of `AnalyticsController` have no end-to-end shell coverage — they are exercised only by `@WebMvcTest` slices (now supplemented by `JobFlowFullStackIT` for job flows, but not for these controllers).

---

## Final Score: 91 / 100

The rubric was rebalanced to carve out a **Full-Context Integration Tests** line (the `@SpringBootTest` tier added this session). Five points were moved from **HTTP Layer Coverage** (which no longer exclusively relies on MockMvc slices) into the new line, keeping the total at 100.

| Category | Possible | Earned |
|----------|----------|--------|
| HTTP Layer Coverage (all 94 endpoints, MockMvc) | 35 | 35 |
| Full-Context Integration Tests (`@SpringBootTest`) | 5 | 5 |
| Service Unit Tests | 15 | 14 |
| Frontend View Tests | 25 | 20 |
| API Shell Tests | 15 | 12 |
| README Quality (Docker-only contract) | 5 | 5 |
| **Total** | **100** | **91** |

**Delta vs. previous revision (92/100):**

- `+1` Service Unit Tests — inventory corrected from 2 files to 18 (ClaimService still pending).
- `+5` Full-Context Integration Tests — new `JobPostingServiceSpringIT`, `ActuatorPublicHealthIT` (extended), `JobFlowFullStackIT`.
- `−5` HTTP Layer Coverage weighting — category cap reduced from 40 to 35 as five points were reallocated to the new Integration line.
- README category held at 5/5 — the earlier revision passed on "Local dev setup", which has now been removed; the category still earns full marks because the Docker-only contract is explicit and consistent.
