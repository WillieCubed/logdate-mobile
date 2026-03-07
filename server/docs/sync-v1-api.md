# Sync V1 API (Launch Contract)

This document is the source of truth for launch sync endpoints.

## Base
- `GET /api/v1/sync/status`
- `GET /api/v1/sync/metrics`
- `GET /api/v1/sync/metrics/prometheus`
- `POST /api/v1/sync/content`
- `GET /api/v1/sync/content/changes`
- `POST /api/v1/sync/content/{id}`
- `POST /api/v1/sync/content/{id}/delete`
- `POST /api/v1/sync/journals`
- `GET /api/v1/sync/journals/changes`
- `POST /api/v1/sync/journals/{id}`
- `POST /api/v1/sync/journals/{id}/delete`
- `POST /api/v1/sync/associations`
- `GET /api/v1/sync/associations/changes`
- `POST /api/v1/sync/associations/delete`
- `POST /api/v1/sync/media`
- `GET /api/v1/sync/media/{mediaId}`
- `POST /api/v1/sync/backups`
- `GET /api/v1/sync/backups`
- `GET /api/v1/sync/backups/{backupId}`
- `DELETE /api/v1/sync/backups/{backupId}`
- `POST /api/v1/sync/maintenance/purge`

All endpoints require `Authorization: Bearer <access_token>`.

## Media Upload Contract
`POST /api/v1/sync/media` accepts `multipart/form-data`.

Required parts:
- `contentId` (text)
- `fileName` (text)
- `mimeType` (text)
- `sizeBytes` (text; positive integer)
- `deviceId` (text)
- `data` (binary file part)

Validation:
- Missing fields return `400 VALIDATION_ERROR`.
- `sizeBytes` must match the actual binary payload size.

Response:
```json
{
  "contentId": "note-1",
  "mediaId": "uuid-like-string",
  "downloadUrl": "https://...",
  "uploadedAt": 1730932569000
}
```

## Backup Upload Contract
`POST /api/v1/sync/backups` accepts `multipart/form-data`.

Required parts:
- `deviceId` (text)
- `manifest` (text; JSON payload serialized as string)
- `data` (binary file part)

Validation:
- Missing fields return `400 VALIDATION_ERROR`.
- Empty payload returns `400 VALIDATION_ERROR`.

Storage behavior:
- If backup storage is not configured, server returns `503 BACKUP_STORAGE_UNAVAILABLE`.

Response:
```json
{
  "id": "backup-id",
  "createdAt": 1730932569000,
  "sizeBytes": 8192
}
```

## Backup Download Contract
`GET /api/v1/sync/backups/{backupId}` returns `application/octet-stream`.

Storage behavior:
- If backup storage is not configured, server returns `503 BACKUP_STORAGE_UNAVAILABLE`.

## Maintenance Purge Contract
`POST /api/v1/sync/maintenance/purge?retentionDays=30`

Rules:
- `retentionDays` defaults to `30` when omitted.
- `retentionDays` must be greater than `0`; otherwise `400 INVALID_PARAMETER`.
- `retentionDays` is clamped to `3650` max.

Response:
```json
{
  "contentPurged": 0,
  "journalPurged": 0,
  "associationPurged": 0,
  "mediaPurged": 0,
  "cutoff": 1730932569000,
  "retentionDaysApplied": 30
}
```

## Error Codes
- `UNAUTHORIZED`: Missing, invalid, or expired bearer token.
- `VALIDATION_ERROR`: Missing/invalid multipart fields.
- `INVALID_PARAMETER`: Invalid query parameter values.
- `NOT_FOUND`: Requested sync resource does not exist for the authenticated account.
- `MEDIA_ENCRYPT_FAILED`, `MEDIA_DECRYPT_FAILED`, `MEDIA_UPLOAD_FAILED`: Media pipeline failures.
- `BACKUP_STORAGE_UNAVAILABLE`: Backup storage not configured.
- `BACKUP_ENCRYPT_FAILED`, `BACKUP_DECRYPT_FAILED`: Backup crypto failures.
