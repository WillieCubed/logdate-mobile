# Activity awareness, step 2: activity context on captured moments — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every note and marked moment captured while activity awareness is on records what the person was doing, moving or in a vehicle. The record is stored, synced encrypted, carried in archives, and shown as "While moving" or "In a vehicle" next to where the moment was captured.

**Architecture:** A new `CaptureActivity` value travels exactly the way `capturedOn` (`CaptureDevice`) already does: a nullable Room column, an entity↔model mapping, an encrypted cloud field the server stores opaquely, the watch's note JSON, archives and restore. Capture sites read the agreed activity from `ActivityTracker` when they save.

**Tech Stack:** Kotlin Multiplatform, Room, kotlinx.serialization, Ktor server, Compose.

**Spec:** `docs/superpowers/specs/2026-10-16-activity-awareness-design.md` ("Activity context on captured moments"). **Prior art:** `docs/superpowers/plans/2026-10-10-phone-companion-foundations.md`, Tasks 5–8, which built `capturedOn` through the same layers. Every file below has a `capturedOn` line to copy.

## Before starting

- **Database version.** This plan takes version 55 (`MIGRATION_54_55`). The stickers branch renumbers its own migration to 55→56 when it rebases onto a `main` with this one. The tasks below write `N` for this version and `N-1` for the one before it.
- **Step 1 is merged.** `ActivityTracker`, `AgreedActivity` and `FeatureFlag.ACTIVITY_AWARENESS` exist.

## Global Constraints

- Stored values are `"moving"` and `"in_vehicle"`. Sleep is never stamped; a capture made while the watch reports sleep has no activity.
- Nothing is stamped while `ACTIVITY_AWARENESS` is off, and nothing is shown either.
- A value this version does not know reads as null everywhere: the database, sync, the watch's JSON and archives.
- Existing notes stay null after the migration; their activity was never recorded.
- UI copy: "While moving" and "In a vehicle". Logging is Napier only.

## File structure

| Layer | Files (each has a `capturedOn` line to copy) |
| --- | --- |
| Value | Create `client/repository/src/commonMain/kotlin/app/logdate/client/repository/journals/CaptureActivity.kt` |
| Model | `client/repository/.../journals/JournalNotesRepository.kt`, `client/repository/.../moments/MarkedMoment.kt` |
| Room | `client/database/.../entities/{Text,Image,Audio,Video}NoteEntity.kt`, `GenericNoteData.kt`, `entities/moments/MarkedMomentEntity.kt`, `LogDateDatabase.kt`, new `migrations/MIGRATION_{N-1}_{N}.kt`, `schemas/.../{N}.json` |
| Mappers | `client/data/.../notes/NoteObjectMappers.kt`, `client/data/.../moments/OfflineFirstMarkedMomentRepository.kt` |
| Sync | `client/sync/.../cloud/CloudContentDataSource.kt`, `SyncDownloader.kt`, `conflict/JournalNoteConflictResolver.kt`, `crypto/MarkedMomentPayloadCodec.kt`, `peer/delivery/MomentEnvelopeBody.kt`, `shared/model/.../sync/SyncModels.kt` |
| Server | `server/.../logdate/LogDateCollectionsRepository.kt`, `LogDateCollectionRecords.kt`, `routes/sync/SyncCollectionRoutes.kt`, `routes/sync/SyncHelpers.kt`, `openapi/schemadocs/SyncSchemaDocs.kt` |
| Archives | `client/domain/.../export/{ExportModels,ExportUserDataUseCase}.kt`, `export/archive/{ArchiveData,ArchiveRecords,ArchiveSchemas}.kt`, `restore/{RestoreNoteDecoder,V2RestoreBundle}.kt` |
| Stamping | `app/wear/.../recording/AudioMomentSaver.kt`, `capture/QuickNoteWriter.kt`, `presentation/quicktext/QuickTextHandler.kt`, `presentation/mood/MoodCheckInViewModel.kt`, `v2/mark/MarkMomentUseCase.kt`, `client/feature/editor/.../mapper/NoteMappers.kt`, `client/domain/.../moments/MarkedMomentEditorContext.kt` |
| Display | `client/ui/.../provenance/CapturedOnWatch.kt`, `client/feature/core/.../main/HomeTimelineMapping.kt`, `client/feature/timeline/.../TimelineViewModel.kt`, `client/ui/.../timeline/HomeTimelineUiState.kt`, the journal detail cards, `client/screenshot-scenes/.../ProvenanceScreenshotScenes.kt` |

---

### Task 1: The value and the model fields

**Files:** create `CaptureActivity.kt`; modify `JournalNotesRepository.kt` and `MarkedMoment.kt`. **Test:** `client/repository/src/commonTest/.../CaptureActivityTest.kt`.

- [ ] **Step 1: Write the failing test.** `CaptureActivity.fromWireName("moving") == MOVING`, `fromWireName("swimming") == null`, and a `JournalNote.Text` with `capturedDuring = IN_VEHICLE` round-trips through `Json` while `{"capturedDuring":"swimming"}` decodes to null.
- [ ] **Step 2: Run it and confirm it fails.** `./gradlew :client:repository:jvmTest --tests '*CaptureActivityTest*'`
- [ ] **Step 3: Implement**, copying `CaptureDevice.kt`:

```kotlin
/** What the person was doing when a moment was captured. [wireName] is stored everywhere and never changes. */
enum class CaptureActivity(val wireName: String) {
    MOVING("moving"),
    IN_VEHICLE("in_vehicle"),
    ;

    companion object {
        fun fromWireName(wireName: String?): CaptureActivity? = entries.firstOrNull { it.wireName == wireName }
    }
}

object LenientCaptureActivitySerializer : KSerializer<CaptureActivity?> // as LenientCaptureDeviceSerializer

fun JournalNote.withCapturedDuring(capturedDuring: CaptureActivity?): JournalNote // as withCapturedOn
```

  Add `abstract val capturedDuring: CaptureActivity?` to `JournalNote`, and `@Serializable(with = LenientCaptureActivitySerializer::class) override val capturedDuring: CaptureActivity? = null` to each of the four subtypes, beside `capturedOn`. Add `val capturedDuring: CaptureActivity? = null` to `MarkedMoment`.
- [ ] **Step 4: Run and confirm it passes**, plus `:client:repository:compileKotlinIosSimulatorArm64`.
- [ ] **Step 5: Commit** with Task 2.

### Task 2: Room column, migration and mappers

**Files:** the four note entities, `GenericNoteData.kt`, `MarkedMomentEntity.kt`, `LogDateDatabase.kt` (version `N`, register the migration in `databaseMigrations()`), `MIGRATION_{N-1}_{N}.kt`, the exported schema, `NoteObjectMappers.kt`, `OfflineFirstMarkedMomentRepository.kt`. **Test:** copy `client/database/src/jvmTest/.../CapturedOnMigrationJvmTest.kt` to `CapturedDuringMigrationJvmTest.kt`, and `client/data/src/commonTest/.../CapturedOnMappingTest.kt` to `CapturedDuringMappingTest.kt`.

- [ ] **Step 1: Write the failing tests**: rows survive the migration with null; re-running it is safe; a model maps to an entity and back with the value; an unknown stored name maps to null.
- [ ] **Step 2: Run and confirm failure.** `./gradlew :client:database:jvmTest --tests '*CapturedDuring*' :client:data:jvmTest --tests '*CapturedDuring*'`
- [ ] **Step 3: Implement.** `@ColumnInfo(name = "captured_during") val capturedDuring: String? = null` on each entity. The migration copies `MIGRATION_53_54`, its `hasColumn` guard included, over `text_notes`, `image_notes`, `video_notes`, `audio_notes` and `marked_moments`. The mappers use `CaptureActivity.fromWireName(capturedDuring)` and `capturedDuring?.wireName`. Export the schema with `./gradlew :client:database:kspKotlinJvm`.
- [ ] **Step 4: Run the database and data tests in full.**
- [ ] **Step 5: Commit** `chore(timeline): Store what someone was doing when they captured a moment`.

### Task 3: Sync and the server

**Files:** as listed under Sync and Server. **Tests:** copy `CapturedOnSyncTest.kt`, `conflict/JournalNoteCaptureDeviceConflictTest.kt`, `NoteDataMapperCapturedOnTest.kt` and `integration/server-client-e2e/.../CapturedOnClientServerE2ETest.kt`.

- [ ] **Step 1: Write the failing tests**: the value is encrypted under `"sync:note:$noteId:capturedDuring"` and decrypts back; an unknown decrypted value is null; a conflict keeps whichever copy has a value; the server stores and returns the encrypted value and keeps it on an update that omits it; a marked moment payload with and without the field decodes.
- [ ] **Step 2: Run and confirm failure.** `./gradlew :client:sync:desktopTest --tests '*CapturedDuring*' :server:test --tests '*Sync*' :integration:server-client-e2e:test --tests '*CapturedDuring*'`
- [ ] **Step 3: Implement**, beside each `capturedOn` line: `capturedDuring: String? = null` on `ContentUploadRequest`, `ContentUpdateRequest` and `ContentChange`; encrypt and decrypt helpers in `CloudContentDataSource`; `capturedDuring = null` in `SyncDownloader.uploadedFields`; a `CaptureFacts` fallback in the conflict resolver; `LogDateEntry.capturedDuring` with the repo JSON, the routes (`req.capturedDuring ?: existing?.capturedDuring`) and the schema docs; a nullable `capturedDuring` in the marked moment `Payload` and `MomentEnvelopeBody`.
- [ ] **Step 4: Check the watch's library fingerprint.** `WindowNoteFingerprint` hashes the full note JSON with nulls written out, so a new null field changes every note's fingerprint once and resends the watch's library. Leave the field out of the JSON while it is null (`@EncodeDefault(EncodeDefault.Mode.NEVER)`), and add a test that a note without an activity keeps its fingerprint.
- [ ] **Step 5: Run the sync, server and end-to-end tests in full, then commit** `chore(sync): Sync what someone was doing when they captured a moment`.

### Task 4: Archives and restore

**Files:** as listed under Archives. **Test:** copy `CapturedOnArchiveCompatibilityTest.kt`.

- [ ] **Step 1–4:** an archive written with the value restores it; an older archive without it restores null; `withExistingCaptureMetadata` keeps an existing value. Run `./gradlew :client:domain:jvmTest --tests '*Archive*' --tests '*Restore*'`.
- [ ] **Step 5: Commit** `chore(export): Keep activity context in archives and restores`.

### Task 5: Stamping captures

**Files:** as listed under Stamping, plus `client/domain/.../notes/AddNoteUseCase.kt` (every new phone note goes through it) and `AttachPhotoToMarkUseCase`/`SaveMarkCaptionUseCase`. Add `CurrentCaptureActivity` to `client/domain/.../activity/`, behind a `CaptureActivitySource` interface that each capture site takes, defaulting to `CaptureActivitySource.None`:

```kotlin
/** What a capture made now records: the walk, ride or trip under way, or the one whose suggestion was acted on in the last 30 minutes. */
class CurrentCaptureActivity(
    private val agreed: Flow<AgreedActivity>,
    private val enabled: Flow<Boolean>,
    private val outcomes: Flow<List<SuggestionOutcome>>,
    private val clock: Clock = Clock.System,
) : CaptureActivitySource
```

A moment started from Home's activity suggestion or the watch's Today card is stamped with that ended activity through the suggestion's ACTED_ON outcome, so no route carries it. A lookup that takes longer than two seconds records nothing, so a capture never waits on it.

- [ ] **Step 1: Write the failing tests**: the watch's `AudioMomentSaver` and `QuickNoteWriter` stamp the current activity; `MarkMomentUseCase` stamps the mark, but not a mark saved more than two minutes after it was made; `AddNoteUseCase` stamps new notes captured now, never a note that already names one, and never a note written about another time (a visit or a mark); `withMarkedCapture` copies the mark's value onto the notes that fill it; a capture right after acting on a suggestion is stamped with that activity; nothing is stamped with the flag off.
- [ ] **Step 2: Run and confirm failure.** `./gradlew :app:wear:testDebugUnitTest :client:domain:jvmTest`
- [ ] **Step 3: Implement**, injecting the source beside each site's time zone stamp. Recovery (`WearRecordingRecovery`) does not stamp, because the activity at recovery time is not the activity at capture time.
- [ ] **Step 4: Run the tests, then the Wear emulator suite** (`./gradlew :app:wear:wearDevicesGroupDebugAndroidTest`).
- [ ] **Step 5: Commit** `chore(reminders): Record the activity a moment was captured during`.

### Task 6: Showing it

**Files:** as listed under Display.

- [ ] **Step 1: Write the failing UI tests**: a note captured while moving shows "While moving" beside its time in the timeline's day detail and the journal detail, and a Home moment whose notes share an activity shows it after its label; a note with no activity shows nothing new; nothing shows while the flag is off. Rewind shows the same surfaces as the watch glyph, which it does not carry: its panels come from stored Rewind content, not from the notes.
- [ ] **Step 2: Run and confirm failure.** `./gradlew :client:ui:desktopTest :client:feature:timeline:desktopTest :client:feature:journal:desktopTest :client:feature:core:desktopTest`
- [ ] **Step 3: Implement.** Extend `CaptureTimeLabel` with an optional activity, add `LocalActivityContextVisible` provided from `LogDateNavDisplay` beside `LocalWatchProvenanceVisible`, and carry `capturedDuring` through the UI state mappers. Add the strings `captured_while_moving` ("While moving") and `captured_in_vehicle` ("In a vehicle") to `client/ui` resources.
- [ ] **Step 4: Extend `ProvenanceScreenshotScenes`** with a moment captured while moving. Inspect the renders at phone, tablet, dark, RTL and 200% text, and have CI record the references (`render-host.txt`).
- [ ] **Step 5: Commit** `feat(timeline): Show what you were doing when you captured a moment`, behind the flag.

## Self-review

- **Spec coverage:** storage, sync, display and the unknown-value rule are covered, plus stamping at every capture site the spec names: `AudioMomentSaver`, `QuickNoteWriter`, `MarkMomentUseCase` and the phone editor.
- **Database version:** 55, agreed with the stickers work, which moves to 56.
- **Type names:** `CaptureActivity`, `LenientCaptureActivitySerializer`, `withCapturedDuring`, `CurrentCaptureActivity`, `capturedDuring` / `captured_during` throughout.
