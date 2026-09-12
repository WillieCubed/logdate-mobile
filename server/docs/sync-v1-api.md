# Sync V1 API (Launch Contract)

This document is the human-readable launch reference for sync endpoints.

Canonical sources:
- `GET /docs` (interactive reference)
- `GET /openapi.json` and `GET /openapi.yaml` (machine-readable)
- `server/docs/openapi.md` (how the reference is maintained)

## Endpoint reference

Every sync endpoint, its parameters, multipart parts, examples and error codes are documented in
the interactive reference at `/docs` (Contents, Journals, Associations, Drafts, Media, Backups and
Sync status sections), and the sync model is explained in its overview. This page keeps only the
hidden maintenance contract, which is not published.

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

Every code each endpoint can return is listed on that endpoint in `/docs`, with what it means and
what to do about it. The overview's Errors section has the cross-cutting table.
