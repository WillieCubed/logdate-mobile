# Wear App Groundwork Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clear the ground for the redesigned watch app (Track B, step B0 of `docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md`): remove the fixed-time prompts, give feelings one source of truth, delete two unreachable screens, move to the current AndroidX Wear libraries, and add the experience gate and launch-request parser the new app plugs into.

**Architecture:** Five commits on the `:app:wear` module, each test-first and shippable on its own. Tasks 1–3 fix or delete shipped behavior without a flag. Task 4 changes only dependencies. Task 5 adds `WearExperienceGate`, the only reader of `FeatureFlag.WATCH_EXPERIENCE_V2`, which `MainActivity` consults once per activity behind the splash screen, plus `WearLaunchRequest`, which turns every `tile_route` a tile or complication ever sent into one typed request; with the flag on the watch shows a placeholder.

**Tech Stack:** Kotlin 2.3.21, AGP 9.2.1 (built-in Kotlin), Wear Compose Material 3 1.7.0, Navigation 3 1.1.1, Koin 4.2.1, kotlinx.coroutines 1.11.0, kotlinx-datetime 0.7.1, JUnit 4 + kotlin.test + MockK + Turbine (unit), Compose UI test v2 rules on the `wearSmallRoundApi34` Gradle Managed Device (instrumented), the Android screenshot plugin 0.0.1-alpha14.

## Global Constraints

- Prerequisite: Phase 0 has landed. `:app:wear` has `minSdk = 34` and `FeatureFlag.WATCH_EXPERIENCE_V2` exists in `client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/featureflags/FeatureFlag.kt`. Task 0 checks both; stop if either is missing.
- Hardware floor: Wear OS 5 and newer (`:app:wear` minSdk 34). Never lower it.
- Flags: Tasks 1–3 ship unflagged. `WearExperienceGate` (Task 5) is the only code in this plan that reads `FeatureFlag.WATCH_EXPERIENCE_V2`. The v2 content in this plan is a placeholder composable.
- `MainActivity` keeps its fully qualified name `app.logdate.wear.presentation.MainActivity`: `QuickCaptureTileService.kt:180`, `TodaySummaryTileService.kt:242`, `MoodComplicationService.kt:76`, `QuickCaptureComplicationService.kt:39` and both `benchmark/wear-*/…/WearBenchmarkConfig.kt:9` name it by string.
- Logging: Napier only (`io.github.aakira.napier.Napier`). Never `println`, `System.out` or `android.util.Log`.
- IDs: `kotlin.uuid.Uuid` (`Uuid.random()`, `Uuid.parseOrNull()`). Never `java.util.UUID`.
- Time: `kotlin.time.Clock` and `kotlin.time.Instant` (what `JournalNote` uses with kotlinx-datetime 0.7) and `kotlinx.datetime` for dates and zones.
- Serialization: kotlinx.serialization. Task 4 applies its Gradle plugin to `:app:wear`.
- DI: Koin constructor injection. Every new injectable type is registered in a Koin module, never looked up ad hoc.
- Imports: `kotlin.*` > `androidx.*`/`kotlinx.*` > `app.logdate.*`, no wildcards. Every file in `:app:wear` lists imports lexicographically with `kotlin.*` last (IntelliJ's ktlint layout); the code in this plan follows the files it edits.
- Code shape: early returns over nested if/else; no comments that restate the code; detekt limits hold (`LongMethod` 60 lines outside `@Composable`, `LargeClass` 600 lines) and nothing is added to `app/wear/detekt-baseline.xml`.
- Copy: literal, never "we", from string resources in `app/wear/src/main/res/values/strings.xml`, and no vague metaphor verbs ("kept", "held", "carried", "forming").
- Devices: emulator or Gradle Managed Device only. Never `connected*AndroidTest`, never `adb` against a physical device (one is usually attached). The watch GMD task is `:app:wear:wearDevicesGroupDebugAndroidTest` (device `wearSmallRoundApi34`); run one class with `-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>` and several with a comma-separated list.
- GMD runs take 3–5 minutes for a cold boot. A run that prints `Starting 0 tests` then `Process crashed` while `uptime` shows a load average above 100 is host load, not a test result; rerun it.
- Gradle in this worktree needs `export ANDROID_HOME="$HOME/Library/Android/sdk"` (there is no `local.properties`); the pre-commit hook runs Gradle too, so `git commit` gets `ANDROID_HOME=...` as a prefix.
- Run every command from the worktree root `/Users/williecubed/Projects/TheHypertextStudio/logdate-mobile/.claude/worktrees/backup-sync-infinite-notification-dd4c3a`.
- ktlint max line length is 140. `:app:wear:ktlintCheck` currently checks only `build.gradle.kts` (AGP 9's built-in Kotlin gives ktlint-gradle no source sets; `app/wear/build/reports/ktlint/` only ever holds `ktlintKotlinScriptCheck`), so keep line length and import order by hand.
- Screenshot baselines in `app/wear/src/screenshotTestDebug/reference/` change only in the commit whose code changes their pixels; CI's `validateAndroidScreenshots` checks them on every pull request touching `app/wear/**` or `gradle/**`.
- Commits: one step writes the message file with a heredoc in its own command; the next step runs `git restore --staged . && git add <exact paths> && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F <file>` as one chain, with deleted paths passed to `git add` too. Never `git add -A`, `git add .`, `--no-verify` or `-m`.
- Commit messages: Conventional Commits with scope `wear` (from `allowed-scopes.txt`), title at most 72 characters, body lines at most 72 characters, no "phase" followed by a number anywhere (the commit-msg hook rejects it), a body that explains user impact or root cause (`fix` bodies name no classes), and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` as the last line.

---

## File Structure

New packages under `app/wear/src/main/kotlin/app/logdate/wear/`:

| Path | Task | Responsibility |
|---|---|---|
| `reminders/LegacyPromptAlarms.kt` | 1 | Cancels the alarms, notification channel and preferences the removed prompts left on a watch. Deleted once no installed build can hold the alarms. |
| `capture/Feeling.kt` | 2 | The only table of feelings: tag, emoji code point, label resource. |
| `capture/MomentNoteCodec.kt` | 2 | Writes and reads the `#mood:<tag> Feeling <tag>` text note; classifies notes (`MomentNoteKind`). |
| `presentation/navigation/WearLaunchRoutes.kt` | 3, 5 | Task 3: `wearRouteFor(tileRoute)`. Task 5 replaces it with `WearLaunchRequest?.toV1Route()`. |
| `launch/WearExperienceGate.kt` | 5 | `WearExperience` (V1, V2) and the gate that reads the flag once. |
| `launch/WearLaunchRequest.kt` | 5 | Typed launch requests, `parse(route, noteId)` and `Intent.toWearLaunchRequest()`. |
| `v2/WearAppV2Placeholder.kt` | 5 | What the watch shows with the flag on until Track B's shell lands. |
| `di/ExperienceModule.kt` | 5 | Koin module for the gate. |
| `presentation/WearApp.kt` | 5 | The v1 Compose tree, moved out of `MainActivity.kt` (the entrypoint rule in `docs/reference/standards/entrypoint-structure.md`). |
| `presentation/MainActivityContent.kt` | 5 | Chooses between nothing (splash), v1 and the v2 placeholder. |

Deleted: `notification/` (4 files, Task 1), `presentation/health/` (2 files) and `presentation/rewind/` (3 files) (Task 3), and their unit tests. No screenshot baselines exist for the Health dashboard or Rewind screens.

Modified across tasks: `LogDateWearApplication.kt`, `AndroidManifest.xml`, `res/values/strings.xml`, `presentation/MainActivity.kt`, `presentation/settings/WearSettingsScreen.kt`, `presentation/mood/*`, `presentation/timeline/WearTimeline{ViewModel,Screen}.kt`, `complication/MoodComplicationService.kt`, `tile/TodaySummaryTileService.kt`, `presentation/navigation/WearNavRoutes.kt`, `di/AudioModule.kt`, `app/wear/build.gradle.kts`, `gradle/libs.versions.toml`, `docs/testing/wear-dogfood-checklist.md`.

Tests: unit tests in `app/wear/src/test/kotlin/app/logdate/wear/` (`capture/`, `complication/`, `tile/`, `launch/`, `presentation/…`), GMD tests in `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/`.

---

### Task 0: Confirm the prerequisites and record a baseline

**Files:** none changed.

**Interfaces:**
- Consumes: Phase 0's `:app:wear` `minSdk = 34` and `FeatureFlag.WATCH_EXPERIENCE_V2`.
- Produces: a GMD baseline report in the scratchpad that Tasks 2, 4 and 5 compare against.

- [ ] **Step 1: Check that Phase 0 landed**

```bash
grep -n "minSdk = 34" app/wear/build.gradle.kts
grep -n "WATCH_EXPERIENCE_V2" client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/featureflags/FeatureFlag.kt
```

Expected: each command prints one line. The flag line must not pass `availableForLaunch = false`: `DataStoreFeatureFlagStore.observe` reports such a flag as off whatever is stored, and Task 5's GMD tests turn the flag on. If either command prints nothing, stop: Phase 0 has not landed and this plan cannot start.

- [ ] **Step 2: Check the tree**

```bash
git status --short && git log --oneline -3
```

Expected: no output from `git status`; the log shows Phase 0's commits on top of `ae3967205 docs(wear): add the Wear OS and phone companion redesign spec`.

- [ ] **Step 3: Run the unit tests and screenshot validation**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:testDebugUnitTest :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`. If screenshot validation fails here, stop and report: the baselines are already stale, and later tasks could not tell their own pixel changes apart.

- [ ] **Step 4: Run the whole watch GMD suite once**

```bash
uptime && export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest --continue
```

Expected: the run finishes (it may report failures). Three failures are known on this base: `MoodCheckInScreenTest` `voice prompt displays buttons`, `voice prompt add voice triggers callback` and `voice prompt hides emoji when null` look for "Add voice note", which commit `e6b262d2b` renamed to "Open recorder". Task 2 fixes them.

- [ ] **Step 5: Save the baseline report**

```bash
mkdir -p /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/gmd-baseline && cp -R app/wear/build/reports/androidTests/managedDevice/debug/allDevices/. /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/gmd-baseline/
```

Expected: `scratchpad/gmd-baseline/index.html` lists every test class with its result. Any test that passes here must still pass after each later task.

---

### Task 1: Remove the morning and evening prompts (`fix(wear)`)

The watch schedules an 8 AM and a 9 PM alarm on every launch (`LogDateWearApplication.kt:54-55`), whatever the Settings switches say. The switches live only in `WearSettingsScreen` (Compose state plus the `wear_settings` SharedPreferences keys `morning_prompt` and `evening_prompt`); `WearSettingsViewModel` has no prompt state. Alarms survive app updates, so already-scheduled ones must be cancelled explicitly. `WearPromptScheduler.kt:71-83` built them as `PendingIntent.getBroadcast(context, 5001 or 5002, Intent(context, WearPromptReceiver::class.java), FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)`. The system matches a PendingIntent on its creator package, request code, intent (`filterEquals`: component `app.logdate.wear.notification.WearPromptReceiver` in the app's own package, no action, data or categories) and its `FLAG_IMMUTABLE` bit (`FLAG_UPDATE_CURRENT` is stripped before matching), so the lookup must pass `FLAG_IMMUTABLE`. The prompts also created the notification channel `wear_journal_prompts` (`WearPromptNotificationBuilder.kt:72`).

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/LegacyPromptAlarms.kt`
- Create: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/LegacyPromptAlarmsTest.kt`
- Delete: `app/wear/src/main/kotlin/app/logdate/wear/notification/PromptType.kt` (1-75), `WearPromptNotificationBuilder.kt` (1-75), `WearPromptReceiver.kt` (1-44), `WearPromptScheduler.kt` (1-85)
- Delete: `app/wear/src/test/kotlin/app/logdate/wear/notification/WearNotificationSchedulerTest.kt` (1-109)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt:9,54-55`
- Modify: `app/wear/src/main/AndroidManifest.xml:215-219`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/settings/WearSettingsScreen.kt:1-125,208,378-412`
- Modify: `app/wear/src/main/res/values/strings.xml:94-97,119-121`
- Modify: `docs/testing/wear-dogfood-checklist.md:63-64`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `object LegacyPromptAlarms { fun cancel(context: android.content.Context) }` in package `app.logdate.wear.reminders`, called once from `LogDateWearApplication.onCreate`. Removes `WearPromptScheduler`, `WearPromptReceiver`, `WearPromptNotificationBuilder`, `PromptType`, `PromptContent`, `PromptCooldownTracker`, and the `morningPromptEnabled`/`eveningPromptEnabled` parameters of `WearSettingsContent`.

- [ ] **Step 1: Add a no-op `LegacyPromptAlarms` so the test compiles**

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/LegacyPromptAlarms.kt`:

```kotlin
package app.logdate.wear.reminders

import android.content.Context

object LegacyPromptAlarms {
    fun cancel(context: Context) {
    }
}
```

- [ ] **Step 2: Write the failing GMD test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/LegacyPromptAlarmsTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.wear.reminders.LegacyPromptAlarms
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recreates what builds with the fixed morning and evening prompts left on a watch, then checks
 * that the cleanup removes it: two repeating alarms, their notification channel and their switches.
 *
 * The alarms are built here the way those builds built them, independently of the cleanup, so a
 * wrong request code, receiver name or flag in the cleanup fails these tests.
 */
@RunWith(AndroidJUnit4::class)
class LegacyPromptAlarmsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @After
    fun tearDown() {
        OLD_REQUEST_CODES.forEach { requestCode ->
            val alarm = oldPromptAlarm(requestCode, PendingIntent.FLAG_NO_CREATE) ?: return@forEach
            alarmManager.cancel(alarm)
            alarm.cancel()
        }
    }

    @Test
    fun the_old_morning_and_evening_alarms_are_cancelled() {
        OLD_REQUEST_CODES.forEach { requestCode ->
            val alarm = checkNotNull(oldPromptAlarm(requestCode, PendingIntent.FLAG_UPDATE_CURRENT))
            alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + AlarmManager.INTERVAL_DAY,
                AlarmManager.INTERVAL_DAY,
                alarm,
            )
        }

        LegacyPromptAlarms.cancel(context)

        OLD_REQUEST_CODES.forEach { requestCode ->
            assertNull(
                "The alarm with request code $requestCode should be gone",
                oldPromptAlarm(requestCode, PendingIntent.FLAG_NO_CREATE),
            )
        }
    }

    @Test
    fun cancelling_when_nothing_is_left_is_harmless() {
        LegacyPromptAlarms.cancel(context)
        LegacyPromptAlarms.cancel(context)
    }

    @Test
    fun the_old_journal_reminders_channel_is_deleted() {
        val notifications = NotificationManagerCompat.from(context)
        notifications.createNotificationChannel(
            NotificationChannelCompat
                .Builder(OLD_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Journal reminders")
                .build(),
        )
        assertNotNull(notifications.getNotificationChannel(OLD_CHANNEL_ID))

        LegacyPromptAlarms.cancel(context)

        assertNull(notifications.getNotificationChannel(OLD_CHANNEL_ID))
    }

    @Test
    fun the_old_prompt_switches_are_forgotten() {
        context
            .getSharedPreferences(OLD_SETTINGS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("morning_prompt", false)
            .putBoolean("evening_prompt", false)
            .commit()

        LegacyPromptAlarms.cancel(context)

        val settings = context.getSharedPreferences(OLD_SETTINGS_FILE, Context.MODE_PRIVATE)
        assertFalse(settings.contains("morning_prompt"))
        assertFalse(settings.contains("evening_prompt"))
    }

    /** The PendingIntent the old prompt scheduler used for [requestCode], looked up with [flag]. */
    private fun oldPromptAlarm(
        requestCode: Int,
        flag: Int,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent().setClassName(context, OLD_RECEIVER),
            flag or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val OLD_RECEIVER = "app.logdate.wear.notification.WearPromptReceiver"
        val OLD_REQUEST_CODES = listOf(5001, 5002)
        const val OLD_CHANNEL_ID = "wear_journal_prompts"
        const val OLD_SETTINGS_FILE = "wear_settings"
    }
}
```

- [ ] **Step 3: Run it on the GMD and watch it fail**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.LegacyPromptAlarmsTest
```

Expected: FAIL. `the_old_morning_and_evening_alarms_are_cancelled` fails with "The alarm with request code 5001 should be gone", and `the_old_journal_reminders_channel_is_deleted` and `the_old_prompt_switches_are_forgotten` fail on their assertions. `cancelling_when_nothing_is_left_is_harmless` passes.

- [ ] **Step 4: Implement `LegacyPromptAlarms`**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/reminders/LegacyPromptAlarms.kt`:

```kotlin
package app.logdate.wear.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.github.aakira.napier.Napier

/**
 * Removes what the fixed morning and evening journal prompts left on a watch.
 *
 * Builds with the prompts scheduled two repeating alarms. Alarms survive app updates, so without
 * this they would keep waking the watch at 8 AM and 9 PM to deliver a broadcast to a receiver that
 * no longer exists. Those builds also created the "Journal reminders" notification channel and
 * saved the two prompt switches.
 *
 * Each step finds nothing to remove once it has run, so calling this on every start is cheap and
 * safe. Delete this object once no installed build can still hold the alarms.
 */
object LegacyPromptAlarms {
    /** The receiver the old alarms target. The class no longer exists, so it is named by string. */
    private const val RECEIVER_CLASS_NAME = "app.logdate.wear.notification.WearPromptReceiver"

    /** The request codes of the old morning (8 AM) and evening (9 PM) alarms. */
    private val REQUEST_CODES = listOf(5001, 5002)

    private const val CHANNEL_ID = "wear_journal_prompts"

    /** The preferences file that held only the morning and evening prompt switches. */
    private const val SETTINGS_FILE = "wear_settings"

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        REQUEST_CODES.forEach { requestCode ->
            val alarm = scheduledAlarm(context, requestCode) ?: return@forEach
            alarmManager.cancel(alarm)
            alarm.cancel()
            Napier.i("Cancelled the legacy journal prompt alarm $requestCode")
        }
        NotificationManagerCompat.from(context).deleteNotificationChannel(CHANNEL_ID)
        context.deleteSharedPreferences(SETTINGS_FILE)
    }

    /**
     * The registered PendingIntent for [requestCode], or null when there is none. The system matches
     * a PendingIntent on its request code, its intent and its FLAG_IMMUTABLE bit, so all three are
     * rebuilt the way the old scheduler built them.
     */
    private fun scheduledAlarm(
        context: Context,
        requestCode: Int,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent().setClassName(context, RECEIVER_CLASS_NAME),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
}
```

- [ ] **Step 5: Delete the prompt code and its unit test**

```bash
rm app/wear/src/main/kotlin/app/logdate/wear/notification/PromptType.kt app/wear/src/main/kotlin/app/logdate/wear/notification/WearPromptNotificationBuilder.kt app/wear/src/main/kotlin/app/logdate/wear/notification/WearPromptReceiver.kt app/wear/src/main/kotlin/app/logdate/wear/notification/WearPromptScheduler.kt app/wear/src/test/kotlin/app/logdate/wear/notification/WearNotificationSchedulerTest.kt && rmdir app/wear/src/main/kotlin/app/logdate/wear/notification app/wear/src/test/kotlin/app/logdate/wear/notification
```

Expected: no output.

- [ ] **Step 6: Cancel the old prompts at startup instead of scheduling them**

In `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`, delete line 9:

```kotlin
import app.logdate.wear.notification.WearPromptScheduler
```

and add this import directly below `import app.logdate.wear.recording.WearRecordingRecovery` (original line 10):

```kotlin
import app.logdate.wear.reminders.LegacyPromptAlarms
```

The import count does not change, so the next lines keep their numbers. Replace lines 54-55:

```kotlin
        // Schedule morning/evening journal prompt alarms.
        WearPromptScheduler(this).scheduleAll()
```

with:

```kotlin
        runCatching { LegacyPromptAlarms.cancel(this) }
            .onFailure { error -> Napier.w("Failed to remove the legacy journal prompts", error) }
```

- [ ] **Step 7: Remove the receiver from the manifest**

In `app/wear/src/main/AndroidManifest.xml`, delete lines 215-219 (the comment, the receiver and the blank line after it):

```xml
        <!-- Journal prompt alarm receiver -->
        <receiver
            android:name=".notification.WearPromptReceiver"
            android:exported="false" />

```

- [ ] **Step 8: Remove the prompt switches from the watch's Settings**

In `app/wear/src/main/kotlin/app/logdate/wear/presentation/settings/WearSettingsScreen.kt`, replace lines 1-125 (from `package` through the `) {` that opens `WearSettingsContent`'s body) with:

```kotlin
package app.logdate.wear.presentation.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhonelinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.wear.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WearSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: WearSettingsViewModel = koinViewModel(),
) {
    val settingsState by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> viewModel.startPolling()
                    Lifecycle.Event.ON_PAUSE -> viewModel.stopPolling()
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    WearSettingsContent(
        settingsState = settingsState,
        onSyncNow = { viewModel.syncNow() },
        onBackgroundLocationChanged = { enabled ->
            viewModel.setBackgroundLocationEnabled(enabled)
        },
        onLocationCaptureModeChanged = { mode ->
            viewModel.setLocationCaptureMode(mode)
        },
        onOpenLocationPermissions = { viewModel.openLocationPermissions() },
    )
}

@Composable
internal fun WearSettingsContent(
    settingsState: WearSettingsUiState = WearSettingsUiState(),
    onSyncNow: () -> Unit = {},
    onBackgroundLocationChanged: (Boolean) -> Unit = {},
    onLocationCaptureModeChanged: (LocationCaptureMode) -> Unit = {},
    onOpenLocationPermissions: () -> Unit = {},
) {
```

Then, in the same file, delete the unused local in the `last_sync` item (original line 208):

```kotlin
                val context = LocalContext.current
```

and delete the blank line plus the whole Notifications section (original lines 378-412), from:

```kotlin

            // Notifications section
            item(key = "notifications_header") {
```

through the end of `item(key = "evening_prompt") { … }`, so that the `}` closing `item(key = "active_location_mode")` is followed directly by the two closing braces of `ScalingLazyColumn` and `ScreenScaffold`:

```kotlin
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
```

- [ ] **Step 9: Remove the prompt strings**

In `app/wear/src/main/res/values/strings.xml`, delete lines 94-97:

```xml
    <!-- Journal Prompt Notifications -->
    <string name="wear_prompt_channel_name">Journal reminders</string>
    <string name="wear_prompt_channel_description">Morning and evening journaling reminders</string>

```

and lines 119-121 (now 115-117):

```xml
    <string name="wear_settings_notifications">Notifications</string>
    <string name="wear_settings_morning_prompt">Morning prompt</string>
    <string name="wear_settings_evening_prompt">Evening prompt</string>
```

- [ ] **Step 10: Confirm nothing still refers to the prompts**

```bash
grep -rn "WearPromptScheduler\|WearPromptReceiver\|PromptCooldownTracker\|wear_prompt_channel\|wear_settings_morning_prompt\|wear_settings_notifications" app/wear/src
```

Expected: only `LegacyPromptAlarms.kt` and `LegacyPromptAlarmsTest.kt`, each naming `app.logdate.wear.notification.WearPromptReceiver` as a string.

- [ ] **Step 11: Run the unit tests and the static checks**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:testDebugUnitTest :app:wear:assembleDebug :app:wear:lintDebug :app:wear:detekt :app:wear:ktlintCheck :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`. Settings has no screenshot test, so no baseline changes.

- [ ] **Step 12: Run the GMD test again**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.LegacyPromptAlarmsTest
```

Expected: PASS, 4 tests.

- [ ] **Step 13: Add the real-hardware check to the dogfood checklist**

In `docs/testing/wear-dogfood-checklist.md`, insert before line 64 (`## Known gaps`):

```markdown
## Reminders

| Do this | You should see |
|---------|----------------|
| Install this build over one that showed "Good morning!" or "Time to reflect", then wear the watch past 8 AM and past 9 PM | Neither notification appears |
| Open Settings on the watch | No Morning prompt or Evening prompt switch |
| Open LogDate's notification settings on the watch | No "Journal reminders" category |

```

- [ ] **Step 14: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-1-prompts.txt <<'EOF'
fix(wear): remove the morning and evening journal prompts

The watch posted "Good morning!" around 8 AM and "Time to reflect"
around 9 PM every day. Turning off the Morning prompt or Evening
prompt switch in the watch's Settings did not stop them, because
every launch scheduled both prompts again. The prompts and both
switches are gone.

A watch that already has the prompts scheduled cancels them the next
time LogDate starts, and the "Journal reminders" notification
category they posted to disappears from the watch's notification
settings.

Reminders that respond to what you are doing, and that stay off until
you turn them on, replace these prompts in a later change.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 15: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/reminders/LegacyPromptAlarms.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/LegacyPromptAlarmsTest.kt app/wear/src/main/kotlin/app/logdate/wear/notification/PromptType.kt app/wear/src/main/kotlin/app/logdate/wear/notification/WearPromptNotificationBuilder.kt app/wear/src/main/kotlin/app/logdate/wear/notification/WearPromptReceiver.kt app/wear/src/main/kotlin/app/logdate/wear/notification/WearPromptScheduler.kt app/wear/src/test/kotlin/app/logdate/wear/notification/WearNotificationSchedulerTest.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/main/AndroidManifest.xml app/wear/src/main/kotlin/app/logdate/wear/presentation/settings/WearSettingsScreen.kt app/wear/src/main/res/values/strings.xml docs/testing/wear-dogfood-checklist.md && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-1-prompts.txt
```

Expected: the hooks pass and `git show --stat HEAD` lists 12 paths: 2 added, 5 deleted, 5 modified. `git status --short` is empty.

---

### Task 2: One source of truth for feelings (`fix(wear)`)

Three bugs share one cause: every surface parses mood notes and draws emoji on its own.

- `MoodComplicationService.kt:62-68` and `TodaySummaryTileService.kt:66-73` keep everything after `#mood:`. `MoodCheckInViewModel.kt:91` writes `#mood:great Feeling great`, so they get `great Feeling great`, `moodToEmoji` (`MoodComplicationService.kt:113-121`) returns "—" for every real check-in, and the tile draws "— great Feeling great".
- Sad and Rough differ: the check-in screen offers 😔 U+1F614 and 😰 U+1F630 (`MoodCheckInViewModel.kt:32-33`), the complication draws 😞 U+1F61E and 😣 U+1F623 (`MoodComplicationService.kt:109-110`). The check-in screen's faces are the canonical ones: they are what the user picked.
- `MoodCheckInViewModel.attachVoice()` (`:127-129`) only emits `NavigateToVoiceNote`, and `MainActivity.kt:126` maps that to `navigateBack`, so "Open recorder" (the button `e6b262d2b` relabelled from "Add voice note") drops the mood. Mapping it to `navigateBack` also does nothing when a tile or complication opened the check-in directly (back stack of one), so it now replaces the stack with Home, which is the recorder.

`WearTimelineViewModel.kt:164-182` has the same parsing in a third copy: the day card shows the raw tag ("good") and can use a check-in as preview text. The stored format `#mood:<tag> Feeling <tag>` does not change: saved notes and the phone's timeline show it as written.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/capture/Feeling.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/capture/MomentNoteCodec.kt`
- Create: `app/wear/src/test/kotlin/app/logdate/wear/capture/MomentNoteCodecTest.kt`
- Create: `app/wear/src/test/kotlin/app/logdate/wear/complication/MoodComplicationTextTest.kt`
- Create: `app/wear/src/test/kotlin/app/logdate/wear/tile/TodaySummaryTest.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/complication/MoodComplicationService.kt:1-123` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/tile/TodaySummaryTileService.kt:1-273` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/timeline/WearTimelineViewModel.kt:7,30,156,164-182`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/timeline/WearTimelineScreen.kt:123-129`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/mood/MoodCheckInViewModel.kt:1-130` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/mood/MoodCheckInScreen.kt:30,80,94-101,108,126`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt:122-128`
- Modify: `app/wear/src/main/res/values/strings.xml` (after `wear_mood_saved`, original line 130)
- Modify: `app/wear/src/test/kotlin/app/logdate/wear/presentation/mood/MoodCheckInViewModelTest.kt:1-189` (whole file)
- Modify: `app/wear/src/test/kotlin/app/logdate/wear/presentation/timeline/WearTimelineViewModelTest.kt:4,193-213`
- Modify: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MoodCheckInScreenTest.kt:1-209` (whole file)
- Modify: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MoodCheckInScreenshots.kt:5,27,40`
- Modify: `docs/testing/wear-dogfood-checklist.md` (before `## Reminders`)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces (package `app.logdate.wear.capture`):
  - `enum class Feeling(val tag: String, val emojiCodePoint: Int, @param:StringRes val labelRes: Int)` with `GREAT`, `GOOD`, `OK`, `SAD`, `ROUGH`, `val emoji: String` and `companion object { fun fromTag(tag: String): Feeling? }`
  - `enum class MomentNoteKind { FEELING, TEXT, VOICE, PHOTO, VIDEO }`
  - `object MomentNoteCodec { fun encode(feeling: Feeling): String; fun feelingOf(content: String): Feeling?; fun classify(note: JournalNote): MomentNoteKind; fun latestFeeling(notes: List<JournalNote>): Feeling? }`
- Produces elsewhere: `internal fun feelingComplicationText(feeling: Feeling?): String` (`app.logdate.wear.complication`); `internal data class TodaySummary(val entryCount: Int, val feeling: Feeling?, val latestEntry: JournalNote?)` and `internal fun summarizeToday(notes: List<JournalNote>): TodaySummary` (`app.logdate.wear.tile`); `WearTimelineDayUiState.feeling: Feeling?` replaces `latestMood: String?`; `MoodCheckInUiState.selectedMood: Feeling?`; `MoodCheckInViewModel.selectMood(mood: Feeling)`.
- Removes: `MoodOption`, `MoodComplicationService.moodToEmoji`.

- [ ] **Step 1: Add the feeling table and its labels**

Create `app/wear/src/main/kotlin/app/logdate/wear/capture/Feeling.kt`:

```kotlin
package app.logdate.wear.capture

import androidx.annotation.StringRes
import app.logdate.wear.R

/**
 * The five feelings a mood check-in offers, in the order the check-in screen lists them.
 *
 * This is the only table of feelings on the watch: the check-in screen, the Mood complication, the
 * Today's Summary tile and the timeline read a feeling's tag, emoji and name from here.
 *
 * @property tag What a check-in note stores (see [MomentNoteCodec]). Saved notes already use these
 *   tags, so they never change.
 * @property emojiCodePoint The Unicode code point of the feeling's emoji.
 * @property labelRes The feeling's name, shown next to its emoji.
 */
enum class Feeling(
    val tag: String,
    val emojiCodePoint: Int,
    @param:StringRes val labelRes: Int,
) {
    GREAT(tag = "great", emojiCodePoint = 0x1F604, labelRes = R.string.wear_feeling_great),
    GOOD(tag = "good", emojiCodePoint = 0x1F642, labelRes = R.string.wear_feeling_good),
    OK(tag = "ok", emojiCodePoint = 0x1F610, labelRes = R.string.wear_feeling_ok),
    SAD(tag = "sad", emojiCodePoint = 0x1F614, labelRes = R.string.wear_feeling_sad),
    ROUGH(tag = "rough", emojiCodePoint = 0x1F630, labelRes = R.string.wear_feeling_rough),
    ;

    /** The feeling's emoji as text. */
    val emoji: String get() = String(Character.toChars(emojiCodePoint))

    companion object {
        /** The feeling stored under [tag], or null when no feeling uses it. */
        fun fromTag(tag: String): Feeling? = entries.firstOrNull { it.tag == tag }
    }
}
```

In `app/wear/src/main/res/values/strings.xml`, add after `<string name="wear_mood_saved">Saved</string>`:

```xml
    <string name="wear_feeling_great">Great</string>
    <string name="wear_feeling_good">Good</string>
    <string name="wear_feeling_ok">OK</string>
    <string name="wear_feeling_sad">Sad</string>
    <string name="wear_feeling_rough">Rough</string>
```

- [ ] **Step 2: Add a stub codec that finds no feelings**

Create `app/wear/src/main/kotlin/app/logdate/wear/capture/MomentNoteCodec.kt` (Step 7 replaces the bodies):

```kotlin
package app.logdate.wear.capture

import app.logdate.client.repository.journals.JournalNote

enum class MomentNoteKind {
    FEELING,
    TEXT,
    VOICE,
    PHOTO,
    VIDEO,
}

object MomentNoteCodec {
    fun encode(feeling: Feeling): String = ""

    fun feelingOf(content: String): Feeling? = null

    fun classify(note: JournalNote): MomentNoteKind = MomentNoteKind.TEXT

    fun latestFeeling(notes: List<JournalNote>): Feeling? = null
}
```

- [ ] **Step 3: Route the complication through the codec**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/complication/MoodComplicationService.kt`:

```kotlin
package app.logdate.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.MomentNoteCodec
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private const val NO_FEELING_TEXT = "—"

/**
 * What the Mood complication shows for [feeling]: the emoji the check-in screen showed for it, or a
 * dash on a day without a check-in.
 */
internal fun feelingComplicationText(feeling: Feeling?): String = feeling?.emoji ?: NO_FEELING_TEXT

/**
 * Complication showing the user's most recent mood for today.
 *
 * Displays the mood as a short emoji. Tapping opens the mood check-in screen.
 */
class MoodComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return createComplicationData(
            text = feelingComplicationText(Feeling.GOOD),
            contentDescription = getString(R.string.wear_complication_mood_description),
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val feeling = fetchTodayFeeling()
        val description =
            if (feeling != null) {
                getString(R.string.wear_complication_mood_current, getString(feeling.labelRes))
            } else {
                getString(R.string.wear_complication_mood_none)
            }
        return createComplicationData(
            text = feelingComplicationText(feeling),
            contentDescription = description,
            tapIntent = createMoodCheckInIntent(),
        )
    }

    private suspend fun fetchTodayFeeling(): Feeling? =
        try {
            val repository =
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<JournalNotesRepository>()
            val today =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            MomentNoteCodec.latestFeeling(repository.observeNotesForDay(today).first())
        } catch (e: Exception) {
            Napier.e("Failed to read today's mood for the Wear complication", e)
            null
        }

    private fun createMoodCheckInIntent(): PendingIntent {
        val intent =
            Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(packageName, "app.logdate.wear.presentation.MainActivity")
                putExtra("tile_route", "mood")
            }
        return PendingIntent.getActivity(
            this,
            MOOD_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createComplicationData(
        text: String,
        contentDescription: String,
        tapIntent: PendingIntent? = null,
    ): ShortTextComplicationData {
        val builder =
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(text).build(),
                contentDescription = PlainComplicationText.Builder(contentDescription).build(),
            )
        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }

    companion object {
        private const val MOOD_REQUEST_CODE = 1001
    }
}
```

- [ ] **Step 4: Route the Today's Summary tile through the codec**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/tile/TodaySummaryTileService.kt` (the layout code from `todaySummaryTile` down is unchanged except `moodLine`, the preview line and the new `latestEntryPreview`):

```kotlin
package app.logdate.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.MomentNoteCodec
import app.logdate.wear.capture.MomentNoteKind
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private const val RESOURCES_VERSION = "1"
private const val MAX_PREVIEW_LENGTH = 30

/**
 * Today's Summary tile showing entry count, current mood, and latest entry preview.
 *
 * Tapping opens the app to the timeline view.
 */
@OptIn(ExperimentalHorologistApi::class)
class TodaySummaryTileService : SuspendingTileService() {
    override suspend fun resourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources =
        ResourceBuilders.Resources
            .Builder()
            .setVersion(RESOURCES_VERSION)
            .build()

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val summary = fetchTodaySummary()
        return todaySummaryTile(requestParams, this, summary)
    }

    private suspend fun fetchTodaySummary(): TodaySummary =
        try {
            val repository =
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<JournalNotesRepository>()
            val today =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            summarizeToday(repository.observeNotesForDay(today).first())
        } catch (e: Exception) {
            Napier.e("Failed to read today's notes for the Wear summary tile", e)
            TodaySummary(entryCount = 0, feeling = null, latestEntry = null)
        }
}

/**
 * What the tile shows: how many notes today has, the feeling of today's newest mood check-in, and
 * the newest note that is not a check-in.
 */
internal data class TodaySummary(
    val entryCount: Int,
    val feeling: Feeling?,
    val latestEntry: JournalNote?,
)

internal fun summarizeToday(notes: List<JournalNote>): TodaySummary =
    TodaySummary(
        entryCount = notes.size,
        feeling = MomentNoteCodec.latestFeeling(notes),
        latestEntry =
            notes
                .filter { MomentNoteCodec.classify(it) != MomentNoteKind.FEELING }
                .maxByOrNull(JournalNote::creationTimestamp),
    )

internal fun todaySummaryTile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    summary: TodaySummary,
): TileBuilders.Tile {
    val timeline =
        TimelineBuilders.Timeline
            .Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry
                    .Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout
                            .Builder()
                            .setRoot(todaySummaryLayout(requestParams, context, summary))
                            .build(),
                    ).build(),
            ).build()

    return TileBuilders.Tile
        .Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(timeline)
        .setFreshnessIntervalMillis(15 * 60 * 1000L)
        .build()
}

private fun todaySummaryLayout(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    summary: TodaySummary,
): LayoutElementBuilders.LayoutElement {
    val entryLabel =
        context.resources.getQuantityString(
            R.plurals.wear_tile_entry_count,
            summary.entryCount,
            summary.entryCount,
        )

    val moodLine =
        summary.feeling?.let { feeling ->
            context.getString(R.string.wear_tile_summary_mood, feeling.emoji, context.getString(feeling.labelRes))
        }
    val latestPreview = summary.latestEntry?.let { note -> latestEntryPreview(context, note) }

    return PrimaryLayout
        .Builder(requestParams.deviceConfiguration)
        .setResponsiveContentInsetEnabled(true)
        .setPrimaryLabelTextContent(
            Text
                .Builder(context, context.getString(R.string.wear_tile_summary_title))
                .setColor(argb(Colors.DEFAULT.primary))
                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                .build(),
        ).setContent(
            summaryContent(context, entryLabel, moodLine, latestPreview),
        ).setSecondaryLabelTextContent(
            Text
                .Builder(context, context.getString(R.string.wear_tile_summary_open))
                .setColor(argb(Colors.DEFAULT.onSurface))
                .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                .setModifiers(
                    ModifiersBuilders.Modifiers
                        .Builder()
                        .setClickable(
                            ModifiersBuilders.Clickable
                                .Builder()
                                .setOnClick(createTimelineLaunchAction(context))
                                .setId("open_timeline")
                                .build(),
                        ).build(),
                ).build(),
        ).build()
}

private fun latestEntryPreview(
    context: Context,
    note: JournalNote,
): String =
    when (note) {
        is JournalNote.Text -> note.content.take(MAX_PREVIEW_LENGTH)
        is JournalNote.Audio -> context.getString(R.string.wear_tile_summary_voice_note)
        is JournalNote.Image -> context.getString(R.string.wear_tile_summary_photo)
        is JournalNote.Video -> context.getString(R.string.wear_tile_summary_video)
    }

private fun summaryContent(
    context: Context,
    entryLabel: String,
    moodLine: String?,
    latestPreview: String?,
): LayoutElementBuilders.LayoutElement {
    val column =
        LayoutElementBuilders.Column
            .Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .addContent(
                Text
                    .Builder(context, entryLabel)
                    .setColor(argb(Colors.DEFAULT.onSurface))
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .build(),
            )

    if (moodLine != null) {
        column
            .addContent(spacer(4f))
            .addContent(
                Text
                    .Builder(context, moodLine)
                    .setColor(argb(Colors.DEFAULT.onSurface))
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .build(),
            )
    }

    if (latestPreview != null) {
        column
            .addContent(spacer(4f))
            .addContent(
                Text
                    .Builder(
                        context,
                        context.getString(R.string.wear_tile_summary_latest, latestPreview),
                    ).setColor(argb(Colors.DEFAULT.onSurface))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setMaxLines(2)
                    .build(),
            )
    }

    return column.build()
}

private fun spacer(heightDp: Float): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Spacer
        .Builder()
        .setHeight(dp(heightDp))
        .build()

private fun createTimelineLaunchAction(context: Context): ActionBuilders.LaunchAction =
    ActionBuilders.LaunchAction
        .Builder()
        .setAndroidActivity(
            ActionBuilders.AndroidActivity
                .Builder()
                .setPackageName(context.packageName)
                .setClassName(
                    ComponentName(context, "app.logdate.wear.presentation.MainActivity")
                        .className,
                ).addKeyToExtraMapping(
                    "tile_route",
                    ActionBuilders.AndroidStringExtra
                        .Builder()
                        .setValue("timeline")
                        .build(),
                ).build(),
        ).build()

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun todaySummaryTilePreview(context: Context) =
    TilePreviewData(
        onTileResourceRequest = {
            ResourceBuilders.Resources
                .Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        },
    ) {
        todaySummaryTile(
            it,
            context,
            TodaySummary(
                entryCount = 5,
                feeling = Feeling.GOOD,
                latestEntry =
                    JournalNote.Text(
                        creationTimestamp = Instant.fromEpochMilliseconds(0),
                        lastUpdated = Instant.fromEpochMilliseconds(0),
                        content = "Had an amazing lunch...",
                    ),
            ),
        )
    }
```

- [ ] **Step 5: Route the timeline through the codec**

In `app/wear/src/main/kotlin/app/logdate/wear/presentation/timeline/WearTimelineViewModel.kt`, add after line 7 (`import app.logdate.client.sync.datalayer.WearAudioRequestPaths`):

```kotlin
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.MomentNoteCodec
import app.logdate.wear.capture.MomentNoteKind
```

Replace line 30:

```kotlin
    val latestMood: String? = null,
```

with:

```kotlin
    val feeling: Feeling? = null,
```

Replace line 156:

```kotlin
                        latestMood = extractMood(dayNotes),
```

with:

```kotlin
                        feeling = MomentNoteCodec.latestFeeling(dayNotes),
```

Replace lines 164-182 (`extractMood` and `extractPreview`) with:

```kotlin
    private fun extractPreview(notes: List<JournalNote>): String? =
        notes
            .filterIsInstance<JournalNote.Text>()
            .firstOrNull { MomentNoteCodec.classify(it) == MomentNoteKind.TEXT }
            ?.content
            ?.take(50)
```

In `app/wear/src/main/kotlin/app/logdate/wear/presentation/timeline/WearTimelineScreen.kt`, replace lines 123-129:

```kotlin
        if (day.latestMood != null) {
            Text(
                text = day.latestMood,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
```

with:

```kotlin
        if (day.feeling != null) {
            Text(
                text = "${day.feeling.emoji} ${stringResource(day.feeling.labelRes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
```

- [ ] **Step 6: Move the check-in screen, its tests and its navigation onto `Feeling`**

In `app/wear/src/main/kotlin/app/logdate/wear/presentation/mood/MoodCheckInViewModel.kt` (line numbers are the original ones; make the edits from the bottom up so they stay valid):
- replace `content = "#mood:${mood.tag} Feeling ${mood.label.lowercase()}",` (line 91) with `content = MomentNoteCodec.encode(mood),`;
- replace `fun selectMood(mood: MoodOption) {` (line 71) with `fun selectMood(mood: Feeling) {`;
- replace `val selectedMood: MoodOption? = null,` (line 44) with `val selectedMood: Feeling? = null,`;
- delete lines 24-35 (`enum class MoodOption(` through its closing `}` and the blank line after it);
- add after line 7 (`import app.logdate.client.repository.journals.SystemCaptureTimeZone`):

```kotlin
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.MomentNoteCodec
```

`attachVoice()` stays as it is until Step 9.

In `app/wear/src/main/kotlin/app/logdate/wear/presentation/mood/MoodCheckInScreen.kt`:
- add after line 30 (`import app.logdate.wear.R`): `import app.logdate.wear.capture.Feeling`
- replace line 80 with `internal fun SelectMoodContent(onMoodSelected: (Feeling) -> Unit) {`
- replace lines 94-101 with:

```kotlin
            items(Feeling.entries) { feeling ->
                FilledTonalButton(
                    onClick = { onMoodSelected(feeling) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "${feeling.emoji} ${stringResource(feeling.labelRes)}")
                }
            }
```

- replace line 108 with `    selectedMood: Feeling?,`
- replace line 126 (`text = selectedMood.label,`) with `text = stringResource(selectedMood.labelRes),`

In `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt`, replace lines 122-128:

```kotlin
                    entry<WearMoodCheckInRoute> {
                        MoodCheckInScreen(
                            onNavigateBack = navigateBack,
                            // The recorder is the Home screen; it never starts recording on its own.
                            onNavigateToVoiceNote = navigateBack,
                        )
                    }
```

with:

```kotlin
                    entry<WearMoodCheckInRoute> {
                        MoodCheckInScreen(
                            onNavigateBack = navigateBack,
                            // The recorder is the Home screen; it never starts recording on its own. Replacing the
                            // stack also reaches Home when a tile or complication opened the check-in directly.
                            onNavigateToVoiceNote = {
                                backStack.clear()
                                backStack.add(WearHomeRoute)
                            },
                        )
                    }
```

In `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MoodCheckInScreenshots.kt`, replace line 5 (`import app.logdate.wear.presentation.mood.MoodOption`) with `import app.logdate.wear.capture.Feeling` (it sorts into the same place), then replace `MoodOption.GREAT` (line 27) with `Feeling.GREAT` and `MoodOption.SAD` (line 40) with `Feeling.SAD`.

Replace the whole of `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MoodCheckInScreenTest.kt` (it also fixes the three tests that still look for "Add voice note", and scrolls to options below the fold on the small round GMD):

```kotlin
package app.logdate.wear.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.wear.capture.Feeling
import app.logdate.wear.presentation.mood.MoodSavedContent
import app.logdate.wear.presentation.mood.SelectMoodContent
import app.logdate.wear.presentation.mood.VoicePromptContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI verification suite for the Wear OS Mood Check-in flow.
 *
 * This test class covers the multi-step mood capture experience on Wear, validating:
 * - The mood selection list and its labels and emoji.
 * - The optional voice prompt follow-up, ensuring contextual mood feedback is shown.
 * - Callbacks for mood selection, opening the recorder and skipping.
 * - Final confirmation screens after a successful check-in.
 */
@RunWith(AndroidJUnit4::class)
class MoodCheckInScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    // -----------------------------------------------------------------------
    // Mood selection
    // -----------------------------------------------------------------------

    @Test
    fun `select mood displays title`() {
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = {})
            }
        }

        composeRule.onNodeWithText("How are you feeling?").assertIsDisplayed()
    }

    @Test
    fun `select mood displays all options`() {
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = {})
            }
        }

        for (feeling in Feeling.entries) {
            scrollUntilShown(optionText(feeling))
            composeRule.onNodeWithText(optionText(feeling)).assertIsDisplayed()
        }
    }

    @Test
    fun `select mood tapping great triggers callback`() {
        var selected: Feeling? = null
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = { selected = it })
            }
        }

        composeRule.onNodeWithText(optionText(Feeling.GREAT)).performClick()

        assertEquals(Feeling.GREAT, selected)
    }

    @Test
    fun `select mood tapping sad triggers callback`() {
        var selected: Feeling? = null
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = { selected = it })
            }
        }

        scrollUntilShown(optionText(Feeling.SAD))
        composeRule.onNodeWithText(optionText(Feeling.SAD)).performClick()

        assertEquals(Feeling.SAD, selected)
    }

    @Test
    fun `select mood tapping rough triggers callback`() {
        var selected: Feeling? = null
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = { selected = it })
            }
        }

        scrollUntilShown(optionText(Feeling.ROUGH))
        composeRule.onNodeWithText(optionText(Feeling.ROUGH)).performClick()

        assertEquals(Feeling.ROUGH, selected)
    }

    // -----------------------------------------------------------------------
    // Voice prompt
    // -----------------------------------------------------------------------

    @Test
    fun `voice prompt displays mood emoji`() {
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = Feeling.GOOD,
                    onAttachVoice = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText(Feeling.GOOD.emoji).assertIsDisplayed()
        composeRule.onNodeWithText(label(Feeling.GOOD)).assertIsDisplayed()
    }

    @Test
    fun `voice prompt displays buttons`() {
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = Feeling.OK,
                    onAttachVoice = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("Open recorder").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").assertIsDisplayed()
    }

    @Test
    fun `voice prompt open recorder triggers callback`() {
        var voiceAttached = false
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = Feeling.GREAT,
                    onAttachVoice = { voiceAttached = true },
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("Open recorder").performClick()
        assertTrue("Open recorder should trigger callback", voiceAttached)
    }

    @Test
    fun `voice prompt skip triggers callback`() {
        var skipped = false
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = Feeling.GREAT,
                    onAttachVoice = {},
                    onSkip = { skipped = true },
                )
            }
        }

        composeRule.onNodeWithText("Skip").performClick()
        assertTrue("Skip should trigger callback", skipped)
    }

    @Test
    fun `voice prompt hides emoji when null`() {
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = null,
                    onAttachVoice = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("Open recorder").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Saved confirmation
    // -----------------------------------------------------------------------

    @Test
    fun `saved state displays saved text`() {
        composeRule.setContent {
            MaterialTheme {
                MoodSavedContent()
            }
        }

        composeRule.onNodeWithText("Saved").assertIsDisplayed()
    }

    private fun label(feeling: Feeling): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(feeling.labelRes)

    private fun optionText(feeling: Feeling): String = "${feeling.emoji} ${label(feeling)}"

    /** Swipes the list up until [text] is on screen; a small round watch shows only the first options. */
    private fun scrollUntilShown(text: String) {
        repeat(MAX_SWIPES) {
            if (composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            composeRule.onRoot().performTouchInput {
                swipeUp(startY = centerY + height / 4f, endY = centerY - height / 4f, durationMillis = 400)
            }
        }
    }

    private companion object {
        const val MAX_SWIPES = 5
    }
}
```

Confirm the app, its instrumented tests and its screenshot tests compile (the unit tests still name `MoodOption` and `latestMood` until Step 7 rewrites them, so they are left out here):

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:compileDebugKotlin :app:wear:compileDebugAndroidTestKotlin :app:wear:compileDebugScreenshotTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Write the failing unit tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/capture/MomentNoteCodecTest.kt`:

```kotlin
package app.logdate.wear.capture

import app.logdate.client.repository.journals.JournalNote
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class MomentNoteCodecTest {
    @Test
    fun `a check-in is saved as its tag followed by a sentence`() {
        assertEquals("#mood:great Feeling great", MomentNoteCodec.encode(Feeling.GREAT))
        assertEquals("#mood:ok Feeling ok", MomentNoteCodec.encode(Feeling.OK))
    }

    @Test
    fun `the feeling comes from the tag, not from the sentence after it`() {
        assertEquals(Feeling.GREAT, MomentNoteCodec.feelingOf("#mood:great Feeling great"))
        assertEquals(Feeling.GOOD, MomentNoteCodec.feelingOf("#mood:good Feeling great today"))
    }

    @Test
    fun `every feeling reads back as itself`() {
        Feeling.entries.forEach { feeling ->
            assertEquals(feeling, MomentNoteCodec.feelingOf(MomentNoteCodec.encode(feeling)))
        }
    }

    @Test
    fun `a tag later in the text is not a check-in`() {
        assertNull(MomentNoteCodec.feelingOf("Dinner was great #mood:great"))
        assertEquals(MomentNoteKind.TEXT, MomentNoteCodec.classify(text("Dinner was great #mood:great")))
    }

    @Test
    fun `a check-in with a tag no feeling uses is still a check-in`() {
        assertNull(MomentNoteCodec.feelingOf("#mood:elated Feeling elated"))
        assertEquals(MomentNoteKind.FEELING, MomentNoteCodec.classify(text("#mood:elated Feeling elated")))
    }

    @Test
    fun `notes are classified by what they hold`() {
        assertEquals(MomentNoteKind.FEELING, MomentNoteCodec.classify(text("#mood:sad Feeling sad")))
        assertEquals(MomentNoteKind.TEXT, MomentNoteCodec.classify(text("Lunch by the water")))
        assertEquals(
            MomentNoteKind.VOICE,
            MomentNoteCodec.classify(JournalNote.Audio(mediaRef = "/a.m4a", creationTimestamp = at(0), lastUpdated = at(0))),
        )
        assertEquals(
            MomentNoteKind.PHOTO,
            MomentNoteCodec.classify(JournalNote.Image(creationTimestamp = at(0), lastUpdated = at(0), mediaRef = "/p.jpg")),
        )
        assertEquals(
            MomentNoteKind.VIDEO,
            MomentNoteCodec.classify(JournalNote.Video(creationTimestamp = at(0), lastUpdated = at(0), mediaRef = "/v.mp4")),
        )
    }

    @Test
    fun `the latest feeling is the newest check-in, whatever the list order`() {
        val notes =
            listOf(
                text("#mood:rough Feeling rough", minute = 5),
                text("#mood:great Feeling great", minute = 30),
                text("Lunch by the water", minute = 45),
                text("#mood:sad Feeling sad", minute = 10),
            )

        assertEquals(Feeling.GREAT, MomentNoteCodec.latestFeeling(notes))
    }

    @Test
    fun `a newest check-in with an unknown tag is skipped`() {
        val notes =
            listOf(
                text("#mood:good Feeling good", minute = 5),
                text("#mood:elated Feeling elated", minute = 30),
            )

        assertEquals(Feeling.GOOD, MomentNoteCodec.latestFeeling(notes))
    }

    @Test
    fun `a day without check-ins has no feeling`() {
        assertNull(MomentNoteCodec.latestFeeling(listOf(text("Lunch by the water"))))
        assertNull(MomentNoteCodec.latestFeeling(emptyList()))
    }

    private fun text(
        content: String,
        minute: Int = 0,
    ): JournalNote.Text = JournalNote.Text(creationTimestamp = at(minute), lastUpdated = at(minute), content = content)

    private fun at(minute: Int): Instant = Instant.fromEpochMilliseconds(1_710_000_000_000 + minute * 60_000L)
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/complication/MoodComplicationTextTest.kt`:

```kotlin
package app.logdate.wear.complication

import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.MomentNoteCodec
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MoodComplicationTextTest {
    @Test
    fun `a check-in saved by the check-in screen shows its emoji`() {
        val checkIn = JournalNote.Text(creationTimestamp = NOON, lastUpdated = NOON, content = "#mood:great Feeling great")

        assertEquals("😄", feelingComplicationText(MomentNoteCodec.latestFeeling(listOf(checkIn))))
    }

    @Test
    fun `sad and rough show the faces the check-in screen offers`() {
        assertEquals("😔", feelingComplicationText(Feeling.SAD))
        assertEquals("😰", feelingComplicationText(Feeling.ROUGH))
    }

    @Test
    fun `a day without a check-in shows a dash`() {
        assertEquals("—", feelingComplicationText(null))
    }

    private companion object {
        val NOON: Instant = Instant.fromEpochMilliseconds(1_710_000_000_000)
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/tile/TodaySummaryTest.kt`:

```kotlin
package app.logdate.wear.tile

import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.capture.Feeling
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TodaySummaryTest {
    @Test
    fun `the mood comes from today's newest check-in`() {
        val summary =
            summarizeToday(
                listOf(
                    text("#mood:sad Feeling sad", minute = 0),
                    text("#mood:great Feeling great", minute = 20),
                ),
            )

        assertEquals(Feeling.GREAT, summary.feeling)
    }

    @Test
    fun `a check-in is never the latest entry`() {
        val lunch = text("Lunch by the water", minute = 0)

        val summary = summarizeToday(listOf(lunch, text("#mood:good Feeling good", minute = 20)))

        assertEquals(lunch, summary.latestEntry)
    }

    @Test
    fun `every note counts as one of today's entries`() {
        val summary = summarizeToday(listOf(text("Lunch by the water", minute = 0), text("#mood:good Feeling good", minute = 20)))

        assertEquals(2, summary.entryCount)
    }

    @Test
    fun `an empty day has no mood and no latest entry`() {
        val summary = summarizeToday(emptyList())

        assertEquals(0, summary.entryCount)
        assertNull(summary.feeling)
        assertNull(summary.latestEntry)
    }

    private fun text(
        content: String,
        minute: Int,
    ): JournalNote.Text {
        val at = Instant.fromEpochMilliseconds(1_710_000_000_000 + minute * 60_000L)
        return JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = content)
    }
}
```

In `app/wear/src/test/kotlin/app/logdate/wear/presentation/timeline/WearTimelineViewModelTest.kt`, add after line 4 (`import app.logdate.client.repository.journals.JournalNotesRepository`):

```kotlin
import app.logdate.wear.capture.Feeling
```

and replace lines 193-213 (the two mood tests) with:

```kotlin
    @Test
    fun `extracts mood from mood-tagged note`() =
        runTest {
            val moodNote = createNote(content = "#mood:good Feeling great")

            val vm = createViewModel(listOf(moodNote))
            val state = vm.uiState.first()

            assertEquals(Feeling.GOOD, state.days[0].feeling)
        }

    @Test
    fun `the day's mood comes from its newest check-in`() =
        runTest {
            val older = createNote(content = "#mood:sad Feeling sad", hoursOffset = 0)
            val newer = createNote(content = "#mood:great Feeling great", hoursOffset = 1)

            val vm = createViewModel(listOf(older, newer))
            val state = vm.uiState.first()

            assertEquals(Feeling.GREAT, state.days[0].feeling)
        }

    @Test
    fun `null mood when no mood notes exist`() =
        runTest {
            val plainNote = createNote(content = "Just a regular note")

            val vm = createViewModel(listOf(plainNote))
            val state = vm.uiState.first()

            assertNull(state.days[0].feeling)
        }

    @Test
    fun `a mood check-in is never the day's preview text`() =
        runTest {
            val checkIn = createNote(content = "#mood:good Feeling good", hoursOffset = 1)
            val note = createNote(content = "Lunch by the water", hoursOffset = 0)

            val vm = createViewModel(listOf(checkIn, note))
            val state = vm.uiState.first()

            assertEquals("Lunch by the water", state.days[0].previewText)
        }
```

Replace the whole of `app/wear/src/test/kotlin/app/logdate/wear/presentation/mood/MoodCheckInViewModelTest.kt`:

```kotlin
package app.logdate.wear.presentation.mood

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.capture.Feeling
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.uuid.Uuid

/**
 * Unit tests for the Wear OS Mood Check-in state machine and business logic.
 *
 * This class validates the [MoodCheckInViewModel], ensuring it correctly manages the
 * multi-step capture flow (mood selection -> optional voice prompt -> success).
 * It verifies that journal notes are persisted with appropriate mood-specific
 * metadata (tags) and that the view model emits the correct navigation and
 * UI events during the check-in lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodCheckInViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var notesRepository: JournalNotesRepository
    private lateinit var dataLayerClient: WearDataLayerClient
    private lateinit var locationCaptureCoordinator: WearLocationCaptureCoordinator
    private lateinit var viewModel: MoodCheckInViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        notesRepository = mockk(relaxed = true)
        dataLayerClient = mockk(relaxed = true)
        locationCaptureCoordinator = mockk(relaxed = true)
        coEvery { notesRepository.create(any()) } returns Uuid.random()
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
        coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns null
        viewModel = MoodCheckInViewModel(notesRepository, dataLayerClient, locationCaptureCoordinator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is SELECT_MOOD with no selection`() =
        runTest {
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(MoodCheckInStep.SELECT_MOOD, state.step)
                assertNull(state.selectedMood)
            }
        }

    @Test
    fun `selectMood transitions to VOICE_PROMPT`() =
        runTest {
            viewModel.uiState.test {
                skipItems(1) // initial state
                viewModel.selectMood(Feeling.GOOD)
                val state = awaitItem()
                assertEquals(MoodCheckInStep.VOICE_PROMPT, state.step)
                assertEquals(Feeling.GOOD, state.selectedMood)
            }
        }

    @Test
    fun `skipVoiceAttachment saves the check-in in the stored note format`() =
        runTest {
            viewModel.selectMood(Feeling.GREAT)
            viewModel.skipVoiceAttachment()

            coVerify {
                notesRepository.create(
                    match { note -> note is JournalNote.Text && note.content == "#mood:great Feeling great" },
                )
            }
        }

    @Test
    fun `skipVoiceAttachment attaches current watch location to mood note`() =
        runTest {
            val capturedLocation =
                NoteLocation(
                    coordinates =
                        NoteCoordinates(
                            latitude = 37.7749,
                            longitude = -122.4194,
                            altitude = 14.0,
                        ),
                )
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns capturedLocation

            viewModel.selectMood(Feeling.GREAT)
            viewModel.skipVoiceAttachment()

            coVerify {
                notesRepository.create(
                    match { note ->
                        note is JournalNote.Text && note.location == capturedLocation
                    },
                )
            }
        }

    @Test
    fun `skipVoiceAttachment transitions to SAVED`() =
        runTest {
            viewModel.selectMood(Feeling.OK)

            viewModel.uiState.test {
                skipItems(1) // current state
                viewModel.skipVoiceAttachment()
                val savedState = awaitItem()
                assertEquals(MoodCheckInStep.SAVED, savedState.step)
            }
        }

    @Test
    fun `skipVoiceAttachment emits NavigateBack event`() =
        runTest {
            viewModel.selectMood(Feeling.SAD)

            viewModel.events.test {
                viewModel.skipVoiceAttachment()
                assertEquals(MoodCheckInEvent.NavigateBack, awaitItem())
            }
        }

    @Test
    fun `attachVoice saves the mood, then opens the recorder`() =
        runTest {
            viewModel.selectMood(Feeling.ROUGH)

            viewModel.events.test {
                viewModel.attachVoice()
                assertEquals(MoodCheckInEvent.NavigateToVoiceNote, awaitItem())
            }

            coVerify(exactly = 1) {
                notesRepository.create(
                    match { note -> note is JournalNote.Text && note.content == "#mood:rough Feeling rough" },
                )
            }
        }

    @Test
    fun `attachVoice stays on the prompt when the mood cannot be saved`() =
        runTest {
            coEvery { notesRepository.create(any()) } throws IllegalStateException("The database is closed")
            viewModel.selectMood(Feeling.GOOD)

            viewModel.events.test {
                viewModel.attachVoice()
                expectNoEvents()
            }

            assertEquals(MoodCheckInStep.VOICE_PROMPT, viewModel.uiState.value.step)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `attachVoice does nothing without mood selection`() =
        runTest {
            viewModel.attachVoice()
            coVerify(exactly = 0) { notesRepository.create(any()) }
        }

    @Test
    fun `skipVoiceAttachment does nothing without mood selection`() =
        runTest {
            viewModel.skipVoiceAttachment()
            coVerify(exactly = 0) { notesRepository.create(any()) }
        }

    @Test
    fun `every feeling is saved under its own tag`() =
        runTest {
            for (feeling in Feeling.entries) {
                val repo: JournalNotesRepository = mockk(relaxed = true)
                val dlc: WearDataLayerClient = mockk(relaxed = true)
                coEvery { repo.create(any()) } returns Uuid.random()
                coEvery { dlc.isPhoneConnected(any()) } returns false
                val locationCoordinator: WearLocationCaptureCoordinator = mockk(relaxed = true)
                coEvery { locationCoordinator.captureForJournalEntry() } returns null
                val vm = MoodCheckInViewModel(repo, dlc, locationCoordinator)

                vm.selectMood(feeling)
                vm.skipVoiceAttachment()

                coVerify {
                    repo.create(
                        match { note ->
                            note is JournalNote.Text && note.content == "#mood:${feeling.tag} Feeling ${feeling.tag}"
                        },
                    )
                }
            }
        }
}
```

- [ ] **Step 8: Run the unit tests and watch them fail**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.capture.*' --tests 'app.logdate.wear.complication.MoodComplicationTextTest' --tests 'app.logdate.wear.tile.TodaySummaryTest' --tests 'app.logdate.wear.presentation.timeline.WearTimelineViewModelTest' --tests 'app.logdate.wear.presentation.mood.MoodCheckInViewModelTest'
```

Expected: FAIL on assertions, not compilation. Failing: in `MomentNoteCodecTest`, every test except `a tag later in the text is not a check-in` and `a day without check-ins has no feeling`; `MoodComplicationTextTest` `a check-in saved by the check-in screen shows its emoji`; `TodaySummaryTest` `the mood comes from today's newest check-in` and `a check-in is never the latest entry`; `WearTimelineViewModelTest` `extracts mood from mood-tagged note`, `the day's mood comes from its newest check-in` and `a mood check-in is never the day's preview text`; `MoodCheckInViewModelTest` `skipVoiceAttachment saves the check-in in the stored note format`, `every feeling is saved under its own tag`, `attachVoice saves the mood, then opens the recorder` and `attachVoice stays on the prompt when the mood cannot be saved`.

- [ ] **Step 9: Implement the codec**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/capture/MomentNoteCodec.kt`:

```kotlin
package app.logdate.wear.capture

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
 * does not change.
 */
object MomentNoteCodec {
    private val FEELING_TAG = Regex("^#mood:(\\w+)")

    /** The text a check-in of [feeling] is saved as. */
    fun encode(feeling: Feeling): String = "#mood:${feeling.tag} Feeling ${feeling.tag}"

    /** The feeling [content] records, or null when it is not a check-in or its tag is unknown. */
    fun feelingOf(content: String): Feeling? {
        val tag = FEELING_TAG.find(content)?.groupValues?.get(1) ?: return null
        return Feeling.fromTag(tag)
    }

    fun classify(note: JournalNote): MomentNoteKind =
        when (note) {
            is JournalNote.Text -> if (FEELING_TAG.containsMatchIn(note.content)) MomentNoteKind.FEELING else MomentNoteKind.TEXT
            is JournalNote.Audio -> MomentNoteKind.VOICE
            is JournalNote.Image -> MomentNoteKind.PHOTO
            is JournalNote.Video -> MomentNoteKind.VIDEO
        }

    /**
     * The feeling of the newest check-in among [notes] whose tag is known, or null. A newer check-in
     * with an unknown tag, written by a later build, is skipped.
     */
    fun latestFeeling(notes: List<JournalNote>): Feeling? =
        notes
            .filterIsInstance<JournalNote.Text>()
            .sortedByDescending(JournalNote::creationTimestamp)
            .firstNotNullOfOrNull { feelingOf(it.content) }
}
```

- [ ] **Step 10: Save the mood before opening the recorder**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/presentation/mood/MoodCheckInViewModel.kt`:

```kotlin
package app.logdate.wear.presentation.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SystemCaptureTimeZone
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.MomentNoteCodec
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.presentation.common.SaveFeedback
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

enum class MoodCheckInStep {
    SELECT_MOOD,
    VOICE_PROMPT,
    SAVED,
}

data class MoodCheckInUiState(
    val step: MoodCheckInStep = MoodCheckInStep.SELECT_MOOD,
    val selectedMood: Feeling? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveFeedback: SaveFeedback? = null,
)

sealed interface MoodCheckInEvent {
    data object NavigateBack : MoodCheckInEvent

    data object NavigateToVoiceNote : MoodCheckInEvent
}

class MoodCheckInViewModel(
    private val notesRepository: JournalNotesRepository,
    private val dataLayerClient: WearDataLayerClient,
    private val locationCaptureCoordinator: WearLocationCaptureCoordinator,
) : ViewModel() {
    companion object {
        private const val SAVED_DISPLAY_MS = 800L
    }

    private val _uiState = MutableStateFlow(MoodCheckInUiState())
    val uiState: StateFlow<MoodCheckInUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MoodCheckInEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MoodCheckInEvent> = _events.asSharedFlow()

    fun selectMood(mood: Feeling) {
        _uiState.update {
            it.copy(
                selectedMood = mood,
                step = MoodCheckInStep.VOICE_PROMPT,
            )
        }
    }

    fun skipVoiceAttachment() {
        val mood = _uiState.value.selectedMood ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val feedback = saveMood(mood)
            if (feedback == null) {
                _uiState.update { it.copy(isSaving = false) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    step = MoodCheckInStep.SAVED,
                    isSaving = false,
                    isSaved = true,
                    saveFeedback = feedback,
                )
            }
            delay(SAVED_DISPLAY_MS)
            _events.emit(MoodCheckInEvent.NavigateBack)
        }
    }

    /** Saves the mood, then opens the recorder so a voice note can follow it. */
    fun attachVoice() {
        val mood = _uiState.value.selectedMood ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val feedback = saveMood(mood)
            _uiState.update { it.copy(isSaving = false, isSaved = feedback != null, saveFeedback = feedback) }
            if (feedback == null) return@launch
            _events.emit(MoodCheckInEvent.NavigateToVoiceNote)
        }
    }

    /** Saves [mood] as a check-in note. Returns where it went, or null when it could not be saved. */
    private suspend fun saveMood(mood: Feeling): SaveFeedback? =
        try {
            val now = Clock.System.now()
            val noteLocation = locationCaptureCoordinator.captureForJournalEntry()
            notesRepository.create(
                JournalNote.Text(
                    content = MomentNoteCodec.encode(mood),
                    uid = Uuid.random(),
                    creationTimestamp = now,
                    lastUpdated = now,
                    location = noteLocation,
                    timeZoneId = SystemCaptureTimeZone.currentTimeZoneId(),
                ),
            )
            Napier.d("Mood check-in saved: ${mood.tag}")
            if (dataLayerClient.isPhoneConnected()) SaveFeedback.SYNCING_TO_PHONE else SaveFeedback.SAVED_LOCALLY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to save mood check-in", e)
            null
        }
}
```

- [ ] **Step 11: Run the unit tests again**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:testDebugUnitTest
```

Expected: PASS, the whole `:app:wear` unit suite.

- [ ] **Step 12: Confirm no old parser or emoji table is left**

```bash
grep -rn "MoodOption\|moodToEmoji\|removePrefix(\"#mood:\")\|startsWith(\"#mood:\")\|Regex(\"\^#mood" app/wear/src
```

Expected: one line, the `FEELING_TAG` regex in `capture/MomentNoteCodec.kt`.

- [ ] **Step 13: Check the screenshots and the static checks**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:validateDebugScreenshotTest :app:wear:assembleDebug :app:wear:lintDebug :app:wear:detekt :app:wear:ktlintCheck
```

Expected: `BUILD SUCCESSFUL`. The check-in screens render the same emoji and the same English labels, so `MoodCheckInScreenshots` S01-S05 match their baselines without re-recording. If a mood baseline differs, stop: a label or emoji changed by mistake.

- [ ] **Step 14: Run the check-in UI tests on the GMD**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.MoodCheckInScreenTest
```

Expected: PASS, 11 tests, including the three that failed in the Task 0 baseline.

- [ ] **Step 15: Add the real-hardware checks to the dogfood checklist**

In `docs/testing/wear-dogfood-checklist.md`, insert before `## Reminders`:

```markdown
## Mood

| Do this | You should see |
|---------|----------------|
| Check in "Sad" from the mood button, wait up to 15 minutes, then look at the Mood complication | 😔, the face the check-in screen showed |
| Check in "Great", wait up to 15 minutes, then open the Today's Summary tile | "😄 Great" |
| Check in a mood, then tap Open recorder | The recorder, and Timeline lists the mood for today |
| Open the check-in from the Mood complication, pick a mood, then tap Open recorder | The recorder, not the check-in again |

```

- [ ] **Step 16: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-2-feelings.txt <<'EOF'
fix(wear): show logged moods correctly and save them before recording

The Mood complication showed a dash after every mood check-in, and the
Today's Summary tile showed that dash beside the raw text of the note,
such as "great Feeling great". Both read the whole note instead of the
mood word at its start. Both now show the mood's emoji and its name.

The complication also drew Sad and Rough with different faces from the
ones the check-in screen offers. Every watch surface now shows the
faces the check-in screen shows.

Picking a mood and then tapping Open recorder went to the recorder
without saving the mood. The mood is now saved first. When it cannot
be saved, the check-in stays open so you can try again. Open recorder
also reaches the recorder when the check-in was opened from the Mood
complication.

The timeline's day cards showed the mood as a bare word such as
"good", and could show a mood check-in as the day's preview. They now
show the mood's emoji and name, and preview a written note instead.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 17: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/capture/Feeling.kt app/wear/src/main/kotlin/app/logdate/wear/capture/MomentNoteCodec.kt app/wear/src/main/kotlin/app/logdate/wear/complication/MoodComplicationService.kt app/wear/src/main/kotlin/app/logdate/wear/tile/TodaySummaryTileService.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/timeline/WearTimelineViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/timeline/WearTimelineScreen.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/mood/MoodCheckInViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/mood/MoodCheckInScreen.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt app/wear/src/main/res/values/strings.xml app/wear/src/test/kotlin/app/logdate/wear/capture/MomentNoteCodecTest.kt app/wear/src/test/kotlin/app/logdate/wear/complication/MoodComplicationTextTest.kt app/wear/src/test/kotlin/app/logdate/wear/tile/TodaySummaryTest.kt app/wear/src/test/kotlin/app/logdate/wear/presentation/timeline/WearTimelineViewModelTest.kt app/wear/src/test/kotlin/app/logdate/wear/presentation/mood/MoodCheckInViewModelTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MoodCheckInScreenTest.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MoodCheckInScreenshots.kt docs/testing/wear-dogfood-checklist.md && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-2-feelings.txt
```

Expected: the hooks pass; `git show --stat HEAD` lists 18 paths (5 added, 13 modified); `git status --short` is empty.

---

### Task 3: Delete the unreachable Health dashboard and Rewind screens (`chore(wear)`)

No button, tile or complication opens `WearHealthDashboardRoute`, `WearRewindListRoute` or `WearRewindPlaybackRoute`; only the `tile_route` names `health` and `rewind` in `MainActivity.kt:197-198` do, and no surface sends them. The health sensor code in `app/wear/src/main/kotlin/app/logdate/wear/health/` stays (`NoteHealthAnnotator` saves heart rate and steps with each recording, `WearRecordingViewModel.kt:293`), and so does `single<RewindRepository>` in `WearDataModule.kt:122-123`, which Track B's moment list reads for the Rewind card. No screenshot baselines exist for these screens; their only tests are the two unit tests deleted below.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutes.kt`
- Create: `app/wear/src/test/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutesTest.kt`
- Delete: `app/wear/src/main/kotlin/app/logdate/wear/presentation/health/HealthDashboardScreen.kt` (1-115), `HealthDashboardViewModel.kt` (1-73)
- Delete: `app/wear/src/main/kotlin/app/logdate/wear/presentation/rewind/WearRewindListScreen.kt` (1-98), `WearRewindPlaybackScreen.kt` (1-198), `WearRewindViewModel.kt` (1-98)
- Delete: `app/wear/src/test/kotlin/app/logdate/wear/presentation/health/HealthDashboardViewModelTest.kt` (1-213), `app/wear/src/test/kotlin/app/logdate/wear/presentation/rewind/WearRewindViewModelTest.kt` (1-370)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/navigation/WearNavRoutes.kt:19-24`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt` (imports 18-19, 25, 33-34, 42-44; line 72; the Rewind and Health entries at original lines 156-175, 160-179 after Task 2; `toWearRoute` at original lines 189-202, 193-206 after Task 2)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/AudioModule.kt:22,34,43,190-194,200-205`
- Modify: `app/wear/src/main/res/values/strings.xml` (the `<!-- Rewind -->` block, original lines 186-189)

**Interfaces:**
- Consumes: Task 2's `MainActivity.kt` mood entry (unchanged here).
- Produces: `internal fun wearRouteFor(tileRoute: String?): NavKey` in `app.logdate.wear.presentation.navigation` (Task 5 replaces it). Removes `WearRewindListRoute`, `WearRewindPlaybackRoute`, `WearHealthDashboardRoute`, `HealthDashboardViewModel`, `WearRewindViewModel` and their screens.

- [ ] **Step 1: Move the route mapping out of `MainActivity` unchanged**

Create `app/wear/src/main/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutes.kt` with today's mapping (Step 4 changes it):

```kotlin
package app.logdate.wear.presentation.navigation

import androidx.navigation3.runtime.NavKey

internal fun wearRouteFor(tileRoute: String?): NavKey =
    when (tileRoute) {
        "quick_record", "voice_note" -> WearHomeRoute
        "memories" -> WearVoiceMemoriesRoute
        "mood" -> WearMoodCheckInRoute
        "quick_text" -> WearQuickTextRoute
        "timeline" -> WearTimelineRoute
        "rewind" -> WearRewindListRoute
        "health" -> WearHealthDashboardRoute
        "remote_camera" -> WearRemoteCameraRoute
        "settings" -> WearSettingsRoute
        else -> WearHomeRoute
    }
```

In `MainActivity.kt`, add `import app.logdate.wear.presentation.navigation.wearRouteFor` after `import app.logdate.wear.presentation.navigation.WearVoiceMemoriesRoute`, replace line 72:

```kotlin
                initialRouteName.toWearRoute() ?: WearHomeRoute
```

with:

```kotlin
                wearRouteFor(initialRouteName)
```

and delete the private `toWearRoute` function at the end of the file together with the blank line before it:

```kotlin

private fun String?.toWearRoute(): NavKey? =
    when (this) {
        "quick_record", "voice_note" -> WearHomeRoute
        "memories" -> WearVoiceMemoriesRoute
        "mood" -> WearMoodCheckInRoute
        "quick_text" -> WearQuickTextRoute
        "timeline" -> WearTimelineRoute
        "rewind" -> WearRewindListRoute
        "health" -> WearHealthDashboardRoute
        "remote_camera" -> WearRemoteCameraRoute
        "settings" -> WearSettingsRoute
        else -> null
    }
```

- [ ] **Step 2: Write the failing test**

Create `app/wear/src/test/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutesTest.kt`:

```kotlin
package app.logdate.wear.presentation.navigation

import org.junit.Test
import kotlin.test.assertEquals

class WearLaunchRoutesTest {
    @Test
    fun `routes to the removed Rewind and Health screens open Home`() {
        assertEquals(WearHomeRoute, wearRouteFor("rewind"))
        assertEquals(WearHomeRoute, wearRouteFor("health"))
    }

    @Test
    fun `recording routes open Home, which is the recorder`() {
        assertEquals(WearHomeRoute, wearRouteFor("quick_record"))
        assertEquals(WearHomeRoute, wearRouteFor("voice_note"))
    }

    @Test
    fun `the other tile routes open their screens`() {
        assertEquals(WearVoiceMemoriesRoute, wearRouteFor("memories"))
        assertEquals(WearMoodCheckInRoute, wearRouteFor("mood"))
        assertEquals(WearQuickTextRoute, wearRouteFor("quick_text"))
        assertEquals(WearTimelineRoute, wearRouteFor("timeline"))
        assertEquals(WearRemoteCameraRoute, wearRouteFor("remote_camera"))
        assertEquals(WearSettingsRoute, wearRouteFor("settings"))
    }

    @Test
    fun `a missing or unknown route opens Home`() {
        assertEquals(WearHomeRoute, wearRouteFor(null))
        assertEquals(WearHomeRoute, wearRouteFor("unknown"))
    }
}
```

- [ ] **Step 3: Run it and watch it fail**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.presentation.navigation.WearLaunchRoutesTest'
```

Expected: FAIL. `routes to the removed Rewind and Health screens open Home` fails with `expected:<WearHomeRoute> but was:<WearRewindListRoute>`. The other three pass.

- [ ] **Step 4: Delete the screens, their tests and their routes**

```bash
rm app/wear/src/main/kotlin/app/logdate/wear/presentation/health/HealthDashboardScreen.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/health/HealthDashboardViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/rewind/WearRewindListScreen.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/rewind/WearRewindPlaybackScreen.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/rewind/WearRewindViewModel.kt app/wear/src/test/kotlin/app/logdate/wear/presentation/health/HealthDashboardViewModelTest.kt app/wear/src/test/kotlin/app/logdate/wear/presentation/rewind/WearRewindViewModelTest.kt && rmdir app/wear/src/main/kotlin/app/logdate/wear/presentation/health app/wear/src/main/kotlin/app/logdate/wear/presentation/rewind app/wear/src/test/kotlin/app/logdate/wear/presentation/health app/wear/src/test/kotlin/app/logdate/wear/presentation/rewind
```

In `WearLaunchRoutes.kt`, replace the whole file with:

```kotlin
package app.logdate.wear.presentation.navigation

import androidx.navigation3.runtime.NavKey

/**
 * The screen a launch opens, from the `tile_route` extra that tiles and complications send. Home is
 * the recorder, so recording routes, unknown routes and routes to removed screens open it.
 */
internal fun wearRouteFor(tileRoute: String?): NavKey =
    when (tileRoute) {
        "memories" -> WearVoiceMemoriesRoute
        "mood" -> WearMoodCheckInRoute
        "quick_text" -> WearQuickTextRoute
        "timeline" -> WearTimelineRoute
        "remote_camera" -> WearRemoteCameraRoute
        "settings" -> WearSettingsRoute
        else -> WearHomeRoute
    }
```

In `WearNavRoutes.kt`, delete lines 19-24:

```kotlin
data object WearRewindListRoute : NavKey

data object WearRewindPlaybackRoute : NavKey

data object WearHealthDashboardRoute : NavKey

```

In `MainActivity.kt`, delete these imports (original lines 18-19, 25, 33-34, 42-44):

```kotlin
import app.logdate.wear.presentation.health.HealthDashboardScreen
import app.logdate.wear.presentation.health.HealthDashboardViewModel
import app.logdate.wear.presentation.navigation.WearHealthDashboardRoute
import app.logdate.wear.presentation.navigation.WearRewindListRoute
import app.logdate.wear.presentation.navigation.WearRewindPlaybackRoute
import app.logdate.wear.presentation.rewind.WearRewindListScreen
import app.logdate.wear.presentation.rewind.WearRewindPlaybackScreen
import app.logdate.wear.presentation.rewind.WearRewindViewModel
```

and delete the three entries between `entry<WearTimelineDayDetailRoute>` and `entry<WearRemoteCameraRoute>`:

```kotlin
                    entry<WearRewindListRoute> {
                        val rewindViewModel = koinViewModel<WearRewindViewModel>()
                        WearRewindListScreen(
                            viewModel = rewindViewModel,
                            onSelectRewind = { uid ->
                                rewindViewModel.selectRewind(uid)
                                backStack.add(WearRewindPlaybackRoute)
                            },
                        )
                    }
                    entry<WearRewindPlaybackRoute> {
                        val rewindViewModel = koinViewModel<WearRewindViewModel>()
                        WearRewindPlaybackScreen(
                            viewModel = rewindViewModel,
                            onExit = navigateBack,
                        )
                    }
                    entry<WearHealthDashboardRoute> {
                        HealthDashboardScreen(viewModel = koinViewModel<HealthDashboardViewModel>())
                    }
```

In `app/wear/src/main/kotlin/app/logdate/wear/di/AudioModule.kt`, delete the imports at lines 22, 34 and 43:

```kotlin
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.wear.presentation.health.HealthDashboardViewModel
import app.logdate.wear.presentation.rewind.WearRewindViewModel
```

and the two registrations at lines 190-194 and 200-205:

```kotlin
        viewModel {
            WearRewindViewModel(
                get<RewindRepository>(),
            )
        }
```

```kotlin
        viewModel {
            HealthDashboardViewModel(
                get(),
                get(),
            )
        }
```

In `app/wear/src/main/res/values/strings.xml`, delete the Rewind block and the blank line after it:

```xml
    <!-- Rewind -->
    <string name="wear_rewind_title">Rewinds</string>
    <string name="wear_rewind_empty">No rewinds yet</string>

```

- [ ] **Step 5: Run the tests and checks**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:testDebugUnitTest :app:wear:assembleDebug :app:wear:lintDebug :app:wear:detekt :app:wear:ktlintCheck :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`; `WearLaunchRoutesTest` passes all 4 tests.

```bash
grep -rn "WearRewind\|HealthDashboard\|wear_rewind_" app/wear/src
```

Expected: no output.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-3-health-rewind.txt <<'EOF'
chore(wear): delete the unreachable Health dashboard and Rewind screens

Nothing on the watch opened the Health dashboard or the Rewind list
and player. No button, tile or complication led to them; only route
names that no tile or complication sends did. The screens, their view
models, their tests and their navigation routes are deleted, and a
launch that names either screen opens Home.

Two things stay: the health sensor code that saves heart rate and
steps with each new recording, and the watch's local copy of Rewinds,
which the redesigned watch app shows as a card in its moment list.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/presentation/health/HealthDashboardScreen.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/health/HealthDashboardViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/rewind/WearRewindListScreen.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/rewind/WearRewindPlaybackScreen.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/rewind/WearRewindViewModel.kt app/wear/src/test/kotlin/app/logdate/wear/presentation/health/HealthDashboardViewModelTest.kt app/wear/src/test/kotlin/app/logdate/wear/presentation/rewind/WearRewindViewModelTest.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/navigation/WearNavRoutes.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutes.kt app/wear/src/test/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutesTest.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt app/wear/src/main/kotlin/app/logdate/wear/di/AudioModule.kt app/wear/src/main/res/values/strings.xml && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-3-health-rewind.txt
```

Expected: the hooks pass; `git show --stat HEAD` lists 13 paths (2 added, 7 deleted, 4 modified); `git status --short` is empty.

---

### Task 4: Move to the current AndroidX Wear libraries (`chore(wear)`)

Versions checked against Google Maven's `group-index.xml` files on 2026-10-02 (Wear Compose 1.7.0 shipped on 2026-09-23): `androidx.wear.compose` compose-material3, compose-foundation, compose-navigation3 and compose-ui-tooling 1.7.0; `androidx.wear.tiles:tiles` 1.6.2; `androidx.wear.protolayout` protolayout, protolayout-expression, protolayout-material and protolayout-material3 1.4.2; `androidx.wear` wear 1.4.0, wear-ongoing 1.1.0, wear-input 1.2.0, wear-remote-interactions 1.2.0 (1.3.0-beta01 is the only newer build), wear-phone-interactions 1.1.1; `androidx.lifecycle:lifecycle-viewmodel-navigation3` 2.11.0; `androidx.health:health-services-client` 1.1.0; `androidx.graphics:graphics-shapes` 1.1.0.

What stays, and why:
- `horologist-tiles` 0.8.3-alpha: `QuickCaptureTileService.kt:25,45` and `TodaySummaryTileService.kt:27,41` extend its `SuspendingTileService`. It goes when the tiles move to `Material3TileService`.
- `protolayout-material` (declared here at 1.4.2): both tiles import `androidx.wear.protolayout.material.{Colors, Text, Typography, layouts.PrimaryLayout}` (`QuickCaptureTileService.kt:13-16`, `TodaySummaryTileService.kt:13-16`). Today it reaches the classpath only through `horologist-tiles` (compile scope, 1.3.0). It goes with the tile rewrite.
- `material-icons-extended`: every v1 screen draws `Icons.Default.*`; it goes when the v1 screens are deleted.
- `wear-tooling-preview` 1.0.0 (current) and `watchface-complications-data-source-ktx` 1.3.0 (current).

What goes:
- `tiles-material`: no file imports `androidx.wear.tiles.material`, and the artifact does not contain the `androidx.wear.protolayout.material` classes (its POM depends only on `tiles` and `protolayout-expression`).
- `androidx.wear.compose:compose-material` (direct dependency only; `horologist-tiles` still pulls it in, and Wear Compose's constraints align it to 1.7.0), `compose-navigation` (unused), `horologist-compose-tools` (no imports; replaced by `compose-ui-tooling`), and the watch's `com.google.android.material:material` (no imports or theme references; `app/compose-main` keeps its own).

Shared with the phone: `lifecycle-viewmodel-navigation3` is one catalog entry (`gradle/libs.versions.toml:193`, version key `lifecycleViewmodelNav3` at line 60) that `app/compose-main/build.gradle.kts:129` also uses, so the phone moves from 2.11.0-alpha03 to 2.11.0 too.

**Files:**
- Modify: `gradle/libs.versions.toml:60,114,132-152,412-424`
- Modify: `app/wear/build.gradle.kts:6-13,151-183`
- Possibly modify: PNGs under `app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/` (Step 10)
- Scratchpad only: `check-wear-libraries.sh`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces catalog accessors: `libs.androidx.wear.compose.navigation3`, `libs.androidx.wear.compose.ui.tooling`, `libs.androidx.wear`, `libs.androidx.wear.ongoing`, `libs.androidx.wear.input`, `libs.androidx.wear.phone.interactions`, `libs.androidx.protolayout`, `libs.androidx.protolayout.expression`, `libs.androidx.protolayout.material`, `libs.androidx.protolayout.material3`, `libs.androidx.graphics.shapes`, and version keys `wearCompose`, `tiles`, `protolayout`, `androidxWear`, `wearOngoing`, `wearInput`, `wearPhoneInteractions`, `graphicsShapes`. Removes `libs.androidx.compose.material`, `libs.androidx.wear.navigation`, `libs.androidx.tiles.material`, `libs.horologist.compose.tools` and version keys `composeMaterial`, `wearComposeMaterial3`, `wearComposeFoundation`, `wearComposeNavigation`, `tilesMaterial`, `tilesTooling`, `tilesToolingPreview`, `horologistComposeTools`.

- [ ] **Step 1: Write the failing dependency check**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/check-wear-libraries.sh <<'EOF'
#!/usr/bin/env bash
# Compares the watch's resolved runtime libraries with the versions the groundwork plan targets.
set -euo pipefail
cd /Users/williecubed/Projects/TheHypertextStudio/logdate-mobile/.claude/worktrees/backup-sync-infinite-notification-dd4c3a
export ANDROID_HOME="$HOME/Library/Android/sdk"
deps="$(./gradlew -q :app:wear:dependencies --configuration debugRuntimeClasspath)"
status=0

resolved() {
    echo "$deps" | { grep -F -- "$1:" || true; } | sed -E 's/ \((\*|c|n)\)$//' | awk -v id="$1:" '{
        arrow = index($0, " -> ")
        if (arrow > 0) { print substr($0, arrow + 4); next }
        rest = substr($0, index($0, id) + length(id))
        split(rest, words, " ")
        print words[1]
    }' | sort -u
}

expect() {
    local got
    got="$(resolved "$1")"
    if [[ "$got" != "$2" ]]; then
        echo "WRONG   $1: want $2, got ${got:-nothing}"
        status=1
    fi
}

absent() {
    if echo "$deps" | grep -qF -- "$1:"; then
        echo "PRESENT $1: should be gone"
        status=1
    fi
}

expect androidx.wear.compose:compose-material3 1.7.0
expect androidx.wear.compose:compose-foundation 1.7.0
expect androidx.wear.compose:compose-navigation3 1.7.0
expect androidx.wear.compose:compose-ui-tooling 1.7.0
expect androidx.wear.tiles:tiles 1.6.2
expect androidx.wear.protolayout:protolayout 1.4.2
expect androidx.wear.protolayout:protolayout-expression 1.4.2
expect androidx.wear.protolayout:protolayout-material 1.4.2
expect androidx.wear.protolayout:protolayout-material3 1.4.2
expect androidx.wear:wear 1.4.0
expect androidx.wear:wear-ongoing 1.1.0
expect androidx.wear:wear-input 1.2.0
expect androidx.wear:wear-remote-interactions 1.2.0
expect androidx.wear:wear-phone-interactions 1.1.1
expect androidx.lifecycle:lifecycle-viewmodel-navigation3 2.11.0
expect androidx.health:health-services-client 1.1.0
expect androidx.graphics:graphics-shapes 1.1.0
absent com.google.android.horologist:horologist-compose-tools
absent androidx.wear.tiles:tiles-material
absent com.google.android.material:material
exit "$status"
EOF
```

- [ ] **Step 2: Run the check and watch it fail**

```bash
bash /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/check-wear-libraries.sh; echo "exit $?"
```

Expected: `exit 1`, with `WRONG` lines such as `androidx.wear.compose:compose-material3: want 1.7.0, got 1.6.1`, `androidx.wear.compose:compose-navigation3: want 1.7.0, got nothing` and `androidx.wear:wear: want 1.4.0, got 1.4.0-alpha02`, and `PRESENT` lines for `horologist-compose-tools`, `tiles-material` and `material`.

- [ ] **Step 3: Update the version catalog**

In `gradle/libs.versions.toml`, replace line 60:

```toml
lifecycleViewmodelNav3 = "2.11.0-alpha03"
```

with:

```toml
lifecycleViewmodelNav3 = "2.11.0"
```

Replace line 114:

```toml
healthServicesClient = "1.1.0-rc01"
```

with:

```toml
healthServicesClient = "1.1.0"
```

Replace lines 132-152 (`# Others` through `zxing = "3.5.4"`):

```toml
# Others
versioningPlugin = "2.2.0"
playServicesWearable = "19.0.0"
composeBom = "2026.05.00"
composeMaterial = "1.6.1"
wearToolingPreview = "1.0.0"
wearComposeMaterial3 = "1.6.1"
wearComposeFoundation = "1.6.1"
wearComposeNavigation = "1.6.1"
tiles = "1.6.0"
tilesMaterial = "1.6.0"
tilesTooling = "1.6.0"
tilesToolingPreview = "1.6.0"
horologistComposeTools = "0.8.3-alpha"
horologistTiles = "0.8.3-alpha"
wearRemoteInteractions = "1.2.0"
watchfaceComplicationsDataSourceKtx = "1.3.0"
uiAndroid = "1.11.1"
okio = "3.17.0"
kotlinxIo = "0.9.0"
zxing = "3.5.4"
```

with:

```toml
# Others
versioningPlugin = "2.2.0"
playServicesWearable = "19.0.0"
composeBom = "2026.05.00"
wearToolingPreview = "1.0.0"
wearCompose = "1.7.0"
tiles = "1.6.2"
protolayout = "1.4.2"
horologistTiles = "0.8.3-alpha"
wearRemoteInteractions = "1.2.0"
androidxWear = "1.4.0"
wearOngoing = "1.1.0"
wearInput = "1.2.0"
wearPhoneInteractions = "1.1.1"
graphicsShapes = "1.1.0"
watchfaceComplicationsDataSourceKtx = "1.3.0"
uiAndroid = "1.11.1"
okio = "3.17.0"
kotlinxIo = "0.9.0"
zxing = "3.5.4"
```

Replace lines 412-424 (`androidx-compose-material` through `wear-remote-interactions`):

```toml
androidx-compose-material = { group = "androidx.wear.compose", name = "compose-material", version.ref = "composeMaterial" }
androidx-compose-material-iconsExtended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-wear-material3 = { group = "androidx.wear.compose", name = "compose-material3", version.ref = "wearComposeMaterial3" }
androidx-wear-foundation = { group = "androidx.wear.compose", name = "compose-foundation", version.ref = "wearComposeFoundation" }
androidx-wear-navigation = { group = "androidx.wear.compose", name = "compose-navigation", version.ref = "wearComposeNavigation" }
androidx-wear-tooling-preview = { group = "androidx.wear", name = "wear-tooling-preview", version.ref = "wearToolingPreview" }
androidx-tiles = { group = "androidx.wear.tiles", name = "tiles", version.ref = "tiles" }
androidx-tiles-material = { group = "androidx.wear.tiles", name = "tiles-material", version.ref = "tilesMaterial" }
androidx-tiles-tooling = { group = "androidx.wear.tiles", name = "tiles-tooling", version.ref = "tilesTooling" }
androidx-tiles-tooling-preview = { group = "androidx.wear.tiles", name = "tiles-tooling-preview", version.ref = "tilesToolingPreview" }
horologist-compose-tools = { group = "com.google.android.horologist", name = "horologist-compose-tools", version.ref = "horologistComposeTools" }
horologist-tiles = { group = "com.google.android.horologist", name = "horologist-tiles", version.ref = "horologistTiles" }
wear-remote-interactions = { group = "androidx.wear", name = "wear-remote-interactions", version.ref = "wearRemoteInteractions" }
```

with:

```toml
androidx-compose-material-iconsExtended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-wear-material3 = { group = "androidx.wear.compose", name = "compose-material3", version.ref = "wearCompose" }
androidx-wear-foundation = { group = "androidx.wear.compose", name = "compose-foundation", version.ref = "wearCompose" }
androidx-wear-compose-navigation3 = { group = "androidx.wear.compose", name = "compose-navigation3", version.ref = "wearCompose" }
androidx-wear-compose-ui-tooling = { group = "androidx.wear.compose", name = "compose-ui-tooling", version.ref = "wearCompose" }
androidx-wear-tooling-preview = { group = "androidx.wear", name = "wear-tooling-preview", version.ref = "wearToolingPreview" }
androidx-wear = { group = "androidx.wear", name = "wear", version.ref = "androidxWear" }
androidx-wear-ongoing = { group = "androidx.wear", name = "wear-ongoing", version.ref = "wearOngoing" }
androidx-wear-input = { group = "androidx.wear", name = "wear-input", version.ref = "wearInput" }
androidx-wear-phone-interactions = { group = "androidx.wear", name = "wear-phone-interactions", version.ref = "wearPhoneInteractions" }
androidx-tiles = { group = "androidx.wear.tiles", name = "tiles", version.ref = "tiles" }
androidx-tiles-tooling = { group = "androidx.wear.tiles", name = "tiles-tooling", version.ref = "tiles" }
androidx-tiles-tooling-preview = { group = "androidx.wear.tiles", name = "tiles-tooling-preview", version.ref = "tiles" }
androidx-protolayout = { group = "androidx.wear.protolayout", name = "protolayout", version.ref = "protolayout" }
androidx-protolayout-expression = { group = "androidx.wear.protolayout", name = "protolayout-expression", version.ref = "protolayout" }
androidx-protolayout-material = { group = "androidx.wear.protolayout", name = "protolayout-material", version.ref = "protolayout" }
androidx-protolayout-material3 = { group = "androidx.wear.protolayout", name = "protolayout-material3", version.ref = "protolayout" }
androidx-graphics-shapes = { group = "androidx.graphics", name = "graphics-shapes", version.ref = "graphicsShapes" }
horologist-tiles = { group = "com.google.android.horologist", name = "horologist-tiles", version.ref = "horologistTiles" }
wear-remote-interactions = { group = "androidx.wear", name = "wear-remote-interactions", version.ref = "wearRemoteInteractions" }
```

(`androidx-wear` is both a library and a prefix of other aliases, like `androidx-tiles` and `androidx-tiles-material` were; Gradle generates `libs.androidx.wear` as a dependency that also has child accessors.)

- [ ] **Step 4: Update the watch's build file**

In `app/wear/build.gradle.kts`, replace lines 6-13:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.screenshot)
    alias(libs.plugins.gradlePlayPublisher)
    id("app.logdate.android-release")
}
```

with:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.screenshot)
    alias(libs.plugins.gradlePlayPublisher)
    id("app.logdate.android-release")
}
```

Replace lines 151-183 (from `// Add navigation for Wear` through `implementation(libs.androidx.health.services.client)` and the blank line after it) with:

```kotlin
    // Navigation 3
    implementation(libs.nav3.runtime)
    implementation(libs.nav3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.compose.material.iconsExtended)

    // Additional Compose support
    implementation(libs.kotlinx.datetime)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.graphics.shapes)

    // Wear Compose
    implementation(libs.androidx.wear.material3)
    implementation(libs.androidx.wear.foundation)
    implementation(libs.androidx.wear.compose.navigation3)
    implementation(libs.androidx.wear.compose.ui.tooling)

    // Wear OS platform libraries
    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.androidx.wear.input)
    implementation(libs.androidx.wear.phone.interactions)
    implementation(libs.wear.remote.interactions)

    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    // Both tiles extend horologist-tiles' SuspendingTileService and draw with protolayout-material;
    // both go when the tiles move to Material3TileService.
    implementation(libs.androidx.tiles)
    implementation(libs.androidx.tiles.tooling.preview)
    implementation(libs.androidx.protolayout)
    implementation(libs.androidx.protolayout.expression)
    implementation(libs.androidx.protolayout.material)
    implementation(libs.androidx.protolayout.material3)
    implementation(libs.horologist.tiles)
    implementation(libs.androidx.watchface.complications.data.source.ktx)
    implementation(libs.androidx.health.services.client)

```

`debugImplementation(libs.androidx.tiles.tooling)` (original line 207) stays; its version now comes from `tiles`.

- [ ] **Step 5: Run the dependency check again**

```bash
bash /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/check-wear-libraries.sh; echo "exit $?"
```

Expected: no `WRONG` or `PRESENT` lines, `exit 0`. Then confirm where the legacy Wear Compose Material library still comes from:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew -q :app:wear:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.wear.compose:compose-material
```

Expected: resolved to 1.7.0, requested only by `com.google.android.horologist:horologist-tiles:0.8.3-alpha` (plus 1.7.0 constraints from the other Wear Compose artifacts).

- [ ] **Step 6: Build and test the watch**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:assembleDebug :app:wear:testDebugUnitTest :app:wear:lintDebug :app:wear:detekt :app:wear:ktlintCheck
```

Expected: `BUILD SUCCESSFUL`. Deprecation warnings from Wear Compose 1.7.0 are acceptable (the module does not treat warnings as errors); a compile error is not. If one appears, check the 1.7.0 release notes at https://developer.android.com/jetpack/androidx/releases/wear-compose for the renamed API and update the call site in this same task.

- [ ] **Step 7: Build and test the phone, which shares `lifecycle-viewmodel-navigation3`**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:android-main:assembleDebug :app:compose-main:testAndroidHostTest
```

Expected: `BUILD SUCCESSFUL`. Without `app/android-main/google-services.json` in this worktree Gradle writes a stub, which is enough for a debug build.

- [ ] **Step 8: Validate the screenshots**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: PASS, or FAIL listing baselines that Wear Compose 1.7.0 draws differently. If it passes, skip Steps 9 and 10.

- [ ] **Step 9: Review every screenshot difference before re-recording**

Open `app/wear/build/reports/screenshotTest/preview/debug/index.html` and read each image in `app/wear/build/outputs/screenshotTest-results/preview/debug/diffs/` (the Read tool shows PNGs). Accept a difference only when it is anti-aliasing, glyph rasterization, a ripple or indication shade, or text moving by a pixel or two. Stop and report, without re-recording, if any screen gains or loses an element, clips or truncates text differently, moves a component by more than 4 dp, changes the colour role of an element (for example error to primary), or loses its `TimeText`; give the screen name and the diff path.

- [ ] **Step 10: Re-record the reviewed baselines**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:updateDebugScreenshotTest && ./gradlew :app:wear:validateDebugScreenshotTest && git status --short app/wear/src/screenshotTestDebug/reference
```

Expected: `BUILD SUCCESSFUL` twice, and `git status` lists only modified (`M`) PNGs that Step 9 reviewed; no added or deleted baselines.

- [ ] **Step 11: Run the whole watch GMD suite and compare with the baseline**

```bash
uptime && export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest --continue
```

Expected: every test that passed in `scratchpad/gmd-baseline/index.html` passes in `app/wear/build/reports/androidTests/managedDevice/debug/allDevices/index.html`, plus the `MoodCheckInScreenTest` and `LegacyPromptAlarmsTest` tests from Tasks 1 and 2. A test that passed before and fails now blocks the commit.

- [ ] **Step 12: Write the commit message**

If Step 8 passed (no baselines changed):

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-4-libraries.txt <<'EOF'
chore(wear): update to Wear Compose 1.7 and the current Wear libraries

The watch moves to the newest stable AndroidX Wear releases that the
redesigned watch app builds on: Wear Compose Material 3, Foundation,
Navigation 3 and UI tooling 1.7.0, Tiles 1.6.2, ProtoLayout 1.4.2 and
Health Services 1.1.0. It also declares wear 1.4.0, wear-ongoing
1.1.0, wear-input 1.2.0, wear-remote-interactions 1.2.0,
wear-phone-interactions 1.1.1, graphics-shapes 1.1.0 and
lifecycle-viewmodel-navigation3 2.11.0, and applies the
kotlinx.serialization plugin.

Removed from the watch: the legacy Wear Compose Material library, the
unused Wear Compose Navigation library, Horologist's compose tools,
which Wear Compose's own UI tooling replaces, tiles-material, which no
code imports, and the Material Components library.

Two older pieces remain until both tiles move to Material3TileService:
horologist-tiles, whose SuspendingTileService both tiles extend, and
protolayout-material, whose layouts both tiles draw with. Horologist
tiles still brings in the legacy Wear Compose Material library, which
now resolves to 1.7.0.

The phone reads the same catalog entry for
lifecycle-viewmodel-navigation3, so it moves from 2.11.0-alpha03 to
2.11.0 as well.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

If Step 10 re-recorded baselines, write this instead. The delimiter is unquoted so the last line of the paragraph expands to one `- <file name>` line per re-recorded baseline:

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-4-libraries.txt <<EOF
chore(wear): update to Wear Compose 1.7 and the current Wear libraries

The watch moves to the newest stable AndroidX Wear releases that the
redesigned watch app builds on: Wear Compose Material 3, Foundation,
Navigation 3 and UI tooling 1.7.0, Tiles 1.6.2, ProtoLayout 1.4.2 and
Health Services 1.1.0. It also declares wear 1.4.0, wear-ongoing
1.1.0, wear-input 1.2.0, wear-remote-interactions 1.2.0,
wear-phone-interactions 1.1.1, graphics-shapes 1.1.0 and
lifecycle-viewmodel-navigation3 2.11.0, and applies the
kotlinx.serialization plugin.

Removed from the watch: the legacy Wear Compose Material library, the
unused Wear Compose Navigation library, Horologist's compose tools,
which Wear Compose's own UI tooling replaces, tiles-material, which no
code imports, and the Material Components library.

Two older pieces remain until both tiles move to Material3TileService:
horologist-tiles, whose SuspendingTileService both tiles extend, and
protolayout-material, whose layouts both tiles draw with. Horologist
tiles still brings in the legacy Wear Compose Material library, which
now resolves to 1.7.0.

The phone reads the same catalog entry for
lifecycle-viewmodel-navigation3, so it moves from 2.11.0-alpha03 to
2.11.0 as well.

Wear Compose 1.7.0 draws some of today's screens differently, so these
screenshot baselines are recorded again. Each difference was checked
in the screenshot report and is a rendering change, not a layout
change:
$(git diff --name-only -- app/wear/src/screenshotTestDebug/reference | sed 's|.*/|- |')

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 13: Commit**

Without re-recorded baselines:

```bash
git restore --staged . && git add app/wear/build.gradle.kts gradle/libs.versions.toml && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-4-libraries.txt
```

With re-recorded baselines (the directory holds only the PNGs Step 9 reviewed):

```bash
git restore --staged . && git add app/wear/build.gradle.kts gradle/libs.versions.toml app/wear/src/screenshotTestDebug/reference && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-4-libraries.txt
```

Expected: the hooks pass; `git show --stat HEAD` lists the two build files (and the reviewed PNGs); `git status --short` is empty.

---

### Task 5: The experience gate and the launch-request parser (`chore(wear)`)

`MainActivity.kt` (after Task 3) reads `tile_route` only in `onCreate` and has the default launch mode, so a tile tapped while the app is open starts a second `MainActivity`. This task makes the activity single-top, reads every launch through one parser, and adds the gate. The gate reads `WATCH_EXPERIENCE_V2` once, behind the splash screen, when the activity is created; a recreated activity (configuration change or process restore) reuses the saved choice, so the app never swaps while the activity is alive. A read that fails or takes longer than 2 seconds opens v1, so the splash always ends. Macrobenchmark copes with a single-top activity: `MacrobenchmarkScope.startActivityAndWait` treats "Warning: Activity not started" as delivered and waits 2 seconds.

Mapping decisions: `quick_record` and `voice_note` (sent by `QuickCaptureTileService.kt:34` and `QuickCaptureComplicationService.kt:40`) parse as `Record`, which v1 opens as Home, as today; `rewind` and `health` parse as `Home`; `record`, `mark`, `moment` (with a `note_id` extra) and `on_this_day` are new names for Track B, and v1 opens all of them as Home. The extra keeps its first name, `tile_route`, because tiles and complications placed by older builds keep sending it.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/launch/WearExperienceGate.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/launch/WearLaunchRequest.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2Placeholder.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/di/ExperienceModule.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/presentation/WearApp.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivityContent.kt`
- Create: `app/wear/src/test/kotlin/app/logdate/wear/launch/WearExperienceGateTest.kt`
- Create: `app/wear/src/test/kotlin/app/logdate/wear/launch/WearLaunchRequestTest.kt`
- Create: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ExperienceGateTest.kt`
- Create: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/LaunchRequestRoutingTest.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutes.kt` (whole file)
- Modify: `app/wear/src/test/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutesTest.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt:7-8,38-41`
- Modify: `app/wear/src/main/AndroidManifest.xml` (the `MainActivity` element, original lines 228-238, 223-233 after Task 1)
- Modify: `app/wear/src/main/res/values/strings.xml` (before `<!-- Plurals -->`)

**Interfaces:**
- Consumes: `FeatureFlag.WATCH_EXPERIENCE_V2` and `FeatureFlagStore` (`app.logdate.client.datastore.featureflags`, registered by `commonDatastoreModule` through `wearDataModule`); Task 2's mood entry; Task 3's `wearRouteFor` (deleted here).
- Produces (package `app.logdate.wear.launch`):
  - `enum class WearExperience { V1, V2; companion object { fun fromName(name: String?): WearExperience? } }`
  - `class WearExperienceGate(featureFlags: FeatureFlagStore) { suspend fun resolve(restored: WearExperience? = null): WearExperience; companion object { val FLAG_READ_TIMEOUT: kotlin.time.Duration } }`
  - `sealed interface WearLaunchRequest` with `Home`, `Record`, `Memories`, `MoodCheckIn`, `QuickText`, `Timeline`, `RemoteCamera`, `Settings`, `Mark`, `OnThisDay` (data objects) and `data class Moment(val noteId: Uuid)`; `companion object { const val EXTRA_ROUTE = "tile_route"; const val EXTRA_NOTE_ID = "note_id"; fun parse(route: String?, noteId: String? = null): WearLaunchRequest? }`
  - `fun android.content.Intent.toWearLaunchRequest(): WearLaunchRequest?`
- Produces elsewhere: `internal fun WearLaunchRequest?.toV1Route(): NavKey` (`app.logdate.wear.presentation.navigation`); `@Composable fun WearApp(initialRequest: WearLaunchRequest? = null, newRequests: Flow<WearLaunchRequest> = emptyFlow())` and `@Composable internal fun MainActivityContent(experience: WearExperience?, initialRequest: WearLaunchRequest?, newRequests: Flow<WearLaunchRequest>)` (`app.logdate.wear.presentation`); `object WearAppV2Tags { const val PLACEHOLDER = "wear_app_v2_placeholder" }` and `@Composable fun WearAppV2Placeholder()` (`app.logdate.wear.v2`); `val wearExperienceModule: org.koin.core.module.Module` (`app.logdate.wear.di`).

- [ ] **Step 1: Add stubs, the placeholder and its strings**

Create `app/wear/src/main/kotlin/app/logdate/wear/launch/WearExperienceGate.kt` (Step 6 replaces it):

```kotlin
package app.logdate.wear.launch

import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class WearExperience {
    V1,
    V2,
    ;

    companion object {
        fun fromName(name: String?): WearExperience? = null
    }
}

class WearExperienceGate(
    private val featureFlags: FeatureFlagStore,
) {
    suspend fun resolve(restored: WearExperience? = null): WearExperience = WearExperience.V1

    companion object {
        val FLAG_READ_TIMEOUT: Duration = 2.seconds
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/launch/WearLaunchRequest.kt` (Step 7 replaces `parse`):

```kotlin
package app.logdate.wear.launch

import android.content.Intent
import kotlin.uuid.Uuid

sealed interface WearLaunchRequest {
    data object Home : WearLaunchRequest

    data object Record : WearLaunchRequest

    data object Memories : WearLaunchRequest

    data object MoodCheckIn : WearLaunchRequest

    data object QuickText : WearLaunchRequest

    data object Timeline : WearLaunchRequest

    data object RemoteCamera : WearLaunchRequest

    data object Settings : WearLaunchRequest

    data object Mark : WearLaunchRequest

    data object OnThisDay : WearLaunchRequest

    data class Moment(
        val noteId: Uuid,
    ) : WearLaunchRequest

    companion object {
        const val EXTRA_ROUTE = "tile_route"
        const val EXTRA_NOTE_ID = "note_id"

        fun parse(
            route: String?,
            noteId: String? = null,
        ): WearLaunchRequest? = null
    }
}

fun Intent.toWearLaunchRequest(): WearLaunchRequest? =
    WearLaunchRequest.parse(
        route = getStringExtra(WearLaunchRequest.EXTRA_ROUTE),
        noteId = getStringExtra(WearLaunchRequest.EXTRA_NOTE_ID),
    )
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2Placeholder.kt` (final):

```kotlin
package app.logdate.wear.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.logdate.wear.R

object WearAppV2Tags {
    const val PLACEHOLDER = "wear_app_v2_placeholder"
}

/**
 * Shown instead of the redesigned watch app until its screens exist. Only someone who turns on
 * WATCH_EXPERIENCE_V2 sees it.
 */
@Composable
fun WearAppV2Placeholder() {
    MaterialTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .testTag(WearAppV2Tags.PLACEHOLDER),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.wear_v2_placeholder_title),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.wear_v2_placeholder_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
```

In `app/wear/src/main/res/values/strings.xml`, add before `<!-- Plurals -->`:

```xml
    <!-- New watch experience -->
    <string name="wear_v2_placeholder_title">New watch experience</string>
    <string name="wear_v2_placeholder_body">Not built yet</string>

```

In `app/wear/src/main/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutes.kt`, add below `wearRouteFor` (Step 8 replaces the file):

```kotlin

internal fun app.logdate.wear.launch.WearLaunchRequest?.toV1Route(): NavKey = WearHomeRoute
```

- [ ] **Step 2: Write the failing unit tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/launch/WearExperienceGateTest.kt`:

```kotlin
package app.logdate.wear.launch

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WearExperienceGateTest {
    @Test
    fun `the current watch app opens while the flag is off`() =
        runTest {
            assertEquals(WearExperience.V1, WearExperienceGate(FakeFeatureFlagStore(enabled = false)).resolve())
        }

    @Test
    fun `the new watch app opens while the flag is on`() =
        runTest {
            assertEquals(WearExperience.V2, WearExperienceGate(FakeFeatureFlagStore(enabled = true)).resolve())
        }

    @Test
    fun `only the watch experience flag is read`() =
        runTest {
            val flags = FakeFeatureFlagStore(enabled = true)

            WearExperienceGate(flags).resolve()

            assertEquals(listOf(FeatureFlag.WATCH_EXPERIENCE_V2), flags.reads)
        }

    @Test
    fun `a recreated activity keeps its watch app without reading the flag`() =
        runTest {
            val flags = FakeFeatureFlagStore(enabled = false)

            val experience = WearExperienceGate(flags).resolve(restored = WearExperience.V2)

            assertEquals(WearExperience.V2, experience)
            assertTrue(flags.reads.isEmpty())
        }

    @Test
    fun `a failed flag read opens the current watch app`() =
        runTest {
            val flags = FakeFeatureFlagStore(enabled = true, failure = IOException("The preferences file is unreadable"))

            assertEquals(WearExperience.V1, WearExperienceGate(flags).resolve())
        }

    @Test
    fun `a flag read that never finishes opens the current watch app`() =
        runTest {
            val flags = FakeFeatureFlagStore(enabled = true, hangs = true)

            assertEquals(WearExperience.V1, WearExperienceGate(flags).resolve())
        }

    @Test
    fun `saved names map back to experiences`() {
        assertEquals(WearExperience.V2, WearExperience.fromName("V2"))
        assertEquals(WearExperience.V1, WearExperience.fromName("V1"))
        assertNull(WearExperience.fromName(null))
        assertNull(WearExperience.fromName("V3"))
    }

    private class FakeFeatureFlagStore(
        private val enabled: Boolean,
        private val failure: Exception? = null,
        private val hangs: Boolean = false,
    ) : FeatureFlagStore {
        val reads = mutableListOf<FeatureFlag>()

        override fun observe(flag: FeatureFlag): Flow<Boolean> =
            flow {
                reads += flag
                failure?.let { throw it }
                if (hangs) awaitCancellation()
                emit(enabled)
            }

        override suspend fun setEnabled(
            flag: FeatureFlag,
            enabled: Boolean,
        ) = Unit
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/launch/WearLaunchRequestTest.kt`:

```kotlin
package app.logdate.wear.launch

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class WearLaunchRequestTest {
    @Test
    fun `routes that today's tiles and complications send still parse`() {
        assertEquals(WearLaunchRequest.Record, WearLaunchRequest.parse("quick_record"))
        assertEquals(WearLaunchRequest.Record, WearLaunchRequest.parse("voice_note"))
        assertEquals(WearLaunchRequest.Memories, WearLaunchRequest.parse("memories"))
        assertEquals(WearLaunchRequest.MoodCheckIn, WearLaunchRequest.parse("mood"))
        assertEquals(WearLaunchRequest.QuickText, WearLaunchRequest.parse("quick_text"))
        assertEquals(WearLaunchRequest.Timeline, WearLaunchRequest.parse("timeline"))
        assertEquals(WearLaunchRequest.RemoteCamera, WearLaunchRequest.parse("remote_camera"))
        assertEquals(WearLaunchRequest.Settings, WearLaunchRequest.parse("settings"))
    }

    @Test
    fun `routes to the removed Rewind and Health screens open Home`() {
        assertEquals(WearLaunchRequest.Home, WearLaunchRequest.parse("rewind"))
        assertEquals(WearLaunchRequest.Home, WearLaunchRequest.parse("health"))
    }

    @Test
    fun `the new actions parse`() {
        assertEquals(WearLaunchRequest.Record, WearLaunchRequest.parse("record"))
        assertEquals(WearLaunchRequest.Mark, WearLaunchRequest.parse("mark"))
        assertEquals(WearLaunchRequest.OnThisDay, WearLaunchRequest.parse("on_this_day"))
    }

    @Test
    fun `a moment request carries its note`() {
        val noteId = Uuid.random()

        assertEquals(WearLaunchRequest.Moment(noteId), WearLaunchRequest.parse("moment", noteId.toString()))
    }

    @Test
    fun `a moment request without a valid note is no request`() {
        assertNull(WearLaunchRequest.parse("moment"))
        assertNull(WearLaunchRequest.parse("moment", "not-a-note-id"))
    }

    @Test
    fun `a missing or unknown route is no request`() {
        assertNull(WearLaunchRequest.parse(null))
        assertNull(WearLaunchRequest.parse("unknown"))
    }
}
```

Replace the whole of `app/wear/src/test/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutesTest.kt`:

```kotlin
package app.logdate.wear.presentation.navigation

import app.logdate.wear.launch.WearLaunchRequest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class WearLaunchRoutesTest {
    @Test
    fun `requests for screens the current app has open those screens`() {
        assertEquals(WearVoiceMemoriesRoute, WearLaunchRequest.Memories.toV1Route())
        assertEquals(WearMoodCheckInRoute, WearLaunchRequest.MoodCheckIn.toV1Route())
        assertEquals(WearQuickTextRoute, WearLaunchRequest.QuickText.toV1Route())
        assertEquals(WearTimelineRoute, WearLaunchRequest.Timeline.toV1Route())
        assertEquals(WearRemoteCameraRoute, WearLaunchRequest.RemoteCamera.toV1Route())
        assertEquals(WearSettingsRoute, WearLaunchRequest.Settings.toV1Route())
    }

    @Test
    fun `requests the current app has no screen for open Home`() {
        assertEquals(WearHomeRoute, WearLaunchRequest.Home.toV1Route())
        assertEquals(WearHomeRoute, WearLaunchRequest.Record.toV1Route())
        assertEquals(WearHomeRoute, WearLaunchRequest.Mark.toV1Route())
        assertEquals(WearHomeRoute, WearLaunchRequest.OnThisDay.toV1Route())
        assertEquals(WearHomeRoute, WearLaunchRequest.Moment(Uuid.random()).toV1Route())
    }

    @Test
    fun `no request opens Home`() {
        val none: WearLaunchRequest? = null

        assertEquals(WearHomeRoute, none.toV1Route())
    }
}
```

- [ ] **Step 3: Write the failing GMD tests**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ExperienceGateTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.v2.WearAppV2Tags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent

/**
 * Launches the real activity with WATCH_EXPERIENCE_V2 off and on. The flag picks the watch app
 * once, when the activity is created, and a recreated activity shows the app it had.
 */
@RunWith(AndroidJUnit4::class)
class ExperienceGateTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val onboarding = context.getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun skipOnboarding() {
        onboarding.edit().putBoolean(ONBOARDING_COMPLETE, true).commit()
    }

    @After
    fun tearDown() {
        scenario?.close()
        setNewWatchApp(enabled = false)
        onboarding.edit().remove(ONBOARDING_COMPLETE).commit()
    }

    @Test
    fun the_current_watch_app_opens_while_the_flag_is_off() {
        setNewWatchApp(enabled = false)

        scenario = ActivityScenario.launch(MainActivity::class.java)

        waitFor { composeRule.onAllNodesWithContentDescription(RECORD_AUDIO) }
        composeRule.onNodeWithContentDescription(RECORD_AUDIO).assertIsDisplayed()
        composeRule.onAllNodesWithTag(WearAppV2Tags.PLACEHOLDER).assertCountEquals(0)
    }

    @Test
    fun the_new_watch_app_opens_while_the_flag_is_on() {
        setNewWatchApp(enabled = true)

        scenario = ActivityScenario.launch(MainActivity::class.java)

        waitFor { composeRule.onAllNodesWithTag(WearAppV2Tags.PLACEHOLDER) }
        composeRule.onNodeWithTag(WearAppV2Tags.PLACEHOLDER).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(RECORD_AUDIO).assertCountEquals(0)
    }

    @Test
    fun turning_the_flag_off_does_not_swap_an_open_activity() {
        setNewWatchApp(enabled = true)
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        waitFor { composeRule.onAllNodesWithTag(WearAppV2Tags.PLACEHOLDER) }

        setNewWatchApp(enabled = false)
        launched.recreate()

        waitFor { composeRule.onAllNodesWithTag(WearAppV2Tags.PLACEHOLDER) }
        composeRule.onAllNodesWithContentDescription(RECORD_AUDIO).assertCountEquals(0)
    }

    private fun setNewWatchApp(enabled: Boolean) =
        runBlocking {
            KoinJavaComponent.getKoin().get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        }

    private fun waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        composeRule.waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        // WearOnboardingScreen's preferences: the current app shows onboarding until this is set.
        const val ONBOARDING_PREFERENCES = "wear_onboarding"
        const val ONBOARDING_COMPLETE = "onboarding_complete"

        // The record button's content description on the current Home screen.
        const val RECORD_AUDIO = "Record Audio"
        const val TIMEOUT_MS = 10_000L
    }
}
```

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/LaunchRequestRoutingTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent

/**
 * A launch that arrives while the watch app is open, as when a tile is tapped, goes to the open
 * activity and opens the requested screen there.
 */
@RunWith(AndroidJUnit4::class)
class LaunchRequestRoutingTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val onboarding = context.getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun openTheCurrentWatchApp() {
        onboarding.edit().putBoolean(ONBOARDING_COMPLETE, true).commit()
        runBlocking {
            KoinJavaComponent.getKoin().get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, false)
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        onboarding.edit().remove(ONBOARDING_COMPLETE).commit()
    }

    @Test
    fun a_request_that_arrives_while_the_app_is_open_opens_its_screen_in_the_same_activity() {
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        waitFor { composeRule.onAllNodesWithContentDescription(RECORD_AUDIO) }

        launched.onActivity { activity ->
            activity.startActivity(
                Intent(activity, MainActivity::class.java).putExtra(WearLaunchRequest.EXTRA_ROUTE, "mood"),
            )
        }

        waitFor { composeRule.onAllNodesWithText(MOOD_TITLE) }
        launched.onActivity { activity ->
            assertEquals(
                "The open activity should receive the request instead of a second copy",
                "mood",
                activity.intent.getStringExtra(WearLaunchRequest.EXTRA_ROUTE),
            )
        }
    }

    private fun waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        composeRule.waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        // WearOnboardingScreen's preferences: the current app shows onboarding until this is set.
        const val ONBOARDING_PREFERENCES = "wear_onboarding"
        const val ONBOARDING_COMPLETE = "onboarding_complete"

        const val RECORD_AUDIO = "Record Audio"
        const val MOOD_TITLE = "How are you feeling?"
        const val TIMEOUT_MS = 10_000L
    }
}
```

- [ ] **Step 4: Run the unit tests and watch them fail**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.launch.*' --tests 'app.logdate.wear.presentation.navigation.WearLaunchRoutesTest'
```

Expected: FAIL on assertions. Failing: `WearExperienceGateTest` `the new watch app opens while the flag is on`, `only the watch experience flag is read`, `a recreated activity keeps its watch app without reading the flag` and `saved names map back to experiences`; `WearLaunchRequestTest` `routes that today's tiles and complications send still parse`, `routes to the removed Rewind and Health screens open Home`, `the new actions parse` and `a moment request carries its note`; `WearLaunchRoutesTest` `requests for screens the current app has open those screens`. The tests whose answer is V1, null or Home pass against the stubs.

- [ ] **Step 5: Run the GMD tests and watch them fail**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ExperienceGateTest,app.logdate.wear.e2e.LaunchRequestRoutingTest
```

Expected: FAIL. `the_new_watch_app_opens_while_the_flag_is_on` and `turning_the_flag_off_does_not_swap_an_open_activity` time out waiting for the placeholder; `a_request_that_arrives_while_the_app_is_open_opens_its_screen_in_the_same_activity` fails with "The open activity should receive the request instead of a second copy". `the_current_watch_app_opens_while_the_flag_is_off` passes.

- [ ] **Step 6: Implement the gate**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/launch/WearExperienceGate.kt`:

```kotlin
package app.logdate.wear.launch

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The two watch apps the activity can show. */
enum class WearExperience {
    /** The watch app that ships today. */
    V1,

    /** The redesigned watch app, behind [FeatureFlag.WATCH_EXPERIENCE_V2]. */
    V2,
    ;

    companion object {
        /** The experience saved under [name], or null when nothing valid was saved. */
        fun fromName(name: String?): WearExperience? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Decides which watch app an activity shows. It is the only reader of
 * [FeatureFlag.WATCH_EXPERIENCE_V2] on the watch, and it reads the flag once per activity.
 */
class WearExperienceGate(
    private val featureFlags: FeatureFlagStore,
) {
    /**
     * Returns [restored] when a recreated activity saved its choice, so a configuration change never
     * swaps the app. Otherwise reads the flag. A read that fails or takes longer than
     * [FLAG_READ_TIMEOUT] opens [WearExperience.V1], so the splash screen always ends.
     */
    suspend fun resolve(restored: WearExperience? = null): WearExperience {
        if (restored != null) return restored
        return if (isNewWatchAppOn()) WearExperience.V2 else WearExperience.V1
    }

    private suspend fun isNewWatchAppOn(): Boolean {
        val enabled =
            try {
                withTimeoutOrNull(FLAG_READ_TIMEOUT) {
                    featureFlags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Couldn't read the watch experience flag; opening the current watch app", e)
                return false
            }
        if (enabled == null) {
            Napier.w("Reading the watch experience flag took over $FLAG_READ_TIMEOUT; opening the current watch app")
            return false
        }
        return enabled
    }

    companion object {
        val FLAG_READ_TIMEOUT: Duration = 2.seconds
    }
}
```

- [ ] **Step 7: Implement the parser**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/launch/WearLaunchRequest.kt`:

```kotlin
package app.logdate.wear.launch

import android.content.Intent
import kotlin.uuid.Uuid

/**
 * What a tile, a complication or another app asked the watch app to open.
 *
 * Tiles and complications that older builds placed on a watch face keep sending the route names
 * those builds used, so every one of them still parses.
 */
sealed interface WearLaunchRequest {
    data object Home : WearLaunchRequest

    /** Open the recorder. Today's tiles and complications ask for it as `quick_record` or `voice_note`. */
    data object Record : WearLaunchRequest

    data object Memories : WearLaunchRequest

    data object MoodCheckIn : WearLaunchRequest

    data object QuickText : WearLaunchRequest

    data object Timeline : WearLaunchRequest

    data object RemoteCamera : WearLaunchRequest

    data object Settings : WearLaunchRequest

    /** Mark the current moment. */
    data object Mark : WearLaunchRequest

    data object OnThisDay : WearLaunchRequest

    /** Open the moment saved as the note [noteId]. */
    data class Moment(
        val noteId: Uuid,
    ) : WearLaunchRequest

    companion object {
        /** The intent extra that names the route. Its name comes from the first tiles. */
        const val EXTRA_ROUTE = "tile_route"

        /** The intent extra that names the note a `moment` route opens. */
        const val EXTRA_NOTE_ID = "note_id"

        /**
         * Parses a route name and, for `moment`, its note ID. Returns null for a missing or unknown
         * route, and for a `moment` without a valid note ID.
         */
        fun parse(
            route: String?,
            noteId: String? = null,
        ): WearLaunchRequest? =
            when (route) {
                "quick_record", "voice_note", "record" -> Record
                "memories" -> Memories
                "mood" -> MoodCheckIn
                "quick_text" -> QuickText
                "timeline" -> Timeline
                "remote_camera" -> RemoteCamera
                "settings" -> Settings
                "rewind", "health" -> Home
                "mark" -> Mark
                "on_this_day" -> OnThisDay
                "moment" -> noteId?.let { Uuid.parseOrNull(it) }?.let { Moment(it) }
                else -> null
            }
    }
}

/** The request this intent carries, or null when it carries none. */
fun Intent.toWearLaunchRequest(): WearLaunchRequest? =
    WearLaunchRequest.parse(
        route = getStringExtra(WearLaunchRequest.EXTRA_ROUTE),
        noteId = getStringExtra(WearLaunchRequest.EXTRA_NOTE_ID),
    )
```

- [ ] **Step 8: Map requests to the current app's screens**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutes.kt` (this deletes Task 3's `wearRouteFor`):

```kotlin
package app.logdate.wear.presentation.navigation

import androidx.navigation3.runtime.NavKey
import app.logdate.wear.launch.WearLaunchRequest

/**
 * The screen the current watch app opens for this request. It has no screen for starting a
 * recording on launch, marking, a single moment or On this day, so those open Home, which is the
 * recorder.
 */
internal fun WearLaunchRequest?.toV1Route(): NavKey =
    when (this) {
        WearLaunchRequest.Memories -> WearVoiceMemoriesRoute
        WearLaunchRequest.MoodCheckIn -> WearMoodCheckInRoute
        WearLaunchRequest.QuickText -> WearQuickTextRoute
        WearLaunchRequest.Timeline -> WearTimelineRoute
        WearLaunchRequest.RemoteCamera -> WearRemoteCameraRoute
        WearLaunchRequest.Settings -> WearSettingsRoute
        null,
        WearLaunchRequest.Home,
        WearLaunchRequest.Record,
        WearLaunchRequest.Mark,
        WearLaunchRequest.OnThisDay,
        is WearLaunchRequest.Moment,
        -> WearHomeRoute
    }
```

- [ ] **Step 9: Register the gate with Koin**

Create `app/wear/src/main/kotlin/app/logdate/wear/di/ExperienceModule.kt`:

```kotlin
package app.logdate.wear.di

import app.logdate.wear.launch.WearExperienceGate
import org.koin.dsl.module

/** Koin module for deciding which watch app the activity shows. */
val wearExperienceModule =
    module {
        factory { WearExperienceGate(get()) }
    }
```

In `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`, add after line 8 (`import app.logdate.wear.di.wearDataModule`):

```kotlin
import app.logdate.wear.di.wearExperienceModule
```

and replace lines 38-41:

```kotlin
            modules(
                wearDataModule,
                wearAudioModule,
            )
```

with:

```kotlin
            modules(
                wearDataModule,
                wearAudioModule,
                wearExperienceModule,
            )
```

- [ ] **Step 10: Move the v1 tree out of `MainActivity` and make the activity a table of contents**

Create `app/wear/src/main/kotlin/app/logdate/wear/presentation/WearApp.kt` (the `NavDisplay` body is today's, after Tasks 2 and 3):

```kotlin
package app.logdate.wear.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.presentation.camera.WearRemoteCameraScreen
import app.logdate.wear.presentation.camera.WearRemoteCameraViewModel
import app.logdate.wear.presentation.home.WearHomeScreen
import app.logdate.wear.presentation.memories.WearMemoryPlayerScreen
import app.logdate.wear.presentation.memories.WearVoiceMemoriesScreen
import app.logdate.wear.presentation.mood.MoodCheckInScreen
import app.logdate.wear.presentation.more.WearMoreScreen
import app.logdate.wear.presentation.navigation.WearHomeRoute
import app.logdate.wear.presentation.navigation.WearMemoryPlayerRoute
import app.logdate.wear.presentation.navigation.WearMoodCheckInRoute
import app.logdate.wear.presentation.navigation.WearMoreRoute
import app.logdate.wear.presentation.navigation.WearOnboardingRoute
import app.logdate.wear.presentation.navigation.WearQuickTextRoute
import app.logdate.wear.presentation.navigation.WearRemoteCameraRoute
import app.logdate.wear.presentation.navigation.WearSettingsRoute
import app.logdate.wear.presentation.navigation.WearTimelineDayDetailRoute
import app.logdate.wear.presentation.navigation.WearTimelineRoute
import app.logdate.wear.presentation.navigation.WearVoiceMemoriesRoute
import app.logdate.wear.presentation.navigation.toV1Route
import app.logdate.wear.presentation.onboarding.WearOnboardingScreen
import app.logdate.wear.presentation.onboarding.isOnboardingComplete
import app.logdate.wear.presentation.quicktext.QuickTextLauncher
import app.logdate.wear.presentation.settings.WearSettingsScreen
import app.logdate.wear.presentation.theme.LogDateTheme
import app.logdate.wear.presentation.timeline.WearDayDetailScreen
import app.logdate.wear.presentation.timeline.WearTimelineScreen
import app.logdate.wear.presentation.timeline.WearTimelineViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The watch app that ships today: Home is the recorder, and every other screen opens from it.
 *
 * [initialRequest] picks the first screen. Each item of [newRequests] replaces the back stack with
 * its screen, as a cold launch from a tile does. Both are ignored until onboarding is done.
 */
@Composable
fun WearApp(
    initialRequest: WearLaunchRequest? = null,
    newRequests: Flow<WearLaunchRequest> = emptyFlow(),
) {
    LogDateTheme {
        val context = LocalContext.current
        val startRoute: NavKey =
            if (isOnboardingComplete(context)) {
                initialRequest.toV1Route()
            } else {
                WearOnboardingRoute
            }
        val backStack = remember { mutableStateListOf(startRoute) }
        val navigateBack: () -> Unit = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
        LaunchedEffect(newRequests) {
            newRequests.collect { request ->
                if (!isOnboardingComplete(context)) return@collect
                backStack.clear()
                backStack.add(request.toV1Route())
            }
        }

        NavDisplay(
            backStack = backStack,
            onBack = navigateBack,
            entryProvider =
                entryProvider {
                    entry<WearOnboardingRoute> {
                        WearOnboardingScreen(
                            onComplete = {
                                backStack.clear()
                                backStack.add(WearHomeRoute)
                            },
                        )
                    }
                    entry<WearHomeRoute> {
                        WearHomeScreen(
                            onNavigateToMoodCheckIn = {
                                backStack.add(WearMoodCheckInRoute)
                            },
                            onNavigateToMemories = {
                                backStack.add(WearVoiceMemoriesRoute)
                            },
                            onNavigateToMore = {
                                backStack.add(WearMoreRoute)
                            },
                        )
                    }
                    entry<WearVoiceMemoriesRoute> {
                        WearVoiceMemoriesScreen(onOpenMemory = { noteId -> backStack.add(WearMemoryPlayerRoute(noteId)) })
                    }
                    entry<WearMemoryPlayerRoute> { route ->
                        WearMemoryPlayerScreen(noteId = route.noteId)
                    }
                    entry<WearMoreRoute> {
                        WearMoreScreen(
                            onNavigateToQuickText = { backStack.add(WearQuickTextRoute) },
                            onNavigateToTimeline = { backStack.add(WearTimelineRoute) },
                            onNavigateToSettings = { backStack.add(WearSettingsRoute) },
                        )
                    }
                    entry<WearMoodCheckInRoute> {
                        MoodCheckInScreen(
                            onNavigateBack = navigateBack,
                            // The recorder is the Home screen; it never starts recording on its own. Replacing the
                            // stack also reaches Home when a tile or complication opened the check-in directly.
                            onNavigateToVoiceNote = {
                                backStack.clear()
                                backStack.add(WearHomeRoute)
                            },
                        )
                    }
                    entry<WearQuickTextRoute> {
                        val notesRepository = koinInject<JournalNotesRepository>()
                        val locationCaptureCoordinator = koinInject<WearLocationCaptureCoordinator>()
                        QuickTextLauncher(
                            notesRepository = notesRepository,
                            locationCaptureCoordinator = locationCaptureCoordinator,
                            onDone = navigateBack,
                        )
                    }
                    entry<WearTimelineRoute> {
                        val timelineViewModel = koinViewModel<WearTimelineViewModel>()
                        WearTimelineScreen(
                            onNavigateBack = navigateBack,
                            onNavigateToDay = { date ->
                                timelineViewModel.selectDay(date)
                                backStack.add(WearTimelineDayDetailRoute(date))
                            },
                            viewModel = timelineViewModel,
                        )
                    }
                    entry<WearTimelineDayDetailRoute> { route ->
                        val timelineViewModel = koinViewModel<WearTimelineViewModel>()
                        WearDayDetailScreen(
                            date = route.date,
                            viewModel = timelineViewModel,
                        )
                    }
                    entry<WearRemoteCameraRoute> {
                        WearRemoteCameraScreen(
                            viewModel = koinViewModel<WearRemoteCameraViewModel>(),
                            onNavigateBack = navigateBack,
                        )
                    }
                    entry<WearSettingsRoute> {
                        WearSettingsScreen(onNavigateBack = navigateBack)
                    }
                },
        )
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivityContent.kt`:

```kotlin
package app.logdate.wear.presentation

import androidx.compose.runtime.Composable
import app.logdate.wear.launch.WearExperience
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.v2.WearAppV2Placeholder
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
        WearExperience.V2 -> WearAppV2Placeholder()
    }
}
```

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt`:

```kotlin
package app.logdate.wear.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.logdate.wear.launch.WearExperience
import app.logdate.wear.launch.WearExperienceGate
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.launch.toWearLaunchRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The watch app's only activity.
 *
 * Tiles, complications and the benchmark modules start it as `app.logdate.wear.presentation.MainActivity`,
 * so its name and package stay as they are. It is single-top: a launch that arrives while it is on
 * screen comes through [onNewIntent].
 */
class MainActivity : ComponentActivity() {
    private val experienceGate: WearExperienceGate by inject()
    private val experience = MutableStateFlow<WearExperience?>(null)
    private val launchRequests = Channel<WearLaunchRequest>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        splashScreen.setKeepOnScreenCondition { experience.value == null }
        resolveExperience(savedInstanceState)

        val initialRequest = intent.toWearLaunchRequest()
        val newRequests = launchRequests.receiveAsFlow()
        setContent {
            val resolvedExperience by experience.collectAsState()
            MainActivityContent(
                experience = resolvedExperience,
                initialRequest = initialRequest,
                newRequests = newRequests,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val request = intent.toWearLaunchRequest() ?: return
        launchRequests.trySend(request)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val resolved = experience.value ?: return
        outState.putString(STATE_EXPERIENCE, resolved.name)
    }

    /**
     * Picks the watch app behind the splash screen. A recreated activity passes its saved choice, so
     * the app never changes while the activity is alive.
     */
    private fun resolveExperience(savedInstanceState: Bundle?) {
        val restored = WearExperience.fromName(savedInstanceState?.getString(STATE_EXPERIENCE))
        lifecycleScope.launch {
            experience.value = experienceGate.resolve(restored)
        }
    }

    private companion object {
        const val STATE_EXPERIENCE = "wear_experience"
    }
}
```

- [ ] **Step 11: Make the activity single-top**

In `app/wear/src/main/AndroidManifest.xml`, replace the `MainActivity` element's opening tag:

```xml
        <activity
            android:name=".presentation.MainActivity"
            android:exported="true"
            android:taskAffinity=""
            android:theme="@style/MainActivityTheme.Starting">
```

with:

```xml
        <activity
            android:name=".presentation.MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:taskAffinity=""
            android:theme="@style/MainActivityTheme.Starting">
```

- [ ] **Step 12: Run the unit tests again**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:testDebugUnitTest
```

Expected: PASS, the whole `:app:wear` unit suite.

```bash
grep -rn "wearRouteFor\|TILE_ROUTE_EXTRA\|toWearRoute()" app/wear/src
```

Expected: no output.

- [ ] **Step 13: Run the GMD tests again, with the recorder E2E test that also launches `MainActivity`**

```bash
uptime && export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ExperienceGateTest,app.logdate.wear.e2e.LaunchRequestRoutingTest,app.logdate.wear.e2e.WearRecorderServiceE2ETest
```

Expected: PASS: 3 + 1 + 10 tests.

- [ ] **Step 14: Run the static checks and the screenshots**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:wear:assembleDebug :app:wear:lintDebug :app:wear:detekt :app:wear:ktlintCheck :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`. No screenshot test renders `MainActivity`, so no baseline changes.

- [ ] **Step 15: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-5-gate.txt <<'EOF'
chore(wear): add the watch experience gate and launch request parser

The redesigned watch app ships behind WATCH_EXPERIENCE_V2. The watch
now reads that flag once, behind the splash screen, when its activity
is created, and uses the answer for the life of the activity, so
turning the flag on or off never swaps the app while it is open. With
the flag on, the watch shows a placeholder until the new screens
land. With it off, the watch app is unchanged.

Launches from tiles and complications are parsed into one request
type. Every route name a tile or complication has sent still parses,
the routes to the removed Rewind and Health screens open Home, and
record, mark, moment and On this day requests are ready for the new
app. The activity is now single-top, so a tile tapped while LogDate
is open shows the requested screen in that activity instead of
opening a second copy of it.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 16: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/launch/WearExperienceGate.kt app/wear/src/main/kotlin/app/logdate/wear/launch/WearLaunchRequest.kt app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2Placeholder.kt app/wear/src/main/kotlin/app/logdate/wear/di/ExperienceModule.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/WearApp.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivityContent.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt app/wear/src/main/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutes.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/main/AndroidManifest.xml app/wear/src/main/res/values/strings.xml app/wear/src/test/kotlin/app/logdate/wear/launch/WearExperienceGateTest.kt app/wear/src/test/kotlin/app/logdate/wear/launch/WearLaunchRequestTest.kt app/wear/src/test/kotlin/app/logdate/wear/presentation/navigation/WearLaunchRoutesTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ExperienceGateTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/LaunchRequestRoutingTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b0-5-gate.txt
```

Expected: the hooks pass; `git show --stat HEAD` lists 16 paths (10 added, 6 modified); `git status --short` is empty.

---

## Self-Review

**Spec coverage.** Item 1 (prompts): Task 1 deletes `notification/*` (four files), the manifest receiver, the `scheduleAll()` call and the Settings switches, and cancels request codes 5001 and 5002 for the receiver `app.logdate.wear.notification.WearPromptReceiver` with `FLAG_IMMUTABLE` in the lookup. Item 2 (feelings): Task 2 adds `capture/Feeling.kt` and `MomentNoteCodec`, applies them in `MoodComplicationService`, `TodaySummaryTileService`, `WearTimelineViewModel` and `MoodCheckInViewModel`, and tests all three bugs (`MoodComplicationTextTest`, `TodaySummaryTest`, `MomentNoteCodecTest`, `MoodCheckInViewModelTest`). Item 3 (unreachable screens): Task 3, with the health sensor code and the Rewind repository kept. Item 4 (libraries): Task 4, with the screenshot re-baseline as its own reviewed step. Item 5 (gate and parser): Task 5, with unit tests for the gate and the parser, `ExperienceGateTest` on the GMD, and `LaunchRequestRoutingTest` for single-top delivery. Phase 0 is a prerequisite (Task 0, Global Constraints).

**Placeholder scan.** The stub bodies in Task 1 Step 1, Task 2 Step 2 and Task 5 Step 1 exist so the failing tests compile, as `CLAUDE.md` asks; each is replaced in a named later step of the same task. Task 4's second message variant generates its file list with a command instead of leaving a gap. No "TBD", "similar to Task N" or untested "add error handling" steps remain.

**Type consistency.** `Feeling`, `MomentNoteKind`, `MomentNoteCodec.encode/feelingOf/classify/latestFeeling`, `feelingComplicationText`, `TodaySummary(entryCount, feeling, latestEntry)`, `summarizeToday`, `WearTimelineDayUiState.feeling`, `WearExperience.fromName`, `WearExperienceGate.resolve(restored)`, `WearLaunchRequest.parse(route, noteId)`, `EXTRA_ROUTE`, `EXTRA_NOTE_ID`, `Intent.toWearLaunchRequest()`, `WearLaunchRequest?.toV1Route()`, `WearApp(initialRequest, newRequests)`, `MainActivityContent(experience, initialRequest, newRequests)`, `WearAppV2Tags.PLACEHOLDER` and `wearExperienceModule` are spelled the same in every task that defines or uses them. `wearRouteFor` lives only between Tasks 3 and 5.

**Research that turned out wrong or incomplete.**
- The prompt switches live only in `WearSettingsScreen` (Compose state plus `wear_settings`); `WearSettingsViewModel` has no prompt state and needs no change.
- The Health dashboard and Rewind screens have no screenshot baselines to delete; only the two unit tests.
- `tiles-material` is not used: no file imports `androidx.wear.tiles.material`. The tiles' `androidx.wear.protolayout.material.*` imports come from `protolayout-material`, which today arrives only through `horologist-tiles`; Task 4 declares it explicitly and removes `tiles-material`.
- Removing the direct `compose-material` dependency does not remove the artifact: `horologist-tiles` still depends on it until the tile rewrite.
- `lifecycle-viewmodel-navigation3` is shared with the phone, so Task 4 also moves `app/compose-main` from 2.11.0-alpha03 to 2.11.0 and builds the phone.
- The "Add voice note" button is labelled "Open recorder" since `e6b262d2b`, and three `MoodCheckInScreenTest` tests still look for the old text; Task 2 fixes them.
- Request codes 5001/5002 and the receiver name were correct; matching also needs `FLAG_IMMUTABLE`.

**Decisions to confirm before executing.**
- `LegacyPromptAlarms.cancel` runs on every start rather than once behind a stored marker: each step is idempotent, and it also deletes the old channel and the `wear_settings` file.
- `quick_record` and `voice_note` parse as `Record`, so placed Record complications start recording once Track B's recorder handles `Record`; v1 keeps opening Home.
- The gate falls back to v1 after 2 seconds or on a read error, so the splash screen cannot hang.
- `androidx.wear:wear` 1.4.0 pulls in fragment and recyclerview, and release builds are not minified. Wear Compose 1.6+ ships `rememberAmbientModeManager()` in compose-foundation, so if Track B's recorder uses that, drop `androidx.wear:wear` then.
- Opening the check-in from a tile and tapping Skip still leaves the Saved confirmation on screen (a back stack of one); that predates this plan and is left for the Track B shell.
