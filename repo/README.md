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
cp .env.example .env
# edit .env and set strong values for MYSQL_ROOT_PASSWORD, DB_PASSWORD,
# AES_SECRET_KEY, and BOOTSTRAP_ADMIN_PASSWORD
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

## Local Run (without Docker)

### Requirements
- Java 17 (JDK)
- Maven 3.9+
- Node.js 18+ and npm
- MySQL 8.0 (including `mysql` and `mysqldump` CLI tools — required for backup and restore)

### 1. Database
```bash
# Start MySQL and create the database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS shiftworks;"
mysql -u root -p -e "CREATE USER IF NOT EXISTS 'shiftworks'@'localhost' IDENTIFIED BY '$DB_PASSWORD';"
mysql -u root -p -e "GRANT ALL PRIVILEGES ON shiftworks.* TO 'shiftworks'@'localhost'; FLUSH PRIVILEGES;"

# Load schema and reference data
mysql -u shiftworks -p"$DB_PASSWORD" shiftworks < init-db/01-schema.sql
```

### 2. Backend
```bash
cd backend

# Set required environment variables
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/shiftworks?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export SPRING_DATASOURCE_USERNAME=shiftworks
export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
export AES_SECRET_KEY="$AES_SECRET_KEY"
export BOOTSTRAP_ADMIN_PASSWORD="$BOOTSTRAP_ADMIN_PASSWORD"
export FILE_STORAGE_PATH=./uploads   # where uploaded files are stored
export BACKUP_PATH=./backups         # where mysqldump backup files are written

mkdir -p uploads backups
./mvnw -B clean package -DskipTests
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

## Bootstrap Admin

- Username: `admin`
- Password: value of `BOOTSTRAP_ADMIN_PASSWORD`
- This bootstrap credential is supplied from your local environment and is not committed in the repository.
- Additional users are provisioned through the Admin console (Admin → Users).

## User Roles

| Role     | Capabilities |
|----------|-------------|
| EMPLOYER | Create/edit/submit job postings, file appeals, create claims |
| REVIEWER | Approve/reject/takedown postings, process appeals |
| ADMIN    | User management, dictionaries, audit logs, backups, alerts |

## Running Tests
- All tests: `./run_tests.sh`
- Backend tests only: `cd backend && ./mvnw test`
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
├── run_tests.sh
└── README.md
```

## Security Notes

| Variable | Purpose | Default | Production Guidance |
|----------|---------|---------|---------------------|
| `AES_SECRET_KEY` | Column-level AES-256-GCM encryption key | none committed | Supply via local `.env` or environment and replace with a securely generated 32-byte key |
| `DB_PASSWORD` | MySQL application user password | none committed | Supply via local `.env`; do not commit it |
| `MYSQL_ROOT_PASSWORD` | MySQL root password for local Docker setup | none committed | Supply via local `.env`; do not commit it |
| `BOOTSTRAP_ADMIN_PASSWORD` | Initial admin login password used by bootstrap seeding | none committed | Supply via local `.env`; rotate after first login if used beyond local testing |
| `COOKIE_SECURE` | Sets `Secure` flag on session cookies | Not set (cookies sent over HTTP) | Set to `true` in any HTTPS deployment to prevent session token leakage over unencrypted connections |

> **Important**: No runtime secrets are committed. Create a local `.env` from `.env.example` before running Docker or local startup commands.

## Key Features
- **Job Lifecycle**: Full status machine (Draft → Review → Approve → Publish)
- **RBAC**: Role-based access on every endpoint (Employer/Reviewer/Admin)
- **Security**: Session-based auth, CAPTCHA, account lock, rate limiting, CSRF
- **AES-256 Encryption**: Column-level encryption for sensitive fields
- **Audit Trails**: Immutable logs with who/what/when/before/after
- **File Handling**: Upload validation, magic bytes, quarantine, PDF watermarks
- **Analytics**: Built-in dashboards, custom builder, scheduled reports, anomaly detection
- **Backup & Restore**: Encrypted nightly backups with admin restore workflow
