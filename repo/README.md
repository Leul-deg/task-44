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

`docker-compose.yml` ships **no** default database passwords, encryption keys, or bootstrap credentials — every secret is sourced from a local `.env` file. A clean checkout therefore has no `.env`, and `docker compose up` would fail fast on missing variables. For first-run developers and CI/verification harnesses there is `scripts/bootstrap-env.sh`, which seeds `.env` from `.env.example` (placeholder values, loudly announced as **not** production-safe) only when `.env` is absent.

```bash
# First run on a clean checkout: seed .env from .env.example if missing.
# Safe to run every time — it is a no-op when .env already exists.
./scripts/bootstrap-env.sh

# Production / shared deployments: edit .env and replace every placeholder.
# Required: MYSQL_ROOT_PASSWORD, DB_PASSWORD, AES_SECRET_KEY (exactly 32 bytes — see .env.example),
# and BOOTSTRAP_ADMIN_PASSWORD (strong password, min length per SecretPolicyValidator).

# Optional: fail fast if any required variable is missing or AES key length is invalid
./scripts/check-env.sh

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

> **Note:** There are no hard-coded compose passwords. Use a strong `BOOTSTRAP_ADMIN_PASSWORD` in `.env` even for local work, and rotate it after first login if the environment is shared.

## User Roles

| Role     | Capabilities |
|----------|-------------|
| EMPLOYER | Create/edit/submit job postings, file appeals, create claims |
| REVIEWER | Approve/reject/takedown postings, process appeals |
| ADMIN    | User management, dictionaries, audit logs, backups, alerts |

## Running Tests

All tests run inside containers — there is no host-based Maven/Node path.

- All tests: `./run_tests.sh` (runs backend tests in a `maven:3.9-eclipse-temurin-17-alpine` container, frontend tests in a `node:18-alpine` container, and API shell tests against the compose stack; loads the same `.env` as Docker so `BOOTSTRAP_ADMIN_PASSWORD` / `JOBOPS_ADMIN_PASSWORD` are set for `API_tests/`)
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
| `AES_SECRET_KEY` | Column-level AES-256-GCM encryption key | **Not** in `docker-compose.yml` (required via `.env`) | Exactly **32 bytes**: 32 ASCII characters, or Base64 decoding to 32 raw bytes. Enforced at startup (`EncryptionService`, `SecretPolicyValidator`). |
| `DB_PASSWORD` | MySQL application user password | **Not** in compose | Set in `.env`; never commit `.env` |
| `MYSQL_ROOT_PASSWORD` | MySQL root password for Docker | **Not** in compose | Set in `.env` |
| `BOOTSTRAP_ADMIN_PASSWORD` | Initial admin login password | **Not** in compose | Set in `.env`; must meet length/complexity checks when policy is enabled |
| `COOKIE_SECURE` | Sets `Secure` flag on session cookies | Optional env | Set to `true` behind HTTPS |

**Docker Compose:** Secret literals are not embedded in `docker-compose.yml`; variables are passed through from your shell or `.env` only.

**CAPTCHA:** Challenge answers are held in an in-memory store on the backend process. This matches a single-node offline deployment; it is not shared across multiple backend replicas without further design.

> **Important:** Create `.env` from `.env.example` before `docker compose up`. Committing a filled-in `.env` must be avoided.

## Key Features
- **Job Lifecycle**: Full status machine (Draft → Review → Approve → Publish)
- **RBAC**: Role-based access on every endpoint (Employer/Reviewer/Admin)
- **Security**: Session-based auth, CAPTCHA, account lock, rate limiting, CSRF
- **AES-256 Encryption**: Column-level encryption for sensitive fields
- **Audit Trails**: Immutable logs with who/what/when/before/after
- **File Handling**: Upload validation, magic bytes, quarantine, PDF watermarks
- **Analytics**: Built-in dashboards, custom builder, scheduled reports, anomaly detection
- **Backup & Restore**: Encrypted nightly backups with admin restore workflow
