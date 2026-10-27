# Wear cloud companion acceptance

The Wear companion requires `deviceLinkingV1`, `conditionalCreateV1`, and `momentsV1` on the selected server. A descriptor alone does not establish that linked uploads, deletion, or revocation work.

## Observed hosted behavior — 2026-10-12

The same disposable passkey-account journey passed against:

| Environment                                     | Origin                              | Observed `/health` release                                |
| ----------------------------------------------- | ----------------------------------- | --------------------------------------------------------- |
| Staging                                         | `https://cloud-staging.logdate.app` | `logdate-server@bdf84d5dc1191e20cab1264e522210c1bbd33f4a` |
| Production backend used by internal Play builds | `https://cloud.logdate.app`         | `logdate-server@3a4288bf7ab10b25a483c5946655c4f3d562ccec` |

Each account was created and signed back in using the real WebAuthn verifier, then deleted with HTTP 204 after the journey. No retained user account or user content was used.

The journey proved:

- Phone mint/redeem creates a linked watch session owned by the disposable account; the sync-only watch cannot mint another link (403).
- Watch content and marked moments upload and appear unchanged in phone reads and durable change feeds; content HEAD succeeds.
- Duplicate strict creates return 412. Caption updates with a known version succeed; stale updates return 409.
- Phone moment deletion produces a tombstone; a strict watch retry returns 410.
- Removing the watch denies its access and refresh tokens (401), while the phone retains the uploaded capture.

Strict Wear create uses `?ifAbsent=true` and returns HTTP 410 for a deleted record. The hosted observations above used an older compatibility contract in which `If-None-Match: *` alone allowed same-ID recreation for Mac clients.

## Deletion recovery compatibility

Deletion-safe server candidates also reject ordinary same-ID writes to tombstoned entries, journals, associations and media with HTTP 409 and `DELETION_CONFLICT`. Saving a retained edit requires fresh identifiers and encryption contexts; the original tombstones remain intact.

The existing native Mac “Keep My Edit” path still retries deleted entries and journals under their original IDs. It must adopt fresh-copy recovery before these server changes are promoted to the shared production backend. Android internal publication and local client/server contract tests do not establish native Mac recovery acceptance.

These hosted observations establish the existing deployed protocol behavior. They **do not prove deployment of the newly added atomic repo/index transaction** or paired phone/watch UI and transport acceptance.

## Durable write acceptance

The server now places repo blocks/head changes and sync index changes in one PostgreSQL transaction for conditional creates, ordinary entry/journal/draft/association mutations, and moment creation/update/deletion. Failure evicts the writer's immutable block cache; transaction retries are disabled so callbacks are not replayed implicitly.

A disposable loopback PostgreSQL 16 instance ran all seven previously gated database suites (nine tests after adding two regression cases), with zero skips. Failure injected after indexing initially left a committed repo head; with the transaction boundary it leaves the original head and index unchanged, and a fresh repository can retry. Moment update and deletion failures preserve the previously committed body, version, and head.

Local evidence:

- `:server:test :server:ktlintCheck :server:detekt`: 934 tests, zero failures or skips.
- Real PostgreSQL client/server journeys: nine tests, zero failures or skips, covering watch linking/revocation, conditional create, marked-moment encrypted cloud reconciliation/Room download, and harness lifecycle/reconnection.
- `scripts/tests/deploy/test-wear-companion-smoke.py`: three checks with seven failure-injection variants, including revoked access/refresh remaining valid, missing tombstones, altered capture payloads, missing caption readback, readable deleted moments, or retained revoked device listings.
- Existing journal deployment smoke tests: nine checks passed.

## Deployment gate

`Deploy Server Cloud Run` now invokes `scripts/wear-companion-smoke.py` on the private candidate before traffic promotion, retaining the session and content in the existing permission-0600 deployment state. It verifies the same content/session after promotion, reads the changed caption from both phone and watch sessions, checks deleted content is unavailable, exercises deletion and revocation, and lets the existing strict smoke cleanup delete the disposable account. Failed candidate checks use the existing recovery cleanup path.

The final immutable feature-batch SHA must be deployed and pass these gates before the new durability fix can be considered delivered. Mobile publication remains restricted to the phone `internal` and Wear `wear:internal` tracks.
