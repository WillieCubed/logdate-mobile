# LogDate Android Launch Readiness Design

**Date:** 2026-08-01  
**Status:** Approved for implementation planning; review findings incorporated
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

A new offline installation begins with exactly one local identity state so it can
create content before authentication. Cloud signup binds that same identity.
Signing in to an existing Cloud identity may replace the local-only identity only
through the atomic adoption state machine defined below. A recovered identity may
be staged and verified, but it is never active or usable for content before the
single cutover transaction. At no point does the app have two active identities or
two libraries.

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

The identity model has one authoritative slot, not a collection of profiles. A
singleton `library_owner` record contains `activeIdentityId`, lifecycle kind,
public signing key, and active encryption-keyset ID. Content refers to the stable
owner slot rather than copying changeable identity IDs into every row. Database
constraints permit exactly one owner record, and all repositories require it
before the first content transaction.

Initialization creates a random P-256 root identity keypair, a root-certified
device signing key, and 256-bit encryption root with the platform cryptographic
random source before first-run navigation. The canonical ID is the versioned
SHA-256 digest of the canonical root public key. Root, device, and encryption
private material is immediately wrapped by an Android Keystore key. It is never
derived from a UUID, username, server response, or other non-secret value.

The normative identity state machine is:

| Current state | Event | Committed result |
| --- | --- | --- |
| No owner | First database initialization, including concurrent attempts | One `LOCAL` active identity; uniqueness makes every later initializer read the winner. |
| `LOCAL` | Create Cloud account | The selected server verifies an origin-bound challenge signature and binds the same ID; one atomic transaction marks the owner `CANONICAL`, installs the session, and retargets unsent outbox work. |
| `LOCAL` | Sign in to an existing Cloud account | The recovered candidate is staged, authenticated, unwrapped, and verified while `LOCAL` remains the sole active identity; after explicit adoption consent, the transaction replaces the one active ID and retargets the library and outbox atomically. |
| `CANONICAL`, signed out | Reauthenticate | The returned ID and recovered public key must equal `activeIdentityId`; only the session changes. |
| `CANONICAL`, signed in or out | Bind a compatible server | The server proves a binding to the same identity; one transaction changes only the active replication origin and session. |
| Any active state | Candidate mismatch, failed proof, crash before cutover, or canceled flow | No owner, content, outbox, cursor, keyset, or active-origin change. |
| Any active state | Explicit destructive reset | After confirmation and any requested export, local content, sessions, keys, and the owner are removed together; the next initialization creates one new local identity. This is not account switching. |

`pending_identity_transition` may hold a sealed candidate, recovery progress, and
idempotency token. It is never queried as an owner and cannot authorize reads,
writes, sync, or UI. During existing-account adoption, local capture continues
against the current owner. Key rewrapping is staged and repeats until current;
the final short local transaction covers the remaining delta, swaps the owner,
retargets unsent operations, and records the idempotency token. A crash either
observes the complete old state or the complete new state. After a committed
cutover, the superseded local secret is securely discarded and its identifier is
retained only as a non-authoritative migration alias for deduplication and audit.

Authentication alone never authorizes adoption. Before cutover, the UI names the
destination account and origin, counts the local entries and media that will join
it, explains that the current local identity will be retired, and offers Cancel
and encrypted export. The user gives a separate confirmation. An empty-library
fast path still shows the destination and identity consequence but has no content
migration. Canceling leaves the local owner and candidate-isolated session
unchanged.

Cloud signup binds the root public key and current root-certified device key to
the account with both the WebAuthn session and an origin-bound identity challenge.
WebAuthn proves access to the server account; the certified device signature
proves ownership of the portable LogDate identity. A server API is never allowed
to assign or silently replace the active identity ID. A server may keep an
internal account-row identifier, but it is an origin-scoped implementation key,
is never presented as a LogDate identity, and cannot own or namespace
synchronized content independently of the canonical ID.

Remote credentials are server-specific, but only one server session is active.
A token created while testing a transition is sealed with that transition and
cannot be used by normal repositories. Every response carrying identity data is
checked against the active ID, except for the isolated candidate-recovery step;
the final cutover still requires verified key ownership.

Changing to a compatible server is a beta requirement because the product must
support a user-selected production backend. LogDate Compatible Server Protocol
v1 is a versioned conformance contract. At launch, automated evidence covers the
first-party deployments and a separately hosted reference-compatible server;
the app does not claim that an arbitrary URL is conformant merely because it
returns a descriptor.

First use pins the descriptor signing key after showing its fingerprint and host
to the user. Rotation requires a new descriptor cross-signed by the pinned key or
an explicit fingerprint reconfirmation. A compatible passkey server must publish
a live `/.well-known/assetlinks.json` entry for the exact LogDate package and
installed signing certificate and must advertise the matching RP ID and Android
origin allowlist. Discovery rejects a host before Credential Manager opens if
these checks or the required protocol capabilities fail.

The server-change flow is:

1. Discover the server over HTTPS and validate API version, capabilities, public
   descriptor, and descriptor signing key.
2. Obtain an origin-bound, nonce-bearing binding challenge.
3. Sign the challenge with the active identity key and authenticate or register
   the server-local passkey credential.
4. Verify the server-signed binding receipt contains the nonce, selected origin,
   exact `activeIdentityId`, and expiry.
5. Upload the current signed recovery envelope to the candidate server, read the
   exact bytes back, decrypt and verify them locally, and prove account-recovery
   challenge support without consulting the prior origin.
6. Show which endpoint will receive the local library and obtain explicit
   confirmation.
7. Atomically change the active origin, then schedule a full identity-scoped
   reconciliation. Live sync, encrypted content/media, backup, quota, and restore
   must pass the same versioned contract suite against the compatible server.

A failed discovery, replayed nonce, invalid descriptor, or identity mismatch
leaves the current endpoint and local data unchanged. The UI offers LogDate Cloud
and one custom-server option; it does not offer account profiles. The prior
origin remains a rollback target only until the new-origin reconciliation
commits, and is not exposed as a second active account.

Sign-out calls the server's revocation endpoint when reachable. If revocation
cannot complete, the app reports the failure and allows a deliberate local
disconnect while retaining one durable, origin-scoped revocation retry. Local
sign-out never deletes journal data, replaces the identity, or discards pending
content work.

This contract intentionally supersedes the current identity document's
dual-ID/background-remapping model, ID-derived key material, and unrestricted
identity replacement APIs. Slice 2 must replace those sections and remove or
constrain the corresponding implementation interfaces in the same shippable
commit.

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

Each local mutation has an immutable operation ID, stable device ID, increasing
device sequence, base server version, and optional causal-parent operation ID.
Several offline mutations of one entity form one ordered local chain. The outbox
sends that chain in order; the server treats an accepted parent result as the
child's effective base, so same-device create/edit/edit/delete sequences cannot
self-conflict. Cross-device divergence from the chain's original server base uses
the conflict rules below. Launch code does not squash chains; any future
coalescing must preserve the root base, terminal state, and operation-ID
deduplication semantics. Upload retries use the same IDs, and the server
deduplicates them.

Download APIs return server-issued opaque cursors backed by a strictly monotonic,
tie-free change sequence; client timestamps are never cursors. Cursors are
independent per identity, origin, and stream. A page and its next cursor are
committed in the same local transaction. Replaying a page after a crash is
idempotent.

Entry metadata and media transfer progress are separate streams. Committing a
media-metadata page creates or updates a durable object download operation, so a
failed large download cannot hide later entry changes. The media object's own
checkpoint is complete only after its ciphertext, digest, decryption, durable
file, and block link have all committed. Authentication expiry pauses the outbox
without consuming operations or cursors and resumes after same-identity
reauthentication.

An expired cursor starts a server-side consistent snapshot session with a
snapshot ID and fixed high-water sequence. Pages enumerate the complete live set
at that boundary; absence is authoritative deletion for a previously synchronized
entity. The client writes pages and their digests to shadow reconciliation tables
while keeping the old active dataset, cursors, and unsent local chains intact.
After every page verifies, one transaction replaces synchronized bases, turns
snapshot absences into tombstones, derives media jobs, rebases but does not erase
local chains, and advances the cursor to the snapshot high-water. Local visible
state continues to include those pending chains; later upload invokes normal
conflict preservation. A crash before the transaction resumes the snapshot or
discards its shadow state, while a crash afterward sees the complete new base.

The launch conflict unit is the complete `JournalEntry` aggregate; ordered blocks
are never spliced heuristically. The first valid operation against a base version
advances the canonical version. A concurrent non-delete operation is preserved
as a visible recovered-copy entry with a deterministic ID derived from its
operation ID. Deletion wins for the original ID, while any concurrent edited
content is preserved as a recovered copy. Journal-association conflicts use
set-union for additions and explicit tombstones for removals. Conflict creation
is server-authoritative, idempotent, synchronized to every device, and surfaced
to the user; no losing content is silently discarded.

Generated Rewinds are stored locally. Generation reads local entries and runs as
durable unique work. Optional network enrichment may retry later, but viewing
existing Rewinds and producing the baseline launch Rewind cannot require Cloud.

### Android lifecycle durability

The navigation back stack is saveable and must not be cleared after restoration.
An incoming intent is assigned a consumption identity and handled once; activity
recreation cannot replay it.

At the Activity boundary, every initial or `onNewIntent` delivery receives a
generated delivery token before dispatch. The token is injected into the current
intent, copied into saved state, and written with the normalized payload to a
durable `intent_delivery` ledger. Recreation reuses the saved token when the
payload fingerprint matches; a genuinely new external delivery without saved
state gets a new token. Any content mutation uses that token as its idempotency
key and commits the result plus `APPLIED` ledger state together. Navigation-only
delivery restores or focuses the same route. Repeated delivery after recreation,
process death, or cold task restoration therefore cannot duplicate an import,
entry, or route-stack side effect. Completed ledger entries expire only after the
corresponding durable operation is no longer replayable.

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

Synchronization metadata is scoped by canonical identity, server origin, stream,
entity type, and entity ID. Server ownership and uniqueness constraints include
identity so one user's IDs cannot collide with another user's records. The server
stores an append-only change sequence long enough for every supported offline
window; a cursor older than retention starts the snapshot/high-water
reconciliation protocol above rather than returning empty or partial success.

The identity secret bundle contains the P-256 root identity private key, current
root-certified device keys, and a random 256-bit content root. Cloud recovery uses
a separate random 256-bit recovery key. Its checksummed code and QR payload encode
the protocol version, canonical identity ID, and key; they never encode journal
plaintext. HKDF-SHA-256 derives an AES-256-GCM wrapping key. The envelope version,
canonical identity ID, root public key, delegated-key certificates, content-key
IDs, and algorithm suite are authenticated data. The client uploads only the
root-signed encrypted envelope and verifies byte-for-byte readback. The user must
confirm the recovery code before Cloud backup is reported as recoverable.

On a clean device, normal recovery combines the server passkey ceremony with the
recovery code. If every passkey is lost, the identity ID in the code may retrieve
only the rate-limited opaque envelope; after local decryption, an origin-bound
root-key challenge authorizes registering a replacement passkey. If the code is
lost but an authenticated device remains, that device can rotate the recovery key
and confirm a new code. A passkey without the code cannot decrypt a clean-device
backup; a code without the matching envelope or root proof cannot claim a
different identity. Losing all initialized devices and the recovery code is
unrecoverable. These cases are explained before backup is enabled and exercised
in recovery tests.

Recovery verifies the envelope signature, decrypts locally, validates every
certificate, and confirms that the recovered root public key hashes to the
canonical identity ID. A wrong code, corrupt envelope, wrong identity, expired
challenge, or server substitution fails before changing the owner or library.

The envelope is a versioned keyring. Content-root rotation creates a new random
root, uploads and verifies the rewrapped keyring before marking the new key ID
active, and retains old roots read-only until every referenced object has been
re-encrypted and verified. Device signing keys can be added, revoked, and rotated
under the stable root identity. Compromise of the root identity private key
cannot be repaired while claiming the same cryptographic identity; it requires
the explicit destructive identity-reset flow. Objects with unknown or
retired-without-key IDs fail closed and remain retryable. Cloud never receives
plaintext private or content keys.

Live sync encrypts every user-authored payload, not only media. Journals, entry
text, ordered blocks, captions, associations, Rewinds selected for backup, and
their user-authored metadata use versioned AES-256-GCM envelopes under keys
derived from the active content root. The server sees only the canonical identity
binding, opaque entity and operation IDs, entity kind, base/server version,
tombstone state, key ID, ciphertext size, server receipt order, and transport
timing. It does not receive journal titles, Markdown, captions, block order or
content, media plaintext, or plaintext digests. Conflict preservation operates on
opaque aggregate ciphertext and is finalized after client decryption.

Each media object uses a random content-encryption key wrapped by the active
content root and records versioned algorithm metadata, nonce, key ID, ciphertext
byte length, and encrypted plaintext digest plus a transport ciphertext digest.
Another recovered installation of the same identity can unwrap, verify, and
decrypt it.

An upload declares ciphertext size and digest. A media transfer operation becomes
complete only after bytes are downloaded, hashed, decrypted, durably written,
and linked to the local block; its independently replayable metadata cursor does
not erase that pending obligation. Remote deletion removes database metadata and
object storage content, with idempotent retry.

Backup is distinct from live sync and from the existing human-readable data
export. The portable export remains a versioned JSON-plus-media archive intended
for the user. A Cloud backup is a separate versioned opaque binary artifact with
an authenticated header and encrypted canonical manifest. Its versioned schema
contains the canonical identity public record; journals; entries and ordered
blocks; associations; generated Rewinds; deletion tombstones; immutable media;
per-entity base versions; pending operation chains; active origin; per-stream
cursors and snapshot high-water; database/app schema versions; backup and key
IDs; byte lengths; and per-object digests. Secret keys remain in the separately
recoverable identity envelope. The server stores and returns the client ciphertext
unchanged; optional server-side envelope encryption is defense in depth and must
round-trip the exact client bytes.

Creation first takes one consistent database snapshot with a local mutation
high-water and leases every referenced immutable media object against cleanup.
Serialization and encryption may continue while the user captures or edits; later
mutations have higher local sequences and are intentionally outside this backup.
Only after every leased object and manifest digest verifies does the backup become
uploadable. Cancellation or process death releases leases only after the durable
backup worker resumes or discards its staging record.

Android can create, list, download, verify, and restore Cloud backups through the
server backup API. Restore is supported only into an empty library at beta launch;
it never overwrites or merges a non-empty library. Download and verification use
resumable checkpoints in an isolated staging area. No restored row or file is
visible until the complete manifest, identity, key IDs, lengths, and digests pass,
after which verified media moves into an immutable content-addressed local store
and is fsynced before one database transaction publishes every reference and
records the applied backup ID. A crash before that transaction can leave only
unreferenced objects, which deterministic cleanup removes; a crash after it finds
every referenced object already durable. Process death before publication resumes
or safely discards staging, and replay of an applied backup ID is a no-op.
Corrupt/truncated payloads, wrong keys or identity, missing media, and failed
finalization leave the active empty library unchanged and expose a retry or
discard action. The published dataset is fully usable offline before any
subsequent synchronization.

After restore, a same-origin client resumes from the captured cursor/high-water
while retaining restored pending operation chains. Newer remote edits and deletes
then apply through the normal incremental or expired-cursor snapshot protocol; an
old backup cannot resurrect an absent remote entity without producing the defined
pending-operation conflict. A different-origin restore ignores origin-specific
cursors and performs full reconciliation. Restore-then-sync convergence is
compared against the canonical remote set for both recent and beyond-retention
backups.

Quota comes from the authenticated server and counts the same stored objects and
backups that enforcement uses. The UI presents:

- live usage and limit with retrieval timestamp;
- cached last-known usage labeled as offline or stale;
- pending local uploads separately; or
- an explicit unavailable/error state.

The client never substitutes a fabricated limit.

### Cloud environment and build contract

`infra/terraform/staging.tfvars` and `production.tfvars` are the sole committed,
non-secret sources of truth for the complete runtime contract. The current
`repo_vars` deploy-source mode is retired; bootstrap automation may mirror values
for inspection, but deployment fails if a mirror differs and never reads it as
authority. Secret values remain in Secret Manager. Every deployment renders and
applies the full set of:

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

`logdate.backendUrl` is the one build-time Cloud endpoint property consumed by
the shared configuration repository and every Cloud client. Android debug and CI
builds default it to staging; signed release builds default it to production.
`logdate.origin` and `logdate.apiBaseUrl` remain app-link/website properties and
must not select the Cloud endpoint. An explicit Gradle property may override the
debug target without source edits; release publishing rejects a non-production
value. The user-level compatible-server selection takes precedence at runtime
after successful discovery and identity verification.

This target intentionally supersedes the present runbook statements that all app
builds default to production and that deploys may read `repo_vars`. Slice 1 must
update the runbook and executable validation in the same shippable commit, so the
documentation cannot remain split-brained.

Public `assetlinks.json` contains only fingerprints derived from the actual debug,
upload, and Play app-signing certificates as appropriate for each host. CI reads
the built artifact with `apksigner`, compares its certificate to the generated
statement, fetches the live staging or production statement, and runs Android's
domain-verification check against an installed emulator artifact. A placeholder,
missing fingerprint, mismatched package, stale live document, or failed link
verification is a deployment failure.

### Required user journeys

#### First run and Cloud offer

1. The user installs and launches LogDate with or without connectivity.
2. The singleton local identity and local storage initialize atomically without a
   server before any content can be written.
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

### Identity-transition evidence matrix

Every row asserts exactly one `library_owner`, one active identity ID, one visible
library, ownership of every local row through that owner, and identical entry and
media digests before and after the transition unless the row explicitly starts
empty.

| Transition | Required interruption and rejection proof |
| --- | --- |
| Concurrent first launch | Race multiple initializers and kill between key generation and database commit; one owner wins and no content can be created unowned. |
| Local identity to new Cloud account | Exercise empty and populated libraries; the server binds the existing ID, never creates a replacement, and restart preserves it. |
| Local identity to existing Cloud account | Exercise empty and populated libraries, verify destination/count/consequence review plus cancel/export paths, capture concurrently during staging, and kill before each durable boundary; only the old or new complete state is observable and adoption is idempotent. |
| Duplicate offline installations target one existing account | A second create attempt cannot bind a new ID to the existing account; sign-in recovers the account identity and uses the adoption flow. |
| Sign-out while online and offline | Online revocation succeeds; offline local disconnect preserves identity/library/outbox, persists one revocation retry through reboot, and never activates another account. |
| Same-identity reauthentication | Exact identity and key proof restore the session; mismatched server ID, public key, recovery envelope, or stale challenge is rejected without state change. |
| Compatible-server change | Success preserves the exact identity and digests; failure, cancellation, process death, and rollback retain one active origin and one identity. |
| Cloud account deletion and recreation | Server deletion removes remote credentials/data as promised but leaves the local identity/library; rebinding that same identity does not create a new local identity. |
| Recovery on a clean installation | Starting from the automatically initialized `LOCAL` owner and an empty library, passkey plus recovery code performs the atomic adoption into the exact recovered identity before backup publication; wrong identity or key preserves the local owner and empty library. Passkey-loss and recovery-code-loss cases follow the documented matrix. |
| Explicit destructive reset | Confirmation names all local consequences; interruption is atomic; after completion the old library and keys are absent before one new local identity may initialize. |

### Offline and lifecycle evidence matrix

Each local journey runs once with networking disabled from cold start, once with
connectivity removed mid-action, and once after activity recreation. Persistent
journeys additionally inject process death and managed-emulator reboot. Tests
assert visible content, database rows, files, pending-operation IDs, and replayed
results rather than screenshots alone.

| Journey | Required offline and interruption proof |
| --- | --- |
| First run | Complete onboarding in airplane mode, kill/restart at each page, and reach usable Home with the same single identity. |
| Text entry and inline Markdown | Create, edit, undo, publish, reopen, and delete offline; rotate and kill during a durable draft without losing text, selection-restoration keys, block order, or Markdown source. |
| Audio entry | Deny/regrant permission, record, stop, kill before and after finalization, reboot, publish, play, and delete without connectivity; the file always belongs to the initiating block or is safely recoverable/cleaned. |
| Photo and video entries | Capture and import through external activity recreation, kill before attachment, reboot, review/play, publish, and delete offline with durable URI/file ownership and no microphone request for still photos. |
| Home and detail | Cold-start offline with more than 50 mixed entries, paginate across semantic-day boundaries, open every stable ID, and observe edits/deletes reactively. |
| Library | Cold-start offline, search every supported local media type, play/view it, handle missing/corrupt files explicitly, and retain selection and scroll restoration. |
| Rewinds | Generate the baseline Rewind offline, kill/reboot during work, resume idempotently, and experience stored overview/detail/story/audio with reduced motion. |
| Transcription | Audio capture and playback succeed with no network; any promised transcription remains visibly paused and durably keyed to the exact block through process death, reboot, auth expiry, retry, completion, or cancellation. |
| Sync outbox | Create/update/delete while offline, kill/reboot, reconnect with valid and expired authentication, reauthenticate the same identity, and drain each immutable operation exactly once without user re-entry. |
| Quota and Cloud status | Lose connectivity before and during refresh; local capture remains available and the UI shows timestamped cached, pending, unavailable, or error state without a fabricated value. |
| Backup | Create the encrypted artifact locally offline and retain its upload operation across kill/reboot; uploading waits for connectivity without blocking capture. |
| Restore | Network is required only until the selected artifact and recovery envelope are fully downloaded; after verified staging, final publication and all restored reading/playback succeed offline through kill/restart. |

### Requirement-to-evidence matrix

| Requirement | Authoritative evidence |
| --- | --- |
| Download from `logdate.app` | Public download page exposes a working closed-test opt-in/listing; signed Play-delivered artifact is available to enrolled testers and its certificate passes live Digital Asset Links verification. |
| Start onboarding offline | First-run row of the offline/lifecycle matrix passes and retains the one identity after restart. |
| Cloud offer and signup | Compact phone/tablet UI journey plus real staging Credential Manager signup, identity challenge, recovery-code confirmation, restart, and sign-in. |
| Text with inline Markdown | Parser/editor host tests, cursor/undo tests, editing/detail screenshots, and managed-device save/reopen journey. |
| Audio entry | Audio offline/lifecycle row plus real emulator recording, playback, Library, sync, and restored-media digest proof. |
| Photo entry | Photo/video offline/lifecycle row plus camera-without-microphone, thumbnail/detail, Library, sync, and restored-media digest proof. |
| Video entry | Photo/video offline/lifecycle row plus playable review/detail, corrupt-media recovery, Library, sync, and restored-media digest proof. |
| Every Home entry and detail | More than 50 mixed entries with semantic-day boundaries; persisted IDs equal Home-visible/detail-reachable IDs exactly once. |
| Lifecycle persistence | Every row in the offline/lifecycle matrix passes its required recreation, process-death, and reboot injections. |
| Deep links and shared imports | Delivery-ledger tests repeat the same initial and `onNewIntent` payload through recreation, process death, and cold task restoration; exactly one durable side effect occurs while a later genuine delivery remains possible. |
| Library | Seeded real local media plus permission/error/search/selection/playback journeys. |
| Rewinds | Seeded local generation, concurrent request, interruption/resume, overview/detail/story, audio, and reduced-motion journeys. |
| Real quota | Authenticated server response matches database/object storage before and after upload/delete; offline UI shows labeled cached data. |
| Sign-out and same-identity reauthentication | Every applicable identity-transition row passes, including failed revocation retry and mismatch rejection. |
| Compatible server | Identity-transition server-change row plus protocol-v1 conformance, descriptor pin/rotation, exact installed-certificate Digital Asset Links, recovery-envelope independence, and complete sync/backup/restore/quota suites against the separately hosted reference server. User-entered URLs receive only preflight verification, not a blanket compatibility claim. |
| Sync | Two independent emulators create/update/delete offline, including several causal mutations to one entity, survive kill/reboot and auth expiry, reconnect, paginate beyond 200 with same-sequence-boundary changes and page replay, expire a cursor into crash-injected snapshot reconciliation, exercise every cross-device conflict type, and compare canonical entry/media digests. |
| Backup/restore | Versioned Cloud artifact round-trips byte-for-byte; clean second installation authenticates and recovers the same identity, restores, verifies every entry/block/media digest, and works fully offline. Tests also cover corrupt/truncated artifact, wrong key/identity, missing media, process death at each checkpoint, repeated restore, rollback, and restore-then-sync. |
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

No Play upload occurs until every non-distribution launch row above is proven and
the app has completed functional, visual, accessibility, sync, backup, quota, and
signed-artifact review. At that point the exact candidate may be uploaded to an
internal-track release published only to the restricted launch-validation tester
list so Play signing, Play delivery, installed-artifact behavior, and Digital
Asset Links can be validated. The Play-delivered bundle is installed from Play on
safe emulators and reruns the local/offline, visual, accessibility, lifecycle,
staging two-installation sync/backup/restore/quota, and custom-server suites. It
then uses disposable generated content for a real production passkey, encrypted
mixed-entry sync, backup/clean-restore, delete, and quota-accounting round trip.
No private journal content is used. The same bundle/version is promoted to closed
testing only after those checks pass; only then are the public opt-in link and
website changed. This breaks the distribution evidence cycle without uploading
an unfinished developer build.

Production Cloud health, Play closed-testing availability, and the public website
are separate external gates and are reported separately from repository health.

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
