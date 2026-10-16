# Native Android backup and restore

Android's backup service delivers application data independently of account sign-in. LogDate
preserves the journal owner and encryption identity while creating a distinct installation device
ID. Content availability and account authentication have independent recovery state.

## Backup coverage

The manifest selects `backup_rules.xml` for older Android versions and `data_extraction_rules.xml`
for Android 12 and later. The main preferences file is `files/logdate.preferences_pb`, rather than
a file beneath `datastore/`.

| Content | Encrypted cloud backup | Direct transfer |
| --- | --- | --- |
| Main and legacy preferences | Included | Included |
| Journal database and SQLite sidecars | Included | Included |
| Database passphrase recovery | Included | Included |
| Phrase-derived or account-bound identity recovery and independent media keys | Included | Included |
| Committed originals and accepted camera drafts | Excluded | Included |
| Incomplete archive recovery records | Included | Included |
| Retained source archives for incomplete recovery | Excluded | Included |
| Keystore-bound preferences, vendor installation state, background jobs, caches, and staging | Excluded | Excluded |

Cloud backup requires client-side encryption for the portable recovery material. Android's 25 MB
per-app cloud quota applies to the selected data as a whole: exceeding it prevents a new backup;
the platform does not selectively drop the largest files. LogDate Cloud and user-created archives
provide separate recovery paths. The app cannot infer a recent successful Android backup from a
local save or a successful LogDate Cloud synchronization. Backup settings explain these limits
and offer archive export without requiring an account. A native quota callback records the last
reported failure in non-backed-up storage; the UI displays that failed attempt independently of
LogDate Cloud status.

## Restore lifecycle and keys

`NativeBackupAgent` uses file-based Auto Backup (`fullBackupOnly`). Its restore-completion callback
atomically records content availability before normal startup can create an empty Room database. It removes
the transferred `device.id` field without removing canonical owner or user preferences. Content
and account recovery have separate markers in `noBackupFilesDir`; acknowledging local content
does not acknowledge unresolved authentication. An unreadable delivery marker cannot authorize
automatic archive import over retained content.

The installation device ID also lives in non-backed-up storage. An ordinary upgrade retains its
previous ID; a replacement installation creates a new one even if preference cleanup is interrupted.
Archive workers carry the native delivery ID and authenticated account/origin. Only completion of
that delivery can acknowledge missing content, and stale work cannot acknowledge a later delivery.
Pending sign-in recovery can retry when the app resumes or networking reconnects.

Archive retries preserve drafts with newer or equal saved modification times. Recovery reads and
writes propagate storage failures, retain archive timestamps, and stay bound to the account and
server selected at the start. Conditional writes preserve later editor saves;
duplicate records track their captured snapshots, and invalid draft IDs are skipped. A retry
repairs a missing upload request after an interrupted persistence/enqueue boundary without
replacing a later pending mutation. Successful note, journal, and membership writes survive a later
archive failure; recovery never deletes an accepted original as failure cleanup. Draft writes also
survive later failures, and captured snapshots cannot replace a later edit.
Per-entry application receipts commit with Room rows or the draft DataStore value, so replay cannot
recreate an entry deleted after import even after that deletion has synced.

Android Keystore ciphertext is not portable. The database passphrase has a recoverable copy under
`db_recovery/`. Phrase-derived content identities retain their recovery phrase under
`identity_recovery/`; phrase-less account identities retain a versioned, account-bound key there.
Independent media keys are selected by identity binding and retained alongside its recovery material.
Explicit phrase recovery selects the corresponding media key while preserving earlier identity
records. Root changes, media selection, and backfill share a lock so startup cannot bind a
previously cached media key to the recovered identity.
Account backup version 2 stores the root and independent media key together, so interruption
between primary key writes leaves both recoverable. Version 1 account backups remain readable.
Phrase recovery preserves a paired media key before replacing the account backup representation.
Startup, sync provisioning, and the full-backup callback backfill existing installations without
minting new identities. These copies use app-private filesystem protection, Android file-based encryption, and the
additional native cloud encryption requirement described above.

A retained database without a valid passphrase enters database recovery instead of generating a
replacement key. A malformed identity backup or an account-key backup belonging to another retained
owner cannot mint an unrelated replacement identity. Account-key backups validate a checksum
over the owner and key to detect corruption. Conflicting database key copies preserve both for
recovery. Backfill validates retained identity and media copies against the primary keys before
writing, and preserves conflicting or malformed material. Key files are replaced atomically, and incomplete replacements are excluded from backup.

## Incomplete media recovery

Before importing content, Android stores a private copy of the archive and a recovery record bound
to the canonical owner and server. Each missing attachment is recorded before its entry is written.
Unavailable references identify the recovery generation, entry, block, and original or preview role.
They keep text and captions uploadable without attempting to read absent bytes. A recovered video
preview cannot stand in for its original.

Recovery runs as separate WorkManager jobs. It first reuses available originals and checkpointed
candidates, then searches retained archives and the current account's Cloud media. Other retained
archives must match the expected bytes by SHA-256; a repeated filename is insufficient. Cloud
recovery refreshes expired credentials and stays bound to the captured owner and server. Temporary
failures retain their checkpoints and retry without requiring another app launch.

Recovery observes server configuration, encryption-identity changes, authenticated-session
readiness, and durable media-mapping changes. Scheduling awaits WorkManager's database commit and coalesces changes into one waiting
successor, so a running job cannot discard a later wakeup. Missing or invalid source bytes retain
their checkpoints while awaiting a changed source, key, or session; empty sync passes do not
restart foreground recovery.

Conditional reference patches preserve captions, timestamps, text, block order, and memberships.
Deleted entries, replaced attachments, and pending deletions take precedence. Notes, journals,
memberships, places, location samples, drafts, and profile decisions have durable application
receipts, including decisions to preserve newer content. Draft recovery admits its sync write
inside a Room transaction before committing preferences; an interrupted Room commit can repair
its upload intent without replaying the draft.

A restarted worker uses its retained source even if the original picker permission or file is gone.
Native cloud backup carries the compact records; direct transfer also carries retained source
archives. A source ZIP is unnecessary for attachment repair once metadata is safely imported.
Explicit cancellation persists before source copying can finish; a paused import can resume from
its retained archive. Source replacement checkpoints unfinished metadata replay across restart.

The result sheet reports background repair quietly. It requests another archive only when the
remaining content needs a source the app cannot recover itself, and retains that action after the
sheet closes. Technical warnings remain in recovery records instead of appearing as raw error
strings. Completing all pending content removes the retained source copy. Manual imports do not
acknowledge native delivery markers.

## Account recovery

Credential Manager Restore Credentials creates a cloud-backed restore key where possible and
retries local-only creation specifically when cloud end-to-end encryption is unavailable. Restored credentials are requested independently
of onboarding preferences, and transient errors remain retryable. Account recovery does not
automatically import an archive over an intact transferred database.

Before explicit passkey or Google authentication publishes its session, enrollment is marked
pending for the authentication request's original server and owner. An already signed-in Android
installation without an enrollment record initializes one at startup. Temporary storage failures
remain inside the retry loop; resolved startup records do not rotate keys on every launch. Accounts
opened for a secondary server do not enroll over the connected installation's provider credential.

After background sign-in, a durable, origin-bound marker keeps replacement restore-key registration
pending until the provider and server confirm it. While signed in, transient failures retry with
backoff from 30 seconds to 15 minutes; a process restart resumes from the marker. Sign-out or a
server/account change stops that session's retry loop. Expired access tokens refresh through the
captured server, and conditional vault writes prevent an old refresh from replacing a new login.
Foreground registration and background replacement share a lock because the provider holds one
restore credential per app.

Each pending replacement has its own generation. Completion clears only the generation it
registered, under the same lock used to persist a new obligation; another restore for the same
account therefore remains pending. Retries capture current API configuration for their original
server, allowing corrected discovery or API-version settings to take effect without signing in again.

Foreground restore also records the replacement obligation before publishing the recovered
session. Its requests use a pinned server configuration, and vault installation compares an
initialized origin and revision so a later login or sign-out wins. A server switch during a
completed secure write leaves that session only in its issuing server's vault. Cancellation
retains pending renewal, and an explicit trigger resumes registration even when the recovered
session matches the existing session. Platform account creation and registration failures retry
without delaying access to the recovered journal.

Restore assertions have a passive authentication contract. The dedicated server restore verifier
validates origin, RP, challenge, registered-key signature, ownership, and replay without requiring
a fresh user-presence or biometric ceremony. Ordinary passkey verification is a separate path.
Android retains restore keys after authentication until they are explicitly cleared. The server
therefore consumes the challenge and atomically updates the verified active credential's counter;
it keeps the key available for a fresh signed challenge after a dropped response, account-preparation
outage, or interrupted session write. A repeated challenge remains invalid. Positive counter updates
use compare-and-set; zero-counter credentials can authenticate concurrently with distinct challenges.

Registration adds the verified key without invalidating keys held by other installations. Failed
storage never reports successful enrollment or revokes an existing key. Post-restore enrollment
remains enabled for compatibility with older servers that consumed keys; it is not required to make
the corrected server key usable again. Retained historical server keys require explicit revocation;
local sign-out clears the provider credential rather than revoking every device's keys.

Sign-out persists a server-scoped authorization decision in the backed-up main preferences before
writing the secure session tombstone. A replacement device therefore retains sign-out intent even
though Keystore-bound session records are excluded from transfer. Missing decisions preserve
compatibility with older backups; malformed decisions and storage-read failures cannot authorize
restoration. This protects snapshots containing the decision; it cannot change older provider backups.
Provider credential clearing remains best effort.

Explicit authentication captures an opaque guard before its ceremony and enables restoration only
after securely persisting the resulting session. Installation compares the durable session record
and authorization generation under a shared vault lock, so later sign-out or login wins across
foreground, native-callback, and server-pinned vaults. An interrupted authorization write keeps
persisted tokens inaccessible until explicit authentication repairs it. Refresh captures one
origin-bound session, conditionally replaces or clears only that session, and cannot enable
restoration. Server-scoped accounts use the same guarded storage rather than ordinary vault writes.

## Emulator acceptance

`tests/e2e/native-backup-acceptance.py` exercises Android's local debug transport on two fresh,
rootable Google APIs emulators. It invokes the source backup service, moves that transport's
payload, and invokes the destination restore service. The harness requires fresh installations and preserves existing app data.

The fixture includes two journals, shared entry relationships, original image/audio/video content,
a rich draft, and a deleted entry. Destination assertions cover owner and content-key continuity,
a distinct device ID, original bytes, offline decoding/playback, draft structure, and an offline
edit retained across process restart. The `legacy-account` identity scenario starts with enrolled
secure keys but no portable key backup, and checks that the native backup callback prepares them.
An independently encrypted media payload must decrypt on the replacement phone. The harness
requires explicit `emulator-NNNN` serials and
refuses a destination that already has the debug application installed. The optional
`--exercise-quota` scenario creates an oversized synthetic source entry and checks Android's
quota rejection and persisted callback record. Optional summarization and entity extraction
reject inputs beyond their processing budget before constructing prompts or normalizing cache
keys. Moment inference checks the budget before serializing a day and uses bounded preview
fragments for large entries; the source entries remain intact.

The optional `--exercise-archive-recovery` scenario also transfers an incomplete archive import.
It preserves edited captions, reordered draft blocks, text, memberships, and a video preview while
two originals are unavailable. The destination retries from the retained archive after the picker
file is gone, accepts a replacement archive with different media paths, and verifies recovered
bytes and offline playback across another process restart. Test IDs travel as instrumentation
arguments; the application's recovery record and source ZIP must arrive through native backup.

The local transport exercises file selection, native callbacks, and application recovery using
test encryption and direct-transfer flags. Google credential delivery and setup transfer require
the corresponding providers and a dedicated test account. Cross-platform transfer requires a
matching Android/iOS format and receiving implementation before configuration can be enabled.
The receiving implementation is the native app in `logdate-apple`, bundle
`studio.hypertext.logdateapp`, team `T95VDD3A4W`. An iOS application target and AppMigrationKit
extension are required, together with the App Store Connect OS data transfer mapping.
AppMigrationKit does not run in the macOS application.

## Platform references

- [Android Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Restore Credentials implementation](https://developer.android.com/identity/sign-in/restore-credentials-implementation-common)
- [BackupAgent API](https://developer.android.com/reference/android/app/backup/BackupAgent)
- [Testing native backup and restore](https://developer.android.com/identity/data/testingbackup)
