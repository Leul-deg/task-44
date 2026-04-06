Required Document Description:
questions.md: Record all your questions during the process of understanding the Prompt. questions.md: Record all questions you have when understanding the business logic in the Prompt. Key points: Unclear business-level aspects such as business processes, business rules, data relationships, and boundary conditions. Format: Question + Your understanding/hypothesis + Solution

Definition of "Claims"
Question: The prompt mentions "claims and report tickets" and "claim success rate" in dashboards, but does not define what a "claim" is in this business context. Is it a worker claiming a job, an employer claiming compensation, or a dispute?
My Understanding: Since the system only serves employers, reviewers, and admins (no worker role), "claims" are employer-initiated complaints or issues related to job postings — for example, disputes about a posting's handling, reports of policy disagreements, or operational issues. "Claim success rate" measures the ratio of claims resolved to total claims filed.
Solution: Modeled claims as a generic issue entity tied to a job posting, with status tracking (OPEN → IN_PROGRESS → RESOLVED → CLOSED), assignable to admin users, and supporting file attachments.

Job Posting Lifecycle: Is "Approve" the Same as "Publish"?
Question: The prompt says the platform supports "create, approve, publish, and govern" as separate actions, but doesn't clarify who triggers publishing after approval or whether approval auto-publishes.
My Understanding: These are separate steps. A reviewer approves a posting (changing status to APPROVED), and then the employer manually publishes it when ready (changing status to PUBLISHED). This gives the employer control over timing.
Solution: Implemented a two-step flow: APPROVED (reviewer action) → PUBLISHED (employer action, requires step-up verification). This also aligns with the requirement that publishing is a "critical action" requiring password re-entry.

Tags: Free-Form or Dictionary-Based?
Question: The prompt mentions that each post supports "tags" but doesn't specify whether tags come from a predefined dictionary managed by admins or are free-form text entered by employers.
My Understanding: Since the prompt specifies admin-managed dictionaries for categories and locations but does not mention tags in that context, tags are free-form text entered by employers.
Solution: Implemented tags as free-form strings stored in a separate `job_posting_tags` table. Limited to 10 tags per posting and 30 characters per tag to prevent abuse.

"Job Items" in Admin Console
Question: The prompt says admins manage "job items" — is this a separate concept from job postings, or does it refer to admin-level management of all job postings?
My Understanding: "Job items" refers to the same job postings but from an administrative perspective. The admin needs a view of ALL postings across all employers with management capabilities (view, search, filter by status).
Solution: Admin endpoints provide access to all job postings with full filtering, while employer endpoints are scoped to only their own postings.

Side-by-Side Comparison Scope
Question: The prompt says reviewers have "side-by-side comparisons for edits." Does this mean comparing the current version to the immediately previous version, or to the original submission?
My Understanding: The comparison should be between the current submission and the most recent previous version. This is most useful when a rejected posting is edited and resubmitted — the reviewer wants to see what the employer changed.
Solution: When a posting is edited, a JSON snapshot of the pre-edit state is stored in `job_posting_history`. On the review page, the diff endpoint returns field-by-field old vs. new comparison. If no previous snapshot exists (first submission), no diff is shown.

Single Logout Behavior
Question: The prompt says "single logout that invalidates all active sessions for the user." Does this mean logging out from one device/browser logs the user out from all devices?
My Understanding: Yes. When a user clicks logout, ALL their active sessions across all devices/browsers are terminated immediately. This is a security measure to ensure no orphaned sessions remain.
Solution: On logout, all `user_sessions` records for that user are set to `is_valid = false`, and the session cookie is cleared on the current client.

CAPTCHA Implementation for Offline Use
Question: The prompt requires a "locally generated CAPTCHA" after 3 failed logins. What kind of CAPTCHA should be used since the system is offline (no reCAPTCHA)?
My Understanding: A simple image-based CAPTCHA generated entirely on the server. A random alphanumeric string is rendered as an image with noise/distortion and returned as a base64-encoded PNG.
Solution: Implemented a CaptchaService that generates a 5-character alphanumeric string, renders it on a 160×60 BufferedImage with random fonts, rotations, and noise lines. The answer is stored server-side with a 5-minute TTL, and the image is returned as base64.

Anomaly Detection Thresholds
Question: The prompt says "anomalies such as sudden spikes in takedowns or unusually low approval rates generate in-app alerts" but doesn't specify what constitutes an "anomaly" or what thresholds to use.
My Understanding: Anomalies should be statistically defined — a metric that deviates significantly from its historical average. The standard deviation approach is commonly used.
Solution: Hourly scheduled job compares current-day metrics against a 30-day rolling average. If a metric deviates by more than 2 standard deviations, a WARNING alert is generated. If deviation exceeds 3 standard deviations, a CRITICAL alert is generated.

Flat Pay Validation Bounds
Question: The prompt specifies hourly pay bounds ($12.00–$75.00) but doesn't specify bounds for flat-rate pay.
My Understanding: Flat-rate pay needs reasonable bounds too. Since flat pay covers entire shifts or jobs, it should allow a wider range than hourly.
Solution: Set flat-rate pay bounds to $50.00–$10,000.00, covering single-shift jobs through multi-day flat-rate contracts.

PDF Watermark Content
Question: The prompt says "mandatory watermarks on exported PDFs" but doesn't specify what the watermark should contain.
My Understanding: The watermark should serve as a traceability measure, identifying who exported the document and when, plus a confidentiality notice.
Solution: Diagonal semi-transparent text watermark: "CONFIDENTIAL — {username} — {ISO timestamp}" applied to each page of the PDF using Apache PDFBox.

Report Masking Scope
Question: The prompt says "download masked exports" — what fields should be masked?
My Understanding: Any field containing sensitive personal information should be masked in exports by default: contact phone numbers, email addresses, and any PII. The masked version shows partial data (e.g., last 4 digits of phone).
Solution: Export service applies masking to phone numbers and email addresses by default. Unmasked exports require ADMIN role and step-up verification.

Password Rotation Enforcement
Question: The prompt says passwords "must be rotated every 90 days." Should the system block access entirely or just warn/force a password change?
My Understanding: The system should force a password change but not completely lock the user out. On login, if the password is older than 90 days, the user should be redirected to a mandatory password change page.
Solution: On login, if `password_changed_at` is older than 90 days, the response includes `passwordExpired: true`. The frontend redirects to the change-password page. The user cannot access any other functionality until the password is changed.
