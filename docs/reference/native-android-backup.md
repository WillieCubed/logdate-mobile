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
server selected at the start. Conditional writes and rollback preserve later editor saves;
duplicate records share their original undo snapshot, and invalid draft IDs are skipped. A retry
repairs a missing upload request after an interrupted persistence/enqueue boundary without
replacing a later pending mutation.

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

## Account recovery

Credential Manager Restore Credentials creates a cloud-backed restore key where possible and
retries local-only creation specifically when cloud end-to-end encryption is unavailable. Restored credentials are requested independently
of onboarding preferences, and transient errors remain retryable. Account recovery does not
automatically import an archive over an intact transferred database.

Restore assertions have a passive authentication contract. The dedicated server restore verifier
validates origin, RP, challenge, registered-key signature, ownership, and replay without requiring
a fresh user-presence or biometric ceremony. Ordinary passkey verification is a separate path.

## Emulator acceptance

`tests/e2e/native-backup-acceptance.py` exercises Android's local debug transport on two fresh,
rootable Google APIs emulators. It invokes the source backup service, moves that transport's
payload, and invokes the destination restore service. The harness requires fresh installations and preserves existing app data.

The fixture includes two journals, shared entry relationships, original image/audio/video content,
a rich draft, and a deleted entry. Destination assertions cover owner and content-key continuity,
a distinct device ID, original bytes, offline decoding/playback, draft structure, and an offline
edit retained across process restart. The `legacy-account` identity scenario starts with enrolled
secure keys but no portable key backup, and checks that the native backup callback prepares them.
An independently encrypted media payload must decrypt on the replacement phone. The harness requires explicit `emulator-NNNN` serials and
refuses a destination that already has the debug application installed. The optional
`--exercise-quota` scenario creates an oversized synthetic source entry and checks Android's
quota rejection and persisted callback record. Optional summarization and entity extraction
reject inputs beyond their processing budget before constructing prompts or normalizing cache
keys. Moment inference checks the budget before serializing a day and uses bounded preview
fragments for large entries; the source entries remain intact.

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
