## Architecture Overview

The platform uses a three-tier architecture: a Vue.js SPA served statically via nginx, a Spring Boot REST API backend, and a MySQL 8 database. All services are run in Docker Compose containers with clearly defined environment wiring, allowing local developers to stand up the full stack with a single command. The frontend communicates exclusively through `/api/*` endpoints, which nginx proxies to the backend, keeping the browser-side code offline-friendly without external API dependencies. The backend relies on Spring Data JPA for persistence and Spring Security for authentication, authorization, CSRF, and rate limiting. Scheduled jobs and background workers live in the Spring process, ensuring regular backups, anomaly detection, and report generation without additional services. File storage, backup artifacts, and uploads are co-located on shared Docker volumes so state persists across restarts. The layered architecture separates concerns cleanly and supports horizontal scaling of the backend + frontend containers when needed.

## Database Design

The database schema tracks 18 key tables. Authentication data lives in `users` (credentials, roles, statuses) and `user_sessions` (opaque session tokens, expiry metadata). The job lifecycle uses `job_postings`, `job_posting_tags`, and `job_posting_history` for the posting record, tag references, and immutable change logs. Review workflows rely on `review_actions` logging every reviewer decision and `appeals` capturing employer challenges. Disputes are tracked via `claims` (employer-submitted issues) and `tickets` (support escalations). Admin-managed dictionaries such as `categories` and `locations` supply referential data. Uploaded documents are described by `file_attachments` with checksum and status, while `dashboard_configs`, `scheduled_reports`, and `report_exports` power analytics delivery. An `alerts` table stores anomaly detection events, `audit_logs` keeps immutable action histories, and `backup_records` tracks nightly and manual backup metadata for restore operations.

## Auth & Security

Authentication is session-based: successful login issues an HTTP-only, SameSite=Strict cookie bearing an opaque UUID found in `user_sessions`. Passwords are salted and hashed with bcrypt, requiring at least 12 characters plus uppercase, lowercase, digits, and special characters. After three consecutive failures CAPTCHA is enforced, and after five failures accounts lock for 15 minutes. Sessions expire after 30 minutes idle or after 12 hours absolute lifetime. CSRF tokens accompany every mutating request and are validated through a filter chain. Critical operations (publishing, takedowns, role changes, backup restore/export) require step-up verification by re-entering the password. Rate limiting restricts authenticated traffic to roughly 60 requests per minute. Audit trails record every important action with IP address, before/after JSON, and actor metadata.

## Job Posting Lifecycle

Job postings traverse a strict state machine: DRAFT → PENDING_REVIEW → APPROVED → PUBLISHED. Employers start in DRAFT and can only edit postings while drafts. Submitting for review runs full field validation, then marks the post as PENDING_REVIEW. Reviewers approve (→ APPROVED) or reject (→ REJECTED with explicit rationale stored for the employer). Publishing requires step-up authentication and transitions APPROVED posts to PUBLISHED. Published posts can be unpublished by the employer or taken down by reviewers (with step-up) into TAKEN_DOWN, and takedown reasons are recorded. Employers may then appeal, sending the posting into APPEAL_PENDING; reviewers can grant (→ PUBLISHED again) or deny (→ TAKEN_DOWN) appeals, both captured in `job_posting_history` along with JSON snapshots for auditing.

## RBAC Model

The system exposes three roles: EMPLOYER, REVIEWER, and ADMIN. Authorization is enforced via Spring Security's `@PreAuthorize` annotations on every controller endpoint plus service-layer ownership checks. Employers can only manipulate their own postings, claims, and appeals. Reviewers are granted access to all reviewable postings, review queues, and pending appeals, while admins enjoy unrestricted full-access capabilities. Role transitions and sensitive admin operations require step-up verification and are logged via the audit service.

## File Handling

Uploaded files pass through a validation pipeline: allowed extensions (PDF, JPG, PNG), a 10 MB size cap, and magic-byte verification ensure content matches the claimed format. SHA-256 checksums provide tamper evidence, and records track upload status and quarantine state. Files failing the magic check are quarantined for admin review. Files are stored on the filesystem with path metadata persisted in `file_attachments`. When CSV exports are performed and PDFs are generated, Apache PDFBox stamps a watermark with the viewer's username and timestamp to mark sensitive downloads.

## Analytics

Built-in dashboards cover post volume, approval rates, handling time, reviewer activity, and claim success. Admins can also build custom dashboards by selecting metrics, dimensions, and filters, previewing the results instantly. Scheduled reports run via cron expressions and generate CSVs stored on disk; the scheduler logs each export in `report_exports`. An anomaly detection cron job runs every minute, calculating sigma thresholds across takedown spikes, low approval rates, claim spikes, and review backlogs, issuing alerts that appear in a dedicated admin inbox for acknowledgment. Custom dashboards and alerts are backed by reusable SQL builders that respect filters, joins, and date ranges.

## Audit System

Every significant event writes to `audit_logs` with the acting user, action name, entity type/id, before/after JSON, IP address, and timestamps. Audit log entries are append-only: there are no update or delete operations, ensuring an immutable trail. The admin interface offers filtering by entity, action, user, and time range with pagination. Clicking an audit row shows the JSON diff of the before/after state.

## Backup Strategy

Nightly backups use `mysqldump` piped through GZIP compression and AES-256-GCM encryption with a 12-byte IV. Each backup file stores the checksum alongside metadata in `backup_records`, and backups are kept for 30 days with automatic cleanup of expired files. Administrators can trigger manual backups via the UI, which also records the action in the audit log. The restore operation decrypts the backup, decompresses it, and streams it into `mysql`, requiring step-up password verification; errors are logged and surfaced to the admin.
