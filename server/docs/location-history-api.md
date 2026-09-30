# Encrypted location history

`/api/v1/location-history` uses the same Bearer access tokens as the other sync collections. The server derives the account from the validated token; callers cannot choose another account in the request.

`PUT` accepts `LocationHistoryBatchRequest.records`, with 1–100 unique IDs. Each record contains `id`, `recordType`, `payload`, `payloadSchemaVersion`, `deviceId`, `deviceVersion`, `expectedServerVersion`, and `deleted`. IDs and device IDs are limited to 128 characters. The accepted coarse types are `observation`, `activity`, `place`, `correction`, `memory-link`, and `manual`.

Nondeleted payloads must use the `LDSE2:` client encryption envelope and fit within 65,536 characters. The server checks this marker, not the encrypted contents or their truthfulness. Coordinates, place names, observation times, activity evidence, and correction values belong inside the ciphertext. Payload schema versions must be positive. The entire request body is limited to 7,000,000 bytes. Deleted records require a null payload.

A successful batch returns `LocationHistoryBatchResponse.records` with server versions. New records require `expectedServerVersion = 0`; edits require the current server version. Device versions identify immutable edits and must increase for successive changes made by the same device. A retry of the current device/version returns the first accepted record even if encryption used a new nonce. The retry must retain the same ID, type, schema version, and deleted state. A conflict rejects the entire batch with HTTP 409; malformed or invalid records receive HTTP 400.

`GET ?since=0&limit=100` returns `records`, `nextCursor`, and `hasMore`. The cursor is an account-specific committed sequence, never an observation timestamp. Clients apply a complete page and its cursor in one local transaction. The feed contains the latest state of each changed record, including tombstones. Follow `nextCursor` until `hasMore` is false. Page limits are 1–100.

Tombstones remain until account deletion. They cannot be resurrected, even by an upload that knows the tombstone's current server version. Deleting an ID that has never been uploaded also creates a tombstone. To create a replacement observation, use a new ID.

PostgreSQL stores records and account sequence rows separately. A transaction locks the account sequence row before checking versions and writing a batch, so writers on different server processes cannot publish versions out of order. Both tables cascade on account deletion. The existing collection endpoints and response shapes are unchanged.

## Client backfill and continuation

The client imports two pages of at most 100 samples per source owner/device/type in each sync pass. Source queries use ingestion time rather than the observation's time. Once a sweep reaches the end, the next periodic sweep starts from the beginning and checks existing IDs through an indexed lookup. This deliberately revisits earlier rows: same-millisecond inserts and clock changes cannot leave them permanently behind a watermark. Existing records and tombstones are never re-created by the importer.

This is bounded in memory, but it re-reads local history over successive sweeps. A newly inserted row behind the current watermark may wait until the next sweep. A durable ingestion sequence could make future imports more efficient. Upload and download each stop after ten pages and return `hasMorePending`; the Android worker retries and foreground sync schedules another pass without reporting a failed sync.
