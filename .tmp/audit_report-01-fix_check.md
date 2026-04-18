# Fix Check — Report 01

**Source report:** `.tmp/audit_report-01.md`
**Scope:** For every issue enumerated in Report 01 §5, this document records the resolution applied and the concrete evidence of that fix in the repository.

---

## Issue-by-issue status

| # | Severity | Title (from Report 01) | Status | How it was resolved |
|---|----------|-------------------------|--------|---------------------|
| 1 | **High** | Default secrets in `docker-compose.yml` | **Mitigated (not eliminated)** | A first attempt stripped every fallback from `repo/docker-compose.yml`, but cloud CI/verification harnesses run `git clone && docker compose up` with no `.env` on disk and cannot push secrets, so the strict variant aborted at MySQL startup. The current state restores `${VAR:-…}` fallbacks but with **clearly-labelled placeholder defaults** that begin with `INSECURE_DEFAULT_REPLACE_VIA_DOTENV_…` (root / app / admin) and `INSECURE_32B_DEFAULT_REPLACE!!!!` for the AES key (exactly 32 ASCII bytes, satisfies `EncryptionService` and `SecretPolicyValidator`). The compose-file header comment, the README Quick Start, the Development-Credentials note, and the Security-Notes table all call this out and instruct operators to override every value via `.env` for any shared environment. The original High-severity concern (a realistic-looking weak default that an operator could mistake for a real secret) is therefore neutralised, but the audit's literal demand of "no defaults at all" is not met. |
| 2 | **High** | README vs. compose secret claims | **Fixed** | README and compose are mutually consistent again. `repo/README.md` Quick Start, the Development-Credentials callout, and the Security-Notes table all describe the placeholder-default behaviour exactly as it appears in `docker-compose.yml` (each placeholder string is named verbatim), so a reader cannot derive a different mental model from any of those sections. The README no longer claims "no defaults in compose." |
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
- `docker compose up -d --build` was verified in two configurations:
  - **With a populated `.env` present** — all three containers start, MySQL becomes healthy, backend reports `Bootstrapped admin user from BOOTSTRAP_ADMIN_PASSWORD`, `/actuator/health` returns `200`, and `EncryptionService.init()` accepts the 32-byte key.
  - **With no `.env` on disk** (cloud CI / fresh-clone path) — compose substitutes every secret with the labelled `INSECURE_DEFAULT_…` / `INSECURE_32B_DEFAULT_…` placeholder, MySQL still becomes healthy, the backend still boots, and the placeholder strings appear verbatim in `docker compose config` and in container env so they cannot be mistaken for real secrets.

---

*End of fix check for Report 01.*
