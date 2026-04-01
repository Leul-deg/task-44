# ShiftWorks JobOps — API Specification

## Authentication

### POST /api/auth/login
- **Auth**: None
- **Body**: `{ "username": "string", "password": "string", "captchaId": "string?", "captchaAnswer": "string?" }`
- **Response**: `{ "user": {...}, "csrfToken": "string", "passwordExpired": boolean }
- **Errors**: 401 Unauthorized (invalid creds or captcha required, includes `captchaRequired` field)

### POST /api/auth/logout
- **Auth**: Required
- **Body**: None
- **Response**: `200 OK`
- **Errors**: 401 when not authenticated

### POST /api/auth/register
- **Auth**: None
- **Body**: `{ "username": "string", "email": "string", "password": "string" }`
- **Response**: `201 Created` user payload
- **Errors**: 400 bad password, 409 duplicate username/email

### GET /api/auth/captcha
- **Auth**: None
- **Body**: None
- **Response**: `{ "captchaId": "string", "image": "data:image/png;base64,..." }`

### POST /api/auth/change-password
- **Auth**: Required
- **Body**: `{ "newPassword": "string" }`
- **Response**: `200 OK`
- **Errors**: 401 unauthorized, 400 policy violations list

### GET /api/auth/me
- **Auth**: Required
- **Body**: None
- **Response**: `UserResponse`
- **Errors**: 401 unauthorized

## Job Postings

### GET /api/jobs
- **Auth**: Required
- **Body**: query parameters `status`, `categoryId`, `locationId`, `search`, `page`, `size`, `sort`, `direction`
- **Response**: `PageResponse<JobPostingResponse>` (paged postings for user with masked fields)
- **Errors**: 403 forbidden if role access violation

### GET /api/jobs/summary
- **Auth**: Required
- **Body**: None
- **Response**: `JobPostingSummaryResponse`

### GET /api/jobs/{id}
- **Auth**: Required
- **Body**: None
- **Response**: `JobPostingResponse` (masking phone for unauthorized users)
- **Errors**: 404 not found, 403 if access denied

### POST /api/jobs
- **Auth**: EMPLOYER
- **Body**: `JobPostingRequest` (title, description, categoryId, locationId, payType, settlementType, payAmount, headcount, weeklyHours, contactPhone, tags, validityStart, validityEnd)
- **Response**: `JobPostingResponse` (new draft)
- **Errors**: 400 validation failures

### PUT /api/jobs/{id}
- **Auth**: EMPLOYER
- **Body**: `JobPostingRequest`
- **Response**: Updated `JobPostingResponse`
- **Errors**: 400 invalid status/fields, 403 unauthorized, 404 not found

### POST /api/jobs/{id}/submit
- **Auth**: EMPLOYER
- **Body**: None
- **Response**: `200 OK`
- **Errors**: 400 invalid status, 403 unauthorized

### POST /api/jobs/{id}/publish
- **Auth**: EMPLOYER
- **Body**: `PublishJobRequest { "stepUpPassword": "string" }`
- **Response**: `200 OK`
- **Errors**: 400 invalid status, 403 step-up failed

### POST /api/jobs/{id}/unpublish
- **Auth**: EMPLOYER
- **Body**: None
- **Response**: `200 OK`
- **Errors**: 400 invalid status, 403 unauthorized

### GET /api/jobs/{id}/preview
- **Auth**: EMPLOYER
- **Body**: None
- **Response**: `JobPostingPreviewResponse`

### GET /api/jobs/{id}/history
- **Auth**: Required
- **Body**: None
- **Response**: `List<JobPostingHistoryResponse>`

### POST /api/jobs/{id}/contact-phone
- **Auth**: ADMIN
- **Body**: `StepUpPhoneRequest { "stepUpPassword": "string" }`
- **Response**: `{ "contactPhone": "string" }`
- **Errors**: 403 step-up failure, 404 not found

## Dictionaries

### GET /api/categories
- **Auth**: Required
- **Body**: None
- **Response**: `List<CategoryResponse>`

### GET /api/locations
- **Auth**: Required
- **Body**: Optional `state` query string
- **Response**: `List<LocationResponse>`

### GET /api/locations/states
- **Auth**: Required
- **Body**: None
- **Response**: `List<String>` (active states)

## Review

### GET /api/review/dashboard
- **Auth**: REVIEWER
- **Body**: None
- **Response**: `ReviewDashboardResponse`

### GET /api/review/queue
- **Auth**: REVIEWER
- **Body**: Query params `page`, `size`
- **Response**: `PageResponse<JobPostingResponse>`

### GET /api/review/jobs/{id}
- **Auth**: REVIEWER
- **Body**: None
- **Response**: `JobPostingResponse`

### GET /api/review/jobs/{id}/diff
- **Auth**: REVIEWER
- **Body**: None
- **Response**: `Map<String, FieldDiff>`

### GET /api/review/jobs/{id}/actions
- **Auth**: REVIEWER
- **Body**: None
- **Response**: `List<ReviewActionResponse>`

### POST /api/review/jobs/{id}/approve
- **Auth**: REVIEWER
- **Body**: `ReviewRationaleRequest { "rationale": "string" }`
- **Response**: `200 OK`

### POST /api/review/jobs/{id}/reject
- **Auth**: REVIEWER
- **Body**: `ReviewRejectRequest { "rationale": "string", "reviewerNotes": "string?" }`
- **Response**: `200 OK`

### POST /api/review/jobs/{id}/takedown
- **Auth**: REVIEWER
- **Body**: `TakedownRequest { "rationale": "string", "stepUpPassword": "string" }`
- **Response**: `200 OK`

## Appeals

### GET /api/appeals
- **Auth**: Required
- **Body**: Query params `status`, `page`, `size`
- **Response**: `PageResponse<AppealResponse>`

### POST /api/appeals
- **Auth**: EMPLOYER
- **Body**: `AppealCreateRequest { "jobPostingId": number, "appealReason": "string" }`
- **Response**: `200 OK`

### GET /api/appeals/{id}
- **Auth**: Required
- **Body**: None
- **Response**: `AppealDetailResponse`

### POST /api/appeals/{id}/process
- **Auth**: REVIEWER
- **Body**: `AppealProcessRequest { "decision": "GRANTED|DENIED", "reviewerRationale": "string" }`
- **Response**: `200 OK`

## Admin Users

### GET /api/admin/users
- **Auth**: ADMIN
- **Body**: query params `role`, `status`, `search`, `page`, `size`
- **Response**: `PageResponse<AdminUserResponse>`

### GET /api/admin/users/{id}
- **Auth**: ADMIN
- **Body**: None
- **Response**: `AdminUserResponse`

### POST /api/admin/users
- **Auth**: ADMIN
- **Body**: `AdminUserCreateRequest`
- **Response**: `AdminUserResponse`

### PUT /api/admin/users/{id}
- **Auth**: ADMIN
- **Body**: `AdminUserUpdateRequest`
- **Response**: `AdminUserResponse`

### PUT /api/admin/users/{id}/role
- **Auth**: ADMIN
- **Body**: `AdminUserRoleChangeRequest`
- **Response**: `200 OK`

### PUT /api/admin/users/{id}/unlock
- **Auth**: ADMIN
- **Body**: None
- **Response**: `200 OK`

### PUT /api/admin/users/{id}/reset-password
- **Auth**: ADMIN
- **Body**: None
- **Response**: `ResetPasswordResponse`

## Admin Dictionaries

### GET /api/admin/categories
- **Auth**: ADMIN
- **Body**: None
- **Response**: `List<AdminCategoryResponse>`

### POST /api/admin/categories
- **Auth**: ADMIN
- **Body**: `AdminCategoryRequest`
- **Response**: `AdminCategoryResponse`

### PUT /api/admin/categories/{id}
- **Auth**: ADMIN
- **Body**: `AdminCategoryRequest`
- **Response**: `AdminCategoryResponse`

### DELETE /api/admin/categories/{id}
- **Auth**: ADMIN
- **Body**: None
- **Response**: `200 OK`

### GET /api/admin/locations
- **Auth**: ADMIN
- **Body**: optional `state` query param
- **Response**: `List<AdminLocationResponse>`

### POST /api/admin/locations
- **Auth**: ADMIN
- **Body**: `AdminLocationRequest`
- **Response**: `AdminLocationResponse`

### PUT /api/admin/locations/{id}
- **Auth**: ADMIN
- **Body**: `AdminLocationRequest`
- **Response**: `AdminLocationResponse`

### DELETE /api/admin/locations/{id}
- **Auth**: ADMIN
- **Body**: None
- **Response**: `200 OK`

## Claims

### GET /api/claims
- **Auth**: Required
- **Body**: query params `status`, `page`, `size`
- **Response**: `PageResponse<ClaimResponse>`

### POST /api/claims
- **Auth**: EMPLOYER
- **Body**: `ClaimRequest`
- **Response**: `ClaimResponse`

### GET /api/claims/{id}
- **Auth**: Required
- **Body**: None
- **Response**: `ClaimResponse`

### PUT /api/claims/{id}
- **Auth**: ADMIN
- **Body**: `ClaimUpdateRequest`
- **Response**: `ClaimResponse`

## Tickets

### GET /api/tickets
- **Auth**: Required
- **Body**: query params `status`, `priority`, `page`, `size`
- **Response**: `PageResponse<TicketResponse>`

### POST /api/tickets
- **Auth**: Required
- **Body**: `TicketRequest`
- **Response**: `TicketResponse`

### GET /api/tickets/{id}
- **Auth**: Required
- **Body**: None
- **Response**: `TicketResponse`

### PUT /api/tickets/{id}
- **Auth**: ADMIN
- **Body**: `TicketUpdateRequest`
- **Response**: `TicketResponse`

## Files

### POST /api/files/upload
- **Auth**: Required
- **Body**: multipart form with `file`, `entityType`, `entityId`
- **Response**: `FileAttachmentResponse`

### GET /api/files/{id}/download
- **Auth**: Required
- **Body**: `export` query boolean
- **Response**: binary file + Content-Disposition header

### GET /api/files/entity/{entityType}/{entityId}
- **Auth**: Required
- **Body**: None
- **Response**: `List<FileAttachmentResponse>`

### GET /api/files/quarantined
- **Auth**: ADMIN
- **Body**: None
- **Response**: `List<FileAttachmentResponse>`

### PUT /api/files/{id}/release
- **Auth**: ADMIN
- **Body**: None
- **Response**: `200 OK`

### DELETE /api/files/{id}
- **Auth**: ADMIN
- **Body**: None
- **Response**: `200 OK`

## Analytics

### GET /api/analytics/post-volume
- **Auth**: ADMIN or REVIEWER
- **Body**: query params `from`, `to`
- **Response**: `List<PostVolumePoint>`

### GET /api/analytics/post-status-distribution
- **Auth**: ADMIN or REVIEWER
- **Body**: query params `from`, `to`
- **Response**: `List<PostStatusPoint>`

### GET /api/analytics/claim-success-rate
- **Auth**: ADMIN
- **Body**: query params `from`, `to`
- **Response**: `ClaimSuccessRateResponse`

### GET /api/analytics/avg-handling-time
- **Auth**: ADMIN or REVIEWER
- **Body**: query params `from`, `to`
- **Response**: `AverageHandlingTimeResponse`

### GET /api/analytics/reviewer-activity
- **Auth**: ADMIN
- **Body**: query params `from`, `to`
- **Response**: `List<ReviewerActivityPoint>`

### GET /api/analytics/approval-rate
- **Auth**: ADMIN or REVIEWER
- **Body**: query params `from`, `to`
- **Response**: `ApprovalRateResponse`

### GET /api/analytics/takedown-trend
- **Auth**: ADMIN or REVIEWER
- **Body**: query params `from`, `to`
- **Response**: `List<PostVolumePoint>`

## Dashboards

### GET /api/dashboards
- **Auth**: Required
- **Body**: None
- **Response**: `List<DashboardConfigResponse>`

### GET /api/dashboards/{id}
- **Auth**: Required
- **Body**: None
- **Response**: `DashboardConfigResponse`

### POST /api/dashboards
- **Auth**: Required
- **Body**: `DashboardRequest`
- **Response**: `DashboardConfigResponse`

### PUT /api/dashboards/{id}
- **Auth**: Required
- **Body**: `DashboardRequest`
- **Response**: `DashboardConfigResponse`

### DELETE /api/dashboards/{id}
- **Auth**: Required
- **Body**: None
- **Response**: `200 OK`

### GET /api/dashboards/{id}/data
- **Auth**: Required
- **Body**: None
- **Response**: `List<Map<String,Object>>`

### POST /api/dashboards/preview
- **Auth**: Required
- **Body**: `DashboardPreviewRequest`
- **Response**: `List<Map<String,Object>>`

### POST /api/dashboards/{id}/export
- **Auth**: Required
- **Body**: optional `ExportRequest`, query `masked`
- **Response**: CSV download

## Reports

### POST /api/reports/scheduled
- **Auth**: ADMIN or REVIEWER
- **Body**: `ScheduledReportRequest`
- **Response**: `ScheduledReportResponse`

### GET /api/reports/scheduled
- **Auth**: ADMIN or REVIEWER
- **Body**: None
- **Response**: `List<ScheduledReportResponse>`

### PUT /api/reports/scheduled/{id}
- **Auth**: ADMIN or REVIEWER
- **Body**: `ScheduledReportUpdateRequest`
- **Response**: `ScheduledReportResponse`

### DELETE /api/reports/scheduled/{id}
- **Auth**: ADMIN or REVIEWER
- **Body**: None
- **Response**: `200 OK`

### GET /api/reports/exports
- **Auth**: ADMIN or REVIEWER
- **Body**: None
- **Response**: `List<ReportExportResponse>`

### GET /api/reports/exports/{id}/download
- **Auth**: ADMIN or REVIEWER
- **Body**: None
- **Response**: CSV download


## Alerts

### GET /api/alerts
- **Auth**: ADMIN
- **Body**: query params `severity`, `is_read`, `page`, `size`
- **Response**: `Page<AlertPageResponse>`

### GET /api/alerts/unread-count
- **Auth**: ADMIN
- **Body**: None
- **Response**: `{ "count": number }`

### PUT /api/alerts/{id}/read
- **Auth**: ADMIN
- **Body**: None
- **Response**: `200 OK`

### PUT /api/alerts/{id}/acknowledge
- **Auth**: ADMIN
- **Body**: None
- **Response**: `200 OK`

## Audit

### GET /api/admin/audit-logs
- **Auth**: ADMIN
- **Body**: query params `entityType`, `entityId`, `userId`, `action`, `from`, `to`, `page`, `size`
- **Response**: `PageResponse<AuditLogResponse>`

### GET /api/admin/audit-logs/{id}
- **Auth**: ADMIN
- **Body**: None
- **Response**: `AuditLogDetailResponse`

## Backup

### POST /api/admin/backup/trigger
- **Auth**: ADMIN
- **Body**: `{ "stepUpPassword": "string" }`
- **Response**: `BackupRecord`

### POST /api/admin/backup/restore/{id}
- **Auth**: ADMIN
- **Body**: `{ "stepUpPassword": "string", "confirm": "RESTORE" }`
- **Response**: `{ "message": "Restore completed successfully" }`

### GET /api/admin/backup/list
- **Auth**: ADMIN
- **Body**: None
- **Response**: `List<BackupRecord>`
