# Phone Companion Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the phone-side foundations of the redesigned watch experience that remain in Track C wave 1 of the master plan, plus the reminder decision core (Track R1): a neutral graphite fallback palette for everyone (C12), watch preferences shared with the watch (C2), a watch status in Settings that shows only what the watch reported (C3b), which device captured each moment, from storage through sync, archives and the timeline (D1), the read side of marked moments with places filled in from location history (D2), and a pure, exhaustively tested `ReminderOrchestrator` (R1).

**Architecture:** Ten test-first commits on `claude/wearos-android-ux-redesign-081dc0`, each shippable on its own. Shared logic is `commonMain` code that compiles for iOS: preferences and reminders in `:client:domain` (`app.logdate.client.domain.watch`, `app.logdate.client.domain.reminders`, `app.logdate.client.domain.moments`), the capture device on `JournalNote` in `:client:repository`, the watch status model next to the existing Watch settings in `:client:feature:core`, and the shared provenance composables in `:client:ui`. Preferences ride on the existing `StateDocument.shared` map with the existing `StateMerger` last-writer-wins rule, published by the existing `PhoneStatePublisher`. The capture device is a nullable `CaptureDevice` stored in a new `captured_on` column (Room 53 → 54), encrypted with the existing `SyncPayloadCipher` field scheme before upload, passed through the server as an opaque string like `photoPresentation`, and written to archives as `"phone"`/`"watch"`. Everything a user could see behind the Labs switch reads `FeatureFlag.WATCH_EXPERIENCE_V2` through `FeatureFlagStore.observe` at the navigation root, the same pattern as `LocationTimelineScreen`. Background: `docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md` and the master plan sections "Experience spec / Phone", "Architecture and cross-track contracts" (items 7, 9, 10, 11) and "Track C … Track R".

**Tech Stack:** Kotlin 2.3.21 Multiplatform (Android, iOS, desktop JVM), Compose Multiplatform 1.11.0 with Material 3 `1.11.0-alpha07` (the 48-argument `lightColorScheme`/`darkColorScheme` with fixed roles) and Compose resources, Room KMP 2.8.4 (schema 53 → 54, `MigrationTestHelper` with `BundledSQLiteDriver` on the JVM), kotlinx.serialization 1.11.0, kotlinx-datetime 0.7.1 with `kotlin.time.Instant`/`Clock`, kotlinx.coroutines and Flow, Koin 4.2.1, Napier, the Ktor server's content routes and repo record JSON, kotlin.test, MockK, `json-schema-validator` (archive schema tests), the desktop screenshot lane (`DesktopScreenshotTest`), Gradle Managed Devices.

## Global Constraints

- Flagging: Task 1 (fallback palette) and Task 4 (the watch status fix) ship to everyone, which is what the master plan calls "unflagged" ("fixes that are safe for everyone"); every other user-visible surface stays dark until `FeatureFlag.WATCH_EXPERIENCE_V2` is on.
- UI gating follows `LocationTimelineScreen`: `koinInject<FeatureFlagStore>().observe(flag)` collected as state. The watch glyph reads it once, in `LogDateNavDisplay`, into `LocalWatchProvenanceVisible` (default `false`), so leaf composables and screenshot scenes never touch Koin.
- No dead controls: a control appears only when its action works today; a status line appears only when the watch itself reported the value behind it.
- Room goes from version 53 to 54 in Task 5 only (`MIGRATION_53_54`, `client/database/schemas/app.logdate.client.database.LogDateDatabase/54.json`). The master plan's `mark_photo_decisions` table is not added here; D4 adds it with its first user.
- Server: Task 6 adds one optional, opaque string to the content routes and the entry record JSON; no Flyway migration, no new route.
- Data safety: every field this plan adds is optional on every wire and file format; an unknown capture device value written by a newer app reads as unknown (`null`), never as an error; restoring an archive written before this plan yields notes with no capture device; an edit or sync copy that lacks the field never erases it.
- Logging: Napier only, never `println`, `System.out` or `Log.*`; logs never carry journal text, captions, coordinates or tokens.
- IDs: `kotlin.uuid.Uuid` (`Uuid.random()`, `Uuid.parse`, `Uuid.parseOrNull`); never `java.util.UUID`.
- Time: `kotlin.time.Instant`/`Clock`/`Duration`; zones and calendar math with `kotlinx.datetime` (`TimeZone`, `LocalDate`, `LocalTime`, `LocalDateTime`, `toInstant`, `toLocalDateTime`).
- Serialization: kotlinx.serialization only.
- DI: Koin constructor injection; collaborators arrive as narrow interfaces or function types (`WatchPeerSnapshotSource`, `windowDays: Flow<Int>`, `adoptWatchPreferences: suspend (Map<String, Stamped>) -> Unit`).
- Imports: ktlint_official order (lexicographic, `java.*`, `javax.*` and `kotlin.*` last, no wildcards in new files); when `ktlintCheck` fails, run `:<module>:ktlintFormat`, rerun the task's tests, and stage the reformatted file.
- Style: early returns and guard clauses; KDoc explains why and never restates code; one argument per line with trailing commas once a call has two or more.
- Copy: literal, never speaks as "we", never uses "kept", "held" or "carried", lives in Compose string resources (`composeResources/values/*.xml`); the same rules apply to KDoc and commit text.
- Size: new files stay at 500 lines or fewer. `WatchSettingsScreen.kt` (534) and `WatchNoteIngestorTest.kt` (552) are split as part of the tasks that change them. Files already over 500 lines that receive only the lines a new field needs (`SyncHelpers.kt` 609, `SyncSchemaDocs.kt` 525, `ExportUserDataUseCase.kt` 733, `RestoreUserDataUseCase.kt` 766, `ExportImportRoundTripTest.kt` 699, `EntryEditorViewModel.kt` 1415, `SharedScreenshotCatalog.kt` 1517) are not split here; new code and new tests go into new files, and the catalog loses its watch scenes to a new file.
- detekt: `LongMethod` 60 lines (`@Composable` exempt) and `LargeClass` 600 lines (`config/detekt/detekt.yml`).
- iOS: every touched KMP module passes `:<module>:compileKotlinIosSimulatorArm64` and `:<module>:compileTestKotlinIosSimulatorArm64`; `commonTest` backtick names contain only letters, digits and spaces (no commas, periods, colons, slashes, apostrophes or parentheses); no JVM-only APIs in `commonMain`.
- Gradle: run from `/Users/williecubed/Projects/TheHypertextStudio/logdate-mobile/.claude/worktrees/backup-sync-infinite-notification-dd4c3a`, and prefix every command with `export ANDROID_HOME=$HOME/Library/Android/sdk &&`.
- Devices: Gradle Managed Devices only, and only through `:app:android-main:smokeDevicesGroupDebugAndroidTest -Plogdate.androidTestClass=<fqcn>`; never `connected*AndroidTest`, `install*`, `uninstall*` or any `adb` command (a physical phone may be attached); never start, stop or kill an emulator by hand. Check `uptime` first; if the managed device reports "Process crashed" or "failed to complete startup", rerun once with the machine otherwise idle.
- Screenshots: record with `-Dlogdate.desktopScreenshots.sceneFilter=<prefix> :app:compose-main:updateDesktopScreenshotTest --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared*'`, validate with the same filter on `:app:compose-main:desktopTest`, and open every new or changed PNG with the Read tool before committing.
- Test-first: write the test, add the API with inert bodies only when the test would not otherwise compile, watch it fail on its assertions, then implement. Inert bodies never reach a commit.
- Commits: Conventional Commits, title at most 72 characters, scope from `allowed-scopes.txt` (this plan uses `theme`, `settings`, `database`, `sync`, `export`, `timeline`, `reminders`); never the `style` type and never `build`, `android`, `ios`, `desktop` or `infra` as a scope; `feat`/`fix` only for behavior a user can see (Tasks 1, 4 and 8), `chore` otherwise; bodies are full sentences wrapped at 72 characters, `feat`/`fix` bodies name no classes; the last line is `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Commit mechanics: the message is written by its own command (a quoted heredoc into `/private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/<name>.txt`); the next command is the single chain `git restore --staged . && git add <exact paths> && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F <file>`. Never `git add -A`, `git add .`, `git commit -a`, `-m` or `--no-verify`; never push; leave `.github/workflows/` untouched.
- Every commit leaves the branch shippable: the touched modules' tests, `ktlintCheck` and `detekt` pass before the commit step.

---

## What the code shows today (verified on `claude/wearos-android-ux-redesign-081dc0` at `17bcbc04b`, rebased onto `origin/main` `100340fa6`)

| Item | Finding | Where |
|---|---|---|
| Fallback colors | `LightColorScheme`/`DarkColorScheme` set only `primary`, `secondary`, `tertiary` from the template `Purple40/80`, `PurpleGrey40/80`, `Pink40/80`; every other role is Material's purple-tinted baseline. Desktop and iOS always use them; Android uses them below API 31 or with dynamic color off. `client/theme` has no test source set. | `client/theme/src/commonMain/kotlin/app/logdate/ui/theme/Color.kt:1-11`, `Theme.kt:18-40`, `desktopMain/.../Theme.desktop.kt:16-19`, `androidMain/.../Theme.android.kt:20-32`, `client/theme/build.gradle.kts:41-59` |
| Material 3 API | The cached `material3-desktop-1.11.0-alpha07` jar has a 48-argument `lightColorScheme` whose parameters (from its `LocalVariableTable`) end with `primaryFixed`, `primaryFixedDim`, `onPrimaryFixed`, `onPrimaryFixedVariant` and the secondary and tertiary equivalents. | `~/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material3/material3-desktop/1.11.0-alpha07/` |
| Desktop baselines | 329 PNGs in `app/compose-main/src/desktopTest/reference`; every scene renders with `LogDateTheme(dynamicColor = false)`. The 20 `memory-selection-*` baselines are skipped by both validation and update (`nonDeterministicScenePrefixes`). Android preview screenshots use `LogDateTheme()` with dynamic color on and no `apiLevel`, so they render dynamic color. | `app/compose-main/src/desktopTest/kotlin/app/logdate/screenshots/DesktopScreenshotTest.kt:50-75, 116-133`; `app/android-main/src/screenshotTest/.../common/ScreenshotTheme.kt:36` |
| Shared state | `StateDocument.shared: Map<String, Stamped>`; `StateMerger.merge` returns merged values only (`mergeShared` is private); ties go to the phone; a stamp from the future counts as now. `PhoneStatePublisher` publishes `PhoneState(flags, zone)` with no `features` and no `shared`, and toggles the capability on every publish. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateDocument.kt:16-104`, `StateMerger.kt:19-63`; `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt:26-72` |
| Watch preferences | Only `WatchSettingsRepository` (entry notifications). `FakeKeyValueStorage` is an `internal` test double in `:client:domain` `commonTest`. `domainModule` binds repositories with `single<…> { Default…(get()) }`; `:app:wear` does not include `domainModule`. | `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchSettings.kt`; `client/domain/src/commonTest/.../recommendation/DefaultAmbientPromptHistoryRepositoryTest.kt:98`; `client/domain/.../di/DomainModule.kt:280-293`; `app/wear/.../di/WearDataModule.kt:79-85` |
| Watch status | `WatchConnectionState.Connected(watchName, lastSynced, pendingCount)` and `OutOfRange(watchName, lastSynced)`; the resolver always passes `lastSynced = null, pendingCount = 0`, so a connected watch always reads "Not yet synced". `WatchSettingsViewModel` exposes `connectionState` only; `watchSettingsModule` is included only on Android. | `client/feature/core/.../settings/ui/watch/WatchConnectionState.kt:39-53`; `app/compose-main/.../client/watch/WatchConnectionStateResolver.kt:68-114`; `.../WatchSettingsScreen.kt:452-481`; `client/feature/core/.../di/WatchSettingsModule.kt:17-21` |
| Peer state | `PeerStateStore.latest(role)`, `features(role)`; `WearProtocolGate(flag, peerCapability, peerFeatures).isV2Active()`; the watch currently publishes `WatchState()` defaults (no real counts) and lists no features. | `client/sync/.../peer/state/PeerStateStore.kt:46-60`; `.../datalayer/v2/WearProtocolGate.kt`; `app/wear/.../sync/v2/WatchStateMirror.kt:63-66`; bindings in `app/compose-main/.../di/AppModule.android.kt:219-243` |
| Note model | `JournalNote` (sealed, `@Serializable`) has `timeZoneId` as the precedent for a once-written capture fact (`withTimeZoneId`, `JournalNoteConflictResolver.withZoneIfMissing`, `SyncDownloader.uploadedFields` sets it to null before comparing). Four note tables share `GenericNoteData`. Room is at 53 (`MIGRATION_52_53` is the newest). The only JSON user of `JournalNote.serializer()` is `NoteDataMapper` (`ignoreUnknownKeys`, `encodeDefaults`). | `client/repository/.../journals/JournalNotesRepository.kt:243-342`; `client/sync/.../conflict/JournalNoteConflictResolver.kt:16-38`; `client/sync/.../SyncDownloader.kt:274-391`; `client/database/.../LogDateDatabase.kt:228, 434-441`; `client/sync/.../datalayer/NoteDataMapper.kt:15-42` |
| Encrypted fields | Each note field is encrypted on its own with `SyncPayloadCipher.encryptString("sync:note:<id>:<field>", …)` (`LDSE2:` prefix) and sent as its own DTO field; decryption passes legacy plaintext through. The server stores `photoPresentation` and `transcript` as opaque strings through `LogDateEntry`, `toRepoJson`/`toEntryRepoJson`/`toLogDateEntry`, the PUT and PATCH content routes and `toContentChange`; `SchemaDocumentationTest` fails for any undocumented DTO field. | `client/sync/.../cloud/CloudContentDataSource.kt:144-368`; `shared/model/.../sync/SyncModels.kt:48-147`; `server/.../logdate/LogDateCollectionsRepository.kt:14-31`, `LogDateCollectionRecords.kt:145-224`, `routes/sync/SyncCollectionRoutes.kt:77-90, 187-200`, `routes/sync/SyncHelpers.kt:398-413`, `openapi/schemadocs/SyncSchemaDocs.kt:80-162`; `server/src/test/.../openapi/SchemaDocumentationTest.kt` |
| Archives | 2.0 archives write `ArchiveNote` (optional fields, nulls omitted) and a schema per file; `ArchiveSchemaValidationTest` validates the maximal `ArchiveSamples.note` against a strict copy that rejects unlisted fields. Restore maps `ArchiveNote` → `ExportNote` → `JournalNote`; it does not verify checksums. | `client/domain/.../export/archive/ArchiveData.kt:36-57`, `ArchiveRecords.kt:85-117`, `ArchiveSchemas.kt:139-166`; `client/domain/src/jvmTest/.../ArchiveSchemaValidationTest.kt:53-72`; `restore/V2RestoreBundle.kt:145-170`, `RestoreUserDataUseCase.kt:484-539` |
| Ingest | `WatchNoteIngestor` decodes watch notes with `NoteDataMapper.fromDataMap` in `onNoteMetadata` and again in `storeIfComplete`; two tests verify `create(note)` with the exact decoded note. | `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt:118-176`; `app/android-main/src/test/.../WatchNoteIngestorTest.kt:181-204` |
| Timeline UI | `NoteUiState` (four data classes) and `MomentUiState` live in `client/ui`; Home maps notes and moments in `HomeTimelineMapping.kt`, the Timeline in `TimelineViewModel.kt`; day detail rows render a `labelSmall` timestamp above each card (`NotesListSection.kt`), audio through `AudioNoteSnippet` → `MomentAudioCard`; moment rows render `MomentLabel` in three containers. `Moment.sourceNotes` lists the notes behind a moment. | `client/ui/.../timeline/HomeTimelineUiState.kt:174-213, 90-127`; `client/feature/core/.../main/HomeTimelineMapping.kt:62-143`; `client/feature/timeline/.../TimelineViewModel.kt:232-351`; `.../details/NotesListSection.kt`, `AudioNoteSnippet.kt`; `client/ui/.../newstuff/MomentComposables.kt:124-386`; `client/domain/.../timeline/Moment.kt:14-54` |
| Journal and editor | Journal entries render their time through `VerticalEntryCardShell`/`InlineEntryCardShell`; `JournalDetailViewModel.toDisplayData` is private. The editor loads an existing entry through `ContentLoader.loadEntry` and shows `VisitMemoryContextBanner` above the content; `EditorState` is a hand-written `@Stable` class with `copy`, `equals`, `hashCode`. | `client/feature/journal/.../detail/JournalEntryCardShells.kt:42-130`, `JournalDetailViewModel.kt:157-196`; `client/feature/editor/.../editor/delegate/ContentLoader.kt:20-30`, `EntryEditorContent.kt:271-273`, `editor/EditorState.kt:37-223` |
| Glyph | `LogDateGlyph.WATCH.vector()` exists in `client/ui` with `glyph_watch.xml`. | `client/ui/src/commonMain/kotlin/app/logdate/ui/glyphs/LogDateGlyph.kt` |
| Flag at the root | `LogDateNavDisplay` already reads `HOME_WORKSPACE_V2` from `FeatureFlagStore` and provides it as `LocalWorkspaceEnabled`. | `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/LogDateNavDisplay.kt:133-135, 191-200` |
| Marked moments | `marked_moments` and `MarkedMomentDao` (`upsert`, `getById`, `observeByState`) exist and are bound in `DaosModule`; nothing reads them. Location history offers `getLocationHistoryBetween(start, end)` with `accuracyMeters`, `isGenuine`, `isMock`. Repositories are bound per platform in `DataModule.{android,desktop,ios}.kt`. | `client/database/.../entities/moments/MarkedMomentEntity.kt`, `dao/moments/MarkedMomentDao.kt`, `DaosModule.kt:32`; `client/repository/.../location/LocationHistoryRepository.kt:55-59, 157-174`; `client/data/src/*Main/.../di/DataModule.*.kt` |
| Streak day | `Instant.toStreakDay(zone, dayStartHour)`, `nextStreakDayStart`, `DEFAULT_STREAK_DAY_START_HOUR = 4`; `FirePhase.EMBERS` means the last logged day was two days ago. There is no reminders package. | `client/domain/.../streak/StreakDay.kt`, `CampfireState.kt`, `CampfireCalculator.kt` |

## File Structure

| File | Task | Responsibility |
|---|---|---|
| `client/theme/src/commonMain/kotlin/app/logdate/ui/theme/Color.kt` | 1 | Graphite tonal palette |
| `client/theme/src/commonMain/kotlin/app/logdate/ui/theme/Theme.kt` | 1 | Full light and dark fallback schemes |
| `client/theme/src/commonTest/kotlin/app/logdate/ui/theme/FallbackColorSchemeTest.kt` | 1 | Neutrality and contrast of the fallback |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateMerger.kt` | 2 | `mergeStamped` keeps the winning stamps |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/SharedPreferenceKey.kt` | 2 | Typed keys, defaults, validation |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/StampedPreferenceStore.kt` | 2 | Device copy of `shared`, stamping, adopting a peer's newer entries |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchPreferencesRepository.kt` | 2 | Read and write typed watch preferences |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt` | 2 | Publishes `shared` and `prefs.v1`; adopts the watch's newer entries |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatus.kt` | 3 | Sealed status and its lines |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusResolver.kt` | 3 | Connection + the watch's report → `WatchStatus` |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/PeerStateWatchSnapshotSource.kt` | 3 | Reads the watch's report from `PeerStateStore` |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusCard.kt` | 4 | The status card, moved out of `WatchSettingsScreen.kt` |
| `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/WatchSettingsScreenshotScenes.kt` | 4 | Watch settings scenes, moved out of the catalog |
| `client/repository/src/commonMain/kotlin/app/logdate/client/repository/journals/CaptureDevice.kt` | 5 | `CaptureDevice`, its lenient serializer, `withCapturedOn` |
| `client/database/src/commonMain/kotlin/app/logdate/client/database/migrations/MIGRATION_53_54.kt` | 5 | `captured_on` on the four note tables |
| `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchIngestorTestDoubles.kt` | 5 | Test doubles moved out of `WatchNoteIngestorTest.kt` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt` | 6 | Encrypts and decrypts `capturedOn` |
| `server/.../logdate/*`, `server/.../routes/sync/*`, `server/.../openapi/schemadocs/SyncSchemaDocs.kt` | 6 | Opaque pass-through and its docs |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/*`, `export/ExportModels.kt`, `restore/*` | 7 | `capturedOn` in archives, both versions of restore |
| `client/ui/src/commonMain/kotlin/app/logdate/ui/provenance/CapturedOnWatch.kt` | 8 | `LocalWatchProvenanceVisible`, `CapturedOnWatchGlyph`, `CaptureTimeLabel`, `CapturedOnWatchBanner` |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/timeline/MomentProvenance.kt` | 8 | `Moment.capturedOnWatch()` |
| `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/EntryDisplayMapping.kt` | 8 | `JournalNote.toEntryDisplayData`, moved out of the view model |
| `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/ProvenanceScreenshotScenes.kt` | 8 | The provenance scene |
| `client/repository/src/commonMain/kotlin/app/logdate/client/repository/moments/MarkedMoment.kt` | 9 | `MarkedMoment`, `MarkState`, `MarkLocation`, `MarkedMomentRepository` |
| `client/data/src/commonMain/kotlin/app/logdate/client/data/moments/OfflineFirstMarkedMomentRepository.kt` | 9 | Room-backed repository |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/moments/EnrichMarkLocationUseCase.kt` | 9 | Place from location history within ±10 min |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/ReminderModels.kt` | 10 | Inputs, triggers, decisions, waking hours |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/ReminderOrchestrator.kt` | 10 | The pure decision |

## Task Map

| Task | Item | Commit title |
|---|---|---|
| 1 | C12 | `feat(theme): use a neutral graphite palette without dynamic color` |
| 2 | C2 | `chore(settings): share watch preferences with the paired watch` |
| 3 | C3b model | `chore(settings): describe the watch from what it reports` |
| 4 | C3b screen | `fix(settings): show watch sync status only when the watch reports it` |
| 5 | D1 storage | `chore(database): record which device captured each moment` |
| 6 | D1 sync | `chore(sync): sync which device captured a moment, encrypted` |
| 7 | D1 archives | `chore(export): include which device captured a moment in archives` |
| 8 | D1 timeline | `feat(timeline): mark moments captured on the watch` |
| 9 | D2 | `chore(timeline): read marked moments and fill in their place` |
| 10 | R1 | `chore(reminders): decide when a moment-aware reminder may ring` |

Dependencies flow forward: 3 uses 2 (`SharedPreferenceKey.WindowDays`); 4 uses 3; 6, 7 and 8 use 5; 9 uses 2 (`SharedPreferenceKey.CapturePlaces`). Task 10 is independent. Task 1 goes first so every later scene is recorded with the final palette. Line numbers are as of `17bcbc04b`; for a file an earlier task already edited, find the code by the quoted text.

---
### Task 1: Use a neutral graphite palette without dynamic color (C12)

**Files:**
- Modify: `client/theme/build.gradle.kts:41-59` (add a `commonTest` dependency block)
- Modify: `client/theme/src/commonMain/kotlin/app/logdate/ui/theme/Color.kt` (replace the whole file)
- Modify: `client/theme/src/commonMain/kotlin/app/logdate/ui/theme/Theme.kt:18-40`
- Modify: `client/theme/src/iosMain/kotlin/app/logdate/ui/theme/PlatformColors.ios.kt:17-18` (KDoc that names the purple)
- Test: `client/theme/src/commonTest/kotlin/app/logdate/ui/theme/FallbackColorSchemeTest.kt`
- Modify (regenerated): `app/compose-main/src/desktopTest/reference/*.png` (every scene except the quarantined `memory-selection-*`, plus `lock-screen.png`)

**Interfaces:**
- Consumes: `androidx.compose.material3.lightColorScheme`/`darkColorScheme` (48-argument overload with fixed roles), `androidx.compose.ui.graphics.luminance`.
- Produces: `val LightColorScheme: ColorScheme` and `val DarkColorScheme: ColorScheme` (same names and call sites as today); `val Graphite{Primary,Secondary,Tertiary}{10,20,30,40,80,90,100}`, `val GraphiteNeutral{0,4,6,10,12,17,20,22,24,87,90,92,94,95,96,98,100}`, `val GraphiteNeutralVariant{30,50,60,80,90}: Color`. Every later task's screenshot is recorded against these schemes.

- [ ] **Step 1: Give `client/theme` a test source set**

In `client/theme/build.gradle.kts`, after the `desktopMain.dependencies { … }` block inside `sourceSets`, add:

```kotlin
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
```

- [ ] **Step 2: Write the failing test**

Create `client/theme/src/commonTest/kotlin/app/logdate/ui/theme/FallbackColorSchemeTest.kt`:

```kotlin
package app.logdate.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests the color schemes LogDate uses when dynamic color is unavailable: desktop, iOS, Android 11,
 * and anyone who turns dynamic color off.
 *
 * They are graphite: every role except the error colors is nearly gray, so the template purple and
 * pink cannot come back unnoticed, and every text color meets the WCAG AA minimum on its surface.
 */
class FallbackColorSchemeTest {
    @Test
    fun `the light fallback is graphite outside the error colors`() {
        assertNeutral("light", LightColorScheme)
    }

    @Test
    fun `the dark fallback is graphite outside the error colors`() {
        assertNeutral("dark", DarkColorScheme)
    }

    @Test
    fun `light fallback text is readable on its surface`() {
        assertReadable("light", LightColorScheme)
    }

    @Test
    fun `dark fallback text is readable on its surface`() {
        assertReadable("dark", DarkColorScheme)
    }

    @Test
    fun `the template purple is gone`() {
        assertNotEquals(Color(0xFF6650A4), LightColorScheme.primary)
        assertNotEquals(Color(0xFFD0BCFF), DarkColorScheme.primary)
    }

    private fun assertNeutral(
        name: String,
        scheme: ColorScheme,
    ) {
        val tinted = scheme.nonErrorRoles().filterValues { channelSpread(it) > MAX_CHANNEL_SPREAD }
        assertTrue(tinted.isEmpty(), "$name roles with a strong hue: ${tinted.keys}")
    }

    private fun assertReadable(
        name: String,
        scheme: ColorScheme,
    ) {
        val unreadable = scheme.textPairs().filterValues { (text, surface) -> contrast(text, surface) < MIN_CONTRAST }
        assertTrue(unreadable.isEmpty(), "$name pairs below the contrast minimum: ${unreadable.keys}")
    }

    private fun channelSpread(color: Color): Int {
        val channels = listOf(color.red, color.green, color.blue).map { (it * 255).roundToInt() }
        return channels.max() - channels.min()
    }

    private fun contrast(
        first: Color,
        second: Color,
    ): Double {
        val lighter = max(first.luminance(), second.luminance())
        val darker = min(first.luminance(), second.luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun ColorScheme.nonErrorRoles(): Map<String, Color> =
        mapOf(
            "primary" to primary,
            "onPrimary" to onPrimary,
            "primaryContainer" to primaryContainer,
            "onPrimaryContainer" to onPrimaryContainer,
            "inversePrimary" to inversePrimary,
            "secondary" to secondary,
            "onSecondary" to onSecondary,
            "secondaryContainer" to secondaryContainer,
            "onSecondaryContainer" to onSecondaryContainer,
            "tertiary" to tertiary,
            "onTertiary" to onTertiary,
            "tertiaryContainer" to tertiaryContainer,
            "onTertiaryContainer" to onTertiaryContainer,
            "background" to background,
            "onBackground" to onBackground,
            "surface" to surface,
            "onSurface" to onSurface,
            "surfaceVariant" to surfaceVariant,
            "onSurfaceVariant" to onSurfaceVariant,
            "surfaceTint" to surfaceTint,
            "inverseSurface" to inverseSurface,
            "inverseOnSurface" to inverseOnSurface,
            "outline" to outline,
            "outlineVariant" to outlineVariant,
            "scrim" to scrim,
            "surfaceBright" to surfaceBright,
            "surfaceDim" to surfaceDim,
            "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainerLowest" to surfaceContainerLowest,
            "primaryFixed" to primaryFixed,
            "primaryFixedDim" to primaryFixedDim,
            "onPrimaryFixed" to onPrimaryFixed,
            "onPrimaryFixedVariant" to onPrimaryFixedVariant,
            "secondaryFixed" to secondaryFixed,
            "secondaryFixedDim" to secondaryFixedDim,
            "onSecondaryFixed" to onSecondaryFixed,
            "onSecondaryFixedVariant" to onSecondaryFixedVariant,
            "tertiaryFixed" to tertiaryFixed,
            "tertiaryFixedDim" to tertiaryFixedDim,
            "onTertiaryFixed" to onTertiaryFixed,
            "onTertiaryFixedVariant" to onTertiaryFixedVariant,
        )

    private fun ColorScheme.textPairs(): Map<String, Pair<Color, Color>> =
        mapOf(
            "onPrimary on primary" to (onPrimary to primary),
            "onPrimaryContainer on primaryContainer" to (onPrimaryContainer to primaryContainer),
            "onSecondary on secondary" to (onSecondary to secondary),
            "onSecondaryContainer on secondaryContainer" to (onSecondaryContainer to secondaryContainer),
            "onTertiary on tertiary" to (onTertiary to tertiary),
            "onTertiaryContainer on tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
            "onBackground on background" to (onBackground to background),
            "onSurface on surface" to (onSurface to surface),
            "onSurfaceVariant on surfaceVariant" to (onSurfaceVariant to surfaceVariant),
            "inverseOnSurface on inverseSurface" to (inverseOnSurface to inverseSurface),
            "onSurface on surfaceContainerHighest" to (onSurface to surfaceContainerHighest),
            "onSurfaceVariant on surfaceContainer" to (onSurfaceVariant to surfaceContainer),
            "onPrimaryFixed on primaryFixed" to (onPrimaryFixed to primaryFixed),
            "onPrimaryFixedVariant on primaryFixed" to (onPrimaryFixedVariant to primaryFixed),
        )

    private companion object {
        /** The largest difference between two RGB channels, out of 255, that still reads as gray. */
        const val MAX_CHANNEL_SPREAD = 32

        /** WCAG 2.2 AA for body text. */
        const val MIN_CONTRAST = 4.5
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:theme:desktopTest --tests 'app.logdate.ui.theme.FallbackColorSchemeTest' --console=plain
```
Expected: FAIL. `the light fallback is graphite outside the error colors` lists `primary` (the template `0xFF6650A4` has a channel spread of 84) and the baseline lavender surfaces; `the template purple is gone` fails on `LightColorScheme.primary`.

- [ ] **Step 4: Replace the template colors with the graphite palette**

Replace the whole of `client/theme/src/commonMain/kotlin/app/logdate/ui/theme/Color.kt` with:

```kotlin
package app.logdate.ui.theme

import androidx.compose.ui.graphics.Color

// Graphite: the tonal palettes behind LogDate's fallback color schemes, used wherever dynamic color
// is unavailable. Each value is the CIELAB color at lightness L* = tone with a slight cool tint
// (hue 265°; chroma 10 for primary, 5 for secondary, 1.5 for neutral, 3.5 for neutral variant) and
// a blue-green tint for tertiary (hue 205°, chroma 8), clamped to sRGB. Tones 0 and 100 are black
// and white. Orange stays out of the palette: the campfire draws its own flame colors.

val GraphitePrimary10 = Color(0xFF111C29)
val GraphitePrimary20 = Color(0xFF26313F)
val GraphitePrimary30 = Color(0xFF3C4856)
val GraphitePrimary40 = Color(0xFF545F6E)
val GraphitePrimary80 = Color(0xFFBBC8D9)
val GraphitePrimary90 = Color(0xFFD7E4F5)
val GraphitePrimary100 = Color(0xFFFFFFFF)

val GraphiteSecondary10 = Color(0xFF171C22)
val GraphiteSecondary20 = Color(0xFF2C3137)
val GraphiteSecondary30 = Color(0xFF42474E)
val GraphiteSecondary40 = Color(0xFF595F66)
val GraphiteSecondary80 = Color(0xFFC1C7D0)
val GraphiteSecondary90 = Color(0xFFDDE3EC)
val GraphiteSecondary100 = Color(0xFFFFFFFF)

val GraphiteTertiary10 = Color(0xFF0B1E20)
val GraphiteTertiary20 = Color(0xFF203435)
val GraphiteTertiary30 = Color(0xFF364A4C)
val GraphiteTertiary40 = Color(0xFF4E6263)
val GraphiteTertiary80 = Color(0xFFB4CBCC)
val GraphiteTertiary90 = Color(0xFFD0E7E8)
val GraphiteTertiary100 = Color(0xFFFFFFFF)

val GraphiteNeutral0 = Color(0xFF000000)
val GraphiteNeutral4 = Color(0xFF0C0E11)
val GraphiteNeutral6 = Color(0xFF121315)
val GraphiteNeutral10 = Color(0xFF1A1C1D)
val GraphiteNeutral12 = Color(0xFF1E2022)
val GraphiteNeutral17 = Color(0xFF292A2C)
val GraphiteNeutral20 = Color(0xFF2F3032)
val GraphiteNeutral22 = Color(0xFF333537)
val GraphiteNeutral24 = Color(0xFF38393B)
val GraphiteNeutral87 = Color(0xFFD8DADD)
val GraphiteNeutral90 = Color(0xFFE1E3E5)
val GraphiteNeutral92 = Color(0xFFE6E8EB)
val GraphiteNeutral94 = Color(0xFFECEEF1)
val GraphiteNeutral95 = Color(0xFFEFF1F3)
val GraphiteNeutral96 = Color(0xFFF2F4F6)
val GraphiteNeutral98 = Color(0xFFF8F9FC)
val GraphiteNeutral100 = Color(0xFFFFFFFF)

val GraphiteNeutralVariant30 = Color(0xFF43474C)
val GraphiteNeutralVariant50 = Color(0xFF73777D)
val GraphiteNeutralVariant60 = Color(0xFF8D9197)
val GraphiteNeutralVariant80 = Color(0xFFC3C7CD)
val GraphiteNeutralVariant90 = Color(0xFFDEE3E9)
```

In `client/theme/src/commonMain/kotlin/app/logdate/ui/theme/Theme.kt`, replace lines 18-40 (from the KDoc `The default light color scheme for the LogDate app.` through the closing `)` of `DarkColorScheme`) with:

```kotlin
/**
 * The light color scheme for the LogDate app when dynamic color is unavailable (Android 11, desktop,
 * iOS, or dynamic color turned off).
 *
 * Every role is set from the graphite palettes in `Color.kt`, following Material 3's tone mapping,
 * so no Material baseline purple shows through. Error roles stay at the Material defaults.
 */
val LightColorScheme =
    lightColorScheme(
        primary = GraphitePrimary40,
        onPrimary = GraphitePrimary100,
        primaryContainer = GraphitePrimary90,
        onPrimaryContainer = GraphitePrimary30,
        inversePrimary = GraphitePrimary80,
        secondary = GraphiteSecondary40,
        onSecondary = GraphiteSecondary100,
        secondaryContainer = GraphiteSecondary90,
        onSecondaryContainer = GraphiteSecondary30,
        tertiary = GraphiteTertiary40,
        onTertiary = GraphiteTertiary100,
        tertiaryContainer = GraphiteTertiary90,
        onTertiaryContainer = GraphiteTertiary30,
        background = GraphiteNeutral98,
        onBackground = GraphiteNeutral10,
        surface = GraphiteNeutral98,
        onSurface = GraphiteNeutral10,
        surfaceVariant = GraphiteNeutralVariant90,
        onSurfaceVariant = GraphiteNeutralVariant30,
        surfaceTint = GraphitePrimary40,
        inverseSurface = GraphiteNeutral20,
        inverseOnSurface = GraphiteNeutral95,
        outline = GraphiteNeutralVariant50,
        outlineVariant = GraphiteNeutralVariant80,
        scrim = GraphiteNeutral0,
        surfaceBright = GraphiteNeutral98,
        surfaceContainer = GraphiteNeutral94,
        surfaceContainerHigh = GraphiteNeutral92,
        surfaceContainerHighest = GraphiteNeutral90,
        surfaceContainerLow = GraphiteNeutral96,
        surfaceContainerLowest = GraphiteNeutral100,
        surfaceDim = GraphiteNeutral87,
        primaryFixed = GraphitePrimary90,
        primaryFixedDim = GraphitePrimary80,
        onPrimaryFixed = GraphitePrimary10,
        onPrimaryFixedVariant = GraphitePrimary30,
        secondaryFixed = GraphiteSecondary90,
        secondaryFixedDim = GraphiteSecondary80,
        onSecondaryFixed = GraphiteSecondary10,
        onSecondaryFixedVariant = GraphiteSecondary30,
        tertiaryFixed = GraphiteTertiary90,
        tertiaryFixedDim = GraphiteTertiary80,
        onTertiaryFixed = GraphiteTertiary10,
        onTertiaryFixedVariant = GraphiteTertiary30,
    )

/**
 * The dark color scheme for the LogDate app when dynamic color is unavailable.
 *
 * Built from the same graphite palettes as [LightColorScheme] with Material 3's dark tone mapping.
 */
val DarkColorScheme =
    darkColorScheme(
        primary = GraphitePrimary80,
        onPrimary = GraphitePrimary20,
        primaryContainer = GraphitePrimary30,
        onPrimaryContainer = GraphitePrimary90,
        inversePrimary = GraphitePrimary40,
        secondary = GraphiteSecondary80,
        onSecondary = GraphiteSecondary20,
        secondaryContainer = GraphiteSecondary30,
        onSecondaryContainer = GraphiteSecondary90,
        tertiary = GraphiteTertiary80,
        onTertiary = GraphiteTertiary20,
        tertiaryContainer = GraphiteTertiary30,
        onTertiaryContainer = GraphiteTertiary90,
        background = GraphiteNeutral6,
        onBackground = GraphiteNeutral90,
        surface = GraphiteNeutral6,
        onSurface = GraphiteNeutral90,
        surfaceVariant = GraphiteNeutralVariant30,
        onSurfaceVariant = GraphiteNeutralVariant80,
        surfaceTint = GraphitePrimary80,
        inverseSurface = GraphiteNeutral90,
        inverseOnSurface = GraphiteNeutral20,
        outline = GraphiteNeutralVariant60,
        outlineVariant = GraphiteNeutralVariant30,
        scrim = GraphiteNeutral0,
        surfaceBright = GraphiteNeutral24,
        surfaceContainer = GraphiteNeutral12,
        surfaceContainerHigh = GraphiteNeutral17,
        surfaceContainerHighest = GraphiteNeutral22,
        surfaceContainerLow = GraphiteNeutral10,
        surfaceContainerLowest = GraphiteNeutral4,
        surfaceDim = GraphiteNeutral6,
        primaryFixed = GraphitePrimary90,
        primaryFixedDim = GraphitePrimary80,
        onPrimaryFixed = GraphitePrimary10,
        onPrimaryFixedVariant = GraphitePrimary30,
        secondaryFixed = GraphiteSecondary90,
        secondaryFixedDim = GraphiteSecondary80,
        onSecondaryFixed = GraphiteSecondary10,
        onSecondaryFixedVariant = GraphiteSecondary30,
        tertiaryFixed = GraphiteTertiary90,
        tertiaryFixedDim = GraphiteTertiary80,
        onTertiaryFixed = GraphiteTertiary10,
        onTertiaryFixedVariant = GraphiteTertiary30,
    )
```

In `client/theme/src/iosMain/kotlin/app/logdate/ui/theme/PlatformColors.ios.kt`, replace the two KDoc lines

```kotlin
 * Brand colors (primary / tint) intentionally stay null so the LogDate purple keeps its role as
 * the brand accent.
```

with

```kotlin
 * Brand colors (primary / tint) intentionally stay null so the graphite primary from
 * [LightColorScheme] / [DarkColorScheme] stays the accent.
```

- [ ] **Step 5: Run the tests and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:theme:desktopTest --tests 'app.logdate.ui.theme.FallbackColorSchemeTest' :client:theme:compileKotlinIosSimulatorArm64 :client:theme:compileTestKotlinIosSimulatorArm64 :client:theme:ktlintCheck :client:theme:detekt --console=plain
```
Expected: PASS, 5 tests; BUILD SUCCESSFUL. (The palette's largest channel spread is 30, on `GraphitePrimary80` and `GraphitePrimary90`; the lowest text contrast is 5.5:1, dark `onSurfaceVariant` on `surfaceVariant`.)

- [ ] **Step 6: Re-record every desktop baseline**

Run (no scene filter, so every scene and the lock screen render again):
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:compose-main:updateDesktopScreenshotTest --tests 'app.logdate.screenshots.DesktopScreenshotTest' --console=plain
```
Expected: BUILD SUCCESSFUL. Then run `git status --short app/compose-main/src/desktopTest/reference`: every line is ` M` on a `.png` (about 300 files), no file is added or deleted, and no `memory-selection-*` file is listed (those scenes are quarantined and keep their old palette until the staggered-grid fix lands).

- [ ] **Step 7: Validate the new baselines**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:compose-main:desktopTest --tests 'app.logdate.screenshots.DesktopScreenshotTest' --console=plain
```
Expected: PASS, 2 tests.

- [ ] **Step 8: Look at the new palette**

Open each of these with the Read tool: `app/compose-main/src/desktopTest/reference/settings-overview--phone.png`, `settings-overview--phone-dark.png`, `onboarding-start-landing--phone.png`, `campfire-streak-embers--phone.png`, `search-results--phone-dark.png`, `labs-settings--phone.png`, `watch-settings--tablet.png`, `rewind-detail-populated--desktop.png` and `lock-screen.png`. Check: backgrounds and cards are neutral gray-white (light) or near-black gray (dark) with no lavender cast; filled buttons and switches are slate gray with white or near-black labels; every label is legible; the campfire flame is still orange. If a scene shows a hard-coded purple that the scheme does not control, note its file in the commit body instead of changing that scene here.

- [ ] **Step 9: Confirm Android previews are unaffected**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:validateDebugScreenshotTest --console=plain
```
Expected: PASS. Android previews render with dynamic color (`ScreenshotTheme` calls `LogDateTheme()` with its default `dynamicColor = true`, and no preview pins an `apiLevel` below 31), so the fallback schemes do not reach them. If it fails, open the report the task's output links to and stop before committing: a preview that renders the fallback means a scene sets `dynamicColor = false`, which this task does not expect, and the person reviewing the plan decides whether those references change in this commit.

- [ ] **Step 10: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task01-graphite.txt <<'EOF'
feat(theme): use a neutral graphite palette without dynamic color

Where LogDate cannot use colors from the wallpaper, it now uses a calm
graphite palette instead of the purple and pink it started with. This
covers the desktop app, iOS, Android 11, and anyone who turns dynamic
colors off. Backgrounds, cards and buttons are near-neutral grays with
a slight cool tint, every text color meets the WCAG AA contrast
minimum on its background, and the campfire's flame colors do not
change.

Phones that use wallpaper colors look the same as before.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 11: Commit**

Run `git status --short` first: the only changes are the five theme files and `.png` files under `app/compose-main/src/desktopTest/reference`. Then:

```bash
git restore --staged . && git add client/theme/build.gradle.kts client/theme/src/commonMain/kotlin/app/logdate/ui/theme/Color.kt client/theme/src/commonMain/kotlin/app/logdate/ui/theme/Theme.kt client/theme/src/iosMain/kotlin/app/logdate/ui/theme/PlatformColors.ios.kt client/theme/src/commonTest/kotlin/app/logdate/ui/theme/FallbackColorSchemeTest.kt app/compose-main/src/desktopTest/reference && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task01-graphite.txt
```

---
### Task 2: Share watch preferences with the paired watch (C2)

**Files:**
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateMerger.kt:31-51`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateMergerTest.kt` (three tests before the private helpers)
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/SharedPreferenceKey.kt`
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/StampedPreferenceStore.kt`
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchPreferencesRepository.kt`
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/di/DomainModule.kt:292` (two bindings after the day boundary repository)
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/watch/StampedPreferenceStoreTest.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/watch/WatchPreferencesRepositoryTest.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt` (whole file)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt:228-235` (the `PhoneStatePublisher` binding)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt`

**Interfaces:**
- Consumes: `Stamped(value: JsonElement, at: Long)`, `PeerRole`, `StateMerger(clock)`, `WearJson.json`, `WearFeatures.PREFS`, `PeerStateStore.latest(role): Flow<StateDocument?>` (all `client/sync`); `KeyValueStorage` (`client/datastore`); `FakeKeyValueStorage` (`internal`, `client/domain` `commonTest`, package `app.logdate.client.domain.recommendation`).
- Produces:
  - `fun StateMerger.mergeStamped(phone: Map<String, Stamped>, watch: Map<String, Stamped>): Map<String, Stamped>`
  - `class SharedPreferenceKey<T>(val name: String, val default: T, serializer: KSerializer<T>, allowed: (T) -> Boolean = { true }) { fun isAllowed(value: T): Boolean; fun encode(value: T): JsonElement; fun read(stamped: Stamped?): T; companion object { val RemindersEnabled: SharedPreferenceKey<Boolean>; val CapturePlaces: SharedPreferenceKey<Boolean>; val MoodAfterRecording: SharedPreferenceKey<Boolean>; val WindowDays: SharedPreferenceKey<Int>; val WindowOnThisDay: SharedPreferenceKey<Boolean>; val WindowRewind: SharedPreferenceKey<Boolean>; val WINDOW_DAY_CHOICES: Set<Int>; val ALL: List<SharedPreferenceKey<*>> } }`
  - `class StampedPreferenceStore(storage: KeyValueStorage, localRole: PeerRole, merger: StateMerger = StateMerger(), clock: Clock = Clock.System) { fun observe(): Flow<Map<String, Stamped>>; suspend fun snapshot(): Map<String, Stamped>; suspend fun write(name: String, value: JsonElement): Stamped; suspend fun adopt(peer: Map<String, Stamped>): Boolean; companion object { const val STORAGE_KEY = "watch_shared_preferences_v1" } }`
  - `interface WatchPreferencesRepository { fun <T> observe(key: SharedPreferenceKey<T>): Flow<T>; suspend fun <T> get(key: SharedPreferenceKey<T>): T; suspend fun <T> set(key: SharedPreferenceKey<T>, value: T) }` and `class DefaultWatchPreferencesRepository(store: StampedPreferenceStore) : WatchPreferencesRepository`
  - `PhoneStatePublisher(…, sharedPreferences: Flow<Map<String, Stamped>> = flowOf(emptyMap()), adoptWatchPreferences: suspend (Map<String, Stamped>) -> Unit = {})`; `suspend fun publish(v2Enabled: Boolean, shared: Map<String, Stamped> = emptyMap())`
  - Koin (`domainModule`): `single { StampedPreferenceStore(storage = get(), localRole = PeerRole.PHONE) }`, `single<WatchPreferencesRepository> { DefaultWatchPreferencesRepository(get()) }`.

- [ ] **Step 1: Write the failing tests**

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateMergerTest.kt`, add these tests directly above `private fun phoneDocument(`:

```kotlin
    @Test
    fun `merging stamped entries keeps the winning stamp`() {
        val merged =
            merger.mergeStamped(
                phone = mapOf("a" to stamp("phone", NOW - 10), "b" to stamp("phone", NOW - 1)),
                watch = mapOf("a" to stamp("watch", NOW - 5), "c" to stamp("watch", NOW - 2)),
            )

        assertEquals(
            mapOf("a" to stamp("watch", NOW - 5), "b" to stamp("phone", NOW - 1), "c" to stamp("watch", NOW - 2)),
            merged,
        )
    }

    @Test
    fun `merging stamped entries gives a tie to the phone`() {
        val merged = merger.mergeStamped(phone = mapOf("a" to stamp("phone", NOW - 5)), watch = mapOf("a" to stamp("watch", NOW - 5)))

        assertEquals(stamp("phone", NOW - 5), merged["a"])
    }

    @Test
    fun `a stamped entry from the future cannot beat a current one`() {
        val merged = merger.mergeStamped(phone = mapOf("a" to stamp("phone", NOW)), watch = mapOf("a" to stamp("watch", NOW + 60_000)))

        assertEquals(stamp("phone", NOW), merged["a"])
    }
```

Create `client/domain/src/commonTest/kotlin/app/logdate/client/domain/watch/StampedPreferenceStoreTest.kt`:

```kotlin
package app.logdate.client.domain.watch

import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.Stamped
import app.logdate.client.sync.peer.state.StateMerger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [StampedPreferenceStore]: a local write is stamped and never goes back in time, and the other
 * device's entries are taken by the rule both devices apply to their state documents.
 */
class StampedPreferenceStoreTest {
    private val clock = MutableClock(NOW)
    private val storage = FakeKeyValueStorage()

    private fun store(role: PeerRole = PeerRole.PHONE) =
        StampedPreferenceStore(
            storage = storage,
            localRole = role,
            merger = StateMerger(clock),
            clock = clock,
        )

    @Test
    fun `a write is stamped with the current time`() =
        runTest {
            val store = store()

            val written = store.write(KEY, JsonPrimitive(true))

            assertEquals(Stamped(JsonPrimitive(true), NOW), written)
            assertEquals(mapOf(KEY to written), store.snapshot())
        }

    @Test
    fun `a write after the clock moved back is still the latest`() =
        runTest {
            val store = store()
            store.write(KEY, JsonPrimitive(true))
            clock.millis = NOW - 60_000

            val second = store.write(KEY, JsonPrimitive(false))

            assertEquals(NOW + 1, second.at)
        }

    @Test
    fun `a later entry from the watch is taken`() =
        runTest {
            val store = store()
            clock.millis = NOW - 10
            store.write(KEY, JsonPrimitive(false))
            clock.millis = NOW
            val fromWatch = Stamped(JsonPrimitive(true), NOW - 5)

            assertTrue(store.adopt(mapOf(KEY to fromWatch)))
            assertEquals(fromWatch, store.snapshot()[KEY])
        }

    @Test
    fun `an earlier entry from the watch changes nothing`() =
        runTest {
            val store = store()
            val local = store.write(KEY, JsonPrimitive(true))

            assertFalse(store.adopt(mapOf(KEY to Stamped(JsonPrimitive(false), NOW - 5))))
            assertEquals(local, store.snapshot()[KEY])
        }

    @Test
    fun `a tie goes to the phone on either device`() =
        runTest {
            val onPhone = store(PeerRole.PHONE)
            val phoneEntry = onPhone.write(KEY, JsonPrimitive(true))
            assertFalse(onPhone.adopt(mapOf(KEY to Stamped(JsonPrimitive(false), NOW))))
            assertEquals(phoneEntry, onPhone.snapshot()[KEY])

            storage.clear()
            val onWatch = store(PeerRole.WATCH)
            onWatch.write(KEY, JsonPrimitive(false))
            assertTrue(onWatch.adopt(mapOf(KEY to Stamped(JsonPrimitive(true), NOW))))
            assertEquals(JsonPrimitive(true), onWatch.snapshot()[KEY]?.value)
        }

    @Test
    fun `entries only the other device wrote are added`() =
        runTest {
            val store = store()
            store.write(KEY, JsonPrimitive(true))
            val fromWatch = Stamped(JsonPrimitive(30), NOW - 100)

            assertTrue(store.adopt(mapOf("window.days" to fromWatch)))
            assertEquals(setOf(KEY, "window.days"), store.snapshot().keys)
        }

    @Test
    fun `nothing from the other device changes nothing`() =
        runTest {
            assertFalse(store().adopt(emptyMap()))
        }

    @Test
    fun `stored preferences that cannot be read count as none`() =
        runTest {
            storage.putString(StampedPreferenceStore.STORAGE_KEY, "not json")

            assertEquals(emptyMap(), store().snapshot())
        }

    @Test
    fun `observers see each write`() =
        runTest {
            val store = store()

            store.write(KEY, JsonPrimitive(true))

            assertEquals(mapOf(KEY to Stamped(JsonPrimitive(true), NOW)), store.observe().first())
        }

    private class MutableClock(
        var millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val NOW = 1_760_000_000_000
        const val KEY = "reminders.enabled"
    }
}
```

Create `client/domain/src/commonTest/kotlin/app/logdate/client/domain/watch/WatchPreferencesRepositoryTest.kt`:

```kotlin
package app.logdate.client.domain.watch

import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.Stamped
import app.logdate.client.sync.peer.state.StateMerger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [DefaultWatchPreferencesRepository]: typed reads with defaults, validated writes, and values
 * from another device or app version that this one cannot use reading as the default.
 */
class WatchPreferencesRepositoryTest {
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(NOW)
        }
    private val store = StampedPreferenceStore(FakeKeyValueStorage(), PeerRole.PHONE, StateMerger(clock), clock)
    private val repository = DefaultWatchPreferencesRepository(store)

    @Test
    fun `an unset preference reads as its default`() =
        runTest {
            assertEquals(14, repository.get(SharedPreferenceKey.WindowDays))
            assertFalse(repository.get(SharedPreferenceKey.CapturePlaces))
            assertFalse(repository.get(SharedPreferenceKey.RemindersEnabled))
            assertTrue(repository.get(SharedPreferenceKey.MoodAfterRecording))
            assertTrue(repository.get(SharedPreferenceKey.WindowOnThisDay))
            assertTrue(repository.get(SharedPreferenceKey.WindowRewind))
        }

    @Test
    fun `a preference reads back after it is set`() =
        runTest {
            repository.set(SharedPreferenceKey.WindowDays, 30)

            assertEquals(30, repository.get(SharedPreferenceKey.WindowDays))
            assertEquals(30, repository.observe(SharedPreferenceKey.WindowDays).first())
        }

    @Test
    fun `a library length outside the choices is refused`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { repository.set(SharedPreferenceKey.WindowDays, 10) }
            assertEquals(14, repository.get(SharedPreferenceKey.WindowDays))
        }

    @Test
    fun `a value this version cannot read is the default`() =
        runTest {
            store.adopt(
                mapOf(
                    "window.days" to Stamped(JsonPrimitive("forever"), NOW),
                    "capture.places" to Stamped(JsonPrimitive(3), NOW),
                ),
            )

            assertEquals(14, repository.get(SharedPreferenceKey.WindowDays))
            assertFalse(repository.get(SharedPreferenceKey.CapturePlaces))
        }

    @Test
    fun `a library length outside the choices from another device is the default`() =
        runTest {
            store.adopt(mapOf("window.days" to Stamped(JsonPrimitive(21), NOW)))

            assertEquals(14, repository.get(SharedPreferenceKey.WindowDays))
        }

    @Test
    fun `a choice made on the watch reaches observers`() =
        runTest {
            store.adopt(mapOf("capture.places" to Stamped(JsonPrimitive(true), NOW)))

            assertTrue(repository.observe(SharedPreferenceKey.CapturePlaces).first())
        }

    @Test
    fun `every key has its own name`() {
        val names = SharedPreferenceKey.ALL.map { it.name }

        assertEquals(names.size, names.toSet().size)
    }

    private companion object {
        const val NOW = 1_760_000_000_000
    }
}
```

In `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt`:

1. Add the imports `app.logdate.client.sync.peer.state.Stamped` and `kotlinx.serialization.json.JsonPrimitive`.
2. Replace the field block from `private val enabled = MutableStateFlow(false)` through the end of the `publisher` initializer with:

```kotlin
    private val enabled = MutableStateFlow(false)
    private val preferences = MutableStateFlow<Map<String, Stamped>>(emptyMap())
    private val adopted = mutableListOf<Map<String, Stamped>>()
    private val channel = FakeChannel()
    private val capabilities = FakeCapabilities()
    private val peerStates = PeerStateStore()
    private val publisher =
        PhoneStatePublisher(
            v2Enabled = enabled,
            channel = channel,
            capabilities = capabilities,
            peerStates = peerStates,
            clock = FixedClock(1_000),
            timeZone = { TimeZone.of("Europe/Paris") },
            sharedPreferences = preferences,
            adoptWatchPreferences = { adopted += it },
        )
```

3. In `the phone publishes its state with the switch off`, change the expected `PhoneState` to:

```kotlin
                        phone = PhoneState(flags = mapOf(FLAG_KEY to false), zone = "Europe/Paris", features = setOf("prefs.v1")),
```

4. Add these tests after `a failed publish still updates the capability`:

```kotlin
    @Test
    fun `a preference change republishes the document without touching the capability`() =
        runTest {
            publisher.start(backgroundScope)
            runCurrent()

            preferences.value = mapOf("window.days" to Stamped(JsonPrimitive(30), 900))
            runCurrent()

            assertEquals(2, channel.published.size)
            assertEquals(mapOf("window.days" to Stamped(JsonPrimitive(30), 900)), channel.published.last().shared)
            assertEquals(listOf("remove $PHONE_V2"), capabilities.calls)
        }

    @Test
    fun `preferences a watch wrote are handed over to be adopted`() =
        runTest {
            publisher.start(backgroundScope)
            runCurrent()
            val shared = mapOf("capture.places" to Stamped(JsonPrimitive(true), 950))

            peerStates.accept(
                "watch-node",
                Decoded.Supported(StateDocument(role = PeerRole.WATCH, writtenAt = 950, watch = WatchState(), shared = shared)),
                PeerRole.WATCH,
            )
            runCurrent()

            assertEquals(listOf(shared), adopted)
        }
```

- [ ] **Step 2: Add the API surface with inert bodies**

Create `SharedPreferenceKey.kt`, `StampedPreferenceStore.kt` and `WatchPreferencesRepository.kt` with the declarations from Step 4, with bodies: `SharedPreferenceKey.read` returns `default`, `encode` returns `JsonNull`, `isAllowed` returns `true`; `StampedPreferenceStore.observe` returns `flowOf(emptyMap())`, `snapshot` returns `emptyMap()`, `write` returns `Stamped(value, 0)`, `adopt` returns `false`; `DefaultWatchPreferencesRepository.observe` returns `flowOf(key.default)`, `get` returns `key.default`, `set` is empty. Add `fun mergeStamped(phone: Map<String, Stamped>, watch: Map<String, Stamped>): Map<String, Stamped> = emptyMap()` to `StateMerger`. Add the two `PhoneStatePublisher` constructor parameters from Step 4 without using them.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.peer.state.StateMergerTest' :client:domain:jvmTest --tests 'app.logdate.client.domain.watch.*' :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.v2.PhoneStatePublisherTest' --console=plain
```
Expected: FAIL. `StateMergerTest`: the three new tests (`expected:<{a=…}> but was:<{}>`). `StampedPreferenceStoreTest`: every test except `nothing from the other device changes nothing` and `stored preferences that cannot be read count as none`. `WatchPreferencesRepositoryTest`: `a preference reads back after it is set`, `a library length outside the choices is refused`, `a choice made on the watch reaches observers`. `PhoneStatePublisherTest`: `the phone publishes its state with the switch off` (no `prefs.v1`), both new tests.

- [ ] **Step 4: Implement the preferences model and its publishing**

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateMerger.kt`, replace the `shared = mergeShared(…)` argument in `merge` with

```kotlin
            shared = mergeStamped(phoneDocument?.shared.orEmpty(), watchDocument?.shared.orEmpty()).mapValues { it.value.value },
```

and replace the whole `private fun mergeShared(…)` function with:

```kotlin
    /**
     * The winning entry for every key either device wrote, with its stamp, so a device can store the
     * result and stamp its next write after it.
     */
    fun mergeStamped(
        phone: Map<String, Stamped>,
        watch: Map<String, Stamped>,
    ): Map<String, Stamped> {
        val now = clock.now().toEpochMilliseconds()
        return (phone.keys + watch.keys).associateWith { key -> later(phone[key], watch[key], now) }
    }
```

`client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/SharedPreferenceKey.kt`:

```kotlin
package app.logdate.client.domain.watch

import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.state.Stamped
import io.github.aakira.napier.Napier
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

/**
 * A preference the phone and the watch share through the `shared` section of their state documents.
 *
 * [name] is the key on the wire and never changes once shipped. A stored value that cannot be read as
 * [T], or that [isAllowed] rejects (for example one a newer app version wrote), reads as [default]
 * rather than failing.
 */
class SharedPreferenceKey<T>(
    val name: String,
    val default: T,
    private val serializer: KSerializer<T>,
    private val allowed: (T) -> Boolean = { true },
) {
    fun isAllowed(value: T): Boolean = allowed(value)

    fun encode(value: T): JsonElement = WearJson.json.encodeToJsonElement(serializer, value)

    fun read(stamped: Stamped?): T {
        if (stamped == null) return default
        val value =
            try {
                WearJson.json.decodeFromJsonElement(serializer, stamped.value)
            } catch (e: IllegalArgumentException) {
                return unreadable(e)
            } catch (e: IllegalStateException) {
                return unreadable(e)
            }
        if (allowed(value)) return value
        Napier.w("The shared preference $name has a value outside what this version allows; it reads as its default")
        return default
    }

    private fun unreadable(error: Exception): T {
        Napier.w("The shared preference $name has a value this version cannot read; it reads as its default", error)
        return default
    }

    override fun toString(): String = "SharedPreferenceKey($name)"

    companion object {
        /** Moment-aware reminders, off until someone turns them on. */
        val RemindersEnabled = SharedPreferenceKey("reminders.enabled", false, Boolean.serializer())

        /** Whether marks and watch captures get a place. Off until someone agrees to it on either device. */
        val CapturePlaces = SharedPreferenceKey("capture.places", false, Boolean.serializer())

        /** Whether the watch offers the five feelings right after a recording is saved. */
        val MoodAfterRecording = SharedPreferenceKey("capture.moodAfterRecording", true, Boolean.serializer())

        /** How many days of moments the watch library covers: one of [WINDOW_DAY_CHOICES]. */
        val WindowDays = SharedPreferenceKey("window.days", 14, Int.serializer()) { it in WINDOW_DAY_CHOICES }

        /** Whether the watch library includes On this day. */
        val WindowOnThisDay = SharedPreferenceKey("window.onThisDay", true, Boolean.serializer())

        /** Whether the watch library includes this week's Rewind. */
        val WindowRewind = SharedPreferenceKey("window.rewind", true, Boolean.serializer())

        val WINDOW_DAY_CHOICES: Set<Int> = setOf(7, 14, 30)

        val ALL: List<SharedPreferenceKey<*>> =
            listOf(
                RemindersEnabled,
                CapturePlaces,
                MoodAfterRecording,
                WindowDays,
                WindowOnThisDay,
                WindowRewind,
            )
    }
}
```

`client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/StampedPreferenceStore.kt`:

```kotlin
package app.logdate.client.domain.watch

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.Stamped
import app.logdate.client.sync.peer.state.StateMerger
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock

/**
 * This device's copy of the preferences it shares with its paired device, each with the time it was
 * written.
 *
 * A local write is stamped with the current time, and never earlier than the entry it replaces, so a
 * clock that moved backwards cannot make a new choice lose to an old one. [adopt] takes the other
 * device's entries through [StateMerger.mergeStamped], the rule both devices apply to their state
 * documents: the later write wins, a stamp from the future counts as now, and a tie goes to the phone.
 */
class StampedPreferenceStore(
    private val storage: KeyValueStorage,
    private val localRole: PeerRole,
    private val merger: StateMerger = StateMerger(),
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()

    fun observe(): Flow<Map<String, Stamped>> = storage.observeString(STORAGE_KEY).map(::decode).distinctUntilChanged()

    suspend fun snapshot(): Map<String, Stamped> = decode(storage.getString(STORAGE_KEY))

    suspend fun write(
        name: String,
        value: JsonElement,
    ): Stamped =
        mutex.withLock {
            val current = snapshot()
            val now = clock.now().toEpochMilliseconds()
            val at = current[name]?.let { maxOf(now, it.at + 1) } ?: now
            val stamped = Stamped(value = value, at = at)
            persist(current + (name to stamped))
            stamped
        }

    /** Takes each entry of [peer] that is later than this device's; true when anything changed. */
    suspend fun adopt(peer: Map<String, Stamped>): Boolean =
        mutex.withLock {
            if (peer.isEmpty()) return@withLock false
            val current = snapshot()
            val merged =
                when (localRole) {
                    PeerRole.PHONE -> merger.mergeStamped(phone = current, watch = peer)
                    PeerRole.WATCH -> merger.mergeStamped(phone = peer, watch = current)
                }
            if (merged == current) return@withLock false
            persist(merged)
            true
        }

    private suspend fun persist(entries: Map<String, Stamped>) {
        storage.putString(STORAGE_KEY, WearJson.json.encodeToString(entriesSerializer, entries))
    }

    private fun decode(text: String?): Map<String, Stamped> {
        if (text == null) return emptyMap()
        return try {
            WearJson.json.decodeFromString(entriesSerializer, text)
        } catch (e: IllegalArgumentException) {
            Napier.e("Stored watch preferences could not be read; each reads as its default until the next change", e)
            emptyMap()
        }
    }

    companion object {
        const val STORAGE_KEY = "watch_shared_preferences_v1"

        private val entriesSerializer = MapSerializer(String.serializer(), Stamped.serializer())
    }
}
```

`client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchPreferencesRepository.kt`:

```kotlin
package app.logdate.client.domain.watch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Typed reads and writes of the preferences this device shares with its paired device. */
interface WatchPreferencesRepository {
    fun <T> observe(key: SharedPreferenceKey<T>): Flow<T>

    suspend fun <T> get(key: SharedPreferenceKey<T>): T

    /** @throws IllegalArgumentException when [SharedPreferenceKey.isAllowed] rejects [value]. */
    suspend fun <T> set(
        key: SharedPreferenceKey<T>,
        value: T,
    )
}

class DefaultWatchPreferencesRepository(
    private val store: StampedPreferenceStore,
) : WatchPreferencesRepository {
    override fun <T> observe(key: SharedPreferenceKey<T>): Flow<T> =
        store
            .observe()
            .map { entries -> key.read(entries[key.name]) }
            .distinctUntilChanged()

    override suspend fun <T> get(key: SharedPreferenceKey<T>): T = key.read(store.snapshot()[key.name])

    override suspend fun <T> set(
        key: SharedPreferenceKey<T>,
        value: T,
    ) {
        require(key.isAllowed(value)) { "${key.name} does not accept $value" }
        store.write(key.name, key.encode(value))
    }
}
```

In `client/domain/src/commonMain/kotlin/app/logdate/client/domain/di/DomainModule.kt`, after `single<DayBoundarySettingsRepository> { DefaultDayBoundarySettingsRepository(get()) }`, add:

```kotlin

        // Preferences shared with a paired watch. The watch app binds its own store with its role.
        single { StampedPreferenceStore(storage = get(), localRole = PeerRole.PHONE) }
        single<WatchPreferencesRepository> { DefaultWatchPreferencesRepository(get()) }
```

and the imports `app.logdate.client.domain.watch.DefaultWatchPreferencesRepository`, `app.logdate.client.domain.watch.StampedPreferenceStore`, `app.logdate.client.domain.watch.WatchPreferencesRepository` and `app.logdate.client.sync.peer.state.PeerRole`.

Replace the whole of `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt` with:

```kotlin
package app.logdate.client.sync.v2

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.Stamped
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Publishes this phone's state document whenever the Labs watch switch or a shared preference
 * changes, and advertises [WearProtocol.PHONE_CAPABILITY_V2] only while the switch is on.
 *
 * The document goes out whatever the switch says, because the watch takes the switch from it. It
 * lists [WearFeatures.PREFS], the only version 2 feature the phone serves so far; each other feature
 * is added by the work that ships it. Preferences a watch wrote are handed to [adoptWatchPreferences],
 * which takes the entries that are later than this phone's.
 */
class PhoneStatePublisher(
    private val v2Enabled: Flow<Boolean>,
    private val channel: StateDocumentChannel,
    private val capabilities: LocalCapabilities,
    private val peerStates: PeerStateStore,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val sharedPreferences: Flow<Map<String, Stamped>> = flowOf(emptyMap()),
    private val adoptWatchPreferences: suspend (Map<String, Stamped>) -> Unit = {},
) {
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            catchUpWithWatches()
            launch { adoptPreferencesFromWatches() }
            var advertised: Boolean? = null
            combine(v2Enabled.distinctUntilChanged(), sharedPreferences.distinctUntilChanged()) { enabled, shared -> enabled to shared }
                .collect { (enabled, shared) ->
                    publish(enabled, shared)
                    if (enabled == advertised) return@collect
                    updateCapability(enabled)
                    advertised = enabled
                }
        }

    /** Reads the documents the watches published while this phone's app was not running. */
    suspend fun catchUpWithWatches() {
        channel.peerDocuments().forEach { peer -> peerStates.accept(peer.nodeId, peer.decoded, PeerRole.WATCH) }
    }

    suspend fun publish(
        v2Enabled: Boolean,
        shared: Map<String, Stamped> = emptyMap(),
    ) {
        val document =
            StateDocument(
                role = PeerRole.PHONE,
                writtenAt = clock.now().toEpochMilliseconds(),
                phone =
                    PhoneState(
                        flags = mapOf(FeatureFlag.WATCH_EXPERIENCE_V2.key to v2Enabled),
                        zone = timeZone().id,
                        features = setOf(WearFeatures.PREFS),
                    ),
                shared = shared,
            )
        if (!channel.publish(document)) {
            Napier.e(
                "Could not publish this phone's state; watches use the old Labs setting and preferences until the next change or restart",
            )
        }
    }

    private suspend fun updateCapability(v2Enabled: Boolean) {
        val capabilityUpdated =
            if (v2Enabled) {
                capabilities.add(WearProtocol.PHONE_CAPABILITY_V2)
            } else {
                capabilities.remove(WearProtocol.PHONE_CAPABILITY_V2)
            }
        if (!capabilityUpdated) {
            Napier.e(
                "Could not update ${WearProtocol.PHONE_CAPABILITY_V2}; watches use the old protocol until the next change or restart",
            )
        }
    }

    private suspend fun adoptPreferencesFromWatches() {
        peerStates
            .latest(PeerRole.WATCH)
            .filterNotNull()
            .map { document -> document.shared }
            .distinctUntilChanged()
            .collect { shared -> adoptWatchPreferences(shared) }
    }
}
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt`, replace the `PhoneStatePublisher` binding with:

```kotlin
        single {
            val watchPreferences = get<StampedPreferenceStore>()
            PhoneStatePublisher(
                v2Enabled = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                channel = get(),
                capabilities = get(),
                peerStates = get(),
                sharedPreferences = watchPreferences.observe(),
                adoptWatchPreferences = { shared -> watchPreferences.adopt(shared) },
            )
        }
```

and add the import `app.logdate.client.domain.watch.StampedPreferenceStore`.

- [ ] **Step 5: Run the tests again**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.peer.state.StateMergerTest' :client:domain:jvmTest --tests 'app.logdate.client.domain.watch.*' :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.v2.PhoneStatePublisherTest' --console=plain
```
Expected: PASS (`StateMergerTest` 3 new tests and every existing one; 9 `StampedPreferenceStoreTest`; 7 `WatchPreferencesRepositoryTest`; 6 `PhoneStatePublisherTest`).

- [ ] **Step 6: Compile for iOS and run the module checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:domain:compileKotlinIosSimulatorArm64 :client:domain:compileTestKotlinIosSimulatorArm64 :client:sync:desktopTest :client:domain:jvmTest :client:sync:ktlintCheck :client:domain:ktlintCheck :app:compose-main:ktlintCheck :app:android-main:ktlintCheck :client:sync:detekt :client:domain:detekt :app:compose-main:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task02-shared-preferences.txt <<'EOF'
chore(settings): share watch preferences with the paired watch

The phone and its watch need to agree on a few choices: whether
reminders are on, whether moments get a place, whether the watch
offers a mood after a recording, and what goes into the watch's
library. This adds typed keys for those choices with their defaults,
a store for this device's copy with the time each choice was made,
and a repository to read and change them.

The phone now publishes its copy in the shared section of its state
document, lists the prefs.v1 feature, and takes any choice the watch
made later than its own. Both follow the rule the state documents
already use: the later write wins, a time from the future counts as
now, and a tie goes to the phone. A local change is never stamped
earlier than the one it replaces, so a clock that moved backwards
cannot undo it. A value written by a newer app version that this one
cannot read falls back to its default. Nothing shows these choices
yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateMerger.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateMergerTest.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/SharedPreferenceKey.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/StampedPreferenceStore.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchPreferencesRepository.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/di/DomainModule.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/watch/StampedPreferenceStoreTest.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/watch/WatchPreferencesRepositoryTest.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task02-shared-preferences.txt
```

---
### Task 3: Describe the watch from what it reports (C3b, model)

**Files:**
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatus.kt`
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusResolver.kt`
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/PeerStateWatchSnapshotSource.kt`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModel.kt:21-28`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/di/WatchSettingsModule.kt` (whole file)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt:236-242` (one binding after `WearProtocolGate`)
- Test: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusResolverTest.kt`
- Test: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/PeerStateWatchSnapshotSourceTest.kt`
- Test: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModelTest.kt` (one test)

**Interfaces:**
- Consumes: `WatchConnectionState` (today's shape, `Connected(watchName, lastSynced, pendingCount)`), `WatchState`, `PeerLinkState`, `PeerStateStore`, `StateMerger`, `WearProtocolGate.isV2Active()`, `WearFeatures.{RECEIPTS, WINDOW, LINK_CLOUD}` (`client/sync`); `SharedPreferenceKey.WindowDays`, `WatchPreferencesRepository` (Task 2).
- Produces:
  - `sealed interface WatchStatus { data object Unsupported; data object NoWatch; data class NotInstalled(val watchName: String); data class SettingUp(val step: WatchSetupStep); data class Ready(val watchName: String, val nearby: Boolean, val link: WatchLinkLine? = null, val sync: WatchSyncLine? = null, val window: WatchWindowLine? = null, val capabilities: Set<String> = emptySet()); data class NeedsAttention(val watchName: String, val nearby: Boolean, val issue: WatchIssue) }`
  - `sealed interface WatchSetupStep { data class PairWithPhone(val watchName: String); data object ConfirmOnPhone }`, `enum class WatchLinkLine { THROUGH_PHONE, ON_ITS_OWN }`, `data class WatchSyncLine(val waiting: Int)`, `data class WatchWindowLine(val days: Int)`, `sealed interface WatchIssue { data object SetUpAgain; data class CapturesNotSent(val count: Int) }`
  - `data class WatchPeerSnapshot(val v2Active: Boolean, val watch: WatchState?, val features: Set<String>) { companion object { val Inactive: WatchPeerSnapshot } }`, `fun interface WatchPeerSnapshotSource { fun observe(): Flow<WatchPeerSnapshot> }`
  - `object WatchStatusResolver { fun resolve(connection: WatchConnectionState, peer: WatchPeerSnapshot, windowDays: Int): WatchStatus? }`
  - `class PeerStateWatchSnapshotSource(gate: WearProtocolGate, peerStates: PeerStateStore, merger: StateMerger = StateMerger()) : WatchPeerSnapshotSource`
  - `WatchSettingsViewModel(connectionManager, settingsRepository, peerSnapshots: WatchPeerSnapshotSource = WatchPeerSnapshotSource { flowOf(WatchPeerSnapshot.Inactive) }, windowDays: Flow<Int> = flowOf(SharedPreferenceKey.WindowDays.default))` with `val status: StateFlow<WatchStatus?>` (null until the phone has looked for the watch). Task 4 renders it.

- [ ] **Step 1: Write the failing tests**

Create `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusResolverTest.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.watch

import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.WatchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests [WatchStatusResolver]: the phone's own view of the connection decides the status, and each
 * line comes only from what the watch reported under a feature it listed while version 2 is active.
 */
class WatchStatusResolverTest {
    private val connected = WatchConnectionState.Connected(watchName = NAME, lastSynced = null, pendingCount = 0)
    private val outOfRange = WatchConnectionState.OutOfRange(watchName = NAME, lastSynced = null)

    private fun reporting(
        watch: WatchState,
        vararg features: String,
    ) = WatchPeerSnapshot(v2Active = true, watch = watch, features = features.toSet())

    private fun resolve(
        connection: WatchConnectionState,
        peer: WatchPeerSnapshot = WatchPeerSnapshot.Inactive,
        windowDays: Int = 14,
    ) = WatchStatusResolver.resolve(connection, peer, windowDays)

    @Test
    fun `nothing is known while the phone looks for the watch`() {
        assertNull(resolve(WatchConnectionState.Loading))
    }

    @Test
    fun `a phone without a paired watch has no watch`() {
        assertEquals(WatchStatus.NoWatch, resolve(WatchConnectionState.NoPairedWatch))
    }

    @Test
    fun `a watch LogDate is not linked with yet is being set up`() {
        assertEquals(
            WatchStatus.SettingUp(WatchSetupStep.PairWithPhone(NAME)),
            resolve(WatchConnectionState.NeedsAssociation(NAME)),
        )
        assertEquals(WatchStatus.SettingUp(WatchSetupStep.ConfirmOnPhone), resolve(WatchConnectionState.AssociationPending))
    }

    @Test
    fun `a watch without LogDate is not installed`() {
        assertEquals(WatchStatus.NotInstalled(NAME), resolve(WatchConnectionState.AppNotInstalled(NAME)))
    }

    @Test
    fun `a connected watch that reports nothing shows only its name`() {
        assertEquals(WatchStatus.Ready(watchName = NAME, nearby = true), resolve(connected))
    }

    @Test
    fun `a watch out of range is not nearby`() {
        assertEquals(WatchStatus.Ready(watchName = NAME, nearby = false), resolve(outOfRange))
    }

    @Test
    fun `a watch that lists no features shows no lines`() {
        assertEquals(WatchStatus.Ready(watchName = NAME, nearby = true), resolve(connected, reporting(WatchState(outboxCount = 5))))
    }

    @Test
    fun `a watch that lists receipts shows how many moments wait`() {
        assertEquals(
            WatchStatus.Ready(
                watchName = NAME,
                nearby = true,
                sync = WatchSyncLine(waiting = 3),
                capabilities = setOf(WearFeatures.RECEIPTS),
            ),
            resolve(connected, reporting(WatchState(outboxCount = 3), WearFeatures.RECEIPTS)),
        )
    }

    @Test
    fun `a watch that lists the window shows how many days it covers`() {
        assertEquals(
            WatchStatus.Ready(
                watchName = NAME,
                nearby = true,
                window = WatchWindowLine(days = 30),
                capabilities = setOf(WearFeatures.WINDOW),
            ),
            resolve(connected, reporting(WatchState(), WearFeatures.WINDOW), windowDays = 30),
        )
    }

    @Test
    fun `the link line follows the link the watch reports`() {
        fun link(state: PeerLinkState) =
            (resolve(connected, reporting(WatchState(link = state), WearFeatures.LINK_CLOUD)) as WatchStatus.Ready).link

        assertEquals(WatchLinkLine.THROUGH_PHONE, link(PeerLinkState.PAIRED))
        assertEquals(WatchLinkLine.ON_ITS_OWN, link(PeerLinkState.LINKED))
        assertNull(link(PeerLinkState.UNLINKED))
    }

    @Test
    fun `features count only while version 2 is active`() {
        val inactive =
            WatchPeerSnapshot(
                v2Active = false,
                watch = WatchState(outboxCount = 3, deadLetterCount = 2),
                features = setOf(WearFeatures.RECEIPTS, WearFeatures.WINDOW),
            )

        assertEquals(WatchStatus.Ready(watchName = NAME, nearby = true), resolve(connected, inactive))
    }

    @Test
    fun `a watch that lost its link needs to be set up again`() {
        assertEquals(
            WatchStatus.NeedsAttention(watchName = NAME, nearby = true, issue = WatchIssue.SetUpAgain),
            resolve(connected, reporting(WatchState(link = PeerLinkState.NEEDS_RELINK), WearFeatures.LINK_CLOUD)),
        )
        assertEquals(
            WatchStatus.Ready(watchName = NAME, nearby = true),
            resolve(connected, reporting(WatchState(link = PeerLinkState.NEEDS_RELINK))),
        )
    }

    @Test
    fun `moments that did not send need attention`() {
        assertEquals(
            WatchStatus.NeedsAttention(watchName = NAME, nearby = false, issue = WatchIssue.CapturesNotSent(count = 2)),
            resolve(outOfRange, reporting(WatchState(deadLetterCount = 2), WearFeatures.RECEIPTS)),
        )
    }

    @Test
    fun `a lost link comes before moments that did not send`() {
        val both = WatchState(link = PeerLinkState.NEEDS_RELINK, deadLetterCount = 2)

        assertEquals(
            WatchStatus.NeedsAttention(watchName = NAME, nearby = true, issue = WatchIssue.SetUpAgain),
            resolve(connected, reporting(both, WearFeatures.LINK_CLOUD, WearFeatures.RECEIPTS)),
        )
    }

    private companion object {
        const val NAME = "Pixel Watch"
    }
}
```

Create `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/PeerStateWatchSnapshotSourceTest.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.watch

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearProtocolGate
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.WatchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests [PeerStateWatchSnapshotSource], which reads the watch's report from the peer state the phone received. */
class PeerStateWatchSnapshotSourceTest {
    private val peerStates = PeerStateStore()
    private val flag = MutableStateFlow(true)
    private val source =
        PeerStateWatchSnapshotSource(
            gate =
                WearProtocolGate(
                    flagEnabled = flag,
                    peerHasV2Capability = MutableStateFlow(true),
                    peerFeatures = peerStates.features(PeerRole.WATCH),
                ),
            peerStates = peerStates,
        )

    @Test
    fun `before the watch reports anything there is no report`() =
        runTest {
            val snapshot = source.observe().first()

            assertTrue(snapshot.v2Active)
            assertNull(snapshot.watch)
            assertEquals(emptySet(), snapshot.features)
        }

    @Test
    fun `the watch report and its features reach the snapshot`() =
        runTest {
            val watch = WatchState(outboxCount = 2, features = setOf(WearFeatures.RECEIPTS))
            peerStates.accept(
                "watch-node",
                Decoded.Supported(StateDocument(role = PeerRole.WATCH, writtenAt = 1, watch = watch)),
                PeerRole.WATCH,
            )

            val snapshot = source.observe().first()

            assertEquals(2, snapshot.watch?.outboxCount)
            assertEquals(setOf(WearFeatures.RECEIPTS), snapshot.features)
        }

    @Test
    fun `with the Labs switch off version 2 is inactive`() =
        runTest {
            flag.value = false

            assertFalse(source.observe().first().v2Active)
        }
}
```

In `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModelTest.kt`, add the imports `app.logdate.client.sync.datalayer.v2.WearFeatures`, `app.logdate.client.sync.peer.state.WatchState` and `kotlinx.coroutines.flow.flowOf`, and add this test after `connection state is exposed from connection manager`:

```kotlin
    @Test
    fun `status combines the connection with what the watch reported`() =
        runTest {
            val report =
                WatchPeerSnapshot(
                    v2Active = true,
                    watch = WatchState(outboxCount = 3),
                    features = setOf(WearFeatures.RECEIPTS, WearFeatures.WINDOW),
                )
            val viewModel =
                WatchSettingsViewModel(
                    connectionManager =
                        FakeWatchConnectionManager(
                            initialState = WatchConnectionState.Connected(watchName = "Pixel Watch", lastSynced = null, pendingCount = 0),
                        ),
                    settingsRepository = FakeWatchSettingsRepository(),
                    peerSnapshots = { flowOf(report) },
                    windowDays = flowOf(30),
                )
            backgroundScope.launch { viewModel.status.collect {} }
            advanceUntilIdle()

            assertEquals(
                WatchStatus.Ready(
                    watchName = "Pixel Watch",
                    nearby = true,
                    sync = WatchSyncLine(waiting = 3),
                    window = WatchWindowLine(days = 30),
                    capabilities = setOf(WearFeatures.RECEIPTS, WearFeatures.WINDOW),
                ),
                viewModel.status.value,
            )
        }
```

- [ ] **Step 2: Add the API surface with inert bodies**

Create `WatchStatus.kt` exactly as in Step 4 (it has no behavior). Create `WatchStatusResolver.kt` with `fun resolve(connection: WatchConnectionState, peer: WatchPeerSnapshot, windowDays: Int): WatchStatus? = null`, and `PeerStateWatchSnapshotSource.kt` with `override fun observe(): Flow<WatchPeerSnapshot> = flowOf(WatchPeerSnapshot.Inactive)`. Add the two `WatchSettingsViewModel` constructor parameters from Step 4 and `val status: StateFlow<WatchStatus?> = MutableStateFlow(null)`.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.watch.*' --console=plain
```
Expected: FAIL. Every `WatchStatusResolverTest` test except `nothing is known while the phone looks for the watch`; `PeerStateWatchSnapshotSourceTest > before the watch reports anything there is no report` (`v2Active` is false) and `the watch report and its features reach the snapshot`; `WatchSettingsViewModelTest > status combines the connection with what the watch reported` (`but was:<null>`).

- [ ] **Step 4: Implement the status model**

`client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatus.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.watch

import app.logdate.client.sync.peer.state.WatchState
import kotlinx.coroutines.flow.Flow

/**
 * What the phone can say about its watch, for the Watch page and, later, the device card in Settings.
 *
 * A line in [Ready] stays null until the watch reports the value behind it under a feature it lists.
 * The phone never fills a line with a guess: an older watch, or one with the Labs switch off, shows
 * only its name and whether it is nearby.
 */
sealed interface WatchStatus {
    /** This device cannot connect to a Wear OS watch at all. */
    data object Unsupported : WatchStatus

    /** No watch is paired with this phone. */
    data object NoWatch : WatchStatus

    /** A watch is paired, and LogDate is not installed on it. */
    data class NotInstalled(
        val watchName: String,
    ) : WatchStatus

    /** LogDate is being linked with a paired watch. */
    data class SettingUp(
        val step: WatchSetupStep,
    ) : WatchStatus

    /**
     * LogDate is on the watch.
     *
     * @property capabilities The version 2 features the watch lists while version 2 is active; empty otherwise.
     */
    data class Ready(
        val watchName: String,
        val nearby: Boolean,
        val link: WatchLinkLine? = null,
        val sync: WatchSyncLine? = null,
        val window: WatchWindowLine? = null,
        val capabilities: Set<String> = emptySet(),
    ) : WatchStatus

    /** The watch reported a problem the person needs to know about. */
    data class NeedsAttention(
        val watchName: String,
        val nearby: Boolean,
        val issue: WatchIssue,
    ) : WatchStatus
}

sealed interface WatchSetupStep {
    /** LogDate still needs the system's permission to manage [watchName]. */
    data class PairWithPhone(
        val watchName: String,
    ) : WatchSetupStep

    /** The system pairing prompt is open on this phone. */
    data object ConfirmOnPhone : WatchSetupStep
}

enum class WatchLinkLine {
    /** The watch syncs through this phone only. */
    THROUGH_PHONE,

    /** The watch backs up to the cloud by itself. */
    ON_ITS_OWN,
}

/** @property waiting Moments on the watch that have not reached this phone yet. */
data class WatchSyncLine(
    val waiting: Int,
)

/** @property days How many days of moments the watch library covers. */
data class WatchWindowLine(
    val days: Int,
)

sealed interface WatchIssue {
    /** The watch lost its link to the account and needs to be set up again. */
    data object SetUpAgain : WatchIssue

    /** [count] moments failed to send; the watch tries them again by itself. */
    data class CapturesNotSent(
        val count: Int,
    ) : WatchIssue
}

/**
 * The watch's own report, as this phone last received it.
 *
 * @property v2Active Whether the Labs switch is on and the watch advertises the version 2 capability.
 * @property watch The watch-owned section of its latest state document, or null before one arrives.
 * @property features The version 2 features the watch lists.
 */
data class WatchPeerSnapshot(
    val v2Active: Boolean,
    val watch: WatchState?,
    val features: Set<String>,
) {
    companion object {
        val Inactive = WatchPeerSnapshot(v2Active = false, watch = null, features = emptySet())
    }
}

/** Where the watch's report comes from; on Android, the peer state the Data Layer delivers. */
fun interface WatchPeerSnapshotSource {
    fun observe(): Flow<WatchPeerSnapshot>
}
```

`client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusResolver.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.watch

import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.WatchState

/**
 * Combines the phone's view of the connection with the watch's own report into a [WatchStatus].
 *
 * The watch's report counts only while version 2 is active, and each part of it only under the
 * feature that makes it meaningful: outbox counts under receipts, the library under the window, and
 * the link under cloud linking. A watch that publishes defaults it does not track yet lists none of
 * these, so none of its defaults reach the screen.
 */
object WatchStatusResolver {
    fun resolve(
        connection: WatchConnectionState,
        peer: WatchPeerSnapshot,
        windowDays: Int,
    ): WatchStatus? =
        when (connection) {
            WatchConnectionState.Loading -> null
            WatchConnectionState.NoPairedWatch -> WatchStatus.NoWatch
            is WatchConnectionState.NeedsAssociation -> WatchStatus.SettingUp(WatchSetupStep.PairWithPhone(connection.watchName))
            WatchConnectionState.AssociationPending -> WatchStatus.SettingUp(WatchSetupStep.ConfirmOnPhone)
            is WatchConnectionState.AppNotInstalled -> WatchStatus.NotInstalled(connection.watchName)
            is WatchConnectionState.Connected -> installed(connection.watchName, nearby = true, peer, windowDays)
            is WatchConnectionState.OutOfRange -> installed(connection.watchName, nearby = false, peer, windowDays)
        }

    private fun installed(
        watchName: String,
        nearby: Boolean,
        peer: WatchPeerSnapshot,
        windowDays: Int,
    ): WatchStatus {
        if (!peer.v2Active) return WatchStatus.Ready(watchName = watchName, nearby = nearby)
        val watch = peer.watch
        val features = peer.features
        issue(watch, features)?.let { return WatchStatus.NeedsAttention(watchName = watchName, nearby = nearby, issue = it) }
        return WatchStatus.Ready(
            watchName = watchName,
            nearby = nearby,
            link = linkLine(watch, features),
            sync = watch?.takeIf { WearFeatures.RECEIPTS in features }?.let { WatchSyncLine(waiting = it.outboxCount) },
            window = WatchWindowLine(days = windowDays).takeIf { WearFeatures.WINDOW in features },
            capabilities = features,
        )
    }

    private fun issue(
        watch: WatchState?,
        features: Set<String>,
    ): WatchIssue? {
        if (watch == null) return null
        if (WearFeatures.LINK_CLOUD in features && watch.link == PeerLinkState.NEEDS_RELINK) return WatchIssue.SetUpAgain
        if (WearFeatures.RECEIPTS in features && watch.deadLetterCount > 0) return WatchIssue.CapturesNotSent(watch.deadLetterCount)
        return null
    }

    private fun linkLine(
        watch: WatchState?,
        features: Set<String>,
    ): WatchLinkLine? {
        if (watch == null || WearFeatures.LINK_CLOUD !in features) return null
        return when (watch.link) {
            PeerLinkState.PAIRED -> WatchLinkLine.THROUGH_PHONE
            PeerLinkState.LINKED -> WatchLinkLine.ON_ITS_OWN
            PeerLinkState.UNLINKED, PeerLinkState.NEEDS_RELINK -> null
        }
    }
}
```

`client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/PeerStateWatchSnapshotSource.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.watch

import app.logdate.client.sync.datalayer.v2.WearProtocolGate
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.StateMerger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reads the watch's report from the state documents the phone received, through [StateMerger] so a
 * phone-owned section a watch document claims is ignored and a stale presence reads as unknown.
 */
class PeerStateWatchSnapshotSource(
    private val gate: WearProtocolGate,
    private val peerStates: PeerStateStore,
    private val merger: StateMerger = StateMerger(),
) : WatchPeerSnapshotSource {
    override fun observe(): Flow<WatchPeerSnapshot> =
        combine(
            gate.isV2Active(),
            peerStates.latest(PeerRole.WATCH),
            peerStates.features(PeerRole.WATCH),
        ) { active, document, features ->
            WatchPeerSnapshot(
                v2Active = active,
                watch = merger.merge(phone = null, watch = document).watch,
                features = features,
            )
        }.distinctUntilChanged()
}
```

In `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModel.kt`, replace the class header and the `connectionState` property (lines 21-28) with:

```kotlin
class WatchSettingsViewModel(
    private val connectionManager: WatchConnectionManager,
    private val settingsRepository: WatchSettingsRepository,
    peerSnapshots: WatchPeerSnapshotSource = WatchPeerSnapshotSource { flowOf(WatchPeerSnapshot.Inactive) },
    windowDays: Flow<Int> = flowOf(SharedPreferenceKey.WindowDays.default),
) : ViewModel() {
    val connectionState: StateFlow<WatchConnectionState> =
        connectionManager
            .observeConnectionState()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchConnectionState.Loading)

    /** What the Watch page shows: null until the phone has looked for the watch. */
    val status: StateFlow<WatchStatus?> =
        combine(connectionState, peerSnapshots.observe(), windowDays) { connection, peer, days ->
            WatchStatusResolver.resolve(connection, peer, days)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
```

and add the imports `app.logdate.client.domain.watch.SharedPreferenceKey`, `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.combine` and `kotlinx.coroutines.flow.flowOf`.

Replace the whole of `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/di/WatchSettingsModule.kt` with:

```kotlin
package app.logdate.feature.core.di

import app.logdate.client.domain.watch.DefaultWatchSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.domain.watch.WatchSettingsRepository
import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.ui.watch.WatchPeerSnapshotSource
import app.logdate.feature.core.settings.ui.watch.WatchSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Dependency injection module for the watch settings feature.
 *
 * The [WatchConnectionManager] and [WatchPeerSnapshotSource] bindings must be provided by the
 * platform module, since they depend on platform-specific Wearable APIs.
 */
val watchSettingsModule: Module =
    module {
        single<WatchSettingsRepository> { DefaultWatchSettingsRepository(get()) }
        viewModel {
            WatchSettingsViewModel(
                connectionManager = get(),
                settingsRepository = get(),
                peerSnapshots = get(),
                windowDays = get<WatchPreferencesRepository>().observe(SharedPreferenceKey.WindowDays),
            )
        }
    }
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt`, directly after the `WearProtocolGate` binding (`single { WearProtocolGate(…) }`), add:

```kotlin
        single<WatchPeerSnapshotSource> { PeerStateWatchSnapshotSource(gate = get(), peerStates = get()) }
```

and the imports `app.logdate.feature.core.settings.ui.watch.PeerStateWatchSnapshotSource` and `app.logdate.feature.core.settings.ui.watch.WatchPeerSnapshotSource`.

- [ ] **Step 5: Run the tests again**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.watch.*' --console=plain
```
Expected: PASS (14 `WatchStatusResolverTest`, 3 `PeerStateWatchSnapshotSourceTest`, 4 `WatchSettingsViewModelTest`).

- [ ] **Step 6: Compile for iOS and run the module checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:compileKotlinIosSimulatorArm64 :client:feature:core:compileTestKotlinIosSimulatorArm64 :client:feature:core:testAndroidHostTest :client:feature:core:ktlintCheck :client:feature:core:detekt :app:compose-main:ktlintCheck :app:android-main:compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task03-watch-status.txt <<'EOF'
chore(settings): describe the watch from what it reports

The Watch page needs one description of the watch that never claims
more than the phone knows. This adds a watch status built from the
phone's own view of the connection and the watch's state document:
no watch, setting up, not installed, ready, or needing attention.

A ready watch shows lines for how many moments wait to sync, how many
days its library covers, and whether it backs up through this phone
or by itself, but only once the watch lists the feature behind each
line while the redesigned watch experience is on. A watch that lost
its link to the account, or has moments that did not send, needs
attention. The Watch settings view model now exposes this status; the
screen still shows the old one until the next change.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatus.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusResolver.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/PeerStateWatchSnapshotSource.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModel.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/di/WatchSettingsModule.kt app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusResolverTest.kt client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/PeerStateWatchSnapshotSourceTest.kt client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModelTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task03-watch-status.txt
```

---
### Task 4: Show watch sync status only when the watch reports it (C3b, screen)

**Files:**
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchConnectionState.kt:37-53` (drop the two invented fields)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/WatchConnectionStateResolver.kt:59-114`
- Modify: `app/android-main/src/test/kotlin/app/logdate/client/watch/WatchConnectionStateResolverTest.kt:44-50, 86-92`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsScreen.kt` (whole file; 534 → about 300 lines)
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusCard.kt` (the status card, moved out of the screen file and rewritten for `WatchStatus`)
- Modify: `client/feature/core/src/commonMain/composeResources/values/strings_watch.xml`
- Modify: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusResolverTest.kt:18-19`, `WatchSettingsViewModelTest.kt` (the `Connected(…)` constructions)
- Test: `client/feature/core/src/desktopTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsStatusTest.kt`
- Modify: `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt` (scene ids, the watch scenes move out, `allScenes`, two helpers become `internal`)
- Create: `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/WatchSettingsScreenshotScenes.kt`
- Modify: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WatchPairingSettingsE2ETest.kt`
- Modify (regenerated): `app/compose-main/src/desktopTest/reference/watch-settings--{phone,phone-dark,phone-landscape,tablet}.png`
- Create (recorded): `app/compose-main/src/desktopTest/reference/watch-settings-reporting--{phone,phone-dark,phone-landscape,tablet}.png`, `watch-settings-attention--{phone,phone-dark,phone-landscape,tablet}.png`

**Interfaces:**
- Consumes: `WatchStatus` and its lines, `WatchSettingsViewModel.status` (Task 3).
- Produces: `WatchConnectionState.Connected(watchName: String)` and `OutOfRange(watchName: String)`; `fun WatchSettingsContent(status: WatchStatus?, requestFailure: WatchRequestFailure?, onBack: () -> Unit, onBeginAssociation: () -> Unit, onRequestSync: () -> Unit, onInstallOnWatch: () -> Unit, onNavigateToNotifications: () -> Unit, onNavigateToTroubleshooting: () -> Unit, modifier: Modifier = Modifier)`; `internal fun WatchStatusCard(status: WatchStatus?, requestFailure: WatchRequestFailure?, onBeginAssociation: () -> Unit, onInstallOnWatch: () -> Unit, modifier: Modifier = Modifier)`; `internal val watchSettingsScenes: List<SharedScreenshotSceneSpec>`; `internal fun sharedScene(…)` and `internal val standardMatrixVariants` in the catalog (Task 8 uses both).

- [ ] **Step 1: Write the failing screen test**

Create `client/feature/core/src/desktopTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsStatusTest.kt`:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

/**
 * The Watch page says only what the watch reported. A connected watch that reports nothing shows its
 * name and that it is connected, never a sync state the phone does not know.
 */
@OptIn(ExperimentalTestApi::class)
class WatchSettingsStatusTest {
    @Test
    fun `a connected watch that reports nothing shows no sync status`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent { WatchPage(WatchStatus.Ready(watchName = "Pixel Watch", nearby = true)) }

            onNodeWithText("Pixel Watch").assertExists()
            onNodeWithText("Connected").assertExists()
            onNodeWithText("Not yet synced").assertDoesNotExist()
            onNodeWithText("Up to date").assertDoesNotExist()
        }

    @Test
    fun `a watch that reports waiting moments says how many`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent { WatchPage(WatchStatus.Ready(watchName = "Pixel Watch", nearby = true, sync = WatchSyncLine(waiting = 2))) }

            onNodeWithText("2 entries waiting to sync").assertExists()
        }

    @Test
    fun `a watch that reports an empty queue is up to date`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent { WatchPage(WatchStatus.Ready(watchName = "Pixel Watch", nearby = true, sync = WatchSyncLine(waiting = 0))) }

            onNodeWithText("Up to date").assertExists()
        }

    @Test
    fun `reported link and library lines appear`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent {
                WatchPage(
                    WatchStatus.Ready(
                        watchName = "Pixel Watch",
                        nearby = true,
                        link = WatchLinkLine.ON_ITS_OWN,
                        window = WatchWindowLine(days = 14),
                    ),
                )
            }

            onNodeWithText("Backs up on its own over Wi-Fi or mobile data").assertExists()
            onNodeWithText("14 days of moments on your watch").assertExists()
        }

    @Test
    fun `moments that did not send are named`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent {
                WatchPage(
                    WatchStatus.NeedsAttention(
                        watchName = "Pixel Watch",
                        nearby = true,
                        issue = WatchIssue.CapturesNotSent(count = 3),
                    ),
                )
            }

            onNodeWithText("3 moments on your watch didn't send").assertExists()
        }

    @Test
    fun `a watch that lost its link asks to be set up again`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent { WatchPage(WatchStatus.NeedsAttention(watchName = "Pixel Watch", nearby = true, issue = WatchIssue.SetUpAgain)) }

            onNodeWithText("Set up LogDate on your watch again").assertExists()
        }
}

@Composable
private fun WatchPage(status: WatchStatus) {
    LogDateTheme(dynamicColor = false, darkTheme = false) {
        WatchSettingsContent(
            status = status,
            requestFailure = null,
            onBack = {},
            onBeginAssociation = {},
            onRequestSync = {},
            onInstallOnWatch = {},
            onNavigateToNotifications = {},
            onNavigateToTroubleshooting = {},
        )
    }
}
```

In `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WatchPairingSettingsE2ETest.kt`, add this test after `watch settings pending association state is rendered`:

```kotlin
    @Test
    fun `a connected watch that reports nothing shows no sync status`() {
        fakeConnectionManager.updateState(WatchConnectionState.Connected(watchName = "Pixel Watch"))

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithText("Not yet synced").assertDoesNotExist()
        composeRule.onNodeWithText("Up to date").assertDoesNotExist()
    }
```

and replace each of the three blocks

```kotlin
            WatchConnectionState.Connected(
                watchName = "Pixel Watch",
                lastSynced = null,
                pendingCount = 0,
            ),
```

with

```kotlin
            WatchConnectionState.Connected(watchName = "Pixel Watch"),
```

- [ ] **Step 2: Run the screen test and watch it fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.watch.WatchSettingsStatusTest' --console=plain
```
Expected: FAIL to compile: `No parameter with name 'status' found` for `WatchSettingsContent`. This task changes the screen's input type, so the first red is the signature; Step 4 turns it into behavior.

- [ ] **Step 3: Drop the invented connection fields**

In `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchConnectionState.kt`, replace the `Connected` and `OutOfRange` declarations with:

```kotlin
    /**
     * LogDate is installed on the watch and communication is possible.
     */
    data class Connected(
        val watchName: String,
    ) : WatchConnectionState

    /**
     * The watch was previously connected but is currently out of range.
     */
    data class OutOfRange(
        val watchName: String,
    ) : WatchConnectionState
```

and remove the now unused `import kotlin.time.Instant`.

In `app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/WatchConnectionStateResolver.kt`, replace `resolveLegacyWatchConnectionState` and `resolveAssociatedWatchConnectionState` with:

```kotlin
private fun resolveLegacyWatchConnectionState(transport: WatchTransportSnapshot): WatchConnectionState {
    val connectedWatch = transport.connectedNodes.firstOrNull() ?: return WatchConnectionState.NoPairedWatch
    val installedNodes = installedConnectedNodes(transport)
    if (installedNodes.isEmpty()) {
        return WatchConnectionState.AppNotInstalled(connectedWatch.displayName)
    }
    val watchName = installedNodes.first().displayName
    if (installedNodes.any(WatchNodeSnapshot::isNearby)) return WatchConnectionState.Connected(watchName)
    return WatchConnectionState.OutOfRange(watchName)
}

/** Resolves watch state once LogDate already has an app-level companion association. */
private fun resolveAssociatedWatchConnectionState(
    association: WatchAssociationSnapshot.Associated,
    transport: WatchTransportSnapshot,
): WatchConnectionState {
    val installedNodes = installedConnectedNodes(transport)
    val connectedWatch = transport.connectedNodes.firstOrNull()
    val watchName =
        installedNodes.firstOrNull()?.displayName ?: connectedWatch?.displayName ?: association.displayName ?: FALLBACK_WATCH_NAME

    if (connectedWatch == null) return WatchConnectionState.OutOfRange(watchName)
    if (installedNodes.isEmpty()) return WatchConnectionState.AppNotInstalled(watchName)
    if (installedNodes.any(WatchNodeSnapshot::isNearby)) return WatchConnectionState.Connected(watchName)
    return WatchConnectionState.OutOfRange(watchName)
}
```

In `app/android-main/src/test/kotlin/app/logdate/client/watch/WatchConnectionStateResolverTest.kt`, replace the expected `WatchConnectionState.Connected(watchName = "Pixel Watch", lastSynced = null, pendingCount = 0)` block with `WatchConnectionState.Connected(watchName = "Pixel Watch")` and the expected `WatchConnectionState.OutOfRange(watchName = "Pixel Watch", lastSynced = null)` block with `WatchConnectionState.OutOfRange(watchName = "Pixel Watch")`.

In `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusResolverTest.kt`, replace the two fields with:

```kotlin
    private val connected = WatchConnectionState.Connected(watchName = NAME)
    private val outOfRange = WatchConnectionState.OutOfRange(watchName = NAME)
```

In `WatchSettingsViewModelTest.kt`, replace `WatchConnectionState.Connected(watchName = "Pixel Watch", lastSynced = null, pendingCount = 0)` with `WatchConnectionState.Connected(watchName = "Pixel Watch")`.

In `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt`:

1. After `    WatchSettings("watch-settings"),` add:

```kotlin
    WatchSettingsReporting("watch-settings-reporting"),
    WatchSettingsAttention("watch-settings-attention"),
```

2. Delete the four watch scenes from `settingsScenes`: everything from `            sharedScene(SharedScreenshotSceneId.WatchSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {` through the `            },` that closes `SharedScreenshotSceneId.WatchTroubleshootingRequestFailed` (lines 1121-1172), so `EventsSettings` follows `LabsSettings`.
3. Delete the imports `app.logdate.client.domain.watch.WatchNotificationSettings`, `app.logdate.feature.core.settings.ui.watch.WatchConnectionState`, `app.logdate.feature.core.settings.ui.watch.WatchNotificationSettingsContent`, `app.logdate.feature.core.settings.ui.watch.WatchRequestFailure`, `app.logdate.feature.core.settings.ui.watch.WatchSettingsContent` and `app.logdate.feature.core.settings.ui.watch.WatchTroubleshootingContent`.
4. Change `private val standardMatrixVariants =` to `internal val standardMatrixVariants =` and `private fun sharedScene(` to `internal fun sharedScene(`.
5. Replace the `allScenes` initializer with:

```kotlin
    val allScenes: List<SharedScreenshotSceneSpec> =
        onboardingMainScenes + onboardingExtendedScenes + settingsScenes + watchSettingsScenes + searchScenes + desktopOnlyScenes
```

Create `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/WatchSettingsScreenshotScenes.kt`:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.screenshots.shared

import androidx.compose.runtime.Composable
import app.logdate.client.domain.watch.WatchNotificationSettings
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import app.logdate.feature.core.settings.ui.watch.WatchIssue
import app.logdate.feature.core.settings.ui.watch.WatchLinkLine
import app.logdate.feature.core.settings.ui.watch.WatchNotificationSettingsContent
import app.logdate.feature.core.settings.ui.watch.WatchRequestFailure
import app.logdate.feature.core.settings.ui.watch.WatchSettingsContent
import app.logdate.feature.core.settings.ui.watch.WatchStatus
import app.logdate.feature.core.settings.ui.watch.WatchSyncLine
import app.logdate.feature.core.settings.ui.watch.WatchTroubleshootingContent
import app.logdate.feature.core.settings.ui.watch.WatchWindowLine

/** The Watch page and its detail screens, in the states a phone can be in with its watch. */
internal val watchSettingsScenes: List<SharedScreenshotSceneSpec> =
    listOf(
        sharedScene(SharedScreenshotSceneId.WatchSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchSettingsPage(WatchStatus.Ready(watchName = WATCH_NAME, nearby = true))
        },
        sharedScene(SharedScreenshotSceneId.WatchSettingsReporting, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchSettingsPage(
                WatchStatus.Ready(
                    watchName = WATCH_NAME,
                    nearby = true,
                    link = WatchLinkLine.ON_ITS_OWN,
                    sync = WatchSyncLine(waiting = 2),
                    window = WatchWindowLine(days = 14),
                    capabilities = setOf(WearFeatures.RECEIPTS, WearFeatures.WINDOW, WearFeatures.LINK_CLOUD),
                ),
            )
        },
        sharedScene(SharedScreenshotSceneId.WatchSettingsAttention, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchSettingsPage(
                WatchStatus.NeedsAttention(
                    watchName = WATCH_NAME,
                    nearby = true,
                    issue = WatchIssue.CapturesNotSent(count = 3),
                ),
            )
        },
        sharedScene(SharedScreenshotSceneId.WatchNotificationSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchNotificationSettingsContent(
                settings = WatchNotificationSettings(showEntryNotifications = true),
                onBack = {},
                onSetShowEntryNotifications = {},
            )
        },
        sharedScene(SharedScreenshotSceneId.WatchTroubleshooting, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchTroubleshootingContent(
                connectionState = WatchConnectionState.AppNotInstalled(watchName = WATCH_NAME),
                requestFailure = null,
                onBack = {},
                onBeginAssociation = {},
                onInstallOnWatch = {},
                onOpenOnWatch = {},
            )
        },
        sharedScene(SharedScreenshotSceneId.WatchTroubleshootingRequestFailed, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchTroubleshootingContent(
                connectionState = WatchConnectionState.Connected(watchName = WATCH_NAME),
                requestFailure = WatchRequestFailure.WATCH_DID_NOT_RESPOND,
                onBack = {},
                onBeginAssociation = {},
                onInstallOnWatch = {},
                onOpenOnWatch = {},
            )
        },
    )

@Composable
private fun WatchSettingsPage(status: WatchStatus) {
    WatchSettingsContent(
        status = status,
        requestFailure = null,
        onBack = {},
        onBeginAssociation = {},
        onRequestSync = {},
        onInstallOnWatch = {},
        onNavigateToNotifications = {},
        onNavigateToTroubleshooting = {},
    )
}

private const val WATCH_NAME = "Pixel Watch 3"
```

- [ ] **Step 4: Render the status on the Watch page**

In `client/feature/core/src/commonMain/composeResources/values/strings_watch.xml`, delete the `watch_sync_not_yet` and `watch_sync_last_connected` strings, and after `<string name="watch_out_of_range">Out of range</string>` add:

```xml
    <string name="watch_unsupported">This device can&apos;t connect to a watch</string>
    <string name="watch_link_through_phone">Syncs through this phone</string>
    <string name="watch_link_on_its_own">Backs up on its own over Wi-Fi or mobile data</string>
    <string name="watch_window_days">%1$d days of moments on your watch</string>
    <string name="watch_attention_set_up_again">Set up LogDate on your watch again</string>
    <string name="watch_attention_set_up_again_description">Your watch is no longer connected to your account. Open LogDate on your watch while it&apos;s near this phone.</string>
    <string name="watch_attention_not_sent">%1$d moments on your watch didn&apos;t send</string>
    <string name="watch_attention_not_sent_description">Your watch tries again on its own. Bring it close to this phone with Bluetooth on.</string>
```

Create `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusCard.kt`:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_app_not_installed
import logdate.client.feature.core.generated.resources.watch_app_not_installed_description
import logdate.client.feature.core.generated.resources.watch_association_pending
import logdate.client.feature.core.generated.resources.watch_association_pending_description
import logdate.client.feature.core.generated.resources.watch_attention_not_sent
import logdate.client.feature.core.generated.resources.watch_attention_not_sent_description
import logdate.client.feature.core.generated.resources.watch_attention_set_up_again
import logdate.client.feature.core.generated.resources.watch_attention_set_up_again_description
import logdate.client.feature.core.generated.resources.watch_connected
import logdate.client.feature.core.generated.resources.watch_install_on_watch
import logdate.client.feature.core.generated.resources.watch_link_on_its_own
import logdate.client.feature.core.generated.resources.watch_link_through_phone
import logdate.client.feature.core.generated.resources.watch_loading
import logdate.client.feature.core.generated.resources.watch_needs_association
import logdate.client.feature.core.generated.resources.watch_needs_association_description
import logdate.client.feature.core.generated.resources.watch_no_paired
import logdate.client.feature.core.generated.resources.watch_no_paired_description
import logdate.client.feature.core.generated.resources.watch_out_of_range
import logdate.client.feature.core.generated.resources.watch_pair_watch
import logdate.client.feature.core.generated.resources.watch_unsupported
import logdate.client.feature.core.generated.resources.watch_window_days
import org.jetbrains.compose.resources.stringResource

/** The card at the top of the Watch page: what the phone knows about its watch, and the next step. */
@Composable
internal fun WatchStatusCard(
    status: WatchStatus?,
    requestFailure: WatchRequestFailure?,
    onBeginAssociation: () -> Unit,
    onInstallOnWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialContainer(modifier = modifier) {
        when (status) {
            null -> LookingForWatch()
            WatchStatus.Unsupported ->
                CenteredStatus(
                    icon = Icons.Outlined.Watch,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(Res.string.watch_unsupported),
                )
            WatchStatus.NoWatch ->
                CenteredStatus(
                    icon = Icons.Outlined.Watch,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(Res.string.watch_no_paired),
                    description = stringResource(Res.string.watch_no_paired_description),
                )
            is WatchStatus.SettingUp -> SettingUpStatus(status.step, onBeginAssociation)
            is WatchStatus.NotInstalled ->
                CenteredStatus(
                    icon = Icons.Default.Download,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(Res.string.watch_app_not_installed),
                    description = stringResource(Res.string.watch_app_not_installed_description, status.watchName),
                ) {
                    Button(onClick = onInstallOnWatch) {
                        Text(stringResource(Res.string.watch_install_on_watch))
                    }
                    requestFailure?.let { WatchRequestFailureMessage(it) }
                }
            is WatchStatus.Ready -> ReadyStatus(status)
            is WatchStatus.NeedsAttention -> AttentionStatus(status)
        }
    }
}

@Composable
private fun LookingForWatch() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(
            text = stringResource(Res.string.watch_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SettingUpStatus(
    step: WatchSetupStep,
    onBeginAssociation: () -> Unit,
) {
    when (step) {
        is WatchSetupStep.PairWithPhone ->
            CenteredStatus(
                icon = Icons.Default.Watch,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(Res.string.watch_needs_association),
                description = stringResource(Res.string.watch_needs_association_description, step.watchName),
            ) {
                Button(onClick = onBeginAssociation) {
                    Text(stringResource(Res.string.watch_pair_watch))
                }
            }
        WatchSetupStep.ConfirmOnPhone ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Text(
                    text = stringResource(Res.string.watch_association_pending),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(Res.string.watch_association_pending_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun CenteredStatus(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = iconTint,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        actions()
    }
}

@Composable
private fun ReadyStatus(status: WatchStatus.Ready) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WatchNameRow(
            name = status.watchName,
            supporting = stringResource(if (status.nearby) Res.string.watch_connected else Res.string.watch_out_of_range),
            accent = if (status.nearby) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val lines =
            listOfNotNull(
                status.link?.let { link ->
                    when (link) {
                        WatchLinkLine.THROUGH_PHONE -> stringResource(Res.string.watch_link_through_phone)
                        WatchLinkLine.ON_ITS_OWN -> stringResource(Res.string.watch_link_on_its_own)
                    }
                },
                status.window?.let { stringResource(Res.string.watch_window_days, it.days) },
            )
        if (lines.isEmpty()) return@Column
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AttentionStatus(status: WatchStatus.NeedsAttention) {
    val (title, description) =
        when (val issue = status.issue) {
            WatchIssue.SetUpAgain ->
                stringResource(Res.string.watch_attention_set_up_again) to
                    stringResource(Res.string.watch_attention_set_up_again_description)
            is WatchIssue.CapturesNotSent ->
                stringResource(Res.string.watch_attention_not_sent, issue.count) to
                    stringResource(Res.string.watch_attention_not_sent_description)
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        WatchNameRow(name = status.watchName, supporting = title, accent = MaterialTheme.colorScheme.error)
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        )
    }
}

@Composable
private fun WatchNameRow(
    name: String,
    supporting: String,
    accent: Color,
) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = {
            Text(
                text = supporting,
                color = accent,
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.Watch,
                contentDescription = null,
                tint = accent,
            )
        },
    )
}
```

Replace the whole of `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsScreen.kt` with:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_help
import logdate.client.feature.core.generated.resources.watch_notifications
import logdate.client.feature.core.generated.resources.watch_notifications_description
import logdate.client.feature.core.generated.resources.watch_notifications_title
import logdate.client.feature.core.generated.resources.watch_privacy_description
import logdate.client.feature.core.generated.resources.watch_privacy_title
import logdate.client.feature.core.generated.resources.watch_settings
import logdate.client.feature.core.generated.resources.watch_sync
import logdate.client.feature.core.generated.resources.watch_sync_now
import logdate.client.feature.core.generated.resources.watch_sync_pending
import logdate.client.feature.core.generated.resources.watch_sync_up_to_date
import logdate.client.feature.core.generated.resources.watch_troubleshooting
import logdate.client.feature.core.generated.resources.watch_troubleshooting_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WatchSettingsScreen(
    onBack: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToTroubleshooting: () -> Unit,
    viewModel: WatchSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val status by viewModel.status.collectAsState()
    val requestFailure by viewModel.requestFailure.collectAsState()

    WatchSettingsContent(
        status = status,
        requestFailure = requestFailure,
        onBack = onBack,
        onBeginAssociation = viewModel::beginAssociation,
        onRequestSync = viewModel::requestSync,
        onInstallOnWatch = viewModel::installAppOnWatch,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToTroubleshooting = onNavigateToTroubleshooting,
        modifier = modifier,
    )
}

@Composable
fun WatchSettingsContent(
    status: WatchStatus?,
    requestFailure: WatchRequestFailure?,
    onBack: () -> Unit,
    onBeginAssociation: () -> Unit,
    onRequestSync: () -> Unit,
    onInstallOnWatch: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToTroubleshooting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                WatchStatusCard(
                    status = status,
                    requestFailure = requestFailure,
                    onBeginAssociation = onBeginAssociation,
                    onInstallOnWatch = onInstallOnWatch,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
                WatchPrivacyNote()
            }
        },
        endPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                WatchConnectedSections(
                    status = status,
                    onRequestSync = onRequestSync,
                    onNavigateToNotifications = onNavigateToNotifications,
                )
                WatchTroubleshootingSection(onNavigateToTroubleshooting)
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.watch_settings),
                onBack = onBack,
                modifier = modifier,
            ) {
                item {
                    WatchStatusCard(
                        status = status,
                        requestFailure = requestFailure,
                        onBeginAssociation = onBeginAssociation,
                        onInstallOnWatch = onInstallOnWatch,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                if (status.hasLogDate()) {
                    item {
                        WatchConnectedSections(
                            status = status,
                            onRequestSync = onRequestSync,
                            onNavigateToNotifications = onNavigateToNotifications,
                        )
                    }
                }
                item {
                    WatchTroubleshootingSection(onNavigateToTroubleshooting)
                }
                item {
                    WatchPrivacyNote()
                }
            }
        },
    )
}

/** Sync and notification settings exist only for a watch that has LogDate. */
private fun WatchStatus?.hasLogDate(): Boolean = this is WatchStatus.Ready || this is WatchStatus.NeedsAttention

@Composable
private fun WatchConnectedSections(
    status: WatchStatus?,
    onRequestSync: () -> Unit,
    onNavigateToNotifications: () -> Unit,
) {
    val nearby =
        when (status) {
            is WatchStatus.Ready -> status.nearby
            is WatchStatus.NeedsAttention -> status.nearby
            else -> return
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SettingsSection(
            title = stringResource(Res.string.watch_sync),
            modifier = Modifier.padding(horizontal = Spacing.lg),
        ) {
            (status as? WatchStatus.Ready)?.sync?.let { SyncStatusRow(it) }

            OutlinedButton(
                onClick = onRequestSync,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                enabled = nearby,
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(Res.string.watch_sync_now))
            }
        }

        SettingsSection(
            title = stringResource(Res.string.watch_notifications),
            modifier = Modifier.padding(horizontal = Spacing.lg),
        ) {
            SettingsNavItem(
                title = stringResource(Res.string.watch_notifications_title),
                description = stringResource(Res.string.watch_notifications_description),
                onClick = onNavigateToNotifications,
            )
        }
    }
}

/** Shown only when the watch reported how many moments wait; the phone does not know otherwise. */
@Composable
private fun SyncStatusRow(sync: WatchSyncLine) {
    val text =
        if (sync.waiting > 0) {
            stringResource(Res.string.watch_sync_pending, sync.waiting)
        } else {
            stringResource(Res.string.watch_sync_up_to_date)
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.sm),
    )
}

@Composable
private fun WatchTroubleshootingSection(onNavigateToTroubleshooting: () -> Unit) {
    SettingsSection(
        title = stringResource(Res.string.watch_help),
        modifier = Modifier.padding(horizontal = Spacing.lg),
    ) {
        SettingsNavItem(
            title = stringResource(Res.string.watch_troubleshooting),
            description = stringResource(Res.string.watch_troubleshooting_description),
            onClick = onNavigateToTroubleshooting,
        )
    }
}

@Composable
private fun SettingsNavItem(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun WatchPrivacyNote() {
    Surface {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    Icons.Default.Watch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.watch_privacy_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = stringResource(Res.string.watch_privacy_description),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

- [ ] **Step 5: Run the tests**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.watch.*' :client:screenshot-scenes:desktopTest :app:android-main:testDebugUnitTest --tests 'app.logdate.client.watch.WatchConnectionStateResolverTest' :app:android-main:compileDebugAndroidTestKotlin --console=plain
```
Expected: PASS (6 `WatchSettingsStatusTest`, the Task 3 watch tests, `SharedScreenshotCatalogTest`, 5 `WatchConnectionStateResolverTest`); the instrumented sources compile.

- [ ] **Step 6: Record and look at the Watch page scenes**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=watch-settings :app:compose-main:updateDesktopScreenshotTest --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared*' --console=plain
```
Expected: BUILD SUCCESSFUL; `git status --short app/compose-main/src/desktopTest/reference` shows the four `watch-settings--*.png` modified and eight new files (`watch-settings-reporting--*`, `watch-settings-attention--*`), nothing else.

Open each of the twelve with the Read tool. `watch-settings--phone.png`: the card shows "Pixel Watch 3" and "Connected", and the Sync section has only the "Sync Now" button (no "Not yet synced" and no "entries waiting" line). `watch-settings-reporting--phone.png`: the card adds "Backs up on its own over Wi-Fi or mobile data" and "14 days of moments on your watch"; the Sync section reads "2 entries waiting to sync". `watch-settings-attention--phone.png`: the card's supporting line reads "3 moments on your watch didn't send" in the error color with the explanation below it. Check the dark, landscape and tablet variants for clipping and contrast.

Then validate every watch scene:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=watch- :app:compose-main:desktopTest --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared*' --console=plain
```
Expected: PASS (the troubleshooting and notification baselines are unchanged).

- [ ] **Step 7: Run the Watch settings flow on a managed device**

Run (`uptime` first):
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:smokeDevicesGroupDebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.e2e.WatchPairingSettingsE2ETest --console=plain
```
Expected: PASS, 6 tests.

- [ ] **Step 8: Run the module checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:compileKotlinIosSimulatorArm64 :client:feature:core:compileTestKotlinIosSimulatorArm64 :client:screenshot-scenes:compileKotlinIosSimulatorArm64 :client:feature:core:ktlintCheck :client:screenshot-scenes:ktlintCheck :app:compose-main:ktlintCheck :app:android-main:ktlintCheck :client:feature:core:detekt :client:screenshot-scenes:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task04-watch-status-screen.txt <<'EOF'
fix(settings): show watch sync status only when the watch reports it

The Watch page said a connected watch was "Not yet synced" no matter
what had happened, because the phone never actually knew. It now
shows a sync line only when the watch reports how many moments are
waiting; otherwise it shows the watch's name and whether it is
connected or out of range.

With the redesigned watch experience turned on in Labs, a watch that
reports more also shows whether it backs up through this phone or on
its own, how many days of moments it has, and a note when moments on
it did not send or it needs to be set up again.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 10: Commit**

```bash
git restore --staged . && git add client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchConnectionState.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/WatchConnectionStateResolver.kt app/android-main/src/test/kotlin/app/logdate/client/watch/WatchConnectionStateResolverTest.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsScreen.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusCard.kt client/feature/core/src/commonMain/composeResources/values/strings_watch.xml client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchStatusResolverTest.kt client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsViewModelTest.kt client/feature/core/src/desktopTest/kotlin/app/logdate/feature/core/settings/ui/watch/WatchSettingsStatusTest.kt client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/WatchSettingsScreenshotScenes.kt app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/WatchPairingSettingsE2ETest.kt app/compose-main/src/desktopTest/reference/watch-settings--phone.png app/compose-main/src/desktopTest/reference/watch-settings--phone-dark.png app/compose-main/src/desktopTest/reference/watch-settings--phone-landscape.png app/compose-main/src/desktopTest/reference/watch-settings--tablet.png app/compose-main/src/desktopTest/reference/watch-settings-reporting--phone.png app/compose-main/src/desktopTest/reference/watch-settings-reporting--phone-dark.png app/compose-main/src/desktopTest/reference/watch-settings-reporting--phone-landscape.png app/compose-main/src/desktopTest/reference/watch-settings-reporting--tablet.png app/compose-main/src/desktopTest/reference/watch-settings-attention--phone.png app/compose-main/src/desktopTest/reference/watch-settings-attention--phone-dark.png app/compose-main/src/desktopTest/reference/watch-settings-attention--phone-landscape.png app/compose-main/src/desktopTest/reference/watch-settings-attention--tablet.png && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task04-watch-status-screen.txt
```

---
### Task 5: Record which device captured each moment (D1, storage)

**Files:**
- Create: `client/repository/src/commonMain/kotlin/app/logdate/client/repository/journals/CaptureDevice.kt`
- Modify: `client/repository/src/commonMain/kotlin/app/logdate/client/repository/journals/JournalNotesRepository.kt:243-327` (abstract property and one parameter per note type)
- Test: `client/repository/src/commonTest/kotlin/app/logdate/client/repository/journals/CaptureDeviceTest.kt`
- Modify: `client/database/src/commonMain/kotlin/app/logdate/client/database/entities/GenericNoteData.kt`, `TextNoteEntity.kt`, `ImageNoteEntity.kt`, `VideoNoteEntity.kt`, `AudioNoteEntity.kt` (one column each)
- Create: `client/database/src/commonMain/kotlin/app/logdate/client/database/migrations/MIGRATION_53_54.kt`
- Modify: `client/database/src/commonMain/kotlin/app/logdate/client/database/LogDateDatabase.kt:151, 228, 440` (import, version, migration list)
- Create (generated): `client/database/schemas/app.logdate.client.database.LogDateDatabase/54.json`
- Test: `client/database/src/jvmTest/kotlin/app/logdate/client/database/CapturedOnMigrationJvmTest.kt`
- Modify: `client/data/src/commonMain/kotlin/app/logdate/client/data/notes/NoteObjectMappers.kt` (eight mappers)
- Test: `client/data/src/commonTest/kotlin/app/logdate/client/data/notes/CapturedOnMappingTest.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/conflict/JournalNoteConflictResolver.kt:10-38`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloader.kt:345-391` (`uploadedFields`)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/conflict/JournalNoteCaptureDeviceConflictTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/NoteDataMapperCapturedOnTest.kt`
- Modify: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/CreateReplayReconciliationTest.kt:36-44, 85`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt:76-176`
- Create: `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchIngestorTestDoubles.kt` (the two doubles moved out of `WatchNoteIngestorTest.kt`)
- Modify: `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteIngestorTest.kt` (two expectations; the doubles move out; 552 → about 485 lines)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteProvenanceTest.kt`

**Interfaces:**
- Consumes: `NoteDataMapper` (`client/sync`), `JournalNoteConflictResolver`, `withTimeZoneId`.
- Produces:
  - `enum class CaptureDevice(val wireName: String) { PHONE("phone"), WATCH("watch"); companion object { fun fromWireName(wireName: String?): CaptureDevice? } }`
  - `object LenientCaptureDeviceSerializer : KSerializer<CaptureDevice?>`
  - `abstract val JournalNote.capturedOn: CaptureDevice?` and a trailing `capturedOn: CaptureDevice? = null` parameter on `JournalNote.Text`, `Image`, `Video` and `Audio`
  - `val JournalNote.isCapturedOnWatch: Boolean`, `fun JournalNote.withCapturedOn(capturedOn: CaptureDevice?): JournalNote`
  - Room 54: column `captured_on TEXT` (nullable) on `text_notes`, `image_notes`, `video_notes`, `audio_notes`; `abstract val GenericNoteData.capturedOn: String?`
  - Tasks 6, 7 and 8 read and write `capturedOn` through these.

- [ ] **Step 1: Write the failing tests**

Create `client/repository/src/commonTest/kotlin/app/logdate/client/repository/journals/CaptureDeviceTest.kt`:

```kotlin
package app.logdate.client.repository.journals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Tests [CaptureDevice] and the note helpers built on it. */
class CaptureDeviceTest {
    private val at = Instant.fromEpochMilliseconds(1_000)
    private val notes =
        listOf(
            JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Walk"),
            JournalNote.Image(creationTimestamp = at, lastUpdated = at, mediaRef = "photo.jpg"),
            JournalNote.Video(creationTimestamp = at, lastUpdated = at, mediaRef = "clip.mp4"),
            JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = at, lastUpdated = at),
        )

    @Test
    fun `a wire name reads back as its device`() {
        assertEquals(CaptureDevice.WATCH, CaptureDevice.fromWireName("watch"))
        assertEquals(CaptureDevice.PHONE, CaptureDevice.fromWireName("phone"))
    }

    @Test
    fun `an unknown or missing wire name is unknown`() {
        assertNull(CaptureDevice.fromWireName("tablet"))
        assertNull(CaptureDevice.fromWireName("WATCH"))
        assertNull(CaptureDevice.fromWireName(null))
    }

    @Test
    fun `a note has no capture device until one is recorded`() {
        notes.forEach { note ->
            assertNull(note.capturedOn)
            assertFalse(note.isCapturedOnWatch)
        }
    }

    @Test
    fun `recording the capture device changes nothing else`() {
        notes.forEach { note ->
            val stamped = note.withCapturedOn(CaptureDevice.WATCH)

            assertEquals(CaptureDevice.WATCH, stamped.capturedOn)
            assertTrue(stamped.isCapturedOnWatch)
            assertEquals(note, stamped.withCapturedOn(null))
        }
    }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/NoteDataMapperCapturedOnTest.kt`:

```kotlin
package app.logdate.client.sync.datalayer

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The capture device travels in the note messages the watch sends. Watches that predate it send no
 * device, and a device a newer version names must not stop the note from being read.
 */
class NoteDataMapperCapturedOnTest {
    private val mapper = NoteDataMapper()
    private val at = Instant.fromEpochMilliseconds(1_000)
    private val note = JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = at, lastUpdated = at, capturedOn = CaptureDevice.WATCH)

    @Test
    fun `a note captured on the watch keeps its device through a message`() {
        assertEquals(note, mapper.fromDataMap(mapper.toDataMap(note)))
    }

    @Test
    fun `the device is written as its wire name`() {
        val payload = mapper.toDataMap(note).getValue(NoteDataMapper.KEY_JSON_PAYLOAD)

        assertEquals(JsonPrimitive("watch"), Json.parseToJsonElement(payload).jsonObject["capturedOn"])
    }

    @Test
    fun `a note from a watch that predates the device has none`() {
        val decoded = mapper.fromDataMap(withPayload { it - "capturedOn" })

        assertNull(decoded.capturedOn)
    }

    @Test
    fun `a device this version does not know reads as unknown`() {
        val decoded = mapper.fromDataMap(withPayload { it + ("capturedOn" to JsonPrimitive("tablet")) })

        assertNull(decoded.capturedOn)
        assertTrue(decoded is JournalNote.Audio)
    }

    private fun withPayload(edit: (JsonObject) -> Map<String, JsonElement>): Map<String, String> {
        val map = mapper.toDataMap(note)
        val payload = Json.parseToJsonElement(map.getValue(NoteDataMapper.KEY_JSON_PAYLOAD)).jsonObject
        return map + (NoteDataMapper.KEY_JSON_PAYLOAD to JsonObject(edit(payload)).toString())
    }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/conflict/JournalNoteCaptureDeviceConflictTest.kt`:

```kotlin
package app.logdate.client.sync.conflict

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A note's capture device is written once, where the note was captured. A copy that lacks it (an
 * older app version, or a server that predates the field) must never erase it.
 */
class JournalNoteCaptureDeviceConflictTest {
    private val resolver = JournalNoteConflictResolver()
    private val noteId = Uuid.random()
    private val created = Instant.fromEpochMilliseconds(1_710_000_000_000)
    private val later = Instant.fromEpochMilliseconds(1_710_000_100_000)

    private fun text(
        content: String,
        capturedOn: CaptureDevice?,
    ) = JournalNote.Text(
        uid = noteId,
        creationTimestamp = created,
        lastUpdated = created,
        content = content,
        capturedOn = capturedOn,
    )

    private fun resolve(
        local: JournalNote,
        remote: JournalNote,
    ) = resolver.resolve(local, remote, created, later)

    @Test
    fun `a remote copy without a device keeps the local device when the content matches`() {
        val resolved = assertIs<ConflictResolution.KeepRemote<JournalNote>>(resolve(text("Walk", CaptureDevice.WATCH), text("Walk", null)))

        assertEquals(CaptureDevice.WATCH, resolved.value.capturedOn)
    }

    @Test
    fun `a remote copy without a device keeps the local device when the remote content wins`() {
        val resolution = resolve(text("Walk", CaptureDevice.WATCH), text("Walk by the river", null))

        val resolved = assertIs<ConflictResolution.KeepRemote<JournalNote>>(resolution)
        assertEquals("Walk by the river", (resolved.value as JournalNote.Text).content)
        assertEquals(CaptureDevice.WATCH, resolved.value.capturedOn)
    }

    @Test
    fun `a local copy without a device takes the remote device when the local content wins`() {
        val resolution = resolve(text("Walk by the river", null), text("Walk", CaptureDevice.WATCH))

        val resolved = assertIs<ConflictResolution.KeepLocal<JournalNote>>(resolution)
        assertEquals(CaptureDevice.WATCH, resolved.value.capturedOn)
    }

    @Test
    fun `a merge keeps a device from either copy`() {
        val withLocal = resolve(text("Morning", CaptureDevice.WATCH), text("Evening", null))
        val withRemote = resolve(text("Morning", null), text("Evening", CaptureDevice.WATCH))

        assertEquals(CaptureDevice.WATCH, assertIs<ConflictResolution.Merge<JournalNote>>(withLocal).merged.capturedOn)
        assertEquals(CaptureDevice.WATCH, assertIs<ConflictResolution.Merge<JournalNote>>(withRemote).merged.capturedOn)
    }

    @Test
    fun `an audio note keeps its local device when the remote copy has none`() {
        val local =
            JournalNote.Audio(
                uid = noteId,
                mediaRef = "voice.m4a",
                creationTimestamp = created,
                lastUpdated = created,
                capturedOn = CaptureDevice.WATCH,
            )

        val resolved = assertIs<ConflictResolution.KeepRemote<JournalNote>>(resolve(local, local.copy(capturedOn = null)))
        assertEquals(CaptureDevice.WATCH, resolved.value.capturedOn)
    }
}
```

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/CreateReplayReconciliationTest.kt`, add `capturedOn = CaptureDevice.WATCH,` after `timeZoneId = "America/Los_Angeles",` in the local note, add `assertEquals(local.capturedOn, notes.getNoteById(local.uid)?.capturedOn)` after the `timeZoneId` assertion, and add the import `app.logdate.client.repository.journals.CaptureDevice`. (The server copy in that test carries no device, as an older server's does.)

Create `client/data/src/commonTest/kotlin/app/logdate/client/data/notes/CapturedOnMappingTest.kt`:

```kotlin
package app.logdate.client.data.notes

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CapturedOnMappingTest {
    private val now = Instant.fromEpochMilliseconds(1_000)

    @Test
    fun `database mapping keeps the capture device of every note type`() {
        val text = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Walk", capturedOn = CaptureDevice.WATCH)
        val image = JournalNote.Image(creationTimestamp = now, lastUpdated = now, mediaRef = "photo.jpg", capturedOn = CaptureDevice.WATCH)
        val video = JournalNote.Video(creationTimestamp = now, lastUpdated = now, mediaRef = "clip.mp4", capturedOn = CaptureDevice.PHONE)
        val audio = JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = now, lastUpdated = now, capturedOn = CaptureDevice.WATCH)

        assertEquals("watch", text.toEntity().capturedOn)
        assertEquals(CaptureDevice.WATCH, text.toEntity().toModel().capturedOn)
        assertEquals(CaptureDevice.WATCH, image.toEntity().toModel().capturedOn)
        assertEquals(CaptureDevice.PHONE, video.toEntity().toModel().capturedOn)
        assertEquals(CaptureDevice.WATCH, audio.toEntity().toModel().capturedOn)
    }

    @Test
    fun `a note without a device stores none`() {
        assertNull(JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Walk").toEntity().capturedOn)
    }

    @Test
    fun `a stored device this version does not know reads as unknown`() {
        val entity = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Walk").toEntity().copy(capturedOn = "tablet")

        assertNull(entity.toModel().capturedOn)
    }
}
```

Create `client/database/src/jvmTest/kotlin/app/logdate/client/database/CapturedOnMigrationJvmTest.kt`:

```kotlin
package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_53_54
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the 53 to 54 migration through Room's own schema validation on the exported schemas, the same
 * check Room makes when the app opens an upgraded database. Every note survives and none claims a
 * capture device it never recorded.
 */
class CapturedOnMigrationJvmTest {
    private val databaseFile = Files.createTempFile("logdate-migration", ".db")

    private val helper =
        MigrationTestHelper(
            Path.of("schemas"),
            databaseFile,
            BundledSQLiteDriver(),
            LogDateDatabase::class,
            { LogDateDatabaseConstructor.initialize() },
            emptyList(),
        )

    @AfterTest
    fun tearDown() {
        Files.deleteIfExists(databaseFile)
    }

    @Test
    fun `room accepts the migrated schema and existing notes have no capture device`() {
        helper.createDatabase(53).use { connection ->
            connection.execSQL(
                "INSERT INTO text_notes (uid, content, lastUpdated, created, syncVersion) " +
                    "VALUES ('text-1', 'Walk', 1710000000000, 1710000000000, 0)",
            )
            connection.execSQL(
                "INSERT INTO image_notes (uid, contentUri, lastUpdated, created, syncVersion) " +
                    "VALUES ('image-1', 'photo.jpg', 1710000000000, 1710000000000, 0)",
            )
            connection.execSQL(
                "INSERT INTO video_notes (uid, contentUri, lastUpdated, created, syncVersion) " +
                    "VALUES ('video-1', 'clip.mp4', 1710000000000, 1710000000000, 0)",
            )
            connection.execSQL(
                "INSERT INTO audio_notes (uid, contentUri, durationMs, lastUpdated, created, syncVersion) " +
                    "VALUES ('audio-1', 'voice.m4a', 4000, 1710000000000, 1710000000000, 0)",
            )
        }

        helper.runMigrationsAndValidate(54, listOf(MIGRATION_53_54)).use { connection ->
            listOf("text_notes" to "text-1", "image_notes" to "image-1", "video_notes" to "video-1", "audio_notes" to "audio-1")
                .forEach { (table, uid) ->
                    connection.prepare("SELECT uid, captured_on FROM $table WHERE uid = '$uid'").use { row ->
                        assertTrue(row.step(), "$uid is gone after the migration")
                        assertEquals(uid, row.getText(0))
                        assertTrue(row.isNull(1), "$uid should have no capture device")
                    }
                }
        }
    }
}
```

Move the doubles out of `WatchNoteIngestorTest.kt`: cut the two nested classes `private class RecordingAcknowledger : WatchNoteAcknowledger { … }` and `private class InMemoryWatchAudioStore : WatchAudioStore { … }` (lines 480-551, through the line before the class's final `}`) and create `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchIngestorTestDoubles.kt` with them as top-level `internal` classes:

```kotlin
package app.logdate.client.sync

import app.logdate.client.sync.datalayer.AudioFileDigest
import app.logdate.client.sync.datalayer.copyToDigesting
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Records the acknowledgements the phone sends the watch. */
internal class RecordingAcknowledger : WatchNoteAcknowledger {
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

/** A [WatchAudioStore] in memory, with switches for a failed or a delayed write. */
internal class InMemoryWatchAudioStore : WatchAudioStore {
    private val audio = mutableMapOf<Uuid, ByteArray>()
    private val metadata = mutableMapOf<Uuid, Map<String, String>>()
    private val deleted = mutableSetOf<Uuid>()
    var failNextWrite = false
    var writeGate: CompletableDeferred<Unit>? = null
    var writes = 0

    fun audioText(noteId: Uuid): String? = audio[noteId]?.decodeToString()

    override fun audioPath(noteId: Uuid): String = "/phone/audio_notes/wear_$noteId.m4a"

    override fun hasAudio(noteId: Uuid): Boolean = noteId in audio

    override suspend fun writeAudio(
        noteId: Uuid,
        source: InputStream,
    ): Boolean {
        writeGate?.await()
        writes++
        if (failNextWrite) {
            failNextWrite = false
            return false
        }
        audio[noteId] = source.readBytes()
        return true
    }

    override suspend fun storedDigest(noteId: Uuid): AudioFileDigest? =
        audio[noteId]?.let { bytes -> ByteArrayInputStream(bytes).copyToDigesting(ByteArrayOutputStream()) }

    override suspend fun stashMetadata(
        noteId: Uuid,
        data: Map<String, String>,
    ) {
        metadata[noteId] = data
    }

    override suspend fun stashedMetadata(noteId: Uuid): Map<String, String>? = metadata[noteId]

    override suspend fun clearMetadata(noteId: Uuid) {
        metadata.remove(noteId)
    }

    override suspend fun discard(noteId: Uuid) {
        audio.remove(noteId)
        metadata.remove(noteId)
    }

    override suspend fun markDeleted(noteId: Uuid) {
        deleted += noteId
    }

    override suspend fun isDeleted(noteId: Uuid): Boolean = noteId in deleted
}
```

In `WatchNoteIngestorTest.kt`, change the two expectations that name the decoded note:

```kotlin
            coVerify(exactly = 1) { notesRepository.create(note.withCapturedOn(CaptureDevice.WATCH)) }
            assertEquals(listOf(note.withCapturedOn(CaptureDevice.WATCH)), notified)
```

(in `text note from the watch is stored and queued for backup`) and

```kotlin
            coVerify(exactly = 1) { notesRepository.createFromSync(note.withCapturedOn(CaptureDevice.WATCH)) }
```

(in `redelivered text note updates the stored copy without queuing another backup`), and add the imports `app.logdate.client.repository.journals.CaptureDevice` and `app.logdate.client.repository.journals.withCapturedOn`.

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteProvenanceTest.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.datalayer.NoteDataMapper
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Notes that reach the phone from the watch are recorded as captured on the watch. Watch builds that
 * predate the capture device send none, so the phone records it; a device the watch named stays.
 */
class WatchNoteProvenanceTest {
    private val mapper = NoteDataMapper()
    private val store = InMemoryWatchAudioStore()
    private val stored = mutableMapOf<Uuid, JournalNote>()
    private val notesRepository =
        mockk<SyncableJournalNotesRepository>(relaxed = true).also { repository ->
            coEvery { repository.getNoteById(any()) } answers { stored[firstArg()] }
            coEvery { repository.create(any<JournalNote>()) } answers {
                val note = firstArg<JournalNote>()
                stored[note.uid] = note
                note.uid
            }
        }
    private val ingestor =
        WatchNoteIngestor(
            notesRepository = notesRepository,
            audioStore = store,
            acknowledger = RecordingAcknowledger(),
            notifier = {},
            noteDataMapper = mapper,
        )
    private val at = Instant.fromEpochMilliseconds(1_710_000_000_000)

    @Test
    fun `a text note from an older watch is stored as captured on the watch`() =
        runTest {
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "From the watch")

            ingestor.onNoteMetadata(mapper.toDataMap(note))

            assertEquals(CaptureDevice.WATCH, stored.getValue(note.uid).capturedOn)
        }

    @Test
    fun `an audio note from an older watch is stored as captured on the watch`() =
        runTest {
            val note = JournalNote.Audio(mediaRef = "/watch/voice.m4a", durationMs = 4_000, creationTimestamp = at, lastUpdated = at)

            ingestor.onNoteMetadata(mapper.toDataMap(note))
            ingestor.onAudioBytes(note.uid, ByteArrayInputStream("m4a".encodeToByteArray()))

            assertEquals(CaptureDevice.WATCH, stored.getValue(note.uid).capturedOn)
        }

    @Test
    fun `a device the watch named stays as it is`() =
        runTest {
            val note =
                JournalNote.Text(
                    creationTimestamp = at,
                    lastUpdated = at,
                    content = "Written on the phone",
                    capturedOn = CaptureDevice.PHONE,
                )

            ingestor.onNoteMetadata(mapper.toDataMap(note))

            assertEquals(CaptureDevice.PHONE, stored.getValue(note.uid).capturedOn)
        }
}
```

- [ ] **Step 2: Add the API surface with inert bodies, the column and an empty migration**

Create `CaptureDevice.kt` exactly as in Step 4 except: `fromWireName` returns `null`, and `withCapturedOn` returns `this`. Add the abstract property and the four `capturedOn` parameters to `JournalNote` as in Step 4 (declarations, no behavior). In `client/database`, add `abstract val capturedOn: String?` to `GenericNoteData` and the `captured_on` column to the four entities as in Step 4, bump `version = 53` to `version = 54` in `LogDateDatabase.kt`, and create `MIGRATION_53_54.kt` with an empty `migrate` body. Regenerate the schema:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:database:kspKotlinJvm --console=plain
```
Expected: BUILD SUCCESSFUL and a new `client/database/schemas/app.logdate.client.database.LogDateDatabase/54.json` in which the `createSql` of `text_notes`, `image_notes`, `video_notes` and `audio_notes` ends its column list with `` `captured_on` TEXT `` (after `time_zone_id`, or after `presentation` for `image_notes`).

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:repository:jvmTest --tests 'app.logdate.client.repository.journals.CaptureDeviceTest' :client:database:jvmTest --tests 'app.logdate.client.database.CapturedOnMigrationJvmTest' :client:data:desktopTest --tests 'app.logdate.client.data.notes.CapturedOnMappingTest' :client:sync:desktopTest --tests 'app.logdate.client.sync.conflict.JournalNoteCaptureDeviceConflictTest' --tests 'app.logdate.client.sync.datalayer.NoteDataMapperCapturedOnTest' --tests 'app.logdate.client.sync.CreateReplayReconciliationTest' :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.WatchNoteProvenanceTest' --tests 'app.logdate.client.sync.WatchNoteIngestorTest' --console=plain
```
Expected: FAIL. `CaptureDeviceTest`: `a wire name reads back as its device` and `recording the capture device changes nothing else`. `CapturedOnMigrationJvmTest`: `Migration didn't properly handle: text_notes` (the column is missing). `CapturedOnMappingTest`: `database mapping keeps the capture device of every note type` (`capturedOn` is not mapped). `JournalNoteCaptureDeviceConflictTest`: all five tests (the resolver returns a copy without the device, or a different resolution). `NoteDataMapperCapturedOnTest`: `a note captured on the watch keeps its device through a message` (the inert `fromWireName` reads every name as unknown). `CreateReplayReconciliationTest`: at least the `CREATE` and `UPDATE` cases, because `uploadedFields` still compares the device, so the server's copy no longer counts as the note that was uploaded. `WatchNoteProvenanceTest`: the two "older watch" tests. `WatchNoteIngestorTest` passes (with the inert `withCapturedOn` its changed expectations equal the decoded note).

- [ ] **Step 4: Implement the capture device end to end on the device**

`client/repository/src/commonMain/kotlin/app/logdate/client/repository/journals/CaptureDevice.kt`:

```kotlin
package app.logdate.client.repository.journals

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The kind of device a moment was captured on.
 *
 * [wireName] is what every format stores: the database column, the encrypted sync field, archives and
 * the watch's note messages. It never changes once shipped. A new kind is a new value, and an app
 * version that does not know it reads it as unknown.
 */
enum class CaptureDevice(
    val wireName: String,
) {
    PHONE("phone"),
    WATCH("watch"),
    ;

    companion object {
        /** The device named [wireName], or null for null or a name a newer app version wrote. */
        fun fromWireName(wireName: String?): CaptureDevice? = entries.firstOrNull { it.wireName == wireName }
    }
}

/**
 * Writes a [CaptureDevice] as its [CaptureDevice.wireName] and reads a name this version does not know
 * as null, so a note from a newer app version still decodes.
 */
@OptIn(ExperimentalSerializationApi::class)
object LenientCaptureDeviceSerializer : KSerializer<CaptureDevice?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.logdate.client.repository.journals.CaptureDevice", PrimitiveKind.STRING).nullable

    override fun serialize(
        encoder: Encoder,
        value: CaptureDevice?,
    ) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        encoder.encodeString(value.wireName)
    }

    override fun deserialize(decoder: Decoder): CaptureDevice? {
        if (!decoder.decodeNotNullMark()) return decoder.decodeNull()
        return CaptureDevice.fromWireName(decoder.decodeString())
    }
}

/** Whether this note was captured on a watch. */
val JournalNote.isCapturedOnWatch: Boolean
    get() = capturedOn == CaptureDevice.WATCH

/** A copy of this note with [capturedOn] as its capture device; everything else is unchanged. */
fun JournalNote.withCapturedOn(capturedOn: CaptureDevice?): JournalNote =
    when (this) {
        is JournalNote.Text -> copy(capturedOn = capturedOn)
        is JournalNote.Image -> copy(capturedOn = capturedOn)
        is JournalNote.Video -> copy(capturedOn = capturedOn)
        is JournalNote.Audio -> copy(capturedOn = capturedOn)
    }
```

In `client/repository/src/commonMain/kotlin/app/logdate/client/repository/journals/JournalNotesRepository.kt`, after the `abstract val timeZoneId: String?` declaration and its KDoc, add:

```kotlin

    /**
     * The device the note was captured on, or null when it was not recorded: notes from before this
     * was tracked, and notes written on this device, which does not record itself yet. Set once where
     * the note is captured and never edited; no copy of the note may erase it.
     */
    abstract val capturedOn: CaptureDevice?
```

and add this as the last constructor parameter of each of `Text`, `Image`, `Video` and `Audio` (after `timeZoneId` for the first three, after `transcript` for `Audio`):

```kotlin
        @Serializable(with = LenientCaptureDeviceSerializer::class)
        override val capturedOn: CaptureDevice? = null,
```

In `client/database/src/commonMain/kotlin/app/logdate/client/database/entities/GenericNoteData.kt`, after `abstract val timeZoneId: String?`, add:

```kotlin

    /**
     * The wire name of the device the note was captured on (`phone` or `watch`), or null when it was
     * not recorded.
     */
    abstract val capturedOn: String?
```

In each of `TextNoteEntity.kt`, `VideoNoteEntity.kt` and `AudioNoteEntity.kt`, after the `timeZoneId` property, and in `ImageNoteEntity.kt` after the `presentation` property, add:

```kotlin
    @ColumnInfo(name = "captured_on")
    override val capturedOn: String? = null,
```

Replace `client/database/src/commonMain/kotlin/app/logdate/client/database/migrations/MIGRATION_53_54.kt` with:

```kotlin
@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds the device each note was captured on to every note table. Existing notes stay null: the device
 * was never recorded, and a note that came from the watch before this version cannot be told apart
 * from one written on the phone.
 */
val MIGRATION_53_54 =
    object : Migration(53, 54) {
        override fun migrate(connection: SQLiteConnection) {
            listOf("text_notes", "image_notes", "video_notes", "audio_notes").forEach { table ->
                connection.execSQL("ALTER TABLE $table ADD COLUMN captured_on TEXT")
            }
        }
    }
```

In `client/database/src/commonMain/kotlin/app/logdate/client/database/LogDateDatabase.kt`, add `import app.logdate.client.database.migrations.MIGRATION_53_54` after the `MIGRATION_52_53` import, and `MIGRATION_53_54,` after `MIGRATION_52_53,` in the migration list (line 440). Run `:client:database:kspKotlinJvm` again and confirm `54.json` is unchanged from Step 2.

In `client/data/src/commonMain/kotlin/app/logdate/client/data/notes/NoteObjectMappers.kt`, add `import app.logdate.client.repository.journals.CaptureDevice`; in each of the four `toModel` functions add `capturedOn = CaptureDevice.fromWireName(capturedOn),` after `timeZoneId = timeZoneId,`; and in each of the four `toEntity` functions add `capturedOn = capturedOn?.wireName,` after `timeZoneId = timeZoneId,`.

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/conflict/JournalNoteConflictResolver.kt`, replace the class KDoc, `resolve`, `withZoneIfMissing` and `withZoneFallback` (lines 10-38) with:

```kotlin
/**
 * Conflict resolver for notes that attempts content-safe merges before deferring to manual review.
 *
 * A note's capture time zone and capture device are set once, where the note was captured, and never
 * edited. Whichever copy wins, the resolved note records each of them from either copy: a copy from
 * an app version or server that predates a field must not erase it.
 */
class JournalNoteConflictResolver : ConflictResolver<JournalNote> {
    override fun resolve(
        local: JournalNote,
        remote: JournalNote,
        localTimestamp: Instant,
        remoteTimestamp: Instant,
    ): ConflictResolution<JournalNote> {
        val either =
            CaptureFacts(
                timeZoneId = local.timeZoneId ?: remote.timeZoneId,
                capturedOn = local.capturedOn ?: remote.capturedOn,
            )
        val localFacts = CaptureFacts(timeZoneId = local.timeZoneId, capturedOn = local.capturedOn)
        val resolution = resolveContent(local, remote.withCaptureFactsIfMissing(localFacts), localTimestamp, remoteTimestamp)
        return resolution.withCaptureFactsFallback(either)
    }

    /** What a note records once, where it is captured. */
    private data class CaptureFacts(
        val timeZoneId: String?,
        val capturedOn: CaptureDevice?,
    )

    private fun JournalNote.withCaptureFactsIfMissing(facts: CaptureFacts): JournalNote {
        val withZone = if (timeZoneId == null && facts.timeZoneId != null) withTimeZoneId(facts.timeZoneId) else this
        if (withZone.capturedOn != null || facts.capturedOn == null) return withZone
        return withZone.withCapturedOn(facts.capturedOn)
    }

    private fun ConflictResolution<JournalNote>.withCaptureFactsFallback(facts: CaptureFacts): ConflictResolution<JournalNote> =
        when (this) {
            is ConflictResolution.KeepLocal -> ConflictResolution.KeepLocal(value.withCaptureFactsIfMissing(facts))
            is ConflictResolution.KeepRemote -> ConflictResolution.KeepRemote(value.withCaptureFactsIfMissing(facts))
            is ConflictResolution.Merge -> ConflictResolution.Merge(merged.withCaptureFactsIfMissing(facts))
            is ConflictResolution.RequiresManualResolution -> this
        }
```

and add the imports `app.logdate.client.repository.journals.CaptureDevice` and `app.logdate.client.repository.journals.withCapturedOn`.

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloader.kt`, in `uploadedFields`, replace every

```kotlin
                timeZoneId = null,
            )
```

(four occurrences, one per note type) with

```kotlin
                timeZoneId = null,
                capturedOn = null,
            )
```

and change its KDoc to:

```kotlin
/**
 * Compare only the fields the content contract sends, after resolving cached media. The capture time
 * zone and device are left out: a server that predates them returns none, and neither changes after
 * capture.
 */
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt`:

1. Add the imports `app.logdate.client.repository.journals.CaptureDevice` and `app.logdate.client.repository.journals.withCapturedOn`.
2. Append to the class KDoc (after "…does not hold up the others."):

```kotlin
 *
 * A note from a watch build that does not record its capture device is stored as captured on the
 * watch, because everything this path receives was captured there.
```

3. In `onNoteMetadata`, change `val note = noteDataMapper.fromDataMap(data)` to `val note = noteDataMapper.fromDataMap(data).capturedOnWatchUnlessNamed()`.
4. In `storeIfComplete`, change `val note = watchNote.copy(mediaRef = audioStore.audioPath(noteId))` to

```kotlin
        val note = watchNote.copy(mediaRef = audioStore.audioPath(noteId), capturedOn = watchNote.capturedOn ?: CaptureDevice.WATCH)
```

5. Add this private function at the end of the class:

```kotlin
    private fun JournalNote.capturedOnWatchUnlessNamed(): JournalNote =
        if (capturedOn != null) this else withCapturedOn(CaptureDevice.WATCH)
```

- [ ] **Step 5: Run the tests again**

Run the Step 3 command again.
Expected: PASS (4 `CaptureDeviceTest`, 1 `CapturedOnMigrationJvmTest`, 3 `CapturedOnMappingTest`, 5 `JournalNoteCaptureDeviceConflictTest`, 4 `NoteDataMapperCapturedOnTest`, 3 `CreateReplayReconciliationTest`, 3 `WatchNoteProvenanceTest`, every `WatchNoteIngestorTest`).

- [ ] **Step 6: Run the touched modules' suites, the iOS compile and the checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:repository:jvmTest :client:database:jvmTest :client:data:desktopTest :client:sync:desktopTest :app:android-main:testDebugUnitTest :client:repository:compileKotlinIosSimulatorArm64 :client:repository:compileTestKotlinIosSimulatorArm64 :client:database:compileKotlinIosSimulatorArm64 :client:data:compileKotlinIosSimulatorArm64 :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:repository:ktlintCheck :client:database:ktlintCheck :client:data:ktlintCheck :client:sync:ktlintCheck :app:compose-main:ktlintCheck :app:android-main:ktlintCheck :client:repository:detekt :client:database:detekt :client:data:detekt :client:sync:detekt :app:wear:compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL. `wc -l app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteIngestorTest.kt` is at most 500. (`:app:wear:compileDebugKotlin` confirms the watch, which shares the database and the note model, still builds.)

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task05-captured-on.txt <<'EOF'
chore(database): record which device captured each moment

Moments captured on the watch should be recognizable on the phone.
This adds a capture device to every note, phone or watch, stored in a
new column of each note table (database version 54). Existing notes
have no device: it was never recorded, and a note that arrived from
the watch earlier cannot be told apart.

Notes that reach the phone from a watch build that does not record
its device are stored as captured on the watch. The device travels in
the watch's note messages as "phone" or "watch", and a name a newer
version introduces reads as unknown instead of breaking the message.
Like the capture time zone, the device is written once: a synced copy
from an app or server that lacks it never erases it, and comparing an
uploaded note with the server's copy ignores it. Nothing displays the
device yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/repository/src/commonMain/kotlin/app/logdate/client/repository/journals/CaptureDevice.kt client/repository/src/commonMain/kotlin/app/logdate/client/repository/journals/JournalNotesRepository.kt client/repository/src/commonTest/kotlin/app/logdate/client/repository/journals/CaptureDeviceTest.kt client/database/src/commonMain/kotlin/app/logdate/client/database/entities/GenericNoteData.kt client/database/src/commonMain/kotlin/app/logdate/client/database/entities/TextNoteEntity.kt client/database/src/commonMain/kotlin/app/logdate/client/database/entities/ImageNoteEntity.kt client/database/src/commonMain/kotlin/app/logdate/client/database/entities/VideoNoteEntity.kt client/database/src/commonMain/kotlin/app/logdate/client/database/entities/AudioNoteEntity.kt client/database/src/commonMain/kotlin/app/logdate/client/database/migrations/MIGRATION_53_54.kt client/database/src/commonMain/kotlin/app/logdate/client/database/LogDateDatabase.kt client/database/schemas/app.logdate.client.database.LogDateDatabase/54.json client/database/src/jvmTest/kotlin/app/logdate/client/database/CapturedOnMigrationJvmTest.kt client/data/src/commonMain/kotlin/app/logdate/client/data/notes/NoteObjectMappers.kt client/data/src/commonTest/kotlin/app/logdate/client/data/notes/CapturedOnMappingTest.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/conflict/JournalNoteConflictResolver.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloader.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/conflict/JournalNoteCaptureDeviceConflictTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/NoteDataMapperCapturedOnTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/CreateReplayReconciliationTest.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt app/android-main/src/test/kotlin/app/logdate/client/sync/WatchIngestorTestDoubles.kt app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteIngestorTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/WatchNoteProvenanceTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task05-captured-on.txt
```

---
### Task 6: Sync which device captured a moment, encrypted (D1, sync)

**Files:**
- Modify: `shared/model/src/commonMain/kotlin/app/logdate/shared/model/sync/SyncModels.kt:48-147` (`ContentUploadRequest`, `ContentUpdateRequest`, `ContentChange`)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt:144-368`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/CapturedOnSyncTest.kt`
- Modify: `server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionsRepository.kt:28-31` (`LogDateEntry`)
- Modify: `server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionRecords.kt:173-224` (`toRepoJson`, `toEntryRepoJson`, `toLogDateEntry`)
- Modify: `server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt:90, 200`
- Modify: `server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt:410-413` (`toContentChange`)
- Modify: `server/src/main/kotlin/app/logdate/server/openapi/schemadocs/SyncSchemaDocs.kt:80-162`
- Test: `integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/CapturedOnClientServerE2ETest.kt`

**Interfaces:**
- Consumes: `CaptureDevice`, `JournalNote.capturedOn` (Task 5); `SyncPayloadCipher.encryptString/decryptString`; `FakeCloudApiClient.uploadContentCalls`, `updateContentCalls`, `getContentChangesResponse`, `TestCryptoManager`, `InMemorySecureStorage` (`client/sync` `commonTest`); `withServerClientHarness`, `createAccountWithSyntheticPasskey` (`integration/server-client-e2e`).
- Produces: `capturedOn: String? = null` on `ContentUploadRequest`, `ContentUpdateRequest` and `ContentChange` (an opaque, encrypted value; `"phone"` or `"watch"` before encryption, under field id `sync:note:<id>:capturedOn`); `LogDateEntry.capturedOn: String? = null`; the entry record JSON key `capturedOn`. An update that omits it leaves the stored value.

- [ ] **Step 1: Add the wire field (no behavior)**

In `shared/model/src/commonMain/kotlin/app/logdate/shared/model/sync/SyncModels.kt`, add as the last property of `ContentUploadRequest`, `ContentUpdateRequest` and `ContentChange`:

```kotlin
    /**
     * The device the entry was captured on, encrypted by the apps, or null when it was not recorded.
     * The server stores and returns it without reading it; an update that omits it leaves it as it is.
     */
    val capturedOn: String? = null,
```

- [ ] **Step 2: Write the failing tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/CapturedOnSyncTest.kt`:

```kotlin
package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.test.FakeCloudApiClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * The capture device travels to the server encrypted, like a caption: the server must not learn which
 * entries came from the watch.
 */
class CapturedOnSyncTest {
    private val api = FakeCloudApiClient()

    private suspend fun cipher(): SyncPayloadCipher {
        val manager = TestCryptoManager()
        val identity = IdentityKeyManager(InMemorySecureStorage(), manager)
        identity.setupNewIdentity()
        return SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(manager), manager), identity, manager)
    }

    @Test
    fun `the capture device is encrypted before create and update`() =
        runTest {
            val cipher = cipher()
            val source = DefaultCloudContentDataSource(api, cipher)
            val id = Uuid.random()
            val now = Clock.System.now()
            val note =
                JournalNote.Audio(
                    uid = id,
                    mediaRef = "https://server/media/audio",
                    creationTimestamp = now,
                    lastUpdated = now,
                    capturedOn = CaptureDevice.WATCH,
                )

            assertTrue(source.uploadNote("token", note).isSuccess)
            assertTrue(source.updateNote("token", note).isSuccess)

            val payloads = api.uploadContentCalls.map { it.second.capturedOn } + api.updateContentCalls.map { it.third.capturedOn }
            assertEquals(2, payloads.size)
            payloads.forEach { payload ->
                assertTrue(payload.orEmpty().startsWith("LDSE2:"))
                assertFalse(payload.orEmpty().contains("watch"))
                assertEquals("watch", cipher.decryptString("sync:note:$id:capturedOn", payload.orEmpty()))
            }
        }

    @Test
    fun `a note without a device sends none`() =
        runTest {
            val source = DefaultCloudContentDataSource(api, cipher())
            val now = Clock.System.now()

            source.uploadNote("token", JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Typed"))

            assertNull(api.uploadContentCalls.single().second.capturedOn)
        }

    @Test
    fun `a downloaded device is decrypted`() =
        runTest {
            val cipher = cipher()
            val id = Uuid.random()
            api.getContentChangesResponse = changes(id, cipher.encryptString("sync:note:$id:capturedOn", "watch"))

            val note =
                DefaultCloudContentDataSource(api, cipher)
                    .getContentChanges("token", Clock.System.now())
                    .getOrThrow()
                    .changes
                    .single()

            assertEquals(CaptureDevice.WATCH, note.capturedOn)
        }

    @Test
    fun `a device a newer version wrote downloads as unknown`() =
        runTest {
            val cipher = cipher()
            val id = Uuid.random()
            api.getContentChangesResponse = changes(id, cipher.encryptString("sync:note:$id:capturedOn", "tablet"))

            val result = DefaultCloudContentDataSource(api, cipher).getContentChanges("token", Clock.System.now()).getOrThrow()

            assertEquals(listOf(id), result.changes.map { it.uid })
            assertNull(result.changes.single().capturedOn)
        }

    @Test
    fun `an entry from a server without the field downloads with no device`() =
        runTest {
            val id = Uuid.random()
            api.getContentChangesResponse = changes(id, capturedOn = null)

            val note =
                DefaultCloudContentDataSource(api, cipher())
                    .getContentChanges("token", Clock.System.now())
                    .getOrThrow()
                    .changes
                    .single()

            assertNull(note.capturedOn)
        }

    private fun changes(
        id: Uuid,
        capturedOn: String?,
    ) = Result.success(
        ContentChangesResponse(
            changes =
                listOf(
                    ContentChange(
                        id = id.toString(),
                        type = "AUDIO",
                        mediaUri = "https://server/media/audio",
                        createdAt = 1,
                        lastUpdated = 2,
                        serverVersion = 3,
                        capturedOn = capturedOn,
                    ),
                ),
            deletions = emptyList(),
            lastTimestamp = 3,
        ),
    )
}
```

Create `integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/CapturedOnClientServerE2ETest.kt`:

```kotlin
package app.logdate.integration.e2e.journeys

import app.logdate.client.sync.cloud.ContentUpdateRequest
import app.logdate.client.sync.cloud.ContentUploadRequest
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The server keeps the encrypted capture device it was given, and an update from an app version that
 * does not send it leaves it in place.
 */
class CapturedOnClientServerE2ETest {
    @Test
    fun `the capture device survives upload and an update that does not send it`() =
        runTest {
            withServerClientHarness {
                val token =
                    apiClient
                        .createAccountWithSyntheticPasskey("device_${Random.nextInt(100000, 999999)}")
                        .data.tokens.accessToken
                apiClient
                    .uploadContent(
                        token,
                        ContentUploadRequest(
                            id = "watch-capture",
                            type = "AUDIO",
                            content = null,
                            mediaUri = "voice.m4a",
                            durationMs = 4_000,
                            createdAt = 1000,
                            lastUpdated = 1000,
                            capturedOn = SEALED_DEVICE,
                        ),
                    ).getOrThrow()
                assertEquals(SEALED_DEVICE, apiClient.getContentChanges(token, 0).getOrThrow().changes.single().capturedOn)

                apiClient
                    .updateContent(token, "watch-capture", ContentUpdateRequest(lastUpdated = 2000, caption = "At the station"))
                    .getOrThrow()

                val updated =
                    apiClient
                        .getContentChanges(token, 0)
                        .getOrThrow()
                        .changes
                        .single()
                assertEquals(SEALED_DEVICE, updated.capturedOn)
                assertEquals("At the station", updated.caption)
            }
        }

    private companion object {
        /** The server never reads the value, so any opaque string stands in for the app's ciphertext. */
        const val SEALED_DEVICE = "LDSE2:sealed-device"
    }
}
```

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.cloud.CapturedOnSyncTest' :server:test --tests 'app.logdate.server.openapi.SchemaDocumentationTest' :integration:server-client-e2e:test --tests 'app.logdate.integration.e2e.journeys.CapturedOnClientServerE2ETest' --continue --console=plain
```
Expected: FAIL. `CapturedOnSyncTest > the capture device is encrypted before create and update` (`payload` is null) and `a downloaded device is decrypted` (`but was:<null>`); the other three pass already. `SchemaDocumentationTest > schemas reachable from documented operations are fully described` lists `ContentUploadRequest.capturedOn`, `ContentUpdateRequest.capturedOn` and `ContentChange.capturedOn`. `CapturedOnClientServerE2ETest` fails on the first `assertEquals` (`but was:<null>`): the server drops the field.

- [ ] **Step 4: Encrypt the device on the client**

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt`:

1. Add the import `app.logdate.client.repository.journals.CaptureDevice`.
2. In both `toUploadRequest()` and `toUpdateRequest()`, after the `transcript = …` argument, add:

```kotlin
            capturedOn = capturedOn?.let { encryptNoteCapturedOn(uid, it) },
```

3. In `ContentChange.toJournalNote()`, add `capturedOn = decryptNoteCapturedOn(uid, capturedOn),` after `syncVersion = serverVersion,` in each of the `TEXT`, `IMAGE`, `VIDEO` and `AUDIO` branches.
4. Add these functions after `noteLocationFieldId`:

```kotlin
    private suspend fun encryptNoteCapturedOn(
        noteId: Uuid,
        device: CaptureDevice,
    ): String = syncPayloadCipher?.encryptString(noteCapturedOnFieldId(noteId), device.wireName) ?: device.wireName

    /**
     * Reads the capture device. A device a newer app version names reads as unknown, so the entry still
     * downloads; a payload that does not decrypt fails the entry like any other encrypted field.
     */
    private suspend fun decryptNoteCapturedOn(
        noteId: Uuid,
        payload: String?,
    ): CaptureDevice? {
        if (payload.isNullOrBlank()) return null
        val plaintext = syncPayloadCipher?.decryptString(noteCapturedOnFieldId(noteId), payload) ?: payload
        return CaptureDevice.fromWireName(plaintext)
    }

    private fun noteCapturedOnFieldId(noteId: Uuid): String = "sync:note:$noteId:capturedOn"
```

- [ ] **Step 5: Store the device on the server as an opaque value**

In `server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionsRepository.kt`, add to `LogDateEntry` after `transcript`:

```kotlin
    /** Opaque encrypted capture device; the server stores and returns it without reading it. */
    val capturedOn: String? = null,
```

In `server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionRecords.kt`, in both `toRepoJson()` and `toEntryRepoJson()`, after the `if (transcript != null) { put("transcript", transcript) }` block, add:

```kotlin
        if (capturedOn != null) {
            put("capturedOn", capturedOn)
        }
```

and in `toLogDateEntry`, after `transcript = nullableStringValue("transcript"),`, add `capturedOn = nullableStringValue("capturedOn"),`.

In `server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt`, in the `PUT /contents/{contentId}` entry (line 90), after `transcript = req.transcript,` add `capturedOn = req.capturedOn,`; in the update's entry (line 200), after `transcript = req.transcript,` add `capturedOn = req.capturedOn ?: existing?.capturedOn,`.

In `server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt`, in `LogDateEntry.toContentChange()`, after `transcript = transcript,` add `capturedOn = capturedOn,`.

In `server/src/main/kotlin/app/logdate/server/openapi/schemadocs/SyncSchemaDocs.kt`, add a `capturedOn` entry as the last entry of three maps:

- in `"ContentUploadRequest"`, after the two-line `"transcript" to …` entry that precedes `"ContentUploadResponse" to`:

```kotlin
                        "capturedOn" to "The device that captured the entry, or `null` when not recorded. $ENCRYPTED_BY_APPS",
```

- in `"ContentUpdateRequest"`, after `"transcript" to "Encrypted audio transcript; omit to preserve it while media is unchanged. $ENCRYPTED_BY_APPS",`:

```kotlin
                        "capturedOn" to "The device that captured the entry; omit it to leave it unchanged. $ENCRYPTED_BY_APPS",
```

- in `"ContentChange"`, after the two-line `"transcript" to …` entry that precedes `"ContentDeletion" to`:

```kotlin
                        "capturedOn" to "The device that captured the entry, or `null` when not recorded. $ENCRYPTED_BY_APPS",
```

- [ ] **Step 6: Run the tests again**

Run the Step 3 command again.
Expected: PASS (5 `CapturedOnSyncTest`, `SchemaDocumentationTest`, 1 `CapturedOnClientServerE2ETest`).

- [ ] **Step 7: Run the suites that cover these files, the iOS compile and the checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :server:test --tests 'app.logdate.server.routes.sync.*' --tests 'app.logdate.server.openapi.*' :integration:server-client-e2e:test --tests 'app.logdate.integration.e2e.journeys.PhotoPresentationClientServerE2ETest' --tests 'app.logdate.integration.e2e.journeys.CapturedOnClientServerE2ETest' :shared:model:compileKotlinIosSimulatorArm64 :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :shared:model:ktlintCheck :client:sync:ktlintCheck :server:ktlintCheck :integration:server-client-e2e:ktlintCheck :client:sync:detekt :server:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task06-captured-on-sync.txt <<'EOF'
chore(sync): sync which device captured a moment, encrypted

The capture device now syncs with each entry, so a moment recorded on
the watch is recognizable on every device signed in to the account.
It is encrypted on the device like a caption or a place, so the
server never learns which entries came from a watch; the server keeps
the value as it receives it and returns it unchanged.

An update from an app version that does not send the device leaves
the stored one in place, and a device a newer version names reads as
unknown instead of failing the entry. Older servers ignore the field,
so nothing changes until the server is updated.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 9: Commit**

```bash
git restore --staged . && git add shared/model/src/commonMain/kotlin/app/logdate/shared/model/sync/SyncModels.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/CapturedOnSyncTest.kt server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionsRepository.kt server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionRecords.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt server/src/main/kotlin/app/logdate/server/openapi/schemadocs/SyncSchemaDocs.kt integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/CapturedOnClientServerE2ETest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task06-captured-on-sync.txt
```

---
### Task 7: Include which device captured a moment in archives (D1, export and restore)

**Files:**
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/ArchiveData.kt:50` (`ArchiveNote`)
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/ArchiveRecords.kt:113` (`toArchiveNote`)
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/ArchiveSchemas.kt:159` (notes schema)
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportModels.kt:49` (`ExportNote`)
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportUserDataUseCase.kt:184, 196, 209, 220` (1.x export, one line per note type)
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/restore/V2RestoreBundle.kt:164`
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/restore/RestoreUserDataUseCase.kt:498, 510, 521, 534`
- Modify: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/archive/support/ArchiveSamples.kt:66` (the maximal note sample)
- Modify: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/V2ExportImportRoundTripTest.kt`
- Modify: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/ExportImportRoundTripTest.kt:198-208, 558-568`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/CapturedOnArchiveCompatibilityTest.kt`
- Test (unchanged, run): `client/domain/src/jvmTest/kotlin/app/logdate/client/domain/export/archive/ArchiveSchemaValidationTest.kt`

**Interfaces:**
- Consumes: `CaptureDevice`, `JournalNote.capturedOn` (Task 5); `ArchiveExportFixture`, `InMemoryArchiveContainer`, the `RoundTrip*` repositories, `RestoreTestOwnerProvider`, `RestoreTestDeviceIdProvider` (all `commonTest`); `RestoreArchiveReader.read(entryNames, readText)`, `RestoreUserDataUseCase.restore(archive: RestoreArchiveBundle, …)`.
- Produces: `ArchiveNote.capturedOn: String? = null` and `ExportNote.capturedOn: String? = null`, written as `"phone"` or `"watch"` and omitted when unknown; the notes schema lists `capturedOn` as a non-empty string. The archive's schema version stays `2.0`, as it did when `photoPresentation` was added: the field is optional and readers ignore keys they do not know.

- [ ] **Step 1: Add the archive fields (no behavior)**

In `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/ArchiveData.kt`, in `ArchiveNote`, after `val photoPresentation: String? = null,`, add:

```kotlin
    /** The kind of device the note was captured on, `phone` or `watch`. Absent when it was not recorded. */
    val capturedOn: String? = null,
```

In `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportModels.kt`, in `ExportNote`, after `val photoPresentation: String? = null,`, add `val capturedOn: String? = null,`.

- [ ] **Step 2: Write the failing tests**

In `client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/archive/support/ArchiveSamples.kt`, add `capturedOn = "watch",` after `caption = "Sunset",` in the maximal `ArchiveNote(` sample, so the strict schema check sees the field.

In `client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/V2ExportImportRoundTripTest.kt`, add `capturedOn = CaptureDevice.WATCH,` after the `transcript = TranscriptDocument.fromPlainText("Spoken archived audio").copy(revision = 4),` argument of `audioNote.copy(`, add these assertions after the `transcript` assertion:

```kotlin
            assertEquals(CaptureDevice.WATCH, notes.getNoteById(audioNote.uid)?.capturedOn)
            assertNull(notes.getNoteById(textNote.uid)?.capturedOn)
```

and the imports `app.logdate.client.repository.journals.CaptureDevice` and `kotlin.test.assertNull`.

In `client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/ExportImportRoundTripTest.kt`, add `capturedOn = CaptureDevice.WATCH,` after the `transcript = …` argument of the seeded `JournalNote.Audio(uid = noteAudio, …)`, add `assertEquals(CaptureDevice.WATCH, audio.capturedOn)` after the `transcript` assertion in `full round-trip preserves audio note`, and add the import `app.logdate.client.repository.journals.CaptureDevice`.

Create `client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/CapturedOnArchiveCompatibilityTest.kt`:

```kotlin
package app.logdate.client.domain.export

import app.logdate.client.domain.export.archive.ArchiveExportOptions
import app.logdate.client.domain.export.archive.ArchiveLayout
import app.logdate.client.domain.export.archive.support.ArchiveExportFixture
import app.logdate.client.domain.export.archive.support.InMemoryArchiveContainer
import app.logdate.client.domain.export.support.RoundTripJournalContentRepository
import app.logdate.client.domain.export.support.RoundTripJournalNotesRepository
import app.logdate.client.domain.export.support.RoundTripJournalRepository
import app.logdate.client.domain.export.support.RoundTripLocationHistoryRepository
import app.logdate.client.domain.export.support.RoundTripProfileRepository
import app.logdate.client.domain.export.support.RoundTripUserPlacesRepository
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreArchiveReader
import app.logdate.client.domain.restore.RestoreTestDeviceIdProvider
import app.logdate.client.domain.restore.RestoreTestOwnerProvider
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.repository.journals.CaptureDevice
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every note in an archive restores whatever its capture device says: an archive written before the
 * device existed, or by a newer version that names a device this one does not know, restores the note
 * with no device.
 */
class CapturedOnArchiveCompatibilityTest : ArchiveExportFixture() {
    @Test
    fun `a note captured on the watch restores as captured on the watch`() =
        runTest {
            notesRepository.testNotes = listOf(audioNote.copy(capturedOn = CaptureDevice.WATCH))

            val restored = exportAndRestore { it }

            assertEquals(CaptureDevice.WATCH, restored.getNoteById(audioNote.uid)?.capturedOn)
        }

    @Test
    fun `an archive written before capture devices restores its notes with none`() =
        runTest {
            notesRepository.testNotes = listOf(audioNote.copy(capturedOn = CaptureDevice.WATCH))

            val restored = exportAndRestore { note -> JsonObject(note - "capturedOn") }

            assertEquals(audioNote.uid, restored.getNoteById(audioNote.uid)?.uid)
            assertNull(restored.getNoteById(audioNote.uid)?.capturedOn)
        }

    @Test
    fun `a device a newer version names restores as unknown`() =
        runTest {
            notesRepository.testNotes = listOf(audioNote.copy(capturedOn = CaptureDevice.WATCH))

            val restored = exportAndRestore { note -> JsonObject(note + ("capturedOn" to JsonPrimitive("tablet"))) }

            assertEquals(audioNote.uid, restored.getNoteById(audioNote.uid)?.uid)
            assertNull(restored.getNoteById(audioNote.uid)?.capturedOn)
        }

    private suspend fun exportAndRestore(editNote: (JsonObject) -> JsonObject): RoundTripJournalNotesRepository {
        val archive = InMemoryArchiveContainer()
        useCase().export(options = ArchiveExportOptions(), container = archive).toList()
        val bundle =
            RestoreArchiveReader.read(archive.paths) { path ->
                when {
                    !archive.has(path) -> null
                    path.endsWith(ArchiveLayout.NOTES.value) -> editNotes(archive.text(path), editNote)
                    else -> archive.text(path)
                }
            }
        val notes = RoundTripJournalNotesRepository()
        RestoreUserDataUseCase(
            RoundTripJournalRepository(),
            notes,
            RoundTripJournalContentRepository(),
            RoundTripProfileRepository(),
            RoundTripUserPlacesRepository(),
            RoundTripLocationHistoryRepository(),
            RestoreTestOwnerProvider(),
            RestoreTestDeviceIdProvider(),
        ).restore(
            bundle,
            mediaImporter =
                object : MediaImporter {
                    override suspend fun importMedia(exportPath: String): String? =
                        if (archive.has(exportPath)) "file:///restored/${exportPath.substringAfterLast('/')}" else null
                },
        )
        return notes
    }

    private fun editNotes(
        text: String,
        editNote: (JsonObject) -> JsonObject,
    ): String {
        val file = Json.parseToJsonElement(text).jsonObject
        val notes = file.getValue("notes").jsonArray.map { editNote(it.jsonObject) }
        return JsonObject(file + ("notes" to JsonArray(notes))).toString()
    }
}
```

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:domain:jvmTest --tests 'app.logdate.client.domain.export.*' --console=plain
```
Expected: FAIL. `ArchiveSchemaValidationTest > every maximal sample validates against its schema, including a strict copy that rejects unlisted fields` (`schema/notes.schema.json forgot a field the DTO writes`); `V2ExportImportRoundTripTest > fresh repositories restore a newly exported v2 archive` (`expected:<WATCH> but was:<null>`); `ExportImportRoundTripTest > full round-trip preserves audio note`; `CapturedOnArchiveCompatibilityTest > a note captured on the watch restores as captured on the watch`. The two compatibility tests that expect no device pass already, and stay as guards.

- [ ] **Step 4: Write and read the device in both archive versions**

In `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/ArchiveRecords.kt`, in `toArchiveNote`, after `photoPresentation = (this as? JournalNote.Image)?.presentation?.name,`, add:

```kotlin
        capturedOn = capturedOn?.wireName,
```

In `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/ArchiveSchemas.kt`, in the `note` object of `notes()`, after `"photoPresentation" to string(),`, add:

```kotlin
                                "capturedOn" to string(minLength = 1),
```

In `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportUserDataUseCase.kt`, in the `exportNotes` mapping, add `capturedOn = note.capturedOn?.wireName,` after `syncVersion = note.syncVersion,` in each of the four `ExportNote(` constructions. With the Edit tool, replace all occurrences of the 36-space-indented

```kotlin
                                    syncVersion = note.syncVersion,
                                )
```

with

```kotlin
                                    syncVersion = note.syncVersion,
                                    capturedOn = note.capturedOn?.wireName,
                                )
```

(exactly four matches; the `ExportJournalNoteRelation` at line 243 is indented 40 spaces and is not one of them).

In `client/domain/src/commonMain/kotlin/app/logdate/client/domain/restore/V2RestoreBundle.kt`, in `decodeAndMapNotes`, after `timeZone = note.timeZone,`, add `capturedOn = note.capturedOn,`.

In `client/domain/src/commonMain/kotlin/app/logdate/client/domain/restore/RestoreUserDataUseCase.kt`, add the import `app.logdate.client.repository.journals.CaptureDevice`, and in `ExportNote.toJournalNote` replace all four

```kotlin
                    timeZoneId = timeZone,
```

with

```kotlin
                    timeZoneId = timeZone,
                    capturedOn = CaptureDevice.fromWireName(capturedOn),
```

- [ ] **Step 5: Run the tests again**

Run the Step 3 command again.
Expected: PASS, including every existing export, archive and restore test (`ArchiveDtoConventionsTest` confirms `capturedOn` is camelCase, never null in output and not a device reference).

- [ ] **Step 6: Compile for iOS and run the module checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:domain:jvmTest :client:domain:testAndroidHostTest :client:domain:compileKotlinIosSimulatorArm64 :client:domain:compileTestKotlinIosSimulatorArm64 :client:domain:ktlintCheck :client:domain:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task07-captured-on-archives.txt <<'EOF'
chore(export): include which device captured a moment in archives

Exported archives now record, for each note, whether it was captured
on the phone or the watch, and restoring an archive brings that back.
The field is optional in the archive schema and in both archive
versions, so archives made before it restore every note as they did,
with no device. An archive from a newer version that names a device
this version does not know also restores the note, with the device
left unknown.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/ArchiveData.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/ArchiveRecords.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/ArchiveSchemas.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportModels.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportUserDataUseCase.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/restore/V2RestoreBundle.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/restore/RestoreUserDataUseCase.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/archive/support/ArchiveSamples.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/V2ExportImportRoundTripTest.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/ExportImportRoundTripTest.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/export/CapturedOnArchiveCompatibilityTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task07-captured-on-archives.txt
```

---
### Task 8: Mark moments captured on the watch (D1, timeline)

**Files:**
- Create: `client/ui/src/commonMain/kotlin/app/logdate/ui/provenance/CapturedOnWatch.kt`
- Modify: `client/ui/src/commonMain/composeResources/values/strings.xml` (one string)
- Test: `client/ui/src/desktopTest/kotlin/app/logdate/ui/provenance/CapturedOnWatchTest.kt`
- Modify: `client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/HomeTimelineUiState.kt:90-127, 174-213` (`MomentUiState`, `NoteUiState` and its four classes)
- Modify: `client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/TimelineDayUiStateFactory.kt:109-132` (synthesized moment)
- Test: `client/ui/src/commonTest/kotlin/app/logdate/ui/timeline/TimelineDayUiStateFactoryTest.kt` (one test)
- Modify: `client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/newstuff/MomentComposables.kt:135-137, 207-209, 263-265, 364` (`MomentHeader`)
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/timeline/MomentProvenance.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/timeline/MomentProvenanceTest.kt`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/main/HomeTimelineMapping.kt:73-143`
- Test: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/main/HomeTimelineCapturedOnTest.kt`
- Modify: `client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/TimelineViewModel.kt:242-351`
- Modify: `client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/details/NotesListSection.kt` (three timestamp lines)
- Modify: `client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/details/AudioNoteSnippet.kt:34-47`
- Modify: `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailUiState.kt:41-78`
- Create: `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/EntryDisplayMapping.kt` (the mapping moved out of the view model)
- Modify: `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailViewModel.kt:117, 157-196`
- Modify: `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalEntryCardShells.kt:42-130`
- Modify: `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailMediaCards.kt:74, 116, 136, 228`
- Test: `client/feature/journal/src/commonTest/kotlin/app/logdate/feature/journals/ui/detail/EntryDisplayMappingTest.kt`
- Modify: `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/editor/EditorState.kt` (constructor, `copy`, `equals`, `hashCode`)
- Modify: `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/editor/delegate/ContentLoader.kt:17-30`
- Modify: `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/editor/EntryEditorViewModel.kt:1361-1374`
- Modify: `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/EntryEditorContent.kt:271-273`
- Test: `client/feature/editor/src/commonTest/kotlin/app/logdate/feature/editor/ui/editor/delegate/ContentLoaderTest.kt`
- Modify: `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/LogDateNavDisplay.kt:133-135, 193-200`
- Modify: `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt` (scene id, group, `allScenes`)
- Create: `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/ProvenanceScreenshotScenes.kt`
- Create (recorded): `app/compose-main/src/desktopTest/reference/captured-on-watch--{phone,phone-dark,phone-landscape,tablet}.png`

**Interfaces:**
- Consumes: `CaptureDevice`, `JournalNote.isCapturedOnWatch` (Task 5); `LogDateGlyph.WATCH.vector()`; `Moment.sourceNotes`; `sharedScene`, `standardMatrixVariants` (`internal` since Task 4); `FeatureFlagStore.observe(FeatureFlag.WATCH_EXPERIENCE_V2)`.
- Produces:
  - `val LocalWatchProvenanceVisible: ProvidableCompositionLocal<Boolean>` (default `false`); `@Composable fun showsWatchProvenance(capturedOnWatch: Boolean): Boolean`; `@Composable fun CapturedOnWatchGlyph(modifier: Modifier = Modifier)`; `@Composable fun CaptureTimeLabel(text: String, capturedOnWatch: Boolean, modifier: Modifier = Modifier)`; `@Composable fun CapturedOnWatchBanner(modifier: Modifier = Modifier)` (all `app.logdate.ui.provenance`)
  - `NoteUiState.capturedOnWatch: Boolean`, `MomentUiState.capturedOnWatch: Boolean = false`, `EntryDisplayData.capturedOnWatch: Boolean`, `EditorState.capturedOnWatch: Boolean = false`
  - `fun Moment.capturedOnWatch(): Boolean` (`app.logdate.client.domain.timeline`)
  - `internal fun JournalNote.toEntryDisplayData(otherJournals: List<JournalReference> = emptyList()): EntryDisplayData`
  - `data class LoadedEntry(val block: EntryBlockUiState, val capturedOnWatch: Boolean)` and `suspend fun ContentLoader.loadEntry(entryId: Uuid): Result<LoadedEntry>`
  - `SharedScreenshotSceneId.CapturedOnWatch("captured-on-watch")`, `ScreenshotSceneGroup.TIMELINE`, `internal val provenanceScenes`

- [ ] **Step 1: Write the failing tests**

Create `client/ui/src/desktopTest/kotlin/app/logdate/ui/provenance/CapturedOnWatchTest.kt`:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.provenance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

/**
 * A moment captured on the watch carries a small watch glyph next to its time, but only while the
 * redesigned watch experience is on; a moment from the phone looks the same as before.
 */
@OptIn(ExperimentalTestApi::class)
class CapturedOnWatchTest {
    @Test
    fun `the watch glyph follows the time of a moment captured on the watch`() =
        runDesktopComposeUiTest {
            setContent { Provenance(visible = true) { CaptureTimeLabel(text = "3:42 PM", capturedOnWatch = true) } }

            onNodeWithText("3:42 PM").assertExists()
            onNodeWithContentDescription("Captured on your watch").assertExists()
        }

    @Test
    fun `a moment from the phone shows only its time`() =
        runDesktopComposeUiTest {
            setContent { Provenance(visible = true) { CaptureTimeLabel(text = "3:42 PM", capturedOnWatch = false) } }

            onNodeWithText("3:42 PM").assertExists()
            onNodeWithContentDescription("Captured on your watch").assertDoesNotExist()
        }

    @Test
    fun `nothing shows while the redesigned watch experience is off`() =
        runDesktopComposeUiTest {
            setContent { Provenance(visible = false) { CaptureTimeLabel(text = "3:42 PM", capturedOnWatch = true) } }

            onNodeWithContentDescription("Captured on your watch").assertDoesNotExist()
        }

    @Test
    fun `the editor banner names the watch`() =
        runDesktopComposeUiTest {
            setContent { Provenance(visible = true) { CapturedOnWatchBanner() } }

            onNodeWithText("Captured on your watch").assertExists()
        }
}

@Composable
private fun Provenance(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    LogDateTheme(dynamicColor = false, darkTheme = false) {
        CompositionLocalProvider(LocalWatchProvenanceVisible provides visible, content = content)
    }
}
```

In `client/ui/src/commonTest/kotlin/app/logdate/ui/timeline/TimelineDayUiStateFactoryTest.kt`, add the import `kotlin.test.assertFalse` and this test at the end of the class:

```kotlin
    @Test
    fun `a synthesized moment is captured on the watch only when all its notes were`() {
        val at = Instant.fromEpochMilliseconds(1_000)

        fun day(vararg fromWatch: Boolean) =
            createSemanticTimelineDayUiState(
                summary = "",
                date = LocalDate(2025, 1, 17),
                moments = emptyList(),
                notes =
                    fromWatch.mapIndexed { index, captured ->
                        TextNoteUiState(noteId = Uuid.random(), text = "Note $index", timestamp = at, capturedOnWatch = captured)
                    },
            )

        assertTrue(day(true, true).moments.single().capturedOnWatch)
        assertFalse(day(true, false).moments.single().capturedOnWatch)
    }
```

Create `client/domain/src/commonTest/kotlin/app/logdate/client/domain/timeline/MomentProvenanceTest.kt`:

```kotlin
package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** A moment counts as captured on the watch only when every note behind it was. */
class MomentProvenanceTest {
    private val at = Instant.fromEpochMilliseconds(1_000)
    private val fromWatch =
        JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = at, lastUpdated = at, capturedOn = CaptureDevice.WATCH)
    private val fromPhone = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Typed", capturedOn = CaptureDevice.PHONE)
    private val unrecorded = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Written before devices were recorded")

    @Test
    fun `a moment whose notes all came from the watch was captured on the watch`() {
        assertTrue(moment(fromWatch, fromWatch.copy(uid = Uuid.random())).capturedOnWatch())
    }

    @Test
    fun `a moment with a note from elsewhere was not`() {
        assertFalse(moment(fromWatch, fromPhone).capturedOnWatch())
        assertFalse(moment(fromWatch, unrecorded).capturedOnWatch())
    }

    @Test
    fun `a moment without notes was not`() {
        assertFalse(moment().capturedOnWatch())
    }

    private fun moment(vararg notes: JournalNote) =
        Moment(
            id = Uuid.random(),
            label = "",
            estimatedStart = at,
            estimatedEnd = at,
            sourceNotes = notes.toList(),
            textFragments = emptyList(),
            media = emptyList(),
            audio = emptyList(),
            places = emptyList(),
            people = emptyList(),
            inferenceSource = MomentInferenceSource.TIME_OF_DAY_FALLBACK,
        )
}
```

Create `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/main/HomeTimelineCapturedOnTest.kt`:

```kotlin
package app.logdate.feature.core.main

import app.logdate.client.domain.timeline.Moment
import app.logdate.client.domain.timeline.MomentInferenceSource
import app.logdate.client.domain.timeline.TimelineDay
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Home marks the notes and moments captured on the watch, and only those. */
class HomeTimelineCapturedOnTest {
    private val at = Instant.parse("2026-10-10T15:42:00Z")
    private val fromWatch =
        JournalNote.Audio(
            mediaRef = "voice.m4a",
            durationMs = 4_000,
            creationTimestamp = at,
            lastUpdated = at,
            capturedOn = CaptureDevice.WATCH,
        )
    private val fromPhone = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Typed on the phone")

    @Test
    fun `notes captured on the watch are marked in the day detail`() {
        val day = TimelineDay(start = at, end = at, tldr = "", date = LocalDate(2026, 10, 10), entries = listOf(fromWatch, fromPhone))

        val notes = day.toHomeTimelineUiState().notes

        assertTrue(notes.single { it.noteId == fromWatch.uid }.capturedOnWatch)
        assertFalse(notes.single { it.noteId == fromPhone.uid }.capturedOnWatch)
    }

    @Test
    fun `a moment is marked only when every note behind it came from the watch`() {
        val watchOnly = moment(listOf(fromWatch))
        val mixed = moment(listOf(fromWatch, fromPhone))
        val day = TimelineDay(start = at, end = at, tldr = "", date = LocalDate(2026, 10, 10), moments = listOf(watchOnly, mixed))

        val moments = day.toHomeTimelineUiState().moments

        assertTrue(moments.single { it.id == watchOnly.id.toString() }.capturedOnWatch)
        assertFalse(moments.single { it.id == mixed.id.toString() }.capturedOnWatch)
    }

    private fun moment(notes: List<JournalNote>) =
        Moment(
            id = Uuid.random(),
            label = "Morning run",
            estimatedStart = at,
            estimatedEnd = at,
            sourceNotes = notes,
            textFragments = emptyList(),
            media = emptyList(),
            audio = emptyList(),
            places = emptyList(),
            people = emptyList(),
            inferenceSource = MomentInferenceSource.TIME_OF_DAY_FALLBACK,
        )
}
```

Create `client/feature/journal/src/commonTest/kotlin/app/logdate/feature/journals/ui/detail/EntryDisplayMappingTest.kt`:

```kotlin
package app.logdate.feature.journals.ui.detail

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.PhotoPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Tests [toEntryDisplayData], which turns a note into what a journal shows for it. */
class EntryDisplayMappingTest {
    private val at = Instant.fromEpochMilliseconds(1_000)

    @Test
    fun `a note captured on the watch is marked`() {
        val audio = JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = at, lastUpdated = at, capturedOn = CaptureDevice.WATCH)

        assertTrue(audio.toEntryDisplayData().capturedOnWatch)
    }

    @Test
    fun `a note from the phone or from before devices were recorded is not marked`() {
        val fromPhone = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Typed", capturedOn = CaptureDevice.PHONE)
        val unrecorded = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Older")

        assertFalse(fromPhone.toEntryDisplayData().capturedOnWatch)
        assertFalse(unrecorded.toEntryDisplayData().capturedOnWatch)
    }

    @Test
    fun `media references captions and framing reach the entry`() {
        val image =
            JournalNote.Image(
                creationTimestamp = at,
                lastUpdated = at,
                mediaRef = "photo.jpg",
                caption = "Sunset",
                presentation = PhotoPresentation.Framed,
            )

        val entry = image.toEntryDisplayData(listOf(JournalReference(image.uid, "Travel"))) as EntryDisplayData.ImageEntry

        assertEquals("photo.jpg", entry.mediaRef)
        assertEquals("Sunset", entry.caption)
        assertEquals(PhotoPresentation.Framed, entry.presentation)
        assertEquals(listOf(JournalReference(image.uid, "Travel")), entry.otherJournals)
    }
}
```

Create `client/feature/editor/src/commonTest/kotlin/app/logdate/feature/editor/ui/editor/delegate/ContentLoaderTest.kt`:

```kotlin
package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.client.domain.notes.FetchEntryUseCase
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalContentRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalNotesRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [ContentLoader.loadEntry]: the entry's block, and whether the editor should say it came from the watch. */
class ContentLoaderTest {
    private val notes = FakeJournalNotesRepository()
    private val loader =
        ContentLoader(
            fetchEntryUseCase = FetchEntryUseCase(notes),
            getDefaultSelectedJournals = GetDefaultSelectedJournalsUseCase(notes, FakeJournalContentRepository()),
        )
    private val at = Instant.parse("2026-10-10T15:42:00Z")

    @Test
    fun `an entry captured on the watch loads marked`() =
        runTest {
            val note = JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = at, lastUpdated = at, capturedOn = CaptureDevice.WATCH)
            notes.seedExternally(note)

            val loaded = loader.loadEntry(note.uid).getOrThrow()

            assertEquals(note.uid, loaded.block.id)
            assertTrue(loaded.capturedOnWatch)
        }

    @Test
    fun `an entry from the phone loads unmarked`() =
        runTest {
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Typed")
            notes.seedExternally(note)

            assertFalse(loader.loadEntry(note.uid).getOrThrow().capturedOnWatch)
        }

    @Test
    fun `a missing entry fails to load`() =
        runTest {
            assertTrue(loader.loadEntry(Uuid.random()).isFailure)
        }
}
```

- [ ] **Step 2: Add the API surface with inert bodies**

Create `CapturedOnWatch.kt` with the declarations from Step 4, with `LocalWatchProvenanceVisible` as in Step 4 and these bodies: `showsWatchProvenance` returns `false`; `CapturedOnWatchGlyph` and `CapturedOnWatchBanner` are empty; `CaptureTimeLabel` draws only the `Text` from Step 4's first branch. Add the `captured_on_watch` string. Add the `capturedOnWatch` properties to `NoteUiState` (and `= false` to its four classes), `MomentUiState`, `EntryDisplayData` (and its four classes) and `EditorState` as in Step 4. Create `MomentProvenance.kt` with `fun Moment.capturedOnWatch(): Boolean = false`. Create `EntryDisplayMapping.kt` from Step 4 without the four `capturedOnWatch = isCapturedOnWatch,` arguments and switch `JournalDetailViewModel` to it. Change `ContentLoader.loadEntry` to return `LoadedEntry(block = entry.toDomainBlock(), capturedOnWatch = false)` and the view model to read `loaded.block`.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:ui:desktopTest --tests 'app.logdate.ui.provenance.CapturedOnWatchTest' --tests 'app.logdate.ui.timeline.TimelineDayUiStateFactoryTest' :client:domain:jvmTest --tests 'app.logdate.client.domain.timeline.MomentProvenanceTest' :client:feature:core:desktopTest --tests 'app.logdate.feature.core.main.HomeTimelineCapturedOnTest' :client:feature:journal:desktopTest --tests 'app.logdate.feature.journals.ui.detail.EntryDisplayMappingTest' :client:feature:editor:desktopTest --tests 'app.logdate.feature.editor.ui.editor.delegate.ContentLoaderTest' --continue --console=plain
```
Expected: FAIL. `CapturedOnWatchTest`: `the watch glyph follows the time…` and `the editor banner names the watch`. `TimelineDayUiStateFactoryTest`: the new test. `MomentProvenanceTest`: `a moment whose notes all came from the watch…`. `HomeTimelineCapturedOnTest`: both tests. `EntryDisplayMappingTest`: `a note captured on the watch is marked`. `ContentLoaderTest`: `an entry captured on the watch loads marked`. Every other test in these classes passes.

- [ ] **Step 4: Show the watch glyph**

`client/ui/src/commonMain/kotlin/app/logdate/ui/provenance/CapturedOnWatch.kt`:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.provenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.glyphs.LogDateGlyph
import app.logdate.ui.glyphs.vector
import app.logdate.ui.theme.Spacing
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.captured_on_watch
import org.jetbrains.compose.resources.stringResource

/**
 * Whether moments show that they were captured on the watch.
 *
 * Off by default. The navigation root turns it on with the redesigned watch experience in Labs, so
 * leaf composables and screenshot scenes never read feature flags themselves.
 */
val LocalWatchProvenanceVisible: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** Whether to show that something was captured on the watch: it was, and provenance is on. */
@Composable
fun showsWatchProvenance(capturedOnWatch: Boolean): Boolean = capturedOnWatch && LocalWatchProvenanceVisible.current

/** The small watch glyph that marks a moment captured on the watch. */
@Composable
fun CapturedOnWatchGlyph(modifier: Modifier = Modifier) {
    Icon(
        imageVector = LogDateGlyph.WATCH.vector(),
        contentDescription = stringResource(Res.string.captured_on_watch),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(14.dp),
    )
}

/**
 * A moment's time as the line above its card shows it, followed by the watch glyph when the moment was
 * captured on the watch. Without the glyph it is exactly the line the cards showed before.
 */
@Composable
fun CaptureTimeLabel(
    text: String,
    capturedOnWatch: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!showsWatchProvenance(capturedOnWatch)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(bottom = 4.dp),
        )
        return
    }
    Row(
        modifier = modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CapturedOnWatchGlyph()
    }
}

/** The line at the top of the editor when the entry being edited was captured on the watch. */
@Composable
fun CapturedOnWatchBanner(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.padding(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = LogDateGlyph.WATCH.vector(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(Res.string.captured_on_watch),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
```

In `client/ui/src/commonMain/composeResources/values/strings.xml`, before `</resources>`, add:

```xml
    <!-- Provenance -->
    <string name="captured_on_watch">Captured on your watch</string>
```

In `client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/HomeTimelineUiState.kt`:

1. Replace

```kotlin
sealed interface NoteUiState {
    val noteId: Uuid
    val journals: List<JournalBadgeUiState>
}
```

with

```kotlin
sealed interface NoteUiState {
    val noteId: Uuid
    val journals: List<JournalBadgeUiState>

    /** Whether this note was captured on the watch. */
    val capturedOnWatch: Boolean
}
```

2. Replace all four occurrences of

```kotlin
    override val journals: List<JournalBadgeUiState> = emptyList(),
) : NoteUiState
```

with

```kotlin
    override val journals: List<JournalBadgeUiState> = emptyList(),
    override val capturedOnWatch: Boolean = false,
) : NoteUiState
```

3. In `MomentUiState`, after `val isHero: Boolean = false,`, add:

```kotlin
    /**
     * Whether every note behind this moment was captured on the watch.
     */
    val capturedOnWatch: Boolean = false,
```

In `client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/TimelineDayUiStateFactory.kt`, in `synthesizeMoment`, after `isHero = true,`, add:

```kotlin
        capturedOnWatch = sortedNotes.isNotEmpty() && sortedNotes.all { it.capturedOnWatch },
```

In `client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/newstuff/MomentComposables.kt`, add the imports `app.logdate.ui.provenance.CapturedOnWatchGlyph` and `app.logdate.ui.provenance.showsWatchProvenance`, then replace

```kotlin
        if (moment.label.isNotBlank()) {
            MomentLabel(label = moment.label, timeOfDay = null, color = style.accentColor)
        }
```

with `        MomentHeader(moment = moment, timeOfDay = null, color = style.accentColor)`, replace

```kotlin
        if (moment.label.isNotBlank()) {
            MomentLabel(label = moment.label, timeOfDay = moment.timeOfDay, color = style.accentColor)
        }
```

with `        MomentHeader(moment = moment, timeOfDay = moment.timeOfDay, color = style.accentColor)`, replace

```kotlin
            if (moment.label.isNotBlank()) {
                MomentLabel(label = moment.label, timeOfDay = moment.timeOfDay, color = style.accentColor)
            }
```

with `            MomentHeader(moment = moment, timeOfDay = moment.timeOfDay, color = style.accentColor)`, and add this function directly above `private fun MomentLabel(` (and its `@Composable`):

```kotlin
/**
 * A moment's label, followed by the watch glyph when everything in the moment was captured on the
 * watch. `MomentLabel` draws nothing for a blank label, so a moment with neither shows nothing.
 */
@Composable
private fun MomentHeader(
    moment: MomentUiState,
    timeOfDay: DaylightPeriod?,
    color: Color,
) {
    if (!showsWatchProvenance(moment.capturedOnWatch)) {
        MomentLabel(label = moment.label, timeOfDay = timeOfDay, color = color)
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        MomentLabel(label = moment.label, timeOfDay = timeOfDay, color = color)
        CapturedOnWatchGlyph()
    }
}

```

Create `client/domain/src/commonMain/kotlin/app/logdate/client/domain/timeline/MomentProvenance.kt`:

```kotlin
package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.isCapturedOnWatch

/** Whether every note behind this moment was captured on the watch; false for a moment without notes. */
fun Moment.capturedOnWatch(): Boolean = sourceNotes.isNotEmpty() && sourceNotes.all { it.isCapturedOnWatch }
```

In `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/main/HomeTimelineMapping.kt`, add the imports `app.logdate.client.domain.timeline.capturedOnWatch` and `app.logdate.client.repository.journals.isCapturedOnWatch`; in `Moment.toMomentUiState`, after `isHero = isHero,` add `        capturedOnWatch = capturedOnWatch(),`; and replace all four occurrences of

```kotlin
                    journals = noteJournals,
                )
```

with

```kotlin
                    journals = noteJournals,
                    capturedOnWatch = note.isCapturedOnWatch,
                )
```

In `client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/TimelineViewModel.kt`, add the same two imports; in `Moment.toMomentUiState`, after `isHero = isHero,` add `            capturedOnWatch = capturedOnWatch(),`; and replace all four occurrences of

```kotlin
                        journals = noteJournals,
                    )
```

with

```kotlin
                        journals = noteJournals,
                        capturedOnWatch = note.isCapturedOnWatch,
                    )
```

In `client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/details/NotesListSection.kt`, add the import `app.logdate.ui.provenance.CaptureTimeLabel` and replace all three occurrences of

```kotlin
        // Date/time displayed above the card
        Text(
            text = uiState.timestamp.toReadableDateTimeShort(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
```

with

```kotlin
        CaptureTimeLabel(
            text = uiState.timestamp.toReadableDateTimeShort(),
            capturedOnWatch = uiState.capturedOnWatch,
        )
```

In `client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/details/AudioNoteSnippet.kt`, add the imports `app.logdate.ui.provenance.CapturedOnWatchGlyph` and `app.logdate.ui.provenance.showsWatchProvenance`, and insert directly above `        MomentAudioCard(`:

```kotlin
        if (showsWatchProvenance(uiState.capturedOnWatch)) {
            CapturedOnWatchGlyph()
        }
```

In `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailUiState.kt`, add to `EntryDisplayData` after `val otherJournals: List<JournalReference>`:

```kotlin

    /** Whether the entry was captured on the watch. */
    val capturedOnWatch: Boolean
```

and replace all four occurrences of

```kotlin
        override val otherJournals: List<JournalReference> = emptyList(),
    ) : EntryDisplayData
```

with

```kotlin
        override val otherJournals: List<JournalReference> = emptyList(),
        override val capturedOnWatch: Boolean = false,
    ) : EntryDisplayData
```

Create `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/EntryDisplayMapping.kt`:

```kotlin
package app.logdate.feature.journals.ui.detail

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.isCapturedOnWatch

/**
 * Converts a note to the [EntryDisplayData] subtype for its kind, keeping media references for rich
 * rendering and whether it was captured on the watch.
 */
internal fun JournalNote.toEntryDisplayData(otherJournals: List<JournalReference> = emptyList()): EntryDisplayData =
    when (this) {
        is JournalNote.Text ->
            EntryDisplayData.TextEntry(
                id = uid,
                timestamp = creationTimestamp,
                content = content,
                otherJournals = otherJournals,
                capturedOnWatch = isCapturedOnWatch,
            )
        is JournalNote.Image ->
            EntryDisplayData.ImageEntry(
                id = uid,
                timestamp = creationTimestamp,
                mediaRef = mediaRef,
                caption = caption,
                presentation = presentation,
                otherJournals = otherJournals,
                capturedOnWatch = isCapturedOnWatch,
            )
        is JournalNote.Video ->
            EntryDisplayData.VideoEntry(
                id = uid,
                timestamp = creationTimestamp,
                mediaRef = mediaRef,
                caption = caption,
                otherJournals = otherJournals,
                capturedOnWatch = isCapturedOnWatch,
            )
        is JournalNote.Audio ->
            EntryDisplayData.AudioEntry(
                id = uid,
                timestamp = creationTimestamp,
                mediaRef = mediaRef,
                durationMs = durationMs,
                locationName = location?.displayName,
                otherJournals = otherJournals,
                capturedOnWatch = isCapturedOnWatch,
            )
    }
```

In `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailViewModel.kt`, change `note.toDisplayData(otherJournals)` to `note.toEntryDisplayData(otherJournals)` and delete the private `toDisplayData` function together with its KDoc (from `    /**\n     * Converts a JournalNote to the appropriate EntryDisplayData subtype,` through the closing `}` of its `when`). Run `:client:feature:journal:ktlintFormat` if the `JournalNote` import is left unused.

In `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalEntryCardShells.kt`:

1. Add the import `app.logdate.ui.provenance.CaptureTimeLabel`.
2. In `VerticalEntryCardShell`, replace `    contentPadding: Dp = Spacing.md,\n    content: @Composable () -> Unit,` with:

```kotlin
    contentPadding: Dp = Spacing.md,
    capturedOnWatch: Boolean = false,
    content: @Composable () -> Unit,
```

3. In `InlineEntryCardShell`, replace `    cardModifier: Modifier = Modifier,\n    content: @Composable RowScope.() -> Unit,` with:

```kotlin
    cardModifier: Modifier = Modifier,
    capturedOnWatch: Boolean = false,
    content: @Composable RowScope.() -> Unit,
```

4. Replace both occurrences of

```kotlin
        Text(
            text = timestamp.localTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
```

with

```kotlin
        CaptureTimeLabel(text = timestamp.localTime, capturedOnWatch = capturedOnWatch)
```

In `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailMediaCards.kt`, replace all four occurrences of `        timestamp = entry.timestamp,` (8 spaces; one per card shell call) with:

```kotlin
        timestamp = entry.timestamp,
        capturedOnWatch = entry.capturedOnWatch,
```

In `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/editor/EditorState.kt`:

1. After the constructor parameter `val visitContext: VisitMemoryContext? = null,` add `val capturedOnWatch: Boolean = false,`.
2. In `copy`, after the parameter `visitContext: VisitMemoryContext? = this.visitContext,` add `capturedOnWatch: Boolean = this.capturedOnWatch,`, and after the argument `visitContext = visitContext,` add `capturedOnWatch = capturedOnWatch,`.
3. In `equals`, after `if (visitContext != other.visitContext) return false` add `if (capturedOnWatch != other.capturedOnWatch) return false`.
4. In `hashCode`, after `result = 31 * result + contentRevision.hashCode()` add `result = 31 * result + capturedOnWatch.hashCode()`.

In `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/editor/delegate/ContentLoader.kt`, add the import `app.logdate.client.repository.journals.isCapturedOnWatch`, add above the class:

```kotlin
/** An entry loaded for editing, with whether the editor should say it was captured on the watch. */
data class LoadedEntry(
    val block: EntryBlockUiState,
    val capturedOnWatch: Boolean,
)

```

and replace `loadEntry` with:

```kotlin
    /**
     * Loads an existing entry by ID and converts it to a UI block.
     */
    suspend fun loadEntry(entryId: Uuid): Result<LoadedEntry> =
        try {
            val entry = fetchEntryUseCase(entryId)
            if (entry != null) {
                Result.success(LoadedEntry(block = entry.toDomainBlock(), capturedOnWatch = entry.isCapturedOnWatch))
            } else {
                Result.failure(NoSuchElementException("Entry not found: $entryId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
```

In `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/editor/EntryEditorViewModel.kt`, in `loadExistingEntry`, change `onSuccess = { block ->` to `onSuccess = { loaded ->` and replace `                            blocks = listOf(block),` with:

```kotlin
                            blocks = listOf(loaded.block),
                            capturedOnWatch = loaded.capturedOnWatch,
```

In `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/EntryEditorContent.kt`, add the imports `app.logdate.ui.provenance.CapturedOnWatchBanner` and `app.logdate.ui.provenance.showsWatchProvenance`, and replace

```kotlin
                if (!isImmersiveBlockActive) {
                    editorState.visitContext?.let { VisitMemoryContextBanner(it) }
                }
```

with

```kotlin
                if (!isImmersiveBlockActive) {
                    editorState.visitContext?.let { VisitMemoryContextBanner(it) }
                    if (showsWatchProvenance(editorState.capturedOnWatch)) {
                        CapturedOnWatchBanner()
                    }
                }
```

In `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/LogDateNavDisplay.kt`, add the import `app.logdate.ui.provenance.LocalWatchProvenanceVisible`; after the `workspaceEnabled` declaration add:

```kotlin
    val watchExperienceEnabled by remember(workspaceFlags) { workspaceFlags.observe(FeatureFlag.WATCH_EXPERIENCE_V2) }
        .collectAsState(initial = FeatureFlag.WATCH_EXPERIENCE_V2.defaultEnabled)
```

and in the root `CompositionLocalProvider`, after `LocalWorkspaceEnabled provides workspaceEnabled,`, add `LocalWatchProvenanceVisible provides watchExperienceEnabled,`.

In `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt`: after `    DayBoundaryRecovery("day-boundary-recovery"),` add `    CapturedOnWatch("captured-on-watch"),`; add `    TIMELINE,` after `    SECURITY,` in `ScreenshotSceneGroup`; and replace the `allScenes` initializer with:

```kotlin
    val allScenes: List<SharedScreenshotSceneSpec> =
        onboardingMainScenes +
            onboardingExtendedScenes +
            settingsScenes +
            watchSettingsScenes +
            searchScenes +
            desktopOnlyScenes +
            provenanceScenes
```

Create `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/ProvenanceScreenshotScenes.kt`:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.screenshots.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.provenance.CaptureTimeLabel
import app.logdate.ui.provenance.CapturedOnWatchBanner
import app.logdate.ui.provenance.CapturedOnWatchGlyph
import app.logdate.ui.provenance.LocalWatchProvenanceVisible

/** How a moment captured on the watch is marked: the editor banner, a note's time, and a moment's label. */
internal val provenanceScenes: List<SharedScreenshotSceneSpec> =
    listOf(
        sharedScene(SharedScreenshotSceneId.CapturedOnWatch, ScreenshotSceneGroup.TIMELINE, standardMatrixVariants) {
            CapturedOnWatchScene()
        },
    )

@Composable
private fun CapturedOnWatchScene() {
    CompositionLocalProvider(LocalWatchProvenanceVisible provides true) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CapturedOnWatchBanner()
            CaptureTimeLabel(text = "Today, 3:42 PM", capturedOnWatch = true)
            CaptureTimeLabel(text = "Today, 9:15 AM", capturedOnWatch = false)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "Morning run", style = MaterialTheme.typography.titleSmall)
                CapturedOnWatchGlyph()
            }
        }
    }
}
```

- [ ] **Step 5: Run the tests again**

Run the Step 3 command again.
Expected: PASS (4 `CapturedOnWatchTest`, every `TimelineDayUiStateFactoryTest`, 3 `MomentProvenanceTest`, 2 `HomeTimelineCapturedOnTest`, 3 `EntryDisplayMappingTest`, 3 `ContentLoaderTest`).

- [ ] **Step 6: Record and look at the provenance scene**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=captured-on-watch :app:compose-main:updateDesktopScreenshotTest --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared*' --console=plain
```
Expected: BUILD SUCCESSFUL and four new files, `captured-on-watch--{phone,phone-dark,phone-landscape,tablet}.png`, nothing else changed. Open all four with the Read tool: the banner shows the watch glyph and "Captured on your watch" on the secondary container; "Today, 3:42 PM" has a small watch glyph after it, vertically centered, and "Today, 9:15 AM" has none; "Morning run" has the glyph after it. The glyph is legible against both the light and dark surfaces.

Then confirm no other desktop or Android baseline moved (without the glyph, the time line and the moment label render exactly as before):
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:compose-main:desktopTest --tests 'app.logdate.screenshots.DesktopScreenshotTest' :app:android-main:validateDebugScreenshotTest --console=plain
```
Expected: PASS.

- [ ] **Step 7: Run the touched modules' suites, the iOS compile and the checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:ui:desktopTest :client:domain:jvmTest :client:feature:core:desktopTest :client:feature:timeline:desktopTest :client:feature:journal:desktopTest :client:feature:editor:desktopTest :client:screenshot-scenes:desktopTest :app:compose-main:compileKotlinDesktop :client:ui:compileKotlinIosSimulatorArm64 :client:ui:compileTestKotlinIosSimulatorArm64 :client:domain:compileKotlinIosSimulatorArm64 :client:domain:compileTestKotlinIosSimulatorArm64 :client:feature:core:compileTestKotlinIosSimulatorArm64 :client:feature:timeline:compileKotlinIosSimulatorArm64 :client:feature:journal:compileTestKotlinIosSimulatorArm64 :client:feature:editor:compileTestKotlinIosSimulatorArm64 :client:screenshot-scenes:compileKotlinIosSimulatorArm64 :app:compose-main:compileKotlinIosSimulatorArm64 :client:ui:ktlintCheck :client:domain:ktlintCheck :client:feature:core:ktlintCheck :client:feature:timeline:ktlintCheck :client:feature:journal:ktlintCheck :client:feature:editor:ktlintCheck :client:screenshot-scenes:ktlintCheck :app:compose-main:ktlintCheck :client:ui:detekt :client:feature:journal:detekt :client:feature:editor:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task08-watch-glyph.txt <<'EOF'
feat(timeline): mark moments captured on the watch

With the redesigned watch experience turned on in Labs, moments you
recorded on your watch now show a small watch symbol: next to the
time of each note in a day's details, above a voice recording, beside
a moment in the timeline when everything in it came from the watch,
next to the time of an entry in a journal, and at the top of the
editor when you open one.

Moments from the phone, and moments from before this was recorded,
look the same as before.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 9: Commit**

```bash
git restore --staged . && git add client/ui/src/commonMain/kotlin/app/logdate/ui/provenance/CapturedOnWatch.kt client/ui/src/commonMain/composeResources/values/strings.xml client/ui/src/desktopTest/kotlin/app/logdate/ui/provenance/CapturedOnWatchTest.kt client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/HomeTimelineUiState.kt client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/TimelineDayUiStateFactory.kt client/ui/src/commonTest/kotlin/app/logdate/ui/timeline/TimelineDayUiStateFactoryTest.kt client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/newstuff/MomentComposables.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/timeline/MomentProvenance.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/timeline/MomentProvenanceTest.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/main/HomeTimelineMapping.kt client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/main/HomeTimelineCapturedOnTest.kt client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/TimelineViewModel.kt client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/details/NotesListSection.kt client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/details/AudioNoteSnippet.kt client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailUiState.kt client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/EntryDisplayMapping.kt client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailViewModel.kt client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalEntryCardShells.kt client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailMediaCards.kt client/feature/journal/src/commonTest/kotlin/app/logdate/feature/journals/ui/detail/EntryDisplayMappingTest.kt client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/editor/EditorState.kt client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/editor/delegate/ContentLoader.kt client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/editor/EntryEditorViewModel.kt client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/EntryEditorContent.kt client/feature/editor/src/commonTest/kotlin/app/logdate/feature/editor/ui/editor/delegate/ContentLoaderTest.kt app/compose-main/src/commonMain/kotlin/app/logdate/navigation/LogDateNavDisplay.kt client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/ProvenanceScreenshotScenes.kt app/compose-main/src/desktopTest/reference/captured-on-watch--phone.png app/compose-main/src/desktopTest/reference/captured-on-watch--phone-dark.png app/compose-main/src/desktopTest/reference/captured-on-watch--phone-landscape.png app/compose-main/src/desktopTest/reference/captured-on-watch--tablet.png && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task08-watch-glyph.txt
```

---
### Task 9: Read marked moments and fill in their place (D2)

**Files:**
- Create: `client/repository/src/commonMain/kotlin/app/logdate/client/repository/moments/MarkedMoment.kt`
- Modify: `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/moments/MarkedMomentDao.kt` (one query)
- Test: `client/database/src/jvmTest/kotlin/app/logdate/client/database/MarkedMomentLocationDaoJvmTest.kt`
- Create: `client/data/src/commonMain/kotlin/app/logdate/client/data/moments/OfflineFirstMarkedMomentRepository.kt`
- Test: `client/data/src/commonTest/kotlin/app/logdate/client/data/moments/OfflineFirstMarkedMomentRepositoryTest.kt`
- Modify: `client/data/src/androidMain/kotlin/app/logdate/client/data/di/DataModule.android.kt:208`, `client/data/src/desktopMain/kotlin/app/logdate/client/data/di/DataModule.desktop.kt:201`, `client/data/src/iosMain/kotlin/app/logdate/client/data/di/DataModule.ios.kt:203` (one binding each, after `UserPlacesRepository`)
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/moments/EnrichMarkLocationUseCase.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/moments/EnrichMarkLocationUseCaseTest.kt`
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/di/DomainModule.kt` (one factory, next to the Task 2 bindings)

**Interfaces:**
- Consumes: `MarkedMomentEntity`, `MarkedMomentState`, `MarkedMomentDao` (bound in `DaosModule`); `LocationHistoryRepository.getLocationHistoryBetween(startTime, endTime): List<LocationHistoryItem>`; `WatchPreferencesRepository`, `SharedPreferenceKey.CapturePlaces` (Task 2); `RoundTripLocationHistoryRepository` (`client/domain` `commonTest`, package `app.logdate.client.domain.export.support`).
- Produces:
  - `enum class MarkState { OPEN, DISMISSED, FILLED }`, `data class MarkLocation(val latitude: Double, val longitude: Double, val accuracyMeters: Float?)`, `data class MarkedMoment(val id: Uuid, val capturedAt: Instant, val timeZoneId: String, val location: MarkLocation?, val heartRateBpm: Int?, val state: MarkState, val caption: String, val resolvedNoteIds: Set<Uuid>, val originDeviceId: String, val lastUpdated: Instant, val syncVersion: Long)`
  - `interface MarkedMomentRepository { fun observeOpenMarks(): Flow<List<MarkedMoment>>; suspend fun getMark(id: Uuid): MarkedMoment?; suspend fun fillLocationIfMissing(id: Uuid, location: MarkLocation, updatedAt: Instant): Boolean }` and `class OfflineFirstMarkedMomentRepository(dao: MarkedMomentDao)`
  - `suspend fun MarkedMomentDao.fillLocationIfMissing(id: Uuid, latitude: Double, longitude: Double, accuracy: Float?, lastUpdated: Instant): Int`
  - `sealed interface MarkLocationResult { data class Filled(val location: MarkLocation); data object AlreadyPlaced; data object PlacesOff; data object NoNearbySample; data object MarkNotFound }` and `class EnrichMarkLocationUseCase(marks: MarkedMomentRepository, locationHistory: LocationHistoryRepository, watchPreferences: WatchPreferencesRepository, clock: Clock = Clock.System) { suspend operator fun invoke(markId: Uuid): MarkLocationResult; companion object { val WINDOW: Duration; const val MAX_ACCURACY_METERS: Float } }`
  - D3 (`TimelineDay.marks`, `MarkedMomentEntry`) builds on these; nothing calls them yet.

- [ ] **Step 1: Write the failing tests**

Create `client/database/src/jvmTest/kotlin/app/logdate/client/database/MarkedMomentLocationDaoJvmTest.kt`:

```kotlin
package app.logdate.client.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests that a place fills a mark only while the mark has none and still exists. */
class MarkedMomentLocationDaoJvmTest {
    private val database = Room.inMemoryDatabaseBuilder<LogDateDatabase>().setDriver(BundledSQLiteDriver()).build()
    private val filledAt = Instant.fromEpochMilliseconds(50)

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a place fills a mark captured without one`() =
        runTest {
            val dao = database.markedMomentDao()
            val mark = mark()
            dao.upsert(mark)

            assertEquals(1, dao.fillLocationIfMissing(mark.id, 37.7694, -122.4862, 12f, filledAt))
            assertEquals(
                mark.copy(latitude = 37.7694, longitude = -122.4862, locationAccuracy = 12f, lastUpdated = filledAt),
                dao.getById(mark.id),
            )
        }

    @Test
    fun `a mark that has a place is left as it is`() =
        runTest {
            val dao = database.markedMomentDao()
            val mark = mark().copy(latitude = 1.0, longitude = 2.0, locationAccuracy = 5f)
            dao.upsert(mark)

            assertEquals(0, dao.fillLocationIfMissing(mark.id, 37.7694, -122.4862, 12f, filledAt))
            assertEquals(mark, dao.getById(mark.id))
        }

    @Test
    fun `a deleted mark is left as it is`() =
        runTest {
            val dao = database.markedMomentDao()
            val mark = mark().copy(deletedAt = Instant.fromEpochMilliseconds(20))
            dao.upsert(mark)

            assertEquals(0, dao.fillLocationIfMissing(mark.id, 37.7694, -122.4862, 12f, filledAt))
            assertEquals(mark, dao.getById(mark.id))
        }

    private fun mark() =
        MarkedMomentEntity(
            id = Uuid.random(),
            capturedAt = Instant.fromEpochMilliseconds(10),
            timeZoneId = "America/Los_Angeles",
            state = MarkedMomentState.OPEN,
            originDeviceId = "watch",
            lastUpdated = Instant.fromEpochMilliseconds(10),
        )
}
```

Create `client/data/src/commonTest/kotlin/app/logdate/client/data/moments/OfflineFirstMarkedMomentRepositoryTest.kt`:

```kotlin
package app.logdate.client.data.moments

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [OfflineFirstMarkedMomentRepository], which reads stored marks as the timeline uses them. */
class OfflineFirstMarkedMomentRepositoryTest {
    private val dao = FakeMarkedMomentDao()
    private val repository = OfflineFirstMarkedMomentRepository(dao)
    private val at = Instant.fromEpochMilliseconds(1_710_000_000_000)

    @Test
    fun `open marks read with their place and notes`() =
        runTest {
            val id = Uuid.random()
            val note = Uuid.random()
            dao.rows += row(id).copy(latitude = 37.7694, longitude = -122.4862, locationAccuracy = 8f, resolvedNoteIds = note.toString())

            assertEquals(
                listOf(
                    MarkedMoment(
                        id = id,
                        capturedAt = at,
                        timeZoneId = "America/Los_Angeles",
                        location = MarkLocation(latitude = 37.7694, longitude = -122.4862, accuracyMeters = 8f),
                        heartRateBpm = 72,
                        state = MarkState.OPEN,
                        caption = "",
                        resolvedNoteIds = setOf(note),
                        originDeviceId = "watch",
                        lastUpdated = at,
                        syncVersion = 0,
                    ),
                ),
                repository.observeOpenMarks().first(),
            )
        }

    @Test
    fun `a mark without coordinates has no place`() =
        runTest {
            val id = Uuid.random()
            dao.rows += row(id)

            assertNull(repository.getMark(id)?.location)
        }

    @Test
    fun `a mark in a state this version does not know is left out`() =
        runTest {
            val id = Uuid.random()
            dao.rows += row(id).copy(state = "SNOOZED")

            assertNull(repository.getMark(id))
        }

    @Test
    fun `a deleted mark is not found`() =
        runTest {
            val id = Uuid.random()
            dao.rows += row(id).copy(deletedAt = at)

            assertNull(repository.getMark(id))
        }

    @Test
    fun `note ids that cannot be read are skipped`() =
        runTest {
            val id = Uuid.random()
            val note = Uuid.random()
            dao.rows += row(id).copy(resolvedNoteIds = "not-an-id,$note")

            assertEquals(setOf(note), repository.getMark(id)?.resolvedNoteIds)
        }

    @Test
    fun `filling a place says whether the mark changed`() =
        runTest {
            val id = Uuid.random()
            val place = MarkLocation(latitude = 37.7694, longitude = -122.4862, accuracyMeters = 12f)

            assertTrue(repository.fillLocationIfMissing(id, place, at))
            dao.filledRows = 0
            assertFalse(repository.fillLocationIfMissing(id, place, at))
            assertEquals(listOf(id, id), dao.fills)
        }

    private fun row(id: Uuid) =
        MarkedMomentEntity(
            id = id,
            capturedAt = at,
            timeZoneId = "America/Los_Angeles",
            heartRateBpm = 72,
            state = MarkedMomentState.OPEN,
            originDeviceId = "watch",
            lastUpdated = at,
        )

    private class FakeMarkedMomentDao : MarkedMomentDao {
        val rows = mutableListOf<MarkedMomentEntity>()
        val fills = mutableListOf<Uuid>()
        var filledRows = 1

        override suspend fun upsert(moment: MarkedMomentEntity) {
            rows.removeAll { it.id == moment.id }
            rows += moment
        }

        override suspend fun getById(id: Uuid): MarkedMomentEntity? = rows.firstOrNull { it.id == id }

        override fun observeByState(state: String): Flow<List<MarkedMomentEntity>> =
            flowOf(rows.filter { it.state == state && it.deletedAt == null }.sortedByDescending { it.capturedAt })

        override suspend fun fillLocationIfMissing(
            id: Uuid,
            latitude: Double,
            longitude: Double,
            accuracy: Float?,
            lastUpdated: Instant,
        ): Int {
            fills += id
            return filledRows
        }
    }
}
```

Create `client/domain/src/commonTest/kotlin/app/logdate/client/domain/moments/EnrichMarkLocationUseCaseTest.kt`:

```kotlin
package app.logdate.client.domain.moments

import app.logdate.client.domain.export.support.RoundTripLocationHistoryRepository
import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.domain.watch.DefaultWatchPreferencesRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [EnrichMarkLocationUseCase]: a mark captured without a place takes the nearest accurate,
 * genuine location sample within ten minutes, and only when places are on.
 */
class EnrichMarkLocationUseCaseTest {
    private val capturedAt = Instant.parse("2026-10-10T22:42:00Z")
    private val now = Instant.parse("2026-10-10T23:00:00Z")
    private val marks = FakeMarks()
    private val history = RoundTripLocationHistoryRepository()
    private val preferences = DefaultWatchPreferencesRepository(StampedPreferenceStore(FakeKeyValueStorage(), PeerRole.PHONE))
    private val enrich =
        EnrichMarkLocationUseCase(
            marks = marks,
            locationHistory = history,
            watchPreferences = preferences,
            clock =
                object : Clock {
                    override fun now(): Instant = now
                },
        )

    @Test
    fun `nothing is filled while places are off`() =
        runTest {
            val mark = marks.add(mark())
            history.entries = listOf(sample(offset = 1.minutes, accuracy = 10f))

            assertEquals(MarkLocationResult.PlacesOff, enrich(mark.id))
            assertTrue(marks.fills.isEmpty())
        }

    @Test
    fun `a mark that has a place is left alone`() =
        runTest {
            placesOn()
            val mark = marks.add(mark().copy(location = MarkLocation(1.0, 2.0, 5f)))

            assertEquals(MarkLocationResult.AlreadyPlaced, enrich(mark.id))
        }

    @Test
    fun `a missing mark is reported`() =
        runTest {
            placesOn()

            assertEquals(MarkLocationResult.MarkNotFound, enrich(Uuid.random()))
        }

    @Test
    fun `the nearest accurate genuine sample within ten minutes fills the mark`() =
        runTest {
            placesOn()
            val mark = marks.add(mark())
            history.entries =
                listOf(
                    sample(offset = 3.minutes, accuracy = 40f, latitude = 37.10),
                    sample(offset = (-1).minutes, accuracy = 150f, latitude = 37.20),
                    sample(offset = (-11).minutes, accuracy = 5f, latitude = 37.30),
                    sample(offset = 30.seconds, accuracy = 5f, latitude = 37.40, isMock = true),
                    sample(offset = (-2).minutes, accuracy = 5f, latitude = 37.50, isGenuine = false),
                    sample(offset = (-1).minutes, accuracy = null, latitude = 37.60),
                )

            val expected = MarkLocation(latitude = 37.10, longitude = LONGITUDE, accuracyMeters = 40f)
            assertEquals(MarkLocationResult.Filled(expected), enrich(mark.id))
            assertEquals(listOf(Triple(mark.id, expected, now)), marks.fills)
        }

    @Test
    fun `ten minutes away and 100 meters still count`() =
        runTest {
            placesOn()
            val mark = marks.add(mark())
            history.entries = listOf(sample(offset = 10.minutes, accuracy = 100f, latitude = 37.70))

            assertEquals(MarkLocationResult.Filled(MarkLocation(37.70, LONGITUDE, 100f)), enrich(mark.id))
        }

    @Test
    fun `a tie in time goes to the more accurate sample`() =
        runTest {
            placesOn()
            val mark = marks.add(mark())
            history.entries =
                listOf(
                    sample(offset = (-4).minutes, accuracy = 50f, latitude = 37.80),
                    sample(offset = 4.minutes, accuracy = 20f, latitude = 37.90),
                )

            assertEquals(MarkLocationResult.Filled(MarkLocation(37.90, LONGITUDE, 20f)), enrich(mark.id))
        }

    @Test
    fun `no usable sample leaves the mark as it is`() =
        runTest {
            placesOn()
            val mark = marks.add(mark())
            history.entries = listOf(sample(offset = 1.minutes, accuracy = 250f))

            assertEquals(MarkLocationResult.NoNearbySample, enrich(mark.id))
            assertTrue(marks.fills.isEmpty())
        }

    @Test
    fun `a place filled elsewhere first is not overwritten`() =
        runTest {
            placesOn()
            val mark = marks.add(mark())
            history.entries = listOf(sample(offset = 1.minutes, accuracy = 10f))
            marks.fillSucceeds = false

            assertEquals(MarkLocationResult.AlreadyPlaced, enrich(mark.id))
        }

    private suspend fun placesOn() {
        preferences.set(SharedPreferenceKey.CapturePlaces, true)
    }

    private fun mark() =
        MarkedMoment(
            id = Uuid.random(),
            capturedAt = capturedAt,
            timeZoneId = "America/Los_Angeles",
            location = null,
            heartRateBpm = null,
            state = MarkState.OPEN,
            caption = "",
            resolvedNoteIds = emptySet(),
            originDeviceId = "watch",
            lastUpdated = capturedAt,
            syncVersion = 0,
        )

    private fun sample(
        offset: Duration,
        accuracy: Float?,
        latitude: Double = 37.77,
        isGenuine: Boolean = true,
        isMock: Boolean = false,
    ) = LocationHistoryItem(
        userId = "user",
        deviceId = "phone",
        timestamp = capturedAt + offset,
        location = Location(latitude = latitude, longitude = LONGITUDE, altitude = LocationAltitude(10.0, AltitudeUnit.METERS)),
        confidence = 0.9f,
        isGenuine = isGenuine,
        accuracyMeters = accuracy,
        isMock = isMock,
    )

    private class FakeMarks : MarkedMomentRepository {
        private val marks = mutableMapOf<Uuid, MarkedMoment>()
        val fills = mutableListOf<Triple<Uuid, MarkLocation, Instant>>()
        var fillSucceeds = true

        fun add(mark: MarkedMoment): MarkedMoment {
            marks[mark.id] = mark
            return mark
        }

        override fun observeOpenMarks(): Flow<List<MarkedMoment>> = flowOf(marks.values.filter { it.state == MarkState.OPEN })

        override suspend fun getMark(id: Uuid): MarkedMoment? = marks[id]

        override suspend fun fillLocationIfMissing(
            id: Uuid,
            location: MarkLocation,
            updatedAt: Instant,
        ): Boolean {
            if (!fillSucceeds) return false
            fills += Triple(id, location, updatedAt)
            return true
        }
    }

    private companion object {
        const val LONGITUDE = -122.48
    }
}
```

- [ ] **Step 2: Add the API surface with inert bodies**

Create `MarkedMoment.kt` exactly as in Step 4 (types and the interface have no behavior). Add the `fillLocationIfMissing` query to `MarkedMomentDao` exactly as in Step 4: Room generates the DAO from the query, so it has no inert form, and its JVM test guards the query rather than driving it. Create `OfflineFirstMarkedMomentRepository.kt` with `observeOpenMarks` returning `flowOf(emptyList())`, `getMark` returning `null` and `fillLocationIfMissing` returning `false`, and `EnrichMarkLocationUseCase.kt` with the declarations from Step 4 and `invoke` returning `MarkLocationResult.MarkNotFound`.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:database:jvmTest --tests 'app.logdate.client.database.MarkedMomentLocationDaoJvmTest' :client:data:desktopTest --tests 'app.logdate.client.data.moments.OfflineFirstMarkedMomentRepositoryTest' :client:domain:jvmTest --tests 'app.logdate.client.domain.moments.EnrichMarkLocationUseCaseTest' --continue --console=plain
```
Expected: FAIL. `MarkedMomentLocationDaoJvmTest` passes (see Step 2). `OfflineFirstMarkedMomentRepositoryTest`: `open marks read with their place and notes`, `note ids that cannot be read are skipped`, `filling a place says whether the mark changed`. `EnrichMarkLocationUseCaseTest`: every test except `a missing mark is reported`.

- [ ] **Step 4: Implement reading marks and filling their place**

`client/repository/src/commonMain/kotlin/app/logdate/client/repository/moments/MarkedMoment.kt`:

```kotlin
package app.logdate.client.repository.moments

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Where a mark is in its life. Merges take the highest: OPEN, then DISMISSED, then FILLED. */
enum class MarkState {
    OPEN,
    DISMISSED,
    FILLED,
}

/** Where a mark was captured, as precisely as the source knew it. */
data class MarkLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)

/**
 * A moment someone marked on the watch to fill in later.
 *
 * Its own record rather than a note: it is left out of moment inference, Rewind, widgets and the
 * library, and appears in the timeline as a mark to finish.
 *
 * @property resolvedNoteIds The notes that filled this mark in.
 * @property originDeviceId The device that captured the mark.
 */
data class MarkedMoment(
    val id: Uuid,
    val capturedAt: Instant,
    val timeZoneId: String,
    val location: MarkLocation?,
    val heartRateBpm: Int?,
    val state: MarkState,
    val caption: String,
    val resolvedNoteIds: Set<Uuid>,
    val originDeviceId: String,
    val lastUpdated: Instant,
    val syncVersion: Long,
)

interface MarkedMomentRepository {
    /** Marks waiting to be filled in, newest first; deleted marks are left out. */
    fun observeOpenMarks(): Flow<List<MarkedMoment>>

    /** The mark with [id], or null when there is none or it was deleted. */
    suspend fun getMark(id: Uuid): MarkedMoment?

    /**
     * Records [location] for a mark captured without one. False when the mark already has a place or
     * no longer exists, so a place set elsewhere is never overwritten.
     */
    suspend fun fillLocationIfMissing(
        id: Uuid,
        location: MarkLocation,
        updatedAt: Instant,
    ): Boolean
}
```

In `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/moments/MarkedMomentDao.kt`, add the import `kotlin.time.Instant` and this method after `observeByState`:

```kotlin
    /** Records a place for a mark captured without one; 1 when it did, 0 when the mark has a place or was deleted. */
    @Query(
        "UPDATE marked_moments SET latitude = :latitude, longitude = :longitude, locationAccuracy = :accuracy, " +
            "lastUpdated = :lastUpdated WHERE id = :id AND latitude IS NULL AND deletedAt IS NULL",
    )
    suspend fun fillLocationIfMissing(
        id: Uuid,
        latitude: Double,
        longitude: Double,
        accuracy: Float?,
        lastUpdated: Instant,
    ): Int
```

`client/data/src/commonMain/kotlin/app/logdate/client/data/moments/OfflineFirstMarkedMomentRepository.kt`:

```kotlin
package app.logdate.client.data.moments

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Reads marked moments from Room. Marks are written by sync and the watch ingest, not here. */
class OfflineFirstMarkedMomentRepository(
    private val dao: MarkedMomentDao,
) : MarkedMomentRepository {
    override fun observeOpenMarks(): Flow<List<MarkedMoment>> =
        dao.observeByState(MarkedMomentState.OPEN).map { rows -> rows.mapNotNull { it.toModel() } }

    override suspend fun getMark(id: Uuid): MarkedMoment? = dao.getById(id)?.takeIf { it.deletedAt == null }?.toModel()

    override suspend fun fillLocationIfMissing(
        id: Uuid,
        location: MarkLocation,
        updatedAt: Instant,
    ): Boolean = dao.fillLocationIfMissing(id, location.latitude, location.longitude, location.accuracyMeters, updatedAt) > 0
}

/** The mark this row stores, or null for a state a newer app version wrote, which this one cannot show. */
internal fun MarkedMomentEntity.toModel(): MarkedMoment? {
    val markState = MarkState.entries.firstOrNull { it.name == state }
    if (markState == null) {
        Napier.w("Mark $id is in a state this version does not know; it is left out until the app updates")
        return null
    }
    return MarkedMoment(
        id = id,
        capturedAt = capturedAt,
        timeZoneId = timeZoneId,
        location = locationOrNull(),
        heartRateBpm = heartRateBpm,
        state = markState,
        caption = caption,
        resolvedNoteIds = resolvedNoteIds.split(',').mapNotNull { Uuid.parseOrNull(it.trim()) }.toSet(),
        originDeviceId = originDeviceId,
        lastUpdated = lastUpdated,
        syncVersion = syncVersion,
    )
}

private fun MarkedMomentEntity.locationOrNull(): MarkLocation? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return MarkLocation(latitude = lat, longitude = lon, accuracyMeters = locationAccuracy)
}
```

In each of `client/data/src/androidMain/kotlin/app/logdate/client/data/di/DataModule.android.kt`, `client/data/src/desktopMain/kotlin/app/logdate/client/data/di/DataModule.desktop.kt` and `client/data/src/iosMain/kotlin/app/logdate/client/data/di/DataModule.ios.kt`, after `single<UserPlacesRepository> { OfflineFirstUserPlacesRepository(get()) }`, add:

```kotlin
        single<MarkedMomentRepository> { OfflineFirstMarkedMomentRepository(get()) }
```

and the imports `app.logdate.client.data.moments.OfflineFirstMarkedMomentRepository` and `app.logdate.client.repository.moments.MarkedMomentRepository`.

`client/domain/src/commonMain/kotlin/app/logdate/client/domain/moments/EnrichMarkLocationUseCase.kt`:

```kotlin
package app.logdate.client.domain.moments

import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkedMomentRepository
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** What happened when the phone looked for a place for a mark. */
sealed interface MarkLocationResult {
    data class Filled(
        val location: MarkLocation,
    ) : MarkLocationResult

    /** The mark already had a place, or another path set one first. */
    data object AlreadyPlaced : MarkLocationResult

    /** Places are off, so the mark is left without one. */
    data object PlacesOff : MarkLocationResult

    /** No accurate, genuine sample falls within [EnrichMarkLocationUseCase.WINDOW] of the mark. */
    data object NoNearbySample : MarkLocationResult

    data object MarkNotFound : MarkLocationResult
}

/**
 * Gives a mark captured without a place the place this phone recorded around the same time.
 *
 * A mark from the watch face cannot read a location in the background, so the phone fills it in from
 * its own location history: the sample nearest in time within [WINDOW] either way, at most
 * [MAX_ACCURACY_METERS] uncertain, genuine and not mocked, with a tie in time going to the more
 * accurate sample. Nothing is filled unless places are on.
 */
class EnrichMarkLocationUseCase(
    private val marks: MarkedMomentRepository,
    private val locationHistory: LocationHistoryRepository,
    private val watchPreferences: WatchPreferencesRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(markId: Uuid): MarkLocationResult {
        val mark = marks.getMark(markId) ?: return MarkLocationResult.MarkNotFound
        if (mark.location != null) return MarkLocationResult.AlreadyPlaced
        if (!watchPreferences.get(SharedPreferenceKey.CapturePlaces)) return MarkLocationResult.PlacesOff
        val sample = nearestSample(mark.capturedAt) ?: return MarkLocationResult.NoNearbySample
        val location =
            MarkLocation(
                latitude = sample.location.latitude,
                longitude = sample.location.longitude,
                accuracyMeters = sample.accuracyMeters,
            )
        if (!marks.fillLocationIfMissing(mark.id, location, clock.now())) return MarkLocationResult.AlreadyPlaced
        return MarkLocationResult.Filled(location)
    }

    private suspend fun nearestSample(capturedAt: Instant): LocationHistoryItem? =
        locationHistory
            .getLocationHistoryBetween(capturedAt - WINDOW, capturedAt + WINDOW)
            .filter { it.isGenuine && !it.isMock }
            .filter { sample -> sample.accuracyMeters?.let { it <= MAX_ACCURACY_METERS } == true }
            .filter { (it.timestamp - capturedAt).absoluteValue <= WINDOW }
            .minWithOrNull(
                compareBy<LocationHistoryItem> { (it.timestamp - capturedAt).absoluteValue }
                    .thenBy { it.accuracyMeters },
            )

    companion object {
        val WINDOW: Duration = 10.minutes
        const val MAX_ACCURACY_METERS = 100f
    }
}
```

In `client/domain/src/commonMain/kotlin/app/logdate/client/domain/di/DomainModule.kt`, after the Task 2 `WatchPreferencesRepository` binding, add:

```kotlin
        factory { EnrichMarkLocationUseCase(marks = get(), locationHistory = get(), watchPreferences = get()) }
```

and the import `app.logdate.client.domain.moments.EnrichMarkLocationUseCase`.

- [ ] **Step 5: Run the tests again**

Run the Step 3 command again.
Expected: PASS (3 `MarkedMomentLocationDaoJvmTest`, 6 `OfflineFirstMarkedMomentRepositoryTest`, 8 `EnrichMarkLocationUseCaseTest`).

- [ ] **Step 6: Run the touched modules' suites, the iOS compile and the checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:database:jvmTest :client:data:desktopTest :client:data:testAndroidHostTest :client:domain:jvmTest :client:repository:compileKotlinIosSimulatorArm64 :client:database:compileKotlinIosSimulatorArm64 :client:data:compileKotlinIosSimulatorArm64 :client:data:compileTestKotlinIosSimulatorArm64 :client:domain:compileKotlinIosSimulatorArm64 :client:domain:compileTestKotlinIosSimulatorArm64 :client:repository:ktlintCheck :client:database:ktlintCheck :client:data:ktlintCheck :client:domain:ktlintCheck :client:data:detekt :client:domain:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task09-marked-moments.txt <<'EOF'
chore(timeline): read marked moments and fill in their place

Marks made on the watch will appear in the phone's timeline as moments
to finish. This adds the read side: the marks still open, newest first,
with their place, heart rate and the notes that filled them, leaving
out deleted marks and any mark in a state a newer version introduced.

A mark from the watch face has no place, because the watch cannot read
its location in the background. The phone can now fill one in from
its own location history: the sample nearest in time within ten
minutes either way, at most 100 meters uncertain, genuine and not
mocked. It does so only when places are on, and never overwrites a
place that is already set. Nothing calls this yet; the timeline entry
for marks comes next.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/repository/src/commonMain/kotlin/app/logdate/client/repository/moments/MarkedMoment.kt client/database/src/commonMain/kotlin/app/logdate/client/database/dao/moments/MarkedMomentDao.kt client/database/src/jvmTest/kotlin/app/logdate/client/database/MarkedMomentLocationDaoJvmTest.kt client/data/src/commonMain/kotlin/app/logdate/client/data/moments/OfflineFirstMarkedMomentRepository.kt client/data/src/commonTest/kotlin/app/logdate/client/data/moments/OfflineFirstMarkedMomentRepositoryTest.kt client/data/src/androidMain/kotlin/app/logdate/client/data/di/DataModule.android.kt client/data/src/desktopMain/kotlin/app/logdate/client/data/di/DataModule.desktop.kt client/data/src/iosMain/kotlin/app/logdate/client/data/di/DataModule.ios.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/moments/EnrichMarkLocationUseCase.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/moments/EnrichMarkLocationUseCaseTest.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/di/DomainModule.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task09-marked-moments.txt
```

---
### Task 10: Decide when a moment-aware reminder may ring (R1)

**Files:**
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/ReminderModels.kt`
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/ReminderOrchestrator.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/reminders/ReminderTestFixtures.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/reminders/ReminderWindowTest.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/reminders/ReminderTriggerTest.kt`

**Interfaces:**
- Consumes: `FirePhase`, `DEFAULT_STREAK_DAY_START_HOUR`, `Instant.toStreakDay(zone, dayStartHour)`, `nextStreakDayStart(after, zone, dayStartHour)` (`client/domain/.../streak`).
- Produces (pure, no I/O; R2 supplies the signals and R3 delivers):
  - `data class WakingHours(val wake: LocalTime, val sleep: LocalTime) { companion object { val Fallback: WakingHours; fun from(averageWake: LocalTime?, averageSleep: LocalTime?): WakingHours } }`
  - `data class ReminderClock(val now: Instant, val zone: TimeZone, val wakingHours: WakingHours = WakingHours.Fallback, val dayStartHour: Int = DEFAULT_STREAK_DAY_START_HOUR, val lastReminderAt: Instant? = null)`
  - `data class ReminderSignals(val enabled: Boolean, val writtenToday: Boolean, val openMarks: Int = 0, val campfire: FirePhase? = null, val newPlaceArrivedAt: Instant? = null, val workoutEndedAt: Instant? = null)`
  - `enum class ReminderTrigger { WORKOUT_ENDED, ARRIVED_SOMEWHERE_NEW, CAMPFIRE_EMBERS, WIND_DOWN }`, `enum class SkipReason { REMINDERS_OFF, OUTSIDE_WAKING_HOURS, ALREADY_REMINDED_TODAY, NOTHING_TO_REMIND }`
  - `sealed interface ReminderDecision { data class Deliver(val trigger: ReminderTrigger, val openMarks: Int); data class Skip(val reason: SkipReason) }`
  - `object ReminderOrchestrator { val WORKOUT_RECENCY: Duration; val ARRIVAL_DWELL: Duration; val CAMPFIRE_LAST_STRETCH: Duration; val WIND_DOWN_LEAD: Duration; fun decide(clock: ReminderClock, signals: ReminderSignals): ReminderDecision }`

- [ ] **Step 1: Write the failing tests**

Create `client/domain/src/commonTest/kotlin/app/logdate/client/domain/reminders/ReminderTestFixtures.kt`:

```kotlin
package app.logdate.client.domain.reminders

import app.logdate.client.domain.streak.FirePhase
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** One row of a decision table: the clock and signals, and the decision they must produce. */
internal data class ReminderCase(
    val name: String,
    val clock: ReminderClock,
    val signals: ReminderSignals,
    val expected: ReminderDecision,
)

internal val denver: TimeZone = TimeZone.of("America/Denver")

/** A Wednesday in October, when Denver is on daylight time. */
internal val day: LocalDate = LocalDate(2026, 10, 14)

internal fun at(
    hour: Int,
    minute: Int,
    date: LocalDate = day,
): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(denver)

internal fun nextDay(
    hour: Int,
    minute: Int,
): Instant = at(hour, minute, day.plus(1, DateTimeUnit.DAY))

internal fun hours(
    wakeHour: Int,
    wakeMinute: Int,
    sleepHour: Int,
    sleepMinute: Int,
) = WakingHours(LocalTime(wakeHour, wakeMinute), LocalTime(sleepHour, sleepMinute))

internal fun clock(
    now: Instant,
    wakingHours: WakingHours = WakingHours.Fallback,
    dayStartHour: Int = 4,
    lastReminderAt: Instant? = null,
) = ReminderClock(now = now, zone = denver, wakingHours = wakingHours, dayStartHour = dayStartHour, lastReminderAt = lastReminderAt)

/** Reminders on, something written, the fire burning: nothing to remind about. */
internal val quiet = ReminderSignals(enabled = true, writtenToday = true, campfire = FirePhase.BURNING)

/** [quiet] plus a workout that ended five minutes before [now], so only the window and the daily limit decide. */
internal fun workoutJustEnded(now: Instant) = quiet.copy(workoutEndedAt = now - 5.minutes)

internal val deliverWorkout = ReminderDecision.Deliver(ReminderTrigger.WORKOUT_ENDED, openMarks = 0)

internal fun skip(reason: SkipReason) = ReminderDecision.Skip(reason)

internal fun assertTable(cases: List<ReminderCase>) {
    cases.forEach { case ->
        assertEquals(case.expected, ReminderOrchestrator.decide(case.clock, case.signals), case.name)
    }
}
```

Create `client/domain/src/commonTest/kotlin/app/logdate/client/domain/reminders/ReminderWindowTest.kt`:

```kotlin
package app.logdate.client.domain.reminders

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Tests when [ReminderOrchestrator] may ring at all: only with reminders on, only inside the person's
 * waking hours (their average wake time to their average bedtime, falling back to 7:30 to 22:30), and
 * at most once per streak day. A workout just ended in every row, so only these rules decide.
 */
class ReminderWindowTest {
    private val outside = skip(SkipReason.OUTSIDE_WAKING_HOURS)
    private val alreadyReminded = skip(SkipReason.ALREADY_REMINDED_TODAY)
    private val earlyBird = hours(6, 0, 23, 30)
    private val lateRiser = hours(10, 0, 1, 30)
    private val yesterday = day.minus(1, DateTimeUnit.DAY)
    private val springForward = LocalDate(2026, 3, 8)
    private val fallBack = LocalDate(2026, 11, 1)

    private fun case(
        name: String,
        now: Instant,
        expected: ReminderDecision,
        wakingHours: WakingHours = WakingHours.Fallback,
        dayStartHour: Int = 4,
        lastReminderAt: Instant? = null,
        enabled: Boolean = true,
    ) = ReminderCase(name, clock(now, wakingHours, dayStartHour, lastReminderAt), workoutJustEnded(now).copy(enabled = enabled), expected)

    private val wakingHoursTable =
        listOf(
            case("fallback window opens at 7:30", at(7, 30), deliverWorkout),
            case("a minute before the fallback window", at(7, 29), outside),
            case("last minute of the fallback window", at(22, 29), deliverWorkout),
            case("fallback window closes at 22:30", at(22, 30), outside),
            case("an earlier average wake time opens the window", at(6, 15), deliverWorkout, earlyBird),
            case("a later average bedtime leaves the window open", at(23, 0), deliverWorkout, earlyBird),
            case("after the average bedtime", at(23, 45), outside, earlyBird),
            case("a bedtime after midnight extends the window", nextDay(0, 30), deliverWorkout, lateRiser),
            case("after a bedtime past midnight", nextDay(2, 0), outside, lateRiser),
            case("before a late wake time", at(9, 30), outside, lateRiser),
        )

    private val dailyLimitTable =
        listOf(
            case("a reminder earlier in the streak day blocks another", at(15, 0), alreadyReminded, lastReminderAt = at(9, 0)),
            case("a reminder before the day start belongs to the day before", at(9, 0), deliverWorkout, lastReminderAt = at(3, 0)),
            case("a reminder yesterday evening does not block today", at(9, 0), deliverWorkout, lastReminderAt = at(21, 0, yesterday)),
            case("a later day start moves the boundary", at(10, 30), deliverWorkout, dayStartHour = 10, lastReminderAt = at(9, 30)),
            case("after midnight is still the same streak day", nextDay(0, 30), alreadyReminded, lateRiser, lastReminderAt = at(23, 0)),
            case("reminders off", at(12, 0), skip(SkipReason.REMINDERS_OFF), enabled = false),
            case("reminders off outside waking hours", at(3, 0), skip(SkipReason.REMINDERS_OFF), enabled = false),
            case("outside waking hours comes before the daily limit", at(23, 0), outside, lastReminderAt = at(9, 0)),
        )

    private val daylightSavingTable =
        listOf(
            case("the window opens at 7:30 on the day clocks spring forward", at(7, 30, springForward), deliverWorkout),
            case("a minute before the window on the day clocks spring forward", at(7, 29, springForward), outside),
            case("the window closes at 22:30 on the day clocks fall back", at(22, 30, fallBack), outside),
            case("the last minute on the day clocks fall back", at(22, 29, fallBack), deliverWorkout),
        )

    @Test
    fun `reminders ring only inside waking hours`() {
        assertTable(wakingHoursTable)
    }

    @Test
    fun `reminders ring at most once per streak day`() {
        assertTable(dailyLimitTable)
    }

    @Test
    fun `waking hours follow the local clock across daylight saving changes`() {
        assertTable(daylightSavingTable)
    }

    @Test
    fun `known averages are used as they are`() {
        assertEquals(earlyBird, WakingHours.from(LocalTime(6, 0), LocalTime(23, 30)))
    }

    @Test
    fun `a missing average falls back on its own`() {
        assertEquals(hours(7, 30, 23, 30), WakingHours.from(null, LocalTime(23, 30)))
        assertEquals(hours(6, 0, 22, 30), WakingHours.from(LocalTime(6, 0), null))
        assertEquals(WakingHours.Fallback, WakingHours.from(null, null))
    }

    @Test
    fun `equal averages describe no window and fall back entirely`() {
        assertEquals(WakingHours.Fallback, WakingHours.from(LocalTime(8, 0), LocalTime(8, 0)))
    }
}
```

Create `client/domain/src/commonTest/kotlin/app/logdate/client/domain/reminders/ReminderTriggerTest.kt`:

```kotlin
package app.logdate.client.domain.reminders

import app.logdate.client.domain.reminders.ReminderTrigger.ARRIVED_SOMEWHERE_NEW
import app.logdate.client.domain.reminders.ReminderTrigger.CAMPFIRE_EMBERS
import app.logdate.client.domain.reminders.ReminderTrigger.WIND_DOWN
import app.logdate.client.domain.reminders.ReminderTrigger.WORKOUT_ENDED
import app.logdate.client.domain.streak.FirePhase
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Tests what a reminder rings for, inside waking hours on a day without an earlier reminder: a workout
 * that just ended, arriving somewhere new, a campfire about to go out, or the evening wind-down,
 * preferred in that order.
 */
class ReminderTriggerTest {
    private val noon = at(12, 0)
    private val nothing = skip(SkipReason.NOTHING_TO_REMIND)
    private val nothingWritten = quiet.copy(writtenToday = false)
    private val embers = nothingWritten.copy(campfire = FirePhase.EMBERS)

    private fun deliver(
        trigger: ReminderTrigger,
        openMarks: Int = 0,
    ) = ReminderDecision.Deliver(trigger, openMarks)

    private fun case(
        name: String,
        now: Instant,
        signals: ReminderSignals,
        expected: ReminderDecision,
        wakingHours: WakingHours = WakingHours.Fallback,
        dayStartHour: Int = 4,
    ) = ReminderCase(name, clock(now, wakingHours, dayStartHour), signals, expected)

    private val momentTable =
        listOf(
            case("a workout that ended 30 minutes ago", noon, quiet.copy(workoutEndedAt = noon - 30.minutes), deliver(WORKOUT_ENDED)),
            case("a workout that ended 31 minutes ago", noon, quiet.copy(workoutEndedAt = noon - 31.minutes), nothing),
            case("a workout that has not ended yet", noon, quiet.copy(workoutEndedAt = noon + 5.minutes), nothing),
            case("nine minutes at a new place", noon, quiet.copy(newPlaceArrivedAt = noon - 9.minutes), nothing),
            case("ten minutes at a new place", noon, quiet.copy(newPlaceArrivedAt = noon - 10.minutes), deliver(ARRIVED_SOMEWHERE_NEW)),
            case("an arrival time in the future", noon, quiet.copy(newPlaceArrivedAt = noon + 1.minutes), nothing),
        )

    private val eveningTable =
        listOf(
            case("embers with three hours to bedtime", at(19, 30), embers, deliver(CAMPFIRE_EMBERS)),
            case("embers with three hours and a minute to bedtime", at(19, 29), embers, nothing),
            case("embers after something was written", at(21, 15), quiet.copy(campfire = FirePhase.EMBERS), nothing),
            case("embers end with the streak day before bedtime", at(18, 0), embers, deliver(CAMPFIRE_EMBERS), dayStartHour = 21),
            case("embers with a bedtime after midnight", at(22, 30), embers, deliver(CAMPFIRE_EMBERS), hours(10, 0, 1, 30)),
            case("a fire that went out has no campfire reminder", at(20, 0), nothingWritten.copy(campfire = FirePhase.OUT), nothing),
            case("wind down starts 90 minutes before bedtime", at(21, 0), nothingWritten, deliver(WIND_DOWN)),
            case("a minute before the wind down", at(20, 59), nothingWritten, nothing),
            case("streak tracking off still winds down", at(21, 15), nothingWritten.copy(campfire = null), deliver(WIND_DOWN)),
            case("nothing to wind down after writing", at(21, 30), quiet, nothing),
            case("open marks wind down even after writing", at(21, 30), quiet.copy(openMarks = 2), deliver(WIND_DOWN, openMarks = 2)),
            case("a later bedtime moves the wind down", at(21, 45), nothingWritten, nothing, hours(6, 0, 23, 30)),
            case("the wind down before a later bedtime", at(22, 0), nothingWritten, deliver(WIND_DOWN), hours(6, 0, 23, 30)),
        )

    private val everything = embers.copy(openMarks = 1, workoutEndedAt = at(21, 25), newPlaceArrivedAt = at(21, 15))
    private val noWorkout = everything.copy(workoutEndedAt = null)
    private val onlyEvening = noWorkout.copy(newPlaceArrivedAt = null)
    private val onlyWindDown = onlyEvening.copy(campfire = FirePhase.BURNING)

    private val priorityTable =
        listOf(
            case("a workout comes first", at(21, 30), everything, deliver(WORKOUT_ENDED, openMarks = 1)),
            case("an arrival comes before the campfire", at(21, 30), noWorkout, deliver(ARRIVED_SOMEWHERE_NEW, openMarks = 1)),
            case("the campfire comes before the wind down", at(21, 30), onlyEvening, deliver(CAMPFIRE_EMBERS, openMarks = 1)),
            case("the wind down when nothing else applies", at(21, 30), onlyWindDown, deliver(WIND_DOWN, openMarks = 1)),
        )

    @Test
    fun `a moment rings within its own time limits`() {
        assertTable(momentTable)
    }

    @Test
    fun `the evening rings for a fading campfire or the wind down`() {
        assertTable(eveningTable)
    }

    @Test
    fun `one trigger wins in a fixed order and the reminder says how many marks wait`() {
        assertTable(priorityTable)
    }
}
```

- [ ] **Step 2: Add the API surface with inert bodies**

Create `ReminderModels.kt` exactly as in Step 4 except that `WakingHours.from` returns `Fallback`. Create `ReminderOrchestrator.kt` with the four duration constants from Step 4 and `fun decide(clock: ReminderClock, signals: ReminderSignals): ReminderDecision = ReminderDecision.Skip(SkipReason.NOTHING_TO_REMIND)`.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:domain:jvmTest --tests 'app.logdate.client.domain.reminders.*' --console=plain
```
Expected: FAIL. Each table test fails on its first row that expects something other than `NOTHING_TO_REMIND`, named in the message (for example `fallback window opens at 7:30`); `known averages are used as they are` and `a missing average falls back on its own` fail; `equal averages describe no window and fall back entirely` passes.

- [ ] **Step 4: Implement the orchestrator**

`client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/ReminderModels.kt`:

```kotlin
package app.logdate.client.domain.reminders

import app.logdate.client.domain.streak.DEFAULT_STREAK_DAY_START_HOUR
import app.logdate.client.domain.streak.FirePhase
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The local times a person is usually awake, from their average wake time to their average bedtime.
 * A bedtime earlier than the wake time falls after midnight.
 */
data class WakingHours(
    val wake: LocalTime,
    val sleep: LocalTime,
) {
    companion object {
        /** Used for whichever average is not known yet. */
        val Fallback = WakingHours(wake = LocalTime(7, 30), sleep = LocalTime(22, 30))

        /** Waking hours from the averages that are known; equal times describe no window, so they fall back. */
        fun from(
            averageWake: LocalTime?,
            averageSleep: LocalTime?,
        ): WakingHours {
            val hours = WakingHours(wake = averageWake ?: Fallback.wake, sleep = averageSleep ?: Fallback.sleep)
            if (hours.wake == hours.sleep) return Fallback
            return hours
        }
    }
}

/**
 * The moment of a reminder decision and the bounds of the person's day.
 *
 * @property dayStartHour The hour a streak day starts, as the campfire counts it.
 * @property lastReminderAt The later of the two devices' last reminders, as the state documents merge it.
 */
data class ReminderClock(
    val now: Instant,
    val zone: TimeZone,
    val wakingHours: WakingHours = WakingHours.Fallback,
    val dayStartHour: Int = DEFAULT_STREAK_DAY_START_HOUR,
    val lastReminderAt: Instant? = null,
)

/**
 * What is happening, as the signal sources observe it.
 *
 * @property enabled The reminders switch shared with the watch.
 * @property writtenToday Whether anything was logged in the current streak day; a mark counts once it is filled in.
 * @property openMarks Marks still waiting to be filled in.
 * @property campfire Today's fire, or null when streak tracking is off.
 * @property newPlaceArrivedAt When the person arrived at a place new to them, while they are still there.
 * @property workoutEndedAt When the latest workout ended.
 */
data class ReminderSignals(
    val enabled: Boolean,
    val writtenToday: Boolean,
    val openMarks: Int = 0,
    val campfire: FirePhase? = null,
    val newPlaceArrivedAt: Instant? = null,
    val workoutEndedAt: Instant? = null,
)

/** What a reminder rings for, in the order [ReminderOrchestrator] prefers them. */
enum class ReminderTrigger {
    WORKOUT_ENDED,
    ARRIVED_SOMEWHERE_NEW,
    CAMPFIRE_EMBERS,
    WIND_DOWN,
}

enum class SkipReason {
    REMINDERS_OFF,
    OUTSIDE_WAKING_HOURS,
    ALREADY_REMINDED_TODAY,
    NOTHING_TO_REMIND,
}

sealed interface ReminderDecision {
    /** Ring now for [trigger]; [openMarks] lets the reminder add how many marks wait to be filled in. */
    data class Deliver(
        val trigger: ReminderTrigger,
        val openMarks: Int,
    ) : ReminderDecision

    data class Skip(
        val reason: SkipReason,
    ) : ReminderDecision
}
```

`client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/ReminderOrchestrator.kt`:

```kotlin
package app.logdate.client.domain.reminders

import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.nextStreakDayStart
import app.logdate.client.domain.streak.toStreakDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Decides whether a moment-aware reminder may ring now, and what for.
 *
 * Pure: the same clock and signals always give the same decision, so delivery can ask again whenever
 * a signal changes. A reminder rings only with reminders on, only inside the person's waking hours,
 * at most once per streak day across both devices, and for the first of these that applies:
 * 1. a workout that ended within the last [WORKOUT_RECENCY];
 * 2. arriving somewhere new and staying at least [ARRIVAL_DWELL];
 * 3. a campfire in embers with nothing logged today and at most [CAMPFIRE_LAST_STRETCH] left before
 *    bedtime or the end of the streak day, whichever comes first;
 * 4. the evening wind-down, the last [WIND_DOWN_LEAD] before bedtime, when nothing was written today
 *    or marks wait to be filled in.
 */
object ReminderOrchestrator {
    val WORKOUT_RECENCY: Duration = 30.minutes
    val ARRIVAL_DWELL: Duration = 10.minutes
    val CAMPFIRE_LAST_STRETCH: Duration = 3.hours
    val WIND_DOWN_LEAD: Duration = 90.minutes

    fun decide(
        clock: ReminderClock,
        signals: ReminderSignals,
    ): ReminderDecision {
        if (!signals.enabled) return ReminderDecision.Skip(SkipReason.REMINDERS_OFF)
        val window =
            WakingWindow.containing(clock.now, clock.zone, clock.wakingHours)
                ?: return ReminderDecision.Skip(SkipReason.OUTSIDE_WAKING_HOURS)
        if (remindedThisStreakDay(clock)) return ReminderDecision.Skip(SkipReason.ALREADY_REMINDED_TODAY)
        val trigger = firstTrigger(clock, signals, window) ?: return ReminderDecision.Skip(SkipReason.NOTHING_TO_REMIND)
        return ReminderDecision.Deliver(trigger, signals.openMarks)
    }

    private fun remindedThisStreakDay(clock: ReminderClock): Boolean {
        val last = clock.lastReminderAt ?: return false
        return last.toStreakDay(clock.zone, clock.dayStartHour) == clock.now.toStreakDay(clock.zone, clock.dayStartHour)
    }

    private fun firstTrigger(
        clock: ReminderClock,
        signals: ReminderSignals,
        window: WakingWindow,
    ): ReminderTrigger? {
        val now = clock.now
        if (signals.workoutEndedAt.endedWithin(now, WORKOUT_RECENCY)) return ReminderTrigger.WORKOUT_ENDED
        if (signals.newPlaceArrivedAt.lastedAtLeast(now, ARRIVAL_DWELL)) return ReminderTrigger.ARRIVED_SOMEWHERE_NEW
        if (campfireFading(clock, signals, window)) return ReminderTrigger.CAMPFIRE_EMBERS
        if (windingDown(now, signals, window)) return ReminderTrigger.WIND_DOWN
        return null
    }

    private fun campfireFading(
        clock: ReminderClock,
        signals: ReminderSignals,
        window: WakingWindow,
    ): Boolean {
        if (signals.campfire != FirePhase.EMBERS || signals.writtenToday) return false
        val streakDayEnds = nextStreakDayStart(clock.now, clock.zone, clock.dayStartHour)
        return minOf(window.end, streakDayEnds) - clock.now <= CAMPFIRE_LAST_STRETCH
    }

    private fun windingDown(
        now: Instant,
        signals: ReminderSignals,
        window: WakingWindow,
    ): Boolean {
        if (now < window.end - WIND_DOWN_LEAD) return false
        return !signals.writtenToday || signals.openMarks > 0
    }

    /** True when this instant is no later than [now] and at most [limit] before it. */
    private fun Instant?.endedWithin(
        now: Instant,
        limit: Duration,
    ): Boolean {
        if (this == null || this > now) return false
        return now - this <= limit
    }

    /** True when this instant is no later than [now] and at least [minimum] before it. */
    private fun Instant?.lastedAtLeast(
        now: Instant,
        minimum: Duration,
    ): Boolean {
        if (this == null || this > now) return false
        return now - this >= minimum
    }
}

/** The waking hours that contain an instant, as instants, so a bedtime after midnight and DST both work. */
internal data class WakingWindow(
    val start: Instant,
    val end: Instant,
) {
    companion object {
        /** The window that contains [now], or null when [now] is outside waking hours. */
        fun containing(
            now: Instant,
            zone: TimeZone,
            hours: WakingHours,
        ): WakingWindow? {
            val today = now.toLocalDateTime(zone).date
            return listOf(today.minus(1, DateTimeUnit.DAY), today)
                .map { startDate -> startingOn(startDate, zone, hours) }
                .firstOrNull { window -> now >= window.start && now < window.end }
        }

        private fun startingOn(
            date: LocalDate,
            zone: TimeZone,
            hours: WakingHours,
        ): WakingWindow {
            val endDate = if (hours.sleep > hours.wake) date else date.plus(1, DateTimeUnit.DAY)
            return WakingWindow(
                start = LocalDateTime(date, hours.wake).toInstant(zone),
                end = LocalDateTime(endDate, hours.sleep).toInstant(zone),
            )
        }
    }
}
```

- [ ] **Step 5: Run the tests again**

Run the Step 3 command again.
Expected: PASS, 9 tests (6 in `ReminderWindowTest`, 3 in `ReminderTriggerTest`) covering 45 table rows.

- [ ] **Step 6: Compile for iOS and run the module checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:domain:compileKotlinIosSimulatorArm64 :client:domain:compileTestKotlinIosSimulatorArm64 :client:domain:iosSimulatorArm64Test --tests 'app.logdate.client.domain.reminders.*' :client:domain:jvmTest :client:domain:ktlintCheck :client:domain:detekt --console=plain
```
Expected: BUILD SUCCESSFUL. (The iOS test run confirms `TimeZone.of("America/Denver")` and the DST rows on Kotlin/Native.)

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task10-reminder-orchestrator.txt <<'EOF'
chore(reminders): decide when a moment-aware reminder may ring

Moment-aware reminders replace fixed morning and evening prompts with
one reminder a day at a moment that matters. This adds the decision on
its own, without delivery: given the time, the person's waking hours
and what is happening, may a reminder ring now, and for what.

A reminder rings only with reminders on, only between the person's
average wake time and average bedtime (7:30 to 22:30 until those are
known), and at most once per streak day across both devices. It rings
for the first that applies: a workout that ended in the last 30
minutes, arriving somewhere new and staying 10 minutes, a campfire in
embers with nothing logged and 3 hours or less left before bedtime or
the end of the streak day, or the last 90 minutes before bedtime when
nothing was written or marks wait to be filled in. Table-driven tests
cover each boundary, bedtimes after midnight, and both daylight saving
changes.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/ReminderModels.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/ReminderOrchestrator.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/reminders/ReminderTestFixtures.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/reminders/ReminderWindowTest.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/reminders/ReminderTriggerTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task10-reminder-orchestrator.txt
```

---
## Self-Review

**Spec coverage.** Every remaining Track C wave 1 item and R1 maps to a task:

| Item | Task | Covered by |
|---|---|---|
| C12 neutral fallback replacing `Purple*`/`Pink*` in `client/theme` `Color.kt`, updated desktop baselines, "unflagged" | 1 | Graphite palettes, full 48-role light and dark schemes, `FallbackColorSchemeTest` (neutrality, WCAG AA pairs), every desktop baseline re-recorded, Android previews confirmed unchanged |
| C2 `SharedPreferenceKey`, `StampedPreferenceStore`, `WatchPreferencesRepository` in `client/domain/.../watch/` on top of `StateDocument.shared` | 2 | The three types, `StateMerger.mergeStamped` (same rule as the documents), `PhoneStatePublisher` publishing `shared` and `prefs.v1` and adopting the watch's later entries |
| C3b sealed `WatchStatus` (`Unsupported`, `NoWatch`, `NotInstalled`, `SettingUp`, `Ready(link, sync, window, capabilities)`, `NeedsAttention`) and `WatchStatusResolver` replacing the hard-coded values; lines it cannot fill stay hidden | 3, 4 | Model, resolver, `PeerStateWatchSnapshotSource`, view model `status` (3); `lastSynced`/`pendingCount` removed, the Watch page renders `WatchStatus`, screen test, two new scenes, GMD flow (4) |
| D1 `capturedOn` end to end: Room migration (53 → 54, verified), mappers, encrypted sync payload, export and restore | 5, 6, 7 | `CaptureDevice` and its lenient serializer, `MIGRATION_53_54` with a Room-validated JVM test, mappers, ingest stamping, conflict and comparison rules (5); `LDSE2` field on all three DTOs, server pass-through and docs, client-server e2e (6); both archive versions, schema, round trips including archives without the field and with an unknown value (7) |
| D1 `CapturedOnWatchGlyph` in moment rows, audio snippets, journal detail, editor banner (`feat(timeline)`) | 8 | `MomentHeader` in the three moment containers, `AudioNoteSnippet`, `NotesListSection` time lines, journal card shells, `CapturedOnWatchBanner` in the editor; gated by `WATCH_EXPERIENCE_V2` at the navigation root; one scene with four baselines |
| D2 `observeOpenMarks`, `EnrichMarkLocationUseCase` (±10 min, ≤100 m, only with `capture.places`) | 9 | `MarkedMomentRepository.observeOpenMarks`, the Room-backed repository, a conditional `fillLocationIfMissing` query, the use case and its eight cases |
| R1 pure `ReminderOrchestrator` in `client/domain/.../reminders/`: waking window (averages, falling back to 07:30–22:30), once per streak day, arrival dwell ≥10 min, workout within 30 min, wind-down (nothing written or open marks), campfire EMBERS with nothing logged and ≤3 h left; exhaustive table-driven tests | 10 | `ReminderOrchestrator.decide`, `WakingHours.from`, 45 table rows across window, daily limit, daylight saving, each trigger's boundaries and the trigger order |

**Out of scope here, by design.** The Watch page redesign, device card and diagnostics (C4–C6), the onboarding watch step (C8), signal sources and delivery for reminders (R2, R3), the reminders opt-in (R4), marks in the timeline (D3–D6), and the watch side of shared preferences (B6). The watch app stamping its own notes as captured on the watch belongs to Track B, when the watch writes notes through the shared engine.

**Decisions made in this plan (each can be changed before execution).**
- Room 54, because `main` is at 53 (`LogDateDatabase.kt:228`, `MIGRATION_52_53` newest). The master plan's `mark_photo_decisions` table moves to D4, its first user.
- D1 is four commits rather than the single template commit (`493080bf4`): storage, sync, archives, then the visible glyph. Each is shippable and data-safe on its own; only the last is `feat(timeline)`.
- Notes written on the phone stay `capturedOn = null`. The note model is shared with desktop and iOS, where "phone" would be wrong, and only `WATCH` is shown. The phone stamps `WATCH` only on notes that arrive from the watch without a device.
- The capture device is excluded from the "same persisted content" check and from the uploaded-fields comparison, like the capture time zone, so an older server that drops it never turns an acknowledged upload into a conflict.
- The device syncs as its own encrypted field (`sync:note:<id>:capturedOn`), the scheme captions and places already use, rather than a new combined payload.
- Archives write `"phone"`/`"watch"` as a plain string (not a closed enum in the schema) and keep schema version 2.0, as `photoPresentation` did, so a newer device name never makes an older reader reject a notes file.
- `WatchStatus` uses `null` for "still looking" instead of a seventh variant; `Unsupported` exists for the non-Android device card, and the Android resolver never returns it; `SettingUp` carries a sealed step so the pairing prompt never shows an empty watch name.
- Status lines follow the feature that makes the watch's number meaningful: the sync line and "moments didn't send" need `receipts.v1`, the library line needs `window.v1`, the link line and "set up again" need `link.cloud.v1`. Today's watch publishes defaults and lists no features, so after Task 4 a connected watch shows only its name and "Connected", which is the fix.
- `NeedsAttention` adds no new control: "Sync Now" and Troubleshooting already work, and nothing else would yet.
- Shared preferences start with six keys (`reminders.enabled`, `capture.places`, `capture.moodAfterRecording`, `window.days`, `window.onThisDay`, `window.rewind`). `capture.places` and reminders default off; `window.days` defaults to 14 and accepts only 7, 14 and 30. The audio download policy, haptics, hold to talk and microphone keys arrive with their screens (C4, B6).
- The phone always publishes its shared preferences and lists `prefs.v1`, flag or not; it changes its version 2 capability only when the Labs switch changes, not on every preference change.
- `StampedPreferenceStore` is bound in `domainModule` with the phone role; `:app:wear` does not include that module and binds its own in B6.
- Enrichment writes the place back to the mark and moves its `lastUpdated`, only while the mark has no latitude and is not deleted, and only from genuine, non-mock samples.
- R1 prefers workout, then arrival, then campfire, then wind-down; the wind-down is the last 90 minutes before bedtime; the campfire's "3 hours left" counts to bedtime or the end of the streak day, whichever is first (with the default 4 AM day start, "3 hours before the day ends" would always fall outside waking hours); an arrival has no upper age, because R2 clears it when the person leaves; `Deliver` carries the open mark count for D6's line.
- The fallback palette is a cool graphite (CIELAB hue 265°, tertiary 205°) with Material's error roles unchanged and every fixed role set, so no baseline purple remains. It is `feat(theme)` because desktop, iOS, Android 11 and dynamic-color-off users see it.
- The provenance glyph is gated once, at `LogDateNavDisplay`, through `LocalWatchProvenanceVisible`; `EditorActivity` (Android's separate editor window) provides nothing and so shows no banner.
- A moment row shows the glyph only when every note behind it came from the watch; a voice note in a day's details shows it for that note alone.
- To keep files at 500 lines or fewer, the Watch page's status card moves to `WatchStatusCard.kt`, the ingest test doubles move to `WatchIngestorTestDoubles.kt`, the watch scenes move to `WatchSettingsScreenshotScenes.kt`, and the journal note mapping moves to `EntryDisplayMapping.kt` (which also makes it testable).

**Assumed, and to confirm while executing.**
- `darkColorScheme` in Material 3 `1.11.0-alpha07` takes the same named fixed-role parameters as `lightColorScheme` (checked for `lightColorScheme` only, from the jar's `LocalVariableTable`; the `ColorScheme` getters list the same roles).
- A `KSerializer<CaptureDevice?>` on a nullable property through `@Serializable(with = …)` compiles on every target, with `encodeNull`, `decodeNotNullMark` and `decodeNull` behind `ExperimentalSerializationApi`.
- kotlinx JSON tree decoding reports a type mismatch as `IllegalArgumentException` (including `SerializationException`) or `IllegalStateException`; `SharedPreferenceKey.read` catches both.
- Room KSP accepts a `@Query` built from two concatenated constant strings.
- Layoutlib renders Android previews with dynamic color, so Task 1 leaves `app/android-main/src/screenshotTestDebug/reference` untouched.
- The client-server harness stores entries in the repo-backed collections, as `PhotoPresentationClientServerE2ETest` relies on.
- Context7's quota was exhausted during planning, so library details were checked against the cached jars and the repository's own usage instead of current docs.

**Checked rather than assumed.** Room is at 53 with `53.json`; the four note tables' columns and `NOT NULL`s come from `53.json`. `lightColorScheme`'s 48 parameter names come from the jar. Every palette role's channel spread (at most 30) and each tested pair's contrast (at least 5.5:1) were computed independently. There are 329 desktop baselines and 20 quarantined `memory-selection-*` ones. `JournalNote.serializer()` is used only by `NoteDataMapper` (`ignoreUnknownKeys`, `encodeDefaults`); the server's JSON and the client's HTTP JSON both ignore unknown keys; no Swift code constructs `JournalNote`. `SchemaDocumentationTest` fails for an undocumented DTO field; `ArchiveSchemaValidationTest` validates the maximal sample against a strict copy; restore does not verify checksums, so the compatibility test can rewrite `data/notes.json`. `FakeKeyValueStorage` and `RoundTripLocationHistoryRepository` are reachable from `:client:domain` `commonTest`; `watchSettingsModule` is Android-only; `LogDateNavDisplay` already reads a flag into a composition local; `:app:wear` does not include `domainModule`. The Gradle tasks named in this plan (`desktopTest`, `jvmTest`, `testAndroidHostTest`, `compile*IosSimulatorArm64`, `iosSimulatorArm64Test`, `updateDesktopScreenshotTest`, `validateDebugScreenshotTest`, `smokeDevicesGroupDebugAndroidTest`, `ktlintCheck`, `detekt`) were listed from the build.

**Known limits, not fixed here.**
- Until the watch publishes real counts and lists `receipts.v1`, `window.v1` or `link.cloud.v1`, no status line or attention state can appear; the model and screen are ready for it.
- The watch does not read the phone's shared preferences yet (B6), so preferences flow phone to watch only once the watch side lands.
- The 20 `memory-selection-*` desktop baselines keep the old palette because the screenshot test skips that family in both validation and update.
- `SharedScreenshotCatalog.kt`, `SyncHelpers.kt`, `SyncSchemaDocs.kt`, `ExportUserDataUseCase.kt`, `RestoreUserDataUseCase.kt`, `ExportImportRoundTripTest.kt` and `EntryEditorViewModel.kt` stay over 500 lines; each receives only the lines its field needs.
- The legacy sync-table store and the AT Protocol record write path on the server do not carry `capturedOn`, the same as `photoPresentation` today.
- The timeline glyph is covered by unit, desktop Compose and screenshot tests, not by a managed-device test.

**Placeholder scan.** No step says TBD, TODO, "add error handling" or "similar to Task N". Every code step shows its code or names the exact text to replace and its replacement. Inert bodies exist only in each task's Step 2 (described precisely, with the final code in Step 4), so each test fails on behavior; Task 4's first red is a compile error because the task changes the screen's input type, and Task 9's DAO test passes from Step 2 because Room needs the real query. The one generated artifact, `54.json`, is checked against the four `captured_on` columns.

**Name consistency.** `SharedPreferenceKey.WindowDays`/`CapturePlaces`, `StampedPreferenceStore.observe/snapshot/write/adopt` and `WatchPreferencesRepository.observe/get/set` (Task 2) are used unchanged in Tasks 3 and 9. `WatchStatus`, `WatchSetupStep.PairWithPhone/ConfirmOnPhone`, `WatchLinkLine.THROUGH_PHONE/ON_ITS_OWN`, `WatchSyncLine(waiting)`, `WatchWindowLine(days)`, `WatchIssue.SetUpAgain/CapturesNotSent(count)`, `WatchPeerSnapshot` and `WatchPeerSnapshotSource` (Task 3) are rendered unchanged in Task 4. `CaptureDevice.fromWireName/wireName`, `JournalNote.capturedOn`, `isCapturedOnWatch` and `withCapturedOn` (Task 5) are used unchanged in Tasks 6, 7 and 8. `sharedScene` and `standardMatrixVariants` become `internal` in Task 4 and are used in Task 8. `MarkLocation(latitude, longitude, accuracyMeters)` and `MarkedMomentRepository.fillLocationIfMissing(id, location, updatedAt)` match between Task 9's repository, use case and tests. `ReminderClock`, `ReminderSignals`, `ReminderTrigger`, `SkipReason` and `ReminderDecision.Deliver/Skip` match between Task 10's code and its fixtures.

**Commits.** Ten Conventional Commits, titles 50–68 characters: `feat(theme)`, `chore(settings)` ×2, `fix(settings)`, `chore(database)`, `chore(sync)`, `chore(export)`, `feat(timeline)`, `chore(timeline)`, `chore(reminders)`, every scope in `allowed-scopes.txt`. `feat`/`fix` are used only for the palette, the Watch page's status and the watch glyph, and their bodies name no classes. Every body line is at most 72 characters and ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; every commit is the one `git restore --staged . && git add <paths> && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F <file>` chain after its message file is written in a separate step, and its paths match the task's file list.
