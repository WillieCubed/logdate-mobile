# Watch Recorder and Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Track B steps B3 (Recorder) and B4 (Capture) of `docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md` for the new watch app: hold to talk, slide up to lock and tap for a locked recording on Today's Record edge button, the live recording with its saved, too-short and failed states, the Ongoing Activity on the watch face, the ambient timer, permissions asked at the point of use, Mark the moment from Today and from outside the app, and the + sheet, all dark behind `FeatureFlag.WATCH_EXPERIENCE_V2`.

**Architecture:** The v1 recorder's save and undo steps move into `AudioMomentSaver` and `AfterSaveController`, which an activity-scoped `RecorderViewModel` (Ready, Arming, Live, Saving, Saved, TooShort, Failed) shares with it, while a pure `RecordGestureMachine`, fed by `Modifier.recordGesture` from the Initial pointer pass, decides tap, hold, lock and yield on Today's Record edge button. Permissions go through a small `v2/permissions` layer that asks at the point of use; the recording reaches the watch face through a new `RecordingNotificationDecorator` hook in `client/media` that the watch's `Application` supplies; ambient mode uses `rememberAmbientModeManager()` only where the Wear SDK ambient service exists. Marks are saved by `MarkedMomentStore` (the row, a `MOMENT` outbox entry and a `peer_delivery` row) through `MarkMomentUseCase`, which adds the place and an 8 s `MeasureClient` heart rate reading in the foreground and is reached from Today and from `MarkMomentReceiver`. The + sheet saves text (RemoteInput) and feelings through `QuickNoteWriter` and opens the existing remote camera screen.

**Tech Stack:** Kotlin 2.3.21, AGP 9.2.1, Wear Compose Material 3 / Foundation / Navigation 3 1.7.0, Navigation 3 1.1.1, `wear-ongoing` 1.1.0, `wear-input` 1.2.0, `androidx.wear:wear` 1.4.0, `health-services-client` 1.1.0, Compose UI 1.11 (BOM 2026.05.00), Room 2.8.4, Koin 4.2.1, kotlinx.coroutines, kotlinx-datetime 0.7, JUnit 4 + kotlin.test + MockK + Turbine (unit), Compose UI test v2 rules on the `wearSmallRoundApi34` Gradle Managed Device, the Android screenshot plugin.

## Global Constraints

- Prerequisites (checked in Task 0): the design system, shell and Today plan (`docs/superpowers/plans/2026-10-03-wear-design-system-shell-and-today.md`) is fully landed, `MarkedMomentDao`/`PeerDeliveryDao` exist at Room version 53, and `wear-ongoing`, `wear-input`, `androidx.wear:wear` and `health-services-client` are on `:app:wear`'s classpath.
- Flag: everything under `app/wear/src/main/kotlin/app/logdate/wear/v2/` is reachable only through `WearExperience.V2`; the two entry points outside it, the Ongoing Activity decorator and `MarkMomentReceiver`, check WATCH_EXPERIENCE_V2 themselves.
- v1 stays as it is: `WearRecordingViewModel` keeps its constructor, constants and behavior, and `WearRecordingViewModelTest.kt` (63 tests) is not edited in any task.
- No dead controls: Record is set in `TodayControls` in Task 5, Mark in Task 9 and + in Task 10, each in the commit that makes it work.
- Shared code is additive: the `client/media` hook does nothing unless the `Application` implements `RecordingNotificationDecorator.Provider`, which only the watch does.
- No new server behavior and no Room migration (Room stays at version 53); marks only enter the existing outbox and ledger tables.
- Logging: Napier only. IDs: `kotlin.uuid.Uuid`. Time: `kotlin.time.Clock`/`Instant` (what `JournalNote` and the Room entities use) and `kotlinx.datetime.TimeZone`. DI: Koin constructor injection; new bindings go in `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`.
- Imports: ASCII order with `java.*`, `javax.*`, `kotlin.*` last, no wildcards; ktlint max line length 140; run `:app:wear:ktlintFormat` before `:app:wear:ktlintCheck` when the check fails.
- Code shape: early returns, no comments that restate the code, detekt `LongMethod` 60 lines outside `@Composable`, `LargeClass` 600 lines, nothing added to `app/wear/detekt-baseline.xml`.
- Copy: literal, never "we", from string resources (`strings_recorder.xml`, `strings_capture.xml`), no vague metaphor verbs ("kept", "held", "carried") in copy, names, KDoc or commit text.
- Gradle: every command starts with `export ANDROID_HOME=$HOME/Library/Android/sdk &&` and runs from `/Users/williecubed/Projects/TheHypertextStudio/logdate-mobile/.claude/worktrees/backup-sync-infinite-notification-dd4c3a`.
- Devices: Gradle Managed Device only, `./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fqcn>[,<fqcn>]`. Never `connected*AndroidTest`, `install*` or `adb`, and never start, stop or kill an emulator by hand. A run that prints `Starting 0 tests` then `Process crashed` while `uptime` shows a load average above 100 is host load; rerun it.
- Screenshots: every new state gets references at small round, large round and 1.24 font scale (`@WearAllSizesPreview`, added in Task 5). References live in `app/wear/src/screenshotTestDebug/reference/` and change only in the commit whose code changes their pixels; previews use `LogDateWearTheme(dynamicColor = false)`.
- Commits: the message file is written with a heredoc in its own step to `/private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/<name>.txt`; the next step runs `git restore --staged . && git add <exact paths> && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F <file>` as one chain. Never `git add -A`, `git add .`, `--no-verify` or `-m`.
- Commit messages: Conventional Commits, title at most 72 characters, body lines at most 72, type `chore` (dark behind the flag) except Task 1's `refactor`, never `style`, scope `wear` (never `build`, `android`, `ios`, `desktop` or `infra`), no "phase" with a number, last line `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

---

## File Structure

### Shared (`client/media`)

| Path | Task | Responsibility |
|---|---|---|
| `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/RecordingNotificationDecorator.kt` | 6 | The decorator hook, its `Provider` and `RecordingNotificationState`. |
| `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioNotificationHandler.kt` (modified) | 6 | Runs the decorator before building the recording notification. |
| `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AudioRecordingService.kt` (modified) | 6 | Passes the recorded time with each notification update. |

### Watch (`app/wear/src/main/kotlin/app/logdate/wear/`)

| Path | Task | Responsibility |
|---|---|---|
| `recording/AudioMomentSaver.kt` | 1 | A finished recording becomes an audio note, too short, or a failed save. |
| `recording/AfterSaveController.kt` | 1, 4 | The undo window, and from Task 4 the four seconds of feelings. |
| `presentation/recording/WearRecordingViewModel.kt` (modified) | 1 | v1 recorder, now saving and undoing through the two classes above. |
| `recording/WearRecordingRecovery.kt` (modified) | 1 | Reads the minimum length from `AudioMomentSaver`. |
| `v2/recorder/gesture/RecordGestureMachine.kt` | 2 | The pure gesture rules: phases, events, signals. |
| `v2/recorder/gesture/RecordGestureListener.kt` | 2 | What the gesture tells the recorder. |
| `v2/recorder/gesture/RecordGestureModifier.kt` | 2 | `Modifier.recordGesture`: pointer input and accessibility. |
| `v2/permissions/WatchPermission.kt` | 3, 9 | The runtime permissions the new app asks for. |
| `v2/permissions/PermissionPrompts.kt` | 3 | `PermissionChecker`, `PermissionPromptStore`, `PermissionPrompts`. |
| `v2/permissions/PermissionRequester.kt` | 3 | `rememberPermissionRequester` and `PermissionResult`. |
| `capture/QuickNoteWriter.kt` | 4, 10 | Saves a feeling (Task 4) or a line of text (Task 10) as a note. |
| `v2/recorder/RecorderState.kt` | 4 | `RecorderState`, `LiveMode`, `RecorderFailure`, `locksPaging`, `canStartRecording`. |
| `v2/recorder/RecorderActions.kt` | 4 | What Today's recorder controls do. |
| `v2/recorder/RecorderViewModel.kt` (replaced) | 4 | The activity-scoped recorder. |
| `v2/recorder/TodayRecorder.kt` | 5 | `TodayRecorder` and `RecordButtonModel`. |
| `v2/recorder/RecordEdgeButton.kt` | 5 | Record as Today's edge button with the gesture and the morph. |
| `v2/recorder/RecorderItems.kt` | 5 | Today's list while recording, saving, saved, too short or failed. |
| `v2/recorder/RecordingRing.kt` | 5 | The input level as bars around the screen edge. |
| `v2/permissions/SystemSettings.kt` | 5 | Opens LogDate's settings page or the storage screen. |
| `v2/today/TodayPage.kt` (modified) | 5, 7 | Today with the recorder; the ambient face while recording in ambient. |
| `v2/WearAppV2.kt` (modified) | 5, 7, 9, 10 | Wires the recorder, ambient scope, Mark, the + sheet and the phone camera. |
| `launch/FeatureSwitch.kt` | 6 | A flag's latest value for code that can't suspend. |
| `recording/OngoingRecordingDecorator.kt` | 6 | The Ongoing Activity for a recording. |
| `LogDateWearApplication.kt` (modified) | 6 | Supplies the decorator and follows the flag. |
| `v2/ambient/WatchAmbient.kt` | 7 | `WearSdkAmbient`, `WatchAmbientScope`, `currentAmbientMode()`. |
| `v2/ambient/AmbientRecordingFace.kt` | 7 | The minute-tick recording face with burn-in shifts. |
| `v2/mark/MarkedMomentStore.kt` | 8 | Saves a mark and queues it: outbox and delivery ledger. |
| `health/HeartRateSampler.kt` | 9 | One `MeasureClient` heart rate reading within 8 s. |
| `v2/mark/MarkMomentUseCase.kt` | 9 | Marks now, then adds place and heart rate. |
| `v2/mark/MarkViewModel.kt` | 9 | Mark from Today: haptic, confirmation, heart rate prompt. |
| `v2/mark/MarkMomentReceiver.kt` | 9 | `BackgroundMarker` and the broadcast receiver. |
| `v2/mark/MarkConfirmation.kt` | 9 | The "Moment marked" and failure dialogs. |
| `v2/capture/RemoteTextInput.kt` | 10 | The system text input (voice or keyboard) and its result. |
| `v2/capture/CaptureSheetViewModel.kt` | 10 | The + sheet's state and saves. |
| `v2/capture/CaptureSheet.kt` | 10 | The + sheet and its confirmations. |

Resources (under `app/wear/src/main/`): `res/values/strings_recorder.xml` (Tasks 5, 6, 7), `res/values/strings_capture.xml` (Tasks 9, 10), `res/drawable/ic_recorder_pause.xml`, `ic_recorder_resume.xml`, `ic_recorder_discard.xml` (Task 5), `AndroidManifest.xml` (Task 9), `di/WearV2Module.kt` (Tasks 3, 4, 8, 9, 10).

Tests: `app/wear/src/test/kotlin/app/logdate/wear/{recording,capture,launch,health,v2/...}/`, `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/`, `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/`, `client/media/src/androidHostTest/kotlin/app/logdate/client/media/audio/`.

## Verified API surface

context7 is not used: every signature below was read with `javap` from the exact artifacts Gradle resolved (`~/.gradle/caches/modules-2/files-2.1/`), with nullability from their Kotlin metadata where it matters, or from the repository.

- `androidx.wear.compose.foundation.rememberAmbientModeManager(): AmbientModeManager` needs a non-null `LocalActivity.current` and builds `AmbientModeManagerImpl`, whose constructor calls `com.google.wear.services.ambient.AmbientComponentState` and `com.google.wear.Sdk.getWearManager(Context, Class)` from the optional `wear-sdk` shared library (`<uses-library android:name="wear-sdk" android:required="false"/>` in the AAR manifest). `wear-sdk.jar` ships only in `platforms/android-36` and later, so on Wear OS 5 (API 34/35) the call throws `NoClassDefFoundError`. `LocalAmbientModeManager: ProvidableCompositionLocal<AmbientModeManager?>` defaults to null. `AmbientModeManager { val currentAmbientMode: AmbientMode; suspend fun withAmbientTick(block: () -> Unit) }`, `AmbientMode.Interactive`, `AmbientMode.Ambient(isBurnInProtectionRequired: Boolean, isLowBitAmbientSupported: Boolean)`, and the composable extension `AmbientModeManager.AmbientTickEffect(block: () -> Unit)`.
- `androidx.wear.ambient.AmbientLifecycleObserver` (wear 1.4.0) enables ambient in `onCreate` and has no way to turn it off, so it is not used as a fallback.
- `androidx.wear.ongoing.OngoingActivity.Builder(context, notificationId: Int, builder: NotificationCompat.Builder)` with `setStaticIcon(Int)`, `setTouchIntent(PendingIntent)`, `setTitle(String)`, `setStatus(Status)`, `build()`; `OngoingActivity.apply(context)` extends the builder, so it runs before `builder.build()`; `OngoingActivity.recoverOngoingActivity(context): OngoingActivity?`, `getNotificationId()`, `getTitle()`, `getStatus()`; `Status.forPart(Status.Part)`, `Status.getPartNames()`, `Status.getPart(String)`; `Status.StopwatchPart(timeZeroMillis)`, `(timeZeroMillis, pausedAtMillis)`, `isPaused()`. `OngoingActivity.Builder.build()` reads `SystemClock.elapsedRealtime()`, the clock its stopwatch parts use.
- `androidx.wear.input.RemoteInputIntentHelper.createActionRemoteInputIntent(): Intent`, `putRemoteInputsExtra(Intent, List<android.app.RemoteInput>)`, `getRemoteInputsExtra(Intent)`, `isActionRemoteInput(Intent)`; `android.app.RemoteInput.Builder.wearableExtender(block: WearableRemoteInputExtender.() -> Unit)`; `WearableRemoteInputExtender.setEmojisAllowed(Boolean)`, `setInputActionType(Int)`, `INPUT_ACTION_TYPE_DONE`; results come back through `android.app.RemoteInput.getResultsFromIntent(Intent): Bundle?`.
- `androidx.health.services.client.HealthServices.getClient(context).measureClient: MeasureClient` with `registerMeasureCallback(DeltaDataType, MeasureCallback)` and `unregisterMeasureCallbackAsync(DeltaDataType, MeasureCallback)`; the suspend extension `MeasureClient.getCapabilities(): MeasureCapabilities` with `supportedDataTypesMeasure`; `MeasureCallback { onAvailabilityChanged(DeltaDataType, Availability); onDataReceived(DataPointContainer); onRegistrationFailed(Throwable) }`; `DataType.HEART_RATE_BPM: DeltaDataType<Double, SampleDataPoint<Double>>`; `DataPointContainer.getData(DataType.HEART_RATE_BPM): List<SampleDataPoint<Double>>`; `DataTypeAvailability.UNAVAILABLE`, `UNAVAILABLE_DEVICE_OFF_BODY`.
- Heart rate permission: `android.permission.health.READ_HEART_RATE` (`HealthPermissions.READ_HEART_RATE`, API 36) and `android.permission.BODY_SENSORS` before it; neither is in the merged watch manifest today, so Task 9 adds them.
- Wear Compose Material 3 1.7.0: `EdgeButton(onClick, modifier, buttonSize: EdgeButtonSize, enabled, colors, border, interactionSource, content: RowScope.() -> Unit)`; `FilledTonalIconButton`/`FilledIconButton`/`IconButton(onClick, modifier, onLongClick, onLongClickLabel, enabled, shapes, colors, border, interactionSource, content: BoxScope.() -> Unit)`; `Button`/`FilledTonalButton(onClick, modifier, onLongClick, onLongClickLabel, enabled, shape, colors, border, contentPadding, interactionSource, transformation, content)`; `ButtonGroup(modifier, spacing, expansionWidth, contentPadding, verticalAlignment, content)`; `Icon(painter | imageVector, contentDescription, modifier, tint)`; `ListHeader(modifier, backgroundColor, contentColor, contentPadding, transformation, content)`; `ConfirmationDialog(visible, onDismissRequest, curvedText, modifier, colors, properties, durationMillis, content)`, `SuccessConfirmationDialog` and `FailureConfirmationDialog` (same parameters, default icons), their `...Content(curvedText, modifier, colors, content)` forms, `confirmationDialogCurvedText(text, style)`, `ConfirmationDialogDefaults.curvedTextStyle`; `LocalContentColor`; typography `numeralSmall`, `titleSmall`, `labelSmall`, `bodyMedium`.
- `TransformingLazyColumnScope.item(key, contentType, content: TransformingLazyColumnItemScope.() -> Unit)` and `items(count, key, contentType, content: TransformingLazyColumnItemScope.(Int) -> Unit)`.
- Compose UI 1.11: `SuspendingPointerInputModifierNode(handler: suspend PointerInputScope.() -> Unit)`; `DelegatingNode.delegate(node)`; `PointerInputModifierNode.onPointerEvent(PointerEvent, PointerEventPass, IntSize)` and `onCancelPointerInput()`; `SemanticsModifierNode.applySemantics(SemanticsPropertyReceiver)`, `shouldClearDescendantSemantics`, `invalidateSemantics()`.
- Compose UI test 1.11: `TouchInjectionScope.down/moveBy/move/up/cancel`, `advanceEventTime(Long)`, `viewConfiguration`; `performCustomAccessibilityActionWithLabel(String)`, `performSemanticsAction(SemanticsActions.OnClick)`.
- Repository: `AndroidAudioNotificationHandler(context)` is built in `AudioRecordingService.onCreate()` (`AudioRecordingService.kt:145`), posts notification 1001 (`LogDateNotificationChannelKey.AUDIO_RECORDING.notificationId`), and is rebuilt on pause and resume (`:271`, `:302`). `MarkedMomentDao.upsert/getById/observeByState`, `PeerDeliveryDao.upsert/get/due/delete`, `EntityType.MOMENT`, `SyncMetadataService.enqueuePending(id, type, operation)`; `WatchSyncScheduler.UPLOADED_TYPES` is `JOURNAL, NOTE, ASSOCIATION`, so a queued `MOMENT` never wakes the watch's v1 sync.

---

### Task 0: Confirm the prerequisites and record a baseline

**Files:** none changed.

**Interfaces:**
- Consumes: the design system, shell and Today plan and the sync v2 foundations, both on this branch.
- Produces: confidence that every later task starts from green.

- [ ] **Step 1: Check the prerequisites**

```bash
grep -n "class RecorderViewModel\|data object Recording" app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderViewModel.kt
grep -n "val onRecord\|val onMark\|val onAdd" app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayPage.kt
grep -n "fun markedMomentDao\|fun peerDeliveryDao" client/database/src/commonMain/kotlin/app/logdate/client/database/LogDateDatabase.kt
grep -n "MOMENT," client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/SyncMetadataService.kt
grep -n "libs.androidx.wear.ongoing\|libs.androidx.wear.input\|libs.androidx.wear)\|libs.androidx.health.services.client" app/wear/build.gradle.kts
ls client/database/schemas/app.logdate.client.database.LogDateDatabase/ | sort -n | tail -1
```

Expected: two lines, three lines, two lines, one line, four lines, and `53.json`. If anything is missing, stop: this plan builds on those commits.

- [ ] **Step 2: Check the tree**

```bash
git status --short && git log --oneline -3
```

Expected: no output from `git status`; the log ends at or after `17bcbc04b chore(sync): add a linked-watch profile to the sync engine`.

- [ ] **Step 3: Run the unit, host and screenshot tests once**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :client:media:testAndroidHostTest :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`. Record the number of tests in `app/wear/build/test-results/testDebugUnitTest/TEST-app.logdate.wear.presentation.recording.WearRecordingViewModelTest.xml` (`tests="63"`). If screenshot validation fails here, stop and report: the baselines are already stale.

- [ ] **Step 4: Run the GMD tests this plan touches**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearRecorderServiceE2ETest,app.logdate.wear.e2e.HomePagerRotaryTest,app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.ExperienceGateTest
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 1: Pull the save and undo steps out of the v1 recorder (`refactor(wear)`)

`WearRecordingViewModel` does three jobs: the press and release rules, turning a finished file into a note, and the undo window after a save. The new recorder needs the last two without the first, so they move into `AudioMomentSaver` and `AfterSaveController`. The view model keeps its constructor and constants and builds both itself, so its Koin binding and all 63 of its tests stay as they are; that unchanged test file is the proof the refactor changed nothing. `WearRecordingRecovery` reads the minimum length from the saver instead of the v1 view model, which Phase Z deletes.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/recording/AudioMomentSaver.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/recording/AfterSaveController.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/recording/WearRecordingViewModel.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/recording/WearRecordingRecovery.kt:9` and `:96`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/recording/AudioMomentSaverTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/recording/AfterSaveControllerTest.kt`
- Unchanged and must stay green: `app/wear/src/test/kotlin/app/logdate/wear/presentation/recording/WearRecordingViewModelTest.kt`

**Interfaces:**
- Consumes: `JournalNotesRepository`, `AudioDurationResolver`, `NoteHealthAnnotator`, `WearLocationCaptureCoordinator`, `WearNoteRemovalNotifier`, `CrashSafeRecording.inFlightFile(File)`.
- Produces:
  - `sealed interface AudioSaveResult { data class Saved(val note: JournalNote.Audio); data object TooShort; data class Failed(val path: String) }` in `app.logdate.wear.recording`.
  - `class AudioMomentSaver(notesRepository, durationResolver, noteHealthAnnotator, locationCaptureCoordinator, clock: Clock = Clock.System, deleteFile: (String) -> Unit = ...)` with `suspend fun save(path: String, recorderElapsed: Duration): AudioSaveResult`, `fun discard(path: String)`, `const val MIN_DURATION_MS = 500L`, `const val LOCATION_TIMEOUT_MS = 3_000L`.
  - `data class AfterSaveWindow(val noteId: Uuid)` and `class AfterSaveController(scope: CoroutineScope, notesRepository, removalNotifier: WearNoteRemovalNotifier, deleteFile: (String) -> Unit = ...)` with `val window: StateFlow<AfterSaveWindow?>`, `fun open(noteId: Uuid)`, `fun close()`, `suspend fun undo(noteId: Uuid): Boolean`, `const val UNDO_WINDOW_MS = 5_000L`.

- [ ] **Step 1: Write the failing saver test**

Create `app/wear/src/test/kotlin/app/logdate/wear/recording/AudioMomentSaverTest.kt`:

```kotlin
package app.logdate.wear.recording

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.location.WearLocationCaptureCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [AudioMomentSaver]: a finished recording becomes an audio note, is discarded as too
 * short, or stays on disk when the note can't be stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioMomentSaverTest {
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val durationResolver = mockk<AudioDurationResolver>()
    private val noteHealthAnnotator = mockk<NoteHealthAnnotator>(relaxed = true)
    private val locationCaptureCoordinator = mockk<WearLocationCaptureCoordinator>()
    private val deletedFiles = mutableListOf<String>()
    private val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)

    @Before
    fun setUp() {
        coEvery { durationResolver.resolveDurationMs(any()) } returns 4_200L
        coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns null
        coEvery { notesRepository.create(any<JournalNote>()) } returns Uuid.random()
    }

    @Test
    fun `a recording becomes an audio note of the length its file reads`() =
        runTest {
            val created = slot<JournalNote>()
            coEvery { notesRepository.create(capture(created)) } returns Uuid.random()

            val result = saver().save("/files/audio_notes/a.m4a", recorderElapsed = 9.seconds)

            val note = (result as AudioSaveResult.Saved).note
            assertEquals("/files/audio_notes/a.m4a", note.mediaRef)
            assertEquals(4_200L, note.durationMs)
            assertEquals(now, note.creationTimestamp)
            assertEquals(note, created.captured)
        }

    @Test
    fun `the recorder clock counts when the file length cannot be read`() =
        runTest {
            coEvery { durationResolver.resolveDurationMs(any()) } returns null

            val result = saver().save("/files/audio_notes/b.m4a", recorderElapsed = 9.seconds)

            assertEquals(9_000L, (result as AudioSaveResult.Saved).note.durationMs)
        }

    @Test
    fun `a recording under half a second is too short and both of its files go`() =
        runTest {
            val (finished, raw) = recordingWithRawFile("blip")
            coEvery { durationResolver.resolveDurationMs(any()) } returns 300L

            val result = saver().save(finished.path, recorderElapsed = 300.milliseconds)

            assertEquals(AudioSaveResult.TooShort, result)
            assertEquals(listOf(finished.path, raw.path), deletedFiles)
            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    @Test
    fun `a saved recording loses its raw file and gets a health annotation`() =
        runTest {
            val (finished, raw) = recordingWithRawFile("saved")

            val result = saver().save(finished.path, recorderElapsed = 4.seconds)

            val note = (result as AudioSaveResult.Saved).note
            assertEquals(listOf(raw.path), deletedFiles)
            coVerify { noteHealthAnnotator.annotate(note.uid) }
        }

    @Test
    fun `a note that cannot be stored leaves both files for another try`() =
        runTest {
            val (finished, _) = recordingWithRawFile("unsaved")
            coEvery { notesRepository.create(any<JournalNote>()) } throws IllegalStateException("database closed")

            val result = saver().save(finished.path, recorderElapsed = 4.seconds)

            assertEquals(AudioSaveResult.Failed(finished.path), result)
            assertTrue(deletedFiles.isEmpty())
        }

    @Test
    fun `the place is saved with the note`() =
        runTest {
            val location = NoteLocation(coordinates = NoteCoordinates(latitude = 37.77, longitude = -122.41))
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns location

            val result = saver().save("/files/audio_notes/c.m4a", recorderElapsed = 4.seconds)

            assertEquals(location, (result as AudioSaveResult.Saved).note.location)
        }

    @Test
    fun `a slow place reading does not delay the save past three seconds`() =
        runTest {
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } coAnswers {
                delay(60_000)
                null
            }

            val result = async { saver().save("/files/audio_notes/d.m4a", recorderElapsed = 4.seconds) }
            advanceTimeBy(AudioMomentSaver.LOCATION_TIMEOUT_MS + 100)

            assertTrue(result.isCompleted)
            assertNull((result.await() as AudioSaveResult.Saved).note.location)
        }

    private fun saver() =
        AudioMomentSaver(
            notesRepository = notesRepository,
            durationResolver = durationResolver,
            noteHealthAnnotator = noteHealthAnnotator,
            locationCaptureCoordinator = locationCaptureCoordinator,
            clock = FixedClock(now),
            deleteFile = { deletedFiles += it },
        )

    /** A finished recording and the raw file a crash-safe session leaves beside it, as real files. */
    private fun recordingWithRawFile(name: String): Pair<File, File> {
        val directory = Files.createTempDirectory("saver").toFile().also { it.deleteOnExit() }
        val finished = File(directory, "recording_$name.m4a").apply { writeText("audio") }
        val raw = CrashSafeRecording.inFlightFile(finished).apply { writeText("raw audio") }
        return finished to raw
    }

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }
}
```

- [ ] **Step 2: Write the failing undo window test**

Create `app/wear/src/test/kotlin/app/logdate/wear/recording/AfterSaveControllerTest.kt`:

```kotlin
package app.logdate.wear.recording

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [AfterSaveController]: the five seconds after a save, and what Undo does in them. */
@OptIn(ExperimentalCoroutinesApi::class)
class AfterSaveControllerTest {
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val removedNotes = mutableListOf<Uuid>()
    private val deletedFiles = mutableListOf<String>()
    private val noteId = Uuid.random()

    @Test
    fun `a window opens for the saved note and closes after five seconds`() =
        runTest {
            val controller = controller()

            controller.open(noteId)
            advanceTimeBy(AfterSaveController.UNDO_WINDOW_MS - 1)
            runCurrent()
            assertEquals(AfterSaveWindow(noteId), controller.window.value)

            advanceTimeBy(2)
            runCurrent()
            assertNull(controller.window.value)
        }

    @Test
    fun `opening again restarts the five seconds`() =
        runTest {
            val controller = controller()
            controller.open(noteId)
            advanceTimeBy(4_000)
            val second = Uuid.random()

            controller.open(second)
            advanceTimeBy(4_000)
            runCurrent()

            assertEquals(AfterSaveWindow(second), controller.window.value)
        }

    @Test
    fun `closing ends the window and leaves the note alone`() =
        runTest {
            val controller = controller()
            controller.open(noteId)

            controller.close()

            assertNull(controller.window.value)
            coVerify(exactly = 0) { notesRepository.removeById(any()) }
        }

    @Test
    fun `undo removes the note and its audio and tells the phone`() =
        runTest {
            coEvery { notesRepository.getNoteById(noteId) } returnsMany listOf(audio("/files/audio_notes/u.m4a"), null)
            val controller = controller()
            controller.open(noteId)

            assertTrue(controller.undo(noteId))

            coVerify { notesRepository.removeById(noteId) }
            assertEquals(listOf("/files/audio_notes/u.m4a"), deletedFiles)
            assertEquals(listOf(noteId), removedNotes)
            assertNull(controller.window.value)
        }

    @Test
    fun `undo leaves the audio while a note still refers to it`() =
        runTest {
            coEvery { notesRepository.getNoteById(noteId) } returns audio("/files/audio_notes/shared.m4a")

            assertTrue(controller().undo(noteId))

            assertTrue(deletedFiles.isEmpty())
        }

    @Test
    fun `a removal that fails reports false and tells the phone nothing`() =
        runTest {
            coEvery { notesRepository.removeById(noteId) } throws IllegalStateException("database closed")

            assertFalse(controller().undo(noteId))

            assertTrue(removedNotes.isEmpty())
        }

    private fun TestScope.controller() =
        AfterSaveController(
            scope = backgroundScope,
            notesRepository = notesRepository,
            removalNotifier = { removedNotes += it },
            deleteFile = { deletedFiles += it },
        )

    private fun audio(path: String): JournalNote.Audio {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        return JournalNote.Audio(mediaRef = path, uid = noteId, creationTimestamp = now, lastUpdated = now, durationMs = 4_200)
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.recording.AudioMomentSaverTest' --tests 'app.logdate.wear.recording.AfterSaveControllerTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'AudioMomentSaver'`, `'AudioSaveResult'`, `'AfterSaveController'` and `'AfterSaveWindow'`.

- [ ] **Step 4: Add the saver**

Create `app/wear/src/main/kotlin/app/logdate/wear/recording/AudioMomentSaver.kt`:

```kotlin
package app.logdate.wear.recording

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SystemCaptureTimeZone
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.location.WearLocationCaptureCoordinator
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** What became of a finished recording handed to [AudioMomentSaver.save]. */
sealed interface AudioSaveResult {
    /** The recording is a note now, and the raw file that marked it unsaved is gone. */
    data class Saved(
        val note: JournalNote.Audio,
    ) : AudioSaveResult

    /** Shorter than [AudioMomentSaver.MIN_DURATION_MS]. The file and its raw file are deleted. */
    data object TooShort : AudioSaveResult

    /**
     * The note could not be stored. The file and its raw file stay on disk, so saving again, or
     * startup recovery, can still make it a note.
     */
    data class Failed(
        val path: String,
    ) : AudioSaveResult
}

/**
 * Makes a finished recording an audio note: its length, where it was made, its health annotation,
 * and the removal of the raw file that marks it unsaved for startup recovery. Both watch recorders
 * save through it.
 */
class AudioMomentSaver(
    private val notesRepository: JournalNotesRepository,
    private val durationResolver: AudioDurationResolver,
    private val noteHealthAnnotator: NoteHealthAnnotator,
    private val locationCaptureCoordinator: WearLocationCaptureCoordinator,
    private val clock: Clock = Clock.System,
    private val deleteFile: (String) -> Unit = { path -> File(path).delete() },
) {
    /**
     * Saves the recording at [path]. [recorderElapsed] is the recorder's own clock. It counts when the
     * file's length doesn't read as a real recording, so a metadata hiccup can't make a long
     * recording look too short to save.
     */
    suspend fun save(
        path: String,
        recorderElapsed: Duration,
    ): AudioSaveResult {
        val durationMs = recordedDurationMs(path, recorderElapsed)
        if (durationMs < MIN_DURATION_MS) {
            discard(path)
            return AudioSaveResult.TooShort
        }
        return try {
            AudioSaveResult.Saved(persist(path, durationMs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to save recording $path", e)
            AudioSaveResult.Failed(path)
        }
    }

    /** Deletes the recording at [path] and the raw file its session left beside it. */
    fun discard(path: String) {
        deleteFile(path)
        deleteRawRecording(path)
    }

    private suspend fun recordedDurationMs(
        path: String,
        recorderElapsed: Duration,
    ): Long {
        val resolved = durationResolver.resolveDurationMs(path)
        if (resolved != null && resolved >= MIN_DURATION_MS) return resolved
        return maxOf(resolved ?: 0L, recorderElapsed.inWholeMilliseconds)
    }

    private suspend fun persist(
        path: String,
        durationMs: Long,
    ): JournalNote.Audio {
        val now = clock.now()
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { locationCaptureCoordinator.captureForJournalEntry() }
        val note =
            JournalNote.Audio(
                mediaRef = path,
                uid = Uuid.random(),
                creationTimestamp = now,
                lastUpdated = now,
                durationMs = durationMs,
                location = location,
                timeZoneId = SystemCaptureTimeZone.currentTimeZoneId(),
            )
        notesRepository.create(note)
        // The raw file marks a recording as unsaved for startup recovery, so it goes once the note exists.
        deleteRawRecording(path)
        noteHealthAnnotator.annotate(note.uid)
        return note
    }

    private fun deleteRawRecording(path: String) {
        val raw = CrashSafeRecording.inFlightFile(File(path))
        if (raw.isFile) deleteFile(raw.path)
    }

    companion object {
        /** A recording shorter than this is a slip of the finger, not a moment. */
        const val MIN_DURATION_MS = 500L

        /** How long a save waits for the place before saving without it. */
        const val LOCATION_TIMEOUT_MS = 3_000L
    }
}
```

- [ ] **Step 5: Add the undo window**

Create `app/wear/src/main/kotlin/app/logdate/wear/recording/AfterSaveController.kt`:

```kotlin
package app.logdate.wear.recording

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.sync.WearNoteRemovalNotifier
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.uuid.Uuid

/** The note a save just made, while Undo can still take it back. */
data class AfterSaveWindow(
    val noteId: Uuid,
)

/**
 * The seconds after a recording is saved. [open] starts a window of [UNDO_WINDOW_MS]; while it
 * lasts, [undo] removes the note, deletes its audio once no note refers to it, and tells the phone,
 * which may already have the note.
 */
class AfterSaveController(
    private val scope: CoroutineScope,
    private val notesRepository: JournalNotesRepository,
    private val removalNotifier: WearNoteRemovalNotifier,
    private val deleteFile: (String) -> Unit = { path -> File(path).delete() },
) {
    private val mutableWindow = MutableStateFlow<AfterSaveWindow?>(null)
    private var closeJob: Job? = null

    /** The open window, or null once it has closed. */
    val window: StateFlow<AfterSaveWindow?> = mutableWindow.asStateFlow()

    fun open(noteId: Uuid) {
        closeJob?.cancel()
        mutableWindow.value = AfterSaveWindow(noteId)
        closeJob =
            scope.launch {
                delay(UNDO_WINDOW_MS)
                mutableWindow.value = null
            }
    }

    /** Ends the window early, as recording again does. The note stays. */
    fun close() {
        closeJob?.cancel()
        closeJob = null
        mutableWindow.value = null
    }

    /**
     * Removes [noteId] and its audio, and tells the phone. Returns false when the note could not be
     * removed; the phone is told nothing then.
     */
    suspend fun undo(noteId: Uuid): Boolean {
        if (mutableWindow.value?.noteId == noteId) close()
        return try {
            val mediaRef = (notesRepository.getNoteById(noteId) as? JournalNote.Audio)?.mediaRef
            notesRepository.removeById(noteId)
            if (mediaRef != null && notesRepository.getNoteById(noteId) == null) deleteFile(mediaRef)
            // The outbox drops a delete that meets a pending create, but the phone may already have
            // the note, so it is told directly.
            removalNotifier.notifyRemoved(noteId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to undo saved recording $noteId", e)
            false
        }
    }

    companion object {
        const val UNDO_WINDOW_MS = 5_000L
    }
}
```

- [ ] **Step 6: Save and undo through the new classes in the v1 recorder**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/presentation/recording/WearRecordingViewModel.kt` with:

```kotlin
package app.logdate.wear.presentation.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.haptic.WearHapticEngine
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.presentation.common.SaveFeedback
import app.logdate.wear.recording.AfterSaveController
import app.logdate.wear.recording.AudioMomentSaver
import app.logdate.wear.recording.AudioSaveResult
import app.logdate.wear.recording.RecordingStartFailure
import app.logdate.wear.recording.WearAudioRecordingManager
import app.logdate.wear.recording.WearRecorder
import app.logdate.wear.sync.WearDataLayerClient
import app.logdate.wear.sync.WearNoteRemovalNotifier
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

enum class RecordingPhase {
    READY,
    STARTING,
    RECORDING,
    PAUSED,
    SAVING,
    SAVED,
    TOO_SHORT,
    ERROR,
}

/** What went wrong, for the screen to explain in words. */
enum class RecordingError {
    MICROPHONE_PERMISSION_DENIED,
    NOT_ENOUGH_STORAGE,
    RECORDER_UNAVAILABLE,

    /** The recording finished but could not be stored. Its file is intact and pressing again saves it. */
    SAVE_FAILED,

    /** The recorder produced no usable file, so there is nothing to save. */
    RECORDING_LOST,
}

data class RecordingUiState(
    val phase: RecordingPhase = RecordingPhase.READY,
    val recordingDurationMs: Long = 0,
    val audioLevels: List<Float> = emptyList(),
    /** True once a tap started the recording, so it keeps going after the finger lifts. */
    val isLatched: Boolean = false,
    val pausedByInterruption: Boolean = false,
    val savedDurationMs: Long = 0,
    /** The note just saved, while it can still be undone. */
    val undoableNoteId: Uuid? = null,
    val saveFeedback: SaveFeedback? = null,
    val error: RecordingError? = null,
    val showGestureHint: Boolean = false,
    /** The discard control was tapped once; a second tap within the window deletes the recording. */
    val confirmingDiscard: Boolean = false,
)

/** Remembers whether the tap-or-hold hint has already been shown. */
interface RecordingHintStore {
    fun hasSeenHint(): Boolean

    fun markHintSeen()
}

/**
 * Drives the watch's voice recorder.
 *
 * Pressing the record surface starts recording at once. Lifting the finger quickly (a tap) latches
 * the recording so it continues until the next tap; holding past [HOLD_THRESHOLD_MS] is
 * push-to-talk and saves on release. While a latched recording is paused, the record surface saves
 * it and the side control resumes. A saved note stays undoable for [UNDO_WINDOW_MS].
 *
 * Every phase change happens before the recorder is asked to do anything, so the recorder's own
 * state flow can never be mistaken for the recorder ending a session by itself. Saving goes through
 * [AudioMomentSaver] and the undo window through [AfterSaveController], which the new watch app's
 * recorder shares.
 *
 * Leaving the screen never drops a recording. A recording still running is stopped and saved on
 * [applicationScope], which outlives this ViewModel, and one that failed to save is saved again.
 */
class WearRecordingViewModel(
    private val recorder: WearRecorder,
    notesRepository: JournalNotesRepository,
    durationResolver: AudioDurationResolver,
    noteHealthAnnotator: NoteHealthAnnotator,
    private val dataLayerClient: WearDataLayerClient,
    locationCaptureCoordinator: WearLocationCaptureCoordinator,
    private val haptics: WearHapticEngine,
    private val hintStore: RecordingHintStore,
    removalNotifier: WearNoteRemovalNotifier,
    private val applicationScope: CoroutineScope,
    private val clock: Clock = Clock.System,
    deleteFile: (String) -> Unit = { path -> File(path).delete() },
) : ViewModel() {
    companion object {
        const val HOLD_THRESHOLD_MS = 400L
        const val MIN_DURATION_MS = AudioMomentSaver.MIN_DURATION_MS
        const val UNDO_WINDOW_MS = AfterSaveController.UNDO_WINDOW_MS
        const val TOO_SHORT_DISPLAY_MS = 1_200L
        const val DISCARD_CONFIRM_MS = 3_000L
        const val LOCATION_TIMEOUT_MS = AudioMomentSaver.LOCATION_TIMEOUT_MS
        private const val MAX_LEVEL_SAMPLES = 50
        private val NEAR_LIMIT = WearAudioRecordingManager.MAX_RECORDING_DURATION - 1.minutes
    }

    private val saver =
        AudioMomentSaver(
            notesRepository = notesRepository,
            durationResolver = durationResolver,
            noteHealthAnnotator = noteHealthAnnotator,
            locationCaptureCoordinator = locationCaptureCoordinator,
            clock = clock,
            deleteFile = deleteFile,
        )
    private val afterSave =
        AfterSaveController(
            scope = viewModelScope,
            notesRepository = notesRepository,
            removalNotifier = removalNotifier,
            deleteFile = deleteFile,
        )

    private val _uiState = MutableStateFlow(RecordingUiState(showGestureHint = !hintStore.hasSeenHint()))
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var pressedAtMs = 0L
    private var releasedAtMs: Long? = null
    private var nearLimitWarned = false
    private var discardConfirmJob: Job? = null

    /** A finalized recording that could not be stored. The next press saves it instead of starting a new one. */
    private var unsavedPath: String? = null

    init {
        observeRecorder()
        observeUndoWindow()
    }

    override fun onCleared() {
        when (_uiState.value.phase) {
            RecordingPhase.RECORDING, RecordingPhase.PAUSED -> applicationScope.launch { stopAndSaveDetached() }
            RecordingPhase.STARTING -> recorder.requestStop()
            RecordingPhase.ERROR ->
                unsavedPath?.let { path -> applicationScope.launch { saver.save(path, recorder.elapsed.value) } }
            else -> Unit
        }
        super.onCleared()
    }

    fun onPress() {
        when (_uiState.value.phase) {
            RecordingPhase.READY -> beginRecording()
            RecordingPhase.ERROR -> retrySaveOrBeginRecording()
            RecordingPhase.SAVED -> {
                // Recording again keeps the last note; the undo window ends.
                afterSave.close()
                beginRecording()
            }
            RecordingPhase.RECORDING -> if (_uiState.value.isLatched) stopAndSave()
            RecordingPhase.PAUSED -> stopAndSave()
            RecordingPhase.STARTING, RecordingPhase.SAVING, RecordingPhase.TOO_SHORT -> Unit
        }
    }

    fun onRelease() {
        val state = _uiState.value
        val recorderRunning = state.phase == RecordingPhase.RECORDING || state.phase == RecordingPhase.PAUSED
        when {
            state.phase == RecordingPhase.STARTING -> releasedAtMs = now()
            recorderRunning && !state.isLatched -> applyRelease(now())
        }
    }

    fun onPauseToggle() {
        cancelDiscardConfirmation()
        when (_uiState.value.phase) {
            RecordingPhase.RECORDING -> viewModelScope.launch { recorder.pause() }
            RecordingPhase.PAUSED -> resume()
            else -> Unit
        }
    }

    /** The first tap asks to confirm, so a long recording is never one slip from gone; a second tap discards. */
    fun onDiscard() {
        val state = _uiState.value
        if (state.phase != RecordingPhase.RECORDING && state.phase != RecordingPhase.PAUSED) return
        if (!state.confirmingDiscard) {
            _uiState.update { it.copy(confirmingDiscard = true) }
            discardConfirmJob?.cancel()
            discardConfirmJob =
                viewModelScope.launch {
                    delay(DISCARD_CONFIRM_MS)
                    _uiState.update { it.copy(confirmingDiscard = false) }
                }
            return
        }
        discardConfirmJob?.cancel()
        _uiState.update { RecordingUiState(showGestureHint = it.showGestureHint) }
        viewModelScope.launch { recorder.discard() }
    }

    private fun cancelDiscardConfirmation() {
        discardConfirmJob?.cancel()
        _uiState.update { if (it.confirmingDiscard) it.copy(confirmingDiscard = false) else it }
    }

    fun onUndo() {
        val noteId = _uiState.value.undoableNoteId ?: return
        if (_uiState.value.phase != RecordingPhase.SAVED) return
        afterSave.close()
        _uiState.update { RecordingUiState(showGestureHint = it.showGestureHint) }
        viewModelScope.launch {
            if (afterSave.undo(noteId)) haptics.rejection()
        }
    }

    private fun retrySaveOrBeginRecording() {
        val path = unsavedPath ?: return beginRecording()
        _uiState.update { it.copy(phase = RecordingPhase.SAVING, error = null) }
        viewModelScope.launch { saveRecording(path) }
    }

    private fun beginRecording() {
        unsavedPath = null
        pressedAtMs = now()
        releasedAtMs = null
        nearLimitWarned = false
        _uiState.update { RecordingUiState(phase = RecordingPhase.STARTING, showGestureHint = it.showGestureHint) }
        viewModelScope.launch {
            if (!recorder.start()) {
                _uiState.update { it.copy(phase = RecordingPhase.ERROR, error = recorder.lastStartFailure.toError()) }
                haptics.rejection()
                return@launch
            }
            haptics.startRecording()
            _uiState.update { it.copy(phase = RecordingPhase.RECORDING) }
            // The recorder can end between start() returning and the phase changing, and the flow does not repeat itself.
            if (!recorder.isRecording.value) {
                stopAndSave()
                return@launch
            }
            releasedAtMs?.let(::applyRelease)
        }
    }

    private fun applyRelease(releasedAt: Long) {
        if (releasedAt - pressedAtMs < HOLD_THRESHOLD_MS) {
            _uiState.update { it.copy(isLatched = true) }
        } else {
            stopAndSave()
        }
    }

    private fun resume() {
        viewModelScope.launch { recorder.resume() }
    }

    private fun stopAndSave() {
        discardConfirmJob?.cancel()
        _uiState.update { it.copy(phase = RecordingPhase.SAVING, confirmingDiscard = false) }
        viewModelScope.launch {
            // Stopping and saving finish even if the screen is cleared partway through.
            withContext(NonCancellable) {
                haptics.stopRecording()
                val path = recorder.stop()
                if (path == null) {
                    Napier.e("Recording ended without a file")
                    _uiState.update { it.copy(phase = RecordingPhase.ERROR, error = RecordingError.RECORDING_LOST) }
                    return@withContext
                }
                saveRecording(path)
            }
        }
    }

    /** Stops the recorder and stores its file after the screen is gone, so there is no state to update. */
    private suspend fun stopAndSaveDetached() {
        val path = recorder.stop()
        if (path == null) {
            Napier.e("Recording ended without a file when the screen was cleared")
            return
        }
        saver.save(path, recorder.elapsed.value)
    }

    private suspend fun saveRecording(path: String) {
        when (val result = saver.save(path, recorder.elapsed.value)) {
            is AudioSaveResult.Saved -> onSaved(result.note)
            AudioSaveResult.TooShort -> showTooShort()
            is AudioSaveResult.Failed -> {
                unsavedPath = result.path
                _uiState.update { it.copy(phase = RecordingPhase.ERROR, error = RecordingError.SAVE_FAILED) }
            }
        }
    }

    private suspend fun onSaved(note: JournalNote.Audio) {
        unsavedPath = null
        val feedback =
            if (dataLayerClient.isPhoneConnected()) SaveFeedback.SYNCING_TO_PHONE else SaveFeedback.SAVED_LOCALLY
        hintStore.markHintSeen()
        haptics.success()
        _uiState.update {
            RecordingUiState(
                phase = RecordingPhase.SAVED,
                savedDurationMs = note.durationMs,
                undoableNoteId = note.uid,
                saveFeedback = feedback,
            )
        }
        afterSave.open(note.uid)
    }

    private suspend fun showTooShort() {
        unsavedPath = null
        haptics.rejection()
        _uiState.update { it.copy(phase = RecordingPhase.TOO_SHORT) }
        delay(TOO_SHORT_DISPLAY_MS)
        _uiState.update { RecordingUiState(showGestureHint = it.showGestureHint) }
    }

    /** When the undo window closes on its own, the screen goes back to ready. */
    private fun observeUndoWindow() {
        viewModelScope.launch {
            afterSave.window.collect { window ->
                if (window == null && _uiState.value.phase == RecordingPhase.SAVED) _uiState.update { RecordingUiState() }
            }
        }
    }

    private fun observeRecorder() {
        viewModelScope.launch {
            recorder.audioLevel.collect { level ->
                _uiState.update { state ->
                    if (state.phase != RecordingPhase.RECORDING) return@update state
                    state.copy(audioLevels = (state.audioLevels + level).takeLast(MAX_LEVEL_SAMPLES))
                }
            }
        }
        viewModelScope.launch {
            recorder.elapsed.collect { elapsed ->
                _uiState.update { state ->
                    val live = state.phase == RecordingPhase.RECORDING || state.phase == RecordingPhase.PAUSED
                    if (live) state.copy(recordingDurationMs = elapsed.inWholeMilliseconds) else state
                }
                if (!nearLimitWarned && _uiState.value.phase == RecordingPhase.RECORDING && elapsed >= NEAR_LIMIT) {
                    nearLimitWarned = true
                    haptics.warning()
                }
            }
        }
        viewModelScope.launch {
            combine(recorder.isPaused, recorder.pausedByInterruption) { paused, interrupted -> paused to interrupted }
                .collect { (paused, interrupted) -> onPauseStateChanged(paused, interrupted) }
        }
        viewModelScope.launch {
            recorder.isRecording.collect { recording ->
                val phase = _uiState.value.phase
                if (!recording && (phase == RecordingPhase.RECORDING || phase == RecordingPhase.PAUSED)) stopAndSave()
            }
        }
    }

    private fun onPauseStateChanged(
        paused: Boolean,
        interrupted: Boolean,
    ) {
        val phase = _uiState.value.phase
        when {
            paused && phase == RecordingPhase.RECORDING -> {
                haptics.pause()
                _uiState.update { it.copy(phase = RecordingPhase.PAUSED, pausedByInterruption = interrupted) }
            }
            paused && phase == RecordingPhase.PAUSED -> {
                _uiState.update { it.copy(pausedByInterruption = interrupted) }
            }
            !paused && phase == RecordingPhase.PAUSED -> {
                haptics.resume()
                _uiState.update { it.copy(phase = RecordingPhase.RECORDING, pausedByInterruption = false) }
            }
        }
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private fun RecordingStartFailure?.toError(): RecordingError =
        when (this) {
            RecordingStartFailure.MICROPHONE_PERMISSION_DENIED -> RecordingError.MICROPHONE_PERMISSION_DENIED
            RecordingStartFailure.NOT_ENOUGH_STORAGE -> RecordingError.NOT_ENOUGH_STORAGE
            RecordingStartFailure.RECORDER_UNAVAILABLE, null -> RecordingError.RECORDER_UNAVAILABLE
        }
}
```

- [ ] **Step 7: Read the minimum length from the saver in startup recovery**

In `app/wear/src/main/kotlin/app/logdate/wear/recording/WearRecordingRecovery.kt`, delete the import line `import app.logdate.wear.presentation.recording.WearRecordingViewModel` and change line 96 from

```kotlin
        if (durationMs < WearRecordingViewModel.MIN_DURATION_MS) {
```

to

```kotlin
        if (durationMs < AudioMomentSaver.MIN_DURATION_MS) {
```

(`AudioMomentSaver` is in the same package, so it needs no import.)

- [ ] **Step 8: Run the new tests, the v1 recorder tests and recovery tests**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.recording.*' --tests 'app.logdate.wear.presentation.recording.WearRecordingViewModelTest'
```

Expected: `BUILD SUCCESSFUL`; `TEST-app.logdate.wear.presentation.recording.WearRecordingViewModelTest.xml` shows `tests="63" failures="0"`, the same count as Task 0.

- [ ] **Step 9: Confirm the v1 test file is untouched and lint**

```bash
git diff --stat -- app/wear/src/test/kotlin/app/logdate/wear/presentation/recording/WearRecordingViewModelTest.kt && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintCheck :app:wear:detekt
```

Expected: no output from `git diff`, then `BUILD SUCCESSFUL`.

- [ ] **Step 10: Run the real recorder on the Wear GMD**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearRecorderServiceE2ETest,app.logdate.wear.e2e.WearHomeScreenTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task1-commit.txt <<'EOF'
refactor(wear): move recording saves and undo out of the recorder

The watch's recorder screen turned a finished file into a note and ran
the five-second Undo itself. Both steps now live in AudioMomentSaver
and AfterSaveController, so the redesigned watch app's recorder can
save and undo the same way without the old press and release rules.

The current recorder builds both from its existing constructor and
behaves exactly as before: its 63 tests pass unedited. Startup
recovery reads the minimum recording length from the saver.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 12: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/recording/AudioMomentSaver.kt app/wear/src/main/kotlin/app/logdate/wear/recording/AfterSaveController.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/recording/WearRecordingViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/recording/WearRecordingRecovery.kt app/wear/src/test/kotlin/app/logdate/wear/recording/AudioMomentSaverTest.kt app/wear/src/test/kotlin/app/logdate/wear/recording/AfterSaveControllerTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task1-commit.txt
```

---
### Task 2: The record gesture (`chore(wear)`)

Record has to tell a tap, a hold, a slide to the lock and a scroll apart, on a button that sits inside the Home pager. The rules live in a pure `RecordGestureMachine` so every path is tested without a device. `Modifier.recordGesture` feeds it: it sees the touch on the Initial pass, before the pager and before the `EdgeButton`'s own click, and consumes the down so that click never fires. Until the system long-press timeout it only watches, checking on the Final pass whether the pager consumed the drag; after the timeout it consumes every event, so the pager cannot move and a finger that slides off the button still holds. A touch the system cancels arrives as an up that is already consumed: before the timeout that yields, after it that releases, so the recording is saved. The modifier also owns the button's accessibility, so TalkBack sees one control with a "Start recording" action no matter how `EdgeButton` lays out its click. Nothing uses the modifier until Task 5.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureMachine.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureListener.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureModifier.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureMachineTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/RecordGestureTest.kt`

**Interfaces:**
- Consumes: `HomeScreen(pagingLocked, today, flow)` and `LogDateWearTheme` (test harness only).
- Produces:
  - `sealed interface RecordGesturePhase { Idle; Pressed(downY); Holding(downY, lockProgress); Locked }`, `sealed interface RecordGestureEvent { Down(y); Move(y); Claimed; Timeout; Up; Cancel }`, `sealed interface RecordGestureSignal { Pressed; Yielded; Tapped; Committed; LockProgress(progress); Locked; Released }`, `data class RecordGestureStep(phase, signals)`, `class RecordGestureMachine(lockDistancePx: Float) { fun step(phase, event): RecordGestureStep }` in `app.logdate.wear.v2.recorder.gesture`.
  - `interface RecordGestureListener { onPressed(); onYielded(); onTapped(); onCommitted(); onLockProgress(progress: Float); onLocked(); onReleased() }` and `fun RecordGestureListener.startLocked()`.
  - `fun Modifier.recordGesture(armed: Boolean, listener: RecordGestureListener, description: String, startLabel: String, onClick: () -> Unit, enabled: Boolean = true, lockDistance: Dp = RecordGestureDefaults.LockDistance): Modifier` and `object RecordGestureDefaults { val LockDistance: Dp = 56.dp }`.

- [ ] **Step 1: Write the failing state machine test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureMachineTest.kt`:

```kotlin
package app.logdate.wear.v2.recorder.gesture

import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Cancel
import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Claimed
import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Down
import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Move
import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Timeout
import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Up
import app.logdate.wear.v2.recorder.gesture.RecordGesturePhase.Holding
import app.logdate.wear.v2.recorder.gesture.RecordGesturePhase.Idle
import app.logdate.wear.v2.recorder.gesture.RecordGesturePhase.Pressed
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Every phase of the record gesture against every event, with a lock 100 pixels above a finger
 * that went down at y = 200.
 */
class RecordGestureMachineTest {
    private val machine = RecordGestureMachine(lockDistancePx = 100f)
    private val pressed = Pressed(downY = 200f)
    private val holding = Holding(downY = 200f, lockProgress = 0f)

    @Test
    fun `a finger down presses`() = assertStep(Idle, Down(200f), pressed, RecordGestureSignal.Pressed)

    @Test
    fun `only a finger down leaves idle`() {
        listOf(Move(10f), Claimed, Timeout, Up, Cancel).forEach { event -> assertStep(Idle, event, Idle) }
    }

    @Test
    fun `lifting before the timeout is a tap`() = assertStep(pressed, Up, Idle, RecordGestureSignal.Tapped)

    @Test
    fun `the pager claiming the touch yields`() = assertStep(pressed, Claimed, Idle, RecordGestureSignal.Yielded)

    @Test
    fun `a touch the system cancels before the timeout yields`() = assertStep(pressed, Cancel, Idle, RecordGestureSignal.Yielded)

    @Test
    fun `the timeout commits to hold to talk`() = assertStep(pressed, Timeout, holding, RecordGestureSignal.Committed)

    @Test
    fun `moving before the timeout changes nothing`() = assertStep(pressed, Move(120f), pressed)

    @Test
    fun `a second finger before the timeout changes nothing`() = assertStep(pressed, Down(10f), pressed)

    @Test
    fun `sliding up reports how far the lock is`() =
        assertStep(holding, Move(150f), holding.copy(lockProgress = 0.5f), RecordGestureSignal.LockProgress(0.5f))

    @Test
    fun `sliding down reports nothing`() = assertStep(holding, Move(260f), holding)

    @Test
    fun `progress follows the finger back down`() =
        assertStep(holding.copy(lockProgress = 0.5f), Move(180f), holding.copy(lockProgress = 0.2f), RecordGestureSignal.LockProgress(0.2f))

    @Test
    fun `sliding the whole distance locks`() =
        assertStep(holding, Move(100f), RecordGesturePhase.Locked, RecordGestureSignal.LockProgress(1f), RecordGestureSignal.Locked)

    @Test
    fun `sliding past the lock locks once`() =
        assertStep(holding, Move(-50f), RecordGesturePhase.Locked, RecordGestureSignal.LockProgress(1f), RecordGestureSignal.Locked)

    @Test
    fun `lifting the finger while holding releases`() = assertStep(holding, Up, Idle, RecordGestureSignal.Released)

    @Test
    fun `a touch the system cancels while holding releases so the recording is saved`() =
        assertStep(holding, Cancel, Idle, RecordGestureSignal.Released)

    @Test
    fun `a hold ignores claims and timeouts and second fingers`() {
        listOf(Claimed, Timeout, Down(0f)).forEach { event -> assertStep(holding, event, holding) }
    }

    @Test
    fun `lifting after the lock lets the recording go on`() = assertStep(RecordGesturePhase.Locked, Up, Idle)

    @Test
    fun `a cancel after the lock lets the recording go on`() = assertStep(RecordGesturePhase.Locked, Cancel, Idle)

    @Test
    fun `a locked gesture ignores moves and claims and timeouts`() {
        listOf(Move(500f), Claimed, Timeout, Down(0f)).forEach { event ->
            assertStep(RecordGesturePhase.Locked, event, RecordGesturePhase.Locked)
        }
    }

    @Test
    fun `every phase answers every event`() {
        val phases = listOf(Idle, pressed, holding, holding.copy(lockProgress = 0.7f), RecordGesturePhase.Locked)
        val events = listOf(Down(200f), Move(150f), Claimed, Timeout, Up, Cancel)
        phases.forEach { phase -> events.forEach { event -> machine.step(phase, event) } }
    }

    @Test
    fun `the lock distance must be positive`() {
        assertFailsWith<IllegalArgumentException> { RecordGestureMachine(lockDistancePx = 0f) }
    }

    private fun assertStep(
        from: RecordGesturePhase,
        event: RecordGestureEvent,
        to: RecordGesturePhase,
        vararg signals: RecordGestureSignal,
    ) {
        assertEquals(RecordGestureStep(to, signals.toList()), machine.step(from, event))
    }
}
```

- [ ] **Step 2: Write the failing GMD gesture test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/RecordGestureTest.kt`:

```kotlin
package app.logdate.wear.e2e

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.logdate.wear.v2.home.HomeScreen
import app.logdate.wear.v2.recorder.gesture.RecordGestureDefaults
import app.logdate.wear.v2.recorder.gesture.RecordGestureListener
import app.logdate.wear.v2.recorder.gesture.recordGesture
import app.logdate.wear.v2.recorder.gesture.startLocked
import app.logdate.wear.v2.theme.LogDateWearTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives Record's gesture with real touch input on a Wear OS emulator, inside the real Home pager.
 * The pager is never locked here, so every check that it stays still proves the gesture owns the
 * touch after the long-press timeout.
 */
@RunWith(AndroidJUnit4::class)
class RecordGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val signals = RecordedSignals()
    private val clicks = Collections.synchronizedList(mutableListOf<String>())

    @Test
    fun a_tap_starts_a_locked_recording_and_never_clicks_the_button() {
        show()

        record {
            down(center)
            advanceEventTime(100)
            up()
        }

        assertEquals(listOf("pressed", "tapped"), signals.list)
        assertTrue(clicks.isEmpty())
    }

    @Test
    fun holding_past_the_long_press_timeout_records_until_the_finger_lifts() {
        show()

        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            up()
        }

        assertEquals(listOf("pressed", "committed", "released"), signals.list)
    }

    @Test
    fun a_lift_just_before_the_long_press_timeout_is_still_a_tap() {
        show()

        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis - 100)
            up()
        }

        assertEquals(listOf("pressed", "tapped"), signals.list)
    }

    @Test
    fun sliding_up_after_the_timeout_locks_the_recording() {
        show()

        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            val step = RecordGestureDefaults.LockDistance.toPx() / 4
            repeat(5) { moveBy(Offset(0f, -step)) }
            up()
        }

        assertEquals(listOf("pressed", "committed"), signals.list.take(2))
        assertTrue("progress 0.5" in signals.list, "signals were ${signals.list}")
        assertEquals("locked", signals.list.last())
        assertFalse("released" in signals.list)
    }

    @Test
    fun a_quick_swipe_up_scrolls_the_pager_and_records_nothing() {
        show()

        record {
            down(center)
            repeat(6) { moveBy(Offset(0f, -40f), delayMillis = 10) }
            up()
        }

        composeRule.waitUntil(TIMEOUT_MS) { isShown(FLOW_TEXT) }
        assertEquals(listOf("pressed", "yielded"), signals.list)
    }

    @Test
    fun after_the_timeout_the_pager_does_not_move() {
        show()

        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(3) { moveBy(Offset(0f, -RecordGestureDefaults.LockDistance.toPx() / 4)) }
            up()
        }

        assertTrue(isShown(TODAY_TEXT))
        assertFalse(isShown(FLOW_TEXT))
        assertEquals("released", signals.list.last())
    }

    @Test
    fun sliding_sideways_off_the_button_does_not_end_the_recording() {
        show()
        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(width.toFloat(), 0f))
            move(delayMillis = 500)
        }
        assertFalse("released" in signals.list)

        record { up() }

        assertEquals("released", signals.list.last())
    }

    @Test
    fun a_touch_the_system_cancels_before_the_timeout_records_nothing() {
        show()

        record {
            down(center)
            advanceEventTime(100)
            cancel()
        }

        assertEquals(listOf("pressed", "yielded"), signals.list)
    }

    @Test
    fun a_touch_the_system_cancels_while_holding_is_saved() {
        show()

        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            cancel()
        }

        assertEquals(listOf("pressed", "committed", "released"), signals.list)
    }

    @Test
    fun talkback_starts_a_locked_recording_by_click_and_by_its_action() {
        show()

        composeRule.onNodeWithTag(RECORD).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag(RECORD).performCustomAccessibilityActionWithLabel(START)
        composeRule.waitForIdle()

        assertEquals(listOf("pressed", "tapped", "pressed", "tapped"), signals.list)
    }

    @Test
    fun record_is_one_control_for_accessibility() {
        show()

        composeRule.onAllNodesWithContentDescription(DESCRIPTION).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(RECORD))).assertCountEquals(0)
    }

    @Test
    fun a_button_that_is_not_armed_clicks_and_records_nothing() {
        show(armed = false)

        composeRule.onNodeWithTag(RECORD).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("click"), clicks.toList())
        assertTrue(signals.list.isEmpty())
    }

    private fun record(gesture: TouchInjectionScope.() -> Unit) {
        composeRule.onNodeWithTag(RECORD).performTouchInput(gesture)
        composeRule.waitForIdle()
    }

    private fun show(armed: Boolean = true) {
        composeRule.setContent {
            LogDateWearTheme(dynamicColor = false) {
                AppScaffold {
                    HomeScreen(
                        pagingLocked = false,
                        today = { listState -> RecordPage(listState, armed, signals) { clicks += "click" } },
                        flow = { listState -> TextPage(listState, FLOW_TEXT) },
                    )
                }
            }
        }
    }

    private fun isShown(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() && composeRule.onNodeWithText(text).isDisplayed()

    private class RecordedSignals : RecordGestureListener {
        private val recorded = Collections.synchronizedList(mutableListOf<String>())

        val list: List<String> get() = recorded.toList()

        override fun onPressed() {
            recorded += "pressed"
        }

        override fun onYielded() {
            recorded += "yielded"
        }

        override fun onTapped() {
            recorded += "tapped"
        }

        override fun onCommitted() {
            recorded += "committed"
        }

        override fun onLockProgress(progress: Float) {
            recorded += "progress $progress"
        }

        override fun onLocked() {
            recorded += "locked"
        }

        override fun onReleased() {
            recorded += "released"
        }
    }

    private companion object {
        const val RECORD = "gesture_record"
        const val DESCRIPTION = "Record"
        const val START = "Start recording"
        const val TODAY_TEXT = "Today"
        const val FLOW_TEXT = "Earlier"
        const val TIMEOUT_MS = 5_000L

        @Composable
        fun RecordPage(
            listState: TransformingLazyColumnState,
            armed: Boolean,
            listener: RecordGestureListener,
            onDisarmedClick: () -> Unit,
        ) {
            val click: () -> Unit = if (armed) listener::startLocked else onDisarmedClick
            ScreenScaffold(
                scrollState = listState,
                edgeButton = {
                    EdgeButton(
                        onClick = click,
                        modifier =
                            Modifier
                                .testTag(RECORD)
                                .recordGesture(
                                    armed = armed,
                                    listener = listener,
                                    description = DESCRIPTION,
                                    startLabel = START,
                                    onClick = click,
                                ),
                        buttonSize = EdgeButtonSize.Medium,
                    ) {
                        Text("●")
                    }
                },
            ) { contentPadding ->
                TransformingLazyColumn(state = listState, contentPadding = contentPadding) { item { Text(TODAY_TEXT) } }
            }
        }

        @Composable
        fun TextPage(
            listState: TransformingLazyColumnState,
            text: String,
        ) {
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(state = listState, contentPadding = contentPadding) { item { Text(text) } }
            }
        }
    }
}
```

- [ ] **Step 3: Run the unit test to verify it fails**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.recorder.gesture.RecordGestureMachineTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'RecordGestureMachine'` (and the other gesture types).

- [ ] **Step 4: Add the state machine**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureMachine.kt`:

```kotlin
package app.logdate.wear.v2.recorder.gesture

/** Where a touch on Record stands. Positions are pixels from the top of the button. */
sealed interface RecordGesturePhase {
    /** No finger down, or the gesture is over. */
    data object Idle : RecordGesturePhase

    /** A finger is down and the long-press timeout hasn't passed; the pager may still take the touch. */
    data class Pressed(
        val downY: Float,
    ) : RecordGesturePhase

    /** Hold to talk: past the timeout, sliding up toward the lock. */
    data class Holding(
        val downY: Float,
        val lockProgress: Float,
    ) : RecordGesturePhase

    /** Slid all the way up: the recording goes on after the finger lifts. */
    data object Locked : RecordGesturePhase
}

/** What happened to the touch. */
sealed interface RecordGestureEvent {
    data class Down(
        val y: Float,
    ) : RecordGestureEvent

    data class Move(
        val y: Float,
    ) : RecordGestureEvent

    /** The pager, or another handler above the button, consumed the touch before the timeout. */
    data object Claimed : RecordGestureEvent

    /** The system long-press timeout passed with the finger still down. */
    data object Timeout : RecordGestureEvent

    data object Up : RecordGestureEvent

    /** The system cancelled the touch, as it does for a palm or a window taking focus. */
    data object Cancel : RecordGestureEvent
}

/** What the gesture tells the recorder; see [RecordGestureListener]. */
sealed interface RecordGestureSignal {
    data object Pressed : RecordGestureSignal

    data object Yielded : RecordGestureSignal

    data object Tapped : RecordGestureSignal

    data object Committed : RecordGestureSignal

    data class LockProgress(
        val progress: Float,
    ) : RecordGestureSignal

    data object Locked : RecordGestureSignal

    data object Released : RecordGestureSignal
}

/** The phase after an event, and what to tell the recorder about it. */
data class RecordGestureStep(
    val phase: RecordGesturePhase,
    val signals: List<RecordGestureSignal> = emptyList(),
)

/**
 * The rules of Record's gesture, apart from any touch system.
 *
 * A finger down presses. Before the long-press timeout, a lift is a tap and a claim by the pager
 * or a cancelled touch yields. At the timeout the gesture commits to hold to talk; sliding up
 * [lockDistancePx] then locks it, and a lift or a cancel before that releases it. After the lock
 * the gesture ignores everything until the finger lifts.
 */
class RecordGestureMachine(
    private val lockDistancePx: Float,
) {
    init {
        require(lockDistancePx > 0f) { "The lock distance must be positive, not $lockDistancePx" }
    }

    fun step(
        phase: RecordGesturePhase,
        event: RecordGestureEvent,
    ): RecordGestureStep =
        when (phase) {
            RecordGesturePhase.Idle -> fromIdle(event)
            is RecordGesturePhase.Pressed -> fromPressed(phase, event)
            is RecordGesturePhase.Holding -> fromHolding(phase, event)
            RecordGesturePhase.Locked -> fromLocked(event)
        }

    private fun fromIdle(event: RecordGestureEvent): RecordGestureStep {
        if (event !is RecordGestureEvent.Down) return RecordGestureStep(RecordGesturePhase.Idle)
        return RecordGestureStep(RecordGesturePhase.Pressed(event.y), listOf(RecordGestureSignal.Pressed))
    }

    private fun fromPressed(
        phase: RecordGesturePhase.Pressed,
        event: RecordGestureEvent,
    ): RecordGestureStep =
        when (event) {
            is RecordGestureEvent.Down, is RecordGestureEvent.Move -> RecordGestureStep(phase)
            RecordGestureEvent.Claimed, RecordGestureEvent.Cancel ->
                RecordGestureStep(RecordGesturePhase.Idle, listOf(RecordGestureSignal.Yielded))
            RecordGestureEvent.Up -> RecordGestureStep(RecordGesturePhase.Idle, listOf(RecordGestureSignal.Tapped))
            RecordGestureEvent.Timeout ->
                RecordGestureStep(RecordGesturePhase.Holding(phase.downY, lockProgress = 0f), listOf(RecordGestureSignal.Committed))
        }

    private fun fromHolding(
        phase: RecordGesturePhase.Holding,
        event: RecordGestureEvent,
    ): RecordGestureStep =
        when (event) {
            is RecordGestureEvent.Down, RecordGestureEvent.Claimed, RecordGestureEvent.Timeout -> RecordGestureStep(phase)
            is RecordGestureEvent.Move -> slide(phase, event.y)
            RecordGestureEvent.Up, RecordGestureEvent.Cancel ->
                RecordGestureStep(RecordGesturePhase.Idle, listOf(RecordGestureSignal.Released))
        }

    private fun slide(
        phase: RecordGesturePhase.Holding,
        y: Float,
    ): RecordGestureStep {
        val progress = ((phase.downY - y) / lockDistancePx).coerceIn(0f, 1f)
        if (progress >= 1f) {
            return RecordGestureStep(
                RecordGesturePhase.Locked,
                listOf(RecordGestureSignal.LockProgress(1f), RecordGestureSignal.Locked),
            )
        }
        if (progress == phase.lockProgress) return RecordGestureStep(phase)
        return RecordGestureStep(phase.copy(lockProgress = progress), listOf(RecordGestureSignal.LockProgress(progress)))
    }

    private fun fromLocked(event: RecordGestureEvent): RecordGestureStep =
        when (event) {
            RecordGestureEvent.Up, RecordGestureEvent.Cancel -> RecordGestureStep(RecordGesturePhase.Idle)
            else -> RecordGestureStep(RecordGesturePhase.Locked)
        }
}
```

- [ ] **Step 5: Add the listener**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureListener.kt`:

```kotlin
package app.logdate.wear.v2.recorder.gesture

/** What Record's gesture tells the recorder, in the order it can happen. */
interface RecordGestureListener {
    /** A finger is down on Record. It may still turn into a scroll. */
    fun onPressed()

    /** The touch became a scroll, or the system cancelled it, before the long-press timeout. */
    fun onYielded()

    /** A tap: start a recording that goes on after the finger lifts. */
    fun onTapped()

    /** Still down at the long-press timeout: hold to talk. */
    fun onCommitted()

    /** How far the finger has slid toward the lock while holding, from 0 to 1. */
    fun onLockProgress(progress: Float)

    /** Slid all the way up: the recording goes on after the finger lifts. */
    fun onLocked()

    /** The finger lifted while holding, or the system cancelled the touch, before the lock: save the recording. */
    fun onReleased()
}

/** The same as a tap. TalkBack's actions and a click on a ready Record run it. */
fun RecordGestureListener.startLocked() {
    onPressed()
    onTapped()
}

internal fun RecordGestureListener.deliver(signal: RecordGestureSignal) {
    when (signal) {
        RecordGestureSignal.Pressed -> onPressed()
        RecordGestureSignal.Yielded -> onYielded()
        RecordGestureSignal.Tapped -> onTapped()
        RecordGestureSignal.Committed -> onCommitted()
        is RecordGestureSignal.LockProgress -> onLockProgress(signal.progress)
        RecordGestureSignal.Locked -> onLocked()
        RecordGestureSignal.Released -> onReleased()
    }
}
```

- [ ] **Step 6: Add the modifier**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureModifier.kt`:

```kotlin
package app.logdate.wear.v2.recorder.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** The measurements of Record's gesture. */
object RecordGestureDefaults {
    /** How far up the finger slides while holding to lock the recording. */
    val LockDistance: Dp = 56.dp
}

/**
 * Hold to talk, slide up to lock, and tap for a recording that is locked from the start.
 *
 * While [armed], the touch is watched on the Initial pass, before the element's own click and
 * before the pager above it, and the down is consumed so the click never fires. Until the system
 * long-press timeout the gesture only watches: if the pager takes the drag, the gesture yields and
 * the pager scrolls. After the timeout it consumes every event, so the pager cannot move, and a
 * finger that slides sideways off the element does not end the recording until it lifts.
 *
 * While not [armed], touches pass through and the element's click does what the button shows, such
 * as Stop. Either way this modifier owns the element's accessibility: TalkBack announces
 * [description], a click runs [onClick], and while armed an action named [startLabel] starts a
 * locked recording.
 */
fun Modifier.recordGesture(
    armed: Boolean,
    listener: RecordGestureListener,
    description: String,
    startLabel: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    lockDistance: Dp = RecordGestureDefaults.LockDistance,
): Modifier = this then RecordGestureElement(armed, listener, description, startLabel, onClick, enabled, lockDistance)

private data class RecordGestureElement(
    val armed: Boolean,
    val listener: RecordGestureListener,
    val description: String,
    val startLabel: String,
    val onClick: () -> Unit,
    val enabled: Boolean,
    val lockDistance: Dp,
) : ModifierNodeElement<RecordGestureNode>() {
    override fun create(): RecordGestureNode = RecordGestureNode(armed, listener, description, startLabel, onClick, enabled, lockDistance)

    override fun update(node: RecordGestureNode) {
        node.update(armed, listener, description, startLabel, onClick, enabled, lockDistance)
    }
}

private class RecordGestureNode(
    private var armed: Boolean,
    private var listener: RecordGestureListener,
    private var description: String,
    private var startLabel: String,
    private var clickAction: () -> Unit,
    private var enabled: Boolean,
    private var lockDistance: Dp,
) : DelegatingNode(),
    PointerInputModifierNode,
    SemanticsModifierNode {
    private val pointerInput = delegate(SuspendingPointerInputModifierNode { detectRecordGesture() })

    override val shouldClearDescendantSemantics: Boolean
        get() = true

    fun update(
        armed: Boolean,
        listener: RecordGestureListener,
        description: String,
        startLabel: String,
        onClick: () -> Unit,
        enabled: Boolean,
        lockDistance: Dp,
    ) {
        val semanticsChanged =
            armed != this.armed || description != this.description || startLabel != this.startLabel || enabled != this.enabled
        this.armed = armed
        this.listener = listener
        this.description = description
        this.startLabel = startLabel
        this.clickAction = onClick
        this.enabled = enabled
        this.lockDistance = lockDistance
        if (semanticsChanged) invalidateSemantics()
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        pointerInput.onPointerEvent(pointerEvent, pass, bounds)
    }

    override fun onCancelPointerInput() {
        pointerInput.onCancelPointerInput()
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        role = Role.Button
        contentDescription = description
        if (!enabled) disabled()
        onClick(label = if (armed) startLabel else null) {
            clickAction()
            true
        }
        if (!armed) return
        customActions =
            listOf(
                CustomAccessibilityAction(startLabel) {
                    listener.startLocked()
                    true
                },
            )
    }

    private suspend fun PointerInputScope.detectRecordGesture() {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!armed || !enabled) return@awaitEachGesture
            down.consume()
            val tracker = GestureTracker(RecordGestureMachine(lockDistance.toPx()), listener)
            tracker.send(RecordGestureEvent.Down(down.position.y))
            val beforeTimeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitDecision(down.id) }
            tracker.send(beforeTimeout ?: RecordGestureEvent.Timeout)
            if (tracker.phase !is RecordGesturePhase.Holding) return@awaitEachGesture
            followAfterTimeout(down.id, tracker)
        }
    }
}

/** The phase of one gesture, stepped through [machine], with every signal passed to [listener]. */
private class GestureTracker(
    private val machine: RecordGestureMachine,
    private val listener: RecordGestureListener,
) {
    var phase: RecordGesturePhase = RecordGesturePhase.Idle
        private set

    fun send(event: RecordGestureEvent) {
        val step = machine.step(phase, event)
        phase = step.phase
        step.signals.forEach(listener::deliver)
    }
}

/**
 * Before the timeout: an up ends the wait (a cancelled touch arrives as an up that is already
 * consumed), and a move the pager consumed on its Main pass shows up consumed on the Final pass.
 */
private suspend fun AwaitPointerEventScope.awaitDecision(pointer: PointerId): RecordGestureEvent {
    while (true) {
        val initial = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == pointer } ?: continue
        if (!initial.pressed) return if (initial.isConsumed) RecordGestureEvent.Cancel else RecordGestureEvent.Up
        val final = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == pointer } ?: continue
        if (final.isConsumed) return RecordGestureEvent.Claimed
    }
}

/** After the timeout: every event is consumed here, so nothing below or above sees the touch. */
private suspend fun AwaitPointerEventScope.followAfterTimeout(
    pointer: PointerId,
    tracker: GestureTracker,
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointer }
        if (change == null || !change.pressed) {
            val cancelled = change?.isConsumed == true
            event.changes.forEach { it.consume() }
            tracker.send(if (cancelled) RecordGestureEvent.Cancel else RecordGestureEvent.Up)
            return
        }
        event.changes.forEach { it.consume() }
        tracker.send(RecordGestureEvent.Move(change.position.y))
    }
}
```

- [ ] **Step 7: Run the unit test to verify it passes**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.recorder.gesture.RecordGestureMachineTest'
```

Expected: `BUILD SUCCESSFUL`, 21 tests passed.

- [ ] **Step 8: Run the GMD gesture test**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.RecordGestureTest
```

Expected: `BUILD SUCCESSFUL`, 12 tests passed. If `a_quick_swipe_up_scrolls_the_pager_and_records_nothing` reports `[pressed]` only, the pager consumed the drag on a pass the Final check does not see; add a `Log` of `final.isConsumed` per event, rerun once, and report the output instead of loosening the test. If `record_is_one_control_for_accessibility` finds two nodes, `EdgeButton` placed its click on a separate merging node; report it rather than removing the assertion.

- [ ] **Step 9: Lint**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintCheck :app:wear:detekt
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task2-commit.txt <<'EOF'
chore(wear): add the hold, lock and tap gesture for Record

Record in the redesigned watch app tells four things apart: a tap
starts a recording that goes on by itself, holding past the system
long-press timeout is hold to talk, sliding up 56 dp while holding
locks the recording, and a quick swipe scrolls the Home pager instead.

Before the timeout the gesture only watches and steps aside when the
pager takes the drag. After it, the gesture takes every touch event,
so the pager stays put and sliding off the button does not end the
recording. A touch the system cancels counts as a lift. TalkBack gets
one Record control with a "Start recording" action.

The rules are a pure state machine with a test for every phase and
event; an emulator test drives real touches through the Home pager.
Nothing uses the gesture yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureMachine.kt app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureListener.kt app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureModifier.kt app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/gesture/RecordGestureMachineTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/RecordGestureTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task2-commit.txt
```

---
### Task 3: Asking for permissions when a feature first needs them (`chore(wear)`)

The spec asks for each permission at its point of use, never up front: the microphone every time recording starts without it, notifications once on the first locked recording, and (Task 9) heart rate once on the first mark. This task adds that plumbing for the new app: which permissions exist, whether one is granted, whether an optional one was already asked for, and a composable that runs the system request and reports granted, denied, or blocked for good (the system won't show its prompt again). It is ordered before the recorder view model, which asks through it, and before Today, which shows the result; nothing asks for anything until Task 5.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/WatchPermission.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/PermissionPrompts.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/PermissionRequester.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/permissions/PermissionPromptsTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/permissions/SharedPreferencesPermissionPromptStoreTest.kt`

**Interfaces:**
- Consumes: `android.content.SharedPreferences`, `ContextCompat.checkSelfPermission`, `ActivityCompat.shouldShowRequestPermissionRationale`, `LocalActivity`.
- Produces (package `app.logdate.wear.v2.permissions`):
  - `enum class WatchPermission(val storedName: String) { MICROPHONE, NOTIFICATIONS; fun manifestName(): String }`.
  - `fun interface PermissionChecker { fun isGranted(permission: WatchPermission): Boolean }`, `class AndroidPermissionChecker(context: Context) : PermissionChecker`.
  - `interface PermissionPromptStore { fun hasAsked(permission): Boolean; fun markAsked(permission) }`, `class SharedPreferencesPermissionPromptStore(preferences: SharedPreferences)` with `PREFERENCES_NAME = "wear_permission_prompts"`.
  - `class PermissionPrompts(checker: PermissionChecker, store: PermissionPromptStore)` with `fun isGranted(permission): Boolean` and `fun takeOptionalPrompt(permission): Boolean`.
  - `enum class PermissionResult { GRANTED, DENIED, BLOCKED }`, `internal fun permissionResult(granted: Boolean, systemWillAskAgain: Boolean): PermissionResult`, `fun interface PermissionRequester { fun request(permission: WatchPermission) }`, `@Composable fun rememberPermissionRequester(onResult: (WatchPermission, PermissionResult) -> Unit): PermissionRequester`.

- [ ] **Step 1: Write the failing policy test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/permissions/PermissionPromptsTest.kt`:

```kotlin
package app.logdate.wear.v2.permissions

import android.Manifest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionPromptsTest {
    private val granted = mutableSetOf<WatchPermission>()
    private val store = InMemoryPromptStore()
    private val prompts = PermissionPrompts(checker = { it in granted }, store = store)

    @Test
    fun `an optional permission that is missing is offered once`() {
        assertTrue(prompts.takeOptionalPrompt(WatchPermission.NOTIFICATIONS))
        assertFalse(prompts.takeOptionalPrompt(WatchPermission.NOTIFICATIONS))
        assertTrue(store.hasAsked(WatchPermission.NOTIFICATIONS))
    }

    @Test
    fun `a granted permission is never offered`() {
        granted += WatchPermission.NOTIFICATIONS

        assertFalse(prompts.takeOptionalPrompt(WatchPermission.NOTIFICATIONS))
        assertFalse(store.hasAsked(WatchPermission.NOTIFICATIONS))
    }

    @Test
    fun `each permission is offered on its own`() {
        assertTrue(prompts.takeOptionalPrompt(WatchPermission.NOTIFICATIONS))
        assertTrue(prompts.takeOptionalPrompt(WatchPermission.MICROPHONE))
    }

    @Test
    fun `granted follows the checker`() {
        assertFalse(prompts.isGranted(WatchPermission.MICROPHONE))
        granted += WatchPermission.MICROPHONE
        assertTrue(prompts.isGranted(WatchPermission.MICROPHONE))
    }

    @Test
    fun `a refusal the system will ask about again is a denial`() {
        assertEquals(PermissionResult.GRANTED, permissionResult(granted = true, systemWillAskAgain = false))
        assertEquals(PermissionResult.DENIED, permissionResult(granted = false, systemWillAskAgain = true))
        assertEquals(PermissionResult.BLOCKED, permissionResult(granted = false, systemWillAskAgain = false))
    }

    @Test
    fun `each permission has its system name`() {
        assertEquals(Manifest.permission.RECORD_AUDIO, WatchPermission.MICROPHONE.manifestName())
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, WatchPermission.NOTIFICATIONS.manifestName())
    }

    private class InMemoryPromptStore : PermissionPromptStore {
        private val asked = mutableSetOf<WatchPermission>()

        override fun hasAsked(permission: WatchPermission): Boolean = permission in asked

        override fun markAsked(permission: WatchPermission) {
            asked += permission
        }
    }
}
```

- [ ] **Step 2: Write the failing store test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/permissions/SharedPreferencesPermissionPromptStoreTest.kt`:

```kotlin
package app.logdate.wear.v2.permissions

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPreferencesPermissionPromptStoreTest {
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val preferences = mockk<SharedPreferences> { every { edit() } returns editor }
    private val store = SharedPreferencesPermissionPromptStore(preferences)

    @Test
    fun `nothing saved means not asked`() {
        every { preferences.getBoolean("asked_notifications", false) } returns false

        assertFalse(store.hasAsked(WatchPermission.NOTIFICATIONS))
    }

    @Test
    fun `a saved ask is read back`() {
        every { preferences.getBoolean("asked_notifications", false) } returns true

        assertTrue(store.hasAsked(WatchPermission.NOTIFICATIONS))
    }

    @Test
    fun `marking a permission asked saves it`() {
        store.markAsked(WatchPermission.MICROPHONE)

        verify { editor.putBoolean("asked_microphone", true) }
        verify { editor.apply() }
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.permissions.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'PermissionPrompts'`, `'WatchPermission'`, `'permissionResult'` and `'SharedPreferencesPermissionPromptStore'`.

- [ ] **Step 4: Add the permissions**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/WatchPermission.kt`:

```kotlin
package app.logdate.wear.v2.permissions

import android.Manifest

/**
 * A runtime permission the new watch app asks for when a feature first needs it.
 *
 * @property storedName How [PermissionPromptStore] records that it was asked for. Never changes.
 */
enum class WatchPermission(
    val storedName: String,
) {
    /** Recording. Asked each time recording starts without it. */
    MICROPHONE("microphone"),

    /** A recording's Ongoing Activity and its Stop control. Asked once, on the first locked recording. */
    NOTIFICATIONS("notifications"),
    ;

    /** The name the system knows this permission by. */
    fun manifestName(): String =
        when (this) {
            MICROPHONE -> Manifest.permission.RECORD_AUDIO
            NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
        }
}
```

- [ ] **Step 5: Add the checker, the store and the policy**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/PermissionPrompts.kt`:

```kotlin
package app.logdate.wear.v2.permissions

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/** Whether a permission is granted right now. */
fun interface PermissionChecker {
    fun isGranted(permission: WatchPermission): Boolean
}

/** [PermissionChecker] over the system's own record. */
class AndroidPermissionChecker(
    private val context: Context,
) : PermissionChecker {
    override fun isGranted(permission: WatchPermission): Boolean =
        ContextCompat.checkSelfPermission(context, permission.manifestName()) == PackageManager.PERMISSION_GRANTED
}

/** Remembers which optional permissions were already asked for, so each is asked at most once. */
interface PermissionPromptStore {
    fun hasAsked(permission: WatchPermission): Boolean

    fun markAsked(permission: WatchPermission)
}

/** [PermissionPromptStore] in the watch's own SharedPreferences file, [PREFERENCES_NAME]. */
class SharedPreferencesPermissionPromptStore(
    private val preferences: SharedPreferences,
) : PermissionPromptStore {
    override fun hasAsked(permission: WatchPermission): Boolean = preferences.getBoolean(key(permission), false)

    override fun markAsked(permission: WatchPermission) {
        preferences.edit { putBoolean(key(permission), true) }
    }

    private fun key(permission: WatchPermission): String = "asked_${permission.storedName}"

    companion object {
        const val PREFERENCES_NAME = "wear_permission_prompts"
    }
}

/**
 * When to ask for a permission. One a feature can't work without (the microphone to record) is
 * asked each time it is missing at the point of use; check it with [isGranted]. An optional one
 * (notifications for the recording on the watch face) is asked once, the first time its feature
 * runs without it; [takeOptionalPrompt] decides.
 */
class PermissionPrompts(
    private val checker: PermissionChecker,
    private val store: PermissionPromptStore,
) {
    fun isGranted(permission: WatchPermission): Boolean = checker.isGranted(permission)

    /** True once, when [permission] is missing and was never asked for; it counts as asked from then on. */
    fun takeOptionalPrompt(permission: WatchPermission): Boolean {
        if (checker.isGranted(permission)) return false
        if (store.hasAsked(permission)) return false
        store.markAsked(permission)
        return true
    }
}
```

- [ ] **Step 6: Add the requester**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/PermissionRequester.kt`:

```kotlin
package app.logdate.wear.v2.permissions

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat

/** How a permission request ended. */
enum class PermissionResult {
    GRANTED,

    /** Refused this time; asking again shows the system prompt. */
    DENIED,

    /** Refused for good: the system won't show its prompt again, and only Settings can grant it. */
    BLOCKED,
}

/** Asks the system for a permission; the answer goes to the callback given to [rememberPermissionRequester]. */
fun interface PermissionRequester {
    fun request(permission: WatchPermission)
}

internal fun permissionResult(
    granted: Boolean,
    systemWillAskAgain: Boolean,
): PermissionResult =
    when {
        granted -> PermissionResult.GRANTED
        systemWillAskAgain -> PermissionResult.DENIED
        else -> PermissionResult.BLOCKED
    }

/**
 * A [PermissionRequester] for this screen. The permission being asked for survives the activity
 * being recreated while the system prompt is up.
 */
@Composable
fun rememberPermissionRequester(onResult: (WatchPermission, PermissionResult) -> Unit): PermissionRequester {
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    var pending by rememberSaveable { mutableStateOf<WatchPermission?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val permission = pending ?: return@rememberLauncherForActivityResult
            pending = null
            val askAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission.manifestName())
            currentOnResult(permission, permissionResult(granted, askAgain))
        }
    return remember(launcher) {
        PermissionRequester { permission ->
            pending = permission
            launcher.launch(permission.manifestName())
        }
    }
}
```

- [ ] **Step 7: Bind the permissions in Koin**

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add these imports in ASCII order with the existing ones:

```kotlin
import app.logdate.wear.v2.permissions.AndroidPermissionChecker
import app.logdate.wear.v2.permissions.PermissionChecker
import app.logdate.wear.v2.permissions.PermissionPromptStore
import app.logdate.wear.v2.permissions.PermissionPrompts
import app.logdate.wear.v2.permissions.SharedPreferencesPermissionPromptStore
```

and add these definitions right after the `single<LogDateHaptics> { ... }` block:

```kotlin
        single<PermissionChecker> { AndroidPermissionChecker(get()) }
        single<PermissionPromptStore> {
            SharedPreferencesPermissionPromptStore(
                get<Context>().getSharedPreferences(SharedPreferencesPermissionPromptStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single { PermissionPrompts(checker = get(), store = get()) }
```

- [ ] **Step 8: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.permissions.*' && ./gradlew :app:wear:ktlintCheck :app:wear:detekt
```

Expected: `BUILD SUCCESSFUL` twice, 9 tests passed.

- [ ] **Step 9: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task3-commit.txt <<'EOF'
chore(wear): ask for watch permissions when a feature first needs them

The redesigned watch app asks for a permission at the moment a
feature needs it instead of during onboarding. A permission recording
can't work without, the microphone, is asked each time it is missing.
An optional one, such as notifications for the recording on the watch
face, is asked once and never again.

A request reports whether the permission was granted, refused, or
refused for good, so the screen can point to Settings only when the
system will no longer show its own prompt. Nothing asks yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 10: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/WatchPermission.kt app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/PermissionPrompts.kt app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/PermissionRequester.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/v2/permissions/PermissionPromptsTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/permissions/SharedPreferencesPermissionPromptStoreTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task3-commit.txt
```

---
### Task 4: The recorder view model (`chore(wear)`)

`RecorderViewModel` is the placeholder from the shell plan; this task makes it the recorder. It implements the gesture's listener and the controls Today will draw, and follows the rules the spec sets:

- A press starts the recorder speculatively, so the first word is never lost, and shows nothing (Arming).
- A tap locks it; holding past the timeout is hold to talk (Live HOLDING), sliding up locks it (Live LOCKED).
- A scroll before the timeout discards the recording silently, including when the start is still running.
- Hold to talk saves when the finger lifts; a locked recording saves on Stop. Pause and Discard (two taps) work on a locked one.
- After a save, Undo works for five seconds and the five feelings show for four (`AfterSaveController`, extended here).
- The microphone is checked at the press and asked for when the gesture decides; notifications are offered once, on the first locked recording.
- Every failure is a `Failed` state with its reason, and a recording still running when the activity finishes is saved on the application scope.

The feelings are saved by a new `QuickNoteWriter` in the same `#mood:` form the check-in screen writes. Today does not draw any of this until Task 5.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/capture/QuickNoteWriter.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/recording/AfterSaveController.kt` (whole file)
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderState.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderActions.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderViewModel.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`
- Create (test helpers): `app/wear/src/test/kotlin/app/logdate/wear/recording/FakeWearRecorder.kt`, `app/wear/src/test/kotlin/app/logdate/wear/v2/haptics/RecordedHaptics.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/capture/QuickNoteWriterTest.kt`
- Modify (test): `app/wear/src/test/kotlin/app/logdate/wear/recording/AfterSaveControllerTest.kt` (whole file)
- Modify (test): `app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecorderStateTest.kt` (whole file)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecorderViewModelTest.kt`

**Interfaces:**
- Consumes: `WearRecorder`, `AudioMomentSaver`, `AfterSaveController` (Task 1), `RecordGestureListener` (Task 2), `PermissionPrompts`, `WatchPermission`, `PermissionResult` (Task 3), `LogDateHaptics`, `MomentNoteCodec.encode(Feeling)`.
- Produces:
  - `fun interface FeelingSaver { suspend fun save(feeling: Feeling): Boolean; companion object { val None: FeelingSaver } }` and `class QuickNoteWriter(notesRepository, locationCaptureCoordinator, clock = Clock.System) : FeelingSaver` in `app.logdate.wear.capture`.
  - `data class AfterSaveWindow(val noteId: Uuid, val feelingsOffered: Boolean = false, val feeling: Feeling? = null)`; `AfterSaveController(scope, notesRepository, removalNotifier, deleteFile = ..., feelingSaver: FeelingSaver = FeelingSaver.None)` gains `fun open(noteId: Uuid, offerFeelings: Boolean = false)`, `suspend fun chooseFeeling(feeling: Feeling): Boolean`, `const val FEELINGS_WINDOW_MS = 4_000L`.
  - `sealed interface RecorderState { Ready; Arming; Live(mode, paused, pausedByInterruption, lockProgress, elapsed, levels, confirmingDiscard); Saving; Saved(noteId, duration, feelingsOffered, feeling); TooShort; Failed(failure) }`, `enum class LiveMode { HOLDING, LOCKED }`, `enum class RecorderFailure { MICROPHONE_DENIED, MICROPHONE_BLOCKED, NOT_ENOUGH_STORAGE, RECORDER_UNAVAILABLE, SAVE_FAILED, RECORDING_LOST }`, `val RecorderState.locksPaging: Boolean`, `val RecorderState.canStartRecording: Boolean`.
  - `interface RecorderActions { stop(); togglePause(); discard(); undo(); chooseFeeling(feeling); retry(); dismiss() }`.
  - `class RecorderViewModel(recorder, saver, notesRepository, removalNotifier, feelingSaver, haptics, permissions, applicationScope, deleteFile = ...) : ViewModel(), RecordGestureListener, RecorderActions` with `val state: StateFlow<RecorderState>`, `val permissionRequests: Flow<WatchPermission>`, `fun onPermissionResult(permission: WatchPermission, result: PermissionResult)`, `TOO_SHORT_DISPLAY_MS = 1_200L`, `DISCARD_CONFIRM_MS = 3_000L`.

- [ ] **Step 1: Add the test helpers**

Create `app/wear/src/test/kotlin/app/logdate/wear/recording/FakeWearRecorder.kt`:

```kotlin
package app.logdate.wear.recording

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** A [WearRecorder] for tests: each call changes its flows at once and is counted. */
class FakeWearRecorder : WearRecorder {
    val recordingFlow = MutableStateFlow(false)
    val pausedFlow = MutableStateFlow(false)
    val interruptedFlow = MutableStateFlow(false)
    val levelFlow = MutableStateFlow(0f)
    val elapsedFlow = MutableStateFlow(Duration.ZERO)

    override val isRecording: StateFlow<Boolean> = recordingFlow
    override val isPaused: StateFlow<Boolean> = pausedFlow
    override val pausedByInterruption: StateFlow<Boolean> = interruptedFlow
    override val audioLevel: StateFlow<Float> = levelFlow
    override val elapsed: StateFlow<Duration> = elapsedFlow
    override var lastStartFailure: RecordingStartFailure? = null

    var startResult = true
    var startDelay: Duration = Duration.ZERO
    var stopPath: String? = DEFAULT_PATH
    var starts = 0
    var stops = 0
    var resumes = 0
    var discards = 0
    var stopRequested = false

    override suspend fun start(): Boolean {
        starts++
        if (startDelay > Duration.ZERO) delay(startDelay)
        if (startResult) recordingFlow.value = true
        return startResult
    }

    override suspend fun stop(): String? {
        stops++
        recordingFlow.value = false
        pausedFlow.value = false
        return stopPath
    }

    override suspend fun pause(): Boolean {
        pausedFlow.value = true
        return true
    }

    override suspend fun resume(): Boolean {
        resumes++
        pausedFlow.value = false
        interruptedFlow.value = false
        return true
    }

    override suspend fun discard() {
        discards++
        recordingFlow.value = false
        pausedFlow.value = false
    }

    override fun requestStop() {
        stopRequested = true
    }

    companion object {
        const val DEFAULT_PATH = "/files/audio_notes/recording_fake.m4a"
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/haptics/RecordedHaptics.kt`:

```kotlin
package app.logdate.wear.v2.haptics

import app.logdate.ui.platform.LogDateHaptics

/** A [LogDateHaptics] that records the name of each event it is asked to play. */
class RecordedHaptics : LogDateHaptics {
    val played = mutableListOf<String>()

    override fun saveSucceeded() {
        played += "saveSucceeded"
    }

    override fun recordingStarted() {
        played += "recordingStarted"
    }

    override fun recordingFinished() {
        played += "recordingFinished"
    }

    override fun transcriptionReady() {
        played += "transcriptionReady"
    }

    override fun confirmDestruction() {
        played += "confirmDestruction"
    }

    override fun recordingLocked() {
        played += "recordingLocked"
    }

    override fun recordingPaused() {
        played += "recordingPaused"
    }

    override fun recordingResumed() {
        played += "recordingResumed"
    }

    override fun momentMarked() {
        played += "momentMarked"
    }

    override fun limitApproaching() {
        played += "limitApproaching"
    }

    override fun actionRejected() {
        played += "actionRejected"
    }

    override fun detentTick() {
        played += "detentTick"
    }
}
```

- [ ] **Step 2: Write the failing check-in writer test**

Create `app/wear/src/test/kotlin/app/logdate/wear/capture/QuickNoteWriterTest.kt`:

```kotlin
package app.logdate.wear.capture

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.location.WearLocationCaptureCoordinator
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class QuickNoteWriterTest {
    private val notesRepository = mockk<JournalNotesRepository>()
    private val locationCaptureCoordinator = mockk<WearLocationCaptureCoordinator>()
    private val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
    private val created = slot<JournalNote>()

    @Before
    fun setUp() {
        coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns null
        coEvery { notesRepository.create(capture(created)) } returns Uuid.random()
    }

    @Test
    fun `a feeling is saved as a mood check-in note`() =
        runTest {
            assertTrue(writer().save(Feeling.GOOD))

            val note = created.captured as JournalNote.Text
            assertEquals(MomentNoteCodec.encode(Feeling.GOOD), note.content)
            assertEquals(now, note.creationTimestamp)
        }

    @Test
    fun `the place is saved with the check-in`() =
        runTest {
            val location = NoteLocation(coordinates = NoteCoordinates(latitude = 51.5, longitude = -0.12))
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns location

            writer().save(Feeling.GREAT)

            assertEquals(location, created.captured.location)
        }

    @Test
    fun `a check-in that cannot be stored reports false`() =
        runTest {
            coEvery { notesRepository.create(any<JournalNote>()) } throws IllegalStateException("database closed")

            assertFalse(writer().save(Feeling.SAD))
        }

    private fun writer() =
        QuickNoteWriter(
            notesRepository = notesRepository,
            locationCaptureCoordinator = locationCaptureCoordinator,
            clock = FixedClock(now),
        )

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }
}
```

- [ ] **Step 3: Replace the undo window test with one that covers the feelings**

Replace the whole of `app/wear/src/test/kotlin/app/logdate/wear/recording/AfterSaveControllerTest.kt` with:

```kotlin
package app.logdate.wear.recording

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.capture.Feeling
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [AfterSaveController]: the seconds after a save, what Undo does in them, and the five feelings. */
@OptIn(ExperimentalCoroutinesApi::class)
class AfterSaveControllerTest {
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val removedNotes = mutableListOf<Uuid>()
    private val deletedFiles = mutableListOf<String>()
    private val savedFeelings = mutableListOf<Feeling>()
    private var feelingSaves = true
    private val noteId = Uuid.random()

    @Test
    fun `a window opens for the saved note and closes after five seconds`() =
        runTest {
            val controller = controller()

            controller.open(noteId)
            advanceTimeBy(AfterSaveController.UNDO_WINDOW_MS - 1)
            runCurrent()
            assertEquals(AfterSaveWindow(noteId), controller.window.value)

            advanceTimeBy(2)
            runCurrent()
            assertNull(controller.window.value)
        }

    @Test
    fun `opening again restarts the five seconds`() =
        runTest {
            val controller = controller()
            controller.open(noteId)
            advanceTimeBy(4_000)
            val second = Uuid.random()

            controller.open(second)
            advanceTimeBy(4_000)
            runCurrent()

            assertEquals(AfterSaveWindow(second), controller.window.value)
        }

    @Test
    fun `closing ends the window and leaves the note alone`() =
        runTest {
            val controller = controller()
            controller.open(noteId)

            controller.close()

            assertNull(controller.window.value)
            coVerify(exactly = 0) { notesRepository.removeById(any()) }
        }

    @Test
    fun `undo removes the note and its audio and tells the phone`() =
        runTest {
            coEvery { notesRepository.getNoteById(noteId) } returnsMany listOf(audio("/files/audio_notes/u.m4a"), null)
            val controller = controller()
            controller.open(noteId)

            assertTrue(controller.undo(noteId))

            coVerify { notesRepository.removeById(noteId) }
            assertEquals(listOf("/files/audio_notes/u.m4a"), deletedFiles)
            assertEquals(listOf(noteId), removedNotes)
            assertNull(controller.window.value)
        }

    @Test
    fun `undo leaves the audio while a note still refers to it`() =
        runTest {
            coEvery { notesRepository.getNoteById(noteId) } returns audio("/files/audio_notes/shared.m4a")

            assertTrue(controller().undo(noteId))

            assertTrue(deletedFiles.isEmpty())
        }

    @Test
    fun `a removal that fails reports false and tells the phone nothing`() =
        runTest {
            coEvery { notesRepository.removeById(noteId) } throws IllegalStateException("database closed")

            assertFalse(controller().undo(noteId))

            assertTrue(removedNotes.isEmpty())
        }

    @Test
    fun `offered feelings go after four seconds and Undo stays until five`() =
        runTest {
            val controller = controller()

            controller.open(noteId, offerFeelings = true)
            assertEquals(AfterSaveWindow(noteId, feelingsOffered = true), controller.window.value)
            advanceTimeBy(AfterSaveController.FEELINGS_WINDOW_MS + 1)
            runCurrent()

            assertEquals(AfterSaveWindow(noteId, feelingsOffered = false), controller.window.value)
        }

    @Test
    fun `choosing a feeling saves a check-in and shows which`() =
        runTest {
            val controller = controller()
            controller.open(noteId, offerFeelings = true)

            assertTrue(controller.chooseFeeling(Feeling.GOOD))

            assertEquals(listOf(Feeling.GOOD), savedFeelings)
            assertEquals(AfterSaveWindow(noteId, feelingsOffered = false, feeling = Feeling.GOOD), controller.window.value)
        }

    @Test
    fun `a feeling that cannot be saved leaves the five feelings offered`() =
        runTest {
            feelingSaves = false
            val controller = controller()
            controller.open(noteId, offerFeelings = true)

            assertFalse(controller.chooseFeeling(Feeling.GOOD))

            assertEquals(AfterSaveWindow(noteId, feelingsOffered = true), controller.window.value)
        }

    @Test
    fun `no feeling is saved once the feelings are gone`() =
        runTest {
            val controller = controller()
            controller.open(noteId, offerFeelings = true)
            advanceTimeBy(AfterSaveController.FEELINGS_WINDOW_MS + 1)
            runCurrent()

            assertFalse(controller.chooseFeeling(Feeling.GOOD))

            assertTrue(savedFeelings.isEmpty())
        }

    private fun TestScope.controller() =
        AfterSaveController(
            scope = backgroundScope,
            notesRepository = notesRepository,
            removalNotifier = { removedNotes += it },
            deleteFile = { deletedFiles += it },
            feelingSaver = { feeling ->
                if (feelingSaves) savedFeelings += feeling
                feelingSaves
            },
        )

    private fun audio(path: String): JournalNote.Audio {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        return JournalNote.Audio(mediaRef = path, uid = noteId, creationTimestamp = now, lastUpdated = now, durationMs = 4_200)
    }
}
```

- [ ] **Step 4: Replace the recorder state test**

Replace the whole of `app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecorderStateTest.kt` with:

```kotlin
package app.logdate.wear.v2.recorder

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class RecorderStateTest {
    private val saved = RecorderState.Saved(noteId = Uuid.random(), duration = 4.seconds, feelingsOffered = true)

    @Test
    fun `only a live recording locks Home on Today`() {
        assertTrue(RecorderState.Live(LiveMode.HOLDING).locksPaging)
        assertTrue(RecorderState.Live(LiveMode.LOCKED).locksPaging)
        listOf(
            RecorderState.Ready,
            RecorderState.Arming,
            RecorderState.Saving,
            saved,
            RecorderState.TooShort,
            RecorderState.Failed(RecorderFailure.SAVE_FAILED),
        ).forEach { state -> assertFalse(state.locksPaging, "$state") }
    }

    @Test
    fun `Record starts a recording when nothing is under way and no save is waiting`() {
        listOf(
            RecorderState.Ready,
            saved,
            RecorderState.TooShort,
            RecorderState.Failed(RecorderFailure.RECORDING_LOST),
            RecorderState.Failed(RecorderFailure.MICROPHONE_DENIED),
        ).forEach { state -> assertTrue(state.canStartRecording, "$state") }
        listOf(
            RecorderState.Arming,
            RecorderState.Live(LiveMode.LOCKED),
            RecorderState.Saving,
            RecorderState.Failed(RecorderFailure.SAVE_FAILED),
        ).forEach { state -> assertFalse(state.canStartRecording, "$state") }
    }
}
```

- [ ] **Step 5: Write the failing recorder test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecorderViewModelTest.kt`:

```kotlin
package app.logdate.wear.v2.recorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.capture.Feeling
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.recording.AudioMomentSaver
import app.logdate.wear.recording.FakeWearRecorder
import app.logdate.wear.recording.RecordingStartFailure
import app.logdate.wear.v2.haptics.RecordedHaptics
import app.logdate.wear.v2.permissions.PermissionPromptStore
import app.logdate.wear.v2.permissions.PermissionPrompts
import app.logdate.wear.v2.permissions.PermissionResult
import app.logdate.wear.v2.permissions.WatchPermission
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [RecorderViewModel] through the signals the record gesture sends and the controls Today
 * shows. Nothing recorded may be dropped silently, so every failure is a specific state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecorderViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val recorder = FakeWearRecorder()
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val durationResolver = mockk<AudioDurationResolver>()
    private val locationCaptureCoordinator = mockk<WearLocationCaptureCoordinator>()
    private val haptics = RecordedHaptics()
    private val granted = mutableSetOf(WatchPermission.MICROPHONE, WatchPermission.NOTIFICATIONS)
    private val asked = mutableSetOf<WatchPermission>()
    private val savedFeelings = mutableListOf<Feeling>()
    private val removedNotes = mutableListOf<Uuid>()
    private val deletedFiles = mutableListOf<String>()
    private val viewModelStore = ViewModelStore()
    private val applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { durationResolver.resolveDurationMs(any()) } returns 4_200L
        coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns null
        coEvery { notesRepository.create(any<JournalNote>()) } returns Uuid.random()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        applicationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `the recorder starts ready`() = runTest { assertEquals(RecorderState.Ready, createViewModel().state.value) }

    @Test
    fun `a press starts the recorder at once but shows nothing yet`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onPressed()
            runCurrent()

            assertEquals(RecorderState.Arming, viewModel.state.value)
            assertEquals(1, recorder.starts)
            assertTrue(haptics.played.isEmpty())
        }

    @Test
    fun `a tap makes it a locked recording`() =
        runTest {
            val viewModel = createViewModel()

            tap(viewModel)

            val live = assertIs<RecorderState.Live>(viewModel.state.value)
            assertEquals(LiveMode.LOCKED, live.mode)
            assertEquals(1f, live.lockProgress)
            assertEquals(listOf("recordingStarted"), haptics.played)
        }

    @Test
    fun `holding past the timeout is hold to talk and sliding up locks it`() =
        runTest {
            val viewModel = createViewModel()
            hold(viewModel)
            assertEquals(LiveMode.HOLDING, assertIs<RecorderState.Live>(viewModel.state.value).mode)

            viewModel.onLockProgress(0.6f)
            assertEquals(0.6f, assertIs<RecorderState.Live>(viewModel.state.value).lockProgress)

            viewModel.onLocked()
            assertEquals(LiveMode.LOCKED, assertIs<RecorderState.Live>(viewModel.state.value).mode)
            assertEquals(listOf("recordingStarted", "recordingLocked"), haptics.played)
        }

    @Test
    fun `lifting the finger while holding saves the recording`() =
        runTest {
            val viewModel = createViewModel()
            hold(viewModel)

            viewModel.onReleased()
            runCurrent()

            val saved = assertIs<RecorderState.Saved>(viewModel.state.value)
            assertEquals(4_200.milliseconds, saved.duration)
            assertTrue(saved.feelingsOffered)
            assertEquals(1, recorder.stops)
            assertEquals(listOf("recordingStarted", "recordingFinished", "saveSucceeded"), haptics.played)
        }

    @Test
    fun `a scroll before the timeout discards the recording without a trace`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onPressed()
            runCurrent()

            viewModel.onYielded()
            runCurrent()

            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertEquals(1, recorder.discards)
            assertTrue(haptics.played.isEmpty())
            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    @Test
    fun `a scroll while the recorder is still starting discards it once it has started`() =
        runTest {
            recorder.startDelay = 500.milliseconds
            val viewModel = createViewModel()
            viewModel.onPressed()
            advanceTimeBy(100)

            viewModel.onYielded()
            viewModel.onPressed()
            assertEquals(RecorderState.Arming, viewModel.state.value)

            advanceTimeBy(600)
            runCurrent()
            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertEquals(1, recorder.starts)
            assertEquals(1, recorder.discards)
        }

    @Test
    fun `lifting before the recorder confirms still saves`() =
        runTest {
            recorder.startDelay = 1.seconds
            val viewModel = createViewModel()
            viewModel.onPressed()
            advanceTimeBy(100)
            viewModel.onCommitted()
            viewModel.onReleased()

            advanceTimeBy(1_100)
            runCurrent()

            assertIs<RecorderState.Saved>(viewModel.state.value)
            assertEquals(1, recorder.stops)
        }

    @Test
    fun `without the microphone a press starts nothing and the decision asks for it`() =
        runTest {
            granted -= WatchPermission.MICROPHONE
            val viewModel = createViewModel()
            val requests = collectRequests(viewModel)

            viewModel.onPressed()
            viewModel.onTapped()
            runCurrent()

            assertEquals(0, recorder.starts)
            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertEquals(listOf(WatchPermission.MICROPHONE), requests)
        }

    @Test
    fun `granting the microphone starts a locked recording`() =
        runTest {
            granted -= WatchPermission.MICROPHONE
            val viewModel = createViewModel()
            viewModel.onPressed()
            viewModel.onCommitted()
            granted += WatchPermission.MICROPHONE

            viewModel.onPermissionResult(WatchPermission.MICROPHONE, PermissionResult.GRANTED)
            runCurrent()

            assertEquals(LiveMode.LOCKED, assertIs<RecorderState.Live>(viewModel.state.value).mode)
            assertEquals(1, recorder.starts)
        }

    @Test
    fun `refusing the microphone explains it and blocking it points to Settings`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onPermissionResult(WatchPermission.MICROPHONE, PermissionResult.DENIED)
            assertEquals(RecorderState.Failed(RecorderFailure.MICROPHONE_DENIED), viewModel.state.value)

            viewModel.onPermissionResult(WatchPermission.MICROPHONE, PermissionResult.BLOCKED)
            assertEquals(RecorderState.Failed(RecorderFailure.MICROPHONE_BLOCKED), viewModel.state.value)
        }

    @Test
    fun `trying again after a refusal asks for the microphone again`() =
        runTest {
            val viewModel = createViewModel()
            val requests = collectRequests(viewModel)
            viewModel.onPermissionResult(WatchPermission.MICROPHONE, PermissionResult.DENIED)

            viewModel.retry()
            runCurrent()

            assertEquals(listOf(WatchPermission.MICROPHONE), requests)
        }

    @Test
    fun `a scroll without the microphone asks for nothing`() =
        runTest {
            granted -= WatchPermission.MICROPHONE
            val viewModel = createViewModel()
            val requests = collectRequests(viewModel)

            viewModel.onPressed()
            viewModel.onYielded()
            runCurrent()

            assertTrue(requests.isEmpty())
        }

    @Test
    fun `the first locked recording asks for notifications once`() =
        runTest {
            granted -= WatchPermission.NOTIFICATIONS
            val viewModel = createViewModel()
            val requests = collectRequests(viewModel)

            recordAndStop(viewModel)
            tap(viewModel)
            runCurrent()

            assertEquals(listOf(WatchPermission.NOTIFICATIONS), requests)
        }

    @Test
    fun `a refused start shows its reason once the gesture decides`() =
        runTest {
            recorder.startResult = false
            recorder.lastStartFailure = RecordingStartFailure.NOT_ENOUGH_STORAGE
            val viewModel = createViewModel()
            viewModel.onPressed()
            runCurrent()
            assertEquals(RecorderState.Arming, viewModel.state.value)

            viewModel.onCommitted()

            assertEquals(RecorderState.Failed(RecorderFailure.NOT_ENOUGH_STORAGE), viewModel.state.value)
            assertEquals(listOf("actionRejected"), haptics.played)
        }

    @Test
    fun `a refused start under a scroll shows nothing`() =
        runTest {
            recorder.startResult = false
            recorder.lastStartFailure = RecordingStartFailure.RECORDER_UNAVAILABLE
            val viewModel = createViewModel()
            viewModel.onPressed()
            runCurrent()

            viewModel.onYielded()

            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertTrue(haptics.played.isEmpty())
        }

    @Test
    fun `stop saves a locked recording but not hold to talk`() =
        runTest {
            val viewModel = createViewModel()
            hold(viewModel)
            viewModel.stop()
            runCurrent()
            assertIs<RecorderState.Live>(viewModel.state.value)

            viewModel.onLocked()
            viewModel.stop()
            runCurrent()

            assertIs<RecorderState.Saved>(viewModel.state.value)
        }

    @Test
    fun `pause and resume follow the recorder`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            viewModel.togglePause()
            runCurrent()
            assertTrue(assertIs<RecorderState.Live>(viewModel.state.value).paused)

            viewModel.togglePause()
            runCurrent()
            assertFalse(assertIs<RecorderState.Live>(viewModel.state.value).paused)
            assertEquals(listOf("recordingStarted", "recordingPaused", "recordingResumed"), haptics.played)
        }

    @Test
    fun `a pause another app causes is reported as one`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            recorder.pausedFlow.value = true
            recorder.interruptedFlow.value = true
            runCurrent()

            val live = assertIs<RecorderState.Live>(viewModel.state.value)
            assertTrue(live.paused)
            assertTrue(live.pausedByInterruption)
        }

    @Test
    fun `discarding takes two taps and leaves no note`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            viewModel.discard()
            assertTrue(assertIs<RecorderState.Live>(viewModel.state.value).confirmingDiscard)
            viewModel.discard()
            runCurrent()

            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertEquals(1, recorder.discards)
            assertEquals(0, recorder.stops)
            assertEquals("confirmDestruction", haptics.played.last())
            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    @Test
    fun `the discard question goes away after three seconds`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)
            viewModel.discard()

            advanceTimeBy(RecorderViewModel.DISCARD_CONFIRM_MS + 1)
            runCurrent()

            assertFalse(assertIs<RecorderState.Live>(viewModel.state.value).confirmingDiscard)
            assertEquals(0, recorder.discards)
        }

    @Test
    fun `a recording the recorder ends on its own is saved`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            recorder.recordingFlow.value = false
            runCurrent()

            assertIs<RecorderState.Saved>(viewModel.state.value)
            assertEquals(1, recorder.stops)
        }

    @Test
    fun `a recording too short to save says so and goes back to ready`() =
        runTest {
            coEvery { durationResolver.resolveDurationMs(any()) } returns 300L
            val viewModel = createViewModel()

            recordAndStop(viewModel)
            assertEquals(RecorderState.TooShort, viewModel.state.value)
            assertEquals("actionRejected", haptics.played.last())

            advanceTimeBy(RecorderViewModel.TOO_SHORT_DISPLAY_MS + 100)
            runCurrent()
            assertEquals(RecorderState.Ready, viewModel.state.value)
        }

    @Test
    fun `a save that fails is tried again on the same file`() =
        runTest {
            var failures = 1
            val created = mutableListOf<JournalNote>()
            coEvery { notesRepository.create(any<JournalNote>()) } answers {
                if (failures-- > 0) throw IllegalStateException("database closed")
                created += firstArg<JournalNote>()
                Uuid.random()
            }
            val viewModel = createViewModel()
            recordAndStop(viewModel)
            assertEquals(RecorderState.Failed(RecorderFailure.SAVE_FAILED), viewModel.state.value)
            assertFalse(viewModel.state.value.canStartRecording)

            viewModel.retry()
            runCurrent()

            assertIs<RecorderState.Saved>(viewModel.state.value)
            assertEquals(1, recorder.starts)
            assertEquals(1, recorder.stops)
            assertEquals(FakeWearRecorder.DEFAULT_PATH, (created.single() as JournalNote.Audio).mediaRef)
        }

    @Test
    fun `a recording that produced no file is reported as lost`() =
        runTest {
            recorder.stopPath = null
            val viewModel = createViewModel()

            recordAndStop(viewModel)

            assertEquals(RecorderState.Failed(RecorderFailure.RECORDING_LOST), viewModel.state.value)
        }

    @Test
    fun `undo removes the saved recording`() =
        runTest {
            val viewModel = createViewModel()
            recordAndStop(viewModel)
            val saved = assertIs<RecorderState.Saved>(viewModel.state.value)
            coEvery { notesRepository.getNoteById(saved.noteId) } returnsMany listOf(audio(saved.noteId), null)

            viewModel.undo()
            runCurrent()

            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertEquals(listOf(saved.noteId), removedNotes)
            assertTrue(FakeWearRecorder.DEFAULT_PATH in deletedFiles)
            assertEquals("confirmDestruction", haptics.played.last())
        }

    @Test
    fun `the feelings go after four seconds and the saved state after five`() =
        runTest {
            val viewModel = createViewModel()
            recordAndStop(viewModel)

            advanceTimeBy(4_001)
            runCurrent()
            assertFalse(assertIs<RecorderState.Saved>(viewModel.state.value).feelingsOffered)

            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(RecorderState.Ready, viewModel.state.value)
        }

    @Test
    fun `choosing a feeling saves a check-in beside the recording`() =
        runTest {
            val viewModel = createViewModel()
            recordAndStop(viewModel)

            viewModel.chooseFeeling(Feeling.GOOD)
            runCurrent()

            assertEquals(listOf(Feeling.GOOD), savedFeelings)
            val saved = assertIs<RecorderState.Saved>(viewModel.state.value)
            assertEquals(Feeling.GOOD, saved.feeling)
            assertFalse(saved.feelingsOffered)
        }

    @Test
    fun `recording again leaves the note just saved`() =
        runTest {
            val viewModel = createViewModel()
            recordAndStop(viewModel)

            tap(viewModel)

            assertIs<RecorderState.Live>(viewModel.state.value)
            coVerify(exactly = 0) { notesRepository.removeById(any()) }
            assertEquals(2, recorder.starts)
        }

    @Test
    fun `a haptic warns once as the length limit approaches`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            recorder.elapsedFlow.value = 29.minutes + 1.seconds
            runCurrent()
            recorder.elapsedFlow.value = 29.minutes + 2.seconds
            runCurrent()

            assertEquals(1, haptics.played.count { it == "limitApproaching" })
        }

    @Test
    fun `time and input level follow the recorder and the level history is capped`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            repeat(80) { index ->
                recorder.levelFlow.value = (index % 10) / 10f + 0.01f
                runCurrent()
            }
            recorder.elapsedFlow.value = 12.seconds
            runCurrent()

            val live = assertIs<RecorderState.Live>(viewModel.state.value)
            assertEquals(50, live.levels.size)
            assertEquals(12.seconds, live.elapsed)
        }

    @Test
    fun `a recording running when the activity finishes is saved`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            viewModelStore.clear()
            runCurrent()

            assertEquals(1, recorder.stops)
            coVerify(exactly = 1) { notesRepository.create(any<JournalNote>()) }
        }

    @Test
    fun `a press the activity finishing interrupts is discarded`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onPressed()
            runCurrent()

            viewModelStore.clear()
            runCurrent()

            assertEquals(1, recorder.discards)
            assertEquals(0, recorder.stops)
        }

    @Test
    fun `a failed save is tried once more when the activity finishes`() =
        runTest {
            var failures = 1
            coEvery { notesRepository.create(any<JournalNote>()) } answers {
                if (failures-- > 0) throw IllegalStateException("database closed")
                Uuid.random()
            }
            val viewModel = createViewModel()
            recordAndStop(viewModel)

            viewModelStore.clear()
            runCurrent()

            coVerify(exactly = 2) { notesRepository.create(any<JournalNote>()) }
        }

    /** Created through a [ViewModelProvider] so clearing [viewModelStore] runs the real `onCleared`. */
    private fun createViewModel(): RecorderViewModel {
        val viewModel =
            RecorderViewModel(
                recorder = recorder,
                saver =
                    AudioMomentSaver(
                        notesRepository = notesRepository,
                        durationResolver = durationResolver,
                        noteHealthAnnotator = mockk<NoteHealthAnnotator>(relaxed = true),
                        locationCaptureCoordinator = locationCaptureCoordinator,
                        deleteFile = { deletedFiles += it },
                    ),
                notesRepository = notesRepository,
                removalNotifier = { noteId -> removedNotes += noteId },
                feelingSaver = { feeling ->
                    savedFeelings += feeling
                    true
                },
                haptics = haptics,
                permissions = PermissionPrompts(checker = { it in granted }, store = InMemoryPromptStore(asked)),
                applicationScope = applicationScope,
                deleteFile = { deletedFiles += it },
            )
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
        return ViewModelProvider(viewModelStore, factory)["recorder-${Uuid.random()}", RecorderViewModel::class.java]
    }

    private fun TestScope.tap(viewModel: RecorderViewModel) {
        viewModel.onPressed()
        runCurrent()
        viewModel.onTapped()
        runCurrent()
    }

    private fun TestScope.hold(viewModel: RecorderViewModel) {
        viewModel.onPressed()
        runCurrent()
        viewModel.onCommitted()
        runCurrent()
    }

    private fun TestScope.recordAndStop(viewModel: RecorderViewModel) {
        tap(viewModel)
        viewModel.stop()
        runCurrent()
    }

    private fun TestScope.collectRequests(viewModel: RecorderViewModel): List<WatchPermission> {
        val requests = mutableListOf<WatchPermission>()
        backgroundScope.launch { viewModel.permissionRequests.collect { requests += it } }
        runCurrent()
        return requests
    }

    private fun audio(noteId: Uuid): JournalNote.Audio {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        return JournalNote.Audio(
            mediaRef = FakeWearRecorder.DEFAULT_PATH,
            uid = noteId,
            creationTimestamp = now,
            lastUpdated = now,
            durationMs = 4_200,
        )
    }

    private class InMemoryPromptStore(
        private val asked: MutableSet<WatchPermission>,
    ) : PermissionPromptStore {
        override fun hasAsked(permission: WatchPermission): Boolean = permission in asked

        override fun markAsked(permission: WatchPermission) {
            asked += permission
        }
    }
}
```

- [ ] **Step 6: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.recorder.*' --tests 'app.logdate.wear.recording.AfterSaveControllerTest' --tests 'app.logdate.wear.capture.QuickNoteWriterTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'QuickNoteWriter'`, `'LiveMode'`, `'RecorderFailure'`, `'canStartRecording'`, `'FEELINGS_WINDOW_MS'`, `'chooseFeeling'`, and `No parameter with name 'feelingSaver'`.

- [ ] **Step 7: Add the check-in writer**

Create `app/wear/src/main/kotlin/app/logdate/wear/capture/QuickNoteWriter.kt`:

```kotlin
package app.logdate.wear.capture

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SystemCaptureTimeZone
import app.logdate.wear.location.WearLocationCaptureCoordinator
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Saves a feeling as a mood check-in note. */
fun interface FeelingSaver {
    /** Returns false when the check-in could not be stored. */
    suspend fun save(feeling: Feeling): Boolean

    companion object {
        /** For a recorder that offers no feelings after a save. */
        val None = FeelingSaver { false }
    }
}

/**
 * Saves the short notes the watch captures in a tap or two: a feeling, as a mood check-in in the
 * same `#mood:` form the check-in screen writes. Each note has the place when it can be read
 * within [LOCATION_TIMEOUT_MS].
 */
class QuickNoteWriter(
    private val notesRepository: JournalNotesRepository,
    private val locationCaptureCoordinator: WearLocationCaptureCoordinator,
    private val clock: Clock = Clock.System,
) : FeelingSaver {
    override suspend fun save(feeling: Feeling): Boolean = write(MomentNoteCodec.encode(feeling)) != null

    private suspend fun write(content: String): Uuid? =
        try {
            val now = clock.now()
            val note =
                JournalNote.Text(
                    content = content,
                    uid = Uuid.random(),
                    creationTimestamp = now,
                    lastUpdated = now,
                    location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { locationCaptureCoordinator.captureForJournalEntry() },
                    timeZoneId = SystemCaptureTimeZone.currentTimeZoneId(),
                )
            notesRepository.create(note)
            note.uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't save a note from the watch", e)
            null
        }

    companion object {
        const val LOCATION_TIMEOUT_MS = 3_000L
    }
}
```

- [ ] **Step 8: Add the feelings to the undo window**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/recording/AfterSaveController.kt` with:

```kotlin
package app.logdate.wear.recording

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.FeelingSaver
import app.logdate.wear.sync.WearNoteRemovalNotifier
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.uuid.Uuid

/**
 * The note a save just made, while Undo can still take it back.
 *
 * @property feelingsOffered True while the five feelings show beside Undo.
 * @property feeling The feeling chosen for this recording, once one is saved.
 */
data class AfterSaveWindow(
    val noteId: Uuid,
    val feelingsOffered: Boolean = false,
    val feeling: Feeling? = null,
)

/**
 * The seconds after a recording is saved. [open] starts a window of [UNDO_WINDOW_MS]; while it
 * lasts, [undo] removes the note, deletes its audio once no note refers to it, and tells the phone,
 * which may already have the note. A window opened with feelings offers them for
 * [FEELINGS_WINDOW_MS], and [chooseFeeling] saves the one chosen as a check-in through [feelingSaver].
 */
class AfterSaveController(
    private val scope: CoroutineScope,
    private val notesRepository: JournalNotesRepository,
    private val removalNotifier: WearNoteRemovalNotifier,
    private val deleteFile: (String) -> Unit = { path -> File(path).delete() },
    private val feelingSaver: FeelingSaver = FeelingSaver.None,
) {
    private val mutableWindow = MutableStateFlow<AfterSaveWindow?>(null)
    private var closeJob: Job? = null
    private var feelingsJob: Job? = null

    /** The open window, or null once it has closed. */
    val window: StateFlow<AfterSaveWindow?> = mutableWindow.asStateFlow()

    fun open(
        noteId: Uuid,
        offerFeelings: Boolean = false,
    ) {
        cancelTimers()
        mutableWindow.value = AfterSaveWindow(noteId, feelingsOffered = offerFeelings)
        closeJob =
            scope.launch {
                delay(UNDO_WINDOW_MS)
                mutableWindow.value = null
            }
        if (!offerFeelings) return
        feelingsJob =
            scope.launch {
                delay(FEELINGS_WINDOW_MS)
                mutableWindow.update { window -> window?.copy(feelingsOffered = false) }
            }
    }

    /** Ends the window early, as recording again does. The note stays. */
    fun close() {
        cancelTimers()
        mutableWindow.value = null
    }

    /**
     * Saves [feeling] as a check-in while the feelings are offered. Returns false when they are not
     * offered or the check-in could not be stored; the feelings stay offered then.
     */
    suspend fun chooseFeeling(feeling: Feeling): Boolean {
        val window = mutableWindow.value ?: return false
        if (!window.feelingsOffered) return false
        if (!feelingSaver.save(feeling)) return false
        feelingsJob?.cancel()
        mutableWindow.update { current ->
            if (current == null || current.noteId != window.noteId) current else current.copy(feelingsOffered = false, feeling = feeling)
        }
        return true
    }

    /**
     * Removes [noteId] and its audio, and tells the phone. Returns false when the note could not be
     * removed; the phone is told nothing then.
     */
    suspend fun undo(noteId: Uuid): Boolean {
        if (mutableWindow.value?.noteId == noteId) close()
        return try {
            val mediaRef = (notesRepository.getNoteById(noteId) as? JournalNote.Audio)?.mediaRef
            notesRepository.removeById(noteId)
            if (mediaRef != null && notesRepository.getNoteById(noteId) == null) deleteFile(mediaRef)
            // The outbox drops a delete that meets a pending create, but the phone may already have
            // the note, so it is told directly.
            removalNotifier.notifyRemoved(noteId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to undo saved recording $noteId", e)
            false
        }
    }

    private fun cancelTimers() {
        closeJob?.cancel()
        closeJob = null
        feelingsJob?.cancel()
        feelingsJob = null
    }

    companion object {
        const val UNDO_WINDOW_MS = 5_000L
        const val FEELINGS_WINDOW_MS = 4_000L
    }
}
```

- [ ] **Step 9: Add the recorder states**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderState.kt`:

```kotlin
package app.logdate.wear.v2.recorder

import app.logdate.wear.capture.Feeling
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** What the recorder is doing, as Today shows it. */
sealed interface RecorderState {
    /** Nothing is recording; Record starts a recording. */
    data object Ready : RecorderState

    /**
     * A finger is on Record and the gesture hasn't decided between a tap, a hold and a scroll. The
     * recorder may already be running; nothing on screen changes until the gesture decides.
     */
    data object Arming : RecorderState

    /** Recording, by hold to talk or locked. */
    data class Live(
        val mode: LiveMode,
        val paused: Boolean = false,
        val pausedByInterruption: Boolean = false,
        val lockProgress: Float = 0f,
        val elapsed: Duration = Duration.ZERO,
        val levels: List<Float> = emptyList(),
        val confirmingDiscard: Boolean = false,
    ) : RecorderState

    data object Saving : RecorderState

    /** Saved as [noteId]. Undo works while this state lasts; the five feelings while [feelingsOffered]. */
    data class Saved(
        val noteId: Uuid,
        val duration: Duration,
        val feelingsOffered: Boolean,
        val feeling: Feeling? = null,
    ) : RecorderState

    /** The recording was under half a second and was discarded. */
    data object TooShort : RecorderState

    data class Failed(
        val failure: RecorderFailure,
    ) : RecorderState
}

enum class LiveMode {
    /** Hold to talk: the recording saves when the finger lifts. */
    HOLDING,

    /** Goes on after the finger lifts; Stop saves it. */
    LOCKED,
}

/** Why recording or saving failed, so Today can say what to do. */
enum class RecorderFailure {
    /** The microphone permission was refused; asking again shows the system prompt. */
    MICROPHONE_DENIED,

    /** The microphone permission was refused for good; only Settings can grant it. */
    MICROPHONE_BLOCKED,
    NOT_ENOUGH_STORAGE,
    RECORDER_UNAVAILABLE,

    /** The recording finished but could not be stored. Its file is intact and trying again saves it. */
    SAVE_FAILED,

    /** The recorder produced no file, so there is nothing to save. */
    RECORDING_LOST,
}

/** Whether Home has to stay on Today, where Stop is. */
val RecorderState.locksPaging: Boolean
    get() = this is RecorderState.Live

/**
 * Whether a touch on Record starts a recording, rather than doing what the button shows. While a
 * failed save waits, Record tries the save again instead of starting over.
 */
val RecorderState.canStartRecording: Boolean
    get() =
        when (this) {
            RecorderState.Ready, RecorderState.TooShort, is RecorderState.Saved -> true
            is RecorderState.Failed -> failure != RecorderFailure.SAVE_FAILED
            RecorderState.Arming, is RecorderState.Live, RecorderState.Saving -> false
        }
```

- [ ] **Step 10: Add the recorder's actions**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderActions.kt`:

```kotlin
package app.logdate.wear.v2.recorder

import app.logdate.wear.capture.Feeling

/** What Today's recorder controls do. [RecorderViewModel] implements it; previews and tests use stand-ins. */
interface RecorderActions {
    /** Saves a locked recording. */
    fun stop()

    /** Pauses or resumes a locked recording. */
    fun togglePause()

    /** The first call asks for confirmation; a second within three seconds deletes the recording. */
    fun discard()

    /** Removes the recording just saved. */
    fun undo()

    /** Saves [feeling] as a check-in beside the recording just saved. */
    fun chooseFeeling(feeling: Feeling)

    /** Tries the failed step again: the save, or the microphone request. */
    fun retry()

    /** Clears a failure. */
    fun dismiss()
}
```

- [ ] **Step 11: Replace the recorder view model**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderViewModel.kt` with:

```kotlin
package app.logdate.wear.v2.recorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.FeelingSaver
import app.logdate.wear.recording.AfterSaveController
import app.logdate.wear.recording.AudioMomentSaver
import app.logdate.wear.recording.AudioSaveResult
import app.logdate.wear.recording.RecordingStartFailure
import app.logdate.wear.recording.WearAudioRecordingManager
import app.logdate.wear.recording.WearRecorder
import app.logdate.wear.sync.WearNoteRemovalNotifier
import app.logdate.wear.v2.permissions.PermissionPrompts
import app.logdate.wear.v2.permissions.PermissionResult
import app.logdate.wear.v2.permissions.WatchPermission
import app.logdate.wear.v2.recorder.gesture.RecordGestureListener
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The recorder the new watch app shares. It belongs to the activity, so a recording outlives the
 * screen that started it, and it follows Record's gesture:
 *
 * - A press starts the recorder at once, so the first word is never lost, but shows nothing.
 * - A tap makes it a locked recording; holding past the long-press timeout is hold to talk, and
 *   sliding up to the lock locks it.
 * - If the pager takes the touch first, the recording is discarded without a trace.
 *
 * Hold to talk saves when the finger lifts, and a locked recording on Stop. After a save, Undo works
 * for five seconds and the five feelings show for four. The microphone is asked for when the gesture
 * decides without it, and notifications once, on the first locked recording. Nothing recorded is
 * dropped silently: each failure becomes [RecorderState.Failed] with its reason, and a recording
 * still running when the activity finishes is saved on [applicationScope].
 */
class RecorderViewModel(
    private val recorder: WearRecorder,
    private val saver: AudioMomentSaver,
    notesRepository: JournalNotesRepository,
    removalNotifier: WearNoteRemovalNotifier,
    feelingSaver: FeelingSaver,
    private val haptics: LogDateHaptics,
    private val permissions: PermissionPrompts,
    private val applicationScope: CoroutineScope,
    deleteFile: (String) -> Unit = { path -> File(path).delete() },
) : ViewModel(),
    RecordGestureListener,
    RecorderActions {
    /** What the gesture has decided about the recording the recorder started. */
    private enum class GestureDecision {
        NONE,
        PENDING,
        HOLD,
        LOCK,
        RELEASED,
    }

    private val afterSave =
        AfterSaveController(
            scope = viewModelScope,
            notesRepository = notesRepository,
            removalNotifier = removalNotifier,
            deleteFile = deleteFile,
            feelingSaver = feelingSaver,
        )
    private val mutableState = MutableStateFlow<RecorderState>(RecorderState.Ready)
    private val permissionRequestChannel = Channel<WatchPermission>(Channel.BUFFERED)

    private var decision = GestureDecision.NONE

    /** Whether the recorder's start succeeded, or null while it is still running. */
    private var started: Boolean? = null
    private var waitingForMicrophone = false
    private var nearLimitWarned = false
    private var unsavedPath: String? = null
    private var discardConfirmJob: Job? = null

    val state: StateFlow<RecorderState> = mutableState.asStateFlow()

    /** Permissions the screen should ask for now, at the point of use. */
    val permissionRequests: Flow<WatchPermission> = permissionRequestChannel.receiveAsFlow()

    init {
        observeRecorder()
        observeAfterSave()
    }

    override fun onPressed() {
        if (!mutableState.value.canStartRecording) return
        if (!permissions.isGranted(WatchPermission.MICROPHONE)) {
            waitingForMicrophone = true
            return
        }
        beginStart(GestureDecision.PENDING)
    }

    override fun onYielded() {
        waitingForMicrophone = false
        if (decision != GestureDecision.PENDING) return
        decision = GestureDecision.NONE
        when (started) {
            // The start is still running; it discards the recording when it finishes.
            null -> Unit
            true -> {
                mutableState.value = RecorderState.Ready
                viewModelScope.launch { recorder.discard() }
            }
            false -> mutableState.value = RecorderState.Ready
        }
    }

    override fun onTapped() = decide(GestureDecision.LOCK)

    override fun onCommitted() = decide(GestureDecision.HOLD)

    override fun onLockProgress(progress: Float) {
        if (decision != GestureDecision.HOLD) return
        updateLive { live -> live.copy(lockProgress = progress) }
    }

    override fun onLocked() {
        if (decision != GestureDecision.HOLD) return
        decision = GestureDecision.LOCK
        haptics.recordingLocked()
        updateLive { live -> live.copy(mode = LiveMode.LOCKED, lockProgress = 1f) }
        offerNotifications()
    }

    override fun onReleased() {
        if (decision != GestureDecision.HOLD) return
        decision = GestureDecision.RELEASED
        if (started == true) stopAndSave()
    }

    override fun stop() {
        val live = mutableState.value as? RecorderState.Live ?: return
        if (live.mode != LiveMode.LOCKED) return
        stopAndSave()
    }

    override fun togglePause() {
        val live = mutableState.value as? RecorderState.Live ?: return
        if (live.mode != LiveMode.LOCKED) return
        cancelDiscardConfirmation()
        viewModelScope.launch { if (live.paused) recorder.resume() else recorder.pause() }
    }

    override fun discard() {
        val live = mutableState.value as? RecorderState.Live ?: return
        if (live.mode != LiveMode.LOCKED) return
        if (!live.confirmingDiscard) {
            askToConfirmDiscard(live)
            return
        }
        discardConfirmJob?.cancel()
        decision = GestureDecision.NONE
        mutableState.value = RecorderState.Ready
        haptics.confirmDestruction()
        viewModelScope.launch { recorder.discard() }
    }

    override fun undo() {
        val saved = mutableState.value as? RecorderState.Saved ?: return
        mutableState.value = RecorderState.Ready
        viewModelScope.launch {
            if (afterSave.undo(saved.noteId)) haptics.confirmDestruction()
        }
    }

    override fun chooseFeeling(feeling: Feeling) {
        val saved = mutableState.value as? RecorderState.Saved ?: return
        if (!saved.feelingsOffered) return
        viewModelScope.launch {
            if (afterSave.chooseFeeling(feeling)) haptics.saveSucceeded()
        }
    }

    override fun retry() {
        val failed = mutableState.value as? RecorderState.Failed ?: return
        when (failed.failure) {
            RecorderFailure.SAVE_FAILED -> retrySave()
            RecorderFailure.MICROPHONE_DENIED -> permissionRequestChannel.trySend(WatchPermission.MICROPHONE)
            else -> mutableState.value = RecorderState.Ready
        }
    }

    override fun dismiss() {
        if (mutableState.value !is RecorderState.Failed) return
        mutableState.value = RecorderState.Ready
    }

    /** The answer to a permission this view model asked the screen to request. */
    fun onPermissionResult(
        permission: WatchPermission,
        result: PermissionResult,
    ) {
        if (permission != WatchPermission.MICROPHONE) return
        when (result) {
            PermissionResult.GRANTED -> if (mutableState.value.canStartRecording) beginStart(GestureDecision.LOCK)
            PermissionResult.DENIED -> fail(RecorderFailure.MICROPHONE_DENIED)
            PermissionResult.BLOCKED -> fail(RecorderFailure.MICROPHONE_BLOCKED)
        }
    }

    override fun onCleared() {
        val state = mutableState.value
        when {
            state is RecorderState.Live -> applicationScope.launch { stopAndSaveDetached() }
            state == RecorderState.Arming -> {
                val decided = decision == GestureDecision.HOLD || decision == GestureDecision.LOCK || decision == GestureDecision.RELEASED
                applicationScope.launch { if (decided) stopAndSaveDetached() else recorder.discard() }
            }
            state is RecorderState.Failed && state.failure == RecorderFailure.SAVE_FAILED ->
                unsavedPath?.let { path -> applicationScope.launch { saver.save(path, recorder.elapsed.value) } }
        }
        super.onCleared()
    }

    private fun decide(next: GestureDecision) {
        if (waitingForMicrophone) {
            waitingForMicrophone = false
            permissionRequestChannel.trySend(WatchPermission.MICROPHONE)
            return
        }
        if (decision != GestureDecision.PENDING) return
        decision = next
        applyDecision()
    }

    private fun beginStart(next: GestureDecision) {
        afterSave.close()
        decision = next
        started = null
        nearLimitWarned = false
        mutableState.value = RecorderState.Arming
        viewModelScope.launch {
            val ok = recorder.start()
            started = ok
            onStartFinished(ok)
        }
    }

    private suspend fun onStartFinished(ok: Boolean) {
        when (decision) {
            GestureDecision.NONE -> {
                if (ok) recorder.discard()
                mutableState.value = RecorderState.Ready
            }
            GestureDecision.PENDING -> Unit
            GestureDecision.HOLD, GestureDecision.LOCK -> applyDecision()
            GestureDecision.RELEASED -> if (ok) stopAndSave() else failStart()
        }
    }

    private fun applyDecision() {
        when (started) {
            null -> Unit
            false -> failStart()
            true -> goLive()
        }
    }

    private fun goLive() {
        val locked = decision == GestureDecision.LOCK
        haptics.recordingStarted()
        mutableState.value =
            RecorderState.Live(
                mode = if (locked) LiveMode.LOCKED else LiveMode.HOLDING,
                lockProgress = if (locked) 1f else 0f,
                elapsed = recorder.elapsed.value,
            )
        // The recorder can end between start() returning and now, and its flow does not repeat itself.
        if (!recorder.isRecording.value) {
            stopAndSave()
            return
        }
        if (locked) offerNotifications()
    }

    private fun failStart() {
        decision = GestureDecision.NONE
        fail(recorder.lastStartFailure.toFailure())
    }

    private fun offerNotifications() {
        if (permissions.takeOptionalPrompt(WatchPermission.NOTIFICATIONS)) permissionRequestChannel.trySend(WatchPermission.NOTIFICATIONS)
    }

    private fun askToConfirmDiscard(live: RecorderState.Live) {
        mutableState.value = live.copy(confirmingDiscard = true)
        discardConfirmJob?.cancel()
        discardConfirmJob =
            viewModelScope.launch {
                delay(DISCARD_CONFIRM_MS)
                updateLive { current -> current.copy(confirmingDiscard = false) }
            }
    }

    private fun cancelDiscardConfirmation() {
        discardConfirmJob?.cancel()
        updateLive { current -> current.copy(confirmingDiscard = false) }
    }

    private fun stopAndSave() {
        decision = GestureDecision.NONE
        discardConfirmJob?.cancel()
        mutableState.value = RecorderState.Saving
        haptics.recordingFinished()
        viewModelScope.launch {
            // Stopping and saving finish even if the activity finishes partway through.
            withContext(NonCancellable) {
                val path = recorder.stop()
                if (path == null) {
                    Napier.e("Recording ended without a file")
                    fail(RecorderFailure.RECORDING_LOST)
                    return@withContext
                }
                handle(saver.save(path, recorder.elapsed.value))
            }
        }
    }

    private fun retrySave() {
        val path = unsavedPath
        if (path == null) {
            mutableState.value = RecorderState.Ready
            return
        }
        mutableState.value = RecorderState.Saving
        viewModelScope.launch {
            withContext(NonCancellable) { handle(saver.save(path, recorder.elapsed.value)) }
        }
    }

    /** Stops the recorder and stores its file after the activity is gone, so there is no state to update. */
    private suspend fun stopAndSaveDetached() {
        val path = recorder.stop()
        if (path == null) {
            Napier.e("Recording ended without a file when the app closed")
            return
        }
        saver.save(path, recorder.elapsed.value)
    }

    private fun handle(result: AudioSaveResult) {
        when (result) {
            is AudioSaveResult.Saved -> onSaved(result.note)
            AudioSaveResult.TooShort -> onTooShort()
            is AudioSaveResult.Failed -> {
                unsavedPath = result.path
                fail(RecorderFailure.SAVE_FAILED)
            }
        }
    }

    private fun onSaved(note: JournalNote.Audio) {
        unsavedPath = null
        haptics.saveSucceeded()
        afterSave.open(note.uid, offerFeelings = true)
        mutableState.value =
            RecorderState.Saved(noteId = note.uid, duration = note.durationMs.milliseconds, feelingsOffered = true)
    }

    private fun onTooShort() {
        unsavedPath = null
        haptics.actionRejected()
        mutableState.value = RecorderState.TooShort
        viewModelScope.launch {
            delay(TOO_SHORT_DISPLAY_MS)
            mutableState.update { state -> if (state == RecorderState.TooShort) RecorderState.Ready else state }
        }
    }

    private fun fail(failure: RecorderFailure) {
        haptics.actionRejected()
        mutableState.value = RecorderState.Failed(failure)
    }

    private fun observeRecorder() {
        viewModelScope.launch {
            recorder.audioLevel.collect { level ->
                updateLive { live -> if (live.paused) live else live.copy(levels = (live.levels + level).takeLast(MAX_LEVEL_SAMPLES)) }
            }
        }
        viewModelScope.launch {
            recorder.elapsed.collect { elapsed ->
                updateLive { live -> live.copy(elapsed = elapsed) }
                warnNearLimit(elapsed)
            }
        }
        viewModelScope.launch {
            combine(recorder.isPaused, recorder.pausedByInterruption) { paused, interrupted -> paused to interrupted }
                .collect { (paused, interrupted) -> onPauseChanged(paused, interrupted) }
        }
        viewModelScope.launch {
            recorder.isRecording.collect { recording ->
                if (!recording && mutableState.value is RecorderState.Live) stopAndSave()
            }
        }
    }

    /** The saved state follows the undo window: the feelings go with it, and Undo with its end. */
    private fun observeAfterSave() {
        viewModelScope.launch {
            afterSave.window.collect { window ->
                mutableState.update { state ->
                    if (state !is RecorderState.Saved) return@update state
                    if (window == null || window.noteId != state.noteId) return@update RecorderState.Ready
                    state.copy(feelingsOffered = window.feelingsOffered, feeling = window.feeling)
                }
            }
        }
    }

    private fun warnNearLimit(elapsed: Duration) {
        val live = mutableState.value as? RecorderState.Live ?: return
        if (nearLimitWarned || live.paused || elapsed < NEAR_LIMIT) return
        nearLimitWarned = true
        haptics.limitApproaching()
    }

    private fun onPauseChanged(
        paused: Boolean,
        interrupted: Boolean,
    ) {
        val live = mutableState.value as? RecorderState.Live ?: return
        if (paused == live.paused) {
            if (paused) mutableState.value = live.copy(pausedByInterruption = interrupted)
            return
        }
        if (paused) haptics.recordingPaused() else haptics.recordingResumed()
        mutableState.value = live.copy(paused = paused, pausedByInterruption = paused && interrupted, confirmingDiscard = false)
    }

    private inline fun updateLive(transform: (RecorderState.Live) -> RecorderState.Live) {
        mutableState.update { state -> if (state is RecorderState.Live) transform(state) else state }
    }

    private fun RecordingStartFailure?.toFailure(): RecorderFailure =
        when (this) {
            RecordingStartFailure.MICROPHONE_PERMISSION_DENIED -> RecorderFailure.MICROPHONE_DENIED
            RecordingStartFailure.NOT_ENOUGH_STORAGE -> RecorderFailure.NOT_ENOUGH_STORAGE
            RecordingStartFailure.RECORDER_UNAVAILABLE, null -> RecorderFailure.RECORDER_UNAVAILABLE
        }

    companion object {
        const val TOO_SHORT_DISPLAY_MS = 1_200L
        const val DISCARD_CONFIRM_MS = 3_000L
        private const val MAX_LEVEL_SAMPLES = 50
        private val NEAR_LIMIT = WearAudioRecordingManager.MAX_RECORDING_DURATION - 1.minutes
    }
}
```

- [ ] **Step 12: Bind the recorder and its savers in Koin**

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add these imports in ASCII order with the existing ones:

```kotlin
import app.logdate.wear.capture.QuickNoteWriter
import app.logdate.wear.recording.AudioMomentSaver
```

add these definitions after `single { PermissionPrompts(checker = get(), store = get()) }`:

```kotlin
        single { QuickNoteWriter(notesRepository = get(), locationCaptureCoordinator = get()) }
        factory {
            AudioMomentSaver(
                notesRepository = get(),
                durationResolver = get(),
                noteHealthAnnotator = get(),
                locationCaptureCoordinator = get(),
            )
        }
```

and replace `viewModel { RecorderViewModel() }` with:

```kotlin
        viewModel {
            RecorderViewModel(
                recorder = get(),
                saver = get(),
                notesRepository = get(),
                removalNotifier = get(),
                feelingSaver = get<QuickNoteWriter>(),
                haptics = get(),
                permissions = get(),
                applicationScope = get(),
            )
        }
```

- [ ] **Step 13: Run the unit tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.recorder.*' --tests 'app.logdate.wear.recording.*' --tests 'app.logdate.wear.capture.*' --tests 'app.logdate.wear.presentation.recording.WearRecordingViewModelTest'
```

Expected: `BUILD SUCCESSFUL`; `RecorderViewModelTest` 34 tests, `RecorderStateTest` 2, `AfterSaveControllerTest` 10, `QuickNoteWriterTest` 3, and `WearRecordingViewModelTest` still 63.

- [ ] **Step 14: Lint and resolve the real graph on the Wear GMD**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintCheck :app:wear:detekt && uptime && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.ExperienceGateTest,app.logdate.wear.e2e.HomePagerRotaryTest
```

Expected: `BUILD SUCCESSFUL` twice. The shell tests resolve `RecorderViewModel` from the real Koin graph, so a missing binding fails here.

- [ ] **Step 15: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task4-commit.txt <<'EOF'
chore(wear): add the recorder behind the new watch app

The redesigned watch app has one recorder for the whole app, so a
recording goes on when its screen goes away. A press starts recording
at once so the first word is never lost, and the record gesture then
decides: a tap locks the recording, holding is hold to talk, sliding
up locks it, and a scroll discards it without a trace.

Hold to talk saves when the finger lifts and a locked recording on
Stop, with Pause and a two-tap Discard. After a save, Undo works for
five seconds and the five feelings show for four; a feeling is saved
as a mood check-in. The microphone is asked for when it is missing,
and notifications once, on the first locked recording.

Every failure has its own state: the microphone refused or turned off,
no storage, no recorder, a save that failed and can be tried again,
or a recording with no audio. Today does not show the recorder yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 16: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/capture/QuickNoteWriter.kt app/wear/src/main/kotlin/app/logdate/wear/recording/AfterSaveController.kt app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderState.kt app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderActions.kt app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/recording/FakeWearRecorder.kt app/wear/src/test/kotlin/app/logdate/wear/v2/haptics/RecordedHaptics.kt app/wear/src/test/kotlin/app/logdate/wear/capture/QuickNoteWriterTest.kt app/wear/src/test/kotlin/app/logdate/wear/recording/AfterSaveControllerTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecorderStateTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecorderViewModelTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task4-commit.txt
```

---
### Task 5: Recording from Today (`chore(wear)`)

This is the task that makes Record work, so it is the task that sets it in `TodayControls`. `TodayControls.onRecord` becomes `recorder: TodayRecorder?`, and Today draws it:

- **Record** is the edge button with `Modifier.recordGesture`. Its shape follows the recorder with the shared `RecordShape` morph: the circle while ready, toward the Stop square as the finger slides to the lock while holding, the square once locked, when its click is Stop.
- **While recording**, Today's list shows the time, "Release to save" with a lock that fills in, or "Recording"/"Paused"; once locked, Pause and Discard sit where Mark and + were. The input level runs around the edge of the screen.
- **After a save**: "Saved · 0:42", Undo, and the five feelings for four seconds. Too short, and each failure, say what happened and offer the one action that helps (Allow, Open Settings, Storage, Try again, OK).

The edge button stays the same node from the press through the lock, so the gesture never loses its finger when Today switches to the live recording; `TodayRecorderTest` proves it. The permissions the recorder asks for are requested at the app's root. All copy is new in `strings_recorder.xml` under `wear_today_recorder_*`, because the v1 recorder already owns the `wear_recorder_*` names.

**Files:**
- Create: `app/wear/src/main/res/values/strings_recorder.xml`
- Create: `app/wear/src/main/res/drawable/ic_recorder_pause.xml`, `ic_recorder_resume.xml`, `ic_recorder_discard.xml`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/TodayRecorder.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecordEdgeButton.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderItems.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecordingRing.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/SystemSettings.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayPage.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` (whole file)
- Create (screenshot): `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WearAllSizesPreview.kt`, `PreviewRecorder.kt`, `RecorderScreenshots.kt`
- Modify (screenshot): `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/TodayPageScreenshots.kt:112` (`allControls`) and its imports
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecordButtonModelTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/TodayRecorderTest.kt`
- References: `app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/RecorderScreenshots/` (45 new) and `TodayPageScreenshots/S09_*`, `S11_*` (3 changed)

**Interfaces:**
- Consumes: `RecorderViewModel`, `RecorderState`, `RecorderActions`, `canStartRecording`, `locksPaging` (Task 4); `Modifier.recordGesture`, `startLocked`, `RecordGestureDefaults` (Task 2); `rememberPermissionRequester` (Task 3); `RecordShape.outline(progress, size, path)`, `LogDateMotion.quick()`, `LogDateGlyph.LOCK`, `formatClockDuration(Duration)`, `Feeling.emoji`/`labelRes`.
- Produces:
  - `@Stable class TodayRecorder(val state: RecorderState, val gesture: RecordGestureListener, val actions: RecorderActions)`; `internal enum class RecordButtonAction { START, STOP, RETRY_SAVE, NONE }`; `internal data class RecordButtonModel(action, armed, enabled, morph, descriptionRes)`; `internal fun RecorderState.recordButton(): RecordButtonModel`.
  - `data class TodayControls(val recorder: TodayRecorder? = null, val onMark: (() -> Unit)? = null, val onAdd: (() -> Unit)? = null)` (replaces `onRecord`).
  - `object RecorderTags { ELAPSED, MESSAGE, LOCK_HINT, PAUSE, DISCARD, UNDO, FEELINGS, FAILURE_ACTION, RING }`, `internal val RecorderState.replacesSummary`, `internal fun TransformingLazyColumnScope.recorderItems(recorder: TodayRecorder)`, `@Composable internal fun RecordEdgeButton(recorder: TodayRecorder)`, `@Composable internal fun RecordingRing(levels: List<Float>, modifier: Modifier = Modifier)`.
  - `fun openAppSettings(context: Context)` and `fun openStorageSettings(context: Context)` in `app.logdate.wear.v2.permissions`.
  - `annotation class WearAllSizesPreview` (screenshot source set).

- [ ] **Step 1: Write the failing button model test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecordButtonModelTest.kt`:

```kotlin
package app.logdate.wear.v2.recorder

import app.logdate.wear.R
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class RecordButtonModelTest {
    @Test
    fun `a ready Record is armed to start a recording`() {
        val model = RecorderState.Ready.recordButton()

        assertEquals(RecordButtonAction.START, model.action)
        assertTrue(model.armed)
        assertEquals(0f, model.morph)
        assertEquals(R.string.wear_today_record, model.descriptionRes)
    }

    @Test
    fun `while holding Record turns toward Stop as the finger slides to the lock`() {
        val model = RecorderState.Live(LiveMode.HOLDING, lockProgress = 0.4f).recordButton()

        assertEquals(RecordButtonAction.NONE, model.action)
        assertFalse(model.armed)
        assertEquals(0.4f, model.morph)
    }

    @Test
    fun `a locked recording makes Record its Stop button`() {
        val model = RecorderState.Live(LiveMode.LOCKED, lockProgress = 1f).recordButton()

        assertEquals(RecordButtonAction.STOP, model.action)
        assertEquals(1f, model.morph)
        assertEquals(R.string.wear_today_recorder_stop, model.descriptionRes)
    }

    @Test
    fun `Record waits while a recording saves`() {
        assertFalse(RecorderState.Saving.recordButton().enabled)
    }

    @Test
    fun `after a failed save Record saves the same recording again`() {
        val model = RecorderState.Failed(RecorderFailure.SAVE_FAILED).recordButton()

        assertEquals(RecordButtonAction.RETRY_SAVE, model.action)
        assertFalse(model.armed)
    }

    @Test
    fun `Record is armed exactly when a touch can start a recording`() {
        val states =
            listOf(
                RecorderState.Ready,
                RecorderState.Arming,
                RecorderState.Live(LiveMode.HOLDING),
                RecorderState.Live(LiveMode.LOCKED),
                RecorderState.Saving,
                RecorderState.Saved(Uuid.random(), 4.seconds, feelingsOffered = true),
                RecorderState.TooShort,
            ) + RecorderFailure.entries.map { RecorderState.Failed(it) }

        states.forEach { state -> assertEquals(state.canStartRecording, state.recordButton().armed, "$state") }
    }
}
```

- [ ] **Step 2: Write the failing GMD Today recorder test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/TodayRecorderTest.kt`:

```kotlin
package app.logdate.wear.e2e

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import app.logdate.client.domain.today.TodaySummary
import app.logdate.wear.R
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.recorder.LiveMode
import app.logdate.wear.v2.recorder.RecorderActions
import app.logdate.wear.v2.recorder.RecorderFailure
import app.logdate.wear.v2.recorder.RecorderState
import app.logdate.wear.v2.recorder.RecorderTags
import app.logdate.wear.v2.recorder.TodayRecorder
import app.logdate.wear.v2.recorder.gesture.RecordGestureDefaults
import app.logdate.wear.v2.recorder.gesture.RecordGestureListener
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.HandoffState
import app.logdate.wear.v2.today.PhoneCard
import app.logdate.wear.v2.today.TodayControls
import app.logdate.wear.v2.today.TodayPage
import app.logdate.wear.v2.today.TodayTags
import app.logdate.wear.v2.today.TodayUiState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Today with a scripted recorder on a Wear OS emulator: one hold goes on while Today switches to
 * the live recording and reaches the lock, and each state's controls reach the recorder.
 */
@RunWith(AndroidJUnit4::class)
class TodayRecorderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val recorder = ScriptedRecorder()

    @Test
    fun holding_shows_the_live_recording_and_the_same_hold_reaches_the_lock() {
        show()
        composeRule.onNodeWithTag(TodayTags.RECORD).performTouchInput {
            down(center)
            // A move that does not move: it is sent after the timeout, so the gesture has committed by then.
            move(delayMillis = viewConfiguration.longPressTimeoutMillis + 100)
        }
        composeRule.onNodeWithText(context.getString(R.string.wear_today_recorder_release_to_save)).assertIsDisplayed()

        composeRule.onNodeWithTag(TodayTags.RECORD).performTouchInput {
            repeat(5) { moveBy(Offset(0f, -RecordGestureDefaults.LockDistance.toPx() / 4)) }
            up()
        }

        composeRule.onNodeWithTag(RecorderTags.PAUSE).assertIsDisplayed()
        assertTrue(recorder.locked)
    }

    @Test
    fun stop_saves_a_locked_recording() {
        recorder.state = RecorderState.Live(LiveMode.LOCKED, lockProgress = 1f)
        show()

        composeRule.onNodeWithTag(TodayTags.RECORD).performClick()
        composeRule.waitForIdle()

        assertEquals(1, recorder.stops)
    }

    @Test
    fun a_saved_recording_offers_undo_and_the_five_feelings() {
        recorder.state = RecorderState.Saved(Uuid.random(), 42.seconds, feelingsOffered = true)
        show()

        composeRule.onNodeWithContentDescription(context.getString(R.string.wear_feeling_good)).performClick()
        composeRule.onNodeWithTag(RecorderTags.UNDO).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(Feeling.GOOD), recorder.feelings)
        assertEquals(1, recorder.undos)
    }

    @Test
    fun a_failed_save_is_tried_again_from_its_message_and_from_Record() {
        recorder.state = RecorderState.Failed(RecorderFailure.SAVE_FAILED)
        show()

        composeRule.onNodeWithTag(RecorderTags.FAILURE_ACTION).performClick()
        composeRule.onNodeWithTag(TodayTags.RECORD).performClick()
        composeRule.waitForIdle()

        assertEquals(2, recorder.retries)
    }

    @Test
    fun record_waits_while_a_recording_saves() {
        recorder.state = RecorderState.Saving
        show()

        composeRule.onNodeWithTag(TodayTags.RECORD).assertIsNotEnabled()
    }

    private fun show() {
        composeRule.setContent {
            LogDateWearTheme(dynamicColor = false) {
                AppScaffold {
                    TodayPage(
                        state = today,
                        handoff = HandoffState.IDLE,
                        onGetPhoneApp = {},
                        onHandoffDismissed = {},
                        controls = TodayControls(recorder = TodayRecorder(recorder.state, recorder, recorder)),
                    )
                }
            }
        }
    }

    /** A recorder whose gesture moves it through the states the way the real one does, and counts its controls. */
    private class ScriptedRecorder :
        RecordGestureListener,
        RecorderActions {
        var state: RecorderState by mutableStateOf(RecorderState.Ready)
        var locked = false
        var stops = 0
        var undos = 0
        var retries = 0
        val feelings = mutableListOf<Feeling>()

        override fun onPressed() = Unit

        override fun onYielded() = Unit

        override fun onTapped() {
            state = RecorderState.Live(LiveMode.LOCKED, lockProgress = 1f)
        }

        override fun onCommitted() {
            state = RecorderState.Live(LiveMode.HOLDING)
        }

        override fun onLockProgress(progress: Float) {
            val live = state as? RecorderState.Live ?: return
            state = live.copy(lockProgress = progress)
        }

        override fun onLocked() {
            locked = true
            state = RecorderState.Live(LiveMode.LOCKED, lockProgress = 1f)
        }

        override fun onReleased() {
            state = RecorderState.Saving
        }

        override fun stop() {
            stops++
        }

        override fun togglePause() = Unit

        override fun discard() = Unit

        override fun undo() {
            undos++
        }

        override fun chooseFeeling(feeling: Feeling) {
            feelings += feeling
        }

        override fun retry() {
            retries++
        }

        override fun dismiss() = Unit
    }

    private companion object {
        val today =
            TodayUiState.Ready(
                date = LocalDate(2026, 10, 10),
                timeZone = TimeZone.UTC,
                summary = TodaySummary.Empty,
                feeling = null,
                campfire = null,
                phoneCard = PhoneCard.None,
            )
    }
}
```

- [ ] **Step 3: Add the all-sizes preview and the preview recorder**

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WearAllSizesPreview.kt`:

```kotlin
package app.logdate.wear.screenshots

import androidx.compose.ui.tooling.preview.Preview

/** A small round watch, a large round watch, and a small round watch at the largest font scale checked, 1.24. */
@Preview(name = "Small Round", device = "id:wearos_small_round", showBackground = true)
@Preview(name = "Large Round", device = "id:wearos_large_round", showBackground = true)
@Preview(name = "Large Font", device = "id:wearos_small_round", showBackground = true, fontScale = 1.24f)
annotation class WearAllSizesPreview
```

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/PreviewRecorder.kt`:

```kotlin
package app.logdate.wear.screenshots

import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.recorder.RecorderActions
import app.logdate.wear.v2.recorder.gesture.RecordGestureListener

/** A recorder that does nothing, for drawing Today's recorder in previews. */
internal object PreviewRecorder :
    RecordGestureListener,
    RecorderActions {
    override fun onPressed() = Unit

    override fun onYielded() = Unit

    override fun onTapped() = Unit

    override fun onCommitted() = Unit

    override fun onLockProgress(progress: Float) = Unit

    override fun onLocked() = Unit

    override fun onReleased() = Unit

    override fun stop() = Unit

    override fun togglePause() = Unit

    override fun discard() = Unit

    override fun undo() = Unit

    override fun chooseFeeling(feeling: Feeling) = Unit

    override fun retry() = Unit

    override fun dismiss() = Unit
}
```

- [ ] **Step 4: Write the recorder screenshot scenes**

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/RecorderScreenshots.kt`:

```kotlin
package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import app.logdate.client.domain.today.TodaySummary
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.recorder.LiveMode
import app.logdate.wear.v2.recorder.RecorderFailure
import app.logdate.wear.v2.recorder.RecorderState
import app.logdate.wear.v2.recorder.TodayRecorder
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.HandoffState
import app.logdate.wear.v2.today.PhoneCard
import app.logdate.wear.v2.today.TodayControls
import app.logdate.wear.v2.today.TodayPage
import app.logdate.wear.v2.today.TodayUiState
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** Every recorder state on Today, on the graphite theme screenshots can render. */
class RecorderScreenshots {
    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R01_Holding() {
        RecorderScene(RecorderState.Live(LiveMode.HOLDING, lockProgress = 0.4f, elapsed = 7.seconds, levels = levels))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R02_Locked() {
        RecorderScene(locked)
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R03_Paused() {
        RecorderScene(locked.copy(paused = true))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R04_PausedByAnotherApp() {
        RecorderScene(locked.copy(paused = true, pausedByInterruption = true))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R05_ConfirmDiscard() {
        RecorderScene(locked.copy(confirmingDiscard = true))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R06_Saving() {
        RecorderScene(RecorderState.Saving)
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R07_SavedWithFeelings() {
        RecorderScene(RecorderState.Saved(noteId, 42.seconds, feelingsOffered = true))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R08_SavedFeelingChosen() {
        RecorderScene(RecorderState.Saved(noteId, 42.seconds, feelingsOffered = false, feeling = Feeling.GOOD))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R09_TooShort() {
        RecorderScene(RecorderState.TooShort)
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R10_MicrophoneRefused() {
        RecorderScene(RecorderState.Failed(RecorderFailure.MICROPHONE_DENIED))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R11_MicrophoneOff() {
        RecorderScene(RecorderState.Failed(RecorderFailure.MICROPHONE_BLOCKED))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R12_StorageFull() {
        RecorderScene(RecorderState.Failed(RecorderFailure.NOT_ENOUGH_STORAGE))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R13_RecorderUnavailable() {
        RecorderScene(RecorderState.Failed(RecorderFailure.RECORDER_UNAVAILABLE))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R14_SaveFailed() {
        RecorderScene(RecorderState.Failed(RecorderFailure.SAVE_FAILED))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R15_RecordingLost() {
        RecorderScene(RecorderState.Failed(RecorderFailure.RECORDING_LOST))
    }
}

private val levels = List(50) { index -> ((index * 37) % 100) / 100f }
private val locked = RecorderState.Live(LiveMode.LOCKED, lockProgress = 1f, elapsed = 42.seconds, levels = levels)
private val noteId = Uuid.parse("6f1c2b8e-3a1d-4e5f-9a7b-2c4d6e8f0a1b")
private val today =
    TodayUiState.Ready(
        date = LocalDate(2026, 10, 10),
        timeZone = TimeZone.UTC,
        summary = TodaySummary.Empty,
        feeling = null,
        campfire = null,
        phoneCard = PhoneCard.None,
    )

@Composable
private fun RecorderScene(state: RecorderState) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            TodayPage(
                state = today,
                handoff = HandoffState.IDLE,
                onGetPhoneApp = {},
                onHandoffDismissed = {},
                controls = TodayControls(recorder = TodayRecorder(state, PreviewRecorder, PreviewRecorder), onMark = {}, onAdd = {}),
                timeSource = RecorderTimeSource,
            )
        }
    }
}

/** A fixed clock reading, so the time text renders the same on every run. */
private object RecorderTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
```

- [ ] **Step 5: Give Today's existing controls scene a ready recorder**

In `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/TodayPageScreenshots.kt`, add these imports in ASCII order with the existing ones:

```kotlin
import app.logdate.wear.v2.recorder.RecorderState
import app.logdate.wear.v2.recorder.TodayRecorder
```

and replace

```kotlin
private val allControls = TodayControls(onRecord = {}, onMark = {}, onAdd = {})
```

with

```kotlin
private val allControls =
    TodayControls(recorder = TodayRecorder(RecorderState.Ready, PreviewRecorder, PreviewRecorder), onMark = {}, onAdd = {})
```

- [ ] **Step 6: Run the unit test to verify it fails**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.recorder.RecordButtonModelTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'recordButton'`, `'RecordButtonAction'` and `'wear_today_recorder_stop'`.

- [ ] **Step 7: Add the copy and the control icons**

Create `app/wear/src/main/res/values/strings_recorder.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- The recorder on Today, in the new watch app -->
    <string name="wear_today_recorder_start">Start recording</string>
    <string name="wear_today_recorder_stop">Stop recording</string>
    <string name="wear_today_recorder_save_again">Save the recording again</string>
    <string name="wear_today_recorder_recording">Recording</string>
    <string name="wear_today_recorder_saving">Saving…</string>
    <string name="wear_today_recorder_release_to_save">Release to save</string>
    <string name="wear_today_recorder_slide_to_lock">Slide up to lock</string>
    <string name="wear_today_recorder_paused">Paused</string>
    <string name="wear_today_recorder_paused_by_app">Paused by another app</string>
    <string name="wear_today_recorder_pause">Pause</string>
    <string name="wear_today_recorder_resume">Resume</string>
    <string name="wear_today_recorder_discard">Discard</string>
    <string name="wear_today_recorder_discard_confirm">Tap again to discard</string>
    <string name="wear_today_recorder_saved">Saved&#160;· %1$s</string>
    <string name="wear_today_recorder_undo">Undo</string>
    <!-- The feeling chosen after a recording: its emoji, then its name, such as "🙂 Good". -->
    <string name="wear_today_recorder_feeling_chosen">%1$s %2$s</string>
    <string name="wear_today_recorder_too_short">Too short to save</string>
    <string name="wear_today_recorder_failed_microphone">LogDate needs the microphone to record</string>
    <string name="wear_today_recorder_failed_microphone_off">The microphone is off for LogDate</string>
    <string name="wear_today_recorder_failed_storage">The watch is out of storage</string>
    <string name="wear_today_recorder_failed_start">Couldn’t start recording</string>
    <string name="wear_today_recorder_failed_save">Couldn’t save the recording</string>
    <string name="wear_today_recorder_failed_lost">The recording had no audio</string>
    <string name="wear_today_recorder_allow">Allow</string>
    <string name="wear_today_recorder_open_settings">Open Settings</string>
    <string name="wear_today_recorder_open_storage">Storage</string>
    <string name="wear_today_recorder_try_again">Try again</string>
    <string name="wear_today_recorder_ok">OK</string>
</resources>
```

Create `app/wear/src/main/res/drawable/ic_recorder_pause.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M6,19h4V5H6v14zM14,5v14h4V5h-4z" />
</vector>
```

Create `app/wear/src/main/res/drawable/ic_recorder_resume.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M8,5v14l11,-7z" />
</vector>
```

Create `app/wear/src/main/res/drawable/ic_recorder_discard.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z" />
</vector>
```

- [ ] **Step 8: Add Today's recorder and Record's model**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/TodayRecorder.kt`:

```kotlin
package app.logdate.wear.v2.recorder

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import app.logdate.wear.R
import app.logdate.wear.v2.recorder.gesture.RecordGestureListener

/** The recorder as Today shows it: its state, the gesture it listens to, and its controls. */
@Stable
class TodayRecorder(
    val state: RecorderState,
    val gesture: RecordGestureListener,
    val actions: RecorderActions,
)

/** What a click on Record does. A touch on an armed Record goes through the gesture instead. */
internal enum class RecordButtonAction {
    /** Starts a locked recording, as TalkBack's click does. */
    START,
    STOP,
    RETRY_SAVE,

    /** A recording is starting, or is hold to talk; a click does nothing. */
    NONE,
}

/**
 * Record in one recorder state.
 *
 * @property armed Whether a touch goes through the record gesture.
 * @property morph 0 draws the record circle and 1 the Stop square.
 */
internal data class RecordButtonModel(
    val action: RecordButtonAction,
    val armed: Boolean,
    val enabled: Boolean,
    val morph: Float,
    @param:StringRes val descriptionRes: Int,
)

internal fun RecorderState.recordButton(): RecordButtonModel =
    when (this) {
        RecorderState.Ready, RecorderState.TooShort, is RecorderState.Saved -> startButton
        is RecorderState.Failed -> if (failure == RecorderFailure.SAVE_FAILED) retrySaveButton else startButton
        RecorderState.Arming -> waitingButton(morph = 0f, descriptionRes = R.string.wear_today_record)
        is RecorderState.Live ->
            if (mode == LiveMode.LOCKED) stopButton else waitingButton(lockProgress, R.string.wear_today_recorder_recording)
        RecorderState.Saving -> waitingButton(morph = 1f, descriptionRes = R.string.wear_today_recorder_saving, enabled = false)
    }

/** Record while a recording starts, runs as hold to talk, or saves: a touch passes through and a click does nothing. */
private fun waitingButton(
    morph: Float,
    @StringRes descriptionRes: Int,
    enabled: Boolean = true,
): RecordButtonModel =
    RecordButtonModel(RecordButtonAction.NONE, armed = false, enabled = enabled, morph = morph, descriptionRes = descriptionRes)

private val startButton =
    RecordButtonModel(RecordButtonAction.START, armed = true, enabled = true, morph = 0f, descriptionRes = R.string.wear_today_record)
private val stopButton =
    RecordButtonModel(
        RecordButtonAction.STOP,
        armed = false,
        enabled = true,
        morph = 1f,
        descriptionRes = R.string.wear_today_recorder_stop,
    )
private val retrySaveButton =
    RecordButtonModel(
        RecordButtonAction.RETRY_SAVE,
        armed = false,
        enabled = true,
        morph = 0f,
        descriptionRes = R.string.wear_today_recorder_save_again,
    )
```

- [ ] **Step 9: Add Record as the edge button**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecordEdgeButton.kt`:

```kotlin
package app.logdate.wear.v2.recorder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.LocalContentColor
import app.logdate.ui.tokens.LogDateMotion
import app.logdate.ui.tokens.RecordShape
import app.logdate.wear.R
import app.logdate.wear.v2.recorder.gesture.recordGesture
import app.logdate.wear.v2.recorder.gesture.startLocked
import app.logdate.wear.v2.today.TodayTags

/**
 * Record as Today's edge button. While armed, a touch goes through [recordGesture]; otherwise a
 * click does what the button shows. Its shape follows the recorder from the record circle to the
 * Stop square.
 */
@Composable
internal fun RecordEdgeButton(recorder: TodayRecorder) {
    val model = recorder.state.recordButton()
    val onClick = recordButtonClick(model.action, recorder)
    val morph by animateFloatAsState(targetValue = model.morph, animationSpec = LogDateMotion.quick(), label = "recordMorph")
    EdgeButton(
        onClick = onClick,
        modifier =
            Modifier
                .testTag(TodayTags.RECORD)
                .recordGesture(
                    armed = model.armed,
                    listener = recorder.gesture,
                    description = stringResource(model.descriptionRes),
                    startLabel = stringResource(R.string.wear_today_recorder_start),
                    onClick = onClick,
                    enabled = model.enabled,
                ),
        buttonSize = EdgeButtonSize.Medium,
        enabled = model.enabled,
    ) {
        RecordMorphGlyph(progress = morph)
    }
}

private fun recordButtonClick(
    action: RecordButtonAction,
    recorder: TodayRecorder,
): () -> Unit =
    when (action) {
        RecordButtonAction.START -> recorder.gesture::startLocked
        RecordButtonAction.STOP -> recorder.actions::stop
        RecordButtonAction.RETRY_SAVE -> recorder.actions::retry
        RecordButtonAction.NONE -> NoClick
    }

@Composable
private fun RecordMorphGlyph(progress: Float) {
    val color = LocalContentColor.current
    val path = remember { Path() }
    Canvas(modifier = Modifier.size(RECORD_GLYPH_SIZE)) {
        drawPath(path = RecordShape.outline(progress = progress, size = size.minDimension, path = path), color = color)
    }
}

private val NoClick: () -> Unit = {}
private val RECORD_GLYPH_SIZE = 24.dp
```

- [ ] **Step 10: Add Today's recorder items and the level ring**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderItems.kt`:

```kotlin
package app.logdate.wear.v2.recorder

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.logdate.ui.glyphs.LogDateGlyph
import app.logdate.ui.glyphs.vector
import app.logdate.wear.R
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.moments.formatClockDuration
import app.logdate.wear.v2.permissions.openAppSettings
import app.logdate.wear.v2.permissions.openStorageSettings
import kotlin.time.Duration

object RecorderTags {
    const val ELAPSED = "recorder_elapsed"
    const val MESSAGE = "recorder_message"
    const val LOCK_HINT = "recorder_lock_hint"
    const val PAUSE = "recorder_pause"
    const val DISCARD = "recorder_discard"
    const val UNDO = "recorder_undo"
    const val FEELINGS = "recorder_feelings"
    const val FAILURE_ACTION = "recorder_failure_action"
    const val RING = "recorder_ring"
}

/** Whether Today shows the recorder in place of the day's summary. */
internal val RecorderState.replacesSummary: Boolean
    get() = this != RecorderState.Ready && this != RecorderState.Arming

/** Today's list while the recorder has something to show. */
internal fun TransformingLazyColumnScope.recorderItems(recorder: TodayRecorder) {
    when (val state = recorder.state) {
        is RecorderState.Live -> liveItems(state, recorder.actions)
        RecorderState.Saving -> item { RecorderMessage(stringResource(R.string.wear_today_recorder_saving)) }
        is RecorderState.Saved -> savedItems(state, recorder.actions)
        RecorderState.TooShort -> item { RecorderMessage(stringResource(R.string.wear_today_recorder_too_short), live = true) }
        is RecorderState.Failed -> failedItems(state.failure, recorder.actions)
        RecorderState.Ready, RecorderState.Arming -> Unit
    }
}

private fun TransformingLazyColumnScope.liveItems(
    live: RecorderState.Live,
    actions: RecorderActions,
) {
    item { ElapsedTime(live.elapsed) }
    item { LiveStatus(live) }
    if (live.mode == LiveMode.LOCKED) item { LockedControls(live, actions) }
}

private fun TransformingLazyColumnScope.savedItems(
    saved: RecorderState.Saved,
    actions: RecorderActions,
) {
    item {
        RecorderMessage(
            text = stringResource(R.string.wear_today_recorder_saved, formatClockDuration(saved.duration)),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            live = true,
        )
    }
    item {
        Button(onClick = actions::undo, modifier = Modifier.fillMaxWidth().testTag(RecorderTags.UNDO)) {
            Text(text = stringResource(R.string.wear_today_recorder_undo), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
    if (saved.feelingsOffered) item { FeelingRow(actions) }
    saved.feeling?.let { feeling ->
        item {
            val name = stringResource(feeling.labelRes)
            RecorderMessage(stringResource(R.string.wear_today_recorder_feeling_chosen, feeling.emoji, name))
        }
    }
}

private fun TransformingLazyColumnScope.failedItems(
    failure: RecorderFailure,
    actions: RecorderActions,
) {
    item { RecorderMessage(stringResource(failure.messageRes()), color = MaterialTheme.colorScheme.error, live = true) }
    item {
        val context = LocalContext.current
        Button(
            onClick = { runFailureAction(context, failure, actions) },
            modifier = Modifier.fillMaxWidth().testTag(RecorderTags.FAILURE_ACTION),
        ) {
            Text(text = stringResource(failure.actionRes()), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

/** The one action that helps with each failure: ask again, open Settings or storage, save again, or clear it. */
private fun runFailureAction(
    context: Context,
    failure: RecorderFailure,
    actions: RecorderActions,
) {
    when (failure) {
        RecorderFailure.MICROPHONE_BLOCKED -> {
            openAppSettings(context)
            actions.dismiss()
        }
        RecorderFailure.NOT_ENOUGH_STORAGE -> {
            openStorageSettings(context)
            actions.dismiss()
        }
        RecorderFailure.MICROPHONE_DENIED, RecorderFailure.SAVE_FAILED -> actions.retry()
        RecorderFailure.RECORDER_UNAVAILABLE, RecorderFailure.RECORDING_LOST -> actions.dismiss()
    }
}

private fun RecorderFailure.messageRes(): Int =
    when (this) {
        RecorderFailure.MICROPHONE_DENIED -> R.string.wear_today_recorder_failed_microphone
        RecorderFailure.MICROPHONE_BLOCKED -> R.string.wear_today_recorder_failed_microphone_off
        RecorderFailure.NOT_ENOUGH_STORAGE -> R.string.wear_today_recorder_failed_storage
        RecorderFailure.RECORDER_UNAVAILABLE -> R.string.wear_today_recorder_failed_start
        RecorderFailure.SAVE_FAILED -> R.string.wear_today_recorder_failed_save
        RecorderFailure.RECORDING_LOST -> R.string.wear_today_recorder_failed_lost
    }

private fun RecorderFailure.actionRes(): Int =
    when (this) {
        RecorderFailure.MICROPHONE_DENIED -> R.string.wear_today_recorder_allow
        RecorderFailure.MICROPHONE_BLOCKED -> R.string.wear_today_recorder_open_settings
        RecorderFailure.NOT_ENOUGH_STORAGE -> R.string.wear_today_recorder_open_storage
        RecorderFailure.SAVE_FAILED -> R.string.wear_today_recorder_try_again
        RecorderFailure.RECORDER_UNAVAILABLE, RecorderFailure.RECORDING_LOST -> R.string.wear_today_recorder_ok
    }

@Composable
private fun ElapsedTime(elapsed: Duration) {
    Text(
        text = formatClockDuration(elapsed),
        modifier = Modifier.fillMaxWidth().testTag(RecorderTags.ELAPSED),
        style = MaterialTheme.typography.numeralSmall,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun LiveStatus(live: RecorderState.Live) {
    when {
        live.confirmingDiscard ->
            RecorderMessage(
                text = stringResource(R.string.wear_today_recorder_discard_confirm),
                color = MaterialTheme.colorScheme.error,
                live = true,
            )
        live.mode == LiveMode.HOLDING -> LockHint(live.lockProgress)
        live.pausedByInterruption -> RecorderMessage(stringResource(R.string.wear_today_recorder_paused_by_app))
        live.paused -> RecorderMessage(stringResource(R.string.wear_today_recorder_paused))
        else -> RecorderMessage(stringResource(R.string.wear_today_recorder_recording))
    }
}

/** While holding: release to save, and a lock that fills in as the finger slides toward it. */
@Composable
private fun LockHint(progress: Float) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(RecorderTags.LOCK_HINT),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = LogDateGlyph.LOCK.vector(),
            contentDescription = null,
            modifier = Modifier.size(LOCK_GLYPH_SIZE).graphicsLayer { alpha = LOCK_MIN_ALPHA + (1f - LOCK_MIN_ALPHA) * progress },
        )
        Text(
            text = stringResource(R.string.wear_today_recorder_release_to_save),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.wear_today_recorder_slide_to_lock),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Pause and Discard beside Stop, once the recording is locked. */
@Composable
private fun LockedControls(
    live: RecorderState.Live,
    actions: RecorderActions,
) {
    val pauseLabel = stringResource(if (live.paused) R.string.wear_today_recorder_resume else R.string.wear_today_recorder_pause)
    val pauseIcon = if (live.paused) R.drawable.ic_recorder_resume else R.drawable.ic_recorder_pause
    val discardLabel =
        stringResource(if (live.confirmingDiscard) R.string.wear_today_recorder_discard_confirm else R.string.wear_today_recorder_discard)
    ButtonGroup(modifier = Modifier.fillMaxWidth()) {
        FilledTonalIconButton(onClick = actions::togglePause, modifier = Modifier.weight(1f).testTag(RecorderTags.PAUSE)) {
            Icon(painter = painterResource(pauseIcon), contentDescription = pauseLabel)
        }
        if (live.confirmingDiscard) {
            FilledIconButton(onClick = actions::discard, modifier = Modifier.weight(1f).testTag(RecorderTags.DISCARD)) {
                Icon(painter = painterResource(R.drawable.ic_recorder_discard), contentDescription = discardLabel)
            }
        } else {
            FilledTonalIconButton(onClick = actions::discard, modifier = Modifier.weight(1f).testTag(RecorderTags.DISCARD)) {
                Icon(painter = painterResource(R.drawable.ic_recorder_discard), contentDescription = discardLabel)
            }
        }
    }
}

/** The five feelings, one tap each, for four seconds after a save. */
@Composable
private fun FeelingRow(actions: RecorderActions) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(RecorderTags.FEELINGS),
        horizontalArrangement = Arrangement.spacedBy(FEELING_SPACING, Alignment.CenterHorizontally),
    ) {
        Feeling.entries.forEach { feeling ->
            val label = stringResource(feeling.labelRes)
            IconButton(
                onClick = { actions.chooseFeeling(feeling) },
                modifier = Modifier.size(FEELING_BUTTON_SIZE).semantics { contentDescription = label },
            ) {
                Text(text = feeling.emoji)
            }
        }
    }
}

@Composable
private fun RecorderMessage(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    live: Boolean = false,
) {
    Text(
        text = text,
        modifier =
            Modifier.fillMaxWidth().testTag(RecorderTags.MESSAGE).semantics {
                if (live) liveRegion = LiveRegionMode.Polite
            },
        color = color,
        style = style,
        textAlign = TextAlign.Center,
    )
}

private val LOCK_GLYPH_SIZE = 20.dp
private const val LOCK_MIN_ALPHA = 0.4f
private val FEELING_BUTTON_SIZE = 32.dp
private val FEELING_SPACING = 2.dp
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecordingRing.kt`:

```kotlin
package app.logdate.wear.v2.recorder

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The input level as short bars around the edge of the screen, oldest at the top, going clockwise. */
@Composable
internal fun RecordingRing(
    levels: List<Float>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.testTag(RecorderTags.RING)) {
        val outer = size.minDimension / 2f - RING_INSET.toPx()
        val longest = RING_BAR_LENGTH.toPx()
        val stroke = RING_STROKE.toPx()
        levels.takeLast(RING_BARS).forEachIndexed { index, level ->
            val angle = index.toFloat() / RING_BARS * FULL_TURN - QUARTER_TURN
            val inner = outer - longest * level.coerceIn(MIN_LEVEL, 1f)
            val direction = Offset(cos(angle), sin(angle))
            drawLine(
                color = color,
                start = center + direction * inner,
                end = center + direction * outer,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val RING_BARS = 50
private const val MIN_LEVEL = 0.08f
private val FULL_TURN = (2 * PI).toFloat()
private val QUARTER_TURN = (PI / 2).toFloat()
private val RING_INSET = 4.dp
private val RING_BAR_LENGTH = 10.dp
private val RING_STROKE = 2.dp
```

- [ ] **Step 11: Add the settings shortcuts**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/SystemSettings.kt`:

```kotlin
package app.logdate.wear.v2.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.aakira.napier.Napier

/** Opens LogDate's page in the system settings, where a permission refused for good can be turned on. */
fun openAppSettings(context: Context) {
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    openFirstAvailable(context, listOf(appDetails, Intent(Settings.ACTION_SETTINGS)))
}

/** Opens the system storage screen, or the main settings on a watch without one. */
fun openStorageSettings(context: Context) {
    openFirstAvailable(context, listOf(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Intent(Settings.ACTION_SETTINGS)))
}

private fun openFirstAvailable(
    context: Context,
    intents: List<Intent>,
) {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            Napier.w("No screen for ${intent.action} on this watch", e)
        }
    }
    Napier.e("No settings screen could be opened on this watch")
}
```

- [ ] **Step 12: Draw the recorder on Today**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayPage.kt` with:

```kotlin
package app.logdate.wear.v2.today

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.compose.material3.timeTextCurvedText
import androidx.wear.compose.material3.timeTextSeparator
import app.logdate.client.domain.today.TodaySummary
import app.logdate.client.repository.journals.JournalNote
import app.logdate.ui.glyphs.LogDateGlyph
import app.logdate.ui.glyphs.vector
import app.logdate.ui.streak.Campfire
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.campfireShortDescription
import app.logdate.wear.R
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.moments.formatClockDuration
import app.logdate.wear.v2.moments.momentClockTime
import app.logdate.wear.v2.moments.momentLabel
import app.logdate.wear.v2.recorder.RecordEdgeButton
import app.logdate.wear.v2.recorder.RecorderState
import app.logdate.wear.v2.recorder.RecordingRing
import app.logdate.wear.v2.recorder.TodayRecorder
import app.logdate.wear.v2.recorder.recorderItems
import app.logdate.wear.v2.recorder.replacesSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

object TodayTags {
    const val PAGE = "today_page"
    const val CAMPFIRE = "today_campfire"
    const val SUMMARY = "today_summary"
    const val LATEST = "today_latest"
    const val GET_PHONE_APP = "today_get_phone_app"
    const val MARK = "today_mark"
    const val ADD = "today_add"
    const val RECORD = "today_record"
}

/**
 * The controls Today shows. Each appears only when it is set, and the change that makes a control
 * work is the change that sets it, so Today never shows a control that does nothing.
 */
data class TodayControls(
    val recorder: TodayRecorder? = null,
    val onMark: (() -> Unit)? = null,
    val onAdd: (() -> Unit)? = null,
)

/**
 * Home's first page: the date in the time text, the campfire, the day's summary line, the latest
 * moment and, when the paired Android phone lacks LogDate, a card that opens its Play listing.
 * Record sits at the end as the edge button, with Mark and + above it. While the recorder has
 * something to show, it takes the summary's place: the live recording with Pause and Discard,
 * then the saved recording with Undo and the five feelings, or what went wrong.
 */
@Composable
fun TodayPage(
    state: TodayUiState,
    handoff: HandoffState,
    onGetPhoneApp: () -> Unit,
    onHandoffDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    controls: TodayControls = TodayControls(),
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
) {
    val recorderState = controls.recorder?.state
    val timeText: @Composable () -> Unit = { TodayTimeText(state = state, timeSource = timeSource) }
    TodayScaffold(listState = listState, timeText = timeText, recorder = controls.recorder) { contentPadding ->
        if (recorderState is RecorderState.Live) RecordingRing(levels = recorderState.levels, modifier = Modifier.matchParentSize())
        TodayList(
            state = state,
            handoff = handoff,
            controls = controls,
            onGetPhoneApp = onGetPhoneApp,
            listState = listState,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    }
    val continueOnPhone = OpenOnPhoneDialogDefaults.text
    val curvedTextStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = handoff == HandoffState.SENT,
        onDismissRequest = onHandoffDismissed,
        curvedText = { openOnPhoneDialogCurvedText(text = continueOnPhone, style = curvedTextStyle) },
    )
}

/**
 * The time text Today shows: the date, when there is one, then the time. [TodayPage] places it in
 * its scaffold, which fades it in; a screenshot places it at the app level to capture it.
 */
@Composable
fun TodayTimeText(
    state: TodayUiState,
    modifier: Modifier = Modifier,
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
) {
    val dateText = (state as? TodayUiState.Ready)?.let { ready -> todayDateText(ready.date) }
    TimeText(modifier = modifier, timeSource = timeSource) { time ->
        if (dateText != null) {
            timeTextCurvedText(dateText)
            timeTextSeparator()
        }
        timeTextCurvedText(time)
    }
}

@Composable
private fun TodayScaffold(
    listState: TransformingLazyColumnState,
    timeText: @Composable () -> Unit,
    recorder: TodayRecorder?,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    if (recorder == null) {
        ScreenScaffold(scrollState = listState, timeText = timeText, content = content)
        return
    }
    ScreenScaffold(scrollState = listState, edgeButton = { RecordEdgeButton(recorder) }, timeText = timeText, content = content)
}

@Composable
private fun TodayList(
    state: TodayUiState,
    handoff: HandoffState,
    controls: TodayControls,
    onGetPhoneApp: () -> Unit,
    listState: TransformingLazyColumnState,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val spec = rememberTransformationSpec()
    val recorder = controls.recorder?.takeIf { it.state.replacesSummary }
    val padding = if (recorder == null && startsWithCampfire(state)) contentPadding else contentPadding.belowTimeText()
    TransformingLazyColumn(state = listState, contentPadding = padding, modifier = modifier.testTag(TodayTags.PAGE)) {
        if (recorder != null) {
            recorderItems(recorder)
            return@TransformingLazyColumn
        }
        when (state) {
            TodayUiState.Loading -> Unit
            TodayUiState.Failed -> item { CenteredText(stringResource(R.string.wear_today_failed)) }
            is TodayUiState.Ready -> {
                state.campfire?.let { campfire -> item { TodayCampfire(campfire) } }
                item { CenteredText(summaryText(state.summary, state.feeling), Modifier.testTag(TodayTags.SUMMARY)) }
                state.summary.latestMoment?.let { latest -> item { LatestMoment(latest, state.timeZone) } }
                if (state.phoneCard is PhoneCard.GetApp) {
                    item {
                        GetPhoneAppCard(
                            handoff = handoff,
                            onClick = onGetPhoneApp,
                            modifier = Modifier.transformedHeight(this, spec),
                            transformation = SurfaceTransformation(spec),
                        )
                    }
                }
                if (controls.onMark != null || controls.onAdd != null) item { TodayButtonGroup(controls) }
            }
        }
    }
}

private fun startsWithCampfire(state: TodayUiState): Boolean = state is TodayUiState.Ready && state.campfire != null

/** Text is wider than the campfire, so a list that starts with text clears the curve of the time text. */
@Composable
private fun PaddingValues.belowTimeText(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding() + TIME_TEXT_CLEARANCE,
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding(),
    )
}

@Composable
private fun CenteredText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TodayCampfire(campfire: CampfirePresentation) {
    Campfire(
        phase = campfire.phase,
        size = campfire.size,
        modifier = Modifier.size(campfireSize).testTag(TodayTags.CAMPFIRE),
        waitingForToday = campfire.isWaitingForToday,
        contentDescription = campfireShortDescription(campfire),
    )
}

@Composable
private fun LatestMoment(
    note: JournalNote,
    timeZone: TimeZone,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TodayTags.LATEST),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = momentClockTime(note.creationTimestamp, timeZone),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = momentLabel(note),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GetPhoneAppCard(
    handoff: HandoffState,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val body = if (handoff == HandoffState.FAILED) R.string.wear_today_get_phone_app_failed else R.string.wear_today_get_phone_app_body
    TitleCard(
        onClick = onClick,
        title = { Text(stringResource(R.string.wear_today_get_phone_app_title)) },
        modifier = modifier.fillMaxWidth().testTag(TodayTags.GET_PHONE_APP),
        enabled = handoff != HandoffState.SENDING,
        transformation = transformation,
    ) {
        Text(stringResource(body))
    }
}

@Composable
private fun TodayButtonGroup(controls: TodayControls) {
    val addLabel = stringResource(R.string.wear_today_add)
    val markLabel = stringResource(R.string.wear_today_mark)
    ButtonGroup(modifier = Modifier.fillMaxWidth()) {
        controls.onMark?.let { onMark ->
            FilledTonalButton(onClick = onMark, modifier = Modifier.weight(1f).testTag(TodayTags.MARK)) {
                CenteredContent { Icon(imageVector = LogDateGlyph.MARK.vector(), contentDescription = markLabel) }
            }
        }
        controls.onAdd?.let { onAdd ->
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier.weight(1f).testTag(TodayTags.ADD).semantics { contentDescription = addLabel },
            ) {
                CenteredContent { Text(stringResource(R.string.wear_today_add_symbol)) }
            }
        }
    }
}

@Composable
private fun RowScope.CenteredContent(content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun summaryText(
    summary: TodaySummary,
    feeling: Feeling?,
): String {
    val line = summaryLine(summary)
    return if (feeling == null) line else "${feeling.emoji} $line"
}

@Composable
private fun summaryLine(summary: TodaySummary): String {
    if (summary.momentCount == 0) return stringResource(R.string.wear_today_no_moments)
    val moments = pluralStringResource(R.plurals.wear_today_moments, summary.momentCount, summary.momentCount)
    val places =
        if (summary.placeCount > 0) {
            pluralStringResource(R.plurals.wear_today_places, summary.placeCount, summary.placeCount)
        } else {
            null
        }
    val voice =
        if (summary.voiceDuration > Duration.ZERO) {
            stringResource(R.string.wear_today_voice, formatClockDuration(summary.voiceDuration))
        } else {
            null
        }
    return listOfNotNull(moments, places, voice).joinToString(stringResource(R.string.wear_today_summary_separator))
}

/** [date] as a short weekday, month and day in the user's locale, such as "Sat, Oct 3". */
@Composable
private fun todayDateText(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, date) {
        val pattern = DateFormat.getBestDateTimePattern(locale, DATE_SKELETON)
        DateTimeFormatter.ofPattern(pattern, locale).format(date.toJavaLocalDate())
    }
}

private const val DATE_SKELETON = "EEEMMMd"
private val campfireSize = 48.dp
private val TIME_TEXT_CLEARANCE = 28.dp
```

- [ ] **Step 13: Wire the recorder into the app**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` with:

```kotlin
package app.logdate.wear.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import app.logdate.ui.platform.LocalLogDateHaptics
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowViewModel
import app.logdate.wear.v2.home.HomeScreen
import app.logdate.wear.v2.navigation.WearRoute
import app.logdate.wear.v2.navigation.toV2BackStack
import app.logdate.wear.v2.permissions.rememberPermissionRequester
import app.logdate.wear.v2.recorder.RecorderViewModel
import app.logdate.wear.v2.recorder.TodayRecorder
import app.logdate.wear.v2.recorder.locksPaging
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.TodayControls
import app.logdate.wear.v2.today.TodayPage
import app.logdate.wear.v2.today.TodayViewModel
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

object WearAppV2Tags {
    const val SHELL = "wear_app_v2_shell"
}

/**
 * The redesigned watch app, shown while WATCH_EXPERIENCE_V2 is on.
 *
 * [initialRequest] picks the first back stack; a recreated activity restores the one it saved
 * instead. Each item of [newRequests] replaces the back stack, as a cold launch from a tile does.
 * Swiping right dismisses the top screen. Each screen's view models live as long as its entry; the
 * recorder is resolved here, outside every entry, so it belongs to the activity, and the
 * permissions it asks for are requested here.
 */
@Composable
fun WearAppV2(
    initialRequest: WearLaunchRequest?,
    newRequests: Flow<WearLaunchRequest>,
) {
    val recorder = koinViewModel<RecorderViewModel>()
    val backStack = rememberNavBackStack(*initialRequest.toV2BackStack().toTypedArray())
    LaunchedEffect(newRequests) {
        newRequests.collect { request ->
            backStack.clear()
            backStack.addAll(request.toV2BackStack())
        }
    }
    RecorderPermissionPrompts(recorder)
    LogDateWearTheme {
        CompositionLocalProvider(LocalLogDateHaptics provides koinInject<LogDateHaptics>()) {
            AppScaffold(modifier = Modifier.testTag(WearAppV2Tags.SHELL)) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                            rememberViewModelStoreNavEntryDecorator<NavKey>(),
                        ),
                    sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy<NavKey>()),
                    entryProvider =
                        entryProvider {
                            entry<WearRoute.Home> { HomeEntry(recorder) }
                        },
                )
            }
        }
    }
}

/** Asks for the permissions the recorder needs at the moment it needs them, and gives it the answers. */
@Composable
private fun RecorderPermissionPrompts(recorder: RecorderViewModel) {
    val requester = rememberPermissionRequester(onResult = recorder::onPermissionResult)
    LaunchedEffect(recorder, requester) {
        recorder.permissionRequests.collect { permission -> requester.request(permission) }
    }
}

@Composable
private fun HomeEntry(recorder: RecorderViewModel) {
    val recorderState by recorder.state.collectAsState()
    val controls =
        remember(recorderState, recorder) {
            TodayControls(recorder = TodayRecorder(recorderState, gesture = recorder, actions = recorder))
        }
    HomeScreen(
        pagingLocked = recorderState.locksPaging,
        today = { listState -> TodayEntry(listState, controls) },
        flow = { listState -> FlowEntry(listState) },
    )
}

@Composable
private fun TodayEntry(
    listState: TransformingLazyColumnState,
    controls: TodayControls,
) {
    val viewModel = koinViewModel<TodayViewModel>()
    val state by viewModel.uiState.collectAsState()
    val handoff by viewModel.handoff.collectAsState()
    TodayPage(
        state = state,
        handoff = handoff,
        onGetPhoneApp = viewModel::getPhoneApp,
        onHandoffDismissed = viewModel::dismissHandoff,
        controls = controls,
        listState = listState,
    )
}

@Composable
private fun FlowEntry(listState: TransformingLazyColumnState) {
    val viewModel = koinViewModel<FlowViewModel>()
    val state by viewModel.uiState.collectAsState()
    FlowPage(state = state, listState = listState)
}
```

- [ ] **Step 14: Run the unit tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.*'
```

Expected: `BUILD SUCCESSFUL`; `RecordButtonModelTest` 6 tests passed.

- [ ] **Step 15: Run the GMD tests**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.TodayRecorderTest,app.logdate.wear.e2e.RecordGestureTest,app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.ExperienceGateTest,app.logdate.wear.e2e.HomePagerRotaryTest
```

Expected: `BUILD SUCCESSFUL`. `TodayRecorderTest` passes 5 tests; `holding_shows_the_live_recording_and_the_same_hold_reaches_the_lock` is the proof that Today switching to the live recording does not drop the finger.

- [ ] **Step 16: Run screenshot validation to verify it fails**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: FAIL; the report lists the 45 `RecorderScreenshots` previews as missing references and `S09_TodayWithControls` (both sizes) and `S11_TodayWithControlsLargeFont` as changed, because Record now draws the shared record shape. Every other preview passes.

- [ ] **Step 17: Record the references**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/RecorderScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: `BUILD SUCCESSFUL`, `45`, and the status lists only the new `RecorderScreenshots/` directory and three modified `TodayPageScreenshots/S09_*`/`S11_*` files. If any other reference changed, stop and find out why.

- [ ] **Step 18: Look at the references**

Open these with the Read tool and check them against the spec: `R01_Holding_Small Round_*.png` ("0:07", the lock glyph half filled, "Release to save" over "Slide up to lock", bars around the edge, Record part way to a square), `R02_Locked_Small Round_*.png` ("0:42", "Recording", Pause and Discard side by side, Record a square), `R05_ConfirmDiscard_Small Round_*.png` ("Tap again to discard" in the error color, a filled Discard), `R07_SavedWithFeelings_Small Round_*.png` ("Saved · 0:42", Undo, five emoji in one row inside the round edge), `R11_MicrophoneOff_Small Round_*.png` ("The microphone is off for LogDate", Open Settings), and every `*_Large Font_*.png` (nothing clipped at the round edge; the feelings row may scroll but must not overlap). Fix the layout and record again if any is wrong.

- [ ] **Step 19: Validate the references and lint**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest :app:wear:ktlintCheck :app:wear:detekt
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 20: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task5-commit.txt <<'EOF'
chore(wear): record from Today in the new watch app

Today in the redesigned watch app now has Record as its edge button.
Hold it to talk and lift to save, slide up to lock, or tap for a
recording that goes on by itself. A quick swipe still scrolls Home.
The button's shape turns from the record circle into Stop as the
finger slides to the lock.

While recording, Today shows the time, the input level around the
edge of the screen, and either "Release to save" or, once locked,
Pause and Discard. After a save it shows Undo and the five feelings
for a one-tap mood. Too short, a refused or turned-off microphone, a
full watch, and a failed save each say what happened and offer the one
action that helps. The microphone is asked for the first time
someone records, and notifications on the first locked recording.

Screenshots cover every state on small and large round watches and at
a 1.24 font scale.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 21: Commit**

```bash
git restore --staged . && git add app/wear/src/main/res/values/strings_recorder.xml app/wear/src/main/res/drawable/ic_recorder_pause.xml app/wear/src/main/res/drawable/ic_recorder_resume.xml app/wear/src/main/res/drawable/ic_recorder_discard.xml app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/TodayRecorder.kt app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecordEdgeButton.kt app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderItems.kt app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecordingRing.kt app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/SystemSettings.kt app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayPage.kt app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WearAllSizesPreview.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/PreviewRecorder.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/RecorderScreenshots.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/TodayPageScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/RecorderScreenshots app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/TodayPageScreenshots app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecordButtonModelTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/TodayRecorderTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task5-commit.txt
```

---
### Task 6: The recording on the watch face (`chore(wear)`)

A recording on the watch should show on the watch face, with its time, and take you back to Today when tapped: an Ongoing Activity. The recording notification is built inside `client/media`'s `AudioRecordingService`, which `AndroidAudioNotificationHandler` serves (`AudioRecordingService.kt:145`, `:227`, `:271`, `:302`), and the service has no dependency injection. So `client/media` gains a narrow hook, `RecordingNotificationDecorator`, which an app supplies by having its `Application` implement `RecordingNotificationDecorator.Provider`, the same shape as WorkManager's `Configuration.Provider`. The handler applies it before building every recording notification; the phone supplies none and is unchanged. The watch's decorator attaches the Ongoing Activity with a stopwatch that stops while paused, and only while WATCH_EXPERIENCE_V2 is on, read through a `FeatureSwitch` because notification code can't suspend. Without the notifications permission the system shows no notification and so no Ongoing Activity; Task 4 asks for it on the first locked recording.

**Files:**
- Create: `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/RecordingNotificationDecorator.kt`
- Modify: `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioNotificationHandler.kt:14-21`, `:73-76`, `:131`, `:171-176`
- Modify: `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AudioRecordingService.kt:271-274`, `:302-305`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/launch/FeatureSwitch.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/recording/OngoingRecordingDecorator.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt` (whole file)
- Modify: `app/wear/src/main/res/values/strings_recorder.xml`
- Test: `client/media/src/androidHostTest/kotlin/app/logdate/client/media/audio/RecordingNotificationDecoratorTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/recording/OngoingStopwatchTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/launch/FeatureSwitchTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/OngoingRecordingE2ETest.kt`

**Interfaces:**
- Consumes: `NotificationCompat.Builder`, `OngoingActivity.Builder`, `Status.forPart`, `Status.StopwatchPart`, `FeatureFlagStore.observe(flag)`, `R.drawable.ic_glyph_record`, `MainActivity`.
- Produces:
  - `fun interface RecordingNotificationDecorator { fun decorate(context: Context, builder: NotificationCompat.Builder, notificationId: Int, state: RecordingNotificationState); interface Provider { val recordingNotificationDecorator: RecordingNotificationDecorator } }`, `data class RecordingNotificationState(isRecording: Boolean, startTimeMillis: Long, recordedMillis: Long)`, `fun recordingNotificationDecoratorOf(application: Any?): RecordingNotificationDecorator?` in `app.logdate.client.media.audio`.
  - `AndroidAudioNotificationHandler(context, decorator: RecordingNotificationDecorator? = recordingNotificationDecoratorOf(context.applicationContext))`, and `createRecordingNotification(isRecording, startTimeMillis, recordedMillis: Long = 0L)` / `updateRecordingNotification(isRecording, startTimeMillis, recordedMillis: Long = 0L)`.
  - `class FeatureSwitch(featureFlags: FeatureFlagStore, flag: FeatureFlag) { val state: StateFlow<Boolean>; val isOn: Boolean; fun start(scope: CoroutineScope) }` in `app.logdate.wear.launch`.
  - `class OngoingRecordingDecorator(enabled: () -> Boolean, elapsedRealtime: () -> Long = SystemClock::elapsedRealtime) : RecordingNotificationDecorator`, `data class OngoingStopwatch(timeZeroMillis: Long, pausedAtMillis: Long?)`, `internal fun ongoingStopwatch(state: RecordingNotificationState, nowElapsedRealtime: Long): OngoingStopwatch` in `app.logdate.wear.recording`.
  - `LogDateWearApplication : RecordingNotificationDecorator.Provider` with `internal val newWatchApp: FeatureSwitch`.

- [ ] **Step 1: Write the failing hook test**

Create `client/media/src/androidHostTest/kotlin/app/logdate/client/media/audio/RecordingNotificationDecoratorTest.kt`:

```kotlin
package app.logdate.client.media.audio

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class RecordingNotificationDecoratorTest {
    private val decorator = RecordingNotificationDecorator { _, _, _, _ -> }

    @Test
    fun `an app that supplies a decorator has it applied`() {
        val application =
            object : RecordingNotificationDecorator.Provider {
                override val recordingNotificationDecorator = decorator
            }

        assertSame(decorator, recordingNotificationDecoratorOf(application))
    }

    @Test
    fun `an app that supplies none gets the plain notification`() {
        assertNull(recordingNotificationDecoratorOf(Any()))
        assertNull(recordingNotificationDecoratorOf(null))
    }
}
```

- [ ] **Step 2: Write the failing stopwatch and switch tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/recording/OngoingStopwatchTest.kt`:

```kotlin
package app.logdate.wear.recording

import app.logdate.client.media.audio.RecordingNotificationState
import org.junit.Test
import kotlin.test.assertEquals

class OngoingStopwatchTest {
    @Test
    fun `a running recording counts up from when its recorded time began`() {
        val state = RecordingNotificationState(isRecording = true, startTimeMillis = 1_000L, recordedMillis = 42_000L)

        assertEquals(
            OngoingStopwatch(timeZeroMillis = 58_000L, pausedAtMillis = null),
            ongoingStopwatch(state, nowElapsedRealtime = 100_000L),
        )
    }

    @Test
    fun `a paused recording stops the stopwatch at the time recorded`() {
        val state = RecordingNotificationState(isRecording = false, startTimeMillis = 1_000L, recordedMillis = 42_000L)

        assertEquals(
            OngoingStopwatch(timeZeroMillis = 58_000L, pausedAtMillis = 100_000L),
            ongoingStopwatch(state, nowElapsedRealtime = 100_000L),
        )
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/launch/FeatureSwitchTest.kt`:

```kotlin
package app.logdate.wear.launch

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureSwitchTest {
    private val flag = MutableStateFlow(true)
    private val featureFlags = mockk<FeatureFlagStore> { every { observe(FeatureFlag.WATCH_EXPERIENCE_V2) } returns flag }

    @Test
    fun `the switch is off until the flag has been read`() =
        runTest {
            val switch = FeatureSwitch(featureFlags, FeatureFlag.WATCH_EXPERIENCE_V2)
            assertFalse(switch.isOn)

            switch.start(backgroundScope)
            runCurrent()

            assertTrue(switch.isOn)
        }

    @Test
    fun `the switch follows the flag`() =
        runTest {
            val switch = FeatureSwitch(featureFlags, FeatureFlag.WATCH_EXPERIENCE_V2)
            switch.start(backgroundScope)
            runCurrent()

            flag.value = false
            runCurrent()

            assertFalse(switch.isOn)
        }

    @Test
    fun `a flag that cannot be read leaves the switch off`() =
        runTest {
            every { featureFlags.observe(FeatureFlag.WATCH_EXPERIENCE_V2) } returns flow { throw IllegalStateException("datastore closed") }
            val switch = FeatureSwitch(featureFlags, FeatureFlag.WATCH_EXPERIENCE_V2)

            switch.start(backgroundScope)
            runCurrent()

            assertFalse(switch.isOn)
        }
}
```

- [ ] **Step 3: Write the failing GMD Ongoing Activity test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/OngoingRecordingE2ETest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.media.audio.AndroidAudioStorage
import app.logdate.client.media.audio.AndroidRecordingServiceController
import app.logdate.client.media.audio.AudioRecordingService
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.media.audio.RecordingSessionOptions
import app.logdate.client.media.device.AndroidAudioRouteRepository
import app.logdate.wear.LogDateWearApplication
import app.logdate.wear.R
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.recording.WearAudioRecordingManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Records through the real foreground service on a Wear OS emulator and reads the Ongoing Activity
 * back from the posted notification, the way the watch face finds it.
 */
@RunWith(AndroidJUnit4::class)
class OngoingRecordingE2ETest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var activity: ActivityScenario<MainActivity>
    private lateinit var manager: WearAudioRecordingManager
    private val recordedFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        runBlocking { setNewWatchApp(enabled = true) }
        // A microphone foreground service may only start while the app is on screen.
        activity = ActivityScenario.launch(MainActivity::class.java)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS).forEach { permission ->
            automation.grantRuntimePermission(context.packageName, permission)
        }
        manager =
            WearAudioRecordingManager(
                storageChecker = StorageSpaceChecker(context),
                audioStorage = AndroidAudioStorage(context),
                audioRouteRepository = AndroidAudioRouteRepository(context),
                serviceController =
                    AndroidRecordingServiceController(
                        context = context,
                        options =
                            RecordingSessionOptions(
                                maxDurationMs = WearAudioRecordingManager.MAX_RECORDING_DURATION.inWholeMilliseconds,
                                pauseOnInterruption = true,
                                holdWakeLock = true,
                                crashSafe = true,
                            ),
                    ),
                microphonePermission = { true },
            )
    }

    @After
    fun tearDown() =
        runBlocking {
            if (manager.isRecording.value) manager.discard()
            recordedFiles.forEach { file ->
                file.delete()
                CrashSafeRecording.inFlightFile(file).delete()
            }
            activity.close()
            setNewWatchApp(enabled = false)
        }

    @Test
    fun a_recording_shows_on_the_watch_face_until_it_stops() =
        runBlocking {
            assertTrue(manager.start())

            val ongoing = awaitOngoingActivity { true }
            assertEquals(AudioRecordingService.NOTIFICATION_ID, ongoing.notificationId)
            assertEquals(context.getString(R.string.wear_ongoing_recording_title), ongoing.title)
            assertNotNull(ongoing.touchIntent)

            manager.stop()?.let { path -> recordedFiles += File(path) }
            awaitNoOngoingActivity()
        }

    @Test
    fun a_paused_recording_stops_its_stopwatch() =
        runBlocking {
            assertTrue(manager.start())
            delay(1_500)

            assertTrue(manager.pause())
            awaitOngoingActivity { ongoing -> ongoing.stopwatch().isPaused }
            assertTrue(manager.resume())
            awaitOngoingActivity { ongoing -> !ongoing.stopwatch().isPaused }
        }

    @Test
    fun the_current_watch_app_records_without_an_ongoing_activity() =
        runBlocking {
            setNewWatchApp(enabled = false)

            assertTrue(manager.start())
            delay(1_500)

            assertNull(OngoingActivity.recoverOngoingActivity(context))
        }

    private suspend fun awaitOngoingActivity(matches: (OngoingActivity) -> Boolean): OngoingActivity {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val ongoing = OngoingActivity.recoverOngoingActivity(context)
            if (ongoing != null && matches(ongoing)) return ongoing
            delay(POLL_MS)
        }
        throw AssertionError("No matching Ongoing Activity within $TIMEOUT_MS ms")
    }

    private suspend fun awaitNoOngoingActivity() {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (OngoingActivity.recoverOngoingActivity(context) == null) return
            delay(POLL_MS)
        }
        throw AssertionError("The Ongoing Activity was still posted after the recording stopped")
    }

    private fun OngoingActivity.stopwatch(): Status.StopwatchPart {
        val status = assertNotNull(status)
        return status.getPart(status.partNames.single()) as Status.StopwatchPart
    }

    /** Sets the flag and waits until the app's switch, which the notification reads, has seen it. */
    private suspend fun setNewWatchApp(enabled: Boolean) {
        KoinJavaComponent.getKoin().get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        val app = context as LogDateWearApplication
        withTimeout(TIMEOUT_MS) { app.newWatchApp.state.first { it == enabled } }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 200L
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:media:testAndroidHostTest --tests 'app.logdate.client.media.audio.RecordingNotificationDecoratorTest'; ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.recording.OngoingStopwatchTest' --tests 'app.logdate.wear.launch.FeatureSwitchTest'
```

Expected: both FAIL at compilation with `Unresolved reference 'RecordingNotificationDecorator'` / `'recordingNotificationDecoratorOf'`, and `'RecordingNotificationState'`, `'ongoingStopwatch'`, `'FeatureSwitch'`.

- [ ] **Step 5: Add the hook to `client/media`**

Create `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/RecordingNotificationDecorator.kt`:

```kotlin
package app.logdate.client.media.audio

import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * Adds to the recording notification before it is posted. The watch uses it to attach an Ongoing
 * Activity, so a recording shows on the watch face; the phone has none.
 */
fun interface RecordingNotificationDecorator {
    fun decorate(
        context: Context,
        builder: NotificationCompat.Builder,
        notificationId: Int,
        state: RecordingNotificationState,
    )

    /** Implemented by an app's `Application` to supply its decorator. */
    interface Provider {
        val recordingNotificationDecorator: RecordingNotificationDecorator
    }
}

/**
 * The recording a notification describes.
 *
 * @property isRecording False while paused.
 * @property startTimeMillis When the recording started, in wall-clock milliseconds.
 * @property recordedMillis How much has been recorded so far, without pauses.
 */
data class RecordingNotificationState(
    val isRecording: Boolean,
    val startTimeMillis: Long,
    val recordedMillis: Long,
)

/** The decorator [application] supplies, or null when it supplies none. */
fun recordingNotificationDecoratorOf(application: Any?): RecordingNotificationDecorator? =
    (application as? RecordingNotificationDecorator.Provider)?.recordingNotificationDecorator
```

- [ ] **Step 6: Apply the decorator in the notification handler**

In `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioNotificationHandler.kt`, replace

```kotlin
/**
 * Handler for audio recording notifications on Android.
 *
 * Creates and manages foreground notification required for audio recording.
 */
class AndroidAudioNotificationHandler(
    private val context: Context,
) {
```

with

```kotlin
/**
 * Handler for audio recording notifications on Android.
 *
 * Creates and manages foreground notification required for audio recording. When the app's
 * `Application` supplies a [RecordingNotificationDecorator], every recording notification goes
 * through it before it is built.
 */
class AndroidAudioNotificationHandler(
    private val context: Context,
    private val decorator: RecordingNotificationDecorator? = recordingNotificationDecoratorOf(context.applicationContext),
) {
```

replace

```kotlin
    fun createRecordingNotification(
        isRecording: Boolean = true,
        startTimeMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Notification {
```

with

```kotlin
    fun createRecordingNotification(
        isRecording: Boolean = true,
        startTimeMillis: Long = Clock.System.now().toEpochMilliseconds(),
        recordedMillis: Long = 0L,
    ): Notification {
```

replace the last line of that function

```kotlin
        return builder.build()
    }
```

with

```kotlin
        decorate(builder, RecordingNotificationState(isRecording, startTimeMillis, recordedMillis))
        return builder.build()
    }

    /** A decorator that fails never stops the recording notification; it is posted without the decoration. */
    private fun decorate(
        builder: NotificationCompat.Builder,
        state: RecordingNotificationState,
    ) {
        val decorator = decorator ?: return
        try {
            decorator.decorate(context, builder, NOTIFICATION_ID, state)
        } catch (e: Exception) {
            Napier.e("The recording notification decorator failed; posting the notification without it", e)
        }
    }
```

and replace

```kotlin
    fun updateRecordingNotification(
        isRecording: Boolean,
        startTimeMillis: Long,
    ) {
        try {
            val notification = createRecordingNotification(isRecording, startTimeMillis)
```

with

```kotlin
    fun updateRecordingNotification(
        isRecording: Boolean,
        startTimeMillis: Long,
        recordedMillis: Long = 0L,
    ) {
        try {
            val notification = createRecordingNotification(isRecording, startTimeMillis, recordedMillis)
```

- [ ] **Step 7: Pass the recorded time when the service pauses and resumes**

In `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AudioRecordingService.kt`, replace

```kotlin
            notificationHandler.updateRecordingNotification(
                isRecording = false,
                startTimeMillis = recordingStartTime,
            )
```

with

```kotlin
            notificationHandler.updateRecordingNotification(
                isRecording = false,
                startTimeMillis = recordingStartTime,
                recordedMillis = recordedMillis(),
            )
```

replace

```kotlin
            notificationHandler.updateRecordingNotification(
                isRecording = true,
                startTimeMillis = recordingStartTime,
            )
```

with

```kotlin
            notificationHandler.updateRecordingNotification(
                isRecording = true,
                startTimeMillis = recordingStartTime,
                recordedMillis = recordedMillis(),
            )
```

and add this function right after `fun isRecordingPaused(): Boolean = isPaused && _recordingState.value.isRecording`:

```kotlin

    /** How much has been recorded so far, without pauses, to the second the duration ticker counts. */
    private fun recordedMillis(): Long = _recordingState.value.durationSeconds * 1_000L
```

- [ ] **Step 8: Add the feature switch**

Create `app/wear/src/main/kotlin/app/logdate/wear/launch/FeatureSwitch.kt`:

```kotlin
package app.logdate.wear.launch

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * The latest value of [flag] for code that can't suspend, such as a notification builder. It reads
 * false until [start] has read the flag, and stays false when the flag can't be read.
 */
class FeatureSwitch(
    private val featureFlags: FeatureFlagStore,
    private val flag: FeatureFlag,
) {
    private val current = MutableStateFlow(false)

    val state: StateFlow<Boolean> = current.asStateFlow()

    val isOn: Boolean get() = current.value

    /** Follows the flag in [scope] for the life of the app. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            featureFlags
                .observe(flag)
                .catch { error ->
                    Napier.e("Couldn't read ${flag.name}; treating it as off", error)
                    emit(false)
                }.collect { enabled -> current.value = enabled }
        }
    }
}
```

- [ ] **Step 9: Add the Ongoing Activity decorator**

Add this line to `app/wear/src/main/res/values/strings_recorder.xml`, replacing the closing `</resources>`:

```xml
    <!-- The recording's Ongoing Activity on the watch face -->
    <string name="wear_ongoing_recording_title">Recording</string>
</resources>
```

Create `app/wear/src/main/kotlin/app/logdate/wear/recording/OngoingRecordingDecorator.kt`:

```kotlin
package app.logdate.wear.recording

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import app.logdate.client.media.audio.RecordingNotificationDecorator
import app.logdate.client.media.audio.RecordingNotificationState
import app.logdate.wear.R
import app.logdate.wear.presentation.MainActivity

/**
 * The stopwatch a recording's Ongoing Activity shows, in [SystemClock.elapsedRealtime]
 * milliseconds: it reads zero at [timeZeroMillis] and stands still from [pausedAtMillis], when set.
 */
data class OngoingStopwatch(
    val timeZeroMillis: Long,
    val pausedAtMillis: Long?,
)

internal fun ongoingStopwatch(
    state: RecordingNotificationState,
    nowElapsedRealtime: Long,
): OngoingStopwatch =
    OngoingStopwatch(
        timeZeroMillis = nowElapsedRealtime - state.recordedMillis,
        pausedAtMillis = if (state.isRecording) null else nowElapsedRealtime,
    )

/**
 * Shows a recording on the watch face: an Ongoing Activity with the record glyph and a stopwatch
 * that stands still while paused. Tapping it opens the app on Today, where Stop is. Only the new
 * watch app gets one, while [enabled] reads true.
 */
class OngoingRecordingDecorator(
    private val enabled: () -> Boolean,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : RecordingNotificationDecorator {
    override fun decorate(
        context: Context,
        builder: NotificationCompat.Builder,
        notificationId: Int,
        state: RecordingNotificationState,
    ) {
        if (!enabled()) return
        val stopwatch = ongoingStopwatch(state, elapsedRealtime())
        val pausedAt = stopwatch.pausedAtMillis
        val part =
            if (pausedAt == null) {
                Status.StopwatchPart(stopwatch.timeZeroMillis)
            } else {
                Status.StopwatchPart(stopwatch.timeZeroMillis, pausedAt)
            }
        OngoingActivity
            .Builder(context, notificationId, builder)
            .setStaticIcon(R.drawable.ic_glyph_record)
            .setTouchIntent(openToday(context))
            .setTitle(context.getString(R.string.wear_ongoing_recording_title))
            .setStatus(Status.forPart(part))
            .build()
            .apply(context)
    }

    private fun openToday(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_OPEN_TODAY,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private companion object {
        const val REQUEST_OPEN_TODAY = 4_101
    }
}
```

- [ ] **Step 10: Supply the decorator from the watch's `Application`**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt` with:

```kotlin
package app.logdate.wear

import android.app.Application
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.media.audio.RecordingNotificationDecorator
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.wear.di.wearAudioModule
import app.logdate.wear.di.wearDataModule
import app.logdate.wear.di.wearExperienceModule
import app.logdate.wear.di.wearV2Module
import app.logdate.wear.launch.FeatureSwitch
import app.logdate.wear.recording.OngoingRecordingDecorator
import app.logdate.wear.recording.WearRecordingRecovery
import app.logdate.wear.reminders.LegacyPromptAlarms
import app.logdate.wear.sync.WatchSyncScheduler
import app.logdate.wear.sync.WearDeadLetterMigration
import app.logdate.wear.sync.v2.WatchStateMirror
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import kotlin.concurrent.thread

/**
 * Application class for LogDate Wear OS app.
 *
 * Initializes:
 * - Koin dependency injection
 * - Napier logging
 * - Audio recording modules
 *
 * It also supplies the recording notification's decorator, which shows a recording on the watch
 * face as an Ongoing Activity while the new watch app is on.
 */
class LogDateWearApplication :
    Application(),
    RecordingNotificationDecorator.Provider {
    /** WATCH_EXPERIENCE_V2 for code that can't suspend, such as the recording notification. */
    internal val newWatchApp: FeatureSwitch by lazy {
        FeatureSwitch(
            featureFlags = org.koin.java.KoinJavaComponent.getKoin().get(),
            flag = FeatureFlag.WATCH_EXPERIENCE_V2,
        )
    }

    override val recordingNotificationDecorator: RecordingNotificationDecorator by lazy {
        OngoingRecordingDecorator(enabled = { newWatchApp.isOn })
    }

    override fun onCreate() {
        super.onCreate()
        val launchedAtMs = System.currentTimeMillis()

        Napier.base(DebugAntilog())

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LogDateWearApplication)
            modules(
                wearDataModule,
                wearAudioModule,
                wearExperienceModule,
                wearV2Module,
            )
        }

        listOf(LogDateNotificationChannelKey.AUDIO_PLAYBACK, LogDateNotificationChannelKey.AUDIO_RECORDING).forEach { channel ->
            runCatching {
                LogDateNotificationRegistrar(this).registerChannel(channel)
            }.onFailure { error ->
                Napier.w("Failed to register Wear notification channel ${channel.id} on app startup", error)
            }
        }

        recoverInterruptedRecordings(launchedAtMs)
        startSync()
        newWatchApp.start(
            org.koin.java.KoinJavaComponent
                .getKoin()
                .get<CoroutineScope>(),
        )

        runCatching { LegacyPromptAlarms.cancel(this) }
            .onFailure { error -> Napier.w("Failed to remove the legacy journal prompts", error) }

        // Trigger database initialization on a background thread.
        // The database singleton uses runBlocking for SQLCipher passphrase retrieval
        // and Room database opening. By resolving it here off the main thread, the
        // heavy I/O (KeyStore, EncryptedSharedPreferences, SQLCipher, Room migrations)
        // runs in parallel with Activity creation instead of blocking the first UI frame.
        warmUpDatabase()
    }

    /**
     * Saves recordings a killed process left behind. Only files written before this launch are
     * touched, so a recording started right away is never mistaken for one of them.
     */
    private fun recoverInterruptedRecordings(launchedAtMs: Long) {
        thread(name = "recording-recovery", isDaemon = true) {
            try {
                val recovery =
                    org.koin.java.KoinJavaComponent
                        .getKoin()
                        .get<WearRecordingRecovery>()
                val recovered = runBlocking { recovery.recover(modifiedBefore = launchedAtMs) }
                if (recovered > 0) Napier.i("Recovered $recovered interrupted recording(s)")
            } catch (e: Exception) {
                Napier.e("Recovering interrupted recordings failed", e)
            }
        }
    }

    /**
     * Puts captures an older version dropped after repeated failures back in the outbox, wakes the
     * watch whenever something in it is due, and follows the phone's state.
     */
    private fun startSync() {
        val koin =
            org.koin.java.KoinJavaComponent
                .getKoin()
        val scope = koin.get<CoroutineScope>()
        koin.get<WatchStateMirror>().start(scope)
        scope.launch {
            requeueSetAsideCaptures(koin.get())
            koin.get<WatchSyncScheduler>().start(this)
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

    private fun warmUpDatabase() {
        thread(name = "db-warmup", isDaemon = true) {
            try {
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<LogDateDatabase>()
            } catch (e: Exception) {
                Napier.w("Background database warmup failed", e)
            }
        }
    }
}
```

Before replacing, diff the current file against the version above apart from the decorator, the switch and their `start` call (`git diff --no-index` on a copy); if `onCreate` gained anything since this plan was written, keep it.

- [ ] **Step 11: Run the unit and host tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:media:testAndroidHostTest :app:wear:testDebugUnitTest --tests 'app.logdate.wear.recording.*' --tests 'app.logdate.wear.launch.*'
```

Expected: `BUILD SUCCESSFUL`; `RecordingNotificationDecoratorTest` 2, `OngoingStopwatchTest` 2 and `FeatureSwitchTest` 3 tests passed, and every other `client/media` host test still passes.

- [ ] **Step 12: Check the phone still builds and run the GMD tests**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:media:compileAndroidMain :app:android-main:assembleDebug && uptime && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.OngoingRecordingE2ETest,app.logdate.wear.e2e.WearRecorderServiceE2ETest,app.logdate.wear.e2e.ExperienceGateTest
```

Expected: `BUILD SUCCESSFUL` twice; `OngoingRecordingE2ETest` 3 tests passed and `WearRecorderServiceE2ETest` unchanged.

- [ ] **Step 13: Lint**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:media:ktlintCheck :app:wear:ktlintCheck :app:wear:detekt
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 14: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task6-commit.txt <<'EOF'
chore(wear): show a recording on the watch face as an Ongoing Activity

While the redesigned watch app records, the watch face shows it: the
record glyph and a stopwatch of the time recorded, which stands still
while paused. Tapping it opens the app on Today, where Stop is. The
current watch app records as before.

The recording notification is built in the shared recording service,
which has no dependency injection, so the media module gains a small
hook: an app whose Application supplies a recording notification
decorator has it applied to every recording notification. Only the
watch supplies one; the phone's notification is unchanged. The
notification now carries the time recorded when a recording pauses or
resumes.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 15: Commit**

```bash
git restore --staged . && git add client/media/src/androidMain/kotlin/app/logdate/client/media/audio/RecordingNotificationDecorator.kt client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioNotificationHandler.kt client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AudioRecordingService.kt client/media/src/androidHostTest/kotlin/app/logdate/client/media/audio/RecordingNotificationDecoratorTest.kt app/wear/src/main/kotlin/app/logdate/wear/launch/FeatureSwitch.kt app/wear/src/main/kotlin/app/logdate/wear/recording/OngoingRecordingDecorator.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/main/res/values/strings_recorder.xml app/wear/src/test/kotlin/app/logdate/wear/recording/OngoingStopwatchTest.kt app/wear/src/test/kotlin/app/logdate/wear/launch/FeatureSwitchTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/OngoingRecordingE2ETest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task6-commit.txt
```

---
### Task 7: The recording time in ambient mode (`chore(wear)`)

When the screen dims during a recording, the spec shows a timer that updates once a minute. Compose for Wear 1.7.0's `rememberAmbientModeManager()` gives that, but `javap` shows it is built on the optional `wear-sdk` shared library (`com.google.wear.Sdk`, `com.google.wear.services.ambient.*`), which ships only with Wear OS 6 (API 36); on Wear OS 5, this app's minimum, calling it throws `NoClassDefFoundError`. The older `AmbientLifecycleObserver` works everywhere but can never turn always-on off again, which would leave the app on screen in ambient after every recording. So the watch is always-on only while recording and only where the Wear SDK ambient service exists: `WatchAmbientScope` at the app's root hosts `rememberAmbientModeManager()` then and provides `LocalAmbientModeManager`; everywhere else the screen dims to the watch face, where Task 6's Ongoing Activity shows the recording. In ambient, Today shows `AmbientRecordingFace`: "Recording" and the whole minutes, white on black, updated at each ambient tick and shifted a few pixels each minute when the screen needs burn-in protection.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/ambient/WatchAmbient.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/ambient/AmbientRecordingFace.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayPage.kt` (the start of `TodayPage` and its imports)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` (whole file)
- Modify: `app/wear/src/main/res/values/strings_recorder.xml`
- Create (screenshot): `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/AmbientRecordingScreenshots.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/ambient/WearSdkAmbientTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/ambient/BurnInShiftTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/AmbientSupportTest.kt`
- References: `app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/AmbientRecordingScreenshots/` (6 new)

**Interfaces:**
- Consumes: `rememberAmbientModeManager()`, `LocalAmbientModeManager`, `AmbientMode`, `AmbientModeManager.AmbientTickEffect` (Compose for Wear foundation 1.7.0); `RecorderState.Live` (Task 4); `TodayPage` and `WearAppV2` from Task 5.
- Produces: `internal object WearSdkAmbient { fun isAvailable(context: Context, sdkInt: Int = Build.VERSION.SDK_INT, loadClass: (String) -> Class<*>? = ...): Boolean }`, `@Composable fun WatchAmbientScope(enabled: Boolean, content: @Composable () -> Unit)`, `@Composable fun currentAmbientMode(): AmbientMode`, `data class BurnInShift(x: Int, y: Int)`, `internal fun burnInShift(tick: Int): BurnInShift`, `@Composable fun AmbientRecordingFace(elapsed: Duration, burnInProtection: Boolean, modifier: Modifier = Modifier)`, `object AmbientTags { FACE }` in `app.logdate.wear.v2.ambient`.

- [ ] **Step 1: Write the failing Wear SDK test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/ambient/WearSdkAmbientTest.kt`:

```kotlin
package app.logdate.wear.v2.ambient

import android.content.Context
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WearSdkAmbientTest {
    private val context = mockk<Context>(relaxed = true)
    private val wearSdkClasses: (String) -> Class<*>? = { name ->
        when (name) {
            "com.google.wear.services.ambient.AmbientManager" -> FakeAmbientManager::class.java
            "com.google.wear.Sdk" -> FakeWearSdk::class.java
            else -> null
        }
    }

    @Before
    fun resetTheFakeSdk() {
        FakeWearSdk.manager = null
        FakeWearSdk.failure = null
    }

    @Test
    fun `a watch before Wear OS 6 never uses the Wear SDK`() {
        FakeWearSdk.manager = FakeAmbientManager()

        assertFalse(WearSdkAmbient.isAvailable(context, sdkInt = 35, loadClass = wearSdkClasses))
    }

    @Test
    fun `a watch without the Wear SDK classes has no ambient`() {
        assertFalse(WearSdkAmbient.isAvailable(context, sdkInt = 36, loadClass = { null }))
    }

    @Test
    fun `a Wear SDK without the ambient service has no ambient`() {
        FakeWearSdk.manager = null

        assertFalse(WearSdkAmbient.isAvailable(context, sdkInt = 36, loadClass = wearSdkClasses))
    }

    @Test
    fun `Wear OS 6 with the ambient service has ambient`() {
        FakeWearSdk.manager = FakeAmbientManager()

        assertTrue(WearSdkAmbient.isAvailable(context, sdkInt = 36, loadClass = wearSdkClasses))
    }

    @Test
    fun `a lookup that throws means no ambient`() {
        FakeWearSdk.failure = IllegalStateException("service crashed")

        assertFalse(WearSdkAmbient.isAvailable(context, sdkInt = 36, loadClass = wearSdkClasses))
    }
}

class FakeAmbientManager

/** Stands in for `com.google.wear.Sdk`, which only Wear OS 6 has. */
class FakeWearSdk {
    companion object {
        var manager: Any? = null
        var failure: RuntimeException? = null

        @JvmStatic
        fun getWearManager(
            context: Context,
            type: Class<*>,
        ): Any? {
            failure?.let { throw it }
            return manager
        }
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/ambient/BurnInShiftTest.kt`:

```kotlin
package app.logdate.wear.v2.ambient

import org.junit.Test
import kotlin.test.assertEquals

class BurnInShiftTest {
    @Test
    fun `the face moves to a new place at each of four ticks and then starts over`() {
        val places = (0 until 8).map(::burnInShift)

        assertEquals(4, places.take(4).toSet().size)
        assertEquals(places.take(4), places.drop(4))
        assertEquals(BurnInShift.NONE, burnInShift(0))
    }
}
```

- [ ] **Step 2: Write the failing GMD ambient test and the screenshot scenes**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/AmbientSupportTest.kt`:

```kotlin
package app.logdate.wear.e2e

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.Text
import app.logdate.wear.v2.ambient.WatchAmbientScope
import app.logdate.wear.v2.ambient.WearSdkAmbient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse

/**
 * The Wear OS 5 emulator has no Wear SDK ambient service. An ambient scope switched on there must
 * not reach `rememberAmbientModeManager`, which would crash.
 */
@RunWith(AndroidJUnit4::class)
class AmbientSupportTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wear_os_5_has_no_wear_sdk_ambient_service() {
        assertFalse(WearSdkAmbient.isAvailable(ApplicationProvider.getApplicationContext()))
    }

    @Test
    fun an_ambient_scope_switched_on_without_the_service_shows_its_content() {
        composeRule.setContent {
            WatchAmbientScope(enabled = true) { Text("Recording", modifier = Modifier.testTag(CONTENT)) }
        }

        composeRule.onNodeWithTag(CONTENT).assertIsDisplayed()
    }

    private companion object {
        const val CONTENT = "ambient_content"
    }
}
```

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/AmbientRecordingScreenshots.kt`:

```kotlin
package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import app.logdate.wear.v2.ambient.AmbientRecordingFace
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The recording face the watch shows in ambient mode. */
class AmbientRecordingScreenshots {
    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun A01_OneMinute() {
        LogDateWearTheme(dynamicColor = false) { AmbientRecordingFace(elapsed = 1.minutes + 20.seconds, burnInProtection = false) }
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun A02_FortyTwoMinutes() {
        LogDateWearTheme(dynamicColor = false) { AmbientRecordingFace(elapsed = 42.minutes, burnInProtection = true) }
    }
}
```

- [ ] **Step 3: Run the unit tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.ambient.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'WearSdkAmbient'`, `'burnInShift'` and `'BurnInShift'`.

- [ ] **Step 4: Add the copy**

Replace the closing `</resources>` of `app/wear/src/main/res/values/strings_recorder.xml` with:

```xml
    <!-- The recording face in ambient mode: the whole minutes recorded so far -->
    <plurals name="wear_today_recorder_ambient_minutes">
        <item quantity="one">%1$d&#160;min</item>
        <item quantity="other">%1$d&#160;min</item>
    </plurals>
</resources>
```

- [ ] **Step 5: Add the ambient scope**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/ambient/WatchAmbient.kt`:

```kotlin
package app.logdate.wear.v2.ambient

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.AmbientModeManager
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import io.github.aakira.napier.Napier

/**
 * Whether this watch has the Wear SDK ambient service that [rememberAmbientModeManager] is built on.
 * Wear OS 6 has it. On Wear OS 5 its classes are missing and the call would crash the app.
 */
internal object WearSdkAmbient {
    private const val AMBIENT_MANAGER = "com.google.wear.services.ambient.AmbientManager"
    private const val WEAR_SDK = "com.google.wear.Sdk"

    fun isAvailable(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT,
        loadClass: (String) -> Class<*>? = ::loadOrNull,
    ): Boolean {
        if (sdkInt < Build.VERSION_CODES.BAKLAVA) return false
        val ambientManager = loadClass(AMBIENT_MANAGER) ?: return false
        val sdk = loadClass(WEAR_SDK) ?: return false
        return try {
            val getWearManager = sdk.getMethod("getWearManager", Context::class.java, Class::class.java)
            getWearManager.invoke(null, context.applicationContext, ambientManager) != null
        } catch (e: ReflectiveOperationException) {
            Napier.w("The Wear SDK on this watch has no ambient service", e)
            false
        } catch (e: RuntimeException) {
            Napier.w("Looking up the Wear SDK ambient service failed", e)
            false
        }
    }

    private fun loadOrNull(name: String): Class<*>? =
        try {
            Class.forName(name)
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: LinkageError) {
            null
        }
}

/**
 * Keeps the app on screen in ambient mode while [enabled], by providing [LocalAmbientModeManager] to
 * [content]. The app turns it on only while recording. On a watch without the Wear SDK ambient
 * service nothing is provided, and the screen dims to the watch face, where the recording's
 * Ongoing Activity shows.
 */
@Composable
fun WatchAmbientScope(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supported = remember(context) { WearSdkAmbient.isAvailable(context) }
    var manager by remember { mutableStateOf<AmbientModeManager?>(null) }
    if (enabled && supported) AmbientManagerHost(onManager = { manager = it })
    CompositionLocalProvider(LocalAmbientModeManager provides manager, content = content)
}

/** Holds the ambient manager while composed; leaving composition stops always-on again. */
@Composable
private fun AmbientManagerHost(onManager: (AmbientModeManager?) -> Unit) {
    val manager = rememberAmbientModeManager()
    DisposableEffect(manager) {
        onManager(manager)
        onDispose { onManager(null) }
    }
}

/** The ambient mode the app is in; interactive when no ambient manager is provided. */
@Composable
fun currentAmbientMode(): AmbientMode = LocalAmbientModeManager.current?.currentAmbientMode ?: AmbientMode.Interactive
```

- [ ] **Step 6: Add the ambient recording face**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/ambient/AmbientRecordingFace.kt`:

```kotlin
package app.logdate.wear.v2.ambient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.AmbientTickEffect
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.logdate.wear.R
import kotlin.time.Duration

object AmbientTags {
    const val FACE = "ambient_recording_face"
}

/** How far ambient content moves, in dp, so the same pixels don't stay lit minute after minute. */
data class BurnInShift(
    val x: Int,
    val y: Int,
) {
    companion object {
        val NONE = BurnInShift(0, 0)
    }
}

/** One of four places a few pixels apart, a new one at each ambient tick. */
internal fun burnInShift(tick: Int): BurnInShift = BURN_IN_SHIFTS[Math.floorMod(tick, BURN_IN_SHIFTS.size)]

private val BURN_IN_SHIFTS = listOf(BurnInShift(0, 0), BurnInShift(4, 0), BurnInShift(4, 4), BurnInShift(0, 4))

/**
 * "Recording" and the whole minutes recorded, white on black. The minutes change at each ambient
 * tick, once a minute; with [burnInProtection] the text also moves to a new place at each tick.
 */
@Composable
fun AmbientRecordingFace(
    elapsed: Duration,
    burnInProtection: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentElapsed by rememberUpdatedState(elapsed)
    var minutes by remember { mutableLongStateOf(elapsed.inWholeMinutes) }
    var tick by remember { mutableIntStateOf(0) }
    LocalAmbientModeManager.current?.AmbientTickEffect {
        minutes = currentElapsed.inWholeMinutes
        tick += 1
    }
    val shift = if (burnInProtection) burnInShift(tick) else BurnInShift.NONE
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black).testTag(AmbientTags.FACE),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.offset(x = shift.x.dp, y = shift.y.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.wear_today_recorder_recording),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = pluralStringResource(R.plurals.wear_today_recorder_ambient_minutes, minutes.toInt(), minutes.toInt()),
                color = Color.White,
                style = MaterialTheme.typography.numeralSmall,
            )
        }
    }
}
```

- [ ] **Step 7: Show the face on Today in ambient mode**

In `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayPage.kt`, add these imports in ASCII order with the existing ones:

```kotlin
import androidx.wear.compose.foundation.AmbientMode
import app.logdate.wear.v2.ambient.AmbientRecordingFace
import app.logdate.wear.v2.ambient.currentAmbientMode
```

and replace

```kotlin
    val recorderState = controls.recorder?.state
    val timeText: @Composable () -> Unit = { TodayTimeText(state = state, timeSource = timeSource) }
```

with

```kotlin
    val recorderState = controls.recorder?.state
    val ambient = currentAmbientMode()
    if (recorderState is RecorderState.Live && ambient is AmbientMode.Ambient) {
        AmbientRecordingFace(elapsed = recorderState.elapsed, burnInProtection = ambient.isBurnInProtectionRequired, modifier = modifier)
        return
    }
    val timeText: @Composable () -> Unit = { TodayTimeText(state = state, timeSource = timeSource) }
```

- [ ] **Step 8: Turn ambient on at the root while recording**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` with:

```kotlin
package app.logdate.wear.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import app.logdate.ui.platform.LocalLogDateHaptics
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.v2.ambient.WatchAmbientScope
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowViewModel
import app.logdate.wear.v2.home.HomeScreen
import app.logdate.wear.v2.navigation.WearRoute
import app.logdate.wear.v2.navigation.toV2BackStack
import app.logdate.wear.v2.permissions.rememberPermissionRequester
import app.logdate.wear.v2.recorder.RecorderState
import app.logdate.wear.v2.recorder.RecorderViewModel
import app.logdate.wear.v2.recorder.TodayRecorder
import app.logdate.wear.v2.recorder.locksPaging
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.TodayControls
import app.logdate.wear.v2.today.TodayPage
import app.logdate.wear.v2.today.TodayViewModel
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

object WearAppV2Tags {
    const val SHELL = "wear_app_v2_shell"
}

/**
 * The redesigned watch app, shown while WATCH_EXPERIENCE_V2 is on.
 *
 * [initialRequest] picks the first back stack; a recreated activity restores the one it saved
 * instead. Each item of [newRequests] replaces the back stack, as a cold launch from a tile does.
 * Swiping right dismisses the top screen. Each screen's view models live as long as its entry; the
 * recorder is resolved here, outside every entry, so it belongs to the activity, and the
 * permissions it asks for are requested here. While it records, the app stays on screen in ambient
 * mode where the watch supports it.
 */
@Composable
fun WearAppV2(
    initialRequest: WearLaunchRequest?,
    newRequests: Flow<WearLaunchRequest>,
) {
    val recorder = koinViewModel<RecorderViewModel>()
    val recorderState by recorder.state.collectAsState()
    val backStack = rememberNavBackStack(*initialRequest.toV2BackStack().toTypedArray())
    LaunchedEffect(newRequests) {
        newRequests.collect { request ->
            backStack.clear()
            backStack.addAll(request.toV2BackStack())
        }
    }
    RecorderPermissionPrompts(recorder)
    WatchAmbientScope(enabled = recorderState is RecorderState.Live) {
        LogDateWearTheme {
            CompositionLocalProvider(LocalLogDateHaptics provides koinInject<LogDateHaptics>()) {
                AppScaffold(modifier = Modifier.testTag(WearAppV2Tags.SHELL)) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryDecorators =
                            listOf(
                                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                                rememberViewModelStoreNavEntryDecorator<NavKey>(),
                            ),
                        sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy<NavKey>()),
                        entryProvider =
                            entryProvider {
                                entry<WearRoute.Home> { HomeEntry(recorder) }
                            },
                    )
                }
            }
        }
    }
}

/** Asks for the permissions the recorder needs at the moment it needs them, and gives it the answers. */
@Composable
private fun RecorderPermissionPrompts(recorder: RecorderViewModel) {
    val requester = rememberPermissionRequester(onResult = recorder::onPermissionResult)
    LaunchedEffect(recorder, requester) {
        recorder.permissionRequests.collect { permission -> requester.request(permission) }
    }
}

@Composable
private fun HomeEntry(recorder: RecorderViewModel) {
    val recorderState by recorder.state.collectAsState()
    val controls =
        remember(recorderState, recorder) {
            TodayControls(recorder = TodayRecorder(recorderState, gesture = recorder, actions = recorder))
        }
    HomeScreen(
        pagingLocked = recorderState.locksPaging,
        today = { listState -> TodayEntry(listState, controls) },
        flow = { listState -> FlowEntry(listState) },
    )
}

@Composable
private fun TodayEntry(
    listState: TransformingLazyColumnState,
    controls: TodayControls,
) {
    val viewModel = koinViewModel<TodayViewModel>()
    val state by viewModel.uiState.collectAsState()
    val handoff by viewModel.handoff.collectAsState()
    TodayPage(
        state = state,
        handoff = handoff,
        onGetPhoneApp = viewModel::getPhoneApp,
        onHandoffDismissed = viewModel::dismissHandoff,
        controls = controls,
        listState = listState,
    )
}

@Composable
private fun FlowEntry(listState: TransformingLazyColumnState) {
    val viewModel = koinViewModel<FlowViewModel>()
    val state by viewModel.uiState.collectAsState()
    FlowPage(state = state, listState = listState)
}
```

- [ ] **Step 9: Run the unit tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.*'
```

Expected: `BUILD SUCCESSFUL`; `WearSdkAmbientTest` 5 and `BurnInShiftTest` 1 tests passed.

- [ ] **Step 10: Run the GMD tests**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.AmbientSupportTest,app.logdate.wear.e2e.TodayRecorderTest,app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.ExperienceGateTest
```

Expected: `BUILD SUCCESSFUL`; `AmbientSupportTest` passes 2 tests on the API 34 image, which has no Wear SDK ambient service.

- [ ] **Step 11: Record and check the ambient references**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest; ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/AmbientRecordingScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: the first command FAILs listing only the 6 `AmbientRecordingScreenshots` previews as missing; then `BUILD SUCCESSFUL`, `6`, and only the new directory in the status. Open `A01_OneMinute_Small Round_*.png` ("Recording" over "1 min", white on black, centered) and `A02_FortyTwoMinutes_Large Font_*.png` ("42 min", not clipped).

- [ ] **Step 12: Validate and lint**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest :app:wear:ktlintCheck :app:wear:detekt
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 13: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task7-commit.txt <<'EOF'
chore(wear): show the recording time in ambient mode

When the screen dims during a recording in the redesigned watch app,
it stays on a black screen with "Recording" and the minutes recorded,
updated once a minute and moved a few pixels each minute on screens
that need burn-in protection. The app is always-on only while it
records.

Ambient mode comes from Compose for Wear's ambient manager, which
needs the Wear SDK ambient service that only Wear OS 6 has; on Wear
OS 5 it would crash. Those watches dim to the watch face instead,
where the recording's Ongoing Activity shows its time.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 14: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/ambient/WatchAmbient.kt app/wear/src/main/kotlin/app/logdate/wear/v2/ambient/AmbientRecordingFace.kt app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayPage.kt app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/main/res/values/strings_recorder.xml app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/AmbientRecordingScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/AmbientRecordingScreenshots app/wear/src/test/kotlin/app/logdate/wear/v2/ambient/WearSdkAmbientTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/ambient/BurnInShiftTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/AmbientSupportTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task7-commit.txt
```

---
### Task 8: Saving marks and queuing them for backup (`chore(wear)`)

This is B4c, placed before B4a on purpose: if Mark landed first, every mark made with a build from between the two commits would sit in `marked_moments` and never be queued, a silent backup gap. `MarkedMomentStore` is the one way the watch writes a mark. It saves the row, enqueues a `MOMENT` change in the existing outbox (`SyncMetadataService`), and records a `peer_delivery` row that says the mark waits for a phone that takes marks (`moments.v1`). Nothing sends marks yet, and no server behavior changes: `WearDataLayerSyncManager` and `WatchSyncScheduler` only send `NOTE`, `JOURNAL` and `ASSOCIATION`, so a queued mark never wakes the v1 sync; Track A's router reads the ledger when it delivers marks. The writes run in the same order as a note's (`OfflineFirstJournalNotesRepository.create` writes the row, then the outbox, without a transaction on Android): the mark first, so a crash between writes can only leave a mark not yet queued, never a queue entry without a mark. A queued mark counts in the watch's pending total, which is honest: it has not reached the phone.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkedMomentStore.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`
- Create (test helper): `app/wear/src/test/kotlin/app/logdate/wear/v2/mark/MarkFakes.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/mark/MarkedMomentStoreTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MarkedMomentStoreE2ETest.kt`

**Interfaces:**
- Consumes: `MarkedMomentDao.upsert/getById/observeByState`, `MarkedMomentEntity`, `MarkedMomentState`, `PeerDeliveryDao.upsert/get`, `PeerDeliveryEntity`, `SyncMetadataService.enqueuePending(entityId, entityType, operation)`, `EntityType.MOMENT`, `PendingOperation.CREATE/UPDATE`.
- Produces: `object MarkDelivery { PEER_SCOPE = "phone"; DIRECTION = "TO_PHONE"; STATE_WAITING = "WAITING"; REASON_PEER_LACKS_MOMENTS = "PEER_LACKS_MOMENTS" }` and `class MarkedMomentStore(markedMomentDao, peerDeliveryDao, syncMetadataService, clock = Clock.System) { suspend fun create(mark: MarkedMomentEntity); suspend fun update(mark: MarkedMomentEntity) }` in `app.logdate.wear.v2.mark`; test fakes `FakeMarkedMomentDao`, `FakePeerDeliveryDao`.

- [ ] **Step 1: Add the test fakes**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/mark/MarkFakes.kt`:

```kotlin
package app.logdate.wear.v2.mark

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/** [MarkedMomentDao] in memory. Set [failure] to make the next writes throw. */
class FakeMarkedMomentDao : MarkedMomentDao {
    private val stored = MutableStateFlow<Map<Uuid, MarkedMomentEntity>>(emptyMap())
    var failure: Exception? = null

    val rows: Map<Uuid, MarkedMomentEntity> get() = stored.value

    override suspend fun upsert(moment: MarkedMomentEntity) {
        failure?.let { throw it }
        stored.value = stored.value + (moment.id to moment)
    }

    override suspend fun getById(id: Uuid): MarkedMomentEntity? = stored.value[id]

    override fun observeByState(state: String): Flow<List<MarkedMomentEntity>> =
        stored.map { rows -> rows.values.filter { it.state == state && it.deletedAt == null }.sortedByDescending { it.capturedAt } }
}

/** [PeerDeliveryDao] in memory. */
class FakePeerDeliveryDao : PeerDeliveryDao {
    private val rows = mutableMapOf<List<String>, PeerDeliveryEntity>()

    override suspend fun upsert(entry: PeerDeliveryEntity) {
        rows[listOf(entry.peerScope, entry.direction, entry.entityType, entry.entityId)] = entry
    }

    override suspend fun get(
        peerScope: String,
        direction: String,
        entityType: String,
        entityId: String,
    ): PeerDeliveryEntity? = rows[listOf(peerScope, direction, entityType, entityId)]

    override suspend fun due(
        peerScope: String,
        direction: String,
        now: Long,
        limit: Int,
    ): List<PeerDeliveryEntity> =
        rows.values
            .filter { it.peerScope == peerScope && it.direction == direction && (it.nextAttemptAt ?: Long.MAX_VALUE) <= now }
            .sortedBy { it.nextAttemptAt }
            .take(limit)

    override suspend fun delete(
        peerScope: String,
        direction: String,
        entityType: String,
        entityId: String,
    ) {
        rows.remove(listOf(peerScope, direction, entityType, entityId))
    }
}
```

- [ ] **Step 2: Write the failing store test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/mark/MarkedMomentStoreTest.kt`:

```kotlin
package app.logdate.wear.v2.mark

import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkedMomentStoreTest {
    private val marks = FakeMarkedMomentDao()
    private val deliveries = FakePeerDeliveryDao()
    private val syncMetadataService = mockk<SyncMetadataService>(relaxed = true)
    private val now = Instant.fromEpochMilliseconds(1_760_000_000_000L)
    private val store = MarkedMomentStore(marks, deliveries, syncMetadataService, clock = FixedClock(now))
    private val mark =
        MarkedMomentEntity(
            id = Uuid.random(),
            capturedAt = now,
            timeZoneId = "Europe/London",
            state = MarkedMomentState.OPEN,
            originDeviceId = "watch-1",
            lastUpdated = now,
        )

    @Test
    fun `a new mark is saved and queued for backup`() =
        runTest {
            store.create(mark)

            assertEquals(mark, marks.rows[mark.id])
            coVerify { syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE) }
        }

    @Test
    fun `the ledger records that the mark waits for a phone that takes marks`() =
        runTest {
            store.create(mark)

            val row = assertNotNull(delivery())
            assertEquals(MarkDelivery.STATE_WAITING, row.state)
            assertEquals(MarkDelivery.REASON_PEER_LACKS_MOMENTS, row.holdReason)
            assertEquals(now.toEpochMilliseconds(), row.contentVersion)
            assertEquals(0, row.attempts)
            assertNull(row.nextAttemptAt)
        }

    @Test
    fun `a change is saved and queued with its new version`() =
        runTest {
            store.create(mark)
            val later = mark.copy(heartRateBpm = 72, lastUpdated = now + 5.seconds)

            store.update(later)

            assertEquals(later, marks.rows[mark.id])
            coVerify { syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE) }
            val row = assertNotNull(delivery())
            assertEquals(later.lastUpdated.toEpochMilliseconds(), row.contentVersion)
        }

    @Test
    fun `a mark that cannot be saved is not queued`() =
        runTest {
            marks.failure = IllegalStateException("database closed")

            assertFailsWith<IllegalStateException> { store.create(mark) }

            coVerify(exactly = 0) { syncMetadataService.enqueuePending(any(), any(), any()) }
            assertNull(delivery())
        }

    private suspend fun delivery() =
        deliveries.get(MarkDelivery.PEER_SCOPE, MarkDelivery.DIRECTION, EntityType.MOMENT.name, mark.id.toString())

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }
}
```

- [ ] **Step 3: Write the failing GMD store test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MarkedMomentStoreE2ETest.kt`:

```kotlin
package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.v2.mark.MarkDelivery
import app.logdate.wear.v2.mark.MarkedMomentStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Saves a mark through the real Koin graph and the watch's encrypted database on a Wear OS emulator. */
@RunWith(AndroidJUnit4::class)
class MarkedMomentStoreE2ETest {
    private val koin get() = KoinJavaComponent.getKoin()

    @Test
    fun a_saved_mark_is_in_the_database_the_outbox_and_the_ledger() =
        runBlocking {
            // The database keeps milliseconds, so the mark is made at a whole millisecond to compare equal.
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val mark =
                MarkedMomentEntity(
                    id = Uuid.random(),
                    capturedAt = now,
                    timeZoneId = "UTC",
                    state = MarkedMomentState.OPEN,
                    originDeviceId = "e2e",
                    lastUpdated = now,
                )

            koin.get<MarkedMomentStore>().create(mark)

            assertEquals(mark, koin.get<MarkedMomentDao>().getById(mark.id))
            assertTrue(koin.get<SyncMetadataService>().hasPending(EntityType.MOMENT, mark.id.toString()))
            val delivery =
                koin.get<PeerDeliveryDao>().get(MarkDelivery.PEER_SCOPE, MarkDelivery.DIRECTION, EntityType.MOMENT.name, mark.id.toString())
            assertEquals(MarkDelivery.STATE_WAITING, delivery?.state)
        }
}
```

- [ ] **Step 4: Run the unit test to verify it fails**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.mark.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'MarkedMomentStore'` and `'MarkDelivery'`.

- [ ] **Step 5: Add the store**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkedMomentStore.kt`:

```kotlin
package app.logdate.wear.v2.mark

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import kotlin.time.Clock

/** The delivery ledger values a mark waiting for the phone is recorded with; the watch's sync router reads them. */
object MarkDelivery {
    const val PEER_SCOPE = "phone"
    const val DIRECTION = "TO_PHONE"
    const val STATE_WAITING = "WAITING"

    /** Marks go only to a phone that lists `moments.v1` in its state document. */
    const val REASON_PEER_LACKS_MOMENTS = "PEER_LACKS_MOMENTS"
}

/**
 * Saves the moments marked on this watch and queues each one for backup: the outbox gets a MOMENT
 * change, and the delivery ledger records that the mark waits for a phone that takes marks. The
 * mark is written first, so a crash between the writes can only leave a mark that is not queued
 * yet, never a queued change without its mark.
 */
class MarkedMomentStore(
    private val markedMomentDao: MarkedMomentDao,
    private val peerDeliveryDao: PeerDeliveryDao,
    private val syncMetadataService: SyncMetadataService,
    private val clock: Clock = Clock.System,
) {
    /** Saves a new mark and queues its creation. */
    suspend fun create(mark: MarkedMomentEntity) {
        markedMomentDao.upsert(mark)
        syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
        recordWaiting(mark)
    }

    /** Saves a change to a mark, such as its place arriving, and queues it. */
    suspend fun update(mark: MarkedMomentEntity) {
        markedMomentDao.upsert(mark)
        syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
        recordWaiting(mark)
    }

    private suspend fun recordWaiting(mark: MarkedMomentEntity) {
        peerDeliveryDao.upsert(
            PeerDeliveryEntity(
                peerScope = MarkDelivery.PEER_SCOPE,
                direction = MarkDelivery.DIRECTION,
                entityType = EntityType.MOMENT.name,
                entityId = mark.id.toString(),
                contentVersion = mark.lastUpdated.toEpochMilliseconds(),
                state = MarkDelivery.STATE_WAITING,
                stateChangedAt = clock.now().toEpochMilliseconds(),
                holdReason = MarkDelivery.REASON_PEER_LACKS_MOMENTS,
            ),
        )
    }
}
```

- [ ] **Step 6: Bind the store in Koin**

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add the import `import app.logdate.wear.v2.mark.MarkedMomentStore` in ASCII order, and add after the `QuickNoteWriter` definition:

```kotlin
        single { MarkedMomentStore(markedMomentDao = get(), peerDeliveryDao = get(), syncMetadataService = get()) }
```

- [ ] **Step 7: Run the unit test to verify it passes**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.mark.*' && ./gradlew :app:wear:ktlintCheck :app:wear:detekt
```

Expected: `BUILD SUCCESSFUL` twice, 4 tests passed.

- [ ] **Step 8: Run the GMD store test**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.MarkedMomentStoreE2ETest,app.logdate.wear.e2e.WatchSyncWorkerTest
```

Expected: `BUILD SUCCESSFUL`. `WatchSyncWorkerTest` still passes, because a queued mark is not something the v1 sync sends.

- [ ] **Step 9: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task8-commit.txt <<'EOF'
chore(wear): save marked moments and queue them for backup

A moment marked on the watch is saved with a change in the backup
queue, and the watch's delivery ledger records that it waits for a
phone that takes marks. The phone, the cloud and the current sync
send nothing new: marks travel only once the watch's sync router
delivers them to a phone that advertises them.

The mark is written before it is queued, so an interrupted save can
only leave a mark waiting to be queued, never a queued change without
its mark. Nothing marks a moment yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 10: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkedMomentStore.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/v2/mark/MarkFakes.kt app/wear/src/test/kotlin/app/logdate/wear/v2/mark/MarkedMomentStoreTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MarkedMomentStoreE2ETest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task8-commit.txt
```

---
### Task 9: Mark the moment (`chore(wear)`)

Mark appears on Today in this task, because this is the task that makes it work. A tap saves and queues the mark at once, with its time, time zone and this watch, plays `HapticEvent.MomentMarked` (`LogDateHaptics.momentMarked()`, which plays even with haptics set to Essential) and shows "Moment marked". Then, on the application scope so it finishes if the screen goes away, `MarkMomentUseCase.enrich` reads the place (the existing `WearLocationCaptureCoordinator`, at most 3 s) and one heart rate reading (`MeasuredHeartRateSampler` over Health Services' `MeasureClient`, at most 8 s) and saves the mark again with whichever it got. The first mark asks for the heart rate permission, once; the mark waiting for the answer is filled in either way. Without Health Services, the permission, a sensor reading or time, the mark keeps no heart rate. Outside the app, `MarkMomentReceiver` (not exported; fired by this app's own pending intents from the complication, tile or reminder action that later plans add) saves a mark with only its time, since a background app can read neither the place nor the heart rate, and confirms with the same haptic; it marks nothing while WATCH_EXPERIENCE_V2 is off. The manifest gains the heart rate permission: `health.READ_HEART_RATE` on Wear OS 6, `BODY_SENSORS` before it.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/health/HeartRateSampler.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkMomentUseCase.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkViewModel.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkMomentReceiver.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkConfirmation.kt`
- Create: `app/wear/src/main/res/values/strings_capture.xml`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/WatchPermission.kt` (whole file)
- Modify: `app/wear/src/main/AndroidManifest.xml` (permissions after `POST_NOTIFICATIONS`; receiver before `MainActivity`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` (whole file)
- Create (screenshot): `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MarkConfirmationScreenshots.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/health/MeasuredHeartRateSamplerTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/mark/MarkMomentUseCaseTest.kt`, `MarkViewModelTest.kt`, `BackgroundMarkerTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/permissions/WatchPermissionTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MarkMomentReceiverTest.kt`
- References: `app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/MarkConfirmationScreenshots/` (6 new)

**Interfaces:**
- Consumes: `MarkedMomentStore` (Task 8), `PermissionPrompts`, `WatchPermission`, `PermissionResult`, `rememberPermissionRequester` (Task 3), `LogDateHaptics.momentMarked()`/`actionRejected()`, `WearLocationCaptureCoordinator.captureForJournalEntry()`, `DeviceIdProvider.getDeviceId()`, `HealthServices.getClient(context).measureClient`, `FeatureFlagStore.isEnabled`, `TodayControls.onMark`.
- Produces:
  - `fun interface HeartRateSampler { suspend fun sample(): Int?; companion object { val None } }`, `sealed interface HeartRateReading { Bpm(value); Unavailable }`, `fun interface HeartRateReadings { fun readings(): Flow<HeartRateReading> }`, `class MeasuredHeartRateSampler(readings, hasPermission: () -> Boolean, timeout: Duration = 8.seconds)`, `class MeasureClientHeartRateReadings(measureClient: MeasureClient)` in `app.logdate.wear.health`.
  - `class MarkMomentUseCase(store, deviceId: () -> String, place: suspend () -> NoteLocation?, heartRate: HeartRateSampler, clock = Clock.System, timeZoneId: () -> String = ...) { suspend fun mark(): MarkedMomentEntity; suspend fun enrich(mark: MarkedMomentEntity): MarkedMomentEntity }`.
  - `enum class MarkFeedback { NONE, MARKED, FAILED }`, `class MarkViewModel(markMoment, haptics, permissions, applicationScope) { val feedback: StateFlow<MarkFeedback>; val permissionRequests: Flow<WatchPermission>; fun mark(); fun onPermissionResult(permission: WatchPermission); fun dismissFeedback() }`.
  - `class BackgroundMarker(featureFlags, markMoment, haptics) { suspend fun mark(): Boolean }`, `class MarkMomentReceiver : BroadcastReceiver` with `ACTION_MARK_MOMENT` and `fun pendingIntent(context): PendingIntent`.
  - `@Composable fun MarkConfirmation(feedback: MarkFeedback, onDismiss: () -> Unit)`, `@Composable fun MarkConfirmationContent(feedback: MarkFeedback)`.
  - `WatchPermission.HEART_RATE`, `internal fun heartRatePermission(sdkInt: Int): String`.

- [ ] **Step 1: Write the failing heart rate test**

Create `app/wear/src/test/kotlin/app/logdate/wear/health/MeasuredHeartRateSamplerTest.kt`:

```kotlin
package app.logdate.wear.health

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MeasuredHeartRateSamplerTest {
    private var permitted = true
    private var collected = false

    @Test
    fun `the first plausible reading is the heart rate`() =
        runTest {
            assertEquals(72, sampler(HeartRateReading.Bpm(0), HeartRateReading.Bpm(72), HeartRateReading.Bpm(75)).sample())
        }

    @Test
    fun `a sensor that cannot read gives no heart rate`() =
        runTest {
            assertNull(sampler(HeartRateReading.Unavailable, HeartRateReading.Bpm(72)).sample())
        }

    @Test
    fun `without the permission the sensor is never read`() =
        runTest {
            permitted = false

            assertNull(sampler(HeartRateReading.Bpm(72)).sample())
            assertFalse(collected)
        }

    @Test
    fun `nothing within eight seconds gives no heart rate`() =
        runTest {
            val result = async { sampler(waitAfterwards = true).sample() }

            advanceTimeBy(8_001)

            assertTrue(result.isCompleted)
            assertNull(result.await())
        }

    @Test
    fun `a sensor that fails gives no heart rate`() =
        runTest {
            val failing =
                MeasuredHeartRateSampler(
                    readings = { flow { throw IllegalStateException("registration failed") } },
                    hasPermission = { true },
                )

            assertNull(failing.sample())
        }

    private fun sampler(
        vararg readings: HeartRateReading,
        waitAfterwards: Boolean = false,
    ) = MeasuredHeartRateSampler(
        readings = {
            flow {
                collected = true
                readings.forEach { emit(it) }
                if (waitAfterwards) awaitCancellation()
            }
        },
        hasPermission = { permitted },
    )
}
```

- [ ] **Step 2: Write the failing mark tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/mark/MarkMomentUseCaseTest.kt`:

```kotlin
package app.logdate.wear.v2.mark

import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.health.HeartRateSampler
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MarkMomentUseCaseTest {
    private val marks = FakeMarkedMomentDao()
    private val syncMetadataService = mockk<SyncMetadataService>(relaxed = true)
    private val clock = SteppingClock(Instant.fromEpochMilliseconds(1_760_000_000_000L))
    private var place: NoteLocation? = null
    private var bpm: Int? = null

    @Test
    fun `a mark is saved at once with its time and zone and this watch`() =
        runTest {
            val mark = useCase().mark()

            assertEquals(mark, marks.rows[mark.id])
            assertEquals(clock.start, mark.capturedAt)
            assertEquals("Europe/Paris", mark.timeZoneId)
            assertEquals("watch-1", mark.originDeviceId)
            assertEquals(MarkedMomentState.OPEN, mark.state)
            coVerify { syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE) }
        }

    @Test
    fun `the place and heart rate are added when they can be read`() =
        runTest {
            place = NoteLocation(coordinates = NoteCoordinates(latitude = 37.77, longitude = -122.41, accuracy = 12f))
            bpm = 72
            val useCase = useCase()
            val mark = useCase.mark()
            clock.advance(5.seconds)

            val enriched = useCase.enrich(mark)

            assertEquals(37.77, enriched.latitude)
            assertEquals(-122.41, enriched.longitude)
            assertEquals(12f, enriched.locationAccuracy)
            assertEquals(72, enriched.heartRateBpm)
            assertEquals(clock.start + 5.seconds, enriched.lastUpdated)
            assertEquals(enriched, marks.rows[mark.id])
            coVerify { syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE) }
        }

    @Test
    fun `a mark with nothing to add is left as it is`() =
        runTest {
            val useCase = useCase()
            val mark = useCase.mark()

            assertEquals(mark, useCase.enrich(mark))

            coVerify(exactly = 0) { syncMetadataService.enqueuePending(any(), any(), PendingOperation.UPDATE) }
        }

    @Test
    fun `a slow place reading is given up after three seconds`() =
        runTest {
            bpm = 70
            val useCase =
                useCase(
                    readPlace = {
                        delay(60_000)
                        place
                    },
                )
            val mark = useCase.mark()

            val enriched = async { useCase.enrich(mark) }
            advanceTimeBy(MarkMomentUseCase.PLACE_TIMEOUT_MS + 1)

            assertTrue(enriched.isCompleted)
            assertNull(enriched.await().latitude)
            assertEquals(70, enriched.await().heartRateBpm)
        }

    private fun useCase(readPlace: suspend () -> NoteLocation? = { place }) =
        MarkMomentUseCase(
            store = MarkedMomentStore(marks, FakePeerDeliveryDao(), syncMetadataService, clock),
            deviceId = { "watch-1" },
            place = readPlace,
            heartRate = HeartRateSampler { bpm },
            clock = clock,
            timeZoneId = { "Europe/Paris" },
        )

    private class SteppingClock(
        val start: Instant,
    ) : Clock {
        private var current = start

        fun advance(by: Duration) {
            current += by
        }

        override fun now(): Instant = current
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/mark/MarkViewModelTest.kt`:

```kotlin
package app.logdate.wear.v2.mark

import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.health.HeartRateSampler
import app.logdate.wear.v2.haptics.RecordedHaptics
import app.logdate.wear.v2.permissions.PermissionPromptStore
import app.logdate.wear.v2.permissions.PermissionPrompts
import app.logdate.wear.v2.permissions.WatchPermission
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MarkViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)
    private val marks = FakeMarkedMomentDao()
    private val haptics = RecordedHaptics()
    private val granted = mutableSetOf(WatchPermission.HEART_RATE)
    private val asked = mutableSetOf<WatchPermission>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `marking saves at once and confirms it`() =
        runTest {
            val viewModel = viewModel()

            viewModel.mark()
            runCurrent()

            assertEquals(MarkFeedback.MARKED, viewModel.feedback.value)
            assertEquals("momentMarked", haptics.played.first())
            assertEquals(1, marks.rows.size)
        }

    @Test
    fun `the heart rate follows on the application scope`() =
        runTest {
            val viewModel = viewModel()

            viewModel.mark()
            runCurrent()

            assertEquals(68, marks.rows.values.single().heartRateBpm)
        }

    @Test
    fun `the first mark asks for the heart rate once and is filled in after the answer`() =
        runTest {
            granted -= WatchPermission.HEART_RATE
            val viewModel = viewModel()
            val requests = collectRequests(viewModel)

            viewModel.mark()
            runCurrent()
            assertEquals(listOf(WatchPermission.HEART_RATE), requests)
            assertNull(marks.rows.values.single().heartRateBpm)

            granted += WatchPermission.HEART_RATE
            viewModel.onPermissionResult(WatchPermission.HEART_RATE)
            runCurrent()
            assertEquals(68, marks.rows.values.single().heartRateBpm)

            granted -= WatchPermission.HEART_RATE
            viewModel.mark()
            runCurrent()
            assertEquals(listOf(WatchPermission.HEART_RATE), requests)
        }

    @Test
    fun `a mark that cannot be saved says so`() =
        runTest {
            marks.failure = IllegalStateException("database closed")
            val viewModel = viewModel()

            viewModel.mark()
            runCurrent()

            assertEquals(MarkFeedback.FAILED, viewModel.feedback.value)
            assertEquals(listOf("actionRejected"), haptics.played)
        }

    @Test
    fun `dismissing clears the confirmation`() =
        runTest {
            val viewModel = viewModel()
            viewModel.mark()
            runCurrent()

            viewModel.dismissFeedback()

            assertEquals(MarkFeedback.NONE, viewModel.feedback.value)
        }

    private fun viewModel(): MarkViewModel {
        val markMoment =
            MarkMomentUseCase(
                store = MarkedMomentStore(marks, FakePeerDeliveryDao(), mockk<SyncMetadataService>(relaxed = true)),
                deviceId = { "watch-1" },
                place = { null },
                heartRate = HeartRateSampler { if (WatchPermission.HEART_RATE in granted) 68 else null },
                timeZoneId = { "UTC" },
            )
        return MarkViewModel(
            markMoment = markMoment,
            haptics = haptics,
            permissions = PermissionPrompts(checker = { it in granted }, store = InMemoryPromptStore(asked)),
            applicationScope = applicationScope,
        )
    }

    private fun TestScope.collectRequests(viewModel: MarkViewModel): List<WatchPermission> {
        val requests = mutableListOf<WatchPermission>()
        backgroundScope.launch { viewModel.permissionRequests.collect { requests += it } }
        runCurrent()
        return requests
    }

    private class InMemoryPromptStore(
        private val asked: MutableSet<WatchPermission>,
    ) : PermissionPromptStore {
        override fun hasAsked(permission: WatchPermission): Boolean = permission in asked

        override fun markAsked(permission: WatchPermission) {
            asked += permission
        }
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/mark/BackgroundMarkerTest.kt`:

```kotlin
package app.logdate.wear.v2.mark

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.health.HeartRateSampler
import app.logdate.wear.v2.haptics.RecordedHaptics
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundMarkerTest {
    private val featureFlags = mockk<FeatureFlagStore> { coEvery { isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) } returns true }
    private val marks = FakeMarkedMomentDao()
    private val haptics = RecordedHaptics()
    private val marker =
        BackgroundMarker(
            featureFlags = featureFlags,
            markMoment =
                MarkMomentUseCase(
                    store = MarkedMomentStore(marks, FakePeerDeliveryDao(), mockk<SyncMetadataService>(relaxed = true)),
                    deviceId = { "watch-1" },
                    place = { null },
                    heartRate = HeartRateSampler.None,
                    timeZoneId = { "UTC" },
                ),
            haptics = haptics,
        )

    @Test
    fun `a mark from outside the app has only its time and is confirmed by a haptic`() =
        runTest {
            assertTrue(marker.mark())

            val mark = marks.rows.values.single()
            assertNull(mark.latitude)
            assertNull(mark.heartRateBpm)
            assertEquals(listOf("momentMarked"), haptics.played)
        }

    @Test
    fun `nothing is marked while the new watch app is off`() =
        runTest {
            coEvery { featureFlags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) } returns false

            assertFalse(marker.mark())

            assertTrue(marks.rows.isEmpty())
            assertTrue(haptics.played.isEmpty())
        }

    @Test
    fun `a mark that cannot be saved is refused with a haptic`() =
        runTest {
            marks.failure = IllegalStateException("database closed")

            assertFalse(marker.mark())

            assertEquals(listOf("actionRejected"), haptics.played)
        }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/permissions/WatchPermissionTest.kt`:

```kotlin
package app.logdate.wear.v2.permissions

import android.Manifest
import org.junit.Test
import kotlin.test.assertEquals

class WatchPermissionTest {
    @Test
    fun `Wear OS 6 reads the heart rate under its health permission`() {
        assertEquals("android.permission.health.READ_HEART_RATE", heartRatePermission(sdkInt = 36))
    }

    @Test
    fun `earlier watches read the heart rate under body sensors`() {
        assertEquals(Manifest.permission.BODY_SENSORS, heartRatePermission(sdkInt = 34))
    }
}
```

- [ ] **Step 3: Write the failing GMD receiver test and the confirmation scenes**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MarkMomentReceiverTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.v2.mark.MarkDelivery
import app.logdate.wear.v2.mark.MarkMomentReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Fires Mark's pending intent, as a complication or tile does, and reads the mark from the watch's database. */
@RunWith(AndroidJUnit4::class)
class MarkMomentReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val koin get() = KoinJavaComponent.getKoin()

    @After
    fun tearDown() = runBlocking { setNewWatchApp(enabled = false) }

    @Test
    fun a_broadcast_marks_the_moment_and_queues_it_for_backup() =
        runBlocking {
            setNewWatchApp(enabled = true)
            val before = Instant.fromEpochMilliseconds(System.currentTimeMillis())

            MarkMomentReceiver.pendingIntent(context).send()

            val mark = withTimeout(TIMEOUT_MS) { openMarksSince(before).first { it.isNotEmpty() }.single() }
            assertNull(mark.latitude)
            assertNull(mark.heartRateBpm)
            assertTrue(koin.get<SyncMetadataService>().hasPending(EntityType.MOMENT, mark.id.toString()))
            val delivery =
                koin.get<PeerDeliveryDao>().get(MarkDelivery.PEER_SCOPE, MarkDelivery.DIRECTION, EntityType.MOMENT.name, mark.id.toString())
            assertEquals(MarkDelivery.STATE_WAITING, delivery?.state)
        }

    @Test
    fun nothing_is_marked_while_the_new_watch_app_is_off() =
        runBlocking {
            setNewWatchApp(enabled = false)
            val before = Instant.fromEpochMilliseconds(System.currentTimeMillis())

            MarkMomentReceiver.pendingIntent(context).send()
            delay(3_000)

            assertTrue(openMarksSince(before).first().isEmpty())
        }

    private fun openMarksSince(after: Instant): Flow<List<MarkedMomentEntity>> =
        koin.get<MarkedMomentDao>().observeByState(MarkedMomentState.OPEN).map { rows -> rows.filter { it.capturedAt >= after } }

    private suspend fun setNewWatchApp(enabled: Boolean) {
        koin.get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
```

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MarkConfirmationScreenshots.kt`:

```kotlin
package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import app.logdate.wear.v2.mark.MarkConfirmationContent
import app.logdate.wear.v2.mark.MarkFeedback
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest

/** What Mark shows when it saves a mark, and when it can't. */
class MarkConfirmationScreenshots {
    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun M01_Marked() {
        LogDateWearTheme(dynamicColor = false) { MarkConfirmationContent(MarkFeedback.MARKED) }
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun M02_MarkFailed() {
        LogDateWearTheme(dynamicColor = false) { MarkConfirmationContent(MarkFeedback.FAILED) }
    }
}
```

- [ ] **Step 4: Run the unit tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.health.MeasuredHeartRateSamplerTest' --tests 'app.logdate.wear.v2.mark.*' --tests 'app.logdate.wear.v2.permissions.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'MeasuredHeartRateSampler'`, `'HeartRateReading'`, `'MarkMomentUseCase'`, `'MarkViewModel'`, `'BackgroundMarker'`, `'HEART_RATE'` and `'heartRatePermission'`.

- [ ] **Step 5: Add the heart rate permission and the copy**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/WatchPermission.kt` with:

```kotlin
package app.logdate.wear.v2.permissions

import android.Manifest
import android.os.Build

/**
 * A runtime permission the new watch app asks for when a feature first needs it.
 *
 * @property storedName How [PermissionPromptStore] records that it was asked for. Never changes.
 */
enum class WatchPermission(
    val storedName: String,
) {
    /** Recording. Asked each time recording starts without it. */
    MICROPHONE("microphone"),

    /** A recording's Ongoing Activity and its Stop control. Asked once, on the first locked recording. */
    NOTIFICATIONS("notifications"),

    /** A mark's heart rate. Asked once, on the first mark. */
    HEART_RATE("heart_rate"),
    ;

    /** The name the system knows this permission by. */
    fun manifestName(): String =
        when (this) {
            MICROPHONE -> Manifest.permission.RECORD_AUDIO
            NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
            HEART_RATE -> heartRatePermission(Build.VERSION.SDK_INT)
        }
}

/** Wear OS 6 reads the heart rate under its own health permission; earlier versions under body sensors. */
internal fun heartRatePermission(sdkInt: Int): String =
    if (sdkInt >= Build.VERSION_CODES.BAKLAVA) READ_HEART_RATE else Manifest.permission.BODY_SENSORS

private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
```

In `app/wear/src/main/AndroidManifest.xml`, replace

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

with

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!--
        A mark's heart rate, read once when someone marks a moment. Wear OS 6 reads it under its own
        health permission, earlier versions under body sensors.
    -->
    <uses-permission
        android:name="android.permission.BODY_SENSORS"
        android:maxSdkVersion="35" />
    <uses-permission android:name="android.permission.health.READ_HEART_RATE" />
```

and replace

```xml
        <activity
            android:name=".presentation.MainActivity"
```

with

```xml
        <!-- Marks the moment from a complication, a tile or a notification action, without opening the app. -->
        <receiver
            android:name=".v2.mark.MarkMomentReceiver"
            android:exported="false" />

        <activity
            android:name=".presentation.MainActivity"
```

Create `app/wear/src/main/res/values/strings_capture.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Mark the moment, in the new watch app -->
    <string name="wear_mark_saved">Moment marked</string>
    <string name="wear_mark_failed">Couldn’t mark the moment</string>
</resources>
```

- [ ] **Step 6: Add the heart rate sampler**

Create `app/wear/src/main/kotlin/app/logdate/wear/health/HeartRateSampler.kt`:

```kotlin
package app.logdate.wear.health

import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.getCapabilities
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One heart rate reading, for a mark. */
fun interface HeartRateSampler {
    /** Beats per minute, or null when none can be read in time. */
    suspend fun sample(): Int?

    companion object {
        /** For a watch without Health Services. */
        val None = HeartRateSampler { null }
    }
}

/** What the heart rate sensor reports while it is read. */
sealed interface HeartRateReading {
    data class Bpm(
        val value: Int,
    ) : HeartRateReading

    /** The sensor can't read now: the watch is off the wrist, or the sensor is unavailable. */
    data object Unavailable : HeartRateReading
}

/** Live readings from the sensor, for as long as they are collected. */
fun interface HeartRateReadings {
    fun readings(): Flow<HeartRateReading>
}

/**
 * Reads the heart rate once: the first plausible reading within [timeout], or null when the
 * permission is missing, the sensor reports it can't read, or nothing arrives in time.
 */
class MeasuredHeartRateSampler(
    private val readings: HeartRateReadings,
    private val hasPermission: () -> Boolean,
    private val timeout: Duration = DEFAULT_TIMEOUT,
) : HeartRateSampler {
    override suspend fun sample(): Int? {
        if (!hasPermission()) return null
        return try {
            withTimeoutOrNull(timeout) { firstReading() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Couldn't read the heart rate", e)
            null
        }
    }

    private suspend fun firstReading(): Int? {
        val reading =
            readings.readings().first { candidate ->
                candidate == HeartRateReading.Unavailable || (candidate is HeartRateReading.Bpm && candidate.value in PLAUSIBLE_BPM)
            }
        return (reading as? HeartRateReading.Bpm)?.value
    }

    companion object {
        val DEFAULT_TIMEOUT: Duration = 8.seconds
        private val PLAUSIBLE_BPM = 30..240
    }
}

/** [HeartRateReadings] from Health Services' MeasureClient, which runs the sensor only while collected. */
class MeasureClientHeartRateReadings(
    private val measureClient: MeasureClient,
) : HeartRateReadings {
    override fun readings(): Flow<HeartRateReading> =
        callbackFlow {
            if (DataType.HEART_RATE_BPM !in measureClient.getCapabilities().supportedDataTypesMeasure) {
                send(HeartRateReading.Unavailable)
                close()
                return@callbackFlow
            }
            val callback =
                object : MeasureCallback {
                    override fun onAvailabilityChanged(
                        dataType: DeltaDataType<*, *>,
                        availability: Availability,
                    ) {
                        if (availability in UNAVAILABLE) trySend(HeartRateReading.Unavailable)
                    }

                    override fun onDataReceived(data: DataPointContainer) {
                        val point = data.getData(DataType.HEART_RATE_BPM).lastOrNull() ?: return
                        trySend(HeartRateReading.Bpm(point.value.roundToInt()))
                    }

                    override fun onRegistrationFailed(throwable: Throwable) {
                        close(throwable)
                    }
                }
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
            awaitClose { measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback) }
        }

    private companion object {
        val UNAVAILABLE: Set<Availability> = setOf(DataTypeAvailability.UNAVAILABLE, DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY)
    }
}
```

- [ ] **Step 7: Add the use case**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkMomentUseCase.kt`:

```kotlin
package app.logdate.wear.v2.mark

import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.health.HeartRateSampler
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Marks the current moment on the watch. [mark] saves and queues the mark at once, with its time,
 * time zone and this watch, so a slow sensor can never lose it. [enrich] then adds the place and
 * the heart rate, when either can be read, and saves the mark again; only the app on screen can
 * read them.
 */
class MarkMomentUseCase(
    private val store: MarkedMomentStore,
    private val deviceId: () -> String,
    private val place: suspend () -> NoteLocation?,
    private val heartRate: HeartRateSampler,
    private val clock: Clock = Clock.System,
    private val timeZoneId: () -> String = { TimeZone.currentSystemDefault().id },
) {
    suspend fun mark(): MarkedMomentEntity {
        val now = clock.now()
        val mark =
            MarkedMomentEntity(
                id = Uuid.random(),
                capturedAt = now,
                timeZoneId = timeZoneId(),
                state = MarkedMomentState.OPEN,
                originDeviceId = deviceId(),
                lastUpdated = now,
            )
        store.create(mark)
        return mark
    }

    /** Adds what can be read in a few seconds and saves the mark again; returns [mark] unchanged when nothing could. */
    suspend fun enrich(mark: MarkedMomentEntity): MarkedMomentEntity {
        val (location, bpm) =
            coroutineScope {
                val location = async { withTimeoutOrNull(PLACE_TIMEOUT_MS) { place() } }
                val bpm = async { heartRate.sample() }
                location.await() to bpm.await()
            }
        val latitude = location?.effectiveLatitude
        val longitude = location?.effectiveLongitude
        val hasPlace = latitude != null && longitude != null
        if (!hasPlace && bpm == null) return mark
        val enriched =
            mark.copy(
                latitude = if (hasPlace) latitude else mark.latitude,
                longitude = if (hasPlace) longitude else mark.longitude,
                locationAccuracy = if (hasPlace) location?.coordinates?.accuracy else mark.locationAccuracy,
                heartRateBpm = bpm ?: mark.heartRateBpm,
                lastUpdated = clock.now(),
            )
        store.update(enriched)
        return enriched
    }

    companion object {
        const val PLACE_TIMEOUT_MS = 3_000L
    }
}
```

- [ ] **Step 8: Add Mark for Today and for outside the app**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkViewModel.kt`:

```kotlin
package app.logdate.wear.v2.mark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.v2.permissions.PermissionPrompts
import app.logdate.wear.v2.permissions.WatchPermission
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** What Today says after Mark. */
enum class MarkFeedback {
    NONE,
    MARKED,
    FAILED,
}

/**
 * Mark from Today. The mark is saved at once and confirmed with a haptic and "Moment marked"; the
 * place and heart rate follow on [applicationScope], so they finish even if the screen goes away.
 * The first mark asks for the heart rate permission, once.
 */
class MarkViewModel(
    private val markMoment: MarkMomentUseCase,
    private val haptics: LogDateHaptics,
    private val permissions: PermissionPrompts,
    private val applicationScope: CoroutineScope,
) : ViewModel() {
    private val mutableFeedback = MutableStateFlow(MarkFeedback.NONE)
    private val permissionRequestChannel = Channel<WatchPermission>(Channel.BUFFERED)
    private var waitingForHeartRate: MarkedMomentEntity? = null

    val feedback: StateFlow<MarkFeedback> = mutableFeedback.asStateFlow()

    /** Permissions the screen should ask for now. */
    val permissionRequests: Flow<WatchPermission> = permissionRequestChannel.receiveAsFlow()

    fun mark() {
        viewModelScope.launch {
            val mark = saveMark() ?: return@launch
            haptics.momentMarked()
            mutableFeedback.value = MarkFeedback.MARKED
            if (permissions.takeOptionalPrompt(WatchPermission.HEART_RATE)) {
                waitingForHeartRate = mark
                permissionRequestChannel.trySend(WatchPermission.HEART_RATE)
                return@launch
            }
            enrichLater(mark)
        }
    }

    /** The heart rate request was answered; the mark waiting for it is filled in either way. */
    fun onPermissionResult(permission: WatchPermission) {
        if (permission != WatchPermission.HEART_RATE) return
        val mark = waitingForHeartRate ?: return
        waitingForHeartRate = null
        enrichLater(mark)
    }

    fun dismissFeedback() {
        mutableFeedback.value = MarkFeedback.NONE
    }

    private suspend fun saveMark(): MarkedMomentEntity? =
        try {
            markMoment.mark()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't mark the moment", e)
            haptics.actionRejected()
            mutableFeedback.value = MarkFeedback.FAILED
            null
        }

    private fun enrichLater(mark: MarkedMomentEntity) {
        applicationScope.launch {
            try {
                markMoment.enrich(mark)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Couldn't add the place or heart rate to mark ${mark.id}", e)
            }
        }
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkMomentReceiver.kt`:

```kotlin
package app.logdate.wear.v2.mark

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.ui.platform.LogDateHaptics
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Marks the moment for [MarkMomentReceiver]. A background app can read neither the place nor the
 * heart rate, so the mark has only its time; the phone adds the place later. A haptic confirms it.
 */
class BackgroundMarker(
    private val featureFlags: FeatureFlagStore,
    private val markMoment: MarkMomentUseCase,
    private val haptics: LogDateHaptics,
) {
    /** Returns whether a mark was saved. Nothing is marked while the new watch app is off. */
    suspend fun mark(): Boolean =
        try {
            if (featureFlags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) {
                markMoment.mark()
                haptics.momentMarked()
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't mark the moment from outside the app", e)
            haptics.actionRejected()
            false
        }
}

/**
 * Marks the moment from outside the app: a complication, a tile or a notification action fires
 * [pendingIntent]. It isn't exported, so only this app's own pending intents reach it.
 */
class MarkMomentReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val marker: BackgroundMarker by inject()
    private val scope: CoroutineScope by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_MARK_MOMENT) return
        val pending = goAsync()
        scope.launch {
            try {
                marker.mark()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_MOMENT = "app.logdate.wear.action.MARK_MOMENT"
        private const val REQUEST_MARK = 4_102

        /** What a complication, a tile or a notification action fires to mark the moment. */
        fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_MARK,
                Intent(context, MarkMomentReceiver::class.java).setAction(ACTION_MARK_MOMENT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkConfirmation.kt`:

```kotlin
package app.logdate.wear.v2.mark

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogContent
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.FailureConfirmationDialogContent
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.confirmationDialogCurvedText
import app.logdate.ui.glyphs.LogDateGlyph
import app.logdate.ui.glyphs.vector
import app.logdate.wear.R

/** "Moment marked" with the mark glyph after Mark, or the failure, each for the dialog's usual few seconds. */
@Composable
fun MarkConfirmation(
    feedback: MarkFeedback,
    onDismiss: () -> Unit,
) {
    val marked = stringResource(R.string.wear_mark_saved)
    val failed = stringResource(R.string.wear_mark_failed)
    val style = ConfirmationDialogDefaults.curvedTextStyle
    ConfirmationDialog(
        visible = feedback == MarkFeedback.MARKED,
        onDismissRequest = onDismiss,
        curvedText = { confirmationDialogCurvedText(marked, style) },
    ) {
        MarkGlyph()
    }
    FailureConfirmationDialog(
        visible = feedback == MarkFeedback.FAILED,
        onDismissRequest = onDismiss,
        curvedText = { confirmationDialogCurvedText(failed, style) },
    )
}

/** What [MarkConfirmation] shows, without the dialog window, for screenshots. */
@Composable
fun MarkConfirmationContent(feedback: MarkFeedback) {
    val style = ConfirmationDialogDefaults.curvedTextStyle
    when (feedback) {
        MarkFeedback.MARKED -> {
            val marked = stringResource(R.string.wear_mark_saved)
            ConfirmationDialogContent(curvedText = { confirmationDialogCurvedText(marked, style) }) { MarkGlyph() }
        }
        MarkFeedback.FAILED -> {
            val failed = stringResource(R.string.wear_mark_failed)
            FailureConfirmationDialogContent(curvedText = { confirmationDialogCurvedText(failed, style) })
        }
        MarkFeedback.NONE -> Unit
    }
}

@Composable
private fun MarkGlyph() {
    Icon(imageVector = LogDateGlyph.MARK.vector(), contentDescription = null, modifier = Modifier.size(MARK_GLYPH_SIZE))
}

private val MARK_GLYPH_SIZE = 32.dp
```

- [ ] **Step 9: Bind Mark in Koin**

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add these imports in ASCII order with the existing ones:

```kotlin
import androidx.health.services.client.HealthServices
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.wear.health.HeartRateSampler
import app.logdate.wear.health.MeasureClientHeartRateReadings
import app.logdate.wear.health.MeasuredHeartRateSampler
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.v2.mark.BackgroundMarker
import app.logdate.wear.v2.mark.MarkMomentUseCase
import app.logdate.wear.v2.mark.MarkViewModel
import app.logdate.wear.v2.permissions.WatchPermission
import io.github.aakira.napier.Napier
```

and add after the `MarkedMomentStore` definition:

```kotlin
        single<HeartRateSampler> {
            val checker = get<PermissionChecker>()
            try {
                MeasuredHeartRateSampler(
                    readings = MeasureClientHeartRateReadings(HealthServices.getClient(get<Context>()).measureClient),
                    hasPermission = { checker.isGranted(WatchPermission.HEART_RATE) },
                )
            } catch (e: Exception) {
                Napier.w("Health Services is not available; marks have no heart rate on this watch", e)
                HeartRateSampler.None
            }
        }
        single {
            val location = get<WearLocationCaptureCoordinator>()
            val devices = get<DeviceIdProvider>()
            MarkMomentUseCase(
                store = get(),
                deviceId = { devices.getDeviceId().value.toString() },
                place = { location.captureForJournalEntry() },
                heartRate = get(),
            )
        }
        single { BackgroundMarker(featureFlags = get(), markMoment = get(), haptics = get()) }
        viewModel { MarkViewModel(markMoment = get(), haptics = get(), permissions = get(), applicationScope = get()) }
```

- [ ] **Step 10: Put Mark on Today**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` with:

```kotlin
package app.logdate.wear.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import app.logdate.ui.platform.LocalLogDateHaptics
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.v2.ambient.WatchAmbientScope
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowViewModel
import app.logdate.wear.v2.home.HomeScreen
import app.logdate.wear.v2.mark.MarkConfirmation
import app.logdate.wear.v2.mark.MarkViewModel
import app.logdate.wear.v2.navigation.WearRoute
import app.logdate.wear.v2.navigation.toV2BackStack
import app.logdate.wear.v2.permissions.rememberPermissionRequester
import app.logdate.wear.v2.recorder.RecorderState
import app.logdate.wear.v2.recorder.RecorderViewModel
import app.logdate.wear.v2.recorder.TodayRecorder
import app.logdate.wear.v2.recorder.locksPaging
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.TodayControls
import app.logdate.wear.v2.today.TodayPage
import app.logdate.wear.v2.today.TodayViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

object WearAppV2Tags {
    const val SHELL = "wear_app_v2_shell"
}

/**
 * The redesigned watch app, shown while WATCH_EXPERIENCE_V2 is on.
 *
 * [initialRequest] picks the first back stack; a recreated activity restores the one it saved
 * instead. Each item of [newRequests] replaces the back stack, as a cold launch from a tile does.
 * Swiping right dismisses the top screen. Each screen's view models live as long as its entry; the
 * recorder and Mark are resolved here, outside every entry, so they belong to the activity, and the
 * permissions they ask for are requested here. While recording, the app stays on screen in ambient
 * mode where the watch supports it.
 */
@Composable
fun WearAppV2(
    initialRequest: WearLaunchRequest?,
    newRequests: Flow<WearLaunchRequest>,
) {
    val recorder = koinViewModel<RecorderViewModel>()
    val marker = koinViewModel<MarkViewModel>()
    val recorderState by recorder.state.collectAsState()
    val backStack = rememberNavBackStack(*initialRequest.toV2BackStack().toTypedArray())
    LaunchedEffect(newRequests) {
        newRequests.collect { request ->
            backStack.clear()
            backStack.addAll(request.toV2BackStack())
        }
    }
    PermissionRequests(recorder, marker)
    WatchAmbientScope(enabled = recorderState is RecorderState.Live) {
        LogDateWearTheme {
            CompositionLocalProvider(LocalLogDateHaptics provides koinInject<LogDateHaptics>()) {
                AppScaffold(modifier = Modifier.testTag(WearAppV2Tags.SHELL)) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryDecorators =
                            listOf(
                                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                                rememberViewModelStoreNavEntryDecorator<NavKey>(),
                            ),
                        sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy<NavKey>()),
                        entryProvider =
                            entryProvider {
                                entry<WearRoute.Home> { HomeEntry(recorder, marker) }
                            },
                    )
                }
            }
        }
    }
}

/** Asks for the permissions the recorder and Mark need at the moment they need them, and gives them the answers. */
@Composable
private fun PermissionRequests(
    recorder: RecorderViewModel,
    marker: MarkViewModel,
) {
    val requester =
        rememberPermissionRequester { permission, result ->
            recorder.onPermissionResult(permission, result)
            marker.onPermissionResult(permission)
        }
    LaunchedEffect(recorder, marker, requester) {
        merge(recorder.permissionRequests, marker.permissionRequests).collect { permission -> requester.request(permission) }
    }
}

@Composable
private fun HomeEntry(
    recorder: RecorderViewModel,
    marker: MarkViewModel,
) {
    val recorderState by recorder.state.collectAsState()
    val markFeedback by marker.feedback.collectAsState()
    val controls =
        remember(recorderState, recorder, marker) {
            TodayControls(
                recorder = TodayRecorder(recorderState, gesture = recorder, actions = recorder),
                onMark = marker::mark,
            )
        }
    HomeScreen(
        pagingLocked = recorderState.locksPaging,
        today = { listState -> TodayEntry(listState, controls) },
        flow = { listState -> FlowEntry(listState) },
    )
    MarkConfirmation(feedback = markFeedback, onDismiss = marker::dismissFeedback)
}

@Composable
private fun TodayEntry(
    listState: TransformingLazyColumnState,
    controls: TodayControls,
) {
    val viewModel = koinViewModel<TodayViewModel>()
    val state by viewModel.uiState.collectAsState()
    val handoff by viewModel.handoff.collectAsState()
    TodayPage(
        state = state,
        handoff = handoff,
        onGetPhoneApp = viewModel::getPhoneApp,
        onHandoffDismissed = viewModel::dismissHandoff,
        controls = controls,
        listState = listState,
    )
}

@Composable
private fun FlowEntry(listState: TransformingLazyColumnState) {
    val viewModel = koinViewModel<FlowViewModel>()
    val state by viewModel.uiState.collectAsState()
    FlowPage(state = state, listState = listState)
}
```

- [ ] **Step 11: Run the unit tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.health.*' --tests 'app.logdate.wear.v2.*'
```

Expected: `BUILD SUCCESSFUL`; `MeasuredHeartRateSamplerTest` 5, `MarkMomentUseCaseTest` 4, `MarkViewModelTest` 5, `BackgroundMarkerTest` 3, `WatchPermissionTest` 2.

- [ ] **Step 12: Run the GMD tests**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.MarkMomentReceiverTest,app.logdate.wear.e2e.MarkedMomentStoreE2ETest,app.logdate.wear.e2e.WearManifestFiltersTest,app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.ExperienceGateTest
```

Expected: `BUILD SUCCESSFUL`; `MarkMomentReceiverTest` passes 2 tests, and the shell tests resolve `MarkViewModel` and the heart rate sampler from the real graph.

- [ ] **Step 13: Record and check the confirmation references**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest; ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/MarkConfirmationScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: the first command FAILs listing only the 6 `MarkConfirmationScreenshots` previews as missing; then `BUILD SUCCESSFUL`, `6`, and only the new directory in the status. Open `M01_Marked_Small Round_*.png` (the mark glyph and "Moment marked" curved along the edge) and `M02_MarkFailed_Large Font_*.png` ("Couldn’t mark the moment", not clipped). Then:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 14: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task9-commit.txt <<'EOF'
chore(wear): mark the moment from Today or outside the app

Mark appears on Today in the redesigned watch app. A tap saves the
moment at once with its time, confirms it with a haptic that plays
even with haptics set to Essential, and shows "Moment marked". Within
a few seconds the place and one heart rate reading are added when the
watch can read them; the first mark asks for the heart rate
permission, once.

A complication, tile or notification action can also mark the moment
without opening the app, through a receiver only this app can reach.
Those marks have only their time, because a background app can read
neither the place nor the heart rate. Nothing is marked while the new
watch app is off.

The watch now declares the heart rate permission: Wear OS 6's health
permission, and body sensors on earlier versions.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 15: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/health/HeartRateSampler.kt app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkMomentUseCase.kt app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkMomentReceiver.kt app/wear/src/main/kotlin/app/logdate/wear/v2/mark/MarkConfirmation.kt app/wear/src/main/res/values/strings_capture.xml app/wear/src/main/kotlin/app/logdate/wear/v2/permissions/WatchPermission.kt app/wear/src/main/AndroidManifest.xml app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MarkConfirmationScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/MarkConfirmationScreenshots app/wear/src/test/kotlin/app/logdate/wear/health/MeasuredHeartRateSamplerTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/mark/MarkMomentUseCaseTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/mark/MarkViewModelTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/mark/BackgroundMarkerTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/permissions/WatchPermissionTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MarkMomentReceiverTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task9-commit.txt
```

---
### Task 10: The + sheet (`chore(wear)`)

+ appears on Today in this task, with the sheet it opens (`WearRoute.CaptureSheet`):

- **Write** opens the system text input through `wear-input`'s `RemoteInputIntentHelper`, where the watch offers voice and the keyboard, and saves what comes back as a text note.
- **Mood** opens the five feelings in the sheet; a tap saves a check-in.
- **Phone camera** appears only while a phone running LogDate is in reach (`PhoneReachability`, the capability the existing camera protocol needs) and opens the existing remote camera screen as `WearRoute.PhoneCamera`, so the photo is taken and stored on the phone.

A save confirms with "Saved" and returns to Today; a failure says "Couldn’t save" and stays on the sheet. The sheet has no Microphone row: the spec shows it only with more than one input to choose from, and choosing an input is not part of this plan.

**Files:**
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/capture/QuickNoteWriter.kt` (whole file)
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/capture/RemoteTextInput.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/capture/CaptureSheetViewModel.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/capture/CaptureSheet.kt`
- Modify: `app/wear/src/main/res/values/strings_capture.xml`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` (whole file)
- Create (screenshot): `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/CaptureSheetScreenshots.kt`
- Modify (test): `app/wear/src/test/kotlin/app/logdate/wear/capture/QuickNoteWriterTest.kt` (whole file)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/capture/CaptureSheetViewModelTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/CaptureSheetTest.kt`
- References: `app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/CaptureSheetScreenshots/` (15 new)

**Interfaces:**
- Consumes: `FeelingSaver`, `QuickNoteWriter` (Task 4), `PhoneReachability.observe(): Flow<ReachablePhone?>`, `WearRemoteCameraScreen(viewModel, onNavigateBack)` and `WearRemoteCameraViewModel` (already bound in `AudioModule.kt`), `RemoteInputIntentHelper`, `WearRoute.CaptureSheet`/`PhoneCamera`, `TodayControls.onAdd`.
- Produces:
  - `fun interface TextNoteSaver { suspend fun save(text: String): Boolean }`; `QuickNoteWriter : FeelingSaver, TextNoteSaver`.
  - `internal const val WRITTEN_TEXT_KEY`, `fun remoteTextInputIntent(label: String): Intent`, `fun writtenText(result: Intent?): String?`, `@Composable fun rememberRemoteTextInput(onText: (String) -> Unit): () -> Unit`.
  - `enum class CaptureResult { NONE, SAVED, FAILED }`, `data class CaptureSheetUiState(phoneInReach, choosingFeeling, saving, result)`, `class CaptureSheetViewModel(textSaver, feelingSaver, phoneReachability, haptics) { val uiState; fun saveText(text: String); fun toggleFeelings(); fun saveFeeling(feeling: Feeling); fun dismissResult() }`.
  - `object CaptureSheetTags`, `@Composable fun CaptureSheet(state, onWrite, onToggleFeelings, onFeeling, onPhoneCamera, modifier, listState)`, `@Composable fun CaptureConfirmation(result, onSavedDismissed, onFailedDismissed)`, `@Composable fun CaptureConfirmationContent(result)`.

- [ ] **Step 1: Write the failing writer and view model tests**

Replace the whole of `app/wear/src/test/kotlin/app/logdate/wear/capture/QuickNoteWriterTest.kt` with:

```kotlin
package app.logdate.wear.capture

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.location.WearLocationCaptureCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class QuickNoteWriterTest {
    private val notesRepository = mockk<JournalNotesRepository>()
    private val locationCaptureCoordinator = mockk<WearLocationCaptureCoordinator>()
    private val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
    private val created = slot<JournalNote>()

    @Before
    fun setUp() {
        coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns null
        coEvery { notesRepository.create(capture(created)) } returns Uuid.random()
    }

    @Test
    fun `a feeling is saved as a mood check-in note`() =
        runTest {
            assertTrue(writer().save(Feeling.GOOD))

            val note = created.captured as JournalNote.Text
            assertEquals(MomentNoteCodec.encode(Feeling.GOOD), note.content)
            assertEquals(now, note.creationTimestamp)
        }

    @Test
    fun `the place is saved with the check-in`() =
        runTest {
            val location = NoteLocation(coordinates = NoteCoordinates(latitude = 51.5, longitude = -0.12))
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns location

            writer().save(Feeling.GREAT)

            assertEquals(location, created.captured.location)
        }

    @Test
    fun `a check-in that cannot be stored reports false`() =
        runTest {
            coEvery { notesRepository.create(any<JournalNote>()) } throws IllegalStateException("database closed")

            assertFalse(writer().save(Feeling.SAD))
        }

    @Test
    fun `written text is saved as a text note without its outer spaces`() =
        runTest {
            assertTrue(writer().save("  Coffee with Sam "))

            assertEquals("Coffee with Sam", (created.captured as JournalNote.Text).content)
        }

    @Test
    fun `blank text is not saved`() =
        runTest {
            assertFalse(writer().save("   "))

            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    private fun writer() =
        QuickNoteWriter(
            notesRepository = notesRepository,
            locationCaptureCoordinator = locationCaptureCoordinator,
            clock = FixedClock(now),
        )

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/capture/CaptureSheetViewModelTest.kt`:

```kotlin
package app.logdate.wear.v2.capture

import app.cash.turbine.test
import app.logdate.wear.capture.Feeling
import app.logdate.wear.sync.ReachablePhone
import app.logdate.wear.v2.haptics.RecordedHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureSheetViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val phone = MutableStateFlow<ReachablePhone?>(null)
    private val savedTexts = mutableListOf<String>()
    private val savedFeelings = mutableListOf<Feeling>()
    private var saves = true
    private val haptics = RecordedHaptics()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the phone camera is offered only while the phone is in reach`() =
        runTest {
            viewModel().uiState.test {
                assertFalse(awaitItem().phoneInReach)

                phone.value = ReachablePhone("Pixel")

                assertTrue(awaitItem().phoneInReach)
            }
        }

    @Test
    fun `written text is saved and confirmed`() =
        runTest {
            val viewModel = watched(viewModel())

            viewModel.saveText("Coffee with Sam")
            runCurrent()

            assertEquals(listOf("Coffee with Sam"), savedTexts)
            assertEquals(CaptureResult.SAVED, viewModel.uiState.value.result)
            assertEquals(listOf("saveSucceeded"), haptics.played)
        }

    @Test
    fun `blank text saves nothing`() =
        runTest {
            val viewModel = watched(viewModel())

            viewModel.saveText("  ")
            runCurrent()

            assertTrue(savedTexts.isEmpty())
            assertEquals(CaptureResult.NONE, viewModel.uiState.value.result)
        }

    @Test
    fun `a feeling is saved and the feelings close`() =
        runTest {
            val viewModel = watched(viewModel())
            viewModel.toggleFeelings()
            runCurrent()
            assertTrue(viewModel.uiState.value.choosingFeeling)

            viewModel.saveFeeling(Feeling.OK)
            runCurrent()

            assertEquals(listOf(Feeling.OK), savedFeelings)
            assertFalse(viewModel.uiState.value.choosingFeeling)
            assertEquals(CaptureResult.SAVED, viewModel.uiState.value.result)
        }

    @Test
    fun `a save that fails says so and the message can be dismissed`() =
        runTest {
            saves = false
            val viewModel = watched(viewModel())

            viewModel.saveText("Coffee with Sam")
            runCurrent()
            assertEquals(CaptureResult.FAILED, viewModel.uiState.value.result)
            assertEquals(listOf("actionRejected"), haptics.played)

            viewModel.dismissResult()
            runCurrent()
            assertEquals(CaptureResult.NONE, viewModel.uiState.value.result)
        }

    private fun viewModel() =
        CaptureSheetViewModel(
            textSaver = { text ->
                if (saves) savedTexts += text
                saves
            },
            feelingSaver = { feeling ->
                if (saves) savedFeelings += feeling
                saves
            },
            phoneReachability = { phone },
            haptics = haptics,
        )

    /** Collects [viewModel]'s state, as the sheet does, so its value follows each change. */
    private fun TestScope.watched(viewModel: CaptureSheetViewModel): CaptureSheetViewModel {
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }
}
```

- [ ] **Step 2: Write the failing GMD sheet test and the screenshot scenes**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/CaptureSheetTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.input.RemoteInputIntentHelper
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.capture.CaptureSheet
import app.logdate.wear.v2.capture.CaptureSheetTags
import app.logdate.wear.v2.capture.CaptureSheetUiState
import app.logdate.wear.v2.capture.WRITTEN_TEXT_KEY
import app.logdate.wear.v2.capture.remoteTextInputIntent
import app.logdate.wear.v2.capture.writtenText
import app.logdate.wear.v2.theme.LogDateWearTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The + sheet on a Wear OS emulator, and the system text input it hands writing to. */
@RunWith(AndroidJUnit4::class)
class CaptureSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_phone_camera_shows_only_while_the_phone_is_in_reach() {
        var state by mutableStateOf(CaptureSheetUiState(phoneInReach = false))
        show(state = { state })
        composeRule.onAllNodesWithTag(CaptureSheetTags.PHONE_CAMERA).assertCountEquals(0)

        state = CaptureSheetUiState(phoneInReach = true)
        scrollTo(CaptureSheetTags.PHONE_CAMERA)

        composeRule.onNodeWithTag(CaptureSheetTags.PHONE_CAMERA).assertExists()
    }

    @Test
    fun mood_opens_the_five_feelings_and_a_tap_chooses_one() {
        val chosen = mutableListOf<Feeling>()
        var state by mutableStateOf(CaptureSheetUiState())
        show(
            state = { state },
            onToggleFeelings = { state = state.copy(choosingFeeling = !state.choosingFeeling) },
            onFeeling = { feeling -> chosen += feeling },
        )

        composeRule.onNodeWithTag(CaptureSheetTags.MOOD).performClick()
        scrollTo(CaptureSheetTags.feeling(Feeling.GOOD))
        composeRule.onNodeWithTag(CaptureSheetTags.feeling(Feeling.GOOD)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(Feeling.GOOD), chosen)
    }

    @Test
    fun nothing_in_the_sheet_works_while_a_save_runs() {
        show(state = { CaptureSheetUiState(saving = true) })

        composeRule.onNodeWithTag(CaptureSheetTags.WRITE).assertIsNotEnabled()
    }

    @Test
    fun write_asks_the_system_for_text_by_voice_or_keyboard() {
        val intent = remoteTextInputIntent("Write a note")

        assertTrue(RemoteInputIntentHelper.isActionRemoteInput(intent))
        assertEquals(WRITTEN_TEXT_KEY, RemoteInputIntentHelper.getRemoteInputsExtra(intent).single().resultKey)
    }

    @Test
    fun the_written_text_comes_back_from_the_system_input() {
        assertEquals("Coffee with Sam", writtenText(resultWith("Coffee with Sam")))
    }

    @Test
    fun nothing_written_is_no_text() {
        assertNull(writtenText(null))
        assertNull(writtenText(Intent()))
        assertNull(writtenText(resultWith("   ")))
    }

    private fun resultWith(text: String): Intent {
        val input = RemoteInputIntentHelper.getRemoteInputsExtra(remoteTextInputIntent("Write a note")).single()
        val result = Intent()
        RemoteInput.addResultsToIntent(arrayOf(input), result, Bundle().apply { putCharSequence(input.resultKey, text) })
        return result
    }

    private fun show(
        state: () -> CaptureSheetUiState,
        onToggleFeelings: () -> Unit = {},
        onFeeling: (Feeling) -> Unit = {},
    ) {
        composeRule.setContent {
            LogDateWearTheme(dynamicColor = false) {
                AppScaffold {
                    CaptureSheet(
                        state = state(),
                        onWrite = {},
                        onToggleFeelings = onToggleFeelings,
                        onFeeling = onFeeling,
                        onPhoneCamera = {},
                    )
                }
            }
        }
    }

    /** Scrolls the sheet a quarter screen at a time until [tag] is composed. */
    private fun scrollTo(tag: String) {
        repeat(MAX_SCROLLS) {
            composeRule.waitForIdle()
            if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
            composeRule.onNodeWithTag(CaptureSheetTags.LIST).performTouchInput {
                swipeUp(startY = centerY, endY = centerY - height / 4f, durationMillis = 400)
            }
        }
    }

    private companion object {
        const val MAX_SCROLLS = 8
    }
}
```

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/CaptureSheetScreenshots.kt`:

```kotlin
package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import app.logdate.wear.v2.capture.CaptureConfirmationContent
import app.logdate.wear.v2.capture.CaptureResult
import app.logdate.wear.v2.capture.CaptureSheet
import app.logdate.wear.v2.capture.CaptureSheetUiState
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest

/** The + sheet in each state, and what a save from it shows. */
class CaptureSheetScreenshots {
    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun C01_Sheet() {
        CaptureScene(CaptureSheetUiState())
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun C02_PhoneInReach() {
        CaptureScene(CaptureSheetUiState(phoneInReach = true))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun C03_ChoosingAFeeling() {
        CaptureScene(CaptureSheetUiState(choosingFeeling = true))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun C04_Saved() {
        LogDateWearTheme(dynamicColor = false) { CaptureConfirmationContent(CaptureResult.SAVED) }
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun C05_SaveFailed() {
        LogDateWearTheme(dynamicColor = false) { CaptureConfirmationContent(CaptureResult.FAILED) }
    }
}

@Composable
private fun CaptureScene(state: CaptureSheetUiState) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            CaptureSheet(state = state, onWrite = {}, onToggleFeelings = {}, onFeeling = {}, onPhoneCamera = {})
        }
    }
}
```

- [ ] **Step 3: Run the unit tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.capture.QuickNoteWriterTest' --tests 'app.logdate.wear.v2.capture.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'CaptureSheetViewModel'`, `'CaptureResult'`, and no `save(String)` on `QuickNoteWriter`.

- [ ] **Step 4: Save written text**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/capture/QuickNoteWriter.kt` with:

```kotlin
package app.logdate.wear.capture

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SystemCaptureTimeZone
import app.logdate.wear.location.WearLocationCaptureCoordinator
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Saves a feeling as a mood check-in note. */
fun interface FeelingSaver {
    /** Returns false when the check-in could not be stored. */
    suspend fun save(feeling: Feeling): Boolean

    companion object {
        /** For a recorder that offers no feelings after a save. */
        val None = FeelingSaver { false }
    }
}

/** Saves a line of text, written by voice or on the keyboard, as a note. */
fun interface TextNoteSaver {
    /** Returns false when there was nothing to save or the note could not be stored. */
    suspend fun save(text: String): Boolean
}

/**
 * Saves the short notes the watch captures in a tap or two: a feeling, as a mood check-in in the
 * same `#mood:` form the check-in screen writes, and a line of written text. Each note has the
 * place when it can be read within [LOCATION_TIMEOUT_MS].
 */
class QuickNoteWriter(
    private val notesRepository: JournalNotesRepository,
    private val locationCaptureCoordinator: WearLocationCaptureCoordinator,
    private val clock: Clock = Clock.System,
) : FeelingSaver,
    TextNoteSaver {
    override suspend fun save(feeling: Feeling): Boolean = write(MomentNoteCodec.encode(feeling)) != null

    override suspend fun save(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return write(trimmed) != null
    }

    private suspend fun write(content: String): Uuid? =
        try {
            val now = clock.now()
            val note =
                JournalNote.Text(
                    content = content,
                    uid = Uuid.random(),
                    creationTimestamp = now,
                    lastUpdated = now,
                    location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { locationCaptureCoordinator.captureForJournalEntry() },
                    timeZoneId = SystemCaptureTimeZone.currentTimeZoneId(),
                )
            notesRepository.create(note)
            note.uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't save a note from the watch", e)
            null
        }

    companion object {
        const val LOCATION_TIMEOUT_MS = 3_000L
    }
}
```

- [ ] **Step 5: Add the system text input**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/capture/RemoteTextInput.kt`:

```kotlin
package app.logdate.wear.v2.capture

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.WearableRemoteInputExtender
import androidx.wear.input.wearableExtender
import app.logdate.wear.R

/** The key the written text comes back under. */
internal const val WRITTEN_TEXT_KEY = "written_text"

/** The system's text input, where the watch offers voice and the keyboard, with [label] as its prompt. */
fun remoteTextInputIntent(label: String): Intent {
    val input =
        RemoteInput
            .Builder(WRITTEN_TEXT_KEY)
            .setLabel(label)
            .wearableExtender { setInputActionType(WearableRemoteInputExtender.INPUT_ACTION_TYPE_DONE) }
            .build()
    return RemoteInputIntentHelper.putRemoteInputsExtra(RemoteInputIntentHelper.createActionRemoteInputIntent(), listOf(input))
}

/** The text the system input returned, or null when nothing was written. */
fun writtenText(result: Intent?): String? {
    if (result == null) return null
    val text = RemoteInput.getResultsFromIntent(result)?.getCharSequence(WRITTEN_TEXT_KEY)?.toString()
    return text?.takeIf { it.isNotBlank() }
}

/** Opens the system text input; [onText] gets what was written, and a cancelled input does nothing. */
@Composable
fun rememberRemoteTextInput(onText: (String) -> Unit): () -> Unit {
    val label = stringResource(R.string.wear_capture_write_prompt)
    val currentOnText by rememberUpdatedState(onText)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            writtenText(result.data)?.let { text -> currentOnText(text) }
        }
    return remember(launcher, label) { { launcher.launch(remoteTextInputIntent(label)) } }
}
```

- [ ] **Step 6: Add the sheet's view model**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/capture/CaptureSheetViewModel.kt`:

```kotlin
package app.logdate.wear.v2.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.FeelingSaver
import app.logdate.wear.capture.TextNoteSaver
import app.logdate.wear.sync.PhoneReachability
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the last save from the + sheet went. */
enum class CaptureResult {
    NONE,
    SAVED,
    FAILED,
}

/**
 * What the + sheet shows.
 *
 * @property phoneInReach Whether a phone running LogDate is in reach, so its camera can be used.
 * @property choosingFeeling Whether the five feelings are open under Mood.
 * @property saving Whether a save is running; the sheet takes no other action meanwhile.
 */
data class CaptureSheetUiState(
    val phoneInReach: Boolean = false,
    val choosingFeeling: Boolean = false,
    val saving: Boolean = false,
    val result: CaptureResult = CaptureResult.NONE,
)

/** The + sheet: written text and feelings are saved here; the phone camera is its own screen. */
class CaptureSheetViewModel(
    private val textSaver: TextNoteSaver,
    private val feelingSaver: FeelingSaver,
    phoneReachability: PhoneReachability,
    private val haptics: LogDateHaptics,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CaptureSheetUiState())

    val uiState: StateFlow<CaptureSheetUiState> =
        combine(mutableState, phoneInReach(phoneReachability)) { state, inReach -> state.copy(phoneInReach = inReach) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CaptureSheetUiState())

    fun saveText(text: String) {
        if (text.isBlank()) return
        save { textSaver.save(text) }
    }

    fun toggleFeelings() {
        mutableState.update { state -> state.copy(choosingFeeling = !state.choosingFeeling) }
    }

    fun saveFeeling(feeling: Feeling) {
        save { feelingSaver.save(feeling) }
    }

    fun dismissResult() {
        mutableState.update { state -> state.copy(result = CaptureResult.NONE) }
    }

    private fun save(block: suspend () -> Boolean) {
        if (mutableState.value.saving) return
        mutableState.update { state -> state.copy(saving = true, result = CaptureResult.NONE) }
        viewModelScope.launch {
            val saved = block()
            if (saved) haptics.saveSucceeded() else haptics.actionRejected()
            mutableState.update { state ->
                state.copy(saving = false, choosingFeeling = false, result = if (saved) CaptureResult.SAVED else CaptureResult.FAILED)
            }
        }
    }

    private fun phoneInReach(reachability: PhoneReachability): Flow<Boolean> =
        reachability
            .observe()
            .map { phone -> phone != null }
            .onStart { emit(false) }
            .catch { error ->
                Napier.e("Couldn't tell whether the phone is in reach; its camera is not offered", error)
                emit(false)
            }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 7: Add the sheet and its confirmations**

Replace the closing `</resources>` of `app/wear/src/main/res/values/strings_capture.xml` with:

```xml
    <!-- The + sheet, in the new watch app -->
    <string name="wear_capture_title">Add</string>
    <string name="wear_capture_write">Write</string>
    <string name="wear_capture_write_prompt">Write a note</string>
    <string name="wear_capture_mood">Mood</string>
    <string name="wear_capture_phone_camera">Phone camera</string>
    <string name="wear_capture_saved">Saved</string>
    <string name="wear_capture_failed">Couldn’t save</string>
</resources>
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/capture/CaptureSheet.kt`:

```kotlin
package app.logdate.wear.v2.capture

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.FailureConfirmationDialogContent
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.SuccessConfirmationDialogContent
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.wear.R
import app.logdate.wear.capture.Feeling

object CaptureSheetTags {
    const val LIST = "capture_sheet"
    const val WRITE = "capture_write"
    const val MOOD = "capture_mood"
    const val PHONE_CAMERA = "capture_phone_camera"

    fun feeling(feeling: Feeling): String = "capture_feeling_${feeling.tag}"
}

/**
 * The + sheet: Write, Mood with the five feelings under it, and Phone camera while the phone is in
 * reach. Nothing in it can be tapped while a save runs.
 */
@Composable
fun CaptureSheet(
    state: CaptureSheetUiState,
    onWrite: () -> Unit,
    onToggleFeelings: () -> Unit,
    onFeeling: (Feeling) -> Unit,
    onPhoneCamera: () -> Unit,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    val spec = rememberTransformationSpec()
    val enabled = !state.saving
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier.testTag(CaptureSheetTags.LIST)) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.wear_capture_title))
                }
            }
            item { CaptureButton(stringResource(R.string.wear_capture_write), CaptureSheetTags.WRITE, enabled, onWrite, spec) }
            item { CaptureButton(stringResource(R.string.wear_capture_mood), CaptureSheetTags.MOOD, enabled, onToggleFeelings, spec) }
            if (state.choosingFeeling) {
                items(count = Feeling.entries.size) { index ->
                    val feeling = Feeling.entries[index]
                    val label = "${feeling.emoji} ${stringResource(feeling.labelRes)}"
                    CaptureButton(label, CaptureSheetTags.feeling(feeling), enabled, { onFeeling(feeling) }, spec)
                }
            }
            if (state.phoneInReach) {
                item {
                    CaptureButton(
                        text = stringResource(R.string.wear_capture_phone_camera),
                        tag = CaptureSheetTags.PHONE_CAMERA,
                        enabled = enabled,
                        onClick = onPhoneCamera,
                        spec = spec,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.CaptureButton(
    text: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    spec: TransformationSpec,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).testTag(tag),
        enabled = enabled,
        transformation = SurfaceTransformation(spec),
    ) {
        Text(text = text)
    }
}

/** "Saved", which returns to Today when it goes, or "Couldn’t save", which leaves the sheet open. */
@Composable
fun CaptureConfirmation(
    result: CaptureResult,
    onSavedDismissed: () -> Unit,
    onFailedDismissed: () -> Unit,
) {
    val style = ConfirmationDialogDefaults.curvedTextStyle
    val saved = stringResource(R.string.wear_capture_saved)
    val failed = stringResource(R.string.wear_capture_failed)
    SuccessConfirmationDialog(
        visible = result == CaptureResult.SAVED,
        onDismissRequest = onSavedDismissed,
        curvedText = { confirmationDialogCurvedText(saved, style) },
    )
    FailureConfirmationDialog(
        visible = result == CaptureResult.FAILED,
        onDismissRequest = onFailedDismissed,
        curvedText = { confirmationDialogCurvedText(failed, style) },
    )
}

/** What [CaptureConfirmation] shows, without the dialog window, for screenshots. */
@Composable
fun CaptureConfirmationContent(result: CaptureResult) {
    val style = ConfirmationDialogDefaults.curvedTextStyle
    when (result) {
        CaptureResult.SAVED -> {
            val saved = stringResource(R.string.wear_capture_saved)
            SuccessConfirmationDialogContent(curvedText = { confirmationDialogCurvedText(saved, style) })
        }
        CaptureResult.FAILED -> {
            val failed = stringResource(R.string.wear_capture_failed)
            FailureConfirmationDialogContent(curvedText = { confirmationDialogCurvedText(failed, style) })
        }
        CaptureResult.NONE -> Unit
    }
}
```

- [ ] **Step 8: Bind the sheet in Koin**

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add the import `import app.logdate.wear.v2.capture.CaptureSheetViewModel` in ASCII order, and add after the `MarkViewModel` definition:

```kotlin
        viewModel {
            val notes = get<QuickNoteWriter>()
            CaptureSheetViewModel(textSaver = notes, feelingSaver = notes, phoneReachability = get(), haptics = get())
        }
```

- [ ] **Step 9: Put + on Today and add the sheet and phone camera screens**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` with:

```kotlin
package app.logdate.wear.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import app.logdate.ui.platform.LocalLogDateHaptics
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.camera.WearRemoteCameraScreen
import app.logdate.wear.presentation.camera.WearRemoteCameraViewModel
import app.logdate.wear.v2.ambient.WatchAmbientScope
import app.logdate.wear.v2.capture.CaptureConfirmation
import app.logdate.wear.v2.capture.CaptureSheet
import app.logdate.wear.v2.capture.CaptureSheetViewModel
import app.logdate.wear.v2.capture.rememberRemoteTextInput
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowViewModel
import app.logdate.wear.v2.home.HomeScreen
import app.logdate.wear.v2.mark.MarkConfirmation
import app.logdate.wear.v2.mark.MarkViewModel
import app.logdate.wear.v2.navigation.WearRoute
import app.logdate.wear.v2.navigation.toV2BackStack
import app.logdate.wear.v2.permissions.rememberPermissionRequester
import app.logdate.wear.v2.recorder.RecorderState
import app.logdate.wear.v2.recorder.RecorderViewModel
import app.logdate.wear.v2.recorder.TodayRecorder
import app.logdate.wear.v2.recorder.locksPaging
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.TodayControls
import app.logdate.wear.v2.today.TodayPage
import app.logdate.wear.v2.today.TodayViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

object WearAppV2Tags {
    const val SHELL = "wear_app_v2_shell"
}

/**
 * The redesigned watch app, shown while WATCH_EXPERIENCE_V2 is on.
 *
 * [initialRequest] picks the first back stack; a recreated activity restores the one it saved
 * instead. Each item of [newRequests] replaces the back stack, as a cold launch from a tile does.
 * Swiping right dismisses the top screen. Each screen's view models live as long as its entry; the
 * recorder and Mark are resolved here, outside every entry, so they belong to the activity, and the
 * permissions they ask for are requested here. While recording, the app stays on screen in ambient
 * mode where the watch supports it.
 */
@Composable
fun WearAppV2(
    initialRequest: WearLaunchRequest?,
    newRequests: Flow<WearLaunchRequest>,
) {
    val recorder = koinViewModel<RecorderViewModel>()
    val marker = koinViewModel<MarkViewModel>()
    val recorderState by recorder.state.collectAsState()
    val backStack = rememberNavBackStack(*initialRequest.toV2BackStack().toTypedArray())
    LaunchedEffect(newRequests) {
        newRequests.collect { request ->
            backStack.clear()
            backStack.addAll(request.toV2BackStack())
        }
    }
    PermissionRequests(recorder, marker)
    WatchAmbientScope(enabled = recorderState is RecorderState.Live) {
        LogDateWearTheme {
            CompositionLocalProvider(LocalLogDateHaptics provides koinInject<LogDateHaptics>()) {
                AppScaffold(modifier = Modifier.testTag(WearAppV2Tags.SHELL)) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryDecorators =
                            listOf(
                                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                                rememberViewModelStoreNavEntryDecorator<NavKey>(),
                            ),
                        sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy<NavKey>()),
                        entryProvider =
                            entryProvider {
                                entry<WearRoute.Home> {
                                    HomeEntry(recorder, marker, onAdd = { backStack.add(WearRoute.CaptureSheet) })
                                }
                                entry<WearRoute.CaptureSheet> {
                                    CaptureSheetEntry(
                                        onOpenPhoneCamera = { backStack.add(WearRoute.PhoneCamera) },
                                        onDone = { backStack.removeLastOrNull() },
                                    )
                                }
                                entry<WearRoute.PhoneCamera> { PhoneCameraEntry(onDone = { backStack.removeLastOrNull() }) }
                            },
                    )
                }
            }
        }
    }
}

/** Asks for the permissions the recorder and Mark need at the moment they need them, and gives them the answers. */
@Composable
private fun PermissionRequests(
    recorder: RecorderViewModel,
    marker: MarkViewModel,
) {
    val requester =
        rememberPermissionRequester { permission, result ->
            recorder.onPermissionResult(permission, result)
            marker.onPermissionResult(permission)
        }
    LaunchedEffect(recorder, marker, requester) {
        merge(recorder.permissionRequests, marker.permissionRequests).collect { permission -> requester.request(permission) }
    }
}

@Composable
private fun HomeEntry(
    recorder: RecorderViewModel,
    marker: MarkViewModel,
    onAdd: () -> Unit,
) {
    val recorderState by recorder.state.collectAsState()
    val markFeedback by marker.feedback.collectAsState()
    val controls =
        remember(recorderState, recorder, marker) {
            TodayControls(
                recorder = TodayRecorder(recorderState, gesture = recorder, actions = recorder),
                onMark = marker::mark,
                onAdd = onAdd,
            )
        }
    HomeScreen(
        pagingLocked = recorderState.locksPaging,
        today = { listState -> TodayEntry(listState, controls) },
        flow = { listState -> FlowEntry(listState) },
    )
    MarkConfirmation(feedback = markFeedback, onDismiss = marker::dismissFeedback)
}

@Composable
private fun TodayEntry(
    listState: TransformingLazyColumnState,
    controls: TodayControls,
) {
    val viewModel = koinViewModel<TodayViewModel>()
    val state by viewModel.uiState.collectAsState()
    val handoff by viewModel.handoff.collectAsState()
    TodayPage(
        state = state,
        handoff = handoff,
        onGetPhoneApp = viewModel::getPhoneApp,
        onHandoffDismissed = viewModel::dismissHandoff,
        controls = controls,
        listState = listState,
    )
}

@Composable
private fun FlowEntry(listState: TransformingLazyColumnState) {
    val viewModel = koinViewModel<FlowViewModel>()
    val state by viewModel.uiState.collectAsState()
    FlowPage(state = state, listState = listState)
}

@Composable
private fun CaptureSheetEntry(
    onOpenPhoneCamera: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel = koinViewModel<CaptureSheetViewModel>()
    val state by viewModel.uiState.collectAsState()
    val write = rememberRemoteTextInput(onText = viewModel::saveText)
    CaptureSheet(
        state = state,
        onWrite = write,
        onToggleFeelings = viewModel::toggleFeelings,
        onFeeling = viewModel::saveFeeling,
        onPhoneCamera = onOpenPhoneCamera,
    )
    CaptureConfirmation(result = state.result, onSavedDismissed = onDone, onFailedDismissed = viewModel::dismissResult)
}

/**
 * The existing remote camera screen. It asks to leave by setting its state rather than calling back
 * after composition, so it leaves from an effect here instead of during composition.
 */
@Composable
private fun PhoneCameraEntry(onDone: () -> Unit) {
    val viewModel = koinViewModel<WearRemoteCameraViewModel>()
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.navigateBack) {
        if (state.navigateBack) onDone()
    }
    if (state.navigateBack) return
    WearRemoteCameraScreen(viewModel = viewModel, onNavigateBack = {})
}
```

- [ ] **Step 10: Run the unit tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`; `QuickNoteWriterTest` 5, `CaptureSheetViewModelTest` 5, and the whole watch suite, including `WearRecordingViewModelTest`'s 63, passes.

- [ ] **Step 11: Run the GMD tests**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.CaptureSheetTest,app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.ExperienceGateTest,app.logdate.wear.e2e.HomePagerRotaryTest,app.logdate.wear.e2e.TodayRecorderTest,app.logdate.wear.e2e.RecordGestureTest
```

Expected: `BUILD SUCCESSFUL`; `CaptureSheetTest` passes 6 tests.

- [ ] **Step 12: Record and check the sheet references**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest; ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/CaptureSheetScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: the first command FAILs listing only the 15 `CaptureSheetScreenshots` previews as missing; then `BUILD SUCCESSFUL`, `15`, and only the new directory in the status. Open `C01_Sheet_Small Round_*.png` ("Add", then Write and Mood), `C02_PhoneInReach_Small Round_*.png` (Phone camera below Mood, or reachable by scrolling), `C03_ChoosingAFeeling_Large Font_*.png` (the feelings with emoji and names, not clipped) and `C04_Saved_Small Round_*.png` ("Saved" with the success icon).

- [ ] **Step 13: Run every check once**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest :app:wear:ktlintCheck :app:wear:detekt :client:media:testAndroidHostTest :app:wear:assembleDebug :app:android-main:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 14: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task10-commit.txt <<'EOF'
chore(wear): add the + sheet for notes, moods and the phone camera

+ appears on Today in the redesigned watch app and opens a sheet.
Write opens the watch's own text input, by voice or keyboard, and
saves what was written as a note. Mood opens the five feelings and
saves the one tapped. Phone camera appears only while a phone running
LogDate is in reach, and opens the existing remote camera so the photo
is taken on the phone.

A save shows "Saved" and returns to Today; one that fails says so and
leaves the sheet open.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 15: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/capture/QuickNoteWriter.kt app/wear/src/main/kotlin/app/logdate/wear/v2/capture/RemoteTextInput.kt app/wear/src/main/kotlin/app/logdate/wear/v2/capture/CaptureSheetViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/v2/capture/CaptureSheet.kt app/wear/src/main/res/values/strings_capture.xml app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/CaptureSheetScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/CaptureSheetScreenshots app/wear/src/test/kotlin/app/logdate/wear/capture/QuickNoteWriterTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/capture/CaptureSheetViewModelTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/CaptureSheetTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task10-commit.txt
```

---

## Self-Review

**Spec coverage** (master plan Track B, B3 and B4; spec "The watch": "Recording", "Today and the moment list", "First run" permissions, "Tiles and complications" for Mark, and "Marked moments"):

| Requirement | Task |
|---|---|
| `AudioMomentSaver` and `AfterSaveController` pulled out of `WearRecordingViewModel`; its 63 tests green and unedited | 1 |
| `Modifier.recordGesture`: Initial-pass listener, yields to the pager before the long-press timeout, owns every event after it, tap vs hold vs lock by a 56 dp slide with `lockProgress`, a system cancel counts as a lift after the timeout, sliding sideways off the button does not end the recording (the `8959ec5ac` fix) | 2 |
| Pure gesture state machine tested phase by event; `RecordGestureTest` on the Wear GMD: tap, hold, lock, fling yields, no pager move after commit, sideways still recording, TalkBack, long-press timeout, system cancel, one accessibility control | 2 |
| TalkBack: "Start recording" as the click label and a custom action; one control with the right description in every state | 2, 5 |
| `Pressed` starts speculatively and `Yielded` discards silently, including while the start is still running | 4 |
| `RecorderViewModel` (Ready, Arming, Live(mode, paused, lockProgress), Saving, Saved, TooShort, Failed), activity-scoped, using `WearRecorder` and saving what is left when the activity finishes; startup recovery unchanged | 4 |
| Release saves, tap locks, Stop/Pause/Discard (two taps) when locked, five-second Undo, four seconds of feelings saved as a check-in | 4, 5 |
| Today: Record `EdgeButton` with the gesture and the shared record-to-Stop morph, the live recording (time, lock hint, level ring), saved/too-short/failed states with one helpful action each, localized literal copy | 5 |
| Paging locked while recording (Live only, never while arming, so a swipe still scrolls) | 4, 5 |
| Ongoing Activity through a new `RecordingNotificationDecorator` hook in `client/media`'s `AndroidAudioNotificationHandler`; `OngoingRecordingE2ETest` | 6 |
| Ambient mode via `rememberAmbientModeManager` at the root with a minute-tick timer and burn-in shifts | 7 |
| Contextual permissions: microphone at the first recording (and each time it is missing), notifications once at the first locked recording, heart rate once at the first mark, Settings when refused for good | 3, 4, 5, 9 |
| `MarkMomentUseCase` saving a `MarkedMoment` via `MarkedMomentDao`; place and an 8 s `MeasureClient` heart rate in the foreground, with a fallback when unavailable | 8, 9 |
| Mark receiver for a notification action, tile or complication, without opening the app; `HapticEvent.MomentMarked` | 9 |
| The + sheet: Write via RemoteInput (voice or keyboard), Mood, Phone camera only while the phone is in reach via `WearRoute.PhoneCamera` | 10 |
| Marked moments queued for backup: `EntityType.MOMENT` outbox entry and a `peer_delivery` row, no new server behavior | 8 |
| No dead controls: Record set in Task 5, Mark in Task 9, + in Task 10 | 5, 9, 10 |
| Screenshot baselines for every new state at small round, large round and a 1.24 font scale | 5, 7, 9, 10 |

Not covered, deliberately, each a later plan's: tiles and complications that fire `MarkMomentReceiver.pendingIntent` or open Record (B8), and the Record complication starting a recording on launch (`WearLaunchRequest.Record` and `RemoteCamera` still open Today); the "Add places to your moments?" prompt after the first save (places are read only when the permission and setting already allow); the Microphone row in the + sheet; delivering marks to the phone and the cloud (Track A D4 and D7) and showing them on the phone (Track C D2 and D3); the live recording on the phone (Track D). Nothing in this plan draws any of them.

**Placeholder scan:** every code step carries complete code; there is no "TBD", "similar to Task N" or undefined reference. Files edited more than once (`WearAppV2.kt` in Tasks 5, 7, 9, 10; `TodayPage.kt` in 5 and 7; `WearV2Module.kt` in 3, 4, 8, 9, 10; `AfterSaveController.kt` in 1 and 4; `QuickNoteWriter.kt` in 4 and 10; `WatchPermission.kt` in 3 and 9) are given whole or with exact before and after text each time.

**Type consistency:** `RecordGestureListener` (Task 2) is what `RecorderViewModel` implements (Task 4) and what `TodayRecorder.gesture` holds (Task 5). `RecorderState.canStartRecording` (Task 4) equals `recordButton().armed` for every state (`RecordButtonModelTest`, Task 5). `AfterSaveWindow(noteId, feelingsOffered, feeling)` from Task 4 is the shape `RecorderViewModel.observeAfterSave` copies into `RecorderState.Saved`. `FeelingSaver` (Task 4) and `TextNoteSaver` (Task 10) are both implemented by `QuickNoteWriter`. `PermissionResult` and `WatchPermission` (Task 3) are what `RecorderViewModel.onPermissionResult(permission, result)` and `MarkViewModel.onPermissionResult(permission)` take in `WearAppV2.PermissionRequests` (Task 9). `RecordingNotificationState(isRecording, startTimeMillis, recordedMillis)` is built by the handler (Task 6) and read by `ongoingStopwatch`. `MarkedMomentStore.create/update` (Task 8) are the only writes `MarkMomentUseCase` makes (Task 9). `MarkDelivery.*` (Task 8) is what `MarkMomentReceiverTest` reads back (Task 9).

**Risks an implementer should know:**
- Whether consuming the down on the Initial pass stops `EdgeButton`'s own click, and whether the pager's drag shows as consumed on the Final pass, are checked only on the device by `RecordGestureTest`; if either fails, report the output instead of loosening the test. If the speculative start flickers on real hardware (spec risk), start the recorder at the commit point instead: in `RecorderViewModel.onPressed`, record the press and call `beginStart` from `decide`.
- `rememberAmbientModeManager` cannot be exercised on the API 34 Wear GMD, which has no Wear SDK; the Wear OS 6 path needs the dogfood checklist on real hardware. The `AmbientLifecycleObserver` fallback was rejected because it can never turn always-on off.
- The Ongoing Activity needs the notifications permission; without it the system posts no notification, so the watch face shows nothing. Task 4 asks once, on the first locked recording.
- `MeasureClient` on the emulator usually reports no reading, so the heart rate path runs to its 8 s fallback there; the reading itself is verified on hardware.
- Adding `BODY_SENSORS` and `health.READ_HEART_RATE` changes the watch's Play permission declarations.
- The mark, its outbox entry and its ledger row are three writes without a transaction, the same as notes on Android: a crash between them leaves a mark not yet queued. Track A's router should requeue OPEN marks that have no ledger row.
- The reused remote camera screen (`presentation/camera`) still sets a hard-coded "Back camera" label in `RemoteCameraUiState`; it is shown in the new app from Task 10 and should be localized when that screen is next touched.
- Task 6 replaces `LogDateWearApplication.kt` whole; Step 10 says to diff first in case `onCreate` changed on main.
- Three GMD tests read `internal` declarations from `androidTest` (`WearSdkAmbient`, `LogDateWearApplication.newWatchApp`, `WRITTEN_TEXT_KEY`). Kotlin treats `androidTest` as a friend of `main`; if the build says otherwise, make those three public rather than duplicating them in the test.
- Renumbering: none needed, as no task adds a Room migration; if main has moved past Room 53, nothing here changes.
