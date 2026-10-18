# Private and shared storage

Part of the Private and shared repos project in the LogDate on the AT Protocol initiative. This is
the outcome of the storage-layout spike. It decides:

- how each account's data is split between a public repository and private spaces;
- what a sealed record looks like;
- what the server still has to see for sync to work;
- where the data-home boundary sits;
- how existing accounts move over.

The umbrella design is
[LogDate on the AT Protocol](./2026-10-09-logdate-atproto-interoperability-design.md). Which
record lives where is in the [lexicon reference](../../reference/lexicons.md).

## Where things stand today

### On the server

Each account has exactly one AT Protocol repository, keyed by its DID. It is a Merkle search tree
(MST) in Postgres:

- `atproto_repo_heads` and `atproto_repo_commits`, with one head per DID;
- blocks shared across accounts.

Every synced kind of data sits in that one tree as readable JSON under `studio.hypertext.logdate.*`
collections: entries, journals, journal links, drafts and moments. The apps encrypt some field
values, such as text, captions, locations and transcripts. Everything else is plaintext:

- the record's kind, from both the collection and a `type` field;
- its ID and its timestamps;
- recording length and photo layout;
- which entry belongs to which journal (association keys are `journalId~entryId`);
- media file names and sizes.

App sync is a REST API with one endpoint per kind (`/contents`, `/journals`, `/drafts`, `/moments`,
`/associations`, `/media`). Postgres has a sync index (`logdate_collection_records`) with one row
per record, storing the record's collection name and a per-account version number used for paging,
conflict checks and tombstones.

Every write appends a commit signed with the account's identity signing key, and old blocks are
never deleted.

Several server features read those plaintext details:

- per-kind change feeds and sync status counts;
- journal merges, which rewrite journal links and drafts on the server;
- field-level merges on `PATCH` and transcript preservation on upsert;
- per-kind storage quotas;
- the `/api/v1/resources/{id}` link lookup.

### On the apps

Encryption is per field: AES-256-GCM, with a key derived from the account's identity key for each
field ID (the `LDSE2:` format). Media is encrypted in 64 KiB chunks with an account-wide media key
(`LDCE2`).

There is only one content key per account, so rotation isn't possible. Its only identifier is an
8-byte fingerprint.

The apps resolve conflicts themselves, and use the server's version number for optimistic
concurrency (a 409 on conflict).

### AT Protocol Spaces

Spaces is the protocol's work on non-public data. It has been a public alpha since August 2026, and
it still has breaking releases every few weeks. The parts that matter here:

- **A space is named by three parts:** an authority DID, a space-type NSID and a space key (`skey`).
  A record's address is
  `at://{authority}/space/{spaceType}/{skey}/{authorDid}/{collection}/{rkey}`.
- **Each person who writes to a space gets their own space repository on their own PDS.** It's a
  key/value set summarized by a set hash, not an MST.
- **A personal space is a space whose authority is the owner's own DID and which has one member.**
  The proposal lists drafts, bookmarks and mutes as intended uses.
- **Spaces provide access control, not confidentiality.** Servers and authorized apps read
  plaintext.
- **Still open as proposals:** the commit format, the DID document keys and the policy model.

## Decisions

### 1. Two kinds of storage: the public repository and spaces

| Store | Engine | Contents | Who can read it |
|---|---|---|---|
| Public repository | The existing MST engine, signed with the identity key | Profile, sharing-key declarations, deliberately published stories, records other AT Protocol apps write | Everyone, once identities are published |
| Personal space | A space store | Every private record, sealed | Only the owner's devices |
| Circle space | A space store | Copies of shared items, sealed to the recipients | The sharer and the recipients |
| Group space | A space store, one per member who writes | Items shared into a group, sealed to the group | The group's members |

Spaces follow the Spaces model exactly:

- the personal space is `(ownerDid, app.logdate.space.personal, self)`;
- a circle space is `(sharerDid, app.logdate.space.circle, <random skey>)`;
- a group space is `(creatorDid, app.logdate.space.group, <random skey>)`.

Each author has their own space repository inside a space.

**Spaces don't use the MST engine.** A space store is a table of sealed records keyed by
`(space, author, rkey)`, with a server-assigned sequence number per space. This changes the
umbrella design, which said all three stores would share the MST engine. The reasons:

- **The Spaces protocol itself dropped the MST.** Using a different structure now would mean a
  second data migration when LogDate moves onto Spaces.
- **Signed commits are the wrong cost here.** The MST engine signs a commit with the identity key on
  every write and never deletes old blocks. That is right for public data that relays verify, and
  wrong for a private journal: it costs a signature per keystroke-sized save, and deleted private
  content stays on disk forever.
- **The envelope already authenticates every record**, because only the owner's devices can produce
  a valid one. The server's ordering adds nothing that a client can't check against its own
  records.

The space store won't commit to a set hash until the Spaces commit format settles (proposal PR
#122). Until then, the export format is a CAR file of sealed records plus an index, which is the
shape Spaces exports already use.

### 2. The sealed envelope

Every record in a personal, circle or group space is an `app.logdate.crypto.sealed` record. The
schema is already drafted.

| Part | Visible to the server | Contents |
|---|---|---|
| Space and author | Yes | Which space and whose repository |
| `rkey` | Yes, opaque | `base32(HMAC-SHA256(indexKey, collection + "/" + rkey))`, truncated to 26 characters |
| `version` | Yes | Envelope format, `1` |
| `keyId` | Yes | Which content key sealed it |
| `nonce` | Yes | 12 random bytes |
| `ciphertext` | Yes, as bytes | AES-256-GCM over the DAG-CBOR `#payload`, padded |
| Inner collection, inner rkey, record | No | Only in the payload |

Five rules come with it.

- **The sealed key is derived, not random.** The same inner record always maps to the same sealed
  key. That lets every device update or delete a record without a lookup table, lets two devices
  creating the same record collide cleanly, and lets the server's version checks work per
  record. It never uses a TID, because a TID encodes a time.
- **The additional authenticated data binds the envelope to its place:** `app.logdate.crypto.sealed|1|`,
  then the space URI, the author DID and the sealed rkey. A server that moves an envelope to another
  record, space or account produces a decryption failure.
- **Padding hides size.** The payload is padded to the next bucket: powers of two from 512 bytes up
  to 64 KiB, then multiples of 64 KiB. Without padding, size alone would tell a text entry from a
  location sample.
- **Keys come from the identity key for now.** The personal space's content key is
  `HKDF-SHA256(identityKey, salt = "app.logdate.space.personal", info = "sealed-record v1")`. The
  index key uses the same salt with `info = "sealed-rkey v1"`. `keyId` is the first 8 bytes of
  `HMAC-SHA256(contentKey, "keyId")`, base32. A later key rotation adds content keys under new
  `keyId`s without changing the format. Circle and group spaces get their keys from End-to-end
  encrypted sharing, not from the identity key.
- **It replaces field-level encryption.** `LDSE2` encrypts fields one at a time and leaves the rest
  of the record in plaintext. Sealing encrypts the whole record, so personal-space records don't use
  `LDSE2`. The cipher, the identity key and the HKDF construction are the same ones the apps
  already ship, so the Swift client's crypto fixtures still apply.

**Media.** Media stays chunk-encrypted (`LDCE2`) and is uploaded as a blob in the space. The sealed
record's `app.logdate.defs#media` points at the blob by CID and stores the real `mimeType`, so the
server sees neither the file type nor the file name. The server still sees blob sizes, rounded to
64 KiB chunks. A long recording is still visibly larger than a photo. Padding large media would
multiply storage costs, so this leak is accepted and documented, not fixed.

### 3. What the server still sees, and what moves to the apps

For sync to work, the server needs:

- which space and repository a write is for, and whether the caller may write there;
- the sealed rkey, so it can do upserts, version checks and tombstones per record;
- a sequence number it assigns per space, for paging and conflict detection;
- the padded size and the referenced blob CIDs, for quotas and for deleting unreferenced media;
- deletion times, for purging tombstones.

It no longer sees kinds, inner keys, timestamps, recording lengths, device IDs, journal membership
or media file names.

The features that read those move to the apps:

| Today on the server | After |
|---|---|
| Per-kind change feeds | One feed per space. Apps pull every change since their sequence number, decrypt it and route it by inner collection |
| Per-kind sync status counts | Counted on the device |
| Journal merge | The device rewrites the links and drafts and uploads them as ordinary record changes |
| Field-level `PATCH` merges, transcript preservation | Done on the device, which already resolves conflicts. The server does whole-record replacement with a version check |
| Per-kind storage quota | The server enforces total bytes per account. Settings computes the per-kind breakdown on the device |
| `/api/v1/resources/{id}` lookup across accounts | Only resolves links people deliberately published. Private records can't be found by ID |

**What this costs the account owner:** none of their data is visible to anyone else. The server
still knows how much they store, how often each record changes, and roughly when they're active.
Hiding activity timing would mean cover traffic, which isn't worth the battery and data use.

**What the server still sees about circles and groups:** who is a member of which space. Server
access control needs that. Hiding membership needs Spaces-level or MLS-style designs, which belong
to End-to-end encrypted sharing.

### 4. The data-home boundary

A data home stores opaque sealed records and blobs per space, assigns sequence numbers, and checks
who may read and write. It never sees keys, never reads a record and never resolves conflicts.

Everything above that line lives in the apps:

- mapping Room entities to `app.logdate.*` records;
- sealing and opening envelopes;
- conflict resolution;
- journal merges;
- the per-kind quota breakdown.

In code, the apps depend on a `DataHome` interface with these operations:

- `describe` (capabilities and limits);
- `putRecord` and `deleteRecord`, with a version constraint;
- `listChanges(since, limit)`;
- `getRecord`;
- `uploadBlob` and `getBlob`;
- `exportSpace`.

The interface lives in `shared/`, so the server's tests use the same definitions.

On the wire, LogDate Cloud serves these as XRPC methods under `app.logdate.space.*`, defined as
query and procedure lexicons. Their names and shapes follow the Spaces methods (`putRecord`,
`deleteRecord`, `listRepoOps`, `getBlob`), so moving onto Spaces later means swapping the
transport, not the model.

| Home | Status |
|---|---|
| LogDate Cloud | First |
| Self-hosted LogDate server | Same server code; reached through "Move to another server" |
| Another provider's PDS through Spaces | After Spaces stabilizes (Adopt Atproto Spaces) |

**Moving between homes** uses AT Protocol account migration. The public repository goes as a CAR
file. Each space the account authors goes as a space export: sealed records, blobs and an index.
The identity's service endpoint is updated last. None of this needs keys on the server, because
everything moved is already sealed.

### 5. Moving existing accounts

The server can't convert existing data, because it doesn't have the keys. The migration therefore
runs on a device, one account at a time.

1. **Eligibility.** An updated device starts the migration once it has finished a full download and
   has the identity key.
2. **Upload.** The device builds `app.logdate.*` records from its local copy, seals them and uploads
   them to the personal space. Legacy endpoints continue to work while this runs, and if the device
   stops, the next run continues.
3. **Media.**
   - Media already client-encrypted (`LDCE1`/`LDCE2`) is adopted in place: the device asks the server
     to register each existing object as a space blob, and the server computes its CID.
   - Media the server encrypted itself (`LDSM1`) is uploaded again, encrypted by the device.
4. **Verification.** The device downloads the personal space again, opens every envelope and
   compares it with its local copy and the legacy records. Counts per kind must match, and every
   record must match by content hash. On any mismatch it stops, reports a specific error and leaves
   the account on the legacy format.
5. **Switchover.** The server marks the account's storage as `personalSpaceV1`. From then on, legacy
   sync endpoints for that account answer `409` with `SYNC_FORMAT_UPGRADE_REQUIRED`, the same
   pattern drafts already use. Older app versions show an update prompt instead of syncing.
6. **Cleanup after 30 days,** the same as the tombstone window:
   - the legacy sync index and media records are deleted;
   - the account gets a fresh, empty public repository;
   - the old MST blocks and commits are deleted.

   A fresh repository is safe because no account's DID has been published yet, so no relay or app
   has a copy of the old history. Until cleanup, switching the account back is a database change.

Other updated devices on the same account notice the switchover and start syncing from the personal
space.

### 6. Space declarations

Spaces declares a space type as a lexicon whose `main` definition has `"type": "space"`. The schema
tools don't support that yet. That work is the existing task "Support permission sets and Spaces in
the schema tools". Until it lands, `app.logdate.space.defs` stays as tokens.

When it lands:

- `app.logdate.space.personal`, `app.logdate.space.circle` and `app.logdate.space.group` become
  space declarations;
- the group's own description record moves from `app.logdate.space.group` to
  `app.logdate.space.groupProfile`.

Nothing is published yet, so these renames are free.

## Found along the way

These were found during the spike and are tracked separately:

- **Account deletion leaves repository data behind.** Deleting an account doesn't remove its
  `atproto_repo_*` or `logdate_collection_*` rows. Those tables have no foreign key to `accounts`.
- **Repository revisions are a counter, not a TID.** The AT Protocol repository spec expects a
  time-based revision. This needs fixing before the public repository goes to relays.
- **The commit signer falls back silently.** If it can't find the account, it falls back to a plain
  SHA-256 digest instead of failing.

## What this means for the next project

Migrate sync and export to the lexicons implements decisions 3 to 5. Its first tasks are:

- the space store and the `app.logdate.space.*` methods on the server;
- the `DataHome` interface and the sealing code in `shared/`;
- the device-side migration with verification;
- the switchover and cleanup.

The export archive moves to the same records in the same project.
