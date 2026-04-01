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

## Quick Start (Docker — recommended)
```bash
docker compose up --build
```
Wait 30-60 seconds for all services to start. Verify with:
```bash
# All three containers should be Up / Healthy
docker compose ps

# Health check — login should return HTTP 200 with user JSON
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123456789"}'

# Seed verification — should show 1 admin user
docker compose exec mysql mysql -ushiftworks -pShiftW0rks2026 shiftworks \
  -e "SELECT username, role FROM users;"
```

## Local Run (without Docker)

### Requirements
- Java 17 (JDK)
- Maven 3.9+
- Node.js 18+ and npm
- MySQL 8.0

### 1. Database
```bash
# Start MySQL and create the database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS shiftworks;"
mysql -u root -p -e "CREATE USER IF NOT EXISTS 'shiftworks'@'localhost' IDENTIFIED BY 'ShiftW0rks2026';"
mysql -u root -p -e "GRANT ALL PRIVILEGES ON shiftworks.* TO 'shiftworks'@'localhost'; FLUSH PRIVILEGES;"

# Load schema and seed data
mysql -u shiftworks -pShiftW0rks2026 shiftworks < init-db/01-schema.sql
```

### 2. Backend
```bash
cd backend

# Set required environment variables
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/shiftworks?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export SPRING_DATASOURCE_USERNAME=shiftworks
export SPRING_DATASOURCE_PASSWORD=ShiftW0rks2026
export AES_SECRET_KEY=0123456789abcdef0123456789abcdef
export FILE_STORAGE_PATH=./uploads
export BACKUP_PATH=./backups

mkdir -p uploads backups
mvn -B clean package -DskipTests
java -jar target/*.jar
# Backend starts on http://localhost:8080
```

### 3. Frontend
```bash
cd frontend
npm install
npm run dev
# Frontend starts on http://localhost:5173 (Vite dev server)
# API calls proxy to http://localhost:8080
```

## Access URLs

| Service  | URL                    |
|----------|------------------------|
| Frontend | http://localhost       |
| Backend  | http://localhost:8080  |
| MySQL    | localhost:3307         |

## Default Credentials

| Username | Password        | Role  |
|----------|-----------------|-------|
| admin    | Admin@123456789 | ADMIN |

Users are provisioned exclusively through the Admin console (Admin → Users).

## User Roles

| Role     | Capabilities |
|----------|-------------|
| EMPLOYER | Create/edit/submit job postings, file appeals, create claims |
| REVIEWER | Approve/reject/takedown postings, process appeals |
| ADMIN    | User management, dictionaries, audit logs, backups, alerts |

## Running Tests
- All tests: `./run_tests.sh`
- Unit tests only: `cd backend && mvn test`
- Individual API test: `./API_tests/test_auth.sh`

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
1. Login as admin (`admin` / `Admin@123456789`)
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
├── .env                # Environment variables (AES key, DB password)
├── run_tests.sh
└── README.md
```

## Security Notes

| Variable | Purpose | Default | Production Guidance |
|----------|---------|---------|---------------------|
| `AES_SECRET_KEY` | Column-level AES-256-GCM encryption key | `ThisIsA32ByteKeyForAES256Enc!!` (dev only) | Replace with a securely generated 32-byte key via Docker secrets or `.env` excluded from version control |
| `COOKIE_SECURE` | Sets `Secure` flag on session cookies | Not set (cookies sent over HTTP) | Set to `true` in any HTTPS deployment to prevent session token leakage over unencrypted connections |

> **Important**: The default `AES_SECRET_KEY` in `docker-compose.yml` is for development/demo only. Never use it in production.

## Key Features
- **Job Lifecycle**: Full status machine (Draft → Review → Approve → Publish)
- **RBAC**: Role-based access on every endpoint (Employer/Reviewer/Admin)
- **Security**: Session-based auth, CAPTCHA, account lock, rate limiting, CSRF
- **AES-256 Encryption**: Column-level encryption for sensitive fields
- **Audit Trails**: Immutable logs with who/what/when/before/after
- **File Handling**: Upload validation, magic bytes, quarantine, PDF watermarks
- **Analytics**: Built-in dashboards, custom builder, scheduled reports, anomaly detection
- **Backup & Restore**: Encrypted nightly backups with admin restore workflow
