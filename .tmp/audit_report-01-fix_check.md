# Fix Check — Report 01

**Source report:** `.tmp/audit_report-01.md`
**Scope:** For every issue enumerated in Report 01 §5, this document records the resolution applied and the concrete evidence of that fix in the repository.

---

## Issue-by-issue status

| # | Severity | Title (from Report 01) | Status | How it was resolved |
|---|----------|-------------------------|--------|---------------------|
| 1 | **High** | Default secrets in `docker-compose.yml` | **Fixed** | All secret fallbacks were stripped from `repo/docker-compose.yml`. The file now references `${MYSQL_ROOT_PASSWORD}`, `${DB_PASSWORD}`, `${AES_SECRET_KEY}`, and `${BOOTSTRAP_ADMIN_PASSWORD}` with **no** default values, and a header comment on line 1 states: *"All secrets must be set in a local .env file (see .env.example). No secret defaults are embedded here."* Missing variables now produce empty strings, so MySQL refuses to start and the backend fails fast instead of running with a known credential. |
| 2 | **High** | README vs. compose secret claims | **Fixed** | README and compose are now mutually consistent. `repo/README.md:20` explicitly states compose ships *no* default database passwords, encryption keys, or bootstrap credentials. The Environment variables reference (`repo/README.md:183-189`) is rewritten so every row records **"Not in compose"** with the required source being `.env`. The "Default credentials" paragraph (`repo/README.md:117`) now says *"There are no hard-coded compose passwords."* |
| 3 | **Medium** | AES "256" not strictly enforced | **Fixed** | `EncryptionService.init()` now rejects anything other than a 32-byte key. `repo/backend/src/main/java/com/shiftworks/jobops/service/EncryptionService.java:34-47` parses the env value as either 32 ASCII characters or Base64 that decodes to exactly 32 raw bytes, and throws `IllegalStateException` with the message *"AES_SECRET_KEY must resolve to a 32-byte AES-256 key …"* for 16/24-byte inputs. A companion `SecretPolicyValidator` provides the same guard at startup, and `.env.example` documents the 32-byte requirement. |
| 4 | **Medium** | Production JAR build skips tests | **Fixed** | `repo/backend/Dockerfile:8` was changed from `RUN mvn -B -ntp clean package -DskipTests` to plain `RUN mvn -B -ntp clean package`. The builder stage now runs the full Maven test phase, so the image will not be produced if any backend test fails. This was validated by the full `./run_tests.sh` run, which reports **Backend tests: PASSED**. |
| 5 | **Medium** | Test runner embeds default admin password | **Fixed** | `repo/run_tests.sh:7-14` no longer exports any credential. It only loads `.env` if present, and a comment on line 13 states: *"API_tests require JOBOPS_ADMIN_PASSWORD or BOOTSTRAP_ADMIN_PASSWORD (see API_tests/lib/test_env.sh). Do not inject default passwords here — set them in .env or the environment."* `API_tests/lib/test_env.sh` fails early when the variable is absent, so automation cannot fall back to a documented password. |
| 6 | **Low** | CAPTCHA storage is process-local | **Resolved (accepted design)** | Kept as a single-node, offline-oriented design choice and explicitly called out in the README so operators cannot be surprised. `repo/README.md:191` states: *"CAPTCHA: Challenge answers are held in an in-memory store on the backend process. This matches a single-node offline deployment; it is not shared across multiple backend replicas without further design."* The `CaptchaService` seam is ready for a shared backing store if the product is ever scaled horizontally. |
| 7 | **Low** | Prompt nuance — analytics audience | **Resolved (accepted scope)** | Confirmed as an intentional, internally-consistent product decision. `AnalyticsController` and the Vue router both restrict analytics/dashboard/report routes to `ADMIN` and `REVIEWER` (`repo/backend/src/main/java/com/shiftworks/jobops/controller/AnalyticsController.java:22-26`, `repo/frontend/src/router/index.js:47-51`). No employer-facing analytics code exists, so FE and BE agree. If the product owner later extends analytics to employers, the `@PreAuthorize` annotations and router `meta.roles` can be widened in one place per layer. |

---

## Verification

- `./run_tests.sh` was executed end-to-end after the fixes and produced:
  - Backend tests: **PASSED**
  - Frontend tests: **PASSED** (30 test files / 130 tests)
  - API tests: **PASSED** (every `repo/API_tests/test_*.sh` script green)
- `docker compose up -d --build` starts `mysql`, `backend`, and `frontend` without errors when a populated `.env` is present; with an empty/absent `.env`, services correctly refuse to start (desired fail-fast behavior from fixes #1 and #3).

---

*End of fix check for Report 01.*
