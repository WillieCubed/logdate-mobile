# Wear Redesign Phase 0 Prerequisites Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the four prerequisites the Wear OS and phone companion redesign (`docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md`) needs before any track starts: a `reminders` commit scope, a Wear OS 5 floor for the watch app, the `FeatureFlag.WATCH_EXPERIENCE_V2` flag, and a hidden Labs page that turns that flag on in release builds.

**Architecture:** Five small commits, each shippable on its own. The scope and minimum-SDK changes touch only docs, Gradle files and one watch onboarding helper. The flag is one new `FeatureFlag` entry in `client/datastore`. The Labs page lives in `client/feature/core` beside the other settings screens: a common Compose screen with a Koin-resolved `ViewModel` that reads and writes flags through `FeatureFlagStore`, registered as a Navigation 3 route in `settingsEntries` and `AppNavKeys`. No About screen exists yet, so Task 4 adds a minimal one whose version row becomes the seven-tap entry to Labs in Task 5.

**Tech Stack:** Kotlin Multiplatform (Android, iOS, desktop JVM), Compose Multiplatform with Material 3 and compose resources, Navigation 3 (`NavKey`, `taggedEntry`), Koin, kotlinx.coroutines `Flow`/`StateFlow`, AndroidX DataStore behind `FeatureFlagStore`, kotlin.test with kotlinx-coroutines-test, Android Gradle Plugin (Wear OS app plus two `com.android.test` benchmark modules), shared screenshot catalog with desktop and Android screenshot baselines.

## Global Constraints

- Hardware floor: Wear OS 5+ (`:app:wear` minSdk 34); the Wear Gradle Managed Device `wearSmallRoundApi34` and the benchmark device `wearApi35` stay valid targets.
- Unflagged vs WATCH_EXPERIENCE_V2-gated work: every task in this plan is unflagged and leaves `main` shippable; the flag from Task 3 gates nothing yet, and later tracks put redesign behavior behind it.
- Logging: Napier only. Never `System.out`, `println`, or `Log.d`.
- IDs: `kotlin.uuid.Uuid` with `Uuid.random()`, never `java.util.UUID`.
- Time: `kotlinx.datetime` (the codebase's `Instant` and `Clock` come from `kotlin.time`, as `AppUpdateController.kt` shows).
- Serialization: kotlinx.serialization; every route is a `@Serializable` `NavKey` registered in `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/AppNavKeys.kt`.
- DI: Koin constructor injection; a common view model goes in a common Koin module that all three platform `coreFeatureModule`s include.
- Imports ordered kotlin.* > androidx.*/kotlinx.* > app.logdate.*, no wildcards; `ktlintFormat` has the final say on order (lexicographic, `kotlin.*` last), and the code below already matches it.
- Code style: early returns (guard clauses) and no comments that restate code.
- Copy is literal, never "we", localized through `composeResources` string resources (no hard-coded strings), with no vague metaphor verbs ("kept", "held", "carried") and no evocative names.
- Devices: emulator or Gradle Managed Device only. Never `connected*AndroidTest`, never a physical device. No task here needs a device.
- Gradle in a worktree needs `export ANDROID_HOME=$HOME/Library/Android/sdk`; every Gradle command below starts with it, and every `git commit` sets it because the pre-commit hook runs ktlint through Gradle.
- Shared (commonMain) code must also compile for iOS (`:<module>:compileKotlinIosSimulatorArm64`, plus `:<module>:compileTestKotlinIosSimulatorArm64` for new common tests); backtick test names must not contain commas on Kotlin/Native.
- ktlint max line length 140 (`./gradlew :<module>:ktlintFormat` fixes most findings); a chain of four or more calls goes one call per line.
- Run every command from the worktree root: `/Users/williecubed/Projects/TheHypertextStudio/logdate-mobile/.claude/worktrees/backup-sync-infinite-notification-dd4c3a`.
- Commit mechanics: one step writes the message file in its own command (a heredoc into `/private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/`); the next step runs `git restore --staged . && git add <exact paths> && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F <that file>` as one chain. Never `git add -A`, `git add .`, `--no-verify`, or `-m`.
- Commit messages: Conventional Commits, title 72 characters or fewer with a scope from `allowed-scopes.txt`, body explains why and wraps at 72, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. `feat`/`fix` only for user-visible changes; `chore`/`refactor`/`test` otherwise. No phase numbers (the commit-msg hook rejects "phase N"). Every message below already passes `scripts/validation/validate-commit-message.sh`.
- Line numbers were read at `ae3967205`; Task 5 gives post-Task-4 numbers where Task 4 shifted them, and within one step every number refers to the file before that step's edits. If the branch has been rebased since, find each edit site by its quoted anchor text instead.

---

## File Structure

| File | Task | Responsibility |
|---|---|---|
| `allowed-scopes.txt` | 1 | Machine-readable list of commit scopes; gains `reminders`. |
| `docs/reference/standards/commit-scopes.md` | 1 | Human scope table; gains the `reminders` row. |
| `app/wear/build.gradle.kts` | 2 | Watch app build; `minSdk` 31 → 34. |
| `benchmark/wear-macro/build.gradle.kts` | 2 | Watch macrobenchmark test APK; `minSdk` 31 → 34. |
| `benchmark/wear-baselineprofile/build.gradle.kts` | 2 | Watch baseline profile generator; `minSdk` 31 → 34. |
| `app/wear/src/main/kotlin/app/logdate/wear/presentation/onboarding/OnboardingPermissionsUiState.kt` | 2 | Onboarding permission request; loses the API 33 branch. |
| `app/wear/src/test/kotlin/app/logdate/wear/presentation/onboarding/OnboardingPermissionsUiStateTest.kt` | 2 | Tests the request without SDK parameters. |
| `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearAudioPlaybackBackgroundE2ETest.kt` | 2 | Drops the now-dead API 33 check before granting notifications. |
| `app/wear/README.md` | 2 | States the new minimum (Wear OS 5, API 34). |
| `client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/featureflags/FeatureFlag.kt` | 3 | Gains `WATCH_EXPERIENCE_V2`. |
| `client/datastore/src/commonTest/kotlin/app/logdate/client/datastore/featureflags/FeatureFlagStoreTest.kt` | 3 | Proves the new flag starts off and can be turned on. |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsViewModel.kt` | 4 | `AboutUiState` and the view model that reads the installed version. |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsScreen.kt` | 4, 5 | About page UI; Task 5 makes the version row count taps. |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/VersionTapCounter.kt` | 5 | Counts version taps; the seventh opens Labs. |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsViewModel.kt` | 5 | `LabsFlag`, `LabsUiState`, `labsFlags()` and the view model over `FeatureFlagStore`. |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsFlagCopy.kt` | 5 | Title and description resources for each flag Labs offers. |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsScreen.kt` | 5 | Labs page UI. |
| `client/feature/core/src/commonMain/composeResources/values/strings_about.xml` | 4 | About copy. |
| `client/feature/core/src/commonMain/composeResources/values/strings_labs.xml` | 5 | Labs copy. |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/di/AboutSettingsModule.kt` | 4, 5 | Koin module for the About and Labs view models. |
| `client/feature/core/src/{android,desktop,ios}Main/kotlin/app/logdate/feature/core/di/CoreFeatureModule.*.kt` | 4 | Include `aboutSettingsModule` on every platform. |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/SettingsRoute.kt` | 4, 5 | `AboutSettingsRoute`, `LabsSettingsRoute`. |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/SettingsEntries.kt` | 4, 5 | Registers both entries and the overview's About row. |
| `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/SettingsOverviewScreen.kt` | 4 | New "App" group with the About row, in both layouts. |
| `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/AppNavKeys.kt` | 4, 5 | Serializer registration for both routes. |
| `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsViewModelTest.kt` | 4 | Version and failure tests. |
| `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/about/VersionTapCounterTest.kt` | 5 | Tap counter tests. |
| `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsViewModelTest.kt` | 5 | Flag listing, toggling and copy tests. |
| `client/screenshot-scenes/build.gradle.kts` | 5 | Adds the datastore dependency the Labs scene needs for `FeatureFlag`. |
| `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt` | 4, 5 | `about-settings` and `labs-settings` scenes. |
| `app/compose-main/src/desktopTest/reference/*.png` | 4, 5 | Desktop baselines for the new scenes (and the overview, if its render changes). |
| `app/android-main/src/screenshotTestDebug/reference/.../*.png` | 4 | Android overview baselines, if their render changes. |
| `client/feature/core/README.md` | 4, 5 | Settings System list gains the two pages. |

## Scope Notes

- **No About screen exists yet.** At `ae3967205` there is no About screen or route. The app version appears only in the update status line of `AdvancedSettingsScreen.kt:147-153`, and nothing navigates to `AdvancedSettingsRoute`: commit `1ecab287f` removed its row from the overview. Task 4 therefore adds a minimal About page (one version row) at the end of Settings, and Task 5 makes that row the entry to Labs, as the spec's Defaults describe ("opened by tapping the version seven times in About"). If reviewers would rather revive the Advanced page, decide that before Task 4 starts.
- **Mirroring the flag to the watch is not in this plan.** A later Track A task ("C. Foundations: flag mirroring") sends `WATCH_EXPERIENCE_V2` to the watch. Until then the Labs switch changes this phone only, and its description says so ("Applies to this phone only."); the mirroring task updates `labs_watch_experience_description` in `strings_labs.xml` when the watch starts reading the value.
- **Landing the dogfood branch on main is not in this plan.** The spec lists it with these prerequisites, but it is a rebase, not code. Local `main` (`0d94e087a`) has seven commits this branch lacks. Of the files this plan edits, they touch only the three platform `CoreFeatureModule` files (device approval wiring), so after that rebase find the `includes(devicesModule)` anchor there rather than trusting the line numbers.
- **Nothing reads the flag yet.** Tracks A to D add the branches that read `WATCH_EXPERIENCE_V2`.
- **Labs on iOS and desktop.** Settings entries are shared, so About and Labs exist on every platform. The watch flag does nothing on iOS or desktop; Labs has no row in Settings, so that is left as is.
- **Not included:** book-posture audit previews (`A1xx_*BookPosture` in `AdaptiveSettingsBookPostureScreenshots.kt`) for the two new pages. The shared catalog scenes cover them on desktop.

---

### Task 1: Add the `reminders` commit scope

**Files:**
- Modify: `allowed-scopes.txt:24` (insert after)
- Modify: `docs/reference/standards/commit-scopes.md:27` (insert after)
- Test: `scripts/validation/validate-commit-scope.sh` (existing script, run as the check)

**Interfaces:**
- Consumes: nothing.
- Produces: `reminders` is a valid scope for `scripts/validation/validate-commit-scope.sh` and therefore for the commit-msg hook. Track C commits use `feat(reminders): …`.

- [ ] **Step 1: Run the scope check and watch it fail**

Run: `./scripts/validation/validate-commit-scope.sh reminders; echo "exit=$?"`
Expected: FAIL. Output contains `ERROR: Invalid commit scope: 'reminders'` and `No similar scopes found.`, then `exit=1`.

- [ ] **Step 2: Add the scope to `allowed-scopes.txt`**

The feature scopes end at line 24 (`streaks  # Journaling streak (campfire) tracking and display`). Insert one line after it, so the block reads:

```text
android-widget  # Home screen widgets (Memories, On This Day)
streaks  # Journaling streak (campfire) tracking and display
reminders  # Moment-aware reminders on the phone and the watch: triggers, limits, delivery
```

- [ ] **Step 3: Add the row to `docs/reference/standards/commit-scopes.md`**

The Feature Scopes table ends at line 27. Insert one row after it, so the end of the table reads:

```markdown
| `client/feature/android-widgets/` | `android-widget` |
| `client/domain/` (streak), streak UI in any feature | `streaks` |
| `client/domain/` (reminders), reminder settings and delivery on the phone or the watch | `reminders` |
```

- [ ] **Step 4: Run the scope check and watch it pass**

Run: `./scripts/validation/validate-commit-scope.sh reminders; echo "exit=$?"`
Expected: PASS. No output except `exit=0`.

- [ ] **Step 5: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-reminders-scope.txt <<'EOF'
chore(standards): add the reminders commit scope

Moment-aware reminders replace the fixed morning and evening prompts
on both the phone and the watch. The work spans the reminder engine,
the phone's settings and the watch app, so it gets its own scope
rather than borrowing settings or wear.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 6: Commit**

```bash
git restore --staged . && git add allowed-scopes.txt docs/reference/standards/commit-scopes.md && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-reminders-scope.txt
```

Expected: the hooks pass and git prints `[claude/wearos-android-ux-redesign-081dc0 <sha>] chore(standards): add the reminders commit scope`.

- [ ] **Step 7: Check the commit contents**

Run: `git show --stat --format='%s' HEAD`
Expected: the subject line, then exactly `allowed-scopes.txt` and `docs/reference/standards/commit-scopes.md`, 2 insertions.

---

### Task 2: Require Wear OS 5 (`chore(wear): require Wear OS 5`)

**Files:**
- Modify: `app/wear/build.gradle.kts:34`
- Modify: `benchmark/wear-macro/build.gradle.kts:17`
- Modify: `benchmark/wear-baselineprofile/build.gradle.kts:18`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/presentation/onboarding/OnboardingPermissionsUiState.kt:4` and `:33-45`
- Modify: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearAudioPlaybackBackgroundE2ETest.kt:11`, `:54`, `:198-204`
- Modify: `app/wear/README.md:6` and `:32`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/presentation/onboarding/OnboardingPermissionsUiStateTest.kt:54-73`

**Interfaces:**
- Consumes: nothing.
- Produces: `:app:wear` `minSdk = 34`. `internal fun onboardingPermissionRequest(): Array<String>` replaces `onboardingPermissionRequest(sdkInt: Int = Build.VERSION.SDK_INT)`; its only caller, `WearOnboardingScreen.kt:284` (`permissionLauncher.launch(onboardingPermissionRequest())`), compiles unchanged.

- [ ] **Step 1: Rewrite the SDK-specific tests**

In `OnboardingPermissionsUiStateTest.kt`, replace lines 54-73 (the last three tests and the class's closing brace) with:

```kotlin
    @Test
    fun `permission request covers mic and location`() {
        val request = onboardingPermissionRequest().toSet()

        assertTrue(Manifest.permission.RECORD_AUDIO in request)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in request)
        assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in request)
    }

    @Test
    fun `permission request always asks for notifications`() {
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in onboardingPermissionRequest())
    }
}
```

The file's imports stay as they are: `assertFalse` is still used by the first four tests.

- [ ] **Step 2: Run the test and watch it fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.presentation.onboarding.OnboardingPermissionsUiStateTest' --console=plain`
Expected: FAIL. `OnboardingPermissionsUiStateTest > permission request always asks for notifications FAILED` with `java.lang.AssertionError: Expected value to be true.` The default argument is `Build.VERSION.SDK_INT`, which is `0` in local unit tests, so the API 33 branch leaves notifications out. The other five tests pass.

- [ ] **Step 3: Remove the API 33 branch**

In `OnboardingPermissionsUiState.kt`, delete line 4:

```kotlin
import android.os.Build
```

Then replace lines 33-45:

```kotlin
/**
 * The permissions the onboarding Allow button asks for in one prompt sequence.
 *
 * Notifications are included from Android 13, where the system hides the recording notification
 * until the user allows them. Neither notifications nor location gate onboarding.
 */
internal fun onboardingPermissionRequest(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
    buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
```

with:

```kotlin
/**
 * The permissions the onboarding Allow button asks for in one prompt sequence.
 *
 * Every supported Wear OS version hides the recording notification until the user allows
 * notifications, so they are part of the request. Neither notifications nor location gate
 * onboarding.
 */
internal fun onboardingPermissionRequest(): Array<String> =
    arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.presentation.onboarding.OnboardingPermissionsUiStateTest' --console=plain`
Expected: PASS, 6 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 5: Raise the watch app's minimum SDK**

In `app/wear/build.gradle.kts`, line 34 inside `defaultConfig`, change:

```kotlin
        minSdk = 31
```

to:

```kotlin
        minSdk = 34
```

- [ ] **Step 6: Raise both benchmark modules to match**

In `benchmark/wear-macro/build.gradle.kts` line 17 and `benchmark/wear-baselineprofile/build.gradle.kts` line 18, change `        minSdk = 31` to:

```kotlin
        minSdk = 34
```

Their managed device (`wearApi35`, API 35) still meets the new floor.

- [ ] **Step 7: Remove the dead API 33 check from the playback end-to-end test**

In `WearAudioPlaybackBackgroundE2ETest.kt`, delete line 11:

```kotlin
import android.os.Build
```

Change line 54 from `        grantNotificationPermissionIfNeeded()` to:

```kotlin
        grantNotificationPermission()
```

Replace lines 198-204:

```kotlin
    private fun grantNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }
```

with:

```kotlin
    private fun grantNotificationPermission() {
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }
```

`Build` has no other use in that file. Do not run this instrumented test; compiling it (Step 9) is enough.

- [ ] **Step 8: Update the watch README**

In `app/wear/README.md`, change line 6 from:

```markdown
**Min SDK**: 31 (Wear OS 3+) | **Target SDK**: 37 | **Compile SDK**: 37
```

to:

```markdown
**Min SDK**: 34 (Wear OS 5+) | **Target SDK**: 37 | **Compile SDK**: 37
```

and line 32 from:

```markdown
- Android Studio with Wear OS system images (API 33+ round)
```

to:

```markdown
- Android Studio with Wear OS system images (API 34+ round)
```

No other doc in the repository states a watch minimum (`grep -rn "Wear OS 3\|API 33" docs app/wear --include='*.md'` finds only these two lines).

- [ ] **Step 9: Build the watch app, its unit tests and its instrumented test sources**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:assembleDebug :app:wear:compileDebugAndroidTestKotlin :app:wear:ktlintCheck --console=plain`
Expected: PASS, `BUILD SUCCESSFUL`.

- [ ] **Step 10: Confirm the APK's minimum SDK**

Run: `$HOME/Library/Android/sdk/cmdline-tools/latest/bin/apkanalyzer manifest min-sdk app/wear/build/outputs/apk/debug/wear-debug.apk`
Expected: `34`

- [ ] **Step 11: Build both benchmark modules**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :benchmark:wear-macro:assembleBenchmark :benchmark:wear-baselineprofile:assembleBenchmarkBenchmark :benchmark:wear-baselineprofile:assembleNonMinifiedBenchmark :benchmark:wear-macro:ktlintCheck :benchmark:wear-baselineprofile:ktlintCheck --console=plain`
Expected: PASS, `BUILD SUCCESSFUL`. `wear-macro` declares a `benchmark` build type (`create("benchmark")` at `benchmark/wear-macro/build.gradle.kts:25`), which is where its `assembleBenchmark` comes from. In `wear-baselineprofile` the baseline-profile plugin adds variants (`benchmarkBenchmark`, `nonMinifiedBenchmark`, two release ones), so a bare `assembleBenchmark` is ambiguous there; build the two benchmark variants by name.

- [ ] **Step 12: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-require-wear-os-5.txt <<'EOF'
chore(wear): require Wear OS 5

The redesigned watch app is built for Wear OS 5 and newer, so the
watch app and the two benchmark modules that drive it now require
API 34. Watches on older versions of Wear OS stop receiving updates
from Play, and the watch README states the new minimum.

Every supported watch asks before it shows notifications, so
onboarding always requests the notification permission instead of
checking for Android 13 first. The playback end-to-end test drops the
same check.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 13: Commit**

```bash
git restore --staged . && git add app/wear/build.gradle.kts benchmark/wear-macro/build.gradle.kts benchmark/wear-baselineprofile/build.gradle.kts app/wear/src/main/kotlin/app/logdate/wear/presentation/onboarding/OnboardingPermissionsUiState.kt app/wear/src/test/kotlin/app/logdate/wear/presentation/onboarding/OnboardingPermissionsUiStateTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearAudioPlaybackBackgroundE2ETest.kt app/wear/README.md && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-require-wear-os-5.txt
```

Expected: the pre-commit hook prints `Running ktlint on affected modules: :app:wear :benchmark:wear-macro :benchmark:wear-baselineprofile` (in some order), passes, and git prints `[claude/wearos-android-ux-redesign-081dc0 <sha>] chore(wear): require Wear OS 5`.

- [ ] **Step 14: Check the commit contents**

Run: `git show --stat --format='%s' HEAD`
Expected: exactly the seven files staged in Step 13.

---

### Task 3: Add `FeatureFlag.WATCH_EXPERIENCE_V2`

**Files:**
- Modify: `client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/featureflags/FeatureFlag.kt:54-55` (insert between the last entry and `;`)
- Test: `client/datastore/src/commonTest/kotlin/app/logdate/client/datastore/featureflags/FeatureFlagStoreTest.kt:14-15` (import) and `:116-117` (new test)

**Interfaces:**
- Consumes: `FeatureFlagStore.isEnabled(flag: FeatureFlag): Boolean`, `FeatureFlagStore.setEnabled(flag: FeatureFlag, enabled: Boolean)`, `FeatureFlag.forKey(key: String): FeatureFlag?` (all existing).
- Produces: `FeatureFlag.WATCH_EXPERIENCE_V2` with `key = "watch_experience_v2_enabled"`, `defaultEnabled = false`, and the default `availableForLaunch = true`, so `DataStoreFeatureFlagStore.observe` (`FeatureFlagStore.kt:47-50`) lets a stored `true` through in release builds. Task 5 and Tracks A to D read it.

The only tests that enumerate flags are in `FeatureFlagStoreTest.kt`: `every flag owns a distinct key` (line 112) walks `FeatureFlag.entries` and covers the new key without changes; no other test or `when` expression in the repository lists the entries (`grep -rn "FeatureFlag.entries" --include='*.kt' .` finds only line 113 and `FeatureFlag.forKey`).

- [ ] **Step 1: Write the failing test**

In `FeatureFlagStoreTest.kt`, add the import between line 14 (`import kotlin.test.assertFalse`) and line 15 (`import kotlin.test.assertNull`):

```kotlin
import kotlin.test.assertNotNull
```

Then add this test after the `every flag owns a distinct key` test (after line 116, before the class's closing brace on line 117):

```kotlin

    @Test
    fun `the watch experience flag starts off and can be turned on`() =
        runTest {
            val flag = assertNotNull(FeatureFlag.forKey("watch_experience_v2_enabled"))

            assertFalse(store.isEnabled(flag))

            store.setEnabled(flag, enabled = true)

            assertTrue(store.isEnabled(flag), "testers turn this flag on in release builds")
        }
```

The test finds the flag by its stored key, so it compiles before the entry exists and fails on behavior.

- [ ] **Step 2: Run the test and watch it fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:logdate-datastore:jvmTest --tests 'app.logdate.client.datastore.featureflags.FeatureFlagStoreTest' --console=plain`
Expected: FAIL. `FeatureFlagStoreTest > the watch experience flag starts off and can be turned on FAILED` with `AssertionError: Expected value to be not null.` The other nine tests pass. (The datastore project is `:client:logdate-datastore`; its directory is `client/datastore`, per `settings.gradle.kts:67-68`.)

- [ ] **Step 3: Add the flag**

In `FeatureFlag.kt`, insert between line 54 (`HUMAN_LOCATION_HISTORY(key = "human_location_history_enabled", defaultEnabled = true),`) and line 55 (`;`):

```kotlin

    /**
     * The redesigned watch experience and its phone companion: the watch app, and the phone
     * screens that set up and manage the watch.
     *
     * Off by default while the redesign is built. Once it ships to everyone, delete this flag
     * together with every branch that reads it and the watch screens it replaces.
     */
    WATCH_EXPERIENCE_V2(key = "watch_experience_v2_enabled", defaultEnabled = false),
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:logdate-datastore:jvmTest --tests 'app.logdate.client.datastore.featureflags.FeatureFlagStoreTest' --console=plain`
Expected: PASS, 10 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 5: Compile for iOS and lint**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:logdate-datastore:compileKotlinIosSimulatorArm64 :client:logdate-datastore:compileTestKotlinIosSimulatorArm64 :client:logdate-datastore:ktlintCheck --console=plain`
Expected: PASS, `BUILD SUCCESSFUL`.

- [ ] **Step 6: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-watch-experience-flag.txt <<'EOF'
chore(wear): add the WATCH_EXPERIENCE_V2 feature flag

The watch app redesign and the phone screens that set up the watch
ship behind one flag, so the work can land on main while the current
watch experience stays the default. The flag is off by default, can
be turned on in release builds, and nothing reads it yet.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/featureflags/FeatureFlag.kt client/datastore/src/commonTest/kotlin/app/logdate/client/datastore/featureflags/FeatureFlagStoreTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-watch-experience-flag.txt
```

Expected: the hook runs ktlint on `:client:logdate-datastore`, passes, and git prints the `chore(wear): add the WATCH_EXPERIENCE_V2 feature flag` summary.

- [ ] **Step 8: Check the commit contents**

Run: `git show --stat --format='%s' HEAD`
Expected: exactly `FeatureFlag.kt` and `FeatureFlagStoreTest.kt`.

---

### Task 4: About page with the app version

**Files:**
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsViewModel.kt`
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsScreen.kt`
- Create: `client/feature/core/src/commonMain/composeResources/values/strings_about.xml`
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/di/AboutSettingsModule.kt`
- Modify: `client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/di/CoreFeatureModule.android.kt:88`
- Modify: `client/feature/core/src/desktopMain/kotlin/app/logdate/feature/core/di/CoreFeatureModule.desktop.kt:79`
- Modify: `client/feature/core/src/iosMain/kotlin/app/logdate/feature/core/di/CoreFeatureModule.ios.kt:80`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/SettingsRoute.kt:44-45`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/SettingsEntries.kt:42`, `:108`, `:158-160`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/SettingsOverviewScreen.kt:21`, `:59`, `:86`, `:112`, `:136-138`, `:167`, `:204-205`, `:360-369`, `:530-541`
- Modify: `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/AppNavKeys.kt:11`, `:135`
- Modify: `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt:82`, `:212`, `:1135-1148`
- Modify: `client/feature/core/README.md:45`
- Test: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsViewModelTest.kt`
- Baselines: `app/compose-main/src/desktopTest/reference/about-settings--{phone,phone-dark,phone-landscape,tablet}.png` (new); the four `settings-overview--*.png` desktop baselines and nine Android overview baselines (listed in Step 15) only if their render changes.

**Interfaces:**
- Consumes: `app.logdate.client.device.AppInfoProvider.getAppInfo(): AppInfo` (throws `AppInfoRetrievalException`), `AppInfo(versionName: String, versionCode: Int, packageName: String)`; bound on every platform (`client/device/src/androidMain/kotlin/app/logdate/client/device/di/DeviceInstanceModule.android.kt:63-65` on Android, `BuildConfigAppInfoProvider` on desktop, the iOS `DeviceModule.kt`). `SettingsScaffold`, `MaterialContainer`, `SimpleSettingsItem`, `SettingsSection`, `SettingsNavigationItem` from `client/ui`, and `FoldableBookLayout` from `app.logdate.ui.adaptive`.
- Produces (Task 5 relies on these exact names):
  - `@Serializable data object AboutSettingsRoute : NavKey` in `app.logdate.feature.core.settings.navigation`
  - `sealed interface AboutUiState` with `data class Version(val versionName: String, val versionCode: Int)` and `data object VersionUnavailable`
  - `class AboutSettingsViewModel(appInfoProvider: AppInfoProvider) : ViewModel` with `val uiState: AboutUiState`
  - `@Composable fun AboutSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AboutSettingsViewModel = koinViewModel())`
  - `@Composable fun AboutSettingsContent(uiState: AboutUiState, onBack: () -> Unit, modifier: Modifier = Modifier)`
  - `val aboutSettingsModule: Module` in `app.logdate.feature.core.di`
  - `SettingsOverviewScreen(…, onNavigateToAbout: () -> Unit = {}, …)` and `SettingsOverviewContent(…, onNavigateToAbout: () -> Unit = {}, …)`
  - `SharedScreenshotSceneId.AboutSettings("about-settings")`

- [ ] **Step 1: Write the failing test**

Create `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsViewModelTest.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.about

import app.logdate.client.device.AppInfo
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.AppInfoRetrievalException
import kotlin.test.Test
import kotlin.test.assertEquals

class AboutSettingsViewModelTest {
    @Test
    fun `the page shows the installed version name and build number`() {
        val provider = FixedAppInfoProvider(AppInfo(versionName = "1.4.0", versionCode = 1402, packageName = "studio.hypertext.logdate"))

        val viewModel = AboutSettingsViewModel(provider)

        assertEquals(AboutUiState.Version(versionName = "1.4.0", versionCode = 1402), viewModel.uiState)
    }

    @Test
    fun `the page says the version is unavailable when the platform cannot report it`() {
        val viewModel = AboutSettingsViewModel(FailingAppInfoProvider())

        assertEquals(AboutUiState.VersionUnavailable, viewModel.uiState)
    }
}

private class FixedAppInfoProvider(
    private val appInfo: AppInfo,
) : AppInfoProvider {
    override fun getAppInfo(): AppInfo = appInfo
}

private class FailingAppInfoProvider : AppInfoProvider {
    override fun getAppInfo(): AppInfo = throw AppInfoRetrievalException("Package info unavailable")
}
```

- [ ] **Step 2: Add a compile-only stub so the test fails on behavior**

Create `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsViewModel.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.about

import androidx.lifecycle.ViewModel
import app.logdate.client.device.AppInfoProvider

/** What the About page shows about this installation. */
sealed interface AboutUiState {
    /** The installed version name and build number. */
    data class Version(
        val versionName: String,
        val versionCode: Int,
    ) : AboutUiState

    /** The platform could not report the installed version. */
    data object VersionUnavailable : AboutUiState
}

/** Reads the installed version of LogDate for the About page. */
class AboutSettingsViewModel(
    appInfoProvider: AppInfoProvider,
) : ViewModel() {
    val uiState: AboutUiState = readVersion(appInfoProvider)
}

private fun readVersion(appInfoProvider: AppInfoProvider): AboutUiState = AboutUiState.VersionUnavailable
```

- [ ] **Step 3: Run the test and watch it fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.about.AboutSettingsViewModelTest' --console=plain`
Expected: FAIL. `the page shows the installed version name and build number FAILED` with `AssertionError: Expected <Version(versionName=1.4.0, versionCode=1402)>, actual <VersionUnavailable>.` The unavailable test passes.

- [ ] **Step 4: Read the version for real**

In `AboutSettingsViewModel.kt`, add these imports below `import app.logdate.client.device.AppInfoProvider`:

```kotlin
import app.logdate.client.device.AppInfoRetrievalException
import io.github.aakira.napier.Napier
```

and replace the stub function:

```kotlin
private fun readVersion(appInfoProvider: AppInfoProvider): AboutUiState = AboutUiState.VersionUnavailable
```

with:

```kotlin
private fun readVersion(appInfoProvider: AppInfoProvider): AboutUiState =
    try {
        val appInfo = appInfoProvider.getAppInfo()
        AboutUiState.Version(versionName = appInfo.versionName, versionCode = appInfo.versionCode)
    } catch (e: AppInfoRetrievalException) {
        Napier.e("The installed app version could not be read", e)
        AboutUiState.VersionUnavailable
    }
```

- [ ] **Step 5: Run the test and watch it pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.about.AboutSettingsViewModelTest' --console=plain`
Expected: PASS, 2 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 6: Add the About strings**

Create `client/feature/core/src/commonMain/composeResources/values/strings_about.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- About -->
    <string name="about_settings_title">About</string>
    <string name="about_settings_description">Installed version</string>
    <string name="about_version_title">Version</string>
    <string name="about_version_value">%1$s (%2$d)</string>
    <string name="about_version_unavailable">Not available</string>
</resources>
```

`settings_group_app` ("App", `strings.xml:30`) already exists and has had no use since `1ecab287f`; the overview reuses it in Step 11.

- [ ] **Step 7: Build the About screen**

Create `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsScreen.kt`:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.about_settings_title
import logdate.client.feature.core.generated.resources.about_version_title
import logdate.client.feature.core.generated.resources.about_version_unavailable
import logdate.client.feature.core.generated.resources.about_version_value
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The About page: the installed version of LogDate. */
@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutSettingsViewModel = koinViewModel(),
) {
    AboutSettingsContent(
        uiState = viewModel.uiState,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun AboutSettingsContent(
    uiState: AboutUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            Text(
                text = stringResource(Res.string.about_settings_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(Spacing.lg),
            )
        },
        endPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
            ) {
                VersionRow(
                    uiState = uiState,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.about_settings_title),
                onBack = onBack,
            ) {
                item {
                    VersionRow(
                        uiState = uiState,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        },
    )
}

@Composable
private fun VersionRow(
    uiState: AboutUiState,
    modifier: Modifier = Modifier,
) {
    val version =
        when (uiState) {
            is AboutUiState.Version -> stringResource(Res.string.about_version_value, uiState.versionName, uiState.versionCode)
            AboutUiState.VersionUnavailable -> stringResource(Res.string.about_version_unavailable)
        }
    MaterialContainer(modifier = modifier) {
        SimpleSettingsItem(
            title = stringResource(Res.string.about_version_title),
            description = version,
            action = {},
        )
    }
}
```

- [ ] **Step 8: Register the view model with Koin on every platform**

Create `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/di/AboutSettingsModule.kt`:

```kotlin
package app.logdate.feature.core.di

import app.logdate.feature.core.settings.ui.about.AboutSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Dependency injection for the About page.
 */
val aboutSettingsModule: Module =
    module {
        viewModel { AboutSettingsViewModel(get()) }
    }
```

Then, in each platform module, add one line directly after `        includes(devicesModule)` (same package, so no import):

- `CoreFeatureModule.android.kt` line 88
- `CoreFeatureModule.desktop.kt` line 79
- `CoreFeatureModule.ios.kt` line 80

```kotlin
        includes(aboutSettingsModule)
```

- [ ] **Step 9: Add the route and its serializer registration**

In `SettingsRoute.kt`, after lines 44-45:

```kotlin
@Serializable
data object AdvancedSettingsRoute : NavKey
```

add:

```kotlin

@Serializable
data object AboutSettingsRoute : NavKey
```

In `AppNavKeys.kt`, add the import directly above line 11 (`import app.logdate.feature.core.settings.navigation.AccountSettingsRoute`):

```kotlin
import app.logdate.feature.core.settings.navigation.AboutSettingsRoute
```

and directly after line 135 (`                    subclass(AdvancedSettingsRoute::class, AdvancedSettingsRoute.serializer())`) add:

```kotlin
                    subclass(AboutSettingsRoute::class, AboutSettingsRoute.serializer())
```

- [ ] **Step 10: Register the entry and the overview row's destination**

In `SettingsEntries.kt`, add the import directly after line 42 (`import app.logdate.feature.core.settings.ui.VoiceNotesSettingsScreen`):

```kotlin
import app.logdate.feature.core.settings.ui.about.AboutSettingsScreen
```

In the `SettingsOverviewScreen(` call, directly after line 108 (`            onNavigateToExport = { onNavigateTo(ExportSettingsRoute) },`) add:

```kotlin
            onNavigateToAbout = { onNavigateTo(AboutSettingsRoute) },
```

After the Advanced entry (lines 158-160):

```kotlin
    taggedEntry<AdvancedSettingsRoute> {
        AdvancedSettingsScreen(onBack = onBack)
    }
```

add:

```kotlin
    taggedEntry<AboutSettingsRoute> {
        AboutSettingsScreen(onBack = onBack)
    }
```

`LogDateNavDisplay.kt:341` already passes `onNavigateTo = { route -> backStack.add(route) }`, so no other wiring is needed.

- [ ] **Step 11: Add the App group with the About row to the overview**

In `SettingsOverviewScreen.kt`:

1. After line 21 (`import androidx.compose.material.icons.filled.GraphicEq`) add `import androidx.compose.material.icons.filled.Info`.
2. After line 59 (`import logdate.client.feature.core.generated.resources.Res`) add:

```kotlin
import logdate.client.feature.core.generated.resources.about_settings_description
import logdate.client.feature.core.generated.resources.about_settings_title
```

3. After line 86 (`import logdate.client.feature.core.generated.resources.screen_title_settings`) add `import logdate.client.feature.core.generated.resources.settings_group_app`.
4. Line 112: change ` * Groups: Personal / Privacy & Security / Data & Storage` to ` * Groups: Personal / Privacy & Security / Data & Storage / App`.
5. In `SettingsOverviewScreen`'s parameters, replace lines 136-138:

```kotlin
    onNavigateToSignIn: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsOverviewViewModel = koinViewModel(),
```

with:

```kotlin
    onNavigateToSignIn: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsOverviewViewModel = koinViewModel(),
```

6. In the `SettingsOverviewContent(` call inside it, directly after line 167 (`        onNavigateToSignIn = onNavigateToSignIn,`) add `        onNavigateToAbout = onNavigateToAbout,`.
7. In `SettingsOverviewContent`'s parameters, replace lines 204-205:

```kotlin
    onNavigateToLibrarySettings: () -> Unit = {},
    userProfile: UserProfile,
```

with:

```kotlin
    onNavigateToLibrarySettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    userProfile: UserProfile,
```

8. Book-posture end pane, lines 360-369. Replace:

```kotlin
                    SettingsNavigationItem(
                        title = stringResource(Res.string.reset),
                        description = stringResource(Res.string.reset_description),
                        icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                        onClick = onNavigateToReset,
                    )
                }
            }
        },
        standardContent = {
```

with:

```kotlin
                    SettingsNavigationItem(
                        title = stringResource(Res.string.reset),
                        description = stringResource(Res.string.reset_description),
                        icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                        onClick = onNavigateToReset,
                    )
                }

                SettingsSection(
                    title = stringResource(Res.string.settings_group_app),
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    SettingsNavigationItem(
                        title = stringResource(Res.string.about_settings_title),
                        description = stringResource(Res.string.about_settings_description),
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = onNavigateToAbout,
                    )
                }
            }
        },
        standardContent = {
```

9. Standard layout, lines 530-541. Replace:

```kotlin
                        SettingsNavigationItem(
                            title = stringResource(Res.string.reset),
                            description = stringResource(Res.string.reset_description),
                            icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                            onClick = onNavigateToReset,
                        )
                    }
                }
            }
        },
    )
}
```

with:

```kotlin
                        SettingsNavigationItem(
                            title = stringResource(Res.string.reset),
                            description = stringResource(Res.string.reset_description),
                            icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                            onClick = onNavigateToReset,
                        )
                    }
                }

                item {
                    SettingsSection(
                        title = stringResource(Res.string.settings_group_app),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        SettingsNavigationItem(
                            title = stringResource(Res.string.about_settings_title),
                            description = stringResource(Res.string.about_settings_description),
                            icon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = onNavigateToAbout,
                        )
                    }
                }
            }
        },
    )
}
```

The new parameter has a default, so the existing callers (`SharedScreenshotCatalog.kt:677`, the Android screenshot files, `SettingsOverviewNotificationsRowTest.kt` and the two previews) compile unchanged and render the row.

- [ ] **Step 12: Add the About scene to the shared screenshot catalog**

In `SharedScreenshotCatalog.kt`:

1. After line 82 (`import app.logdate.feature.core.settings.ui.VoiceNotesSettingsViewModel`) add:

```kotlin
import app.logdate.feature.core.settings.ui.about.AboutSettingsContent
import app.logdate.feature.core.settings.ui.about.AboutUiState
```

2. After line 212 (`    AdvancedSettings("advanced-settings"),`) add `    AboutSettings("about-settings"),`.
3. The Advanced scene ends at lines 1145-1148. Replace:

```kotlin
                    onCheckForAppUpdates = {},
                    onCompleteAppUpdate = {},
                )
            },
```

with:

```kotlin
                    onCheckForAppUpdates = {},
                    onCompleteAppUpdate = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.AboutSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                AboutSettingsContent(
                    uiState = AboutUiState.Version(versionName = "1.0.0", versionCode = 100),
                    onBack = {},
                )
            },
```

- [ ] **Step 13: Note the page in the module README**

In `client/feature/core/README.md`, after line 45 (``- `DevicesScreen.kt` - Connected device management``) add:

```markdown
- `settings/ui/about/` - About page: the installed version
```

- [ ] **Step 14: Compile every target, run the module's tests and lint**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest :client:feature:core:compileAndroidMain :client:feature:core:compileKotlinIosSimulatorArm64 :client:feature:core:compileTestKotlinIosSimulatorArm64 :client:feature:core:ktlintCheck :client:feature:core:detekt --console=plain`
Expected: PASS, `BUILD SUCCESSFUL`.

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:screenshot-scenes:desktopTest :client:screenshot-scenes:compileAndroidMain :client:screenshot-scenes:compileKotlinIosSimulatorArm64 :client:screenshot-scenes:ktlintCheck :app:compose-main:compileKotlinDesktop :app:compose-main:compileKotlinIosSimulatorArm64 :app:compose-main:ktlintCheck :app:android-main:assembleDebug --console=plain`
Expected: PASS, `BUILD SUCCESSFUL`. `SharedScreenshotCatalogTest` confirms the new scene id and baseline names are unique.

- [ ] **Step 15: Record the screenshot baselines**

These render through a real window, so `LOGDATE_REQUIRE_DESKTOP_SCREENSHOTS=true` makes a machine without a display fail instead of skipping.

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && LOGDATE_REQUIRE_DESKTOP_SCREENSHOTS=true ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=about-settings :app:compose-main:updateDesktopScreenshotTest --tests 'app.logdate.screenshots.DesktopScreenshotTest' --console=plain`
Expected: `BUILD SUCCESSFUL`, and four new files: `app/compose-main/src/desktopTest/reference/about-settings--phone.png`, `about-settings--phone-dark.png`, `about-settings--phone-landscape.png`, `about-settings--tablet.png`.

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && LOGDATE_REQUIRE_DESKTOP_SCREENSHOTS=true ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=settings-overview :app:compose-main:updateDesktopScreenshotTest --tests 'app.logdate.screenshots.DesktopScreenshotTest' --console=plain`
Expected: `BUILD SUCCESSFUL`.

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:updateDebugScreenshotTest --tests '*SettingsOverview*' --tests '*SettingsListDetail*' --console=plain --no-build-cache`
Expected: `BUILD SUCCESSFUL`. The filters match exactly the six previews that render the overview: `SettingsOverview`, `SettingsOverview_Dark`, `SettingsListDetail_Landscape_Account`, `SettingsListDetail_Tablet_Account`, `S01_SettingsOverview` and `A09_SettingsOverviewBookPosture`.

Run: `git status --short app/compose-main/src/desktopTest/reference app/android-main/src/screenshotTestDebug`
Expected: the four `about-settings--*.png` files as untracked (`??`), plus, only where the new App group falls inside the rendered area, modified (`M`) copies of the overview baselines listed in Step 18. Nothing else.

Open each new or modified PNG. The About files show the "About" title and one "Version" row reading "1.0.0 (100)". In a modified overview file, the only difference is the "App" group with its "About · Installed version" row after Data & Storage. Restore any file that changed without a visible difference with `git restore <path>`.

- [ ] **Step 16: Validate the baselines**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && LOGDATE_REQUIRE_DESKTOP_SCREENSHOTS=true ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=settings :app:compose-main:desktopTest --tests 'app.logdate.screenshots.DesktopScreenshotTest' --console=plain`
Expected: PASS for every settings scene, `BUILD SUCCESSFUL`.

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:validateDebugScreenshotTest --tests '*SettingsOverview*' --tests '*SettingsListDetail*' --console=plain --no-build-cache`
Expected: PASS, `BUILD SUCCESSFUL`.

- [ ] **Step 17: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-about-page.txt <<'EOF'
feat(settings): add an About page with the app version

Settings now ends with an About page that shows the installed version
of LogDate and its build number, so you can check which version you
have when you report a problem or confirm that an update installed.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 18: Commit**

The overview baselines are listed whether or not they changed; `git add` on an unchanged tracked file stages nothing.

```bash
git restore --staged . && git add client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsViewModel.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsScreen.kt client/feature/core/src/commonMain/composeResources/values/strings_about.xml client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/di/AboutSettingsModule.kt client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/di/CoreFeatureModule.android.kt client/feature/core/src/desktopMain/kotlin/app/logdate/feature/core/di/CoreFeatureModule.desktop.kt client/feature/core/src/iosMain/kotlin/app/logdate/feature/core/di/CoreFeatureModule.ios.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/SettingsRoute.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/SettingsEntries.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/SettingsOverviewScreen.kt app/compose-main/src/commonMain/kotlin/app/logdate/navigation/AppNavKeys.kt client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt client/feature/core/README.md client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsViewModelTest.kt app/compose-main/src/desktopTest/reference/about-settings--phone.png app/compose-main/src/desktopTest/reference/about-settings--phone-dark.png app/compose-main/src/desktopTest/reference/about-settings--phone-landscape.png app/compose-main/src/desktopTest/reference/about-settings--tablet.png app/compose-main/src/desktopTest/reference/settings-overview--phone.png app/compose-main/src/desktopTest/reference/settings-overview--phone-dark.png app/compose-main/src/desktopTest/reference/settings-overview--phone-landscape.png app/compose-main/src/desktopTest/reference/settings-overview--tablet.png app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/components/settings_account/SettingsComponentScreenshotsKt/SettingsOverview_7ea6b8b9_0.png app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/components/settings_account/SettingsComponentScreenshotsKt/SettingsOverview_Dark_99bcb0dc_0.png app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/components/settings_account/SettingsComponentScreenshotsKt/SettingsListDetail_Landscape_Account_645f60ba_0.png app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/components/settings_account/SettingsComponentScreenshotsKt/SettingsListDetail_Tablet_Account_0c03ded2_0.png app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/flows/flow08_export_import/Flow08_01_ExportImportFlowScreenshotsKt/S01_SettingsOverview_Landscape_7391e7ba_0.png "app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/flows/flow08_export_import/Flow08_01_ExportImportFlowScreenshotsKt/S01_SettingsOverview_Phone Dark_67e6f6ed_0.png" app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/flows/flow08_export_import/Flow08_01_ExportImportFlowScreenshotsKt/S01_SettingsOverview_Phone_2dd8a156_0.png app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/flows/flow08_export_import/Flow08_01_ExportImportFlowScreenshotsKt/S01_SettingsOverview_Tablet_341ed7fd_0.png "app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/audit/adaptive/AdaptiveSettingsBookPostureScreenshotsKt/A09_SettingsOverviewBookPosture_Settings overview book posture_48368a50_0.png" && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-about-page.txt
```

Expected: the hook runs ktlint on `:client:feature:core`, `:client:screenshot-scenes` and `:app:compose-main`, passes, and git prints the `feat(settings): add an About page with the app version` summary.

- [ ] **Step 19: Check the commit contents**

Run: `git show --stat --format='%s' HEAD` and then `git status --short`
Expected: the 14 source and test files, the four `about-settings--*.png` files, any overview baselines Step 15 left modified, and a clean working tree afterwards.

---

### Task 5: Hidden Labs page, opened from the About page

**Files:**
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/VersionTapCounter.kt`
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsViewModel.kt`
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsFlagCopy.kt`
- Create: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsScreen.kt`
- Create: `client/feature/core/src/commonMain/composeResources/values/strings_labs.xml`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsScreen.kt` (whole file, from Task 4)
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/di/AboutSettingsModule.kt` (whole file, from Task 4)
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/SettingsRoute.kt` (after `AboutSettingsRoute`, lines 47-48 after Task 4)
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/SettingsEntries.kt` (import after the `ui.devices.DevicesScreen` import, line 44 after Task 4; the About entry, lines 163-165 after Task 4)
- Modify: `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/AppNavKeys.kt` (import after `ExportSettingsRoute`, line 24 after Task 4; registration after the `AboutSettingsRoute` line, line 137 after Task 4)
- Modify: `client/screenshot-scenes/build.gradle.kts:57`
- Modify: `client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt` (imports at lines 14 and 87, scene id at line 215, About scene at lines 1152-1157, all after Task 4)
- Modify: `client/feature/core/README.md` (after the line Task 4 added, line 46)
- Test: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/about/VersionTapCounterTest.kt`
- Test: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsViewModelTest.kt`
- Baselines: `app/compose-main/src/desktopTest/reference/labs-settings--{phone,phone-dark,phone-landscape,tablet}.png` (new)

**Interfaces:**
- Consumes: from Task 3, `FeatureFlag.WATCH_EXPERIENCE_V2`; from Task 4, `AboutSettingsRoute`, `AboutUiState`, `AboutSettingsViewModel.uiState`, `aboutSettingsModule`, `SharedScreenshotSceneId.AboutSettings`; existing `FeatureFlagStore.observe(flag: FeatureFlag): Flow<Boolean>` and `FeatureFlagStore.setEnabled(flag: FeatureFlag, enabled: Boolean)`, bound in `commonDatastoreModule` on every platform; `ToggleSettingsItem(title, description, checked, onCheckedChange)` and `SettingsSection(title, modifier, content)` from `client/ui`.
- Produces:
  - `class VersionTapCounter(tapsToOpenLabs: Int = TAPS_TO_OPEN_LABS) { fun tap(): Boolean }` with `const val TAPS_TO_OPEN_LABS: Int = 7`
  - `@Serializable data object LabsSettingsRoute : NavKey`
  - `data class LabsFlag(val flag: FeatureFlag, val enabled: Boolean)`
  - `sealed interface LabsUiState` with `data object Loading` and `data class Ready(val flags: List<LabsFlag>)`
  - `class LabsSettingsViewModel(featureFlagStore: FeatureFlagStore, flags: List<FeatureFlag> = labsFlags()) : ViewModel` with `val uiState: StateFlow<LabsUiState>` and `fun setEnabled(flag: FeatureFlag, enabled: Boolean)`
  - `fun labsFlags(entries: List<FeatureFlag> = FeatureFlag.entries): List<FeatureFlag>`
  - `internal data class LabsFlagCopy(val title: StringResource, val description: StringResource)` and `internal fun FeatureFlag.labsCopy(): LabsFlagCopy?`
  - `@Composable fun LabsSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: LabsSettingsViewModel = koinViewModel())`
  - `@Composable fun LabsSettingsContent(uiState: LabsUiState, onBack: () -> Unit, onSetEnabled: (FeatureFlag, Boolean) -> Unit, modifier: Modifier = Modifier)`
  - `AboutSettingsScreen(onBack: () -> Unit, onOpenLabs: () -> Unit, modifier: Modifier = Modifier, viewModel: AboutSettingsViewModel = koinViewModel())` and `AboutSettingsContent(uiState: AboutUiState, onBack: () -> Unit, onVersionClick: () -> Unit, modifier: Modifier = Modifier)`
  - `SharedScreenshotSceneId.LabsSettings("labs-settings")`
  - Track A's flag-mirroring task reads the same `FeatureFlagStore` value and edits `labs_watch_experience_description`.

- [ ] **Step 1: Write the failing tap counter test**

Create `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/about/VersionTapCounterTest.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionTapCounterTest {
    @Test
    fun `six taps do not open Labs`() {
        val counter = VersionTapCounter()

        repeat(6) { assertFalse(counter.tap()) }
    }

    @Test
    fun `the seventh tap opens Labs`() {
        val counter = VersionTapCounter()
        repeat(6) { counter.tap() }

        assertTrue(counter.tap())
    }

    @Test
    fun `counting starts over once Labs opens`() {
        val counter = VersionTapCounter()
        repeat(7) { counter.tap() }

        val nextSevenTaps = List(7) { counter.tap() }

        assertEquals(List(6) { false } + true, nextSevenTaps)
    }
}
```

- [ ] **Step 2: Write the failing Labs view model test**

Create `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsViewModelTest.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.labs

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LabsSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Labs offers flags that are off by default and allowed to turn on`() {
        val offered = labsFlags()

        assertTrue(FeatureFlag.WATCH_EXPERIENCE_V2 in offered)
        assertFalse(FeatureFlag.LIBRARY in offered, "a launched feature is not offered")
        assertFalse(FeatureFlag.EVENTS in offered, "a deferred feature cannot be turned on")
    }

    @Test
    fun `every flag Labs offers has a title and description`() {
        assertTrue(labsFlags().all { flag -> flag.labsCopy() != null })
    }

    @Test
    fun `the page loads each flag with its stored value`() =
        runTest {
            val viewModel = LabsSettingsViewModel(FakeFeatureFlagStore(), flags = listOf(FeatureFlag.WATCH_EXPERIENCE_V2))
            assertEquals(LabsUiState.Loading, viewModel.uiState.value)

            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            assertEquals(
                LabsUiState.Ready(listOf(LabsFlag(FeatureFlag.WATCH_EXPERIENCE_V2, enabled = false))),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `turning a flag on stores it and shows it on`() =
        runTest {
            val store = FakeFeatureFlagStore()
            val viewModel = LabsSettingsViewModel(store, flags = listOf(FeatureFlag.WATCH_EXPERIENCE_V2))
            backgroundScope.launch { viewModel.uiState.collect { } }

            viewModel.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled = true)
            advanceUntilIdle()

            assertEquals(listOf(FeatureFlag.WATCH_EXPERIENCE_V2 to true), store.writes)
            assertEquals(
                LabsUiState.Ready(listOf(LabsFlag(FeatureFlag.WATCH_EXPERIENCE_V2, enabled = true))),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `a flag changed elsewhere shows its new value`() =
        runTest {
            val store = FakeFeatureFlagStore()
            val viewModel = LabsSettingsViewModel(store, flags = listOf(FeatureFlag.WATCH_EXPERIENCE_V2))
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            store.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled = true)
            advanceUntilIdle()

            assertEquals(
                LabsUiState.Ready(listOf(LabsFlag(FeatureFlag.WATCH_EXPERIENCE_V2, enabled = true))),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `with nothing in testing the page shows an empty list`() =
        runTest {
            val viewModel = LabsSettingsViewModel(FakeFeatureFlagStore(), flags = emptyList())
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            assertEquals(LabsUiState.Ready(emptyList()), viewModel.uiState.value)
        }
}

private class FakeFeatureFlagStore : FeatureFlagStore {
    private val values = MutableStateFlow<Map<FeatureFlag, Boolean>>(emptyMap())
    val writes = mutableListOf<Pair<FeatureFlag, Boolean>>()

    override fun observe(flag: FeatureFlag): Flow<Boolean> = values.map { stored -> stored[flag] ?: flag.defaultEnabled }

    override suspend fun setEnabled(
        flag: FeatureFlag,
        enabled: Boolean,
    ) {
        writes += flag to enabled
        values.update { stored -> stored + (flag to enabled) }
    }
}
```

- [ ] **Step 3: Add compile-only stubs so both tests fail on behavior**

Create `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/VersionTapCounter.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.about

class VersionTapCounter(
    private val tapsToOpenLabs: Int = TAPS_TO_OPEN_LABS,
) {
    fun tap(): Boolean = false

    companion object {
        const val TAPS_TO_OPEN_LABS: Int = 7
    }
}
```

Create `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsViewModel.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.labs

import androidx.lifecycle.ViewModel
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LabsFlag(
    val flag: FeatureFlag,
    val enabled: Boolean,
)

sealed interface LabsUiState {
    data object Loading : LabsUiState

    data class Ready(
        val flags: List<LabsFlag>,
    ) : LabsUiState
}

class LabsSettingsViewModel(
    private val featureFlagStore: FeatureFlagStore,
    flags: List<FeatureFlag> = labsFlags(),
) : ViewModel() {
    val uiState: StateFlow<LabsUiState> = MutableStateFlow(LabsUiState.Loading)

    fun setEnabled(
        flag: FeatureFlag,
        enabled: Boolean,
    ) = Unit
}

fun labsFlags(entries: List<FeatureFlag> = FeatureFlag.entries): List<FeatureFlag> = emptyList()
```

Create `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsFlagCopy.kt`:

```kotlin
package app.logdate.feature.core.settings.ui.labs

import app.logdate.client.datastore.featureflags.FeatureFlag
import org.jetbrains.compose.resources.StringResource

internal data class LabsFlagCopy(
    val title: StringResource,
    val description: StringResource,
)

internal fun FeatureFlag.labsCopy(): LabsFlagCopy? = null
```

- [ ] **Step 4: Run the tests and watch them fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.about.VersionTapCounterTest' --tests 'app.logdate.feature.core.settings.ui.labs.LabsSettingsViewModelTest' --console=plain`
Expected: FAIL. Seven tests fail on assertions: `the seventh tap opens Labs`, `counting starts over once Labs opens`, `Labs offers flags that are off by default and allowed to turn on`, `the page loads each flag with its stored value`, `turning a flag on stores it and shows it on`, `a flag changed elsewhere shows its new value`, `with nothing in testing the page shows an empty list`. `six taps do not open Labs` and `every flag Labs offers has a title and description` pass (the stub offers no flags).

- [ ] **Step 5: Implement the tap counter**

Replace the contents of `VersionTapCounter.kt` with:

```kotlin
package app.logdate.feature.core.settings.ui.about

/**
 * Counts taps on the version row of the About page and reports the tap that opens Labs.
 *
 * The count starts over after Labs opens. The About page creates a new counter each time it is
 * shown, so taps from an earlier visit do not count.
 */
class VersionTapCounter(
    private val tapsToOpenLabs: Int = TAPS_TO_OPEN_LABS,
) {
    private var taps = 0

    /** Records one tap and returns whether it is the one that opens Labs. */
    fun tap(): Boolean {
        taps += 1
        if (taps < tapsToOpenLabs) return false
        taps = 0
        return true
    }

    companion object {
        /** How many taps on the version open Labs. */
        const val TAPS_TO_OPEN_LABS: Int = 7
    }
}
```

- [ ] **Step 6: Implement the Labs view model**

Replace the contents of `LabsSettingsViewModel.kt` with:

```kotlin
package app.logdate.feature.core.settings.ui.labs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One unreleased feature on the Labs page, and whether it is on for this installation. */
data class LabsFlag(
    val flag: FeatureFlag,
    val enabled: Boolean,
)

/** What the Labs page shows. */
sealed interface LabsUiState {
    /** The stored flag values have not been read yet. */
    data object Loading : LabsUiState

    /** Every flag Labs offers, in declaration order; empty when no feature is unreleased. */
    data class Ready(
        val flags: List<LabsFlag>,
    ) : LabsUiState
}

/**
 * Lists the features that have not launched and turns them on or off for this installation.
 *
 * Release builds have no other switch for an unfinished feature, so this is how a tester tries one
 * before it ships.
 *
 * @param flags the flags to offer; [labsFlags] everywhere except tests
 */
class LabsSettingsViewModel(
    private val featureFlagStore: FeatureFlagStore,
    flags: List<FeatureFlag> = labsFlags(),
) : ViewModel() {
    val uiState: StateFlow<LabsUiState> =
        observe(flags).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LabsUiState.Loading)

    /** Turns [flag] on or off for this installation. */
    fun setEnabled(
        flag: FeatureFlag,
        enabled: Boolean,
    ) {
        viewModelScope.launch { featureFlagStore.setEnabled(flag, enabled) }
    }

    private fun observe(flags: List<FeatureFlag>): Flow<LabsUiState> {
        if (flags.isEmpty()) return flowOf(LabsUiState.Ready(emptyList()))
        val rows = flags.map { flag -> featureFlagStore.observe(flag).map { enabled -> LabsFlag(flag, enabled) } }
        return combine(rows) { values -> LabsUiState.Ready(values.toList()) }
    }
}

/**
 * The flags Labs offers: off by default, and allowed to turn on.
 *
 * A flag deferred from launch is left out: the store reports it off whatever is saved, so its
 * switch would do nothing.
 */
fun labsFlags(entries: List<FeatureFlag> = FeatureFlag.entries): List<FeatureFlag> =
    entries.filter { it.availableForLaunch && !it.defaultEnabled }
```

The `flags.isEmpty()` guard matters: `combine` over an empty list emits nothing, which would leave the page on `Loading` forever once the last unreleased flag ships.

- [ ] **Step 7: Add the Labs strings**

Create `client/feature/core/src/commonMain/composeResources/values/strings_labs.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Labs -->
    <string name="labs_settings_title">Labs</string>
    <string name="labs_intro">These features are unfinished. Turning one on can change how LogDate works, and it can stop working in any update.</string>
    <string name="labs_section_unreleased">Unreleased features</string>
    <string name="labs_empty">No unreleased features to try.</string>
    <string name="labs_watch_experience_title">Redesigned watch app</string>
    <string name="labs_watch_experience_description">The new watch app and the phone screens that set up and manage the watch. Applies to this phone only.</string>
</resources>
```

- [ ] **Step 8: Give each offered flag its wording**

Replace the contents of `LabsFlagCopy.kt` with:

```kotlin
package app.logdate.feature.core.settings.ui.labs

import app.logdate.client.datastore.featureflags.FeatureFlag
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.labs_watch_experience_description
import logdate.client.feature.core.generated.resources.labs_watch_experience_title
import org.jetbrains.compose.resources.StringResource

/** The title and description Labs shows for a flag. */
internal data class LabsFlagCopy(
    val title: StringResource,
    val description: StringResource,
)

/**
 * The Labs wording for this flag, or `null` for a flag Labs never offers.
 *
 * There is no `else` branch: a new flag does not compile until it is given wording here or listed
 * with the flags Labs never offers.
 */
internal fun FeatureFlag.labsCopy(): LabsFlagCopy? =
    when (this) {
        FeatureFlag.WATCH_EXPERIENCE_V2 ->
            LabsFlagCopy(
                title = Res.string.labs_watch_experience_title,
                description = Res.string.labs_watch_experience_description,
            )
        FeatureFlag.LIBRARY,
        FeatureFlag.EVENTS,
        FeatureFlag.PEOPLE,
        FeatureFlag.CAMPFIRE_STREAKS,
        FeatureFlag.HUMAN_LOCATION_HISTORY,
        -> null
    }
```

- [ ] **Step 9: Run the tests and watch them pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.about.VersionTapCounterTest' --tests 'app.logdate.feature.core.settings.ui.labs.LabsSettingsViewModelTest' --console=plain`
Expected: PASS, 9 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 10: Build the Labs screen**

Create `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsScreen.kt`:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.labs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.labs_empty
import logdate.client.feature.core.generated.resources.labs_intro
import logdate.client.feature.core.generated.resources.labs_section_unreleased
import logdate.client.feature.core.generated.resources.labs_settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Labs: switches for features that have not launched, for testing them in a release build.
 *
 * Settings has no row for it; tapping the version on the About page seven times opens it.
 */
@Composable
fun LabsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LabsSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LabsSettingsContent(
        uiState = uiState,
        onBack = onBack,
        onSetEnabled = viewModel::setEnabled,
        modifier = modifier,
    )
}

@Composable
fun LabsSettingsContent(
    uiState: LabsUiState,
    onBack: () -> Unit,
    onSetEnabled: (FeatureFlag, Boolean) -> Unit,
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
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.labs_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
                LabsIntro(modifier = Modifier.padding(horizontal = Spacing.lg))
            }
        },
        endPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
            ) {
                LabsFlagsSection(
                    uiState = uiState,
                    onSetEnabled = onSetEnabled,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.labs_settings_title),
                onBack = onBack,
            ) {
                item {
                    LabsIntro(modifier = Modifier.padding(horizontal = Spacing.lg))
                }
                item {
                    LabsFlagsSection(
                        uiState = uiState,
                        onSetEnabled = onSetEnabled,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        },
    )
}

@Composable
private fun LabsIntro(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.labs_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun LabsFlagsSection(
    uiState: LabsUiState,
    onSetEnabled: (FeatureFlag, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState !is LabsUiState.Ready) return
    if (uiState.flags.isEmpty()) {
        Text(
            text = stringResource(Res.string.labs_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
        return
    }
    SettingsSection(
        title = stringResource(Res.string.labs_section_unreleased),
        modifier = modifier,
    ) {
        uiState.flags.forEach { row ->
            val copy = row.flag.labsCopy() ?: return@forEach
            ToggleSettingsItem(
                title = stringResource(copy.title),
                description = stringResource(copy.description),
                checked = row.enabled,
                onCheckedChange = { enabled -> onSetEnabled(row.flag, enabled) },
            )
        }
    }
}
```

- [ ] **Step 11: Make the About version row open Labs**

Replace the contents of `AboutSettingsScreen.kt` (from Task 4) with:

```kotlin
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.about_settings_title
import logdate.client.feature.core.generated.resources.about_version_title
import logdate.client.feature.core.generated.resources.about_version_unavailable
import logdate.client.feature.core.generated.resources.about_version_value
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The About page: the installed version of LogDate.
 *
 * Tapping the version seven times opens Labs.
 */
@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
    onOpenLabs: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutSettingsViewModel = koinViewModel(),
) {
    val versionTaps = remember { VersionTapCounter() }

    AboutSettingsContent(
        uiState = viewModel.uiState,
        onBack = onBack,
        onVersionClick = { if (versionTaps.tap()) onOpenLabs() },
        modifier = modifier,
    )
}

@Composable
fun AboutSettingsContent(
    uiState: AboutUiState,
    onBack: () -> Unit,
    onVersionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            Text(
                text = stringResource(Res.string.about_settings_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(Spacing.lg),
            )
        },
        endPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
            ) {
                VersionRow(
                    uiState = uiState,
                    onVersionClick = onVersionClick,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.about_settings_title),
                onBack = onBack,
            ) {
                item {
                    VersionRow(
                        uiState = uiState,
                        onVersionClick = onVersionClick,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        },
    )
}

@Composable
private fun VersionRow(
    uiState: AboutUiState,
    onVersionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val version =
        when (uiState) {
            is AboutUiState.Version -> stringResource(Res.string.about_version_value, uiState.versionName, uiState.versionCode)
            AboutUiState.VersionUnavailable -> stringResource(Res.string.about_version_unavailable)
        }
    MaterialContainer(modifier = modifier) {
        SimpleSettingsItem(
            title = stringResource(Res.string.about_version_title),
            description = version,
            onClick = onVersionClick,
            action = {},
        )
    }
}
```

- [ ] **Step 12: Register the Labs view model**

Replace the contents of `AboutSettingsModule.kt` with:

```kotlin
package app.logdate.feature.core.di

import app.logdate.feature.core.settings.ui.about.AboutSettingsViewModel
import app.logdate.feature.core.settings.ui.labs.LabsSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Dependency injection for the About page and the Labs page it opens.
 */
val aboutSettingsModule: Module =
    module {
        viewModel { AboutSettingsViewModel(get()) }
        viewModel { LabsSettingsViewModel(get()) }
    }
```

The three platform modules already include `aboutSettingsModule` (Task 4, Step 8).

- [ ] **Step 13: Add the Labs route, entry and serializer registration**

In `SettingsRoute.kt`, after:

```kotlin
@Serializable
data object AboutSettingsRoute : NavKey
```

add:

```kotlin

@Serializable
data object LabsSettingsRoute : NavKey
```

In `SettingsEntries.kt`, add the import directly after `import app.logdate.feature.core.settings.ui.devices.DevicesScreen`:

```kotlin
import app.logdate.feature.core.settings.ui.labs.LabsSettingsScreen
```

and replace the About entry from Task 4:

```kotlin
    taggedEntry<AboutSettingsRoute> {
        AboutSettingsScreen(onBack = onBack)
    }
```

with:

```kotlin
    taggedEntry<AboutSettingsRoute> {
        AboutSettingsScreen(
            onBack = onBack,
            onOpenLabs = { onNavigateTo(LabsSettingsRoute) },
        )
    }
    taggedEntry<LabsSettingsRoute> {
        LabsSettingsScreen(onBack = onBack)
    }
```

In `AppNavKeys.kt`, add the import directly after `import app.logdate.feature.core.settings.navigation.ExportSettingsRoute`:

```kotlin
import app.logdate.feature.core.settings.navigation.LabsSettingsRoute
```

and directly after `                    subclass(AboutSettingsRoute::class, AboutSettingsRoute.serializer())` add:

```kotlin
                    subclass(LabsSettingsRoute::class, LabsSettingsRoute.serializer())
```

- [ ] **Step 14: Add the Labs scene to the shared screenshot catalog**

The Labs scene names `FeatureFlag`, which `client/screenshot-scenes` cannot see yet. In `client/screenshot-scenes/build.gradle.kts`, after line 57 (`            implementation(projects.client.location)`) add:

```kotlin
            implementation(projects.client.logdateDatastore)
```

In `SharedScreenshotCatalog.kt`:

1. Directly above `import app.logdate.client.domain.dayboundary.HealthConnectGateKind` (line 14) add `import app.logdate.client.datastore.featureflags.FeatureFlag`.
2. Directly after `import app.logdate.feature.core.settings.ui.devices.DevicesUiState` add:

```kotlin
import app.logdate.feature.core.settings.ui.labs.LabsFlag
import app.logdate.feature.core.settings.ui.labs.LabsSettingsContent
import app.logdate.feature.core.settings.ui.labs.LabsUiState
```

3. Directly after `    AboutSettings("about-settings"),` add `    LabsSettings("labs-settings"),`.
4. Replace the About scene from Task 4:

```kotlin
                AboutSettingsContent(
                    uiState = AboutUiState.Version(versionName = "1.0.0", versionCode = 100),
                    onBack = {},
                )
            },
```

with:

```kotlin
                AboutSettingsContent(
                    uiState = AboutUiState.Version(versionName = "1.0.0", versionCode = 100),
                    onBack = {},
                    onVersionClick = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.LabsSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                LabsSettingsContent(
                    uiState = LabsUiState.Ready(listOf(LabsFlag(FeatureFlag.WATCH_EXPERIENCE_V2, enabled = true))),
                    onBack = {},
                    onSetEnabled = { _, _ -> },
                )
            },
```

- [ ] **Step 15: Note the page in the module README**

In `client/feature/core/README.md`, after ``- `settings/ui/about/` - About page: the installed version`` add:

```markdown
- `settings/ui/labs/` - Labs page: switches for unreleased feature flags, opened by tapping the version on About seven times
```

- [ ] **Step 16: Compile every target, run the module tests and lint**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest :client:feature:core:compileAndroidMain :client:feature:core:compileKotlinIosSimulatorArm64 :client:feature:core:compileTestKotlinIosSimulatorArm64 :client:feature:core:ktlintCheck :client:feature:core:detekt --console=plain`
Expected: PASS, `BUILD SUCCESSFUL`.

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:screenshot-scenes:desktopTest :client:screenshot-scenes:compileAndroidMain :client:screenshot-scenes:compileKotlinIosSimulatorArm64 :client:screenshot-scenes:ktlintCheck :app:compose-main:compileKotlinDesktop :app:compose-main:compileKotlinIosSimulatorArm64 :app:compose-main:ktlintCheck :app:android-main:assembleDebug --console=plain`
Expected: PASS, `BUILD SUCCESSFUL`.

- [ ] **Step 17: Record and check the Labs baselines**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && LOGDATE_REQUIRE_DESKTOP_SCREENSHOTS=true ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=labs-settings :app:compose-main:updateDesktopScreenshotTest --tests 'app.logdate.screenshots.DesktopScreenshotTest' --console=plain`
Expected: `BUILD SUCCESSFUL`, and four new files: `app/compose-main/src/desktopTest/reference/labs-settings--phone.png`, `labs-settings--phone-dark.png`, `labs-settings--phone-landscape.png`, `labs-settings--tablet.png`.

Open each one. Each shows the title "Labs", the sentence beginning "These features are unfinished.", the "Unreleased features" section, and one switch, turned on, titled "Redesigned watch app".

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && LOGDATE_REQUIRE_DESKTOP_SCREENSHOTS=true ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=settings :app:compose-main:desktopTest --tests 'app.logdate.screenshots.DesktopScreenshotTest' --console=plain`
Expected: PASS for every settings scene, including the unchanged `about-settings` baselines (adding `onVersionClick` does not change the render).

Run: `git status --short app/compose-main/src/desktopTest/reference app/android-main/src/screenshotTestDebug`
Expected: only the four `labs-settings--*.png` files, untracked.

- [ ] **Step 18: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-labs-page.txt <<'EOF'
chore(settings): add a hidden Labs page for unreleased features

Tapping the version on the About page seven times opens Labs. It lists
every feature flag that is still off by default and can be turned on,
so a tester can try an unfinished feature in a release build. The
redesigned watch app is the first entry.

Labs has no row in Settings, so nothing changes for anyone who does
not go looking for it. The switch applies to this phone only; sending
it to the watch comes with the watch sync work.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 19: Commit**

```bash
git restore --staged . && git add client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/VersionTapCounter.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/about/AboutSettingsScreen.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsViewModel.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsFlagCopy.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsScreen.kt client/feature/core/src/commonMain/composeResources/values/strings_labs.xml client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/di/AboutSettingsModule.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/SettingsRoute.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/navigation/SettingsEntries.kt app/compose-main/src/commonMain/kotlin/app/logdate/navigation/AppNavKeys.kt client/screenshot-scenes/build.gradle.kts client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt client/feature/core/README.md client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/about/VersionTapCounterTest.kt client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsViewModelTest.kt app/compose-main/src/desktopTest/reference/labs-settings--phone.png app/compose-main/src/desktopTest/reference/labs-settings--phone-dark.png app/compose-main/src/desktopTest/reference/labs-settings--phone-landscape.png app/compose-main/src/desktopTest/reference/labs-settings--tablet.png && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-labs-page.txt
```

Expected: the hook runs ktlint on `:client:feature:core`, `:client:screenshot-scenes` and `:app:compose-main`, passes, and git prints the `chore(settings): add a hidden Labs page for unreleased features` summary.

- [ ] **Step 20: Check the commit contents**

Run: `git show --stat --format='%s' HEAD` and then `git status --short`
Expected: exactly the 19 paths staged in Step 19, and a clean working tree afterwards.

---

## Self-Review

- **Spec coverage.** The spec's Delivery item 1 and the brief map to tasks: the `reminders` scope (Task 1); Wear OS 5 for `:app:wear` and both benchmark modules, the API 33 branch, its test and the README (Task 2, which also removes the same dead check from `WearAudioPlaybackBackgroundE2ETest`); `FeatureFlag.WATCH_EXPERIENCE_V2` with KDoc and a store test (Task 3); the hidden Labs page reached by seven taps on the version in About, toggling the flag through `FeatureFlagStore` in release builds, with a sealed UI state, view model and tap counter tests, string resources and a catalog scene (Task 5). About does not exist, so Task 4 adds it; Scope Notes records that deviation, that flag mirroring belongs to Track A, and that landing the dogfood branch is a separate rebase.
- **Placeholder scan.** Every code step shows the full file or an exact before/after block; every command is complete; every expected result names the pass, the failing test and its message, or the files that must appear. The two stub steps (Task 4 Step 2, Task 5 Step 3) exist so tests fail on behavior, as `CLAUDE.md` requires, and later steps replace them with the full implementation.
- **Name consistency.** `AboutSettingsRoute`, `AboutUiState.Version`/`VersionUnavailable`, `AboutSettingsViewModel.uiState`, `AboutSettingsScreen(onBack, onOpenLabs)`, `AboutSettingsContent(uiState, onBack, onVersionClick)`, `aboutSettingsModule`, `VersionTapCounter.tap()`, `LabsSettingsRoute`, `LabsFlag(flag, enabled)`, `LabsUiState.Loading`/`Ready(flags)`, `LabsSettingsViewModel(featureFlagStore, flags).setEnabled(flag, enabled)`, `labsFlags()`, `labsCopy()`, `LabsSettingsContent(uiState, onBack, onSetEnabled)`, the scene ids `about-settings`/`labs-settings`, and the string names in `strings_about.xml`/`strings_labs.xml` are spelled the same in every task that defines or uses them.
- **Commands.** Gradle paths and tasks come from the build files and existing docs: `:client:logdate-datastore` (`settings.gradle.kts:67-68`), `jvmTest` for its `jvm()` target, `desktopTest` and `compileKotlinDesktop` for `jvm("desktop")` targets, `compileAndroidMain` for the KMP Android library plugin, `compileKotlinIosSimulatorArm64`/`compileTestKotlinIosSimulatorArm64` for `iosSimulatorArm64()`, `assembleBenchmark` from `wear-macro`'s `benchmark` build type and the variant-named `assembleBenchmarkBenchmark`/`assembleNonMinifiedBenchmark` in `wear-baselineprofile`, `ktlintCheck` and `detekt` on every subproject (root `build.gradle.kts:55-88`), and the screenshot tasks and flags from `docs/testing/screenshot-tests.md` and `app/compose-main/build.gradle.kts:238-278`. All five commit messages pass `scripts/validation/validate-commit-message.sh`.
