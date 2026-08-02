# Android Canonical Media Storage Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` to execute this plan one task at a time, with an independent review and fresh verification before each direct-to-`main` commit.

**Goal:** Make every photo, video, and restored audio attachment that LogDate accepts on Android survive picker-grant loss, gallery deletion, process death, restart, and offline use by publishing it only after its bytes are durable in an immutable app-private content-addressed store.

**Architecture:** Keep Android `MediaStore` as a read-only picker source and an explicit export destination. Add an app-private object store under `filesDir/media/objects/sha256`, addressed by the SHA-256 digest of the bytes plus a MIME-derived extension. `MediaManager.saveMedia`, `saveMediaFromFile`, and a new idempotent `ensureManagedMedia` boundary stream into a temporary file, fsync it, atomically rename it, fsync its parent directory, and return a stable `file://` URI. A resumable local migration rewrites legacy media references only after the corresponding object is durable and preserves the existing remote sync mapping. Sharing converts private file references to grantable `FileProvider` URIs; gallery publication remains an optional derived copy and never becomes the entry's source of truth.

**Tech stack:** Kotlin Multiplatform, Android file APIs and `android.system.Os`, Room repositories, Koin, WorkManager, kotlinx.coroutines, MediaStore, FileProvider, Gradle Managed Devices, Kotlin test and MockK.

**Launch claim boundary:** This plan proves the Android local durability prerequisite. It does not by itself prove Cloud upload/download durability or clean-emulator restore; those remain separate launch gates and must consume the canonical store in the subsequent staging acceptance run.

---

## Invariants

1. A note or completed import never references a picker/provider URI.
2. A successful save returns only after the complete object bytes and directory entry are fsynced.
3. The published filename is derived from verified bytes and normalized MIME, never from an untrusted path.
4. Existing objects are never overwritten. Identical bytes deduplicate to the same URI.
5. A crash or exception may leave only an unreferenced temporary object; cleanup never removes a published object.
6. `MediaStore` insertion is performed only by `addToDefaultCollection`; it is never part of save/import/restore success.
7. Legacy `content://`, raw-path, `audio_notes`, `user_media`, and prior `file://` references remain readable while migration is incomplete.
8. Migration updates a note reference only after the managed object is readable byte-for-byte and retains any existing remote media mapping without re-uploading.
9. All canonical reads, playback, sharing, export, and sync upload work without network access.
10. No physical Android device or `connected*AndroidTest` task is used.

## Storage format

```text
filesDir/
  media/
    objects/
      sha256/
        ab/
          abcdef...0123.jpg
    staging/
      <Uuid>.tmp
```

- Digest: lowercase 64-character SHA-256 of exact stored bytes.
- Shard: first two digest characters.
- Extension: a small explicit mapping from normalized supported MIME type; never trust the source extension.
- Canonical URI: `file://<absolute object path>`.
- Temporary files: created only beneath `media/staging`, mode-private through the app sandbox, and recognized by strict UUID-plus-`.tmp` naming.
- Deduplication: same digest and normalized extension returns the existing verified object.
- Corruption: if a destination exists but its length/digest does not match its name, quarantine it under staging, publish the verified replacement atomically, then delete the quarantine file.

## Task 1: Build the atomic content-addressed object store with RED tests

**Files:**

- Create: `client/media/src/androidMain/kotlin/app/logdate/client/media/storage/AndroidCanonicalMediaStore.kt`
- Create: `client/media/src/androidHostTest/kotlin/app/logdate/client/media/storage/AndroidCanonicalMediaStoreTest.kt`

### Step 1: Write failing contract tests

Cover these behaviors before implementation:

- identical bytes with the same normalized MIME return the same canonical URI;
- different bytes return different URIs;
- the final path is inside `filesDir/media/objects/sha256/<prefix>` and contains no source filename fragments;
- the digest in the filename equals the digest of the readable final bytes;
- an input stream that throws after a partial write publishes no object and leaves no live temp after cleanup;
- a pre-existing corrupt object at the expected digest path is never accepted as valid;
- concurrent writes of identical bytes converge on one intact object;
- stale recognized temp files are removed, but unrelated files are untouched;
- blank, unsupported, or parameterized MIME types are normalized/rejected deterministically;
- an advertised payload length mismatch fails before publication.

Use an injectable stream opener/finalization adapter only where required to deterministically force an interrupted copy or fsync/finalize failure. Keep the production path on real files.

### Step 2: Run RED

```bash
./gradlew :client:media:testAndroidHostTest --tests '*AndroidCanonicalMediaStoreTest' --console=plain
```

Expected: the new tests fail because the store does not exist or its methods are deliberately stubbed. Record the exact failing assertions in the SDD report.

### Step 3: Implement the minimal store

Implementation requirements:

- stream once through a buffered output while updating `MessageDigest`;
- call `FileDescriptor.sync()` before closing the staged file;
- move within the same app-private filesystem using an atomic move when supported;
- use `android.system.Os.fsync` on the destination directory after finalization;
- serialize only the short destination-validation/finalization critical section, not the entire copy;
- verify any pre-existing destination before deduplicating;
- cleanup in `NonCancellable` after cancellation and rethrow `CancellationException`;
- use Napier for diagnostic logging and never log bytes or private paths at info level.

### Step 4: Run GREEN and quality checks

```bash
./gradlew \
  :client:media:testAndroidHostTest --tests '*AndroidCanonicalMediaStoreTest' \
  :client:media:compileAndroidMain \
  :client:media:ktlintCheck \
  --console=plain
```

Expected: `BUILD SUCCESSFUL` and every new test passes.

### Step 5: Review checkpoint

Have a fresh reviewer inspect path confinement, symlink/canonical-path handling, MIME mapping, hash correctness, cancellation, concurrent deduplication, temp cleanup, and fsync ordering. Resolve every Critical or Important finding and rerun Step 4.

### Step 6: Commit the inert tested primitive

Commit only the two Task 1 paths with the repository's atomic index-reset workflow.

```text
refactor(media): add atomic canonical object storage
```

## Task 2: Route Android save, restore, and managed imports into the private store

**Files:**

- Modify: `client/media/src/commonMain/kotlin/app/logdate/client/media/MediaManager.kt`
- Modify: `client/media/src/androidMain/kotlin/app/logdate/client/media/AndroidMediaManager.kt`
- Modify: `client/media/src/androidMain/kotlin/app/logdate/client/media/di/MediaModule.android.kt`
- Modify: `client/media/src/commonMain/kotlin/app/logdate/client/media/ManagedMediaImporter.kt`
- Modify: `client/media/src/commonTest/kotlin/app/logdate/client/media/ManagedMediaImporterTest.kt`
- Modify: `app/android-main/src/androidTest/kotlin/app/logdate/client/media/AndroidMediaManagerTest.kt`
- Modify: `app/android-main/src/androidTest/kotlin/app/logdate/client/media/AndroidManagedMediaImporterTest.kt`
- Modify: `app/android-main/src/main/res/xml/backup_rules.xml`
- Modify: `app/android-main/src/main/res/xml/data_extraction_rules.xml`
- Modify: `docs/reference/android-media-storage.md`

### Step 1: Write failing behavior tests

Update/add tests that require:

- `saveMedia` and `saveMediaFromFile` return app-private canonical `file://` URIs for image, video, and audio payloads;
- those calls do not insert into `MediaStore`;
- deleting the original image/video MediaStore row or revoking the provider grant does not affect `exists`, `getMedia`, or `readMedia` for the returned URI;
- duplicate picker/importer selections return the same canonical URI without a second object;
- MIME parameters and misleading source extensions produce the trusted MIME-derived extension;
- `saveMedia` rejects a `sizeBytes` mismatch;
- `saveMediaFromFile` leaves the caller-owned source intact;
- `addToDefaultCollection` can publish a canonical image/video as a derivative, and deleting that derivative leaves the canonical object readable;
- `getRecentMedia` and `queryMediaByDate` remain MediaStore picker queries and no longer auto-publish `user_media` files;
- legacy file/content/raw-path references remain readable.

The existing GMD importer test must stop asserting `content://media` as the final result. It must assert that the result is app-private, byte-readable after source deletion/revocation, and absent from the public collection until explicit export.

### Step 2: Run RED

```bash
./gradlew \
  :client:media:desktopTest --tests '*ManagedMediaImporterTest' \
  :app:android-main:compileDebugAndroidTestKotlin \
  --console=plain
```

Then run the smallest emulator-only failing lane for the updated Android tests:

```bash
./gradlew \
  :app:android-main:smokeDevicesGroupDebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.client.media.AndroidManagedMediaImporterTest,app.logdate.client.media.AndroidMediaManagerTest \
  -Plogdate.androidTestCoverage=false \
  --console=plain
```

Expected: current MediaStore-as-final behavior fails the canonical URI and no-insert assertions.

### Step 3: Implement the boundary switch

- Add `ensureManagedMedia(uri: String): String` to `MediaManager` with a compatibility default so existing platform fakes keep compiling.
- Android overrides it to return an already verified canonical URI unchanged or stream any supported legacy/content/file source into `AndroidCanonicalMediaStore`.
- Route Android `saveMedia` and `saveMediaFromFile` through the object store for all supported media.
- Retain `addToDefaultCollection` as the only MediaStore publication path.
- Remove `ensureLegacyManagedMediaBackfilled` calls from picker queries and delete only the now-dead auto-publication code.
- Keep legacy readers and existence checks; do not delete old source bytes in this task.
- Inject one singleton canonical store through the Android media Koin module.
- Exclude `media/staging` and `media/objects` from Android platform Auto Backup because Room references and media bytes must be restored atomically by LogDate export/Cloud restore, not independently by the OS. Document that boundary.

### Step 4: Run GREEN

```bash
./gradlew \
  :client:media:desktopTest \
  :client:media:testAndroidHostTest \
  :client:media:compileAndroidMain \
  :app:android-main:compileDebugAndroidTestKotlin \
  :client:media:ktlintCheck \
  :app:android-main:ktlintCheck \
  --console=plain
```

Run the targeted API 36 phone and API 35 tablet GMD lane from Step 2. Expected: all targeted device tests pass on both devices with byte-level assertions.

### Step 5: Review checkpoint

Fresh review must verify that no normal save/import/restore path returns a MediaStore URI, the default interface method cannot recurse, legacy reads remain intact, backup exclusions are coherent, and the device tests would fail if source deletion or grant revocation were skipped.

### Step 6: Commit

```text
fix(media): keep journal attachments in private durable storage
```

## Task 3: Preserve sharing, rendering, playback, export, and restore compatibility

**Files:**

- Modify: `client/sharing/src/androidMain/kotlin/app/logdate/client/sharing/AndroidSharingLauncher.kt`
- Create: `client/sharing/src/androidHostTest/kotlin/app/logdate/client/sharing/AndroidSharingLauncherTest.kt`
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportUserDataUseCase.kt` only if canonical file URIs expose a missing path case
- Modify: `client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/restore/RestoreWorker.kt` only if it bypasses `MediaManager.saveMediaFromFile`
- Modify focused image/video/audio playback tests only where canonical URI coverage is absent
- Create: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/CanonicalMediaConsumersE2ETest.kt`

### Step 1: Write failing consumer tests

Require that:

- sharing a canonical `file://` URI converts it to `${applicationId}.provider` and grants read access rather than exposing a file URI;
- Coil image loading can decode a canonical image after its gallery derivative is deleted;
- video playback can prepare a canonical video URI offline;
- audio playback can prepare a canonical restored-audio URI offline;
- export includes exact canonical bytes and restore returns a canonical managed URI;
- deleting the external source/derivative does not break any of those consumers.

### Step 2: Run RED

```bash
./gradlew \
  :client:sharing:testAndroidHostTest \
  :client:domain:jvmTest --tests '*ExportUserDataUseCaseTest*' \
  :app:android-main:compileDebugAndroidTestKotlin \
  --console=plain
```

Expected: at minimum, current Android sharing returns a private `file://` URI directly and fails the provider assertion.

### Step 3: Implement compatibility fixes

- Resolve every local `file://` reference to a canonical `File`, validate it remains inside an allowed app-private root, and convert it with `FileProvider.getUriForFile`.
- Preserve direct MediaStore `content://` behavior for legacy refs.
- Add `FLAG_GRANT_READ_URI_PERMISSION` and `ClipData` where target apps require durable grants for the intent lifetime.
- Keep export and restore streaming; do not introduce whole-video reads outside the existing `MediaPayload` compatibility API.

### Step 4: Run GREEN and GMD proof

```bash
./gradlew \
  :client:sharing:testAndroidHostTest \
  :client:domain:jvmTest --tests '*ExportUserDataUseCaseTest*' \
  :client:sharing:ktlintCheck \
  :client:domain:ktlintCheck \
  :app:android-main:ktlintCheck \
  --console=plain
```

```bash
./gradlew \
  :app:android-main:smokeDevicesGroupDebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.client.e2e.CanonicalMediaConsumersE2ETest \
  -Plogdate.androidTestCoverage=false \
  --console=plain
```

Expected: consumer E2E tests pass on API 36 phone and API 35 tablet.

### Step 5: Review and commit

Fresh review focuses on URI permission leakage, path traversal, MIME intent type, consumer realism, and false-positive playback tests. Resolve Critical/Important findings, rerun Step 4, then commit:

```text
fix(sharing): grant safe access to private journal media
```

## Task 4: Migrate legacy note references without data loss or redundant upload

**Files:**

- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/media/CanonicalMediaReferenceMigrator.kt`
- Create: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/media/CanonicalMediaReferenceMigratorTest.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/MediaSyncRefStore.kt` only if an explicit local-URI replacement helper is required
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/di/CloudModule.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/media/CanonicalMediaMigrationWorker.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/media/CanonicalMediaMigrationScheduler.kt`
- Create: `app/compose-main/src/androidHostTest/kotlin/app/logdate/client/media/CanonicalMediaMigrationWorkerTest.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt`

### Step 1: Write RED migration tests

Cover:

- image, video, and audio local references are canonicalized one at a time;
- HTTP(S) remote references and already-canonical refs are untouched;
- the repository reference changes only after the new object is readable;
- a failed/canceled copy preserves the original reference and remaining work can resume;
- an existing `MediaSyncRef` keeps `remoteUrl` and `mediaId` while only `localUri` changes;
- migration never enqueues a note mutation or creates a second cloud upload;
- repeated execution is a no-op;
- mixed success preserves successful rows and reports retryable remaining work;
- process restart resumes from persisted database state rather than an in-memory cursor.

Use a pure coordinator test with fakes; the WorkManager wrapper test should assert unique work, offline/no-network constraints, retry policy, and bounded attempts.

### Step 2: Run RED

```bash
./gradlew \
  :client:sync:desktopTest --tests '*CanonicalMediaReferenceMigratorTest' \
  :app:compose-main:testAndroidHostTest --tests '*CanonicalMediaMigrationWorkerTest' \
  --console=plain
```

### Step 3: Implement resumable migration

- Read a stable snapshot from `JournalNotesRepository.allNotesObserved.first()`.
- For each `JournalNote.Image`, `.Video`, or `.Audio`, call `ensureManagedMedia` unless remote.
- Verify `exists` and `readMedia` before updating the repository.
- Require `SyncableJournalNotesRepository` for the local-only reference rewrite; fail closed if unavailable.
- Update any matching `MediaSyncRef` to the new local URI while preserving remote fields.
- Do not delete the legacy source in this launch slice; safe garbage collection needs a separately proven reachability pass.
- Schedule unique, immediate, network-unconstrained work at startup. Retry only bounded transient failures; log permanent unreadable legacy refs without blocking offline capture.

### Step 4: Run GREEN

```bash
./gradlew \
  :client:sync:desktopTest --tests '*CanonicalMediaReferenceMigratorTest' \
  :app:compose-main:testAndroidHostTest --tests '*CanonicalMediaMigrationWorkerTest' \
  :client:sync:ktlintCheck \
  :app:compose-main:ktlintCheck \
  --console=plain
```

### Step 5: Review and commit

Fresh review focuses on sync races, accidental outbox writes, resume semantics, retry storms, local-reference rollback, remote-map preservation, and offline startup behavior. Resolve Critical/Important findings, rerun Step 4, then commit:

```text
fix(media): migrate legacy attachments to durable storage
```

## Task 5: Prove process-death and gallery-deletion durability on managed emulators

**Files:**

- Create: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/CanonicalMediaDurabilityE2ETest.kt`
- Modify: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/ArchiveRoundTripTest.kt`
- Create: `.superpowers/sdd/2026-08-01-android-canonical-media-storage/final-report.md`

### Step 1: Add the acceptance test

On each managed emulator, the test must:

1. create/import one image, video, and audio asset;
2. attach them to local notes while offline;
3. capture canonical URIs and byte digests;
4. delete/revoke every original provider URI and delete any explicit gallery derivative;
5. recreate the relevant repository/media manager/process-facing components;
6. load the notes again and verify exact bytes, image decode, video prepare, audio prepare, and export archive contents;
7. run migration a second time and prove no URI or object count changes;
8. verify no staged files or unexpected MediaStore rows remain.

Do not weaken the test to checking file existence alone.

### Step 2: Run the emulator matrix

```bash
./gradlew \
  :app:android-main:smokeDevicesGroupDebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.client.e2e.CanonicalMediaDurabilityE2ETest,app.logdate.client.e2e.ArchiveRoundTripTest \
  -Plogdate.androidTestCoverage=false \
  --console=plain
```

Expected for both `flagshipPhoneApi36` and `largeScreenTabletApi35`: zero skips, zero failures, zero errors. Record device-by-device counts and XML/report paths.

### Step 3: Run the affected full gate

```bash
./gradlew \
  :client:media:desktopTest \
  :client:media:testAndroidHostTest \
  :client:sync:desktopTest \
  :client:sharing:testAndroidHostTest \
  :client:domain:jvmTest \
  :app:compose-main:testAndroidHostTest \
  :app:android-main:assembleDebug \
  :client:media:ktlintCheck \
  :client:sync:ktlintCheck \
  :client:sharing:ktlintCheck \
  :client:domain:ktlintCheck \
  :app:compose-main:ktlintCheck \
  :app:android-main:ktlintCheck \
  --console=plain
```

Also run `git diff --check` and inspect all affected screenshots/consumer states if UI output changes.

### Step 4: Independent final review

A fresh reviewer must compare the implementation against every invariant at the top of this plan and the approved launch design. No Critical or Important findings may remain.

### Step 5: Commit the acceptance harness

```text
test(media): prove private attachment durability after restart
```

## Task 6: Handoff to live Cloud backup/restore proof

This task is a gate transition, not a claim of completion.

1. Push each reviewed green commit directly to `main` using the repository workflow.
2. Confirm CI for the exact pushed SHA.
3. Use the staging contract produced by the Cloud recovery plan.
4. Run the real signup/sign-in client plus one-identity session.
5. Create offline text, image, video, and audio entries whose media refs are canonical.
6. Reconnect, upload, and record returned media IDs, digests, quota deltas, and sync cursors.
7. Restore into a separate clean managed emulator, verify exact decrypted bytes and all playback/detail screens while offline, then make a new offline edit and prove it uploads after reconnect.
8. Only after that evidence is green may the parent launch plan mark backup/sync durability complete.

No Play upload or website install claim is authorized by this local-storage plan.
