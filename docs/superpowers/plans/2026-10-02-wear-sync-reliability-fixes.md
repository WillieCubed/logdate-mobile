# Wear Sync Reliability Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make phone↔watch sync on today's (v1) Data Layer protocol reliable, so every watch capture reaches the phone promptly, intact and once, and from there reaches cloud backup, with no feature flag and no break in any old/new phone and watch pairing.

**Architecture:** Twelve test-first commits on the dogfood branch, each shippable on its own: Track A, "A. Reliability fixes", of `docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md`. Every fix stays inside the v1 protocol. New DataMap keys (`audioSize`, `audioSha256`) and the new acknowledgement payload (`{"lastUpdated":…}`) are optional, older builds ignore them, and a missing key or an empty payload selects today's behavior. Watch-side fixes live in `:app:wear` (`app.logdate.wear.sync`), phone-side fixes in `:app:compose-main` `androidMain` (`app.logdate.client.sync`, `app.logdate.client.watch`), and the wire format shared by both in `:client:sync` (`app.logdate.client.sync.datalayer`).

**Tech Stack:** Kotlin 2.3.21, Wear Data Layer (`play-services-wearable` 19.0.0: `DataClient`, `MessageClient`, `CapabilityClient`, `NodeClient`), kotlinx.coroutines 1.11.0 and Flow, kotlinx.serialization 1.11.0, kotlinx-datetime 0.7.1 (`kotlin.time.Instant`), Koin 4.2.1, Room, WorkManager 2.11.2, JUnit 4 + `kotlin.test` + MockK 1.14.9 + Turbine 1.2.1, Gradle Managed Device `wearSmallRoundApi34` (AGP 9.2.1).

## Global Constraints

- Unflagged: no `FeatureFlag` gate. Every change is on for everyone the moment it lands.
- v1-compatible in both directions: an old phone with a new watch and a new phone with an old watch both still work. New keys and payloads are optional and ignored by older builds, and their absence selects today's behavior.
- Logging: Napier only, never `println`, `System.out` or `Log.*`.
- UUIDs: `kotlin.uuid.Uuid` (`Uuid.random()`), never `java.util.UUID`.
- Dates and times: `kotlinx.datetime` for calendar values. Instants are `kotlin.time.Instant` and `kotlin.time.Clock`, which is where kotlinx-datetime 0.7 puts them and what every touched file already imports.
- Serialization: `kotlinx.serialization` only.
- DI: Koin constructor injection. New classes are bound in the existing modules (`wearDataModule`, `wearAudioModule`, the phone's `appModule`). New code looks things up in Koin only where its host already does: `WearableListenerService`s and `KoinComponent` workers.
- Imports: kotlin.* > androidx.*/kotlinx.* > app.logdate.*, no wildcards (CLAUDE.md). `ktlintCheck` lints the Kotlin sources of every module this plan touches: `:client:sync` and `:app:compose-main` through their Kotlin plugin, and `:app:wear` and `:app:android-main` (main, test and androidTest sources alike) since `cc3acdaf6`, with `app/wear/.editorconfig` and `app/android-main/.editorconfig` allowing PascalCase `@Composable` names. ktlint_official orders imports lexicographically with `java.*`, `javax.*` and `kotlin.*` last, and wraps an expression body that does not fit on the signature line, so a test reads ``fun `name`() =`` with `runTest {` on the next line. Every existing file follows that layout and the code in this plan copies it. The pre-commit hook runs `ktlintFormat` on staged Kotlin; if `ktlintCheck` still reports a violation, run `./gradlew :<module>:ktlintFormat`, re-run the task's tests, and include the reformatted file in the commit.
- Style: early returns over nested conditionals. No comments that restate the code; KDoc says why. Functions and classes with two or more parameters put one per line, with trailing commas.
- Never drop errors: every sync failure is logged with the entity id and the reason, stays visible (an outbox row, a dead-letter record, or the status), and is retried or actionable. No silent fallbacks: a compatibility fallback is logged.
- Copy and names are literal. The app never says "we", and the words "kept", "held" and "carried" are never used. User-visible text lives in string resources and is localized; this plan adds none, and it moves no existing string.
- Devices: emulators and Gradle Managed Devices only. Never run `connected*AndroidTest`, `install*`, `uninstall*`, or any `adb` command; a physical phone may be attached. Watch instrumented tests run only through the Wear managed-device group, `./gradlew :app:wear:wearDevicesGroupDebugAndroidTest` (one device, `wearSmallRoundApi34`, declared for `:app:wear` in the root `build.gradle.kts`). Add `-Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.TestClass>` to run one class, or a comma-separated list for several; AGP 9.2.1 passes these project properties to managed-device runs through the variant's shared `TestDataImpl` (checked in the AGP bytecode, and used for `WearDayDetailPlaybackTest` in `docs/audits/camera-audio-adaptive-quality-checklist.md`). `:app:wear` has no `logdate.androidTestClass` property; that one belongs to `:app:android-main`. Allow 3-5 minutes for a cold emulator boot. If the managed emulator reports "Process crashed" or "failed to complete startup" under host load, rerun once with the machine otherwise idle; never fall back to a connected device.
- Gradle in this worktree: prefix every command with `export ANDROID_HOME=$HOME/Library/Android/sdk &&`, and run it from `/Users/williecubed/Projects/TheHypertextStudio/logdate-mobile/.claude/worktrees/backup-sync-infinite-notification-dd4c3a`. The commit chain sets the same variable for the pre-commit hook, which runs Gradle.
- iOS: changes to `:client:sync` `commonMain` must compile with `./gradlew :client:sync:compileKotlinIosSimulatorArm64`.
- Limits: ktlint max line length 140. `detekt` (`./gradlew :<module>:detekt`) caps a class at 600 lines and a function at 60.
- Commit messages: Conventional Commits; the title is at most 72 characters, with a scope from `allowed-scopes.txt` (`wear`, `sync`) and a lowercase description that names the change declaratively (never "stop X doing Y"); the body is full sentences, user impact first and then root cause, wrapped at 72 characters; the last line is `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. `fix` bodies describe behavior, not class names. The `style` type is never used, and `build`, `android`, `ios`, `desktop` and `infra` are never scopes: a Gradle or manifest change takes the scope of the feature it serves (`wear` for Task 11's WorkManager dependency).
- Commit mechanics: one command, on its own, writes the message to `/private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/<name>.txt` with a quoted heredoc. The next command is the single chain `git restore --staged . && git add <exact paths> && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F <file>`. Never `git add -A`, `git add .`, `git commit -a`, `--no-verify` or `-m`.
- Test-first: write the test, then add the API surface with inert bodies so the test compiles and fails on its assertions (CLAUDE.md: tests fail on behavior, not on compilation), then implement. Inert bodies never reach a commit.
- Every commit leaves `main` shippable: the touched modules' unit tests, `ktlintCheck` and `detekt` pass before committing. `:app:android-main` has only tests in this plan, so its gate is `testDebugUnitTest` and `ktlintCheck`.
- Outbox API (as of `78faf0960` on `main`): every `SyncMetadataService.enqueuePending` writes a row with a fresh operation id, and `getPendingUploads` returns it with its `scope` and `operationId`. `settleIfCurrent(entityType, pending, syncedAt, version)` removes the row only while it still holds that operation, and `incrementRetryIfCurrent(entityType, pending)` counts a failure only against it, so a change queued while an upload is in flight is never cleared or charged by that upload. `markAsSynced(entityId, …)` and `incrementRetryCount(entityId, …)` still exist and act on whatever row is there. The phone's `SyncRetryCoordinator` settles through the `IfCurrent` calls and names a dead letter `pending.operationId ?: "TYPE:id"`; the watch does the same from Task 7 on.
- Order against the other 2026-10-02 plans: `2026-10-02-phone-sync-and-watch-settings-fixes.md` Task 4 (C3a) lands before Task 3 here, and Task 3 is written against the `AndroidWatchConnectionManager` and `AndroidWatchConnectionManagerTest` that C3a leaves. Before starting Task 3, `git log --oneline -1 --grep='make "Open LogDate on watch"'` must print C3a's commit; if it prints nothing, land C3a first.

---

## What the code shows today (verified at `a8615ae5d`)

First written against `ae3967205`, before this branch was rebased onto `main`. Every path, line, name and signature below and in the tasks was checked again at `a8615ae5d` (`fix(wear): show logged moods correctly and save them before recording`). What moved since the first version:
- The rebase renamed this branch's commits. The ones this plan cites are now `7c58cce8d` (`feat(wear): recover a recording the app loses when it is killed`, was `bc92f55a9`), `3677db44c` (`fix(wear): remove pulled audio when the phone deletes a note`, was `d8ddcb538`) and `3475a6276` (`fix(wear): deliver watch recordings to the phone and back them up`, was `801f41792`), and the spec is `b48fd0c35`.
- `main` brought `78faf0960` (`fix(sync): Keep failed records queued instead of skipping or losing them`: operation ids, compare-and-set uploads and settling only the captured operation; see the Outbox API constraint), `8f25cd326` (`fix(sync): Accept create-only and strict-version writes from the Mac app`: exact version checks on PATCH, so a journal update must carry the phone's own `syncVersion`; Task 9), `cc90665c4` (server: streamed backups, diagnostic reports in Flyway V30, deletion tombstones that carry their server version) and `c606d8ca4`/`a69c354c6` (device approval, wired in `CoreFeatureModule.*.kt`, `DevicesModule.kt` and the phone manifest). The hashes are the ones in this branch's history; the same commits were first published as `7c00e40fb`, `44439ea49`, `a49c5b7d8` and `5706e7bfd`. None of these edits a file this plan touches except through the outbox API, which Tasks 7, 8 and 9 now use.
- On this branch, `d69aea766` removed the fixed morning and evening prompts: `WearSettingsScreen.kt` lost 84 lines (its polling observer is now at lines 43-55), the watch manifest lost the prompt receiver (`MainActivity` is now at lines 223-233), and `LogDateWearApplication.onCreate` calls `LegacyPromptAlarms.cancel(this)` right after `recoverInterruptedRecordings(launchedAtMs)`. `a8615ae5d` changed only the mood check-in and its codec.
- `48b7abf18`, `31f19c85b` and `cc3acdaf6` formatted `app/wear` and `app/android-main` and turned on `ktlintCheck` for them. The phone-side test files this plan edits were reformatted, so their line numbers below are new; the production files in `app/compose-main` and the watch sync files did not move.

| Item | Finding | Where |
|---|---|---|
| A1 | The `DATA_CHANGED` filter lists `/logdate/notes`, `/logdate/journals`, `/logdate/associations` but not `/logdate/camera`. The phone writes `/logdate/camera/devices` and `/logdate/camera/capture-result`, and the watch only learns them in the listener service, so they never arrive, whether the app is open or closed. | `app/wear/src/main/AndroidManifest.xml:78-92`; handled at `WearDataLayerListenerService.kt:89-93` |
| A2 | No put calls `setUrgent()`. `PutDataRequest` reference: "Non-urgent DataItems will be delayed no longer than 30 minutes". | `GoogleWearDataLayerClient.kt:66-78`, `PhoneWearSyncBridge.kt:144-155` |
| A3 | Watch `sendMessage`/`sendFile` use `connectedNodes.firstOrNull()`. The phone's `requestSync` messages every connected node, and the first failed `await()` throws, so later nodes never get it. `GoogleWatchMessageSender` stops at the first failed node too. On `main`, the phone's capability file is still `res/xml/wear_capabilities.xml`, which Android never reads: shipped phones do not advertise `logdate_phone_app`. This branch moved it to `res/values/wear.xml` in `3475a6276`. Every watch build has advertised `logdate_watch_app` since `b2e59dc35`. | `GoogleWearDataLayerClient.kt:137-157, 243-271`; `AndroidWatchConnectionManager.kt:52-58`; `WatchNoteAcknowledgement.kt:43-61` |
| Echo | Each listener applies every `DATA_CHANGED` event. It reads neither the event type nor the URI host. Google's `DataClient` reference: "given a data item's URI, calling `Uri.getHost()` will return the creator's node ID". Data Layer events reach "all connected nodes", and in practice the writing node's own listener too, so the phone sends the watch an acknowledgement for each of its own audio notes it publishes. Not observable without paired hardware; Task 4 guards it either way. | `PhoneDataLayerListenerService.kt:184-210`, `WearDataLayerListenerService.kt:75-112` |
| A4 | The phone stores watch audio once any bytes arrive (`staging.length() == 0L` is the only check). | `FileWatchAudioStore.kt:45-69`, `WatchNoteIngestor.kt:163-173` |
| A5 | **Partly fixed already.** `3677db44c` deletes pulled audio when the phone deletes the note, and `7c58cce8d` never adopts an m4a without its raw file and never adopts a raw file a note references. **Still open:** pulls land in `audio_notes/recording_<random>.<ext>`, a partial pull (process killed mid-transfer) stays forever, and a pulled `.aac` killed before `updateMediaRef` looks exactly like an interrupted recording, so recovery adopts it as a duplicate note. | `WearSyncedAudioResolver.kt:38-46`, `AndroidAudioStorage.kt:17-35`, `WearRecordingRecovery.kt:43-65` |
| A6 | After 5 failures `handleSyncFailure` calls `markAsSynced`, which takes the item out of the outbox for good, with dead-letter id `TYPE_id`. The phone names a dead letter `pending.operationId ?: "TYPE:id"`, leaves the row queued, retries it after 24 h, and settles, counts and discards only the captured operation (`settleIfCurrent`, `incrementRetryIfCurrent`). The watch still uses `markAsSynced` and `incrementRetryCount` everywhere. | `WearDataLayerSyncManager.kt:530-572`; phone `SyncRetryCoordinator.kt:157-210, 354-475` |
| A7 | `onNoteAcknowledged` runs outside `syncLock`, has no version to check, and clears whatever is queued, a pending DELETE included. | `WearDataLayerSyncManager.kt:138-146` |
| A8 | Watch journals and links are applied with `createFromSync`/`deleteFromSync`, and links straight through the DAO, because `SyncableJournalContentRepository` is not bound on the phone. None of these paths enqueues an upload. The phone's uploader sends a journal UPDATE with `pending.expectedServerVersion ?: journal.syncVersion` as an exact version check, so a journal from the watch must keep the phone's `syncVersion`. | `PhoneDataLayerListenerService.kt:280-367`; `SyncUploader.kt:194-206`; `CloudJournalDataSource.kt:132-144` |
| A9 | `syncStatusFlow` is a `MutableStateFlow` that nothing writes. Home polls every 30 s, Settings every 5 s. | `WearDataLayerSyncManager.kt:77-87`; `WearHomeViewModel.kt:74-104`; `WearSettingsViewModel.kt:45-63` |
| A10 | `SyncRetryScheduleStore` stores retry times; nothing wakes the app for them. `:app:wear` has WorkManager at runtime (through `:client:location`) but not on its compile classpath. `libs.androidx.work.runtime` and `libs.androidx.work.testing` exist. | `app/wear/build.gradle.kts:114-207` |
| A11 | `deleteDataItem` builds `wear:` + path with no host, which is not the fully specified `wear://<node_id>/<path>` form that deletes one item, and nothing calls it. The phone publishes its own notes at the same `/logdate/notes/<id>` paths. `DataEvent` reference: "An event of TYPE_DELETED will only have its URI populated". Checked in the 19.0.0 bytecode: `DataMapItem.fromDataItem` returns an empty `DataMap` for such an event, so an older phone logs one decode warning and does not crash. | `GoogleWearDataLayerClient.kt:80-94` |

## File Structure

| File | Task | Responsibility |
|---|---|---|
| `app/wear/src/main/AndroidManifest.xml` | 1 | Listener filter accepts `/logdate/camera` data items |
| `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt` (new) | 1 | Resolves every phone→watch event against the installed manifest |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerClient.kt` | 2, 12 | `putDataItem(…, urgent)`; `deleteOwnDataItem` |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClient.kt` | 2, 3, 12 | `setUrgent()`; sends through `PhoneNodeSelector`; deletes its own items by `wear://<localNode>/<path>` |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/PhoneNodeSelector.kt` (new) | 3 | Phone advertising `logdate_phone_app`, nearby first; connected-node fallback for shipped phones |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/WatchNodeSelector.kt` (new) | 3 | Reachable watches advertising `logdate_watch_app`, nearby first |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteAcknowledgement.kt` | 3, 8 | Per-watch independent sends; versioned acknowledgement payload |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/AndroidWatchConnectionManager.kt` | 3, 8 | `requestSync` through `WatchMessageSender`, which replaces the `messageClient` parameter C3a added; `WatchNodeSelector.WATCH_CAPABILITY` replaces the file's private constant |
| `app/android-main/src/test/kotlin/app/logdate/client/watch/AndroidWatchConnectionManagerTest.kt` (created by C3a) | 3, 8 | Sync request tests beside C3a's open and install tests |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/DataEventFilter.kt` (new) | 4 | Watch acts only on changes another node made |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt` | 4, 9 | Phone acts only on changes another node made; journals and links go to `WatchJournalIngestor` |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt` | 4, 6, 8 | Same filter on the watch; deletes cached phone audio; passes ack payloads |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/NoteDataMapper.kt` | 5 | `AudioFileDigest`, `audioSize`/`audioSha256` keys |
| `client/sync/src/androidMain/kotlin/app/logdate/client/sync/datalayer/AudioFileDigests.kt` (new) | 5 | Length + SHA-256 while copying, or of a file |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/AudioFileDigester.kt` (new) | 5 | Measures a recording off the main thread |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/FileWatchAudioStore.kt` | 5 | Measures audio as it is written and records the digest |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt` | 5, 8 | Rejects audio that does not match; acknowledges with the stored version |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/SyncedAudioCache.kt` (new) | 6 | `filesDir/synced_audio/<noteId>.<ext>`, `.part` then atomic rename |
| `app/wear/src/main/kotlin/app/logdate/wear/playback/WearSyncedAudioResolver.kt` | 6 | Pulls into, and reuses, the cache |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/SyncedAudioFiles.kt`, `docs/feature-design/wear-audio-recording.md` | 6 | Describe where pulled audio lives |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt` | 2, 5, 7, 8, 10, 12 | Urgency, digests, dead letters that stay queued, settling only the captured operation, versioned acks under the lock, live status, data item deletion |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDeadLetterMigration.kt` (new) | 7 | Puts `TYPE_id` records back in the outbox |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/NoteAckPayload.kt` (new) | 8 | `{"lastUpdated":<epoch ms>}` codec |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/WearNoteAckHandler.kt`, `WearPhoneMessageHandler.kt` | 8 | Ack handler receives the decoded version |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchJournalIngestor.kt` (new) | 9 | Applies watch journals and links through the enqueuing repositories |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/PhoneReachability.kt` (new) | 10 | Capability-listener flow of the reachable phone |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/WearSyncStatus.kt` (new) | 10 | Combines run, outbox, dead letters and reachability into `SyncStatus` |
| `app/wear/src/main/kotlin/app/logdate/wear/presentation/home/WearHomeViewModel.kt`, `settings/WearSettingsViewModel.kt`, `settings/WearSettingsScreen.kt` | 10 | Collect flows; polling removed |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncScheduler.kt`, `WatchSyncWorker.kt` (new) | 11 | Unique work `watch_sync` at the earliest retry time |
| `app/wear/build.gradle.kts` | 11 | WorkManager on the compile and androidTest classpaths |
| `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt`, `AudioModule.kt`, `LogDateWearApplication.kt` | 6, 7, 10, 11 | Bindings and startup |
| `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt` | 3, 9 | Bindings |

## Task Map

| Task | Item | Commit title |
|---|---|---|
| 1 | A1 | `fix(wear): deliver the phone's camera updates to the watch` |
| 2 | A2 | `fix(wear): deliver captures to the phone without batching delay` |
| 3 | A3 | `fix(wear): send messages to the devices that run LogDate` |
| 4 | Echo guard (+ the TYPE_DELETED half of A11) | `fix(sync): ignore Data Layer events a device caused itself` |
| 5 | A4 | `fix(sync): reject watch audio that arrives incomplete` |
| 6 | A5 | `fix(wear): store phone audio apart from the watch's recordings` |
| 7 | A6 | `fix(wear): leave captures that fail repeatedly in the backup queue` |
| 8 | A7 | `fix(wear): clear a capture only for the version the phone stored` |
| 9 | A8 | `fix(sync): back up journals and journal links made on the watch` |
| 10 | A9 | `fix(wear): update sync status as it changes` |
| 11 | A10 | `fix(wear): wake the watch when a sync retry is due` |
| 12 | A11 | `chore(wear): delete a note's data item once the phone has it` |

Dependencies flow forward. Task 3's node selection comes before Task 8's acknowledgements, which reach watches through it. Task 4 makes both devices ignore deletion events before Task 12 starts producing them. Task 7's `settle` and dead-letter ids come before Task 8 uses them. Task 10's live `syncStatusFlow` comes before Task 11's scheduler, which follows it. Line numbers below are as of `a8615ae5d`; for a file an earlier task, or C3a, already edited, find the code by the quoted anchor text.

**Other plans dated 2026-10-02 edit some of the same files.** None of them changes the sync behavior here, but they move code, so each edit below is also given by its anchor text:
- `2026-10-02-phone-sync-and-watch-settings-fixes.md` Task 4 (C3a) lands **before** Task 3 here (see Global Constraints). C3a rewrites `AndroidWatchConnectionManager` (injectable `nodeClient`, `capabilityClient`, `messageClient`, `remoteActivityHelper` and `linkIntent` parameters with defaults, `installAppOnWatch`/`openAppOnWatch` returning `WatchRequestResult`) and creates `app/android-main/src/test/kotlin/app/logdate/client/watch/AndroidWatchConnectionManagerTest.kt`, whose `manager()` helper builds the class with named arguments. Task 3 then adds `messageSender: WatchMessageSender` after `associationManager`, removes C3a's `messageClient` parameter (only `requestSync` used it), sends `requestSync()` through `messageSender`, and adds its two tests to C3a's test class. Koin keeps passing named arguments, so `AppModule.android.kt:210-216` gains only `messageSender = get()`.
- The same plan's Task 3 (C1b) edits `WatchNoteIngestor.kt:57-59` (`WatchNoteNotifier`), which Tasks 5 and 8 here also edit, and `AppModule.android.kt`, which Tasks 3 and 9 also edit. Its Task 4 (C3a) adds a VIEW filter to the watch manifest's `MainActivity` (now lines 223-233), below Task 1's edit.
- `2026-10-02-wear-app-groundwork.md` edits `LogDateWearApplication.kt`, `WearSettingsScreen.kt`, `AudioModule.kt`, the watch manifest and `app/wear/build.gradle.kts`, which Tasks 1, 6, 7, 10 and 11 here also edit. Whichever of the two lands second merges by anchor text.

---

### Task 1: Deliver the phone's camera updates to the watch (A1)

**Files:**
- Modify: `app/wear/src/main/AndroidManifest.xml:74-92` (comment and `DATA_CHANGED` filter)
- Create: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt`

**Interfaces:**
- Consumes: `NoteDataMapper.notePath(noteId: Uuid): String`, `NoteDataMapper.noteDeletePath(noteId: Uuid): String`, `JournalDataMapper.journalPath(journalId: Uuid): String`, `JournalDataMapper.journalDeletePath(journalId: Uuid): String`, `AssociationDataMapper.associationPath(journalId: Uuid, contentId: Uuid): String`, `AssociationDataMapper.associationDeletePath(journalId: Uuid, contentId: Uuid): String`, `RemoteCameraDeviceDataMapper.PATH_CAMERA_DEVICES = "/logdate/camera/devices"`, `RemoteCameraCaptureResultDataMapper.PATH_CAMERA_CAPTURE_RESULT = "/logdate/camera/capture-result"`, `WearAudioRequestPaths.noteAckPath(noteId: Uuid): String`, `WearAudioRequestPaths.SYNC_REQUEST_PATH = "/logdate/sync/request"`, `WearDataLayerClient.PHONE_CAPABILITY = "logdate_phone_app"`, and the Play services constants `DataClient.ACTION_DATA_CHANGED`, `MessageClient.ACTION_MESSAGE_RECEIVED`, `CapabilityClient.ACTION_CAPABILITY_CHANGED`.
- Produces: no code API. The manifest now routes `wear://*/logdate/camera/**` `DATA_CHANGED` events to `app.logdate.wear.sync.WearDataLayerListenerService`.

- [ ] **Step 1: Write the failing instrumented test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.sync.datalayer.AssociationDataMapper
import app.logdate.client.sync.datalayer.JournalDataMapper
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.RemoteCameraCaptureResultDataMapper
import app.logdate.client.sync.datalayer.RemoteCameraDeviceDataMapper
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Checks that the watch's Data Layer listener is declared for everything the phone sends it.
 *
 * Play services starts the listener service only for an event whose action and URI match one of its
 * manifest filters, so a missing path means the event never arrives, whether or not the app is
 * running. Each event is resolved here the way Play services resolves it, against the installed
 * manifest.
 */
@RunWith(AndroidJUnit4::class)
class WearManifestFiltersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val noteId = Uuid.random()
    private val journalId = Uuid.random()

    @Test
    fun `every data item the phone writes reaches the listener`() {
        listOf(
            NoteDataMapper.notePath(noteId),
            NoteDataMapper.noteDeletePath(noteId),
            JournalDataMapper.journalPath(journalId),
            JournalDataMapper.journalDeletePath(journalId),
            AssociationDataMapper.associationPath(journalId, noteId),
            AssociationDataMapper.associationDeletePath(journalId, noteId),
            RemoteCameraDeviceDataMapper.PATH_CAMERA_DEVICES,
            RemoteCameraCaptureResultDataMapper.PATH_CAMERA_CAPTURE_RESULT,
        ).forEach { path -> assertResolvesToListener(DataClient.ACTION_DATA_CHANGED, path) }
    }

    @Test
    fun `every message the phone sends reaches the listener`() {
        listOf(
            WearAudioRequestPaths.noteAckPath(noteId),
            WearAudioRequestPaths.SYNC_REQUEST_PATH,
        ).forEach { path -> assertResolvesToListener(MessageClient.ACTION_MESSAGE_RECEIVED, path) }
    }

    @Test
    fun `the phone coming into reach reaches the listener`() {
        assertResolvesToListener(CapabilityClient.ACTION_CAPABILITY_CHANGED, "/${WearDataLayerClient.PHONE_CAPABILITY}")
    }

    private fun assertResolvesToListener(
        action: String,
        path: String,
    ) {
        val intent = Intent(action, Uri.parse("wear://$PHONE_NODE$path")).setPackage(context.packageName)
        val services = context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))

        assertEquals(listOf(LISTENER_SERVICE), services.map { it.serviceInfo.name }, "$action $path")
    }

    private companion object {
        const val PHONE_NODE = "phone-node"
        const val LISTENER_SERVICE = "app.logdate.wear.sync.WearDataLayerListenerService"
    }
}
```

- [ ] **Step 2: Run it on the managed Wear OS device and watch it fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearManifestFiltersTest
```
Expected: FAIL. `every data item the phone writes reaches the listener` reports `com.google.android.gms.wearable.DATA_CHANGED /logdate/camera/devices expected:<[app.logdate.wear.sync.WearDataLayerListenerService]> but was:<[]>`. The other two tests pass.

- [ ] **Step 3: Add the camera path to the listener's `DATA_CHANGED` filter**

In `app/wear/src/main/AndroidManifest.xml`, replace lines 74-92 (from `<!-- Data Layer sync listener: receives notes, journals, and associations from paired phone -->` through the closing `</intent-filter>` of the `DATA_CHANGED` filter) with:

```xml
        <!--
            Data Layer listener: receives notes, journals, associations and remote camera updates from
            the paired phone. Play services delivers only events whose path is listed here.
        -->
        <service
            android:name=".sync.WearDataLayerListenerService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/logdate/notes" />
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/logdate/journals" />
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/logdate/associations" />
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/logdate/camera" />
            </intent-filter>
```

The `MESSAGE_RECEIVED` and `CAPABILITY_CHANGED` filters that follow stay as they are.

- [ ] **Step 4: Run the test again**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearManifestFiltersTest
```
Expected: PASS, 3 tests.

- [ ] **Step 5: Run the watch unit tests and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:ktlintCheck
```
Expected: BUILD SUCCESSFUL. `ktlintCheck` reads the new androidTest file too.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task01-camera-filter.txt <<'EOF'
fix(wear): deliver the phone's camera updates to the watch

The remote camera screen on the watch never learned which cameras the
phone has or whether a photo was saved. The phone published both, but
the watch only accepts Data Layer events whose path its manifest lists,
and the camera paths were not listed, so the watch never received
them, whether the app was open or closed.

The watch now accepts the camera paths. An instrumented test checks
every path the phone writes or messages against the installed
manifest, so a path added later without a filter fails the test.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/wear/src/main/AndroidManifest.xml app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task01-camera-filter.txt
```

---

### Task 2: Deliver captures to the phone without batching delay (A2)

**Files:**
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerClient.kt:12-19`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClient.kt:66-78`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt:133, 350, 388, 417, 446, 459, 480, 497`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneWearSyncBridge.kt:40-51, 73-77, 113-116, 124-127, 144-155`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClientTest.kt` (call at L58; missing `@Test` at L64; 2 new tests)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt` (every `putDataItem` matcher; 3 new tests)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/AudioSyncFlowTest.kt` (every `putDataItem` matcher)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/GooglePhoneWearTransportTest.kt` (calls at L42, L60; 2 new tests)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/PhoneWearSyncBridgeTest.kt` (`RecordingTransport`; 1 new test)

**Interfaces:**
- Consumes: `PutDataRequest.setUrgent(): PutDataRequest` (Play services).
- Produces: `WearDataLayerClient.putDataItem(path: String, data: Map<String, String>, urgent: Boolean): Boolean` and `PhoneWearTransport.putDataItem(path: String, data: Map<String, String>, urgent: Boolean): Boolean`. No default value: every call site states its urgency. Urgent: note puts and note removals from the watch, and camera updates from the phone. Routine: journals, links and health snapshots from the watch, and the phone's bulk `publishNotesToWatch`.

- [ ] **Step 1: Thread the `urgent` parameter through with every call site routine (inert)**

`WearDataLayerClient.kt`, replace lines 12-19 (the `putDataItem` KDoc and declaration) with:

```kotlin
    /**
     * Puts a data item at the given [path] with the provided key-value [data].
     *
     * The Data Layer batches ordinary items to save battery and may delay one by up to 30 minutes. An
     * [urgent] item goes out at once: use it for what someone just captured or removed.
     *
     * Returns true if the put succeeded.
     */
    suspend fun putDataItem(
        path: String,
        data: Map<String, String>,
        urgent: Boolean,
    ): Boolean
```

`GoogleWearDataLayerClient.kt`, replace lines 66-78 (`override suspend fun putDataItem` through its closing `}`) with:

```kotlin
    override suspend fun putDataItem(
        path: String,
        data: Map<String, String>,
        urgent: Boolean,
    ): Boolean =
        try {
            val request = putDataRequestFactory.create(path, data)
            dataClient.putDataItem(request).await()
            Napier.d("Data item put at path: $path")
            true
        } catch (e: Exception) {
            Napier.w("Failed to put data item at path: $path", e)
            false
        }
```

`WearDataLayerSyncManager.kt`, add `, urgent = false` as the last argument of all eight `dataLayerClient.putDataItem(...)` calls:
- line 133: `dataLayerClient.putDataItem(NoteDataMapper.noteDeletePath(noteId), deleteSignal(noteId), urgent = false)`
- line 350: `dataLayerClient.putDataItem(path, dataMap, urgent = false)`
- line 388: `dataLayerClient.putDataItem(path, dataMapFor(note), urgent = false)`
- line 417: `dataLayerClient.putDataItem(NoteDataMapper.noteDeletePath(noteId), deleteSignal(noteId), urgent = false)`
- line 446: `dataLayerClient.putDataItem(path, dataMap, urgent = false)`
- line 459: `dataLayerClient.putDataItem(path, deleteData, urgent = false)`
- line 480: `dataLayerClient.putDataItem(path, dataMap, urgent = false)`
- line 497: `dataLayerClient.putDataItem(path, deleteData, urgent = false)`

`PhoneWearSyncBridge.kt`, replace lines 40-51 (`interface PhoneWearTransport { … }`) with:

```kotlin
interface PhoneWearTransport {
    /**
     * Puts a data item for the watch. The Data Layer may delay an ordinary item by up to 30 minutes;
     * an [urgent] one goes out at once, for anything someone on the watch is waiting on.
     */
    suspend fun putDataItem(
        path: String,
        data: Map<String, String>,
        urgent: Boolean,
    ): Boolean

    suspend fun streamToNode(
        nodeId: String,
        channelPath: String,
        inputStream: InputStream,
    ): Boolean
}
```

In `DefaultPhoneWearSyncBridge`, add `urgent = false,` after the `data = …,` argument of the three `transport.putDataItem(` calls (lines 74-77, 113-116 and 124-127). In `GooglePhoneWearTransport`, replace lines 144-155 with:

```kotlin
    override suspend fun putDataItem(
        path: String,
        data: Map<String, String>,
        urgent: Boolean,
    ): Boolean =
        runCatching {
            val request = putDataRequestFactory.create(path, data)
            dataClient.putDataItem(request).await()
            true
        }.getOrElse { error ->
            Napier.w("Failed to put wear data item at path: $path", error)
            false
        }
```

Update the existing tests to the new shape. Every single-line `dataLayerClient.putDataItem(…)` matcher in the two watch sync suites gains a trailing `any()`:

```bash
perl -pi -e 's/(dataLayerClient\.putDataItem\()((?:[^()\n]|\((?:[^()\n]|\([^()\n]*\))*\))*)\)/$1$2, any())/g' app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/AudioSyncFlowTest.kt
```

The one multi-line matcher, `WearDataLayerSyncManagerTest.kt:232-237` in `upload sends UPDATE as data item put`, becomes:

```kotlin
            coVerify {
                dataLayerClient.putDataItem(
                    NoteDataMapper.notePath(noteId),
                    any(),
                    any(),
                )
            }
```

`GoogleWearDataLayerClientTest.kt` line 58 becomes `val success = client.putDataItem(path = "/logdate/notes/1", data = mapOf("id" to "1"), urgent = false)`. The test at line 64, ``fun `deleteDataItem returns false when wearable delete fails`()``, has no `@Test` annotation, so it never runs: add `@Test` on the line above it.

`GooglePhoneWearTransportTest.kt` lines 42 and 60 become `val success = transport.putDataItem(path = "/logdate/notes/1", data = mapOf("id" to "1"), urgent = false)`.

`PhoneWearSyncBridgeTest.kt`: in `RecordingTransport` (its `putDataItem`, lines 182-188) and `PutRequest` (lines 200-203), use:

```kotlin
        override suspend fun putDataItem(
            path: String,
            data: Map<String, String>,
            urgent: Boolean,
        ): Boolean {
            putRequests += PutRequest(path, data, urgent)
            return true
        }
```

```kotlin
    private data class PutRequest(
        val path: String,
        val data: Map<String, String>,
        val urgent: Boolean,
    )
```

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.*' && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.*'
```
Expected: BUILD SUCCESSFUL. Nothing behaves differently yet; the newly annotated `deleteDataItem` test passes, because its exception is caught.

- [ ] **Step 2: Write the failing tests**

Append to `GoogleWearDataLayerClientTest` (before `private fun createClient`):

```kotlin
    @Test
    fun `putDataItem marks an urgent item for immediate delivery`() =
        runTest {
            val request = mockk<PutDataRequest>(relaxed = true)
            every { dataClient.putDataItem(any()) } returns Tasks.forResult(mockk<DataItem>(relaxed = true))
            val client = createClient(this, putDataRequestFactory = WearPutDataRequestFactory { _, _ -> request })

            assertTrue(client.putDataItem(path = "/logdate/notes/1", data = mapOf("id" to "1"), urgent = true))

            verify(exactly = 1) { request.setUrgent() }
        }

    @Test
    fun `putDataItem leaves a routine item to the Data Layer's batching`() =
        runTest {
            val request = mockk<PutDataRequest>(relaxed = true)
            every { dataClient.putDataItem(any()) } returns Tasks.forResult(mockk<DataItem>(relaxed = true))
            val client = createClient(this, putDataRequestFactory = WearPutDataRequestFactory { _, _ -> request })

            assertTrue(client.putDataItem(path = "/logdate/journals/1", data = mapOf("id" to "1"), urgent = false))

            verify(exactly = 0) { request.setUrgent() }
        }
```

Append to `WearDataLayerSyncManagerTest` (before the final closing `}`), and add `import app.logdate.shared.model.Journal`:

```kotlin
    // =======================================================================
    // Delivery urgency
    // =======================================================================

    @Test
    fun `notes go to the phone urgently`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(PendingUpload(noteId.toString(), PendingOperation.CREATE))
            coEvery { notesRepository.getNoteById(noteId) } returns textNote

            syncManager.uploadPendingChanges()

            coVerify(exactly = 1) { dataLayerClient.putDataItem(NoteDataMapper.notePath(noteId), any(), true) }
        }

    @Test
    fun `note removals go to the phone urgently`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(PendingUpload(noteId.toString(), PendingOperation.DELETE))

            syncManager.uploadPendingChanges()
            syncManager.notifyRemoved(noteId)

            coVerify(exactly = 2) { dataLayerClient.putDataItem(NoteDataMapper.noteDeletePath(noteId), any(), true) }
        }

    @Test
    fun `journals go to the phone with the Data Layer's usual batching`() =
        runTest {
            val journalId = Uuid.parse("770e8400-e29b-41d4-a716-446655440000")
            coEvery { syncMetadataService.getPendingUploads(EntityType.JOURNAL) } returns
                listOf(PendingUpload(journalId.toString(), PendingOperation.CREATE))
            coEvery { journalRepository.getJournalById(journalId) } returns Journal(id = journalId, title = "Walks")

            syncManager.syncJournals()

            coVerify(exactly = 1) { dataLayerClient.putDataItem(JournalDataMapper.journalPath(journalId), any(), false) }
        }
```

Append to `GooglePhoneWearTransportTest` (before the final `}`):

```kotlin
    @Test
    fun `putDataItem marks an urgent item for immediate delivery`() =
        runTest {
            val request = mockk<PutDataRequest>(relaxed = true)
            val transport =
                GooglePhoneWearTransport(
                    dataClient = dataClient,
                    channelClient = channelClient,
                    putDataRequestFactory = PhoneWearPutDataRequestFactory { _, _ -> request },
                )
            every { dataClient.putDataItem(any()) } returns Tasks.forResult(mockk<DataItem>(relaxed = true))

            transport.putDataItem(path = "/logdate/camera/capture-result", data = mapOf("isSaved" to "true"), urgent = true)

            verify(exactly = 1) { request.setUrgent() }
        }

    @Test
    fun `putDataItem leaves a routine item to the Data Layer's batching`() =
        runTest {
            val request = mockk<PutDataRequest>(relaxed = true)
            val transport =
                GooglePhoneWearTransport(
                    dataClient = dataClient,
                    channelClient = channelClient,
                    putDataRequestFactory = PhoneWearPutDataRequestFactory { _, _ -> request },
                )
            every { dataClient.putDataItem(any()) } returns Tasks.forResult(mockk<DataItem>(relaxed = true))

            transport.putDataItem(path = "/logdate/notes/1", data = mapOf("id" to "1"), urgent = false)

            verify(exactly = 0) { request.setUrgent() }
        }
```

Append to `PhoneWearSyncBridgeTest` (before `private object UnusedAudioOpener`):

```kotlin
    @Test
    fun `notes go to the watch with the usual batching and camera results go at once`() =
        runTest {
            val transport = RecordingTransport()
            val phoneNote =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Instant.fromEpochMilliseconds(1_710_000_000_000),
                    lastUpdated = Instant.fromEpochMilliseconds(1_710_000_000_000),
                    content = "Phone note",
                )
            every { notesRepository.allNotesObserved } returns flowOf(listOf(phoneNote))
            val bridge =
                DefaultPhoneWearSyncBridge(
                    notesRepository = notesRepository,
                    noteDataMapper = noteDataMapper,
                    transport = transport,
                    audioStreamOpener = UnusedAudioOpener,
                )

            bridge.publishNotesToWatch(sourceNodeId = "watch-node")
            bridge.publishRemoteCameraCaptureResult(
                RemoteCameraCaptureResult(isSaved = true, message = "Remote photo saved", mediaType = "photo"),
            )

            assertEquals(listOf(false, true), transport.putRequests.map { it.urgent })
        }
```

- [ ] **Step 3: Run the new tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.GoogleWearDataLayerClientTest' --tests 'app.logdate.wear.sync.WearDataLayerSyncManagerTest'
```
Expected: FAIL. `putDataItem marks an urgent item for immediate delivery` fails with `Verification failed: call 1 of 1: PutDataRequest(#…).setUrgent()) was not called`. `notes go to the phone urgently` and `note removals go to the phone urgently` fail because the matching `putDataItem(…, true)` calls never happened. The routine-item and journal tests already pass: they confirm that routine items stay routine.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.GooglePhoneWearTransportTest' --tests 'app.logdate.client.sync.PhoneWearSyncBridgeTest'
```
Expected: FAIL. In `putDataItem marks an urgent item for immediate delivery`, `setUrgent()` was not called. `notes go to the watch with the usual batching and camera results go at once` fails with `expected:<[false, true]> but was:<[false, false]>`.

- [ ] **Step 4: Mark urgent items urgent**

`GoogleWearDataLayerClient.putDataItem`: after `val request = putDataRequestFactory.create(path, data)`, add:

```kotlin
            if (urgent) request.setUrgent()
```

`GooglePhoneWearTransport.putDataItem`: after `val request = putDataRequestFactory.create(path, data)`, add the same line.

`WearDataLayerSyncManager.kt`: change these three calls to `urgent = true`. The other five (`syncHealthSnapshots`, `uploadJournal`, `uploadJournalDelete`, `uploadAssociation`, `uploadAssociationDelete`) stay `urgent = false`:
- `notifyRemoved`: `dataLayerClient.putDataItem(NoteDataMapper.noteDeletePath(noteId), deleteSignal(noteId), urgent = true)`
- `uploadNote`: `dataLayerClient.putDataItem(path, dataMapFor(note), urgent = true)`
- `uploadNoteDelete`: `dataLayerClient.putDataItem(NoteDataMapper.noteDeletePath(noteId), deleteSignal(noteId), urgent = true)`

`DefaultPhoneWearSyncBridge`: in `publishRemoteCameraDevices` and `publishRemoteCameraCaptureResult`, change `urgent = false,` to `urgent = true,`. `publishNotesToWatch` stays `urgent = false,`.

- [ ] **Step 5: Run the tests and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:detekt :app:wear:ktlintCheck && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.*' && ./gradlew :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task02-urgent-puts.txt <<'EOF'
fix(wear): deliver captures to the phone without batching delay

A recording, note or mood saved on the watch could take up to half an
hour to reach the phone, and so to reach backup. The Data Layer
batches ordinary items to save battery and may delay one by up to 30
minutes unless the sender marks it urgent, and nothing was marked
urgent.

Captures and their removals now go out at once, and so do the phone's
answers to the watch's remote camera. Bulk updates nobody is waiting
on, such as the phone's list of notes for the watch, still use the
Data Layer's batching.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerClient.kt app/wear/src/main/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClient.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneWearSyncBridge.kt app/wear/src/test/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClientTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/AudioSyncFlowTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/GooglePhoneWearTransportTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/PhoneWearSyncBridgeTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task02-urgent-puts.txt
```

---

### Task 3: Send messages to the devices that run LogDate (A3)

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/PhoneNodeSelector.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClient.kt:64-65` (new property), `:137-157` (`sendMessage`), `:243-271` (`sendFile`)
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/WatchNodeSelector.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteAcknowledgement.kt:28-66` (`GoogleWatchMessageSender`)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/AndroidWatchConnectionManager.kt` as C3a left it: imports, the two private constants at the top, the constructor's `messageClient` parameter, `requestSync()`, and the three `WATCH_CAPABILITY` uses (in `connectedWatches()`, `observeTransportSnapshot()` and `resolveTransportSnapshot()`)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt:210-216`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/PhoneNodeSelectorTest.kt` (new)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClientTest.kt` (default capability stub, relaxed `Node` mocks, 2 new tests)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/watch/WatchNodeSelectorTest.kt` (new)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/GoogleWatchMessageSenderTest.kt` (new)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/watch/AndroidWatchConnectionManagerTest.kt` (created by C3a: imports, two fields, `manager()`, 2 new tests)

**Interfaces:**
- Consumes: `CapabilityClient.getCapability(capability: String, filter: Int): Task<CapabilityInfo>`, `NodeClient.getConnectedNodes(): Task<List<Node>>`, `Node.isNearby`, `MessageClient.sendMessage(nodeId: String, path: String, data: ByteArray): Task<Int>`, `WatchMessageSender.send(path: String): Boolean` (unchanged in this task).
- Produces:
  - `class PhoneNodeSelector(capabilityClient: CapabilityClient, nodeClient: NodeClient) { suspend fun select(): Node? }`
  - `class WatchNodeSelector(capabilityClient: CapabilityClient) { suspend fun reachableWatches(): List<Node>; companion object { const val WATCH_CAPABILITY = "logdate_watch_app" } }`
  - `class GoogleWatchMessageSender(watchNodeSelector: WatchNodeSelector, messageClient: MessageClient) : WatchMessageSender`, with a secondary constructor `(context: Context)`
  - `class AndroidWatchConnectionManager(context: Context, associationManager: WatchCompanionAssociationManager, messageSender: WatchMessageSender, ioDispatcher: CoroutineDispatcher, nodeClient: NodeClient = Wearable.getNodeClient(context), capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context), remoteActivityHelper: RemoteActivityHelper = RemoteActivityHelper(context), linkIntent: (String) -> Intent = ::watchLinkIntent)`: C3a's constructor with `messageSender` added and `messageClient` removed. `requestSync()` throws `IllegalStateException("No watch running LogDate received the sync request")` when no watch received it.

**Prerequisite:** C3a (`2026-10-02-phone-sync-and-watch-settings-fixes.md` Task 4) has landed; see Global Constraints. The `AndroidWatchConnectionManager` edits below are given against the file and test class C3a leaves: its constructor ends `ioDispatcher`, `nodeClient`, `capabilityClient`, `messageClient`, `remoteActivityHelper`, `linkIntent`; it keeps `private const val WATCH_CAPABILITY` and `private const val SYNC_REQUEST_PATH` above `REMOTE_START_TIMEOUT_SECONDS`; and its `requestSync()` still loops over `nodeClient.connectedNodes` and stops at the first failed `await()`. C3a's test class builds the manager in `manager()` with named arguments, including `messageClient = mockk<MessageClient>(),`.

**Compatibility:** shipped phones (built from `main`) do not advertise `logdate_phone_app`, so when no node advertises it `PhoneNodeSelector` falls back to a connected node, nearby first, and logs that it did. That fallback is where every watch message went before this task. Every watch build advertises `logdate_watch_app`, so the phone needs no fallback.

- [ ] **Step 1: Add the API surface with inert bodies**

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/PhoneNodeSelector.kt`:

```kotlin
package app.logdate.wear.sync

import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient

class PhoneNodeSelector(
    private val capabilityClient: CapabilityClient,
    private val nodeClient: NodeClient,
) {
    suspend fun select(): Node? = null
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/WatchNodeSelector.kt`:

```kotlin
package app.logdate.client.watch

import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node

class WatchNodeSelector(
    private val capabilityClient: CapabilityClient,
) {
    suspend fun reachableWatches(): List<Node> = emptyList()

    companion object {
        const val WATCH_CAPABILITY = "logdate_watch_app"
    }
}
```

In `WatchNoteAcknowledgement.kt`, replace lines 28-66 (the `GoogleWatchMessageSender` KDoc and class) with:

```kotlin
class GoogleWatchMessageSender(
    private val watchNodeSelector: WatchNodeSelector,
    private val messageClient: MessageClient,
) : WatchMessageSender {
    constructor(context: Context) : this(
        watchNodeSelector = WatchNodeSelector(Wearable.getCapabilityClient(context)),
        messageClient = Wearable.getMessageClient(context),
    )

    override suspend fun send(path: String): Boolean = false
}
```

and change its imports to:

```kotlin
import android.content.Context
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.client.watch.WatchNodeSelector
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid
```

In `AndroidWatchConnectionManager.kt` (as C3a left it), add `import app.logdate.client.sync.WatchMessageSender`, remove `import com.google.android.gms.wearable.MessageClient`, and replace the first six constructor parameters, from `private val context: Context,` through `private val messageClient: MessageClient = Wearable.getMessageClient(context),`, with:

```kotlin
    private val context: Context,
    private val associationManager: WatchCompanionAssociationManager,
    private val messageSender: WatchMessageSender,
    private val ioDispatcher: CoroutineDispatcher,
    private val nodeClient: NodeClient = Wearable.getNodeClient(context),
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context),
```

`remoteActivityHelper` and `linkIntent` stay as the last two parameters. Only `requestSync()` used `messageClient`; until Step 4 gives it its body, replace it with:

```kotlin
    override suspend fun requestSync() = Unit
```

In `AppModule.android.kt`, replace lines 210-216 (the `single<WatchConnectionManager>` binding, which C3a leaves as it is) with:

```kotlin
        single<WatchConnectionManager> {
            AndroidWatchConnectionManager(
                context = androidContext(),
                associationManager = get(),
                messageSender = get(),
                ioDispatcher = get(qualifier = watchConnectionIoDispatcherQualifier),
            )
        }
```

In C3a's `AndroidWatchConnectionManagerTest.kt`, add the imports `app.logdate.client.sync.WatchMessageSender`, `app.logdate.client.sync.datalayer.WearAudioRequestPaths` and `kotlin.test.assertFailsWith`, remove `import com.google.android.gms.wearable.MessageClient`, add two fields after `private val accepted = …`:

```kotlin
    private val sentMessages = mutableListOf<String>()
    private var messageDelivered = true
```

and in `manager()`, replace `messageClient = mockk<MessageClient>(),` with:

```kotlin
            messageSender =
                WatchMessageSender { path ->
                    sentMessages += path
                    messageDelivered
                },
```

In `GoogleWearDataLayerClientTest.kt`, make every block-form `Node` mock relaxed, so the selector can read `isNearby` and `displayName`:

```bash
perl -pi -e 's/mockk<Node> \{/mockk<Node>(relaxed = true) {/g' app/wear/src/test/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClientTest.kt
```

Then add `import kotlin.test.BeforeTest` and, as the first member after the mock fields, a default under which no node advertises the phone app. That is the shipped-phone case, so the existing tests still resolve through `connectedNodes`:

```kotlin
    @BeforeTest
    fun noNodeAdvertisesThePhoneAppByDefault() {
        every {
            capabilityClient.getCapability(WearDataLayerClient.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        } returns Tasks.forResult(mockk<CapabilityInfo> { every { nodes } returns emptySet() })
    }
```

Tests that stub `getCapability(any(), any())` themselves override this stub, because MockK prefers the most recent matching one.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.*' && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.*' --tests 'app.logdate.client.watch.*'
```
Expected: BUILD SUCCESSFUL. The selectors are not wired in yet.

- [ ] **Step 2: Write the failing tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/PhoneNodeSelectorTest.kt`:

```kotlin
package app.logdate.wear.sync

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests [PhoneNodeSelector], which picks the phone a watch message or file goes to.
 */
class PhoneNodeSelectorTest {
    private val capabilityClient = mockk<CapabilityClient>()
    private val nodeClient = mockk<NodeClient>()
    private val selector = PhoneNodeSelector(capabilityClient, nodeClient)

    @Test
    fun `a phone advertising LogDate wins over the first connected node`() =
        runTest {
            val other = node("other-node")
            val phone = node("phone-node")
            givenAdvertising(phone)
            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(other, phone))

            assertEquals("phone-node", selector.select()?.id)
        }

    @Test
    fun `a phone connected directly wins over one reached through the cloud`() =
        runTest {
            givenAdvertising(node("cloud-phone", nearby = false), node("nearby-phone", nearby = true))

            assertEquals("nearby-phone", selector.select()?.id)
        }

    @Test
    fun `connected nodes are not consulted when a phone advertises LogDate`() =
        runTest {
            givenAdvertising(node("phone-node"))

            selector.select()

            verify(exactly = 0) { nodeClient.connectedNodes }
        }

    @Test
    fun `a phone build that advertises nothing gets the nearby connected node`() =
        runTest {
            givenAdvertising()
            every { nodeClient.connectedNodes } returns
                Tasks.forResult(listOf(node("cloud-node", nearby = false), node("old-phone", nearby = true)))

            assertEquals("old-phone", selector.select()?.id)
        }

    @Test
    fun `nothing connected selects nothing`() =
        runTest {
            givenAdvertising()
            every { nodeClient.connectedNodes } returns Tasks.forResult(emptyList())

            assertNull(selector.select())
        }

    private fun givenAdvertising(vararg advertising: Node) {
        val info = mockk<CapabilityInfo> { every { nodes } returns advertising.toSet() }
        every {
            capabilityClient.getCapability(WearDataLayerClient.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        } returns Tasks.forResult(info)
    }

    private fun node(
        id: String,
        nearby: Boolean = true,
    ): Node =
        mockk {
            every { this@mockk.id } returns id
            every { isNearby } returns nearby
            every { displayName } returns id
        }
}
```

Append to `GoogleWearDataLayerClientTest` (before `private fun createClient`):

```kotlin
    @Test
    fun `sendMessage goes to the phone advertising LogDate rather than the first connected node`() =
        runTest {
            val other = mockk<Node>(relaxed = true) { every { id } returns "other-node" }
            val phone = mockk<Node>(relaxed = true) { every { id } returns "phone-node" }
            every {
                capabilityClient.getCapability(WearDataLayerClient.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            } returns Tasks.forResult(mockk<CapabilityInfo> { every { nodes } returns setOf(phone) })
            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(other, phone))
            every { messageClient.sendMessage("phone-node", "/logdate/test", any()) } returns Tasks.forResult(1)

            assertTrue(createClient(this).sendMessage(path = "/logdate/test"))
            verify(exactly = 0) { messageClient.sendMessage("other-node", any(), any()) }
        }

    @Test
    fun `sendFile goes to the phone advertising LogDate rather than the first connected node`() =
        runTest {
            val source = createTempDirectory().resolve("note.m4a").apply { writeBytes("audio".encodeToByteArray()) }
            val other = mockk<Node>(relaxed = true) { every { id } returns "other-node" }
            val phone = mockk<Node>(relaxed = true) { every { id } returns "phone-node" }
            val channel = mockk<ChannelClient.Channel>()
            every {
                capabilityClient.getCapability(WearDataLayerClient.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            } returns Tasks.forResult(mockk<CapabilityInfo> { every { nodes } returns setOf(phone) })
            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(other, phone))
            every { channelClient.openChannel("phone-node", "/logdate/notes/1/audio") } returns Tasks.forResult(channel)
            every { channelClient.getOutputStream(channel) } returns Tasks.forResult(ByteArrayOutputStream())
            every { channelClient.close(channel) } returns Tasks.forResult<Void>(null)

            assertTrue(createClient(this).sendFile(channelPath = "/logdate/notes/1/audio", localFilePath = source.toString()))
            verify(exactly = 0) { channelClient.openChannel("other-node", any()) }
        }
```

Create `app/android-main/src/test/kotlin/app/logdate/client/watch/WatchNodeSelectorTest.kt`:

```kotlin
package app.logdate.client.watch

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests [WatchNodeSelector], which finds the reachable watches running LogDate.
 */
class WatchNodeSelectorTest {
    private val capabilityClient = mockk<CapabilityClient>()
    private val selector = WatchNodeSelector(capabilityClient)

    @Test
    fun `reachable watches running LogDate come back, directly connected first`() =
        runTest {
            givenWatches(watch("cloud-watch", nearby = false), watch("wrist-watch", nearby = true))

            assertEquals(listOf("wrist-watch", "cloud-watch"), selector.reachableWatches().map { it.id })
        }

    @Test
    fun `no watch running LogDate gives no watches`() =
        runTest {
            givenWatches()

            assertEquals(emptyList(), selector.reachableWatches())
        }

    @Test
    fun `only reachable watches are asked for`() =
        runTest {
            givenWatches(watch("wrist-watch"))

            selector.reachableWatches()

            verify { capabilityClient.getCapability(WatchNodeSelector.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE) }
        }

    private fun givenWatches(vararg watches: Node) {
        val info = mockk<CapabilityInfo> { every { nodes } returns watches.toSet() }
        every { capabilityClient.getCapability(WatchNodeSelector.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE) } returns
            Tasks.forResult(info)
    }

    private fun watch(
        id: String,
        nearby: Boolean = true,
    ): Node =
        mockk {
            every { this@mockk.id } returns id
            every { isNearby } returns nearby
            every { displayName } returns id
        }
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/GoogleWatchMessageSenderTest.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.watch.WatchNodeSelector
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [GoogleWatchMessageSender], which messages every reachable watch running LogDate, each one
 * on its own.
 */
class GoogleWatchMessageSenderTest {
    private val capabilityClient = mockk<CapabilityClient>()
    private val messageClient = mockk<MessageClient>()
    private val sender = GoogleWatchMessageSender(WatchNodeSelector(capabilityClient), messageClient)

    @Test
    fun `every reachable watch running LogDate gets the message`() =
        runTest {
            givenWatches(watch("pixel-watch"), watch("galaxy-watch"))
            every { messageClient.sendMessage(any(), PATH, any()) } returns Tasks.forResult(1)

            assertTrue(sender.send(PATH))

            verify(exactly = 1) { messageClient.sendMessage("pixel-watch", PATH, any()) }
            verify(exactly = 1) { messageClient.sendMessage("galaxy-watch", PATH, any()) }
        }

    @Test
    fun `a watch that cannot be reached does not stop the others`() =
        runTest {
            givenWatches(watch("gone-watch"), watch("pixel-watch"))
            every { messageClient.sendMessage("gone-watch", PATH, any()) } returns
                Tasks.forException(IllegalStateException("TARGET_NODE_NOT_CONNECTED"))
            every { messageClient.sendMessage("pixel-watch", PATH, any()) } returns Tasks.forResult(1)

            assertTrue(sender.send(PATH))

            verify(exactly = 1) { messageClient.sendMessage("pixel-watch", PATH, any()) }
        }

    @Test
    fun `nothing is sent when no reachable watch runs LogDate`() =
        runTest {
            givenWatches()

            assertFalse(sender.send(PATH))

            verify(exactly = 0) { messageClient.sendMessage(any(), any(), any()) }
        }

    @Test
    fun `a message no watch accepted is reported as not sent`() =
        runTest {
            givenWatches(watch("pixel-watch"))
            every { messageClient.sendMessage("pixel-watch", PATH, any()) } returns
                Tasks.forException(IllegalStateException("TARGET_NODE_NOT_CONNECTED"))

            assertFalse(sender.send(PATH))
        }

    @Test
    fun `a watch lookup that fails is reported as not sent`() =
        runTest {
            every { capabilityClient.getCapability(any(), any()) } returns Tasks.forException(IllegalStateException("API_UNAVAILABLE"))

            assertFalse(sender.send(PATH))
        }

    private fun givenWatches(vararg watches: Node) {
        val info = mockk<CapabilityInfo> { every { nodes } returns watches.toSet() }
        every { capabilityClient.getCapability(WatchNodeSelector.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE) } returns
            Tasks.forResult(info)
    }

    private fun watch(id: String): Node =
        mockk {
            every { this@mockk.id } returns id
            every { isNearby } returns true
            every { displayName } returns id
        }

    private companion object {
        const val PATH = "/logdate/notes/550e8400-e29b-41d4-a716-446655440000/ack"
    }
}
```

Append to C3a's `AndroidWatchConnectionManagerTest` (before `private fun connected`):

```kotlin
    @Test
    fun `a sync request goes to the watches running LogDate`() =
        runTest {
            manager().requestSync()

            assertEquals(listOf(WearAudioRequestPaths.SYNC_REQUEST_PATH), sentMessages)
        }

    @Test
    fun `a sync request no watch received fails with the reason`() =
        runTest {
            messageDelivered = false

            val error = assertFailsWith<IllegalStateException> { manager().requestSync() }

            assertEquals("No watch running LogDate received the sync request", error.message)
        }
```

- [ ] **Step 3: Run the new tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.PhoneNodeSelectorTest' --tests 'app.logdate.wear.sync.GoogleWearDataLayerClientTest'
```
Expected: FAIL. `PhoneNodeSelectorTest` fails on its assertions (`expected:<phone-node> but was:<null>`). The two new routing tests fail because the client still messages `connectedNodes.first()` ("other-node"), whose `sendMessage`/`openChannel` call is not stubbed, so they return `false`.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.watch.WatchNodeSelectorTest' --tests 'app.logdate.client.sync.GoogleWatchMessageSenderTest' --tests 'app.logdate.client.watch.AndroidWatchConnectionManagerTest'
```
Expected: FAIL. The selector returns no watches and the sender reports nothing sent. `a sync request goes to the watches running LogDate` reports `expected:<[/logdate/sync/request]> but was:<[]>`, and `a sync request no watch received fails with the reason` reports that no `IllegalStateException` was thrown. C3a's seven tests still pass.

- [ ] **Step 4: Implement the selectors and route through them**

Replace `app/wear/src/main/kotlin/app/logdate/wear/sync/PhoneNodeSelector.kt` with:

```kotlin
package app.logdate.wear.sync

import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.tasks.await

/**
 * Picks the phone a message or file from the watch goes to.
 *
 * The phone running LogDate advertises [WearDataLayerClient.PHONE_CAPABILITY]. Among reachable nodes
 * that do, one connected directly over Bluetooth is preferred to one reached through the cloud. Phone
 * builds whose capability file sat where Android does not read it advertise nothing, so when no node
 * advertises the capability the watch falls back to a connected node, nearby first, which is where
 * every message went before this selector existed.
 */
class PhoneNodeSelector(
    private val capabilityClient: CapabilityClient,
    private val nodeClient: NodeClient,
) {
    /** The phone to send to, or null when no phone is connected at all. */
    suspend fun select(): Node? {
        val advertising =
            capabilityClient
                .getCapability(WearDataLayerClient.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        nearestOf(advertising)?.let { return it }
        return nearestOf(nodeClient.connectedNodes.await())?.also { node ->
            Napier.i("No reachable node advertises ${WearDataLayerClient.PHONE_CAPABILITY}; using connected node ${node.displayName}")
        }
    }

    private fun nearestOf(nodes: Collection<Node>): Node? = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
}
```

In `GoogleWearDataLayerClient.kt`, add this property right after the secondary constructor (after line 64):

```kotlin
    private val phoneNodeSelector = PhoneNodeSelector(capabilityClient, nodeClient)
```

Replace `sendMessage` (lines 137-157) with:

```kotlin
    override suspend fun sendMessage(
        path: String,
        data: ByteArray,
    ): Boolean {
        return try {
            val phoneNode =
                phoneNodeSelector.select() ?: run {
                    Napier.w("No reachable phone for message $path")
                    return false
                }
            messageClient
                .sendMessage(phoneNode.id, path, data)
                .await()
            Napier.d("Message sent to phone: $path")
            true
        } catch (e: Exception) {
            Napier.w("Failed to send message: $path", e)
            false
        }
    }
```

In `sendFile` (lines 243-271), replace

```kotlin
            val nodes = nodeClient.connectedNodes.await()
            val phoneNode =
                nodes.firstOrNull() ?: run {
                    Napier.w("No connected nodes for file transfer")
                    return false
                }
```

with

```kotlin
            val phoneNode =
                phoneNodeSelector.select() ?: run {
                    Napier.w("No reachable phone for file transfer $channelPath")
                    return false
                }
```

Replace `app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/WatchNodeSelector.kt` with:

```kotlin
package app.logdate.client.watch

import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import kotlinx.coroutines.tasks.await

/**
 * Finds the watches a phone message goes to: every reachable node running LogDate's watch app.
 *
 * Every watch build advertises [WATCH_CAPABILITY], so a connected node without it has no LogDate to
 * read the message. A watch connected directly comes before one reachable only through the cloud.
 */
class WatchNodeSelector(
    private val capabilityClient: CapabilityClient,
) {
    suspend fun reachableWatches(): List<Node> =
        capabilityClient
            .getCapability(WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .sortedByDescending { it.isNearby }

    companion object {
        const val WATCH_CAPABILITY = "logdate_watch_app"
    }
}
```

In `WatchNoteAcknowledgement.kt`, replace the inert `GoogleWatchMessageSender` with:

```kotlin
/**
 * Messages every reachable watch running LogDate, each on its own, so a watch that cannot be reached
 * does not stop the message reaching another.
 */
class GoogleWatchMessageSender(
    private val watchNodeSelector: WatchNodeSelector,
    private val messageClient: MessageClient,
) : WatchMessageSender {
    constructor(context: Context) : this(
        watchNodeSelector = WatchNodeSelector(Wearable.getCapabilityClient(context)),
        messageClient = Wearable.getMessageClient(context),
    )

    override suspend fun send(path: String): Boolean {
        val watches =
            try {
                watchNodeSelector.reachableWatches()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Could not look up reachable watches for $path", e)
                return false
            }
        if (watches.isEmpty()) {
            Napier.w("No reachable watch runs LogDate; $path was not sent")
            return false
        }
        return watches.count { watch -> deliver(watch, path) } > 0
    }

    private suspend fun deliver(
        watch: Node,
        path: String,
    ): Boolean =
        try {
            messageClient.sendMessage(watch.id, path, ByteArray(0)).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not message watch ${watch.displayName} at $path", e)
            false
        }
}
```

and set the file's imports to:

```kotlin
import android.content.Context
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.client.watch.WatchNodeSelector
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlin.uuid.Uuid
```

In `AndroidWatchConnectionManager.kt`:
- Delete `private const val WATCH_CAPABILITY = "logdate_watch_app"` and `private const val SYNC_REQUEST_PATH = "/logdate/sync/request"` at the top of the file. `REMOTE_START_TIMEOUT_SECONDS`, `WATCH_TODAY_LINK`, `playListingLink` and `watchLinkIntent` stay.
- Change the three remaining uses of `WATCH_CAPABILITY` to `WatchNodeSelector.WATCH_CAPABILITY`: `.getCapability(WATCH_CAPABILITY, CapabilityClient.FILTER_ALL)` in `connectedWatches()` and in `resolveTransportSnapshot()`, and `Uri.parse("wear://*/$WATCH_CAPABILITY"),` in `observeTransportSnapshot()`, which becomes `Uri.parse("wear://*/${WatchNodeSelector.WATCH_CAPABILITY}"),`.
- Add `import app.logdate.client.sync.datalayer.WearAudioRequestPaths`.
- Replace the inert `requestSync()` with:

```kotlin
    /** Asks every reachable watch running LogDate to send what it has waiting. */
    override suspend fun requestSync() {
        check(messageSender.send(WearAudioRequestPaths.SYNC_REQUEST_PATH)) {
            "No watch running LogDate received the sync request"
        }
    }
```

`WatchSettingsViewModel.requestSync()` already catches and logs the exception, as it did for C3a's version, which threw on the first watch it could not reach.

- [ ] **Step 5: Run the tests and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:detekt :app:wear:ktlintCheck && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.*' --tests 'app.logdate.client.watch.*' && ./gradlew :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task03-node-selection.txt <<'EOF'
fix(wear): send messages to the devices that run LogDate

Messages and recordings from the watch went to whichever device the
watch listed first rather than to the phone running LogDate, so a
playback request or a recording could go to the wrong device and fail.
On the phone, a sync request went to every connected device, and the
first one that could not be reached stopped the request reaching the
rest. Note confirmations stopped the same way.

The watch now sends to the phone running LogDate, preferring one
connected directly over one reached through the cloud, and falls back
to a connected phone for older phone versions that do not announce
themselves. The phone sends to each reachable watch running LogDate on
its own, and reports a sync request that no watch received.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/sync/PhoneNodeSelector.kt app/wear/src/main/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClient.kt app/wear/src/test/kotlin/app/logdate/wear/sync/PhoneNodeSelectorTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClientTest.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/WatchNodeSelector.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteAcknowledgement.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/AndroidWatchConnectionManager.kt app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt app/android-main/src/test/kotlin/app/logdate/client/watch/WatchNodeSelectorTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/GoogleWatchMessageSenderTest.kt app/android-main/src/test/kotlin/app/logdate/client/watch/AndroidWatchConnectionManagerTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task03-node-selection.txt
```

---

### Task 4: Ignore Data Layer events a device caused itself (echo guard)

The phone publishes its notes for the watch as `/logdate/notes/<id>` data items. Those writes come back to the phone's own listener: the URI host is the creating node (`DataClient` reference), and the Data Layer delivers changes to the writing node's listener too. The phone then hands them to `WatchNoteIngestor`. For a phone audio note that already exists, that means an acknowledgement message to the watch (`WatchNoteIngestor.kt:146-149`), one per audio note in the batch, and each one wakes the watch. The watch likewise re-applies its own note, journal and link items. This task also makes both listeners ignore `TYPE_DELETED` events, which Task 12 starts producing.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/DataEventFilter.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt:17-31` (imports), `:33-48` (KDoc and new property), `:75-112` (`onDataChanged`), `:305` (`extractStringMap` signature), `:320-327` (`SnapshotDataEvent`), plus a new `localNodeId()`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt:19-35` (imports), `:184-210` (`onDataChanged`, `processSnapshotEvents`), `:429` (`extractStringMap` signature), `:445-452` (`PhoneDataLayerSnapshotEvent`), plus a new `localNodeId()`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/DataEventFilterTest.kt` (new)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/PhoneDataLayerListenerServiceTest.kt` (helpers at `:750-759`, test service at `:777-809`, 3 new tests)

**Interfaces:**
- Consumes: `DataEvent.TYPE_CHANGED = 1`, `DataEvent.TYPE_DELETED = 2`, `NodeClient.getLocalNode(): Task<Node>`.
- Produces:
  - `internal fun isChangeFromAnotherDevice(eventType: Int, sourceNodeId: String?, localNodeId: String?): Boolean` (watch, `app.logdate.wear.sync`)
  - `data class PhoneDataLayerSnapshotEvent(path: String, data: Map<String, String>, type: Int = DataEvent.TYPE_CHANGED, sourceNodeId: String? = null) { fun isChangeFromAnotherDevice(localNodeId: String?): Boolean }`
  - `protected open suspend fun localNodeId(): String?` on `PhoneDataLayerListenerService`, overridable in tests

- [ ] **Step 1: Add the API surface with inert bodies**

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/DataEventFilter.kt`:

```kotlin
package app.logdate.wear.sync

internal fun isChangeFromAnotherDevice(
    eventType: Int,
    sourceNodeId: String?,
    localNodeId: String?,
): Boolean = true
```

In `PhoneDataLayerListenerService.kt`, replace lines 445-452 (`PhoneDataLayerSnapshotEvent`) with:

```kotlin
/**
 * Snapshot of a data event's path, payload, type and writer.
 * Created before [DataEventBuffer] is recycled by the system.
 */
data class PhoneDataLayerSnapshotEvent(
    val path: String,
    val data: Map<String, String>,
    val type: Int = DataEvent.TYPE_CHANGED,
    /** The node that wrote the data item: the host of its URI. */
    val sourceNodeId: String? = null,
)
```

Add `import com.google.android.gms.wearable.DataEvent`, change `private fun extractStringMap(event: com.google.android.gms.wearable.DataEvent)` (line 429) to `private fun extractStringMap(event: DataEvent)`, and add this member below `resolvePhoneWearSyncBridge()` (line 413):

```kotlin
    protected open suspend fun localNodeId(): String? = null
```

In `PhoneDataLayerListenerServiceTest.kt`, replace the helpers `mockDataEvent` and `mockDataItem` (lines 750-759) with:

```kotlin
    private fun mockDataEvent(
        dataItem: DataItem,
        type: Int = DataEvent.TYPE_CHANGED,
    ): DataEvent =
        mockk {
            every { this@mockk.dataItem } returns dataItem
            every { this@mockk.type } returns type
        }

    private fun mockDataItem(
        uriPath: String?,
        host: String? = "watch-node",
    ): DataItem =
        mockk {
            val uri =
                mockk<Uri> {
                    every { path } returns uriPath
                    every { this@mockk.host } returns host
                }
            every { this@mockk.uri } returns uri
        }
```

Then give `TestPhoneDataLayerListenerService` (lines 777-809) a last constructor parameter `private val localNode: String? = "phone-node",` and this override:

```kotlin
        override suspend fun localNodeId(): String? = localNode
```

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.*' && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.PhoneDataLayerListenerServiceTest'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Write the failing tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/DataEventFilterTest.kt`:

```kotlin
package app.logdate.wear.sync

import com.google.android.gms.wearable.DataEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [isChangeFromAnotherDevice], which decides which data events the watch acts on.
 */
class DataEventFilterTest {
    @Test
    fun `a change the phone made is acted on`() {
        assertTrue(isChangeFromAnotherDevice(DataEvent.TYPE_CHANGED, sourceNodeId = "phone-node", localNodeId = "watch-node"))
    }

    @Test
    fun `a change the watch made itself is ignored`() {
        assertFalse(isChangeFromAnotherDevice(DataEvent.TYPE_CHANGED, sourceNodeId = "watch-node", localNodeId = "watch-node"))
    }

    @Test
    fun `a deletion is ignored`() {
        assertFalse(isChangeFromAnotherDevice(DataEvent.TYPE_DELETED, sourceNodeId = "phone-node", localNodeId = "watch-node"))
    }

    @Test
    fun `every change is acted on when the watch's own node is unknown`() {
        assertTrue(isChangeFromAnotherDevice(DataEvent.TYPE_CHANGED, sourceNodeId = "watch-node", localNodeId = null))
    }
}
```

Append to `PhoneDataLayerListenerServiceTest` (before `private fun serviceWithDependencies`), and add `import io.mockk.verify`:

```kotlin
    @Test
    fun `a note the phone wrote itself is not treated as one from the watch`() =
        runTest {
            val note = textNote()
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = dependencies(noteIngestor = ingestor),
                )

            mockkStatic(DataMapItem::class)
            try {
                val dataItem = mockDataItem(uriPath = NoteDataMapper.notePath(note.uid), host = "phone-node")
                every { DataMapItem.fromDataItem(dataItem) } returns mockDataMapItem(noteDataMapper.toDataMap(note))

                service.onDataChanged(mockDataEventBuffer(mockDataEvent(dataItem)))
                advanceUntilIdle()

                coVerify(exactly = 0) { ingestor.onNoteMetadata(any()) }
            } finally {
                unmockkStatic(DataMapItem::class)
            }
        }

    @Test
    fun `a deleted data item is ignored without reading its contents`() =
        runTest {
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = dependencies(noteIngestor = ingestor),
                )

            mockkStatic(DataMapItem::class)
            try {
                val dataItem = mockDataItem(uriPath = NoteDataMapper.notePath(Uuid.random()))

                service.onDataChanged(mockDataEventBuffer(mockDataEvent(dataItem, type = DataEvent.TYPE_DELETED)))
                advanceUntilIdle()

                confirmVerified(ingestor)
                verify(exactly = 0) { DataMapItem.fromDataItem(any()) }
            } finally {
                unmockkStatic(DataMapItem::class)
            }
        }

    @Test
    fun `changes are applied when the phone's own node cannot be read`() =
        runTest {
            val note = textNote()
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = dependencies(noteIngestor = ingestor),
                    localNode = null,
                )

            service.processEvents(
                PhoneDataLayerSnapshotEvent(
                    path = NoteDataMapper.notePath(note.uid),
                    data = noteDataMapper.toDataMap(note),
                    sourceNodeId = "phone-node",
                ),
            )

            coVerify(exactly = 1) { ingestor.onNoteMetadata(noteDataMapper.toDataMap(note)) }
        }
```

- [ ] **Step 3: Run the new tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.DataEventFilterTest'
```
Expected: FAIL. `a change the watch made itself is ignored` and `a deletion is ignored` fail with `Expected value to be false.`

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.PhoneDataLayerListenerServiceTest'
```
Expected: FAIL. In `a note the phone wrote itself is not treated as one from the watch`, `onNoteMetadata` was called once. In `a deleted data item is ignored without reading its contents`, the unstubbed `DataMapItem.fromDataItem` reaches the real implementation, which fails on the strict `DataItem` mock. `changes are applied when the phone's own node cannot be read` already passes; it pins the unknown-node behavior.

- [ ] **Step 4: Filter on type and writer in both listeners**

Replace `app/wear/src/main/kotlin/app/logdate/wear/sync/DataEventFilter.kt` with:

```kotlin
package app.logdate.wear.sync

import com.google.android.gms.wearable.DataEvent

/**
 * Whether the watch acts on a data event: only a change another device made.
 *
 * The Data Layer reports a device's own writes back to it, and a data item's URI host is the node
 * that created it, so an event from [localNodeId] is the watch hearing its own write. A deletion
 * event has only a URI; LogDate announces removals with explicit `/delete` items instead. When the
 * watch's own node is unknown, every change is acted on, as before this check existed.
 */
internal fun isChangeFromAnotherDevice(
    eventType: Int,
    sourceNodeId: String?,
    localNodeId: String?,
): Boolean = eventType == DataEvent.TYPE_CHANGED && (localNodeId == null || sourceNodeId != localNodeId)
```

In `WearDataLayerListenerService.kt`:

Add imports `com.google.android.gms.wearable.DataEvent`, `com.google.android.gms.wearable.Wearable`, `kotlinx.coroutines.CancellationException` and `kotlinx.coroutines.tasks.await`. Change `private fun extractStringMap(event: com.google.android.gms.wearable.DataEvent)` to `private fun extractStringMap(event: DataEvent)`.

Add to the class KDoc, after its first paragraph:

```kotlin
 * Only changes the phone made are applied: the Data Layer also reports the watch's own writes back
 * to it, and deletions are ignored (see [isChangeFromAnotherDevice]).
```

Below `private val associationDataMapper = AssociationDataMapper()` (line 51), add:

```kotlin
    @Volatile
    private var cachedLocalNodeId: String? = null
```

Replace `onDataChanged` up to and including the `for` loop over `events` (lines 75-96) with:

```kotlin
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Snapshot events before they are recycled by the system
        val events = mutableListOf<SnapshotDataEvent>()
        for (event in dataEvents) {
            val uri = event.dataItem.uri
            val path = uri.path ?: continue
            val data = if (event.type == DataEvent.TYPE_CHANGED) extractStringMap(event) else emptyMap()
            events.add(SnapshotDataEvent(path, data, event.type, uri.host))
        }

        if (events.isEmpty()) return

        serviceScope.launch {
            val localNodeId = localNodeId()
            val syncEvents = mutableListOf<SnapshotDataEvent>()
            for (event in events) {
                if (!isChangeFromAnotherDevice(event.type, event.sourceNodeId, localNodeId)) continue
                when (event.path) {
                    RemoteCameraDeviceDataMapper.PATH_CAMERA_DEVICES -> handleRemoteCameraDevices(event.data)
                    RemoteCameraCaptureResultDataMapper.PATH_CAMERA_CAPTURE_RESULT -> {
                        handleRemoteCameraCaptureResult(event.data)
                    }
                    else -> syncEvents.add(event)
                }
            }
```

The rest of the launched block (`if (syncEvents.isEmpty()) return@launch` onward) is unchanged. Add this member below `resolveKoin()`:

```kotlin
    /**
     * This watch's Data Layer node, whose writes come back to it as data events, or null when Play
     * services cannot say, in which case every change is acted on.
     */
    private suspend fun localNodeId(): String? {
        cachedLocalNodeId?.let { return it }
        return try {
            Wearable
                .getNodeClient(this)
                .localNode
                .await()
                .id
                .also { cachedLocalNodeId = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not read this watch's Data Layer node; acting on every data change", e)
            null
        }
    }
```

Replace `SnapshotDataEvent` (lines 320-327) with:

```kotlin
    /**
     * Snapshot of a data event's path, payload, type and writer.
     * Created before [DataEventBuffer] is recycled by the system.
     */
    private data class SnapshotDataEvent(
        val path: String,
        val data: Map<String, String>,
        val type: Int,
        val sourceNodeId: String?,
    )
```

In `PhoneDataLayerListenerService.kt`:

Add imports `com.google.android.gms.wearable.DataEvent` (if Step 1 did not already) and `kotlinx.coroutines.CancellationException`. Below `private val healthSnapshotDataMapper = HealthSnapshotDataMapper()` (line 56), add:

```kotlin
    @Volatile
    private var cachedLocalNodeId: String? = null
```

Replace `onDataChanged` and `processSnapshotEvents` (lines 184-210) with:

```kotlin
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Snapshot events before they are recycled by the system
        val events = mutableListOf<PhoneDataLayerSnapshotEvent>()
        for (event in dataEvents) {
            val uri = event.dataItem.uri
            val path = uri.path ?: continue
            val data = if (event.type == DataEvent.TYPE_CHANGED) extractStringMap(event) else emptyMap()
            events.add(PhoneDataLayerSnapshotEvent(path, data, type = event.type, sourceNodeId = uri.host))
        }

        if (events.isEmpty()) return

        serviceScope.launch {
            processSnapshotEvents(events)
        }
    }

    protected open suspend fun processSnapshotEvents(events: List<PhoneDataLayerSnapshotEvent>) {
        val dependencies = resolveSyncDependencies()
        if (dependencies == null) {
            Napier.e("Koin unavailable after $MAX_KOIN_RETRIES retries, dropping ${events.size} data events")
            return
        }

        val localNodeId = localNodeId()
        for (event in events) {
            if (!event.isChangeFromAnotherDevice(localNodeId)) continue
            processSnapshotEvent(event, dependencies)
        }
    }
```

Replace the inert `localNodeId()` with:

```kotlin
    /**
     * The phone's own Data Layer node, whose writes come back to it as data events, or null when Play
     * services cannot say, in which case every change is acted on.
     */
    protected open suspend fun localNodeId(): String? {
        cachedLocalNodeId?.let { return it }
        return try {
            Wearable
                .getNodeClient(this)
                .localNode
                .await()
                .id
                .also { cachedLocalNodeId = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not read this phone's Data Layer node; acting on every data change", e)
            null
        }
    }
```

Give `PhoneDataLayerSnapshotEvent` its body:

```kotlin
data class PhoneDataLayerSnapshotEvent(
    val path: String,
    val data: Map<String, String>,
    val type: Int = DataEvent.TYPE_CHANGED,
    /** The node that wrote the data item: the host of its URI. */
    val sourceNodeId: String? = null,
) {
    /**
     * Whether the phone acts on this event: only a change another device made. The Data Layer
     * reports the phone's own writes, such as the notes it publishes for the watch, back to the
     * phone. A deletion has no payload; LogDate announces removals with explicit `/delete` items.
     */
    fun isChangeFromAnotherDevice(localNodeId: String?): Boolean =
        type == DataEvent.TYPE_CHANGED && (localNodeId == null || sourceNodeId != localNodeId)
}
```

- [ ] **Step 5: Run the tests and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:detekt :app:wear:ktlintCheck && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.*' && ./gradlew :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task04-echo-guard.txt <<'EOF'
fix(sync): ignore Data Layer events a device caused itself

When the phone published its notes for the watch, it also sent the
watch a confirmation for each of its own voice notes, waking the watch
once per note. The Data Layer reports a device's own writes back to
it, and the phone treated the notes it had just published as notes
arriving from the watch. The watch likewise applied its own notes,
journals and links a second time.

Each device now acts only on changes the other device made, and
ignores deletion events, which have no content; LogDate announces
removals with explicit delete items. When a device cannot tell which
node it is, it acts on every change, as before.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/sync/DataEventFilter.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt app/wear/src/test/kotlin/app/logdate/wear/sync/DataEventFilterTest.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt app/android-main/src/test/kotlin/app/logdate/client/sync/PhoneDataLayerListenerServiceTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task04-echo-guard.txt
```

---

### Task 5: Reject watch audio that arrives incomplete (A4)

**Files:**
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/NoteDataMapper.kt:44-68` (companion) and append `AudioFileDigest`
- Create: `client/sync/src/androidMain/kotlin/app/logdate/client/sync/datalayer/AudioFileDigests.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/AudioFileDigester.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt:51-64` (constructor), `:404-412` (`dataMapFor`)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt:1-50` (imports, `WatchAudioStore`), `:163-173` (`storeIfComplete`)
- Modify (rewrite): `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/FileWatchAudioStore.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/NoteDataMapperTest.kt` (append)
- Test: `client/sync/src/androidHostTest/kotlin/app/logdate/client/sync/datalayer/AudioFileDigestsTest.kt` (new)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt` (append)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/FileWatchAudioStoreTest.kt` (append)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteIngestorTest.kt` (`InMemoryWatchAudioStore`, append)

**Interfaces:**
- Consumes: `NoteDataMapper.toDataMap(note: JournalNote): Map<String, String>` and `fromDataMap(map)`, which reads only `jsonPayload`, so older phones ignore the new keys.
- Produces:
  - `data class AudioFileDigest(val sizeBytes: Long, val sha256Hex: String)` (`app.logdate.client.sync.datalayer`, commonMain)
  - `NoteDataMapper.KEY_AUDIO_SIZE = "audioSize"`, `NoteDataMapper.KEY_AUDIO_SHA256 = "audioSha256"`
  - `NoteDataMapper.withAudioDigest(map: Map<String, String>, digest: AudioFileDigest): Map<String, String>`
  - `NoteDataMapper.audioDigest(map: Map<String, String>): AudioFileDigest?`: null when neither key is present (older watches); `sizeBytes = -1` or `sha256Hex = ""` when a key is present but unreadable
  - `fun InputStream.copyToDigesting(output: OutputStream): AudioFileDigest` and `fun File.audioDigest(): AudioFileDigest` (androidMain; lowercase hex)
  - `fun interface AudioFileDigester { suspend fun digest(path: String): AudioFileDigest? }` and `class FileAudioDigester(ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : AudioFileDigester` (watch)
  - `WearDataLayerSyncManager(…, audioDigester: AudioFileDigester = FileAudioDigester(), clock: Clock = Clock.System)`
  - `WatchAudioStore.storedDigest(noteId: Uuid): AudioFileDigest?`

**Behavior:** `audioSize`/`audioSha256` present and different from the received file: the phone deletes its copy and the stashed metadata, stores no note and sends no acknowledgement, so the watch's next resend replaces both. Keys missing (older watch): the audio is accepted as today. Older phones never read the keys.

- [ ] **Step 1: Add the API surface with inert bodies**

In `NoteDataMapper.kt`, add to the companion object after `KEY_JSON_PAYLOAD` (line 47):

```kotlin
        /** Length in bytes of an audio note's file, sent by the watch so the phone can check its copy. */
        const val KEY_AUDIO_SIZE = "audioSize"

        /** Lowercase hex SHA-256 of an audio note's file, sent with [KEY_AUDIO_SIZE]. */
        const val KEY_AUDIO_SHA256 = "audioSha256"

        fun withAudioDigest(
            map: Map<String, String>,
            digest: AudioFileDigest,
        ): Map<String, String> = map

        fun audioDigest(map: Map<String, String>): AudioFileDigest? = null
```

and append to the end of the file:

```kotlin
/** The length and SHA-256 of an audio file, as one device measured it. */
data class AudioFileDigest(
    val sizeBytes: Long,
    val sha256Hex: String,
)
```

Create `client/sync/src/androidMain/kotlin/app/logdate/client/sync/datalayer/AudioFileDigests.kt`:

```kotlin
package app.logdate.client.sync.datalayer

import java.io.File
import java.io.InputStream
import java.io.OutputStream

fun InputStream.copyToDigesting(output: OutputStream): AudioFileDigest = AudioFileDigest(sizeBytes = copyTo(output), sha256Hex = "")

fun File.audioDigest(): AudioFileDigest = AudioFileDigest(sizeBytes = length(), sha256Hex = "")
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/AudioFileDigester.kt`:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.datalayer.AudioFileDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

fun interface AudioFileDigester {
    suspend fun digest(path: String): AudioFileDigest?
}

class FileAudioDigester(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioFileDigester {
    override suspend fun digest(path: String): AudioFileDigest? = null
}
```

In `WearDataLayerSyncManager.kt`, add the constructor parameter `private val audioDigester: AudioFileDigester = FileAudioDigester(),` on the line before `private val clock: Clock = Clock.System,` (line 63).

In `WatchNoteIngestor.kt`, add `import app.logdate.client.sync.datalayer.AudioFileDigest`, and add to `interface WatchAudioStore`, after `writeAudio` (line 28):

```kotlin

    /**
     * The length and SHA-256 of the file at [audioPath], measured when it was written, or null when
     * there is no file or it cannot be read.
     */
    suspend fun storedDigest(noteId: Uuid): AudioFileDigest?
```

and give `clearMetadata` (line 37) the KDoc `/** Removes what waited for the note to be stored: its stashed metadata and its audio's recorded digest. */`.

In `FileWatchAudioStore.kt`, add `import app.logdate.client.sync.datalayer.AudioFileDigest` and, after `writeAudio`:

```kotlin
    override suspend fun storedDigest(noteId: Uuid): AudioFileDigest? = null
```

In `WatchNoteIngestorTest.kt`, add imports `app.logdate.client.sync.datalayer.AudioFileDigest`, `app.logdate.client.sync.datalayer.copyToDigesting` and `java.io.ByteArrayOutputStream`, and add to `InMemoryWatchAudioStore`, after `writeAudio`:

```kotlin
        override suspend fun storedDigest(noteId: Uuid): AudioFileDigest? =
            audio[noteId]?.let { bytes -> ByteArrayInputStream(bytes).copyToDigesting(ByteArrayOutputStream()) }
```

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:compileKotlinIosSimulatorArm64 :app:wear:testDebugUnitTest && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.*'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Write the failing tests**

Append to `NoteDataMapperTest` (before the final `}`):

```kotlin
    // =======================================================================
    // Audio digest keys
    // =======================================================================

    @Test
    fun `an audio digest added to a data map reads back`() {
        val digest = AudioFileDigest(sizeBytes = 42_000, sha256Hex = "ab".repeat(32))

        val map = NoteDataMapper.withAudioDigest(mapper.toDataMap(audioNote()), digest)

        assertEquals("42000", map[NoteDataMapper.KEY_AUDIO_SIZE])
        assertEquals(digest, NoteDataMapper.audioDigest(map))
    }

    @Test
    fun `a data map from a watch that sends no digest has none`() {
        assertNull(NoteDataMapper.audioDigest(mapper.toDataMap(audioNote())))
    }

    @Test
    fun `an unreadable size gives a digest no file matches`() {
        val map =
            mapper.toDataMap(audioNote()) +
                mapOf(
                    NoteDataMapper.KEY_AUDIO_SIZE to "lots",
                    NoteDataMapper.KEY_AUDIO_SHA256 to "AB".repeat(32),
                )

        val digest = assertNotNull(NoteDataMapper.audioDigest(map))

        assertEquals(-1L, digest.sizeBytes)
        assertEquals("ab".repeat(32), digest.sha256Hex)
    }

    @Test
    fun `a digest does not change the note read from the map`() {
        val note = audioNote()

        val map = NoteDataMapper.withAudioDigest(mapper.toDataMap(note), AudioFileDigest(1, "00".repeat(32)))

        assertEquals(note, mapper.fromDataMap(map))
    }

    private fun audioNote(): JournalNote.Audio =
        JournalNote.Audio(
            uid = fixedUuid,
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            mediaRef = "/watch/recording.m4a",
            durationMs = 4_200,
        )
```

Create `client/sync/src/androidHostTest/kotlin/app/logdate/client/sync/datalayer/AudioFileDigestsTest.kt`:

```kotlin
package app.logdate.client.sync.datalayer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Tests the length and SHA-256 measurements both devices make of watch audio.
 */
class AudioFileDigestsTest {
    private val file = File.createTempFile("audio-digest", ".m4a")

    @AfterTest
    fun cleanUp() {
        file.delete()
    }

    @Test
    fun `copying measures exactly what was copied`() {
        val output = ByteArrayOutputStream()

        val digest = ByteArrayInputStream("abc".encodeToByteArray()).copyToDigesting(output)

        assertContentEquals("abc".encodeToByteArray(), output.toByteArray())
        assertEquals(AudioFileDigest(sizeBytes = 3, sha256Hex = ABC_SHA256), digest)
    }

    @Test
    fun `an empty stream measures zero bytes`() {
        val digest = ByteArrayInputStream(ByteArray(0)).copyToDigesting(ByteArrayOutputStream())

        assertEquals(AudioFileDigest(sizeBytes = 0, sha256Hex = EMPTY_SHA256), digest)
    }

    @Test
    fun `a file is measured from its contents`() {
        file.writeText("abc")

        assertEquals(AudioFileDigest(sizeBytes = 3, sha256Hex = ABC_SHA256), file.audioDigest())
    }

    @Test
    fun `a file longer than one buffer is measured whole`() {
        val bytes = ByteArray(3 * DEFAULT_BUFFER_SIZE + 17) { index -> (index % 251).toByte() }
        file.writeBytes(bytes)

        val digest = file.audioDigest()

        assertEquals(bytes.size.toLong(), digest.sizeBytes)
        assertEquals(ByteArrayInputStream(bytes).copyToDigesting(ByteArrayOutputStream()), digest)
    }

    private companion object {
        const val ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
```

Append to `WearDataLayerSyncManagerTest` (before the final `}`), and add imports `app.logdate.client.sync.datalayer.AudioFileDigest` and `java.io.File`:

```kotlin
    // =======================================================================
    // Audio digests
    // =======================================================================

    @Test
    fun `an audio note tells the phone the size and SHA-256 of its file`() =
        runTest {
            val file = File.createTempFile("watch-recording", ".m4a").apply { writeText("abc") }
            try {
                coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                    listOf(PendingUpload(noteId.toString(), PendingOperation.CREATE))
                coEvery { notesRepository.getNoteById(noteId) } returns audioNote.copy(mediaRef = file.absolutePath)
                val sentData = mutableListOf<Map<String, String>>()
                coEvery { dataLayerClient.putDataItem(any(), capture(sentData), any()) } returns true

                syncManager.uploadPendingChanges()

                assertEquals(
                    AudioFileDigest(sizeBytes = 3, sha256Hex = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
                    NoteDataMapper.audioDigest(sentData.single()),
                )
            } finally {
                file.delete()
            }
        }

    @Test
    fun `an audio note whose file cannot be read goes without a digest`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(PendingUpload(noteId.toString(), PendingOperation.CREATE))
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote
            val sentData = mutableListOf<Map<String, String>>()
            coEvery { dataLayerClient.putDataItem(any(), capture(sentData), any()) } returns true

            syncManager.uploadPendingChanges()

            assertNull(NoteDataMapper.audioDigest(sentData.single()))
        }
```

Append to `FileWatchAudioStoreTest` (before `private fun stagingFile`), add `const val ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"` to its companion object, and add `import app.logdate.client.sync.datalayer.AudioFileDigest`:

```kotlin
    @Test
    fun `written audio records its size and SHA-256`() =
        runTest {
            store.writeAudio(noteId, ByteArrayInputStream("abc".encodeToByteArray()))

            assertEquals(AudioFileDigest(sizeBytes = 3, sha256Hex = ABC_SHA256), store.storedDigest(noteId))
        }

    @Test
    fun `audio stored by an older version is measured from the file`() =
        runTest {
            audioDirectory.mkdirs()
            File(store.audioPath(noteId)).writeText("abc")

            assertEquals(AudioFileDigest(sizeBytes = 3, sha256Hex = ABC_SHA256), store.storedDigest(noteId))
        }

    @Test
    fun `a note with no audio has no digest`() =
        runTest {
            assertNull(store.storedDigest(noteId))
        }

    @Test
    fun `a newer transfer replaces the recorded digest`() =
        runTest {
            store.writeAudio(noteId, ByteArrayInputStream("ab".encodeToByteArray()))

            store.writeAudio(noteId, ByteArrayInputStream("abc".encodeToByteArray()))

            assertEquals(AudioFileDigest(sizeBytes = 3, sha256Hex = ABC_SHA256), store.storedDigest(noteId))
        }

    @Test
    fun `discarding a note removes its recorded digest`() =
        runTest {
            store.writeAudio(noteId, ByteArrayInputStream("abc".encodeToByteArray()))

            store.discard(noteId)

            assertNull(store.storedDigest(noteId))
            assertFalse(File(incomingDirectory, "$noteId.sha256").exists())
        }
```

Append to `WatchNoteIngestorTest` (before `private fun bytes`):

```kotlin
    @Test
    fun `audio matching what the watch measured is stored`() =
        runTest {
            val note = watchAudioNote()

            ingestor.onNoteMetadata(withDigestOf("complete audio", note))
            ingestor.onAudioBytes(note.uid, bytes("complete audio"))

            assertTrue(stored.containsKey(note.uid))
            assertEquals(listOf(note.uid), acknowledger.acknowledged)
        }

    @Test
    fun `audio cut short on the way is neither stored nor acknowledged`() =
        runTest {
            val note = watchAudioNote()

            ingestor.onNoteMetadata(withDigestOf("complete audio", note))
            ingestor.onAudioBytes(note.uid, bytes("complete"))

            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
            assertTrue(acknowledger.acknowledged.isEmpty())
            assertFalse(store.hasAudio(note.uid))
        }

    @Test
    fun `audio cut short is stored once the watch resends it whole`() =
        runTest {
            val note = watchAudioNote()
            val metadata = withDigestOf("complete audio", note)
            ingestor.onNoteMetadata(metadata)
            ingestor.onAudioBytes(note.uid, bytes("complete"))

            ingestor.onNoteMetadata(metadata)
            ingestor.onAudioBytes(note.uid, bytes("complete audio"))

            coVerify(exactly = 1) { notesRepository.create(any<JournalNote>()) }
            assertEquals(listOf(note.uid), acknowledger.acknowledged)
        }

    @Test
    fun `audio that arrived first is checked when its metadata follows`() =
        runTest {
            val note = watchAudioNote()

            ingestor.onAudioBytes(note.uid, bytes("complete"))
            ingestor.onNoteMetadata(withDigestOf("complete audio", note))

            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
            assertFalse(store.hasAudio(note.uid))
        }

    @Test
    fun `a digest that cannot be read rejects the audio`() =
        runTest {
            val note = watchAudioNote()

            ingestor.onNoteMetadata(mapper.toDataMap(note) + (NoteDataMapper.KEY_AUDIO_SIZE to "unknown"))
            ingestor.onAudioBytes(note.uid, bytes("m4a"))

            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    private fun withDigestOf(
        audio: String,
        note: JournalNote.Audio,
    ): Map<String, String> =
        NoteDataMapper.withAudioDigest(
            mapper.toDataMap(note),
            ByteArrayInputStream(audio.encodeToByteArray()).copyToDigesting(ByteArrayOutputStream()),
        )
```

- [ ] **Step 3: Run the new tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.datalayer.NoteDataMapperTest' ; ./gradlew :client:sync:testAndroidHostTest --tests 'app.logdate.client.sync.datalayer.AudioFileDigestsTest'
```
Expected: FAIL. `an audio digest added to a data map reads back` fails with `expected:<42000> but was:<null>`. `an unreadable size gives a digest no file matches` fails in `assertNotNull`. The digest tests fail with `sha256Hex=` empty. The two "no digest" and "does not change the note" tests already pass.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.WearDataLayerSyncManagerTest' ; ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.FileWatchAudioStoreTest' --tests 'app.logdate.client.sync.WatchNoteIngestorTest'
```
Expected: FAIL. `an audio note tells the phone the size and SHA-256 of its file` gets `null`. The `FileWatchAudioStoreTest` digest tests get `null`. `audio cut short on the way is neither stored nor acknowledged` stores the note.

- [ ] **Step 4: Measure on the watch, measure and compare on the phone**

In `NoteDataMapper.kt`, replace the inert `withAudioDigest` and `audioDigest` with:

```kotlin
        /** [map] with [digest] added, for an audio note whose file the phone should check. */
        fun withAudioDigest(
            map: Map<String, String>,
            digest: AudioFileDigest,
        ): Map<String, String> =
            map +
                mapOf(
                    KEY_AUDIO_SIZE to digest.sizeBytes.toString(),
                    KEY_AUDIO_SHA256 to digest.sha256Hex,
                )

        /**
         * The digest a note's data map has, or null when it has none, as from watch builds that
         * predate digests. A value that cannot be read gives a digest no file matches, so a damaged
         * key never lets a short file through.
         */
        fun audioDigest(map: Map<String, String>): AudioFileDigest? {
            val size = map[KEY_AUDIO_SIZE]
            val sha256 = map[KEY_AUDIO_SHA256]
            if (size == null && sha256 == null) return null
            return AudioFileDigest(
                sizeBytes = size?.toLongOrNull() ?: UNREADABLE_SIZE,
                sha256Hex = sha256?.lowercase().orEmpty(),
            )
        }

        private const val UNREADABLE_SIZE = -1L
```

Replace `client/sync/src/androidMain/kotlin/app/logdate/client/sync/datalayer/AudioFileDigests.kt` with:

```kotlin
package app.logdate.client.sync.datalayer

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Copies this stream into [output] and returns the length and SHA-256 of what was copied, so a file
 * is measured in the same pass that writes it.
 */
fun InputStream.copyToDigesting(output: OutputStream): AudioFileDigest = digest { buffer, count -> output.write(buffer, 0, count) }

/** The length and SHA-256 of this file's contents. */
fun File.audioDigest(): AudioFileDigest = inputStream().use { input -> input.digest { _, _ -> } }

private inline fun InputStream.digest(onChunk: (ByteArray, Int) -> Unit): AudioFileDigest {
    val sha256 = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var size = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        onChunk(buffer, count)
        sha256.update(buffer, 0, count)
        size += count
    }
    return AudioFileDigest(
        sizeBytes = size,
        sha256Hex = sha256.digest().joinToString(separator = "") { byte -> "%02x".format(byte) },
    )
}
```

Replace `app/wear/src/main/kotlin/app/logdate/wear/sync/AudioFileDigester.kt` with:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.datalayer.AudioFileDigest
import app.logdate.client.sync.datalayer.audioDigest
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Measures a recording so the phone can tell whether the copy it receives is complete. */
fun interface AudioFileDigester {
    /** Null when the file cannot be read; the note then goes without a digest, as notes did before digests existed. */
    suspend fun digest(path: String): AudioFileDigest?
}

/** Reads the whole file on [ioDispatcher]; a 30-minute recording is about 29 MB. */
class FileAudioDigester(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioFileDigester {
    override suspend fun digest(path: String): AudioFileDigest? =
        withContext(ioDispatcher) {
            val file = File(path)
            if (!file.isFile) return@withContext null
            try {
                file.audioDigest()
            } catch (e: IOException) {
                Napier.w("Could not measure $path for the phone; sending the note without a digest", e)
                null
            }
        }
}
```

In `WearDataLayerSyncManager.kt`, replace `dataMapFor` and its KDoc (lines 404-412) with:

```kotlin
    /**
     * Audio notes include the send time so a resend is a changed data item: the Data Layer drops a
     * put that matches what it already has, and the phone may have lost the earlier one. They also
     * include the file's length and SHA-256, so the phone can tell a complete copy from one cut short.
     */
    private suspend fun dataMapFor(note: JournalNote): Map<String, String> {
        val dataMap = noteDataMapper.toDataMap(note)
        if (note !is JournalNote.Audio) return dataMap
        val stamped = dataMap + (KEY_SENT_AT to clock.now().toEpochMilliseconds().toString())
        val digest = audioDigester.digest(note.mediaRef) ?: return stamped
        return NoteDataMapper.withAudioDigest(stamped, digest)
    }
```

Replace `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/FileWatchAudioStore.kt` with:

```kotlin
package app.logdate.client.sync

import android.content.Context
import app.logdate.client.sync.datalayer.AudioFileDigest
import app.logdate.client.sync.datalayer.audioDigest
import app.logdate.client.sync.datalayer.copyToDigesting
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

/**
 * Stores watch audio in the phone's private audio directory, next to phone recordings, and stores
 * metadata that arrived ahead of its audio in a separate directory the audio scan never sees.
 *
 * The audio file appears at [audioPath] only when complete: bytes land in a staging file that is
 * synced to disk and renamed into place. Its length and SHA-256 are measured in the same pass and
 * recorded beside the metadata, so the ingestor can compare them with what the watch measured.
 *
 * Files left by a transfer that never finished are cleared as new work arrives: a staging file the
 * process died in the middle of, and metadata whose audio never came. Deletion markers are not
 * cleared, since a marker is empty and the only thing that stops a late delivery from coming back.
 */
class FileWatchAudioStore(
    private val audioDirectory: File,
    private val incomingDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WatchAudioStore {
    constructor(context: Context) : this(
        audioDirectory = File(context.filesDir, "audio_notes"),
        incomingDirectory = File(context.filesDir, "watch_incoming"),
    )

    private val metadataSerializer = MapSerializer(String.serializer(), String.serializer())

    override fun audioPath(noteId: Uuid): String = audioFile(noteId).absolutePath

    override fun hasAudio(noteId: Uuid): Boolean = audioFile(noteId).isFile

    override suspend fun writeAudio(
        noteId: Uuid,
        source: InputStream,
    ): Boolean =
        withContext(ioDispatcher) {
            val staging = File(audioDirectory, "wear_$noteId.m4a.part")
            try {
                audioDirectory.mkdirs()
                clearOlderThan(audioDirectory, STAGING_MAX_AGE_MS) { it.name.endsWith(STAGING_SUFFIX) }
                val digest =
                    FileOutputStream(staging).use { output ->
                        source.copyToDigesting(output).also { output.fd.sync() }
                    }
                if (digest.sizeBytes == 0L) {
                    staging.delete()
                    return@withContext false
                }
                digestFile(noteId).delete()
                Files.move(staging.toPath(), audioFile(noteId).toPath(), StandardCopyOption.ATOMIC_MOVE)
                recordDigest(noteId, digest)
                true
            } catch (e: Exception) {
                Napier.w("Failed to store watch audio for note $noteId", e)
                staging.delete()
                false
            }
        }

    override suspend fun storedDigest(noteId: Uuid): AudioFileDigest? =
        withContext(ioDispatcher) {
            val audio = audioFile(noteId)
            if (!audio.isFile) return@withContext null
            try {
                recordedDigest(noteId) ?: audio.audioDigest()
            } catch (e: IOException) {
                Napier.w("Could not measure watch audio for note $noteId", e)
                null
            }
        }

    override suspend fun stashMetadata(
        noteId: Uuid,
        data: Map<String, String>,
    ) {
        withContext(ioDispatcher) {
            incomingDirectory.mkdirs()
            clearOlderThan(incomingDirectory, STASHED_METADATA_MAX_AGE_MS) { it.name.endsWith(METADATA_SUFFIX) }
            metadataFile(noteId).writeText(Json.encodeToString(metadataSerializer, data))
        }
    }

    override suspend fun stashedMetadata(noteId: Uuid): Map<String, String>? =
        withContext(ioDispatcher) {
            val file = metadataFile(noteId)
            if (!file.isFile) return@withContext null
            try {
                Json.decodeFromString(metadataSerializer, file.readText())
            } catch (e: Exception) {
                Napier.w("Discarding unreadable watch metadata for note $noteId", e)
                null
            }
        }

    override suspend fun clearMetadata(noteId: Uuid) {
        withContext(ioDispatcher) {
            metadataFile(noteId).delete()
            digestFile(noteId).delete()
        }
    }

    override suspend fun discard(noteId: Uuid) {
        withContext(ioDispatcher) {
            audioFile(noteId).delete()
            metadataFile(noteId).delete()
            digestFile(noteId).delete()
        }
    }

    override suspend fun markDeleted(noteId: Uuid) {
        withContext(ioDispatcher) {
            incomingDirectory.mkdirs()
            deletionMarker(noteId).writeText("")
        }
    }

    override suspend fun isDeleted(noteId: Uuid): Boolean = withContext(ioDispatcher) { deletionMarker(noteId).isFile }

    /** Best effort: [storedDigest] measures the file itself when nothing is recorded. */
    private fun recordDigest(
        noteId: Uuid,
        digest: AudioFileDigest,
    ) {
        try {
            incomingDirectory.mkdirs()
            digestFile(noteId).writeText("${digest.sizeBytes} ${digest.sha256Hex}")
        } catch (e: IOException) {
            Napier.w("Could not record the digest of watch audio for note $noteId; it is measured when needed", e)
        }
    }

    private fun recordedDigest(noteId: Uuid): AudioFileDigest? {
        val file = digestFile(noteId)
        if (!file.isFile) return null
        val (size, sha256) = file.readText().split(' ').takeIf { it.size == 2 } ?: return null
        return AudioFileDigest(sizeBytes = size.toLongOrNull() ?: return null, sha256Hex = sha256)
    }

    private fun clearOlderThan(
        directory: File,
        maxAgeMs: Long,
        matches: (File) -> Boolean,
    ) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        directory
            .listFiles()
            .orEmpty()
            .filter { matches(it) && it.isFile && it.lastModified() < cutoff }
            .forEach { stale ->
                if (stale.delete()) Napier.d("Cleared abandoned watch file ${stale.name}")
            }
    }

    private fun audioFile(noteId: Uuid): File = File(audioDirectory, "wear_$noteId.m4a")

    private fun metadataFile(noteId: Uuid): File = File(incomingDirectory, "$noteId.json")

    private fun digestFile(noteId: Uuid): File = File(incomingDirectory, "$noteId$DIGEST_SUFFIX")

    private fun deletionMarker(noteId: Uuid): File = File(incomingDirectory, "$noteId.deleted")

    private companion object {
        const val STAGING_SUFFIX = ".m4a.part"
        const val METADATA_SUFFIX = ".json"
        const val DIGEST_SUFFIX = ".sha256"

        /** A transfer writes continuously, so a staging file untouched for an hour was abandoned. */
        const val STAGING_MAX_AGE_MS = 60L * 60 * 1000

        /** The watch gives up resending a note after minutes, so metadata a week old will never get its audio. */
        const val STASHED_METADATA_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
```

In `WatchNoteIngestor.kt`, replace `storeIfComplete` (lines 163-173) with:

```kotlin
    private suspend fun storeIfComplete(noteId: Uuid) {
        if (!audioStore.hasAudio(noteId)) return
        val data = audioStore.stashedMetadata(noteId) ?: return
        val watchNote = noteDataMapper.fromDataMap(data) as? JournalNote.Audio ?: return
        if (!audioMatchesWhatTheWatchSent(noteId, data)) return

        val note = watchNote.copy(mediaRef = audioStore.audioPath(noteId))
        notesRepository.create(note)
        audioStore.clearMetadata(noteId)
        acknowledger.acknowledge(noteId)
        notifier.noteReceived(note)
    }

    /**
     * A watch that sends a digest lets the phone tell a complete file from one cut short on the way.
     * A file that does not match is removed with its metadata, so the note is neither stored nor
     * acknowledged and the watch's next resend replaces both. A watch that sends no digest is trusted,
     * as every watch was before digests existed.
     */
    private suspend fun audioMatchesWhatTheWatchSent(
        noteId: Uuid,
        data: Map<String, String>,
    ): Boolean {
        val expected = NoteDataMapper.audioDigest(data) ?: return true
        val received = audioStore.storedDigest(noteId)
        if (received == expected) return true
        Napier.w("Audio for watch note $noteId is $received, but the watch sent $expected; waiting for it to be sent again")
        audioStore.discard(noteId)
        return false
    }
```

- [ ] **Step 5: Run the tests, the iOS compile and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:testAndroidHostTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:ktlintCheck && ./gradlew :app:wear:testDebugUnitTest :app:wear:detekt :app:wear:ktlintCheck && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.*' && ./gradlew :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task05-audio-digest.txt <<'EOF'
fix(sync): reject watch audio that arrives incomplete

A watch recording whose transfer was cut short could be saved on the
phone and backed up as a shorter, unplayable file, after which the
watch stopped resending it. The phone only checked that some audio had
arrived, not that all of it had.

The watch now sends the length and SHA-256 of each recording with its
note, and the phone measures the file as it receives it. When the two
differ, the phone drops its copy and does not confirm the note, so the
watch sends it again. Recordings from watches that do not send these
values are accepted as before, and older phones ignore them.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/NoteDataMapper.kt client/sync/src/androidMain/kotlin/app/logdate/client/sync/datalayer/AudioFileDigests.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/NoteDataMapperTest.kt client/sync/src/androidHostTest/kotlin/app/logdate/client/sync/datalayer/AudioFileDigestsTest.kt app/wear/src/main/kotlin/app/logdate/wear/sync/AudioFileDigester.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/FileWatchAudioStore.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt app/android-main/src/test/kotlin/app/logdate/client/sync/FileWatchAudioStoreTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteIngestorTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task05-audio-digest.txt
```

---

### Task 6: Store phone audio apart from the watch's recordings (A5)

**Already fixed, not repeated here:** `3677db44c` deletes pulled audio when the phone deletes the note (`SyncedAudioFiles.deleteLocalAudio`). `7c58cce8d` never adopts an m4a without a raw file, and never adopts a raw `.aac` that a note references. **Still open:** `PhoneSyncedAudioResolver` pulls into `audio_notes/recording_<random uuid>.<ext>` through `AudioStorage.createRecordingTarget` (`WearSyncedAudioResolver.kt:38-46`). A pull cut off when the app is killed stays there forever. A pulled `.aac` that finished, but whose `updateMediaRef` never ran, is indistinguishable from an interrupted recording, so `WearRecordingRecovery` adopts it as a duplicate note. And every pull downloads again under a new random name.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/SyncedAudioCache.kt`
- Modify (rewrite): `app/wear/src/main/kotlin/app/logdate/wear/playback/WearSyncedAudioResolver.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt:128` (`processEvent` delete branch), `:148-174` (`handleNoteDelete`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/SyncedAudioFiles.kt:5-13` (KDoc)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/AudioModule.kt:132-140`
- Modify: `docs/feature-design/wear-audio-recording.md:91-97`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/SyncedAudioCacheTest.kt` (new)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/playback/PhoneSyncedAudioResolverTest.kt` (rewrite)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/recording/WearRecordingRecoveryTest.kt` (append 2 guards)

**Interfaces:**
- Consumes: `WearDataLayerClient.downloadAudioFromPhone(noteId: Uuid, destinationPath: String): Boolean`, which writes to the path and deletes it on failure; `SyncableJournalNotesRepository.updateMediaRef(noteId: Uuid, mediaRef: String)`.
- Produces:
  - `class SyncedAudioCache(directory: File) { fun cachedFile(noteId: Uuid): File?; suspend fun pull(noteId: Uuid, extension: String, download: suspend (partPath: String) -> Boolean): File?; fun delete(noteId: Uuid): Boolean; companion object { const val DIRECTORY_NAME = "synced_audio" } }`
  - `class PhoneSyncedAudioResolver(context: Context, syncedAudioCache: SyncedAudioCache, dataLayerClient: WearDataLayerClient, notesRepository: JournalNotesRepository, ioDispatcher: CoroutineDispatcher) : WearSyncedAudioResolver`

- [ ] **Step 1: Add the cache with inert bodies, and point the resolver and DI at it**

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/SyncedAudioCache.kt`:

```kotlin
package app.logdate.wear.sync

import java.io.File
import kotlin.uuid.Uuid

class SyncedAudioCache(
    private val directory: File,
) {
    fun cachedFile(noteId: Uuid): File? = null

    suspend fun pull(
        noteId: Uuid,
        extension: String,
        download: suspend (partPath: String) -> Boolean,
    ): File? = null

    fun delete(noteId: Uuid): Boolean = false

    companion object {
        const val DIRECTORY_NAME = "synced_audio"
    }
}
```

Replace `app/wear/src/main/kotlin/app/logdate/wear/playback/WearSyncedAudioResolver.kt` with:

```kotlin
package app.logdate.wear.playback

import android.content.Context
import android.net.Uri
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.wear.sync.SyncedAudioCache
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

interface WearSyncedAudioResolver {
    suspend fun resolvePlayableUri(note: JournalNote.Audio): Result<String>
}

/**
 * Finds a playable file for an audio note. When the watch has none, the audio is pulled from the phone
 * into [syncedAudioCache], or taken from an earlier pull, and the note is pointed at it so the next
 * play needs no transfer.
 */
class PhoneSyncedAudioResolver(
    private val context: Context,
    private val syncedAudioCache: SyncedAudioCache,
    private val dataLayerClient: WearDataLayerClient,
    private val notesRepository: JournalNotesRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : WearSyncedAudioResolver {
    override suspend fun resolvePlayableUri(note: JournalNote.Audio): Result<String> {
        val result =
            withContext(ioDispatcher) {
                runCatching {
                    if (isLocallyPlayable(note.mediaRef)) {
                        return@runCatching note.mediaRef
                    }
                    val file = syncedAudioCache.cachedFile(note.uid) ?: pullFromPhone(note)
                    (notesRepository as? SyncableJournalNotesRepository)?.updateMediaRef(note.uid, file.absolutePath)
                    file.absolutePath
                }
            }
        result.onFailure { error ->
            Napier.w("Failed to resolve synced audio for note ${note.uid}", error)
        }
        return result
    }

    private suspend fun pullFromPhone(note: JournalNote.Audio): File {
        check(dataLayerClient.isPhoneConnected()) {
            "No connected phone available to download audio ${note.uid}"
        }
        val pulled =
            syncedAudioCache.pull(note.uid, inferExtension(note.mediaRef)) { partPath ->
                dataLayerClient.downloadAudioFromPhone(note.uid, partPath)
            }
        return checkNotNull(pulled) { "Phone audio transfer failed for note ${note.uid}" }
    }

    private fun isLocallyPlayable(mediaRef: String): Boolean {
        if (mediaRef.startsWith("content://")) {
            return runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(mediaRef), "r")?.use { true } ?: false
            }.getOrDefault(false)
        }

        val filePath =
            when {
                mediaRef.startsWith("file://") -> Uri.parse(mediaRef).path
                mediaRef.startsWith("/") -> mediaRef
                else -> null
            }

        return filePath?.let { File(it).exists() } ?: false
    }

    private fun inferExtension(mediaRef: String): String {
        val normalized = mediaRef.substringBefore('?').substringBefore('#')
        val extension = normalized.substringAfterLast('.', "")
        return extension.takeIf { it.matches(SAFE_EXTENSION) } ?: "m4a"
    }

    private companion object {
        /** The extension becomes part of a file name, so anything but a short alphanumeric one is replaced. */
        val SAFE_EXTENSION = Regex("[A-Za-z0-9]{1,5}")
    }
}
```

In `AudioModule.kt`, replace lines 132-140 (the `WearSyncedAudioResolver` binding) with the two bindings below, and add `import app.logdate.wear.sync.SyncedAudioCache`:

```kotlin
        single { SyncedAudioCache(File(get<Context>().filesDir, SyncedAudioCache.DIRECTORY_NAME)) }
        single<WearSyncedAudioResolver> {
            PhoneSyncedAudioResolver(
                context = get(),
                syncedAudioCache = get(),
                dataLayerClient = get(),
                notesRepository = get(),
                ioDispatcher = get(qualifier = wearIoDispatcherQualifier),
            )
        }
```

`AudioStorage` stays bound; `WearAudioRecordingManager` still uses it.

- [ ] **Step 2: Write the failing tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/SyncedAudioCacheTest.kt`:

```kotlin
package app.logdate.wear.sync

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests [SyncedAudioCache], where audio pulled from the phone is stored apart from recordings.
 */
class SyncedAudioCacheTest {
    private val directory = Files.createTempDirectory("synced-audio").toFile()
    private val cache = SyncedAudioCache(directory)
    private val noteId = Uuid.random()

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `a completed pull becomes the note's audio`() =
        runTest {
            val file =
                cache.pull(noteId, "m4a") { partPath ->
                    File(partPath).writeText("phone audio")
                    true
                }

            assertEquals(File(directory, "$noteId.m4a"), file)
            assertEquals("phone audio", file?.readText())
            assertEquals(listOf("$noteId.m4a"), directory.list().orEmpty().toList())
        }

    @Test
    fun `a pull still in progress is not the note's audio yet`() =
        runTest {
            cache.pull(noteId, "m4a") { partPath ->
                File(partPath).writeText("half")
                assertNull(cache.cachedFile(noteId))
                true
            }

            assertEquals(File(directory, "$noteId.m4a"), cache.cachedFile(noteId))
        }

    @Test
    fun `a failed pull leaves nothing behind`() =
        runTest {
            val file =
                cache.pull(noteId, "m4a") { partPath ->
                    File(partPath).writeText("partial")
                    false
                }

            assertNull(file)
            assertEquals(emptyList(), directory.list().orEmpty().toList())
        }

    @Test
    fun `a pull that wrote nothing is not stored`() =
        runTest {
            assertNull(cache.pull(noteId, "m4a") { true })
            assertNull(cache.cachedFile(noteId))
        }

    @Test
    fun `a cached file belongs to its own note only`() =
        runTest {
            cache.pull(Uuid.random(), "m4a") { partPath ->
                File(partPath).writeText("another note")
                true
            }

            assertNull(cache.cachedFile(noteId))
        }

    @Test
    fun `deleting removes the note's audio`() =
        runTest {
            cache.pull(noteId, "aac") { partPath ->
                File(partPath).writeText("phone audio")
                true
            }

            assertTrue(cache.delete(noteId))
            assertNull(cache.cachedFile(noteId))
            assertFalse(cache.delete(noteId))
        }

    @Test
    fun `a part file a killed app left is cleared by the next pull`() =
        runTest {
            directory.mkdirs()
            val abandoned =
                File(directory, "${Uuid.random()}.m4a.part").apply {
                    writeText("cut off")
                    setLastModified(System.currentTimeMillis() - TWO_HOURS)
                }
            val inProgress = File(directory, "${Uuid.random()}.m4a.part").apply { writeText("arriving") }

            cache.pull(noteId, "m4a") { partPath ->
                File(partPath).writeText("phone audio")
                true
            }

            assertFalse(abandoned.exists())
            assertTrue(inProgress.exists())
        }

    private companion object {
        const val TWO_HOURS = 2 * 60 * 60 * 1000L
    }
}
```

Replace `app/wear/src/test/kotlin/app/logdate/wear/playback/PhoneSyncedAudioResolverTest.kt` with:

```kotlin
package app.logdate.wear.playback

import android.content.Context
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.wear.sync.SyncedAudioCache
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [PhoneSyncedAudioResolver], which finds a playable file for an audio note and pulls one from
 * the phone into the synced audio cache when the watch has none.
 */
class PhoneSyncedAudioResolverTest {
    private val context = mockk<Context>(relaxed = true)
    private val dataLayerClient = mockk<WearDataLayerClient>(relaxed = true)
    private val notesRepository = mockk<SyncableJournalNotesRepository>(relaxed = true)
    private val cacheDirectory = Files.createTempDirectory("synced-audio").toFile()
    private val cache = SyncedAudioCache(cacheDirectory)

    @Before
    fun setUp() {
        coEvery { dataLayerClient.isPhoneConnected() } returns true
        coEvery { dataLayerClient.downloadAudioFromPhone(any(), any()) } answers {
            File(secondArg<String>()).writeText("phone audio")
            true
        }
    }

    @After
    fun cleanUp() {
        cacheDirectory.deleteRecursively()
    }

    private fun buildResolver(dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) =
        PhoneSyncedAudioResolver(
            context = context,
            syncedAudioCache = cache,
            dataLayerClient = dataLayerClient,
            notesRepository = notesRepository,
            ioDispatcher = dispatcher,
        )

    @Test
    fun `returns existing local media ref without download`() =
        runTest {
            val noteId = Uuid.random()
            val localFile = File.createTempFile("wear-note-$noteId", ".m4a").apply { writeText("audio") }
            try {
                val result = buildResolver().resolvePlayableUri(audioNote(noteId, localFile.absolutePath))

                assertEquals(localFile.absolutePath, result.getOrNull())
                coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any()) }
                coVerify(exactly = 0) { dataLayerClient.isPhoneConnected() }
            } finally {
                localFile.delete()
            }
        }

    @Test
    fun `phone audio is pulled into the cache under the note's id`() =
        runTest {
            val noteId = Uuid.random()

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId.m4a"))

            val expected = File(cacheDirectory, "$noteId.m4a").absolutePath
            assertEquals(expected, result.getOrNull())
            assertEquals("phone audio", File(expected).readText())
            coVerify { dataLayerClient.isPhoneConnected() }
            coVerify { notesRepository.updateMediaRef(noteId, expected) }
        }

    @Test
    fun `audio pulled earlier is reused without asking the phone again`() =
        runTest {
            val noteId = Uuid.random()
            val earlier = File(cacheDirectory, "$noteId.m4a").apply { writeText("earlier pull") }

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId.m4a"))

            assertEquals(earlier.absolutePath, result.getOrNull())
            coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any()) }
            coVerify { notesRepository.updateMediaRef(noteId, earlier.absolutePath) }
        }

    @Test
    fun `an extension that is not a plain file extension is replaced`() =
        runTest {
            val noteId = Uuid.random()

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/a.b/c"))

            assertEquals(File(cacheDirectory, "$noteId.m4a").absolutePath, result.getOrNull())
        }

    @Test
    fun `a remote url is fetched from the phone instead of streamed`() =
        runTest {
            val noteId = Uuid.random()

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "https://cloud.example/media/$noteId"))

            assertEquals(File(cacheDirectory, "$noteId.m4a").absolutePath, result.getOrNull())
            coVerify { dataLayerClient.downloadAudioFromPhone(noteId, any()) }
        }

    @Test
    fun `a failed pull fails and leaves nothing in the cache`() =
        runTest {
            val noteId = Uuid.random()
            coEvery { dataLayerClient.downloadAudioFromPhone(any(), any()) } answers {
                File(secondArg<String>()).writeText("partial")
                false
            }

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId"))

            assertTrue(result.isFailure)
            assertEquals(emptyList(), cacheDirectory.listFiles().orEmpty().toList())
            coVerify { dataLayerClient.downloadAudioFromPhone(noteId, any()) }
            coVerify(exactly = 0) { notesRepository.updateMediaRef(any(), any()) }
        }

    @Test
    fun `a remote url with no phone in range fails instead of streaming`() =
        runTest {
            val noteId = Uuid.random()
            coEvery { dataLayerClient.isPhoneConnected() } returns false

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "https://cloud.example/media/$noteId"))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any()) }
        }

    @Test
    fun `fails when the phone is unreachable`() =
        runTest {
            val noteId = Uuid.random()
            coEvery { dataLayerClient.isPhoneConnected() } returns false

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId"))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any()) }
            coVerify(exactly = 0) { notesRepository.updateMediaRef(any(), any()) }
        }

    private fun audioNote(
        noteId: Uuid,
        mediaRef: String,
    ) = JournalNote.Audio(
        uid = noteId,
        creationTimestamp = Instant.fromEpochMilliseconds(1_710_000_000_000),
        lastUpdated = Instant.fromEpochMilliseconds(1_710_000_000_000),
        mediaRef = mediaRef,
        durationMs = 4_200,
    )
}
```

Each of the six tests the file has today is still there with its assertions, rewritten against the cache instead of a mocked `AudioStorage`: `returns existing local media ref without download`, `a remote url is fetched from the phone instead of streamed` and `a remote url with no phone in range fails instead of streaming` have the same names, `fails when phone is unreachable` becomes `fails when the phone is unreachable`, `downloads phone media and updates synced note media ref` becomes `phone audio is pulled into the cache under the note's id`, and `fails when phone media download fails` becomes `a failed pull fails and leaves nothing in the cache`.

Append to `WearRecordingRecoveryTest` (before the final `}`), and add `import app.logdate.wear.sync.SyncedAudioCache`. These two pin the invariant and pass before and after this task:

```kotlin
    @Test
    fun `audio in the synced audio cache is never adopted`() =
        runTest {
            val root = Files.createTempDirectory("wear-files").toFile()
            try {
                val recordings = File(root, "audio_notes").apply { mkdirs() }
                val synced = File(root, SyncedAudioCache.DIRECTORY_NAME).apply { mkdirs() }
                val pulled = File(synced, "recording_${Uuid.random()}.aac").apply { writeBytes(ByteArray(50_000) { 1 }) }

                val recovered = WearRecordingRecovery(recordings, repository, resolver, remuxer).recover()

                assertEquals(0, recovered)
                assertTrue(created.isEmpty())
                assertTrue(pulled.isFile)
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun `a partial download is never adopted`() =
        runTest {
            val id = Uuid.random()
            val part = file("recording_$id.aac.part", bytes = 50_000)
            durations[File(directory, "recording_$id.m4a").absolutePath] = 10_000L

            val recovered = recovery.recover()

            assertEquals(0, recovered)
            assertTrue(created.isEmpty())
            assertTrue(part.isFile)
        }
```

- [ ] **Step 3: Run the new tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.SyncedAudioCacheTest' --tests 'app.logdate.wear.playback.PhoneSyncedAudioResolverTest' --tests 'app.logdate.wear.recording.WearRecordingRecoveryTest'
```
Expected: FAIL. The cache tests get `null` where a file is expected. `phone audio is pulled into the cache under the note's id` fails with `Phone audio transfer failed`. The recovery guards pass.

- [ ] **Step 4: Implement the cache and delete through it**

Replace `app/wear/src/main/kotlin/app/logdate/wear/sync/SyncedAudioCache.kt` with:

```kotlin
package app.logdate.wear.sync

import io.github.aakira.napier.Napier
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

/**
 * Audio pulled from the phone so the watch can play a phone note, stored apart from the watch's own
 * recordings.
 *
 * A note's audio lives at `<directory>/<noteId>.<extension>`. A download is written to a `.part` file
 * beside it and renamed into place only once complete, so a transfer cut short by the app being
 * killed never looks like a finished file. Crash recovery only scans the recordings directory, so
 * nothing here can be mistaken for an interrupted recording and saved as a new note.
 */
class SyncedAudioCache(
    private val directory: File,
) {
    /** The complete audio already pulled for [noteId], or null when there is none. */
    fun cachedFile(noteId: Uuid): File? =
        directory
            .listFiles()
            .orEmpty()
            .firstOrNull { it.isFile && it.nameWithoutExtension == noteId.toString() }

    /**
     * Pulls the audio for [noteId]: [download] writes it to the path it is given, and the file becomes
     * the note's audio only when [download] reports success and wrote something.
     *
     * @return the complete file, or null when nothing was stored.
     */
    suspend fun pull(
        noteId: Uuid,
        extension: String,
        download: suspend (partPath: String) -> Boolean,
    ): File? {
        directory.mkdirs()
        clearAbandonedParts()
        val part = File(directory, "$noteId.$extension.$PART_EXTENSION")
        val complete = download(part.absolutePath) && part.length() > 0L
        if (!complete) {
            part.delete()
            return null
        }
        val target = File(directory, "$noteId.$extension")
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return target
    }

    /** Removes the audio pulled for [noteId]. @return true when a file was deleted. */
    fun delete(noteId: Uuid): Boolean = cachedFile(noteId)?.delete() ?: false

    private fun clearAbandonedParts() {
        val cutoff = System.currentTimeMillis() - ABANDONED_PART_AGE_MS
        directory
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == PART_EXTENSION && it.lastModified() < cutoff }
            .forEach { abandoned ->
                if (abandoned.delete()) Napier.d("Cleared abandoned audio download ${abandoned.name}")
            }
    }

    companion object {
        const val DIRECTORY_NAME = "synced_audio"
        private const val PART_EXTENSION = "part"

        /** A download writes continuously, so a part file untouched for an hour was abandoned. */
        private const val ABANDONED_PART_AGE_MS = 60L * 60 * 1000
    }
}
```

In `WearDataLayerListenerService.kt`, change the delete branch of `processEvent` (line 128) to:

```kotlin
                NoteDataMapper.isDeletePath(path) -> handleNoteDelete(path, notesRepository, koin.get())
```

and in `handleNoteDelete` (lines 148-174), change the signature and the cleanup to:

```kotlin
    private suspend fun handleNoteDelete(
        path: String,
        notesRepository: JournalNotesRepository,
        syncedAudioCache: SyncedAudioCache,
    ) {
```

```kotlin
            if (notesRepository.getNoteById(noteId) == null) {
                deleteLocalAudio(mediaRef, File(filesDir, "audio_notes"))
                syncedAudioCache.delete(noteId)
            }
```

In `SyncedAudioFiles.kt`, replace the KDoc's first paragraph (lines 6-7) with:

```kotlin
 * Deletes the audio file of a note the phone removed, including audio that versions before
 * [SyncedAudioCache] pulled from the phone into the recordings directory, which otherwise stays in
 * storage for good.
```

In `docs/feature-design/wear-audio-recording.md`, replace the paragraph under `### Recovery after the app is killed` (lines 93-97) with:

```markdown
`WearRecordingRecovery` runs at every launch, on a background thread, over raw files written before the
process started. It wraps a raw file that has no m4a, saves a note for any recording whose note is
missing, and removes the raw file once the note exists. A raw file that cannot be wrapped yet stays for
the next launch. An m4a with no raw file beside it is never adopted, because it was saved or deleted.

Audio pulled from the phone for playback lives in `filesDir/synced_audio/<note id>.<extension>`, apart
from recordings, and appears there only once the download is complete, so recovery never sees it and
an interrupted download never becomes a note. Earlier versions pulled audio into `audio_notes`; a raw
file a note references is such a pull and is left alone.
```

- [ ] **Step 5: Run the tests and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:detekt :app:wear:ktlintCheck
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task06-synced-audio-cache.txt <<'EOF'
fix(wear): store phone audio apart from the watch's recordings

Playing a phone voice note on the watch downloads its audio, and the
download went into the folder where the watch's own recordings live,
named like a recording. If the app was killed during a download, the
partial file stayed there for good, and a download in the raw recording
format could be picked up at the next launch as an interrupted
recording and saved as a new, duplicate note.

Downloads now go to their own folder, named after the note, and appear
there only once complete. Playing the note again reuses the download
instead of fetching it again, deleting the note on the phone removes
it, and downloads cut off by the app closing are cleared.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/sync/SyncedAudioCache.kt app/wear/src/main/kotlin/app/logdate/wear/playback/WearSyncedAudioResolver.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt app/wear/src/main/kotlin/app/logdate/wear/sync/SyncedAudioFiles.kt app/wear/src/main/kotlin/app/logdate/wear/di/AudioModule.kt docs/feature-design/wear-audio-recording.md app/wear/src/test/kotlin/app/logdate/wear/sync/SyncedAudioCacheTest.kt app/wear/src/test/kotlin/app/logdate/wear/playback/PhoneSyncedAudioResolverTest.kt app/wear/src/test/kotlin/app/logdate/wear/recording/WearRecordingRecoveryTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task06-synced-audio-cache.txt
```

---

### Task 7: Leave captures that fail repeatedly in the backup queue (A6)

`main` changed what an outbox row is (`78faf0960`): each queued change has an operation id, and `settleIfCurrent`/`incrementRetryIfCurrent` act only on the operation an attempt captured. The watch still calls `markAsSynced` and `incrementRetryCount`, which act on whatever row the entity has, so an upload that finishes after the note was changed again clears the newer change, and a failure is charged against it. This task moves every way an item leaves the watch's outbox, and every failure count, to the captured-operation calls, the way the phone's `SyncRetryCoordinator` does, while it changes the dead-letter policy.

**Files:**
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt:42-48` (class KDoc), `:138-146` (`onNoteAcknowledged`), `:296-311` (`retryDeadLetter`, `discardDeadLetter`), `:522-572` (`handleSyncFailure`), `:607-626` (`handleOutcome` SUCCESS/SKIPPED), `:665-671` (companion), plus new `settle`, `removeDeadLetters`, `queuedNote`, `setAsideOperation` and `deadLetterId`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDeadLetterMigration.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt:99` (binding)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt:1-18` (imports), `:52` (call), plus new `startSync()`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt` (`setUp` stubs, outbox verifications, the acknowledgement test at `:532-539` as of `a8615ae5d`, 10 new tests and a helper)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/AudioSyncFlowTest.kt` (`setUp` stubs, outbox verifications)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WearDeadLetterMigrationTest.kt` (new)

**Interfaces:**
- Consumes: `SyncMetadataService.getPendingUploads(entityType): List<PendingUpload>`, `settleIfCurrent(entityType: EntityType, pending: PendingUpload, syncedAt: Instant, version: Long): Boolean`, `incrementRetryIfCurrent(entityType: EntityType, pending: PendingUpload): Boolean`, `enqueuePending(entityId, entityType, operation)`; `PendingUpload.scope`, `operationId`, `expectedServerVersion`; `SyncDeadLetterRecord(id, entityType, entityId, operation, retryCount, lastError, failedAt, reason, scope, operationId, expectedServerVersion)`; `SyncDeadLetterStore.add/remove/list`; `SyncRetryScheduleStore.setNextAttemptAt/clear`.
- Produces:
  - Dead-letter id `pending.operationId ?: "${entityType.name}:${pending.entityId}"`, the phone's `SyncRetryCoordinator` form, and a record with the operation's `scope`, `operationId` and `expectedServerVersion`
  - `WearDataLayerSyncManager.DEAD_LETTER_RETRY_INTERVAL_MS = 6L * 60 * 60 * 1000` (public const)
  - `private suspend fun settle(entityType: EntityType, pending: PendingUpload, version: Long)`: the one way an operation leaves the outbox, whether delivered, unrecoverable (SKIPPED), acknowledged, or discarded. It settles only the operation `pending` captured, clears the item's retry time and removes the item's dead letters. Tasks 8 and 12 build on it.
  - `retryDeadLetter(id)` and `discardDeadLetter(id)` run under `syncLock`. `discardDeadLetter` is the only path that drops a set-aside operation without delivering it, and it never drops a change made after the operation was set aside.
  - `class WearDeadLetterMigration(deadLetterStore: SyncDeadLetterStore, syncMetadataService: SyncMetadataService) { suspend fun migrate(): Int }`

- [ ] **Step 1: Add the API surface with inert bodies**

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDeadLetterMigration.kt`:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService

class WearDeadLetterMigration(
    private val deadLetterStore: SyncDeadLetterStore,
    private val syncMetadataService: SyncMetadataService,
) {
    suspend fun migrate(): Int = 0
}
```

In `WearDataLayerSyncManager.kt`, add to the companion object (lines 665-671):

```kotlin
        /** How long an item set aside after [MAX_RETRY_ATTEMPTS] failures waits before it is tried again. */
        const val DEAD_LETTER_RETRY_INTERVAL_MS = 6L * 60 * 60 * 1000
```

In `WearDataModule.kt`, after `single { WearPhoneMessageHandler(ackHandler = get(), syncManager = get()) }` (line 99), add the line below, plus `import app.logdate.wear.sync.WearDeadLetterMigration`:

```kotlin
        single { WearDeadLetterMigration(deadLetterStore = get(), syncMetadataService = get()) }
```

In `LogDateWearApplication.kt`, add imports `app.logdate.wear.sync.WearDeadLetterMigration`, `kotlinx.coroutines.CoroutineScope` and `kotlinx.coroutines.launch`. Call `startSync()` right after `recoverInterruptedRecordings(launchedAtMs)` (line 52), before the `LegacyPromptAlarms.cancel(this)` call that `d69aea766` added, and add below `recoverInterruptedRecordings`:

```kotlin
    /** Puts captures an older version dropped after repeated failures back in the outbox. */
    private fun startSync() {
        val koin =
            org.koin.java.KoinJavaComponent
                .getKoin()
        koin.get<CoroutineScope>().launch {
            requeueSetAsideCaptures(koin.get())
        }
    }

    private suspend fun requeueSetAsideCaptures(migration: WearDeadLetterMigration) {
        try {
            val requeued = migration.migrate()
            if (requeued > 0) Napier.i("Queued $requeued capture(s) again that an older version set aside")
        } catch (e: Exception) {
            Napier.e("Queueing set-aside captures again failed", e)
        }
    }
```

The `CoroutineScope` comes from `deviceInstanceModule` (`CoroutineScope(SupervisorJob() + Dispatchers.Default)`), which `wearDataModule` includes.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Write the failing tests**

Point the existing outbox verifications in both watch sync suites at the captured-operation calls. Each still checks what it checked before: that the note left the outbox, or that a failure was counted, now for the operation the attempt sent:

```bash
perl -0pi -e '
s/syncMetadataService\.markAsSynced\(\n(\s*)entityId = noteId\.toString\(\),\n\s*entityType = EntityType\.NOTE,\n\s*syncedAt = any\(\),\n\s*version = any\(\),\n(\s*)\)/syncMetadataService.settleIfCurrent(\n$1EntityType.NOTE,\n$1match { it.entityId == noteId.toString() },\n$1any(),\n$1any(),\n$2)/g;
s/syncMetadataService\.markAsSynced\(noteId\.toString\(\), EntityType\.NOTE, any\(\), any\(\)\)/syncMetadataService.settleIfCurrent(EntityType.NOTE, match { it.entityId == noteId.toString() }, any(), any())/g;
s/syncMetadataService\.markAsSynced\(any\(\), EntityType\.NOTE, any\(\), any\(\)\)/syncMetadataService.settleIfCurrent(EntityType.NOTE, any(), any(), any())/g;
s/syncMetadataService\.markAsSynced\(any\(\), any\(\), any\(\), any\(\)\)/syncMetadataService.settleIfCurrent(any(), any(), any(), any())/g;
s/syncMetadataService\.incrementRetryCount\(noteId\.toString\(\), EntityType\.NOTE\)/syncMetadataService.incrementRetryIfCurrent(EntityType.NOTE, match { it.entityId == noteId.toString() })/g;
' app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/AudioSyncFlowTest.kt
grep -n 'markAsSynced\|incrementRetryCount' app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/AudioSyncFlowTest.kt
```

Expected: `grep` prints nothing. The rewrite touches these tests (lines as of `a8615ae5d`; Tasks 2 and 5 have shifted them since): in `WearDataLayerSyncManagerTest`, `upload marks note as synced after successful put` (252-257), `upload fails for audio note when file transfer fails` (363-364), `upload leaves an audio note pending until the phone acknowledges it` (380), `acknowledged audio note is marked synced and its retry schedule cleared` (537), `delete upload marks as synced on success` (697), `upload increments retry count on individual note failure` (756) and `upload clears outbox for missing notes` (789); in `AudioSyncFlowTest`, `full audio sync flow - record and sync to phone` (150, 155-160), `audio sync fails gracefully when phone not connected` (201), `audio note stays pending for retry when file transfer fails` (242-243), `batch upload of multiple audio notes` (283, 286) and `sync handles note deleted from DB between enqueue and upload` (314).

In the `setUp` of both suites, after `coEvery { deadLetterStore.list() } returns emptyList()`, add the stubs a real outbox would answer with while the attempt's operation is still the queued one:

```kotlin
        coEvery { syncMetadataService.settleIfCurrent(any(), any(), any(), any()) } returns true
        coEvery { syncMetadataService.incrementRetryIfCurrent(any(), any()) } returns true
```

In `WearDataLayerSyncManagerTest`, the acknowledgement now settles the note's queued operation, so `acknowledged audio note is marked synced and its retry schedule cleared` queues one first. Replace that test with:

```kotlin
    @Test
    fun `acknowledged audio note is marked synced and its retry schedule cleared`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(PendingUpload(noteId.toString(), PendingOperation.CREATE))

            syncManager.onNoteAcknowledged(noteId)

            coVerify { syncMetadataService.settleIfCurrent(EntityType.NOTE, match { it.entityId == noteId.toString() }, any(), any()) }
            coVerify { retryScheduleStore.clear(EntityType.NOTE, noteId.toString()) }
        }
```

`AudioSyncFlowTest` needs no such change: its two acknowledgement scenarios queue their notes before acknowledging them.

Append to `WearDataLayerSyncManagerTest` (before the final `}`), and add `import app.logdate.client.sync.metadata.SyncDeadLetterRecord` and `import app.logdate.client.sync.metadata.UploadScope`:

```kotlin
    // =======================================================================
    // Items that fail repeatedly
    // =======================================================================

    @Test
    fun `an item that fails repeatedly is set aside but stays queued for a retry in six hours`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(
                        noteId.toString(),
                        PendingOperation.CREATE,
                        retryCount = WearDataLayerSyncManager.MAX_RETRY_ATTEMPTS - 1,
                    ),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote
            coEvery { dataLayerClient.putDataItem(any(), any(), any()) } returns false

            fixedClockSyncManager().uploadPendingChanges()

            coVerify { deadLetterStore.add(match { it.id == "NOTE:$noteId" && it.entityId == noteId.toString() }) }
            coVerify(exactly = 0) { syncMetadataService.settleIfCurrent(any(), any(), any(), any()) }
            coVerify(exactly = 0) { syncMetadataService.markAsSynced(any(), any(), any(), any()) }
            coVerify {
                retryScheduleStore.setNextAttemptAt(
                    EntityType.NOTE,
                    noteId.toString(),
                    fixedTime.toEpochMilliseconds() + WearDataLayerSyncManager.DEAD_LETTER_RETRY_INTERVAL_MS,
                )
            }
        }

    @Test
    fun `a set-aside operation is named by its operation id, as on the phone`() =
        runTest {
            val scope = UploadScope(ownerId = "owner", serverOrigin = "https://logdate.example")
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(
                        noteId.toString(),
                        PendingOperation.CREATE,
                        retryCount = WearDataLayerSyncManager.MAX_RETRY_ATTEMPTS - 1,
                        scope = scope,
                        operationId = "operation-1",
                    ),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote
            coEvery { dataLayerClient.putDataItem(any(), any(), any()) } returns false

            syncManager.uploadPendingChanges()

            coVerify {
                deadLetterStore.add(match { it.id == "operation-1" && it.operationId == "operation-1" && it.scope == scope })
            }
        }

    @Test
    fun `a failed attempt at an operation a newer change replaced is not counted`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(
                        noteId.toString(),
                        PendingOperation.CREATE,
                        retryCount = WearDataLayerSyncManager.MAX_RETRY_ATTEMPTS - 1,
                    ),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote
            coEvery { dataLayerClient.putDataItem(any(), any(), any()) } returns false
            coEvery { syncMetadataService.incrementRetryIfCurrent(any(), any()) } returns false

            syncManager.uploadPendingChanges()

            coVerify(exactly = 0) { deadLetterStore.add(any()) }
            coVerify(exactly = 0) { retryScheduleStore.setNextAttemptAt(any(), any(), any()) }
        }

    @Test
    fun `a delivered note settles only the operation that was sent`() =
        runTest {
            val pending = PendingUpload(noteId.toString(), PendingOperation.CREATE, operationId = "operation-1")
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns listOf(pending)
            coEvery { notesRepository.getNoteById(noteId) } returns textNote

            syncManager.uploadPendingChanges()

            coVerify { syncMetadataService.settleIfCurrent(EntityType.NOTE, pending, any(), 1L) }
            coVerify(exactly = 0) { syncMetadataService.markAsSynced(any(), any(), any(), any()) }
        }

    @Test
    fun `delivering an item that was set aside clears its record`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(
                        noteId.toString(),
                        PendingOperation.CREATE,
                        retryCount = WearDataLayerSyncManager.MAX_RETRY_ATTEMPTS,
                    ),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote
            coEvery { deadLetterStore.list() } returns listOf(deadLetter())

            syncManager.uploadPendingChanges()

            coVerify { deadLetterStore.remove("NOTE:$noteId") }
        }

    @Test
    fun `discarding a set-aside item takes it out of the outbox`() =
        runTest {
            val pending = PendingUpload(noteId.toString(), PendingOperation.CREATE)
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns listOf(pending)
            coEvery { deadLetterStore.list() } returns listOf(deadLetter())

            syncManager.discardDeadLetter("NOTE:$noteId")

            coVerify { syncMetadataService.settleIfCurrent(EntityType.NOTE, pending, any(), 0L) }
            coVerify { retryScheduleStore.clear(EntityType.NOTE, noteId.toString()) }
            coVerify { deadLetterStore.remove("NOTE:$noteId") }
        }

    @Test
    fun `discarding leaves a change made after the item was set aside queued`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(PendingUpload(noteId.toString(), PendingOperation.CREATE, operationId = "operation-2"))
            coEvery { deadLetterStore.list() } returns listOf(deadLetter().copy(id = "operation-1", operationId = "operation-1"))

            syncManager.discardDeadLetter("operation-1")

            coVerify(exactly = 0) { syncMetadataService.settleIfCurrent(any(), any(), any(), any()) }
            coVerify { deadLetterStore.remove("operation-1") }
        }

    @Test
    fun `retrying a set-aside item makes it due now`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(PendingUpload(noteId.toString(), PendingOperation.CREATE))
            coEvery { deadLetterStore.list() } returns listOf(deadLetter())

            syncManager.retryDeadLetter("NOTE:$noteId")

            coVerify { retryScheduleStore.clear(EntityType.NOTE, noteId.toString()) }
            coVerify { deadLetterStore.remove("NOTE:$noteId") }
            coVerify(exactly = 0) { syncMetadataService.enqueuePending(any(), any(), any()) }
        }

    @Test
    fun `retrying an item that is no longer queued queues it again`() =
        runTest {
            coEvery { deadLetterStore.list() } returns listOf(deadLetter())

            syncManager.retryDeadLetter("NOTE:$noteId")

            coVerify { syncMetadataService.enqueuePending(noteId.toString(), EntityType.NOTE, PendingOperation.CREATE) }
            coVerify { deadLetterStore.remove("NOTE:$noteId") }
        }

    @Test
    fun `an acknowledgement for a note that is not queued changes nothing`() =
        runTest {
            syncManager.onNoteAcknowledged(noteId)

            coVerify(exactly = 0) { syncMetadataService.settleIfCurrent(any(), any(), any(), any()) }
            coVerify(exactly = 0) { syncMetadataService.markAsSynced(any(), any(), any(), any()) }
        }

    private fun deadLetter(): SyncDeadLetterRecord =
        SyncDeadLetterRecord(
            id = "NOTE:$noteId",
            entityType = EntityType.NOTE.name,
            entityId = noteId.toString(),
            operation = PendingOperation.CREATE.name,
            retryCount = WearDataLayerSyncManager.MAX_RETRY_ATTEMPTS,
            lastError = "Failed to sync note $noteId",
            failedAt = fixedTime.toEpochMilliseconds(),
        )
```

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/WearDeadLetterMigrationTest.kt`:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests [WearDeadLetterMigration], which puts captures older versions dropped from the outbox back
 * in it.
 */
class WearDeadLetterMigrationTest {
    private val deadLetterStore = mockk<SyncDeadLetterStore>(relaxed = true)
    private val syncMetadataService = mockk<SyncMetadataService>(relaxed = true)
    private val migration = WearDeadLetterMigration(deadLetterStore, syncMetadataService)
    private val noteId = "550e8400-e29b-41d4-a716-446655440000"

    @Test
    fun `a capture an older version set aside is queued again`() =
        runTest {
            coEvery { deadLetterStore.list() } returns listOf(record(id = "NOTE_$noteId", entityType = "NOTE", entityId = noteId))

            assertEquals(1, migration.migrate())

            coVerify { syncMetadataService.enqueuePending(noteId, EntityType.NOTE, PendingOperation.CREATE) }
            coVerify { deadLetterStore.remove("NOTE_$noteId") }
        }

    @Test
    fun `a link an older version set aside is queued again under its own id`() =
        runTest {
            val pendingId = "770e8400-e29b-41d4-a716-446655440000::$noteId"
            coEvery { deadLetterStore.list() } returns
                listOf(record(id = "ASSOCIATION_$pendingId", entityType = "ASSOCIATION", entityId = pendingId, operation = "DELETE"))

            migration.migrate()

            coVerify { syncMetadataService.enqueuePending(pendingId, EntityType.ASSOCIATION, PendingOperation.DELETE) }
        }

    @Test
    fun `items this version set aside are left alone`() =
        runTest {
            coEvery { deadLetterStore.list() } returns
                listOf(
                    record(id = "NOTE:$noteId", entityType = "NOTE", entityId = noteId),
                    record(id = "operation-1", entityType = "NOTE", entityId = noteId),
                )

            assertEquals(0, migration.migrate())

            coVerify(exactly = 0) { syncMetadataService.enqueuePending(any(), any(), any()) }
            coVerify(exactly = 0) { deadLetterStore.remove(any()) }
        }

    @Test
    fun `a record of an unknown kind stays where it can be seen`() =
        runTest {
            coEvery { deadLetterStore.list() } returns listOf(record(id = "MOMENT_$noteId", entityType = "MOMENT", entityId = noteId))

            assertEquals(0, migration.migrate())

            coVerify(exactly = 0) { deadLetterStore.remove(any()) }
        }

    private fun record(
        id: String,
        entityType: String,
        entityId: String,
        operation: String = "CREATE",
    ) = SyncDeadLetterRecord(
        id = id,
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        retryCount = 5,
        lastError = "Phone did not confirm ${entityType.lowercase()} $entityId",
        failedAt = 1_700_000_000_000,
    )
}
```

- [ ] **Step 3: Run the new tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.WearDataLayerSyncManagerTest' --tests 'app.logdate.wear.sync.AudioSyncFlowTest' --tests 'app.logdate.wear.sync.WearDeadLetterMigrationTest'
```
Expected: FAIL.
- The rewritten outbox verifications fail: the manager still calls `markAsSynced` and `incrementRetryCount`, so `settleIfCurrent` and `incrementRetryIfCurrent` were not called.
- `an item that fails repeatedly…` finds an add with id `NOTE_550e…`, and `markAsSynced` was called once. `a set-aside operation is named by its operation id…` finds id `NOTE_550e…`. `a failed attempt at an operation a newer change replaced…` finds a dead letter added. `a delivered note settles only the operation that was sent` finds `markAsSynced` called.
- `delivering an item that was set aside clears its record`: `remove` was not called. `discarding a set-aside item…`: `settleIfCurrent` was not called. `retrying a set-aside item…`: `enqueuePending` was called and `clear` was not. `an acknowledgement for a note that is not queued changes nothing` and the rewritten acknowledgement test: the manager calls `markAsSynced` whether or not the note is queued.
- `a capture an older version set aside is queued again`: `expected:<1> but was:<0>`.
- Already passing: `retrying an item that is no longer queued queues it again`, `discarding leaves a change made after the item was set aside queued`, and the two "left alone" and "unknown kind" migration tests.

- [ ] **Step 4: Leave failing items queued, settle only the captured operation, and requeue old records**

In `WearDataLayerSyncManager.kt`:

Replace the `- Dead letter queue for permanently failed items (after 5 attempts)` line of the class KDoc (line 44) with:

```kotlin
 * - Items that fail [MAX_RETRY_ATTEMPTS] times are recorded as dead letters and retried every
 *   [DEAD_LETTER_RETRY_INTERVAL_MS]; they leave the outbox only when delivered or discarded
 * - Only the operation an attempt sent is settled or charged with a failure, so a change queued
 *   while an attempt is in flight is never lost or counted against
```

Replace `onNoteAcknowledged` (lines 138-146) with:

```kotlin
    override suspend fun onNoteAcknowledged(noteId: Uuid) {
        val pending = queuedNote(noteId)
        if (pending == null) {
            Napier.d("Note $noteId was confirmed but is no longer queued")
            return
        }
        settle(EntityType.NOTE, pending, version = 1)
    }

    private suspend fun queuedNote(noteId: Uuid): PendingUpload? =
        syncMetadataService.getPendingUploads(EntityType.NOTE).firstOrNull { it.entityId == noteId.toString() }
```

Replace `retryDeadLetter` and `discardDeadLetter` (lines 296-311) with:

```kotlin
    override suspend fun retryDeadLetter(id: String) =
        syncLock.withLock {
            val record = deadLetterStore.list().firstOrNull { it.id == id } ?: return@withLock
            val entityType = runCatching { EntityType.valueOf(record.entityType) }.getOrNull()
            val operation = runCatching { PendingOperation.valueOf(record.operation) }.getOrNull()
            if (entityType == null || operation == null) {
                Napier.w("Cannot retry dead-letter $id with type=${record.entityType} op=${record.operation}")
                deadLetterStore.remove(id)
                return@withLock
            }
            if (syncMetadataService.getPendingUploads(entityType).none { it.entityId == record.entityId }) {
                syncMetadataService.enqueuePending(record.entityId, entityType, operation)
            }
            retryScheduleStore.clear(entityType, record.entityId)
            deadLetterStore.remove(id)
        }

    /**
     * Abandons a set-aside item: the only way one leaves the outbox without reaching the phone. Only
     * the operation that was set aside goes; a change made to the item since stays queued.
     */
    override suspend fun discardDeadLetter(id: String) =
        syncLock.withLock {
            val record = deadLetterStore.list().firstOrNull { it.id == id }
            deadLetterStore.remove(id)
            if (record == null) return@withLock
            val entityType = runCatching { EntityType.valueOf(record.entityType) }.getOrNull()
            if (entityType == null) {
                Napier.w("Cannot discard dead-letter $id with type=${record.entityType}")
                return@withLock
            }
            val pending = setAsideOperation(entityType, record) ?: return@withLock
            settle(entityType, pending, version = 0)
        }

    /** The queued operation [record] set aside, if it is still the one waiting. */
    private suspend fun setAsideOperation(
        entityType: EntityType,
        record: SyncDeadLetterRecord,
    ): PendingUpload? =
        syncMetadataService.getPendingUploads(entityType).firstOrNull { pending ->
            pending.entityId == record.entityId && (record.operationId == null || pending.operationId == record.operationId)
        }
```

Replace the KDoc and the start of `handleSyncFailure` (lines 522-561, from `/**` through the dead-letter branch's `return true` and its closing `}`) with:

```kotlin
    /**
     * Handles a failed sync attempt with exponential backoff.
     *
     * A failure counts only against the operation the attempt sent: when the item changed while it
     * was in flight, the newer change starts with a fresh budget and is tried next. After
     * [MAX_RETRY_ATTEMPTS] failures the item is recorded as a dead letter, so the failure shows as a
     * sync issue, and it is tried again every [DEAD_LETTER_RETRY_INTERVAL_MS]. It stays in the
     * outbox: it still counts as waiting, and it still reaches the phone once whatever broke it is fixed.
     *
     * @return true if the item was dead-lettered, false if it is scheduled for a normal retry or was replaced
     */
    private suspend fun handleSyncFailure(
        pending: PendingUpload,
        entityType: EntityType,
        errorMessage: String,
        retryDelayMs: Long? = null,
    ): Boolean {
        if (!syncMetadataService.incrementRetryIfCurrent(entityType, pending)) {
            Napier.i("${entityType.name} ${pending.entityId} changed while it was being sent; the newer change is tried next")
            return false
        }
        val nextRetryCount = pending.retryCount + 1

        if (nextRetryCount >= MAX_RETRY_ATTEMPTS) {
            Napier.w("Setting aside ${entityType.name} ${pending.entityId} after $nextRetryCount failures; it stays queued")
            deadLetterStore.add(
                SyncDeadLetterRecord(
                    id = deadLetterId(entityType, pending),
                    entityType = entityType.name,
                    entityId = pending.entityId,
                    operation = pending.operation.name,
                    retryCount = nextRetryCount,
                    lastError = errorMessage,
                    failedAt = clock.now().toEpochMilliseconds(),
                    scope = pending.scope,
                    operationId = pending.operationId,
                    expectedServerVersion = pending.expectedServerVersion,
                ),
            )
            retryScheduleStore.setNextAttemptAt(
                entityType,
                pending.entityId,
                clock.now().toEpochMilliseconds() + DEAD_LETTER_RETRY_INTERVAL_MS,
            )
            return true
        }
```

The backoff branch below it is unchanged. In `handleOutcome`, replace the `SUCCESS` and `SKIPPED` branches (lines 608-626) with:

```kotlin
            UploadOutcome.SUCCESS -> {
                onSuccess()
                settle(entityType, pending, version = 1)
            }
            UploadOutcome.SKIPPED -> settle(entityType, pending, version = 0)
```

Add below `handleOutcome`:

```kotlin
    /**
     * Takes the operation [pending] captured out of the outbox for good: delivered, acknowledged,
     * unsendable or discarded. A change queued while it was in flight is a newer operation; it stays
     * queued and is due at once. The item's dead letters go, because the phone now has what failed or
     * it was given up on purpose.
     */
    private suspend fun settle(
        entityType: EntityType,
        pending: PendingUpload,
        version: Long,
    ) {
        if (!syncMetadataService.settleIfCurrent(entityType, pending, clock.now(), version)) {
            Napier.i("${entityType.name} ${pending.entityId} changed while it was being sent; the newer change stays queued")
        }
        retryScheduleStore.clear(entityType, pending.entityId)
        removeDeadLetters(entityType, pending.entityId)
    }

    private suspend fun removeDeadLetters(
        entityType: EntityType,
        entityId: String,
    ) {
        deadLetterStore
            .list()
            .filter { it.entityType == entityType.name && it.entityId == entityId }
            .forEach { deadLetterStore.remove(it.id) }
    }

    /** The phone's form, so a record names an operation the same way on both devices. */
    private fun deadLetterId(
        entityType: EntityType,
        pending: PendingUpload,
    ): String = pending.operationId ?: "${entityType.name}:${pending.entityId}"
```

Replace `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDeadLetterMigration.kt` with:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier

/**
 * Puts captures that older versions dropped from the outbox back in it.
 *
 * Those versions gave up on an item after five failed attempts by marking it synced, which took it out
 * of the outbox and so out of backup, and recorded it under an id of the form `NOTE_<id>`. Each such
 * record is queued again with a fresh attempt budget and then removed. Records this version writes,
 * named by an operation id or `NOTE:<id>`, are left alone, so running this at every launch is safe. A
 * record whose kind this version does not know stays, and with it the sync issue it shows.
 */
class WearDeadLetterMigration(
    private val deadLetterStore: SyncDeadLetterStore,
    private val syncMetadataService: SyncMetadataService,
) {
    /** @return how many items were queued again. */
    suspend fun migrate(): Int =
        deadLetterStore
            .list()
            .filter { it.id == "${it.entityType}_${it.entityId}" }
            .count { record -> requeue(record) }

    private suspend fun requeue(record: SyncDeadLetterRecord): Boolean {
        val entityType = runCatching { EntityType.valueOf(record.entityType) }.getOrNull()
        val operation = runCatching { PendingOperation.valueOf(record.operation) }.getOrNull()
        if (entityType == null || operation == null) {
            Napier.e("Cannot queue ${record.id} again: unknown type ${record.entityType} or operation ${record.operation}")
            return false
        }
        syncMetadataService.enqueuePending(record.entityId, entityType, operation)
        deadLetterStore.remove(record.id)
        Napier.i("Queued ${record.id} again after an older version set it aside")
        return true
    }
}
```

- [ ] **Step 5: Run the tests and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:detekt :app:wear:ktlintCheck
```
Expected: BUILD SUCCESSFUL. The existing `upload gives up on an audio note the phone never acknowledges after the maximum attempts` still passes: the dead letter is recorded and the error is not retryable.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task07-dead-letters.txt <<'EOF'
fix(wear): leave captures that fail repeatedly in the backup queue

A recording or note that failed to reach the phone five times was
taken off the watch's queue as if it had been delivered, so it never
reached the phone or backup, and the watch stopped counting it as
waiting. A change made to a note while the watch was sending it could
also be taken off the queue when the send finished, or counted as one
of those failures.

Such an item now stays queued, shows as a sync issue, and is tried
again every six hours until it gets through or is discarded on
purpose. Finishing a send clears only the version that was sent, and a
failed send counts only against that version. Items an earlier version
dropped this way go back in the queue when the app starts.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDeadLetterMigration.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/AudioSyncFlowTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WearDeadLetterMigrationTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task07-dead-letters.txt
```

---

### Task 8: Clear a capture only for the version the phone stored (A7)

After Task 7, `onNoteAcknowledged` (`WearDataLayerSyncManager.kt:138-146` as of `a8615ae5d`) looks up the note's queued operation and settles it, but it still runs outside `syncLock`. An acknowledgement that lands while the same note is being uploaded clears the row before the upload's own bookkeeping runs (`handleOutcome` → AWAITING_ACK → `handleSyncFailure`); that bookkeeping then finds its operation gone and counts nothing, so the race no longer dead-letters a delivered note, but the acknowledgement and the upload still interleave. An acknowledgement also has no version: it clears whatever operation is queued, a change made after the version the phone stored or a queued DELETE included. Operation ids cannot tell these apart, because the phone never learns the watch's operation id; the note's `lastUpdated` can.

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/NoteAckPayload.kt`
- Modify (rewrite): `app/wear/src/main/kotlin/app/logdate/wear/sync/WearNoteAckHandler.kt`, `app/wear/src/main/kotlin/app/logdate/wear/sync/WearPhoneMessageHandler.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt:53-57` (`onMessageReceived`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt` (`onNoteAcknowledged`; new `isNewerThan`; import)
- Modify (rewrite): `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteAcknowledgement.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt:52-55` (`WatchNoteAcknowledger`), `:122-125`, `:146-149`, `storeIfComplete`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/AndroidWatchConnectionManager.kt` (`requestSync`)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/NoteAckPayloadTest.kt` (new)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt` (call shape of Task 7's `an acknowledgement for a note that is not queued changes nothing`; `acknowledged audio note is marked synced and its retry schedule cleared` replaced by 5 tests and a helper)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/AudioSyncFlowTest.kt` (two acknowledgement calls)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WearPhoneMessageHandlerTest.kt` (rewrite)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteIngestorTest.kt` (`RecordingAcknowledger`, 2 tests)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/MessageWatchNoteAcknowledgerTest.kt` (rewrite)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/GoogleWatchMessageSenderTest.kt` (call shape, 1 test)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/watch/AndroidWatchConnectionManagerTest.kt` (lambda shape)

**Interfaces:**
- Consumes: `settle(entityType, pending, version)`, `queuedNote(noteId)`, the `deadLetter()` test helper and `syncLock` (Task 7); `GoogleWatchMessageSender` and `WatchNodeSelector` (Task 3).
- Produces:
  - `@Serializable data class NoteAckPayload(val lastUpdated: Long) { fun encode(): ByteArray; companion object { fun of(lastUpdated: Instant): NoteAckPayload; fun decode(bytes: ByteArray): NoteAckPayload? } }`. The wire form is `{"lastUpdated":<epoch ms>}`; `decode` returns null for an empty payload and throws `IllegalArgumentException` (`SerializationException`) for an unreadable one.
  - `fun interface WearNoteAckHandler { suspend fun onNoteAcknowledged(noteId: Uuid, ack: NoteAckPayload?) }`
  - `WearPhoneMessageHandler.onMessage(path: String, data: ByteArray = ByteArray(0))`
  - `fun interface WatchMessageSender { suspend fun send(path: String, data: ByteArray): Boolean }` (a `fun interface` member cannot declare a default value)
  - `fun interface WatchNoteAcknowledger { suspend fun acknowledge(noteId: Uuid, lastUpdated: Instant) }`

**Compatibility:** an old phone sends an empty payload, and the watch clears the note without a version check, as today. An old watch ignores the payload. The DELETE guard applies to every acknowledgement: an acknowledgement only ever confirms a create, so it can never stand in for a removal the phone has not heard about.

- [ ] **Step 1: Thread the new signatures through, with an inert payload codec**

Create `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/NoteAckPayload.kt`:

```kotlin
package app.logdate.client.sync.datalayer

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class NoteAckPayload(
    val lastUpdated: Long,
) {
    fun encode(): ByteArray = ByteArray(0)

    companion object {
        fun of(lastUpdated: Instant): NoteAckPayload = NoteAckPayload(0)

        fun decode(bytes: ByteArray): NoteAckPayload? = null
    }
}
```

Replace `WearNoteAckHandler.kt` with:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.datalayer.NoteAckPayload
import kotlin.uuid.Uuid

/** Receives the phone's confirmation that a note, and its audio file, are stored on the phone. */
fun interface WearNoteAckHandler {
    /** [ack] names the version the phone stored; it is null from phones that predate versioned acknowledgements. */
    suspend fun onNoteAcknowledged(
        noteId: Uuid,
        ack: NoteAckPayload?,
    )
}
```

In `WearDataLayerSyncManager.kt`, add `import app.logdate.client.sync.datalayer.NoteAckPayload` and change the signature of `onNoteAcknowledged` to the one below. Its body, as Task 7 left it, stays:

```kotlin
    override suspend fun onNoteAcknowledged(
        noteId: Uuid,
        ack: NoteAckPayload?,
    ) {
```

In `WearPhoneMessageHandler.kt`, replace `onMessage` (lines 15-21) with:

```kotlin
    suspend fun onMessage(
        path: String,
        data: ByteArray = ByteArray(0),
    ) {
        when {
            WearAudioRequestPaths.isNoteAckPath(path) -> acknowledge(path)
            path == WearAudioRequestPaths.SYNC_REQUEST_PATH -> sendPendingChanges()
            else -> Napier.d("Unknown message path: $path")
        }
    }
```

and in `acknowledge` (line 34) change `ackHandler.onNoteAcknowledged(noteId)` to `ackHandler.onNoteAcknowledged(noteId, null)`.

In `WearDataLayerListenerService.kt`, replace `onMessageReceived` (lines 53-57) with:

```kotlin
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val data = messageEvent.data ?: ByteArray(0)
        serviceScope.launch {
            withContext(NonCancellable) { resolvePhoneMessageHandler()?.onMessage(messageEvent.path, data) }
        }
    }
```

On the phone, replace `WatchNoteAcknowledgement.kt` with the final version shown in Step 4. Only `NoteAckPayload` is inert at this point. In `WatchNoteIngestor.kt`, add `import kotlin.time.Instant` and replace `WatchNoteAcknowledger` (lines 52-55) with:

```kotlin
/** Tells the watch a note is stored on the phone, so the watch can stop retrying it. */
fun interface WatchNoteAcknowledger {
    /** [lastUpdated] is the version of the note the phone stored. */
    suspend fun acknowledge(
        noteId: Uuid,
        lastUpdated: Instant,
    )
}
```

In `onAudioBytes`, replace lines 122-125 with:

```kotlin
        val existing = notesRepository.getNoteById(noteId)
        if (existing != null) {
            acknowledger.acknowledge(noteId, existing.lastUpdated)
            return@withNoteLock
        }
```

In `receiveAudioMetadata`, replace lines 146-149 with:

```kotlin
        val existing = notesRepository.getNoteById(note.uid)
        if (existing != null) {
            acknowledger.acknowledge(note.uid, existing.lastUpdated)
            return
        }
```

and in `storeIfComplete` change `acknowledger.acknowledge(noteId)` to `acknowledger.acknowledge(noteId, note.lastUpdated)`. In `AndroidWatchConnectionManager.requestSync`, change `messageSender.send(WearAudioRequestPaths.SYNC_REQUEST_PATH)` to `messageSender.send(WearAudioRequestPaths.SYNC_REQUEST_PATH, ByteArray(0))`.

Update the tests to the new shapes:
- `AudioSyncFlowTest.kt`, in `full audio sync flow - record and sync to phone` (line 153 as of `a8615ae5d`): `syncManager.onNoteAcknowledged(noteId, null)`; in `batch upload of multiple audio notes` (line 284): `for ((id, _) in notes) syncManager.onNoteAcknowledged(id, null)`.
- `WearDataLayerSyncManagerTest.kt`, in `acknowledged audio note is marked synced and its retry schedule cleared` and in Task 7's `an acknowledgement for a note that is not queued changes nothing`: `syncManager.onNoteAcknowledged(noteId, null)`.
- `WearPhoneMessageHandlerTest.kt`: `ackHandler.onNoteAcknowledged(noteId)` becomes `ackHandler.onNoteAcknowledged(noteId, any())` in every `coEvery`/`coVerify`.
- `WatchNoteIngestorTest.kt`, `RecordingAcknowledger` (lines 382-388 as of `a8615ae5d`) becomes (add `import kotlin.time.Instant`):

```kotlin
    private class RecordingAcknowledger : WatchNoteAcknowledger {
        val acknowledged = mutableListOf<Uuid>()
        val versions = mutableListOf<Instant>()

        override suspend fun acknowledge(
            noteId: Uuid,
            lastUpdated: Instant,
        ) {
            acknowledged += noteId
            versions += lastUpdated
        }
    }
```

- `MessageWatchNoteAcknowledgerTest.kt`: replaced in Step 2.
- `GoogleWatchMessageSenderTest.kt`: every `sender.send(PATH)` becomes `sender.send(PATH, ByteArray(0))`.
- `AndroidWatchConnectionManagerTest.kt`: the lambda becomes `WatchMessageSender { path, _ -> … }`.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:compileKotlinIosSimulatorArm64 :app:wear:testDebugUnitTest && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.WatchNoteIngestorTest' --tests 'app.logdate.client.sync.GoogleWatchMessageSenderTest' --tests 'app.logdate.client.watch.*'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Write the failing tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/NoteAckPayloadTest.kt`:

```kotlin
package app.logdate.client.sync.datalayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Tests [NoteAckPayload], the version the phone names when it acknowledges a watch note.
 */
class NoteAckPayloadTest {
    @Test
    fun `a payload reads back`() {
        val ack = NoteAckPayload(lastUpdated = 1_710_000_000_123)

        assertEquals(ack, NoteAckPayload.decode(ack.encode()))
    }

    @Test
    fun `the payload is a small JSON object`() {
        val json = NoteAckPayload(lastUpdated = 1_710_000_000_123).encode().decodeToString()

        assertEquals("""{"lastUpdated":1710000000123}""", json)
    }

    @Test
    fun `an empty payload from an older phone has no version`() {
        assertNull(NoteAckPayload.decode(ByteArray(0)))
    }

    @Test
    fun `fields a newer phone adds are ignored`() {
        val payload = """{"lastUpdated":5,"backedUp":true}""".encodeToByteArray()

        assertEquals(NoteAckPayload(lastUpdated = 5), NoteAckPayload.decode(payload))
    }

    @Test
    fun `a payload that is not an acknowledgement is rejected`() {
        assertFailsWith<IllegalArgumentException> { NoteAckPayload.decode("not json".encodeToByteArray()) }
    }

    @Test
    fun `the version is the note's last update to the millisecond`() {
        val ack = NoteAckPayload.of(Instant.fromEpochMilliseconds(1_710_000_000_123))

        assertEquals(NoteAckPayload(lastUpdated = 1_710_000_000_123), ack)
    }
}
```

In `WearDataLayerSyncManagerTest`, replace the test `acknowledged audio note is marked synced and its retry schedule cleared` with the five tests below, and add imports `app.logdate.client.sync.datalayer.NoteAckPayload`, `io.mockk.coVerifyOrder` and `kotlin.time.Duration.Companion.seconds`:

```kotlin
    @Test
    fun `an acknowledgement of the version the watch has clears the note`() =
        runTest {
            givenQueuedNote(PendingOperation.CREATE)
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote
            coEvery { deadLetterStore.list() } returns listOf(deadLetter())

            syncManager.onNoteAcknowledged(noteId, NoteAckPayload(fixedTime.toEpochMilliseconds()))

            coVerify { syncMetadataService.settleIfCurrent(EntityType.NOTE, match { it.entityId == noteId.toString() }, any(), any()) }
            coVerify { retryScheduleStore.clear(EntityType.NOTE, noteId.toString()) }
            coVerify { deadLetterStore.remove("NOTE:$noteId") }
        }

    @Test
    fun `an acknowledgement of an older version leaves a newer change queued`() =
        runTest {
            givenQueuedNote(PendingOperation.UPDATE)
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote.copy(lastUpdated = fixedTime + 5.seconds)

            syncManager.onNoteAcknowledged(noteId, NoteAckPayload(fixedTime.toEpochMilliseconds()))

            coVerify(exactly = 0) { syncMetadataService.settleIfCurrent(any(), any(), any(), any()) }
        }

    @Test
    fun `an acknowledgement without a version clears the note, as with older phones`() =
        runTest {
            givenQueuedNote(PendingOperation.CREATE)
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote.copy(lastUpdated = fixedTime + 5.seconds)

            syncManager.onNoteAcknowledged(noteId, null)

            coVerify(exactly = 1) {
                syncMetadataService.settleIfCurrent(EntityType.NOTE, match { it.entityId == noteId.toString() }, any(), any())
            }
        }

    @Test
    fun `an acknowledgement never clears a queued removal`() =
        runTest {
            givenQueuedNote(PendingOperation.DELETE)

            syncManager.onNoteAcknowledged(noteId, null)

            coVerify(exactly = 0) { syncMetadataService.settleIfCurrent(any(), any(), any(), any()) }
        }

    @Test
    fun `an acknowledgement that arrives during an upload waits for the upload's own bookkeeping`() =
        runTest {
            givenQueuedNote(PendingOperation.CREATE)
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            coEvery { dataLayerClient.sendFile(any(), any()) } coAnswers {
                gate.await()
                true
            }
            val manager = fixedClockSyncManager()

            val upload = async { manager.uploadPendingChanges() }
            runCurrent()
            val ack = async { manager.onNoteAcknowledged(noteId, NoteAckPayload(fixedTime.toEpochMilliseconds())) }
            runCurrent()

            coVerify(exactly = 0) { syncMetadataService.settleIfCurrent(any(), any(), any(), any()) }
            gate.complete(Unit)
            upload.await()
            ack.await()
            coVerifyOrder {
                retryScheduleStore.setNextAttemptAt(
                    EntityType.NOTE,
                    noteId.toString(),
                    fixedTime.toEpochMilliseconds() + WearDataLayerSyncManager.ACK_TIMEOUT_MS,
                )
                syncMetadataService.settleIfCurrent(EntityType.NOTE, match { it.entityId == noteId.toString() }, any(), any())
                retryScheduleStore.clear(EntityType.NOTE, noteId.toString())
            }
        }

    private fun givenQueuedNote(operation: PendingOperation) {
        coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
            listOf(PendingUpload(noteId.toString(), operation))
    }
```

Replace `app/wear/src/test/kotlin/app/logdate/wear/sync/WearPhoneMessageHandlerTest.kt` with:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.datalayer.NoteAckPayload
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Tests [WearPhoneMessageHandler], which reacts to messages from the phone: acknowledgements that
 * a note is stored, requests to sync now, and the phone becoming reachable again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearPhoneMessageHandlerTest {
    private val ackHandler = mockk<WearNoteAckHandler>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private val handler = WearPhoneMessageHandler(ackHandler, syncManager)

    @Test
    fun `an ack names the version of the note the phone stored`() =
        runTest {
            val noteId = Uuid.random()

            handler.onMessage(WearAudioRequestPaths.noteAckPath(noteId), NoteAckPayload(1_710_000_000_123).encode())

            coVerify(exactly = 1) { ackHandler.onNoteAcknowledged(noteId, NoteAckPayload(1_710_000_000_123)) }
            confirmVerified(syncManager)
        }

    @Test
    fun `an ack from an older phone has no version`() =
        runTest {
            val noteId = Uuid.random()

            handler.onMessage(WearAudioRequestPaths.noteAckPath(noteId))

            coVerify(exactly = 1) { ackHandler.onNoteAcknowledged(noteId, null) }
        }

    @Test
    fun `an ack payload this build cannot read is handled like an older phone's`() =
        runTest {
            val noteId = Uuid.random()

            handler.onMessage(WearAudioRequestPaths.noteAckPath(noteId), "garbage".encodeToByteArray())

            coVerify(exactly = 1) { ackHandler.onNoteAcknowledged(noteId, null) }
        }

    @Test
    fun `ack message with an invalid note id is ignored`() =
        runTest {
            handler.onMessage("/logdate/notes/not-a-uuid/ack")

            confirmVerified(ackHandler)
        }

    @Test
    fun `ack message that fails to record does not propagate`() =
        runTest {
            val noteId = Uuid.random()
            coEvery { ackHandler.onNoteAcknowledged(noteId, any()) } throws IllegalStateException("database closed")

            handler.onMessage(WearAudioRequestPaths.noteAckPath(noteId))

            coVerify(exactly = 1) { ackHandler.onNoteAcknowledged(noteId, any()) }
        }

    @Test
    fun `sync request message sends pending changes`() =
        runTest {
            handler.onMessage(WearAudioRequestPaths.SYNC_REQUEST_PATH)

            coVerify(exactly = 1) { syncManager.fullSync() }
            confirmVerified(ackHandler)
        }

    @Test
    fun `unknown message path does nothing`() =
        runTest {
            handler.onMessage("/logdate/other/thing")

            confirmVerified(ackHandler, syncManager)
        }

    @Test
    fun `phone becoming reachable sends pending changes`() =
        runTest {
            handler.onPhoneReachable()

            coVerify(exactly = 1) { syncManager.fullSync() }
        }

    @Test
    fun `a failed sync does not propagate`() =
        runTest {
            coEvery { syncManager.fullSync() } throws IllegalStateException("no connection")

            handler.onPhoneReachable()

            coVerify(exactly = 1) { syncManager.fullSync() }
        }
}
```

Replace `app/android-main/src/test/kotlin/app/logdate/client/sync/MessageWatchNoteAcknowledgerTest.kt` with:

```kotlin
package app.logdate.client.sync

import app.logdate.client.sync.datalayer.NoteAckPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [MessageWatchNoteAcknowledger], which tells the watch a note is stored on the phone, and which
 * version of it.
 */
class MessageWatchNoteAcknowledgerTest {
    private val noteId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
    private val stored = Instant.fromEpochMilliseconds(1_710_000_000_123)

    @Test
    fun `acknowledging a note messages the watch on the note ack path`() =
        runTest {
            val sentPaths = mutableListOf<String>()
            val acknowledger = MessageWatchNoteAcknowledger { path, _ -> sentPaths.add(path) }

            acknowledger.acknowledge(noteId, stored)

            assertEquals(listOf("/logdate/notes/550e8400-e29b-41d4-a716-446655440000/ack"), sentPaths)
        }

    @Test
    fun `the acknowledgement names the version of the note the phone stored`() =
        runTest {
            val payloads = mutableListOf<ByteArray>()
            val acknowledger = MessageWatchNoteAcknowledger { _, data -> payloads.add(data) }

            acknowledger.acknowledge(noteId, stored)

            assertEquals(NoteAckPayload(lastUpdated = 1_710_000_000_123), NoteAckPayload.decode(payloads.single()))
        }

    @Test
    fun `an undeliverable acknowledgement does not fail the caller`() =
        runTest {
            val acknowledger = MessageWatchNoteAcknowledger { _, _ -> false }

            acknowledger.acknowledge(noteId, stored)
        }
}
```

Append to `WatchNoteIngestorTest` (before `private fun bytes`), and add `import kotlin.time.Duration.Companion.seconds`. Both pin which version is reported; they pass once Step 1 threads it through:

```kotlin
    @Test
    fun `the acknowledgement names the version of the note the phone stored`() =
        runTest {
            val note = watchAudioNote()

            ingestor.onNoteMetadata(mapper.toDataMap(note))
            ingestor.onAudioBytes(note.uid, bytes("m4a"))

            assertEquals(listOf(note.lastUpdated), acknowledger.versions)
        }

    @Test
    fun `a repeated delivery is acknowledged with the version already stored`() =
        runTest {
            val note = watchAudioNote()
            ingestor.onNoteMetadata(mapper.toDataMap(note))
            ingestor.onAudioBytes(note.uid, bytes("m4a"))

            ingestor.onNoteMetadata(mapper.toDataMap(note.copy(lastUpdated = note.lastUpdated + 5.seconds)))

            assertEquals(listOf(note.lastUpdated, note.lastUpdated), acknowledger.versions)
        }
```

Append to `GoogleWatchMessageSenderTest` (before `private fun givenWatches`), add `import io.mockk.slot` and `import kotlin.test.assertContentEquals`, and add `val PAYLOAD = """{"lastUpdated":1710000000123}""".encodeToByteArray()` to its companion object:

```kotlin
    @Test
    fun `the payload reaches each watch unchanged`() =
        runTest {
            givenWatches(watch("pixel-watch"))
            val sent = slot<ByteArray>()
            every { messageClient.sendMessage("pixel-watch", PATH, capture(sent)) } returns Tasks.forResult(1)

            sender.send(PATH, PAYLOAD)

            assertContentEquals(PAYLOAD, sent.captured)
        }
```

- [ ] **Step 3: Run the new tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.datalayer.NoteAckPayloadTest' ; ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.WearDataLayerSyncManagerTest' --tests 'app.logdate.wear.sync.WearPhoneMessageHandlerTest' ; ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.MessageWatchNoteAcknowledgerTest'
```
Expected: FAIL.
- `NoteAckPayloadTest`: round trip, JSON, unknown-field, rejection and `of` fail; empty-payload passes.
- `an acknowledgement of an older version leaves a newer change queued` and `an acknowledgement never clears a queued removal` each find `settleIfCurrent` called once.
- `an acknowledgement that arrives during an upload…` finds `settleIfCurrent` called while the upload is still blocked on the gate.
- The other two new watch tests already pass: Task 7's acknowledgement settles the queued note.
- `an ack names the version of the note the phone stored` receives `null`.
- `the acknowledgement names the version of the note the phone stored` (phone) decodes `null`.

- [ ] **Step 4: Implement the payload, apply acknowledgements under the lock, and version them on the phone**

Replace `NoteAckPayload.kt` with:

```kotlin
package app.logdate.client.sync.datalayer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * The payload of the phone's acknowledgement of a watch note: the version of the note it stored, as
 * the epoch milliseconds of the note's `lastUpdated`.
 *
 * The watch clears a note from its outbox only when its own copy is no newer than this, so an
 * acknowledgement that arrives late cannot clear a change made since. Phones that predate this payload
 * send an empty message, and watches that predate it ignore the payload.
 */
@Serializable
data class NoteAckPayload(
    val lastUpdated: Long,
) {
    fun encode(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(lastUpdated: Instant): NoteAckPayload = NoteAckPayload(lastUpdated.toEpochMilliseconds())

        /**
         * Reads an acknowledgement's payload: null for an empty one, as phones that predate versioned
         * acknowledgements send. Throws [IllegalArgumentException] for one that is not a note
         * acknowledgement.
         */
        fun decode(bytes: ByteArray): NoteAckPayload? {
            if (bytes.isEmpty()) return null
            return json.decodeFromString(serializer(), bytes.decodeToString())
        }
    }
}
```

Replace `WearPhoneMessageHandler.kt` with:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.datalayer.NoteAckPayload
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Reacts to the phone: a note acknowledgement clears it from the watch's outbox, and a sync
 * request or the phone becoming reachable again sends whatever is still waiting.
 */
class WearPhoneMessageHandler(
    private val ackHandler: WearNoteAckHandler,
    private val syncManager: SyncManager,
) {
    suspend fun onMessage(
        path: String,
        data: ByteArray = ByteArray(0),
    ) {
        when {
            WearAudioRequestPaths.isNoteAckPath(path) -> acknowledge(path, data)
            path == WearAudioRequestPaths.SYNC_REQUEST_PATH -> sendPendingChanges()
            else -> Napier.d("Unknown message path: $path")
        }
    }

    suspend fun onPhoneReachable() = sendPendingChanges()

    private suspend fun acknowledge(
        path: String,
        data: ByteArray,
    ) {
        val noteId =
            try {
                WearAudioRequestPaths.noteIdFromAckPath(path)
            } catch (e: IllegalArgumentException) {
                Napier.w("Invalid note ID in ack path: $path", e)
                return
            }
        try {
            ackHandler.onNoteAcknowledged(noteId, readAck(noteId, data))
        } catch (e: Exception) {
            Napier.w("Failed to record the phone's acknowledgement of note $noteId", e)
        }
    }

    /**
     * The phone sends an acknowledgement only once it has stored the note, so a payload this build
     * cannot read is handled like the empty one older phones send, and logged as the error it is.
     */
    private fun readAck(
        noteId: Uuid,
        data: ByteArray,
    ): NoteAckPayload? =
        try {
            NoteAckPayload.decode(data)
        } catch (e: IllegalArgumentException) {
            Napier.e("Unreadable acknowledgement payload for note $noteId; treating it as unversioned", e)
            null
        }

    private suspend fun sendPendingChanges() {
        try {
            syncManager.fullSync()
        } catch (e: Exception) {
            Napier.w("Failed to send pending changes to the phone", e)
        }
    }
}
```

In `WearDataLayerSyncManager.kt`, replace `onNoteAcknowledged` (Task 7's `queuedNote` below it stays) with the following, and add `isNewerThan` below `queuedNote`:

```kotlin
    /**
     * Clears a note the phone confirmed. It runs under the same lock as uploads, so a confirmation can
     * neither land in the middle of an upload's own bookkeeping nor clear a version newer than the one
     * the phone stored. A queued removal is never cleared by the confirmation of a create, and a
     * confirmation without a version, from an older phone, clears the note as before.
     */
    override suspend fun onNoteAcknowledged(
        noteId: Uuid,
        ack: NoteAckPayload?,
    ) = syncLock.withLock {
        val pending = queuedNote(noteId)
        if (pending == null) {
            Napier.d("Note $noteId was confirmed but is no longer queued")
            return@withLock
        }
        if (pending.operation == PendingOperation.DELETE) {
            Napier.i("Note $noteId was confirmed, but its removal has not reached the phone yet; the removal stays queued")
            return@withLock
        }
        if (ack != null && isNewerThan(noteId, ack)) {
            Napier.i("Note $noteId changed after the version the phone confirmed; it stays queued")
            return@withLock
        }
        settle(EntityType.NOTE, pending, version = 1)
    }

    private suspend fun isNewerThan(
        noteId: Uuid,
        ack: NoteAckPayload,
    ): Boolean {
        val local = notesRepository.getNoteById(noteId) ?: return false
        return local.lastUpdated.toEpochMilliseconds() > ack.lastUpdated
    }
```

Replace `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteAcknowledgement.kt` with:

```kotlin
package app.logdate.client.sync

import android.content.Context
import app.logdate.client.sync.datalayer.NoteAckPayload
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.client.watch.WatchNodeSelector
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Delivers a message to the watches running LogDate. Returns false when no watch received it. */
fun interface WatchMessageSender {
    suspend fun send(
        path: String,
        data: ByteArray,
    ): Boolean
}

/**
 * Tells the watch a note is stored on the phone, and which version: the watch clears the note only
 * if it has not changed since. Watches that predate versioned acknowledgements ignore the payload.
 */
class MessageWatchNoteAcknowledger(
    private val sender: WatchMessageSender,
) : WatchNoteAcknowledger {
    override suspend fun acknowledge(
        noteId: Uuid,
        lastUpdated: Instant,
    ) {
        val payload = NoteAckPayload.of(lastUpdated).encode()
        if (!sender.send(WearAudioRequestPaths.noteAckPath(noteId), payload)) {
            Napier.w("Watch did not receive the acknowledgement for note $noteId; it will retry")
        }
    }
}

/**
 * Messages every reachable watch running LogDate, each on its own, so a watch that cannot be reached
 * does not stop the message reaching another.
 */
class GoogleWatchMessageSender(
    private val watchNodeSelector: WatchNodeSelector,
    private val messageClient: MessageClient,
) : WatchMessageSender {
    constructor(context: Context) : this(
        watchNodeSelector = WatchNodeSelector(Wearable.getCapabilityClient(context)),
        messageClient = Wearable.getMessageClient(context),
    )

    override suspend fun send(
        path: String,
        data: ByteArray,
    ): Boolean {
        val watches =
            try {
                watchNodeSelector.reachableWatches()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Could not look up reachable watches for $path", e)
                return false
            }
        if (watches.isEmpty()) {
            Napier.w("No reachable watch runs LogDate; $path was not sent")
            return false
        }
        return watches.count { watch -> deliver(watch, path, data) } > 0
    }

    private suspend fun deliver(
        watch: Node,
        path: String,
        data: ByteArray,
    ): Boolean =
        try {
            messageClient.sendMessage(watch.id, path, data).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not message watch ${watch.displayName} at $path", e)
            false
        }
}
```

- [ ] **Step 5: Run the tests, the iOS compile and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:ktlintCheck && ./gradlew :app:wear:testDebugUnitTest :app:wear:detekt :app:wear:ktlintCheck && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.*' --tests 'app.logdate.client.watch.*' && ./gradlew :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck
```
Expected: BUILD SUCCESSFUL. `AudioSyncFlowTest` still passes: its acknowledgements find the note queued as a CREATE.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task08-versioned-acks.txt <<'EOF'
fix(wear): clear a capture only for the version the phone stored

A confirmation from the phone could clear a watch note from the backup
queue while that same note was being sent again. The send was then
counted as a failure, and enough of those listed a delivered note as a
sync issue. A late confirmation could also clear a newer change to the
note, or the note's pending removal, so the phone never heard about
either.

The phone now says which version of the note it stored. The watch
applies a confirmation only once no send is in progress, only to that
version or an older one, and never to a pending removal.
Confirmations from older phones, which name no version, are applied as
before.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/NoteAckPayload.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/NoteAckPayloadTest.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearNoteAckHandler.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearPhoneMessageHandler.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/AudioSyncFlowTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WearPhoneMessageHandlerTest.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteAcknowledgement.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/AndroidWatchConnectionManager.kt app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteIngestorTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/MessageWatchNoteAcknowledgerTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/GoogleWatchMessageSenderTest.kt app/android-main/src/test/kotlin/app/logdate/client/watch/AndroidWatchConnectionManagerTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task08-versioned-acks.txt
```

---

### Task 9: Back up journals and journal links made on the watch (A8)

`PhoneDataLayerListenerService` applies watch journals with `SyncableJournalRepository.createFromSync`/`deleteFromSync` (`:280-321`) and watch links with `SyncableJournalContentRepository.*FromSync` (`:323-367`). The phone binds only `JournalContentRepository`, so `koin.getOrNull<SyncableJournalContentRepository>()` is null, and links go straight to `JournalContentDao`. None of these paths calls `SyncMetadataService.enqueuePending`, so a watch journal or link never reaches the cloud. The enqueuing calls are the ones the phone's own edits use: `JournalRepository.create/update/delete` (`OfflineFirstJournalRepository.kt:67-114`) and `JournalContentRepository.addContentToJournal/removeContentFromJournal` (`OfflineFirstJournalContentRepository.kt:60-91`). `JournalDao.create` is a plain `@Insert` that fails on a duplicate, so the ingestor checks for the journal first.

Since `78faf0960` each of those repository calls goes through `SyncMetadataService.enqueuePending`, which writes the row with a fresh operation id and coalesces it with whatever is already queued for the item (`PendingOperation.coalesce`: a create after a queued delete becomes a create, a delete after a queued create stays a delete), so the ingestor needs nothing beyond the repository calls. What it does need to carry is the phone's `syncVersion`: the uploader sends a journal UPDATE with `pending.expectedServerVersion ?: journal.syncVersion` as an exact version check (`SyncUploader.kt:194-206`, `CloudJournalDataSource.kt:132-144`), and since `8f25cd326` the server rejects any other version. A watch copy carries whatever version the watch last saw, so an update copies the phone's own `syncVersion` into it before saving.

**Files:**
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchJournalIngestor.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt` (lines as of `a8615ae5d`; Task 4 has moved the later ones): `:3-12` (imports), `:212-221` (`resolveSyncDependencies`), `:237-240` (`processSnapshotEvent` branches), `:280-367` (four handlers), `:454-460` (`PhoneDataLayerSyncDependencies`)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt:174-183` (new binding after `WatchNoteIngestor`, and its import)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchJournalIngestorTest.kt` (new)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/PhoneDataLayerListenerServiceTest.kt` (as of `a8615ae5d`, before Task 4 added three tests: journal and link tests at `:395-537`, helpers at `:636-668`, imports)

**Interfaces:**
- Consumes: `JournalRepository.getJournalById(id: Uuid): Journal?`, `create(journal: Journal): Uuid`, `update(journal: Journal)`, `delete(journalId: Uuid)`; `JournalContentRepository.addContentToJournal(contentId: Uuid, journalId: Uuid)`, `removeContentFromJournal(contentId: Uuid, journalId: Uuid)`; `JournalContentDao.isContentInJournal(journalId: Uuid, contentId: Uuid): Boolean`.
- Produces:
  - `class WatchJournalIngestor(journalRepository: JournalRepository, contentRepository: JournalContentRepository, journalContentDao: JournalContentDao)` with `suspend fun onJournal(journal: Journal)`, `suspend fun onJournalDeleted(journalId: Uuid)`, `suspend fun onAssociation(journalId: Uuid, contentId: Uuid)`, `suspend fun onAssociationDeleted(journalId: Uuid, contentId: Uuid)`
  - `data class PhoneDataLayerSyncDependencies(noteIngestor: WatchNoteIngestor, journalIngestor: WatchJournalIngestor, healthSnapshotDao: HealthSnapshotDao)`

- [ ] **Step 1: Add the ingestor with inert bodies and wire it in**

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchJournalIngestor.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import kotlin.uuid.Uuid

class WatchJournalIngestor(
    private val journalRepository: JournalRepository,
    private val contentRepository: JournalContentRepository,
    private val journalContentDao: JournalContentDao,
) {
    suspend fun onJournal(journal: Journal) = Unit

    suspend fun onJournalDeleted(journalId: Uuid) = Unit

    suspend fun onAssociation(
        journalId: Uuid,
        contentId: Uuid,
    ) = Unit

    suspend fun onAssociationDeleted(
        journalId: Uuid,
        contentId: Uuid,
    ) = Unit
}
```

In `AppModule.android.kt`, add `import app.logdate.client.sync.WatchJournalIngestor` and, after the `WatchNoteIngestor` binding (line 183), add:

```kotlin
        single {
            WatchJournalIngestor(
                journalRepository = get(),
                contentRepository = get(),
                journalContentDao = get(),
            )
        }
```

In `PhoneDataLayerListenerService.kt`, replace `PhoneDataLayerSyncDependencies` (lines 454-460) with:

```kotlin
data class PhoneDataLayerSyncDependencies(
    val noteIngestor: WatchNoteIngestor,
    val journalIngestor: WatchJournalIngestor,
    val healthSnapshotDao: HealthSnapshotDao,
)
```

and `resolveSyncDependencies` (lines 212-221) with:

```kotlin
    protected open suspend fun resolveSyncDependencies(): PhoneDataLayerSyncDependencies? {
        val koin = resolveKoin() ?: return null
        return PhoneDataLayerSyncDependencies(
            noteIngestor = koin.get(),
            journalIngestor = koin.get(),
            healthSnapshotDao = koin.get(),
        )
    }
```

Replace the four journal and association branches of `processSnapshotEvent` (lines 237-240) with:

```kotlin
                JournalDataMapper.isDeletePath(path) -> handleJournalDelete(path, dependencies.journalIngestor)
                JournalDataMapper.isJournalPath(path) -> handleJournalSync(event, path, dependencies.journalIngestor)
                AssociationDataMapper.isDeletePath(path) -> handleAssociationDelete(path, dependencies.journalIngestor)
                AssociationDataMapper.isAssociationPath(path) -> handleAssociationSync(event, path, dependencies.journalIngestor)
```

and the four handlers (lines 280-367) with:

```kotlin
    private suspend fun handleJournalDelete(
        path: String,
        ingestor: WatchJournalIngestor,
    ) {
        val journalId =
            try {
                JournalDataMapper.journalIdFromPath(path)
            } catch (e: IllegalArgumentException) {
                Napier.w("Invalid journal ID in delete path: $path", e)
                return
            }

        Napier.d("Received delete signal from watch for journal: $journalId")
        try {
            ingestor.onJournalDeleted(journalId)
        } catch (e: Exception) {
            Napier.w("Failed to delete synced journal from watch: $journalId", e)
        }
    }

    private suspend fun handleJournalSync(
        event: PhoneDataLayerSnapshotEvent,
        path: String,
        ingestor: WatchJournalIngestor,
    ) {
        try {
            val journal = journalDataMapper.fromDataMap(event.data)
            Napier.d("Received journal from watch: ${journal.id}")
            ingestor.onJournal(journal)
        } catch (e: Exception) {
            Napier.w("Failed to process synced journal from watch at path: $path", e)
        }
    }

    private suspend fun handleAssociationDelete(
        path: String,
        ingestor: WatchJournalIngestor,
    ) {
        val (journalId, contentId) =
            try {
                AssociationDataMapper.idsFromPath(path)
            } catch (e: Exception) {
                Napier.w("Invalid association path: $path", e)
                return
            }

        Napier.d("Received association delete from watch: journal=$journalId, content=$contentId")
        try {
            ingestor.onAssociationDeleted(journalId, contentId)
        } catch (e: Exception) {
            Napier.w("Failed to delete synced association: $path", e)
        }
    }

    private suspend fun handleAssociationSync(
        event: PhoneDataLayerSnapshotEvent,
        path: String,
        ingestor: WatchJournalIngestor,
    ) {
        try {
            val (journalId, contentId) = associationDataMapper.fromDataMap(event.data)
            Napier.d("Received association from watch: journal=$journalId, content=$contentId")
            ingestor.onAssociation(journalId, contentId)
        } catch (e: Exception) {
            Napier.w("Failed to process synced association from watch at path: $path", e)
        }
    }
```

Remove the now-unused imports `JournalContentDao`, `JournalContentEntityLink`, `JournalRepository`, `SyncableJournalContentRepository` and `SyncableJournalRepository`. In the class KDoc, replace lines 42-43 with:

```kotlin
 * them for cloud backup once they are complete and tells the watch they arrived. Journals and
 * associations go through [WatchJournalIngestor], which queues them for cloud backup too.
```

In `PhoneDataLayerListenerServiceTest.kt`, replace the nine tests from `journal sync uses sync-aware repository` through `invalid association delete path does not touch content dependencies` (lines 395-537 as of `a8615ae5d`) with the five below. The four that pinned the non-syncable repository and DAO fallbacks go with the fallbacks; what replaces them, the repository calls that queue a backup, is pinned in `WatchJournalIngestorTest` (Step 2):

```kotlin
    @Test
    fun `a journal from the watch goes to the journal ingestor`() =
        runTest {
            val journal = Journal(id = Uuid.random(), title = "Travel")
            val journalIngestor = mockk<WatchJournalIngestor>(relaxed = true)
            val service = serviceWithDependencies(journalIngestor = journalIngestor)

            service.processEvents(journalEvent(journal))

            coVerify(exactly = 1) { journalIngestor.onJournal(journal) }
        }

    @Test
    fun `a journal deletion from the watch goes to the journal ingestor`() =
        runTest {
            val journalId = Uuid.random()
            val journalIngestor = mockk<WatchJournalIngestor>(relaxed = true)
            val service = serviceWithDependencies(journalIngestor = journalIngestor)

            service.processEvents(journalDeleteEvent(journalId))

            coVerify(exactly = 1) { journalIngestor.onJournalDeleted(journalId) }
        }

    @Test
    fun `a journal link from the watch goes to the journal ingestor`() =
        runTest {
            val journalId = Uuid.random()
            val contentId = Uuid.random()
            val journalIngestor = mockk<WatchJournalIngestor>(relaxed = true)
            val service = serviceWithDependencies(journalIngestor = journalIngestor)

            service.processEvents(associationEvent(journalId, contentId))

            coVerify(exactly = 1) { journalIngestor.onAssociation(journalId, contentId) }
        }

    @Test
    fun `a journal link removal from the watch goes to the journal ingestor`() =
        runTest {
            val journalId = Uuid.random()
            val contentId = Uuid.random()
            val journalIngestor = mockk<WatchJournalIngestor>(relaxed = true)
            val service = serviceWithDependencies(journalIngestor = journalIngestor)

            service.processEvents(associationDeleteEvent(journalId, contentId))

            coVerify(exactly = 1) { journalIngestor.onAssociationDeleted(journalId, contentId) }
        }

    @Test
    fun `an invalid link removal path does not reach the journal ingestor`() =
        runTest {
            val journalIngestor = mockk<WatchJournalIngestor>(relaxed = true)
            val service = serviceWithDependencies(journalIngestor = journalIngestor)

            service.processEvents(PhoneDataLayerSnapshotEvent(path = "/logdate/associations/not-valid/delete", data = emptyMap()))

            confirmVerified(journalIngestor)
        }
```

Replace the helpers `serviceWithDependencies` and `dependencies` (lines 636-668 as of `a8615ae5d`) with:

```kotlin
    private fun serviceWithDependencies(
        noteIngestor: WatchNoteIngestor = mockk(relaxed = true),
        journalIngestor: WatchJournalIngestor = mockk(relaxed = true),
        healthSnapshotDao: HealthSnapshotDao = mockk(relaxed = true),
    ): TestPhoneDataLayerListenerService =
        TestPhoneDataLayerListenerService(
            serviceScope = CoroutineScope(Dispatchers.Unconfined),
            syncDependencies =
                dependencies(
                    noteIngestor = noteIngestor,
                    journalIngestor = journalIngestor,
                    healthSnapshotDao = healthSnapshotDao,
                ),
        )

    private fun dependencies(
        noteIngestor: WatchNoteIngestor = mockk(relaxed = true),
        journalIngestor: WatchJournalIngestor = mockk(relaxed = true),
        healthSnapshotDao: HealthSnapshotDao = mockk(relaxed = true),
    ): PhoneDataLayerSyncDependencies =
        PhoneDataLayerSyncDependencies(
            noteIngestor = noteIngestor,
            journalIngestor = journalIngestor,
            healthSnapshotDao = healthSnapshotDao,
        )
```

and remove the unused imports `JournalContentDao`, `JournalContentEntityLink`, `JournalRepository`, `SyncableJournalContentRepository` and `SyncableJournalRepository`.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.PhoneDataLayerListenerServiceTest'
```
Expected: BUILD SUCCESSFUL. The listener delegates; the ingestor does nothing yet.

- [ ] **Step 2: Write the failing ingestor tests**

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchJournalIngestorTest.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [WatchJournalIngestor], which applies journals and journal links from the watch through the
 * phone's own repository calls, so each one is queued for cloud backup.
 */
class WatchJournalIngestorTest {
    private val journalRepository = mockk<JournalRepository>(relaxed = true)
    private val contentRepository = mockk<JournalContentRepository>(relaxed = true)
    private val journalContentDao = mockk<JournalContentDao>(relaxed = true)
    private val ingestor = WatchJournalIngestor(journalRepository, contentRepository, journalContentDao)

    private val journalId = Uuid.parse("770e8400-e29b-41d4-a716-446655440000")
    private val noteId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
    private val earlier = Instant.fromEpochMilliseconds(1_710_000_000_000)
    private val later = Instant.fromEpochMilliseconds(1_710_000_060_000)

    @Test
    fun `a new journal from the watch is created and queued for backup`() =
        runTest {
            val journal = Journal(id = journalId, title = "Walks", created = earlier, lastUpdated = earlier)
            coEvery { journalRepository.getJournalById(journalId) } returns null

            ingestor.onJournal(journal)

            coVerify(exactly = 1) { journalRepository.create(journal) }
        }

    @Test
    fun `a newer copy of a journal updates it and queues the update`() =
        runTest {
            coEvery { journalRepository.getJournalById(journalId) } returns
                Journal(id = journalId, title = "Walks", created = earlier, lastUpdated = earlier, syncVersion = 7)
            val incoming = Journal(id = journalId, title = "Evening walks", created = earlier, lastUpdated = later)

            ingestor.onJournal(incoming)

            coVerify(exactly = 1) { journalRepository.update(incoming.copy(syncVersion = 7)) }
        }

    @Test
    fun `a repeated or older copy of a journal changes nothing`() =
        runTest {
            val existing = Journal(id = journalId, title = "Walks", created = earlier, lastUpdated = later)
            coEvery { journalRepository.getJournalById(journalId) } returns existing

            ingestor.onJournal(existing)
            ingestor.onJournal(existing.copy(title = "Old title", lastUpdated = earlier))

            coVerify(exactly = 0) { journalRepository.create(any()) }
            coVerify(exactly = 0) { journalRepository.update(any()) }
        }

    @Test
    fun `a journal deleted on the watch is deleted and the deletion queued`() =
        runTest {
            coEvery { journalRepository.getJournalById(journalId) } returns
                Journal(id = journalId, created = earlier, lastUpdated = earlier)

            ingestor.onJournalDeleted(journalId)

            coVerify(exactly = 1) { journalRepository.delete(journalId) }
        }

    @Test
    fun `deleting a journal the phone does not have changes nothing`() =
        runTest {
            coEvery { journalRepository.getJournalById(journalId) } returns null

            ingestor.onJournalDeleted(journalId)

            coVerify(exactly = 0) { journalRepository.delete(any()) }
        }

    @Test
    fun `a new link from the watch is added and queued for backup`() =
        runTest {
            coEvery { journalContentDao.isContentInJournal(journalId, noteId) } returns false

            ingestor.onAssociation(journalId, noteId)

            coVerify(exactly = 1) { contentRepository.addContentToJournal(noteId, journalId) }
        }

    @Test
    fun `a link the phone already has changes nothing`() =
        runTest {
            coEvery { journalContentDao.isContentInJournal(journalId, noteId) } returns true

            ingestor.onAssociation(journalId, noteId)

            coVerify(exactly = 0) { contentRepository.addContentToJournal(any(), any()) }
        }

    @Test
    fun `a link removed on the watch is removed and the removal queued`() =
        runTest {
            coEvery { journalContentDao.isContentInJournal(journalId, noteId) } returns true

            ingestor.onAssociationDeleted(journalId, noteId)

            coVerify(exactly = 1) { contentRepository.removeContentFromJournal(noteId, journalId) }
        }

    @Test
    fun `removing a link the phone does not have changes nothing`() =
        runTest {
            coEvery { journalContentDao.isContentInJournal(journalId, noteId) } returns false

            ingestor.onAssociationDeleted(journalId, noteId)

            coVerify(exactly = 0) { contentRepository.removeContentFromJournal(any(), any()) }
        }
}
```

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.WatchJournalIngestorTest'
```
Expected: FAIL. The five tests that expect a repository call (`…is created…`, `…updates it…`, `…is deleted…`, `…is added…`, `…is removed…`) find none. The four "changes nothing" tests already pass.

- [ ] **Step 4: Implement the ingestor**

Replace `WatchJournalIngestor.kt` with:

```kotlin
package app.logdate.client.sync

import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import kotlin.uuid.Uuid

/**
 * Applies journals and journal links that arrive from the watch through the same repository calls
 * the phone's own edits use, so each one is queued for cloud backup.
 *
 * Every method is safe to repeat: the Data Layer can deliver an item more than once, and a repeat
 * finds the change already made and queues nothing. A journal copy no newer than the phone's own is
 * ignored, so a late delivery cannot undo an edit made on the phone. A newer copy is saved with the
 * phone's own `syncVersion`, because the upload sends that version as an exact check and the
 * server refuses any other.
 */
class WatchJournalIngestor(
    private val journalRepository: JournalRepository,
    private val contentRepository: JournalContentRepository,
    private val journalContentDao: JournalContentDao,
) {
    suspend fun onJournal(journal: Journal) {
        val existing = journalRepository.getJournalById(journal.id)
        if (existing == null) {
            journalRepository.create(journal)
            return
        }
        if (journal.lastUpdated <= existing.lastUpdated) return
        journalRepository.update(journal.copy(syncVersion = existing.syncVersion))
    }

    suspend fun onJournalDeleted(journalId: Uuid) {
        if (journalRepository.getJournalById(journalId) == null) return
        journalRepository.delete(journalId)
    }

    suspend fun onAssociation(
        journalId: Uuid,
        contentId: Uuid,
    ) {
        if (journalContentDao.isContentInJournal(journalId, contentId)) return
        contentRepository.addContentToJournal(contentId, journalId)
    }

    suspend fun onAssociationDeleted(
        journalId: Uuid,
        contentId: Uuid,
    ) {
        if (!journalContentDao.isContentInJournal(journalId, contentId)) return
        contentRepository.removeContentFromJournal(contentId, journalId)
    }
}
```

- [ ] **Step 5: Run the tests and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.*' && ./gradlew :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task09-watch-journals.txt <<'EOF'
fix(sync): back up journals and journal links made on the watch

A journal created, renamed or deleted on the watch, or a note added to
or removed from a journal there, changed the phone but never reached
cloud backup. The phone applied those changes the way it applies
changes downloaded from the cloud, which are not queued for upload.

The phone now applies them the way it applies its own edits, so each
one is queued for backup. A repeated delivery, or a copy no newer than
the phone's own, changes nothing and queues nothing.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchJournalIngestor.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt app/android-main/src/test/kotlin/app/logdate/client/sync/WatchJournalIngestorTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/PhoneDataLayerListenerServiceTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task09-watch-journals.txt
```

---

### Task 10: Update sync status as it changes (A9)

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/PhoneReachability.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearSyncStatus.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt` (imports; constructor; `isSyncing`/`_syncStatusFlow` at `:67-87`; `uploadPendingChanges` `:93`; `syncContent` `:153-160`; `syncJournals` `:162`; `syncAssociations` `:200`; `fullSync`/`fullSyncLocked` `:247-277`; `getSyncStatus` `:279-292`; companion; new `runExclusively`)
- Modify (rewrite): `app/wear/src/main/kotlin/app/logdate/wear/presentation/home/WearHomeViewModel.kt`, `app/wear/src/main/kotlin/app/logdate/wear/presentation/settings/WearSettingsViewModel.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/settings/WearSettingsScreen.kt:43-55` (the polling observer; `d69aea766` moved it up from lines 62-75)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt:77-98`, `app/wear/src/main/kotlin/app/logdate/wear/di/AudioModule.kt:162-168, 211-218`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/CapabilityPhoneReachabilityTest.kt` (new)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt` (fields, `setUp`, `fixedClockSyncManager`, 2 tests and a helper)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/AudioSyncFlowTest.kt` (constructor)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/presentation/home/WearHomeViewModelTest.kt` (new)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/presentation/settings/WearSettingsViewModelTest.kt` (rewrite)

**Interfaces:**
- Consumes: `SyncMetadataService.observePendingCount(): Flow<Int>`, `SyncMetadataService.getLastSyncTime(EntityType.NOTE)`, `SyncDeadLetterStore.observe(): Flow<List<SyncDeadLetterRecord>>`, `WearDataLayerClient.getConnectedPhoneName(): String?`, `CapabilityClient.addListener(listener, capability: String): Task<Void>`, `CapabilityClient.removeListener(listener, capability: String): Task<Boolean>`, `LocationTrackingSettingsRepository.observeSettings(): Flow<LocationTrackingSettings>`, Koin's `CoroutineScope` from `deviceInstanceModule` (`CoroutineScope(SupervisorJob + Dispatchers.Default)`).
- Produces:
  - `data class ReachablePhone(val displayName: String)`
  - `fun interface PhoneReachability { fun observe(): Flow<ReachablePhone?> }`
  - `class CapabilityPhoneReachability(capabilityClient: CapabilityClient, dataLayerClient: WearDataLayerClient) : PhoneReachability`
  - `internal fun wearSyncStatus(running: Flow<Boolean>, syncMetadataService: SyncMetadataService, deadLetterStore: SyncDeadLetterStore, phoneReachability: PhoneReachability): Flow<SyncStatus>`
  - `WearDataLayerSyncManager(…, healthSnapshotDataMapper, phoneReachability: PhoneReachability, scope: CoroutineScope, audioDigester = FileAudioDigester(), clock = Clock.System)`. `syncStatusFlow` is live (`SharingStarted.WhileSubscribed(5_000)`); `isSyncing` is true while a run has `syncLock`.
  - `WearHomeViewModel(notesRepository: JournalNotesRepository, syncManager: SyncManager)` and `internal fun syncBadgeFor(status: SyncStatus): SyncBadge`
  - `WearSettingsViewModel(syncManager: SyncManager, phoneReachability: PhoneReachability, locationSettingsRepository, permissionManager, locationTrackingManager)` with `fun onResume()`; `startPolling`/`stopPolling` are removed.

- [ ] **Step 1: Add the new types and signatures with inert bodies**

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/PhoneReachability.kt`:

```kotlin
package app.logdate.wear.sync

import com.google.android.gms.wearable.CapabilityClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class ReachablePhone(
    val displayName: String,
)

fun interface PhoneReachability {
    fun observe(): Flow<ReachablePhone?>
}

class CapabilityPhoneReachability(
    private val capabilityClient: CapabilityClient,
    private val dataLayerClient: WearDataLayerClient,
) : PhoneReachability {
    override fun observe(): Flow<ReachablePhone?> = emptyFlow()
}
```

In `WearDataLayerSyncManager.kt`, add `import kotlinx.coroutines.CoroutineScope` and two constructor parameters after `private val healthSnapshotDataMapper: HealthSnapshotDataMapper,`:

```kotlin
    phoneReachability: PhoneReachability,
    scope: CoroutineScope,
```

In `WearDataModule.kt`, add imports `android.content.Context`, `com.google.android.gms.wearable.Wearable`, `app.logdate.wear.sync.CapabilityPhoneReachability` and `app.logdate.wear.sync.PhoneReachability`. Bind the reachability before the sync manager, and pass both new arguments after `healthSnapshotDataMapper = get(),`:

```kotlin
        single<PhoneReachability> { CapabilityPhoneReachability(Wearable.getCapabilityClient(get<Context>()), get()) }
```

```kotlin
                phoneReachability = get(),
                scope = get(),
```

Replace `WearHomeViewModel.kt` with an inert version:

```kotlin
package app.logdate.wear.presentation.home

import androidx.lifecycle.ViewModel
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WearHomeViewModel(
    notesRepository: JournalNotesRepository,
    syncManager: SyncManager,
) : ViewModel() {
    val uiState: StateFlow<WearHomeUiState> = MutableStateFlow(WearHomeUiState())
}

enum class TimeOfDay {
    MORNING,
    AFTERNOON,
    EVENING,
}

enum class SyncBadge {
    NONE,
    SYNCING,
    ERROR,
}

data class WearHomeUiState(
    val greeting: String = "",
    val entryCount: Int = 0,
    val entryCountLabel: String = "",
    val syncBadge: SyncBadge = SyncBadge.NONE,
)
```

Replace the class in `WearSettingsViewModel.kt` with an inert version. `WearSettingsUiState` below it is unchanged:

```kotlin
package app.logdate.wear.presentation.settings

import androidx.lifecycle.ViewModel
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.location.tracking.LocationTrackingManager
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.sync.SyncManager
import app.logdate.wear.sync.PhoneReachability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

class WearSettingsViewModel(
    syncManager: SyncManager,
    phoneReachability: PhoneReachability,
    private val locationSettingsRepository: LocationTrackingSettingsRepository,
    private val permissionManager: PermissionManager,
    private val locationTrackingManager: LocationTrackingManager,
) : ViewModel() {
    val uiState: StateFlow<WearSettingsUiState> = MutableStateFlow(WearSettingsUiState())

    fun onResume() = Unit

    fun syncNow() = Unit

    fun setBackgroundLocationEnabled(enabled: Boolean) = Unit

    fun setLocationCaptureMode(mode: LocationCaptureMode) = Unit

    fun openLocationPermissions() = Unit
}
```

In `WearSettingsScreen.kt`, replace lines 43-55 (the polling lifecycle observer, from `val lifecycleOwner = LocalLifecycleOwner.current` through its `DisposableEffect`'s closing `}`) with:

```kotlin
    // The location permission can change in system settings while this screen is away.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
```

In `AudioModule.kt`, replace the `WearHomeViewModel` binding (lines 162-168) and the `WearSettingsViewModel` binding (lines 211-218), and add `import app.logdate.wear.sync.PhoneReachability`:

```kotlin
        viewModel {
            WearHomeViewModel(
                get<JournalNotesRepository>(),
                get<SyncManager>(),
            )
        }
```

```kotlin
        viewModel {
            WearSettingsViewModel(
                get<SyncManager>(),
                get<PhoneReachability>(),
                get(),
                get(),
                get(),
            )
        }
```

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL. Test sources are updated in Step 2.

- [ ] **Step 2: Write the failing tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/CapabilityPhoneReachabilityTest.kt`:

```kotlin
package app.logdate.wear.sync

import app.cash.turbine.test
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests [CapabilityPhoneReachability], which follows the phone coming into and going out of reach.
 */
class CapabilityPhoneReachabilityTest {
    private val capabilityClient = mockk<CapabilityClient>()
    private val dataLayerClient = mockk<WearDataLayerClient>()
    private val listener = slot<CapabilityClient.OnCapabilityChangedListener>()
    private val reachability = CapabilityPhoneReachability(capabilityClient, dataLayerClient)

    init {
        every { capabilityClient.addListener(capture(listener), WearDataLayerClient.PHONE_CAPABILITY) } returns
            Tasks.forResult<Void>(null)
        every { capabilityClient.removeListener(any(), WearDataLayerClient.PHONE_CAPABILITY) } returns Tasks.forResult(true)
    }

    @Test
    fun `the phone in reach is reported at once`() =
        runTest {
            coEvery { dataLayerClient.getConnectedPhoneName() } returns "Pixel 9"

            reachability.observe().test {
                assertEquals(ReachablePhone("Pixel 9"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the phone leaving and coming back is followed without polling`() =
        runTest {
            coEvery { dataLayerClient.getConnectedPhoneName() } returnsMany listOf("Pixel 9", null, "Pixel 9")

            reachability.observe().test {
                assertEquals(ReachablePhone("Pixel 9"), awaitItem())
                listener.captured.onCapabilityChanged(mockk<CapabilityInfo>())
                assertNull(awaitItem())
                listener.captured.onCapabilityChanged(mockk<CapabilityInfo>())
                assertEquals(ReachablePhone("Pixel 9"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `stopping observation stops listening`() =
        runTest {
            coEvery { dataLayerClient.getConnectedPhoneName() } returns null

            reachability.observe().test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            verify { capabilityClient.removeListener(listener.captured, WearDataLayerClient.PHONE_CAPABILITY) }
        }

    @Test
    fun `reachability is still reported when listening cannot start`() =
        runTest {
            every { capabilityClient.addListener(any(), WearDataLayerClient.PHONE_CAPABILITY) } returns
                Tasks.forException(IllegalStateException("API_UNAVAILABLE"))
            coEvery { dataLayerClient.getConnectedPhoneName() } returns "Pixel 9"

            reachability.observe().test {
                assertEquals(ReachablePhone("Pixel 9"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            verify(exactly = 0) { capabilityClient.removeListener(any(), any<String>()) }
        }
}
```

In `WearDataLayerSyncManagerTest.kt`, add imports `app.cash.turbine.test`, `kotlinx.coroutines.CoroutineScope` and `kotlinx.coroutines.test.UnconfinedTestDispatcher`, and add these fields after `private val noteId = …`:

```kotlin
    private val pendingCount = MutableStateFlow(0)
    private val deadLetters = MutableStateFlow<List<SyncDeadLetterRecord>>(emptyList())
    private val phone = MutableStateFlow<ReachablePhone?>(ReachablePhone("Pixel 9"))
```

In `setUp`, replace `every { syncMetadataService.observePendingCount() } returns MutableStateFlow(0)` with these three stubs:

```kotlin
        every { syncMetadataService.observePendingCount() } returns pendingCount
        every { deadLetterStore.observe() } returns deadLetters
        coEvery { syncMetadataService.getLastSyncTime(EntityType.NOTE) } returns null
```

In both constructor calls (the one in `setUp` and the one in `fixedClockSyncManager`), add after `healthSnapshotDataMapper = healthSnapshotDataMapper,`:

```kotlin
                phoneReachability = PhoneReachability { phone },
                scope = CoroutineScope(UnconfinedTestDispatcher()),
```

Append before the final `}`:

```kotlin
    // =======================================================================
    // Live status
    // =======================================================================

    @Test
    fun `status follows the outbox, set-aside items and the phone without polling`() =
        runTest {
            val manager = statusManager(backgroundScope)

            manager.syncStatusFlow.test {
                assertFalse(awaitItem().isEnabled)
                assertTrue(awaitItem().isEnabled)

                pendingCount.value = 2
                assertEquals(2, awaitItem().pendingUploads)

                phone.value = null
                assertFalse(awaitItem().isEnabled)

                deadLetters.value = listOf(deadLetter())
                assertTrue(awaitItem().hasErrors)
            }
            coVerify(exactly = 0) { dataLayerClient.isPhoneConnected(any()) }
            coVerify(exactly = 0) { syncMetadataService.getPendingCount() }
        }

    @Test
    fun `status shows a run while it is in progress`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(PendingUpload(noteId.toString(), PendingOperation.CREATE))
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            coEvery { dataLayerClient.sendFile(any(), any()) } coAnswers {
                gate.await()
                true
            }
            val manager = statusManager(backgroundScope)

            manager.syncStatusFlow.test {
                awaitItem()
                assertFalse(awaitItem().isSyncing)

                val run = async { manager.fullSync() }
                assertTrue(awaitItem().isSyncing)

                gate.complete(Unit)
                run.await()
                assertFalse(awaitItem().isSyncing)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun statusManager(scope: CoroutineScope): WearDataLayerSyncManager =
        WearDataLayerSyncManager(
            dataLayerClient = dataLayerClient,
            syncMetadataService = syncMetadataService,
            retryScheduleStore = retryScheduleStore,
            deadLetterStore = deadLetterStore,
            notesRepository = notesRepository,
            journalRepository = journalRepository,
            healthSnapshotDao = healthSnapshotDao,
            noteDataMapper = noteDataMapper,
            journalDataMapper = journalDataMapper,
            associationDataMapper = associationDataMapper,
            healthSnapshotDataMapper = healthSnapshotDataMapper,
            phoneReachability = PhoneReachability { phone },
            scope = scope,
        )
```

The first item each test awaits is the idle status a new subscriber starts from; the second is the first combined status.

In `AudioSyncFlowTest.kt`, add imports `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.flow.flowOf` and `kotlinx.coroutines.test.UnconfinedTestDispatcher`, and add to the constructor call in `setUp`, after `healthSnapshotDataMapper = HealthSnapshotDataMapper(),`:

```kotlin
                phoneReachability = PhoneReachability { flowOf(ReachablePhone("Pixel 9")) },
                scope = CoroutineScope(UnconfinedTestDispatcher()),
```

Create `app/wear/src/test/kotlin/app/logdate/wear/presentation/home/WearHomeViewModelTest.kt`:

```kotlin
package app.logdate.wear.presentation.home

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncStatus
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests the sync badge of [WearHomeViewModel], which follows the sync status as it changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearHomeViewModelTest {
    private val status =
        MutableStateFlow(
            SyncStatus(isEnabled = false, lastSyncTime = null, pendingUploads = 0, isSyncing = false, hasErrors = false),
        )
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { notesRepository.observeNotesForDay(any()) } returns flowOf(emptyList())
        every { syncManager.syncStatusFlow } returns status
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the badge follows the sync status as it changes`() =
        runTest {
            val viewModel = WearHomeViewModel(notesRepository, syncManager)

            viewModel.uiState.test {
                assertEquals(SyncBadge.NONE, expectMostRecentItem().syncBadge)

                status.value = status.value.copy(isEnabled = true, pendingUploads = 2)
                assertEquals(SyncBadge.SYNCING, awaitItem().syncBadge)

                status.value = status.value.copy(hasErrors = true)
                assertEquals(SyncBadge.ERROR, awaitItem().syncBadge)

                status.value = status.value.copy(hasErrors = false, pendingUploads = 0)
                assertEquals(SyncBadge.NONE, awaitItem().syncBadge)
            }
        }

    @Test
    fun `items waiting with no phone in reach show no badge`() =
        runTest {
            status.value = status.value.copy(isEnabled = false, pendingUploads = 3)
            val viewModel = WearHomeViewModel(notesRepository, syncManager)

            viewModel.uiState.test {
                assertEquals(SyncBadge.NONE, expectMostRecentItem().syncBadge)
            }
        }

    @Test
    fun `the sync status is never polled`() =
        runTest {
            val viewModel = WearHomeViewModel(notesRepository, syncManager)

            viewModel.uiState.test { expectMostRecentItem() }

            coVerify(exactly = 0) { syncManager.getSyncStatus() }
        }
}
```

Replace `app/wear/src/test/kotlin/app/logdate/wear/presentation/settings/WearSettingsViewModelTest.kt` with the version below. Each of today's ten tests has a counterpart that checks the same state, now fed by flows instead of a poll: the two initial-state tests have the same names, the phone name, pending count and error tests become the two flow tests, and the `syncNow`, location and permission-settings action tests make the same checks.

```kotlin
package app.logdate.wear.presentation.settings

import app.cash.turbine.test
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.location.tracking.LocationTrackingManager
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.wear.sync.PhoneReachability
import app.logdate.wear.sync.ReachablePhone
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Tests [WearSettingsViewModel]. The phone, the sync status and the location settings are followed as
 * they change; only the location permission is read again, on resume.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearSettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val phone = MutableStateFlow<ReachablePhone?>(null)
    private val status =
        MutableStateFlow(
            SyncStatus(isEnabled = false, lastSyncTime = null, pendingUploads = 0, isSyncing = false, hasErrors = false),
        )
    private val locationSettings = MutableStateFlow(LocationTrackingSettings())
    private lateinit var syncManager: SyncManager
    private lateinit var locationSettingsRepository: LocationTrackingSettingsRepository
    private lateinit var permissionManager: PermissionManager
    private lateinit var locationTrackingManager: LocationTrackingManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        syncManager = mockk(relaxed = true)
        locationSettingsRepository = mockk(relaxed = true)
        permissionManager = mockk(relaxed = true)
        locationTrackingManager = mockk(relaxed = true)
        every { syncManager.syncStatusFlow } returns status
        every { locationSettingsRepository.observeSettings() } returns locationSettings
        coEvery { locationSettingsRepository.setBackgroundTrackingEnabled(any()) } returns Unit
        coEvery { locationSettingsRepository.setCaptureMode(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WearSettingsViewModel =
        WearSettingsViewModel(
            syncManager,
            PhoneReachability { phone },
            locationSettingsRepository,
            permissionManager,
            locationTrackingManager,
        )

    @Test
    fun `initial state is disconnected with no sync info`() =
        runTest {
            createViewModel().uiState.test {
                val state = expectMostRecentItem()
                assertFalse(state.isPhoneConnected)
                assertNull(state.phoneName)
                assertNull(state.lastSyncTime)
                assertEquals(0, state.pendingCount)
                assertFalse(state.hasErrors)
                assertFalse(state.isSyncingNow)
                assertTrue(state.autoTagJournalEntries)
                assertFalse(state.backgroundLocationEnabled)
                assertEquals(LocationCaptureMode.PASSIVE, state.locationCaptureMode)
            }
        }

    @Test
    fun `initial state reflects location settings and foreground permission`() =
        runTest {
            locationSettings.value =
                LocationTrackingSettings(
                    backgroundTrackingEnabled = true,
                    captureMode = LocationCaptureMode.ACTIVE,
                    autoTrackForJournalEntries = true,
                )
            every { permissionManager.isPermissionGranted(PermissionType.LOCATION) } returns true

            createViewModel().uiState.test {
                val state = expectMostRecentItem()
                assertTrue(state.locationPermissionGranted)
                assertTrue(state.backgroundLocationEnabled)
                assertEquals(LocationCaptureMode.ACTIVE, state.locationCaptureMode)
                assertTrue(state.autoTagJournalEntries)
            }
        }

    @Test
    fun `the phone's name follows the phone coming and going`() =
        runTest {
            createViewModel().uiState.test {
                assertFalse(expectMostRecentItem().isPhoneConnected)

                phone.value = ReachablePhone("Pixel 9")
                val connected = awaitItem()
                assertTrue(connected.isPhoneConnected)
                assertEquals("Pixel 9", connected.phoneName)

                phone.value = null
                assertFalse(awaitItem().isPhoneConnected)
            }
        }

    @Test
    fun `waiting items, errors and the last sync follow the sync status`() =
        runTest {
            val lastSync = Instant.fromEpochMilliseconds(1_710_000_000_000)
            createViewModel().uiState.test {
                expectMostRecentItem()

                status.value = status.value.copy(lastSyncTime = lastSync, pendingUploads = 3, hasErrors = true)
                val state = awaitItem()

                assertEquals(lastSync, state.lastSyncTime)
                assertEquals(3, state.pendingCount)
                assertTrue(state.hasErrors)
            }
        }

    @Test
    fun `location settings follow the repository`() =
        runTest {
            createViewModel().uiState.test {
                expectMostRecentItem()

                locationSettings.value =
                    LocationTrackingSettings(
                        backgroundTrackingEnabled = true,
                        captureMode = LocationCaptureMode.ACTIVE,
                        autoTrackForJournalEntries = true,
                    )
                val state = awaitItem()

                assertTrue(state.backgroundLocationEnabled)
                assertEquals(LocationCaptureMode.ACTIVE, state.locationCaptureMode)
            }
        }

    @Test
    fun `the location permission is read again on resume`() =
        runTest {
            every { permissionManager.isPermissionGranted(PermissionType.LOCATION) } returns false
            val viewModel = createViewModel()

            viewModel.uiState.test {
                assertFalse(expectMostRecentItem().locationPermissionGranted)

                every { permissionManager.isPermissionGranted(PermissionType.LOCATION) } returns true
                viewModel.onResume()

                assertTrue(awaitItem().locationPermissionGranted)
            }
        }

    @Test
    fun `syncNow runs a full sync and shows it while it runs`() =
        runTest {
            val gate = CompletableDeferred<SyncResult>()
            coEvery { syncManager.fullSync() } coAnswers { gate.await() }
            val viewModel = createViewModel()

            viewModel.uiState.test {
                expectMostRecentItem()

                viewModel.syncNow()
                assertTrue(awaitItem().isSyncingNow)

                gate.complete(SyncResult(success = true))
                assertFalse(awaitItem().isSyncingNow)
            }
            coVerify(exactly = 1) { syncManager.fullSync() }
        }

    @Test
    fun `setBackgroundLocationEnabled persists the setting and reapplies tracking`() =
        runTest {
            createViewModel().setBackgroundLocationEnabled(true)

            coVerify { locationSettingsRepository.setBackgroundTrackingEnabled(true) }
            verify { locationTrackingManager.startTracking() }
        }

    @Test
    fun `setLocationCaptureMode persists the mode and reapplies tracking`() =
        runTest {
            createViewModel().setLocationCaptureMode(LocationCaptureMode.ACTIVE)

            coVerify { locationSettingsRepository.setCaptureMode(LocationCaptureMode.ACTIVE) }
            verify { locationTrackingManager.startTracking() }
        }

    @Test
    fun `openLocationPermissions opens app permission settings`() =
        runTest {
            createViewModel().openLocationPermissions()

            verify { permissionManager.openPermissionSettings() }
        }

    @Test
    fun `nothing is polled`() =
        runTest {
            createViewModel().uiState.test { expectMostRecentItem() }

            coVerify(exactly = 0) { syncManager.getSyncStatus() }
            coVerify(exactly = 0) { locationSettingsRepository.getSettings() }
        }
}
```

- [ ] **Step 3: Run the new tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.CapabilityPhoneReachabilityTest' --tests 'app.logdate.wear.sync.WearDataLayerSyncManagerTest' --tests 'app.logdate.wear.presentation.home.WearHomeViewModelTest' --tests 'app.logdate.wear.presentation.settings.WearSettingsViewModelTest'
```
Expected: FAIL.
- `CapabilityPhoneReachabilityTest`: `Expected item but found Complete`.
- The two status tests time out after the idle item (`No value produced in 3s`).
- `the badge follows the sync status as it changes`, the Settings flow tests and `syncNow…` time out, and the three Settings action tests find no calls.
- The "never polled", "no badge", "initial state" and `openLocationPermissions` checks that are already true pass.

- [ ] **Step 4: Implement reachability, the live status and the collecting view models**

Replace `app/wear/src/main/kotlin/app/logdate/wear/sync/PhoneReachability.kt` with:

```kotlin
package app.logdate.wear.sync

import com.google.android.gms.wearable.CapabilityClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** The phone running LogDate that the watch can reach right now. */
data class ReachablePhone(
    val displayName: String,
)

/** Follows whether a phone running LogDate is in reach, so status can change without polling. */
fun interface PhoneReachability {
    /** Emits the reachable phone, or null when none is, at once and again whenever that changes. */
    fun observe(): Flow<ReachablePhone?>
}

/**
 * [PhoneReachability] from the Data Layer: Play services announces each change to
 * [WearDataLayerClient.PHONE_CAPABILITY], and the phone is looked up again on each one.
 */
class CapabilityPhoneReachability(
    private val capabilityClient: CapabilityClient,
    private val dataLayerClient: WearDataLayerClient,
) : PhoneReachability {
    override fun observe(): Flow<ReachablePhone?> =
        callbackFlow {
            val listener = CapabilityClient.OnCapabilityChangedListener { launch { send(currentPhone()) } }
            val listening = startListening(listener)
            send(currentPhone())
            awaitClose {
                if (listening) capabilityClient.removeListener(listener, WearDataLayerClient.PHONE_CAPABILITY)
            }
        }.distinctUntilChanged()

    private suspend fun startListening(listener: CapabilityClient.OnCapabilityChangedListener): Boolean =
        try {
            capabilityClient.addListener(listener, WearDataLayerClient.PHONE_CAPABILITY).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not follow the phone coming and going; reachability is reported as of now only", e)
            false
        }

    private suspend fun currentPhone(): ReachablePhone? = dataLayerClient.getConnectedPhoneName()?.let(::ReachablePhone)
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/WearSyncStatus.kt`:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The watch's sync status as it changes: whether a phone is in reach, how much is waiting, whether a
 * run is in progress, when the last one finished, and whether anything has been set aside.
 *
 * Every input is a flow, so a capture joining the outbox, an acknowledgement clearing it, a run
 * starting or finishing, an item being set aside, and the phone coming or going each change the
 * status as they happen.
 */
internal fun wearSyncStatus(
    running: Flow<Boolean>,
    syncMetadataService: SyncMetadataService,
    deadLetterStore: SyncDeadLetterStore,
    phoneReachability: PhoneReachability,
): Flow<SyncStatus> =
    combine(
        running,
        syncMetadataService.observePendingCount(),
        deadLetterStore.observe(),
        phoneReachability.observe(),
    ) { isSyncing, pendingUploads, deadLetters, phone ->
        SyncStatus(
            isEnabled = phone != null,
            lastSyncTime = syncMetadataService.getLastSyncTime(EntityType.NOTE),
            pendingUploads = pendingUploads,
            isSyncing = isSyncing,
            hasErrors = deadLetters.isNotEmpty(),
        )
    }
```

In `WearDataLayerSyncManager.kt`:

Imports: add `kotlinx.coroutines.flow.SharingStarted` and `kotlinx.coroutines.flow.stateIn`, and remove `kotlinx.coroutines.flow.asStateFlow`.

Replace lines 67-93 as of `a8615ae5d` (from `@Volatile` through `override suspend fun uploadPendingChanges()…`; Tasks 5 and 7 have moved them down a few lines) with:

```kotlin
    /** True while a run has [syncLock]; the status follows it. */
    private val running = MutableStateFlow(false)

    /**
     * Sync entry points can overlap: a reconnect, a phone request, the repository's debounce and the
     * Sync now button all start one. Running them together would put the same audio note on the wire
     * twice, so each runs alone.
     */
    private val syncLock = Mutex()

    override val syncStatusFlow: StateFlow<SyncStatus> =
        wearSyncStatus(running, syncMetadataService, deadLetterStore, phoneReachability)
            .stateIn(scope, SharingStarted.WhileSubscribed(STATUS_SUBSCRIPTION_TIMEOUT_MS), IDLE_STATUS)

    override fun sync(startNow: Boolean) {
        Napier.d("Wear sync requested (startNow=$startNow)")
    }

    override suspend fun uploadPendingChanges(): SyncResult = runExclusively { uploadPendingChangesLocked() }
```

Replace `syncContent` (lines 153-160) with:

```kotlin
    override suspend fun syncContent(): SyncResult = uploadPendingChanges()
```

Change `syncJournals` (line 162) to `override suspend fun syncJournals(): SyncResult = runExclusively { syncJournalsLocked() }`, `syncAssociations` (line 200) to `override suspend fun syncAssociations(): SyncResult = runExclusively { syncAssociationsLocked() }`, and `fullSync` (line 247) to `override suspend fun fullSync(): SyncResult = runExclusively { fullSyncLocked() }`.

Replace `fullSyncLocked` (lines 249-277) with:

```kotlin
    private suspend fun fullSyncLocked(): SyncResult {
        // Phase 1: Journals first (parents)
        val journalsResult = syncJournalsLocked()
        // Phase 2: Notes second (children of journals)
        val notesResult = uploadPendingChangesLocked()
        // Phase 3: Associations third (links between journals and notes)
        val associationsResult = syncAssociationsLocked()
        // Phase 4: Health snapshots last (reference notes)
        val healthResult = syncHealthSnapshots()

        val totalUploaded =
            journalsResult.uploadedItems + notesResult.uploadedItems +
                associationsResult.uploadedItems + healthResult.uploadedItems
        val allErrors =
            journalsResult.errors + notesResult.errors +
                associationsResult.errors + healthResult.errors

        return SyncResult(
            success = allErrors.isEmpty(),
            uploadedItems = totalUploaded,
            errors = allErrors,
            lastSyncTime = clock.now(),
        )
    }

    /** Runs [block] alone under [syncLock], with the status showing a run in progress meanwhile. */
    private suspend fun <T> runExclusively(block: suspend () -> T): T =
        syncLock.withLock {
            running.value = true
            try {
                block()
            } finally {
                running.value = false
            }
        }
```

In `getSyncStatus` (lines 279-292), change `isSyncing = isSyncing,` to `isSyncing = running.value,`. Add to the companion object:

```kotlin
        /** How long the status goes on following its sources after the last screen stops collecting it. */
        private const val STATUS_SUBSCRIPTION_TIMEOUT_MS = 5_000L

        private val IDLE_STATUS =
            SyncStatus(isEnabled = false, lastSyncTime = null, pendingUploads = 0, isSyncing = false, hasErrors = false)
```

Replace `WearHomeViewModel.kt` with the version below. The greeting and entry-count text are the same as in today's file; they are hard-coded English although `wear_home_greeting_*` resources exist, and moving them to resources belongs to the home screen work in Track B, which replaces this view model, not to this sync fix:

```kotlin
package app.logdate.wear.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class WearHomeViewModel(
    notesRepository: JournalNotesRepository,
    syncManager: SyncManager,
) : ViewModel() {
    private val today: LocalDate
        get() =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date

    val uiState: StateFlow<WearHomeUiState> =
        combine(
            notesRepository.observeNotesForDay(today).map { it.size },
            syncManager.syncStatusFlow.map(::syncBadgeFor),
        ) { entryCount, syncBadge ->
            WearHomeUiState(
                greeting =
                    when (currentTimeOfDay()) {
                        TimeOfDay.MORNING -> "Good morning"
                        TimeOfDay.AFTERNOON -> "Good afternoon"
                        TimeOfDay.EVENING -> "Good evening"
                    },
                entryCount = entryCount,
                entryCountLabel =
                    when (entryCount) {
                        0 -> "No entries yet"
                        1 -> "1 entry today"
                        else -> "$entryCount entries today"
                    },
                syncBadge = syncBadge,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WearHomeUiState(),
        )

    private fun currentTimeOfDay(): TimeOfDay {
        val hour =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .hour
        return when {
            hour < 12 -> TimeOfDay.MORNING
            hour < 17 -> TimeOfDay.AFTERNOON
            else -> TimeOfDay.EVENING
        }
    }
}

/** What the home screen says about sync: nothing while it is healthy. */
internal fun syncBadgeFor(status: SyncStatus): SyncBadge =
    when {
        status.hasErrors -> SyncBadge.ERROR
        status.isEnabled && status.pendingUploads > 0 -> SyncBadge.SYNCING
        else -> SyncBadge.NONE
    }

enum class TimeOfDay {
    MORNING,
    AFTERNOON,
    EVENING,
}

enum class SyncBadge {
    NONE,
    SYNCING,
    ERROR,
}

data class WearHomeUiState(
    val greeting: String = "",
    val entryCount: Int = 0,
    val entryCountLabel: String = "",
    val syncBadge: SyncBadge = SyncBadge.NONE,
)
```

Replace the class in `WearSettingsViewModel.kt` (the `WearSettingsUiState` data class stays as it is) with:

```kotlin
package app.logdate.wear.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.location.tracking.LocationTrackingManager
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import app.logdate.client.sync.SyncManager
import app.logdate.wear.sync.PhoneReachability
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * ViewModel backing the Wear OS Settings screen.
 *
 * The phone, the sync status and the location settings are each followed as they change. Only the
 * location permission is read again, on resume, because it can change in system settings without
 * any event reaching the app.
 */
class WearSettingsViewModel(
    private val syncManager: SyncManager,
    phoneReachability: PhoneReachability,
    private val locationSettingsRepository: LocationTrackingSettingsRepository,
    private val permissionManager: PermissionManager,
    private val locationTrackingManager: LocationTrackingManager,
) : ViewModel() {
    private val locationPermissionGranted = MutableStateFlow(permissionManager.isPermissionGranted(PermissionType.LOCATION))
    private val isSyncingNow = MutableStateFlow(false)

    val uiState: StateFlow<WearSettingsUiState> =
        combine(
            phoneReachability.observe(),
            syncManager.syncStatusFlow,
            locationSettingsRepository.observeSettings(),
            locationPermissionGranted,
            isSyncingNow,
        ) { phone, status, location, permissionGranted, syncingNow ->
            WearSettingsUiState(
                isPhoneConnected = phone != null,
                phoneName = phone?.displayName,
                lastSyncTime = status.lastSyncTime,
                pendingCount = status.pendingUploads,
                hasErrors = status.hasErrors,
                isSyncingNow = syncingNow,
                locationPermissionGranted = permissionGranted,
                autoTagJournalEntries = location.autoTrackForJournalEntries,
                backgroundLocationEnabled = location.backgroundTrackingEnabled,
                locationCaptureMode = location.captureMode,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WearSettingsUiState())

    /** Reads the location permission again; it may have changed in system settings. */
    fun onResume() {
        locationPermissionGranted.value = permissionManager.isPermissionGranted(PermissionType.LOCATION)
    }

    fun syncNow() {
        viewModelScope.launch {
            isSyncingNow.value = true
            try {
                syncManager.fullSync()
            } catch (e: Exception) {
                Napier.w("Sync Now failed", e)
            } finally {
                isSyncingNow.value = false
            }
        }
    }

    fun setBackgroundLocationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                locationSettingsRepository.setBackgroundTrackingEnabled(enabled)
                locationTrackingManager.startTracking()
            } catch (e: Exception) {
                Napier.w("Failed to update Wear background location setting", e)
            }
        }
    }

    fun setLocationCaptureMode(mode: LocationCaptureMode) {
        viewModelScope.launch {
            try {
                locationSettingsRepository.setCaptureMode(mode)
                locationTrackingManager.startTracking()
            } catch (e: Exception) {
                Napier.w("Failed to update Wear location capture mode", e)
            }
        }
    }

    fun openLocationPermissions() {
        permissionManager.openPermissionSettings()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 5: Run the tests and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:detekt :app:wear:ktlintCheck :app:wear:validateDebugScreenshotTest
```
Expected: BUILD SUCCESSFUL. The screenshot tests render `WearHomeContent` and the settings content from state, so they are unchanged.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task10-live-status.txt <<'EOF'
fix(wear): update sync status as it changes

The watch's home screen showed a sync badge up to 30 seconds old, and
Settings showed the phone, the count of waiting items and sync problems
as of its last check, checking again every five seconds while it was
open. The status the sync engine reports was set once at startup and
never changed.

The status now changes the moment a capture is queued, the phone
confirms one, a sync starts or ends, an item is set aside, or the phone
comes into or goes out of reach. Both screens follow it instead of
checking on a timer.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/sync/PhoneReachability.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearSyncStatus.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/home/WearHomeViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/settings/WearSettingsViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/settings/WearSettingsScreen.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt app/wear/src/main/kotlin/app/logdate/wear/di/AudioModule.kt app/wear/src/test/kotlin/app/logdate/wear/sync/CapabilityPhoneReachabilityTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/AudioSyncFlowTest.kt app/wear/src/test/kotlin/app/logdate/wear/presentation/home/WearHomeViewModelTest.kt app/wear/src/test/kotlin/app/logdate/wear/presentation/settings/WearSettingsViewModelTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task10-live-status.txt
```

---

### Task 11: Wake the watch when a sync retry is due (A10)

`SyncRetryScheduleStore` records when each outbox item may be tried again: 30 s after an audio note the phone has not confirmed, 5 s doubling to 5 min after a failure, and 6 h for a dead letter (Task 7). Nothing wakes the app at those times. Today a retry happens only when something else starts a sync: opening the app, the repositories' debounce after a write, the phone's sync request, the phone coming back into reach, or Sync now.

**Files:**
- Modify: `app/wear/build.gradle.kts:184-187` (implementation), `:199-204` (androidTest)
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncScheduler.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncWorker.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt` (bindings after the `WearDeadLetterMigration` binding from Task 7)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt` (`startSync()` from Task 7)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WatchSyncSchedulerTest.kt` (new, unit)
- Test: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchSyncWorkerTest.kt` (new, Gradle Managed Device)

**Interfaces:**
- Consumes: `SyncManager.fullSync(): SyncResult` and `SyncManager.syncStatusFlow: StateFlow<SyncStatus>`, which Task 10 made live, with `isSyncing` true while a run has the sync lock; `SyncMetadataService.getPendingUploads(entityType: EntityType): List<PendingUpload>`; `SyncRetryScheduleStore.nextAttemptAt(entityType: EntityType, entityId: String): Long?`; `WearDataLayerClient.isPhoneConnected(capability: String = PHONE_CAPABILITY): Boolean`; `WearDeadLetterMigration` and `LogDateWearApplication.startSync()` from Task 7; `libs.androidx.work.runtime` (`work-runtime-ktx`) and `libs.androidx.work.testing` from `gradle/libs.versions.toml:338-339`.
- Produces:
  - `fun interface WatchSyncWork { fun runAfter(delayMs: Long) }`
  - `class WatchSyncScheduler(work: WatchSyncWork, syncStatus: Flow<SyncStatus>, syncMetadataService: SyncMetadataService, retryScheduleStore: SyncRetryScheduleStore, dataLayerClient: WearDataLayerClient, clock: Clock = Clock.System) { fun start(scope: CoroutineScope); suspend fun scheduleNext() }`
  - `class WatchSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker, KoinComponent` with `companion object { const val UNIQUE_WORK_NAME = "watch_sync" }`
  - `class WorkManagerWatchSyncWork(workManager: WorkManager) : WatchSyncWork`: one-time unique work `watch_sync`, `ExistingWorkPolicy.REPLACE`, `setInitialDelay(delayMs)`

**Design notes:**
- The next run is set for the earliest stored retry time among the outbox items the watch uploads (`JOURNAL`, `NOTE`, `ASSOCIATION`), or for now when an item has never been attempted. `WearDataLayerSyncManager` uploads nothing else, and a `DRAFT` or `MEDIA` row would otherwise schedule a run every time, forever.
- After a run, every operation it attempted has either left the outbox or has a retry time in the future, so a run never schedules another for "now" unless something new arrived. A change queued while its item was in flight counts as new: Task 7 leaves it queued and due at once.
- The scheduler follows the sync status, which changes at app start, when the phone comes or goes, when the outbox or the dead letters change, and when a run starts or ends. It acts 2 s after the status settles, and never while a run is in progress: replacing the waiting work would stop a running worker and let the watch sleep mid-upload. The run's end changes the status again, which schedules the next one.
- The worker sets the next run itself before returning, while the watch is still awake for this work. A follow-up left to the status collector could wait until the watch next wakes. That call replaces the finished worker in WorkManager, which is harmless because nothing follows it.
- The worker runs `fullSync()` in `NonCancellable`, so a run stopped part way through (a replacement that races its start, or a system stop) still finishes the upload it began.
- With no phone in reach nothing is scheduled. A run would only find no phone, and the phone's return starts a sync by itself (the listener's `CAPABILITY_CHANGED` filter, and the status changing here).
- A run that throws returns `Result.retry()` (WorkManager's backoff) instead of scheduling itself again at once.
- `:app:wear` removes `RECEIVE_BOOT_COMPLETED` (`AndroidManifest.xml:27`) to leave it off the Play permission list, so after a reboot WorkManager reschedules this work when the app process next starts: any Data Layer event, tile, complication or launch. Restoring the permission belongs to the reminders work.
- WorkManager initializes through `androidx.startup`; the watch manifest removes only the Emoji2 initializer (`AndroidManifest.xml:52-62`).

- [ ] **Step 1: Add WorkManager, the scheduler and the worker with inert bodies, and wire them**

In `app/wear/build.gradle.kts`, after `implementation(libs.media3.session)` (line 187), add:

```kotlin

    // WorkManager wakes the watch when a sync retry is due
    implementation(libs.androidx.work.runtime)
```

and after `androidTestImplementation(libs.kotlin.test.junit)` (line 204), add:

```kotlin
    androidTestImplementation(libs.androidx.work.testing)
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncScheduler.kt`:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

fun interface WatchSyncWork {
    fun runAfter(delayMs: Long)
}

class WatchSyncScheduler(
    private val work: WatchSyncWork,
    private val syncStatus: Flow<SyncStatus>,
    private val syncMetadataService: SyncMetadataService,
    private val retryScheduleStore: SyncRetryScheduleStore,
    private val dataLayerClient: WearDataLayerClient,
    private val clock: Clock = Clock.System,
) {
    fun start(scope: CoroutineScope) = Unit

    suspend fun scheduleNext() = Unit
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncWorker.kt`:

```kotlin
package app.logdate.wear.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class WatchSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.failure()

    companion object {
        const val UNIQUE_WORK_NAME = "watch_sync"
    }
}

class WorkManagerWatchSyncWork(
    private val workManager: WorkManager,
) : WatchSyncWork {
    override fun runAfter(delayMs: Long) = Unit
}
```

In `WearDataModule.kt`, add imports `androidx.work.WorkManager`, `app.logdate.wear.sync.WatchSyncScheduler`, `app.logdate.wear.sync.WatchSyncWork` and `app.logdate.wear.sync.WorkManagerWatchSyncWork` (`android.content.Context` is imported since Task 10, and `app.logdate.client.sync.SyncManager` already was), and add after the `WearDeadLetterMigration` binding:

```kotlin
        single<WatchSyncWork> { WorkManagerWatchSyncWork(WorkManager.getInstance(get<Context>())) }
        single {
            WatchSyncScheduler(
                work = get(),
                syncStatus = get<SyncManager>().syncStatusFlow,
                syncMetadataService = get(),
                retryScheduleStore = get(),
                dataLayerClient = get(),
            )
        }
```

In `LogDateWearApplication.kt`, add `import app.logdate.wear.sync.WatchSyncScheduler` and replace `startSync()` with:

```kotlin
    /**
     * Puts captures an older version dropped after repeated failures back in the outbox, then wakes
     * the watch whenever something in it is due.
     */
    private fun startSync() {
        val koin =
            org.koin.java.KoinJavaComponent
                .getKoin()
        koin.get<CoroutineScope>().launch {
            requeueSetAsideCaptures(koin.get())
            koin.get<WatchSyncScheduler>().start(this)
        }
    }
```

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Write the failing tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/WatchSyncSchedulerTest.kt`:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [WatchSyncScheduler], which wakes the watch when an outbox item is next due.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchSyncSchedulerTest {
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val scheduled = mutableListOf<Long>()
    private val syncMetadataService = mockk<SyncMetadataService>(relaxed = true)
    private val retryScheduleStore = mockk<SyncRetryScheduleStore>(relaxed = true)
    private val dataLayerClient = mockk<WearDataLayerClient>(relaxed = true)
    private val idle = SyncStatus(isEnabled = true, lastSyncTime = null, pendingUploads = 0, isSyncing = false, hasErrors = false)
    private val status = MutableStateFlow(idle)

    init {
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns true
        coEvery { syncMetadataService.getPendingUploads(any()) } returns emptyList()
        coEvery { retryScheduleStore.nextAttemptAt(any(), any()) } returns null
    }

    @Test
    fun `nothing waiting schedules nothing`() =
        runTest {
            scheduler().scheduleNext()

            assertEquals(emptyList(), scheduled)
        }

    @Test
    fun `with no phone in reach nothing is scheduled`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false

            scheduler().scheduleNext()

            assertEquals(emptyList(), scheduled)
        }

    @Test
    fun `an item never attempted is due now`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")

            scheduler().scheduleNext()

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `the earliest retry time across journals, notes and links decides the run`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1", retryAt = now.toEpochMilliseconds() + 30_000)
            givenWaiting(EntityType.ASSOCIATION, "journal-1::note-2", retryAt = now.toEpochMilliseconds() + 5_000)
            givenWaiting(EntityType.JOURNAL, "journal-1", retryAt = now.toEpochMilliseconds() + 60_000)

            scheduler().scheduleNext()

            assertEquals(listOf(5_000L), scheduled)
        }

    @Test
    fun `a retry time that has passed is due now`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1", retryAt = now.toEpochMilliseconds() - 1_000)

            scheduler().scheduleNext()

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `outbox items the watch does not upload are ignored`() =
        runTest {
            givenWaiting(EntityType.DRAFT, "draft-1")

            scheduler().scheduleNext()

            assertEquals(emptyList(), scheduled)
        }

    @Test
    fun `starting schedules what is already waiting once the status settles`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")

            scheduler().start(backgroundScope)
            advanceTimeBy(2_001)

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `the phone coming into reach schedules a run`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
            status.value = idle.copy(isEnabled = false, pendingUploads = 1)
            scheduler().start(backgroundScope)
            advanceTimeBy(2_001)
            assertEquals(emptyList(), scheduled)

            coEvery { dataLayerClient.isPhoneConnected(any()) } returns true
            status.value = idle.copy(pendingUploads = 1)
            advanceTimeBy(2_001)

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `changes during a run wait for the run to finish`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")
            status.value = idle.copy(isSyncing = true, pendingUploads = 2)
            scheduler().start(backgroundScope)
            status.value = idle.copy(isSyncing = true, pendingUploads = 1)
            advanceTimeBy(10_000)
            assertEquals(emptyList(), scheduled)

            status.value = idle.copy(pendingUploads = 1)
            advanceTimeBy(2_001)

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `a burst of changes schedules one run once the status settles`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")
            scheduler().start(backgroundScope)
            runCurrent()

            status.value = idle.copy(pendingUploads = 1)
            advanceTimeBy(1_000)
            status.value = idle.copy(pendingUploads = 2)
            advanceTimeBy(1_000)
            status.value = idle.copy(pendingUploads = 3)
            advanceTimeBy(2_001)

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `a sync status that cannot be read stops scheduling without failing the app`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")

            scheduler(syncStatus = flow<SyncStatus> { throw IllegalStateException("database closed") }).start(backgroundScope)
            advanceTimeBy(2_001)

            assertEquals(emptyList(), scheduled)
        }

    private fun scheduler(syncStatus: Flow<SyncStatus> = status): WatchSyncScheduler =
        WatchSyncScheduler(
            work = WatchSyncWork { delayMs -> scheduled += delayMs },
            syncStatus = syncStatus,
            syncMetadataService = syncMetadataService,
            retryScheduleStore = retryScheduleStore,
            dataLayerClient = dataLayerClient,
            clock =
                object : Clock {
                    override fun now(): Instant = now
                },
        )

    private fun givenWaiting(
        type: EntityType,
        entityId: String,
        retryAt: Long? = null,
    ) {
        coEvery { syncMetadataService.getPendingUploads(type) } returns listOf(PendingUpload(entityId, PendingOperation.CREATE))
        coEvery { retryScheduleStore.nextAttemptAt(type, entityId) } returns retryAt
    }
}
```

The last test relies on `runTest` reporting a failure in `backgroundScope` when the test ends ("Failures in this scope are reported at the end of the test", kotlinx-coroutines-test `TestScope.backgroundScope`). In the app the same exception would crash the process, because the application scope has no exception handler.

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchSyncWorkerTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.sync.WatchSyncWorker
import app.logdate.wear.sync.WorkManagerWatchSyncWork
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Runs the watch's sync worker on a Wear OS emulator against the app's real dependency graph.
 *
 * The managed emulator has no paired phone, so a run finds no phone in reach. It must succeed, spend
 * no attempts on what is waiting, and set no further run, because the phone coming into reach starts
 * a sync by itself.
 */
@RunWith(AndroidJUnit4::class)
class WatchSyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val workManager = WorkManager.getInstance(context)

    @Before
    fun clearScheduledRuns() {
        workManager.cancelUniqueWork(WatchSyncWorker.UNIQUE_WORK_NAME).result.get()
    }

    @After
    fun cancelScheduledRuns() {
        workManager.cancelUniqueWork(WatchSyncWorker.UNIQUE_WORK_NAME).result.get()
    }

    @Test
    fun `a run with no phone in reach succeeds without spending attempts`() =
        runBlocking {
            val metadata = KoinJavaComponent.getKoin().get<SyncMetadataService>()
            val noteId = Uuid.random().toString()
            metadata.enqueuePending(noteId, EntityType.NOTE, PendingOperation.CREATE)
            try {
                val result = TestListenableWorkerBuilder<WatchSyncWorker>(context).build().doWork()

                assertEquals(ListenableWorker.Result.success(), result)
                assertEquals(0, metadata.getPendingUploads(EntityType.NOTE).single { it.entityId == noteId }.retryCount)
                val waiting = workManager.getWorkInfosForUniqueWork(WatchSyncWorker.UNIQUE_WORK_NAME).get()
                assertTrue(waiting.none { it.state == WorkInfo.State.ENQUEUED }, "No run should be set without a phone")
            } finally {
                metadata.markAsSynced(noteId, EntityType.NOTE, Clock.System.now(), 0L)
            }
        }

    @Test
    fun `scheduling again replaces the waiting run`() {
        val work = WorkManagerWatchSyncWork(workManager)

        work.runAfter(TimeUnit.HOURS.toMillis(1))
        work.runAfter(TimeUnit.HOURS.toMillis(2))

        val waiting = workManager.getWorkInfosForUniqueWork(WatchSyncWorker.UNIQUE_WORK_NAME).get()
        assertEquals(1, waiting.count { it.state == WorkInfo.State.ENQUEUED })
    }
}
```

WorkManager runs enqueue requests and status queries in order on one serial task executor, so `getWorkInfosForUniqueWork` sees both requests `runAfter` made just before it.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.WatchSyncSchedulerTest'
```
Expected: FAIL. The seven tests that expect a run report `expected:<[0]> but was:<[]>` (`expected:<[5000]> but was:<[]>` for the earliest-retry test). The four tests that expect nothing to be scheduled already pass.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WatchSyncWorkerTest
```
Expected: FAIL. `a run with no phone in reach…` reports `expected:<Success {mOutputData=Data {}}> but was:<Failure {mOutputData=Data {}}>`, and `scheduling again replaces the waiting run` reports `expected:<1> but was:<0>`.

- [ ] **Step 4: Implement the scheduler and the worker**

Replace `WatchSyncScheduler.kt` with:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Runs the watch's sync once, after [delayMs], in place of any run already waiting. */
fun interface WatchSyncWork {
    fun runAfter(delayMs: Long)
}

/**
 * Wakes the watch when an outbox item is next due, so a retry happens on time even when nothing else
 * would start the app.
 *
 * The next run is set for the earliest stored retry time of anything the watch uploads, or for now
 * when an item has never been attempted. It is set again each time the sync status settles outside a
 * run: at app start, when the phone comes into or goes out of reach, when the outbox changes, and
 * after every run. Changes during a run wait for it to finish, because setting a new run replaces the
 * waiting work and would stop a run in progress. With no phone in reach nothing is set: the phone's
 * return starts a sync by itself.
 *
 * The watch build removes RECEIVE_BOOT_COMPLETED, so after a reboot WorkManager reschedules this
 * work when the app process next starts, through any Data Layer event, tile, complication or launch.
 */
class WatchSyncScheduler(
    private val work: WatchSyncWork,
    private val syncStatus: Flow<SyncStatus>,
    private val syncMetadataService: SyncMetadataService,
    private val retryScheduleStore: SyncRetryScheduleStore,
    private val dataLayerClient: WearDataLayerClient,
    private val clock: Clock = Clock.System,
) {
    /** Follows the sync status in [scope] for the life of the app. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            syncStatus
                .debounce(STATUS_SETTLE_MS)
                .filterNot { it.isSyncing }
                .catch { e -> Napier.e("Stopped scheduling watch syncs: the sync status could not be read", e) }
                .collect { scheduleNextLogged() }
        }
    }

    /** Sets the next run for when the earliest waiting item is due, if a phone is in reach. */
    suspend fun scheduleNext() {
        if (!dataLayerClient.isPhoneConnected()) return
        val dueAt = earliestAttemptAt() ?: return
        work.runAfter((dueAt - clock.now().toEpochMilliseconds()).coerceAtLeast(0L))
    }

    private suspend fun earliestAttemptAt(): Long? =
        UPLOADED_TYPES
            .flatMap { type -> syncMetadataService.getPendingUploads(type).map { pending -> type to pending.entityId } }
            .minOfOrNull { (type, entityId) -> retryScheduleStore.nextAttemptAt(type, entityId) ?: 0L }

    private suspend fun scheduleNextLogged() {
        try {
            scheduleNext()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not schedule the next watch sync", e)
        }
    }

    private companion object {
        /** What the watch's sync manager uploads; anything else in the outbox is not the watch's to send. */
        val UPLOADED_TYPES = listOf(EntityType.JOURNAL, EntityType.NOTE, EntityType.ASSOCIATION)

        /** Status changes come in bursts: a note, its link to a journal, and the run that sends both. */
        const val STATUS_SETTLE_MS = 2_000L
    }
}
```

Replace `WatchSyncWorker.kt` with:

```kotlin
package app.logdate.wear.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.logdate.client.sync.SyncManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Runs the watch's sync when [WatchSyncScheduler] says something is due, then sets the next run.
 *
 * The next run is set before this one returns, while the watch is still awake for this work. Setting
 * it replaces this work, which stops it, so it is the last thing a run does. The sync itself runs to
 * the end even if the work is stopped part way through, because an upload cut off mid-file would only
 * start again.
 */
class WatchSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val syncManager: SyncManager by inject()
    private val scheduler: WatchSyncScheduler by inject()

    override suspend fun doWork(): Result {
        val result =
            try {
                withContext(NonCancellable) { syncManager.fullSync() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Watch sync run failed", e)
                return Result.retry()
            }
        if (!result.success) {
            Napier.w("Watch sync run finished with errors: ${result.errors.joinToString { it.message }}")
        }
        scheduler.scheduleNext()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "watch_sync"
    }
}

/** [WatchSyncWork] as unique WorkManager work, so only one run ever waits. */
class WorkManagerWatchSyncWork(
    private val workManager: WorkManager,
) : WatchSyncWork {
    override fun runAfter(delayMs: Long) {
        val request =
            OneTimeWorkRequestBuilder<WatchSyncWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(WatchSyncWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
```

- [ ] **Step 5: Run the unit tests, the managed-device tests and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:detekt :app:wear:ktlintCheck
```
Expected: BUILD SUCCESSFUL.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WatchSyncWorkerTest
```
Expected: PASS, 2 tests. If the managed emulator reports "Process crashed" or "failed to complete startup" under host load, rerun once with the machine otherwise idle; never fall back to a connected device.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task11-sync-worker.txt <<'EOF'
fix(wear): wake the watch when a sync retry is due

A recording or note that failed to reach the phone was tried again only
when something else woke the app, such as opening it or the phone
coming back into reach. The watch stored when each retry was due, but
nothing ran at that time, so a capture could wait for hours.

The watch now schedules a background sync for the earliest retry time
when the app starts, when the phone comes into reach, when its queue
changes, and after every sync. With no phone in reach it schedules
nothing and syncs as soon as the phone returns. After the watch
restarts, the schedule resumes the next time the app runs.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/wear/build.gradle.kts app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncScheduler.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncWorker.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WatchSyncSchedulerTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchSyncWorkerTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task11-sync-worker.txt
```

---

### Task 12: Delete a note's data item once the phone has it (A11)

A data item stays in Play services' storage on every device until the device that wrote it deletes it ("Data items are synchronized across all devices… Those data items will be synchronized when the nodes eventually come online", `DataClient` reference). Every audio note the watch sends leaves one behind after the phone confirms it. The only delete the watch has, `deleteDataItem` (`GoogleWearDataLayerClient.kt:80-94`), builds `wear:` plus the path with no node, which is not the fully specified `wear://<node_id>/<path>` form that deletes one item ("If uri is fully specified, this method will delete at most one data item", `DataClient.deleteDataItems`), and nothing calls it. The phone publishes its own notes for the watch at the same `/logdate/notes/<id>` paths, so a delete must name the watch's node.

**Files:**
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerClient.kt:21-25` (`deleteDataItem`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClient.kt:3` (imports), `:28-36` (`WearPutDataRequestFactory` and the misplaced class KDoc above it), `:38-49` (constructor), `:80-94` (`deleteDataItem`), `:273-286` (companion)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt` (`onNoteAcknowledged` as Task 8 left it; new `deleteDeliveredDataItem`)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClientTest.kt` (the `deleteDataItem` test at `:64-71`, `createClient` at `:461-489`, 3 new tests)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt` (the `deleteDataItem` stub in `setUp`, line 113 as of `a8615ae5d`; 3 new tests)

**Interfaces:**
- Consumes: `NodeClient.getLocalNode(): Task<Node>`; `DataClient.deleteDataItems(uri: Uri): Task<Int>`; `PutDataRequest.WEAR_URI_SCHEME = "wear"`; `NoteDataMapper.notePath(noteId: Uuid): String`; Task 8's `onNoteAcknowledged(noteId: Uuid, ack: NoteAckPayload?)`, `NoteAckPayload` and the test helper `givenQueuedNote(operation: PendingOperation)`; Task 7's `settle(entityType, pending, version)` and its `settleIfCurrent` stub in `setUp`; Task 4's listeners, which ignore `TYPE_DELETED` events.
- Produces:
  - `WearDataLayerClient.deleteOwnDataItem(path: String): Boolean`, replacing `deleteDataItem(path: String): Boolean`
  - `fun interface WearDataItemUriFactory { fun create(nodeId: String, path: String): Uri }`
  - `GoogleWearDataLayerClient(dataClient, channelClient, capabilityClient, nodeClient, messageClient, coroutineScope, ioDispatcher, audioTransferTimeoutMs, putDataRequestFactory, dataItemUriFactory: WearDataItemUriFactory = defaultWearDataItemUriFactory)`; the secondary `(context, coroutineScope, …)` constructor is unchanged

**Compatibility:** phones with Task 4 drop the deletion event before reading it. A phone without Task 4 (a dogfood build from `3475a6276`, the first to advertise `logdate_phone_app` and so the first the watch uploads to, up to Task 4) handles it as a change: `DataMapItem.fromDataItem` returns an empty `DataMap` for an item without data (checked in the 19.0.0 bytecode), `WatchNoteIngestor.onNoteMetadata` fails to decode it, and `handleNoteSync` logs "Failed to process synced note from watch" (`PhoneDataLayerListenerService.kt:268-278`). One warning per confirmed note; nothing is stored or removed. Text notes, journals and links are never acknowledged, so their data items stay as they are today.

- [ ] **Step 1: Replace the delete with an inert `deleteOwnDataItem`**

In `WearDataLayerClient.kt`, replace lines 21-25 (the `deleteDataItem` KDoc and declaration) with:

```kotlin
    /**
     * Deletes the data item this watch wrote at [path]. Items the phone wrote at the same path are
     * untouched. Returns true if the item is gone, including when there was none.
     */
    suspend fun deleteOwnDataItem(path: String): Boolean
```

In `GoogleWearDataLayerClient.kt`:
- Add `import android.net.Uri` after `import android.content.Context`.
- The KDoc above `WearPutDataRequestFactory` (lines 28-30, `Production [WearDataLayerClient] backed by Google Play Services Wearable APIs.`) describes the class: move it to directly above `class GoogleWearDataLayerClient(`, and give the factory its own: `/** Builds the request for a put; a seam so JVM tests need no real [PutDataRequest]. */`.
- After the `WearPutDataRequestFactory` interface, add:

```kotlin
/** Builds the URI of the data item [nodeId] wrote at a path; a seam so JVM tests need no [Uri]. */
fun interface WearDataItemUriFactory {
    fun create(
        nodeId: String,
        path: String,
    ): Uri
}
```

- Add `private val dataItemUriFactory: WearDataItemUriFactory = defaultWearDataItemUriFactory,` as the last primary constructor parameter, after `putDataRequestFactory`.
- Replace `deleteDataItem` (lines 80-94) with:

```kotlin
    override suspend fun deleteOwnDataItem(path: String): Boolean = false
```

- In the companion object, after `defaultWearPutDataRequestFactory`, add:

```kotlin

        val defaultWearDataItemUriFactory =
            WearDataItemUriFactory { nodeId, path ->
                Uri
                    .Builder()
                    .scheme(PutDataRequest.WEAR_URI_SCHEME)
                    .authority(nodeId)
                    .path(path)
                    .build()
            }
```

In `GoogleWearDataLayerClientTest.kt`, delete the test ``fun `deleteDataItem returns false when wearable delete fails`()`` (lines 64-71, and the `@Test` Task 2 added above it). Give `createClient` a fourth parameter and pass it to both constructor calls:

```kotlin
    private fun createClient(
        scope: TestScope,
        audioTransferTimeoutMs: Long = 1_000L,
        putDataRequestFactory: WearPutDataRequestFactory? = WearPutDataRequestFactory { _, _ -> mockk(relaxed = true) },
        dataItemUriFactory: WearDataItemUriFactory = WearDataItemUriFactory { _, _ -> mockk(relaxed = true) },
    ): GoogleWearDataLayerClient =
        if (putDataRequestFactory == null) {
            GoogleWearDataLayerClient(
                dataClient = dataClient,
                channelClient = channelClient,
                capabilityClient = capabilityClient,
                nodeClient = nodeClient,
                messageClient = messageClient,
                coroutineScope = scope,
                ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
                audioTransferTimeoutMs = audioTransferTimeoutMs,
                dataItemUriFactory = dataItemUriFactory,
            )
        } else {
            GoogleWearDataLayerClient(
                dataClient = dataClient,
                channelClient = channelClient,
                capabilityClient = capabilityClient,
                nodeClient = nodeClient,
                messageClient = messageClient,
                coroutineScope = scope,
                ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
                audioTransferTimeoutMs = audioTransferTimeoutMs,
                putDataRequestFactory = putDataRequestFactory,
                dataItemUriFactory = dataItemUriFactory,
            )
        }
```

In `WearDataLayerSyncManagerTest.setUp`, change `coEvery { dataLayerClient.deleteDataItem(any()) } returns true` to:

```kotlin
        coEvery { dataLayerClient.deleteOwnDataItem(any()) } returns true
```

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Write the failing tests**

Append to `GoogleWearDataLayerClientTest` (before `private fun createClient`), and add `import android.net.Uri`:

```kotlin
    @Test
    fun `deleteOwnDataItem deletes the item this watch wrote at the path`() =
        runTest {
            every { nodeClient.localNode } returns Tasks.forResult(mockk<Node> { every { id } returns "watch-node" })
            val uri = mockk<Uri>()
            every { dataClient.deleteDataItems(uri) } returns Tasks.forResult(1)
            val requested = mutableListOf<Pair<String, String>>()
            val client =
                createClient(
                    this,
                    dataItemUriFactory =
                        WearDataItemUriFactory { nodeId, path ->
                            requested += nodeId to path
                            uri
                        },
                )

            assertTrue(client.deleteOwnDataItem("/logdate/notes/1"))

            assertEquals(listOf("watch-node" to "/logdate/notes/1"), requested)
            verify(exactly = 1) { dataClient.deleteDataItems(uri) }
        }

    @Test
    fun `deleteOwnDataItem reports failure when the watch's node cannot be read`() =
        runTest {
            every { nodeClient.localNode } returns Tasks.forException(IllegalStateException("Play services unavailable"))

            assertFalse(createClient(this).deleteOwnDataItem("/logdate/notes/1"))

            verify(exactly = 0) { dataClient.deleteDataItems(any()) }
        }

    @Test
    fun `deleteOwnDataItem reports failure when the delete fails`() =
        runTest {
            every { nodeClient.localNode } returns Tasks.forResult(mockk<Node> { every { id } returns "watch-node" })
            every { dataClient.deleteDataItems(any()) } returns Tasks.forException(IllegalStateException("boom"))

            assertFalse(createClient(this).deleteOwnDataItem("/logdate/notes/1"))
        }
```

Append to `WearDataLayerSyncManagerTest` (before the final `}`):

```kotlin
    // =======================================================================
    // Data item cleanup
    // =======================================================================

    @Test
    fun `a confirmed note's data item is deleted after the confirmation is recorded`() =
        runTest {
            givenQueuedNote(PendingOperation.CREATE)
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote

            syncManager.onNoteAcknowledged(noteId, NoteAckPayload(fixedTime.toEpochMilliseconds()))

            coVerifyOrder {
                syncMetadataService.settleIfCurrent(EntityType.NOTE, match { it.entityId == noteId.toString() }, any(), any())
                dataLayerClient.deleteOwnDataItem(NoteDataMapper.notePath(noteId))
            }
        }

    @Test
    fun `a confirmation of an older version leaves the newer data item in place`() =
        runTest {
            givenQueuedNote(PendingOperation.UPDATE)
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote.copy(lastUpdated = fixedTime + 5.seconds)

            syncManager.onNoteAcknowledged(noteId, NoteAckPayload(fixedTime.toEpochMilliseconds()))

            coVerify(exactly = 0) { dataLayerClient.deleteOwnDataItem(any()) }
        }

    @Test
    fun `a data item that cannot be deleted leaves the confirmation applied`() =
        runTest {
            givenQueuedNote(PendingOperation.CREATE)
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote
            coEvery { dataLayerClient.deleteOwnDataItem(any()) } returns false

            syncManager.onNoteAcknowledged(noteId, NoteAckPayload(fixedTime.toEpochMilliseconds()))

            coVerify(exactly = 1) {
                syncMetadataService.settleIfCurrent(EntityType.NOTE, match { it.entityId == noteId.toString() }, any(), any())
            }
            coVerify(exactly = 1) { dataLayerClient.deleteOwnDataItem(NoteDataMapper.notePath(noteId)) }
        }
```

`NoteAckPayload`, `coVerifyOrder` and `seconds` are imported since Task 8; `NoteDataMapper` already was.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.GoogleWearDataLayerClientTest' --tests 'app.logdate.wear.sync.WearDataLayerSyncManagerTest'
```
Expected: FAIL.
- `deleteOwnDataItem deletes the item this watch wrote at the path`: `Expected value to be true.`
- `a confirmed note's data item is deleted after the confirmation is recorded` and `a data item that cannot be deleted leaves the confirmation applied`: verification fails because `deleteOwnDataItem` was never called.
- The two failure-reporting client tests and `a confirmation of an older version leaves the newer data item in place` already pass.

- [ ] **Step 4: Delete the watch's own item, and do so after each applied confirmation**

In `GoogleWearDataLayerClient.kt`, add `import kotlinx.coroutines.CancellationException` and replace the inert `deleteOwnDataItem` with:

```kotlin
    override suspend fun deleteOwnDataItem(path: String): Boolean =
        try {
            val localNodeId = nodeClient.localNode.await().id
            val deleted = dataClient.deleteDataItems(dataItemUriFactory.create(localNodeId, path)).await()
            Napier.d("Deleted $deleted data item(s) this watch wrote at $path")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Failed to delete this watch's data item at path: $path", e)
            false
        }
```

In `WearDataLayerSyncManager.kt`, make deletion the last step of an applied confirmation. In `onNoteAcknowledged`, replace the final line `settle(EntityType.NOTE, pending, version = 1)` with:

```kotlin
        settle(EntityType.NOTE, pending, version = 1)
        deleteDeliveredDataItem(noteId)
```

and add below `isNewerThan`:

```kotlin
    /**
     * The phone has stored the note, so the data item that sent it has done its job. Deleting it
     * stops Play services from storing it on both devices and syncing it to devices that connect
     * later. It runs under the same lock as uploads, so it can never remove a newer version an upload
     * is putting. A failed deletion changes nothing the person sees, so it is logged and the note
     * stays confirmed.
     */
    private suspend fun deleteDeliveredDataItem(noteId: Uuid) {
        if (dataLayerClient.deleteOwnDataItem(NoteDataMapper.notePath(noteId))) return
        Napier.w("Could not delete the data item of note $noteId after the phone confirmed it")
    }
```

Extend the `onNoteAcknowledged` KDoc from Task 8 with one sentence at the end: `Once a confirmation is applied, the note's data item is deleted.`

- [ ] **Step 5: Run the tests and lint**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:detekt :app:wear:ktlintCheck
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task12-delete-data-items.txt <<'EOF'
chore(wear): delete a note's data item once the phone has it

Every audio note the phone confirmed left its data item in Play
services' storage on the watch and the phone. Play services stores a
data item until the device that wrote it deletes it, and syncs it to
devices that connect later.

The watch now deletes a note's data item once the phone confirms the
version it has, naming the watch's own node so the phone's items at
the same path are untouched. Phones from this version on ignore the
deletion. Earlier phones log one warning per deleted item and store or
remove nothing.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerClient.kt app/wear/src/main/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClient.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt app/wear/src/test/kotlin/app/logdate/wear/sync/GoogleWearDataLayerClientTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerTest.kt && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task12-delete-data-items.txt
```

---

## Self-Review

**Spec coverage.** Every Track A item maps to a task, and the echo guard the instructions asked to check for has its own:

| Item | Task | Covered by |
|---|---|---|
| A1 | 1 | `/logdate/camera` in the listener's `DATA_CHANGED` filter. `WearManifestFiltersTest` resolves every data item, message and capability event the phone produces against the installed manifest with `queryIntentServices` on the Wear managed device. |
| A2 | 2 | `putDataItem(…, urgent)` with no default on both devices. Note puts and removals from the watch and camera updates from the phone are urgent; `publishNotesToWatch`, journals, links and health snapshots are routine. |
| A3 | 3 | `PhoneNodeSelector` (the phone advertising `logdate_phone_app`, nearby first, with a logged connected-node fallback for phones that advertise nothing) and `WatchNodeSelector` (reachable watches advertising `logdate_watch_app`, nearby first). Each watch gets its own send, so one failure does not stop the rest, and `requestSync()` throws when no watch received the request instead of returning as if one had; the Watch settings screen logs it. Built on C3a's `AndroidWatchConnectionManager`, whose `messageClient` parameter `messageSender` replaces. |
| Echo | 4 | Both listeners act only on `TYPE_CHANGED` events another node wrote (`isChangeFromAnotherDevice`), and apply every change when their own node id cannot be read. |
| A4 | 5 | Optional `audioSize` and `audioSha256` keys (`AudioFileDigest`). The phone measures audio while it copies it; a mismatch stores no note and sends no acknowledgement, so the watch resends. Missing keys are accepted as today. |
| A5 | 6 | `SyncedAudioCache` at `filesDir/synced_audio/<noteId>.<ext>`, written as `.part` and renamed into place. `WearRecordingRecovery` reads only `audio_notes`, and two recovery tests pin that it never adopts a pull, finished or partial. |
| A6 | 7 | A dead letter stays in the outbox with a 6 h retry, named as on the phone (`pending.operationId ?: "TYPE:id"`) and carrying the operation's scope and ids. An operation leaves the outbox only through `settle`, which uses `settleIfCurrent`: delivered, acknowledged, unsendable (`SKIPPED`), or discarded with `discardDeadLetter`, the one way to drop a dead letter and one that never drops a later change. Failures count through `incrementRetryIfCurrent`. `WearDeadLetterMigration` puts records an older version removed from the outbox back. |
| A7 | 8 | `NoteAckPayload` (`{"lastUpdated":<epoch ms>}`), applied under the `syncLock` that uploads use; the note clears only when the local copy is no newer, and never when a DELETE is queued. An empty payload clears it as before. A race test pins that an acknowledgement waits for an in-flight upload's bookkeeping. |
| A8 | 9 | `WatchJournalIngestor`, bound in `AppModule.android.kt`, applies watch journals and links through the repositories that queue cloud uploads, idempotently, and saves a newer journal copy with the phone's own `syncVersion` for the upload's exact version check. |
| A9 | 10 | `syncStatusFlow` combines the run in progress, the outbox, the dead letters and `PhoneReachability`. Home and Settings collect it, both polling loops are gone, and Turbine tests cover the transitions. |
| A10 | 11 | `WatchSyncScheduler` and `WatchSyncWorker`: unique work `watch_sync` at the earliest retry time, set from the settled sync status and after each run. Unit tests cover the timing and triggers; a managed-device test runs the worker against the real graph. |
| A11 | 4, 12 | Both listeners ignore `TYPE_DELETED` (Task 4) before the watch starts deleting `wear://<its node>/logdate/notes/<id>` after each applied acknowledgement (Task 12). |

**Revised against the current tree (`a8615ae5d`).** Every file path, line number, class and function name, signature, test name and Gradle command was checked again after the rebase onto `main`. What changed in the plan:
- Task 3 now follows C3a, which lands first: it edits the constructor and test class C3a leaves, removes C3a's `messageClient` parameter, and adds its tests to C3a's `AndroidWatchConnectionManagerTest` instead of creating the file.
- Task 7 uses the outbox API `main` added in `78faf0960`: `settle` takes the captured `PendingUpload` and calls `settleIfCurrent`, failures go through `incrementRetryIfCurrent`, dead letters are named and filled in the phone's form, and discarding matches the set-aside operation. The existing outbox verifications in `WearDataLayerSyncManagerTest` and `AudioSyncFlowTest` move to those calls with the same meaning. Tasks 8 and 12 settle the queued `PendingUpload` accordingly, and Task 8's "no longer queued" test moved into Task 7.
- Task 9 explains, and its test pins, why a watch journal update keeps the phone's `syncVersion` (`8f25cd326` made PATCH version checks exact).
- Line numbers in the phone test files follow the ktlint reformat (`31f19c85b`); `WearSettingsScreen.kt` (43-55) and the watch manifest's `MainActivity` (223-233) follow `d69aea766`; `libs.androidx.work.*` are at `gradle/libs.versions.toml:338-339`. Branch commit hashes follow the rebase (`7c58cce8d`, `3677db44c`, `3475a6276`).
- Every lint step now runs `ktlintCheck` on `:app:wear` and `:app:android-main` too, and the phone tests use the expression-body layout ktlint_official enforces there. The resolver and Settings test rewrites in Tasks 6 and 10 include every check the current files make, including the two tests the first version dropped.
- Commit trailers are `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`, and each commit runs as `git restore --staged . && git add <paths> && ANDROID_HOME=$HOME/Library/Android/sdk git commit -F <file>`.

**Already fixed before this plan.** A5 in part: `3677db44c` deletes pulled audio when the phone deletes the note (`SyncedAudioFiles.deleteLocalAudio`), and `7c58cce8d` never adopts an m4a without its raw file or a raw file a note references. Task 6 covers the rest: pulls under random names in the recordings directory, partial pulls that never go away, and a finished pull whose `updateMediaRef` never ran being adopted as a duplicate note. Also on this branch already: `3475a6276` moved the phone's capability declaration to `res/values/wear.xml`, which Android reads (`main` still has `res/xml/wear_capabilities.xml`). Nothing else in A1–A11 was fixed, by this branch or by `main`.

**Checked rather than assumed.**
- Play services 19.0.0 bytecode: `DataMapItem.fromDataItem` returns an empty `DataMap` for an item without data, which is what a phone without Task 4 reads for Task 12's deletions; `getAssets` never returns null.
- AGP 9.2.1 passes `-Pandroid.testInstrumentationRunnerArguments.class` to managed-device runs; `:app:wear:wearDevicesGroupDebugAndroidTest` targets the one Wear device the root build declares (`wearSmallRoundApi34`).
- `DatabaseSyncMetadataService.settleIfCurrent` and `incrementRetryIfCurrent` compare the row's `operationId` with the captured one; `enqueuePending` replaces the row with a new id and resets its retry count; `getPendingUploads` always fills in `scope` and `operationId`.
- `SyncUploader` sends a journal UPDATE with `pending.expectedServerVersion ?: journal.syncVersion`, and `CloudJournalDataSource` turns a positive version into `VersionConstraint.Known`.
- kotlinx-coroutines-test reports a failure in `backgroundScope` at the end of the test, which Task 11's last scheduler test relies on.
- `WearDataLayerSyncManager` has about 480 lines of code today and stays under detekt's 600-line class limit after Tasks 5, 7, 8, 10 and 12; each task's `detekt` run checks it.
- Every commit message has a Conventional Commits title of at most 72 characters with scope `wear` or `sync` from `allowed-scopes.txt` and no `style` type, body lines of at most 72 characters, and the `Co-Authored-By` line last. Every commit step is the one chain above, written after its message file in a separate command, and its paths match the task's file list.

**Assumed, and guarded either way.** Whether a node's own Data Layer writes reach its own listener cannot be observed without a paired phone and watch. Task 4's filter is correct whether or not they do, costs one `getLocalNode` lookup per listener service instance, and applies every change when the lookup fails.

**Known limits, not fixed here.**
- After a reboot, retries wait until the app process next starts, because the watch build removes `RECEIVE_BOOT_COMPLETED` (Task 11). Restoring it belongs to the reminders work.
- The worker sets its successor before returning, and that replaces the finished work in WorkManager, so a completed run is recorded as cancelled rather than succeeded.
- The watch uploads only to a phone that advertises `logdate_phone_app`: `isPhoneConnected()` has no connected-node fallback, as on `main`. Messages and files fall back (Task 3). Phones built from `main` never advertised the capability, so watches never uploaded to them; that does not change.
- The phone acknowledges an audio note it already has with the version it stored and does not apply a newer one, because watch notes have no update path. The watch never changes a note after creating it today (its notes repository queues only CREATE and DELETE). If it ever does, the newer version waits in the outbox and shows as a sync problem instead of disappearing (Task 8).
- Only audio notes are acknowledged, so the data items of text notes, journals and links stay in the Data Layer, as today (Task 12).
- The phone never tells the watch it deleted a journal, so a later edit of that journal on the watch brings it back on the phone and queues it for backup again (Task 9). Today's `createFromSync` path does the same, without the backup.
- The retry schedule on the watch is still keyed by item, not by operation as on the phone. Settling or skipping an operation clears the item's retry time, which makes a newer change to it due at once; nothing is lost, but a newer change does not inherit the older one's backoff.
- `openAppOnWatch` and `installAppOnWatch` belong to C3a and are not changed here.
- The watch home greeting and entry-count text are hard-coded English in `WearHomeViewModel`, as today; Task 10 rewrites the file around them, and Track B's home screen replaces them.

**Placeholder scan.** No step says TBD, TODO, "add error handling" or "similar to Task N". Every code step shows the code, every run step gives the command and the expected result, and every commit step gives the message and the exact paths. In Tasks 2-12, Step 1 adds inert bodies only so the new tests compile and fail on behavior, and Step 4 replaces them before the commit. Task 1 changes only the manifest and needs none.

**Name consistency.** Each name has one signature everywhere it appears: `settle(entityType, pending, version)`, `queuedNote(noteId)`, `setAsideOperation`, `removeDeadLetters` and `deadLetterId(entityType, pending)` (Task 7, used by 8 and 12); `NoteAckPayload` and `WearNoteAckHandler.onNoteAcknowledged(noteId, ack)` (Task 8, used by 12); `WatchMessageSender.send(path, data)` (Task 3, given `data` in Task 8); `PhoneNodeSelector.select()` and `WatchNodeSelector.reachableWatches()`/`WATCH_CAPABILITY` (Task 3); `isChangeFromAnotherDevice` (Task 4); `AudioFileDigest` (Task 5); `SyncedAudioCache` (Task 6); `WearDeadLetterMigration` (Task 7, started in Task 11's `startSync()`); `WatchJournalIngestor` (Task 9); `PhoneReachability` and `ReachablePhone` (Task 10); `syncStatusFlow` with `isSyncing` (Task 10, followed by Task 11); `WatchSyncScheduler`, `WatchSyncWork` and `WatchSyncWorker.UNIQUE_WORK_NAME` (Task 11); `deleteOwnDataItem` and `WearDataItemUriFactory` (Task 12).
