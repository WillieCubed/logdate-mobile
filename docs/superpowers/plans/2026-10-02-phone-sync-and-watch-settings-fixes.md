# Phone Sync and Watch Settings Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the four unflagged fixes from Track C wave 1 of the Wear OS and phone companion redesign: syncing that finishes when another device saves a newer kind of entry (D0), and three Watch settings fixes: sync options that never took effect are removed (C1a), watch note notifications follow their setting (C1b), and "Open LogDate on watch" and "Install on watch" work and explain their failures (C3a).

**Architecture:** D0 is now a small change on top of the durable download inbox that `main` gained in "Keep failed records queued instead of skipping or losing them". That design already stages every downloaded wire record before conversion, moves the cursor past the page, and offers a record that fails to convert again after a retry delay, so an entry of an unknown kind is neither lost nor blocking its page, and no cursor rewind or ledger is needed. What is still wrong is that such a record counts as unfinished recovery and as a sync error on every pass, so no full sync ever succeeds. D0 makes an `UNSUPPORTED_FORMAT` record wait for an app update instead: the shared download engine (`client/sync` commonMain, so Android, iOS and desktop all get it) stops reporting it as an error when the inbox stores it, the inbox's unfinished-work count leaves it out, and its diagnostic advises updating the app. The three settings fixes are phone-side: C1a deletes the never-read sync toggles end to end, C1b replaces the hard-coded `WearSyncNotificationHelper` with a setting-aware `WatchNoteArrivalNotifier` behind a poster seam, and C3a sends watch requests in the one intent shape `RemoteActivityHelper` accepts and returns a `WatchRequestResult` that the Watch settings turn into a specific message. Background: `docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md`, sections "Problem", "The phone companion" and "Compatibility and rollout".

**Tech Stack:** Kotlin Multiplatform (Android, iOS, desktop JVM), Room KMP (database version 50, unchanged by this plan), Compose Multiplatform with Compose resources, Koin, kotlinx.coroutines, kotlinx.serialization, Google Play services Wearable (`NodeClient`, `CapabilityClient`), `androidx.wear:wear-remote-interactions` 1.2.0 (`RemoteActivityHelper`), `androidx.core` `NotificationCompat`, kotlin.test and mockk, Gradle Managed Devices, the desktop screenshot lane and Compose preview screenshot tests.

## Global Constraints

- All four changes ship unflagged; none of them touches `FeatureFlag.WATCH_EXPERIENCE_V2`, because each fixes a defect that exists in today's build.
- No schema change: Room stays at version 50 and no Flyway migration is added (V30 is the latest server migration and this plan does not touch the server). D0 changes one DAO query, which Room does not record in the exported schema.
- Logging is Napier only (`Napier.e/w/i/d`); never `println`, `System.out` or `Log.d`.
- IDs are `kotlin.uuid.Uuid` (`Uuid.random()`, `Uuid.parse`); never `java.util.UUID`.
- Dates and times use kotlinx.datetime; `Instant` is `kotlin.time.Instant`, as everywhere in `client/sync` today.
- Serialization is kotlinx.serialization (`Json`), never hand-rolled string formats for structured data.
- Dependency injection is Koin with constructor injection; new collaborators arrive as constructor parameters and the platform modules wire them.
- Imports are ordered kotlin.* > androidx.*/kotlinx.* > app.logdate.*, with no wildcards; where that wording and ktlint disagree, ktlint's layout (`*`, `java`, `javax`, `kotlin`, then aliases) wins, because the pre-commit hook runs `ktlintFormat`.
- Use early returns and guard clauses; write no comment that restates the code (a comment may explain why).
- Never drop errors: a failure stays specific, visible and actionable; no `runCatching { }.getOrNull()`, no catch that logs at debug and returns a default that looks like success. D0 moves an unreadable-kind record out of the generic sync error, so its diagnostic must carry `UNSUPPORTED_FORMAT` with the `UPDATE_APP` action.
- Copy is literal, never speaks as "we", lives in localized string resources (Compose resources in `client/feature/core`, Android resources in `client/notifications`), and avoids vague metaphor verbs such as "kept", "held" or "carried". The same applies to commit messages and code comments.
- commonMain code must compile for iOS: run `compileKotlinIosSimulatorArm64` and `compileTestKotlinIosSimulatorArm64` for every touched KMP module; no JVM-only APIs (`MutableMap.merge`, `okio.ForwardingSink`); backtick test names contain no commas.
- Android runs use emulators or Gradle Managed Devices only: never `connected*AndroidTest`, never `install*`, never `adb` (a physical phone is usually attached).
- Phone GMD runs use `./gradlew :app:android-main:flagshipPhoneApi36DebugAndroidTest -Plogdate.androidTestClass=<fqcn>[,<fqcn>]` (or `smokeDevicesGroupDebugAndroidTest` with the same property, read at `app/android-main/build.gradle.kts:23`). Check `uptime` first; a load average far above the core count makes GMD runs crash at startup.
- Wear GMD runs use `./gradlew :app:wear:wearSmallRoundApi34DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fqcn>`; `app/wear` does not read `logdate.androidTestClass`.
- Every Gradle command in this plan is written as `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew …` (there is no `local.properties` in this worktree), and the commit chain sets `ANDROID_HOME` for the hook's ktlint run.
- Lint evidence comes from the module `ktlintCheck` tasks, which now lint Kotlin sources in every module, including `:app:android-main:ktlintCheck` and `:app:wear:ktlintCheck`. ktlint max line length is 140 (`ktlintFormat` fixes most findings); detekt only enforces size limits: 60 lines per non-Composable function and 600 per class, with existing offenders in each module's `detekt-baseline.xml`.
- Commits: one per task, Conventional Commits, title at most 72 characters, never the `style` type, scope from `allowed-scopes.txt` and never `build`, `android`, `ios`, `desktop` or `infra` (this plan uses `sync` and `settings`), body lines at most 72 characters describing user impact and root cause with no class names, last line `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Commit mechanics: write the message file in its own command (heredoc into the scratchpad), then run `git restore --staged . && git add <exact paths> && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F <file>` as one chain; never `git add -A`, `git add .`, `--no-verify` or `-m`; deleted files are staged by naming their paths in `git add`.
- Commit each task once its checks pass, and never push; leave `.github/workflows/` untouched.

---

## File Structure

Line numbers in this plan are as of `a8615ae5d` (this branch rebased onto `main` at `a69c354c6`). Tasks 2 to 4 edit some of the same files, so later tasks quote the code to find rather than relying on line numbers alone. Run the tasks in order.

**Task 1 (D0, `client/sync` and `client/database`)**

| File | Change | Responsibility |
|---|---|---|
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloadEngine.kt` | Modify | An `UNSUPPORTED_FORMAT` record the inbox stores is not a sync error |
| `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/DownloadInboxDao.kt` | Modify | `count` leaves out rows waiting for an app update |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/recovery/DownloadInbox.kt` | Modify | `UNSUPPORTED_FORMAT` retries advise `UPDATE_APP` |
| `client/sync/src/commonTest/kotlin/app/logdate/client/sync/recovery/DownloadInboxTest.kt` | Modify | In-memory DAO fake mirrors the new `count` |
| `client/sync/src/commonTest/kotlin/app/logdate/client/sync/recovery/UnsupportedContentTypeTest.kt` | Create | Sync manager and inbox behavior for an unknown entry kind |
| `client/sync/src/desktopTest/kotlin/app/logdate/client/sync/recovery/RoomDownloadInboxTest.kt` | Modify | The real Room query leaves those rows out of `count` but still offers them |

**Task 2 (C1a, Watch settings)**: deletes `WatchSyncSettingsScreen.kt`, the `WatchSyncSettings` model and its five setters, the "Sync settings" row and the `onNavigateToSync` parameter chain, the unused strings, the `WatchSyncSettings` screenshot scene with its Android preview and five baselines, and a stale checklist line; titles the Troubleshooting section "Help" and corrects its description; regenerates the watch settings baselines.

**Task 3 (C1b, watch note notifications)**: removes `includeAudioPreview` end to end; creates `WatchNoteArrivalNotifier.kt` (notifier, content, poster seam) and `AndroidWatchNoteNotificationPoster.kt`; adds `ic_notification_watch.xml` and five strings to `client/notifications`; makes `WatchNoteNotifier.noteReceived` suspend; rewires Koin; deletes `WearSyncNotificationHelper.kt` and its instrumented test; adds a host test and an instrumented test that together cover every case the deleted test covered; regenerates the watch notification settings baselines.

**Task 4 (C3a, Open and Install on watch)**: creates `WatchRequestResult.kt` and `WatchRequestFailureMessage.kt`; changes `WatchConnectionManager`, `AndroidWatchConnectionManager`, `WatchSettingsViewModel`, both watch screens and the screenshot catalog; adds the watch's VIEW intent filter; adds host, desktop, phone GMD and Wear GMD tests; adds one screenshot scene with four baselines.

None of the four tasks touches the device approval or QR connection work from `main` (`DevicesScreen`, `DeviceApproval*`, `DevicesModule`), the About page or the Labs page: the Watch page is still reached through `isWatchSupported` in `LogDateNavDisplay.kt` (Android only) and is not gated by `WATCH_EXPERIENCE_V2`. The catalog scenes for Labs and About sit next to the watch scenes in `SharedScreenshotCatalog.kt`; edit the watch scenes by the code quoted here, not by position.

---

### Task 1: Finish syncing past entries a newer app version saved (D0)

**What `main` already does.** `DefaultCloudContentDataSource.toJournalNote()` (`client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt:215-263`) throws `UnsupportedRemoteFormatException` for a type it does not know (line 261), and `readEach` (`ReadEach.kt:27-67`) turns that into a `RemoteRecordFailure` with `DiagnosticReason.UNSUPPORTED_FORMAT` while the rest of the page converts. `UnreadableRemoteRecordTest.an unsupported note type does not block the rest of the page` (`client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/UnreadableRemoteRecordTest.kt:62-87`) pins that. On every platform (`SyncModule.android.kt:82`, `SyncModule.ios.kt:79`, `SyncModule.desktop.kt:79`) the content data source reads through `DurableCloudApiClient` (`CloudModule.kt:77,112`), which stages each wire record and the page's cursor in the Room download inbox before conversion (`DurableCloudApiClient.kt:27-55`, `DownloadInbox.stage`), then returns the inbox's due rows as the page. `SyncDownloadEngine.download` (`SyncDownloadEngine.kt:138-664`) marks each failure `FAILED` in the inbox with a retry delay of at most an hour (`DownloadInbox.failed`, `DownloadInbox.kt:293-334`) and still advances the cursor (line 637), so the original record stays on the device and is offered again on later syncs and right away after "Back up now" (`DefaultSyncManager.releaseUploadBackoff`, line 612). Once a later version reads that kind, the next offer applies it. Nothing is lost and no page is blocked, so the cursor rewind and ledger the earlier draft of this plan proposed are unnecessary and are dropped.

**What is still wrong.** The engine adds `SyncError(UNKNOWN_ERROR, "Remote record remains queued for recovery")` for every failure on a page (`SyncDownloadEngine.kt:616-629`), and `DownloadInboxDao.count` (`DownloadInboxDao.kt:30-34`) counts every row that is not `APPLIED`. A record only a newer version can read never becomes `APPLIED` on this version, so:

- `DefaultSyncManager.fullSync` (`DefaultSyncManager.kt:555-586`) requires `downloadInbox.count() == 0`, so every full sync on Android, iOS and desktop reports failure and `hasMorePending`;
- `AndroidLogDateSyncWorker.doWork` (`client/sync/src/androidMain/kotlin/app/logdate/client/sync/AndroidLogDateSyncWorker.kt:76-96`) answers a failed, non-authentication result with `Result.retry()`, so Android retries the backup without end;
- `SyncStatusPublisher.publish` (`SyncStatusPublisher.kt:138-160`) sets `hasErrors` whenever `pendingRecoveryCount` is above zero, so Backup status shows an error that never clears;
- every pass that offers the record again adds a generic error, and `DownloadInbox.actionFor` (`DownloadInbox.kt:336-345`) tells Sync Diagnostics to `RETRY`, which cannot help.

`UNSUPPORTED_FORMAT` also covers `UnsupportedPayloadVersionException`, a newer encryption format (`UnreadableRemoteRecordTest.future encryption versions remain queued rather than becoming plaintext`). Only an app update reads either one, so both get the same treatment.

**The minimum change.** When the inbox stores the record (a download scope exists), an `UNSUPPORTED_FORMAT` failure is not a sync error; without an inbox nothing stores it, so the error and the existing `break` at line 631 stay. `count` leaves out rows whose reason is `UNSUPPORTED_FORMAT`, while `pending` keeps offering them. The inbox's retry diagnostic for that reason advises `UPDATE_APP`, matching `DiagnosticReportBundle.kt:126` and `DiagnosticUiSummary.kt:98`.

**Files:**
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloadEngine.kt:616-629`
- Modify: `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/DownloadInboxDao.kt:30-34`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/recovery/DownloadInbox.kt:336-345`
- Modify (test fake): `client/sync/src/commonTest/kotlin/app/logdate/client/sync/recovery/DownloadInboxTest.kt:93-100`
- Test (create): `client/sync/src/commonTest/kotlin/app/logdate/client/sync/recovery/UnsupportedContentTypeTest.kt`
- Test (modify): `client/sync/src/desktopTest/kotlin/app/logdate/client/sync/recovery/RoomDownloadInboxTest.kt` (insert before the closing brace at line 90)

**Interfaces:**
- Consumes: `DownloadInbox(dao, transactions, scope, now, diagnosticSource, diagnostics)`, `DownloadInbox.stage/failed/pending/count/cursor`, `DurableCloudApiClient(delegate, inbox)`, `testDefaultSyncManager(..., downloadInbox, transactionManager, syncScope)`, `DownloadInboxTest.Database` (the `internal` in-memory `DownloadInboxDao` and `SyncTransactionManager`).
- Produces: no new types. `DownloadInboxDao.count(owner, origin)` now means "rows this version can still finish". No signature changes, so no fake outside `DownloadInboxTest.Database` needs editing (`RoomDownloadInboxTest` delegates to the real DAO).

Main's unsupported-type behavior is already reachable without stubs, so the tests in steps 1 and 2 compile against today's code and fail on behavior.

- [ ] **Step 1: Write the sync manager tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/recovery/UnsupportedContentTypeTest.kt`:

```kotlin
package app.logdate.client.sync.recovery

import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.ContentChangesResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * A newer version of LogDate on another device can save a kind of entry this version has never
 * seen. The download inbox stores it and moves past it, but it counted as unfinished recovery and
 * as a sync error on every pass, so no full sync on Android, iOS or desktop ever succeeded and
 * Android retried the backup without end.
 */
class UnsupportedContentTypeTest {
    private val db = DownloadInboxTest.Database()
    private var now = 1_000L
    private val events = mutableListOf<SyncDiagnosticEvent>()
    private val inbox =
        DownloadInbox(
            db,
            db,
            { DownloadScope("owner", "origin") },
            { now },
            diagnostics = { event, _ -> events += event },
        )
    private val newer = Uuid.random()
    private val text = Uuid.random()
    private val api =
        fakeCloudApiClient {
            getContentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                change(newer, type = "MOMENT", serverVersion = 7L),
                                change(text, type = "TEXT", serverVersion = 8L),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = 8L,
                    ),
                )
        }
    private val notes = FakeJournalNotesRepository()

    @Test
    fun `an entry of a newer kind does not fail the sync and the rest of the page arrives`() =
        runTest {
            val manager = manager(backgroundScope)

            val result = manager.downloadRemoteChanges()

            assertTrue(result.success, "An entry from a newer version must not fail the sync: ${result.errors}")
            assertNotNull(notes.getNoteById(text), "The readable entry on the same page arrives")
            assertNull(notes.getNoteById(newer))
            assertEquals(8L, inbox.cursor("NOTE"), "The download moves past the page")
            assertEquals(0, inbox.count(), "An entry waiting for an app update is not unfinished recovery")
            assertFalse(manager.getSyncStatus().hasErrors, "Backup status shows no error for it")
        }

    @Test
    fun `an entry of a newer kind waits in the inbox for an update that can read it`() =
        runTest {
            val manager = manager(backgroundScope)
            manager.downloadRemoteChanges()
            now += 2 * 60 * 60 * 1_000L
            api.getContentChangesResponse = Result.success(ContentChangesResponse(emptyList(), emptyList(), 8L))

            val retried = manager.downloadRemoteChanges()

            assertTrue(retried.success, "Offering it again must not fail the sync either: ${retried.errors}")
            val waiting = db.rows.single { it.entityType == "NOTE" && it.entityId == newer.toString() }
            assertEquals("FAILED", waiting.state)
            assertEquals(DiagnosticReason.UNSUPPORTED_FORMAT.name, waiting.reason)
            assertEquals(2, waiting.attempts, "It was offered to this version again after its retry delay")
            assertEquals(
                "MOMENT",
                Json.decodeFromString<ContentChange>(waiting.payload).type,
                "The original record is still on the device for a later version to read",
            )
        }

    @Test
    fun `sync diagnostics advise an app update for an entry of a newer kind`() =
        runTest {
            manager(backgroundScope).downloadRemoteChanges()

            val retry = events.single { it.outcome == DiagnosticOutcome.RETRY_SCHEDULED }
            assertEquals(DiagnosticReason.UNSUPPORTED_FORMAT, retry.reason)
            assertEquals(DiagnosticAction.UPDATE_APP, retry.action)
        }

    private fun manager(scope: CoroutineScope) =
        testDefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(DurableCloudApiClient(api, inbox)),
            journalNotesRepository = notes,
            downloadInbox = inbox,
            transactionManager = db,
            syncScope = scope,
        )

    private fun change(
        id: Uuid,
        type: String,
        serverVersion: Long,
    ) = ContentChange(
        id = id.toString(),
        type = type,
        content = "Written on another device",
        createdAt = serverVersion,
        lastUpdated = serverVersion,
        serverVersion = serverVersion,
    )
}
```

`DownloadInboxTest.Database` is the `internal` in-memory DAO and transaction manager that `MediaRecoveryTest` and `DurableDownloadIntegrationTest` already share. The manager is built the way `MediaRecoveryTest.normal download pass recovers persisted attachments and exposes unresolved recovery` builds it; the journal and association downloads use the default empty fakes.

- [ ] **Step 2: Write the Room query test**

In `RoomDownloadInboxTest.kt`, add `import app.logdate.shared.model.diagnostics.DiagnosticReason` after the `app.logdate.client.sync.RoomSyncTransactionManager` import and insert before the class's closing brace (line 90):

```kotlin

    @Test
    fun `entries waiting for an app update are offered again but are not unfinished recovery`() =
        runTest {
            val path = Files.createTempFile("logdate-inbox-update-test", ".db")
            Files.delete(path)
            val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(path.toString()))
            try {
                var now = 1L
                val scope = { DownloadScope("disposable-owner", "disposable-server") }
                val inbox = DownloadInbox(database.downloadInboxDao(), RoomSyncTransactionManager(database), scope, { now })
                inbox.stage(
                    "NOTE",
                    2,
                    listOf(WireDownload("newer-kind", 1, false, "{}"), WireDownload("storage-failure", 2, false, "{}")),
                )
                inbox.failed("NOTE", "newer-kind", DiagnosticReason.UNSUPPORTED_FORMAT.name, 1)
                inbox.failed("NOTE", "storage-failure", DiagnosticReason.LOCAL_STORAGE.name, 2)

                assertEquals(1, inbox.count(), "Only the record this version can still finish counts")
                now += 60 * 60 * 1_000L
                assertEquals(setOf("newer-kind", "storage-failure"), inbox.pending("NOTE").map { it.entityId }.toSet())
            } finally {
                database.close()
                Files.deleteIfExists(path)
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
            }
        }
```

This runs the real SQL through Room on the desktop JVM, so the literal in the query and `DiagnosticReason.UNSUPPORTED_FORMAT.name` are checked against each other.

- [ ] **Step 3: Run the new tests and watch them fail**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.recovery.UnsupportedContentTypeTest' --tests 'app.logdate.client.sync.recovery.RoomDownloadInboxTest'
```

Expected: FAIL, four tests. `an entry of a newer kind does not fail the sync…` and `…waits in the inbox for an update that can read it` fail on `success` with `[SyncError(type=UNKNOWN_ERROR, message=Remote record remains queued for recovery)]`; `sync diagnostics advise an app update…` expects `UPDATE_APP` and finds `RETRY`; `entries waiting for an app update…` expects a count of 1 and finds 2. The two existing `RoomDownloadInboxTest` tests pass.

- [ ] **Step 4: Stop reporting a record only an app update can read as a sync error**

In `SyncDownloadEngine.kt`, replace the failure loop (lines 616-629):

```kotlin
                for (failure in page.failures) {
                    ensureScope(downloadScope)
                    if (downloadScope != null) {
                        downloadInbox?.failed(
                            strategy.entityType.name,
                            failure.entityId,
                            failure.reason.name,
                            failure.serverVersion,
                            downloadScope,
                            source = source,
                        )
                    }
                    errors += SyncError(SyncErrorType.UNKNOWN_ERROR, "Remote record remains queued for recovery")
                }
```

with:

```kotlin
                for (failure in page.failures) {
                    ensureScope(downloadScope)
                    if (downloadScope != null) {
                        downloadInbox?.failed(
                            strategy.entityType.name,
                            failure.entityId,
                            failure.reason.name,
                            failure.serverVersion,
                            downloadScope,
                            source = source,
                        )
                    }
                    // Only an app update reads a record a newer version wrote. The inbox stores it
                    // and offers it again, so this sync has nothing left to finish. Without an inbox
                    // nothing stores it, and the page must stay unfinished.
                    if (failure.reason == DiagnosticReason.UNSUPPORTED_FORMAT && downloadScope != null) continue
                    errors += SyncError(SyncErrorType.UNKNOWN_ERROR, "Remote record remains queued for recovery")
                }
```

`DiagnosticReason` is already imported (line 19). The `break` after the loop (lines 631-635) still checks `page.failures`, so a manager without an inbox does not advance past the record. `download` is already listed in `client/sync/detekt-baseline.xml`, so its length is not a new finding.

- [ ] **Step 5: Leave those rows out of the inbox's unfinished-work count**

In `DownloadInboxDao.kt`, replace `count` (lines 30-34):

```kotlin
    @Query("SELECT COUNT(*) FROM sync_download_inbox WHERE ownerId = :owner AND serverOrigin = :origin AND state != 'APPLIED'")
    suspend fun count(
        owner: String,
        origin: String,
    ): Int
```

with:

```kotlin
    /**
     * Rows this version can still finish. A row whose reason is `UNSUPPORTED_FORMAT` (the name of
     * `DiagnosticReason.UNSUPPORTED_FORMAT`) contains a record a newer version of LogDate wrote: it
     * stays in the inbox and [pending] offers it again, so an app update that reads it applies it,
     * but nothing this version does finishes it.
     */
    @Query(
        "SELECT COUNT(*) FROM sync_download_inbox WHERE ownerId = :owner AND serverOrigin = :origin " +
            "AND state != 'APPLIED' AND reason != 'UNSUPPORTED_FORMAT'",
    )
    suspend fun count(
        owner: String,
        origin: String,
    ): Int
```

Room accepts the concatenated constant in `@Query`. The change is a query, not a column, so the database version stays 50 and `client/database/schemas` does not change.

In `DownloadInboxTest.kt`, make the in-memory fake's `count` (lines 93-100) match:

```kotlin
        override suspend fun count(
            owner: String,
            origin: String,
        ) = rows.count {
            it.ownerId == owner &&
                it.serverOrigin == origin &&
                it.state != "APPLIED" &&
                it.reason != "UNSUPPORTED_FORMAT"
        }
```

- [ ] **Step 6: Advise an app update in the retry diagnostic**

In `DownloadInbox.kt`, add a branch to `actionFor` (lines 336-345), after the `INCOMPATIBLE_SERVER` line:

```kotlin
            DiagnosticReason.UNSUPPORTED_FORMAT -> DiagnosticAction.UPDATE_APP
```

- [ ] **Step 7: Run the new tests and watch them pass**

Run the Step 3 command again. Expected: PASS, all tests in both classes.

- [ ] **Step 8: Run every suite the shared code reaches**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:testAndroidHostTest :client:database:jvmTest :client:feature:core:desktopTest
```

Expected: `BUILD SUCCESSFUL`. `UnreadableRemoteRecordTest`, `RemoteRecordFailureTest`, `DurableDownloadIntegrationTest`, `MediaRecoveryTest`, `DraftMediaRecoveryTest` and `DownloadInboxTest` must still pass: a record that fails for any other reason still counts and still reports an error.

- [ ] **Step 9: Check iOS, ktlint and detekt**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:database:compileKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:database:ktlintCheck :client:sync:detekt :client:database:detekt
```

Expected: `BUILD SUCCESSFUL`. If ktlint fails, run the matching `ktlintFormat` task and repeat.

- [ ] **Step 10: Write the commit message**

Run as its own command:

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/d0-newer-entry-kinds.txt <<'EOF'
fix(sync): finish syncing past entries from a newer app version

When a newer version of LogDate on another device saved a kind of
entry this version cannot read, the entry waited in the download
queue as unfinished work. From then on every sync reported a failure,
Backup status showed an error that never cleared, and Android retried
the backup again and again.

Such an entry now waits for an app update without failing the sync.
Its original copy stays on this device and is offered again on later
syncs, so an update that can read it adds it to the journal. Sync
Diagnostics lists it with the advice to update the app.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 11: Commit**

Run as one command:

```bash
git restore --staged . && git add \
  client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloadEngine.kt \
  client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/DownloadInboxDao.kt \
  client/sync/src/commonMain/kotlin/app/logdate/client/sync/recovery/DownloadInbox.kt \
  client/sync/src/commonTest/kotlin/app/logdate/client/sync/recovery/DownloadInboxTest.kt \
  client/sync/src/commonTest/kotlin/app/logdate/client/sync/recovery/UnsupportedContentTypeTest.kt \
  client/sync/src/desktopTest/kotlin/app/logdate/client/sync/recovery/RoomDownloadInboxTest.kt \
  && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/d0-newer-entry-kinds.txt
```

Then run `git status --short`. Expected: no output apart from changes that were already in the tree before this task (check `git status --short` before starting and compare). If the hook's `ktlintFormat` rewrote a file outside this commit, inspect it before deciding what to do; do not discard it unseen.

**Out of scope, noted for later:**
- Backup status says nothing about entries waiting for an app update; only Sync Diagnostics lists them. A line such as "Update LogDate to see N entries from a newer version" would follow the `unreadableCloudCount` path (`SyncStatus` → `SyncStatusViewModel` → `SyncStatusSheet`) with a second DAO query counting `reason = 'UNSUPPORTED_FORMAT'`.
- Drafts and associations have their own download loops. `DraftDownloader.recordRemoteFailures` (`DraftDownloader.kt:421-433`), its `incompatible` error (line 125) and `AssociationDownloader.recordRemoteFailures` (`AssociationDownloader.kt:193-205`) still report an `UNSUPPORTED_FORMAT` record as a sync error. Rich draft blocks with a newer `encryptedBlocksVersion` (`CloudDraftDataSource.kt:135`) therefore still fail the draft sync; giving them the same treatment is a separate change with its own tests.

---

### Task 2: Remove the watch sync options that never took effect (C1a)

`WatchSyncSettings` and its five setters (`client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchSettings.kt:8-14,28,32-40`) are stored by `DefaultWatchSettingsRepository` and read only by `WatchSettingsViewModel.syncSettings`, which only the unreachable `WatchSyncSettingsScreen` collects. There is no route to that screen: the "Sync settings" row (`WatchSettingsScreen.kt:239-243`) calls `onNavigateToSync`, which `LogDateNavDisplay.kt:358` wires to the cloud `SyncSettingsRoute`. The Troubleshooting section is a `SettingsSection(title = "")` (`WatchSettingsScreen.kt:270-272`) that renders a blank heading, and its description promises a "reset sync" the screen does not have.

**Files:**
- Delete: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSyncSettingsScreen.kt`
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchSettings.kt:1-45`
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/DefaultWatchSettingsRepository.kt:1-78`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModel.kt:7,14-19,29-32,79-129`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsScreen.kt:67-68,78,92,106,144,173,205,238-243,271`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/WatchEntries.kt:26,33`
- Modify: `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/LogDateNavDisplay.kt:358`
- Modify: `client/feature/core/src/commonMain/composeResources/values/strings_watch.xml:23-24,29-40,53-54`
- Modify: `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt:25,95,223,1191,1196-1213`
- Modify: `app/android-main/src/screenshotTest/kotlin/app/logdate/screenshots/audit/adaptive/AdaptiveSettingsBookPostureScreenshots.kt:99-105`
- Modify: `docs/audits/desktop-screenshot-coverage-checklist.md:198`
- Delete: `app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/audit/adaptive/AdaptiveSettingsBookPostureScreenshotsKt/A17_WatchSyncSettingsBookPosture_Watch sync settings book posture_9b72daf6_0.png`
- Delete: `app/compose-main/src/desktopTest/reference/watch-sync-settings--{phone,phone-dark,phone-landscape,tablet}.png`
- Update: `app/compose-main/src/desktopTest/reference/watch-settings--{phone,phone-dark,phone-landscape,tablet}.png`
- Update: `app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/audit/adaptive/AdaptiveSettingsBookPostureScreenshotsKt/A16_WatchSettingsBookPosture_Watch settings book posture_1de0de4f_0.png`
- Test: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WatchPairingSettingsE2ETest.kt:11,79-87,172-212`
- Test: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModelTest.kt:5,105-134`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `interface WatchSettingsRepository { fun observeNotificationSettings(): Flow<WatchNotificationSettings>; suspend fun setShowEntryNotifications(enabled: Boolean); suspend fun setIncludeAudioPreview(enabled: Boolean) }` (Task 3 removes the last member); `fun EntryProviderScope<NavKey>.watchEntries(onBack: () -> Unit, onNavigateToNotifications: () -> Unit, onNavigateToTroubleshooting: () -> Unit)`; `fun WatchSettingsScreen(onBack, onNavigateToNotifications, onNavigateToTroubleshooting, viewModel, modifier)`; `fun WatchSettingsContent(connectionState: WatchConnectionState, onBack: () -> Unit, onBeginAssociation: () -> Unit, onRequestSync: () -> Unit, onInstallOnWatch: () -> Unit, onNavigateToNotifications: () -> Unit, onNavigateToTroubleshooting: () -> Unit, modifier: Modifier = Modifier)` (Task 4 adds `requestFailure`); Compose string `Res.string.watch_help`.

The shared `SettingsNavigationItem` (`client/ui/.../common/SettingsItems.kt:252`) requires a leading icon, so replacing the private `SettingsNavItem` (`WatchSettingsScreen.kt:489-511`) would redesign the rows rather than keep the diff small; this task leaves it. The Watch page rebuild in Track C wave 2 replaces this screen. `WatchPairingSettingsE2ETest.kt:57` keeps its existing deprecation warning for `createAndroidComposeRule`: moving to the v2 rule changes dispatcher semantics for the whole suite and belongs in its own change. The only other implementers of `WatchSettingsRepository` are the two test fakes edited here; `WatchSettingsModule.kt` binds `DefaultWatchSettingsRepository` and needs no change.

- [ ] **Step 1: Write the failing end-to-end check**

In `WatchPairingSettingsE2ETest.kt`, add the imports:

```kotlin
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
```

and insert after `watch settings pending association state is rendered` (ends at line 87):

```kotlin

    @Test
    fun `watch settings offer no sync options and title the help section`() {
        fakeConnectionManager.updateState(
            WatchConnectionState.Connected(
                watchName = "Pixel Watch",
                lastSynced = null,
                pendingCount = 0,
            ),
        )

        composeRule.onNodeWithText("Sync settings").assertDoesNotExist()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Help"))
        composeRule.onNodeWithText("Help").assertIsDisplayed()
    }
```

- [ ] **Step 2: Run it on the phone GMD and watch it fail**

Check `uptime` first. Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:flagshipPhoneApi36DebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.e2e.WatchPairingSettingsE2ETest
```

Expected: FAIL in `watch settings offer no sync options and title the help section` with `Failed: assertDoesNotExist. Reason: Expected 0 nodes but found 1`; the other three tests pass.

- [ ] **Step 3: Remove the stored toggles**

Replace the whole of `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchSettings.kt` with:

```kotlin
package app.logdate.client.domain.watch

import kotlinx.coroutines.flow.Flow

/**
 * Settings for notifications triggered by watch sync events.
 */
data class WatchNotificationSettings(
    val showEntryNotifications: Boolean = true,
    val includeAudioPreview: Boolean = false,
)

/**
 * Repository for watch-related user preferences.
 */
interface WatchSettingsRepository {
    fun observeNotificationSettings(): Flow<WatchNotificationSettings>

    suspend fun setShowEntryNotifications(enabled: Boolean)

    suspend fun setIncludeAudioPreview(enabled: Boolean)
}
```

Replace the whole of `DefaultWatchSettingsRepository.kt` with:

```kotlin
package app.logdate.client.domain.watch

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Default implementation of [WatchSettingsRepository] using [KeyValueStorage].
 */
class DefaultWatchSettingsRepository(
    private val keyValueStorage: KeyValueStorage,
) : WatchSettingsRepository {
    companion object {
        private const val KEY_SHOW_ENTRY_NOTIFICATIONS = "watch_show_entry_notifications"
        private const val KEY_INCLUDE_AUDIO_PREVIEW = "watch_include_audio_preview"
    }

    override fun observeNotificationSettings(): Flow<WatchNotificationSettings> =
        combine(
            keyValueStorage.observeBoolean(KEY_SHOW_ENTRY_NOTIFICATIONS, true),
            keyValueStorage.observeBoolean(KEY_INCLUDE_AUDIO_PREVIEW, false),
        ) { showNotifications, audioPreview ->
            WatchNotificationSettings(
                showEntryNotifications = showNotifications,
                includeAudioPreview = audioPreview,
            )
        }

    override suspend fun setShowEntryNotifications(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_SHOW_ENTRY_NOTIFICATIONS, enabled)
    }

    override suspend fun setIncludeAudioPreview(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_INCLUDE_AUDIO_PREVIEW, enabled)
    }
}
```

The `watch_sync_*` and `watch_auto_sync` booleans already stored on devices stay unread; nothing needs to migrate them.

In `WatchSettingsViewModel.kt`, delete the import at line 7 (`app.logdate.client.domain.watch.WatchSyncSettings`), delete `syncSettings` (lines 29-32 and the blank line after them), delete the block from `// Sync settings mutations` through the end of `setAutoSync` (lines 79-130, including the trailing blank line), and replace the KDoc at lines 14-19 with:

```kotlin
/**
 * ViewModel for the watch settings hub and detail screens.
 *
 * Exposes the current watch connection state and notification settings, and provides actions
 * for pairing, syncing and managing the watch app.
 */
```

In `WatchSettingsViewModelTest.kt`, delete the import at line 5 and replace `FakeWatchSettingsRepository` and the closing brace of the test class (lines 105-134) with:

```kotlin
    private class FakeWatchSettingsRepository : WatchSettingsRepository {
        private val notificationSettings = MutableStateFlow(WatchNotificationSettings())

        override fun observeNotificationSettings(): StateFlow<WatchNotificationSettings> = notificationSettings.asStateFlow()

        override suspend fun setShowEntryNotifications(enabled: Boolean) {
        }

        override suspend fun setIncludeAudioPreview(enabled: Boolean) {
        }
    }
}
```

In `WatchPairingSettingsE2ETest.kt`, delete the import at line 11 and replace `FakeWatchSettingsRepository` (lines 172-212) with:

```kotlin
private class FakeWatchSettingsRepository : WatchSettingsRepository {
    private val notificationSettings = MutableStateFlow(WatchNotificationSettings())

    fun reset() {
        notificationSettings.value = WatchNotificationSettings()
    }

    override fun observeNotificationSettings(): StateFlow<WatchNotificationSettings> =
        notificationSettings.asStateFlow()

    override suspend fun setShowEntryNotifications(enabled: Boolean) {
        notificationSettings.value = notificationSettings.value.copy(showEntryNotifications = enabled)
    }

    override suspend fun setIncludeAudioPreview(enabled: Boolean) {
        notificationSettings.value = notificationSettings.value.copy(includeAudioPreview = enabled)
    }
}
```

- [ ] **Step 4: Remove the screen, the row and the misrouted navigation**

Run `rm client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSyncSettingsScreen.kt`.

In `WatchSettingsScreen.kt`:
- delete the imports `watch_sync_settings` and `watch_sync_settings_description` (lines 67-68) and add `import logdate.client.feature.core.generated.resources.watch_help` after the `watch_connected` import (line 47);
- delete the `onNavigateToSync: () -> Unit,` parameters (lines 78 and 106) and the `onNavigateToSync = onNavigateToSync,` arguments (lines 92, 144 and 173);
- in `WatchConnectedSections`, delete the parameter at line 205 and the row at lines 239-243 together with the blank line before it (238):

```kotlin
                    SettingsNavItem(
                        title = stringResource(Res.string.watch_sync_settings),
                        description = stringResource(Res.string.watch_sync_settings_description),
                        onClick = onNavigateToSync,
                    )
```

- in `WatchTroubleshootingSection`, replace `title = "",` (line 271) with:

```kotlin
        title = stringResource(Res.string.watch_help),
```

After the edits, `WatchSettingsScreen` reads:

```kotlin
@Composable
fun WatchSettingsScreen(
    onBack: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToTroubleshooting: () -> Unit,
    viewModel: WatchSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val connectionState by viewModel.connectionState.collectAsState()

    WatchSettingsContent(
        connectionState = connectionState,
        onBack = onBack,
        onBeginAssociation = viewModel::beginAssociation,
        onRequestSync = viewModel::requestSync,
        onInstallOnWatch = viewModel::installAppOnWatch,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToTroubleshooting = onNavigateToTroubleshooting,
        modifier = modifier,
    )
}
```

In `WatchEntries.kt`, delete `onNavigateToSync: () -> Unit,` (line 26) and `onNavigateToSync = onNavigateToSync,` (line 33). In `LogDateNavDisplay.kt`, delete line 358:

```kotlin
                                            onNavigateToSync = { backStack.add(SyncSettingsRoute) },
```

Keep the `SyncSettingsRoute` import (line 57); line 261 still uses it.

In `strings_watch.xml`, delete `watch_sync_settings` and `watch_sync_settings_description` (lines 23-24) and every string from `watch_sync_what_syncs` through `watch_sync_auto_description` (lines 29-40). Replace lines 53-54 (`watch_troubleshooting` and `watch_troubleshooting_description`) with:

```xml
    <string name="watch_help">Help</string>
    <string name="watch_troubleshooting">Troubleshooting</string>
    <string name="watch_troubleshooting_description">Reconnect your watch, or install or open LogDate on it</string>
```

The strings still used by the Sync section (`watch_sync`, `watch_sync_now`, `watch_sync_up_to_date`, `watch_sync_not_yet`, `watch_sync_pending`, `watch_sync_last_connected`) stay. `values/` is the only locale for these Compose resources.

- [ ] **Step 5: Remove the screenshot scene, its preview and its baselines**

In `SharedScreenshotCatalog.kt`, delete the imports at line 25 (`app.logdate.client.domain.watch.WatchSyncSettings`) and line 95 (`app.logdate.feature.core.settings.ui.watch.WatchSyncSettingsContent`), the enum entry at line 223 (`WatchSyncSettings("watch-sync-settings"),`), the argument at line 1191 (`onNavigateToSync = {},` inside the `WatchSettings` scene; leave the one at line 703 in the `SettingsOverview` scene) and the whole `sharedScene(SharedScreenshotSceneId.WatchSyncSettings, …)` block at lines 1196-1213.

In `AdaptiveSettingsBookPostureScreenshots.kt`, delete `A17_WatchSyncSettingsBookPosture` and the blank line after it (lines 99-105). `ScreenshotOrganizationTest.audit screenshots use adaptive bucket and ordered preview prefixes` only requires the remaining `A` numbers to stay in ascending order, which they do.

In `docs/audits/desktop-screenshot-coverage-checklist.md`, delete line 198 (`` - `[todo]` Watch sync settings screen ``).

Delete the five baselines:

```bash
rm "app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/audit/adaptive/AdaptiveSettingsBookPostureScreenshotsKt/A17_WatchSyncSettingsBookPosture_Watch sync settings book posture_9b72daf6_0.png" \
  app/compose-main/src/desktopTest/reference/watch-sync-settings--phone.png \
  app/compose-main/src/desktopTest/reference/watch-sync-settings--phone-dark.png \
  app/compose-main/src/desktopTest/reference/watch-sync-settings--phone-landscape.png \
  app/compose-main/src/desktopTest/reference/watch-sync-settings--tablet.png
```

- [ ] **Step 6: Compile and run the fast tests**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:domain:jvmTest :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.watch.WatchSettingsViewModelTest' :client:screenshot-scenes:desktopTest :app:compose-main:compileKotlinDesktop :app:android-main:testDebugUnitTest --tests 'app.logdate.screenshots.ScreenshotOrganizationTest' :app:android-main:compileDebugAndroidTestKotlin :app:android-main:compileDebugScreenshotTestKotlin
```

Expected: `BUILD SUCCESSFUL`. `SharedScreenshotCatalogTest` checks that scene ids and baseline names stay unique.

- [ ] **Step 7: Run the end-to-end class on the phone GMD and watch it pass**

Run the Step 2 command again. Expected: PASS, four tests.

- [ ] **Step 8: Regenerate the watch settings baselines**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=watch-settings :app:compose-main:updateDesktopScreenshotTest --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared*'
```

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:updateDebugScreenshotTest --tests '*AdaptiveSettingsBookPosture*'
```

```bash
git status --short app/compose-main/src/desktopTest/reference app/android-main/src/screenshotTestDebug/reference
```

Expected: the four `watch-settings--*.png` files and the A16 baseline are modified, alongside the five deletions from Step 5. Open the images: the "Sync settings" row is gone and the Troubleshooting row sits under "Help". Any other changed baseline is re-render noise from this run; restore it with `git restore -- '<path>'` and do not stage it.

Then validate:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=watch- :app:compose-main:desktopTest --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared*'
```

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:validateDebugScreenshotTest --tests '*AdaptiveSettingsBookPosture*'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Check iOS, ktlint and detekt**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:domain:compileKotlinIosSimulatorArm64 :client:feature:core:compileKotlinIosSimulatorArm64 :client:feature:core:compileTestKotlinIosSimulatorArm64 :client:screenshot-scenes:compileKotlinIosSimulatorArm64 :app:compose-main:compileKotlinIosSimulatorArm64 :client:domain:ktlintCheck :client:feature:core:ktlintCheck :client:feature:core:detekt :client:screenshot-scenes:ktlintCheck :app:compose-main:ktlintCheck :app:android-main:ktlintCheck
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Write the commit message**

Run as its own command:

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/c1a-watch-sync-options.txt <<'EOF'
fix(settings): remove watch sync options that never took effect

The Watch settings page had a Sync settings row with switches for voice
notes, text entries, mood check-ins, health data and auto-sync. Nothing
ever read those switches, and the row opened the cloud backup settings
instead of them. The row, its screen and the stored switches are gone,
so the Watch page only offers options that work.

The Troubleshooting row now sits under a Help heading instead of a
blank one, and its description no longer promises a sync reset that
the screen never offered.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 11: Commit**

Run as one command:

```bash
git restore --staged . && git add \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSyncSettingsScreen.kt \
  client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchSettings.kt \
  client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/DefaultWatchSettingsRepository.kt \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModel.kt \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsScreen.kt \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/WatchEntries.kt \
  client/feature/core/src/commonMain/composeResources/values/strings_watch.xml \
  client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModelTest.kt \
  app/compose-main/src/commonMain/kotlin/app/logdate/navigation/LogDateNavDisplay.kt \
  client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt \
  app/android-main/src/screenshotTest/kotlin/app/logdate/screenshots/audit/adaptive/AdaptiveSettingsBookPostureScreenshots.kt \
  app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WatchPairingSettingsE2ETest.kt \
  docs/audits/desktop-screenshot-coverage-checklist.md \
  "app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/audit/adaptive/AdaptiveSettingsBookPostureScreenshotsKt/A16_WatchSettingsBookPosture_Watch settings book posture_1de0de4f_0.png" \
  "app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/audit/adaptive/AdaptiveSettingsBookPostureScreenshotsKt/A17_WatchSyncSettingsBookPosture_Watch sync settings book posture_9b72daf6_0.png" \
  app/compose-main/src/desktopTest/reference/watch-settings--phone.png \
  app/compose-main/src/desktopTest/reference/watch-settings--phone-dark.png \
  app/compose-main/src/desktopTest/reference/watch-settings--phone-landscape.png \
  app/compose-main/src/desktopTest/reference/watch-settings--tablet.png \
  app/compose-main/src/desktopTest/reference/watch-sync-settings--phone.png \
  app/compose-main/src/desktopTest/reference/watch-sync-settings--phone-dark.png \
  app/compose-main/src/desktopTest/reference/watch-sync-settings--phone-landscape.png \
  app/compose-main/src/desktopTest/reference/watch-sync-settings--tablet.png \
  && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/c1a-watch-sync-options.txt
```

Then run `git status --short` and compare with the state before the task. Expected: nothing new.

---

### Task 3: Watch note notifications follow their setting (C1b)

`WearSyncNotificationHelper` (`app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WearSyncNotificationHelper.kt`) never reads `WatchNotificationSettings`, hard-codes English copy (lines 69-76), uses `android.R.drawable.ic_popup_sync` (line 51) and never calls `setLocalOnly`, so Wear OS bridges the notification back to the watch that recorded the note. `includeAudioPreview` ("Show transcription preview in notification") cannot be honored: `JournalNote.Audio` has no transcript (`client/repository/src/commonMain/kotlin/app/logdate/client/repository/journals/JournalNotesRepository.kt:309-319`), transcription is a separate request through `TranscriptionRepository.requestTranscription`, and nothing requests one when a watch note arrives (`WatchNoteIngestor.kt:154-173`).

`app/android-main` host tests run on the stub `android.jar` with no Robolectric and no `unitTests.isReturnDefaultValues` (`app/android-main/build.gradle.kts:362-372` sets only the heap size), so `NotificationCompat.Builder.build()` throws there and a host test cannot drive a real notification through a mocked `NotificationManager`. This task host-tests the decision and the copy through a poster seam with a mocked `Context` (mockk mocks the final `Context.getString`), and verifies the Android notification (channel, local-only flag, icon, title, tap target) with an instrumented test on the phone GMD. Together the two cover every case of the deleted `WearSyncNotificationTest` (voice, short and long text, photo, video, a `#mood:` text note) plus the setting.

**Files:**
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchSettings.kt`, `DefaultWatchSettingsRepository.kt` (as left by Task 2)
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModel.kt` (`setIncludeAudioPreview`)
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchNotificationSettingsScreen.kt:1-77`
- Modify: `client/feature/core/src/commonMain/composeResources/values/strings_watch.xml` (the two `watch_notification_audio_preview*` strings)
- Modify: `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt` (`WatchNotificationSettings` scene; lines 1219 and 1223 before Task 2 runs)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt:57-59`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt:56,168,174-183`
- Modify: `client/notifications/src/androidMain/res/values/strings.xml:29`
- Modify: `app/android-main/build.gradle.kts:527`
- Create: `client/notifications/src/androidMain/res/drawable/ic_notification_watch.xml`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteArrivalNotifier.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/AndroidWatchNoteNotificationPoster.kt`
- Delete: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WearSyncNotificationHelper.kt`
- Delete: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WearSyncNotificationTest.kt`
- Test (create): `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteArrivalNotifierTest.kt`
- Test (create): `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WatchNoteArrivalNotificationTest.kt`
- Test (modify): `WatchSettingsViewModelTest.kt`, `WatchPairingSettingsE2ETest.kt` (fakes)
- Update: `app/compose-main/src/desktopTest/reference/watch-notification-settings--{phone,phone-dark,phone-landscape,tablet}.png`; `app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/audit/adaptive/AdaptiveSettingsBookPostureScreenshotsKt/A18_WatchNotificationSettingsBookPosture_Watch notification settings book posture_350e05fc_0.png`

**Interfaces:**
- Consumes: `WatchSettingsRepository` as left by Task 2.
- Produces: `data class WatchNotificationSettings(val showEntryNotifications: Boolean = true)`; `interface WatchSettingsRepository { fun observeNotificationSettings(): Flow<WatchNotificationSettings>; suspend fun setShowEntryNotifications(enabled: Boolean) }`; `fun interface WatchNoteNotifier { suspend fun noteReceived(note: JournalNote) }`; `data class WatchNoteArrivalContent(val title: String, val body: String)`; `fun interface WatchNoteNotificationPoster { fun post(noteId: Uuid, content: WatchNoteArrivalContent) }`; `class WatchNoteArrivalNotifier(context: Context, settingsRepository: WatchSettingsRepository, poster: WatchNoteNotificationPoster) : WatchNoteNotifier`; `class AndroidWatchNoteNotificationPoster(context: Context) : WatchNoteNotificationPoster` with `companion fun notificationId(noteId: Uuid): Int`; `NotificationResources.drawable.ic_notification_watch`; the strings `notification_watch_note_{audio,text,photo,video}_title` and `notification_watch_note_open_body`.

The default for "Show notifications for new entries" stays on. The spec's silent-by-default watch notes belong to the flagged Watch page in wave 2, not to this unflagged fix.

- [ ] **Step 1: Remove "Include audio preview" end to end**

Replace the model in `WatchSettings.kt` with:

```kotlin
package app.logdate.client.domain.watch

import kotlinx.coroutines.flow.Flow

/**
 * Settings for notifications triggered by watch sync events.
 */
data class WatchNotificationSettings(
    val showEntryNotifications: Boolean = true,
)

/**
 * Repository for watch-related user preferences.
 */
interface WatchSettingsRepository {
    fun observeNotificationSettings(): Flow<WatchNotificationSettings>

    suspend fun setShowEntryNotifications(enabled: Boolean)
}
```

Replace `DefaultWatchSettingsRepository.kt` with:

```kotlin
package app.logdate.client.domain.watch

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Default implementation of [WatchSettingsRepository] using [KeyValueStorage].
 */
class DefaultWatchSettingsRepository(
    private val keyValueStorage: KeyValueStorage,
) : WatchSettingsRepository {
    companion object {
        private const val KEY_SHOW_ENTRY_NOTIFICATIONS = "watch_show_entry_notifications"
    }

    override fun observeNotificationSettings(): Flow<WatchNotificationSettings> =
        keyValueStorage
            .observeBoolean(KEY_SHOW_ENTRY_NOTIFICATIONS, true)
            .map { showEntryNotifications -> WatchNotificationSettings(showEntryNotifications = showEntryNotifications) }

    override suspend fun setShowEntryNotifications(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_SHOW_ENTRY_NOTIFICATIONS, enabled)
    }
}
```

In `WatchSettingsViewModel.kt`, delete `setIncludeAudioPreview` (the last function in the class). Replace the whole of `WatchNotificationSettingsScreen.kt` with:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.client.domain.watch.WatchNotificationSettings
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_notification_entry_section
import logdate.client.feature.core.generated.resources.watch_notification_show
import logdate.client.feature.core.generated.resources.watch_notification_show_description
import logdate.client.feature.core.generated.resources.watch_notifications_title
import org.jetbrains.compose.resources.stringResource

/**
 * Detail screen for configuring notifications triggered by watch sync events.
 */
@Composable
fun WatchNotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: WatchSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val notificationSettings by viewModel.notificationSettings.collectAsState()

    WatchNotificationSettingsContent(
        settings = notificationSettings,
        onBack = onBack,
        onSetShowEntryNotifications = viewModel::setShowEntryNotifications,
        modifier = modifier,
    )
}

@Composable
fun WatchNotificationSettingsContent(
    settings: WatchNotificationSettings,
    onBack: () -> Unit,
    onSetShowEntryNotifications: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(Res.string.watch_notifications_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            SettingsSection(
                title = stringResource(Res.string.watch_notification_entry_section),
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                ToggleSettingsItem(
                    title = stringResource(Res.string.watch_notification_show),
                    description = stringResource(Res.string.watch_notification_show_description),
                    checked = settings.showEntryNotifications,
                    onCheckedChange = onSetShowEntryNotifications,
                )
            }
        }
    }
}
```

In `strings_watch.xml`, delete `watch_notification_audio_preview` and `watch_notification_audio_preview_description` (lines 35-36 once Task 2 has run). In `SharedScreenshotCatalog.kt`, in the `WatchNotificationSettings` scene, delete `includeAudioPreview = false,` and `onSetIncludeAudioPreview = {},`. In `WatchSettingsViewModelTest.kt` and `WatchPairingSettingsE2ETest.kt`, delete the `setIncludeAudioPreview` overrides from `FakeWatchSettingsRepository`.

- [ ] **Step 2: Add the watch icon and the notification copy**

Create `client/notifications/src/androidMain/res/drawable/ic_notification_watch.xml` (the Material "watch" glyph, white on transparent like `ic_notification_rewind.xml`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M20,12c0,-2.54 -1.19,-4.81 -3.04,-6.27L16,0H8l-0.95,5.73C5.19,7.19 4,9.45 4,12s1.19,4.81 3.05,6.27L8,24h8l0.96,-5.73C18.81,16.81 20,14.54 20,12zM6,12c0,-3.31 2.69,-6 6,-6s6,2.69 6,6 -2.69,6 -6,6 -6,-2.69 -6,-6z" />
</vector>
```

In `client/notifications/src/androidMain/res/values/strings.xml`, insert after line 29 (`notification_channel_watch_sync_description`):

```xml
    <string name="notification_watch_note_audio_title">Voice note from your watch</string>
    <string name="notification_watch_note_text_title">Note from your watch</string>
    <string name="notification_watch_note_photo_title">Photo from your watch</string>
    <string name="notification_watch_note_video_title">Video from your watch</string>
    <string name="notification_watch_note_open_body">Tap to open it in the editor</string>
```

In `app/android-main/build.gradle.kts`, add after `testImplementation(projects.client.media)` (line 527), so the host test can see the notifications module's `R` (`android.nonTransitiveRClass=true`, and `app/compose-main` only has an `implementation` dependency on it):

```kotlin
    testImplementation(projects.client.notifications)
```

- [ ] **Step 3: Declare the notifier and poster, and wire them in place of the helper**

In `WatchNoteIngestor.kt`, replace lines 57-59 with:

```kotlin
fun interface WatchNoteNotifier {
    suspend fun noteReceived(note: JournalNote)
}
```

Both callers (`receiveNote` at line 154 and `storeIfComplete` at line 163) are already suspend functions, and `WatchNoteIngestorTest` passes `notifier = { notified += it }` (lines 57 and 211), which converts to the suspend interface unchanged.

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteArrivalNotifier.kt` with the contract and a body that does nothing yet:

```kotlin
package app.logdate.client.sync

import android.content.Context
import app.logdate.client.domain.watch.WatchSettingsRepository
import app.logdate.client.repository.journals.JournalNote
import kotlin.uuid.Uuid

/** The words on the notification for one note that arrived from the watch. */
data class WatchNoteArrivalContent(
    val title: String,
    val body: String,
)

/** Shows the notification for one watch note; [AndroidWatchNoteNotificationPoster] is the real one. */
fun interface WatchNoteNotificationPoster {
    fun post(
        noteId: Uuid,
        content: WatchNoteArrivalContent,
    )
}

/**
 * Tells you a note arrived from your watch, only while "Show notifications for new entries" is on
 * in the Watch settings, and in the phone's language.
 */
class WatchNoteArrivalNotifier(
    private val context: Context,
    private val settingsRepository: WatchSettingsRepository,
    private val poster: WatchNoteNotificationPoster,
) : WatchNoteNotifier {
    override suspend fun noteReceived(note: JournalNote) = Unit
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/AndroidWatchNoteNotificationPoster.kt`:

```kotlin
package app.logdate.client.sync

import android.content.Context
import kotlin.uuid.Uuid

class AndroidWatchNoteNotificationPoster(
    private val context: Context,
) : WatchNoteNotificationPoster {
    override fun post(
        noteId: Uuid,
        content: WatchNoteArrivalContent,
    ) = Unit

    companion object {
        private const val NOTIFICATION_ID_BASE = 3000

        /** One notification per note, so a second note doesn't replace the first. */
        fun notificationId(noteId: Uuid): Int = NOTIFICATION_ID_BASE + noteId.hashCode()
    }
}
```

In `AppModule.android.kt`, replace the import at line 56 (`app.logdate.client.sync.WearSyncNotificationHelper`) with `import app.logdate.client.sync.AndroidWatchNoteNotificationPoster` and `import app.logdate.client.sync.WatchNoteArrivalNotifier` (in sorted position; `ktlintFormat` reorders them if needed), replace line 168 (`single { WearSyncNotificationHelper(androidContext()) }`) with:

```kotlin
        single<WatchNoteNotifier> {
            WatchNoteArrivalNotifier(
                context = androidContext(),
                settingsRepository = get(),
                poster = AndroidWatchNoteNotificationPoster(androidContext()),
            )
        }
```

and replace the `WatchNoteIngestor` binding (lines 174-183) with:

```kotlin
        single {
            WatchNoteIngestor(
                notesRepository = get(),
                audioStore = get(),
                acknowledger = get(),
                notifier = get(),
                noteDataMapper = get(),
            )
        }
```

`WatchSettingsRepository` is bound by `watchSettingsModule` (`client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/di/WatchSettingsModule.kt:19`), which `CoreFeatureModule.android.kt:100` includes. Delete the helper and its instrumented test:

```bash
rm app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WearSyncNotificationHelper.kt \
  app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WearSyncNotificationTest.kt
```

- [ ] **Step 4: Compile**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:compileTestKotlinDesktop :client:screenshot-scenes:compileKotlinDesktop :app:android-main:compileDebugUnitTestKotlin :app:android-main:compileDebugAndroidTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Write the host test**

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteArrivalNotifierTest.kt`:

```kotlin
package app.logdate.client.sync

import android.content.Context
import app.logdate.client.domain.watch.WatchNotificationSettings
import app.logdate.client.domain.watch.WatchSettingsRepository
import app.logdate.client.repository.journals.JournalNote
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import app.logdate.client.notifications.R as NotificationResources

/**
 * The Watch settings switch for new-entry notifications was stored and never read: every note
 * from the watch posted a notification, always in English.
 */
class WatchNoteArrivalNotifierTest {
    private val context =
        mockk<Context> {
            every { getString(NotificationResources.string.notification_watch_note_audio_title) } returns "Voice note from your watch"
            every { getString(NotificationResources.string.notification_watch_note_text_title) } returns "Note from your watch"
            every { getString(NotificationResources.string.notification_watch_note_photo_title) } returns "Photo from your watch"
            every { getString(NotificationResources.string.notification_watch_note_video_title) } returns "Video from your watch"
            every { getString(NotificationResources.string.notification_watch_note_open_body) } returns "Tap to open it in the editor"
        }
    private val posted = mutableListOf<Pair<Uuid, WatchNoteArrivalContent>>()
    private val settings = FakeWatchSettingsRepository()
    private val notifier =
        WatchNoteArrivalNotifier(
            context = context,
            settingsRepository = settings,
            poster = { noteId, content -> posted += noteId to content },
        )
    private val now = Instant.fromEpochMilliseconds(1_710_000_000_000)

    @Test
    fun `no notification is posted while new-entry notifications are off`() =
        runTest {
            settings.showEntryNotifications.value = false

            notifier.noteReceived(audioNote())

            assertTrue(posted.isEmpty())
        }

    @Test
    fun `a voice note from the watch is announced with the localized copy`() =
        runTest {
            val note = audioNote()

            notifier.noteReceived(note)

            assertEquals(listOf(note.uid to WatchNoteArrivalContent("Voice note from your watch", "Tap to open it in the editor")), posted)
        }

    @Test
    fun `photos and videos from the watch are announced with their own titles`() =
        runTest {
            notifier.noteReceived(
                JournalNote.Image(uid = Uuid.random(), creationTimestamp = now, lastUpdated = now, mediaRef = "/storage/photo.jpg"),
            )
            notifier.noteReceived(
                JournalNote.Video(uid = Uuid.random(), creationTimestamp = now, lastUpdated = now, mediaRef = "/storage/clip.mp4"),
            )

            assertEquals(
                listOf(
                    WatchNoteArrivalContent("Photo from your watch", "Tap to open it in the editor"),
                    WatchNoteArrivalContent("Video from your watch", "Tap to open it in the editor"),
                ),
                posted.map { it.second },
            )
        }

    @Test
    fun `a short text note shows all of what was written`() =
        runTest {
            notifier.noteReceived(textNote("#mood: calm after the walk"))

            assertEquals(WatchNoteArrivalContent("Note from your watch", "#mood: calm after the walk"), posted.single().second)
        }

    @Test
    fun `a long text note previews the first 80 characters of what was written`() =
        runTest {
            notifier.noteReceived(textNote("A".repeat(200)))

            assertEquals(WatchNoteArrivalContent("Note from your watch", "A".repeat(80)), posted.single().second)
        }

    private fun audioNote() =
        JournalNote.Audio(
            uid = Uuid.random(),
            creationTimestamp = now,
            lastUpdated = now,
            mediaRef = "/storage/recording.m4a",
            durationMs = 4_200,
        )

    private fun textNote(content: String) =
        JournalNote.Text(uid = Uuid.random(), creationTimestamp = now, lastUpdated = now, content = content)

    private class FakeWatchSettingsRepository : WatchSettingsRepository {
        val showEntryNotifications = MutableStateFlow(true)

        override fun observeNotificationSettings(): Flow<WatchNotificationSettings> =
            showEntryNotifications.map { WatchNotificationSettings(showEntryNotifications = it) }

        override suspend fun setShowEntryNotifications(enabled: Boolean) {
            showEntryNotifications.value = enabled
        }
    }
}
```

- [ ] **Step 6: Write the instrumented notification test**

Create `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WatchNoteArrivalNotificationTest.kt`:

```kotlin
package app.logdate.client.e2e

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.client.sync.AndroidWatchNoteNotificationPoster
import app.logdate.client.sync.WatchNoteArrivalContent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import app.logdate.client.notifications.R as NotificationResources

/**
 * A note recorded on the watch must not come back to the watch as a notification about itself,
 * and tapping it on the phone opens the note in the editor.
 */
@RunWith(AndroidJUnit4::class)
class WatchNoteArrivalNotificationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val noteId = Uuid.random()

    @Before
    fun allowNotifications() {
        LogDateNotificationRegistrar(context).registerAllPhoneChannels()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun dismiss() {
        notificationManager.cancel(AndroidWatchNoteNotificationPoster.notificationId(noteId))
    }

    @Test
    fun `a watch note notification stays on the phone and opens the editor`() {
        AndroidWatchNoteNotificationPoster(context).post(
            noteId,
            WatchNoteArrivalContent(title = "Voice note from your watch", body = "Tap to open it in the editor"),
        )

        val posted = postedNotification()
        assertEquals(LogDateNotificationChannelKey.WATCH_SYNC.id, posted.channelId)
        assertTrue(posted.flags and Notification.FLAG_LOCAL_ONLY != 0, "The watch must not be alerted about its own note")
        assertEquals(NotificationResources.drawable.ic_notification_watch, posted.smallIcon.resId)
        assertEquals("Voice note from your watch", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNotNull(posted.contentIntent, "Tapping the notification opens the note in the editor")
    }

    private fun postedNotification(): Notification {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            notificationManager.activeNotifications
                .firstOrNull { it.id == AndroidWatchNoteNotificationPoster.notificationId(noteId) }
                ?.let { return it.notification }
            SystemClock.sleep(100)
        }
        error("The watch note notification was never posted")
    }
}
```

The loop waits because `notify` hands the notification to the system asynchronously. `app/android-main` already has `androidTestImplementation(projects.client.notifications)` (line 549), so the test sees the module's `R`.

- [ ] **Step 7: Run the host test and watch it fail**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.WatchNoteArrivalNotifierTest'
```

Expected: FAIL, four of five: the voice note, photo and video, short text and long text tests find nothing posted. `no notification is posted while new-entry notifications are off` already passes against the empty body; it guards the setting once posting exists.

- [ ] **Step 8: Run the instrumented test on the phone GMD and watch it fail**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:flagshipPhoneApi36DebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.e2e.WatchNoteArrivalNotificationTest
```

Expected: FAIL with `IllegalStateException: The watch note notification was never posted`.

- [ ] **Step 9: Implement the notifier**

Replace the `WatchNoteArrivalNotifier` class in `WatchNoteArrivalNotifier.kt` (keep the content class and the poster interface above it) and its imports, so the file reads:

```kotlin
package app.logdate.client.sync

import android.content.Context
import androidx.annotation.StringRes
import app.logdate.client.domain.watch.WatchSettingsRepository
import app.logdate.client.repository.journals.JournalNote
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid
import app.logdate.client.notifications.R as NotificationResources

/** The words on the notification for one note that arrived from the watch. */
data class WatchNoteArrivalContent(
    val title: String,
    val body: String,
)

/** Shows the notification for one watch note; [AndroidWatchNoteNotificationPoster] is the real one. */
fun interface WatchNoteNotificationPoster {
    fun post(
        noteId: Uuid,
        content: WatchNoteArrivalContent,
    )
}

/**
 * Tells you a note arrived from your watch, only while "Show notifications for new entries" is on
 * in the Watch settings, and in the phone's language.
 */
class WatchNoteArrivalNotifier(
    private val context: Context,
    private val settingsRepository: WatchSettingsRepository,
    private val poster: WatchNoteNotificationPoster,
) : WatchNoteNotifier {
    override suspend fun noteReceived(note: JournalNote) {
        if (!settingsRepository.observeNotificationSettings().first().showEntryNotifications) return
        try {
            poster.post(note.uid, contentFor(note))
        } catch (e: RuntimeException) {
            Napier.e("Could not show the notification for watch note ${note.uid}", e)
        }
    }

    private fun contentFor(note: JournalNote): WatchNoteArrivalContent =
        when (note) {
            is JournalNote.Audio -> openInEditor(NotificationResources.string.notification_watch_note_audio_title)
            is JournalNote.Image -> openInEditor(NotificationResources.string.notification_watch_note_photo_title)
            is JournalNote.Video -> openInEditor(NotificationResources.string.notification_watch_note_video_title)
            is JournalNote.Text ->
                WatchNoteArrivalContent(
                    title = context.getString(NotificationResources.string.notification_watch_note_text_title),
                    body = note.content.take(TEXT_PREVIEW_LENGTH),
                )
        }

    private fun openInEditor(
        @StringRes title: Int,
    ): WatchNoteArrivalContent =
        WatchNoteArrivalContent(
            title = context.getString(title),
            body = context.getString(NotificationResources.string.notification_watch_note_open_body),
        )

    private companion object {
        const val TEXT_PREVIEW_LENGTH = 80
    }
}
```

`JournalNote` has exactly the four subclasses matched here (`JournalNotesRepository.kt:270-320`), so the `when` is exhaustive. The note is already stored and acknowledged before `noteReceived` runs, so a notification that cannot be shown is logged here with its own message instead of surfacing in `PhoneDataLayerListenerService` as a misleading "Failed to process synced note".

- [ ] **Step 10: Implement the Android poster**

Replace the whole of `AndroidWatchNoteNotificationPoster.kt` with:

```kotlin
package app.logdate.client.sync

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import app.logdate.client.EditorActivity
import app.logdate.client.notifications.LogDateNotificationChannelKey
import kotlin.uuid.Uuid
import app.logdate.client.notifications.R as NotificationResources

/**
 * Posts a watch note's notification on the Watch sync channel. It is local-only: the note came
 * from the watch, so the watch is not alerted about its own note. Tapping it opens the note in
 * the editor.
 */
class AndroidWatchNoteNotificationPoster(
    private val context: Context,
) : WatchNoteNotificationPoster {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override fun post(
        noteId: Uuid,
        content: WatchNoteArrivalContent,
    ) {
        val openNote =
            PendingIntent.getActivity(
                context,
                noteId.hashCode(),
                EditorActivity.createIntent(context = context, entryId = noteId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, LogDateNotificationChannelKey.WATCH_SYNC.id)
                .setSmallIcon(NotificationResources.drawable.ic_notification_watch)
                .setContentTitle(content.title)
                .setContentText(content.body)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setContentIntent(openNote)
                .build()
        notificationManager.notify(notificationId(noteId), notification)
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 3000

        /** One notification per note, so a second note doesn't replace the first. */
        fun notificationId(noteId: Uuid): Int = NOTIFICATION_ID_BASE + noteId.hashCode()
    }
}
```

- [ ] **Step 11: Run the host tests and watch them pass**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.WatchNoteArrivalNotifierTest' --tests 'app.logdate.client.sync.WatchNoteIngestorTest' --tests 'app.logdate.client.sync.PhoneDataLayerListenerServiceTest' :client:domain:jvmTest :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.watch.WatchSettingsViewModelTest'
```

Expected: PASS.

- [ ] **Step 12: Run the instrumented tests on the phone GMD and watch them pass**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:flagshipPhoneApi36DebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.e2e.WatchNoteArrivalNotificationTest,app.logdate.client.e2e.WatchPairingSettingsE2ETest
```

Expected: PASS, five tests.

- [ ] **Step 13: Regenerate the watch notification settings baselines**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=watch-notification-settings :app:compose-main:updateDesktopScreenshotTest --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared*'
```

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:updateDebugScreenshotTest --tests '*AdaptiveSettingsBookPosture*'
```

```bash
git status --short app/compose-main/src/desktopTest/reference app/android-main/src/screenshotTestDebug/reference
```

Expected: only the four `watch-notification-settings--*.png` files and the A18 baseline changed, each now showing one switch. Restore any other changed baseline with `git restore -- '<path>'`. Then validate:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=watch- :app:compose-main:desktopTest --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared*'
```

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:validateDebugScreenshotTest --tests '*AdaptiveSettingsBookPosture*'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 14: Check iOS, ktlint and detekt**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:domain:compileKotlinIosSimulatorArm64 :client:feature:core:compileKotlinIosSimulatorArm64 :client:feature:core:compileTestKotlinIosSimulatorArm64 :client:screenshot-scenes:compileKotlinIosSimulatorArm64 :client:domain:ktlintCheck :client:feature:core:ktlintCheck :client:screenshot-scenes:ktlintCheck :client:notifications:ktlintCheck :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 15: Write the commit message**

Run as its own command:

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/c1b-watch-note-notifications.txt <<'EOF'
fix(settings): watch note notifications follow their setting

Turning off "Show notifications for new entries" in the Watch settings
never stopped them. Every note from the watch posted a notification in
English with a generic sync icon, and the watch was alerted about the
note it had just recorded.

Watch notes now notify only while that setting is on, in the phone's
language, with a watch icon, and on the phone alone. The "Include audio
preview" option is gone: a note has no transcript when it arrives, so
the preview it promised could never appear.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 16: Commit**

Run as one command:

```bash
git restore --staged . && git add \
  client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchSettings.kt \
  client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/DefaultWatchSettingsRepository.kt \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModel.kt \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchNotificationSettingsScreen.kt \
  client/feature/core/src/commonMain/composeResources/values/strings_watch.xml \
  client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModelTest.kt \
  client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt \
  client/notifications/src/androidMain/res/drawable/ic_notification_watch.xml \
  client/notifications/src/androidMain/res/values/strings.xml \
  app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt \
  app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteArrivalNotifier.kt \
  app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/AndroidWatchNoteNotificationPoster.kt \
  app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WearSyncNotificationHelper.kt \
  app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt \
  app/android-main/build.gradle.kts \
  app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteArrivalNotifierTest.kt \
  app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WatchNoteArrivalNotificationTest.kt \
  app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WearSyncNotificationTest.kt \
  app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WatchPairingSettingsE2ETest.kt \
  "app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/audit/adaptive/AdaptiveSettingsBookPostureScreenshotsKt/A18_WatchNotificationSettingsBookPosture_Watch notification settings book posture_350e05fc_0.png" \
  app/compose-main/src/desktopTest/reference/watch-notification-settings--phone.png \
  app/compose-main/src/desktopTest/reference/watch-notification-settings--phone-dark.png \
  app/compose-main/src/desktopTest/reference/watch-notification-settings--phone-landscape.png \
  app/compose-main/src/desktopTest/reference/watch-notification-settings--tablet.png \
  && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/c1b-watch-note-notifications.txt
```

Then run `git status --short` and compare with the state before the task. Expected: nothing new.

---

### Task 4: Make "Open LogDate on watch" and "Install on watch" work and explain failures (C3a)

`AndroidWatchConnectionManager.openAppOnWatch` (`app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/AndroidWatchConnectionManager.kt:77-92`) sends `Intent(ACTION_MAIN)` with `CATEGORY_LAUNCHER`. `RemoteActivityHelper` 1.2.0 rejects it: `checkTargetIntentPrecondition` throws `IllegalArgumentException("Only android.intent.action.VIEW action is currently supported for starting a remote activity")` and also requires a data URI and `CATEGORY_BROWSABLE` (confirmed in the AAR's bytecode and in the `RemoteIntent.startRemoteActivity` reference docs). The check runs inside the returned future, so `.get()` throws and the catch at lines 89-91 turns it into a log line. Both methods target `nodeClient.connectedNodes.await().firstOrNull()` (lines 61 and 78), ignoring which node advertises `logdate_watch_app` (`app/wear/src/main/res/values/wear.xml:4`). The watch's `MainActivity` (`app/wear/src/main/AndroidManifest.xml:223-233`) only declares MAIN/LAUNCHER (lines 228-232). The phone's in-app scheme is `studio.hypertext.logdate` (`app/android-main/src/main/AndroidManifest.xml:109-125`); its filter lists the hosts `journal`, `rewind`, `postcard`, `day`, `note`, `event`, `location` and `search`, not `watch`, so only the watch handles `studio.hypertext.logdate://watch/today`.

Host tests cannot read a real `Intent` (its methods throw on the stub `android.jar`), so the manager takes the intent factory as a parameter: host tests assert the link and the target node through mocks, and an instrumented test asserts the real intent's action, category and data.

**Files:**
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchRequestResult.kt`
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchRequestFailureMessage.kt`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchConnectionManager.kt:27-35`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModel.kt` (as left by Task 3)
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsScreen.kt` (as left by Task 2: `WatchSettingsScreen`, `WatchSettingsContent`, `ConnectionStatusCard`)
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchTroubleshootingScreen.kt:51-187`
- Modify: `client/feature/core/src/commonMain/composeResources/values/strings_watch.xml` (append a group)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/AndroidWatchConnectionManager.kt:1-152`
- Modify: `app/wear/src/main/AndroidManifest.xml:232`
- Modify: `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt` (imports, enum after `WatchTroubleshooting`, the `WatchSettings` and `WatchTroubleshooting` scenes, one new scene)
- Modify: `app/android-main/build.gradle.kts:522`
- Test (create): `app/android-main/src/test/kotlin/app/logdate/client/watch/AndroidWatchConnectionManagerTest.kt`
- Test (create): `app/android-main/src/androidTest/kotlin/app/logdate/client/watch/WatchLinkIntentTest.kt`
- Test (create): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchTodayLinkTest.kt`
- Test (modify): `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModelTest.kt`, `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WatchPairingSettingsE2ETest.kt`
- Create: `app/compose-main/src/desktopTest/reference/watch-troubleshooting-request-failed--{phone,phone-dark,phone-landscape,tablet}.png`

**Interfaces:**
- Consumes: `WatchSettingsContent` and `WatchSettingsScreen` signatures from Task 2; `WatchSettingsViewModel` and `WatchSettingsRepository` from Task 3.
- Produces (package `app.logdate.feature.core.settings.ui.watch`): `sealed interface WatchRequestResult { data object Sent; data class Failed(val reason: WatchRequestFailure) }`; `enum class WatchRequestFailure { NO_WATCH_CONNECTED, NOT_INSTALLED_ON_WATCH, WATCH_DID_NOT_RESPOND }`; `WatchConnectionManager.installAppOnWatch(): WatchRequestResult` and `openAppOnWatch(): WatchRequestResult`; `WatchSettingsViewModel.requestFailure: StateFlow<WatchRequestFailure?>`; `WatchSettingsContent(connectionState, requestFailure: WatchRequestFailure?, onBack, onBeginAssociation, onRequestSync, onInstallOnWatch, onNavigateToNotifications, onNavigateToTroubleshooting, modifier)`; `WatchTroubleshootingContent(connectionState, requestFailure: WatchRequestFailure?, onBack, onBeginAssociation, onInstallOnWatch, onOpenOnWatch, modifier)`; `internal fun WatchRequestFailureMessage(failure: WatchRequestFailure, modifier: Modifier = Modifier)`.
- Produces (package `app.logdate.client.watch`): `const val WATCH_TODAY_LINK = "studio.hypertext.logdate://watch/today"`; `fun playListingLink(packageName: String): String`; `fun watchLinkIntent(link: String): Intent`; `AndroidWatchConnectionManager(context, associationManager, ioDispatcher, nodeClient: NodeClient = Wearable.getNodeClient(context), capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context), messageClient: MessageClient = Wearable.getMessageClient(context), remoteActivityHelper: RemoteActivityHelper = RemoteActivityHelper(context), linkIntent: (String) -> Intent = ::watchLinkIntent)`.
- Produces: `SharedScreenshotSceneId.WatchTroubleshootingRequestFailed("watch-troubleshooting-request-failed")`.

Install prefers a connected watch without the `logdate_watch_app` capability and falls back to one that has it, because the troubleshooting button reads "Install or update on watch" and the Play listing is also where an installed watch updates. Koin needs no change: the new constructor parameters have defaults, and `AppModule.android.kt:210-216` keeps passing `context`, `associationManager` and `ioDispatcher`. The only implementers of `WatchConnectionManager` are `AndroidWatchConnectionManager` and the two test fakes edited here.

- [ ] **Step 1: Declare the request results and route them through the interface, keeping today's behavior**

Create `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchRequestResult.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.watch

/** What happened when the phone asked a watch to open something. */
sealed interface WatchRequestResult {
    /** The watch took the request. */
    data object Sent : WatchRequestResult

    /** The request didn't go through, for [reason]. */
    data class Failed(
        val reason: WatchRequestFailure,
    ) : WatchRequestResult
}

/** Why a request to the watch didn't go through, in terms of what the person can do about it. */
enum class WatchRequestFailure {
    /** The phone can't see a connected watch. */
    NO_WATCH_CONNECTED,

    /** No connected watch has LogDate installed, so there is nothing to open. */
    NOT_INSTALLED_ON_WATCH,

    /** The watch was asked and didn't open what was sent, or never answered. */
    WATCH_DID_NOT_RESPOND,
}
```

In `WatchConnectionManager.kt`, replace lines 27-35 with:

```kotlin
    /**
     * Opens LogDate's Play Store listing on a connected watch: one without LogDate so it can be
     * installed, or else one that has it so it can be updated.
     */
    suspend fun installAppOnWatch(): WatchRequestResult

    /**
     * Opens LogDate at Today on a connected watch that has it installed.
     */
    suspend fun openAppOnWatch(): WatchRequestResult
```

In `WatchSettingsViewModel.kt`, add `import kotlinx.coroutines.flow.MutableStateFlow` and, after `notificationSettings`, a value that stays null until Step 12 records failures:

```kotlin

    /** Why the last Install or Open request didn't go through; cleared when the next one starts. */
    val requestFailure: StateFlow<WatchRequestFailure?> = MutableStateFlow(null)
```

In `AndroidWatchConnectionManager.kt`, add the imports `app.logdate.feature.core.settings.ui.watch.WatchRequestFailure` and `app.logdate.feature.core.settings.ui.watch.WatchRequestResult`, add the link helpers after `SYNC_REQUEST_PATH` (line 24):

```kotlin

/** Opens LogDate on the watch at Today. The watch's MainActivity declares a matching VIEW filter. */
const val WATCH_TODAY_LINK = "studio.hypertext.logdate://watch/today"

/** LogDate's Play Store listing, which the watch opens in its own Play Store. */
fun playListingLink(packageName: String): String = "market://details?id=$packageName"

/**
 * The only intent shape [RemoteActivityHelper] starts on another device: ACTION_VIEW with a data
 * URI and CATEGORY_BROWSABLE. Anything else fails with IllegalArgumentException before it is sent.
 */
fun watchLinkIntent(link: String): Intent =
    Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setData(Uri.parse(link))
```

replace the class header and client fields (lines 30-38) with injectable clients:

```kotlin
class AndroidWatchConnectionManager(
    private val context: Context,
    private val associationManager: WatchCompanionAssociationManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val nodeClient: NodeClient = Wearable.getNodeClient(context),
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context),
    private val messageClient: MessageClient = Wearable.getMessageClient(context),
    private val remoteActivityHelper: RemoteActivityHelper = RemoteActivityHelper(context),
    private val linkIntent: (String) -> Intent = ::watchLinkIntent,
) : WatchConnectionManager {
```

and replace `installAppOnWatch` and `openAppOnWatch` (lines 60-92) with today's behavior expressed as results (Step 10 replaces both):

```kotlin
    override suspend fun installAppOnWatch(): WatchRequestResult {
        val targetNode =
            nodeClient.connectedNodes.await().firstOrNull()
                ?: return WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED)

        val playStoreIntent =
            Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse("market://details?id=${context.packageName}"))

        return try {
            withContext(ioDispatcher) {
                remoteActivityHelper.startRemoteActivity(playStoreIntent, targetNode.id).get()
            }
            WatchRequestResult.Sent
        } catch (e: Exception) {
            Napier.e(e) { "Failed to launch Play Store on watch" }
            WatchRequestResult.Failed(WatchRequestFailure.WATCH_DID_NOT_RESPOND)
        }
    }

    override suspend fun openAppOnWatch(): WatchRequestResult {
        val targetNode =
            nodeClient.connectedNodes.await().firstOrNull()
                ?: return WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED)

        val launchIntent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.packageName)

        return try {
            withContext(ioDispatcher) {
                remoteActivityHelper.startRemoteActivity(launchIntent, targetNode.id).get()
            }
            WatchRequestResult.Sent
        } catch (e: Exception) {
            Napier.e(e) { "Failed to open app on watch" }
            WatchRequestResult.Failed(WatchRequestFailure.WATCH_DID_NOT_RESPOND)
        }
    }
```

In `WatchSettingsViewModelTest.kt`, replace `FakeWatchConnectionManager` with:

```kotlin
    private class FakeWatchConnectionManager(
        initialState: WatchConnectionState = WatchConnectionState.Loading,
        private val installResult: WatchRequestResult = WatchRequestResult.Sent,
        private val openResult: WatchRequestResult = WatchRequestResult.Sent,
    ) : WatchConnectionManager {
        private val state = MutableStateFlow(initialState)

        var beginAssociationCalls: Int = 0

        override fun observeConnectionState(): Flow<WatchConnectionState> = state.asStateFlow()

        override suspend fun beginAssociation() {
            beginAssociationCalls++
        }

        override suspend fun requestSync() {
        }

        override suspend fun installAppOnWatch(): WatchRequestResult = installResult

        override suspend fun openAppOnWatch(): WatchRequestResult = openResult
    }
```

In `WatchPairingSettingsE2ETest.kt`, add the imports `app.logdate.feature.core.settings.ui.watch.WatchRequestFailure` and `app.logdate.feature.core.settings.ui.watch.WatchRequestResult`, and in `RecordingWatchConnectionManager` add two results after `openCalls`, reset them, and return them:

```kotlin
    var installResult: WatchRequestResult = WatchRequestResult.Sent
    var openResult: WatchRequestResult = WatchRequestResult.Sent

    fun reset() {
        state.value = WatchConnectionState.Loading
        beginAssociationCalls = 0
        requestSyncCalls = 0
        installCalls = 0
        openCalls = 0
        installResult = WatchRequestResult.Sent
        openResult = WatchRequestResult.Sent
    }
```

```kotlin
    override suspend fun installAppOnWatch(): WatchRequestResult {
        installCalls++
        return installResult
    }

    override suspend fun openAppOnWatch(): WatchRequestResult {
        openCalls++
        return openResult
    }
```

In `app/android-main/build.gradle.kts`, add after `testImplementation(libs.play.services.wearable)` (line 522), so host tests can mock `RemoteActivityHelper` and its `ListenableFuture` (Guava's `listenablefuture` is an API dependency of that artifact; `app/compose-main` only has an `implementation` dependency on it, line 144):

```kotlin
    testImplementation(libs.wear.remote.interactions)
```

- [ ] **Step 2: Compile**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:compileTestKotlinDesktop :app:compose-main:compileAndroidMain :app:android-main:compileDebugUnitTestKotlin :app:android-main:compileDebugAndroidTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Write the ViewModel test**

In `WatchSettingsViewModelTest.kt`, add `import kotlin.test.assertNull` and insert after `connection state is exposed from connection manager`:

```kotlin

    @Test
    fun `a request the watch could not take is shown until the next request`() =
        runTest {
            val connectionManager =
                FakeWatchConnectionManager(
                    openResult = WatchRequestResult.Failed(WatchRequestFailure.NOT_INSTALLED_ON_WATCH),
                )
            val viewModel =
                WatchSettingsViewModel(
                    connectionManager = connectionManager,
                    settingsRepository = FakeWatchSettingsRepository(),
                )

            viewModel.openAppOnWatch()
            advanceUntilIdle()
            assertEquals(WatchRequestFailure.NOT_INSTALLED_ON_WATCH, viewModel.requestFailure.value)

            viewModel.installAppOnWatch()
            advanceUntilIdle()
            assertNull(viewModel.requestFailure.value)
        }
```

- [ ] **Step 4: Write the connection manager host test**

Create `app/android-main/src/test/kotlin/app/logdate/client/watch/AndroidWatchConnectionManagerTest.kt`:

```kotlin
package app.logdate.client.watch

import android.content.Context
import android.content.Intent
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.logdate.feature.core.settings.ui.watch.WatchRequestFailure
import app.logdate.feature.core.settings.ui.watch.WatchRequestResult
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * "Open LogDate on watch" sent an ACTION_MAIN intent, which RemoteActivityHelper refuses before
 * anything leaves the phone, and both buttons picked whichever watch was listed first. These pin
 * which watch each request goes to, which link it sends, and how each failure reads.
 */
class AndroidWatchConnectionManagerTest {
    private val withLogDate = watch(nodeId = "watch-with-logdate")
    private val withoutLogDate = watch(nodeId = "watch-without-logdate")
    private val nodeClient = mockk<NodeClient>()
    private val capabilityClient = mockk<CapabilityClient>()
    private val remoteActivityHelper = mockk<RemoteActivityHelper>()
    private val linkIntent = mockk<Intent>()
    private val sentLinks = mutableListOf<String>()
    private val accepted = mockk<ListenableFuture<Void>> { every { get(any(), any()) } returns null }

    @Test
    fun `open sends the Today link to the watch that has LogDate`() =
        runTest {
            connected(withoutLogDate, withLogDate)
            every { remoteActivityHelper.startRemoteActivity(linkIntent, any()) } returns accepted

            val result = manager().openAppOnWatch()

            assertEquals(WatchRequestResult.Sent, result)
            assertEquals(listOf("studio.hypertext.logdate://watch/today"), sentLinks)
            verify(exactly = 1) { remoteActivityHelper.startRemoteActivity(linkIntent, "watch-with-logdate") }
        }

    @Test
    fun `install opens the Play listing on the watch without LogDate`() =
        runTest {
            connected(withLogDate, withoutLogDate)
            every { remoteActivityHelper.startRemoteActivity(linkIntent, any()) } returns accepted

            val result = manager().installAppOnWatch()

            assertEquals(WatchRequestResult.Sent, result)
            assertEquals(listOf("market://details?id=studio.hypertext.logdate"), sentLinks)
            verify(exactly = 1) { remoteActivityHelper.startRemoteActivity(linkIntent, "watch-without-logdate") }
        }

    @Test
    fun `install opens the Play listing on a watch that has LogDate when it is the only one`() =
        runTest {
            connected(withLogDate)
            every { remoteActivityHelper.startRemoteActivity(linkIntent, any()) } returns accepted

            manager().installAppOnWatch()

            verify(exactly = 1) { remoteActivityHelper.startRemoteActivity(linkIntent, "watch-with-logdate") }
        }

    @Test
    fun `open reports that LogDate is missing instead of sending anything`() =
        runTest {
            connected(withoutLogDate)

            val result = manager().openAppOnWatch()

            assertEquals(WatchRequestResult.Failed(WatchRequestFailure.NOT_INSTALLED_ON_WATCH), result)
            verify(exactly = 0) { remoteActivityHelper.startRemoteActivity(any(), any()) }
        }

    @Test
    fun `both requests report when no watch is connected`() =
        runTest {
            connected()
            val manager = manager()

            assertEquals(WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED), manager.openAppOnWatch())
            assertEquals(WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED), manager.installAppOnWatch())
        }

    @Test
    fun `a watch that refuses the request is reported`() =
        runTest {
            connected(withLogDate)
            val refused =
                mockk<ListenableFuture<Void>> {
                    every { get(any(), any()) } throws
                        ExecutionException(RemoteActivityHelper.RemoteIntentException("There was an error while starting remote activity."))
                }
            every { remoteActivityHelper.startRemoteActivity(linkIntent, any()) } returns refused

            assertEquals(WatchRequestResult.Failed(WatchRequestFailure.WATCH_DID_NOT_RESPOND), manager().openAppOnWatch())
        }

    @Test
    fun `a Wearable API failure reads as no watch connected`() =
        runTest {
            every { nodeClient.connectedNodes } returns Tasks.forException(IllegalStateException("Wearable API unavailable"))

            assertEquals(WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED), manager().openAppOnWatch())
        }

    private fun connected(vararg watches: Node) {
        every { nodeClient.connectedNodes } returns Tasks.forResult(watches.toList())
        val withApp = mockk<CapabilityInfo> { every { nodes } returns watches.filter { it === withLogDate }.toSet() }
        every { capabilityClient.getCapability("logdate_watch_app", CapabilityClient.FILTER_ALL) } returns Tasks.forResult(withApp)
    }

    private fun manager() =
        AndroidWatchConnectionManager(
            context = mockk<Context> { every { packageName } returns "studio.hypertext.logdate" },
            associationManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            nodeClient = nodeClient,
            capabilityClient = capabilityClient,
            messageClient = mockk<MessageClient>(),
            remoteActivityHelper = remoteActivityHelper,
            linkIntent = { link ->
                sentLinks += link
                linkIntent
            },
        )

    private fun watch(nodeId: String) =
        mockk<Node> {
            every { id } returns nodeId
            every { displayName } returns "Pixel Watch"
            every { isNearby } returns true
        }
}
```

- [ ] **Step 5: Write the phone instrumented tests**

Create `app/android-main/src/androidTest/kotlin/app/logdate/client/watch/WatchLinkIntentTest.kt`:

```kotlin
package app.logdate.client.watch

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RemoteActivityHelper only sends ACTION_VIEW intents with a data URI and CATEGORY_BROWSABLE. The
 * host tests cannot read a real Intent, so the shape every watch request uses is checked here.
 */
@RunWith(AndroidJUnit4::class)
class WatchLinkIntentTest {
    @Test
    fun `a watch link is a browsable view of the link`() {
        val intent = watchLinkIntent(WATCH_TODAY_LINK)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals("studio.hypertext.logdate://watch/today", intent.dataString)
    }
}
```

In `WatchPairingSettingsE2ETest.kt`, insert after `troubleshooting install and open actions use connection manager`:

```kotlin

    @Test
    fun `troubleshooting says why the watch did not open LogDate`() {
        fakeConnectionManager.updateState(
            WatchConnectionState.Connected(
                watchName = "Pixel Watch",
                lastSynced = null,
                pendingCount = 0,
            ),
        )
        fakeConnectionManager.openResult = WatchRequestResult.Failed(WatchRequestFailure.WATCH_DID_NOT_RESPOND)

        composeRule.onNodeWithText("Troubleshooting").performClick()
        composeRule.onNodeWithText("Open LogDate on watch").performClick()

        composeRule
            .onNodeWithText("Your watch didn't respond. Unlock it, move it close to your phone, and try again.")
            .assertIsDisplayed()
    }
```

- [ ] **Step 6: Write the watch instrumented test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchTodayLinkTest.kt` (Wear instrumented tests use snake_case names, as `LegacyPromptAlarmsTest` does):

```kotlin
package app.logdate.wear.e2e

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.wear.presentation.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone opens LogDate on the watch by sending this link through RemoteActivityHelper, which
 * only delivers browsable VIEW intents. Without a matching filter here, the request reaches the
 * watch and nothing opens.
 */
@RunWith(AndroidJUnit4::class)
class WatchTodayLinkTest {
    @Test
    @Suppress("DEPRECATION")
    fun the_today_link_from_the_phone_opens_main_activity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val link =
            Intent(Intent.ACTION_VIEW, Uri.parse("studio.hypertext.logdate://watch/today"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(context.packageName)

        val resolved = context.packageManager.resolveActivity(link, PackageManager.MATCH_DEFAULT_ONLY)

        assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
    }
}
```

`MATCH_DEFAULT_ONLY` also proves the filter includes `CATEGORY_DEFAULT`, which the watch needs when it starts the activity from the request.

- [ ] **Step 7: Run the host and desktop tests and watch them fail**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.watch.WatchSettingsViewModelTest' :app:android-main:testDebugUnitTest --tests 'app.logdate.client.watch.AndroidWatchConnectionManagerTest'
```

Expected: FAIL. The ViewModel test finds `null` instead of `NOT_INSTALLED_ON_WATCH`. Six of seven `AndroidWatchConnectionManagerTest` tests fail: the old methods build their own `Intent`s, which the stub `android.jar` refuses with `Method … not mocked`, target the first connected node, and let a Wearable API failure escape. `both requests report when no watch is connected` already passes.

- [ ] **Step 8: Run the watch test on the Wear GMD and watch it fail**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearSmallRoundApi34DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WatchTodayLinkTest
```

Expected: FAIL with `expected:<app.logdate.wear.presentation.MainActivity> but was:<null>`. Allow 3-5 minutes for a cold emulator boot.

- [ ] **Step 9: Run the phone tests on the GMD and watch the new one fail**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:flagshipPhoneApi36DebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.watch.WatchLinkIntentTest,app.logdate.client.e2e.WatchPairingSettingsE2ETest
```

Expected: FAIL in `troubleshooting says why the watch did not open LogDate` (no node with that text). `WatchLinkIntentTest` already passes: Step 1 declared the link intent in its final form.

- [ ] **Step 10: Target the right watch with the right link**

Replace the whole of `AndroidWatchConnectionManager.kt` with:

```kotlin
package app.logdate.client.watch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import app.logdate.feature.core.settings.ui.watch.WatchRequestFailure
import app.logdate.feature.core.settings.ui.watch.WatchRequestResult
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val WATCH_CAPABILITY = "logdate_watch_app"
private const val SYNC_REQUEST_PATH = "/logdate/sync/request"
private const val REMOTE_START_TIMEOUT_SECONDS = 15L

/** Opens LogDate on the watch at Today. The watch's MainActivity declares a matching VIEW filter. */
const val WATCH_TODAY_LINK = "studio.hypertext.logdate://watch/today"

/** LogDate's Play Store listing, which the watch opens in its own Play Store. */
fun playListingLink(packageName: String): String = "market://details?id=$packageName"

/**
 * The only intent shape [RemoteActivityHelper] starts on another device: ACTION_VIEW with a data
 * URI and CATEGORY_BROWSABLE. Anything else fails with IllegalArgumentException before it is sent.
 */
fun watchLinkIntent(link: String): Intent =
    Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setData(Uri.parse(link))

/**
 * Android implementation of [WatchConnectionManager] using Wear transport plus
 * Companion Device association state from the phone app.
 */
class AndroidWatchConnectionManager(
    private val context: Context,
    private val associationManager: WatchCompanionAssociationManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val nodeClient: NodeClient = Wearable.getNodeClient(context),
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context),
    private val messageClient: MessageClient = Wearable.getMessageClient(context),
    private val remoteActivityHelper: RemoteActivityHelper = RemoteActivityHelper(context),
    private val linkIntent: (String) -> Intent = ::watchLinkIntent,
) : WatchConnectionManager {
    override fun observeConnectionState(): Flow<WatchConnectionState> =
        combine(
            associationManager.observeAssociationState(),
            observeTransportSnapshot(),
        ) { associationState, transportSnapshot ->
            resolveWatchConnectionState(associationState, transportSnapshot)
        }

    override suspend fun beginAssociation() {
        associationManager.beginAssociation()
    }

    override suspend fun requestSync() {
        val nodes = nodeClient.connectedNodes.await()
        for (node in nodes) {
            messageClient.sendMessage(node.id, SYNC_REQUEST_PATH, byteArrayOf()).await()
            Napier.d { "Sent sync request to ${node.displayName}" }
        }
    }

    override suspend fun installAppOnWatch(): WatchRequestResult {
        val watches = connectedWatches() ?: return WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED)
        val target =
            watches.withoutApp.firstOrNull()
                ?: watches.withApp.firstOrNull()
                ?: return WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED)
        return startOnWatch(playListingLink(context.packageName), target)
    }

    override suspend fun openAppOnWatch(): WatchRequestResult {
        val watches = connectedWatches() ?: return WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED)
        if (watches.isEmpty()) return WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED)
        val target = watches.withApp.firstOrNull() ?: return WatchRequestResult.Failed(WatchRequestFailure.NOT_INSTALLED_ON_WATCH)
        return startOnWatch(WATCH_TODAY_LINK, target)
    }

    private suspend fun startOnWatch(
        link: String,
        watch: Node,
    ): WatchRequestResult =
        try {
            withContext(ioDispatcher) {
                remoteActivityHelper
                    .startRemoteActivity(linkIntent(link), watch.id)
                    .get(REMOTE_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            WatchRequestResult.Sent
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e(e) { "${watch.displayName} did not open $link" }
            WatchRequestResult.Failed(WatchRequestFailure.WATCH_DID_NOT_RESPOND)
        }

    /** The connected watches, split by whether they have LogDate, or null when the Wearable API failed. */
    private suspend fun connectedWatches(): ConnectedWatches? =
        try {
            val connected = nodeClient.connectedNodes.await()
            val withAppIds =
                capabilityClient
                    .getCapability(WATCH_CAPABILITY, CapabilityClient.FILTER_ALL)
                    .await()
                    .nodes
                    .map { it.id }
                    .toSet()
            val (withApp, withoutApp) = connected.partition { it.id in withAppIds }
            ConnectedWatches(withApp = withApp, withoutApp = withoutApp)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e(e) { "Could not list the watches connected to this phone" }
            null
        }

    private class ConnectedWatches(
        val withApp: List<Node>,
        val withoutApp: List<Node>,
    ) {
        fun isEmpty(): Boolean = withApp.isEmpty() && withoutApp.isEmpty()
    }

    /** Watches Wear capability changes and refreshes the current transport snapshot. */
    private fun observeTransportSnapshot(): Flow<WatchTransportSnapshot> =
        callbackFlow {
            suspend fun emitSnapshot() {
                trySend(resolveTransportSnapshot())
            }

            emitSnapshot()

            val capabilityListener =
                CapabilityClient.OnCapabilityChangedListener {
                    launch {
                        emitSnapshot()
                    }
                }
            capabilityClient.addListener(
                capabilityListener,
                Uri.parse("wear://*/$WATCH_CAPABILITY"),
                CapabilityClient.FILTER_ALL,
            )

            awaitClose {
                capabilityClient.removeListener(capabilityListener)
            }
        }

    /** Captures the current set of connected nodes and installed LogDate watch nodes. */
    private suspend fun resolveTransportSnapshot(): WatchTransportSnapshot =
        try {
            val connectedNodes =
                nodeClient.connectedNodes.await().map { node ->
                    WatchNodeSnapshot(
                        id = node.id,
                        displayName = node.displayName,
                        isNearby = node.isNearby,
                    )
                }
            val appNodes =
                capabilityClient
                    .getCapability(WATCH_CAPABILITY, CapabilityClient.FILTER_ALL)
                    .await()
                    .nodes
                    .map { node ->
                        WatchNodeSnapshot(
                            id = node.id,
                            displayName = node.displayName,
                            isNearby = node.isNearby,
                        )
                    }

            WatchTransportSnapshot(
                connectedNodes = connectedNodes,
                appNodes = appNodes,
            )
        } catch (e: Exception) {
            Napier.e(e) { "Failed to resolve watch transport state" }
            WatchTransportSnapshot()
        }
}
```

The 15-second bound turns a watch that never answers into `WATCH_DID_NOT_RESPOND` instead of a button that waits forever. `observeConnectionState`, `beginAssociation`, `requestSync` and the transport snapshot are unchanged apart from reading the injected clients.

- [ ] **Step 11: Declare the link on the watch**

In `app/wear/src/main/AndroidManifest.xml`, add a second filter to `MainActivity`, after the MAIN/LAUNCHER filter (which ends at line 232) and before `</activity>`:

```xml

            <!--
                "Open LogDate on watch" on the phone sends this link through
                RemoteActivityHelper, which only delivers browsable VIEW intents.
            -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />

                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />

                <data
                    android:scheme="studio.hypertext.logdate"
                    android:host="watch"
                    android:path="/today" />
            </intent-filter>
```

`MainActivity.onCreate` (`app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt:56-63`) finds no `tile_route` extra on this intent and opens the start route, the home screen today and Today once Track B lands.

- [ ] **Step 12: Record why a request failed**

Replace the whole of `WatchSettingsViewModel.kt` with:

```kotlin
package app.logdate.feature.core.settings.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.watch.WatchNotificationSettings
import app.logdate.client.domain.watch.WatchSettingsRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the watch settings hub and detail screens.
 *
 * Exposes the current watch connection state and notification settings, sends requests to the
 * watch, and records why the last one failed so the screens can say so.
 */
class WatchSettingsViewModel(
    private val connectionManager: WatchConnectionManager,
    private val settingsRepository: WatchSettingsRepository,
) : ViewModel() {
    val connectionState: StateFlow<WatchConnectionState> =
        connectionManager
            .observeConnectionState()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchConnectionState.Loading)

    val notificationSettings: StateFlow<WatchNotificationSettings> =
        settingsRepository
            .observeNotificationSettings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchNotificationSettings())

    private val _requestFailure = MutableStateFlow<WatchRequestFailure?>(null)

    /** Why the last Install or Open request didn't go through; cleared when the next one starts. */
    val requestFailure: StateFlow<WatchRequestFailure?> = _requestFailure.asStateFlow()

    fun beginAssociation() {
        viewModelScope.launch {
            try {
                connectionManager.beginAssociation()
            } catch (e: Exception) {
                Napier.e(e) { "Failed to begin watch association" }
            }
        }
    }

    fun requestSync() {
        viewModelScope.launch {
            try {
                connectionManager.requestSync()
            } catch (e: Exception) {
                Napier.e(e) { "Failed to request watch sync" }
            }
        }
    }

    fun installAppOnWatch() {
        sendToWatch { connectionManager.installAppOnWatch() }
    }

    fun openAppOnWatch() {
        sendToWatch { connectionManager.openAppOnWatch() }
    }

    // Notification settings mutations

    fun setShowEntryNotifications(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setShowEntryNotifications(enabled)
            } catch (e: Exception) {
                Napier.e(e) { "Failed to update entry notifications setting" }
            }
        }
    }

    private fun sendToWatch(request: suspend () -> WatchRequestResult) {
        viewModelScope.launch {
            _requestFailure.value = null
            _requestFailure.value =
                when (val result = request()) {
                    WatchRequestResult.Sent -> null
                    is WatchRequestResult.Failed -> result.reason
                }
        }
    }
}
```

`AndroidWatchConnectionManager` now reports every failure as a result, so the request functions no longer need a catch that could only log.

- [ ] **Step 13: Show the reason on both watch screens**

Append to `strings_watch.xml`, before `<!-- Watch privacy -->`:

```xml
    <!-- Watch requests -->
    <string name="watch_request_failed_no_watch">No watch is connected to this phone. Bring your watch close with Bluetooth on, then try again.</string>
    <string name="watch_request_failed_not_installed">LogDate isn&apos;t installed on your watch yet. Install it first.</string>
    <string name="watch_request_failed_no_response">Your watch didn&apos;t respond. Unlock it, move it close to your phone, and try again.</string>

```

Create `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchRequestFailureMessage.kt`:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_request_failed_no_response
import logdate.client.feature.core.generated.resources.watch_request_failed_no_watch
import logdate.client.feature.core.generated.resources.watch_request_failed_not_installed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Says why the last request to the watch didn't go through; screen readers announce it as it appears. */
@Composable
internal fun WatchRequestFailureMessage(
    failure: WatchRequestFailure,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(failure.message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private val WatchRequestFailure.message: StringResource
    get() =
        when (this) {
            WatchRequestFailure.NO_WATCH_CONNECTED -> Res.string.watch_request_failed_no_watch
            WatchRequestFailure.NOT_INSTALLED_ON_WATCH -> Res.string.watch_request_failed_not_installed
            WatchRequestFailure.WATCH_DID_NOT_RESPOND -> Res.string.watch_request_failed_no_response
        }
```

In `WatchTroubleshootingScreen.kt`:
- in `WatchTroubleshootingScreen`, collect `val requestFailure by viewModel.requestFailure.collectAsState()` after `connectionState` (line 57) and pass `requestFailure = requestFailure,` after `connectionState = connectionState,` (line 60);
- add `requestFailure: WatchRequestFailure?,` after `connectionState: WatchConnectionState,` in `WatchTroubleshootingContent` (line 71) and in `WatchTroubleshootingActions` (line 133), and pass `requestFailure = requestFailure,` after `connectionState = connectionState,` at both `WatchTroubleshootingActions` call sites (lines 89-94 and 115-120);
- in `WatchTroubleshootingActions`, after the "Open LogDate on watch" `OutlinedButton` (ends at line 184), inside the same `Column`, add:

```kotlin
            requestFailure?.let { WatchRequestFailureMessage(it) }
```

In `WatchSettingsScreen.kt`:
- in `WatchSettingsScreen`, collect `val requestFailure by viewModel.requestFailure.collectAsState()` after `connectionState` and pass `requestFailure = requestFailure,` after `connectionState = connectionState,`;
- add `requestFailure: WatchRequestFailure?,` after `connectionState: WatchConnectionState,` in `WatchSettingsContent` and in `ConnectionStatusCard`, and pass `requestFailure = requestFailure,` after `connectionState = connectionState,` at both `ConnectionStatusCard` call sites (the `startPane` and `standardContent` blocks);
- in `ConnectionStatusCard`, in the `WatchConnectionState.AppNotInstalled` branch, after the Install button:

```kotlin
                    Button(onClick = onInstallOnWatch) {
                        Text(stringResource(Res.string.watch_install_on_watch))
                    }
                    requestFailure?.let { WatchRequestFailureMessage(it) }
```

In `SharedScreenshotCatalog.kt`:
- add `import app.logdate.feature.core.settings.ui.watch.WatchRequestFailure` in sorted position among the `app.logdate.feature.core.settings.ui.watch` imports;
- add `WatchTroubleshootingRequestFailed("watch-troubleshooting-request-failed"),` after `WatchTroubleshooting("watch-troubleshooting"),` in `SharedScreenshotSceneId`;
- add `requestFailure = null,` after the `connectionState = …` argument in the `WatchSettings` and `WatchTroubleshooting` scenes;
- add a scene after the `WatchTroubleshooting` scene (before the `EventsSettings` scene):

```kotlin
            sharedScene(SharedScreenshotSceneId.WatchTroubleshootingRequestFailed, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                WatchTroubleshootingContent(
                    connectionState =
                        WatchConnectionState.Connected(
                            watchName = "Pixel Watch 3",
                            lastSynced = baseInstant,
                            pendingCount = 0,
                        ),
                    requestFailure = WatchRequestFailure.WATCH_DID_NOT_RESPOND,
                    onBack = {},
                    onBeginAssociation = {},
                    onInstallOnWatch = {},
                    onOpenOnWatch = {},
                )
            },
```

- [ ] **Step 14: Run the host and desktop tests and watch them pass**

Run the Step 7 command again, then:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.watch.*' :client:screenshot-scenes:desktopTest
```

Expected: PASS, including `WatchConnectionStateResolverTest` and `WatchCompanionAssociationManagerTest`.

- [ ] **Step 15: Run the watch test on the Wear GMD and watch it pass**

Run the Step 8 command again. Expected: PASS.

- [ ] **Step 16: Run the phone tests on the GMD and watch them pass**

Run the Step 9 command again. Expected: PASS, six tests.

- [ ] **Step 17: Add the new screenshot baselines**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=watch-troubleshooting :app:compose-main:updateDesktopScreenshotTest --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared*'
```

```bash
git status --short app/compose-main/src/desktopTest/reference
```

Expected: four new files, `watch-troubleshooting-request-failed--{phone,phone-dark,phone-landscape,tablet}.png`, each showing the red "Your watch didn't respond…" line under the three buttons; the existing `watch-troubleshooting--*` and `watch-settings--*` baselines are unchanged because their scenes pass `requestFailure = null`. Restore any other changed baseline with `git restore -- '<path>'`. Then validate every watch scene:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=watch- :app:compose-main:desktopTest --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared*'
```

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:validateDebugScreenshotTest --tests '*AdaptiveSettingsBookPosture*'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 18: Check iOS, ktlint and detekt**

Run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:compileKotlinIosSimulatorArm64 :client:feature:core:compileTestKotlinIosSimulatorArm64 :client:screenshot-scenes:compileKotlinIosSimulatorArm64 :client:feature:core:ktlintCheck :client:feature:core:detekt :client:screenshot-scenes:ktlintCheck :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck :app:wear:ktlintCheck :app:wear:compileDebugAndroidTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 19: Write the commit message**

Run as its own command:

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/c3a-open-install-on-watch.txt <<'EOF'
fix(settings): make "Open LogDate on watch" and "Install on watch" work

Open LogDate on watch sent a kind of request that can never reach a
watch, so the button did nothing. Install on watch opened the Play
Store on whichever watch was listed first, even one that already had
LogDate. Neither said anything when it failed.

Open now starts LogDate on a watch that has it. Install opens LogDate's
Play Store listing on a watch that doesn't have it yet, or on a watch
that does so it can update. When a request can't go through, the Watch
settings say why: no watch is connected, LogDate isn't on the watch
yet, or the watch didn't respond.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 20: Commit**

Run as one command:

```bash
git restore --staged . && git add \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchRequestResult.kt \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchRequestFailureMessage.kt \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchConnectionManager.kt \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModel.kt \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsScreen.kt \
  client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchTroubleshootingScreen.kt \
  client/feature/core/src/commonMain/composeResources/values/strings_watch.xml \
  client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModelTest.kt \
  client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt \
  app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/AndroidWatchConnectionManager.kt \
  app/wear/src/main/AndroidManifest.xml \
  app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchTodayLinkTest.kt \
  app/android-main/build.gradle.kts \
  app/android-main/src/test/kotlin/app/logdate/client/watch/AndroidWatchConnectionManagerTest.kt \
  app/android-main/src/androidTest/kotlin/app/logdate/client/watch/WatchLinkIntentTest.kt \
  app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WatchPairingSettingsE2ETest.kt \
  app/compose-main/src/desktopTest/reference/watch-troubleshooting-request-failed--phone.png \
  app/compose-main/src/desktopTest/reference/watch-troubleshooting-request-failed--phone-dark.png \
  app/compose-main/src/desktopTest/reference/watch-troubleshooting-request-failed--phone-landscape.png \
  app/compose-main/src/desktopTest/reference/watch-troubleshooting-request-failed--tablet.png \
  && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/c3a-open-install-on-watch.txt
```

Then run `git status --short` and compare with the state before the task. Expected: nothing new.

**Known limit:** a watch still running a build from before this commit has no VIEW filter, so Open reports "Your watch didn't respond" until the watch app updates. The watch and phone ship from the same release, so this lasts only until both update.

---

## Self-Review

**Spec coverage.** "Sync has real bugs" → "One unknown note type breaks content sync on older clients": Task 1. `main`'s durable download inbox already fixed the part the spec describes (the page no longer fails, the record is stored before conversion and offered again, the cursor moves on), so Task 1 now covers what remained: an entry only a newer version can read no longer fails every full sync, no longer keeps Backup status in error, no longer makes Android retry the backup without end, and its diagnostic advises an app update. It still protects every future content type on Android, iOS and desktop, as "Compatibility and rollout" requires, and marks stay exempt because they live in their own collection. "Settings claim things that aren't true" → toggles that do nothing and the unreachable `WatchSyncSettingsScreen`: Task 2. "The phone companion" → "Notifications: watch notes arrive silently unless you ask to be told": Task 3 makes the existing switch work, removes the option that could never work, and keeps watch notes off the watch; the silent default itself belongs to the flagged Watch page. "Setup is a chore, and parts of it are broken" → "Open LogDate on watch sends an ACTION_MAIN intent, which RemoteActivityHelper rejects": Task 4, plus install targeting and visible failures. "The status line is fake" and the watch's prompt toggles are C3b and Track B work, outside this plan. Every task ships unflagged and leaves `main` shippable on its own.

**Placeholder scan.** Every code step shows the code. The deliberate empty bodies in Task 3 Step 3 and the today's-behavior bodies in Task 4 Step 1 exist so the tests compile and then fail on behavior, as `CLAUDE.md` requires; each is replaced in a named later step of the same task, and no task commits before its replacement runs green. Task 1 needs no stubs: its tests compile against today's code.

**Type consistency.** `DownloadInboxDao.count(owner, origin)`, `DownloadInbox(dao, transactions, scope, now, diagnostics = …)`, `WireDownload(id, version, deleted, payload)`, `WatchNoteArrivalContent(title, body)`, `WatchNoteNotificationPoster.post(noteId, content)`, `AndroidWatchNoteNotificationPoster.notificationId(noteId)`, `WatchRequestResult.Sent/Failed(reason)`, `WatchRequestFailure.{NO_WATCH_CONNECTED, NOT_INSTALLED_ON_WATCH, WATCH_DID_NOT_RESPOND}`, `WATCH_TODAY_LINK`, `playListingLink` and `watchLinkIntent` are spelled the same in their declarations, tests and call sites. The Compose string `watch_request_failed_no_response` renders "Your watch didn't respond. Unlock it, move it close to your phone, and try again.", the exact text the end-to-end test looks for. The literal `'UNSUPPORTED_FORMAT'` in the DAO query, the in-memory fake and `DiagnosticReason.UNSUPPORTED_FORMAT.name` in the Room test are the same string, and the Room test fails if they drift.

**Revisions after rebasing onto `main`.** Task 1 was re-derived against the inbox design and shrank from a ledger, a cursor rewind, a new `SyncMetadataService` method and ten fake updates to three production edits and one fake: `main` already stores the unsupported record before conversion, offers it again, and never loses it, so a rewind would only re-download what the inbox already has. No Room or Flyway numbers apply (Room stays at 50, the server is untouched). Line numbers that moved were re-checked: `LogDateNavDisplay.kt` (358 and 261), `SharedScreenshotCatalog.kt` (25, 95, 223, 1191, 1196-1213, 1219, 1223), `WatchPairingSettingsE2ETest.kt` (87, 172-212), `app/android-main/build.gradle.kts` (23, 522, 527), `CoreFeatureModule.android.kt:100`, the wear manifest (228-232) and the phone manifest (109-125); `DeepLinkResolver.kt` no longer exists, so that citation is gone. Task 3's host test gained the photo, video and short-text cases so that, with the instrumented test, it covers everything the deleted `WearSyncNotificationTest` covered. Task 4's failure copy no longer uses "Keep your watch nearby". Every Gradle command carries `export ANDROID_HOME=…`, the commit chain sets `ANDROID_HOME` for the hook, and the trailer names Claude Sonnet 5.5. None of the tasks conflicts with device approval, QR connection, the About page or the Labs page.

**Research corrections folded in.** There is no `SyncDownloaderTest`; the D0 tests drive `DefaultSyncManager` through `testDefaultSyncManager` with a real `DownloadInbox`, the pattern `MediaRecoveryTest` uses, plus a Room test for the query. Task 2 also removes the Android `A17` preview, five baselines and a checklist line the brief did not list. A host test cannot drive a real notification or read a real `Intent` here, which is why Tasks 3 and 4 pair a seam-based host test with an instrumented test. Install falls back to a watch that already has LogDate so "Install or update on watch" keeps its update path.
