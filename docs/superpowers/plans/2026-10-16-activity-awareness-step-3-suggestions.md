# Activity awareness, step 3: suggestions, voice first and the morning start — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a walk, ride or trip ends, Today on the watch and Home on the phone offer to add a moment from it. When the watch sees the person wake up, both show a morning start. While someone is moving, the watch's Today puts voice capture first.

**Architecture:** `ActivityPresenter` in `client/domain` turns the agreed activity and `SuggestionOutcomes` into one `ActivityUiState`. On the phone it feeds the existing Home recommendation pipeline, where `HomeRecommendation` becomes a `TimelineSuggestionBlock`, so no new surface is added. On the watch it adds one item to `TodayPage` and reorders its capture controls.

**Tech Stack:** Kotlin Multiplatform, Compose, Wear Compose Material 3 1.7, Koin.

**Spec:** `docs/superpowers/specs/2026-10-16-activity-awareness-design.md` ("What the app shows" and "Recording what people do with suggestions"). Build after step 2 so a suggestion opens capture with the activity already stamped; without step 2 the suggestion still works and stamps nothing.

## Global Constraints

- Behind `FeatureFlag.ACTIVITY_AWARENESS`; with it off, Today and Home are unchanged.
- One suggestion per activity. It goes away when the person adds a moment, dismisses it, starts another activity, or 2 hours pass after the activity ended.
- Only activities that lasted their kind's minimum count (`ActivityKind.minimum`).
- The morning start shows from waking until the first capture or 2 hours later, and only after a `SLEEP` activity the watch saw end.
- Copy: "Add a moment from the last 40 minutes" (moving), "Add a moment from your 25-minute trip" (vehicle), "Good morning" with "Start the day with a voice note" (morning start), and the actions "Add a moment" and "Dismiss". Durations are whole minutes. The app never speaks as "we".
- `SuggestionOutcomes` stays on the device, is never synced and keeps 30 days.
- The Home workspace contract (`CLAUDE.md`, "Home workspace visual contract") applies: the suggestion uses the existing `TimelineSuggestionBlock` styling inside the timeline panel, not a banner in the shared header.

## File structure

| File | Responsibility |
| --- | --- |
| Create `client/domain/.../activity/SuggestionOutcomes.kt` | Shown, acted on and dismissed, per activity; 30 days kept |
| Create `client/domain/.../activity/ActivityPresenter.kt` | `ActivityUiState`: voice first, the suggestion, the morning start |
| Modify `client/domain/.../recommendation/HomeRecommendation.kt` and its use case | `ActivityEnded` and `MorningStart` recommendations, ahead of the others |
| Modify `client/ui/.../timeline/TimelineSuggestionBlock.kt` | `AfterActivity` and `MorningStart` blocks with Add and Dismiss |
| Modify `client/feature/core/.../main/HomeTimelineMapping.kt`, `client/feature/timeline/.../TimelineViewModel.kt` | Map the new recommendations, and route Add and Dismiss |
| Modify `app/wear/.../v2/today/{TodayViewModel,TodayPage}.kt` | The suggestion item, the morning start item, and voice first |
| Modify `client/screenshot-scenes/...` and `app/wear` screenshot previews | Populated scenes for both cards |

---

### Task 1: `SuggestionOutcomes`

- [ ] **Step 1: Write the failing tests** (`client/domain/src/commonTest/.../activity/SuggestionOutcomesTest.kt`) with `FakeKeyValueStorage`: `wasShown(activity)` is false until `shown` is recorded; `dismissed` and `actedOn` are remembered per activity (keyed by kind and start time); entries older than 30 days are dropped when the next one is written.
- [ ] **Step 2: Run and confirm failure.** `./gradlew :client:domain:jvmTest --tests '*SuggestionOutcomes*'`
- [ ] **Step 3: Implement**:

```kotlin
enum class SuggestionKind { AFTER_ACTIVITY, MORNING_START }
enum class SuggestionResult { SHOWN, ACTED_ON, DISMISSED }

@Serializable
data class SuggestionOutcome(val kind: SuggestionKind, val activityKind: ActivityKind, val activityStartedAt: Instant, val result: SuggestionResult, val at: Instant)

class SuggestionOutcomes(private val storage: KeyValueStorage, private val clock: Clock = Clock.System) {
    val outcomes: Flow<List<SuggestionOutcome>>
    suspend fun record(kind: SuggestionKind, activity: Activity, result: SuggestionResult)
}
```

  Store the list as JSON under `activity.suggestions`.
- [ ] **Step 4: Run and confirm the tests pass.**
- [ ] **Step 5: Commit** with Task 2.

### Task 2: `ActivityPresenter`

- [ ] **Step 1: Write the failing tests** with a test clock: a 40-minute walk that ended 5 minutes ago gives `Suggestion(AFTER_ACTIVITY, minutes = 40)`; the same after `DISMISSED`, after `ACTED_ON` or 2 hours later gives none; a walk under way gives `voiceFirst = true` and no suggestion; a night's sleep that ended 10 minutes ago gives `morningStart = true` until a capture is recorded today or 2 hours pass; with the flag off everything is empty.
- [ ] **Step 2: Run and confirm failure.**
- [ ] **Step 3: Implement**:

```kotlin
data class ActivityUiState(
    val voiceFirst: Boolean = false,
    val suggestion: ActivitySuggestion? = null,
    val morningStart: Boolean = false,
)

data class ActivitySuggestion(val activity: Activity, val minutes: Int)

class ActivityPresenter(
    private val tracker: ActivityTracker,
    private val outcomes: SuggestionOutcomes,
    private val capturedSince: (Instant) -> Flow<Boolean>,  // any note saved since the instant
    private val clock: Clock = Clock.System,
) {
    val state: Flow<ActivityUiState>
    suspend fun dismiss()
    suspend fun actedOn()
}
```

  `state` re-evaluates every minute while a suggestion or the morning start is showing, so they expire on time. It records `SHOWN` the first time a suggestion appears.
- [ ] **Step 4: Run and confirm the tests pass**, plus `:client:domain:compileKotlinIosSimulatorArm64`.
- [ ] **Step 5: Commit** `chore(reminders): Offer a moment after an activity and at the start of the day`.

### Task 3: Home on the phone

- [ ] **Step 1: Write the failing tests**: the Home recommendation use case returns `HomeRecommendation.ActivityEnded` ahead of the others while the presenter has a suggestion, and `MorningStart` while it shows the morning start; `toTimelineSuggestionBlock` maps both; tapping Add a moment calls `actedOn` and opens the editor; tapping Dismiss calls `dismiss`. The editor has no entry-time control, so the moment is timed when it is written, right after the activity; step 2 attaches the activity itself.
- [ ] **Step 2: Run and confirm failure.** `./gradlew :client:domain:jvmTest :client:feature:core:desktopTest :client:feature:timeline:desktopTest`
- [ ] **Step 3: Implement** the two recommendations and blocks, reusing `TimelineSuggestionBlock`'s card, typography and actions.
- [ ] **Step 4: Add populated screenshot scenes** for both blocks in the Home timeline. Inspect them at compact phone, landscape, tablet, book and tabletop postures, dark mode, RTL, 200% text and reduced motion, as the Home workspace contract requires. CI records the references (`render-host.txt`).
- [ ] **Step 5: Run `./gradlew checkHomeWorkspaceContract` and commit** `feat(timeline): Suggest a moment after a walk or trip, behind a Labs switch`.

### Task 4: Today on the watch

- [ ] **Step 1: Write the failing tests** (`TodayViewModelTest`): `TodayUiState.Ready` carries the presenter's `ActivityUiState`. In `TodayPage` screenshot tests: the suggestion card sits after the summary, the morning start replaces the summary's empty-day line, and with `voiceFirst` the Record button gets the primary edge button while Mark and "+" move below it.
- [ ] **Step 2: Run and confirm failure.** `./gradlew :app:wear:testDebugUnitTest --tests '*Today*'`
- [ ] **Step 3: Implement** with Wear Compose Material 3 `TitleCard` for the suggestion and the existing `TodayButtonGroup`. Add opens the capture sheet with the activity's start time.
- [ ] **Step 4: Run the Wear emulator suite** (`./gradlew :app:wear:wearDevicesGroupDebugAndroidTest`) and the Wear screenshot validation, then inspect the round and large-round renders.
- [ ] **Step 5: Commit** `feat(wear): Suggest a moment after a walk and start the day by voice, behind a Labs switch`.

## Self-review

- **Spec coverage:** voice first, the suggestion card on both devices, the morning start, one suggestion per activity, expiry, and outcomes kept on the device for a later decision model.
- **Dependency:** step 2 for stamping the activity on the moment a suggestion creates. Without it, the suggestion only fills in the time.
- **Type names:** `SuggestionOutcomes`, `SuggestionKind`, `SuggestionResult`, `ActivityPresenter`, `ActivityUiState`, `ActivitySuggestion` are used consistently.
