# Native Android backup and restore

Android's backup service delivers application data independently of account sign-in. LogDate
preserves the journal owner and encryption identity while creating a distinct installation device
ID. Content availability and account authentication have independent recovery state.

The [credential custody model](native-android-recovery-custody.md) defines the owner, transport,
recovery, and invalidation boundaries for each key and credential, including deliberate Block Store non-use.

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

Android runs the agent in restricted backup mode, where the framework instantiates the base
`Application` instead of LogDate's manifest-declared `LogdateApplication` and does not initialize
the app's content providers. Keep backup callbacks independent of Koin, Room, WorkManager,
location services, and same-app providers. Do not cast or depend on the `Application` object;
persist a small durable marker and let ordinary startup reconcile it after restore completes.

Journal readiness waits for completed-recording salvage, database/key/privacy validation, and required
native media quarantine. Device observers and background scheduling start once in the application
scope after that validation succeeds; constructing optional service dependencies must not hold the
journal on its loading screen. A failed content-open attempt cannot start those observers. Location
foreground services register their own notification channel before promotion, including service-only
entry and launches racing with background initialization.

On the ARM64 API 34 managed image, verify external location and motion grants with:

```sh
python3 tests/e2e/managed-permission-acceptance.py --output /absolute/path/to/new-permission-evidence
```

The driver starts Gradle, selects only its new managed emulator, and grants each permission after
the matching fixture nonce requests it. It requires one passing native case and retains logs and
results. Startup timeout diagnostics include thread frames without local values. If observation
fails while Gradle remains alive, inspect the saved process handle before launching another run.

The installation device ID also lives in non-backed-up storage. An ordinary upgrade retains its
previous ID; a replacement installation creates a new one even if preference cleanup is interrupted.
Archive workers carry the native delivery ID and authenticated account/origin. Only completion of
that delivery can acknowledge missing content, and stale work cannot acknowledge a later delivery.
A temporarily unavailable captured session leaves archive work and its source pending for retry,
both before opening and after reading the archive. A different authenticated owner or an obsolete
delivery remains stale and cannot import or acknowledge the pending journal.
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

For local database-key recovery, startup tries the primary key, the committed portable copy, and
any complete key left in `passphrase_backup.part` by an interrupted write. It changes neither
portable file until SQLCipher opens the journal. If the pending file supplies the accepted key,
confirmation syncs and atomically promotes that file without truncating its only surviving copy.
Malformed pending material also prevents generation of a replacement key. Pending files remain
excluded from Android backup; this repairs local interruption, not a key omitted from a transfer.

Missing database keys must be recognized through dependency wrappers in `ApplicationStartupGate`.
That surface runs before database-backed navigation and explains how to repeat the transfer from
an updated original device or restore a complete backup, while retaining the encrypted journal.
An identity recovery phrase does not recover the independent SQLCipher database key. Never invoke
the storage-reset controller automatically or replace retained content with an empty database.

### Database version compatibility

Production database builders require an explicit migration path in both directions. Never call
Room’s `fallbackToDestructiveMigration(false)` to disable destructive migration: the boolean
controls whether unrelated tables are dropped, and the call enables destructive fallback.
The same applies to `fallbackToDestructiveMigrationOnDowngrade(false)`. Persistent journal
builders omit both APIs. Only a test with deliberately disposable storage may opt in.

Android checks an existing journal’s schema read-only after its current or legacy SQLCipher key
opens it. A newer schema stops startup before Room callbacks or cipher migration can run; a newer
plaintext journal is refused before encryption export touches any migration files. The schema
limit and Room annotation share `DATABASE_VERSION`. Update that constant with schema migrations.
Unknown older versions remain protected by Room’s required migration path.

When a received journal needs a newer app, startup keeps the journal and recovery key and offers
**Update LogDate** before any content-backed navigation. The action opens this installation’s
Play listing, falls back to a browser, and copies the update link if neither is available. Retrying
startup or resetting storage cannot repair an unsupported schema.

`RetainedDatabaseMigrationSafetyJvmTest` checks refusal, supported migration, tombstones,
relationships, and pending work. `NewerJournalRecoveryRuntimeTest` exercises normal app startup
against a synthetic encrypted restore on a fresh Gradle Managed Device, including the update
action and retained key. `LegacyPlaintextMigrationTest` covers newer plaintext and legacy cipher
refusal as well as supported plaintext conversion. Run the native cases together:

```sh
./gradlew :app:android-main:phoneApi34DebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.client.e2e.NewerJournalRecoveryRuntimeTest,app.logdate.client.e2e.LegacyPlaintextMigrationTest \
  -Plogdate.androidTestOrchestrator=false \
  -Pandroid.testInstrumentationRunnerArguments.newerJournalAcceptance=true
```

## Provider credential cleanup

Sign-out records an installation-local cleanup obligation before changing the backed restore
policy or clearing the secure session. Local sign-out precedes best-effort remote logout. A worker
can finish an interrupted local sign-out only while its captured policy decision still applies;
a later explicit login supersedes that decision.

`RestoreCredentialLifecycle` owns provider creation and clearing through one process-wide mutex.
Its durable records retain cleanup separately for each server and track provider-key origins.
An uncertain creation preserves every possible origin until a successful replacement. A newer enrollment supersedes
older global provider deletion, while old-server Android account cleanup can still finish.
Provider calls keep the mutex until the SDK operation finishes, including caller cancellation.
Secure-storage read failures retain work for retry instead of authorizing deletion.

The cleanup scheduler awaits WorkManager persistence and uses a unique name per ticket. Startup
reconciliation repairs interrupted scheduling; worker failures retain their ticket and retry with
backoff. These obligations and WorkManager state are excluded from device backup. The portable
restore policy travels with native backup and independently prevents signed-out account recovery.

Android account creation, token updates, and removal share a mutation mutex. Writers validate the
captured login inside that boundary. Removal validates the sign-out decision there and handles
partial records, including a missing access token. Use `addAccountIfCurrent`,
`updateTokensIfCurrent`, and `removeAccountIfSignedOut` for these operations. Checking identity
before dispatching an Android account operation does not protect its eventual mutation.

Connected Android account repositories receive `RestoreCredentialLifecycle` rather than the raw
SDK manager. The native backup callback uses the raw manager only to retrieve a delivered key;
it must not create or clear provider credentials. Server-scoped repositories retain their existing
no-provider behavior because Android has one restore credential for the application.

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
Provider cleanup remains locally queued until it succeeds or newer enrollment supersedes it.

Explicit authentication captures an opaque guard before its ceremony and enables restoration only
after securely persisting the resulting session. Installation compares the durable session record
and authorization generation under a shared vault lock, so later sign-out or login wins across
foreground, native-callback, and server-pinned vaults. An interrupted authorization write keeps
persisted tokens inaccessible until explicit authentication repairs it. Refresh captures one
origin-bound session, conditionally replaces or clears only that session, and cannot enable
restoration. Server-scoped accounts use the same guarded storage rather than ordinary vault writes.

Platform token repair retains the authorization captured before refresh or renewal begins. Each
Android account mutation validates that authorization inside the shared platform lock, then reads
the exact current origin and tokens after validation finishes. A refresh must not overwrite a newer
access token merely because the account and refresh token still match. Missing-account fallback uses
the same predicate. Explicit sign-in invalidates earlier work even when the resulting tokens match.
Active operations retain an opaque authorization independently of the vault's bounded installation
guard cache; eviction must not silently stop pending enrollment. Authorization remains scoped to its
issuing vault and origin, and never grants permission to install a session using an expired guard.

## Missing originals on the replacement device

Android provider IDs and shared-storage paths identify content on the source device. A matching
`content://media/...` ID on the destination may name unrelated bytes. Native restore records an
independent quarantine marker in `no_backup`. Normal startup rewrites restored external identities
before indexing, background work, or content exposure; the backup callback does not open Room.
The marker remains pending until notes, covers, and every retained draft scope have been processed.
Damaged scope values are retained privately, and complete draft records are salvaged without
blocking valid journals. Audio reference changes retain their transcripts in the same transaction.
Draft migration preserves ownership, timestamps, and archive application receipts, including while
signed out.

`logdate-unavailable://native/` retains the source reference for scoped mapping lookup. Never open
that source identity as a recovery fallback. Reuse an owned private original or download the mapped
original. Outbound pending edits resolve the mapped remote URL; an unresolved marker stays local.
A preview does not count as its original. Download validation checks the wire length before
legacy plaintext acceptance or authenticated decryption.

Local originals and retained archive files repair without media-library or notification permission.
Availability requires an owned file whose bytes can be opened, rather than a successful provider
metadata query. Network downloads obey the current data policy and account-scoped explicit
mobile-data consent. A deferred download retains its recovery state; an allowed-network transition
wakes recovery automatically. It does not require another sign-in or a manual retry.

Restored entries retain their complete text. Optional enrichment counts the combined input before
concatenating it, and day previews allocate only their displayed range. Oversized entries must not
exhaust the replacement device's heap during timeline startup.

## Replacement-device permissions and widget choices

Restored tracking settings preserve the user's intent independently of the replacement device's
grants. Resume reads the current saved setting before reconciling permissions, so a recently saved
pause takes precedence over an older debounced observation. An unchanged resume does not replace
periodic work. Foreground capture can retry a launch denied before the activity became visible.

A later motion-permission grant must register activity transitions in an already running capture
service. Keep an existing location subscription rather than starting a second one. Media-library
and notification grants are separate from access to private recovered originals. Both reminder paths
check destination notification permission, the app-wide switch, and channel/group blocking before
recording a reminder as shown. A blocked reminder remains eligible for a later evaluation.

Placed-widget choices live in `shared_prefs/logdate_widget_instances.xml`. Native cloud and device
transfer include that specific file; launcher restoration remaps old widget IDs to replacement IDs.
The selected entry, recall filters, photo shape, and prompt preference belong to the placed widget.

Dynamic shortcuts are republished from current journal state after normal startup; a retained
WorkManager request is not their recovery source. Static capture shortcuts keep stable IDs and
use the variant's literal application ID, generated from one template. A string-resource reference
in `android:targetPackage` is not resolved by Android's shortcut intent parser. The opt-in
`RestoredShortcutTargetsRuntimeTest` reads the installed platform shortcut records and checks that
every capture intent targets and resolves within the current application without launching capture.
It also republishes a draft shortcut twice and checks the platform retains one target with the
original draft ID. A refused dynamic-shortcut publication remains retryable work; rate limiting must
not acknowledge a completed refresh. Cancellation propagates so a superseded refresh cannot finish
as a successful publication.

## Emulator acceptance

Worker tests use real `androidx.work.Data`: `Data.EMPTY` for ordinary requests and
`NativeRestoreWorkState.bind` for delivery- and owner-bound recovery. Relaxed data mocks can return
synthetic strings for absent inputs, making valid recovery look obsolete. Assert network calls,
archive handoff, and retained state as well as worker results: success can mean stale work was
correctly skipped. Stale-delivery and changed-owner tests assert that no backup listing occurs.

Recovery note lookups use nullable DAO reads: absence returns null, while cancellation and storage
errors propagate to retry. Never convert a failed read into a missing entry, because repair may then
discard its checkpoint. Check coroutine activity after reading an attachment target before dropping
pending recovery. Replacement archives enter through the locked worker flow and a new source
generation; do not overwrite a retained ZIP while a worker may be reading it.

Transcription waits while a recording carries an unavailable-original reference. Existing text,
segments, and timestamps remain intact. Once recovery rebinds the recording and transcript to the
readable private original, unfinished transcription resumes with recovery priority; a valid final
document is reused. Regression tests must rebind both rows, as the recovery transaction does,
rather than relying on a temporary URI mismatch to trigger work.

`tests/e2e/native-missing-media-acceptance.py` runs on explicitly selected synthetic emulators,
including ones with existing fixtures. Supply `--synthetic-data`, one or more `--emulator` serials,
`--app-apk`, `--test-apk`, and a new `--output` directory. For an isolated debug package, set
`LOGDATE_NATIVE_ACCEPTANCE_PACKAGE` to the same package ID used to build both APKs. It verifies provider-ID collisions,
private originals with denied media/notification permissions, metered and Data Saver transitions,
and retained-archive repair through WorkManager and process restart. It restores network overrides
and each original permission state. It never clears or uninstalls the app.

`tests/e2e/native-permission-acceptance.py` uses the same explicit emulator and synthetic-data
arguments. It exercises externally granted location and motion permission through the resumed
activity, notification delivery and widget ID remapping. Notification cases require the host
sequence opt-in and test denied/granted permission through the real coordinator. The blocked-channel
case uses a disposable channel to check the OS gate, then checks the worker retains reminder history
when that gate refuses delivery. It never mutes a production channel.

The recording fixture stages a completed AAC file, stops the app process, and launches the real
activity. The host observes publication and checks that no recording service is running before
starting verification instrumentation. Verification checks exact bytes and offline playback, then
removes only its uniquely named recording. Normal startup and the foreground fallback for a
restore-retained Application run the same salvage step before database access.

The location test waits for SDK acceptance using a temporary in-memory
Napier sink; a non-null pending request is insufficient, and production log redaction stays intact.
A private checkpoint retains the original tracking preferences
until cleanup succeeds. Cleanup restores permissions and radio settings and reads them back.

`tests/e2e/native-backup-acceptance.py` exercises Android's local debug transport on two fresh,
rootable Google APIs emulators. It invokes the source backup service, moves that transport's
payload, and invokes the destination restore service. The harness requires fresh installations and preserves existing app data.
When an emulator already contains LogDate data, build an isolated application ID with
`-Plogdate.applicationId=studio.hypertext.logdate.transfer` and set
`LOGDATE_NATIVE_ACCEPTANCE_PACKAGE=studio.hypertext.logdate.transfer.debug`; both packages remain
installed side by side, so the transfer fixture does not need to clear the regular app.

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

## Android platform testing methods

Native recovery fixtures must isolate `filesDir`, database paths, `noBackupFilesDir`, and
`applicationInfo.dataDir` wherever recovery reads those roots. Do not reload the application's
shared `databaseModule` value into a test Koin graph: its singleton factories can reuse the live
application's key provider, and closing the test graph can clear those shared factories. Construct
the real key provider with fixture storage when testing key recovery, and use explicit encrypted
files for SQLCipher checks. Test worker construction against the live graph separately.

Follow Android's [backup and restore testing guide](https://developer.android.com/identity/data/testingbackup)
when choosing what each emulator run proves. Cloud backup can be exercised on one device; a
comprehensive device-to-device transfer uses a source device and a separate, fresh target.

The two-emulator `native-backup-acceptance.py` scenario calls Android's Backup Manager on the
source and target with the encrypted local transport. It verifies real native callbacks, selected
files, and destination recovery across two app installations. That transport run does not prove
Google's D2D transport or the setup wizard.

`native-d2d-acceptance.py` follows the guide's repeatable single-device GMS `D2dTransport` test-mode
flow on an API 31+ Google Play emulator. It backs up a synthetic journal, changes its title and a
committed recording after backup, then runs `bmgr restore` and verifies that both saved versions
return. The repository
forbids uninstalling or clearing LogDate app data, so the harness restores explicitly instead of
using the guide's uninstall/reinstall step. Passing it proves the GMS test transport, callbacks, and
restored content; it does not prove source-to-fresh-target setup-wizard transfer. That broader
acceptance remains a separate device-transfer test and must use a new target.
The in-place simulation retains the installation ID stored outside backup. The two-emulator
scenario requires a distinct ID on the fresh destination and verifies recovery into a new Keystore.

After process death, a read-only probe must find the saved offline edit before the content
verifier runs. A verifier that creates a missing edit cannot establish persistence.

`tests/e2e/native-restore-work-restart-acceptance.py` first checks account and server
transitions against real WorkManager on an offline emulator. The current account receives
an immediate recovery request even when the previous account's request is queued or running.
A previous request's failure or retry must not block the new account. Token refresh keeps
the same schedule, and a server change remains observable even with identical credentials.
Signing out and back into the same account also replaces a running pre-authentication request;
its later failure or retry cannot consume the new authenticated wake.
Scheduling acknowledges a target only after its WorkManager operations persist. Transient
storage failures retry without requiring another authentication or network event. A newer account
or policy cancels obsolete retries and reconciles partially applied periodic constraints.

The same harness verifies an unresolved native delivery
and its queued Cloud restore request across process death. Its fixture requires a non-null
delivery ID before scheduling; restart and repeated enqueue must retain that ID and the same
WorkManager request. A generic request without a native delivery cannot satisfy this check.

`tests/e2e/native-device-enrollment-acceptance.py` combines a populated two-emulator native
transfer with the real account/device API and an isolated PostgreSQL server. Build
`:app:android-main:assembleDebug`, `:app:android-main:assembleDebugAndroidTest`, and
`:integration:server-client-e2e:writeAndroidHistoryHarnessClasspath`, then provide two fresh,
rootable emulator serials, both APKs, the generated classpath file, a private database environment
file, and a new output directory:

```sh
LOGDATE_NATIVE_ACCEPTANCE_PACKAGE=studio.hypertext.logdate.debug \
python3 tests/e2e/native-device-enrollment-acceptance.py \
  --source emulator-5554 --destination emulator-5556 \
  --app-apk app/android-main/build/outputs/apk/debug/android-main-debug.apk \
  --test-apk app/android-main/build/outputs/apk/androidTest/debug/android-main-debug-androidTest.apk \
  --classpath integration/server-client-e2e/build/android-history-harness.classpath \
  --fixture-environment /private/temporary/enrollment-environment.json \
  --output /private/temporary/native-enrollment-result
```

The environment file must have mode `0600` and contain `DATABASE_URL`, `DATABASE_USER`,
`DATABASE_PASSWORD`, `SERVER_ENCRYPTION_KEY`, and `LOGDATE_BLOB_STORAGE_DIR`. Use a disposable,
loopback-only PostgreSQL database and a Base64-encoded random 32-byte server key. The harness
creates its own synthetic passkey account. It supplies credentials through standard input to a
private `no_backup` fixture; credentials never appear in instrumentation arguments. The destination
receives that fixture only after native transfer, duplicate delivery, readable offline content,
and process-restart checks pass. The fixture selects its local server through real discovery,
then normal Activity startup and WorkManager must enroll the replacement and upload the retained
draft and offline-created entry. Before authentication, the fixture continues the restored draft
with a real repository save while the emulator is offline. Its block IDs, media, selected journals,
and creation time remain intact. The server copy must decrypt to that new edit after ordinary
startup sync. The test does not call registration or sync on the destination.
A second process restart must execute new sync work and retain the same replacement identity
and session, with exactly two installations in the real server registry.
Both startup stages require a new successful WorkManager request. Server snapshot verification
uses the raw API and the production decoders and cipher; the durable sync inbox contains only
unapplied work and can correctly be empty after successful synchronization.
The native transfer check waits for the retained text entry to appear on the Timeline, rather
than accepting navigation over loading placeholders. Local entries publish independently of
optional recommendations; a stalled or failed lookup cannot hide recovered entries or stop
later local edits from appearing.
Archive recovery must await `LogDateConfigDataSource.awaitConfigurationLoaded()` before
capturing its owner/origin or creating a recovery record. The repository initially exposes the
default Cloud origin; treating that initial value as a saved choice can bind accepted work to
the wrong server before draft storage resolves its persisted scope. Use
`currentArchiveRecoveryScope` for Android archive workers and scheduling. Never repair an older
record by guessing that a different selected origin owns its pending work.

Archive download, import, retained-source replay, attachment repair, and delayed repair retries
require Android's `StorageNotLow` constraint. Cloud downloads also require a connected network;
local archive work must remain eligible offline. Use `archiveRecoveryConstraints` for archive
import and media requests so resume paths do not bypass the storage policy. Write failures during
an attempt still retain its source and checkpoint for retry.

`tests/e2e/native-storage-recovery-acceptance.py` accepts explicit emulator, app APK, test APK,
private output directory, and `--synthetic-data` arguments. It interrupts metadata import after
accepting a private archive, forces Android's low-storage state, and checks the source bytes and
queued request across separate app processes. The final probe starts while storage is still low;
the host releases the storage condition only after that probe is ready. The original WorkManager
request must finish and restore the draft without another app launch. The harness resets and
reads back Android's original storage state even when a stage fails. This validates platform
deferral and interrupted-write custody, rather than physically filling the emulator's filesystem.

`tests/e2e/native-recovery-ui-acceptance.py` checks production restore sheets at phone and tablet
display sizes and with Android's system font scale at 200%. Each sheet's text-layout density must
match the requested font scale: a `LocalDensity` override around the page does not establish the
scale of a separate native modal window. The probe verifies partial-state copy, media counts,
reachable accessibility actions, and one callback per tap, and captures each state's content and
action placement for inspection. It restores and reads back the original display and font settings.
Compose semantics and action checks do not establish a TalkBack navigation session.

The independent server verifier checks that the uploaded draft carries the replacement device ID
and its original creation time. The historical entry keeps its creation time and encrypted capture
metadata; the destination decrypts that metadata through the production content API and checks
its original values. Content records expose capture platform and activity rather than a per-entry
device UUID, so draft attribution and historical capture continuity have separate assertions.
Successful cleanup conditionally clears only the fixture sessions and restores both radio states.

The native acceptance workflow runs this scenario after the transport and permission checks.
Publish only its explicit acceptance receipt and selected instrumentation logs. Keep the database
environment, server control directory, credential fixture, and native transport payload private.
This scenario does not establish Google credential delivery, setup-wizard acceptance, independent
provider-issued replacement sessions, or enrollment key rotation. Account/device registration
stores installation metadata; peer authentication keys have their own fresh-handshake checks.

The local transport exercises file selection, native callbacks, and application recovery using
test encryption and direct-transfer flags. Google credential delivery and setup transfer require
the corresponding providers and a dedicated test account. Cross-platform transfer requires a
matching Android/iOS format and receiving implementation before configuration can be enabled.
The receiving implementation is the native app in `logdate-apple`, bundle
`studio.hypertext.logdateapp`, team `T95VDD3A4W`. An iOS application target and AppMigrationKit
extension are required, together with the App Store Connect OS data transfer mapping.
AppMigrationKit does not run in the macOS application.

The two-emulator harness also supports `--exercise-data-only` to prove a signed-in synthetic source
restores journal and media while leaving the destination signed out, `--exercise-credentials-only`
to prove portable recovery
keys arrive without journal or media data, `--exercise-missing-keys` to retain encrypted journal
data without replacing unreadable keys, `--exercise-partial-media` to omit one original while
checking the remaining media and recoverable source reference, and `--exercise-duplicate-restore`
to replay Android restore before app launch and compare durable state. `--exercise-missing-privacy`
retains the journal, keys, canonical owner, and drafts while removing onboarding and security
metadata. It verifies protected startup, retry, Activity recreation, and refusal to back up an
unknown policy, then delivers the complete source backup through Android and checks journal access.
Run filtered payload scenarios
on a fresh destination because they intentionally alter the transported fixture. These modes use
synthetic app data and do not establish Google Restore Credentials or setup-transfer provider
acceptance.

### Privacy metadata during partial delivery

A restored encryption key proves that LogDate can read the journal; it does not establish the
original app-lock policy. The shared database factory validates restored privacy metadata before
returning a database to any consumer, including widgets and background work. Retained journal or
draft content without that metadata enters the protected restore screen. Retry and process restart
retain the original database and key. A later complete delivery supplies the missing policy and
allows normal startup. An empty installation or credentials-only delivery can still onboard.

Backup preparation writes explicit onboarding and security defaults for an established source or a
proven empty installation. It refuses to manufacture defaults for an unverified restored journal.
Existing explicit policies remain unchanged, including biometric protection and unfinished
onboarding. Legacy onboarding metadata without a security field retains its established default.
Native preferences are staged privately, parsed, and committed through the same canonical DataStore
used by application consumers. A live reader therefore observes a transferred biometric policy
instead of a cached unlocked policy. A delivery receipt also prevents old destination preferences
from authorizing transferred content when the source preferences never arrive.
Completed native-delivery fingerprints keep validation active after restore work is acknowledged,
so a second launch cannot bypass the check.

### Editing retained entries

Stored entries use an explicit edit path. Opening an entry captures `EntryEditBase` with its
original note and account scope. Complete draft snapshots preserve that base alongside the edited
content, so reopening a draft retains its editing intent. Ordinary note creation continues to reject
an existing ID with different content; import and recovery callers must not turn that guard into an
unconditional update.

Keep the base captured at opening until the edit is published. Fetching a new expected note at
Save time would let an old editor silently replace another editor's work. Publication compares the
loaded content with the current record and uses the repository's scoped transaction. Capture
provenance, location, and current sync history come from retained storage rather than the editor's
partial presentation model.

```kotlin
// Capture once when opening, and carry this same value through the draft.
val editBase = fetchEntry.forEditing(noteId) ?: return
// After editing:
saveEntry(notes, selectedJournalIds, activeDraftId, editBase = editBase)
```

A concurrent edit or deletion preserves the edited version in Drafts. The editor's existing snackbar
offers Save separately; it never replaces the newer version or recreates the deleted ID. A separate
version receives its own ID, which every publication attempt commits to the draft before writing any
note. A failed draft write therefore remains retryable without producing another copy after process
death. Even a retry finding an identical copy must confirm its record and account inside the scoped
transaction before acknowledging the draft.

`NativeRestoredEntryEditingTest` exercises production storage and editor state, including oversized
text, current-day entries, reopened drafts, concurrent edits, deletion, and failed copy persistence.
For a rendered walkthrough, retain an entry beyond the AI input budget through Backup Manager,
open and edit it, then compare its full stored text and ID. Android accessibility text is capped,
so an accessibility snapshot alone cannot establish preservation of an oversized entry.

### Secure storage availability during recovery

Android dependency graphs and native backup callbacks construct secure storage through
`createAndroidSecureStorage(context)`. This defers encrypted-preference initialization until an
operation needs it, allowing the database provider to try retained portable keys when the local
vault cannot open. Direct reads and writes still fail explicitly: unavailable identity material
must never be treated as an empty vault or authorize a replacement key.

```kotlin
single<SecureStorage> { createAndroidSecureStorage(androidContext()) }
```

Identity observers are cold and wait without publishing missing-key values while initialization
is unavailable. A later successful storage operation reconnects them; they do not poll, terminate
optional watch/archive observers, or manufacture a signed-out identity event. Preference creation
and cache hydration share one sanitized failure boundary. Initialization errors retain neither
private keyset details nor their underlying exception causes, and unreadable preference files
remain unchanged. Only after SQLCipher accepts a retained key may its primary copy be refreshed;
a failed refresh leaves the readable journal available for a later retry.

### Retained-key failure acceptance

`NativeRetainedKeyFailureTest` requires a verified emulator, an unprivileged synthetic debug app,
and `retainedKeyFailures=true`. Its database, portable material, and original media live in an
isolated fixture. A separate prefixed vault exercises real encrypted-keyset initialization failure
through an isolated dependency graph while preserving its original file. Missing, malformed, truncated, mismatched, and file-permission-denied recovery
material must leave the encrypted journal and originals intact. A valid portable key must recover
an invalid or unreadable primary, and a failed primary commit must remain recoverable when the
provider is recreated. Capture diagnostics without disclosing the key or private storage errors.
The fixture restores file permissions before cleanup and never changes the application's keys.
`NativeKeyPersistenceRestartProbeTest` uses the same isolated storage with a unique `keyRestartId`
and `keyRestartStage=seed`, followed by stopping the synthetic app process and a `verify` invocation.
Verification requires a different process ID and opens the unchanged encrypted journal using the
only valid key retained in an interrupted write, before committing that key. Acceptance rejects
both ignored tests and assumption failures; JUnit's `OK` summary alone does not prove execution.

### Reminder schedules on the replacement installation

Reconciliation and immediate evaluation share a scheduler lock and read current persisted reminder
preferences before accepting work. Repeated startup retains an active evaluation. A late fixed-alarm
callback respects its morning/evening switch and the active reminder mode; shared reminder consent
gates moment signals. Cancellation waits for WorkManager persistence before another enqueue can
enter the gate. Ordinary storage failures retry reconciliation and the pending signal without another
preference change or input event; turning reminders off cancels those retries.

Legacy immediate and periodic workers also share a delivery lock across eligibility, notification
posting, and history persistence. A successful post finishes writing its suppression history even
if the worker is canceled. Unreadable history prevents delivery; failed persistence remains retryable.
Pending suppression is shared across reminder modes and retains the original delivery time, using
the same timestamp precision as persisted history so a failed acknowledgement cannot count twice.
The concurrent native fixture uses a private on-disk history store, reloads it after closing the
original store, and checks one actual notification and one post call. It grants notification access
only on its disposable managed installation. These checks cover concurrent workers and coroutine
cancellation; killing the process between the OS post and history commit is a separate boundary.

The managed startup job in `.github/workflows/native-restore-acceptance.yml` selects the notification,
shortcut, and low-storage fixtures explicitly and rejects skipped or missing methods. The notification
fixture's additional `notificationDedupeAcceptance` opt-in isolates its permission grant from the
denied-permission probes. `managedAcceptedArchiveWaitsForStorageThenResumes` checks actual platform
storage transitions, retained archive bytes and request identity, and automatic restoration in one
process. The cold-process acceptance harness additionally tests process termination between stages.
Storage-monitor commands queue their state update; wait for both forced and observed levels before
continuing, and fail if the requested state never appears.

Broadcast receivers release their pending result after asynchronous scheduling, including when the
application scope was already cancelled. `RestoredReminderSchedulingRuntimeTest` runs on a Gradle
Managed Device and checks actual AlarmManager registrations, unique periodic work, both reminder
modes, and disabled callbacks. It restores the original preferences after checking those states.

### Reminder recovery across app processes

`RestoredReminderColdStartTest` adds six ordered stages on a fresh Gradle Managed Device.
Android Test Orchestrator starts a distinct process for each stage while preserving application data.
The fixture creates a journal with an explicit unlocked privacy policy, records synthetic native
delivery state, and launches the real `MainActivity`. It does not call the reminder scheduler directly.
Enabled legacy and moment modes must reconstruct their actual registrations; disabled modes must
cancel the exact delayed work and future alarms seeded by the fixture. Every stage checks the retained
journal and key. The final stage restores and reads back original reminder preferences. Missing stages,
reused processes, skipped tests, and lost receipt state fail acceptance.

```bash
./gradlew :app:android-main:phoneApi34DebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.client.e2e.RestoredReminderColdStartTest \
  -Plogdate.androidTestOrchestrator=true \
  -Pandroid.testInstrumentationRunnerArguments.restoreAlarmColdStartAcceptance=true \
  -Pandroid.testInstrumentationRunnerArguments.clearPackageData=false
```

This verifies foreground reconciliation across process death. It does not exercise Android's backup
transport, reboot delivery, notification posting, or Google credentials; use their respective acceptance
paths for those behaviors. Never enable Orchestrator's application-data clearing for retained-journal tests.

### Recovery accessibility on an emulator

Verify recovery with TalkBack touch exploration enabled, using forward/backward navigation and
double-tap activation of the retry action. At 200% text, allow TalkBack to scroll the guidance
before expecting focus to reach the action. Wait for the recovery state to appear before sending
gestures. Android shell `input` injection bypasses the input-reader gesture path; native emulator
multitouch events exercise TalkBack's recognizer. Capture accessibility focus/click events and verify
the retained journal before and after retry. After a database has opened, compare journal content
and integrity because SQLite index maintenance can change its encrypted file bytes.

The managed TalkBack harness checks seven recovery scenes with forward/backward navigation and
double-tap activation. Three archive scenes use production sheets with synthetic missing-original,
resumable-import, and preview states and callback counters. Four startup scenes use an actual
encrypted journal: retry with its key missing, retry after synthetic delivery of its original portable
key, and app-lock security setup before and after leaving and returning to the app. Canceling Android
security enrollment must keep the journal locked. Private journal text and titles must remain absent
from every app-owned accessibility window, including content descriptions.

The harness requires a fresh evidence directory, an installed Android NDK, and an ARM64 API 34 image
with TalkBack and `su` support. Its input driver selects only a newly started, expected Gradle Managed
Device, validates the per-run receipt before every gesture, and runs a small NDK-built input helper
that writes native kernel touch events with bounded gesture timing. Shell `input` and direct
recovery-node activation are not substitutes for this check. System Back/Home events use current
uptime timestamps; zero-timestamp key events are stale and may be dropped by Android.

```bash
python3 tests/e2e/managed-talkback-acceptance.py --font-scale 1.0 --output /tmp/recovery-talkback-normal
python3 tests/e2e/managed-talkback-acceptance.py --font-scale 2.0 --output /tmp/recovery-talkback-large
```

The fixture checks the Activity configuration and Compose density before accepting the requested
font scale. It preserves and restores the accessibility-service, font, Bluetooth, and notification
permission settings it touches. Only the known initial Accessibility Suite notification prompt and
Bluetooth crash dialog are handled during setup; unexpected covering windows fail with a diagnostic
receipt. Archive navigation invokes fixture callbacks without executing an import. Startup recovery
verifies journal contents and original-key retention through normal application initialization. The
late key delivery is synthetic; Google-provider delivery and worker restart require separate fixtures.
The managed device retains the accepted journal and keys; cleanup restores the original preferences
and closes the Activity without deleting journal data or recovery material.
If a host observer fails while Gradle is still running, inspect the saved child-process handle before
starting another run; an observer failure does not mean the managed-device task has stopped.

The modern callback fixture constructs a real framework `FullRestoreDataInput` from a private
file descriptor. Its constructor is hidden from the SDK, so the API 36.1 instrumentation command
uses `--no-hidden-api-checks` on its verified emulator. This keeps the callback test independent
of MockK's JVMTI agent and the runtime temporary directory, which may be unavailable on a fresh
emulator. Production callback handling uses the public callback and accessors.

`RestoreCredentialCleanupRuntimeTest` checks encrypted-storage recreation, worker retry, and
production dependency injection and scheduling. Its `cleanupSurvivesAppProcessRestart` test accepts
a unique `cleanupProcessNamespace` and `cleanupProcessStage=seed` for the first instrumentation
invocation. After stopping the synthetic emulator app, invoke the test with the same namespace and
`cleanupProcessStage=verify` to complete the retained failed ticket. These preferences are isolated
from the journal and its credentials. `PlatformCredentialCleanupRuntimeTest` checks delayed writers
and removal of partial account records against the Android account implementation.

`NativeProviderCleanupProbeTest` is opt-in with `nativeProviderCleanup=true`. It calls the actual SDK
clear operation twice on an unseeded emulator. A successful probe establishes SDK availability and
idempotency; deleting a delivered Google restore key still requires the dedicated test account.

## Platform references

- [Android Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Restore Credentials implementation](https://developer.android.com/identity/sign-in/restore-credentials-implementation-common)
- [BackupAgent API](https://developer.android.com/reference/android/app/backup/BackupAgent)
- [Testing native backup and restore](https://developer.android.com/identity/data/testingbackup)

### Installation credentials and watch links

The canonical session vault owns access and refresh tokens. Account preferences retain only metadata,
including the account DID used for synchronization, and expose it only while the matching secure
session and backend remain current. Account loading follows asynchronous session initialization and
sign-out; copied metadata cannot establish a session.

Legacy token keys from both cloud-account and DataStore-session implementations are removed for all
server origins on the first normal preferences read, before a native full backup, and during restore
completion. Restore cleanup runs even when the old preferences contain no device ID. Do not migrate
those token copies into secure storage or replace this cleanup with a signed-out-screen assertion.

Watch removal choices remain preferences. Successful watch-link records require the local pair key,
which stays outside native backup. If a copied record has no local key, reconciliation starts a new
handshake using the current account and linking policy. Failed repairs retain retry backoff. A pending record is committed before replacing a pair key,
so an interrupted final write cannot leave an old successful record with a different key. Storage
errors and interrupted observation reconnect automatically with capped backoff. Forget and relink
actions invalidate exchanges already in flight; confirmation must match the current link.
Storage read failures must propagate through trust guards instead of becoming an absent key or a
false removal preference. The installation-trust emulator fixtures use their own preferences files
and uniquely named secure keys, without opening or resetting the journal database.

The installation-trust runtime case also completes a fresh cryptographic peer handshake after
rejecting the copied record. It checks the replacement pair key and link identity, secure-store
recreation, idempotent confirmation, and a fresh publication revision while preserving the old
link's counter and source key. Draft upload integration changes the installation ID after sync
initialization and verifies that the encrypted outbound request uses the replacement ID without
rewriting the retained draft's creation time. These checks cover local protocol behavior; actual
Google credential delivery and setup-wizard transfer retain their separate acceptance requirements.

### Publication after a native transfer

`tests/e2e/native-publication-acceptance.py` combines a populated native transfer with replacement
peer trust and the first encrypted backup publication. Supply explicit source and destination
emulator serials, both acceptance APKs, and a new private output directory. Both emulators must be
fresh; `--resume-seeded-source` permits a retained two-journal native fixture as the source.
The destination must still be empty. The harness never clears application data.

The source negotiates a synthetic peer link and reserves its delivery counter. Android transfers
the journal and peer preferences while excluding the source vault. On the replacement, the test
requires a new installation ID, rejects the copied trust, negotiates a fresh link and counter,
and invokes the production backup worker with a synthetic in-memory session and capture transport.
It decrypts the outgoing archive, checks its replacement device ID, retained notes and offline edit,
and verifies the exact size and SHA-256 of the image, audio and video originals. After a process
restart, the same link, key and counter must remain stable, with no session installed in the app.
Source cleanup verifies that the original pair key was unchanged before removing only its own fixture.

The native CI data-only lane runs this combined check. Its captured upload proves the app's
export, encryption and attribution behavior; hosted uploads and Google restore delivery require
their own acceptance runs. The BackupManager transport and emulator setup follow
[Android's backup testing guide](https://developer.android.com/identity/data/testingbackup).

### Instrumentation result validation

Native host harnesses share `tests/e2e/native-instrumentation-results.py` for AndroidJUnitRunner
results. A successful summary must contain the expected completed test count; ignored tests,
assumption failures, test failures, runner errors, and crashes fail acceptance even when a success
summary is present. Harnesses preserve private logs before rejecting results. Network-release and
permission-grant acknowledgements remain required for scenarios that depend on them.

The missing-privacy runtime check observes a new startup failure after retry and activity
recreation, verifies its privacy exception, and then checks the recovery guidance. Its bounded
startup wait is separate from the rendered-text wait. The emulator-only `privacyRetryDelayMs`
argument injects up to 25 seconds of retry latency without changing production behavior.

Run `python3 -m unittest discover -s tests/e2e -p 'test_native*.py'` and
`python3 scripts/tests/native-backup/test-native-acceptance-safety.py`, and
`python3 scripts/tests/native-backup/test-native-instrumentation-results.py` when changing these harnesses.
The result regressions cover standalone entry points and the imported D2D and permission helpers,
including Android's LF and CRLF output. Adding a helper requires updating native workflow path
filters so a helper-only change still exercises emulator acceptance.
