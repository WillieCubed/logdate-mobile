# LogDate Android Launch Readiness Design

**Date:** 2026-08-01  
**Status:** Approved conversational design; written-spec review pending  
**Primary platform:** Android  
**Release target:** Google Play closed testing, distributed from `logdate.app`

## Objective

Prepare LogDate for a friend-facing Android beta in which a real user can install
the app, complete onboarding, optionally connect their single LogDate identity to
LogDate Cloud, capture and revisit every supported entry type, use the app through
network and Android lifecycle changes, and prove that encrypted Cloud sync and
backup restore real user data.

The release is complete only when the signed Play artifact, public website,
staging backend, production backend, and tested Android client agree. Repository
tests or local builds alone are not launch evidence.

## Product invariants

### Offline first under all circumstances

The local database and local media store are the source of truth. Network
availability must never be required to:

- finish local onboarding;
- create, edit, save, delete, or read an entry;
- record audio or capture/import photos and videos;
- browse Home, entry detail, Library, or previously generated Rewinds;
- preserve work through rotation, activity recreation, process death, or app
  restart; or
- inspect the last known Cloud state.

Remote-only operations such as creating a Cloud account, authenticating,
refreshing live quota, or transferring data may report that connectivity is
required. They must not block or degrade local capture. Failed remote work stays
durably queued and resumes later without requiring the user to repeat the local
action.

The UI must distinguish live remote data, cached remote data, pending local work,
and failures. It must never fabricate quota or synchronization success.

### One LogDate identity

Each user has one canonical LogDate identity. A Cloud account or compatible
server session is an authentication and replication binding for that identity,
not a second persona or a separate library.

A new offline installation may begin with one provisional local identity so it
can create content before authentication. That provisional identifier is not a
second user-facing account. Cloud signup promotes it to the canonical identity.
Signing in to an existing identity retires the provisional identifier and, when
local content exists, migrates that content through a durable, explicit adoption
flow. At no point does the app expose two active identities or two libraries.

- Signing out removes the active remote session while preserving the identity,
  local library, pending work, and readable data.
- Signing in restores or verifies the same identity.
- Changing servers changes the replication endpoint for the same identity.
- Once a canonical identity is established, a server response that identifies a
  different user must be rejected instead of silently replacing the identity or
  uploading the local library.
- Replacing the canonical identity is a separate, explicit, destructive
  reset/recovery operation with clear data consequences. It is not an account
  switcher.

Multiple authentication methods or passkeys may prove the same identity. The app
does not expose simultaneous identities, account profiles, or parallel account
libraries.

### Local data ownership

Local writes commit before any network work is scheduled. Cloud services may
replicate, back up, transcribe, enrich, or calculate quota for local data, but a
Cloud outage cannot revoke access to the user's journal. User-visible success for
a local write means the local transaction completed, not that Cloud accepted it.

## Current evidence and launch blockers

The 2026-08-01 audit established the following blockers:

| Area | Current evidence | Required correction |
| --- | --- | --- |
| Production Cloud | `cloud.logdate.app` and its direct Cloud Run service returned HTTP 503. | Restore production and verify database-backed health before client launch claims. |
| Staging Cloud | Health returned 200, but `/api/v1/server/info` advertised production origins, production RP ID, and `SELF_HOSTED`. | Make every revision receive the complete staging contract and identify as first-party staging. |
| Deployment | The reusable workflow deploys the image and `RELEASE_VERSION` but does not reapply the Terraform runtime/secret contract. | Make environment configuration authoritative on every candidate revision. |
| Android passkeys | Compact account screens are dead ends, request JSON is malformed, and Asset Links contains a signing placeholder. | Reconcile the existing auth-fix branch, publish exact certificates, and prove Credential Manager against installed artifacts. |
| Sync isolation | Cursors are backend-scoped rather than identity-scoped, while local data is global. | Scope remote metadata to canonical identity plus origin without creating multiple local identities. |
| Encrypted media | The client declares plaintext length for encrypted bytes, uses a local-only media key, advances cursors after failed downloads, and never deletes remote objects. | Make encrypted media round-trip, recover, retry, and delete correctly across devices. |
| Backup | Server backup routes exist, but Android has no complete upload/list/download/restore caller. | Implement and prove clean-install backup restore. |
| Entry model | A multi-block editor session saves unrelated notes with no parent entry ID. | Add a durable ordered entry aggregate and atomic publication. |
| Home/detail | Semantic moments can hide notes, Home has no entry click-through, older pagination can misgroup days, and tombstones can reappear. | Make every published entry visible exactly once and reachable by stable entry ID. |
| Lifecycle | Restored navigation is cleared, deep links can replay, editor state is memory-only, and media/import/Rewind work is composition-scoped. | Restore from durable IDs and move lasting work out of UI scopes. |
| Media/Library | Text focus races, photo capture requires microphone access, audio may attach to the wrong block, video review is inert, and Library search/errors are broken. | Complete real capture, playback, indexing, search, and recovery journeys. |
| Screenshots | Screenshot sources do not compile; tracked references include blank or nearly blank states; validation runs only on pull requests. | Repair the harness, add sanity checks, and gate direct-to-main changes. |
| Distribution | `logdate.app/download` links to an unpublished Play listing, release publishing credentials are incomplete, and Asset Links is invalid. | Complete Play configuration, publish closed testing, and expose a working opt-in/install path. |

Passing host server and sync tests do not invalidate these blockers because the
tests bypass the Android client, real Credential Manager, encrypted media client,
multiple-device restore path, and signed distribution artifact.

## Chosen delivery strategy

Use repair-in-place, release-gated vertical slices. Preserve working code and
reconcile existing unmerged fixes after rebasing and review. Do not perform a
wholesale rewrite, and do not ship a local-only preview with Cloud claims
disabled, because neither approach satisfies the launch objective efficiently.

Every slice must leave `main` shippable. Incomplete user-facing work remains
behind a feature flag. Each slice gets focused host tests, integration tests,
managed-device proof where relevant, and a visual review before the next slice
depends on it.

This document is the launch-program design, not one monolithic implementation
plan. After written review, implementation planning begins with Slice 1 only.
Each later slice receives its own bounded design amendment or specification and
test-first plan before code changes begin.

### Delivery slices

| Order | Slice | Outcome | Dependency |
| --- | --- | --- | --- |
| 1 | Cloud environment recovery | Staging and production deploy reproducibly; debug builds target staging and release builds target production. | None |
| 2 | Identity and authentication | One identity survives offline use, Cloud setup, sign-out, reauthentication, and compatible-server changes. | Slice 1 |
| 3 | Durable entry aggregate | One ordered text/media entry saves atomically, restores after lifecycle changes, and migrates legacy notes safely. | Identity contract |
| 4 | Sync, encrypted media, backup, and quota | Two devices replicate and restore real encrypted data with authoritative quota. | Slices 1-3 |
| 5 | Onboarding, Home, detail, editor, and Library | Every required local journey works without a network and every entry remains reachable. | Durable entry aggregate |
| 6 | Rewinds and persistent operations | Rewind generation and other lasting work resume after interruption. | Local data model |
| 7 | MD3 craft, accessibility, and screenshots | Supported layouts and states meet the visual and semantic quality gates. | Functional journeys |
| 8 | Distribution | The tested signed artifact is available through a working closed-test link on `logdate.app`. | All prior gates |

## Architecture

### Identity, authentication, and server selection

An offline installation creates one provisional local identity during
onboarding so all content has an owner before a Cloud account exists. Cloud
signup promotes that identifier as canonical. Cloud sign-in recovers the
existing canonical identity. If the installation has no local content, recovery
retires the provisional identifier immediately. If it has local content, a
durable migration rekeys and reassigns that content to the recovered identity,
then retires the provisional identifier. Content remains available throughout,
and there is still only one active identity and one library.

Remote credentials and sessions are stored by canonical server origin because
WebAuthn relying parties and tokens are server-specific. Every session record
also stores the canonical LogDate identity ID. A session is usable only when its
returned identity matches the local canonical identity, except during the
explicit provisional-to-canonical recovery transition above.

Changing to a compatible server follows this flow:

1. Discover the server and validate HTTPS, API version, capabilities, identity
   mechanism, and public descriptor.
2. Authenticate or register a server-side binding for the existing LogDate
   identity.
3. Verify that the server returns that same identity.
4. Show the user which endpoint will receive the local library and obtain an
   explicit confirmation.
5. Persist the selected origin and schedule replication from the local source of
   truth.

A failed discovery or identity mismatch leaves the current endpoint and local
data unchanged. The UI offers LogDate Cloud and one custom-server option; it does
not offer account profiles.

Sign-out calls the server's revocation endpoint when reachable. If revocation
cannot complete, the app reports the failure and allows a deliberate local
disconnect while retaining a durable revocation retry. Local sign-out never
deletes journal data or clears synchronization cursors for unrelated origins.

### Durable entry aggregate

Introduce a `JournalEntry` aggregate with:

- a stable `entryId`;
- canonical `identityId` ownership;
- draft or published lifecycle state;
- creation, update, publication, and optional deletion timestamps;
- ordered blocks with stable `blockId`, block type, position, metadata, and
  local media reference where applicable; and
- journal associations and synchronization version metadata.

Raw Markdown is canonical for text blocks. The editor applies inline Markdown
styling while preserving source characters, cursor behavior, selection, undo,
accessibility, and plain-text fallback. The reader uses the same parsing rules so
editing and detail presentation cannot disagree. Headings, emphasis, strong
emphasis, lists, block quotes, links, inline code, and fenced code are the launch
syntax set. No separate formatting toolbar is required for these constructs.

Creating an editor route creates and persists a draft entry immediately. Block
changes update that durable draft incrementally. Publishing changes the aggregate
to published state in one database transaction and schedules synchronization
after commit. A cancellation cannot expose a partially published multi-block
entry.

Legacy migration creates one published entry per legacy note, preserving the
legacy note ID as a deterministic association. Existing notes cannot be grouped
retroactively because the old schema discarded their editor-session identity.
The migration does not delete content or invent relationships.

### Home and detail

Home is an ordered projection of published entries, not of AI-generated moments.
Every non-deleted published `entryId` must appear exactly once across exhausted
pagination. Drafts and tombstones must not appear.

Semantic days and AI moments may organize or decorate entries, but their output
is checked against the authoritative entry set. Omitted entries are appended to
the appropriate day rather than hidden. Day keys from the configured semantic
boundary remain authoritative across pagination and detail queries.

Every entry card opens `EntryDetailRoute(entryId)`. Detail renders all ordered
blocks, journal associations, captions, Markdown, playable audio/video, images,
and defined loading, missing, error, retry, and back states. Deleting an open
entry transitions to a clear not-found state and removes it reactively from Home.

### Offline-first data flow

For every mutation:

1. Validate locally.
2. Commit the complete local transaction.
3. Update the visible local flow.
4. Enqueue durable remote work in the same transaction or through a
   transactionally coupled outbox.
5. Return local success to the user.
6. Let background work upload when authentication and connectivity permit.

Remote failures update outbox state and visible sync status; they do not roll
back local content. Downloads are staged, validated, decrypted, and committed in
local transactions before their cursors advance. Media failures remain retryable
without blocking unrelated text synchronization.

Generated Rewinds are stored locally. Generation reads local entries and runs as
durable unique work. Optional network enrichment may retry later, but viewing
existing Rewinds and producing the baseline launch Rewind cannot require Cloud.

### Android lifecycle durability

The navigation back stack is saveable and must not be cleared after restoration.
An incoming intent is assigned a consumption identity and handled once; activity
recreation cannot replay it.

ViewModels own screen business state across configuration changes. Saved state
stores only compact restoration keys such as `entryId`, selected block ID, cursor
or selection, active dialog, and scroll position. Large entry documents and
media never enter the saved-instance bundle; they are rebuilt from the local
database and files.

Work that matters after leaving a screen uses application-scoped repositories or
unique persistent work:

- entry publication and deletion;
- media import/finalization and cleanup;
- Cloud upload, download, and deletion;
- backup creation and restore;
- identity migration or server rebinding;
- Rewind generation; and
- long-running transcription that the product promises to finish.

Pending audio, photo, and video files are registered durably before capture or
import begins. Completion attaches media to a specific `entryId` and `blockId`.
Orphan cleanup only removes files that are neither attached nor referenced by a
pending operation.

### Sync, encryption, backup, and quota

Synchronization metadata is scoped by canonical identity, server origin, entity
type, and entity ID. Server ownership and uniqueness constraints include user
identity so one user's IDs cannot collide with another user's records.

Media encryption uses recoverable identity-bound key material. Each media object
has authenticated encryption metadata, ciphertext byte length, and a digest.
Another authenticated installation of the same LogDate identity can recover the
necessary key material and verify/decrypt the object. Cloud never needs plaintext
keys.

An upload declares ciphertext size and digest. A download advances its cursor
only after bytes are downloaded, hashed, decrypted, durably written, and linked
to the local block. Remote deletion removes database metadata and object storage
content, with idempotent retry.

Backup is distinct from live sync. Android can create, list, download, verify,
and restore encrypted point-in-time backups through the existing server backup
API. Restore supports a clean installation, is transactional or checkpointed,
and is safe to resume. A restored dataset remains fully usable offline before
any subsequent synchronization.

Quota comes from the authenticated server and counts the same stored objects and
backups that enforcement uses. The UI presents:

- live usage and limit with retrieval timestamp;
- cached last-known usage labeled as offline or stale;
- pending local uploads separately; or
- an explicit unavailable/error state.

The client never substitutes a fabricated limit.

### Cloud environment and build contract

`infra/terraform/staging.tfvars` and `production.tfvars` are the committed,
non-secret sources of truth for the complete runtime contract. Secret values
remain in Secret Manager. Every deployment renders and applies the full set of:

- project, region, service, image, and runtime service account;
- first-party deployment identity and public server descriptor;
- database and object-storage bindings;
- WebAuthn RP ID, canonical origin, Android allowed origins, and exact signing
  certificate fingerprints;
- CORS and HTTPS policy;
- scaling, resource, startup, and liveness settings;
- optional configured providers; and
- release SHA/version metadata.

The deployment creates a tagged candidate with no traffic. It uses authoritative
overwrite semantics for environment and secret bindings so a candidate cannot
inherit stale values. Before promotion, automation verifies migrations, internal
database health, public descriptor values, passkey signup and sign-in, protected
route behavior, object-storage access, and release SHA. Promotion directs traffic
to the tested candidate. Post-promotion failure automatically restores the prior
revision or emits an actionable rollback command.

Staging and production remain isolated:

| Contract | Staging | Production |
| --- | --- | --- |
| Host | `cloud-staging.logdate.app` | `cloud.logdate.app` |
| RP ID | `cloud-staging.logdate.app` | `logdate.app` |
| Canonical origin | `https://cloud-staging.logdate.app` | `https://cloud.logdate.app` |
| Project/service | `logdate-dev` / `logdate-server-staging` | `logdate` / `logdate-server` |
| Media bucket | `logdate-media-staging` | `logdate-media-logdate` |
| Migrations | Automatic only where explicitly configured | Explicit before candidate smoke |
| Android certificate | Debug certificate | Upload and Play app-signing certificates |

Android development builds target staging by default. Signed release builds
target production. CI and explicit local Gradle properties can override the
default without source edits. The user-level compatible-server selection takes
precedence at runtime after successful discovery and identity verification.

The public `assetlinks.json` files contain only real certificate fingerprints;
placeholders are deployment failures.

### Required user journeys

#### First run and Cloud offer

1. The user installs and launches LogDate with or without connectivity.
2. A provisional local identity and local storage initialize without a server.
3. Onboarding explains LogDate Cloud as optional backup/sync and offers Create
   account, Sign in, Continue offline, and recovery where applicable.
4. Offline users can finish onboarding; the Cloud action remains available in
   Settings later.
5. Connected users create or recover their one canonical identity through a real
   passkey ceremony and return to Home without a compact-layout dead end.

Plan and pricing cards come from the selected server's authoritative catalog.
Unconfigured billing or entitlement behavior is not presented as purchasable.

#### Entry capture and reading

- A new entry can contain any ordered combination of text, audio, photo, and
  video blocks.
- Text Markdown formats inline during editing and identically in detail.
- Photo capture requires camera permission only; video requests microphone only
  when audio capture needs it.
- Audio completion is bound to the initiating block and exposes errors,
  permission recovery, duration, playback, and transcription state.
- Video capture provides playable review before acceptance and playable detail
  after saving, including corrupt-media recovery.
- Rotation and activity recreation preserve the route, draft, block selection,
  and captured media.
- Saving makes the entry visible on Home and opens a complete detail view.

#### Library

The Library indexes local media as soon as it is durably attached, regardless of
upload state. It displays all supported media with real thumbnails, playback,
captions, search, selection semantics, loading, permission recovery, empty, and
error states. Gesture actions have accessible non-gesture alternatives.

#### Rewinds

The user can generate, see, resume, and experience a Rewind from local entries.
Concurrent requests are idempotent. A stopped process cannot leave a permanent
`PENDING` or `PROCESSING` record; durable work resumes or marks a recoverable
failure. Overview, detail, story progression, audio inclusion, and reduced-motion
behavior are covered.

#### Sign-out and server change

Sign-out clearly separates remote-session state from local identity and data.
The user can reauthenticate the same identity. A compatible-server change is an
explicit endpoint change for the same identity, with discovery, confirmation,
progress, and rollback; it is not an account switcher.

## Error handling and user trust

Every asynchronous surface has explicit idle/loading/success/empty/offline/stale/
error states as applicable. Failures include a recovery action when one exists.
The app does not collapse repository, permission, or decoding failures into a
cheerful empty state.

Sensitive upstream messages are mapped to stable user-facing language. Detailed
errors are logged with Napier without credentials, passkey material, encryption
keys, journal content, or private media paths.

Destructive actions identify what will be removed, require confirmation, and
preserve recoverability where the product promises it. Ordinary sign-out,
server failure, quota failure, or sync conflict is never data-destructive.

## Material 3, accessibility, and screenshot contract

Functional correctness precedes golden regeneration. Screenshot validation must
compile before references can be accepted.

The screenshot matrix covers:

- compact phone, landscape phone, tablet, and supported foldable postures;
- light and dark themes;
- font scaling and representative long/localized content;
- loading, populated, empty, offline, stale, permission-denied, error, and
  recovery states; and
- every launch journey named in this document.

Automated sanity checks reject fully uniform or unexpectedly low-information
images unless the scene is explicitly allowlisted as intentionally empty.
Screenshots run for direct pushes to `main`, not only pull requests.

Runtime semantic tests verify labels, roles, selected state, traversal, live
regions, minimum touch targets, keyboard/focus behavior, reduced motion, and
non-gesture alternatives. Technical test tags are not exposed as TalkBack
labels.

Contact sheets are reviewed after each functional slice. Golden updates must be
explained by the corresponding intended behavior; bulk acceptance is not a
review.

## Verification strategy

### Test layers

1. **Domain and repository tests** prove local-first ordering, atomic entry
   publication, migration, identity invariants, cursor rules, conflict behavior,
   quota state, and persistent work state machines.
2. **Server tests** prove ownership, encrypted-media metadata, deletion, backup,
   quota accounting, pagination, and deployment configuration validation.
3. **Server-client integration tests** use the production client data sources,
   encryption, and sync manager rather than raw plaintext API helpers.
4. **Gradle Managed Device tests** run real `MainActivity` journeys on phone and
   tablet. Focused foldable coverage uses managed/safe emulator targets.
5. **Live staging tests** use disposable identities and two independent emulator
   installations against the deployed staging revision.
6. **Production smoke** verifies configuration and authentication without using
   private journal content as a probe.
7. **Signed distribution proof** verifies the release AAB/APK signature,
   Play-hosted artifact, Digital Asset Links, and public install instructions.

Agent-driven Android runtime work uses emulators or Gradle Managed Devices only,
as required by repository device-safety policy.

### Requirement-to-evidence matrix

| Requirement | Authoritative evidence |
| --- | --- |
| Download from `logdate.app` | Public download page exposes a working closed-test opt-in/listing; signed Play artifact is available to enrolled testers. |
| Start onboarding offline | Fresh-install managed-device journey with network disabled reaches usable Home and retains one identity after restart. |
| Cloud offer and signup | Compact phone/tablet UI journey plus real staging Credential Manager signup, restart, and sign-in. |
| Text with inline Markdown | Parser/editor host tests, cursor/undo tests, editing/detail screenshots, and managed-device save/reopen journey. |
| Audio entry | Real emulator recording, permission denial/recovery, rotation, save, playback, Library, sync, and restore. |
| Photo entry | Camera-without-microphone journey, rotation, save, thumbnail/detail, Library, sync, and restore. |
| Video entry | Capture, playable review, save, playback/error recovery, Library, sync, and restore using valid media. |
| Every Home entry and detail | More than 50 mixed entries with semantic-day boundaries; persisted IDs equal Home-visible/detail-reachable IDs exactly once. |
| Lifecycle persistence | Activity recreation and saved-state restoration tests for editor, navigation, capture handoff, import, sync, and Rewind; durable draft/process restart proof. |
| Library | Seeded real local media plus permission/error/search/selection/playback journeys. |
| Rewinds | Seeded local generation, concurrent request, interruption/resume, overview/detail/story, audio, and reduced-motion journeys. |
| Real quota | Authenticated server response matches database/object storage before and after upload/delete; offline UI shows labeled cached data. |
| Sign-out and same-identity reauthentication | Server token revocation plus local offline continuity and identity equality after reauthentication. |
| Compatible server | Discovery, same-identity verification, explicit change, sync, restart persistence, and rollback on failure. |
| Sync | Two independent emulators create/update/delete offline, reconnect, paginate beyond 200, resolve conflicts, and compare canonical entry/media digests. |
| Backup/restore | Create encrypted backup, clean second installation, authenticate same identity, restore, verify every entry/block/media digest, then use fully offline. |
| Staging-to-production switch | Same client SHA built with staging and production configuration through one documented property; server descriptor asserts exact environment. |
| MD3 polish | Compiling screenshot matrix, nonblank sanity gate, contact-sheet review, semantics tests, and focused managed-device journeys. |

No individual unit suite, screenshot set, or local assembly can substitute for a
broader row in this matrix.

## Continuous integration and Git strategy

Changes land directly on `main` as complete logical slices. Before each commit:

- refresh and reconcile `main`;
- preserve unrelated worktree changes;
- write tests before implementation;
- run the smallest affected-module validation, then the required broader gate;
- review screenshot diffs when UI changed; and
- use the repository's atomic unstage-stage-commit chain with exact paths.

The existing `claude/logdate-auth-flows-audit-316126` work is an input, not an
integration unit. Each commit is rebased/reconciled, reviewed against this design,
tested, and landed only if it preserves one identity and offline-first behavior.

No Play upload occurs until every launch row above is proven. Production Cloud
health, Play closed-testing availability, and the public website are separate
external gates and are reported separately from repository health.

## Completion criteria

LogDate is ready for beta launch only when:

1. every required local journey works without connectivity;
2. one identity remains stable across onboarding, Cloud binding, sign-out,
   reauthentication, server change, sync, and restore;
3. every entry and media object survives local lifecycle changes and clean-device
   Cloud recovery;
4. staging and production are healthy, correctly configured, and reproducibly
   deployed;
5. the exact signed artifact passed functional, visual, accessibility, sync,
   backup, and quota gates;
6. enrolled friends can reach that artifact by following `logdate.app`; and
7. there is no unresolved requirement in the evidence matrix.

Only after these criteria are satisfied may the release be described as ready
for beta launch or the requested final ASCII-cow launch message be printed.
