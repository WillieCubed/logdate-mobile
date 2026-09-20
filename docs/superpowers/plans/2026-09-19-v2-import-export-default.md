# V2 Import/Export Default Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make LogDate export schema 2.0 the only format written by user exports and cloud backups on Android, desktop, and iOS, while continuing to preview and restore existing 1.x archives.

**Architecture:** Keep the existing repository-writing restore pipeline as the single data applier. A shared manifest-driven v2 reader converts portable v2 records into that canonical pipeline, while v1 retains its existing parser and migrations. Platforms own ZIP access and media transfer only; schema detection, manifest-role routing, preview metadata, and record conversion remain common code.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization, kotlinx.datetime, Okio, Koin, Android WorkManager, JVM ZIP APIs, iOS Foundation/UIKit.

**Spec:** `docs/data-export-specification.md`

## Global Constraints

- Schema `2.0` is the default for Android, desktop, iOS, and LogDate Cloud backups; this is a direct cutover for the app's sole active user.
- Schema 1.x remains import-only, including archives inside one Finder/Explorer wrapper directory.
- V2 paths are resolved from `manifest.json` roles and validated by `ArchivePath`; readers do not guess data filenames.
- Restore writes through ordinary repositories so imported records and journal links remain eligible for sync.
- Omitted or unreadable media never creates notes or draft blocks with dead references.
- Android verification uses JVM tests, compilation, emulators, or Gradle Managed Devices only; no physical-device command is permitted.
- Do not stage or commit until the developer reviews the complete diff. A later commit request must use the repository's atomic reset-stage-commit workflow and an exact reviewed path list.

## Review Focus

- V2 media marked `omitted` or missing from `data/media.json` is skipped with a warning and never falls back to a source-device path.
- V1 archives at the ZIP root or inside one wrapper folder still preview and restore through the v1 migration chain.
- V2 content roles with valid non-default paths are honored; missing required files fail before database mutation.
- Cancellation or failure during iOS staging removes temporary files and never presents a partial archive.
- A cloud backup uploads the exact `manifest.json` from the same v2 ZIP bytes it uploads.

## Current Worktree State

Before the request to pause for a plan, this session added partial Task 1 and Task 2 groundwork plus the initial red Task 3 staging-container test. Treat those edits as unreviewed implementation: compare them against this plan, preserve the already observed red/green evidence, and do not assume either task is complete until its full verification step passes.

---

### Task 1: Complete the Common V2 Restore Contract

**Files:**
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportModels.kt`
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportSchemaVersion.kt`
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/restore/ExportMigrations.kt`
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/restore/PreviewArchiveUseCase.kt`
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/restore/RestoreUserDataUseCase.kt`
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/restore/V2RestoreBundle.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/ExportSchemaVersionTest.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/restore/ExportMigrationsTest.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/restore/PreviewArchiveUseCaseTest.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/restore/RestoreUserDataUseCaseTest.kt`

**Interfaces:**
- Consumes: v2 manifest and data record types from `export/archive`.
- Produces: `V2RestoreBundle` and `RestoreUserDataUseCase.restore(V2RestoreBundle, RestoreOptions, MediaImporter?, onProgress)`.

- [ ] **Step 1: Keep the full v2 restore behavior test red until conversion is complete**

Build literal v2 JSON for one journal, media note, embedded membership, structured draft, profile, place, location sample, and media inventory entry. Include an omitted media record and assert that it produces a warning without creating a dead attachment. Assert repository-visible values rather than adapter internals.

```kotlin
val result = useCase.restore(
    bundle,
    mediaImporter = FakeMediaImporter(mapOf("media/photos/2026/photo.jpg" to "file:///restored/photo.jpg")),
)
assertEquals(ExportSchemaVersion.V2_0, result.metadata.version)
assertEquals(1, result.journalLinksImported)
assertEquals("America/Los_Angeles", (notesRepo.created.single() as JournalNote.Image).timeZoneId)
```

- [ ] **Step 2: Verify the test fails for missing v2 behavior**

Run:

```bash
./gradlew :client:domain:jvmTest --tests 'app.logdate.client.domain.restore.RestoreUserDataUseCaseTest.restore imports every supported record from a v2 archive'
```

Expected before completion: FAIL because v2 restore is absent or loses an asserted field.

- [ ] **Step 3: Finish v2-to-canonical conversion**

Map notes, draft blocks, profile, places, location samples, portable media paths, and embedded journal memberships. Add an optional `timeZone` to `ExportNote` and apply it to every restored note subtype. Use stable content-derived local sample IDs because v2 intentionally omits source account/device IDs.

- [ ] **Step 4: Make v2 current without extending the v1 migration chain into v2**

```kotlin
val CURRENT = V2_0

if (sourceVersion.major != 1 || sourceVersion >= ExportSchemaVersion.V1_2) return bundle
while (currentVersion < ExportSchemaVersion.V1_2) {
    val migration = migrationMap.getValue(currentVersion)
    current = migration.migrate(current)
    currentVersion = migration.to
}
```

- [ ] **Step 5: Preview either v2 manifest metadata or v1 export metadata**

Detect v2 by the `format` key, map `ArchiveCounts` into `ExportStats`, and keep v1 deserialization unchanged.

- [ ] **Step 6: Run the domain compatibility slice**

```bash
./gradlew :client:domain:jvmTest --tests 'app.logdate.client.domain.restore.*' --tests 'app.logdate.client.domain.export.ExportSchemaVersionTest' --tests 'app.logdate.client.domain.export.ExportModelSerializationTest'
```

Expected: PASS, including v1.0/v1.1/v1.2 and v2 coverage.

### Task 2: Centralize ZIP Detection and Manifest-Role Routing

**Files:**
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/restore/RestoreArchiveReader.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/restore/RestoreArchiveReaderTest.kt`
- Modify: `client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/restore/AndroidRestoreLauncher.kt`
- Modify: `client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/restore/RestoreWorker.kt`
- Modify: `client/feature/core/src/desktopMain/kotlin/app/logdate/feature/core/restore/DesktopRestoreLauncher.kt`
- Modify: `client/feature/core/src/iosMain/kotlin/app/logdate/feature/core/restore/IosRestoreLauncher.kt`

**Interfaces:**
- Consumes: archive entry names and a platform `(String) -> String?` text reader.
- Produces: `RestoreArchiveBundle.V1(root, RestoreBundle)` or `RestoreArchiveBundle.V2(root, V2RestoreBundle)`.

- [ ] **Step 1: Test v2 role routing, v1 fallback, and wrapper folders**

Use a v2 manifest whose journals role points to `data/custom-journals.json` so hardcoded path lookup cannot pass. Add a second case where a manifest-listed content path is absent and assert that reading fails before a restore bundle can be applied.

```kotlin
val archive = RestoreArchiveReader.read(files.keys, files::get)
val v2 = assertIs<RestoreArchiveBundle.V2>(archive)
assertEquals("{\"journals\":[]}", v2.bundle.journalsJson)
```

- [ ] **Step 2: Verify the reader test fails before implementation**

```bash
./gradlew :client:domain:jvmTest --tests 'app.logdate.client.domain.restore.RestoreArchiveReaderTest'
```

- [ ] **Step 3: Implement shared detection and role lookup**

Prefer `manifest.json`, fall back to `metadata.json`, preserve `ArchiveRoot.find`, and resolve v2 data by `ArchiveRole`.

- [ ] **Step 4: Route all platform preview and restore paths through the reader**

```kotlin
val archive = RestoreArchiveReader.read(entryNames) { readOptionalEntry(zip, it) }
val mediaImporter = platformMediaImporter(zip, archive.root)
restoreUserDataUseCase.restore(archive, options, mediaImporter, onProgress)
```

- [ ] **Step 5: Compile every platform source set**

```bash
./gradlew :client:feature:core:compileKotlinDesktop :client:feature:core:compileAndroidMain :client:feature:core:compileKotlinIosSimulatorArm64
```

Expected: PASS with no v1-only imports or helpers left in platform readers.

### Task 3: Add Portable iOS V2 ZIP and Media Support

**Files:**
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/StagingZipArchiveContainer.kt`
- Test: `client/domain/src/jvmTest/kotlin/app/logdate/client/domain/export/archive/StagingZipArchiveContainerTest.kt`
- Create: `client/feature/core/src/iosMain/kotlin/app/logdate/feature/core/export/IosMediaSourceOpener.kt`
- Modify: `client/feature/core/src/iosMain/kotlin/app/logdate/feature/core/di/CoreFeatureModule.ios.kt`
- Modify: `client/feature/core/src/iosMain/kotlin/app/logdate/feature/core/export/IosExportLauncher.kt`

**Interfaces:**
- Consumes: `ArchiveContainer.entry` calls and local iOS file references.
- Produces: `StagingZipArchiveContainer.finish(outputPath)` and an iOS `MediaSourceOpener` binding.

- [ ] **Step 1: Write a failing staging ZIP test**

Stage `manifest.json` and `data/notes.json`, call `finish`, inspect both entries with `ZipFile`, and assert cleanup after a simulated failure.

- [ ] **Step 2: Run the focused test**

```bash
./gradlew :client:domain:jvmTest --tests 'app.logdate.client.domain.export.archive.StagingZipArchiveContainerTest'
```

Expected before completion: FAIL because the container does not exist.

- [ ] **Step 3: Implement disk-backed staging and guaranteed cleanup**

```kotlin
override fun entry(path: ArchivePath, compress: Boolean, write: (Sink) -> Unit) {
    val target = stagingDirectory / path.value
    fileSystem.createDirectories(target.parent!!)
    fileSystem.sink(target).use(write)
    entries += ZipArchiveEntry.File(path.value, target)
}
```

Finish through `ZipArchiveWriter`; remove staging files after success, cancellation, or failure.

- [ ] **Step 4: Implement and bind `IosMediaSourceOpener`**

Accept absolute paths and `file://` references, normalize historical doubled extensions, verify the file exists, and return an Okio `Source`. Do not fetch network URLs.

- [ ] **Step 5: Switch `IosExportLauncher` to `ExportArchiveUseCase`**

Build `ArchiveExportOptions`, write the temporary v2 ZIP, present that exact file, and map `ArchiveCounts` into UI `ExportStats`.

- [ ] **Step 6: Verify portable writer and iOS compilation**

```bash
./gradlew :client:domain:jvmTest --tests 'app.logdate.client.domain.export.archive.*' :client:feature:core:compileKotlinIosSimulatorArm64
```

### Task 4: Remove the User-Export Feature Gate

**Files:**
- Modify: `client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/export/ExportWorker.kt`
- Modify: `client/feature/core/src/desktopMain/kotlin/app/logdate/feature/core/export/DesktopExportLauncher.kt`
- Modify: `client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/featureflags/FeatureFlag.kt`
- Modify: `client/datastore/src/commonTest/kotlin/app/logdate/client/datastore/featureflags/FeatureFlagStoreTest.kt`
- Test: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/ExportWorkerTest.kt`
- Test: `client/feature/core/src/desktopTest/kotlin/app/logdate/feature/core/export/ArchiveFileExportTest.kt`

**Interfaces:**
- Consumes: existing `ExportOptions` and `ExportArchiveUseCase`.
- Produces: unconditional v2 ZIPs for Android and desktop.

- [ ] **Step 1: Require `manifest.json` without setting a flag**

Assert new ZIPs contain `manifest.json`, `README.txt`, and `SHA256SUMS`, and do not contain `metadata.json`.

- [ ] **Step 2: Observe the legacy default in the focused desktop test**

```bash
./gradlew :client:feature:core:desktopTest --tests 'app.logdate.feature.core.export.ArchiveFileExportTest'
```

- [ ] **Step 3: Delete runtime format selection**

Remove `FeatureFlagStore` and legacy `ExportUserDataUseCase` from Android/desktop export paths. Invoke `ExportArchiveUseCase` directly and remove `EXPORT_ARCHIVE_V2` plus its storage assertions.

- [ ] **Step 4: Verify datastore, desktop, and Android compilation**

```bash
./gradlew :client:logdate-datastore:jvmTest :client:feature:core:desktopTest :client:feature:core:compileAndroidMain
```

### Task 5: Move LogDate Cloud Backups to V2

**Files:**
- Modify: `client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/export/CloudBackupWorker.kt`
- Test: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/CloudBackupWorkerTest.kt`

**Interfaces:**
- Consumes: `ExportArchiveUseCase` and an app-private ZIP destination.
- Produces: `BackupFile` with v2 ZIP bytes and the exact `manifest.json` from those bytes.

- [ ] **Step 1: Make the cloud test require v2 bytes and matching metadata**

```kotlin
val uploaded = requireNotNull(cloud.uploadedBackup)
ZipInputStream(ByteArrayInputStream(uploaded.data)).use { zip ->
    val entries = generateSequence(zip::getNextEntry).associate { it.name to zip.readBytes() }
    assertEquals(entries.getValue("manifest.json").decodeToString(), uploaded.manifest)
    assertFalse("metadata.json" in entries)
}
```

Add `uploadedBackup: BackupFile?` to `FakeCloudBackupDataSource` and assign it in `uploadBackup` before returning the configured result.

- [ ] **Step 2: Run only on an emulator or Gradle Managed Device**

```bash
./gradlew :app:android-main:smokeDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.client.e2e.CloudBackupWorkerTest
```

Expected before completion: FAIL because cloud backup still invokes the v1 exporter.

- [ ] **Step 3: Generate and upload one v2 artifact**

Write the app-private ZIP through `ExportArchiveUseCase`, then read its `manifest.json` for `BackupFile.manifest`. Delete the ZIP only after upload succeeds; retain it for retry on upload failure.

- [ ] **Step 4: Keep cloud restore format-neutral**

`CloudRestoreWorker` continues handing downloaded bytes to `RestoreWorker`; shared detection chooses v1 or v2.

- [ ] **Step 5: Compile the Android app**

```bash
./gradlew :app:android-main:assembleDebug
```

### Task 6: Round-Trip Proof, Cleanup, and Documentation

**Files:**
- Modify: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/ExportImportRoundTripTest.kt`
- Modify: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/ExportImportE2ETest.kt`
- Modify: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/ArchiveRoundTripTest.kt`
- Modify: `docs/data-export-specification.md`
- Delete only after `rg` proves they have no production callers: obsolete v1 export-only writers/helpers.

**Interfaces:**
- Consumes: public export and restore behavior.
- Produces: end-to-end proof that new archives are v2 and frozen v1 fixtures remain importable.

- [ ] **Step 1: Add a v2 export-to-import round trip**

Cover every note type, multiple journal memberships, structured drafts, profile, places, location history, media, and capture time zones using fresh destination repositories.

- [ ] **Step 2: Add a frozen v1 compatibility fixture**

Keep literal v1 JSON/ZIP data independent of the current writer so deleting the v1 writer cannot make compatibility tests tautological.

- [ ] **Step 3: Run domain, database coverage, and desktop suites**

```bash
./gradlew :client:domain:jvmTest :client:database:jvmTest --tests 'app.logdate.client.database.ExportCoverageTest' :client:feature:core:desktopTest
```

- [ ] **Step 4: Run multiplatform and quality gates**

```bash
./gradlew :client:domain:iosSimulatorArm64Test :client:feature:core:compileKotlinIosSimulatorArm64 :app:android-main:assembleDebug ktlintCheck
```

Expected: PASS. Report exact SDK/environment blockers instead of claiming unrun platform proof.

- [ ] **Step 5: Update the export specification**

State that v2 is written everywhere and v1 is import-only. Document manifest-role lookup and the supported restored categories; retain the honest list of data categories the v2 schema does not yet export.

- [ ] **Step 6: Prove no feature gate or production v1 writer remains**

```bash
rg -n 'EXPORT_ARCHIVE_V2|ExportUserDataUseCase|metadata.json' client/feature/core client/datastore docs/data-export-specification.md
```

Retain v1 models, migrations, `ExportFileStructure`, and parsing code wherever imports require them.

- [ ] **Step 7: Review the complete diff before any commit**

```bash
git status --short
git diff --check
git diff --stat
```

Present the implementation and verification evidence. Do not stage or commit until the developer approves.
