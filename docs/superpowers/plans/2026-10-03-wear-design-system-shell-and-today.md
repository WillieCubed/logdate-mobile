# Watch Design System, Shell and Today Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Track B steps B1 and B2 of `docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md`: the shared design tokens, glyphs and haptic vocabulary, the watch theme, the Navigation 3 shell of the new watch app, and the Home pager with a working Today page, all dark behind `FeatureFlag.WATCH_EXPERIENCE_V2`.

**Architecture:** Shared, additive code lands in `client/ui` (motion and record-shape tokens, five glyphs, seven new haptic events and a non-Compose Android haptics factory) and `client/domain` (`TodaySummaryCalculator` and `MoodNoteTag`). Everything watch-side lives in a new `app.logdate.wear.v2` package that only `WearExperience.V2` reaches: a graphite theme with dynamic color, `WearAppV2` (`AppScaffold` + `NavDisplay` + `rememberSwipeDismissableSceneStrategy()`), a two-page `VerticalPagerScaffold` Home, and a Today page fed by `DayClock`, the shared calculator, the existing `ObserveCampfireUseCase`, `PhoneLinkRepository` and `PhoneHandoff`. Controls whose behavior lands in later plans (Record, Mark, +) exist only as optional handlers; with no handler they are not drawn.

**Tech Stack:** Kotlin 2.3.21, AGP 9.2.1 (built-in Kotlin), Wear Compose Material 3 / Foundation / Navigation 3 1.7.0, Navigation 3 1.1.1, `lifecycle-viewmodel-navigation3` 2.11.0, `wear-phone-interactions` 1.1.1, `wear-remote-interactions` 1.2.0, `graphics-shapes` 1.1.0, Compose Multiplatform resources, Koin 4.2.1, kotlinx.coroutines, kotlinx-datetime 0.7, kotlinx.serialization, JUnit 4 + kotlin.test + MockK + Turbine (unit), Compose UI test v2 rules on the `wearSmallRoundApi34` Gradle Managed Device, the Android screenshot plugin.

## Global Constraints

- Prerequisites (checked in Task 0): `:app:wear` `minSdk = 34`; `FeatureFlag.WATCH_EXPERIENCE_V2` exists; `WearExperienceGate`, `WearLaunchRequest` and `WearAppV2Placeholder` exist; Wear Compose 1.7.0, `compose-navigation3`, `lifecycle-viewmodel-navigation3` 2.11.0, `wear-phone-interactions`, `wear-remote-interactions` and `graphics-shapes` are already on `:app:wear`'s classpath.
- Flag: everything under `app/wear/src/main/kotlin/app/logdate/wear/v2/` is reachable only through `WearExperience.V2`. The v1 UI stays the default and is not edited, except `MomentNoteCodec` (Task 5, behavior unchanged) and `MainActivityContent` (Task 8).
- Shared code (`client/ui`, `client/domain`) is additive: no existing caller changes behavior, except that the phone's `rememberPlatformHapticsController()` builds its controller through the new factory (same object).
- `commonMain` changes compile for iOS: run `:client:ui:compileKotlinIosSimulatorArm64` and `:client:domain:compileKotlinIosSimulatorArm64` in the task that touches them.
- `WearHapticEngine` is untouched. The v2 tree gets its haptics through `LocalLogDateHaptics`; no v2 code calls `WearHapticEngine`.
- No dead controls: a control appears only when it works. Record, Mark and + are drawn only when `TodayControls` carries their handler; nothing in this plan sets one.
- Logging: Napier only (`io.github.aakira.napier.Napier`). Never `println`, `System.out` or `android.util.Log`.
- IDs: `kotlin.uuid.Uuid`. Routes carry IDs as `String`.
- Time: `kotlin.time.Clock` and `kotlin.time.Instant` (what `JournalNote` uses), `kotlinx.datetime` for dates and zones.
- Serialization: kotlinx.serialization (the plugin is already applied to `:app:wear`).
- DI: Koin constructor injection. New watch bindings go in `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`.
- Imports: ASCII order with `java.*`, `javax.*`, `kotlin.*` last, no wildcards (the layout every file in `:app:wear` and `client/ui` uses); `:app:wear:ktlintCheck` lints sources, and `app/wear/.editorconfig` allows PascalCase `@Composable` names.
- Code shape: early returns, no comments that restate the code, detekt size limits (`LongMethod` 60 lines outside `@Composable`), nothing added to `app/wear/detekt-baseline.xml`. ktlint max line length 140.
- Copy: literal, never "we", from string resources (`app/wear/src/main/res/values/strings_home.xml`), times and dates in the user's locale, and no vague metaphor verbs ("kept", "held", "carried", "forming") in copy, names or KDoc.
- Gradle: every command starts with `export ANDROID_HOME=$HOME/Library/Android/sdk &&` and runs from `/Users/williecubed/Projects/TheHypertextStudio/logdate-mobile/.claude/worktrees/backup-sync-infinite-notification-dd4c3a`.
- Devices: Gradle Managed Device only. The watch task is `:app:wear:wearDevicesGroupDebugAndroidTest` (device `wearSmallRoundApi34`); one class with `-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>`, several comma-separated. Never `connected*AndroidTest`, `install*` or `adb`. A run that prints `Starting 0 tests` then `Process crashed` while `uptime` shows a load average above 100 is host load; rerun it.
- Screenshots: references live in `app/wear/src/screenshotTestDebug/reference/` and change only in the commit whose code changes their pixels. Previews use `LogDateWearTheme(dynamicColor = false)`, because screenshots cannot render dynamic color.
- Commits: the message file is written with a heredoc in its own step to `/private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/<name>.txt`; the next step runs `git restore --staged . && git add <exact paths> && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F <file>` as one chain. Never `git add -A`, `git add .`, `--no-verify` or `-m`.
- Commit messages: Conventional Commits, title at most 72 characters, body lines at most 72 characters, type `chore` (the work is dark behind the flag; never `style`), scope `ui` or `wear` (never `build`, `android`, `ios`, `desktop` or `infra`), no "phase" followed by a number, and `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` as the last line.

---

## File Structure

### Shared (`client/ui`, `client/domain`)

| Path | Task | Responsibility |
|---|---|---|
| `client/ui/src/commonMain/kotlin/app/logdate/ui/tokens/LogDateMotion.kt` | 1 | The three springs both apps move with. |
| `client/ui/src/commonMain/kotlin/app/logdate/ui/tokens/RecordShape.kt` | 1 | The record button's circle-to-stop morph and its outline as a Compose `Path`. |
| `client/ui/src/commonMain/kotlin/app/logdate/ui/glyphs/LogDateGlyph.kt` | 1 | The five glyphs (watch, mark, record, lock, campfire) and their vectors. |
| `client/ui/src/commonMain/composeResources/drawable/glyph_*.xml` | 1 | The glyph vectors (five files). |
| `client/ui/src/commonMain/kotlin/app/logdate/ui/platform/{HapticEvent,HapticEventToTokens,LogDateHaptics}.kt` | 2 | Seven new events, their primitives, and the facade methods. |
| `client/ui/src/androidMain/kotlin/app/logdate/ui/platform/AndroidHaptics.kt` | 2 | Builds the vibrator controller without Compose; reads the system haptics switch. |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/today/MoodNoteTag.kt` | 5 | Reads the `#mood:` tag a check-in note starts with. |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/today/TodaySummary.kt` | 5 | `TodaySummary` and `TodaySummaryCalculator` (moments, places, voice time, latest moment, feeling). |

### Watch (`app/wear/src/main/kotlin/app/logdate/wear/`)

| Path | Task | Responsibility |
|---|---|---|
| `res/drawable/ic_glyph_*.xml` (under `app/wear/src/main/`) | 1 | White copies of the five glyphs for tiles and complications. |
| `v2/haptics/HapticsPreference.kt` | 3 | `HapticsPreference` (All, Essential) and its SharedPreferences store. |
| `v2/haptics/WatchHaptics.kt` | 3 | `watchHaptics(...)`: the shared facade gated by the preference and the system switch. |
| `di/WearV2Module.kt` | 3, 7, 9 | Koin bindings of the new watch app. |
| `v2/theme/GraphiteColorScheme.kt` | 4 | The neutral fallback color scheme. |
| `v2/theme/LogDateWearTheme.kt` | 4 | Dynamic color on Wear OS 6, graphite otherwise. |
| `v2/today/DayClock.kt` | 5 | `TodayWindow` and the clock that moves it at midnight and on time zone changes. |
| `v2/today/TimeChangeSignals.kt` | 5 | System time, date and time zone broadcasts as a `Flow<Unit>`. |
| `v2/today/WatchCampfire.kt` | 6 | `WatchStreakSettings` and `WatchDayBoundaryPreferences`, the adapters `ObserveCampfireUseCase` needs on the watch. |
| `v2/phone/PhoneLink.kt` | 6 | `PhoneLink` and `PhoneLinkRepository` (capability `logdate_phone_app` + `PhoneTypeHelper`). |
| `v2/phone/PhoneHandoff.kt` | 6 | Opens a link on the phone with `RemoteActivityHelper`. |
| `v2/today/TodayUiState.kt` | 7 | `TodayUiState`, `PhoneCard`, `HandoffState`. |
| `v2/today/TodayViewModel.kt` | 7 | Combines day, notes, campfire and phone link into `TodayUiState`. |
| `v2/today/CampfireMapping.kt` | 7 | `CampfireState.toPresentation()`. |
| `v2/today/TodayPage.kt` | 7, 9 | The Today page and `TodayControls`. |
| `v2/moments/ClockDuration.kt` | 7 | `formatClockDuration` ("4:12", "1:04:12"). |
| `v2/moments/MomentText.kt` | 7, 9 | A moment's label and its time in the user's locale. |
| `res/values/strings_home.xml` (under `app/wear/src/main/`) | 7, 9 | Copy for Today, moments and the Flow page. |
| `v2/navigation/WearRoute.kt` | 8 | Serializable routes: Home, Moment(id), CaptureSheet, PlayOn, Archive, PhoneCamera. |
| `v2/navigation/WearLaunchRoutes.kt` | 8 | `WearLaunchRequest?.toV2BackStack()`. |
| `v2/WearAppV2.kt` | 8, 9 | The shell. Replaces `v2/WearAppV2Placeholder.kt`, which Task 8 deletes. |
| `v2/recorder/RecorderViewModel.kt` | 9 | Activity-scoped recorder state (`Ready` only until the recorder lands). |
| `v2/home/HomePaging.kt` | 9 | `HomePage` and `pageForCrown(...)`. |
| `v2/home/HomeScreen.kt` | 9 | The `VerticalPagerScaffold` with Today and Flow. |
| `v2/flow/FlowViewModel.kt` | 9 | `FlowUiState` and the last 7 days of moments. |
| `v2/flow/FlowPage.kt` | 9 | The read-only Flow page. |

Modified: `client/ui/build.gradle.kts` (Task 1), `client/ui/src/androidMain/.../PlatformHaptics.android.kt` (Task 2), `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt` (Task 3), `app/wear/src/main/kotlin/app/logdate/wear/capture/MomentNoteCodec.kt` (Task 5), `app/wear/build.gradle.kts` (Task 6), `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivityContent.kt` and `app/wear/src/main/res/values/strings.xml` (Task 8), `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ExperienceGateTest.kt` (Task 8).

Tests: `client/ui/src/commonTest/kotlin/app/logdate/ui/{tokens,platform}/`, `client/ui/src/desktopTest/kotlin/app/logdate/ui/tokens/`, `client/domain/src/commonTest/kotlin/app/logdate/client/domain/today/`, `app/wear/src/test/kotlin/app/logdate/wear/{glyphs,v2/...}/`, `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/`, `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/`.

## Verified API surface

context7 returned "Monthly quota exceeded" for both the CLI and the MCP server while this plan was written (run `npx ctx7@latest login` or set `CONTEXT7_API_KEY` to restore it). Every signature below was read instead from the exact artifacts Gradle resolved, with `javap` on the cached AARs under `~/.gradle/caches/modules-2/files-2.1/` and the parameter names from their Kotlin metadata:

- `androidx.wear.compose.material3.AppScaffold(modifier, timeText, containerColor, contentColor, content: BoxScope.() -> Unit)`.
- `ScreenScaffold(scrollState: TransformingLazyColumnState, edgeButton: BoxScope.() -> Unit, modifier, contentPadding, timeText, scrollIndicator, edgeButtonSpacing, overscrollEffect, content: BoxScope.(PaddingValues) -> Unit)` and the same without `edgeButton`/`edgeButtonSpacing`.
- `VerticalPagerScaffold(pagerState, modifier, pageIndicator, pageIndicatorAnimationSpec, content: () -> Unit)`; `AnimatedPage(pageIndex, pagerState, contentScrimColor, content)`; `androidx.wear.compose.foundation.pager.VerticalPager(state, modifier, contentPadding, beyondViewportPageCount, flingBehavior, userScrollEnabled, reverseLayout, key, rotaryScrollableBehavior, content: PagerScope.(Int) -> Unit)`; `rememberPagerState(initialPage, initialPageOffsetFraction, pageCount: () -> Int)`; `PagerState` implements `ScrollableState` and has `animateScrollToPage`.
- `EdgeButton(onClick, modifier, buttonSize: EdgeButtonSize, enabled, colors, border, interactionSource, content: RowScope.() -> Unit)`; `EdgeButtonSize.{ExtraSmall, Small, Medium, Large}`.
- `ButtonGroup(modifier, spacing, expansionWidth, contentPadding, verticalAlignment, content: ButtonGroupScope.() -> Unit)`; `ButtonGroupScope` has `Modifier.weight(Float)`, `minWidth`, `animateWidth`.
- `TransformingLazyColumn(modifier, state, contentPadding, verticalArrangement, horizontalAlignment, flingBehavior, userScrollEnabled, rotaryScrollableBehavior, overscrollEffect, content)`; `rememberTransformingLazyColumnState()`; `TransformingLazyColumnState` implements `ScrollableState` (`canScrollBackward`, `canScrollForward`); `androidx.wear.compose.material3.lazy.rememberTransformationSpec()`, `Modifier.transformedHeight(scope, spec)`, `SurfaceTransformation(spec)`.
- `dynamicColorScheme(context: Context): ColorScheme?` returns null when the system has no dynamic color. `ColorScheme(...)` takes 29 named colors from `primary` to `onErrorContainer`, each with a default.
- `TimeText(modifier, curvedModifier, maxSweepAngle, backgroundColor, timeSource, contentPadding, content: CurvedScope.(String) -> Unit)`; `timeTextCurvedText(text, style = default)`; `timeTextSeparator(style = default, contentArcPadding = default)`; `TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat())`; `TimeSource` has one `@Composable currentTime(): String`.
- `OpenOnPhoneDialog(visible, onDismissRequest, curvedText: CurvedScope.() -> Unit, modifier, colors, properties, durationMillis, content)`; `openOnPhoneDialogCurvedText(text, style)` has no default style, so it takes `OpenOnPhoneDialogDefaults.curvedTextStyle`; `OpenOnPhoneDialogDefaults.text` is the localized "Continue on phone".
- `TitleCard(onClick, title: RowScope.() -> Unit, modifier, onLongClick, onLongClickLabel, time, subtitle, enabled, shape, colors, border, contentPadding, interactionSource, transformation, content)`; `FilledTonalButton(onClick, modifier, ..., transformation, content: RowScope.() -> Unit)`; `ListHeader(modifier, backgroundColor, contentColor, contentPadding, transformation, content)`.
- `androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy<T>(isUserSwipeEnabled = true)`.
- `androidx.navigation3.ui.NavDisplay(backStack, modifier, contentAlignment, onBack, entryDecorators, sceneStrategy, sizeTransform, transitionSpec, popTransitionSpec, predictivePopTransitionSpec, entryProvider)`; `androidx.navigation3.runtime.rememberNavBackStack(vararg elements: NavKey): NavBackStack<NavKey>`; `rememberSaveableStateHolderNavEntryDecorator<T>()`.
- `androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator<T>()` (defaults: the current `ViewModelStoreOwner`, remove the store on pop).
- `androidx.wear.phone.interactions.PhoneTypeHelper.getPhoneDeviceType(context): Int` with `DEVICE_TYPE_ERROR = 0`, `DEVICE_TYPE_ANDROID = 1`, `DEVICE_TYPE_IOS = 2`, `DEVICE_TYPE_UNKNOWN = 3`, `DEVICE_TYPE_NONE = 4`.
- `androidx.wear.remote.interactions.RemoteActivityHelper(context)` and `startRemoteActivity(targetIntent, targetNodeId): ListenableFuture<Void>`; it accepts only `ACTION_VIEW` + `CATEGORY_BROWSABLE` + a data URI (as `app/compose-main/src/androidMain/kotlin/app/logdate/client/watch/AndroidWatchConnectionManager.kt:41-44` and `:100-103` already rely on).
- `androidx.graphics.shapes`: `RoundedPolygon.circle(numVertices, radius, centerX, centerY)`, `RoundedPolygon.rectangle(width, height, rounding, perVertexRounding, centerX, centerY)`, `CornerRounding(radius, smoothing)`, `Morph(start, end)`, `Morph.asCubics(progress)`, `Morph.forEachCubic(progress, callback)`, `Cubic.anchor0X … anchor1Y`. Version 1.1.0 is published for Android, desktop (JVM), `iosArm64` and `iosSimulatorArm64`.
- None of the APIs above carries an opt-in marker in 1.7.0.

---

### Task 0: Confirm the prerequisites and record a baseline

**Files:** none changed.

**Interfaces:**
- Consumes: the groundwork and Phase 0 commits on this branch.
- Produces: confidence that every later task starts from green.

- [ ] **Step 1: Check the prerequisites**

```bash
grep -n "minSdk = 34" app/wear/build.gradle.kts
grep -n "WATCH_EXPERIENCE_V2" client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/featureflags/FeatureFlag.kt
grep -n 'wearCompose = "1.7.0"\|lifecycleViewmodelNav3 = "2.11.0"\|graphicsShapes = "1.1.0"' gradle/libs.versions.toml
grep -n "androidx.wear.compose.navigation3\|androidx.lifecycle.viewmodel.navigation3\|androidx.wear.phone.interactions\|wear.remote.interactions\|androidx.graphics.shapes" app/wear/build.gradle.kts
ls app/wear/src/main/kotlin/app/logdate/wear/launch/WearExperienceGate.kt app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2Placeholder.kt
```

Expected: one line for each of the first two commands, three lines for the third, five for the fourth, and both files listed. If anything is missing, stop: this plan builds on those commits.

- [ ] **Step 2: Check the tree**

```bash
git status --short && git log --oneline -3
```

Expected: no output from `git status`; the log ends at or after `17490e104 chore(wear): delete a note's data item once the phone has it`.

- [ ] **Step 3: Run the unit, common and screenshot tests once**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:ui:desktopTest :client:ui:testAndroidHostTest :client:domain:jvmTest :app:wear:testDebugUnitTest :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`. If screenshot validation fails here, stop and report: the baselines are already stale.

- [ ] **Step 4: Run the GMD tests this plan changes**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ExperienceGateTest,app.logdate.wear.e2e.LaunchRequestRoutingTest
```

Expected: `BUILD SUCCESSFUL`, 4 tests passed.

---
### Task 1: Shared motion and shape tokens and glyphs (`chore(ui)`)

Both apps need the same springs, the same record button morph and the same five glyphs. Tiles and complications cannot tint a colored vector reliably, so the watch ships white copies of the glyphs in its own `res/drawable`; a unit test fails as soon as a copy's shapes drift from the shared original.

**Files:**
- Modify: `client/ui/build.gradle.kts:84-85` (add `graphics-shapes` to `commonMain`)
- Create: `client/ui/src/commonMain/kotlin/app/logdate/ui/tokens/LogDateMotion.kt`
- Create: `client/ui/src/commonMain/kotlin/app/logdate/ui/tokens/RecordShape.kt`
- Create: `client/ui/src/commonMain/kotlin/app/logdate/ui/glyphs/LogDateGlyph.kt`
- Create: `client/ui/src/commonMain/composeResources/drawable/glyph_watch.xml`, `glyph_mark.xml`, `glyph_record.xml`, `glyph_lock.xml`, `glyph_campfire.xml`
- Create: `app/wear/src/main/res/drawable/ic_glyph_watch.xml`, `ic_glyph_mark.xml`, `ic_glyph_record.xml`, `ic_glyph_lock.xml`, `ic_glyph_campfire.xml`
- Test: `client/ui/src/commonTest/kotlin/app/logdate/ui/tokens/LogDateMotionTest.kt`
- Test: `client/ui/src/commonTest/kotlin/app/logdate/ui/tokens/RecordShapeTest.kt`
- Test: `client/ui/src/desktopTest/kotlin/app/logdate/ui/tokens/RecordShapeOutlineTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/glyphs/GlyphSetSyncTest.kt`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces:
  - `object LogDateMotion { fun <T> standard(): SpringSpec<T>; fun <T> quick(): SpringSpec<T>; fun <T> expressive(): SpringSpec<T> }` in `app.logdate.ui.tokens`.
  - `object RecordShape { const val STOP_SIDE: Float; const val STOP_CORNER: Float; val record: RoundedPolygon; val stop: RoundedPolygon; val morph: Morph; fun outline(progress: Float, size: Float, path: Path = Path()): Path }` in `app.logdate.ui.tokens`.
  - `enum class LogDateGlyph(val fileName: String) { WATCH, MARK, RECORD, LOCK, CAMPFIRE }`, `val LogDateGlyph.resource: DrawableResource` and `@Composable fun LogDateGlyph.vector(): ImageVector` in `app.logdate.ui.glyphs`.
  - Watch drawables `R.drawable.ic_glyph_watch`, `ic_glyph_mark`, `ic_glyph_record`, `ic_glyph_lock`, `ic_glyph_campfire`.

- [ ] **Step 1: Write the failing motion test**

Create `client/ui/src/commonTest/kotlin/app/logdate/ui/tokens/LogDateMotionTest.kt`:

```kotlin
package app.logdate.ui.tokens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the springs both apps move with. A change here changes how every LogDate surface feels, so
 * it should be a deliberate one.
 */
class LogDateMotionTest {
    @Test
    fun `standard barely overshoots at a medium-low stiffness`() {
        val spec = LogDateMotion.standard<Float>()

        assertEquals(0.9f, spec.dampingRatio)
        assertEquals(400f, spec.stiffness)
    }

    @Test
    fun `quick never overshoots and is stiffer than standard`() {
        val quick = LogDateMotion.quick<Float>()

        assertEquals(1f, quick.dampingRatio)
        assertTrue(quick.stiffness > LogDateMotion.standard<Float>().stiffness)
    }

    @Test
    fun `expressive is the only spring with a visible bounce`() {
        val expressive = LogDateMotion.expressive<Float>()

        assertEquals(0.75f, expressive.dampingRatio)
        assertTrue(expressive.dampingRatio < LogDateMotion.standard<Float>().dampingRatio)
    }
}
```

- [ ] **Step 2: Write the failing record shape test**

Create `client/ui/src/commonTest/kotlin/app/logdate/ui/tokens/RecordShapeTest.kt`:

```kotlin
package app.logdate.ui.tokens

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the record button's morph in shape units: a circle of radius 1 at progress 0, a rounded
 * square [RecordShape.STOP_SIDE] wide at progress 1.
 */
class RecordShapeTest {
    @Test
    fun `the record shape is a circle of radius 1`() {
        anchors(progress = 0f).forEach { (x, y) ->
            assertEquals(1f, sqrt(x * x + y * y), TOLERANCE)
        }
    }

    @Test
    fun `the stop shape is a square STOP_SIDE wide`() {
        val anchors = anchors(progress = 1f)

        assertEquals(RecordShape.STOP_SIDE / 2, anchors.maxOf { abs(it.first) }, TOLERANCE)
        assertEquals(RecordShape.STOP_SIDE / 2, anchors.maxOf { abs(it.second) }, TOLERANCE)
    }

    @Test
    fun `halfway the button is between the two shapes`() {
        val extent = anchors(progress = 0.5f).maxOf { abs(it.first) }

        assertTrue(extent < 1f && extent > RecordShape.STOP_SIDE / 2, "extent was $extent")
    }

    private fun anchors(progress: Float): List<Pair<Float, Float>> =
        RecordShape.morph.asCubics(progress).map { cubic -> cubic.anchor0X to cubic.anchor0Y }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
```

- [ ] **Step 3: Write the failing outline test**

Create `client/ui/src/desktopTest/kotlin/app/logdate/ui/tokens/RecordShapeOutlineTest.kt`:

```kotlin
package app.logdate.ui.tokens

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Draws the morph at a real size. Path bounds include curve control points, so the circle is only
 * checked for being centered; the stop square has straight edges and is checked exactly.
 */
class RecordShapeOutlineTest {
    @Test
    fun `the circle is centered in the size it is drawn at`() {
        val bounds = RecordShape.outline(progress = 0f, size = 100f).getBounds()

        assertEquals(50f, bounds.center.x, 0.5f)
        assertEquals(50f, bounds.center.y, 0.5f)
    }

    @Test
    fun `the stop square fills the middle 60 percent`() {
        val bounds = RecordShape.outline(progress = 1f, size = 100f).getBounds()

        assertEquals(20f, bounds.left, 0.5f)
        assertEquals(20f, bounds.top, 0.5f)
        assertEquals(80f, bounds.right, 0.5f)
        assertEquals(80f, bounds.bottom, 0.5f)
    }
}
```

- [ ] **Step 4: Write the failing glyph sync test**

Create `app/wear/src/test/kotlin/app/logdate/wear/glyphs/GlyphSetSyncTest.kt`:

```kotlin
package app.logdate.wear.glyphs

import app.logdate.ui.glyphs.LogDateGlyph
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks that the watch's white glyph copies, which tiles and complications draw, match the shapes
 * of the shared glyphs in `client/ui`. Unit tests run with `app/wear` as the working directory.
 */
class GlyphSetSyncTest {
    private val sharedDirectory = File("../../client/ui/src/commonMain/composeResources/drawable")
    private val watchDirectory = File("src/main/res/drawable")

    @Test
    fun `every shared glyph has a copy on the watch`() {
        LogDateGlyph.entries.forEach { glyph ->
            assertTrue(watchCopy(glyph).isFile, "Missing ${watchCopy(glyph).path} for ${glyph.name}")
        }
    }

    @Test
    fun `the watch has no glyph copy without a shared original`() {
        val expected = LogDateGlyph.entries.map { glyph -> "ic_${glyph.fileName}.xml" }.toSet()
        val copies =
            watchDirectory
                .listFiles()
                .orEmpty()
                .map(File::getName)
                .filter { name -> name.startsWith("ic_glyph_") }
                .toSet()

        assertEquals(expected, copies)
    }

    @Test
    fun `each copy draws the same shapes as its original`() {
        LogDateGlyph.entries.forEach { glyph ->
            val original = parse(File(sharedDirectory, "${glyph.fileName}.xml"))
            val copy = parse(watchCopy(glyph))

            assertEquals(original.viewport, copy.viewport, "${glyph.name} viewport")
            assertEquals(original.paths.map(GlyphPath::geometry), copy.paths.map(GlyphPath::geometry), "${glyph.name} paths")
        }
    }

    @Test
    fun `each copy is white so a tile or complication can tint it`() {
        LogDateGlyph.entries.forEach { glyph ->
            parse(watchCopy(glyph)).paths.forEach { path ->
                assertEquals(MONOCHROME, path.fillColor, "${glyph.name} fill")
            }
        }
    }

    private fun watchCopy(glyph: LogDateGlyph) = File(watchDirectory, "ic_${glyph.fileName}.xml")

    private data class Glyph(
        val viewport: Pair<String, String>,
        val paths: List<GlyphPath>,
    )

    private data class GlyphPath(
        val geometry: Pair<String, String>,
        val fillColor: String,
    )

    private fun parse(file: File): Glyph {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val paths = root.getElementsByTagName("path")
        return Glyph(
            viewport = root.getAttribute("android:viewportWidth") to root.getAttribute("android:viewportHeight"),
            paths =
                (0 until paths.length).map { index ->
                    val path = paths.item(index) as Element
                    GlyphPath(
                        geometry = path.getAttribute("android:pathData") to path.getAttribute("android:fillType"),
                        fillColor = path.getAttribute("android:fillColor"),
                    )
                },
        )
    }

    private companion object {
        const val MONOCHROME = "#FFFFFFFF"
    }
}
```

- [ ] **Step 5: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:ui:desktopTest --tests 'app.logdate.ui.tokens.*'
```

Expected: FAIL at `:client:ui:compileTestKotlinDesktop` with `Unresolved reference 'LogDateMotion'` and `Unresolved reference 'RecordShape'`.

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.glyphs.GlyphSetSyncTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'glyphs'`.

- [ ] **Step 6: Add `graphics-shapes` to `client/ui`**

In `client/ui/build.gradle.kts`, inside `commonMain.dependencies`, after line 85 (`implementation(libs.multiplatform.markdown.renderer)`), add:

```kotlin
            implementation(libs.androidx.graphics.shapes)
```

- [ ] **Step 7: Add the motion tokens**

Create `client/ui/src/commonMain/kotlin/app/logdate/ui/tokens/LogDateMotion.kt`:

```kotlin
package app.logdate.ui.tokens

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * The springs every LogDate surface moves with, on the phone and on the watch.
 *
 * A spring starts from the velocity it already has, so a control that follows a finger, such as
 * the record button sliding up to lock, never jumps when the finger lets go. Use [standard] for
 * anything that changes place or size, [quick] for small state changes such as a press, and
 * [expressive] only for the moment a screen confirms something, such as a recording locking.
 */
object LogDateMotion {
    private const val STANDARD_DAMPING = 0.9f
    private const val STANDARD_STIFFNESS = 400f
    private const val QUICK_DAMPING = 1f
    private const val QUICK_STIFFNESS = 1_500f
    private const val EXPRESSIVE_DAMPING = 0.75f
    private const val EXPRESSIVE_STIFFNESS = 400f

    fun <T> standard(): SpringSpec<T> = spring(dampingRatio = STANDARD_DAMPING, stiffness = STANDARD_STIFFNESS)

    fun <T> quick(): SpringSpec<T> = spring(dampingRatio = QUICK_DAMPING, stiffness = QUICK_STIFFNESS)

    fun <T> expressive(): SpringSpec<T> = spring(dampingRatio = EXPRESSIVE_DAMPING, stiffness = EXPRESSIVE_STIFFNESS)
}
```

- [ ] **Step 8: Add the record shape**

Create `client/ui/src/commonMain/kotlin/app/logdate/ui/tokens/RecordShape.kt`:

```kotlin
package app.logdate.ui.tokens

import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle

/**
 * The record button's change from a circle, ready to record, to a rounded square, Stop. The phone
 * and the watch draw the same morph so the button looks the same on both.
 *
 * Both shapes are centered on the origin in shape units: the circle has radius 1, the square is
 * [STOP_SIDE] wide with corners of radius [STOP_CORNER]. Progress 0 is the circle, 1 the square.
 */
object RecordShape {
    const val STOP_SIDE = 1.2f
    const val STOP_CORNER = 0.3f
    private const val CIRCLE_VERTICES = 8

    val record: RoundedPolygon = RoundedPolygon.circle(numVertices = CIRCLE_VERTICES)

    val stop: RoundedPolygon =
        RoundedPolygon.rectangle(width = STOP_SIDE, height = STOP_SIDE, rounding = CornerRounding(radius = STOP_CORNER))

    val morph: Morph = Morph(start = record, end = stop)

    /**
     * The morph's outline at [progress], scaled so the circle fills a [size] by [size] box from the
     * origin. Reuses [path] so a button can redraw every frame without allocating.
     */
    fun outline(
        progress: Float,
        size: Float,
        path: Path = Path(),
    ): Path {
        path.rewind()
        val half = size / 2f
        var started = false
        morph.forEachCubic(progress.coerceIn(0f, 1f)) { cubic ->
            if (!started) {
                path.moveTo(half + cubic.anchor0X * half, half + cubic.anchor0Y * half)
                started = true
            }
            path.cubicTo(
                half + cubic.control0X * half,
                half + cubic.control0Y * half,
                half + cubic.control1X * half,
                half + cubic.control1Y * half,
                half + cubic.anchor1X * half,
                half + cubic.anchor1Y * half,
            )
        }
        path.close()
        return path
    }
}
```

- [ ] **Step 9: Add the shared glyph vectors**

Create `client/ui/src/commonMain/composeResources/drawable/glyph_watch.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:fillType="evenOdd"
        android:pathData="M8,2h8l1.1,3.6A7.5,7.5 0,0 1,17.1 18.4L16,22H8l-1.1,-3.6A7.5,7.5 0,0 1,6.9 5.6Z M12,7.5a4.5,4.5 0,1 0,0 9a4.5,4.5 0,1 0,0 -9Z" />
</vector>
```

Create `client/ui/src/commonMain/composeResources/drawable/glyph_mark.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:fillType="nonZero"
        android:pathData="M5,3h2v18H5Z M8,4h11l-3,4.5l3,4.5H8Z" />
</vector>
```

Create `client/ui/src/commonMain/composeResources/drawable/glyph_record.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:fillType="nonZero"
        android:pathData="M12,4a8,8 0,1 0,0 16a8,8 0,1 0,0 -16Z" />
</vector>
```

Create `client/ui/src/commonMain/composeResources/drawable/glyph_lock.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:fillType="evenOdd"
        android:pathData="M7,10V8a5,5 0,0 1,10 0v2h1a1,1 0,0 1,1 1v9a1,1 0,0 1,-1 1H6a1,1 0,0 1,-1 -1v-9a1,1 0,0 1,1 -1Z M9,10h6V8a3,3 0,0 0,-6 0Z M12,14a1.5,1.5 0,1 0,0 3a1.5,1.5 0,1 0,0 -3Z" />
</vector>
```

Create `client/ui/src/commonMain/composeResources/drawable/glyph_campfire.xml`. The flame is the only orange in either app's palette:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF8D6E63"
        android:fillType="nonZero"
        android:pathData="M3.5,19.2l16,-3.4l0.6,2.1l-16,3.4Z M20.5,19.2l-16,-3.4l-0.6,2.1l16,3.4Z" />
    <path
        android:fillColor="#FFFF8A3D"
        android:fillType="nonZero"
        android:pathData="M12,2.5c2.6,3.1 5,5.4 5,8.8a5,5 0,0 1,-10 0c0,-2.1 1,-3.6 2.2,-4.7c0.2,1.7 1,2.7 2,3.1c-0.6,-2.5 0,-4.7 0.8,-7.2Z" />
</vector>
```

- [ ] **Step 10: Add the watch's white copies**

Each copy is its original with every `android:fillColor` set to `#FFFFFFFF`.

Create `app/wear/src/main/res/drawable/ic_glyph_watch.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="M8,2h8l1.1,3.6A7.5,7.5 0,0 1,17.1 18.4L16,22H8l-1.1,-3.6A7.5,7.5 0,0 1,6.9 5.6Z M12,7.5a4.5,4.5 0,1 0,0 9a4.5,4.5 0,1 0,0 -9Z" />
</vector>
```

Create `app/wear/src/main/res/drawable/ic_glyph_mark.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="nonZero"
        android:pathData="M5,3h2v18H5Z M8,4h11l-3,4.5l3,4.5H8Z" />
</vector>
```

Create `app/wear/src/main/res/drawable/ic_glyph_record.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="nonZero"
        android:pathData="M12,4a8,8 0,1 0,0 16a8,8 0,1 0,0 -16Z" />
</vector>
```

Create `app/wear/src/main/res/drawable/ic_glyph_lock.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="M7,10V8a5,5 0,0 1,10 0v2h1a1,1 0,0 1,1 1v9a1,1 0,0 1,-1 1H6a1,1 0,0 1,-1 -1v-9a1,1 0,0 1,1 -1Z M9,10h6V8a3,3 0,0 0,-6 0Z M12,14a1.5,1.5 0,1 0,0 3a1.5,1.5 0,1 0,0 -3Z" />
</vector>
```

Create `app/wear/src/main/res/drawable/ic_glyph_campfire.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="nonZero"
        android:pathData="M3.5,19.2l16,-3.4l0.6,2.1l-16,3.4Z M20.5,19.2l-16,-3.4l-0.6,2.1l16,3.4Z" />
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="nonZero"
        android:pathData="M12,2.5c2.6,3.1 5,5.4 5,8.8a5,5 0,0 1,-10 0c0,-2.1 1,-3.6 2.2,-4.7c0.2,1.7 1,2.7 2,3.1c-0.6,-2.5 0,-4.7 0.8,-7.2Z" />
</vector>
```

- [ ] **Step 11: Add the glyph catalog**

Create `client/ui/src/commonMain/kotlin/app/logdate/ui/glyphs/LogDateGlyph.kt`:

```kotlin
package app.logdate.ui.glyphs

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.glyph_campfire
import logdate.client.ui.generated.resources.glyph_lock
import logdate.client.ui.generated.resources.glyph_mark
import logdate.client.ui.generated.resources.glyph_record
import logdate.client.ui.generated.resources.glyph_watch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * The glyphs the phone and the watch share. Each is a 24 by 24 vector in `composeResources/drawable`
 * named [fileName]; the watch also ships a white copy, `ic_<fileName>`, for tiles and complications.
 */
enum class LogDateGlyph(
    val fileName: String,
) {
    /** A moment captured on the watch. */
    WATCH("glyph_watch"),

    /** A marked moment. */
    MARK("glyph_mark"),

    /** Start recording. */
    RECORD("glyph_record"),

    /** A recording locked so it continues without a finger on the button. */
    LOCK("glyph_lock"),

    /** The journaling campfire. Its flame is the only orange in the palette. */
    CAMPFIRE("glyph_campfire"),
}

val LogDateGlyph.resource: DrawableResource
    get() =
        when (this) {
            LogDateGlyph.WATCH -> Res.drawable.glyph_watch
            LogDateGlyph.MARK -> Res.drawable.glyph_mark
            LogDateGlyph.RECORD -> Res.drawable.glyph_record
            LogDateGlyph.LOCK -> Res.drawable.glyph_lock
            LogDateGlyph.CAMPFIRE -> Res.drawable.glyph_campfire
        }

@Composable
fun LogDateGlyph.vector(): ImageVector = vectorResource(resource)
```

- [ ] **Step 12: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:ui:desktopTest --tests 'app.logdate.ui.tokens.*' :client:ui:testAndroidHostTest --tests 'app.logdate.ui.tokens.*' :client:ui:compileKotlinIosSimulatorArm64 :app:wear:testDebugUnitTest --tests 'app.logdate.wear.glyphs.GlyphSetSyncTest' :client:ui:ktlintCheck :app:wear:ktlintCheck
```

Expected: `BUILD SUCCESSFUL`; desktop runs 8 tests, the Android host runs the 6 common ones, the watch runs 4.

- [ ] **Step 13: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task1-commit.txt <<'EOF'
chore(ui): add shared motion and record shape tokens and glyphs

The redesigned watch app and the phone's watch screens need one set of
springs, one record button shape and one set of glyphs, so both apps
look and move alike.

LogDateMotion names three springs (standard, quick, expressive).
RecordShape is the circle-to-stop morph built with graphics-shapes,
which now compiles for every client target. LogDateGlyph lists the
watch, mark, record, lock and campfire glyphs as shared vectors. The
watch carries white copies for tiles and complications, and
GlyphSetSyncTest fails when a copy's shapes drift from the original.

Nothing uses them yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 14: Commit**

```bash
git restore --staged . && git add client/ui/build.gradle.kts client/ui/src/commonMain/kotlin/app/logdate/ui/tokens/LogDateMotion.kt client/ui/src/commonMain/kotlin/app/logdate/ui/tokens/RecordShape.kt client/ui/src/commonMain/kotlin/app/logdate/ui/glyphs/LogDateGlyph.kt client/ui/src/commonMain/composeResources/drawable/glyph_watch.xml client/ui/src/commonMain/composeResources/drawable/glyph_mark.xml client/ui/src/commonMain/composeResources/drawable/glyph_record.xml client/ui/src/commonMain/composeResources/drawable/glyph_lock.xml client/ui/src/commonMain/composeResources/drawable/glyph_campfire.xml app/wear/src/main/res/drawable/ic_glyph_watch.xml app/wear/src/main/res/drawable/ic_glyph_mark.xml app/wear/src/main/res/drawable/ic_glyph_record.xml app/wear/src/main/res/drawable/ic_glyph_lock.xml app/wear/src/main/res/drawable/ic_glyph_campfire.xml client/ui/src/commonTest/kotlin/app/logdate/ui/tokens/LogDateMotionTest.kt client/ui/src/commonTest/kotlin/app/logdate/ui/tokens/RecordShapeTest.kt client/ui/src/desktopTest/kotlin/app/logdate/ui/tokens/RecordShapeOutlineTest.kt app/wear/src/test/kotlin/app/logdate/wear/glyphs/GlyphSetSyncTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task1-commit.txt
```

Expected: the hooks pass and one commit is created.

---

### Task 2: Extend the shared haptic vocabulary (`chore(ui)`)

`HapticEvent` (`client/ui/src/commonMain/kotlin/app/logdate/ui/platform/HapticEvent.kt:12-33`) has five events; the watch needs seven more. Events that report a change the user cannot see or must not miss are critical, so they survive reduced motion and the watch's Essential setting: a recording locking, pausing, resuming or nearing its limit, a mark made without looking, and a request that was refused. Only `DetentTick` is decorative. The Android controller (`AndroidPlatformHaptics.kt:27`) is `internal` and built only by `rememberPlatformHapticsController()` (`PlatformHaptics.android.kt:9-13`); the watch builds its haptics in Koin, outside Compose, so this task adds a public factory.

**Files:**
- Modify: `client/ui/src/commonMain/kotlin/app/logdate/ui/platform/HapticEvent.kt:1-33`
- Modify: `client/ui/src/commonMain/kotlin/app/logdate/ui/platform/HapticEventToTokens.kt:11-19`
- Modify: `client/ui/src/commonMain/kotlin/app/logdate/ui/platform/LogDateHaptics.kt:18-55`
- Create: `client/ui/src/androidMain/kotlin/app/logdate/ui/platform/AndroidHaptics.kt`
- Modify: `client/ui/src/androidMain/kotlin/app/logdate/ui/platform/PlatformHaptics.android.kt:9-13`
- Test: `client/ui/src/commonTest/kotlin/app/logdate/ui/platform/HapticEventTest.kt:20-51`
- Test: `client/ui/src/commonTest/kotlin/app/logdate/ui/platform/LogDateHapticsTest.kt:78-126`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces:
  - `HapticEvent.RecordingLocked`, `Paused`, `Resumed`, `MomentMarked`, `LimitApproaching`, `ActionRejected`, `DetentTick`.
  - `LogDateHaptics.recordingLocked()`, `recordingPaused()`, `recordingResumed()`, `momentMarked()`, `limitApproaching()`, `actionRejected()`, `detentTick()`.
  - `fun createAndroidPlatformHapticsController(context: Context): PlatformHapticsController` and `fun isSystemHapticFeedbackOff(context: Context): Boolean` in `app.logdate.ui.platform` (androidMain).

- [ ] **Step 1: Update the classification test**

In `client/ui/src/commonTest/kotlin/app/logdate/ui/platform/HapticEventTest.kt`, replace lines 20-51 (the two tests after the first) with:

```kotlin
    @Test
    fun `safety-relevant events are classified critical`() {
        val expectedCritical =
            setOf(
                HapticEvent.RecordingStarted,
                HapticEvent.RecordingFinished,
                HapticEvent.ConfirmDestruction,
                HapticEvent.RecordingLocked,
                HapticEvent.Paused,
                HapticEvent.Resumed,
                HapticEvent.MomentMarked,
                HapticEvent.LimitApproaching,
                HapticEvent.ActionRejected,
            )

        assertEquals(
            expectedCritical,
            HapticEvent.criticalEvents,
            "Critical-event allowlist must match the design spec — these are the only events " +
                "that fire when the system has reduced/disabled haptics.",
        )
    }

    @Test
    fun `routine non-critical events are not in the critical set`() {
        val nonCritical =
            listOf(
                HapticEvent.SaveSucceeded,
                HapticEvent.TranscriptionReady,
                HapticEvent.DetentTick,
            )

        for (event in nonCritical) {
            assertTrue(
                event !in HapticEvent.criticalEvents,
                "$event should be suppressible when system reduce-motion is on.",
            )
        }
    }
}
```

- [ ] **Step 2: Add the mapping tests**

In `client/ui/src/commonTest/kotlin/app/logdate/ui/platform/LogDateHapticsTest.kt`, insert after line 125 (the closing brace of `reduce-motion controller still fires safety-critical events`) and before line 126 (the class's closing brace):

```kotlin

    @Test
    fun `recordingLocked fires a rigid impact`() {
        assertEquals(listOf<RecordedHaptic>(RecordedHaptic.Impact(HapticImpactStrength.Rigid)), firedBy { recordingLocked() })
    }

    @Test
    fun `recordingPaused fires a soft impact`() {
        assertEquals(listOf<RecordedHaptic>(RecordedHaptic.Impact(HapticImpactStrength.Soft)), firedBy { recordingPaused() })
    }

    @Test
    fun `recordingResumed fires a light impact`() {
        assertEquals(listOf<RecordedHaptic>(RecordedHaptic.Impact(HapticImpactStrength.Light)), firedBy { recordingResumed() })
    }

    @Test
    fun `momentMarked fires a success notification`() {
        assertEquals(listOf<RecordedHaptic>(RecordedHaptic.Notification(HapticNotificationType.Success)), firedBy { momentMarked() })
    }

    @Test
    fun `limitApproaching fires a warning notification`() {
        assertEquals(listOf<RecordedHaptic>(RecordedHaptic.Notification(HapticNotificationType.Warning)), firedBy { limitApproaching() })
    }

    @Test
    fun `actionRejected fires an error notification`() {
        assertEquals(listOf<RecordedHaptic>(RecordedHaptic.Notification(HapticNotificationType.Error)), firedBy { actionRejected() })
    }

    @Test
    fun `detentTick fires a tick`() {
        assertEquals(listOf<RecordedHaptic>(RecordedHaptic.Tick), firedBy { detentTick() })
    }

    @Test
    fun `reduce-motion still fires recording, mark and refusal events`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder, reduceMotion = { true })

        haptics.recordingLocked()
        haptics.recordingPaused()
        haptics.recordingResumed()
        haptics.momentMarked()
        haptics.limitApproaching()
        haptics.actionRejected()

        assertEquals(6, recorder.events.size, "Got: ${recorder.events}")
    }

    @Test
    fun `reduce-motion drops detent ticks`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder, reduceMotion = { true })

        haptics.detentTick()

        assertTrue(recorder.events.isEmpty(), "Got: ${recorder.events}")
    }

    private fun firedBy(action: LogDateHaptics.() -> Unit): List<RecordedHaptic> {
        val recorder = RecordingPlatformHaptics()
        DefaultLogDateHaptics(recorder).action()
        return recorder.events
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:ui:desktopTest --tests 'app.logdate.ui.platform.*'
```

Expected: FAIL at `:client:ui:compileTestKotlinDesktop` with `Unresolved reference 'RecordingLocked'` and `Unresolved reference 'recordingLocked'`.

- [ ] **Step 4: Add the events**

Replace `client/ui/src/commonMain/kotlin/app/logdate/ui/platform/HapticEvent.kt` with:

```kotlin
package app.logdate.ui.platform

/**
 * Every named haptic event LogDate can fire, on the phone and on the watch. The mapping from event
 * to primitive lives in [HapticEventToTokens] (the single tuning file). Feature code never
 * references primitives directly — it calls [LogDateHaptics] methods, which use this enum internally.
 *
 * Membership in [criticalEvents] decides whether an event survives system reduce-motion, "haptics
 * off", and the watch's Essential haptics setting. An event is critical when it reports something
 * the user cannot see or must not miss: a recording starting, locking, pausing, resuming, nearing
 * its limit or finishing, a destructive confirmation, a mark made without looking at the screen,
 * or a request that was refused. Everything else is suppressible.
 */
enum class HapticEvent {
    SaveSucceeded,
    RecordingStarted,
    RecordingFinished,
    TranscriptionReady,
    ConfirmDestruction,

    /** A recording started by holding Record locked, so lifting the finger no longer stops it. */
    RecordingLocked,

    /** A recording paused. */
    Paused,

    /** A paused recording resumed. */
    Resumed,

    /** A moment was marked, often from a complication with the screen off. */
    MomentMarked,

    /** A recording is close to its length limit and stops on its own soon. */
    LimitApproaching,

    /** Something the user asked for did not happen, such as a recording that could not start. */
    ActionRejected,

    /** The crown or a scrubber passed one detent. */
    DetentTick,
    ;

    companion object {
        /**
         * Events that always fire, even when the OS reports reduce-motion or haptics-off, or the
         * watch is set to Essential haptics. They are signals, never delight; suppressing them would
         * let users miss something important.
         */
        val criticalEvents: Set<HapticEvent> =
            setOf(
                RecordingStarted,
                RecordingFinished,
                ConfirmDestruction,
                RecordingLocked,
                Paused,
                Resumed,
                MomentMarked,
                LimitApproaching,
                ActionRejected,
            )
    }
}
```

- [ ] **Step 5: Map the events to primitives**

Replace lines 11-19 of `client/ui/src/commonMain/kotlin/app/logdate/ui/platform/HapticEventToTokens.kt` with:

```kotlin
internal fun PlatformHapticsController.fire(event: HapticEvent) {
    when (event) {
        HapticEvent.SaveSucceeded -> notification(HapticNotificationType.Success)
        HapticEvent.RecordingStarted -> impact(HapticImpactStrength.Medium)
        HapticEvent.RecordingFinished -> notification(HapticNotificationType.Success)
        HapticEvent.TranscriptionReady -> tick()
        HapticEvent.ConfirmDestruction -> impact(HapticImpactStrength.Heavy)
        HapticEvent.RecordingLocked -> impact(HapticImpactStrength.Rigid)
        HapticEvent.Paused -> impact(HapticImpactStrength.Soft)
        HapticEvent.Resumed -> impact(HapticImpactStrength.Light)
        HapticEvent.MomentMarked -> notification(HapticNotificationType.Success)
        HapticEvent.LimitApproaching -> notification(HapticNotificationType.Warning)
        HapticEvent.ActionRejected -> notification(HapticNotificationType.Error)
        HapticEvent.DetentTick -> tick()
    }
}
```

- [ ] **Step 6: Add the facade methods**

In `client/ui/src/commonMain/kotlin/app/logdate/ui/platform/LogDateHaptics.kt`, replace lines 18-55 (the interface and `DefaultLogDateHaptics`) with:

```kotlin
interface LogDateHaptics {
    fun saveSucceeded()

    fun recordingStarted()

    fun recordingFinished()

    fun transcriptionReady()

    fun confirmDestruction()

    fun recordingLocked()

    fun recordingPaused()

    fun recordingResumed()

    fun momentMarked()

    fun limitApproaching()

    fun actionRejected()

    fun detentTick()
}

/**
 * Default mapping from semantic events to platform primitives. The [reduceMotion] callback
 * is consulted on every fire; when it returns `true`, only events in
 * [HapticEvent.criticalEvents] survive. The watch also returns `true` while its haptics setting
 * is Essential.
 */
class DefaultLogDateHaptics(
    private val controller: PlatformHapticsController,
    private val reduceMotion: () -> Boolean = { false },
) : LogDateHaptics {
    private fun fire(event: HapticEvent) {
        if (reduceMotion() && event !in HapticEvent.criticalEvents) return
        controller.fire(event)
    }

    override fun saveSucceeded() = fire(HapticEvent.SaveSucceeded)

    override fun recordingStarted() = fire(HapticEvent.RecordingStarted)

    override fun recordingFinished() = fire(HapticEvent.RecordingFinished)

    override fun transcriptionReady() = fire(HapticEvent.TranscriptionReady)

    override fun confirmDestruction() = fire(HapticEvent.ConfirmDestruction)

    override fun recordingLocked() = fire(HapticEvent.RecordingLocked)

    override fun recordingPaused() = fire(HapticEvent.Paused)

    override fun recordingResumed() = fire(HapticEvent.Resumed)

    override fun momentMarked() = fire(HapticEvent.MomentMarked)

    override fun limitApproaching() = fire(HapticEvent.LimitApproaching)

    override fun actionRejected() = fire(HapticEvent.ActionRejected)

    override fun detentTick() = fire(HapticEvent.DetentTick)
}
```

- [ ] **Step 7: Add the non-Compose Android factory**

Create `client/ui/src/androidMain/kotlin/app/logdate/ui/platform/AndroidHaptics.kt`:

```kotlin
package app.logdate.ui.platform

import android.content.Context

/**
 * Builds the vibrator-backed [PlatformHapticsController] for code that has a [Context] but no
 * composition, such as the watch app's dependency graph.
 */
fun createAndroidPlatformHapticsController(context: Context): PlatformHapticsController = AndroidPlatformHaptics(context.applicationContext)

/** Whether the user turned touch haptics off in system settings. Read it on every event. */
fun isSystemHapticFeedbackOff(context: Context): Boolean = isHapticDisabled(context)
```

Replace lines 9-13 of `client/ui/src/androidMain/kotlin/app/logdate/ui/platform/PlatformHaptics.android.kt` with:

```kotlin
@Composable
actual fun rememberPlatformHapticsController(): PlatformHapticsController {
    val context = LocalContext.current.applicationContext
    return remember(context) { createAndroidPlatformHapticsController(context) }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:ui:desktopTest --tests 'app.logdate.ui.platform.*' :client:ui:testAndroidHostTest --tests 'app.logdate.ui.platform.*' :client:ui:compileKotlinIosSimulatorArm64 :client:ui:ktlintCheck :app:android-main:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. `HapticEventTest` runs 3 tests and `LogDateHapticsTest` 17.

- [ ] **Step 9: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task2-commit.txt <<'EOF'
chore(ui): add haptic events for locking, pausing and marking

The redesigned watch recorder and Mark need haptics the shared
vocabulary did not have. HapticEvent gains RecordingLocked, Paused,
Resumed, MomentMarked, LimitApproaching, ActionRejected and
DetentTick, each with a primitive in the tuning table and a method on
LogDateHaptics. All but DetentTick are critical, so they still play
with reduced motion or the watch's Essential setting.

createAndroidPlatformHapticsController builds the vibrator controller
outside Compose for the watch's dependency graph, and the phone's
rememberPlatformHapticsController now builds through it. Nothing
fires the new events yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 10: Commit**

```bash
git restore --staged . && git add client/ui/src/commonMain/kotlin/app/logdate/ui/platform/HapticEvent.kt client/ui/src/commonMain/kotlin/app/logdate/ui/platform/HapticEventToTokens.kt client/ui/src/commonMain/kotlin/app/logdate/ui/platform/LogDateHaptics.kt client/ui/src/androidMain/kotlin/app/logdate/ui/platform/AndroidHaptics.kt client/ui/src/androidMain/kotlin/app/logdate/ui/platform/PlatformHaptics.android.kt client/ui/src/commonTest/kotlin/app/logdate/ui/platform/HapticEventTest.kt client/ui/src/commonTest/kotlin/app/logdate/ui/platform/LogDateHapticsTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task2-commit.txt
```

---

### Task 3: Watch haptics with an All or Essential setting (`chore(wear)`)

The new watch app plays the shared vocabulary instead of `WearHapticEngine`'s own methods. Settings (Track B, step B6) offers Haptics: All or Essential; this task adds the stored preference and the haptics that obey it, so the setting only needs a row later. `WearHapticEngine` and its call sites (`WearRecordingViewModel.kt:104`, `MoodCheckInScreen.kt:45`) stay as they are for v1.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/haptics/HapticsPreference.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/haptics/WatchHaptics.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt:9,43-47`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/haptics/HapticsPreferenceStoreTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/haptics/WatchHapticsTest.kt`

**Interfaces:**
- Consumes (Task 2): `LogDateHaptics` and its new methods, `DefaultLogDateHaptics(controller, reduceMotion)`, `createAndroidPlatformHapticsController(context)`, `isSystemHapticFeedbackOff(context)`.
- Produces:
  - `enum class HapticsPreference(val storedName: String) { ALL, ESSENTIAL }` with `fromStored(String?)`.
  - `interface HapticsPreferenceStore { val preference: StateFlow<HapticsPreference>; fun set(preference: HapticsPreference) }` and `class SharedPreferencesHapticsPreferenceStore(preferences: SharedPreferences)` with `PREFERENCES_NAME = "wear_haptics"`.
  - `fun watchHaptics(controller: PlatformHapticsController, preferences: HapticsPreferenceStore, systemHapticsOff: () -> Boolean): LogDateHaptics`.
  - `val wearV2Module: Module` binding `HapticsPreferenceStore` and `LogDateHaptics`, registered in `LogDateWearApplication`.

- [ ] **Step 1: Write the failing store test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/haptics/HapticsPreferenceStoreTest.kt`:

```kotlin
package app.logdate.wear.v2.haptics

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals

class HapticsPreferenceStoreTest {
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val preferences = mockk<SharedPreferences> { every { edit() } returns editor }

    @Test
    fun `nothing saved means All`() {
        every { preferences.getString("haptics", null) } returns null

        assertEquals(HapticsPreference.ALL, store().preference.value)
    }

    @Test
    fun `a saved Essential is read back`() {
        every { preferences.getString("haptics", null) } returns "essential"

        assertEquals(HapticsPreference.ESSENTIAL, store().preference.value)
    }

    @Test
    fun `a value a later version saved means All`() {
        every { preferences.getString("haptics", null) } returns "loud"

        assertEquals(HapticsPreference.ALL, store().preference.value)
    }

    @Test
    fun `setting Essential saves it and reports it at once`() {
        every { preferences.getString("haptics", null) } returns null
        val store = store()

        store.set(HapticsPreference.ESSENTIAL)

        verify { editor.putString("haptics", "essential") }
        verify { editor.apply() }
        assertEquals(HapticsPreference.ESSENTIAL, store.preference.value)
    }

    private fun store() = SharedPreferencesHapticsPreferenceStore(preferences)
}
```

- [ ] **Step 2: Write the failing haptics test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/haptics/WatchHapticsTest.kt`:

```kotlin
package app.logdate.wear.v2.haptics

import app.logdate.ui.platform.HapticImpactStrength
import app.logdate.ui.platform.HapticNotificationType
import app.logdate.ui.platform.PlatformHapticsController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test
import kotlin.test.assertEquals

class WatchHapticsTest {
    private val controller = PlayedHaptics()
    private val preferences = FakeHapticsPreferenceStore()
    private var systemHapticsOff = false
    private val haptics = watchHaptics(controller, preferences) { systemHapticsOff }

    @Test
    fun `All plays every event`() {
        haptics.detentTick()
        haptics.saveSucceeded()
        haptics.momentMarked()

        assertEquals(listOf("tick", "notification Success", "notification Success"), controller.played)
    }

    @Test
    fun `Essential drops crown ticks and save confirmations`() {
        preferences.set(HapticsPreference.ESSENTIAL)

        haptics.detentTick()
        haptics.saveSucceeded()

        assertEquals(emptyList<String>(), controller.played)
    }

    @Test
    fun `Essential still plays recording, mark and refusal events`() {
        preferences.set(HapticsPreference.ESSENTIAL)

        haptics.recordingLocked()
        haptics.recordingPaused()
        haptics.recordingResumed()
        haptics.momentMarked()
        haptics.limitApproaching()
        haptics.actionRejected()

        assertEquals(
            listOf(
                "impact Rigid",
                "impact Soft",
                "impact Light",
                "notification Success",
                "notification Warning",
                "notification Error",
            ),
            controller.played,
        )
    }

    @Test
    fun `system touch haptics off acts like Essential`() {
        systemHapticsOff = true

        haptics.detentTick()
        haptics.momentMarked()

        assertEquals(listOf("notification Success"), controller.played)
    }

    @Test
    fun `a changed setting applies to the next event`() {
        haptics.detentTick()
        preferences.set(HapticsPreference.ESSENTIAL)
        haptics.detentTick()
        preferences.set(HapticsPreference.ALL)
        haptics.detentTick()

        assertEquals(listOf("tick", "tick"), controller.played)
    }
}

private class PlayedHaptics : PlatformHapticsController {
    val played = mutableListOf<String>()

    override fun impact(strength: HapticImpactStrength) {
        played += "impact $strength"
    }

    override fun notification(type: HapticNotificationType) {
        played += "notification $type"
    }

    override fun tick() {
        played += "tick"
    }
}

private class FakeHapticsPreferenceStore : HapticsPreferenceStore {
    private val current = MutableStateFlow(HapticsPreference.ALL)

    override val preference: StateFlow<HapticsPreference> = current

    override fun set(preference: HapticsPreference) {
        current.value = preference
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.haptics.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'SharedPreferencesHapticsPreferenceStore'` and `Unresolved reference 'watchHaptics'`.

- [ ] **Step 4: Add the preference and its store**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/haptics/HapticsPreference.kt`:

```kotlin
package app.logdate.wear.v2.haptics

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How much the watch vibrates. */
enum class HapticsPreference(
    val storedName: String,
) {
    /** Every haptic LogDate plays, including crown ticks and save confirmations. */
    ALL("all"),

    /** Only changes to a recording, marks and refused actions. */
    ESSENTIAL("essential"),
    ;

    companion object {
        /** The preference saved as [storedName], or [ALL] when nothing valid was saved. */
        fun fromStored(storedName: String?): HapticsPreference = entries.firstOrNull { it.storedName == storedName } ?: ALL
    }
}

/** The watch's haptics setting. */
interface HapticsPreferenceStore {
    val preference: StateFlow<HapticsPreference>

    fun set(preference: HapticsPreference)
}

/** [HapticsPreferenceStore] in the watch's own SharedPreferences file, [PREFERENCES_NAME]. */
class SharedPreferencesHapticsPreferenceStore(
    private val preferences: SharedPreferences,
) : HapticsPreferenceStore {
    private val current = MutableStateFlow(HapticsPreference.fromStored(preferences.getString(KEY_HAPTICS, null)))

    override val preference: StateFlow<HapticsPreference> = current.asStateFlow()

    override fun set(preference: HapticsPreference) {
        preferences.edit { putString(KEY_HAPTICS, preference.storedName) }
        current.value = preference
    }

    companion object {
        const val PREFERENCES_NAME = "wear_haptics"
        private const val KEY_HAPTICS = "haptics"
    }
}
```

- [ ] **Step 5: Add the watch haptics**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/haptics/WatchHaptics.kt`:

```kotlin
package app.logdate.wear.v2.haptics

import app.logdate.ui.platform.DefaultLogDateHaptics
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.ui.platform.PlatformHapticsController

/**
 * The haptics the new watch app plays. With [HapticsPreference.ESSENTIAL], or while the system's
 * touch haptics are off, only the events the shared vocabulary marks critical play. Both are read
 * on every event, so a change applies to the next one.
 */
fun watchHaptics(
    controller: PlatformHapticsController,
    preferences: HapticsPreferenceStore,
    systemHapticsOff: () -> Boolean,
): LogDateHaptics =
    DefaultLogDateHaptics(controller) {
        preferences.preference.value == HapticsPreference.ESSENTIAL || systemHapticsOff()
    }
```

- [ ] **Step 6: Add the Koin module**

Create `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`:

```kotlin
package app.logdate.wear.di

import android.content.Context
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.ui.platform.createAndroidPlatformHapticsController
import app.logdate.ui.platform.isSystemHapticFeedbackOff
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.haptics.SharedPreferencesHapticsPreferenceStore
import app.logdate.wear.v2.haptics.watchHaptics
import org.koin.dsl.module

/** Koin module for the redesigned watch app. Only the new app's screens resolve anything from it. */
val wearV2Module =
    module {
        single<HapticsPreferenceStore> {
            SharedPreferencesHapticsPreferenceStore(
                get<Context>().getSharedPreferences(SharedPreferencesHapticsPreferenceStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single<LogDateHaptics> {
            val context = get<Context>()
            watchHaptics(
                controller = createAndroidPlatformHapticsController(context),
                preferences = get(),
                systemHapticsOff = { isSystemHapticFeedbackOff(context) },
            )
        }
    }
```

- [ ] **Step 7: Register the module**

In `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`, after line 9 (`import app.logdate.wear.di.wearExperienceModule`), add:

```kotlin
import app.logdate.wear.di.wearV2Module
```

and replace lines 43-47 with:

```kotlin
            modules(
                wearDataModule,
                wearAudioModule,
                wearExperienceModule,
                wearV2Module,
            )
```

- [ ] **Step 8: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.haptics.*' :app:wear:ktlintCheck :app:wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; 9 tests pass.

- [ ] **Step 9: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task3-commit.txt <<'EOF'
chore(wear): add watch haptics with an All or Essential setting

The redesigned watch app plays the shared haptic vocabulary instead of
the watch's own engine. Haptics follow a stored All or Essential
preference, which the watch settings will offer: Essential, or system
touch haptics turned off, plays only the critical events. Both are
read on every event.

WearV2Module lists the new app's bindings and is registered at
startup. The current watch app still uses WearHapticEngine, and
nothing in the new app plays haptics yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 10: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/haptics/HapticsPreference.kt app/wear/src/main/kotlin/app/logdate/wear/v2/haptics/WatchHaptics.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/test/kotlin/app/logdate/wear/v2/haptics/HapticsPreferenceStoreTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/haptics/WatchHapticsTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task3-commit.txt
```

---
### Task 4: The watch theme (`chore(wear)`)

Today's watch theme (`app/wear/src/main/kotlin/app/logdate/wear/presentation/theme/Theme.kt:7-11`) is Wear Material 3's default. The new app follows the system's dynamic color where Wear OS 6 offers it and falls back to a neutral graphite scheme everywhere else. The campfire's flame is the only warm color; it is drawn by the shared `Campfire` composable and by `glyph_campfire`, never by the scheme. The v1 theme is not touched; Task 8 applies this one to the v2 tree only.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/theme/GraphiteColorScheme.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/theme/LogDateWearTheme.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/theme/GraphiteColorSchemeTest.kt`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `val GraphiteColorScheme: androidx.wear.compose.material3.ColorScheme`, `@Composable fun LogDateWearTheme(dynamicColor: Boolean = true, content: @Composable () -> Unit)`, and `internal fun colorSchemeFor(context: Context, dynamicColor: Boolean): ColorScheme` in `app.logdate.wear.v2.theme`.

- [ ] **Step 1: Write the failing contrast test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/theme/GraphiteColorSchemeTest.kt`:

```kotlin
package app.logdate.wear.v2.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import io.mockk.mockk
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Checks the graphite fallback against WCAG 2.2: 4.5:1 for text and icons on the surface they sit
 * on, 3:1 for outlines and other non-text boundaries. Also checks the scheme stays neutral, so the
 * campfire's flame is the only warm color on the watch.
 */
class GraphiteColorSchemeTest {
    private val scheme = GraphiteColorScheme
    private val surfaces =
        mapOf(
            "background" to scheme.background,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        )

    @Test
    fun `text on filled roles reaches 4_5 to 1`() {
        val pairs =
            mapOf(
                "onPrimary on primary" to (scheme.onPrimary to scheme.primary),
                "onPrimary on primaryDim" to (scheme.onPrimary to scheme.primaryDim),
                "onPrimaryContainer on primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "onSecondary on secondary" to (scheme.onSecondary to scheme.secondary),
                "onSecondary on secondaryDim" to (scheme.onSecondary to scheme.secondaryDim),
                "onSecondaryContainer on secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "onTertiary on tertiary" to (scheme.onTertiary to scheme.tertiary),
                "onTertiary on tertiaryDim" to (scheme.onTertiary to scheme.tertiaryDim),
                "onTertiaryContainer on tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "onError on error" to (scheme.onError to scheme.error),
                "onError on errorDim" to (scheme.onError to scheme.errorDim),
                "onErrorContainer on errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
                "onBackground on background" to (scheme.onBackground to scheme.background),
            )

        pairs.forEach { (name, colors) -> assertContrast(name, colors.first, colors.second, TEXT_MINIMUM) }
    }

    @Test
    fun `text and accents on every surface reach 4_5 to 1`() {
        surfaces.forEach { (surfaceName, surface) ->
            assertContrast("onSurface on $surfaceName", scheme.onSurface, surface, TEXT_MINIMUM)
            assertContrast("onSurfaceVariant on $surfaceName", scheme.onSurfaceVariant, surface, TEXT_MINIMUM)
            assertContrast("primary on $surfaceName", scheme.primary, surface, TEXT_MINIMUM)
        }
    }

    @Test
    fun `outlines reach 3 to 1 on every surface`() {
        surfaces.forEach { (surfaceName, surface) ->
            assertContrast("outline on $surfaceName", scheme.outline, surface, OUTLINE_MINIMUM)
        }
    }

    @Test
    fun `every role except error is a neutral grey`() {
        val neutralRoles =
            mapOf(
                "primary" to scheme.primary,
                "primaryDim" to scheme.primaryDim,
                "primaryContainer" to scheme.primaryContainer,
                "onPrimary" to scheme.onPrimary,
                "onPrimaryContainer" to scheme.onPrimaryContainer,
                "secondary" to scheme.secondary,
                "secondaryDim" to scheme.secondaryDim,
                "secondaryContainer" to scheme.secondaryContainer,
                "onSecondary" to scheme.onSecondary,
                "onSecondaryContainer" to scheme.onSecondaryContainer,
                "tertiary" to scheme.tertiary,
                "tertiaryDim" to scheme.tertiaryDim,
                "tertiaryContainer" to scheme.tertiaryContainer,
                "onTertiary" to scheme.onTertiary,
                "onTertiaryContainer" to scheme.onTertiaryContainer,
                "outline" to scheme.outline,
                "outlineVariant" to scheme.outlineVariant,
                "onSurface" to scheme.onSurface,
                "onSurfaceVariant" to scheme.onSurfaceVariant,
                "onBackground" to scheme.onBackground,
            ) + surfaces

        neutralRoles.forEach { (name, color) ->
            val chroma = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
            assertTrue(chroma <= MAX_NEUTRAL_CHROMA, "$name has chroma $chroma")
        }
    }

    @Test
    fun `turning dynamic color off always gives graphite`() {
        assertSame(GraphiteColorScheme, colorSchemeFor(mockk<Context>(), dynamicColor = false))
    }

    private fun assertContrast(
        name: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= minimum, "$name is ${"%.2f".format(ratio)}:1, needs $minimum:1")
    }

    private fun contrastRatio(
        first: Color,
        second: Color,
    ): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun linear(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    private companion object {
        const val TEXT_MINIMUM = 4.5
        const val OUTLINE_MINIMUM = 3.0
        const val MAX_NEUTRAL_CHROMA = 0.06f
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.theme.GraphiteColorSchemeTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'GraphiteColorScheme'` and `Unresolved reference 'colorSchemeFor'`.

- [ ] **Step 3: Add the graphite scheme**

The values were checked before writing: the lowest text pair is `onError` on `errorDim` at 6.04:1, the lowest outline pair `outline` on `surfaceContainerHigh` at 4.14:1, and the highest neutral chroma 0.051.

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/theme/GraphiteColorScheme.kt`:

```kotlin
package app.logdate.wear.v2.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

/**
 * The new watch app's colors when the system offers no dynamic color, which is every watch before
 * Wear OS 6.
 *
 * Every role but error is a neutral grey: the only warm color on the watch is the campfire's flame,
 * which the campfire draws itself. Text roles reach 4.5:1 on the surfaces they sit on and outlines
 * 3:1; `GraphiteColorSchemeTest` checks both.
 */
val GraphiteColorScheme: ColorScheme =
    ColorScheme(
        primary = Color(0xFFC9CED6),
        primaryDim = Color(0xFFAEB4BC),
        primaryContainer = Color(0xFF3A3F47),
        onPrimary = Color(0xFF1B1F26),
        onPrimaryContainer = Color(0xFFE1E5EC),
        secondary = Color(0xFFC4C7CC),
        secondaryDim = Color(0xFFA9ACB1),
        secondaryContainer = Color(0xFF3B3E43),
        onSecondary = Color(0xFF1D2024),
        onSecondaryContainer = Color(0xFFE0E3E8),
        tertiary = Color(0xFFD0CCC6),
        tertiaryDim = Color(0xFFB4B0AA),
        tertiaryContainer = Color(0xFF45423E),
        onTertiary = Color(0xFF211F1C),
        onTertiaryContainer = Color(0xFFECE8E2),
        surfaceContainerLow = Color(0xFF1B1C1E),
        surfaceContainer = Color(0xFF242528),
        surfaceContainerHigh = Color(0xFF2E3033),
        onSurface = Color(0xFFE4E5E8),
        onSurfaceVariant = Color(0xFFC2C5CA),
        outline = Color(0xFF8D9096),
        outlineVariant = Color(0xFF46484C),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        error = Color(0xFFFFB4AB),
        errorDim = Color(0xFFE89D94),
        errorContainer = Color(0xFF8C1D18),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
    )
```

- [ ] **Step 4: Add the theme**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/theme/LogDateWearTheme.kt`:

```kotlin
package app.logdate.wear.v2.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * The new watch app's theme: the system's dynamic colors where the watch has them (Wear OS 6 and
 * newer), [GraphiteColorScheme] everywhere else. Previews and screenshots pass
 * [dynamicColor] = false, since they cannot render dynamic color.
 */
@Composable
fun LogDateWearTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = remember(context, dynamicColor) { colorSchemeFor(context, dynamicColor) }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

internal fun colorSchemeFor(
    context: Context,
    dynamicColor: Boolean,
): ColorScheme {
    if (!dynamicColor) return GraphiteColorScheme
    return dynamicColorScheme(context) ?: GraphiteColorScheme
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.theme.GraphiteColorSchemeTest' :app:wear:ktlintCheck
```

Expected: `BUILD SUCCESSFUL`; 5 tests pass.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task4-commit.txt <<'EOF'
chore(wear): add the graphite and dynamic color watch theme

The redesigned watch app follows the system's dynamic colors on Wear
OS 6 and falls back to a neutral graphite scheme on older watches, in
place of the template defaults. The campfire's flame stays the only
warm color.

A unit test checks the fallback against WCAG contrast, 4.5:1 for
text and 3:1 for outlines on every surface, and checks that every
role but error is grey. The theme is not applied anywhere yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/theme/GraphiteColorScheme.kt app/wear/src/main/kotlin/app/logdate/wear/v2/theme/LogDateWearTheme.kt app/wear/src/test/kotlin/app/logdate/wear/v2/theme/GraphiteColorSchemeTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task4-commit.txt
```

---

### Task 5: Today's summary and the day it covers (`chore(wear)`)

The line "3 moments · 2 places · 4:12 voice" is computed in shared code, because the Today tile and the phone's Watch page show the same numbers later. A mood check-in is a text note starting `#mood:<tag>`; today only the watch's `MomentNoteCodec` (`app/wear/src/main/kotlin/app/logdate/wear/capture/MomentNoteCodec.kt:24`) knows that, so the tag reader moves to `client/domain` and the codec reads through it. Places cluster by saved place ID, or by distance within 150 m. `android.location.Location.distanceTo` is not available in `commonMain`, so the calculator uses `geographicDistance` (`client/domain/src/commonMain/kotlin/app/logdate/client/domain/location/history/ReconstructLocationDay.kt:138`), the same great-circle distance, which the module already uses for stays.

`DayClock` reports the calendar day Today covers. It moves at local midnight and at once when the time, date or time zone changes. Today's numbers use the calendar day; the campfire uses its own 4 AM streak day.

**Files:**
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/today/MoodNoteTag.kt`
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/today/TodaySummary.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/capture/MomentNoteCodec.kt:1-52`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/today/DayClock.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TimeChangeSignals.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/today/MoodNoteTagTest.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/today/TodaySummaryCalculatorTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/today/DayClockTest.kt`
- Test (unchanged, must stay green): `app/wear/src/test/kotlin/app/logdate/wear/capture/MomentNoteCodecTest.kt`

**Interfaces:**
- Consumes: `JournalNote`, `NoteLocation`, `NotePlace` (`client/repository/.../journals/JournalNotesRepository.kt:199-310`); `internal fun geographicDistance(lat1, lon1, lat2, lon2): Double`.
- Produces:
  - `object MoodNoteTag { const val PREFIX = "#mood:"; fun tagOf(content: String): String?; fun isFeeling(note: JournalNote): Boolean; fun latestTag(notes: List<JournalNote>, isKnown: (String) -> Boolean = { true }): String? }` in `app.logdate.client.domain.today`.
  - `data class TodaySummary(val momentCount: Int, val placeCount: Int, val voiceDuration: Duration, val latestMoment: JournalNote?, val feelingTag: String?)` with `TodaySummary.Empty`.
  - `object TodaySummaryCalculator { const val SAME_PLACE_METERS = 150.0; fun calculate(notes: List<JournalNote>, isKnownFeeling: (String) -> Boolean = { true }): TodaySummary }`.
  - `data class TodayWindow(val date: LocalDate, val timeZone: TimeZone, val start: Instant, val end: Instant)` with `operator fun contains(instant: Instant): Boolean`, and `class DayClock(timeChanges: Flow<Unit>, clock: Clock = Clock.System, timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() })` with `fun observe(): Flow<TodayWindow>` and `fun current(): TodayWindow`, in `app.logdate.wear.v2.today`.
  - `fun timeChangeSignals(context: Context): Flow<Unit>` in `app.logdate.wear.v2.today`.

- [ ] **Step 1: Write the failing tag test**

Create `client/domain/src/commonTest/kotlin/app/logdate/client/domain/today/MoodNoteTagTest.kt`:

```kotlin
package app.logdate.client.domain.today

import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MoodNoteTagTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")

    @Test
    fun `the tag is read from the start of the note`() {
        assertEquals("great", MoodNoteTag.tagOf("#mood:great Feeling great"))
    }

    @Test
    fun `a tag later in the text is not a check-in`() {
        assertNull(MoodNoteTag.tagOf("Felt #mood:great today"))
        assertFalse(MoodNoteTag.isFeeling(JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Felt #mood:great")))
    }

    @Test
    fun `only text notes can be check-ins`() {
        assertTrue(MoodNoteTag.isFeeling(JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "#mood:ok Feeling ok")))
        assertFalse(MoodNoteTag.isFeeling(JournalNote.Audio(mediaRef = "audio/1.m4a", creationTimestamp = now, lastUpdated = now)))
    }
}
```

- [ ] **Step 2: Write the failing summary test**

Create `client/domain/src/commonTest/kotlin/app/logdate/client/domain/today/TodaySummaryCalculatorTest.kt`:

```kotlin
package app.logdate.client.domain.today

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class TodaySummaryCalculatorTest {
    @Test
    fun `an empty day has no moments, places, voice or feeling`() {
        assertEquals(TodaySummary.Empty, TodaySummaryCalculator.calculate(emptyList()))
    }

    @Test
    fun `mood check-ins are not moments`() {
        val summary = TodaySummaryCalculator.calculate(listOf(text("Lunch by the water", minute = 0), text("#mood:good Feeling good", minute = 5)))

        assertEquals(1, summary.momentCount)
    }

    @Test
    fun `voice time adds up every recording`() {
        val summary = TodaySummaryCalculator.calculate(listOf(audio(durationMs = 200_000, minute = 0), audio(durationMs = 52_000, minute = 10)))

        assertEquals(252.seconds, summary.voiceDuration)
        assertEquals(2, summary.momentCount)
    }

    @Test
    fun `a recording with no known length adds no voice time`() {
        val summary = TodaySummaryCalculator.calculate(listOf(audio(durationMs = 0, minute = 0)))

        assertEquals(Duration.ZERO, summary.voiceDuration)
    }

    @Test
    fun `the latest moment is the newest note that is not a check-in`() {
        val lunch = text("Lunch", minute = 30)

        val summary = TodaySummaryCalculator.calculate(listOf(lunch, text("Coffee", minute = 0), text("#mood:great Feeling great", minute = 45)))

        assertEquals(lunch, summary.latestMoment)
    }

    @Test
    fun `the feeling is the newest check-in`() {
        val summary = TodaySummaryCalculator.calculate(listOf(text("#mood:great Feeling great", minute = 20), text("#mood:sad Feeling sad", minute = 0)))

        assertEquals("great", summary.feelingTag)
    }

    @Test
    fun `a newer check-in with a tag the caller does not know is skipped`() {
        val notes = listOf(text("#mood:good Feeling good", minute = 0), text("#mood:elated Feeling elated", minute = 20))

        val summary = TodaySummaryCalculator.calculate(notes) { tag -> tag != "elated" }

        assertEquals("good", summary.feelingTag)
    }

    @Test
    fun `notes at one saved place are one place however far apart`() {
        val park = NotePlace(id = Uuid.random(), name = "Tempelhofer Feld", latitude = berlin.latitude, longitude = berlin.longitude)
        val notes =
            listOf(
                text("North gate", minute = 0, location = NoteLocation(coordinates = berlin, place = park)),
                text("South gate", minute = 30, location = NoteLocation(coordinates = north(meters = -2_000.0), place = park)),
            )

        assertEquals(1, TodaySummaryCalculator.calculate(notes).placeCount)
    }

    @Test
    fun `notes within 150 meters of each other are one place`() {
        val notes =
            listOf(
                text("Coffee", minute = 0, location = NoteLocation(coordinates = berlin)),
                text("Bench outside", minute = 10, location = NoteLocation(coordinates = north(meters = 100.0))),
            )

        assertEquals(1, TodaySummaryCalculator.calculate(notes).placeCount)
    }

    @Test
    fun `notes more than 150 meters apart are two places`() {
        val notes =
            listOf(
                text("Coffee", minute = 0, location = NoteLocation(coordinates = berlin)),
                text("Office", minute = 10, location = NoteLocation(coordinates = north(meters = 300.0))),
            )

        assertEquals(2, TodaySummaryCalculator.calculate(notes).placeCount)
    }

    @Test
    fun `notes without a location add no place`() {
        assertEquals(0, TodaySummaryCalculator.calculate(listOf(text("Coffee", minute = 0))).placeCount)
    }

    @Test
    fun `check-ins add no place`() {
        val notes = listOf(text("#mood:ok Feeling ok", minute = 0, location = NoteLocation(coordinates = berlin)))

        assertEquals(0, TodaySummaryCalculator.calculate(notes).placeCount)
    }
}

private val morning = Instant.parse("2026-10-03T08:00:00Z")
private val berlin = NoteCoordinates(latitude = 52.5200, longitude = 13.4050)
private const val METERS_PER_DEGREE_LATITUDE = 111_195.0

private fun north(meters: Double) = berlin.copy(latitude = berlin.latitude + meters / METERS_PER_DEGREE_LATITUDE)

private fun text(
    content: String,
    minute: Int,
    location: NoteLocation? = null,
) = JournalNote.Text(
    creationTimestamp = morning + minute.minutes,
    lastUpdated = morning + minute.minutes,
    content = content,
    location = location,
)

private fun audio(
    durationMs: Long,
    minute: Int,
) = JournalNote.Audio(
    mediaRef = "audio/$minute.m4a",
    durationMs = durationMs,
    creationTimestamp = morning + minute.minutes,
    lastUpdated = morning + minute.minutes,
)
```

- [ ] **Step 3: Write the failing day clock test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/today/DayClockTest.kt`:

```kotlin
package app.logdate.wear.v2.today

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DayClockTest {
    private val newYork = TimeZone.of("America/New_York")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val lateEvening = LocalDateTime(2026, 10, 3, 22, 0).toInstant(newYork)

    @Test
    fun `today is reported at once`() =
        runTest {
            val clock = SchedulerClock(lateEvening, testScheduler)

            DayClock(timeChanges = emptyFlow(), clock = clock, timeZone = { newYork }).observe().test {
                val today = awaitItem()
                assertEquals(LocalDate(2026, 10, 3), today.date)
                assertEquals(LocalDate(2026, 10, 3).atStartOfDayIn(newYork), today.start)
                assertEquals(LocalDate(2026, 10, 4).atStartOfDayIn(newYork), today.end)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the day moves at local midnight`() =
        runTest {
            val clock = SchedulerClock(lateEvening, testScheduler)

            DayClock(timeChanges = emptyFlow(), clock = clock, timeZone = { newYork }).observe().test {
                awaitItem()
                val tomorrow = awaitItem()
                assertEquals(LocalDate(2026, 10, 4), tomorrow.date)
                assertEquals(LocalDate(2026, 10, 4).atStartOfDayIn(newYork), clock.now())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a time zone change moves the day at once`() =
        runTest {
            var zone = newYork
            val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

            DayClock(timeChanges = changes, clock = SchedulerClock(lateEvening, testScheduler), timeZone = { zone }).observe().test {
                assertEquals(LocalDate(2026, 10, 3), awaitItem().date)

                zone = tokyo
                changes.tryEmit(Unit)

                val moved = awaitItem()
                assertEquals(LocalDate(2026, 10, 4), moved.date)
                assertEquals(tokyo, moved.timeZone)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a clock change inside the same day reports nothing new`() =
        runTest {
            val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

            DayClock(timeChanges = changes, clock = SchedulerClock(lateEvening, testScheduler), timeZone = { newYork }).observe().test {
                awaitItem()

                changes.tryEmit(Unit)
                testScheduler.runCurrent()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the day daylight saving time ends is 25 hours long`() {
        val fallBack = LocalDateTime(2026, 11, 1, 12, 0).toInstant(newYork)

        val day = DayClock(timeChanges = emptyFlow(), clock = FixedClock(fallBack), timeZone = { newYork }).current()

        assertEquals(25.hours, day.end - day.start)
    }

    @Test
    fun `the window includes its start but not its end`() {
        val day = DayClock(timeChanges = emptyFlow(), clock = FixedClock(lateEvening), timeZone = { newYork }).current()

        assertTrue(day.start in day)
        assertFalse(day.end in day)
    }
}

/** A clock that reads the test scheduler, so `delay` in the code under test moves it. */
private class SchedulerClock(
    private val start: Instant,
    private val scheduler: TestCoroutineScheduler,
) : Clock {
    override fun now(): Instant = start + scheduler.currentTime.milliseconds
}

private class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}
```

- [ ] **Step 4: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:domain:jvmTest --tests 'app.logdate.client.domain.today.*'
```

Expected: FAIL at `:client:domain:compileTestKotlinJvm` with `Unresolved reference 'MoodNoteTag'` and `Unresolved reference 'TodaySummaryCalculator'`.

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.today.DayClockTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'DayClock'`.

- [ ] **Step 5: Add the tag reader**

Create `client/domain/src/commonMain/kotlin/app/logdate/client/domain/today/MoodNoteTag.kt`:

```kotlin
package app.logdate.client.domain.today

import app.logdate.client.repository.journals.JournalNote

/**
 * Reads the tag a mood check-in starts with, such as `#mood:great Feeling great`.
 *
 * The watch saves a check-in as a text note whose first word is the tag; only the tag carries the
 * feeling. A check-in describes the day rather than being a moment of it, so summaries count it
 * apart from the moments.
 */
object MoodNoteTag {
    const val PREFIX = "#mood:"
    private val TAG = Regex("^#mood:(\\w+)")

    /** The tag [content] starts with, or null when it is not a check-in. */
    fun tagOf(content: String): String? = TAG.find(content)?.groupValues?.get(1)

    fun isFeeling(note: JournalNote): Boolean = note is JournalNote.Text && tagOf(note.content) != null

    /**
     * The tag of the newest check-in among [notes] that [isKnown] accepts, or null. A newer
     * check-in with a tag this version does not know, written by a later build, is skipped.
     */
    fun latestTag(
        notes: List<JournalNote>,
        isKnown: (String) -> Boolean = { true },
    ): String? =
        notes
            .filterIsInstance<JournalNote.Text>()
            .sortedByDescending(JournalNote::creationTimestamp)
            .firstNotNullOfOrNull { note -> tagOf(note.content)?.takeIf(isKnown) }
}
```

- [ ] **Step 6: Add the summary and its calculator**

Create `client/domain/src/commonMain/kotlin/app/logdate/client/domain/today/TodaySummary.kt`:

```kotlin
package app.logdate.client.domain.today

import app.logdate.client.domain.location.history.geographicDistance
import app.logdate.client.repository.journals.JournalNote
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * What a day contains so far, as Today, the Today tile and the phone's Watch page show it.
 *
 * @property momentCount Notes that are not mood check-ins.
 * @property placeCount Distinct places among the moments that have a location.
 * @property voiceDuration The length of every recording, added up.
 * @property latestMoment The newest moment, or null on a day without one.
 * @property feelingTag The tag of the newest mood check-in, or null.
 */
data class TodaySummary(
    val momentCount: Int,
    val placeCount: Int,
    val voiceDuration: Duration,
    val latestMoment: JournalNote?,
    val feelingTag: String?,
) {
    companion object {
        val Empty = TodaySummary(momentCount = 0, placeCount = 0, voiceDuration = Duration.ZERO, latestMoment = null, feelingTag = null)
    }
}

/**
 * Turns one day's notes into a [TodaySummary]. The caller passes only the day's notes.
 *
 * Two moments are at the same place when they name the same saved place, or when they were
 * captured within [SAME_PLACE_METERS] of each other.
 */
object TodaySummaryCalculator {
    const val SAME_PLACE_METERS = 150.0

    fun calculate(
        notes: List<JournalNote>,
        isKnownFeeling: (String) -> Boolean = { true },
    ): TodaySummary {
        val moments = notes.filterNot(MoodNoteTag::isFeeling)
        return TodaySummary(
            momentCount = moments.size,
            placeCount = countPlaces(moments),
            voiceDuration = moments.filterIsInstance<JournalNote.Audio>().sumOf { it.durationMs.coerceAtLeast(0) }.milliseconds,
            latestMoment = moments.maxByOrNull(JournalNote::creationTimestamp),
            feelingTag = MoodNoteTag.latestTag(notes, isKnownFeeling),
        )
    }

    private class PlaceCluster(
        val latitude: Double,
        val longitude: Double,
        val placeIds: MutableSet<Uuid>,
    )

    private fun countPlaces(moments: List<JournalNote>): Int {
        val clusters = mutableListOf<PlaceCluster>()
        moments.sortedBy(JournalNote::creationTimestamp).forEach { note ->
            val location = note.location ?: return@forEach
            val latitude = location.effectiveLatitude ?: return@forEach
            val longitude = location.effectiveLongitude ?: return@forEach
            val placeId = location.place?.id
            val cluster = clusters.firstOrNull { it.includes(placeId, latitude, longitude) }
            if (cluster == null) {
                clusters += PlaceCluster(latitude, longitude, listOfNotNull(placeId).toMutableSet())
                return@forEach
            }
            placeId?.let(cluster.placeIds::add)
        }
        return clusters.size
    }

    private fun PlaceCluster.includes(
        placeId: Uuid?,
        latitude: Double,
        longitude: Double,
    ): Boolean {
        if (placeId != null && placeId in placeIds) return true
        return geographicDistance(this.latitude, this.longitude, latitude, longitude) <= SAME_PLACE_METERS
    }
}
```

- [ ] **Step 7: Read check-ins through the shared tag reader on the watch**

Replace `app/wear/src/main/kotlin/app/logdate/wear/capture/MomentNoteCodec.kt` with:

```kotlin
package app.logdate.wear.capture

import app.logdate.client.domain.today.MoodNoteTag
import app.logdate.client.repository.journals.JournalNote

/** What a note on the watch holds, as the watch's summaries see it. */
enum class MomentNoteKind {
    /** A mood check-in: a text note that starts with a `#mood:` tag. */
    FEELING,
    TEXT,
    VOICE,
    PHOTO,
    VIDEO,
}

/**
 * Writes and reads the text note a mood check-in saves.
 *
 * A check-in is stored as a text note such as `#mood:great Feeling great`: the tag first, then a
 * sentence for anyone reading the note somewhere else, such as the phone's timeline. Only the tag
 * carries the feeling; the sentence is never parsed. Saved notes already use this format, so it
 * does not change. [MoodNoteTag] reads the tag for the watch and for shared summaries alike.
 */
object MomentNoteCodec {
    /** The text a check-in of [feeling] is saved as. */
    fun encode(feeling: Feeling): String = "${MoodNoteTag.PREFIX}${feeling.tag} Feeling ${feeling.tag}"

    /** The feeling [content] records, or null when it is not a check-in or its tag is unknown. */
    fun feelingOf(content: String): Feeling? = MoodNoteTag.tagOf(content)?.let(Feeling::fromTag)

    fun classify(note: JournalNote): MomentNoteKind =
        when (note) {
            is JournalNote.Text -> if (MoodNoteTag.isFeeling(note)) MomentNoteKind.FEELING else MomentNoteKind.TEXT
            is JournalNote.Audio -> MomentNoteKind.VOICE
            is JournalNote.Image -> MomentNoteKind.PHOTO
            is JournalNote.Video -> MomentNoteKind.VIDEO
        }

    /**
     * The feeling of the newest check-in among [notes] whose tag is known, or null. A newer check-in
     * with an unknown tag, written by a later build, is skipped.
     */
    fun latestFeeling(notes: List<JournalNote>): Feeling? =
        MoodNoteTag.latestTag(notes) { tag -> Feeling.fromTag(tag) != null }?.let(Feeling::fromTag)
}
```

- [ ] **Step 8: Add the day clock**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/today/DayClock.kt`:

```kotlin
package app.logdate.wear.v2.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The calendar day Today covers: [date] in [timeZone], from [start] up to, but not including, [end].
 * A day around a daylight saving change is 23 or 25 hours long.
 */
data class TodayWindow(
    val date: LocalDate,
    val timeZone: TimeZone,
    val start: Instant,
    val end: Instant,
) {
    operator fun contains(instant: Instant): Boolean = instant >= start && instant < end
}

/**
 * Reports the day Today covers, at once and again whenever it changes: at local midnight, and as
 * soon as [timeChanges] says the clock, date or time zone was changed.
 */
class DayClock(
    private val timeChanges: Flow<Unit>,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<TodayWindow> =
        merge(flowOf(Unit), timeChanges)
            .transformLatest {
                while (true) {
                    val today = current()
                    emit(today)
                    delay(today.end - clock.now())
                }
            }.distinctUntilChanged()

    fun current(): TodayWindow {
        val zone = timeZone()
        val date = clock.now().toLocalDateTime(zone).date
        return TodayWindow(
            date = date,
            timeZone = zone,
            start = date.atStartOfDayIn(zone),
            end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
        )
    }
}
```

- [ ] **Step 9: Add the system time signals**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TimeChangeSignals.kt`:

```kotlin
package app.logdate.wear.v2.today

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits whenever the system says the time, the date or the time zone changed, for as long as it is
 * collected. These are protected system broadcasts, so a receiver that is not exported still gets
 * them.
 */
fun timeChangeSignals(context: Context): Flow<Unit> =
    callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    trySend(Unit)
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }
```

- [ ] **Step 10: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:domain:jvmTest --tests 'app.logdate.client.domain.today.*' :client:domain:compileKotlinIosSimulatorArm64 :client:domain:ktlintCheck :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.today.DayClockTest' --tests 'app.logdate.wear.capture.*' --tests 'app.logdate.wear.complication.MoodComplicationTextTest' --tests 'app.logdate.wear.tile.TodaySummaryTest' :app:wear:ktlintCheck
```

Expected: `BUILD SUCCESSFUL`. The domain runs 15 tests; the watch runs `DayClockTest` (6) and every existing `MomentNoteCodecTest`, `MoodComplicationTextTest` and `TodaySummaryTest` case unchanged.

- [ ] **Step 11: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task5-commit.txt <<'EOF'
chore(wear): add the Today summary and a midnight-aware day clock

TodaySummaryCalculator counts a day's moments, places and voice time
and picks the latest moment and feeling, in shared code so the watch,
its Today tile and the phone's Watch page agree. Check-ins are not
moments. Two moments share a place when they name the same saved
place or were captured within 150 m of each other.

MoodNoteTag moves the #mood: reader from the watch into shared code;
MomentNoteCodec reads through it and behaves as before. DayClock
reports the calendar day the new watch Today covers and moves at
local midnight or at once when the time, date or zone changes.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 12: Commit**

```bash
git restore --staged . && git add client/domain/src/commonMain/kotlin/app/logdate/client/domain/today/MoodNoteTag.kt client/domain/src/commonMain/kotlin/app/logdate/client/domain/today/TodaySummary.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/today/MoodNoteTagTest.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/today/TodaySummaryCalculatorTest.kt app/wear/src/main/kotlin/app/logdate/wear/capture/MomentNoteCodec.kt app/wear/src/main/kotlin/app/logdate/wear/v2/today/DayClock.kt app/wear/src/main/kotlin/app/logdate/wear/v2/today/TimeChangeSignals.kt app/wear/src/test/kotlin/app/logdate/wear/v2/today/DayClockTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task5-commit.txt
```

---

### Task 6: The watch campfire, phone link and phone handoff (`chore(wear)`)

Three sources feed Today besides its notes.

- **Campfire.** `ObserveCampfireUseCase` (`client/domain/src/commonMain/kotlin/app/logdate/client/domain/streak/ObserveCampfireUseCase.kt:35-42`) needs a `StreakSettingsRepository` and a `LogdatePreferencesDataSource`, neither of which the watch's Koin graph binds; `StreakComplicationService.kt:75-117` works around that by building the calculator by hand. On the watch the campfire's switch is `FeatureFlag.CAMPFIRE_STREAKS` (`FeatureFlag.kt:51`), and the day-start hour comes from the phone once the shared state document exists (Track A, C2); until then there is none and the use case falls back to `DEFAULT_STREAK_DAY_START_HOUR` (4 AM, `StreakDay.kt:20`).
- **Phone link.** Whether a phone with LogDate is in reach, and whether an Android phone lacks LogDate. The phone advertises `logdate_phone_app` (`WearDataLayerClient.PHONE_CAPABILITY`, `WearDataLayerClient.kt:72`); `PhoneTypeHelper` says whether the paired phone runs Android. iPhones are never offered the Play listing.
- **Handoff.** `RemoteActivityHelper` opens a link on the phone. It accepts only a browsable `ACTION_VIEW` with a data URI, as the phone's own `watchLinkIntent` (`AndroidWatchConnectionManager.kt:41-44`) learned.

**Files:**
- Modify: `app/wear/build.gradle.kts:135` (add `client:healthConnect`, home of `LogdatePreferencesDataSource`; `client:domain` already brings it at runtime)
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/today/WatchCampfire.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/phone/PhoneLink.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/phone/PhoneHandoff.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/today/WatchCampfireTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/phone/PhoneLinkRepositoryTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/phone/PhoneHandoffTest.kt`

**Interfaces:**
- Consumes: `ObserveCampfireUseCase(notesRepository, streakSettingsRepository, dayBoundaryPreferences, clock, timeZone, computeDispatcher)`; `FeatureFlagStore.observe(flag)`/`isEnabled(flag)`; `WearDataLayerClient.PHONE_CAPABILITY`.
- Produces:
  - `class WatchStreakSettings(featureFlags: FeatureFlagStore) : StreakSettingsRepository` and `class WatchDayBoundaryPreferences(phoneDayStartHour: suspend () -> Int?) : LogdatePreferencesDataSource` in `app.logdate.wear.v2.today`.
  - `sealed interface PhoneLink { data class Connected(nodeId: String, phoneName: String); data object NotConnected; data class AppMissing(nodeId: String); data object Unsupported }` and `class PhoneLinkRepository(capabilityClient: CapabilityClient, nodeClient: NodeClient, phoneType: () -> Int, ioDispatcher: CoroutineDispatcher)` with `fun observe(): Flow<PhoneLink>` and `suspend fun current(): PhoneLink`, in `app.logdate.wear.v2.phone`.
  - `enum class HandoffResult { SENT, FAILED }`, `class PhoneHandoff(remoteActivityHelper: RemoteActivityHelper, packageName: String, ioDispatcher: CoroutineDispatcher, viewIntent: (String) -> Intent = ::phoneViewIntent)` with `suspend fun openAppListing(nodeId: String): HandoffResult` and `suspend fun open(link: String, nodeId: String): HandoffResult`, and `fun phoneViewIntent(link: String): Intent`.

- [ ] **Step 1: Write the failing campfire test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/today/WatchCampfireTest.kt`:

```kotlin
package app.logdate.wear.v2.today

import app.cash.turbine.test
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.ObserveCampfireUseCase
import app.logdate.client.repository.journals.JournalNotesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Runs the shared campfire use case with the watch's adapters: the campfire flag as the streak
 * switch, and the phone's day-start hour, or 4 AM when the phone has not shared one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchCampfireTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val noon = at(hour = 12)
    private val flags = mockk<FeatureFlagStore>()
    private val notes = mockk<JournalNotesRepository>()

    @Test
    fun `something saved today lights the fire`() =
        runTest {
            campfireOn(true)
            every { notes.observeEntryTimestamps() } returns flowOf(listOf(at(hour = 10)))

            campfire().invoke().test {
                val fire = awaitItem()
                assertEquals(FirePhase.BURNING, fire?.phase)
                assertTrue(fire!!.loggedToday)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `no fire is shown while campfire streaks are off`() =
        runTest {
            campfireOn(false)
            every { notes.observeEntryTimestamps() } returns flowOf(listOf(at(hour = 10)))

            campfire().invoke().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `without a day start from the phone, 3 AM counts toward yesterday`() =
        runTest {
            campfireOn(true)
            every { notes.observeEntryTimestamps() } returns flowOf(listOf(at(hour = 3)))

            campfire(phoneDayStartHour = null).invoke().test {
                assertFalse(awaitItem()!!.loggedToday)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the phone's day start moves the boundary`() =
        runTest {
            campfireOn(true)
            every { notes.observeEntryTimestamps() } returns flowOf(listOf(at(hour = 3)))

            campfire(phoneDayStartHour = 2).invoke().test {
                assertTrue(awaitItem()!!.loggedToday)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the watch never changes the streak setting`() =
        runTest {
            assertFailsWith<UnsupportedOperationException> { WatchStreakSettings(flags).setStreakEnabled(false) }
        }

    private fun campfireOn(enabled: Boolean) {
        every { flags.observe(FeatureFlag.CAMPFIRE_STREAKS) } returns flowOf(enabled)
    }

    private fun TestScope.campfire(phoneDayStartHour: Int? = null) =
        ObserveCampfireUseCase(
            notesRepository = notes,
            streakSettingsRepository = WatchStreakSettings(flags),
            dayBoundaryPreferences = WatchDayBoundaryPreferences { phoneDayStartHour },
            clock = FixedClock(noon),
            timeZone = { zone },
            computeDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

    private fun at(hour: Int): Instant = LocalDateTime(2026, 10, 3, hour, 0).toInstant(zone)

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }
}
```

- [ ] **Step 2: Write the failing phone link test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/phone/PhoneLinkRepositoryTest.kt`:

```kotlin
package app.logdate.wear.v2.phone

import androidx.wear.phone.interactions.PhoneTypeHelper
import app.cash.turbine.test
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneLinkRepositoryTest {
    private val capabilityClient = mockk<CapabilityClient>()
    private val nodeClient = mockk<NodeClient>()
    private val listener = slot<CapabilityClient.OnCapabilityChangedListener>()
    private var phoneType = PhoneTypeHelper.DEVICE_TYPE_ANDROID
    private val repository =
        PhoneLinkRepository(
            capabilityClient = capabilityClient,
            nodeClient = nodeClient,
            phoneType = { phoneType },
            ioDispatcher = UnconfinedTestDispatcher(),
        )

    init {
        every { capabilityClient.addListener(capture(listener), WearDataLayerClient.PHONE_CAPABILITY) } returns Tasks.forResult<Void>(null)
        every { capabilityClient.removeListener(any(), WearDataLayerClient.PHONE_CAPABILITY) } returns Tasks.forResult(true)
        reachable()
        connected()
    }

    @Test
    fun `a reachable phone with LogDate is connected`() =
        runTest {
            reachable(node("phone-1", "Pixel 9"))

            assertEquals(PhoneLink.Connected(nodeId = "phone-1", phoneName = "Pixel 9"), repository.current())
        }

    @Test
    fun `a phone nearby is preferred over one reached through the cloud`() =
        runTest {
            reachable(node("cloud-phone", "Pixel 8", nearby = false), node("near-phone", "Pixel 9"))

            assertEquals(PhoneLink.Connected(nodeId = "near-phone", phoneName = "Pixel 9"), repository.current())
        }

    @Test
    fun `a connected Android phone without LogDate is missing the app`() =
        runTest {
            connected(node("phone-1", "Pixel 9"))

            assertEquals(PhoneLink.AppMissing(nodeId = "phone-1"), repository.current())
        }

    @Test
    fun `an iPhone is never asked to install LogDate`() =
        runTest {
            phoneType = PhoneTypeHelper.DEVICE_TYPE_IOS
            connected(node("iphone", "iPhone"))

            assertEquals(PhoneLink.Unsupported, repository.current())
        }

    @Test
    fun `a phone of unknown type without LogDate is not offered the app`() =
        runTest {
            phoneType = PhoneTypeHelper.DEVICE_TYPE_UNKNOWN
            connected(node("phone-1", "Phone"))

            assertEquals(PhoneLink.NotConnected, repository.current())
        }

    @Test
    fun `no phone in reach is not connected`() =
        runTest {
            assertEquals(PhoneLink.NotConnected, repository.current())
        }

    @Test
    fun `a failed lookup is reported as not connected`() =
        runTest {
            every {
                capabilityClient.getCapability(WearDataLayerClient.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            } returns Tasks.forException(IllegalStateException("Wearable API unavailable"))

            assertEquals(PhoneLink.NotConnected, repository.current())
        }

    @Test
    fun `LogDate arriving on the phone is noticed without polling`() =
        runTest {
            connected(node("phone-1", "Pixel 9"))

            repository.observe().test {
                assertEquals(PhoneLink.AppMissing(nodeId = "phone-1"), awaitItem())

                reachable(node("phone-1", "Pixel 9"))
                listener.captured.onCapabilityChanged(mockk<CapabilityInfo>())

                assertEquals(PhoneLink.Connected(nodeId = "phone-1", phoneName = "Pixel 9"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun reachable(vararg phones: Node) {
        val info = mockk<CapabilityInfo> { every { nodes } returns phones.toSet() }
        every {
            capabilityClient.getCapability(WearDataLayerClient.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        } returns Tasks.forResult(info)
    }

    private fun connected(vararg phones: Node) {
        every { nodeClient.connectedNodes } returns Tasks.forResult(phones.toList())
    }

    private fun node(
        nodeId: String,
        name: String,
        nearby: Boolean = true,
    ) = mockk<Node> {
        every { id } returns nodeId
        every { displayName } returns name
        every { isNearby } returns nearby
    }
}
```

- [ ] **Step 3: Write the failing handoff test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/phone/PhoneHandoffTest.kt`:

```kotlin
package app.logdate.wear.v2.phone

import android.content.Intent
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneHandoffTest {
    private val remoteActivityHelper = mockk<RemoteActivityHelper>()
    private val intent = mockk<Intent>()
    private val sentLinks = mutableListOf<String>()
    private val handoff =
        PhoneHandoff(
            remoteActivityHelper = remoteActivityHelper,
            packageName = "studio.hypertext.logdate",
            ioDispatcher = UnconfinedTestDispatcher(),
            viewIntent = { link ->
                sentLinks += link
                intent
            },
        )

    @Test
    fun `the Play listing opens on the phone that lacks LogDate`() =
        runTest {
            val accepted = mockk<ListenableFuture<Void>> { every { get(any(), any()) } returns null }
            every { remoteActivityHelper.startRemoteActivity(intent, "phone-1") } returns accepted

            assertEquals(HandoffResult.SENT, handoff.openAppListing("phone-1"))
            assertEquals(listOf("market://details?id=studio.hypertext.logdate"), sentLinks)
        }

    @Test
    fun `a phone that refuses the link is reported`() =
        runTest {
            val refused = mockk<ListenableFuture<Void>> { every { get(any(), any()) } throws ExecutionException(IllegalStateException("No handler")) }
            every { remoteActivityHelper.startRemoteActivity(intent, "phone-1") } returns refused

            assertEquals(HandoffResult.FAILED, handoff.open("studio.hypertext.logdate://settings/watch", "phone-1"))
        }

    @Test
    fun `a phone that never answers is reported`() =
        runTest {
            val silent = mockk<ListenableFuture<Void>> { every { get(any(), any()) } throws TimeoutException() }
            every { remoteActivityHelper.startRemoteActivity(intent, "phone-1") } returns silent

            assertEquals(HandoffResult.FAILED, handoff.openAppListing("phone-1"))
        }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.today.WatchCampfireTest' --tests 'app.logdate.wear.v2.phone.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'WatchStreakSettings'`, `Unresolved reference 'PhoneLinkRepository'` and `Unresolved reference 'PhoneHandoff'`.

- [ ] **Step 5: Add the `client:healthConnect` dependency**

In `app/wear/build.gradle.kts`, after line 135 (`implementation(projects.client.domain)`), add:

```kotlin
    implementation(projects.client.healthConnect)
```

- [ ] **Step 6: Add the campfire adapters**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/today/WatchCampfire.kt`:

```kotlin
package app.logdate.wear.v2.today

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.health.util.LogdatePreferencesDataSource
import app.logdate.client.health.util.UserPreferences
import app.logdate.client.repository.streak.StreakSettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * The streak switch the shared campfire reads, on the watch. The watch shows the campfire while
 * [FeatureFlag.CAMPFIRE_STREAKS] is on. The streak setting itself belongs to the phone, so the
 * watch never writes it and stores no cached streak.
 */
class WatchStreakSettings(
    private val featureFlags: FeatureFlagStore,
) : StreakSettingsRepository {
    override fun observeStreakEnabled(): Flow<Boolean> = featureFlags.observe(FeatureFlag.CAMPFIRE_STREAKS)

    override suspend fun isStreakEnabled(): Boolean = featureFlags.isEnabled(FeatureFlag.CAMPFIRE_STREAKS)

    override suspend fun setStreakEnabled(enabled: Boolean): Unit = throw UnsupportedOperationException(PHONE_OWNED)

    override fun observeCachedStreak(): Flow<Int> = throw UnsupportedOperationException(PHONE_OWNED)

    override suspend fun getCachedStreak(): Int = throw UnsupportedOperationException(PHONE_OWNED)

    override suspend fun setCachedStreak(value: Int): Unit = throw UnsupportedOperationException(PHONE_OWNED)

    private companion object {
        const val PHONE_OWNED = "The streak setting belongs to the phone"
    }
}

/**
 * The day-start hour the shared campfire reads, on the watch: the phone's, from [phoneDayStartHour].
 * Until the phone shares it there is none, and the campfire's 4 AM default applies.
 */
class WatchDayBoundaryPreferences(
    private val phoneDayStartHour: suspend () -> Int?,
) : LogdatePreferencesDataSource {
    override suspend fun getPreferences(): UserPreferences = UserPreferences(dayStartHour = phoneDayStartHour())
}
```

- [ ] **Step 7: Add the phone link**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/phone/PhoneLink.kt`:

```kotlin
package app.logdate.wear.v2.phone

import androidx.wear.phone.interactions.PhoneTypeHelper
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Where the watch stands with the phone it is paired with. */
sealed interface PhoneLink {
    /** A phone running LogDate is in reach. */
    data class Connected(
        val nodeId: String,
        val phoneName: String,
    ) : PhoneLink

    /** No phone running LogDate is in reach, or the watch cannot tell. */
    data object NotConnected : PhoneLink

    /** The connected phone runs Android but not LogDate. */
    data class AppMissing(
        val nodeId: String,
    ) : PhoneLink

    /** The paired phone is an iPhone, which LogDate does not run on. */
    data object Unsupported : PhoneLink
}

/**
 * Follows [PhoneLink] without polling: Play services announces each change to the phone's
 * [WearDataLayerClient.PHONE_CAPABILITY], and the link is looked up again on each one.
 * [phoneType] is `PhoneTypeHelper.getPhoneDeviceType(context)` outside tests.
 */
class PhoneLinkRepository(
    private val capabilityClient: CapabilityClient,
    private val nodeClient: NodeClient,
    private val phoneType: () -> Int,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observe(): Flow<PhoneLink> =
        callbackFlow {
            val listener = CapabilityClient.OnCapabilityChangedListener { launch { send(current()) } }
            val listening = startListening(listener)
            send(current())
            awaitClose {
                if (listening) capabilityClient.removeListener(listener, WearDataLayerClient.PHONE_CAPABILITY)
            }
        }.distinctUntilChanged()

    suspend fun current(): PhoneLink =
        withContext(ioDispatcher) {
            try {
                lookUp()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Couldn't look up the paired phone; reporting it as not connected", e)
                PhoneLink.NotConnected
            }
        }

    private suspend fun lookUp(): PhoneLink {
        val type = phoneType()
        if (type == PhoneTypeHelper.DEVICE_TYPE_IOS) return PhoneLink.Unsupported
        val withLogDate =
            capabilityClient
                .getCapability(WearDataLayerClient.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        val phone = withLogDate.firstOrNull(Node::isNearby) ?: withLogDate.firstOrNull()
        if (phone != null) return PhoneLink.Connected(nodeId = phone.id, phoneName = phone.displayName)
        val connected = nodeClient.connectedNodes.await()
        val paired = connected.firstOrNull(Node::isNearby) ?: connected.firstOrNull() ?: return PhoneLink.NotConnected
        if (type != PhoneTypeHelper.DEVICE_TYPE_ANDROID) return PhoneLink.NotConnected
        return PhoneLink.AppMissing(nodeId = paired.id)
    }

    private suspend fun startListening(listener: CapabilityClient.OnCapabilityChangedListener): Boolean =
        try {
            capabilityClient.addListener(listener, WearDataLayerClient.PHONE_CAPABILITY).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't follow the phone's LogDate install; the phone link is reported as of now only", e)
            false
        }
}
```

- [ ] **Step 8: Add the handoff**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/phone/PhoneHandoff.kt`:

```kotlin
package app.logdate.wear.v2.phone

import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Whether the phone accepted a link the watch sent it. */
enum class HandoffResult {
    SENT,
    FAILED,
}

/**
 * Opens links on the phone from the watch. [packageName] is the watch's own, which the phone app
 * shares.
 */
class PhoneHandoff(
    private val remoteActivityHelper: RemoteActivityHelper,
    private val packageName: String,
    private val ioDispatcher: CoroutineDispatcher,
    private val viewIntent: (String) -> Intent = ::phoneViewIntent,
) {
    /** Opens LogDate's Play listing on the phone [nodeId]. */
    suspend fun openAppListing(nodeId: String): HandoffResult = open("$PLAY_LISTING$packageName", nodeId)

    suspend fun open(
        link: String,
        nodeId: String,
    ): HandoffResult =
        try {
            withContext(ioDispatcher) {
                remoteActivityHelper.startRemoteActivity(viewIntent(link), nodeId).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            HandoffResult.SENT
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't open $link on the phone", e)
            HandoffResult.FAILED
        }

    private companion object {
        const val PLAY_LISTING = "market://details?id="
        const val TIMEOUT_SECONDS = 10L
    }
}

/** The only kind of intent `RemoteActivityHelper` sends: a browsable view of [link]. */
fun phoneViewIntent(link: String): Intent =
    Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setData(Uri.parse(link))
```

- [ ] **Step 9: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.today.WatchCampfireTest' --tests 'app.logdate.wear.v2.phone.*' :app:wear:ktlintCheck :app:wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; 5 + 8 + 3 = 16 tests pass.

- [ ] **Step 10: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task6-commit.txt <<'EOF'
chore(wear): add the watch campfire, phone link and phone handoff

The new watch Today needs three things besides its notes. The shared
campfire use case now runs on the watch: the campfire flag is its
switch, and the day starts at the phone's hour once the phone shares
it, 4 AM until then. PhoneLinkRepository follows whether a phone with
LogDate is in reach, or whether a connected Android phone lacks it;
iPhones are never offered the app. PhoneHandoff opens a link on the
phone, such as LogDate's Play listing.

Nothing shows them yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add app/wear/build.gradle.kts app/wear/src/main/kotlin/app/logdate/wear/v2/today/WatchCampfire.kt app/wear/src/main/kotlin/app/logdate/wear/v2/phone/PhoneLink.kt app/wear/src/main/kotlin/app/logdate/wear/v2/phone/PhoneHandoff.kt app/wear/src/test/kotlin/app/logdate/wear/v2/today/WatchCampfireTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/phone/PhoneLinkRepositoryTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/phone/PhoneHandoffTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task6-commit.txt
```

---
### Task 7: The Today page (`chore(wear)`)

Today shows the date in `TimeText`, the campfire, the summary line, the latest moment, and a card offering LogDate when the paired Android phone lacks it. Record (the `EdgeButton`), Mark and + (the `ButtonGroup`) are part of the page's layout but are drawn only when `TodayControls` carries a handler; the recorder (B3) and capture (B4) plans set them. Nothing in this plan does, so a dogfooder never sees a control that does nothing, while screenshots of the controls fix their layout now.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayUiState.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/today/CampfireMapping.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayViewModel.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayPage.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/ClockDuration.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentText.kt`
- Create: `app/wear/src/main/res/values/strings_home.xml`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (whole file)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/today/TodayViewModelTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/moments/ClockDurationTest.kt`
- Screenshot: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WearLargeFontPreview.kt`
- Screenshot: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/TodayPageScreenshots.kt`
- References: `app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/TodayPageScreenshots/` (new, 20 images)

**Interfaces:**
- Consumes (Task 1): `LogDateGlyph.MARK`, `LogDateGlyph.RECORD`, `LogDateGlyph.vector()`. (Task 4): `LogDateWearTheme(dynamicColor)`. (Task 5): `TodaySummary`, `TodaySummaryCalculator.calculate(notes, isKnownFeeling)`, `DayClock.observe()`, `TodayWindow`, `timeChangeSignals(context)`. (Task 6): `WatchStreakSettings`, `WatchDayBoundaryPreferences`, `PhoneLinkRepository.observe()`, `PhoneLink.AppMissing(nodeId)`, `PhoneHandoff.openAppListing(nodeId): HandoffResult`. Shared: `Campfire(phase, size, modifier, waitingForToday, contentDescription)`, `campfireShortDescription(presentation)`, `CampfirePresentation`.
- Produces:
  - `sealed interface TodayUiState { data object Loading; data class Ready(date: LocalDate, timeZone: TimeZone, summary: TodaySummary, feeling: Feeling?, campfire: CampfirePresentation?, phoneCard: PhoneCard); data object Failed }`, `sealed interface PhoneCard { data object None; data class GetApp(nodeId: String) }`, `enum class HandoffState { IDLE, SENDING, SENT, FAILED }`.
  - `class TodayViewModel(dayClock: DayClock, notesRepository: JournalNotesRepository, observeCampfire: ObserveCampfireUseCase, phoneLinks: PhoneLinkRepository, phoneHandoff: PhoneHandoff) : ViewModel` with `val uiState: StateFlow<TodayUiState>`, `val handoff: StateFlow<HandoffState>`, `fun getPhoneApp()`, `fun dismissHandoff()`.
  - `data class TodayControls(onRecord: (() -> Unit)? = null, onMark: (() -> Unit)? = null, onAdd: (() -> Unit)? = null)`, `object TodayTags { PAGE, CAMPFIRE, SUMMARY, LATEST, GET_PHONE_APP, MARK, ADD, RECORD }` and `@Composable fun TodayPage(state: TodayUiState, handoff: HandoffState, onGetPhoneApp: () -> Unit, onHandoffDismissed: () -> Unit, modifier: Modifier = Modifier, controls: TodayControls = TodayControls(), listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(), timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()))`.
  - `fun formatClockDuration(duration: Duration, locale: Locale = Locale.getDefault()): String`, `@Composable fun momentLabel(note: JournalNote): String`, `@Composable fun momentClockTime(instant: Instant, timeZone: TimeZone): String` in `app.logdate.wear.v2.moments`.
  - Koin: `DayClock`, `ObserveCampfireUseCase`, `PhoneLinkRepository`, `PhoneHandoff` singletons and the `TodayViewModel` view model in `wearV2Module`.

- [ ] **Step 1: Write the failing view model test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/today/TodayViewModelTest.kt`:

```kotlin
package app.logdate.wear.v2.today

import app.cash.turbine.test
import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.FireSize
import app.logdate.client.domain.streak.ObserveCampfireUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.CampfireSize
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.phone.HandoffResult
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLink
import app.logdate.wear.v2.phone.PhoneLinkRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val saturday = window(LocalDate(2026, 10, 3))
    private val sunday = window(LocalDate(2026, 10, 4))
    private val dayClock = mockk<DayClock>()
    private val notesRepository = mockk<JournalNotesRepository>()
    private val observeCampfire = mockk<ObserveCampfireUseCase>()
    private val phoneLinks = mockk<PhoneLinkRepository>()
    private val phoneHandoff = mockk<PhoneHandoff>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { dayClock.observe() } returns flowOf(saturday)
        every { observeCampfire.invoke() } returns flowOf(null)
        every { phoneLinks.observe() } returns flowOf(PhoneLink.NotConnected)
        notesFor(saturday)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the day's notes become today's summary`() =
        runTest {
            notesFor(
                saturday,
                text("Lunch by the water", at(saturday, hour = 12)),
                JournalNote.Audio(mediaRef = "audio/walk.m4a", durationMs = 252_000, creationTimestamp = at(saturday, 14), lastUpdated = at(saturday, 14)),
                text("#mood:good Feeling good", at(saturday, hour = 15)),
            )

            viewModel().uiState.test {
                val today = expectMostRecentItem() as TodayUiState.Ready
                assertEquals(LocalDate(2026, 10, 3), today.date)
                assertEquals(2, today.summary.momentCount)
                assertEquals(252.seconds, today.summary.voiceDuration)
                assertEquals(Feeling.GOOD, today.feeling)
            }
        }

    @Test
    fun `notes from another day are left out`() =
        runTest {
            notesFor(saturday, text("Friday night", saturday.start - 1.hours), text("Breakfast", at(saturday, hour = 8)))

            viewModel().uiState.test {
                assertEquals(1, (expectMostRecentItem() as TodayUiState.Ready).summary.momentCount)
            }
        }

    @Test
    fun `the summary moves to the next day at midnight`() =
        runTest {
            every { dayClock.observe() } returns flowOf(saturday, sunday)
            notesFor(saturday, text("Saturday", at(saturday, hour = 10)))
            notesFor(sunday)

            viewModel().uiState.test {
                val today = expectMostRecentItem() as TodayUiState.Ready
                assertEquals(LocalDate(2026, 10, 4), today.date)
                assertEquals(0, today.summary.momentCount)
            }
        }

    @Test
    fun `the campfire follows the shared campfire`() =
        runTest {
            every { observeCampfire.invoke() } returns
                flowOf(
                    CampfireState(
                        phase = FirePhase.BURNING,
                        loggedToday = true,
                        runDays = 3,
                        size = FireSize.SMALL,
                        longestRunDays = 5,
                        totalDaysJournaled = 9,
                        isRekindled = false,
                    ),
                )

            viewModel().uiState.test {
                assertEquals(
                    CampfirePresentation(
                        phase = CampfirePhase.BURNING,
                        loggedToday = true,
                        runDays = 3,
                        size = CampfireSize.SMALL,
                        longestRunDays = 5,
                        totalDaysJournaled = 9,
                        isRekindled = false,
                    ),
                    (expectMostRecentItem() as TodayUiState.Ready).campfire,
                )
            }
        }

    @Test
    fun `no campfire is shown while streaks are off`() =
        runTest {
            viewModel().uiState.test {
                assertNull((expectMostRecentItem() as TodayUiState.Ready).campfire)
            }
        }

    @Test
    fun `an Android phone without LogDate is offered the app`() =
        runTest {
            every { phoneLinks.observe() } returns flowOf(PhoneLink.AppMissing(nodeId = "phone-1"))

            viewModel().uiState.test {
                assertEquals(PhoneCard.GetApp(nodeId = "phone-1"), (expectMostRecentItem() as TodayUiState.Ready).phoneCard)
            }
        }

    @Test
    fun `a phone that has LogDate needs no card`() =
        runTest {
            every { phoneLinks.observe() } returns flowOf(PhoneLink.Connected(nodeId = "phone-1", phoneName = "Pixel 9"))

            viewModel().uiState.test {
                assertEquals(PhoneCard.None, (expectMostRecentItem() as TodayUiState.Ready).phoneCard)
            }
        }

    @Test
    fun `getting the app opens its listing on that phone`() =
        runTest {
            every { phoneLinks.observe() } returns flowOf(PhoneLink.AppMissing(nodeId = "phone-1"))
            coEvery { phoneHandoff.openAppListing("phone-1") } returns HandoffResult.SENT
            val viewModel = viewModel()

            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.getPhoneApp()

                assertEquals(HandoffState.SENT, viewModel.handoff.value)
                coVerify(exactly = 1) { phoneHandoff.openAppListing("phone-1") }
            }
        }

    @Test
    fun `a refused handoff is reported until dismissed`() =
        runTest {
            every { phoneLinks.observe() } returns flowOf(PhoneLink.AppMissing(nodeId = "phone-1"))
            coEvery { phoneHandoff.openAppListing("phone-1") } returns HandoffResult.FAILED
            val viewModel = viewModel()

            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.getPhoneApp()
                assertEquals(HandoffState.FAILED, viewModel.handoff.value)

                viewModel.dismissHandoff()
                assertEquals(HandoffState.IDLE, viewModel.handoff.value)
            }
        }

    @Test
    fun `a failure reading notes shows the failed state`() =
        runTest {
            every { notesRepository.observeNotesInRange(any(), any()) } returns flow { throw IOException("database closed") }

            viewModel().uiState.test {
                assertEquals(TodayUiState.Failed, expectMostRecentItem())
            }
        }

    private fun viewModel() =
        TodayViewModel(
            dayClock = dayClock,
            notesRepository = notesRepository,
            observeCampfire = observeCampfire,
            phoneLinks = phoneLinks,
            phoneHandoff = phoneHandoff,
        )

    private fun notesFor(
        day: TodayWindow,
        vararg notes: JournalNote,
    ) {
        every { notesRepository.observeNotesInRange(day.start, day.end) } returns flowOf(notes.toList())
    }

    private fun window(date: LocalDate) =
        TodayWindow(
            date = date,
            timeZone = zone,
            start = date.atStartOfDayIn(zone),
            end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
        )

    private fun at(
        day: TodayWindow,
        hour: Int,
    ): Instant = LocalDateTime(day.date, LocalTime(hour, 0)).toInstant(zone)

    private fun text(
        content: String,
        at: Instant,
    ) = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = content)
}
```

- [ ] **Step 2: Write the failing duration test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/moments/ClockDurationTest.kt`:

```kotlin
package app.logdate.wear.v2.moments

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ClockDurationTest {
    @Test
    fun `under an hour reads as minutes and seconds`() {
        assertEquals("4:12", formatClockDuration(252.seconds, Locale.US))
    }

    @Test
    fun `an hour or more adds the hours`() {
        assertEquals("1:04:12", formatClockDuration(1.hours + 4.minutes + 12.seconds, Locale.US))
    }

    @Test
    fun `nothing reads as zero`() {
        assertEquals("0:00", formatClockDuration(Duration.ZERO, Locale.US))
    }

    @Test
    fun `a negative length reads as zero`() {
        assertEquals("0:00", formatClockDuration((-5).seconds, Locale.US))
    }

    @Test
    fun `parts of a second are dropped`() {
        assertEquals("0:01", formatClockDuration(1_999.milliseconds, Locale.US))
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.today.TodayViewModelTest' --tests 'app.logdate.wear.v2.moments.ClockDurationTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'TodayViewModel'`, `Unresolved reference 'TodayUiState'` and `Unresolved reference 'formatClockDuration'`.

- [ ] **Step 4: Add the UI state**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayUiState.kt`:

```kotlin
package app.logdate.wear.v2.today

import app.logdate.client.domain.today.TodaySummary
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.wear.capture.Feeling
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** What the Today page shows. */
sealed interface TodayUiState {
    data object Loading : TodayUiState

    /**
     * @property campfire The journaling fire, or null while campfire streaks are off.
     * @property feeling The newest mood check-in today, or null.
     */
    data class Ready(
        val date: LocalDate,
        val timeZone: TimeZone,
        val summary: TodaySummary,
        val feeling: Feeling?,
        val campfire: CampfirePresentation?,
        val phoneCard: PhoneCard,
    ) : TodayUiState

    /** Today's notes could not be read. */
    data object Failed : TodayUiState
}

/** The card Today shows about the phone. */
sealed interface PhoneCard {
    data object None : PhoneCard

    /** The paired Android phone lacks LogDate; the card opens its Play listing on [nodeId]. */
    data class GetApp(
        val nodeId: String,
    ) : PhoneCard
}

/** Where a request to open something on the phone stands. */
enum class HandoffState {
    IDLE,
    SENDING,
    SENT,
    FAILED,
}
```

- [ ] **Step 5: Add the campfire mapping**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/today/CampfireMapping.kt`. It matches the phone's internal mapping in `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/streak/CampfireViewModel.kt:64-86`, which the watch cannot reach:

```kotlin
package app.logdate.wear.v2.today

import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.FireSize
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.CampfireSize

internal fun CampfireState.toPresentation(): CampfirePresentation =
    CampfirePresentation(
        phase =
            when (phase) {
                FirePhase.UNLIT -> CampfirePhase.UNLIT
                FirePhase.BURNING -> CampfirePhase.BURNING
                FirePhase.EMBERS -> CampfirePhase.EMBERS
                FirePhase.OUT -> CampfirePhase.OUT
            },
        loggedToday = loggedToday,
        runDays = runDays,
        size =
            when (size) {
                null -> null
                FireSize.SPARK -> CampfireSize.SPARK
                FireSize.SMALL -> CampfireSize.SMALL
                FireSize.CAMPFIRE -> CampfireSize.CAMPFIRE
                FireSize.BONFIRE -> CampfireSize.BONFIRE
                FireSize.BEACON -> CampfireSize.BEACON
            },
        longestRunDays = longestRunDays,
        totalDaysJournaled = totalDaysJournaled,
        isRekindled = isRekindled,
    )
```

- [ ] **Step 6: Add the view model**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayViewModel.kt`:

```kotlin
package app.logdate.wear.v2.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.ObserveCampfireUseCase
import app.logdate.client.domain.today.TodaySummaryCalculator
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.phone.HandoffResult
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLink
import app.logdate.wear.v2.phone.PhoneLinkRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Today on the watch: the day's moments, places and voice time, the campfire, and an offer to get
 * LogDate on a paired Android phone that lacks it. The day moves at midnight and when the time
 * zone changes.
 */
class TodayViewModel(
    dayClock: DayClock,
    notesRepository: JournalNotesRepository,
    observeCampfire: ObserveCampfireUseCase,
    phoneLinks: PhoneLinkRepository,
    private val phoneHandoff: PhoneHandoff,
) : ViewModel() {
    private val mutableHandoff = MutableStateFlow(HandoffState.IDLE)

    val handoff: StateFlow<HandoffState> = mutableHandoff.asStateFlow()

    val uiState: StateFlow<TodayUiState> =
        combine(observeDay(dayClock, notesRepository), observeCampfire(), phoneLinks.observe()) { day, campfire, phone ->
            readyState(day, campfire, phone)
        }.catch { error ->
            Napier.e("Couldn't load today on the watch", error)
            emit(TodayUiState.Failed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayUiState.Loading)

    /** Opens LogDate's Play listing on the phone Today offered it for. */
    fun getPhoneApp() {
        val card = (uiState.value as? TodayUiState.Ready)?.phoneCard as? PhoneCard.GetApp ?: return
        if (mutableHandoff.value == HandoffState.SENDING) return
        mutableHandoff.value = HandoffState.SENDING
        viewModelScope.launch {
            mutableHandoff.value =
                when (phoneHandoff.openAppListing(card.nodeId)) {
                    HandoffResult.SENT -> HandoffState.SENT
                    HandoffResult.FAILED -> HandoffState.FAILED
                }
        }
    }

    fun dismissHandoff() {
        mutableHandoff.value = HandoffState.IDLE
    }

    private data class Day(
        val window: TodayWindow,
        val notes: List<JournalNote>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDay(
        dayClock: DayClock,
        notesRepository: JournalNotesRepository,
    ): Flow<Day> =
        dayClock.observe().flatMapLatest { window ->
            notesRepository.observeNotesInRange(window.start, window.end).map { notes ->
                Day(window = window, notes = notes.filter { note -> note.creationTimestamp in window })
            }
        }

    private fun readyState(
        day: Day,
        campfire: CampfireState?,
        phone: PhoneLink,
    ): TodayUiState {
        val summary = TodaySummaryCalculator.calculate(day.notes) { tag -> Feeling.fromTag(tag) != null }
        return TodayUiState.Ready(
            date = day.window.date,
            timeZone = day.window.timeZone,
            summary = summary,
            feeling = summary.feelingTag?.let(Feeling::fromTag),
            campfire = campfire?.toPresentation(),
            phoneCard = if (phone is PhoneLink.AppMissing) PhoneCard.GetApp(phone.nodeId) else PhoneCard.None,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 7: Add the duration format**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/ClockDuration.kt`:

```kotlin
package app.logdate.wear.v2.moments

import java.util.Locale
import kotlin.time.Duration

/**
 * [duration] as a clock reading in [locale]'s digits: "4:12" under an hour and "1:04:12" from an
 * hour up. A negative duration reads as "0:00".
 */
fun formatClockDuration(
    duration: Duration,
    locale: Locale = Locale.getDefault(),
): String {
    val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    if (hours > 0) return String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
    return String.format(locale, "%d:%02d", minutes, seconds)
}

private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L
```

- [ ] **Step 8: Add the copy**

Create `app/wear/src/main/res/values/strings_home.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Today, in the new watch app -->
    <string name="wear_today_no_moments">No moments today</string>
    <plurals name="wear_today_moments">
        <item quantity="one">%1$d moment</item>
        <item quantity="other">%1$d moments</item>
    </plurals>
    <plurals name="wear_today_places">
        <item quantity="one">%1$d place</item>
        <item quantity="other">%1$d places</item>
    </plurals>
    <string name="wear_today_voice">%1$s voice</string>
    <string name="wear_today_summary_separator">" · "</string>
    <string name="wear_today_failed">Couldn’t load today’s moments</string>
    <string name="wear_today_get_phone_app_title">Get LogDate on your phone</string>
    <string name="wear_today_get_phone_app_body">Opens the Play Store on your phone</string>
    <string name="wear_today_get_phone_app_failed">Couldn’t reach your phone. Tap to try again.</string>
    <string name="wear_today_record">Record</string>
    <string name="wear_today_mark">Mark</string>
    <string name="wear_today_add">Add</string>
    <string name="wear_today_add_symbol" translatable="false">+</string>

    <!-- Moments, in the new watch app -->
    <string name="wear_moment_text">Note</string>
    <string name="wear_moment_voice">Voice note · %1$s</string>
    <string name="wear_moment_photo">Photo</string>
    <string name="wear_moment_video">Video</string>
</resources>
```

- [ ] **Step 9: Add the moment text helpers**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentText.kt`:

```kotlin
package app.logdate.wear.v2.moments

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import kotlinx.datetime.TimeZone
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** A moment in a few words: its first line of text, its caption, or its kind and length. */
@Composable
fun momentLabel(note: JournalNote): String =
    when (note) {
        is JournalNote.Text ->
            note.content.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
                ?: stringResource(R.string.wear_moment_text)
        is JournalNote.Audio -> stringResource(R.string.wear_moment_voice, formatClockDuration(note.durationMs.milliseconds))
        is JournalNote.Image -> note.caption.ifBlank { stringResource(R.string.wear_moment_photo) }
        is JournalNote.Video -> note.caption.ifBlank { stringResource(R.string.wear_moment_video) }
    }

/** The clock time of [instant] in [timeZone], in the user's 12 or 24 hour style. */
@Composable
fun momentClockTime(
    instant: Instant,
    timeZone: TimeZone,
): String {
    val context = LocalContext.current
    return remember(context, instant, timeZone) {
        val format = DateFormat.getTimeFormat(context)
        format.timeZone = java.util.TimeZone.getTimeZone(timeZone.id)
        format.format(Date(instant.toEpochMilliseconds()))
    }
}
```

- [ ] **Step 10: Add the Today page**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayPage.kt`:

```kotlin
package app.logdate.wear.v2.today

import android.text.format.DateFormat
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
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
 * The controls Today shows. Each appears only when its handler is set, and the change that makes a
 * control work is the change that sets its handler, so Today never shows a control that does
 * nothing.
 */
data class TodayControls(
    val onRecord: (() -> Unit)? = null,
    val onMark: (() -> Unit)? = null,
    val onAdd: (() -> Unit)? = null,
)

/**
 * Home's first page: the date in the time text, the campfire, the day's summary line, the latest
 * moment and, when the paired Android phone lacks LogDate, a card that opens its Play listing.
 * Record sits at the end as the edge button, with Mark and + above it.
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
    val dateText = (state as? TodayUiState.Ready)?.let { ready -> todayDateText(ready.date) }
    val timeText: @Composable () -> Unit = {
        TimeText(timeSource = timeSource) { time ->
            if (dateText != null) {
                timeTextCurvedText(dateText)
                timeTextSeparator()
            }
            timeTextCurvedText(time)
        }
    }
    TodayScaffold(listState = listState, timeText = timeText, onRecord = controls.onRecord) { contentPadding ->
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

@Composable
private fun TodayScaffold(
    listState: TransformingLazyColumnState,
    timeText: @Composable () -> Unit,
    onRecord: (() -> Unit)?,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    if (onRecord == null) {
        ScreenScaffold(scrollState = listState, timeText = timeText, content = content)
        return
    }
    ScreenScaffold(scrollState = listState, edgeButton = { RecordButton(onRecord) }, timeText = timeText, content = content)
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
    TransformingLazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier.testTag(TodayTags.PAGE)) {
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
    ButtonGroup(modifier = Modifier.fillMaxWidth()) {
        controls.onMark?.let { onMark ->
            FilledTonalButton(onClick = onMark, modifier = Modifier.weight(1f).testTag(TodayTags.MARK)) {
                Icon(imageVector = LogDateGlyph.MARK.vector(), contentDescription = null)
                Text(stringResource(R.string.wear_today_mark))
            }
        }
        controls.onAdd?.let { onAdd ->
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier.weight(1f).testTag(TodayTags.ADD).semantics { contentDescription = addLabel },
            ) {
                Text(stringResource(R.string.wear_today_add_symbol))
            }
        }
    }
}

@Composable
private fun RecordButton(onRecord: () -> Unit) {
    EdgeButton(onClick = onRecord, buttonSize = EdgeButtonSize.Medium, modifier = Modifier.testTag(TodayTags.RECORD)) {
        Icon(imageVector = LogDateGlyph.RECORD.vector(), contentDescription = stringResource(R.string.wear_today_record))
    }
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
```

- [ ] **Step 11: Bind Today in Koin**

Replace `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` with:

```kotlin
package app.logdate.wear.di

import android.content.Context
import androidx.wear.phone.interactions.PhoneTypeHelper
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.logdate.client.domain.streak.ObserveCampfireUseCase
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.ui.platform.createAndroidPlatformHapticsController
import app.logdate.ui.platform.isSystemHapticFeedbackOff
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.haptics.SharedPreferencesHapticsPreferenceStore
import app.logdate.wear.v2.haptics.watchHaptics
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLinkRepository
import app.logdate.wear.v2.today.DayClock
import app.logdate.wear.v2.today.TodayViewModel
import app.logdate.wear.v2.today.WatchDayBoundaryPreferences
import app.logdate.wear.v2.today.WatchStreakSettings
import app.logdate.wear.v2.today.timeChangeSignals
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the redesigned watch app. Only the new app's screens resolve anything from it. */
val wearV2Module =
    module {
        single<HapticsPreferenceStore> {
            SharedPreferencesHapticsPreferenceStore(
                get<Context>().getSharedPreferences(SharedPreferencesHapticsPreferenceStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single<LogDateHaptics> {
            val context = get<Context>()
            watchHaptics(
                controller = createAndroidPlatformHapticsController(context),
                preferences = get(),
                systemHapticsOff = { isSystemHapticFeedbackOff(context) },
            )
        }

        single { DayClock(timeChanges = timeChangeSignals(get())) }
        // The phone shares its day-start hour through the state document (Track A, C2); until then there is none.
        single {
            ObserveCampfireUseCase(
                notesRepository = get(),
                streakSettingsRepository = WatchStreakSettings(get()),
                dayBoundaryPreferences = WatchDayBoundaryPreferences(phoneDayStartHour = { null }),
            )
        }
        single {
            val context = get<Context>()
            PhoneLinkRepository(
                capabilityClient = Wearable.getCapabilityClient(context),
                nodeClient = Wearable.getNodeClient(context),
                phoneType = { PhoneTypeHelper.getPhoneDeviceType(context) },
                ioDispatcher = Dispatchers.IO,
            )
        }
        single {
            val context = get<Context>()
            PhoneHandoff(
                remoteActivityHelper = RemoteActivityHelper(context),
                packageName = context.packageName,
                ioDispatcher = Dispatchers.IO,
            )
        }
        viewModel {
            TodayViewModel(
                dayClock = get(),
                notesRepository = get(),
                observeCampfire = get(),
                phoneLinks = get(),
                phoneHandoff = get(),
            )
        }
    }
```

- [ ] **Step 12: Run the unit tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.today.TodayViewModelTest' --tests 'app.logdate.wear.v2.moments.ClockDurationTest' :app:wear:ktlintCheck :app:wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; 10 + 5 tests pass.

- [ ] **Step 13: Add the large-font preview**

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WearLargeFontPreview.kt`:

```kotlin
package app.logdate.wear.screenshots

import androidx.compose.ui.tooling.preview.Preview

/** A small round watch at the largest font scale the design is checked at, 1.24. */
@Preview(
    name = "Large Font",
    device = "id:wearos_small_round",
    showBackground = true,
    fontScale = 1.24f,
)
annotation class WearLargeFontPreview
```

- [ ] **Step 14: Write the Today screenshot scenes**

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/TodayPageScreenshots.kt`:

```kotlin
package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import app.logdate.client.domain.today.TodaySummary
import app.logdate.client.repository.journals.JournalNote
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.CampfireSize
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.HandoffState
import app.logdate.wear.v2.today.PhoneCard
import app.logdate.wear.v2.today.TodayControls
import app.logdate.wear.v2.today.TodayPage
import app.logdate.wear.v2.today.TodayUiState
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Every Today state, on the graphite theme screenshots can render. */
class TodayPageScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_TodayLoading() {
        TodayScene(TodayUiState.Loading)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_TodayEmpty() {
        TodayScene(ready(summary = TodaySummary.Empty, campfire = CampfirePresentation(phase = CampfirePhase.UNLIT)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_TodayBusy() {
        TodayScene(ready(summary = busySummary, feeling = Feeling.GOOD))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S04_TodayLatestNote() {
        TodayScene(ready(summary = busySummary.copy(latestMoment = latestNote, voiceDuration = 0.seconds)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S05_TodayWithoutCampfire() {
        TodayScene(ready(summary = busySummary, campfire = null))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S06_TodayGetPhoneApp() {
        TodayScene(ready(summary = TodaySummary.Empty, phoneCard = PhoneCard.GetApp(nodeId = "phone")))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S07_TodayGetPhoneAppFailed() {
        TodayScene(ready(summary = TodaySummary.Empty, phoneCard = PhoneCard.GetApp(nodeId = "phone")), handoff = HandoffState.FAILED)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S08_TodayFailed() {
        TodayScene(TodayUiState.Failed)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S09_TodayWithControls() {
        TodayScene(ready(summary = busySummary, feeling = Feeling.GOOD), controls = allControls)
    }

    @PreviewTest
    @WearLargeFontPreview
    @Composable
    fun S10_TodayBusyLargeFont() {
        TodayScene(ready(summary = busySummary, feeling = Feeling.GOOD))
    }

    @PreviewTest
    @WearLargeFontPreview
    @Composable
    fun S11_TodayWithControlsLargeFont() {
        TodayScene(ready(summary = busySummary, feeling = Feeling.GOOD), controls = allControls)
    }
}

private val zone = TimeZone.currentSystemDefault()
private val saturday = LocalDate(2026, 10, 3)
private val burning = CampfirePresentation(phase = CampfirePhase.BURNING, loggedToday = true, runDays = 4, size = CampfireSize.SMALL)
private val allControls = TodayControls(onRecord = {}, onMark = {}, onAdd = {})

private fun at(
    hour: Int,
    minute: Int,
): Instant = LocalDateTime(2026, 10, 3, hour, minute).toInstant(zone)

private val latestVoice =
    JournalNote.Audio(mediaRef = "audio/walk.m4a", durationMs = 42_000, creationTimestamp = at(16, 20), lastUpdated = at(16, 20))
private val latestNote =
    JournalNote.Text(creationTimestamp = at(17, 5), lastUpdated = at(17, 5), content = "Lunch by the water with Sam, then the long way home")
private val busySummary =
    TodaySummary(momentCount = 3, placeCount = 2, voiceDuration = 252.seconds, latestMoment = latestVoice, feelingTag = "good")

private fun ready(
    summary: TodaySummary,
    feeling: Feeling? = null,
    campfire: CampfirePresentation? = burning,
    phoneCard: PhoneCard = PhoneCard.None,
) = TodayUiState.Ready(
    date = saturday,
    timeZone = zone,
    summary = summary,
    feeling = feeling,
    campfire = campfire,
    phoneCard = phoneCard,
)

@Composable
private fun TodayScene(
    state: TodayUiState,
    handoff: HandoffState = HandoffState.IDLE,
    controls: TodayControls = TodayControls(),
) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            TodayPage(
                state = state,
                handoff = handoff,
                onGetPhoneApp = {},
                onHandoffDismissed = {},
                controls = controls,
                timeSource = FixedTimeSource,
            )
        }
    }
}

/** A fixed clock reading, so the time text renders the same on every run. */
private object FixedTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
```

- [ ] **Step 15: Run screenshot validation to verify it fails**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: FAIL; the report lists the 20 `TodayPageScreenshots` previews as missing reference images, and every existing preview passes.

- [ ] **Step 16: Record the references**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/TodayPageScreenshots" | wc -l
```

Expected: `BUILD SUCCESSFUL` and `20`. `git status --short app/wear/src/screenshotTestDebug` lists only the new `TodayPageScreenshots/` directory; if any other reference changed, stop and find out why before going on.

- [ ] **Step 17: Look at the references**

Open these with the Read tool and check them against the spec: `S03_TodayBusy_Small Round_*.png` (date "Sat, Oct 3" before 10:09 in the time text, a burning campfire, "🙂 3 moments · 2 places · 4:12 voice", "4:20 PM" over "Voice note · 0:42", no buttons), `S06_TodayGetPhoneApp_Small Round_*.png` (the "Get LogDate on your phone" card), `S09_TodayWithControls_Small Round_*.png` (Mark and + side by side above a Record edge button), and `S11_TodayWithControlsLargeFont_*.png` (nothing clipped at the round edge). Fix the layout and record again if any of them is wrong.

- [ ] **Step 18: Validate the references**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 19: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task7-commit.txt <<'EOF'
chore(wear): add the Today page for the new watch app

The redesigned watch opens to Today: the date beside the time, the
campfire, a line such as "3 moments · 2 places · 4:12 voice" with the
day's feeling, the latest moment, and a card that opens LogDate's
Play listing on a paired Android phone that lacks it.

Record, Mark and + belong to the page but appear only once the change
that makes each one work supplies its action, so the page never shows
a control that does nothing. Screenshots cover every state, the
controls, and a 1.24 font scale.

The page is not reachable yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 20: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayUiState.kt app/wear/src/main/kotlin/app/logdate/wear/v2/today/CampfireMapping.kt app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/v2/today/TodayPage.kt app/wear/src/main/kotlin/app/logdate/wear/v2/moments/ClockDuration.kt app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentText.kt app/wear/src/main/res/values/strings_home.xml app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/v2/today/TodayViewModelTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/moments/ClockDurationTest.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WearLargeFontPreview.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/TodayPageScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/TodayPageScreenshots && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task7-commit.txt
```

---
### Task 8: The Navigation 3 shell (`chore(wear)`)

With the flag on, `MainActivityContent` (`app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivityContent.kt:19`) shows `WearAppV2Placeholder`. This task replaces it with `WearAppV2`: the graphite or dynamic theme, `LocalLogDateHaptics` from the watch's Koin graph, `AppScaffold`, and a `NavDisplay` over a saved back stack with the swipe-to-dismiss scene strategy and the saveable-state and ViewModel-store entry decorators. Home renders the Today page; Task 9 turns Home into the pager. The six routes are declared now, so later plans add screens without touching the back stack's type. Every launch request still opens Today, because no other screen exists yet. The v1 app is untouched.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/navigation/WearRoute.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/navigation/WearLaunchRoutes.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt`
- Delete: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2Placeholder.kt` (1-51)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivityContent.kt:1-21`
- Modify: `app/wear/src/main/res/values/strings.xml:185-188` (delete the placeholder copy)
- Modify: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ExperienceGateTest.kt:60,69,70,78,83`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/navigation/WearRouteTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ShellNavigationTest.kt`

**Interfaces:**
- Consumes (Task 3): `LogDateHaptics` bound in `wearV2Module`. (Task 4): `LogDateWearTheme`. (Task 7): `TodayViewModel`, `TodayPage`, `TodayTags.PAGE`. Existing: `WearLaunchRequest` and its subtypes (`launch/WearLaunchRequest.kt:12-76`), `WearExperience.V2`.
- Produces:
  - `@Serializable sealed interface WearRoute : NavKey` with `Home`, `Moment(id: String)`, `CaptureSheet`, `PlayOn`, `Archive`, `PhoneCamera`, in `app.logdate.wear.v2.navigation`.
  - `internal fun WearLaunchRequest?.toV2BackStack(): List<WearRoute>`.
  - `object WearAppV2Tags { const val SHELL = "wear_app_v2_shell" }` (replacing `PLACEHOLDER`) and `@Composable fun WearAppV2(initialRequest: WearLaunchRequest?, newRequests: Flow<WearLaunchRequest>)` in `app.logdate.wear.v2`.

- [ ] **Step 1: Write the failing route test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/navigation/WearRouteTest.kt`:

```kotlin
package app.logdate.wear.v2.navigation

import app.logdate.wear.launch.WearLaunchRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class WearRouteTest {
    @Test
    fun `every route survives being saved and restored`() {
        val routes =
            listOf(
                WearRoute.Home,
                WearRoute.Moment(id = Uuid.random().toString()),
                WearRoute.CaptureSheet,
                WearRoute.PlayOn,
                WearRoute.Archive,
                WearRoute.PhoneCamera,
            )

        routes.forEach { route ->
            val saved = Json.encodeToString(serializer<WearRoute>(), route)
            assertEquals(route, Json.decodeFromString(serializer<WearRoute>(), saved))
        }
    }

    @Test
    fun `every launch request opens Today until its own screen exists`() {
        val requests =
            listOf(
                null,
                WearLaunchRequest.Home,
                WearLaunchRequest.Record,
                WearLaunchRequest.Memories,
                WearLaunchRequest.MoodCheckIn,
                WearLaunchRequest.QuickText,
                WearLaunchRequest.Timeline,
                WearLaunchRequest.RemoteCamera,
                WearLaunchRequest.Settings,
                WearLaunchRequest.Mark,
                WearLaunchRequest.OnThisDay,
                WearLaunchRequest.Moment(Uuid.random()),
            )

        requests.forEach { request ->
            assertEquals(listOf<WearRoute>(WearRoute.Home), request.toV2BackStack(), "for $request")
        }
    }
}
```

- [ ] **Step 2: Point the gate test at the shell and write the failing shell test**

In `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ExperienceGateTest.kt`, replace `WearAppV2Tags.PLACEHOLDER` with `WearAppV2Tags.SHELL` on lines 60, 69, 70, 78 and 83. Nothing else in the file changes.

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ShellNavigationTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.v2.WearAppV2Tags
import app.logdate.wear.v2.today.TodayTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent

/**
 * Launches the real activity with WATCH_EXPERIENCE_V2 on and checks the new app's shell: it opens
 * on Today, a launch request that arrives while it is open lands on a single Today, and a recreated
 * activity comes back to Today.
 */
@RunWith(AndroidJUnit4::class)
class ShellNavigationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun openTheNewWatchApp() {
        setNewWatchApp(enabled = true)
    }

    @After
    fun tearDown() {
        // onNewIntent replaces the activity's intent, so ActivityScenario no longer matches the activity
        // to its launch and close() would wait for a DESTROYED it never sees.
        scenario?.onActivity { it.finish() }
        setNewWatchApp(enabled = false)
    }

    @Test
    fun the_new_watch_app_opens_on_today() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }
        composeRule.onNodeWithTag(WearAppV2Tags.SHELL).assertIsDisplayed()
        composeRule.onNodeWithTag(TodayTags.PAGE).assertIsDisplayed()
    }

    @Test
    fun a_request_that_arrives_while_open_lands_on_a_single_today() {
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }

        launched.onActivity { activity ->
            activity.startActivity(Intent(activity, MainActivity::class.java).putExtra(WearLaunchRequest.EXTRA_ROUTE, ROUTE))
        }
        composeRule.waitUntil(TIMEOUT_MS) { deliveredRoute(launched) == ROUTE }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(TodayTags.PAGE).assertCountEquals(1)
        composeRule.onNodeWithTag(TodayTags.PAGE).assertIsDisplayed()
    }

    @Test
    fun a_recreated_activity_comes_back_to_today() {
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }

        launched.recreate()

        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }
        composeRule.onNodeWithTag(TodayTags.PAGE).assertIsDisplayed()
    }

    private fun deliveredRoute(scenario: ActivityScenario<MainActivity>): String? {
        var route: String? = null
        scenario.onActivity { activity -> route = activity.intent.getStringExtra(WearLaunchRequest.EXTRA_ROUTE) }
        return route
    }

    private fun setNewWatchApp(enabled: Boolean) =
        runBlocking {
            KoinJavaComponent.getKoin().get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        }

    private fun waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        composeRule.waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val ROUTE = "memories"
        const val TIMEOUT_MS = 10_000L
    }
}
```

- [ ] **Step 3: Run the unit test to verify it fails**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.navigation.WearRouteTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'WearRoute'` and `Unresolved reference 'toV2BackStack'`.

- [ ] **Step 4: Run the GMD tests to verify they fail**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ExperienceGateTest,app.logdate.wear.e2e.ShellNavigationTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'SHELL'`.

- [ ] **Step 5: Add the routes**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/navigation/WearRoute.kt`:

```kotlin
package app.logdate.wear.v2.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The screens of the new watch app. The back stack is saved with the activity's state, so every
 * route is serializable and names a moment by its note ID as a string.
 */
@Serializable
sealed interface WearRoute : NavKey {
    /** Today and the moments of the last 7 days. */
    @Serializable
    data object Home : WearRoute

    /** One moment, by the ID of its note. */
    @Serializable
    data class Moment(
        val id: String,
    ) : WearRoute

    /** Write, Mood, Phone camera and Microphone, opened from Today's +. */
    @Serializable
    data object CaptureSheet : WearRoute

    /** Where audio plays: the watch, headphones or the phone. */
    @Serializable
    data object PlayOn : WearRoute

    /** The rest of the library window, past the last 7 days. */
    @Serializable
    data object Archive : WearRoute

    /** The phone's camera, driven from the watch. */
    @Serializable
    data object PhoneCamera : WearRoute
}
```

- [ ] **Step 6: Map launch requests to back stacks**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/navigation/WearLaunchRoutes.kt`:

```kotlin
package app.logdate.wear.v2.navigation

import app.logdate.wear.launch.WearLaunchRequest

/**
 * The back stack the new watch app opens for this request. Only Home exists so far, so every
 * request opens Today; the change that adds a screen maps its requests here.
 */
internal fun WearLaunchRequest?.toV2BackStack(): List<WearRoute> =
    when (this) {
        null,
        WearLaunchRequest.Home,
        WearLaunchRequest.Record,
        WearLaunchRequest.Memories,
        WearLaunchRequest.MoodCheckIn,
        WearLaunchRequest.QuickText,
        WearLaunchRequest.Timeline,
        WearLaunchRequest.RemoteCamera,
        WearLaunchRequest.Settings,
        WearLaunchRequest.Mark,
        WearLaunchRequest.OnThisDay,
        is WearLaunchRequest.Moment,
        -> listOf(WearRoute.Home)
    }
```

- [ ] **Step 7: Add the shell**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt`:

```kotlin
package app.logdate.wear.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import app.logdate.ui.platform.LocalLogDateHaptics
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.v2.navigation.WearRoute
import app.logdate.wear.v2.navigation.toV2BackStack
import app.logdate.wear.v2.theme.LogDateWearTheme
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
 * Swiping right dismisses the top screen. Each screen's view models live as long as its entry.
 */
@Composable
fun WearAppV2(
    initialRequest: WearLaunchRequest?,
    newRequests: Flow<WearLaunchRequest>,
) {
    val backStack = rememberNavBackStack(*initialRequest.toV2BackStack().toTypedArray())
    LaunchedEffect(newRequests) {
        newRequests.collect { request ->
            backStack.clear()
            backStack.addAll(request.toV2BackStack())
        }
    }
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
                    sceneStrategy = rememberSwipeDismissableSceneStrategy(),
                    entryProvider =
                        entryProvider {
                            entry<WearRoute.Home> { TodayEntry() }
                        },
                )
            }
        }
    }
}

@Composable
private fun TodayEntry() {
    val viewModel = koinViewModel<TodayViewModel>()
    val state by viewModel.uiState.collectAsState()
    val handoff by viewModel.handoff.collectAsState()
    TodayPage(
        state = state,
        handoff = handoff,
        onGetPhoneApp = viewModel::getPhoneApp,
        onHandoffDismissed = viewModel::dismissHandoff,
    )
}
```

- [ ] **Step 8: Show the shell instead of the placeholder**

Replace `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivityContent.kt` with:

```kotlin
package app.logdate.wear.presentation

import androidx.compose.runtime.Composable
import app.logdate.wear.launch.WearExperience
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.v2.WearAppV2
import kotlinx.coroutines.flow.Flow

/** What [MainActivity] shows: nothing while the splash screen decides, then the app [experience] names. */
@Composable
internal fun MainActivityContent(
    experience: WearExperience?,
    initialRequest: WearLaunchRequest?,
    newRequests: Flow<WearLaunchRequest>,
) {
    when (experience) {
        null -> Unit
        WearExperience.V1 -> WearApp(initialRequest = initialRequest, newRequests = newRequests)
        WearExperience.V2 -> WearAppV2(initialRequest = initialRequest, newRequests = newRequests)
    }
}
```

Delete the placeholder and its copy:

```bash
git rm app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2Placeholder.kt
```

In `app/wear/src/main/res/values/strings.xml`, delete lines 185-188:

```xml
    <!-- New watch experience -->
    <string name="wear_v2_placeholder_title">New watch experience</string>
    <string name="wear_v2_placeholder_body">Not built yet</string>

```

Then confirm nothing still names them:

```bash
grep -rn "WearAppV2Placeholder\|wear_v2_placeholder\|WearAppV2Tags.PLACEHOLDER" app/wear/src
```

Expected: no output.

- [ ] **Step 9: Run the unit test to verify it passes**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.navigation.WearRouteTest' :app:wear:ktlintCheck :app:wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; 2 tests pass.

- [ ] **Step 10: Run the GMD tests to verify they pass**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ExperienceGateTest,app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.LaunchRequestRoutingTest
```

Expected: `BUILD SUCCESSFUL`, 7 tests passed. `ExperienceGateTest` still proves the flag off opens the current app and the flag on opens the new one; `LaunchRequestRoutingTest` proves v1 routing is unchanged.

- [ ] **Step 11: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task8-commit.txt <<'EOF'
chore(wear): open the new watch app in a Navigation 3 shell

With the new watch experience turned on, the watch now opens the
redesigned app on Today instead of a "Not built yet" screen. The app
uses the new theme and haptics, swipes right to go back, and restores
its screens after the activity is recreated. Tiles and complications
still open Today until their own screens exist.

The routes for a moment, the capture sheet, Play on, the archive and
the phone camera are declared for the screens that follow. The current
watch app is unchanged.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 12: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/navigation/WearRoute.kt app/wear/src/main/kotlin/app/logdate/wear/v2/navigation/WearLaunchRoutes.kt app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2Placeholder.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivityContent.kt app/wear/src/main/res/values/strings.xml app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ExperienceGateTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ShellNavigationTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/navigation/WearRouteTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task8-commit.txt
```

`git add` on the deleted placeholder path stages its deletion.

---

### Task 9: Home as a two-page pager (`chore(wear)`)

Home becomes a `VerticalPagerScaffold`: Today on the first page and the moments of the last 7 days on the second, with the scaffold's page indicator. Turning the crown up at the top of the list returns to Today, and turning it down at the end of Today opens the list, so Home works with the crown alone. While a recording runs, Home stays on Today, where Stop is; the activity-scoped `RecorderViewModel` reports whether one runs, and reports `Ready` until the recorder plan (B3) fills it in.

The Flow page here is the read-only list of the last 7 days' moments, newest first, with check-ins left out. Inline playback, the On this day and Rewind cards, "Earlier" and Settings belong to B5 and B6; none of them is drawn before it works.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderViewModel.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/home/HomePaging.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/home/HomeScreen.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowViewModel.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentText.kt` (whole file)
- Modify: `app/wear/src/main/res/values/strings_home.xml` (append before `</resources>`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (imports and two bindings)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecorderStateTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/home/HomePagingTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/flow/FlowViewModelTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/HomePagerRotaryTest.kt`
- Screenshot: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/FlowPageScreenshots.kt`
- References: `app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/FlowPageScreenshots/` (new, 8 images)

**Interfaces:**
- Consumes (Task 5): `DayClock.observe()`, `TodayWindow`, `MoodNoteTag.isFeeling`. (Task 7): `TodayPage(..., listState)`, `momentLabel`, strings in `strings_home.xml`. (Task 8): `WearAppV2`, `WearRoute.Home`.
- Produces:
  - `sealed interface RecorderState { data object Ready; data object Recording }`, `val RecorderState.locksPaging: Boolean`, `class RecorderViewModel : ViewModel` with `val state: StateFlow<RecorderState>`, in `app.logdate.wear.v2.recorder`.
  - `enum class HomePage { TODAY, FLOW }` and `internal fun pageForCrown(current: HomePage, scrollPixels: Float, canScrollBackward: Boolean, canScrollForward: Boolean, locked: Boolean): HomePage?`, `object HomeTags { const val PAGER = "home_pager" }`, `@Composable fun HomeScreen(pagingLocked: Boolean, today: @Composable (TransformingLazyColumnState) -> Unit, flow: @Composable (TransformingLazyColumnState) -> Unit, modifier: Modifier = Modifier)`, in `app.logdate.wear.v2.home`.
  - `sealed interface FlowUiState { data object Loading; data class Ready(timeZone: TimeZone, moments: List<JournalNote>); data object Failed }`, `class FlowViewModel(dayClock: DayClock, notesRepository: JournalNotesRepository)` with `val uiState: StateFlow<FlowUiState>` and `FLOW_DAYS = 7`, `object FlowTags { const val LIST = "flow_list" }`, `@Composable fun FlowPage(state: FlowUiState, modifier: Modifier = Modifier, listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(), timeSource: TimeSource = ...)`, in `app.logdate.wear.v2.flow`.
  - `@Composable fun momentDayAndTime(instant: Instant, timeZone: TimeZone): String` in `app.logdate.wear.v2.moments`.

- [ ] **Step 1: Write the failing recorder state test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecorderStateTest.kt`:

```kotlin
package app.logdate.wear.v2.recorder

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecorderStateTest {
    @Test
    fun `the recorder starts ready`() {
        assertEquals(RecorderState.Ready, RecorderViewModel().state.value)
    }

    @Test
    fun `only a recording locks Home on Today`() {
        assertFalse(RecorderState.Ready.locksPaging)
        assertTrue(RecorderState.Recording.locksPaging)
    }
}
```

- [ ] **Step 2: Write the failing crown test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/home/HomePagingTest.kt`:

```kotlin
package app.logdate.wear.v2.home

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomePagingTest {
    @Test
    fun `turning up at the top of the moment list returns to Today`() {
        assertEquals(
            HomePage.TODAY,
            pageForCrown(HomePage.FLOW, scrollPixels = -40f, canScrollBackward = false, canScrollForward = true, locked = false),
        )
    }

    @Test
    fun `turning up inside the moment list scrolls the list`() {
        assertNull(pageForCrown(HomePage.FLOW, scrollPixels = -40f, canScrollBackward = true, canScrollForward = true, locked = false))
    }

    @Test
    fun `turning down at the end of Today opens the moment list`() {
        assertEquals(
            HomePage.FLOW,
            pageForCrown(HomePage.TODAY, scrollPixels = 40f, canScrollBackward = true, canScrollForward = false, locked = false),
        )
    }

    @Test
    fun `turning down inside Today scrolls Today`() {
        assertNull(pageForCrown(HomePage.TODAY, scrollPixels = 40f, canScrollBackward = false, canScrollForward = true, locked = false))
    }

    @Test
    fun `turning up at the top of Today stays on Today`() {
        assertNull(pageForCrown(HomePage.TODAY, scrollPixels = -40f, canScrollBackward = false, canScrollForward = false, locked = false))
    }

    @Test
    fun `turning down at the end of the moment list stays on the list`() {
        assertNull(pageForCrown(HomePage.FLOW, scrollPixels = 40f, canScrollBackward = true, canScrollForward = false, locked = false))
    }

    @Test
    fun `nothing pages while recording`() {
        assertNull(pageForCrown(HomePage.FLOW, scrollPixels = -40f, canScrollBackward = false, canScrollForward = true, locked = true))
        assertNull(pageForCrown(HomePage.TODAY, scrollPixels = 40f, canScrollBackward = true, canScrollForward = false, locked = true))
    }
}
```

- [ ] **Step 3: Write the failing moment list test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/flow/FlowViewModelTest.kt`:

```kotlin
package app.logdate.wear.v2.flow

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.v2.today.DayClock
import app.logdate.wear.v2.today.TodayWindow
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FlowViewModelTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val saturday =
        TodayWindow(
            date = LocalDate(2026, 10, 3),
            timeZone = zone,
            start = LocalDate(2026, 10, 3).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, 4).atStartOfDayIn(zone),
        )
    private val weekStart = LocalDate(2026, 9, 27).atStartOfDayIn(zone)
    private val dayClock = mockk<DayClock> { every { observe() } returns flowOf(saturday) }
    private val notesRepository = mockk<JournalNotesRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the list covers today and the 6 days before it`() =
        runTest {
            every { notesRepository.observeNotesInRange(weekStart, saturday.end) } returns flowOf(emptyList())

            FlowViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(FlowUiState.Ready(timeZone = zone, moments = emptyList()), expectMostRecentItem())
            }
        }

    @Test
    fun `moments are newest first and check-ins are left out`() =
        runTest {
            val monday = text("Monday walk", at(LocalDateTime(2026, 9, 28, 9, 0)))
            val friday = text("Friday dinner", at(LocalDateTime(2026, 10, 2, 20, 0)))
            val checkIn = text("#mood:ok Feeling ok", at(LocalDateTime(2026, 10, 3, 8, 0)))
            every { notesRepository.observeNotesInRange(weekStart, saturday.end) } returns flowOf(listOf(monday, checkIn, friday))

            FlowViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(listOf<JournalNote>(friday, monday), (expectMostRecentItem() as FlowUiState.Ready).moments)
            }
        }

    @Test
    fun `notes from before the 7 days are left out`() =
        runTest {
            val older = text("Last week", weekStart - 1.hours)
            val sunday = text("Sunday", at(LocalDateTime(2026, 9, 27, 10, 0)))
            every { notesRepository.observeNotesInRange(weekStart, saturday.end) } returns flowOf(listOf(older, sunday))

            FlowViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(listOf<JournalNote>(sunday), (expectMostRecentItem() as FlowUiState.Ready).moments)
            }
        }

    @Test
    fun `a failure reading notes shows the failed state`() =
        runTest {
            every { notesRepository.observeNotesInRange(any(), any()) } returns flow { throw IOException("database closed") }

            FlowViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(FlowUiState.Failed, expectMostRecentItem())
            }
        }

    private fun at(local: LocalDateTime): Instant = local.toInstant(zone)

    private fun text(
        content: String,
        at: Instant,
    ) = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = content)
}
```

- [ ] **Step 4: Run the unit tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.recorder.*' --tests 'app.logdate.wear.v2.home.*' --tests 'app.logdate.wear.v2.flow.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'RecorderViewModel'`, `Unresolved reference 'pageForCrown'` and `Unresolved reference 'FlowViewModel'`.

- [ ] **Step 5: Write the failing GMD pager test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/HomePagerRotaryTest.kt`:

```kotlin
package app.logdate.wear.e2e

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.logdate.wear.v2.home.HomeScreen
import app.logdate.wear.v2.home.HomeTags
import app.logdate.wear.v2.theme.LogDateWearTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives Home's pager with touch and the crown on a real Wear OS emulator. The pages hold plain
 * lists, so only paging is under test.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomePagerRotaryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun swiping_up_opens_the_moment_list() {
        showHome()

        swipeToFlow()

        assertTrue(isShown(FLOW_FIRST))
    }

    @Test
    fun turning_the_crown_up_at_the_top_of_the_list_returns_to_today() {
        showHome()
        swipeToFlow()

        composeRule.onNodeWithTag(FLOW_LIST).performRotaryScrollInput { rotateToScrollVertically(-CROWN_STEP) }

        waitUntilShown(TODAY_FIRST)
    }

    @Test
    fun turning_the_crown_up_inside_the_list_scrolls_the_list_first() {
        showHome()
        swipeToFlow()
        composeRule.onNodeWithTag(FLOW_LIST).performRotaryScrollInput { rotateToScrollVertically(LONG_SCROLL) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FLOW_LIST).performRotaryScrollInput { rotateToScrollVertically(-CROWN_STEP) }
        composeRule.waitForIdle()

        assertTrue(composeRule.onNodeWithTag(FLOW_LIST).isDisplayed())
        assertFalse(isShown(TODAY_FIRST))
    }

    @Test
    fun turning_the_crown_down_at_the_end_of_today_opens_the_list() {
        showHome()

        composeRule.onNodeWithTag(TODAY_LIST).performRotaryScrollInput { rotateToScrollVertically(CROWN_STEP) }

        waitUntilShown(FLOW_FIRST)
    }

    @Test
    fun home_stays_on_today_while_recording() {
        showHome(pagingLocked = true)

        composeRule.onNodeWithTag(HomeTags.PAGER).performTouchInput { swipeUp() }
        composeRule.onNodeWithTag(TODAY_LIST).performRotaryScrollInput { rotateToScrollVertically(CROWN_STEP) }
        composeRule.waitForIdle()

        assertTrue(isShown(TODAY_FIRST))
        assertFalse(isShown(FLOW_FIRST))
    }

    private fun showHome(pagingLocked: Boolean = false) {
        composeRule.setContent {
            LogDateWearTheme(dynamicColor = false) {
                AppScaffold {
                    HomeScreen(
                        pagingLocked = pagingLocked,
                        today = { listState -> SampleList(listState, tag = TODAY_LIST, label = "Today", count = 3) },
                        flow = { listState -> SampleList(listState, tag = FLOW_LIST, label = "Earlier", count = 30) },
                    )
                }
            }
        }
    }

    private fun swipeToFlow() {
        composeRule.onNodeWithTag(HomeTags.PAGER).performTouchInput { swipeUp() }
        waitUntilShown(FLOW_FIRST)
    }

    private fun isShown(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() && composeRule.onNodeWithText(text).isDisplayed()

    private fun waitUntilShown(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { isShown(text) }
    }

    private companion object {
        const val TODAY_LIST = "sample_today_list"
        const val FLOW_LIST = "sample_flow_list"
        const val TODAY_FIRST = "Today 0"
        const val FLOW_FIRST = "Earlier 0"
        const val CROWN_STEP = 120f
        const val LONG_SCROLL = 600f
        const val TIMEOUT_MS = 5_000L
    }
}

@Composable
private fun SampleList(
    listState: TransformingLazyColumnState,
    tag: String,
    label: String,
    count: Int,
) {
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.testTag(tag)) {
            items(count) { index -> Text("$label $index") }
        }
    }
}
```

- [ ] **Step 6: Run the GMD test to verify it fails**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.HomePagerRotaryTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'HomeScreen'`.

- [ ] **Step 7: Add the recorder state**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderViewModel.kt`:

```kotlin
package app.logdate.wear.v2.recorder

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the watch is recording, as the screens that react to it see it. */
sealed interface RecorderState {
    data object Ready : RecorderState

    data object Recording : RecorderState
}

/** Whether Home has to stay on Today, where Stop is. */
val RecorderState.locksPaging: Boolean
    get() = this is RecorderState.Recording

/**
 * The recorder the whole watch app shares. It belongs to the activity, so a recording outlives the
 * screen that started it. Recording itself arrives with the record gesture; until then the state
 * is [RecorderState.Ready].
 */
class RecorderViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<RecorderState>(RecorderState.Ready)

    val state: StateFlow<RecorderState> = mutableState.asStateFlow()
}
```

- [ ] **Step 8: Add the crown rule**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/home/HomePaging.kt`:

```kotlin
package app.logdate.wear.v2.home

/** Home's pages, in pager order. */
enum class HomePage {
    TODAY,
    FLOW,
}

/**
 * The page a turn of the crown moves Home to, or null when the crown scrolls the page it is on.
 * Turning up at the top of the moment list returns to Today; turning down at the end of Today
 * opens the list. Nothing pages while [locked].
 */
internal fun pageForCrown(
    current: HomePage,
    scrollPixels: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    locked: Boolean,
): HomePage? {
    if (locked) return null
    if (current == HomePage.FLOW && scrollPixels < 0f && !canScrollBackward) return HomePage.TODAY
    if (current == HomePage.TODAY && scrollPixels > 0f && !canScrollForward) return HomePage.FLOW
    return null
}
```

- [ ] **Step 9: Add the Home pager**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/home/HomeScreen.kt`:

```kotlin
package app.logdate.wear.v2.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.platform.testTag
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.VerticalPagerScaffold
import kotlinx.coroutines.launch

object HomeTags {
    const val PAGER = "home_pager"
}

/**
 * Home: Today on the first page and the moments of the last 7 days on the second. Record is
 * Today's edge button, which reaches its full size only at the end of a list, so the two pages
 * never share one list.
 *
 * Turning the crown up at the top of the moment list returns to Today, and turning it down at the
 * end of Today opens the list. While [pagingLocked], Home stays on Today, where Stop is.
 */
@Composable
fun HomeScreen(
    pagingLocked: Boolean,
    today: @Composable (TransformingLazyColumnState) -> Unit,
    flow: @Composable (TransformingLazyColumnState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { HomePage.entries.size })
    val todayList = rememberTransformingLazyColumnState()
    val flowList = rememberTransformingLazyColumnState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagingLocked) {
        if (pagingLocked && pagerState.currentPage != HomePage.TODAY.ordinal) {
            pagerState.animateScrollToPage(HomePage.TODAY.ordinal)
        }
    }
    VerticalPagerScaffold(pagerState = pagerState, modifier = modifier.testTag(HomeTags.PAGER)) {
        VerticalPager(state = pagerState, userScrollEnabled = !pagingLocked) { index ->
            val page = HomePage.entries[index]
            val list = if (page == HomePage.TODAY) todayList else flowList
            AnimatedPage(index, pagerState) {
                Box(
                    modifier =
                        Modifier.fillMaxSize().onPreRotaryScrollEvent { event ->
                            if (pagerState.isScrollInProgress) return@onPreRotaryScrollEvent true
                            val target =
                                pageForCrown(
                                    current = page,
                                    scrollPixels = event.verticalScrollPixels,
                                    canScrollBackward = list.canScrollBackward,
                                    canScrollForward = list.canScrollForward,
                                    locked = pagingLocked,
                                ) ?: return@onPreRotaryScrollEvent false
                            scope.launch { pagerState.animateScrollToPage(target.ordinal) }
                            true
                        },
                ) {
                    when (page) {
                        HomePage.TODAY -> today(todayList)
                        HomePage.FLOW -> flow(flowList)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 10: Add the moment list's state**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowViewModel.kt`:

```kotlin
package app.logdate.wear.v2.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.today.MoodNoteTag
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.v2.today.DayClock
import app.logdate.wear.v2.today.TodayWindow
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus

/** What Home's moment list shows. */
sealed interface FlowUiState {
    data object Loading : FlowUiState

    /** The moments of the last [FlowViewModel.FLOW_DAYS] days, newest first, without check-ins. */
    data class Ready(
        val timeZone: TimeZone,
        val moments: List<JournalNote>,
    ) : FlowUiState

    data object Failed : FlowUiState
}

/** Home's second page: today and the 6 days before it, following the day as it moves. */
class FlowViewModel(
    dayClock: DayClock,
    notesRepository: JournalNotesRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FlowUiState> =
        dayClock
            .observe()
            .flatMapLatest<TodayWindow, FlowUiState> { today ->
                val start = today.date.minus(FLOW_DAYS - 1, DateTimeUnit.DAY).atStartOfDayIn(today.timeZone)
                notesRepository.observeNotesInRange(start, today.end).map { notes ->
                    FlowUiState.Ready(
                        timeZone = today.timeZone,
                        moments =
                            notes
                                .filter { it.creationTimestamp >= start && it.creationTimestamp < today.end }
                                .filterNot(MoodNoteTag::isFeeling)
                                .sortedByDescending(JournalNote::creationTimestamp),
                    )
                }
            }.catch { error ->
                Napier.e("Couldn't load the last 7 days on the watch", error)
                emit(FlowUiState.Failed)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FlowUiState.Loading)

    companion object {
        const val FLOW_DAYS = 7
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 11: Add the copy and the weekday time**

In `app/wear/src/main/res/values/strings_home.xml`, insert before the closing `</resources>`:

```xml

    <!-- The moment list, in the new watch app -->
    <string name="wear_flow_title">Last 7 days</string>
    <string name="wear_flow_empty">No moments in the last 7 days</string>
    <string name="wear_flow_failed">Couldn’t load your recent moments</string>
```

Replace `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentText.kt` with:

```kotlin
package app.logdate.wear.v2.moments

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import kotlinx.datetime.TimeZone
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** A moment in a few words: its first line of text, its caption, or its kind and length. */
@Composable
fun momentLabel(note: JournalNote): String =
    when (note) {
        is JournalNote.Text ->
            note.content.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
                ?: stringResource(R.string.wear_moment_text)
        is JournalNote.Audio -> stringResource(R.string.wear_moment_voice, formatClockDuration(note.durationMs.milliseconds))
        is JournalNote.Image -> note.caption.ifBlank { stringResource(R.string.wear_moment_photo) }
        is JournalNote.Video -> note.caption.ifBlank { stringResource(R.string.wear_moment_video) }
    }

/** The clock time of [instant] in [timeZone], in the user's 12 or 24 hour style. */
@Composable
fun momentClockTime(
    instant: Instant,
    timeZone: TimeZone,
): String {
    val context = LocalContext.current
    return remember(context, instant, timeZone) {
        val format = DateFormat.getTimeFormat(context)
        format.timeZone = java.util.TimeZone.getTimeZone(timeZone.id)
        format.format(Date(instant.toEpochMilliseconds()))
    }
}

/** The weekday and clock time of [instant] in [timeZone], such as "Fri 3:14 PM", in the user's 12 or 24 hour style. */
@Composable
fun momentDayAndTime(
    instant: Instant,
    timeZone: TimeZone,
): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(context, locale, instant, timeZone) {
        val skeleton = if (DateFormat.is24HourFormat(context)) DAY_AND_TIME_24_HOUR else DAY_AND_TIME_12_HOUR
        val format = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        format.timeZone = java.util.TimeZone.getTimeZone(timeZone.id)
        format.format(Date(instant.toEpochMilliseconds()))
    }
}

private const val DAY_AND_TIME_24_HOUR = "EEEHHmm"
private const val DAY_AND_TIME_12_HOUR = "EEEhmma"
```

- [ ] **Step 12: Add the moment list page**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt`:

```kotlin
package app.logdate.wear.v2.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.v2.moments.momentDayAndTime
import app.logdate.wear.v2.moments.momentLabel
import kotlinx.datetime.TimeZone

object FlowTags {
    const val LIST = "flow_list"
}

/** Home's second page: the moments of the last 7 days, newest first. */
@Composable
fun FlowPage(
    state: FlowUiState,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
) {
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, timeText = { TimeText(timeSource = timeSource) }) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier.testTag(FlowTags.LIST)) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.wear_flow_title))
                }
            }
            when (state) {
                FlowUiState.Loading -> Unit
                FlowUiState.Failed -> item { FlowMessage(stringResource(R.string.wear_flow_failed)) }
                is FlowUiState.Ready -> momentItems(state)
            }
        }
    }
}

private fun TransformingLazyColumnScope.momentItems(state: FlowUiState.Ready) {
    if (state.moments.isEmpty()) {
        item { FlowMessage(stringResource(R.string.wear_flow_empty)) }
        return
    }
    items(state.moments.size, key = { index -> state.moments[index].uid.toString() }) { index ->
        MomentRow(state.moments[index], state.timeZone)
    }
}

@Composable
private fun FlowMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MomentRow(
    note: JournalNote,
    timeZone: TimeZone,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = momentDayAndTime(note.creationTimestamp, timeZone),
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
```

- [ ] **Step 13: Bind the new view models**

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add the import `import app.logdate.wear.v2.flow.FlowViewModel` directly before `import app.logdate.wear.v2.haptics.HapticsPreferenceStore`, and `import app.logdate.wear.v2.recorder.RecorderViewModel` directly after `import app.logdate.wear.v2.phone.PhoneLinkRepository`. Then, after the `viewModel { TodayViewModel(...) }` block and before the module's closing brace, add:

```kotlin
        viewModel { RecorderViewModel() }
        viewModel { FlowViewModel(dayClock = get(), notesRepository = get()) }
```

- [ ] **Step 14: Make Home the pager**

Replace `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` with:

```kotlin
package app.logdate.wear.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.logdate.wear.v2.recorder.RecorderViewModel
import app.logdate.wear.v2.recorder.locksPaging
import app.logdate.wear.v2.theme.LogDateWearTheme
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
 * recorder is resolved here, outside every entry, so it belongs to the activity.
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
                    sceneStrategy = rememberSwipeDismissableSceneStrategy(),
                    entryProvider =
                        entryProvider {
                            entry<WearRoute.Home> { HomeEntry(recorder) }
                        },
                )
            }
        }
    }
}

@Composable
private fun HomeEntry(recorder: RecorderViewModel) {
    val recorderState by recorder.state.collectAsState()
    HomeScreen(
        pagingLocked = recorderState.locksPaging,
        today = { listState -> TodayEntry(listState) },
        flow = { listState -> FlowEntry(listState) },
    )
}

@Composable
private fun TodayEntry(listState: TransformingLazyColumnState) {
    val viewModel = koinViewModel<TodayViewModel>()
    val state by viewModel.uiState.collectAsState()
    val handoff by viewModel.handoff.collectAsState()
    TodayPage(
        state = state,
        handoff = handoff,
        onGetPhoneApp = viewModel::getPhoneApp,
        onHandoffDismissed = viewModel::dismissHandoff,
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

- [ ] **Step 15: Run the unit tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.*' :app:wear:ktlintCheck :app:wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; every `v2` test passes, including 2 + 7 + 4 new ones.

- [ ] **Step 16: Run the GMD tests to verify they pass**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.HomePagerRotaryTest,app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.ExperienceGateTest
```

Expected: `BUILD SUCCESSFUL`, 11 tests passed. If `turning_the_crown_*` tests time out with the list never receiving rotary input, the page's list has no focus: check that `HomeScreen` wraps each page in `AnimatedPage` inside `VerticalPagerScaffold` (that is what gives the current page focus) before changing anything else.

- [ ] **Step 17: Write the Flow screenshot scenes**

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/FlowPageScreenshots.kt`:

```kotlin
package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowUiState
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** Every state of Home's moment list, on the graphite theme. */
class FlowPageScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_FlowLoading() {
        FlowScene(FlowUiState.Loading)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_FlowEmpty() {
        FlowScene(FlowUiState.Ready(timeZone = flowZone, moments = emptyList()))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_FlowMoments() {
        FlowScene(FlowUiState.Ready(timeZone = flowZone, moments = recentMoments))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S04_FlowFailed() {
        FlowScene(FlowUiState.Failed)
    }
}

private val flowZone = TimeZone.currentSystemDefault()

private fun moment(
    day: Int,
    hour: Int,
): Instant = LocalDateTime(2026, 10, day, hour, 15).toInstant(flowZone)

private val recentMoments =
    listOf(
        JournalNote.Audio(mediaRef = "audio/walk.m4a", durationMs = 42_000, creationTimestamp = moment(3, 16), lastUpdated = moment(3, 16)),
        JournalNote.Text(creationTimestamp = moment(3, 9), lastUpdated = moment(3, 9), content = "Coffee before anyone else was up"),
        JournalNote.Image(mediaRef = "photos/lake.jpg", creationTimestamp = moment(2, 18), lastUpdated = moment(2, 18)),
        JournalNote.Text(creationTimestamp = moment(1, 21), lastUpdated = moment(1, 21), content = "Long call with Mum about the trip"),
    )

@Composable
private fun FlowScene(state: FlowUiState) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            FlowPage(state = state, timeSource = FlowTimeSource)
        }
    }
}

/** A fixed clock reading, so the time text renders the same on every run. */
private object FlowTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
```

- [ ] **Step 18: Run screenshot validation to verify it fails**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: FAIL; the report lists the 8 `FlowPageScreenshots` previews as missing references, and every other preview, including all of `TodayPageScreenshots`, passes.

- [ ] **Step 19: Record and check the references**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/FlowPageScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: `BUILD SUCCESSFUL`, `8`, and only the new `FlowPageScreenshots/` directory in the status. Open `S03_FlowMoments_Small Round_*.png` with the Read tool: "Last 7 days" as the header, then "Sat 4:15 PM" over "Voice note · 0:42", "Sat 9:15 AM" over the coffee note, "Fri 6:15 PM" over "Photo", and "Thu 9:15 PM" over the call note.

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 20: Run the whole watch unit suite once**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:detekt
```

Expected: `BUILD SUCCESSFUL`. Every v1 test that passed in Task 0 still passes.

- [ ] **Step 21: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task9-commit.txt <<'EOF'
chore(wear): add the Home pager with Today and the last 7 days

Home in the new watch app is now two pages: Today, and below it the
moments of the last 7 days, newest first. Swipe up or down to change
page. Turning the crown down at the end of Today opens the list, and
turning it up at the top of the list returns to Today. While a
recording runs, Home stays on Today, where Stop is; recording itself
comes with the recorder.

The list is read-only for now. Playing a moment, On this day, Rewind
and settings appear once each one works.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 22: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/recorder/RecorderViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/v2/home/HomePaging.kt app/wear/src/main/kotlin/app/logdate/wear/v2/home/HomeScreen.kt app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentText.kt app/wear/src/main/res/values/strings_home.xml app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/v2/recorder/RecorderStateTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/home/HomePagingTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/flow/FlowViewModelTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/HomePagerRotaryTest.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/FlowPageScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/FlowPageScreenshots && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task9-commit.txt
```

---

## Self-Review

**Spec coverage** (master plan Track B, B1 and B2; spec "The watch" and "Visual system"):

| Requirement | Task |
|---|---|
| Shared spring motion, the Record shape morph with `graphics-shapes` | 1 |
| Glyphs (watch, mark, record, lock, campfire), white copies for tiles and complications, a test keeping the two sets in sync | 1 |
| `HapticEvent` gains RecordingLocked, Paused, Resumed, MomentMarked, LimitApproaching, ActionRejected, DetentTick; critical classification | 2 |
| Non-Compose Android haptics factory | 2 |
| The watch plays the shared vocabulary, with Haptics All or Essential; `WearHapticEngine` untouched for v1 | 3, 8 (`LocalLogDateHaptics` in the v2 tree) |
| Dynamic color with `dynamicColorScheme(context)`; graphite fallback; orange only in the campfire; WCAG contrast test; applied to the v2 tree only | 4, 8 |
| `WearAppV2` = `AppScaffold` + `NavDisplay` + `rememberSwipeDismissableSceneStrategy()` with saveable-state and ViewModel-store decorators | 8 |
| Routes Home, Moment(id), CaptureSheet, PlayOn, Archive, PhoneCamera, serializable, string IDs | 8 |
| `WearExperienceGate` shows the shell when the flag is on | 8 (`MainActivityContent`; the gate itself is unchanged) |
| Activity-scoped `RecorderViewModel`; paging locked while recording (state only) | 9 |
| Home as a two-page `VerticalPagerScaffold` (Today, Flow) with page indicator; crown up at the top returns to Today | 9 |
| `DayClock` (midnight and time zone rollover) | 5 |
| `TodaySummaryCalculator` in `client/domain/.../today/`: moments = non-feeling notes; places by place ID or within 150 m; voice time; latest moment; feeling | 5 |
| Campfire on the watch: `ObserveCampfireUseCase` in the watch's Koin graph, watch-side preferences adapter, day start from the phone or 4 AM, `CAMPFIRE_STREAKS`, drawn with the shared `Campfire` | 6, 7 |
| `PhoneLinkRepository` (capability `logdate_phone_app`, `PhoneTypeHelper`) and `PhoneHandoff` (`RemoteActivityHelper`) | 6 |
| `TodayViewModel` with sealed UI state; the Today page (date in `TimeText`, campfire, summary line, latest moment, `ButtonGroup` with Mark and +, Record `EdgeButton`), "Get LogDate on your phone" | 7 |
| No dead controls | 7 (`TodayControls` handlers are null, so nothing is drawn), 9 (Flow shows no playback, cards, Earlier or Settings) |
| Screenshots of every Today state on both round sizes and at a 1.24 font scale | 7 |
| GMD tests for the gate, the shell and the pager | 8, 9 |

Not covered, deliberately: "Connected to <phone name>" after provisioning (needs Track A's link flow), contextual permissions (B3), the + sheet, Mark and Record behavior (B3, B4), the Flow page's playback, cards, Earlier and Settings (B5, B6), and the Haptics row in Settings (B6). Each is a later plan's, and nothing here draws it.

**Placeholder scan:** every code step carries complete code; no "TBD", "similar to Task N" or undefined references. `WearAppV2Placeholder` is deleted in Task 8, and Step 8's `grep` proves no reference remains.

**Type consistency:** `TodayWindow(date, timeZone, start, end)` is used the same way in Tasks 5, 7 and 9. `TodaySummaryCalculator.calculate(notes, isKnownFeeling)`, `MoodNoteTag.latestTag(notes, isKnown)` and `TodaySummary.feelingTag` agree between Tasks 5 and 7. `PhoneLink.AppMissing(nodeId)` → `PhoneCard.GetApp(nodeId)` → `PhoneHandoff.openAppListing(nodeId)` agree across Tasks 6 and 7. `TodayPage(..., listState)` from Task 7 is what Task 9 passes. `WearAppV2Tags.SHELL` is defined in Task 8's `WearAppV2.kt`, unchanged in Task 9's replacement, and used by `ExperienceGateTest` and `ShellNavigationTest`. `HapticsPreferenceStore`, `watchHaptics` and `wearV2Module` have the same shapes in Tasks 7 and 9 as in Task 3.

**Risks an implementer should know:**
- `ShellNavigationTest` and `ExperienceGateTest` resolve the real Koin graph on the emulator, where Play services has no phone: `PhoneLinkRepository` then logs one error and reports `NotConnected`, which is expected.
- Rotary input in `HomePagerRotaryTest` depends on the current page's list having focus; Step 16 says what to check if it does not.
- `TimeChangeSignals` has no unit test (it only registers a receiver); `DayClockTest` covers the behavior through a fake signal.
- Screenshot references depend on the host's default time zone only through instants built in that same zone, so they render the same everywhere.
