# Sync V1 API (Launch Contract)

This document is the human-readable launch reference for sync endpoints.

Machine-readable canonical sources:
- `GET /openapi.json`
- `GET /openapi.yaml`
- `server/docs/openapi.md`

## Base
- `GET /api/v1/ops/sync/status`
- `GET /api/v1/ops/sync/metrics`
- `GET /api/v1/ops/sync/metrics/prometheus`
- `POST /api/v1/ops/sync/tombstones:purge`
- `PUT /api/v1/contents/{contentId}`
- `GET /api/v1/contents`
- `GET /api/v1/contents/{contentId}`
- `PATCH /api/v1/contents/{contentId}`
- `DELETE /api/v1/contents/{contentId}`
- `PUT /api/v1/journals/{journalId}`
- `GET /api/v1/journals`
- `GET /api/v1/journals/{journalId}`
- `PATCH /api/v1/journals/{journalId}`
- `DELETE /api/v1/journals/{journalId}`
- `PUT /api/v1/associations`
- `GET /api/v1/associations`
- `PUT /api/v1/associations/{journalId}/{contentId}`
- `DELETE /api/v1/associations/{journalId}/{contentId}`
- `DELETE /api/v1/associations`
- `POST /api/v1/media`
- `GET /api/v1/media/{mediaId}`
- `GET /api/v1/media/{mediaId}/binary`
- `DELETE /api/v1/media/{mediaId}`
- `POST /api/v1/backups`
- `GET /api/v1/backups`
- `GET /api/v1/backups/{backupId}`
- `GET /api/v1/backups/{backupId}/binary`
- `DELETE /api/v1/backups/{backupId}`

All endpoints require `Authorization: Bearer <access_token>`.

## REST Semantics
- `PUT` resource endpoints are idempotent upserts with path IDs as canonical identifiers.
- `PATCH` applies partial updates and returns `409 CONFLICT` on stale `versionConstraint`.
- `DELETE` returns `204 No Content` and is idempotent.
- Collection reads support `since` and optional `limit`; when `since` is omitted, the default is `0`.
- `POST` on collection endpoints creates server-generated IDs and returns `201 Created`.

## Media Upload Contract
`POST /api/v1/media` accepts `multipart/form-data`.

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
- `201 Created`
- `Location: /api/v1/media/{mediaId}`
```json
{
  "contentId": "note-1",
  "mediaId": "uuid-like-string",
  "downloadUrl": "https://...",
  "uploadedAt": 1730932569000
}
```

## Media Read Contract
- `GET /api/v1/media/{mediaId}` returns metadata JSON (`MediaMetadataResponse`).
- `GET /api/v1/media/{mediaId}/binary` returns raw media bytes with original content type.

## Backup Upload Contract
`POST /api/v1/backups` accepts `multipart/form-data`.

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
- `201 Created`
- `Location: /api/v1/backups/{backupId}`
```json
{
  "id": "backup-id",
  "createdAt": 1730932569000,
  "sizeBytes": 8192
}
```

## Backup Download Contract
- `GET /api/v1/backups/{backupId}` returns backup metadata.
- `GET /api/v1/backups/{backupId}/binary` returns `application/octet-stream`.

Storage behavior:
- If backup storage is not configured, server returns `503 BACKUP_STORAGE_UNAVAILABLE`.

## Maintenance Purge Contract
`POST /api/v1/ops/sync/tombstones:purge?retentionDays=30`

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
- `MISSING_PARAMETER`: Required path/query parameter is missing.
- `NOT_FOUND`: Requested sync resource does not exist for the authenticated account.
- `CONFLICT`: Stale version constraint on patch update.
- `MEDIA_ENCRYPT_FAILED`, `MEDIA_DECRYPT_FAILED`, `MEDIA_UPLOAD_FAILED`: Media pipeline failures.
- `BACKUP_STORAGE_UNAVAILABLE`: Backup storage not configured.
- `BACKUP_ENCRYPT_FAILED`, `BACKUP_DECRYPT_FAILED`: Backup crypto failures.
