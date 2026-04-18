# ShiftWorks JobOps Platform

A full-stack offline-capable platform for managing part-time job postings with employer, reviewer, and administrator roles. Built with Spring Boot, Vue.js, and MySQL.

## Tech Stack

| Layer    | Technology                                   |
|----------|----------------------------------------------|
| Backend  | Java 17, Spring Boot 3, Spring Security, JPA |
| Frontend | Vue 3, Element Plus, Vite, vue-echarts        |
| Database | MySQL 8                                       |
| Runtime  | Docker, Docker Compose                        |

## Prerequisites

- Docker 20.10+
- Docker Compose v2+

## Quick Start

This project is Docker-only. All services (MySQL, backend, frontend) run inside containers and are orchestrated by `docker compose`. There is no supported host-based startup path.

`docker-compose.yml` carries **clearly-labelled placeholder defaults** for every required secret (`INSECURE_DEFAULT_REPLACE_VIA_DOTENV_*`, `INSECURE_32B_DEFAULT_REPLACE!!!!`) so that a clean checkout — including cloud CI/verification harnesses with no `.env` on disk — can run `docker compose up --build` and produce a healthy stack. These placeholders are unmistakable in logs and `docker compose config` and **must not** be used in any shared or production-like environment. For real deployments, create a local `.env` (which compose loads automatically and which overrides the placeholders) and replace every value.

```bash
# Cloud CI / first smoke test on a clean checkout — works with no .env present:
docker compose up --build

# Real deployments: copy the template, replace every value, then bring the stack up.
cp .env.example .env
# Required: set MYSQL_ROOT_PASSWORD, DB_PASSWORD, AES_SECRET_KEY (exactly 32 bytes — see .env.example),
# and BOOTSTRAP_ADMIN_PASSWORD (strong password, min length per SecretPolicyValidator).

# Optional helpers:
./scripts/bootstrap-env.sh    # seed .env from .env.example if missing (no-op if .env exists)
./scripts/check-env.sh        # fail fast if any required variable is missing or AES key length is invalid

docker compose up --build
```
Wait 30-60 seconds for all services to start. Verify with:
```bash
# All three containers should be Up / Healthy
docker compose ps

# Health check — login should return HTTP 200 with user JSON
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"$BOOTSTRAP_ADMIN_PASSWORD\"}"

# Seed verification — should show 1 admin user
docker compose exec mysql mysql -ushiftworks -p"$DB_PASSWORD" shiftworks \
  -e "SELECT username, role FROM users;"
```

## Access URLs

| Service  | URL                    |
|----------|------------------------|
| Frontend | http://localhost       |
| Backend  | http://localhost:8080  |
| MySQL    | localhost:3307         |

## Bootstrap Admin

- Username: `admin`
- Password: value of `BOOTSTRAP_ADMIN_PASSWORD`
- This bootstrap credential is supplied from your local environment and is not committed in the repository.
- Additional users are provisioned through the Admin console (Admin → Users).

## Development Credentials

| Role     | Username | Password / secret source | How to obtain |
|----------|----------|----------------------------|---------------|
| ADMIN    | `admin`  | Value of `BOOTSTRAP_ADMIN_PASSWORD` in your `.env` | Seeded on first backend boot from that variable |
| EMPLOYER | _(none)_ | _(create one)_             | Log in as admin → Admin → Users → Create User, set Role = EMPLOYER |
| REVIEWER | _(none)_ | _(create one)_             | Log in as admin → Admin → Users → Create User, set Role = REVIEWER |

> **Note:** `docker-compose.yml` carries clearly-labelled placeholder defaults (prefixed `INSECURE_DEFAULT_…` / `INSECURE_32B_DEFAULT_…`) so a clean checkout boots on cloud CI without an `.env`. They are **not** safe credentials — always create a real `.env` (which compose loads automatically and which overrides the placeholders) for any shared or production-like environment, and rotate `BOOTSTRAP_ADMIN_PASSWORD` after first login.

## User Roles

| Role     | Capabilities |
|----------|-------------|
| EMPLOYER | Create/edit/submit job postings, file appeals, create claims |
| REVIEWER | Approve/reject/takedown postings, process appeals |
| ADMIN    | User management, dictionaries, audit logs, backups, alerts |

## Running Tests

All tests run inside containers — there is no host-based Maven/Node path.

- All tests: `./run_tests.sh` (runs backend tests in a `maven:3.9-eclipse-temurin-17-alpine` container, frontend tests in a `node:18-alpine` container, and API shell tests against the compose stack; loads the same `.env` as Docker so `BOOTSTRAP_ADMIN_PASSWORD` / `JOBOPS_ADMIN_PASSWORD` are set for `API_tests/`). If the backend is not already reachable on `localhost:8080`, the script automatically runs `docker compose up -d --build`, waits for it to become healthy, runs the API tests, and tears the stack down on exit. Set `KEEP_STACK=1` to leave the stack running for follow-up inspection.
- Backend tests only: `docker run --rm -v "$(pwd)/backend:/workspace" -w /workspace maven:3.9-eclipse-temurin-17-alpine mvn test`
- Frontend tests only: `docker run --rm -v "$(pwd)/frontend:/workspace" -w /workspace node:18-alpine sh -c "npm ci --silent && npm test"`
- Individual API test: `./API_tests/test_auth.sh` (requires the compose stack to be running via `docker compose up -d` and `JOBOPS_ADMIN_PASSWORD` or `BOOTSTRAP_ADMIN_PASSWORD` set; see `API_tests/lib/test_env.sh`)

## Verified Runtime Evidence
The following was captured from a successful Docker Compose startup:

**Container status:**
```
NAME              STATUS                  PORTS
repo-mysql-1      Up 4 minutes (healthy)  0.0.0.0:3307->3306/tcp
repo-backend-1    Up About a minute       0.0.0.0:8080->8080/tcp
repo-frontend-1   Up About a minute       0.0.0.0:80->80/tcp
```

**Login response (HTTP 200):**
```json
{"user":{"id":1,"username":"admin","email":"admin@shiftworks.local","role":"ADMIN"},"csrfToken":"...","passwordExpired":false}
```

**Seed verification:**
```
username    role
admin       ADMIN
```

## Verification Steps
1. Login as admin (`admin` / your `BOOTSTRAP_ADMIN_PASSWORD`)
2. Create employer and reviewer users via Admin → Users
3. Login as employer, create a job posting
4. Submit the posting for review
5. Login as reviewer, approve the posting
6. Login as employer, publish (step-up password required)
7. Check Admin Dashboard for updated metrics and charts
8. Review Admin → Audit Log for recorded actions
9. Trigger a backup from Admin → Backups

## Project Structure
```
├── backend/            # Spring Boot API (Java 17, Spring Security, JPA)
├── frontend/           # Vue 3 + Element Plus + Vite
├── init-db/            # MySQL schema + seed data
├── API_tests/          # curl-based API test scripts
├── schema-reference.sql# Full schema reference
├── docker-compose.yml
├── .env.example        # Template for local-only secrets/config
├── scripts/check-env.sh  # Validates required env vars before compose (optional)
├── run_tests.sh
└── README.md
```

## Security Notes

| Variable | Purpose | In repository? | Guidance |
|----------|---------|----------------|----------|
| `AES_SECRET_KEY` | Column-level AES-256-GCM encryption key | Placeholder default in `docker-compose.yml` (`INSECURE_32B_DEFAULT_REPLACE!!!!`, exactly 32 chars) — overridden by `.env` | Exactly **32 bytes**: 32 ASCII characters, or Base64 decoding to 32 raw bytes. Enforced at startup (`EncryptionService`, `SecretPolicyValidator`). The placeholder satisfies the length check so the stack boots on cloud CI; rotate it for any real deployment. |
| `DB_PASSWORD` | MySQL application user password | Placeholder default `INSECURE_DEFAULT_REPLACE_VIA_DOTENV_app` in compose — overridden by `.env` | Replace via `.env`; never commit a filled-in `.env`. |
| `MYSQL_ROOT_PASSWORD` | MySQL root password for Docker | Placeholder default `INSECURE_DEFAULT_REPLACE_VIA_DOTENV_root` in compose — overridden by `.env` | Replace via `.env`. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Initial admin login password | Placeholder default `INSECURE_DEFAULT_REPLACE_VIA_DOTENV_admin` in compose — overridden by `.env` | Replace via `.env`; must meet length/complexity checks when policy is enabled. Rotate after first login on any shared environment. |
| `COOKIE_SECURE` | Sets `Secure` flag on session cookies | Optional env | Set to `true` behind HTTPS |

**Docker Compose:** `docker-compose.yml` carries clearly-labelled placeholder defaults (every default begins with `INSECURE_DEFAULT_…` or `INSECURE_32B_DEFAULT_…`) so a fresh checkout boots on cloud CI without any pre-staged `.env`. A local `.env` (when present) overrides every placeholder via standard compose substitution. The placeholders are visible verbatim in `docker compose config` and in container env, making them trivial to detect in any environment that should not be running them.

**CAPTCHA:** Challenge answers are held in an in-memory store on the backend process. This matches a single-node offline deployment; it is not shared across multiple backend replicas without further design.

> **Important:** Before any shared or production-like deployment, copy `.env.example` to `.env`, replace every value, and confirm with `./scripts/check-env.sh`. Do not commit a filled-in `.env`.

## Key Features
- **Job Lifecycle**: Full status machine (Draft → Review → Approve → Publish)
- **RBAC**: Role-based access on every endpoint (Employer/Reviewer/Admin)
- **Security**: Session-based auth, CAPTCHA, account lock, rate limiting, CSRF
- **AES-256 Encryption**: Column-level encryption for sensitive fields
- **Audit Trails**: Immutable logs with who/what/when/before/after
- **File Handling**: Upload validation, magic bytes, quarantine, PDF watermarks
- **Analytics**: Built-in dashboards, custom builder, scheduled reports, anomaly detection
- **Backup & Restore**: Encrypted nightly backups with admin restore workflow
