# Watch Memories, Playback and Surfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Track B steps B5 to B8 of `docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md` behind `FeatureFlag.WATCH_EXPERIENCE_V2`: moment playback with crown scrubbing, speed and skip silence; inline playback, Earlier, On this day and Rewind on the Flow page; Play on through the system output switcher; the settings section with its status card; watch reminders after a workout and in the evening; and tiles and complications that update when moments change.

**Architecture:** The shared `AndroidAudioPlaybackManager` and `AudioPlaybackService` in `client/media` gain position, seek, speed (the standard Media3 player command) and skip silence (a custom session command). A watch-only `MomentPlayer` in `app.logdate.wear.v2.playback` drives them for every new screen, behind a narrow `MomentPlaybackEngine`. Screens follow the shell's pattern: a view model with a sealed UI state, a stateless composable, and a Navigation 3 entry in `WearAppV2`. Reminders live in `app.logdate.wear.reminders` (a Health Services passive listener, a WorkManager evening check, a notifier on a new `MOMENT_REMINDERS` channel). Surfaces stay in the existing `tile` and `complication` packages; `SurfaceComponents` enables the new ones and retires the old ones while the flag is on, and `SurfaceUpdater` refreshes them.

**Tech Stack:** Kotlin 2.3.21, AGP 9.2.1, Wear Compose Material 3 / Foundation / Navigation 3 1.7.0, Media3 1.10.0, Wear Tiles 1.6.2 (`Material3TileService`), ProtoLayout 1.4.2 with `protolayout-material3` and `protolayout-testing`, watchface complications data source 1.3.0, Health Services client 1.1.0, wear-phone-interactions 1.1.1, WorkManager, Koin 4.2.1, kotlinx.coroutines, kotlinx-datetime, kotlinx.serialization, JUnit 4 + kotlin.test + MockK + Turbine, Compose UI test v2 rules on the `wearSmallRoundApi34` Gradle Managed Device, the Android screenshot plugin.

## Global Constraints

- Prerequisites (checked in Task 0): everything in the predecessor plan `docs/superpowers/plans/2026-10-03-wear-design-system-shell-and-today.md` is on the branch; Room is at version 53 with `marked_moments`; the recorder and capture plan (`2026-10-10-watch-recorder-and-capture.md`, B3 and B4) has landed before Task 3. It replaces `WearAppV2.kt` whole in its Tasks 5, 7, 9 and 10 and `LogDateWearApplication.kt` whole in its Task 6, which would drop this plan's anchored edits to them, and Task 14 uses its recorder and `MarkMomentReceiver`. Tasks 1 and 2 touch neither file and may run first.
- Flag: every new screen and surface is reachable only through `WearExperience.V2` or while `WATCH_EXPERIENCE_V2` is on. The one deliberate exception is Task 12 (the campfire in ranged complication slots), a `feat` for everyone.
- v1 stays the default and continues to work. v1 files change only where a task says so: the shared playback manager and service (Task 1, behavior unchanged for existing callers), the audio resolver (Task 2, same results with typed failures), `TodaySummaryTileService` and `StreakComplicationService` (Tasks 13 and 12, v1 output unchanged with the flag off), the Data Layer listener (Task 11, one call), the manifest and `WatchSyncScheduler`'s KDoc (Task 9).
- No dead controls: a control appears only when it works on this build and this phone. Star, Open on phone, the phone as a Play on target, Hold to talk and "marks to fill in" belong to later plans and are not drawn.
- Room stays at 53. No migration in this plan.
- Logging: Napier only (`io.github.aakira.napier.Napier`). Never `println`, `System.out` or `android.util.Log`.
- IDs: `kotlin.uuid.Uuid`. Routes store IDs as `String`.
- Time: `kotlin.time.Clock` and `kotlin.time.Instant` (what `JournalNote` uses); `kotlinx.datetime` for dates, times of day and zones.
- DI: Koin constructor injection. New watch bindings go in `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`; services, receivers and workers use `KoinComponent` the way `WatchSyncWorker` does.
- Imports: ASCII order with `java.*`, `javax.*`, `kotlin.*` last, no wildcards. ktlint max line length 140.
- Code shape: early returns, no comments that restate the code, detekt `LongMethod` 60 lines outside `@Composable`, nothing added to any detekt baseline.
- Copy: literal, never "we", from string resources, times and dates in the user's locale. No vague metaphor verbs ("kept", "held", "carried", "forming") in copy, names, KDoc or commit text. New watch strings go in new files (`strings_moments.xml`, `strings_watch_settings.xml`, `strings_reminders.xml`, `strings_surfaces.xml`) so they never collide with the recorder plan's edits to `strings_home.xml`.
- Shared files the recorder plan also edits (`WearAppV2.kt`, `WearV2Module.kt`, `WearLaunchRoutes.kt`, `AndroidManifest.xml`, `LogDateWearApplication.kt`): change them with the anchored edits each step names, never by replacing the whole file, and leave whatever the recorder plan added in place.
- Gradle: every command starts with `export ANDROID_HOME=$HOME/Library/Android/sdk &&` and runs from `/Users/williecubed/Projects/TheHypertextStudio/logdate-mobile/.claude/worktrees/backup-sync-infinite-notification-dd4c3a`.
- Devices: Gradle Managed Device only. The watch task is `:app:wear:wearDevicesGroupDebugAndroidTest` (device `wearSmallRoundApi34`); one class with `-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>`, several comma-separated. Never `connected*AndroidTest`, `install*` or `adb`, and never start or stop an emulator by hand. A run that prints `Starting 0 tests` then `Process crashed` while `uptime` shows a load average above 100 is host load; rerun it.
- Screenshots: every new Compose state gets a scene annotated with both `@WearScreenshotPreviewMatrix` and `@WearLargeFontPreview` (small round, large round, and small round at font scale 1.24). References live in `app/wear/src/screenshotTestDebug/reference/` and change only in the commit whose code changes their pixels. Previews use `LogDateWearTheme(dynamicColor = false)`. Tiles and complications are not Compose; their layouts are checked with `protolayout-testing` on the GMD instead.
- Kotlin/Native: test names in `commonMain`/`commonTest` backticks contain no commas.
- Commits: the message file is written with a heredoc in its own step to `/private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/<name>.txt`; the next step runs `git restore --staged . && git add <exact paths> && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F <file>` as one chain. Never `git add -A`, `git add .`, `--no-verify` or `-m`.
- Commit messages: Conventional Commits, title at most 72 characters, body lines at most 72 characters, type `chore` while the work is dark behind the flag (`feat`/`fix` only for behavior an end user sees; never `style`), scope `wear`, `media` or `reminders` (never `build`, `android`, `ios`, `desktop` or `infra`), and `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` as the last line.

---

## File Structure

`R` = `app/wear/src/main/kotlin/app/logdate/wear`. `T` = `app/wear/src/test/kotlin/app/logdate/wear`. `A` = `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e`. `S` = `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots`.

### Shared (`client/media`, `client/notifications`)

| Path | Task | Responsibility |
|---|---|---|
| `client/media/src/commonMain/kotlin/app/logdate/client/media/audio/AudioPlaybackOptions.kt` | 1 | Where a recording starts, how fast, and whether it skips silence. |
| `client/media/src/commonMain/kotlin/app/logdate/client/media/audio/AudioPlaybackStatus.kt` | 1 | Gains `positionMs`, `playbackSpeed`, `skipSilenceEnabled`. |
| `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/PlaybackSessionCommands.kt` | 1 | The skip silence session command, the session callback that applies it, and the controller-side sender. |
| `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AudioPlaybackService.kt` | 1 | Installs the session callback. |
| `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioPlaybackManager.kt` | 1 | Starts with options; `seekToPosition`, `setPlaybackSpeed`, `setSkipSilenceEnabled`. |
| `client/notifications/src/commonMain/kotlin/app/logdate/client/notifications/LogDateNotificationChannels.kt` | 8 | `MOMENT_REMINDERS` and the watch's channel list. |
| `client/notifications/src/androidMain/kotlin/app/logdate/client/notifications/AndroidLogDateNotificationCatalog.kt` | 8 | The watch channel's spec. |
| `client/notifications/src/androidMain/res/values/strings.xml` | 8 | The channel's name and description. |

### Watch

| Path | Task | Responsibility |
|---|---|---|
| `R/playback/WatchMediaCache.kt` | 2 | The local media cache boundary, its `SyncedAudioCache` implementation with a free-space reserve, and the typed fetch failures. |
| `R/playback/WearSyncedAudioResolver.kt` | 2 | Reads and stores through `WatchMediaCache`; fails with the typed failures. |
| `R/v2/playback/PlaybackState.kt` | 2 | `PlaybackState`, `PlaybackFailure`, `PlaybackSpeed`. |
| `R/v2/playback/MomentPlaybackEngine.kt` | 2 | The engine interface and its adapter over `AndroidAudioPlaybackManager`. |
| `R/v2/playback/PlaybackPreferences.kt` | 2 | Speed and skip silence, saved in SharedPreferences. |
| `R/v2/playback/MomentPlayer.kt` | 2 | One recording at a time for every new screen. |
| `R/v2/playback/CrownScrubber.kt` | 2 | Crown rotation to whole 5 s detents. |
| `R/v2/playback/MomentCaption.kt` | 2 | The transcript line at a position, and the whole transcript. |
| `R/v2/moments/MomentUiState.kt`, `MomentViewModel.kt`, `MomentScreen.kt` | 3, 6 | Moment detail: player page with crown scrubbing, details page, delete, Play on. |
| `R/v2/moments/MomentRow.kt` | 3, 4 | A moment in a list, openable, with inline play for voice notes. |
| `R/v2/library/LibraryWindow.kt` | 4 | The 7-day list and the 14-day window, and which notes the watch library shows. |
| `R/v2/flow/FlowViewModel.kt`, `FlowPage.kt` | 3, 4, 5, 7 | The moment list: open, play inline, cards, Earlier, settings. |
| `R/v2/archive/ArchiveViewModel.kt`, `ArchiveScreen.kt` | 4 | Days 8 to 14 of the window, by day. |
| `R/v2/memories/OnThisDay.kt`, `WeeklyRewind.kt`, `MemoriesViewModel.kt`, `MemoryCards.kt` | 5 | On this day and this week's Rewind. |
| `R/v2/playon/OutputPicker.kt` | 6 | The system output switcher, or Bluetooth settings, as the Play on target. |
| `R/v2/settings/WatchStatus.kt`, `WatchSettingsViewModel.kt`, `WatchSettingsItems.kt` | 7, 9 | Status card, haptics, places, reminders, More on your phone. |
| `R/launch/WearLaunchIntents.kt` | 8 | Intents that open `MainActivity` on a route. |
| `R/reminders/WatchReminder.kt`, `WatchNudgeNotifier.kt`, `ReminderBridging.kt`, `WatchReminderPreferences.kt`, `WatchReminderRules.kt`, `WatchReminderDispatcher.kt` | 8, 10 | What a reminder says, posting it, bridging, the switch and the daily cap, the rules. |
| `R/reminders/ReminderPermissions.kt`, `ActivityRegistration.kt`, `WorkoutEndListenerService.kt`, `ReminderRestore.kt`, `WatchReminders.kt` | 9, 10 | The workout trigger and restoring it after a restart. |
| `R/reminders/EveningReminder.kt` | 10 | The evening check and its schedule. |
| `R/v2/surfaces/SurfaceUpdater.kt`, `SurfaceComponents.kt`, `SurfaceCoordinator.kt` | 11, 13, 14 | Refreshing, enabling and retiring tiles and complications. |
| `R/complication/CampfireComplicationContent.kt`, `StreakComplicationService.kt` | 12 | The campfire as a ranged value. |
| `R/v2/surfaces/SurfaceLaunchActions.kt`, `TodayTile.kt`, `OnThisDayTile.kt`, `R/tile/TodaySummaryTileService.kt`, `R/tile/OnThisDayTileService.kt`, `R/complication/OnThisDayComplicationService.kt` | 13 | The Today and On this day tiles and the On this day complication. |
| `R/complication/MarkComplicationService.kt` | 14 | The Mark complication, firing the recorder plan's `MarkMomentReceiver`; `WearAppV2` starts a recording for a Record launch. |
| `R/v2/WearAppV2.kt`, `R/v2/navigation/WearLaunchRoutes.kt`, `R/di/WearV2Module.kt`, `R/di/AudioModule.kt`, `R/LogDateWearApplication.kt`, `R/sync/WearDataLayerListenerService.kt`, `R/sync/WatchSyncScheduler.kt` | many | Wiring. |
| `app/wear/src/main/res/values/strings_moments.xml`, `strings_watch_settings.xml`, `strings_reminders.xml`, `strings_surfaces.xml` | 3–14 | Copy. |
| `app/wear/src/main/AndroidManifest.xml` | 6, 9, 12, 13, 14 | `<queries>`, permissions, services, receivers, new surfaces. |

Tests: `app/wear/src/test/kotlin/app/logdate/client/media/audio/` (Task 1), `T/playback/`, `T/v2/{playback,moments,flow,archive,memories,playon,settings,surfaces}/`, `T/reminders/`, `T/complication/`, `client/notifications/src/commonTest/`, GMD tests in `A/`, screenshot scenes in `S/`.

## Verified API surface

context7 returned "Monthly quota exceeded" while this plan was written (run `npx ctx7@latest login` or set `CONTEXT7_API_KEY` to restore it). Every signature below was read from the artifacts Gradle resolved, with `javap` on the cached AARs under `~/.gradle/caches/modules-2/files-2.1/` and their Kotlin metadata (parameter names, receivers, defaults) read with `kotlin-metadata-jvm`; the three that are not cached are marked.

- Media3 1.10.0: `MediaSession.Builder.setCallback(MediaSession.Callback)`; `MediaSession.Callback.onConnect(session, controller): ConnectionResult` and `onCustomCommand(session, controller, SessionCommand, Bundle): ListenableFuture<SessionResult>` (both default methods); `MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(SessionCommands).build()`; `ConnectionResult.DEFAULT_SESSION_COMMANDS` and `DEFAULT_PLAYER_COMMANDS` (built with `addAllCommands()`, so `Player.COMMAND_SET_SPEED_AND_PITCH` is available to every controller by default); `SessionCommands.buildUpon().add(SessionCommand)`; `SessionCommand(String, Bundle)` with `customAction`; `SessionResult(int)`, `RESULT_SUCCESS`, `RESULT_ERROR_BAD_VALUE`, `resultCode`; `MediaController.sendCustomCommand(SessionCommand, Bundle): ListenableFuture<SessionResult>`, `isSessionCommandAvailable(SessionCommand)`, `setPlaybackSpeed(Float)`, `getPlaybackParameters()`, `setMediaItem(MediaItem, Long)`; `PlaybackParameters(Float).speed`; `ExoPlayer.setSkipSilenceEnabled(Boolean)`; `Player.Listener.onPlaybackParametersChanged(PlaybackParameters)`. Guava is an `api` dependency of media3 (`Futures`, `MoreExecutors`).
- Wear Compose Material 3 1.7.0: `AlertDialog(visible, onDismissRequest, confirmButton: RowScope.() -> Unit, title, modifier, dismissButton, icon, text, …)`; `AlertDialogDefaults.ConfirmButton(onClick, modifier, colors, content)` and `DismissButton(…)`; `ScreenScaffold(modifier, scrollInfoProvider = null, contentPadding, timeText, scrollIndicator, overscrollEffect, content: BoxScope.(PaddingValues) -> Unit)` (no list) and the `TransformingLazyColumnState` overloads; `CircularProgressIndicator(progress: () -> Float, modifier, enabled, allowProgressOverflow, startAngle, endAngle, colors, strokeWidth, gapSize)`; `LinearProgressIndicator(progress: () -> Float, modifier, colors, strokeWidth, enabled)`; `FilledIconButton(onClick, modifier, onLongClick, onLongClickLabel, enabled, shapes, colors, border, interactionSource, content)`; `IconButtonDefaults.LargeButtonSize`; `IconToggleButton(checked, onCheckedChange, modifier, enabled, colors, interactionSource, shapes, border, content)`; `CompactButton(onClick, modifier, …, icon, enabled, …, label)`; `Button(onClick, modifier, onLongClick, onLongClickLabel, secondaryLabel, icon, enabled, shape, colors, border, contentPadding, interactionSource, transformation, label)`; `OutlinedButton` with the same slots; `SwitchButton(checked, onCheckedChange, modifier, enabled, shape, colors, contentPadding, interactionSource, transformation, icon, secondaryLabel, label)`; `RadioButton(selected, onSelect, modifier, enabled, shape, colors, contentPadding, interactionSource, transformation, icon, secondaryLabel, label)`; `TitleCard(onClick, title: RowScope.() -> Unit, modifier, onLongClick, onLongClickLabel, time: (() -> Unit)?, subtitle: (ColumnScope.() -> Unit)?, enabled, shape, colors, border, contentPadding, interactionSource, transformation, content: (() -> Unit)?)`.
- Wear Compose Foundation 1.7.0: `Modifier.requestFocusOnHierarchyActive()` (replaces the deprecated `rememberActiveFocusRequester()`), `Modifier.hierarchicalFocusGroup(active)`.
- Tiles 1.6.2: `abstract class Material3TileService(allowDynamicTheme: Boolean = true, defaultColorScheme: ColorScheme = …, serviceScope: CoroutineScope? = …)` with `abstract suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile`; its `onTileResourcesRequest` is final and answers empty resources with the requested version; `TileService.getUpdater(context).requestUpdate(Class<out TileService>)`; `tile(timeline, freshness: kotlin.time.Duration? = null, state = null, resourcesVersion = null)`; `TimelineBuilders.Timeline.fromLayoutElement(LayoutElement)`.
- ProtoLayout Material 3 1.4.2: `MaterialScope.primaryLayout(mainSlot, titleSlot = null, bottomSlot = null, labelForBottomSlot = null, onClick = null, margins = …)`; `MaterialScope.textEdgeButton(onClick: Clickable, modifier = …, colors = …, labelContent)`; `MaterialScope.text(text: LayoutString, modifier, typography, color, italic, underline, scalable, maxLines, alignment, overflow, settings)`; `MaterialScope.titleCard(onClick, title, modifier, content = null, time = null, height, shape, colors, backgroundContent, style, contentPadding, horizontalAlignment)`; `materialScope(context, deviceConfiguration, allowDynamicTheme = true, defaultColorScheme = …, layout)`; `Typography.{DISPLAY_SMALL, TITLE_MEDIUM, BODY_MEDIUM, BODY_SMALL, LABEL_MEDIUM}`; `String.layoutString` (`androidx.wear.protolayout.types`); `clickable(action, id)` (`androidx.wear.protolayout.modifiers`); `ActionBuilders.launchAction(ComponentName, Map<String, AndroidExtra>)` and `ActionBuilders.stringExtra(String)`; `DeviceParametersBuilders.DeviceParameters.Builder().setScreenWidthDp/HeightDp/Density/FontScale/Shape/DevicePlatform`.
- ProtoLayout testing 1.4.x (not cached; signatures from `wear/protolayout/protolayout-testing/api/1.4.0-rc02.txt` in AOSP): `LayoutElementAssertionsProvider(LayoutElement).onElement(LayoutElementMatcher).assertExists()` / `assertDoesNotExist()`; top-level matchers `hasText(String, subString = false, ignoreCase = false)`, `hasClickable(action, id = null)` (compares `toActionProto()` and the id), `hasContentDescription(String)`, `hasDescendant(matcher)`; matchers combine with `and`/`or`/`not`. The artifact is added in Task 13, whose first step resolves it and reads these names back from the resolved AAR with `javap`.
- Watch face complications 1.3.0: `RangedValueComplicationData.Builder(value: Float, min: Float, max: Float, contentDescription)` with `setText`, `setMonochromaticImage`, `setColorRamp(ColorRamp(IntArray, interpolated))`, `setTapAction`; `ComplicationDataSourceUpdateRequester.create(context, ComponentName).requestUpdateAll()`.
- Health Services 1.1.0: `PassiveListenerService.onUserActivityInfoReceived(UserActivityInfo)`; `UserActivityInfo.userActivityState`, `stateChangeTime: java.time.Instant`; `UserActivityState.USER_ACTIVITY_{EXERCISE,PASSIVE,ASLEEP,UNKNOWN}`; `PassiveListenerConfig.builder().setShouldUserActivityInfoBeRequested(true).build()`; suspend `PassiveMonitoringClient.setPassiveListenerService(Class<out PassiveListenerService>, PassiveListenerConfig)`, `clearPassiveListenerService()`, `getCapabilities().supportedUserActivityStates`. The service needs `android:permission="com.google.android.wearable.healthservices.permission.PASSIVE_DATA_BINDING"` (from the AAR's own manifest). The Health Services guide (developer.android.com, "monitor-background") says user activity state needs `ACTIVITY_RECOGNITION`, registrations do not survive a reboot, and a boot receiver should hand re-registration to WorkManager because acknowledging can take 10 s.
- wear-phone-interactions 1.1.1: `BridgingManager.fromContext(context).setConfig(BridgingConfig)`; `BridgingConfig.Builder(context, isBridgingEnabled).addExcludedTag(String).build()`; `BridgingConfig.excludedTags`, `isBridgingEnabled`. `setConfig` binds a system service and throws when it cannot.
- Output switcher (not an API; read from Horologist `OutputSwitcher.kt` and the Wear audio guide): action `com.android.settings.panel.action.MEDIA_OUTPUT`, extra `com.android.settings.panel.extra.PACKAGE_NAME`, flag `FLAG_ACTIVITY_NEW_TASK`, launched only on a system or updated-system activity; fallback `Settings.ACTION_BLUETOOTH_SETTINGS` with `EXTRA_CONNECTION_ONLY`, `EXTRA_CLOSE_ON_CONNECT` and `android.bluetooth.devicepicker.extra.FILTER_TYPE = 1`. Media3 documents that the built-in speaker of a Wear OS device is an unsuitable output "unless it is explicitly selected by the user", which is why Play on goes through this system picker instead of `setPreferredAudioDevice`.
- Koin compose 4.2.1: `koinViewModel(qualifier, viewModelStoreOwner, key, extras, scope, parameters: (() -> ParametersHolder)?)`.

---

### Task 0: Confirm the prerequisites and record a baseline

**Files:** none changed.

**Interfaces:**
- Consumes: the predecessor plans' commits on this branch, and the recorder and capture plan.
- Produces: confidence that every later task starts from green, and the exact name of the recorder plan's mark entry point for Task 14.

- [ ] **Step 1: Check the prerequisites this plan builds on**

```bash
ls app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt app/wear/src/main/kotlin/app/logdate/wear/v2/today/DayClock.kt app/wear/src/main/kotlin/app/logdate/wear/sync/SyncedAudioCache.kt app/wear/src/main/kotlin/app/logdate/wear/sync/PhoneReachability.kt
grep -n "version = 53" client/database/src/commonMain/kotlin/app/logdate/client/database/LogDateDatabase.kt
grep -n "WATCH_EXPERIENCE_V2" client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/featureflags/FeatureFlag.kt
grep -n 'media3 = "1.10.0"\|tiles = "1.6.2"\|protolayout = "1.4.2"\|healthServicesClient = "1.1.0"' gradle/libs.versions.toml
```

Expected: five files listed, one line from each `grep` and four from the last. If anything is missing, stop: this plan builds on those commits.

- [ ] **Step 2: Check the recorder and capture plan (needed from Task 3 on)**

```bash
git log --oneline -- app/wear/src/main/kotlin/app/logdate/wear/v2/recorder | head -5
git grep -n "fun RecordGestureListener.startLocked\|val RecorderState.canStartRecording\|object RecorderTags" -- app/wear/src/main
git grep -n "class MarkMomentReceiver" -- app/wear/src/main
```

Expected: more than the one commit `chore(wear): add the Home pager with Today and the last 7 days` in the first output; three lines in the second (the recorder Task 14 starts for a Record launch, and the tags its test reads); one line in the third. The recorder plan leaves a Record launch opening Today, so Task 14 is where a Record launch starts recording. Then read the receiver's companion:

```bash
f=$(git grep -l "class MarkMomentReceiver" -- app/wear/src/main) && grep -n "fun \|const val" $f
```

Write down the function that returns the pending intent which marks the moment from outside the app. Task 14 calls it as `MarkMomentReceiver.pendingIntent(context)` (package `app.logdate.wear.v2.mark`), the name in the recorder plan's draft; if it differs, that call and its import are the only lines that change. If the first two checks fail, run Tasks 1 and 2 now and Tasks 3 to 14 after the recorder plan lands (it replaces `WearAppV2.kt` and `LogDateWearApplication.kt` whole); if only the third fails, postpone Task 14 until the receiver lands. Until Task 14, a Record action or button opens the new app on Today, one tap from recording.

- [ ] **Step 3: Check the tree**

```bash
git status --short && git log --oneline -3
```

Expected: no output from `git status`.

- [ ] **Step 4: Run the unit, common and screenshot tests once**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:media:testAndroidHostTest :client:notifications:jvmTest :client:domain:jvmTest :app:wear:testDebugUnitTest :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`. If screenshot validation fails here, stop and report: the baselines are already stale.

- [ ] **Step 5: Run the GMD tests this plan extends**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.HomePagerRotaryTest,app.logdate.wear.e2e.WearAudioPlaybackBackgroundE2ETest,app.logdate.wear.e2e.WearManifestFiltersTest
```

Expected: `BUILD SUCCESSFUL`. A playback test may report "skipped" when the emulator has no suitable output; that is the existing behavior.

---

### Task 1: Position, speed and skip silence on the shared player (`chore(media)`)

Moment detail needs to scrub, change speed and skip silence. Speed is a standard Media3 player command that every controller already has; skip silence is an ExoPlayer setting with no player command, so the session gains one custom command for it. Every playback now sets speed and skip silence explicitly, so one recording never inherits another's; existing callers get normal speed with silence played, as today.

**Files:**
- Create: `client/media/src/commonMain/kotlin/app/logdate/client/media/audio/AudioPlaybackOptions.kt`
- Modify: `client/media/src/commonMain/kotlin/app/logdate/client/media/audio/AudioPlaybackStatus.kt:6-21`
- Create: `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/PlaybackSessionCommands.kt`
- Modify: `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AudioPlaybackService.kt:67-72`
- Modify: `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioPlaybackManager.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/client/media/audio/AndroidAudioPlaybackManagerTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/PlaybackSessionCommandsTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces (package `app.logdate.client.media.audio`):
  - `data class AudioPlaybackOptions(val startPositionMs: Long = 0L, val speed: Float = 1f, val skipSilence: Boolean = false)`
  - `AudioPlaybackStatus` gains `positionMs: Long = 0L`, `playbackSpeed: Float = 1f`, `skipSilenceEnabled: Boolean = false`.
  - `object PlaybackSessionCommands { const val ACTION_SET_SKIP_SILENCE: String; const val EXTRA_ENABLED: String; val SET_SKIP_SILENCE: SessionCommand; fun skipSilenceArgs(enabled: Boolean): Bundle }`
  - `class PlaybackSessionCallback(setSkipSilence: (Boolean) -> Unit) : MediaSession.Callback`
  - `fun interface SkipSilenceCommand { fun send(controller: MediaController, enabled: Boolean, onResult: (Boolean) -> Unit); companion object { val Session: SkipSilenceCommand } }`
  - `AndroidAudioPlaybackManager(…, skipSilenceCommand: SkipSilenceCommand = SkipSilenceCommand.Session)` with `fun startPlayback(uri: String, metadata: AudioPlaybackMetadata?, options: AudioPlaybackOptions, onProgressUpdated: (Float) -> Unit, onPlaybackCompleted: () -> Unit)`, `fun seekToPosition(positionMs: Long)`, `fun setPlaybackSpeed(speed: Float)`, `fun setSkipSilenceEnabled(enabled: Boolean)`.

- [ ] **Step 1: Write the failing manager tests**

In `app/wear/src/test/kotlin/app/logdate/client/media/audio/AndroidAudioPlaybackManagerTest.kt`:

Add these imports in ASCII order: `import androidx.media3.common.PlaybackParameters` (after `import androidx.media3.common.MediaItem`) and `import io.mockk.verifyOrder` (after `import io.mockk.verify`).

Add two fields after `private val directExecutor = Executor { runnable -> runnable.run() }`:

```kotlin
    private val skipSilenceRecorder = RecordingSkipSilence()
    private var speed = 1f
```

Add these tests directly before `private fun createManager(`:

```kotlin
    @Test
    fun `playback starts at the position and speed asked for`() =
        runTest {
            val itemFactory = RecordingAudioPlaybackItemFactory()
            val manager = createManager(this, currentPosition = PlaybackStateHolder(250L), itemFactory = itemFactory)

            manager.startPlayback(
                uri = "file:///tmp/voice-note.m4a",
                metadata = null,
                options = AudioPlaybackOptions(startPositionMs = 250L, speed = 1.5f, skipSilence = true),
                onProgressUpdated = {},
                onPlaybackCompleted = {},
            )

            verify(exactly = 1) { controller.setMediaItem(itemFactory.mediaItem, 250L) }
            verify(exactly = 1) { controller.setPlaybackSpeed(1.5f) }
            assertEquals(listOf(true), skipSilenceRecorder.requests)
            val status = manager.playbackStatus.value
            assertEquals(250L, status.positionMs)
            assertEquals(1.5f, status.playbackSpeed)
            assertTrue(status.skipSilenceEnabled)
            manager.release()
        }

    @Test
    fun `plain playback starts at the beginning at normal speed and plays silence`() =
        runTest {
            val itemFactory = RecordingAudioPlaybackItemFactory()
            val manager = createManager(this, currentPosition = PlaybackStateHolder(0L), itemFactory = itemFactory)

            manager.startPlayback(uri = "file:///tmp/voice-note.m4a", onProgressUpdated = {}, onPlaybackCompleted = {})

            verify(exactly = 1) { controller.setMediaItem(itemFactory.mediaItem) }
            verify(exactly = 0) { controller.setMediaItem(any(), any<Long>()) }
            verify(exactly = 1) { controller.setPlaybackSpeed(1f) }
            assertEquals(listOf(false), skipSilenceRecorder.requests)
            assertFalse(manager.playbackStatus.value.skipSilenceEnabled)
            manager.release()
        }

    @Test
    fun `seeking to a position stays inside the recording`() =
        runTest {
            val manager = createManager(this, currentPosition = PlaybackStateHolder(0L))
            manager.startPlayback(uri = "file:///tmp/voice-note.m4a", onProgressUpdated = {}, onPlaybackCompleted = {})

            manager.seekToPosition(5_000L)
            manager.seekToPosition(-20L)
            manager.seekToPosition(400L)

            verifyOrder {
                controller.seekTo(1_000L)
                controller.seekTo(0L)
                controller.seekTo(400L)
            }
            manager.release()
        }

    @Test
    fun `a new speed applies at once and is reported`() =
        runTest {
            val manager = createManager(this, currentPosition = PlaybackStateHolder(0L))
            manager.startPlayback(uri = "file:///tmp/voice-note.m4a", onProgressUpdated = {}, onPlaybackCompleted = {})

            manager.setPlaybackSpeed(2f)

            verify(exactly = 1) { controller.setPlaybackSpeed(2f) }
            assertEquals(2f, manager.playbackStatus.value.playbackSpeed)
            manager.release()
        }

    @Test
    fun `skip silence the session refuses stays off`() =
        runTest {
            skipSilenceRecorder.applies = false
            val manager = createManager(this, currentPosition = PlaybackStateHolder(0L))
            manager.startPlayback(uri = "file:///tmp/voice-note.m4a", onProgressUpdated = {}, onPlaybackCompleted = {})

            manager.setSkipSilenceEnabled(true)

            assertEquals(listOf(false, true), skipSilenceRecorder.requests)
            assertFalse(manager.playbackStatus.value.skipSilenceEnabled)
            manager.release()
        }

    @Test
    fun `progress tracking reports the position in milliseconds`() =
        runTest {
            val currentPosition = PlaybackStateHolder(200L)
            val dispatcher = StandardTestDispatcher(testScheduler)
            val manager = createManager(this, dispatcher = dispatcher, currentPosition = currentPosition)
            every { controller.isPlaying } returns true
            manager.startPlayback(uri = "file:///tmp/voice-note.m4a", onProgressUpdated = {}, onPlaybackCompleted = {})

            controllerListener.captured.onIsPlayingChanged(true)
            runCurrent()
            currentPosition.value = 640L
            advanceTimeBy(100)
            runCurrent()

            assertEquals(640L, manager.playbackStatus.value.positionMs)
            controllerListener.captured.onIsPlayingChanged(false)
            manager.release()
        }
```

Replace the whole `createManager` function with:

```kotlin
    private fun createManager(
        scope: TestScope,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(scope.testScheduler),
        currentPosition: PlaybackStateHolder,
        itemFactory: AudioPlaybackItemFactory = RecordingAudioPlaybackItemFactory(),
        serviceStarter: AudioPlaybackServiceStarter = RecordingAudioPlaybackServiceStarter(),
        controllerFutureFactory: MediaControllerFutureFactory =
            MediaControllerFutureFactory { Futures.immediateFuture(controller) },
    ): AndroidAudioPlaybackManager {
        stubController(currentPosition)

        return AndroidAudioPlaybackManager(
            context = context,
            coroutineScope = scope,
            progressDispatcher = dispatcher,
            controllerFactory = controllerFutureFactory,
            controllerExecutor = directExecutor,
            mediaItemFactory = itemFactory,
            serviceStarter = serviceStarter,
            skipSilenceCommand = skipSilenceRecorder,
        )
    }
```

Replace the whole `stubController` function with:

```kotlin
    private fun stubController(currentPosition: PlaybackStateHolder) {
        every { controller.addListener(capture(controllerListener)) } just runs
        every { controller.removeListener(any<Player.Listener>()) } just runs
        every { controller.setMediaItem(any()) } just runs
        every { controller.setMediaItem(any(), any<Long>()) } just runs
        every { controller.prepare() } just runs
        every { controller.play() } just runs
        every { controller.pause() } just runs
        every { controller.stop() } just runs
        every { controller.release() } just runs
        every { controller.seekTo(any<Long>()) } just runs
        every { controller.setPlaybackSpeed(any()) } answers { speed = firstArg() }
        every { controller.playbackParameters } answers { PlaybackParameters(speed) }
        every { controller.duration } returns 1_000L
        every { controller.currentPosition } answers { currentPosition.value }
        every { controller.isPlaying } returns false
        every { controller.playbackSuppressionReason } returns Player.PLAYBACK_SUPPRESSION_REASON_NONE
    }
```

Add this class directly after `private class PlaybackStateHolder(…)`:

```kotlin
    private class RecordingSkipSilence(
        var applies: Boolean = true,
    ) : SkipSilenceCommand {
        val requests = mutableListOf<Boolean>()

        override fun send(
            controller: MediaController,
            enabled: Boolean,
            onResult: (Boolean) -> Unit,
        ) {
            requests += enabled
            onResult(applies)
        }
    }
```

- [ ] **Step 2: Run the unit tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.client.media.audio.AndroidAudioPlaybackManagerTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'SkipSilenceCommand'`, `Unresolved reference 'AudioPlaybackOptions'` and `No parameter with name 'skipSilenceCommand' found`.

- [ ] **Step 3: Write the failing GMD test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/PlaybackSessionCommandsTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackOptions
import app.logdate.client.media.audio.AudioPlaybackService
import app.logdate.client.media.audio.PlaybackSessionCommands
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin
import kotlin.uuid.Uuid

/**
 * Drives the real playback session on a Wear OS emulator: it offers skip silence to controllers,
 * refuses the command without a value, and a recording starts and moves where and as fast as asked.
 * A tone is played, because skip silence would skip a silent file to its end.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackSessionCommandsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var playbackManager: AndroidAudioPlaybackManager
    private var controller: MediaController? = null

    @Before
    fun setUp() {
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        LogDateNotificationRegistrar(context).registerChannel(LogDateNotificationChannelKey.AUDIO_PLAYBACK)
        playbackManager = AndroidAudioPlaybackManager(context, scope)
    }

    @After
    fun tearDown() {
        controller?.let { mediaController -> runOnMainSync { mediaController.release() } }
        runOnMainSync { playbackManager.release() }
        context.stopService(Intent(context, AudioPlaybackService::class.java))
        scope.cancel()
    }

    @Test
    fun the_session_offers_skip_silence_to_controllers() {
        val mediaController = mediaController()
        var available = false

        runOnMainSync { available = mediaController.isSessionCommandAvailable(PlaybackSessionCommands.SET_SKIP_SILENCE) }

        assertTrue(available)
    }

    @Test
    fun skip_silence_without_a_value_is_refused() {
        val mediaController = mediaController()
        lateinit var result: ListenableFuture<SessionResult>

        runOnMainSync { result = mediaController.sendCustomCommand(PlaybackSessionCommands.SET_SKIP_SILENCE, Bundle.EMPTY) }

        assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).resultCode)
    }

    @Test
    fun a_recording_starts_where_and_as_fast_as_asked() {
        startTone(AudioPlaybackOptions(startPositionMs = 20_000L, speed = 1.5f, skipSilence = true))

        waitUntil("the session turns skip silence on") { playbackManager.playbackStatus.value.skipSilenceEnabled }
        val status = playbackManager.playbackStatus.value
        assertEquals(1.5f, status.playbackSpeed)
        assertTrue("position ${status.positionMs}", status.positionMs >= 20_000L)
    }

    @Test
    fun speed_and_position_change_while_playing() {
        startTone(AudioPlaybackOptions())

        runOnMainSync {
            playbackManager.setPlaybackSpeed(2f)
            playbackManager.seekToPosition(30_000L)
        }

        waitUntil("speed 2 is reported") { playbackManager.playbackStatus.value.playbackSpeed == 2f }
        waitUntil("the position moved to 0:30") { playbackManager.playbackStatus.value.positionMs >= 30_000L }
    }

    private fun startTone(options: AudioPlaybackOptions) {
        val audio = toneWavFile(durationSeconds = 60)
        runOnMainSync {
            playbackManager.startPlayback(
                uri = Uri.fromFile(audio).toString(),
                metadata = AudioPlaybackMetadata(noteId = Uuid.random()),
                options = options,
                onProgressUpdated = {},
                onPlaybackCompleted = {},
            )
        }
        waitUntil("playback starts or reports an unsuitable output") {
            val status = playbackManager.playbackStatus.value
            status.isPlaying || status.isSuppressedForUnsuitableOutput
        }
        assumeFalse(
            "The managed device has no suitable audio output, as in WearAudioPlaybackBackgroundE2ETest",
            playbackManager.playbackStatus.value.isSuppressedForUnsuitableOutput,
        )
    }

    private fun mediaController(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        return MediaController
            .Builder(context, token)
            .buildAsync()
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .also { controller = it }
    }

    private fun runOnMainSync(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun waitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < TIMEOUT_SECONDS * 1_000) {
            if (condition()) return
            Thread.sleep(100)
        }
        error("Timed out waiting until $description")
    }

    private fun toneWavFile(durationSeconds: Int): File {
        val sampleRate = 8_000
        val dataSize = durationSeconds * sampleRate * 2
        val file = File(context.cacheDir, "playback-session-tone.wav")
        file.outputStream().use { output ->
            output.write("RIFF".toByteArray())
            output.writeIntLe(36 + dataSize)
            output.write("WAVE".toByteArray())
            output.write("fmt ".toByteArray())
            output.writeIntLe(16)
            output.writeShortLe(1)
            output.writeShortLe(1)
            output.writeIntLe(sampleRate)
            output.writeIntLe(sampleRate * 2)
            output.writeShortLe(2)
            output.writeShortLe(16)
            output.write("data".toByteArray())
            output.writeIntLe(dataSize)
            val samples = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            repeat(durationSeconds * sampleRate) { index ->
                samples.putShort((sin(2 * PI * TONE_HZ * index / sampleRate) * AMPLITUDE).toInt().toShort())
            }
            output.write(samples.array())
        }
        return file
    }

    private fun OutputStream.writeIntLe(value: Int) {
        write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun OutputStream.writeShortLe(value: Int) {
        write(ByteBuffer.allocate(Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        const val TONE_HZ = 440.0
        const val AMPLITUDE = 8_000.0
    }
}
```

- [ ] **Step 4: Run the GMD test to verify it fails**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.PlaybackSessionCommandsTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'AudioPlaybackOptions'` and `Unresolved reference 'PlaybackSessionCommands'`.

- [ ] **Step 5: Add the playback options and the new status fields**

Create `client/media/src/commonMain/kotlin/app/logdate/client/media/audio/AudioPlaybackOptions.kt`:

```kotlin
package app.logdate.client.media.audio

/**
 * How a recording starts.
 *
 * @property startPositionMs Where playback starts, in milliseconds from the beginning.
 * @property speed The playback speed; 1 is normal.
 * @property skipSilence Whether stretches of silence are skipped.
 */
data class AudioPlaybackOptions(
    val startPositionMs: Long = 0L,
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
)
```

In `client/media/src/commonMain/kotlin/app/logdate/client/media/audio/AudioPlaybackStatus.kt`, replace

```kotlin
    val errorMessage: String? = null,
)
```

with

```kotlin
    val errorMessage: String? = null,
    /** Where playback is, in milliseconds from the beginning. */
    val positionMs: Long = 0L,
    /** The speed playback runs at; 1 is normal. */
    val playbackSpeed: Float = 1f,
    /** Whether stretches of silence are skipped. Only the Android playback manager sets it. */
    val skipSilenceEnabled: Boolean = false,
)
```

- [ ] **Step 6: Add the skip silence session command**

Create `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/PlaybackSessionCommands.kt`:

```kotlin
package app.logdate.client.media.audio

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/**
 * The custom command LogDate's playback session accepts beside the standard player commands.
 *
 * Speed is a standard player command. Skip silence is an ExoPlayer setting with no player command,
 * so a controller asks the session for it with [SET_SKIP_SILENCE] and [EXTRA_ENABLED].
 */
object PlaybackSessionCommands {
    const val ACTION_SET_SKIP_SILENCE = "app.logdate.audio.SET_SKIP_SILENCE"
    const val EXTRA_ENABLED = "enabled"

    val SET_SKIP_SILENCE: SessionCommand by lazy { SessionCommand(ACTION_SET_SKIP_SILENCE, Bundle.EMPTY) }

    fun skipSilenceArgs(enabled: Boolean): Bundle = Bundle().apply { putBoolean(EXTRA_ENABLED, enabled) }
}

/**
 * Offers [PlaybackSessionCommands.SET_SKIP_SILENCE] to every controller and applies it with
 * [setSkipSilence]. A request without [PlaybackSessionCommands.EXTRA_ENABLED] is refused.
 */
class PlaybackSessionCallback(
    private val setSkipSilence: (Boolean) -> Unit,
) : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult =
        MediaSession.ConnectionResult
            .AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(PlaybackSessionCommands.SET_SKIP_SILENCE)
                    .build(),
            ).build()

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction != PlaybackSessionCommands.ACTION_SET_SKIP_SILENCE) {
            return super.onCustomCommand(session, controller, customCommand, args)
        }
        if (!args.containsKey(PlaybackSessionCommands.EXTRA_ENABLED)) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
        }
        setSkipSilence(args.getBoolean(PlaybackSessionCommands.EXTRA_ENABLED))
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
}

/** Asks a session to turn skip silence on or off; [send]'s `onResult` receives whether it did. */
fun interface SkipSilenceCommand {
    fun send(
        controller: MediaController,
        enabled: Boolean,
        onResult: (Boolean) -> Unit,
    )

    companion object {
        /** Sends [PlaybackSessionCommands.SET_SKIP_SILENCE] to the session behind the controller. */
        val Session =
            SkipSilenceCommand { controller, enabled, onResult ->
                val result =
                    controller.sendCustomCommand(
                        PlaybackSessionCommands.SET_SKIP_SILENCE,
                        PlaybackSessionCommands.skipSilenceArgs(enabled),
                    )
                result.addListener(
                    { onResult(runCatching { result.get().resultCode == SessionResult.RESULT_SUCCESS }.getOrDefault(false)) },
                    MoreExecutors.directExecutor(),
                )
            }
    }
}
```

- [ ] **Step 7: Install the callback in the service**

In `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AudioPlaybackService.kt`, replace

```kotlin
        val sessionBuilder = MediaSession.Builder(this, player)
```

with

```kotlin
        val sessionBuilder =
            MediaSession
                .Builder(this, player)
                .setCallback(PlaybackSessionCallback { enabled -> player.skipSilenceEnabled = enabled })
```

- [ ] **Step 8: Give the manager options, position, speed and skip silence**

In `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioPlaybackManager.kt`:

Add `import androidx.media3.common.PlaybackParameters` after `import androidx.media3.common.PlaybackException`.

Replace the constructor's last parameter line

```kotlin
    private val serviceStarter: AudioPlaybackServiceStarter = defaultAudioPlaybackServiceStarter,
) : AudioPlaybackManager,
```

with

```kotlin
    private val serviceStarter: AudioPlaybackServiceStarter = defaultAudioPlaybackServiceStarter,
    private val skipSilenceCommand: SkipSilenceCommand = SkipSilenceCommand.Session,
) : AudioPlaybackManager,
```

Add `    private var skipSilenceEnabled = false` after `    private var currentMetadata: AudioPlaybackMetadata? = null`.

Replace the whole `override fun startPlayback(…) { … }` function with:

```kotlin
    override fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata?,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit,
    ) = startPlayback(uri, metadata, AudioPlaybackOptions(), onProgressUpdated, onPlaybackCompleted)

    /**
     * Starts [uri] at [options]' position and speed, skipping silence when [options] asks. Speed and
     * skip silence are set for every recording, so one never inherits another's.
     */
    fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata?,
        options: AudioPlaybackOptions,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit,
    ) {
        this.onProgressUpdated = onProgressUpdated
        this.onPlaybackCompleted = onPlaybackCompleted
        currentUri = uri
        currentMetadata = metadata
        _playbackStatus.value = _playbackStatus.value.copy(errorMessage = null)
        // startService (not startForegroundService) ensures MediaSessionService.onStartCommand
        // fires so its MediaNotificationManager gets a valid startId. Without this, the service
        // is only bound via BIND_AUTO_CREATE and stops itself ~1s after the controller connects.
        serviceStarter.start(context)
        withController { player ->
            val mediaItem = mediaItemFactory.create(uri, metadata)
            if (options.startPositionMs > 0L) {
                player.setMediaItem(mediaItem, options.startPositionMs)
            } else {
                player.setMediaItem(mediaItem)
            }
            player.setPlaybackSpeed(options.speed)
            player.prepare()
            player.play()
            applySkipSilence(player, options.skipSilence)
            updateStatus(player)
        }
    }
```

Add these functions directly after the existing `override fun seekTo(position: Float) { … }`:

```kotlin
    /** Moves playback to [positionMs], inside the recording once its length is known. */
    fun seekToPosition(positionMs: Long) {
        withController { player ->
            val duration = player.duration
            val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
            player.seekTo(target)
            updateStatus(player)
        }
    }

    /** Plays at [speed], where 1 is normal. */
    fun setPlaybackSpeed(speed: Float) {
        withController { player ->
            player.setPlaybackSpeed(speed)
            updateStatus(player)
        }
    }

    /** Skips stretches of silence while [enabled]. */
    fun setSkipSilenceEnabled(enabled: Boolean) {
        withController { player -> applySkipSilence(player, enabled) }
    }

    private fun applySkipSilence(
        player: MediaController,
        enabled: Boolean,
    ) {
        skipSilenceCommand.send(player, enabled) { applied ->
            if (applied) {
                skipSilenceEnabled = enabled
                _playbackStatus.value = _playbackStatus.value.copy(skipSilenceEnabled = enabled)
            } else {
                Napier.w { "The playback session did not turn skip silence ${if (enabled) "on" else "off"}" }
            }
        }
    }
```

In `playerListener`, add this override directly after `onPlaybackSuppressionReasonChanged`:

```kotlin
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                controller?.let { updateStatus(it) }
            }
```

In `startProgressTracking`, replace

```kotlin
                            _playbackStatus.value = _playbackStatus.value.copy(progress = normalized)
```

with

```kotlin
                            _playbackStatus.value =
                                _playbackStatus.value.copy(
                                    progress = normalized,
                                    positionMs = player.currentPosition.coerceAtLeast(0L),
                                )
```

In `updateStatus`, replace

```kotlin
        _playbackStatus.value =
            AudioPlaybackStatus(
```

with

```kotlin
        val positionMs =
            when {
                resetProgress -> 0L
                completed && durationMs > 0 -> durationMs
                else -> player.currentPosition.coerceAtLeast(0L)
            }
        _playbackStatus.value =
            AudioPlaybackStatus(
```

and replace

```kotlin
                errorMessage = _playbackStatus.value.errorMessage,
            )
```

with

```kotlin
                errorMessage = _playbackStatus.value.errorMessage,
                positionMs = positionMs,
                playbackSpeed = player.playbackParameters.speed,
                skipSilenceEnabled = skipSilenceEnabled,
            )
```

- [ ] **Step 9: Run the unit tests and the shared module checks to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.client.media.audio.*' :client:media:testAndroidHostTest :client:media:compileKotlinDesktop :client:media:compileKotlinIosSimulatorArm64 :client:media:ktlintCheck :app:wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; the 6 new manager tests and the 10 existing ones pass.

- [ ] **Step 10: Run the GMD tests to verify they pass**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.PlaybackSessionCommandsTest,app.logdate.wear.e2e.WearAudioPlaybackBackgroundE2ETest
```

Expected: `BUILD SUCCESSFUL`. The two session command tests pass. The two playback tests pass, or are skipped with the "no suitable audio output" message when `WearAudioPlaybackBackgroundE2ETest` is skipped for the same reason.

- [ ] **Step 11: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task1-commit.txt <<'EOF'
chore(media): add seeking, speed and skip silence to audio playback

The shared playback manager can now start a recording at a position,
seek to a position in milliseconds, change speed, and skip stretches
of silence, and it reports the position, speed and skip silence in its
status. Speed uses the standard player command every controller has.
Skip silence is an ExoPlayer setting with no player command, so the
playback session offers one custom command for it.

Every playback sets speed and skip silence, so a recording never
inherits the last one's. Existing callers play at normal speed with
silence, as before. The watch's moment player uses these next.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 12: Commit**

```bash
git restore --staged . && git add client/media/src/commonMain/kotlin/app/logdate/client/media/audio/AudioPlaybackOptions.kt client/media/src/commonMain/kotlin/app/logdate/client/media/audio/AudioPlaybackStatus.kt client/media/src/androidMain/kotlin/app/logdate/client/media/audio/PlaybackSessionCommands.kt client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AudioPlaybackService.kt client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioPlaybackManager.kt app/wear/src/test/kotlin/app/logdate/client/media/audio/AndroidAudioPlaybackManagerTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/PlaybackSessionCommandsTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task1-commit.txt
```

---

### Task 2: The moment player (`chore(wear)`)

Every new screen plays through one `MomentPlayer`, so a row in the list and a moment's own screen always agree on what is playing. It fetches audio the watch does not have through a `WatchMediaCache`, the boundary the later prefetch and streaming work store through, which refuses to fill the watch past a 200 MB reserve. A fetch that cannot happen now fails with a reason the screen can say: no speaker or headphones, the audio is on an unreachable phone, no room, or an unreadable file. The crown turns into 5 s detents, and a timed transcript gives a caption for the current position.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/playback/WatchMediaCache.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/playback/WearSyncedAudioResolver.kt` (whole file)
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/PlaybackState.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/MomentPlaybackEngine.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/PlaybackPreferences.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/MomentPlayer.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/CrownScrubber.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/MomentCaption.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/AudioModule.kt:131-140`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (imports and three bindings)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/playback/SyncedAudioWatchMediaCacheTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/playback/PhoneSyncedAudioResolverTest.kt` (modify)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/MomentPlaybackFakes.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/MomentPlayerTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/MomentPlaybackEngineTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/PlaybackPreferencesStoreTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/CrownScrubberTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/MomentCaptionTest.kt`

**Interfaces:**
- Consumes (Task 1): `AudioPlaybackOptions`, `AudioPlaybackStatus.positionMs`, `AndroidAudioPlaybackManager.startPlayback(uri, metadata, options, onProgressUpdated, onPlaybackCompleted)`, `seekToPosition`, `setPlaybackSpeed`, `setSkipSilenceEnabled`, `resumePlayback`.
- Produces:
  - Package `app.logdate.wear.playback`: `interface WatchMediaCache { fun cachedFile(noteId: Uuid): File?; suspend fun store(noteId: Uuid, extension: String, download: suspend (partPath: String) -> Boolean): File?; fun evict(noteId: Uuid): Boolean }`, `class SyncedAudioWatchMediaCache(cache: SyncedAudioCache, freeBytes: suspend () -> Long, reserveBytes: Long = DEFAULT_RESERVE_BYTES)`, `class NoRoomForAudioException(freeBytes: Long, reserveBytes: Long) : IOException`, `class AudioNotOnWatchException(noteId: Uuid) : IOException`; `PhoneSyncedAudioResolver(context, mediaCache: WatchMediaCache, dataLayerClient, notesRepository, ioDispatcher)`.
  - Package `app.logdate.wear.v2.playback`: `sealed interface PlaybackState { Idle; Preparing(noteId: Uuid); Active(noteId: Uuid, positionMs: Long, durationMs: Long, isPaused: Boolean) { val fraction: Float }; Failed(noteId: Uuid, reason: PlaybackFailure) }`, `val PlaybackState.noteId: Uuid?`, `enum class PlaybackFailure { NO_OUTPUT, NOT_ON_WATCH, NO_ROOM, UNPLAYABLE }`, `enum class PlaybackSpeed(factor: Float, storedName: String) { NORMAL, FASTER, FASTEST; fun next(); companion fun fromStored(String?) }`, `data class EngineStatus(noteId: Uuid?, positionMs: Long, durationMs: Long, suppressedForUnsuitableOutput: Boolean)`, `interface MomentPlaybackEngine`, `class AndroidMomentPlaybackEngine(manager, scope)`, `interface PlaybackPreferencesStore { val speed: StateFlow<PlaybackSpeed>; val skipSilence: StateFlow<Boolean>; fun setSpeed(PlaybackSpeed); fun setSkipSilence(Boolean) }`, `class SharedPreferencesPlaybackPreferencesStore(preferences: SharedPreferences)` with `PREFERENCES_NAME = "wear_playback"`, `class MomentPlayer(scope, engine, outputs, resolver, preferences)` with `state: StateFlow<PlaybackState>`, `speed`, `skipSilence`, `playOrPause(JournalNote.Audio)`, `seekBy(Long)`, `seekTo(Long)`, `cycleSpeed()`, `setSkipSilence(Boolean)`, `stop()`, `stopIfPlaying(Uuid)`, `class CrownScrubber(pixelsPerStep: Float = 60f, stepMs: Long = 5_000L)` with `onRotate(pixels: Float): Int` and `reset()`, `fun captionAt(note: JournalNote.Audio, positionMs: Long): String?`, `fun fullTranscript(note: JournalNote.Audio): String?`.
  - Test fakes (package `app.logdate.wear.v2.playback`, test source set): `FakeMomentEngine`, `FakePlaybackPreferences`, `fun voiceNote(durationMs: Long = 42_000L, transcript: TranscriptDocument? = null, transcription: String = ""): JournalNote.Audio`.

- [ ] **Step 1: Write the failing media cache test**

Create `app/wear/src/test/kotlin/app/logdate/wear/playback/SyncedAudioWatchMediaCacheTest.kt`:

```kotlin
package app.logdate.wear.playback

import app.logdate.wear.sync.SyncedAudioCache
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SyncedAudioWatchMediaCacheTest {
    private val directory = Files.createTempDirectory("watch-media").toFile()
    private val noteId = Uuid.random()

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `audio is stored while enough space is free`() =
        runTest {
            val cache = cache(freeBytes = SyncedAudioWatchMediaCache.DEFAULT_RESERVE_BYTES + 1)

            val stored =
                cache.store(noteId, "m4a") { path ->
                    File(path).writeText("audio")
                    true
                }

            assertEquals(File(directory, "$noteId.m4a"), stored)
            assertEquals(stored, cache.cachedFile(noteId))
        }

    @Test
    fun `nothing is fetched when the watch is short of space`() =
        runTest {
            var asked = false

            assertFailsWith<NoRoomForAudioException> {
                cache(freeBytes = 10L).store(noteId, "m4a") {
                    asked = true
                    true
                }
            }

            assertFalse(asked)
            assertNull(cache(freeBytes = 10L).cachedFile(noteId))
        }

    @Test
    fun `evicting removes the stored audio`() =
        runTest {
            val cache = cache(freeBytes = Long.MAX_VALUE)
            cache.store(noteId, "m4a") { path ->
                File(path).writeText("audio")
                true
            }

            assertTrue(cache.evict(noteId))
            assertNull(cache.cachedFile(noteId))
        }

    private fun cache(freeBytes: Long) = SyncedAudioWatchMediaCache(SyncedAudioCache(directory), freeBytes = { freeBytes })
}
```

- [ ] **Step 2: Point the resolver test at the cache boundary and add the typed failures**

In `app/wear/src/test/kotlin/app/logdate/wear/playback/PhoneSyncedAudioResolverTest.kt`:

Add `import kotlin.test.assertIs` after `import kotlin.test.assertEquals`.

Add `    private var freeBytes = Long.MAX_VALUE` after `    private val cache = SyncedAudioCache(cacheDirectory)`.

Replace the whole `buildResolver` function with:

```kotlin
    private fun buildResolver(dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) =
        PhoneSyncedAudioResolver(
            context = context,
            mediaCache = SyncedAudioWatchMediaCache(cache, freeBytes = { freeBytes }),
            dataLayerClient = dataLayerClient,
            notesRepository = notesRepository,
            ioDispatcher = dispatcher,
        )
```

Replace the whole test `fails when the phone is unreachable` with:

```kotlin
    @Test
    fun `fails as not on the watch when the phone is unreachable`() =
        runTest {
            val noteId = Uuid.random()
            coEvery { dataLayerClient.isPhoneConnected() } returns false

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId"))

            assertIs<AudioNotOnWatchException>(result.exceptionOrNull())
            coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any()) }
            coVerify(exactly = 0) { notesRepository.updateMediaRef(any(), any()) }
        }

    @Test
    fun `fails as out of room without asking the phone when the watch is full`() =
        runTest {
            val noteId = Uuid.random()
            freeBytes = 1_024L

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId"))

            assertIs<NoRoomForAudioException>(result.exceptionOrNull())
            coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any()) }
        }
```

- [ ] **Step 3: Write the shared fakes**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/MomentPlaybackFakes.kt`:

```kotlin
package app.logdate.wear.v2.playback

import app.logdate.client.media.audio.AudioPlaybackOptions
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class FakeMomentEngine : MomentPlaybackEngine {
    val starts = mutableListOf<Pair<String, AudioPlaybackOptions>>()
    val seeks = mutableListOf<Long>()
    val speeds = mutableListOf<Float>()
    val skipSilences = mutableListOf<Boolean>()
    var pauses = 0
    var resumes = 0
    var stops = 0
    val mutableStatus = MutableStateFlow(EngineStatus.Idle)
    private var onFinished: (Boolean) -> Unit = {}

    override val status: StateFlow<EngineStatus> = mutableStatus

    override fun start(
        uri: String,
        noteId: Uuid,
        options: AudioPlaybackOptions,
        onFinished: (failed: Boolean) -> Unit,
    ) {
        starts += uri to options
        this.onFinished = onFinished
    }

    override fun pause() {
        pauses++
    }

    override fun resume() {
        resumes++
    }

    override fun stop() {
        stops++
    }

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
    }

    override fun setSpeed(speed: Float) {
        speeds += speed
    }

    override fun setSkipSilence(enabled: Boolean) {
        skipSilences += enabled
    }

    fun finish(failed: Boolean = false) = onFinished(failed)
}

internal class FakePlaybackPreferences(
    speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    skipSilence: Boolean = false,
) : PlaybackPreferencesStore {
    override val speed = MutableStateFlow(speed)
    override val skipSilence = MutableStateFlow(skipSilence)

    override fun setSpeed(speed: PlaybackSpeed) {
        this.speed.value = speed
    }

    override fun setSkipSilence(enabled: Boolean) {
        skipSilence.value = enabled
    }
}

internal val NOTE_TIME: Instant = Instant.fromEpochMilliseconds(1_791_000_000_000)

internal fun voiceNote(
    durationMs: Long = 42_000L,
    transcript: TranscriptDocument? = null,
    transcription: String = "",
): JournalNote.Audio =
    JournalNote.Audio(
        mediaRef = "content://phone/audio/walk.m4a",
        durationMs = durationMs,
        creationTimestamp = NOTE_TIME,
        lastUpdated = NOTE_TIME,
        transcript = transcript,
        transcription = transcription,
    )
```

- [ ] **Step 4: Write the failing player test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/MomentPlayerTest.kt`:

```kotlin
package app.logdate.wear.v2.playback

import app.logdate.client.media.audio.AudioPlaybackOptions
import app.logdate.wear.playback.AudioNotOnWatchException
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.playback.FakeOutputs
import app.logdate.wear.playback.FakeResolver
import app.logdate.wear.playback.NoRoomForAudioException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MomentPlayerTest {
    private val engine = FakeMomentEngine()
    private val outputs = FakeOutputs()
    private val resolver = FakeResolver()
    private val preferences = FakePlaybackPreferences()
    private val note = voiceNote()
    private val other = voiceNote(durationMs = 10_000L)

    @Test
    fun `a voice note plays at the saved speed and skip silence`() =
        runTest(UnconfinedTestDispatcher()) {
            preferences.speed.value = PlaybackSpeed.FASTER
            preferences.skipSilence.value = true

            player().playOrPause(note)

            assertEquals(listOf("/watch/${note.uid}.m4a" to AudioPlaybackOptions(speed = 1.5f, skipSilence = true)), engine.starts)
        }

    @Test
    fun `a playing note is active from its start`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()

            player.playOrPause(note)

            assertEquals(PlaybackState.Active(note.uid, positionMs = 0L, durationMs = 42_000L, isPaused = false), player.state.value)
        }

    @Test
    fun `playing the note again pauses it and once more resumes it`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()

            player.playOrPause(note)
            player.playOrPause(note)
            assertEquals(true, (player.state.value as PlaybackState.Active).isPaused)
            player.playOrPause(note)

            assertEquals(1, engine.pauses)
            assertEquals(1, engine.resumes)
            assertEquals(false, (player.state.value as PlaybackState.Active).isPaused)
        }

    @Test
    fun `nothing plays without a speaker or headphones`() =
        runTest(UnconfinedTestDispatcher()) {
            outputs.state.value = AudioOutputState.Unavailable
            val player = player()

            player.playOrPause(note)

            assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.NO_OUTPUT), player.state.value)
            assertTrue(resolver.resolved.isEmpty())
        }

    @Test
    fun `audio on an unreachable phone fails as not on the watch`() =
        runTest(UnconfinedTestDispatcher()) {
            resolver.failure = AudioNotOnWatchException(note.uid)
            val player = player()

            player.playOrPause(note)

            assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.NOT_ON_WATCH), player.state.value)
            assertTrue(engine.starts.isEmpty())
        }

    @Test
    fun `a full watch fails as out of room`() =
        runTest(UnconfinedTestDispatcher()) {
            resolver.failure = NoRoomForAudioException(freeBytes = 1L, reserveBytes = 2L)
            val player = player()

            player.playOrPause(note)

            assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.NO_ROOM), player.state.value)
        }

    @Test
    fun `the engine's position moves the playing note and no other`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            engine.mutableStatus.value = EngineStatus(other.uid, positionMs = 9_000L, durationMs = 10_000L, suppressedForUnsuitableOutput = false)
            engine.mutableStatus.value = EngineStatus(note.uid, positionMs = 12_000L, durationMs = 41_500L, suppressedForUnsuitableOutput = false)

            assertEquals(PlaybackState.Active(note.uid, positionMs = 12_000L, durationMs = 41_500L, isPaused = false), player.state.value)
        }

    @Test
    fun `seeking stays inside the recording`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            player.seekBy(-5_000L)
            player.seekTo(50_000L)

            assertEquals(listOf(0L, 42_000L), engine.seeks)
        }

    @Test
    fun `a new speed is saved and applies only while playing`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()

            player.cycleSpeed()
            assertEquals(PlaybackSpeed.FASTER, preferences.speed.value)
            assertTrue(engine.speeds.isEmpty())
            player.playOrPause(note)
            player.cycleSpeed()

            assertEquals(PlaybackSpeed.FASTEST, player.speed.value)
            assertEquals(listOf(2f), engine.speeds)
        }

    @Test
    fun `skip silence is saved and applies while playing`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            player.setSkipSilence(true)

            assertEquals(true, player.skipSilence.value)
            assertEquals(listOf(true), engine.skipSilences)
        }

    @Test
    fun `playback the system refuses on this output stops and says so`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            engine.mutableStatus.value = EngineStatus(note.uid, positionMs = 0L, durationMs = 42_000L, suppressedForUnsuitableOutput = true)

            assertEquals(1, engine.stops)
            assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.NO_OUTPUT), player.state.value)
        }

    @Test
    fun `an ended recording returns to idle and a failed one says so`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            engine.finish()
            assertEquals(PlaybackState.Idle, player.state.value)
            player.playOrPause(note)
            engine.finish(failed = true)

            assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.UNPLAYABLE), player.state.value)
        }

    @Test
    fun `playing another note stops the first`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()

            player.playOrPause(note)
            player.playOrPause(other)

            assertEquals(1, engine.stops)
            assertEquals(2, engine.starts.size)
            assertEquals(other.uid, player.state.value.noteId)
        }

    @Test
    fun `stopping for a note that is going away leaves other playback alone`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            player.stopIfPlaying(other.uid)
            assertEquals(note.uid, player.state.value.noteId)
            player.stopIfPlaying(note.uid)

            assertEquals(PlaybackState.Idle, player.state.value)
            assertEquals(1, engine.stops)
        }

    private fun TestScope.player() =
        MomentPlayer(
            scope = backgroundScope,
            engine = engine,
            outputs = outputs,
            resolver = resolver,
            preferences = preferences,
        )
}
```

- [ ] **Step 5: Write the failing engine, preferences, scrubber and caption tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/MomentPlaybackEngineTest.kt`:

```kotlin
package app.logdate.wear.v2.playback

import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class MomentPlaybackEngineTest {
    @Test
    fun `the manager's status names the note and its position`() {
        val noteId = Uuid.random()
        val status =
            AudioPlaybackStatus(
                isPlaying = true,
                duration = 42.seconds,
                metadata = AudioPlaybackMetadata(noteId = noteId),
                isSuppressedForUnsuitableOutput = true,
                positionMs = 5_000L,
            )

        assertEquals(EngineStatus(noteId, positionMs = 5_000L, durationMs = 42_000L, suppressedForUnsuitableOutput = true), status.toEngineStatus())
    }

    @Test
    fun `an idle manager names no note`() {
        assertEquals(EngineStatus.Idle, AudioPlaybackStatus().toEngineStatus())
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/PlaybackPreferencesStoreTest.kt`:

```kotlin
package app.logdate.wear.v2.playback

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals

class PlaybackPreferencesStoreTest {
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val preferences =
        mockk<SharedPreferences> {
            every { edit() } returns editor
            every { getString("speed", null) } returns null
            every { getBoolean("skip_silence", false) } returns false
        }

    @Test
    fun `nothing saved means normal speed with silence`() {
        val store = SharedPreferencesPlaybackPreferencesStore(preferences)

        assertEquals(PlaybackSpeed.NORMAL, store.speed.value)
        assertEquals(false, store.skipSilence.value)
    }

    @Test
    fun `saved choices are read back`() {
        every { preferences.getString("speed", null) } returns "1.5"
        every { preferences.getBoolean("skip_silence", false) } returns true

        val store = SharedPreferencesPlaybackPreferencesStore(preferences)

        assertEquals(PlaybackSpeed.FASTER, store.speed.value)
        assertEquals(true, store.skipSilence.value)
    }

    @Test
    fun `new choices are saved and reported at once`() {
        val store = SharedPreferencesPlaybackPreferencesStore(preferences)

        store.setSpeed(PlaybackSpeed.FASTEST)
        store.setSkipSilence(true)

        verify { editor.putString("speed", "2") }
        verify { editor.putBoolean("skip_silence", true) }
        assertEquals(PlaybackSpeed.FASTEST, store.speed.value)
        assertEquals(true, store.skipSilence.value)
    }

    @Test
    fun `the speeds step from normal to twice and back`() {
        assertEquals(PlaybackSpeed.FASTER, PlaybackSpeed.NORMAL.next())
        assertEquals(PlaybackSpeed.FASTEST, PlaybackSpeed.FASTER.next())
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.FASTEST.next())
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.fromStored("3"))
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/CrownScrubberTest.kt`:

```kotlin
package app.logdate.wear.v2.playback

import org.junit.Test
import kotlin.test.assertEquals

class CrownScrubberTest {
    private val scrubber = CrownScrubber(pixelsPerStep = 60f, stepMs = 5_000L)

    @Test
    fun `less than one detent waits for more rotation`() {
        assertEquals(0, scrubber.onRotate(30f))
        assertEquals(1, scrubber.onRotate(30f))
    }

    @Test
    fun `a fast turn crosses several detents and counts the remainder later`() {
        assertEquals(2, scrubber.onRotate(150f))
        assertEquals(1, scrubber.onRotate(30f))
    }

    @Test
    fun `turning back moves back`() {
        assertEquals(-1, scrubber.onRotate(-60f))
    }

    @Test
    fun `turning back part way cancels part of a forward turn`() {
        assertEquals(0, scrubber.onRotate(50f))
        assertEquals(0, scrubber.onRotate(-20f))
        assertEquals(1, scrubber.onRotate(30f))
    }

    @Test
    fun `reset forgets a part turn`() {
        scrubber.onRotate(50f)

        scrubber.reset()

        assertEquals(0, scrubber.onRotate(30f))
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/playback/MomentCaptionTest.kt`:

```kotlin
package app.logdate.wear.v2.playback

import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSegment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MomentCaptionTest {
    private val timed =
        TranscriptDocument(
            status = TranscriptDocumentStatus.FINAL,
            segments =
                listOf(
                    TranscriptSegment(segmentId = "a", text = "Walked to the lake", startMs = 0L, endMs = 4_000L),
                    TranscriptSegment(segmentId = "b", text = "Saw two herons", startMs = 5_000L, endMs = 9_000L),
                ),
        )

    @Test
    fun `the line spoken at the position is the caption`() {
        assertEquals("Walked to the lake", captionAt(voiceNote(transcript = timed), 2_000L))
        assertEquals("Saw two herons", captionAt(voiceNote(transcript = timed), 6_000L))
    }

    @Test
    fun `a line stays up for a moment after it ends`() {
        assertEquals("Walked to the lake", captionAt(voiceNote(transcript = timed), 4_800L))
    }

    @Test
    fun `nothing shows in a long pause`() {
        assertNull(captionAt(voiceNote(transcript = timed), 12_000L))
    }

    @Test
    fun `an untimed transcript has no captions but is the whole transcript`() {
        val untimed = TranscriptDocument.fromPlainText("A quiet morning")

        assertNull(captionAt(voiceNote(transcript = untimed), 1_000L))
        assertEquals("A quiet morning", fullTranscript(voiceNote(transcript = untimed)))
    }

    @Test
    fun `the plain transcription is used without a transcript document`() {
        assertEquals("Older text", fullTranscript(voiceNote(transcription = "Older text")))
    }

    @Test
    fun `a note without words has no transcript`() {
        assertNull(fullTranscript(voiceNote()))
    }
}
```

- [ ] **Step 6: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.playback.*' --tests 'app.logdate.wear.v2.playback.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'SyncedAudioWatchMediaCache'`, `Unresolved reference 'MomentPlaybackEngine'`, `Unresolved reference 'MomentPlayer'`, `Unresolved reference 'CrownScrubber'` and `Unresolved reference 'captionAt'`.

- [ ] **Step 7: Add the media cache boundary**

Create `app/wear/src/main/kotlin/app/logdate/wear/playback/WatchMediaCache.kt`:

```kotlin
package app.logdate.wear.playback

import app.logdate.wear.sync.SyncedAudioCache
import java.io.File
import java.io.IOException
import kotlin.uuid.Uuid

/**
 * Audio the watch stores for notes it did not record, so they play without the phone.
 *
 * Today audio arrives from the phone when someone plays a note. Prefetching the library window on the
 * charger and streaming by byte range store through this same boundary.
 */
interface WatchMediaCache {
    /** The complete audio stored for [noteId], or null when there is none. */
    fun cachedFile(noteId: Uuid): File?

    /**
     * Stores [noteId]'s audio, written by [download] to the path it is given.
     *
     * @return the complete file, or null when [download] failed.
     * @throws NoRoomForAudioException when the watch is short of space.
     */
    suspend fun store(
        noteId: Uuid,
        extension: String,
        download: suspend (partPath: String) -> Boolean,
    ): File?

    /** Removes [noteId]'s stored audio. @return true when a file was deleted. */
    fun evict(noteId: Uuid): Boolean
}

/** The watch has less free space than audio may use without crowding out new recordings. */
class NoRoomForAudioException(
    freeBytes: Long,
    reserveBytes: Long,
) : IOException("$freeBytes bytes free; audio is stored only above $reserveBytes")

/** A note's audio is on the phone, and no phone is in reach to fetch it from. */
class AudioNotOnWatchException(
    noteId: Uuid,
) : IOException("The audio of note $noteId is on the phone, and no phone is in reach")

/**
 * [WatchMediaCache] in [SyncedAudioCache]'s directory. Audio is stored only while more than
 * [reserveBytes] are free, so playback never takes the space a new recording needs.
 */
class SyncedAudioWatchMediaCache(
    private val cache: SyncedAudioCache,
    private val freeBytes: suspend () -> Long,
    private val reserveBytes: Long = DEFAULT_RESERVE_BYTES,
) : WatchMediaCache {
    override fun cachedFile(noteId: Uuid): File? = cache.cachedFile(noteId)

    override suspend fun store(
        noteId: Uuid,
        extension: String,
        download: suspend (partPath: String) -> Boolean,
    ): File? {
        val free = freeBytes()
        if (free < reserveBytes) throw NoRoomForAudioException(free, reserveBytes)
        return cache.pull(noteId, extension, download)
    }

    override fun evict(noteId: Uuid): Boolean = cache.delete(noteId)

    companion object {
        const val DEFAULT_RESERVE_BYTES = 200L * 1024 * 1024
    }
}
```

- [ ] **Step 8: Resolve through the boundary with typed failures**

Replace `app/wear/src/main/kotlin/app/logdate/wear/playback/WearSyncedAudioResolver.kt` with:

```kotlin
package app.logdate.wear.playback

import android.content.Context
import android.net.Uri
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
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
 * into [mediaCache], or taken from an earlier pull, and the note is pointed at it so the next play
 * needs no transfer.
 *
 * Fails with [AudioNotOnWatchException] when the audio is on an unreachable phone, and with
 * [NoRoomForAudioException] when the watch is too full to store it.
 */
class PhoneSyncedAudioResolver(
    private val context: Context,
    private val mediaCache: WatchMediaCache,
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
                    val file = mediaCache.cachedFile(note.uid) ?: pullFromPhone(note)
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
        if (!dataLayerClient.isPhoneConnected()) throw AudioNotOnWatchException(note.uid)
        val pulled =
            mediaCache.store(note.uid, inferExtension(note.mediaRef)) { partPath ->
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

- [ ] **Step 9: Add the playback state, engine and preferences**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/PlaybackState.kt`:

```kotlin
package app.logdate.wear.v2.playback

import kotlin.uuid.Uuid

/** What the watch is playing, as every screen of the new app sees it. */
sealed interface PlaybackState {
    data object Idle : PlaybackState

    /** The recording is being found or fetched. */
    data class Preparing(
        val noteId: Uuid,
    ) : PlaybackState

    data class Active(
        val noteId: Uuid,
        val positionMs: Long,
        val durationMs: Long,
        val isPaused: Boolean,
    ) : PlaybackState {
        /** How far through the recording playback is, from 0 to 1; 0 while the length is unknown. */
        val fraction: Float
            get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    data class Failed(
        val noteId: Uuid,
        val reason: PlaybackFailure,
    ) : PlaybackState
}

/** The recording this state is about, or null while nothing plays. */
val PlaybackState.noteId: Uuid?
    get() =
        when (this) {
            PlaybackState.Idle -> null
            is PlaybackState.Preparing -> noteId
            is PlaybackState.Active -> noteId
            is PlaybackState.Failed -> noteId
        }

/** Why a recording did not play. */
enum class PlaybackFailure {
    /** No speaker or headphones, or the system refused the speaker. */
    NO_OUTPUT,

    /** The audio is on the phone and no phone is in reach. */
    NOT_ON_WATCH,

    /** The watch is too short of space to fetch the audio. */
    NO_ROOM,

    /** The file could not be read or decoded. */
    UNPLAYABLE,
}

/** The speeds playback offers, in the order a tap steps through them. */
enum class PlaybackSpeed(
    val factor: Float,
    val storedName: String,
) {
    NORMAL(1f, "1"),
    FASTER(1.5f, "1.5"),
    FASTEST(2f, "2"),
    ;

    fun next(): PlaybackSpeed = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The speed saved as [storedName], or [NORMAL] when nothing valid was saved. */
        fun fromStored(storedName: String?): PlaybackSpeed = entries.firstOrNull { it.storedName == storedName } ?: NORMAL
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/MomentPlaybackEngine.kt`:

```kotlin
package app.logdate.wear.v2.playback

import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackOptions
import app.logdate.client.media.audio.AudioPlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.Uuid

/** Where the audio engine is in the recording [noteId], or in nothing while it is null. */
data class EngineStatus(
    val noteId: Uuid?,
    val positionMs: Long,
    val durationMs: Long,
    val suppressedForUnsuitableOutput: Boolean,
) {
    companion object {
        val Idle = EngineStatus(noteId = null, positionMs = 0L, durationMs = 0L, suppressedForUnsuitableOutput = false)
    }
}

/** The audio engine [MomentPlayer] drives. */
interface MomentPlaybackEngine {
    val status: StateFlow<EngineStatus>

    /** Starts [uri]; [onFinished] receives true when playback failed and false when the recording ended. */
    fun start(
        uri: String,
        noteId: Uuid,
        options: AudioPlaybackOptions,
        onFinished: (failed: Boolean) -> Unit,
    )

    fun pause()

    fun resume()

    fun stop()

    fun seekTo(positionMs: Long)

    fun setSpeed(speed: Float)

    fun setSkipSilence(enabled: Boolean)
}

/** [MomentPlaybackEngine] over the shared Media3 playback manager and its session. */
class AndroidMomentPlaybackEngine(
    private val manager: AndroidAudioPlaybackManager,
    scope: CoroutineScope,
) : MomentPlaybackEngine {
    override val status: StateFlow<EngineStatus> =
        manager.playbackStatus
            .map(AudioPlaybackStatus::toEngineStatus)
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, EngineStatus.Idle)

    override fun start(
        uri: String,
        noteId: Uuid,
        options: AudioPlaybackOptions,
        onFinished: (failed: Boolean) -> Unit,
    ) = manager.startPlayback(
        uri = uri,
        metadata = AudioPlaybackMetadata(noteId = noteId),
        options = options,
        onProgressUpdated = {},
        onPlaybackCompleted = { onFinished(manager.playbackStatus.value.errorMessage != null) },
    )

    override fun pause() = manager.pausePlayback()

    override fun resume() = manager.resumePlayback()

    override fun stop() = manager.stopPlayback()

    override fun seekTo(positionMs: Long) = manager.seekToPosition(positionMs)

    override fun setSpeed(speed: Float) = manager.setPlaybackSpeed(speed)

    override fun setSkipSilence(enabled: Boolean) = manager.setSkipSilenceEnabled(enabled)
}

internal fun AudioPlaybackStatus.toEngineStatus(): EngineStatus =
    EngineStatus(
        noteId = metadata?.noteId,
        positionMs = positionMs,
        durationMs = duration.inWholeMilliseconds,
        suppressedForUnsuitableOutput = isSuppressedForUnsuitableOutput,
    )
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/PlaybackPreferences.kt`:

```kotlin
package app.logdate.wear.v2.playback

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The speed and skip silence the next recording plays with. */
interface PlaybackPreferencesStore {
    val speed: StateFlow<PlaybackSpeed>
    val skipSilence: StateFlow<Boolean>

    fun setSpeed(speed: PlaybackSpeed)

    fun setSkipSilence(enabled: Boolean)
}

/** [PlaybackPreferencesStore] in the watch's own SharedPreferences file, [PREFERENCES_NAME]. */
class SharedPreferencesPlaybackPreferencesStore(
    private val preferences: SharedPreferences,
) : PlaybackPreferencesStore {
    private val currentSpeed = MutableStateFlow(PlaybackSpeed.fromStored(preferences.getString(KEY_SPEED, null)))
    private val currentSkipSilence = MutableStateFlow(preferences.getBoolean(KEY_SKIP_SILENCE, false))

    override val speed: StateFlow<PlaybackSpeed> = currentSpeed.asStateFlow()
    override val skipSilence: StateFlow<Boolean> = currentSkipSilence.asStateFlow()

    override fun setSpeed(speed: PlaybackSpeed) {
        preferences.edit { putString(KEY_SPEED, speed.storedName) }
        currentSpeed.value = speed
    }

    override fun setSkipSilence(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SKIP_SILENCE, enabled) }
        currentSkipSilence.value = enabled
    }

    companion object {
        const val PREFERENCES_NAME = "wear_playback"
        private const val KEY_SPEED = "speed"
        private const val KEY_SKIP_SILENCE = "skip_silence"
    }
}
```

- [ ] **Step 10: Add the player, the crown scrubber and captions**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/MomentPlayer.kt`:

```kotlin
package app.logdate.wear.v2.playback

import app.logdate.client.media.audio.AudioPlaybackOptions
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.playback.AudioNotOnWatchException
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.playback.NoRoomForAudioException
import app.logdate.wear.playback.WearAudioOutputs
import app.logdate.wear.playback.WearSyncedAudioResolver
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Plays one recording at a time for every screen of the new watch app, so a row in a list and a
 * moment's own screen always agree on what is playing.
 *
 * A recording the watch does not have is fetched from the phone first. Nothing starts without a
 * speaker or headphones, and playback the system refuses on the current output stops and says so,
 * so a recording never appears to play in silence. Speed and skip silence apply to the recordings
 * after this one too, through [PlaybackPreferencesStore].
 */
class MomentPlayer(
    private val scope: CoroutineScope,
    private val engine: MomentPlaybackEngine,
    private val outputs: WearAudioOutputs,
    private val resolver: WearSyncedAudioResolver,
    private val preferences: PlaybackPreferencesStore,
) {
    private val mutableState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private var resolveJob: Job? = null

    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    val speed: StateFlow<PlaybackSpeed> = preferences.speed
    val skipSilence: StateFlow<Boolean> = preferences.skipSilence

    init {
        scope.launch { engine.status.collect(::onEngineStatus) }
    }

    /** Plays [note] from the start, or pauses or resumes it when it is the recording already playing. */
    fun playOrPause(note: JournalNote.Audio) {
        val current = mutableState.value
        if (current is PlaybackState.Active && current.noteId == note.uid) {
            togglePause(current)
            return
        }
        if (current is PlaybackState.Preparing && current.noteId == note.uid) return
        start(note)
    }

    fun seekBy(deltaMs: Long) {
        val current = mutableState.value as? PlaybackState.Active ?: return
        seekTo(current.positionMs + deltaMs)
    }

    fun seekTo(positionMs: Long) {
        val current = mutableState.value as? PlaybackState.Active ?: return
        val target = if (current.durationMs > 0L) positionMs.coerceIn(0L, current.durationMs) else positionMs.coerceAtLeast(0L)
        engine.seekTo(target)
        mutableState.value = current.copy(positionMs = target)
    }

    /** Steps to the next speed, for this recording and the ones after it. */
    fun cycleSpeed() {
        val next = preferences.speed.value.next()
        preferences.setSpeed(next)
        if (mutableState.value is PlaybackState.Active) engine.setSpeed(next.factor)
    }

    fun setSkipSilence(enabled: Boolean) {
        preferences.setSkipSilence(enabled)
        if (mutableState.value is PlaybackState.Active) engine.setSkipSilence(enabled)
    }

    fun stop() {
        resolveJob?.cancel()
        resolveJob = null
        if (mutableState.value != PlaybackState.Idle) engine.stop()
        mutableState.value = PlaybackState.Idle
    }

    /** Stops playback when it is [noteId]'s, for a recording that is going away. */
    fun stopIfPlaying(noteId: Uuid) {
        if (mutableState.value.noteId == noteId) stop()
    }

    private fun start(note: JournalNote.Audio) {
        resolveJob?.cancel()
        if (mutableState.value is PlaybackState.Active) engine.stop()
        if (outputs.outputState.value is AudioOutputState.Unavailable) {
            Napier.w("No speaker or headphones to play note ${note.uid} on")
            mutableState.value = PlaybackState.Failed(note.uid, PlaybackFailure.NO_OUTPUT)
            return
        }
        mutableState.value = PlaybackState.Preparing(note.uid)
        resolveJob = scope.launch { resolveAndStart(note) }
    }

    private suspend fun resolveAndStart(note: JournalNote.Audio) {
        val uri =
            resolver.resolvePlayableUri(note).getOrElse { error ->
                mutableState.value = PlaybackState.Failed(note.uid, error.toPlaybackFailure())
                return
            }
        val latest = mutableState.value
        if (latest !is PlaybackState.Preparing || latest.noteId != note.uid) return
        mutableState.value = PlaybackState.Active(noteId = note.uid, positionMs = 0L, durationMs = note.durationMs, isPaused = false)
        val options = AudioPlaybackOptions(speed = speed.value.factor, skipSilence = skipSilence.value)
        engine.start(uri, note.uid, options) { failed -> onFinished(note.uid, failed) }
    }

    private fun togglePause(current: PlaybackState.Active) {
        if (current.isPaused) engine.resume() else engine.pause()
        mutableState.value = current.copy(isPaused = !current.isPaused)
    }

    private fun onFinished(
        noteId: Uuid,
        failed: Boolean,
    ) {
        if (mutableState.value.noteId != noteId) return
        mutableState.value = if (failed) PlaybackState.Failed(noteId, PlaybackFailure.UNPLAYABLE) else PlaybackState.Idle
    }

    private fun onEngineStatus(status: EngineStatus) {
        val current = mutableState.value as? PlaybackState.Active ?: return
        if (status.noteId != current.noteId) return
        if (status.suppressedForUnsuitableOutput && !current.isPaused) {
            engine.stop()
            mutableState.value = PlaybackState.Failed(current.noteId, PlaybackFailure.NO_OUTPUT)
            return
        }
        mutableState.value =
            current.copy(
                positionMs = status.positionMs,
                durationMs = status.durationMs.takeIf { it > 0L } ?: current.durationMs,
            )
    }
}

private fun Throwable.toPlaybackFailure(): PlaybackFailure =
    when (this) {
        is AudioNotOnWatchException -> PlaybackFailure.NOT_ON_WATCH
        is NoRoomForAudioException -> PlaybackFailure.NO_ROOM
        else -> PlaybackFailure.UNPLAYABLE
    }
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/CrownScrubber.kt`:

```kotlin
package app.logdate.wear.v2.playback

/**
 * Turns crown rotation into whole seek steps. A step is [pixelsPerStep] of rotation and moves the
 * position by [stepMs]; a part turn counts toward the next step, so slow turns still add up.
 */
class CrownScrubber(
    private val pixelsPerStep: Float = DEFAULT_PIXELS_PER_STEP,
    val stepMs: Long = DEFAULT_STEP_MS,
) {
    private var pending = 0f

    /** The whole steps [pixels] completes: positive forward, negative back. */
    fun onRotate(pixels: Float): Int {
        pending += pixels
        val steps = (pending / pixelsPerStep).toInt()
        pending -= steps * pixelsPerStep
        return steps
    }

    fun reset() {
        pending = 0f
    }

    companion object {
        const val DEFAULT_PIXELS_PER_STEP = 60f
        const val DEFAULT_STEP_MS = 5_000L
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/playback/MomentCaption.kt`:

```kotlin
package app.logdate.wear.v2.playback

import app.logdate.client.repository.journals.JournalNote

/** How long a line stays up after it ends, so short gaps between lines do not flicker. */
const val CAPTION_LINGER_MS = 1_500L

/**
 * The transcript line spoken at [positionMs], or null when the note has no timed transcript or
 * nothing is said there.
 */
fun captionAt(
    note: JournalNote.Audio,
    positionMs: Long,
): String? {
    val timed = note.transcript?.segments.orEmpty().filter { it.endMs > it.startMs && it.text.isNotBlank() }
    val line = timed.lastOrNull { it.startMs <= positionMs } ?: return null
    if (positionMs >= line.endMs + CAPTION_LINGER_MS) return null
    return line.text.trim()
}

/** The whole transcript of [note], or null when it has none. */
fun fullTranscript(note: JournalNote.Audio): String? =
    note.transcript?.plainText?.takeIf(String::isNotBlank) ?: note.transcription.trim().takeIf(String::isNotEmpty)
```

- [ ] **Step 11: Bind the cache, the resolver and the player**

In `app/wear/src/main/kotlin/app/logdate/wear/di/AudioModule.kt`, add `import app.logdate.wear.playback.SyncedAudioWatchMediaCache` after `import app.logdate.wear.playback.PhoneSyncedAudioResolver` and `import app.logdate.wear.playback.WatchMediaCache` after `import app.logdate.wear.playback.WearVoiceNotePlayer`. Then replace

```kotlin
        single<WearSyncedAudioResolver> {
            PhoneSyncedAudioResolver(
                context = get(),
                syncedAudioCache = get(),
```

with

```kotlin
        single<WatchMediaCache> {
            val storage = get<StorageSpaceChecker>()
            SyncedAudioWatchMediaCache(cache = get(), freeBytes = storage::getAvailableStorageSpace)
        }
        single<WearSyncedAudioResolver> {
            PhoneSyncedAudioResolver(
                context = get(),
                mediaCache = get(),
```

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add these imports in ASCII order among the existing ones: `app.logdate.wear.v2.playback.AndroidMomentPlaybackEngine`, `app.logdate.wear.v2.playback.MomentPlaybackEngine`, `app.logdate.wear.v2.playback.MomentPlayer`, `app.logdate.wear.v2.playback.PlaybackPreferencesStore`, `app.logdate.wear.v2.playback.SharedPreferencesPlaybackPreferencesStore`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.SupervisorJob`. Then add, directly before the module's closing brace:

```kotlin

        single<PlaybackPreferencesStore> {
            SharedPreferencesPlaybackPreferencesStore(
                get<Context>().getSharedPreferences(SharedPreferencesPlaybackPreferencesStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single<MomentPlaybackEngine> { AndroidMomentPlaybackEngine(manager = get(), scope = get()) }
        // One player for every screen, on the main thread the media controller needs.
        single {
            MomentPlayer(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                engine = get(),
                outputs = get(),
                resolver = get(),
                preferences = get(),
            )
        }
```

- [ ] **Step 12: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; the new tests in `app.logdate.wear.v2.playback` and `app.logdate.wear.playback` pass, and every v1 test (including `WearVoiceNotePlayerTest`) still passes.

- [ ] **Step 13: Check the Koin graph on the GMD**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.WearDayDetailPlaybackTest
```

Expected: `BUILD SUCCESSFUL`. v1 playback (`WearDayDetailPlaybackTest`) resolves through the new cache boundary.

- [ ] **Step 14: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task2-commit.txt <<'EOF'
chore(wear): add the moment player for the new watch app

Every screen of the new watch app plays through one moment player, so
a row in the list and a moment's own screen agree on what plays. It
remembers speed and skip silence for the next recording, and it fails
with a reason the screen can show: no speaker or headphones, the audio
is on a phone out of reach, the watch is full, or the file is broken.

Audio from the phone is now stored through a media cache that refuses
to use the last 200 MB, so playback never takes the space a new
recording needs. Crown turns become 5 second steps, and a timed
transcript gives the line at the current position.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 15: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/playback/WatchMediaCache.kt app/wear/src/main/kotlin/app/logdate/wear/playback/WearSyncedAudioResolver.kt app/wear/src/main/kotlin/app/logdate/wear/v2/playback app/wear/src/main/kotlin/app/logdate/wear/di/AudioModule.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/playback/SyncedAudioWatchMediaCacheTest.kt app/wear/src/test/kotlin/app/logdate/wear/playback/PhoneSyncedAudioResolverTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/playback && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task2-commit.txt
```

---

### Task 3: Moment detail with crown scrubbing (`chore(wear)`)

A moment opens from its row in the list, or from a `moment` link (the On this day tile and complication use it from Task 13 on). A voice note opens on its player: the position, a ring of progress around the screen, play and pause, speed (1×, 1.5×, 2×) and skip silence, and a caption from a timed transcript. Turning the crown moves through the recording in 5 s steps with a detent tick. Swiping up shows the transcript, the place and Delete, which asks first and deletes on the phone too through the existing sync. Other moments open straight on those details.

**Files:**
- Create: `app/wear/src/main/res/values/strings_moments.xml`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentUiState.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentViewModel.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentScreen.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentRow.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/navigation/WearLaunchRoutes.kt` (one branch and the KDoc)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` (imports, two entries, `HomeEntry`, `FlowEntry`, `MomentEntry`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (one binding)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/moments/MomentViewModelTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/navigation/WearRouteTest.kt` (modify)
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MomentDetailTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ShellNavigationTest.kt` (one test)
- Screenshot: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MomentScreenshots.kt`
- References: `app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/MomentScreenshots/` (new, 18 images)

**Interfaces:**
- Consumes (Task 2): `MomentPlayer`, `PlaybackState`, `PlaybackState.noteId`, `PlaybackSpeed`, `PlaybackFailure`, `CrownScrubber`, `captionAt`, `fullTranscript`. (Predecessor): `momentDayAndTime`, `momentLabel`, `formatClockDuration`, `LogDateWearTheme`, `WearRoute.Moment(id: String)`, `LocalLogDateHaptics`/`LogDateHaptics` (`detentTick()`, `confirmDestruction()`, `actionRejected()`).
- Produces:
  - Package `app.logdate.wear.v2.moments`: `sealed interface MomentUiState { Loading; Missing; Ready(note: JournalNote, timeZone: TimeZone, playback: PlaybackState, speed: PlaybackSpeed, skipSilence: Boolean, deleteFailed: Boolean = false) }`; `class MomentViewModel(noteId: Uuid?, notesRepository, player: MomentPlayer, haptics: LogDateHaptics, timeZone: () -> TimeZone = …)` with `uiState`, `deleted: StateFlow<Boolean>`, `playPause()`, `onCrown(pixels: Float)`, `cycleSpeed()`, `setSkipSilence(Boolean)`, `delete()`; `object MomentTags`; `data class MomentActions(onPlayPause, onCrown, onCycleSpeed, onSkipSilenceChange, onDelete: (() -> Unit)? = null)`; `@Composable fun MomentScreen(state, actions, modifier = Modifier, timeSource = …)`; `@Composable fun MomentDetailsPage(state: MomentUiState.Ready, onDelete: (() -> Unit)?, modifier = Modifier, timeSource = …)`; `internal fun momentBody(note: JournalNote): String?`; `object MomentRowTags { ROW }`; `@Composable fun MomentRow(note: JournalNote, timeZone: TimeZone, onOpen: (() -> Unit)?, modifier: Modifier = Modifier)`.
  - Package `app.logdate.wear.v2.flow`: `data class FlowActions(val onOpenMoment: ((JournalNote) -> Unit)? = null)`; `FlowPage(…, actions: FlowActions = FlowActions())`.
  - `WearLaunchRequest.Moment(noteId)` opens `[WearRoute.Home, WearRoute.Moment(noteId.toString())]`.

- [ ] **Step 1: Add the copy**

Create `app/wear/src/main/res/values/strings_moments.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- One moment, in the new watch app -->
    <string name="wear_moment_open">Open</string>
    <string name="wear_moment_missing">This moment isn’t on your watch anymore</string>
    <string name="wear_moment_play">Play</string>
    <string name="wear_moment_pause">Pause</string>
    <string name="wear_moment_position">%1$s / %2$s</string>
    <string name="wear_moment_speed">%1$s×</string>
    <string name="wear_moment_speed_description">Playback speed %1$s×</string>
    <string name="wear_moment_skip_silence">Skip silence</string>
    <string name="wear_moment_place">Place</string>
    <string name="wear_moment_failed_no_output">No speaker or headphones to play on</string>
    <string name="wear_moment_failed_not_on_watch">This recording is on your phone. Bring your phone nearby to play it.</string>
    <string name="wear_moment_failed_no_room">Not enough space on your watch to play this</string>
    <string name="wear_moment_failed_unplayable">Couldn’t play this recording</string>
    <string name="wear_moment_delete">Delete</string>
    <string name="wear_moment_delete_title">Delete this moment?</string>
    <string name="wear_moment_delete_body">It’s deleted from your phone too.</string>
    <string name="wear_moment_delete_failed">Couldn’t delete this moment</string>
</resources>
```

- [ ] **Step 2: Write the failing view model test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/moments/MomentViewModelTest.kt`:

```kotlin
package app.logdate.wear.v2.moments

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.playback.FakeOutputs
import app.logdate.wear.playback.FakeResolver
import app.logdate.wear.v2.playback.FakeMomentEngine
import app.logdate.wear.v2.playback.FakePlaybackPreferences
import app.logdate.wear.v2.playback.MomentPlayer
import app.logdate.wear.v2.playback.NOTE_TIME
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.playback.voiceNote
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MomentViewModelTest {
    private val note = voiceNote()
    private val text = JournalNote.Text(creationTimestamp = NOTE_TIME, lastUpdated = NOTE_TIME, content = "Coffee at the lake")
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val haptics = mockk<LogDateHaptics>(relaxed = true)
    private val engine = FakeMomentEngine()
    private val zone = TimeZone.of("Europe/Berlin")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { notesRepository.getNoteById(any()) } returns null
        coEvery { notesRepository.getNoteById(note.uid) } returns note
        coEvery { notesRepository.getNoteById(text.uid) } returns text
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a voice note opens ready to play`() =
        runTest {
            viewModel(player()).uiState.test {
                assertEquals(
                    MomentUiState.Ready(note, zone, PlaybackState.Idle, PlaybackSpeed.NORMAL, skipSilence = false),
                    expectMostRecentItem(),
                )
            }
        }

    @Test
    fun `a text moment opens on its details`() =
        runTest {
            viewModel(player(), id = text.uid).uiState.test {
                assertEquals(text, (expectMostRecentItem() as MomentUiState.Ready).note)
            }
        }

    @Test
    fun `an unknown or unreadable ID is missing`() =
        runTest {
            viewModel(player(), id = Uuid.random()).uiState.test { assertEquals(MomentUiState.Missing, expectMostRecentItem()) }
            viewModel(player(), id = null).uiState.test { assertEquals(MomentUiState.Missing, expectMostRecentItem()) }
        }

    @Test
    fun `play starts this note and the state follows it`() =
        runTest {
            val viewModel = viewModel(player())

            viewModel.uiState.test {
                viewModel.playPause()

                val playback = (expectMostRecentItem() as MomentUiState.Ready).playback
                assertEquals(PlaybackState.Active(note.uid, positionMs = 0L, durationMs = 42_000L, isPaused = false), playback)
            }
        }

    @Test
    fun `another recording playing shows this one idle`() =
        runTest {
            val player = player()
            val viewModel = viewModel(player)

            viewModel.uiState.test {
                player.playOrPause(voiceNote(durationMs = 5_000L))

                assertEquals(PlaybackState.Idle, (expectMostRecentItem() as MomentUiState.Ready).playback)
            }
        }

    @Test
    fun `the crown seeks in 5 second steps with one tick a turn`() =
        runTest {
            val viewModel = viewModel(player())
            viewModel.playPause()

            viewModel.onCrown(60f)
            viewModel.onCrown(30f)

            assertEquals(listOf(5_000L), engine.seeks)
            verify(exactly = 1) { haptics.detentTick() }
        }

    @Test
    fun `the crown does nothing while this note is not playing`() =
        runTest {
            val viewModel = viewModel(player())

            viewModel.onCrown(120f)

            assertTrue(engine.seeks.isEmpty())
            verify(exactly = 0) { haptics.detentTick() }
        }

    @Test
    fun `deleting removes the note and its playback and closes the screen`() =
        runTest {
            val viewModel = viewModel(player())
            viewModel.playPause()

            viewModel.uiState.test {
                viewModel.delete()

                assertEquals(MomentUiState.Missing, expectMostRecentItem())
            }
            coVerify { notesRepository.remove(note) }
            assertEquals(1, engine.stops)
            verify { haptics.confirmDestruction() }
            assertTrue(viewModel.deleted.value)
        }

    @Test
    fun `a failed delete stays open and says so`() =
        runTest {
            coEvery { notesRepository.remove(any()) } throws IllegalStateException("database closed")
            val viewModel = viewModel(player())

            viewModel.uiState.test {
                viewModel.delete()

                assertTrue((expectMostRecentItem() as MomentUiState.Ready).deleteFailed)
            }
            assertFalse(viewModel.deleted.value)
            verify { haptics.actionRejected() }
        }

    private fun TestScope.player() =
        MomentPlayer(
            scope = backgroundScope,
            engine = engine,
            outputs = FakeOutputs(),
            resolver = FakeResolver(),
            preferences = FakePlaybackPreferences(),
        )

    private fun viewModel(
        player: MomentPlayer,
        id: Uuid? = note.uid,
    ) = MomentViewModel(
        noteId = id,
        notesRepository = notesRepository,
        player = player,
        haptics = haptics,
        timeZone = { zone },
    )
}
```

- [ ] **Step 3: Write the failing route test**

In `app/wear/src/test/kotlin/app/logdate/wear/v2/navigation/WearRouteTest.kt`, delete the line `                WearLaunchRequest.Moment(Uuid.random()),` from the second test's list, and add this test after it:

```kotlin
    @Test
    fun `a moment link opens the moment above Today`() {
        val noteId = Uuid.random()

        assertEquals(
            listOf(WearRoute.Home, WearRoute.Moment(noteId.toString())),
            WearLaunchRequest.Moment(noteId).toV2BackStack(),
        )
    }
```

- [ ] **Step 4: Run the unit tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.moments.*' --tests 'app.logdate.wear.v2.navigation.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'MomentViewModel'` and `Unresolved reference 'MomentUiState'`.

- [ ] **Step 5: Write the failing GMD tests**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MomentDetailTest.kt`:

```kotlin
package app.logdate.wear.e2e

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.wear.v2.moments.MomentActions
import app.logdate.wear.v2.moments.MomentScreen
import app.logdate.wear.v2.moments.MomentTags
import app.logdate.wear.v2.moments.MomentUiState
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.theme.LogDateWearTheme
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Drives a moment's screen with touch and the crown on a Wear OS emulator. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MomentDetailTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val at = Instant.fromEpochMilliseconds(1_791_000_000_000)
    private val voice =
        JournalNote.Audio(
            mediaRef = "walk.m4a",
            durationMs = 42_000L,
            creationTimestamp = at,
            lastUpdated = at,
            transcription = "Walked to the lake",
            location = NoteLocation(place = NotePlace(Uuid.random(), "Golden Gate Park", 37.77, -122.45)),
        )
    private val text = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Coffee before anyone else was up")

    @Test
    fun a_voice_moment_opens_on_its_player() {
        var plays = 0
        show(ready(voice), MomentActions(onPlayPause = { plays++ }))

        composeRule.onNodeWithTag(MomentTags.POSITION).assertTextEquals("0:00 / 0:42")
        composeRule.onNodeWithTag(MomentTags.PLAY_PAUSE).performClick()

        assertEquals(1, plays)
    }

    @Test
    fun turning_the_crown_on_the_player_scrubs() {
        val turns = mutableListOf<Float>()
        show(
            ready(voice, PlaybackState.Active(voice.uid, positionMs = 12_000L, durationMs = 42_000L, isPaused = false)),
            MomentActions(onCrown = { turns += it }),
        )

        composeRule.onNodeWithTag(MomentTags.PLAYER).performRotaryScrollInput { rotateToScrollVertically(CROWN_STEP) }
        composeRule.waitForIdle()

        assertTrue(turns.sum() > 0f, "turns $turns")
    }

    @Test
    fun speed_and_skip_silence_report_taps() {
        var speedTaps = 0
        val skips = mutableListOf<Boolean>()
        show(ready(voice), MomentActions(onCycleSpeed = { speedTaps++ }, onSkipSilenceChange = { skips += it }))

        composeRule.onNodeWithTag(MomentTags.SPEED).performClick()
        composeRule.onNodeWithTag(MomentTags.SKIP_SILENCE).performClick()

        assertEquals(1, speedTaps)
        assertEquals(listOf(true), skips)
    }

    @Test
    fun swiping_up_shows_the_details_and_delete_asks_first() {
        var deletes = 0
        show(ready(voice), MomentActions(onDelete = { deletes++ }))

        composeRule.onNodeWithTag(MomentTags.SCREEN).performTouchInput { swipeUp() }
        waitForTag(MomentTags.DETAILS)
        composeRule.onNodeWithText("Walked to the lake").assertIsDisplayed()
        composeRule.onNodeWithTag(MomentTags.DETAILS).performRotaryScrollInput { rotateToScrollVertically(LONG_SCROLL) }
        waitForTag(MomentTags.DELETE)
        composeRule.onNodeWithTag(MomentTags.DELETE).performClick()
        assertEquals(0, deletes)
        waitForTag(MomentTags.CONFIRM_DELETE)
        composeRule.onNodeWithTag(MomentTags.CONFIRM_DELETE).performClick()
        composeRule.waitForIdle()

        assertEquals(1, deletes)
    }

    @Test
    fun a_text_moment_opens_on_its_details() {
        show(ready(text), MomentActions())

        composeRule.onNodeWithText("Coffee before anyone else was up").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(MomentTags.PLAYER).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag(MomentTags.DELETE).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun a_missing_moment_says_so() {
        show(MomentUiState.Missing, MomentActions())

        composeRule.onNodeWithTag(MomentTags.MISSING).assertIsDisplayed()
    }

    private fun ready(
        note: JournalNote,
        playback: PlaybackState = PlaybackState.Idle,
    ) = MomentUiState.Ready(note, TimeZone.currentSystemDefault(), playback, PlaybackSpeed.NORMAL, skipSilence = false)

    private fun show(
        state: MomentUiState,
        actions: MomentActions,
    ) {
        composeRule.setContent { Scene { MomentScreen(state = state, actions = actions, timeSource = FixedTime) } }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val CROWN_STEP = 120f
        const val LONG_SCROLL = 600f
        const val TIMEOUT_MS = 5_000L
    }
}

@Composable
private fun Scene(content: @Composable () -> Unit) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) { content() }
    }
}

private object FixedTime : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
```

In `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ShellNavigationTest.kt`, add these imports in ASCII order: `androidx.compose.ui.test.onNodeWithText`, `androidx.test.core.app.ApplicationProvider`, `app.logdate.client.repository.journals.JournalNote`, `app.logdate.client.repository.journals.JournalNotesRepository`, `app.logdate.wear.v2.moments.MomentTags`, `kotlin.time.Clock`. Add this test after `a_recreated_activity_comes_back_to_today`:

```kotlin
    @Test
    fun a_moment_link_opens_that_moment() {
        val now = Clock.System.now()
        val note = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "A moment opened from a link")
        val notes = KoinJavaComponent.getKoin().get<JournalNotesRepository>()
        runBlocking { notes.create(note) }
        try {
            val link =
                Intent(ApplicationProvider.getApplicationContext<Context>(), MainActivity::class.java)
                    .putExtra(WearLaunchRequest.EXTRA_ROUTE, "moment")
                    .putExtra(WearLaunchRequest.EXTRA_NOTE_ID, note.uid.toString())
            scenario = ActivityScenario.launch(link)

            waitFor { composeRule.onAllNodesWithTag(MomentTags.DETAILS) }
            composeRule.onNodeWithText("A moment opened from a link").assertIsDisplayed()
        } finally {
            runBlocking { notes.removeById(note.uid) }
        }
    }
```

- [ ] **Step 6: Run the GMD tests to verify they fail**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.MomentDetailTest,app.logdate.wear.e2e.ShellNavigationTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'MomentScreen'` and `Unresolved reference 'MomentTags'`.

- [ ] **Step 7: Add the state and the view model**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentUiState.kt`:

```kotlin
package app.logdate.wear.v2.moments

import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.PlaybackState
import kotlinx.datetime.TimeZone

/** What a moment's screen shows. */
sealed interface MomentUiState {
    data object Loading : MomentUiState

    /** The moment is not on this watch: deleted, removed on the phone, or never synced here. */
    data object Missing : MomentUiState

    /**
     * @property playback This moment's playback; [PlaybackState.Idle] while another recording plays.
     * @property deleteFailed Deleting it was tried and did not work.
     */
    data class Ready(
        val note: JournalNote,
        val timeZone: TimeZone,
        val playback: PlaybackState,
        val speed: PlaybackSpeed,
        val skipSilence: Boolean,
        val deleteFailed: Boolean = false,
    ) : MomentUiState
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentViewModel.kt`:

```kotlin
package app.logdate.wear.v2.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.v2.playback.CrownScrubber
import app.logdate.wear.v2.playback.MomentPlayer
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.playback.noteId
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.uuid.Uuid

/**
 * One moment: its note, how it plays, and deleting it. [noteId] is null for a link whose ID could
 * not be read, which shows the moment as missing.
 */
class MomentViewModel(
    private val noteId: Uuid?,
    private val notesRepository: JournalNotesRepository,
    private val player: MomentPlayer,
    private val haptics: LogDateHaptics,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {
    private val load = MutableStateFlow<NoteLoad>(NoteLoad.Loading)
    private val deleteFailed = MutableStateFlow(false)
    private val mutableDeleted = MutableStateFlow(false)
    private val scrubber = CrownScrubber()

    /** True once the moment is deleted, so its screen closes. */
    val deleted: StateFlow<Boolean> = mutableDeleted.asStateFlow()

    val uiState: StateFlow<MomentUiState> =
        combine(load, player.state, player.speed, player.skipSilence, deleteFailed) { loaded, playback, speed, skipSilence, failed ->
            when (loaded) {
                NoteLoad.Loading -> MomentUiState.Loading
                NoteLoad.Missing -> MomentUiState.Missing
                is NoteLoad.Found ->
                    MomentUiState.Ready(
                        note = loaded.note,
                        timeZone = timeZone(),
                        playback = playback.takeIf { it.noteId == loaded.note.uid } ?: PlaybackState.Idle,
                        speed = speed,
                        skipSilence = skipSilence,
                        deleteFailed = failed,
                    )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MomentUiState.Loading)

    init {
        viewModelScope.launch { load.value = loadNote() }
    }

    fun playPause() {
        val audio = foundNote() as? JournalNote.Audio ?: return
        player.playOrPause(audio)
    }

    /** Moves through the playing recording, with one detent tick for each turn that crosses a step. */
    fun onCrown(pixels: Float) {
        val steps = scrubber.onRotate(pixels)
        if (steps == 0) return
        val playing = player.state.value
        if (playing !is PlaybackState.Active || playing.noteId != noteId) return
        player.seekBy(steps * scrubber.stepMs)
        haptics.detentTick()
    }

    fun cycleSpeed() = player.cycleSpeed()

    fun setSkipSilence(enabled: Boolean) = player.setSkipSilence(enabled)

    /** Deletes the moment here and, through sync, on the phone. */
    fun delete() {
        val note = foundNote() ?: return
        viewModelScope.launch {
            player.stopIfPlaying(note.uid)
            if (!remove(note)) {
                haptics.actionRejected()
                deleteFailed.value = true
                return@launch
            }
            haptics.confirmDestruction()
            load.value = NoteLoad.Missing
            mutableDeleted.value = true
        }
    }

    private fun foundNote(): JournalNote? = (load.value as? NoteLoad.Found)?.note

    private suspend fun loadNote(): NoteLoad {
        val id = noteId ?: return NoteLoad.Missing
        val note =
            try {
                notesRepository.getNoteById(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Couldn't read moment $id on the watch", e)
                null
            }
        return note?.let(NoteLoad::Found) ?: NoteLoad.Missing
    }

    private suspend fun remove(note: JournalNote): Boolean =
        try {
            notesRepository.remove(note)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't delete moment ${note.uid} on the watch", e)
            false
        }

    private sealed interface NoteLoad {
        data object Loading : NoteLoad

        data object Missing : NoteLoad

        data class Found(
            val note: JournalNote,
        ) : NoteLoad
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 8: Add the row and the screen**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentRow.kt`:

```kotlin
package app.logdate.wear.v2.moments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import kotlinx.datetime.TimeZone

object MomentRowTags {
    const val ROW = "moment_row"
}

/** A moment in a list: when it was, and what it is in a few words. With [onOpen] the row opens it. */
@Composable
fun MomentRow(
    note: JournalNote,
    timeZone: TimeZone,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.wear_moment_open)
    val opens = if (onOpen == null) Modifier else Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .then(opens)
                .padding(vertical = 4.dp)
                .testTag(MomentRowTags.ROW),
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

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentScreen.kt`:

```kotlin
package app.logdate.wear.v2.moments

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.VerticalPagerScaffold
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.v2.playback.PlaybackFailure
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.playback.captionAt
import app.logdate.wear.v2.playback.fullTranscript
import java.text.NumberFormat
import kotlin.time.Duration.Companion.milliseconds

object MomentTags {
    const val SCREEN = "moment_screen"
    const val PLAYER = "moment_player"
    const val POSITION = "moment_position"
    const val PLAY_PAUSE = "moment_play_pause"
    const val SPEED = "moment_speed"
    const val SKIP_SILENCE = "moment_skip_silence"
    const val CAPTION = "moment_caption"
    const val DETAILS = "moment_details"
    const val DELETE = "moment_delete"
    const val CONFIRM_DELETE = "moment_confirm_delete"
    const val MISSING = "moment_missing"
}

/** What the person can do on a moment's screen. Delete appears only with [onDelete]. */
data class MomentActions(
    val onPlayPause: () -> Unit = {},
    val onCrown: (Float) -> Unit = {},
    val onCycleSpeed: () -> Unit = {},
    val onSkipSilenceChange: (Boolean) -> Unit = {},
    val onDelete: (() -> Unit)? = null,
)

/**
 * One moment. A voice note opens on its player, where turning the crown moves through the
 * recording; swiping up shows its transcript, place and actions. Any other moment opens on those
 * details.
 */
@Composable
fun MomentScreen(
    state: MomentUiState,
    actions: MomentActions,
    modifier: Modifier = Modifier,
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
) {
    Box(modifier = modifier.fillMaxSize().testTag(MomentTags.SCREEN)) {
        when (state) {
            MomentUiState.Loading -> Unit
            MomentUiState.Missing -> MissingMoment(timeSource)
            is MomentUiState.Ready -> ReadyMoment(state, actions, timeSource)
        }
    }
}

/** A moment's details: when, its words, its place, and Delete when [onDelete] is set. */
@Composable
fun MomentDetailsPage(
    state: MomentUiState.Ready,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val note = state.note
    val body = momentBody(note)
    val place = note.location?.displayName
    ScreenScaffold(scrollState = listState, timeText = { TimeText(timeSource = timeSource) }) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier.testTag(MomentTags.DETAILS)) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(momentDayAndTime(note.creationTimestamp, state.timeZone))
                }
            }
            if (body != null) item { DetailText(body) }
            if (place != null) item { PlaceLine(place) }
            if (state.deleteFailed) item { DetailText(stringResource(R.string.wear_moment_delete_failed)) }
            if (onDelete != null) {
                item { DeleteButton(onDelete, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec)) }
            }
        }
    }
}

/** What a moment says: its text, transcript or caption; null when it has no words. */
internal fun momentBody(note: JournalNote): String? =
    when (note) {
        is JournalNote.Text -> note.content.trim().takeIf(String::isNotEmpty)
        is JournalNote.Audio -> fullTranscript(note)
        is JournalNote.Image -> note.caption.trim().takeIf(String::isNotEmpty)
        is JournalNote.Video -> note.caption.trim().takeIf(String::isNotEmpty)
    }

@Composable
private fun ReadyMoment(
    state: MomentUiState.Ready,
    actions: MomentActions,
    timeSource: TimeSource,
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val askToDelete: (() -> Unit)? = actions.onDelete?.let { { confirmingDelete = true } }
    val note = state.note
    if (note is JournalNote.Audio) {
        VoiceMoment(note, state, actions, askToDelete, timeSource)
    } else {
        MomentDetailsPage(state, askToDelete, timeSource = timeSource)
    }
    DeleteDialog(
        visible = confirmingDelete,
        onDismiss = { confirmingDelete = false },
        onConfirm = {
            confirmingDelete = false
            actions.onDelete?.invoke()
        },
    )
}

@Composable
private fun VoiceMoment(
    note: JournalNote.Audio,
    state: MomentUiState.Ready,
    actions: MomentActions,
    onDelete: (() -> Unit)?,
    timeSource: TimeSource,
) {
    val pagerState = rememberPagerState(pageCount = { VOICE_PAGES })
    VerticalPagerScaffold(pagerState = pagerState) {
        VerticalPager(state = pagerState, rotaryScrollableBehavior = null) { page ->
            AnimatedPage(page, pagerState) {
                if (page == PLAYER_PAGE) {
                    MomentPlayerPage(note, state, actions, timeSource)
                } else {
                    MomentDetailsPage(state, onDelete, timeSource = timeSource)
                }
            }
        }
    }
}

@Composable
private fun MomentPlayerPage(
    note: JournalNote.Audio,
    state: MomentUiState.Ready,
    actions: MomentActions,
    timeSource: TimeSource,
) {
    val active = state.playback as? PlaybackState.Active
    val positionMs = active?.positionMs ?: 0L
    val durationMs = active?.durationMs?.takeIf { it > 0L } ?: note.durationMs
    val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    ScreenScaffold(timeText = { TimeText(timeSource = timeSource) }) { _ ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent { event ->
                        actions.onCrown(event.verticalScrollPixels)
                        true
                    }.requestFocusOnHierarchyActive()
                    .focusable()
                    .testTag(MomentTags.PLAYER),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxSize().padding(PROGRESS_INSET),
                strokeWidth = PROGRESS_STROKE,
            )
            Column(
                modifier = Modifier.padding(horizontal = PLAYER_SIDE_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = momentDayAndTime(note.creationTimestamp, state.timeZone),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        stringResource(
                            R.string.wear_moment_position,
                            formatClockDuration(positionMs.milliseconds),
                            formatClockDuration(durationMs.milliseconds),
                        ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag(MomentTags.POSITION),
                )
                PlayerControls(state, actions)
                PlayerLine(note, state.playback, positionMs)
            }
        }
    }
}

@Composable
private fun PlayerControls(
    state: MomentUiState.Ready,
    actions: MomentActions,
) {
    val playing = (state.playback as? PlaybackState.Active)?.isPaused == false
    val speedText = speedLabel(state.speed)
    val speedDescription = stringResource(R.string.wear_moment_speed_description, speedText)
    Row(
        modifier = Modifier.padding(vertical = CONTROL_SPACING),
        horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactButton(
            onClick = actions.onCycleSpeed,
            modifier = Modifier.testTag(MomentTags.SPEED).semantics { contentDescription = speedDescription },
            label = { Text(speedText) },
        )
        FilledIconButton(
            onClick = actions.onPlayPause,
            enabled = state.playback !is PlaybackState.Preparing,
            modifier = Modifier.size(IconButtonDefaults.DefaultButtonSize).testTag(MomentTags.PLAY_PAUSE),
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.wear_moment_pause else R.string.wear_moment_play),
            )
        }
        IconToggleButton(
            checked = state.skipSilence,
            onCheckedChange = actions.onSkipSilenceChange,
            modifier = Modifier.size(IconButtonDefaults.SmallButtonSize).testTag(MomentTags.SKIP_SILENCE),
        ) {
            Icon(imageVector = Icons.Filled.FastForward, contentDescription = stringResource(R.string.wear_moment_skip_silence))
        }
    }
}

@Composable
private fun PlayerLine(
    note: JournalNote.Audio,
    playback: PlaybackState,
    positionMs: Long,
) {
    val text = if (playback is PlaybackState.Failed) failureMessage(playback.reason) else captionAt(note, positionMs)
    if (text == null) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(MomentTags.CAPTION),
    )
}

@Composable
private fun failureMessage(reason: PlaybackFailure): String =
    stringResource(
        when (reason) {
            PlaybackFailure.NO_OUTPUT -> R.string.wear_moment_failed_no_output
            PlaybackFailure.NOT_ON_WATCH -> R.string.wear_moment_failed_not_on_watch
            PlaybackFailure.NO_ROOM -> R.string.wear_moment_failed_no_room
            PlaybackFailure.UNPLAYABLE -> R.string.wear_moment_failed_unplayable
        },
    )

/** [speed] as the person reads it, such as "1.5×" or "1,5×", in their locale. */
@Composable
private fun speedLabel(speed: PlaybackSpeed): String {
    val locale = LocalConfiguration.current.locales[0]
    val number =
        remember(locale, speed) {
            NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(speed.factor.toDouble())
        }
    return stringResource(R.string.wear_moment_speed, number)
}

@Composable
private fun MissingMoment(timeSource: TimeSource) {
    ScreenScaffold(timeText = { TimeText(timeSource = timeSource) }) { _ ->
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = MESSAGE_PADDING), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.wear_moment_missing),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(MomentTags.MISSING),
            )
        }
    }
}

@Composable
private fun DetailText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PlaceLine(place: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = stringResource(R.string.wear_moment_place),
            modifier = Modifier.size(PLACE_ICON_SIZE),
        )
        Text(
            text = place,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun DeleteButton(
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(MomentTags.DELETE),
        transformation = transformation,
        icon = { Icon(imageVector = Icons.Filled.Delete, contentDescription = null) },
        label = { Text(stringResource(R.string.wear_moment_delete)) },
    )
}

@Composable
private fun DeleteDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = onConfirm, modifier = Modifier.testTag(MomentTags.CONFIRM_DELETE)) },
        title = { Text(stringResource(R.string.wear_moment_delete_title)) },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = onDismiss) },
        text = { Text(stringResource(R.string.wear_moment_delete_body)) },
    )
}

private const val VOICE_PAGES = 2
private const val PLAYER_PAGE = 0
private val PROGRESS_INSET = 2.dp
private val PROGRESS_STROKE = 4.dp
private val PLAYER_SIDE_PADDING = 12.dp
private val CONTROL_SPACING = 6.dp
private val PLACE_ICON_SIZE = 16.dp
private val MESSAGE_PADDING = 24.dp
```

- [ ] **Step 9: Open moments from the list**

Replace `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt` with:

```kotlin
package app.logdate.wear.v2.flow

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import app.logdate.wear.v2.moments.MomentRow

object FlowTags {
    const val LIST = "flow_list"
}

/** What the moment list lets the person do. Each control appears only when its handler is set. */
data class FlowActions(
    val onOpenMoment: ((JournalNote) -> Unit)? = null,
)

/** Home's second page: the moments of the last 7 days, newest first. */
@Composable
fun FlowPage(
    state: FlowUiState,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
    actions: FlowActions = FlowActions(),
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
                is FlowUiState.Ready -> momentItems(state, actions)
            }
        }
    }
}

private fun TransformingLazyColumnScope.momentItems(
    state: FlowUiState.Ready,
    actions: FlowActions,
) {
    if (state.moments.isEmpty()) {
        item { FlowMessage(stringResource(R.string.wear_flow_empty)) }
        return
    }
    items(state.moments.size, key = { index -> state.moments[index].uid.toString() }) { index ->
        val note = state.moments[index]
        MomentRow(note, state.timeZone, onOpen = actions.onOpenMoment?.let { open -> { open(note) } })
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
```

- [ ] **Step 10: Route moment links and open moments in the shell**

In `app/wear/src/main/kotlin/app/logdate/wear/v2/navigation/WearLaunchRoutes.kt`, replace the KDoc with:

```kotlin
/**
 * The back stack the new watch app opens for this request: the screen the request names above
 * Home, so going back lands on Today. A request whose screen does not exist yet opens Today.
 */
```

Then delete the line `        is WearLaunchRequest.Moment,` from the branch that returns `listOf(WearRoute.Home)`, and add this as the last branch of the `when`:

```kotlin
        is WearLaunchRequest.Moment -> listOf(WearRoute.Home, WearRoute.Moment(noteId.toString()))
```

In `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt`:

Add these imports in ASCII order: `app.logdate.wear.v2.flow.FlowActions`, `app.logdate.wear.v2.moments.MomentActions`, `app.logdate.wear.v2.moments.MomentScreen`, `app.logdate.wear.v2.moments.MomentViewModel`, `org.koin.core.parameter.parametersOf`.

In the `entryProvider { … }` block, make the Home entry pass navigation and add the Moment entry directly after it:

```kotlin
                                entry<WearRoute.Home> {
                                    HomeEntry(
                                        recorder,
                                        marker,
                                        onAdd = { backStack.add(WearRoute.CaptureSheet) },
                                        navigate = { route -> backStack.add(route) },
                                    )
                                }
                                entry<WearRoute.Moment> { route -> MomentEntry(route.id, onClose = { backStack.removeLastOrNull() }) }
```

This is the recorder plan's Home entry (`HomeEntry(recorder, marker, onAdd = …)`, from its Task 10) with `navigate` added. If the landed entry's arguments differ, leave them as they are and add `navigate = { route -> backStack.add(route) }` to them.

Add the parameter `navigate: (WearRoute) -> Unit` as the last parameter of `HomeEntry`, and change its `flow = { listState -> FlowEntry(listState) }` argument to `flow = { listState -> FlowEntry(listState, navigate) }`.

Replace the whole `FlowEntry` function with:

```kotlin
@Composable
private fun FlowEntry(
    listState: TransformingLazyColumnState,
    navigate: (WearRoute) -> Unit,
) {
    val viewModel = koinViewModel<FlowViewModel>()
    val state by viewModel.uiState.collectAsState()
    FlowPage(
        state = state,
        listState = listState,
        actions = FlowActions(onOpenMoment = { note -> navigate(WearRoute.Moment(note.uid.toString())) }),
    )
}
```

Add this function after `FlowEntry`:

```kotlin
@Composable
private fun MomentEntry(
    id: String,
    onClose: () -> Unit,
) {
    val viewModel = koinViewModel<MomentViewModel>(parameters = { parametersOf(id) })
    val state by viewModel.uiState.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    LaunchedEffect(deleted) {
        if (deleted) onClose()
    }
    MomentScreen(
        state = state,
        actions =
            MomentActions(
                onPlayPause = viewModel::playPause,
                onCrown = viewModel::onCrown,
                onCycleSpeed = viewModel::cycleSpeed,
                onSkipSilenceChange = viewModel::setSkipSilence,
                onDelete = viewModel::delete,
            ),
    )
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add `import app.logdate.wear.v2.moments.MomentViewModel` and `import kotlin.uuid.Uuid` in ASCII order, and add before the module's closing brace:

```kotlin
        viewModel { params ->
            MomentViewModel(
                noteId = Uuid.parseOrNull(params.get<String>()),
                notesRepository = get(),
                player = get(),
                haptics = get(),
            )
        }
```

- [ ] **Step 11: Run the unit tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.*' :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; the 9 view model tests and the new route test pass.

- [ ] **Step 12: Run the GMD tests to verify they pass**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.MomentDetailTest,app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.HomePagerRotaryTest
```

Expected: `BUILD SUCCESSFUL`, 6 + 4 + 5 tests passed. If `turning_the_crown_on_the_player_scrubs` sees no rotation, the player box has no focus: check that `requestFocusOnHierarchyActive()` comes before `focusable()` and that the page sits inside `AnimatedPage` in `VerticalPagerScaffold`, before changing anything else.

- [ ] **Step 13: Write the moment screenshot scenes**

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MomentScreenshots.kt`:

```kotlin
package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSegment
import app.logdate.wear.v2.moments.MomentActions
import app.logdate.wear.v2.moments.MomentDetailsPage
import app.logdate.wear.v2.moments.MomentScreen
import app.logdate.wear.v2.moments.MomentUiState
import app.logdate.wear.v2.playback.PlaybackFailure
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.uuid.Uuid

/** Every state of a moment's screen, on the graphite theme. */
class MomentScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S01_VoiceReady() {
        MomentScene(ready(voice))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S02_VoicePlayingWithCaption() {
        MomentScene(
            ready(
                voice,
                playback = PlaybackState.Active(voice.uid, positionMs = 6_000L, durationMs = 42_000L, isPaused = false),
                speed = PlaybackSpeed.FASTER,
                skipSilence = true,
            ),
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S03_VoiceNotOnWatch() {
        MomentScene(ready(voice, playback = PlaybackState.Failed(voice.uid, PlaybackFailure.NOT_ON_WATCH)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S04_VoiceDetails() {
        DetailsScene(ready(voice))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S05_TextDetails() {
        MomentScene(ready(text))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S06_Missing() {
        MomentScene(MomentUiState.Missing)
    }
}

private val momentZone = TimeZone.currentSystemDefault()
private val momentTime = LocalDateTime(2026, 10, 3, 16, 15).toInstant(momentZone)

private val voice =
    JournalNote.Audio(
        mediaRef = "audio/lake.m4a",
        durationMs = 42_000L,
        creationTimestamp = momentTime,
        lastUpdated = momentTime,
        location = NoteLocation(place = NotePlace(Uuid.parse("00000000-0000-4000-8000-000000000001"), "Golden Gate Park", 37.77, -122.45)),
        transcript =
            TranscriptDocument(
                status = TranscriptDocumentStatus.FINAL,
                segments =
                    listOf(
                        TranscriptSegment(segmentId = "a", text = "Walked to the lake after lunch", startMs = 0L, endMs = 4_000L),
                        TranscriptSegment(segmentId = "b", text = "Two herons on the far bank", startMs = 5_000L, endMs = 9_000L),
                    ),
            ),
    )

private val text =
    JournalNote.Text(
        creationTimestamp = momentTime,
        lastUpdated = momentTime,
        content = "Coffee before anyone else was up",
    )

private fun ready(
    note: JournalNote,
    playback: PlaybackState = PlaybackState.Idle,
    speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    skipSilence: Boolean = false,
) = MomentUiState.Ready(note, momentZone, playback, speed, skipSilence)

@Composable
private fun MomentScene(state: MomentUiState) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            MomentScreen(state = state, actions = MomentActions(onDelete = {}), timeSource = MomentTimeSource)
        }
    }
}

@Composable
private fun DetailsScene(state: MomentUiState.Ready) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            MomentDetailsPage(state = state, onDelete = {}, timeSource = MomentTimeSource)
        }
    }
}

/** A fixed clock reading, so the time text renders the same on every run. */
private object MomentTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
```

- [ ] **Step 14: Run screenshot validation to verify it fails**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: FAIL; the report lists the 18 `MomentScreenshots` previews as missing references, and every other preview passes (the list rows render as before).

- [ ] **Step 15: Record and check the references**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/MomentScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: `BUILD SUCCESSFUL`, `18`, and only the new `MomentScreenshots/` directory in the status. Open `S02_VoicePlayingWithCaption_Small Round_*.png` with the Read tool: a progress ring a seventh of the way round, "Sat 4:15 PM", "0:06 / 0:42", the "1.5×" button, the play/pause button showing Pause, the skip silence toggle on, and "Two herons on the far bank" below. Open `S02_…_Large Font_*.png`: nothing clipped by the round edge. Open `S04_VoiceDetails_Small Round_*.png`: the date header, the two transcript lines joined, "Golden Gate Park" with a pin, and Delete.

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 16: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task3-commit.txt <<'EOF'
chore(wear): open a moment with playback, scrubbing and delete

Moments in the new watch app's list now open. A voice note opens on
its player: position, a progress ring, play and pause, speed (1x,
1.5x, 2x) and skip silence, and the line of the transcript being
spoken. Turning the crown moves through the recording in 5 second
steps, with a tick for each. Swiping up shows the transcript, the
place, and Delete, which asks first and also deletes the moment on the
phone through sync. Other moments open on those details.

A moment link from a tile, complication or notification opens the
same screen above Today.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 17: Commit**

```bash
git restore --staged . && git add app/wear/src/main/res/values/strings_moments.xml app/wear/src/main/kotlin/app/logdate/wear/v2/moments app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt app/wear/src/main/kotlin/app/logdate/wear/v2/navigation/WearLaunchRoutes.kt app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/v2/moments/MomentViewModelTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/navigation/WearRouteTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MomentDetailTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ShellNavigationTest.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MomentScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/MomentScreenshots && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task3-commit.txt
```

---

### Task 4: Play from the list and browse earlier days (`chore(wear)`)

The moment list becomes the library window's front page. Voice notes play inline from their row, with a thin progress line while they play, and every row still opens its moment. The list shows voice and text moments only (photos and videos live on the phone), and an "Earlier" button opens the Archive: days 8 to 14 of the window, a day at a time. The window is 14 days until the phone shares the person's choice of 7, 14 or 30.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/library/LibraryWindow.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowViewModel.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentRow.kt` (whole file)
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/archive/ArchiveViewModel.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/archive/ArchiveScreen.kt`
- Modify: `app/wear/src/main/res/values/strings_moments.xml` (append)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` (imports, one entry, `FlowEntry`, `ArchiveEntry`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (one binding)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/library/LibraryWindowTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/flow/FlowViewModelTest.kt` (whole file)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/archive/ArchiveViewModelTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/moments/MomentRowPlaybackTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/FlowPageTest.kt`
- Screenshot: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/FlowPageScreenshots.kt` (two scenes)
- Screenshot: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/ArchiveScreenshots.kt`
- References: `…/screenshots/FlowPageScreenshots/` (6 new images; the 8 existing ones do not change) and `…/screenshots/ArchiveScreenshots/` (new, 9 images)

**Interfaces:**
- Consumes (Task 2): `MomentPlayer.state`, `MomentPlayer.playOrPause`, `PlaybackState.Active.fraction`. (Task 3): `MomentRow`, `FlowActions`, `WearRoute.Moment`, `HomeEntry(…, navigate)`. (Predecessor): `DayClock.observe()`, `TodayWindow(date, timeZone, start, end)`, `MoodNoteTag.isFeeling`, `WearRoute.Archive`.
- Produces:
  - `object LibraryWindow { RECENT_DAYS = 7; WINDOW_DAYS = 14; fun recentStart(today: TodayWindow): Instant; fun windowStart(today: TodayWindow): Instant; fun isLibraryMoment(note: JournalNote): Boolean }` in `app.logdate.wear.v2.library`.
  - `FlowUiState.Ready(timeZone, moments, earlierCount: Int = 0)`; `FlowActions(onOpenMoment, onPlayPause: ((JournalNote.Audio) -> Unit)? = null, onOpenArchive: (() -> Unit)? = null)`; `FlowPage(state, modifier, listState, timeSource, actions, playback: PlaybackState = PlaybackState.Idle)`; `FlowTags.EARLIER`.
  - `data class MomentRowPlayback(isPlaying: Boolean, fraction: Float?, onPlayPause: () -> Unit)`, `fun rowPlayback(note: JournalNote, playback: PlaybackState, onPlayPause: ((JournalNote.Audio) -> Unit)?): MomentRowPlayback?`, `MomentRow(…, playback: MomentRowPlayback? = null)`, `MomentRowTags.PLAY_PAUSE`.
  - `data class ArchiveDay(date: LocalDate, moments: List<JournalNote>)`, `sealed interface ArchiveUiState { Loading; Ready(timeZone, days: List<ArchiveDay>); Failed }`, `class ArchiveViewModel(dayClock, notesRepository)`, `internal fun archiveDays(notes, start, end, timeZone): List<ArchiveDay>`, `object ArchiveTags { LIST }`, `@Composable fun ArchiveScreen(state, modifier, timeSource, playback, onOpenMoment, onPlayPause)` in `app.logdate.wear.v2.archive`.

- [ ] **Step 1: Write the failing window, list and archive tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/library/LibraryWindowTest.kt`:

```kotlin
package app.logdate.wear.v2.library

import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.today.TodayWindow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LibraryWindowTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val saturday =
        TodayWindow(
            date = LocalDate(2026, 10, 3),
            timeZone = zone,
            start = LocalDate(2026, 10, 3).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, 4).atStartOfDayIn(zone),
        )
    private val at = Instant.fromEpochMilliseconds(1_791_000_000_000)

    @Test
    fun `the list starts 6 days before today and the window 13 days before`() {
        assertEquals(LocalDate(2026, 9, 27).atStartOfDayIn(zone), LibraryWindow.recentStart(saturday))
        assertEquals(LocalDate(2026, 9, 20).atStartOfDayIn(zone), LibraryWindow.windowStart(saturday))
    }

    @Test
    fun `a daylight saving change does not move the window by an hour`() {
        val afterChange =
            TodayWindow(
                date = LocalDate(2026, 11, 1),
                timeZone = zone,
                start = LocalDate(2026, 11, 1).atStartOfDayIn(zone),
                end = LocalDate(2026, 11, 2).atStartOfDayIn(zone),
            )

        assertEquals(LocalDate(2026, 10, 26).atStartOfDayIn(zone), LibraryWindow.recentStart(afterChange))
    }

    @Test
    fun `voice and text moments are in the library and check-ins and photos are not`() {
        assertTrue(LibraryWindow.isLibraryMoment(JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Lunch")))
        assertTrue(LibraryWindow.isLibraryMoment(JournalNote.Audio(mediaRef = "a.m4a", creationTimestamp = at, lastUpdated = at)))
        assertFalse(LibraryWindow.isLibraryMoment(JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "#mood:ok Fine")))
        assertFalse(LibraryWindow.isLibraryMoment(JournalNote.Image(mediaRef = "p.jpg", creationTimestamp = at, lastUpdated = at)))
    }
}
```

Replace `app/wear/src/test/kotlin/app/logdate/wear/v2/flow/FlowViewModelTest.kt` with:

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
    private val windowStart = LocalDate(2026, 9, 20).atStartOfDayIn(zone)
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
    fun `the list shows the last 7 days and counts the rest of the 14`() =
        runTest {
            val older = text("Lake walk", at(LocalDateTime(2026, 9, 21, 9, 0)))
            val monday = text("Monday walk", at(LocalDateTime(2026, 9, 28, 9, 0)))
            val friday = voice(at(LocalDateTime(2026, 10, 2, 20, 0)))
            stubWindow(older, monday, friday)

            FlowViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(FlowUiState.Ready(timeZone = zone, moments = listOf(friday, monday), earlierCount = 1), expectMostRecentItem())
            }
        }

    @Test
    fun `check-ins and photos are left out of both`() =
        runTest {
            val checkIn = text("#mood:ok Feeling ok", at(LocalDateTime(2026, 10, 3, 8, 0)))
            val photo = JournalNote.Image(mediaRef = "p.jpg", creationTimestamp = at(LocalDateTime(2026, 9, 22, 8, 0)), lastUpdated = at(LocalDateTime(2026, 9, 22, 8, 0)))
            val lunch = text("Lunch", at(LocalDateTime(2026, 10, 3, 12, 0)))
            stubWindow(checkIn, photo, lunch)

            FlowViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(FlowUiState.Ready(timeZone = zone, moments = listOf(lunch), earlierCount = 0), expectMostRecentItem())
            }
        }

    @Test
    fun `notes outside the window are left out`() =
        runTest {
            val tooOld = text("Too old", at(LocalDateTime(2026, 9, 19, 23, 0)))
            val sunday = text("Sunday", at(LocalDateTime(2026, 9, 27, 10, 0)))
            stubWindow(tooOld, sunday)

            FlowViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(FlowUiState.Ready(timeZone = zone, moments = listOf(sunday), earlierCount = 0), expectMostRecentItem())
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

    private fun stubWindow(vararg notes: JournalNote) {
        every { notesRepository.observeNotesInRange(windowStart, saturday.end) } returns flowOf(notes.toList())
    }

    private fun at(local: LocalDateTime): Instant = local.toInstant(zone)

    private fun text(
        content: String,
        at: Instant,
    ) = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = content)

    private fun voice(at: Instant) = JournalNote.Audio(mediaRef = "v.m4a", durationMs = 30_000L, creationTimestamp = at, lastUpdated = at)
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/archive/ArchiveViewModelTest.kt`:

```kotlin
package app.logdate.wear.v2.archive

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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModelTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val saturday =
        TodayWindow(
            date = LocalDate(2026, 10, 3),
            timeZone = zone,
            start = LocalDate(2026, 10, 3).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, 4).atStartOfDayIn(zone),
        )
    private val windowStart = LocalDate(2026, 9, 20).atStartOfDayIn(zone)
    private val recentStart = LocalDate(2026, 9, 27).atStartOfDayIn(zone)
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
    fun `earlier moments come a day at a time with the newest day first`() =
        runTest {
            val morning = voice(at(LocalDateTime(2026, 9, 21, 8, 0)))
            val evening = text("Dinner on the roof", at(LocalDateTime(2026, 9, 21, 20, 0)))
            val thursday = text("Long call", at(LocalDateTime(2026, 9, 24, 21, 0)))
            val checkIn = text("#mood:ok Fine", at(LocalDateTime(2026, 9, 24, 7, 0)))
            every { notesRepository.observeNotesInRange(windowStart, recentStart) } returns flowOf(listOf(morning, checkIn, evening, thursday))

            ArchiveViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(
                    ArchiveUiState.Ready(
                        timeZone = zone,
                        days =
                            listOf(
                                ArchiveDay(LocalDate(2026, 9, 24), listOf(thursday)),
                                ArchiveDay(LocalDate(2026, 9, 21), listOf(evening, morning)),
                            ),
                    ),
                    expectMostRecentItem(),
                )
            }
        }

    @Test
    fun `nothing earlier is an empty archive`() =
        runTest {
            every { notesRepository.observeNotesInRange(windowStart, recentStart) } returns flowOf(emptyList())

            ArchiveViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(ArchiveUiState.Ready(timeZone = zone, days = emptyList()), expectMostRecentItem())
            }
        }

    @Test
    fun `a failure reading notes shows the failed state`() =
        runTest {
            every { notesRepository.observeNotesInRange(any(), any()) } returns flow { throw IOException("database closed") }

            ArchiveViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(ArchiveUiState.Failed, expectMostRecentItem())
            }
        }

    private fun at(local: LocalDateTime): Instant = local.toInstant(zone)

    private fun text(
        content: String,
        at: Instant,
    ) = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = content)

    private fun voice(at: Instant) = JournalNote.Audio(mediaRef = "v.m4a", durationMs = 30_000L, creationTimestamp = at, lastUpdated = at)
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/moments/MomentRowPlaybackTest.kt`:

```kotlin
package app.logdate.wear.v2.moments

import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.playback.NOTE_TIME
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.playback.voiceNote
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MomentRowPlaybackTest {
    private val voice = voiceNote()
    private val text = JournalNote.Text(creationTimestamp = NOTE_TIME, lastUpdated = NOTE_TIME, content = "Lunch")

    @Test
    fun `only voice notes with a handler play from their row`() {
        assertNull(rowPlayback(text, PlaybackState.Idle) {})
        assertNull(rowPlayback(voice, PlaybackState.Idle, onPlayPause = null))
    }

    @Test
    fun `an idle voice note shows play and no progress`() {
        val row = rowPlayback(voice, PlaybackState.Idle) {}

        assertEquals(false, row?.isPlaying)
        assertNull(row?.fraction)
    }

    @Test
    fun `the playing note shows pause and how far it is`() {
        val row = rowPlayback(voice, PlaybackState.Active(voice.uid, positionMs = 21_000L, durationMs = 42_000L, isPaused = false)) {}

        assertEquals(true, row?.isPlaying)
        assertEquals(0.5f, row?.fraction)
    }

    @Test
    fun `a paused note shows play and where it stopped`() {
        val row = rowPlayback(voice, PlaybackState.Active(voice.uid, positionMs = 21_000L, durationMs = 42_000L, isPaused = true)) {}

        assertEquals(false, row?.isPlaying)
        assertEquals(0.5f, row?.fraction)
    }

    @Test
    fun `another note playing leaves this row idle`() {
        val row = rowPlayback(voice, PlaybackState.Active(voiceNote().uid, positionMs = 1_000L, durationMs = 5_000L, isPaused = false)) {}

        assertEquals(false, row?.isPlaying)
        assertNull(row?.fraction)
    }

    @Test
    fun `tapping play hands the row's note to the handler`() {
        val played = mutableListOf<JournalNote.Audio>()

        rowPlayback(voice, PlaybackState.Idle) { played += it }?.onPlayPause?.invoke()

        assertEquals(listOf(voice), played)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.library.*' --tests 'app.logdate.wear.v2.flow.*' --tests 'app.logdate.wear.v2.archive.*' --tests 'app.logdate.wear.v2.moments.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'LibraryWindow'`, `No parameter with name 'earlierCount' found`, `Unresolved reference 'ArchiveViewModel'` and `Unresolved reference 'rowPlayback'`.

- [ ] **Step 3: Write the failing GMD test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/FlowPageTest.kt`:

```kotlin
package app.logdate.wear.e2e

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.archive.ArchiveDay
import app.logdate.wear.v2.archive.ArchiveScreen
import app.logdate.wear.v2.archive.ArchiveTags
import app.logdate.wear.v2.archive.ArchiveUiState
import app.logdate.wear.v2.flow.FlowActions
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowTags
import app.logdate.wear.v2.flow.FlowUiState
import app.logdate.wear.v2.moments.MomentRowTags
import app.logdate.wear.v2.theme.LogDateWearTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Drives the moment list and the Archive with touch and the crown on a Wear OS emulator. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FlowPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val zone = TimeZone.currentSystemDefault()
    private val at = Instant.fromEpochMilliseconds(1_791_000_000_000)
    private val voice = JournalNote.Audio(mediaRef = "v.m4a", durationMs = 42_000L, creationTimestamp = at, lastUpdated = at)
    private val text = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Coffee before anyone else was up")

    @Test
    fun a_voice_row_plays_inline_and_a_row_opens_its_moment() {
        val played = mutableListOf<JournalNote.Audio>()
        val opened = mutableListOf<JournalNote>()
        showFlow(earlierCount = 0, FlowActions(onOpenMoment = { opened += it }, onPlayPause = { played += it }))

        composeRule.onAllNodesWithTag(MomentRowTags.PLAY_PAUSE).onFirst().performClick()
        composeRule.onNodeWithText("Coffee before anyone else was up").performClick()

        assertEquals(listOf(voice), played)
        assertEquals(listOf<JournalNote>(text), opened)
    }

    @Test
    fun earlier_opens_the_archive_when_there_is_more() {
        var archiveOpens = 0
        showFlow(earlierCount = 3, FlowActions(onOpenArchive = { archiveOpens++ }))

        composeRule.onNodeWithTag(FlowTags.LIST).performRotaryScrollInput { rotateToScrollVertically(LONG_SCROLL) }
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithTag(FlowTags.EARLIER).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(FlowTags.EARLIER).performClick()

        assertEquals(1, archiveOpens)
    }

    @Test
    fun earlier_is_not_offered_without_older_moments() {
        showFlow(earlierCount = 0, FlowActions(onOpenArchive = {}))

        composeRule.onNodeWithTag(FlowTags.LIST).performRotaryScrollInput { rotateToScrollVertically(LONG_SCROLL) }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithTag(FlowTags.EARLIER).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun the_archive_lists_days_and_opens_their_moments() {
        val opened = mutableListOf<JournalNote>()
        composeRule.setContent {
            Scene {
                ArchiveScreen(
                    state = ArchiveUiState.Ready(zone, listOf(ArchiveDay(LocalDate(2026, 9, 21), listOf(text)))),
                    timeSource = FixedTime,
                    onOpenMoment = { opened += it },
                )
            }
        }

        composeRule.onNodeWithTag(ArchiveTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Coffee before anyone else was up").performClick()

        assertEquals(listOf<JournalNote>(text), opened)
    }

    private fun showFlow(
        earlierCount: Int,
        actions: FlowActions,
    ) {
        composeRule.setContent {
            Scene {
                FlowPage(
                    state = FlowUiState.Ready(zone, listOf(voice, text), earlierCount),
                    timeSource = FixedTime,
                    actions = actions,
                )
            }
        }
    }

    private companion object {
        const val LONG_SCROLL = 800f
        const val TIMEOUT_MS = 5_000L
    }
}

@Composable
private fun Scene(content: @Composable () -> Unit) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) { content() }
    }
}

private object FixedTime : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
```

- [ ] **Step 4: Run the GMD test to verify it fails**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.FlowPageTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'ArchiveScreen'` and `Unresolved reference 'PLAY_PAUSE'`.

- [ ] **Step 5: Add the library window**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/library/LibraryWindow.kt`:

```kotlin
package app.logdate.wear.v2.library

import app.logdate.client.domain.today.MoodNoteTag
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.today.TodayWindow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlin.time.Instant

/**
 * The moments the watch library shows: voice and text, without mood check-ins, from today back
 * through [WINDOW_DAYS] days. Home lists the last [RECENT_DAYS]; the Archive lists the rest.
 */
object LibraryWindow {
    const val RECENT_DAYS = 7
    const val WINDOW_DAYS = 14

    /** The start of the oldest day Home lists. */
    fun recentStart(today: TodayWindow): Instant = today.date.minus(RECENT_DAYS - 1, DateTimeUnit.DAY).atStartOfDayIn(today.timeZone)

    /** The start of the oldest day in the library. */
    fun windowStart(today: TodayWindow): Instant = today.date.minus(WINDOW_DAYS - 1, DateTimeUnit.DAY).atStartOfDayIn(today.timeZone)

    fun isLibraryMoment(note: JournalNote): Boolean =
        (note is JournalNote.Audio || note is JournalNote.Text) && !MoodNoteTag.isFeeling(note)
}
```

- [ ] **Step 6: Count earlier moments in the list's state**

Replace `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowViewModel.kt` with:

```kotlin
package app.logdate.wear.v2.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.v2.library.LibraryWindow
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
import kotlinx.datetime.TimeZone

/** What Home's moment list shows. */
sealed interface FlowUiState {
    data object Loading : FlowUiState

    /**
     * The voice and text moments of the last [LibraryWindow.RECENT_DAYS] days, newest first, and how
     * many more the rest of the library window has.
     */
    data class Ready(
        val timeZone: TimeZone,
        val moments: List<JournalNote>,
        val earlierCount: Int = 0,
    ) : FlowUiState

    data object Failed : FlowUiState
}

/** Home's second page: the library window's last 7 days, following the day as it moves. */
class FlowViewModel(
    dayClock: DayClock,
    notesRepository: JournalNotesRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FlowUiState> =
        dayClock
            .observe()
            .flatMapLatest<TodayWindow, FlowUiState> { today ->
                val windowStart = LibraryWindow.windowStart(today)
                val recentStart = LibraryWindow.recentStart(today)
                notesRepository.observeNotesInRange(windowStart, today.end).map { notes ->
                    val library =
                        notes
                            .filter { it.creationTimestamp >= windowStart && it.creationTimestamp < today.end }
                            .filter(LibraryWindow::isLibraryMoment)
                    FlowUiState.Ready(
                        timeZone = today.timeZone,
                        moments = library.filter { it.creationTimestamp >= recentStart }.sortedByDescending(JournalNote::creationTimestamp),
                        earlierCount = library.count { it.creationTimestamp < recentStart },
                    )
                }
            }.catch { error ->
                Napier.e("Couldn't load the last 7 days on the watch", error)
                emit(FlowUiState.Failed)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FlowUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

Check nothing else used the removed constant:

```bash
grep -rn "FLOW_DAYS" app/wear/src
```

Expected: no output.

- [ ] **Step 7: Add inline playback to the row**

Replace `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentRow.kt` with:

```kotlin
package app.logdate.wear.v2.moments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.v2.playback.PlaybackState
import kotlinx.datetime.TimeZone

object MomentRowTags {
    const val ROW = "moment_row"
    const val PLAY_PAUSE = "moment_row_play_pause"
}

/** How a voice note's row shows and controls its playback. */
data class MomentRowPlayback(
    val isPlaying: Boolean,
    val fraction: Float?,
    val onPlayPause: () -> Unit,
)

/**
 * The playback [note]'s row shows while the player is in [playback], or null for anything but a
 * voice note, and for any row when there is no [onPlayPause].
 */
fun rowPlayback(
    note: JournalNote,
    playback: PlaybackState,
    onPlayPause: ((JournalNote.Audio) -> Unit)?,
): MomentRowPlayback? {
    if (note !is JournalNote.Audio || onPlayPause == null) return null
    val active = (playback as? PlaybackState.Active)?.takeIf { it.noteId == note.uid }
    return MomentRowPlayback(isPlaying = active?.isPaused == false, fraction = active?.fraction, onPlayPause = { onPlayPause(note) })
}

/**
 * A moment in a list: when it was, and what it is in a few words. With [onOpen] the row opens it;
 * with [playback] a voice note plays from the row, with a progress line while it plays.
 */
@Composable
fun MomentRow(
    note: JournalNote,
    timeZone: TimeZone,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    playback: MomentRowPlayback? = null,
) {
    if (playback == null) {
        MomentSummary(note, timeZone, onOpen, modifier)
        return
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            MomentSummary(note, timeZone, onOpen, Modifier)
            playback.fraction?.let { fraction ->
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            }
        }
        IconButton(onClick = playback.onPlayPause, modifier = Modifier.testTag(MomentRowTags.PLAY_PAUSE)) {
            Icon(
                imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playback.isPlaying) R.string.wear_moment_pause else R.string.wear_moment_play),
            )
        }
    }
}

@Composable
private fun MomentSummary(
    note: JournalNote,
    timeZone: TimeZone,
    onOpen: (() -> Unit)?,
    modifier: Modifier,
) {
    val openLabel = stringResource(R.string.wear_moment_open)
    val opens = if (onOpen == null) Modifier else Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .then(opens)
                .padding(vertical = 4.dp)
                .testTag(MomentRowTags.ROW),
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

- [ ] **Step 8: Add Earlier and inline playback to the list**

Append to `app/wear/src/main/res/values/strings_moments.xml`, before `</resources>`:

```xml

    <!-- The moment list and the Archive, in the new watch app -->
    <string name="wear_flow_earlier">Earlier</string>
    <plurals name="wear_flow_earlier_count">
        <item quantity="one">%1$d more moment</item>
        <item quantity="other">%1$d more moments</item>
    </plurals>
    <string name="wear_archive_title">Earlier</string>
    <string name="wear_archive_empty">No earlier moments in the last 14 days</string>
    <string name="wear_archive_failed">Couldn’t load earlier moments</string>
```

Replace `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt` with:

```kotlin
package app.logdate.wear.v2.flow

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.v2.moments.MomentRow
import app.logdate.wear.v2.moments.rowPlayback
import app.logdate.wear.v2.playback.PlaybackState

object FlowTags {
    const val LIST = "flow_list"
    const val EARLIER = "flow_earlier"
}

/** What the moment list lets the person do. Each control appears only when its handler is set. */
data class FlowActions(
    val onOpenMoment: ((JournalNote) -> Unit)? = null,
    val onPlayPause: ((JournalNote.Audio) -> Unit)? = null,
    val onOpenArchive: (() -> Unit)? = null,
)

/**
 * Home's second page: the voice and text moments of the last 7 days, newest first, each playable
 * from its row, then Earlier when the library window has older moments.
 */
@Composable
fun FlowPage(
    state: FlowUiState,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
    actions: FlowActions = FlowActions(),
    playback: PlaybackState = PlaybackState.Idle,
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
                is FlowUiState.Ready -> {
                    momentItems(state, actions, playback)
                    earlierItem(state, actions.onOpenArchive, spec)
                }
            }
        }
    }
}

private fun TransformingLazyColumnScope.momentItems(
    state: FlowUiState.Ready,
    actions: FlowActions,
    playback: PlaybackState,
) {
    if (state.moments.isEmpty()) {
        item { FlowMessage(stringResource(R.string.wear_flow_empty)) }
        return
    }
    items(state.moments.size, key = { index -> state.moments[index].uid.toString() }) { index ->
        val note = state.moments[index]
        MomentRow(
            note = note,
            timeZone = state.timeZone,
            onOpen = actions.onOpenMoment?.let { open -> { open(note) } },
            playback = rowPlayback(note, playback, actions.onPlayPause),
        )
    }
}

private fun TransformingLazyColumnScope.earlierItem(
    state: FlowUiState.Ready,
    onOpenArchive: (() -> Unit)?,
    spec: TransformationSpec,
) {
    if (state.earlierCount == 0 || onOpenArchive == null) return
    item {
        Button(
            onClick = onOpenArchive,
            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).testTag(FlowTags.EARLIER),
            transformation = SurfaceTransformation(spec),
            secondaryLabel = { Text(pluralStringResource(R.plurals.wear_flow_earlier_count, state.earlierCount, state.earlierCount)) },
            label = { Text(stringResource(R.string.wear_flow_earlier)) },
        )
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
```

- [ ] **Step 9: Add the Archive**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/archive/ArchiveViewModel.kt`:

```kotlin
package app.logdate.wear.v2.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.v2.library.LibraryWindow
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** One day of the Archive: its date and its moments, newest first. */
data class ArchiveDay(
    val date: LocalDate,
    val moments: List<JournalNote>,
)

/** What the Archive shows. */
sealed interface ArchiveUiState {
    data object Loading : ArchiveUiState

    data class Ready(
        val timeZone: TimeZone,
        val days: List<ArchiveDay>,
    ) : ArchiveUiState

    data object Failed : ArchiveUiState
}

/** The library window past the last 7 days, a day at a time, newest day first, moving with the date. */
class ArchiveViewModel(
    dayClock: DayClock,
    notesRepository: JournalNotesRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ArchiveUiState> =
        dayClock
            .observe()
            .flatMapLatest<TodayWindow, ArchiveUiState> { today ->
                val start = LibraryWindow.windowStart(today)
                val end = LibraryWindow.recentStart(today)
                notesRepository.observeNotesInRange(start, end).map { notes ->
                    ArchiveUiState.Ready(timeZone = today.timeZone, days = archiveDays(notes, start, end, today.timeZone))
                }
            }.catch { error ->
                Napier.e("Couldn't load earlier moments on the watch", error)
                emit(ArchiveUiState.Failed)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ArchiveUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The library moments of [notes] from [start] up to [end], grouped by their day in [timeZone]. */
internal fun archiveDays(
    notes: List<JournalNote>,
    start: Instant,
    end: Instant,
    timeZone: TimeZone,
): List<ArchiveDay> =
    notes
        .filter { it.creationTimestamp >= start && it.creationTimestamp < end }
        .filter(LibraryWindow::isLibraryMoment)
        .sortedByDescending(JournalNote::creationTimestamp)
        .groupBy { it.creationTimestamp.toLocalDateTime(timeZone).date }
        .map { (date, moments) -> ArchiveDay(date, moments) }
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/archive/ArchiveScreen.kt`:

```kotlin
package app.logdate.wear.v2.archive

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.v2.moments.MomentRow
import app.logdate.wear.v2.moments.rowPlayback
import app.logdate.wear.v2.playback.PlaybackState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

object ArchiveTags {
    const val LIST = "archive_list"
}

/** Days 8 to 14 of the library window, a day at a time, each moment playable and openable. */
@Composable
fun ArchiveScreen(
    state: ArchiveUiState,
    modifier: Modifier = Modifier,
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
    playback: PlaybackState = PlaybackState.Idle,
    onOpenMoment: ((JournalNote) -> Unit)? = null,
    onPlayPause: ((JournalNote.Audio) -> Unit)? = null,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, timeText = { TimeText(timeSource = timeSource) }) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier.testTag(ArchiveTags.LIST)) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.wear_archive_title))
                }
            }
            when (state) {
                ArchiveUiState.Loading -> Unit
                ArchiveUiState.Failed -> item { ArchiveMessage(stringResource(R.string.wear_archive_failed)) }
                is ArchiveUiState.Ready -> dayItems(state, spec, playback, onOpenMoment, onPlayPause)
            }
        }
    }
}

private fun TransformingLazyColumnScope.dayItems(
    state: ArchiveUiState.Ready,
    spec: TransformationSpec,
    playback: PlaybackState,
    onOpenMoment: ((JournalNote) -> Unit)?,
    onPlayPause: ((JournalNote.Audio) -> Unit)?,
) {
    if (state.days.isEmpty()) {
        item { ArchiveMessage(stringResource(R.string.wear_archive_empty)) }
        return
    }
    state.days.forEach { day ->
        item(key = day.date.toString()) {
            ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                Text(archiveDayText(day.date), style = MaterialTheme.typography.labelMedium)
            }
        }
        items(day.moments.size, key = { index -> day.moments[index].uid.toString() }) { index ->
            val note = day.moments[index]
            MomentRow(
                note = note,
                timeZone = state.timeZone,
                onOpen = onOpenMoment?.let { open -> { open(note) } },
                playback = rowPlayback(note, playback, onPlayPause),
            )
        }
    }
}

@Composable
private fun ArchiveMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

/** [date] as a short weekday, month and day in the user's locale, such as "Mon, Sep 21". */
@Composable
private fun archiveDayText(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, date) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_SKELETON), locale).format(date.toJavaLocalDate())
    }
}

private const val DAY_SKELETON = "EEEMMMd"
```

- [ ] **Step 10: Open the Archive and play from the list**

In `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt`:

Add these imports in ASCII order: `app.logdate.wear.v2.archive.ArchiveScreen`, `app.logdate.wear.v2.archive.ArchiveViewModel`, `app.logdate.wear.v2.playback.MomentPlayer`.

In the `entryProvider { … }` block, add after the Moment entry:

```kotlin
                            entry<WearRoute.Archive> { ArchiveEntry(navigate = { route -> backStack.add(route) }) }
```

Replace the whole `FlowEntry` function with:

```kotlin
@Composable
private fun FlowEntry(
    listState: TransformingLazyColumnState,
    navigate: (WearRoute) -> Unit,
) {
    val viewModel = koinViewModel<FlowViewModel>()
    val player = koinInject<MomentPlayer>()
    val state by viewModel.uiState.collectAsState()
    val playback by player.state.collectAsState()
    FlowPage(
        state = state,
        listState = listState,
        playback = playback,
        actions =
            FlowActions(
                onOpenMoment = { note -> navigate(WearRoute.Moment(note.uid.toString())) },
                onPlayPause = player::playOrPause,
                onOpenArchive = { navigate(WearRoute.Archive) },
            ),
    )
}
```

Add this function after `FlowEntry`:

```kotlin
@Composable
private fun ArchiveEntry(navigate: (WearRoute) -> Unit) {
    val viewModel = koinViewModel<ArchiveViewModel>()
    val player = koinInject<MomentPlayer>()
    val state by viewModel.uiState.collectAsState()
    val playback by player.state.collectAsState()
    ArchiveScreen(
        state = state,
        playback = playback,
        onOpenMoment = { note -> navigate(WearRoute.Moment(note.uid.toString())) },
        onPlayPause = player::playOrPause,
    )
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add `import app.logdate.wear.v2.archive.ArchiveViewModel` in ASCII order and, before the module's closing brace:

```kotlin
        viewModel { ArchiveViewModel(dayClock = get(), notesRepository = get()) }
```

- [ ] **Step 11: Run the unit tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.*' :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; 3 + 4 + 3 + 6 new or rewritten tests pass.

- [ ] **Step 12: Run the GMD tests to verify they pass**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.FlowPageTest,app.logdate.wear.e2e.HomePagerRotaryTest,app.logdate.wear.e2e.ShellNavigationTest
```

Expected: `BUILD SUCCESSFUL`, 4 + 5 + 4 tests passed.

- [ ] **Step 13: Write the screenshot scenes**

In `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/FlowPageScreenshots.kt`, add the imports `app.logdate.wear.v2.flow.FlowActions` and `app.logdate.wear.v2.playback.PlaybackState` in ASCII order, and add these two scenes inside `class FlowPageScreenshots`, after `S04_FlowFailed`:

```kotlin
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S05_FlowPlayingWithEarlier() {
        val playing = recentMoments.first() as JournalNote.Audio
        FlowScene(
            FlowUiState.Ready(timeZone = flowZone, moments = recentMoments, earlierCount = 5),
            playback = PlaybackState.Active(playing.uid, positionMs = 21_000L, durationMs = 42_000L, isPaused = false),
            actions = allFlowActions,
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S06_FlowEarlierOnly() {
        FlowScene(FlowUiState.Ready(timeZone = flowZone, moments = emptyList(), earlierCount = 2), actions = allFlowActions)
    }
```

Replace the private `FlowScene` function with these two declarations:

```kotlin
@Composable
private fun FlowScene(
    state: FlowUiState,
    playback: PlaybackState = PlaybackState.Idle,
    actions: FlowActions = FlowActions(),
) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            FlowPage(state = state, timeSource = FlowTimeSource, actions = actions, playback = playback)
        }
    }
}

/** Every list action set, so the scenes that need them show play buttons and Earlier. */
private val allFlowActions = FlowActions(onOpenMoment = {}, onPlayPause = {}, onOpenArchive = {})
```

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/ArchiveScreenshots.kt`:

```kotlin
package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.archive.ArchiveDay
import app.logdate.wear.v2.archive.ArchiveScreen
import app.logdate.wear.v2.archive.ArchiveUiState
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** Every state of the Archive, on the graphite theme. */
class ArchiveScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S01_ArchiveDays() {
        ArchiveScene(ArchiveUiState.Ready(archiveZone, archiveDays))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S02_ArchiveEmpty() {
        ArchiveScene(ArchiveUiState.Ready(archiveZone, emptyList()))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S03_ArchiveFailed() {
        ArchiveScene(ArchiveUiState.Failed)
    }
}

private val archiveZone = TimeZone.currentSystemDefault()

private fun archiveTime(
    day: Int,
    hour: Int,
) = LocalDateTime(2026, 9, day, hour, 15).toInstant(archiveZone)

private val archiveDays =
    listOf(
        ArchiveDay(
            LocalDate(2026, 9, 24),
            listOf(JournalNote.Text(creationTimestamp = archiveTime(24, 21), lastUpdated = archiveTime(24, 21), content = "Long call about the trip")),
        ),
        ArchiveDay(
            LocalDate(2026, 9, 21),
            listOf(
                JournalNote.Text(creationTimestamp = archiveTime(21, 20), lastUpdated = archiveTime(21, 20), content = "Dinner on the roof"),
                JournalNote.Audio(mediaRef = "audio/walk.m4a", durationMs = 95_000L, creationTimestamp = archiveTime(21, 8), lastUpdated = archiveTime(21, 8)),
            ),
        ),
    )

@Composable
private fun ArchiveScene(state: ArchiveUiState) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            ArchiveScreen(state = state, timeSource = ArchiveTimeSource, onOpenMoment = {}, onPlayPause = {})
        }
    }
}

/** A fixed clock reading, so the time text renders the same on every run. */
private object ArchiveTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
```

- [ ] **Step 14: Run screenshot validation to verify it fails**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: FAIL; the report lists the 6 new `FlowPageScreenshots` previews and the 9 `ArchiveScreenshots` previews as missing references. `S01` to `S04` of `FlowPageScreenshots` and every other preview pass.

- [ ] **Step 15: Record and check the references**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/FlowPageScreenshots" | wc -l && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/ArchiveScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: `BUILD SUCCESSFUL`, `14`, `9`, and in the status only new files (no modified `S01`–`S04` Flow references). Open `S05_FlowPlayingWithEarlier_Small Round_*.png` with the Read tool: the voice row with a Pause button and a half-filled progress line, and the text and photo rows with no button. Open `S06_FlowEarlierOnly_Small Round_*.png`: "No moments in the last 7 days" over an "Earlier" button reading "2 more moments". Open `S01_ArchiveDays_Large Font_*.png`: "Thu, Sep 24" and "Mon, Sep 21" headers, nothing clipped.

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 16: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task4-commit.txt <<'EOF'
chore(wear): play moments from the list and browse earlier days

Voice notes in the new watch app's moment list now play from their
row, with a progress line while they play, and every row still opens
its moment. The list shows voice and text moments, the ones a watch
can play or read, and ends with Earlier when the 14 day library window
has older moments. Earlier opens the Archive: days 8 to 14, a day at
a time, with the same rows.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 17: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/library app/wear/src/main/kotlin/app/logdate/wear/v2/flow app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentRow.kt app/wear/src/main/kotlin/app/logdate/wear/v2/archive app/wear/src/main/res/values/strings_moments.xml app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/v2/library app/wear/src/test/kotlin/app/logdate/wear/v2/flow/FlowViewModelTest.kt app/wear/src/test/kotlin/app/logdate/wear/v2/archive app/wear/src/test/kotlin/app/logdate/wear/v2/moments/MomentRowPlaybackTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/FlowPageTest.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/FlowPageScreenshots.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/ArchiveScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/FlowPageScreenshots app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/ArchiveScreenshots && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task4-commit.txt
```

---

### Task 5: On this day and this week's Rewind (`chore(wear)`)

After the week's moments, the list shows On this day and this week's Rewind as cards when they exist, each opening a moment. On this day uses the shared `GetMemoryRecallUseCase` over the notes on this watch, limited to voice and text, and opens the day's first voice note, or its first text. Rewind reads the newest Rewind that ended in the last week from the watch's database and opens its first voice or text moment on this watch. Nothing on the watch writes Rewinds yet, so that card appears once the phone sends them (the library window plan); the On this day tile and complication of Task 13 reuse `OnThisDayFinder`.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/memories/OnThisDay.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/memories/WeeklyRewind.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/memories/MemoriesViewModel.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/memories/MemoryCards.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt` (one parameter, one call)
- Modify: `app/wear/src/main/res/values/strings_moments.xml` (append)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` (imports, `FlowEntry`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (imports, four bindings)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/memories/OnThisDayFinderTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/memories/WeeklyRewindSourceTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/memories/MemoriesViewModelTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/FlowPageTest.kt` (one test)
- Screenshot: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/FlowPageScreenshots.kt` (one scene)
- References: `…/screenshots/FlowPageScreenshots/` (3 new images)

**Interfaces:**
- Consumes (Task 4): `LibraryWindow.isLibraryMoment`, `FlowPage`, `FlowActions`. (Shared): `GetMemoryRecallUseCase(notesRepository, aiRecallProvider = null, now = …)` with `operator fun invoke(aiEnabled = false, recallMode = RecallMode.ON_THIS_DAY, contentTypes: Set<WidgetContentType>, rotationDay = null): Flow<MemoryRecallData?>`; `MemoryRecallData(date, summary, …)`; `RewindRepository.getAllRewinds(): Flow<List<Rewind>>`; `Rewind(uid, startDate, endDate, generationDate, label, title, content)`; `RewindContent.TextNote`/`AudioNote` with `sourceId`.
- Produces (package `app.logdate.wear.v2.memories`): `data class OnThisDay(date: LocalDate, yearsAgo: Int, leadMoment: JournalNote, momentCount: Int, summary: String)`; `class OnThisDayFinder(recall: suspend () -> MemoryRecallData?, notesRepository)` with `suspend fun find(today: LocalDate): OnThisDay?`; `val WATCH_RECALL_TYPES: Set<WidgetContentType>`; `data class WeeklyRewind(title: String, leadMoment: JournalNote)`; `class WeeklyRewindSource(rewinds: RewindRepository, notesRepository)` with `fun observe(today: TodayWindow): Flow<WeeklyRewind?>`; `data class MemoryCards(onThisDay: OnThisDay? = null, rewind: WeeklyRewind? = null)`; `class MemoriesViewModel(dayClock, onThisDay: OnThisDayFinder, weeklyRewind: WeeklyRewindSource)` with `cards: StateFlow<MemoryCards>`; `object MemoryCardTags { ON_THIS_DAY; REWIND }`; `fun TransformingLazyColumnScope.memoryCardItems(cards: MemoryCards, spec: TransformationSpec, onOpenMoment: ((JournalNote) -> Unit)?)`. `FlowPage` gains `cards: TransformingLazyColumnScope.(TransformationSpec) -> Unit = {}`.

- [ ] **Step 1: Write the failing finder, Rewind and view model tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/memories/OnThisDayFinderTest.kt`:

```kotlin
package app.logdate.wear.v2.memories

import app.logdate.client.domain.recommendation.MemoryRecallData
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class OnThisDayFinderTest {
    private val today = LocalDate(2026, 10, 10)
    private val lastYear = LocalDate(2025, 10, 10)
    private val notesRepository = mockk<JournalNotesRepository>()

    @Test
    fun `the first voice note of the day opens it`() =
        runTest {
            val breakfast = text("Breakfast by the window", at(8))
            val walk = voice(at(12))
            val dinner = text("Dinner with Sam", at(19))
            coEvery { notesRepository.getNotesForDay(lastYear) } returns listOf(dinner, walk, breakfast)

            val found = finder(MemoryRecallData(lastYear, "Breakfast by the window")).find(today)

            assertEquals(OnThisDay(lastYear, yearsAgo = 1, leadMoment = walk, momentCount = 3, summary = "Breakfast by the window"), found)
        }

    @Test
    fun `without a voice note the first text opens it`() =
        runTest {
            val breakfast = text("Breakfast by the window", at(8))
            val dinner = text("Dinner with Sam", at(19))
            coEvery { notesRepository.getNotesForDay(lastYear) } returns listOf(dinner, breakfast)

            assertEquals(breakfast, finder(MemoryRecallData(lastYear, "")).find(today)?.leadMoment)
        }

    @Test
    fun `a day of only check-ins and photos has nothing to show`() =
        runTest {
            val checkIn = text("#mood:good Feeling good", at(9))
            val photo = JournalNote.Image(mediaRef = "p.jpg", creationTimestamp = at(10), lastUpdated = at(10))
            coEvery { notesRepository.getNotesForDay(lastYear) } returns listOf(checkIn, photo)

            assertNull(finder(MemoryRecallData(lastYear, "")).find(today))
        }

    @Test
    fun `no recalled day means no card`() =
        runTest {
            assertNull(finder(null).find(today))
            coVerify(exactly = 0) { notesRepository.getNotesForDay(any()) }
        }

    @Test
    fun `years are counted from today`() =
        runTest {
            val threeYearsAgo = LocalDate(2023, 10, 11)
            coEvery { notesRepository.getNotesForDay(threeYearsAgo) } returns listOf(text("Moved in", at(10)))

            assertEquals(3, finder(MemoryRecallData(threeYearsAgo, "Moved in")).find(today)?.yearsAgo)
        }

    private fun finder(recalled: MemoryRecallData?) = OnThisDayFinder(recall = { recalled }, notesRepository = notesRepository)

    private fun at(hour: Int): Instant = LocalDateTime(2025, 10, 10, hour, 0).toInstant(TimeZone.UTC)

    private fun text(
        content: String,
        at: Instant,
    ) = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = content)

    private fun voice(at: Instant) = JournalNote.Audio(mediaRef = "v.m4a", durationMs = 30_000L, creationTimestamp = at, lastUpdated = at)
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/memories/WeeklyRewindSourceTest.kt`:

```kotlin
package app.logdate.wear.v2.memories

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import app.logdate.wear.v2.today.TodayWindow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class WeeklyRewindSourceTest {
    private val zone = TimeZone.UTC
    private val today =
        TodayWindow(
            date = LocalDate(2026, 10, 10),
            timeZone = zone,
            start = LocalDate(2026, 10, 10).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, 11).atStartOfDayIn(zone),
        )
    private val rewinds = mockk<RewindRepository>()
    private val notesRepository = mockk<JournalNotesRepository>()
    private val at = Instant.fromEpochMilliseconds(1_791_000_000_000)
    private val walk = JournalNote.Audio(mediaRef = "v.m4a", durationMs = 30_000L, creationTimestamp = at, lastUpdated = at)

    @Test
    fun `the newest Rewind of the past week opens its first moment on the watch`() =
        runTest {
            val missing = Uuid.random()
            val photo = Uuid.random()
            coEvery { notesRepository.getNoteById(missing) } returns null
            coEvery { notesRepository.getNoteById(walk.uid) } returns walk
            every { rewinds.getAllRewinds() } returns
                flowOf(
                    listOf(
                        rewind("Two weeks ago", endedOn = LocalDate(2026, 9, 27), walk.uid),
                        rewind(
                            "A quiet week",
                            endedOn = LocalDate(2026, 10, 4),
                            RewindContent.Image(timestamp = at, sourceId = photo, uri = "p.jpg"),
                            missing,
                            walk.uid,
                        ),
                    ),
                )

            WeeklyRewindSource(rewinds, notesRepository).observe(today).test {
                assertEquals(WeeklyRewind("A quiet week", walk), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `no Rewind this week means no card`() =
        runTest {
            every { rewinds.getAllRewinds() } returns flowOf(listOf(rewind("Two weeks ago", endedOn = LocalDate(2026, 9, 27))))

            WeeklyRewindSource(rewinds, notesRepository).observe(today).test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a Rewind with none of its moments on the watch has no card`() =
        runTest {
            val gone = Uuid.random()
            coEvery { notesRepository.getNoteById(gone) } returns null
            every { rewinds.getAllRewinds() } returns flowOf(listOf(rewind("A quiet week", endedOn = LocalDate(2026, 10, 4), gone)))

            WeeklyRewindSource(rewinds, notesRepository).observe(today).test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

    private fun rewind(
        title: String,
        endedOn: LocalDate,
        vararg sources: Any,
    ) = Rewind(
        uid = Uuid.random(),
        startDate = endedOn.atStartOfDayIn(zone),
        endDate = endedOn.atStartOfDayIn(zone),
        generationDate = endedOn.atStartOfDayIn(zone),
        label = "2026#40",
        title = title,
        content =
            sources.map { source ->
                source as? RewindContent ?: RewindContent.TextNote(timestamp = at, sourceId = source as Uuid, content = "note")
            },
    )
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/memories/MemoriesViewModelTest.kt`:

```kotlin
package app.logdate.wear.v2.memories

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.today.DayClock
import app.logdate.wear.v2.today.TodayWindow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelTest {
    private val zone = TimeZone.UTC
    private val today =
        TodayWindow(
            date = LocalDate(2026, 10, 10),
            timeZone = zone,
            start = LocalDate(2026, 10, 10).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, 11).atStartOfDayIn(zone),
        )
    private val dayClock = mockk<DayClock> { every { observe() } returns flowOf(today) }
    private val finder = mockk<OnThisDayFinder>()
    private val rewindSource = mockk<WeeklyRewindSource>()
    private val at = Instant.fromEpochMilliseconds(1_760_000_000_000)
    private val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Beach day")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `both cards come from today's date`() =
        runTest {
            val onThisDay = OnThisDay(LocalDate(2025, 10, 10), yearsAgo = 1, leadMoment = note, momentCount = 1, summary = "Beach day")
            val rewind = WeeklyRewind("A quiet week", note)
            coEvery { finder.find(today.date) } returns onThisDay
            every { rewindSource.observe(today) } returns flowOf(rewind)

            MemoriesViewModel(dayClock, finder, rewindSource).cards.test {
                assertEquals(MemoryCards(onThisDay, rewind), expectMostRecentItem())
            }
        }

    @Test
    fun `a failure shows no cards`() =
        runTest {
            coEvery { finder.find(any()) } throws IOException("database closed")
            every { rewindSource.observe(any()) } returns flowOf(null)

            MemoriesViewModel(dayClock, finder, rewindSource).cards.test {
                assertEquals(MemoryCards(), expectMostRecentItem())
            }
        }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.memories.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'OnThisDayFinder'`, `Unresolved reference 'WeeklyRewindSource'` and `Unresolved reference 'MemoriesViewModel'`.

- [ ] **Step 3: Write the failing GMD test**

In `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/FlowPageTest.kt`, add these imports in ASCII order: `app.logdate.wear.v2.memories.MemoryCardTags`, `app.logdate.wear.v2.memories.MemoryCards`, `app.logdate.wear.v2.memories.OnThisDay`, `app.logdate.wear.v2.memories.memoryCardItems`. Add this test after `earlier_is_not_offered_without_older_moments`:

```kotlin
    @Test
    fun the_on_this_day_card_opens_its_moment() {
        val opened = mutableListOf<JournalNote>()
        val onThisDay = OnThisDay(LocalDate(2025, 10, 10), yearsAgo = 1, leadMoment = text, momentCount = 2, summary = "A day at the beach")
        composeRule.setContent {
            Scene {
                FlowPage(
                    state = FlowUiState.Ready(zone, emptyList(), earlierCount = 0),
                    timeSource = FixedTime,
                    cards = { spec -> memoryCardItems(MemoryCards(onThisDay = onThisDay), spec, onOpenMoment = { opened += it }) },
                )
            }
        }

        composeRule.onNodeWithTag(MemoryCardTags.ON_THIS_DAY).performClick()

        assertEquals(listOf<JournalNote>(text), opened)
    }
```

- [ ] **Step 4: Run the GMD test to verify it fails**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.FlowPageTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'memoryCardItems'` and `No parameter with name 'cards' found`.

- [ ] **Step 5: Add On this day and the weekly Rewind**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/memories/OnThisDay.kt`:

```kotlin
package app.logdate.wear.v2.memories

import app.logdate.client.domain.recommendation.MemoryRecallData
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.v2.library.LibraryWindow
import kotlinx.datetime.LocalDate

/** What On this day on the watch recalls: words and voice, since photos stay on the phone. */
val WATCH_RECALL_TYPES: Set<WidgetContentType> = setOf(WidgetContentType.TEXT, WidgetContentType.AUDIO)

/**
 * A day from a past year to revisit. [leadMoment] opens it: the day's first voice note, or its
 * first text when it has none.
 */
data class OnThisDay(
    val date: LocalDate,
    val yearsAgo: Int,
    val leadMoment: JournalNote,
    val momentCount: Int,
    val summary: String,
)

/** Finds On this day among the notes on this watch, on the day [recall] picks. */
class OnThisDayFinder(
    private val recall: suspend () -> MemoryRecallData?,
    private val notesRepository: JournalNotesRepository,
) {
    suspend fun find(today: LocalDate): OnThisDay? {
        val memory = recall() ?: return null
        val moments =
            notesRepository
                .getNotesForDay(memory.date)
                .filter(LibraryWindow::isLibraryMoment)
                .sortedBy(JournalNote::creationTimestamp)
        val lead = moments.firstOrNull { it is JournalNote.Audio } ?: moments.firstOrNull() ?: return null
        return OnThisDay(
            date = memory.date,
            yearsAgo = (today.year - memory.date.year).coerceAtLeast(1),
            leadMoment = lead,
            momentCount = moments.size,
            summary = memory.summary,
        )
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/memories/WeeklyRewind.kt`:

```kotlin
package app.logdate.wear.v2.memories

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import app.logdate.wear.v2.library.LibraryWindow
import app.logdate.wear.v2.today.TodayWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus

/** This week's Rewind as the watch shows it: its title, and the moment that opens it. */
data class WeeklyRewind(
    val title: String,
    val leadMoment: JournalNote,
)

/**
 * The newest Rewind that ended in the last [REWIND_AGE_DAYS] days, when one of its voice or text
 * moments is on this watch; null otherwise.
 */
class WeeklyRewindSource(
    private val rewinds: RewindRepository,
    private val notesRepository: JournalNotesRepository,
) {
    fun observe(today: TodayWindow): Flow<WeeklyRewind?> {
        val since = today.date.minus(REWIND_AGE_DAYS, DateTimeUnit.DAY).atStartOfDayIn(today.timeZone)
        return rewinds.getAllRewinds().map { all ->
            all.filter { it.endDate >= since }.maxByOrNull(Rewind::endDate)?.let { rewind -> leadFor(rewind) }
        }
    }

    private suspend fun leadFor(rewind: Rewind): WeeklyRewind? {
        val lead =
            rewind.content
                .filter { it is RewindContent.AudioNote || it is RewindContent.TextNote }
                .firstNotNullOfOrNull { content -> notesRepository.getNoteById(content.sourceId)?.takeIf(LibraryWindow::isLibraryMoment) }
                ?: return null
        return WeeklyRewind(title = rewind.title, leadMoment = lead)
    }

    companion object {
        const val REWIND_AGE_DAYS = 7
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/memories/MemoriesViewModel.kt`:

```kotlin
package app.logdate.wear.v2.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.wear.v2.today.DayClock
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** The memory cards on the moment list. Either can be missing. */
data class MemoryCards(
    val onThisDay: OnThisDay? = null,
    val rewind: WeeklyRewind? = null,
)

/** On this day and this week's Rewind, found again when the date changes. */
class MemoriesViewModel(
    dayClock: DayClock,
    onThisDay: OnThisDayFinder,
    weeklyRewind: WeeklyRewindSource,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val cards: StateFlow<MemoryCards> =
        dayClock
            .observe()
            .flatMapLatest { today ->
                combine(flow { emit(onThisDay.find(today.date)) }, weeklyRewind.observe(today)) { found, rewind ->
                    MemoryCards(onThisDay = found, rewind = rewind)
                }
            }.catch { error ->
                Napier.e("Couldn't load On this day and Rewind on the watch", error)
                emit(MemoryCards())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MemoryCards())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 6: Add the cards and a slot for them in the list**

Append to `app/wear/src/main/res/values/strings_moments.xml`, before `</resources>`:

```xml

    <!-- Memory cards, in the new watch app -->
    <string name="wear_memory_on_this_day">On this day</string>
    <plurals name="wear_memory_years_ago">
        <item quantity="one">%1$d year ago</item>
        <item quantity="other">%1$d years ago</item>
    </plurals>
    <string name="wear_memory_rewind">This week’s Rewind</string>
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/memories/MemoryCards.kt`:

```kotlin
package app.logdate.wear.v2.memories

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.v2.moments.momentLabel

object MemoryCardTags {
    const val ON_THIS_DAY = "memory_on_this_day"
    const val REWIND = "memory_rewind"
}

/** On this day and this week's Rewind, each as a card that opens its moment. No cards without [onOpenMoment]. */
fun TransformingLazyColumnScope.memoryCardItems(
    cards: MemoryCards,
    spec: TransformationSpec,
    onOpenMoment: ((JournalNote) -> Unit)?,
) {
    val open = onOpenMoment ?: return
    cards.onThisDay?.let { day ->
        item {
            OnThisDayCard(day, open, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec))
        }
    }
    cards.rewind?.let { rewind ->
        item {
            RewindCard(rewind, open, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec))
        }
    }
}

@Composable
private fun OnThisDayCard(
    day: OnThisDay,
    open: (JournalNote) -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val body = if (day.summary.isBlank()) momentLabel(day.leadMoment) else day.summary
    TitleCard(
        onClick = { open(day.leadMoment) },
        title = { Text(stringResource(R.string.wear_memory_on_this_day)) },
        modifier = modifier.fillMaxWidth().testTag(MemoryCardTags.ON_THIS_DAY),
        time = { Text(pluralStringResource(R.plurals.wear_memory_years_ago, day.yearsAgo, day.yearsAgo)) },
        transformation = transformation,
    ) {
        Text(text = body, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RewindCard(
    rewind: WeeklyRewind,
    open: (JournalNote) -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    TitleCard(
        onClick = { open(rewind.leadMoment) },
        title = { Text(stringResource(R.string.wear_memory_rewind)) },
        modifier = modifier.fillMaxWidth().testTag(MemoryCardTags.REWIND),
        transformation = transformation,
    ) {
        Text(text = rewind.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt`, add the parameter after `playback`:

```kotlin
    cards: TransformingLazyColumnScope.(TransformationSpec) -> Unit = {},
```

and in the `is FlowUiState.Ready ->` branch, call it between the moments and Earlier:

```kotlin
                is FlowUiState.Ready -> {
                    momentItems(state, actions, playback)
                    cards(spec)
                    earlierItem(state, actions.onOpenArchive, spec)
                }
```

Update the KDoc's first sentence to: `Home's second page: the voice and text moments of the last 7 days, newest first, each playable from its row, then the memory [cards], then Earlier when the library window has older moments.`

- [ ] **Step 7: Show the cards on Home**

In `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt`, add the imports `app.logdate.client.repository.journals.JournalNote`, `app.logdate.wear.v2.memories.MemoriesViewModel` and `app.logdate.wear.v2.memories.memoryCardItems` in ASCII order, and replace the whole `FlowEntry` function with:

```kotlin
@Composable
private fun FlowEntry(
    listState: TransformingLazyColumnState,
    navigate: (WearRoute) -> Unit,
) {
    val viewModel = koinViewModel<FlowViewModel>()
    val memories = koinViewModel<MemoriesViewModel>()
    val player = koinInject<MomentPlayer>()
    val state by viewModel.uiState.collectAsState()
    val memoryCards by memories.cards.collectAsState()
    val playback by player.state.collectAsState()
    val openMoment: (JournalNote) -> Unit = { note -> navigate(WearRoute.Moment(note.uid.toString())) }
    FlowPage(
        state = state,
        listState = listState,
        playback = playback,
        actions =
            FlowActions(
                onOpenMoment = openMoment,
                onPlayPause = player::playOrPause,
                onOpenArchive = { navigate(WearRoute.Archive) },
            ),
        cards = { spec -> memoryCardItems(memoryCards, spec, openMoment) },
    )
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add these imports in ASCII order: `app.logdate.client.domain.recommendation.GetMemoryRecallUseCase`, `app.logdate.wear.v2.memories.MemoriesViewModel`, `app.logdate.wear.v2.memories.OnThisDayFinder`, `app.logdate.wear.v2.memories.WATCH_RECALL_TYPES`, `app.logdate.wear.v2.memories.WeeklyRewindSource`, `kotlinx.coroutines.flow.first`. Add before the module's closing brace:

```kotlin
        single { GetMemoryRecallUseCase(notesRepository = get()) }
        single {
            val recall = get<GetMemoryRecallUseCase>()
            OnThisDayFinder(recall = { recall(contentTypes = WATCH_RECALL_TYPES).first() }, notesRepository = get())
        }
        single { WeeklyRewindSource(rewinds = get(), notesRepository = get()) }
        viewModel { MemoriesViewModel(dayClock = get(), onThisDay = get(), weeklyRewind = get()) }
```

- [ ] **Step 8: Run the unit tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.*' :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; 5 + 3 + 2 new tests pass.

- [ ] **Step 9: Run the GMD tests to verify they pass**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.FlowPageTest,app.logdate.wear.e2e.ShellNavigationTest
```

Expected: `BUILD SUCCESSFUL`, 5 + 4 tests passed. `ShellNavigationTest` proves the real Koin graph resolves `MemoriesViewModel` on the emulator.

- [ ] **Step 10: Write the screenshot scene**

In `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/FlowPageScreenshots.kt`, add these imports in ASCII order: `androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope`, `androidx.wear.compose.material3.lazy.TransformationSpec`, `app.logdate.wear.v2.memories.MemoryCards`, `app.logdate.wear.v2.memories.OnThisDay`, `app.logdate.wear.v2.memories.WeeklyRewind`, `app.logdate.wear.v2.memories.memoryCardItems`, `kotlinx.datetime.LocalDate`. Add this scene after `S06_FlowEarlierOnly`:

```kotlin
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S07_FlowMemories() {
        val lastYear = recentMoments[1]
        val cards =
            MemoryCards(
                onThisDay = OnThisDay(LocalDate(2025, 10, 3), yearsAgo = 1, leadMoment = lastYear, momentCount = 3, summary = "A day at the beach"),
                rewind = WeeklyRewind("A week of quiet mornings", lastYear),
            )
        FlowScene(
            FlowUiState.Ready(timeZone = flowZone, moments = emptyList(), earlierCount = 0),
            actions = allFlowActions,
            cards = { spec -> memoryCardItems(cards, spec, onOpenMoment = {}) },
        )
    }
```

Replace the `FlowScene` function with:

```kotlin
@Composable
private fun FlowScene(
    state: FlowUiState,
    playback: PlaybackState = PlaybackState.Idle,
    actions: FlowActions = FlowActions(),
    cards: TransformingLazyColumnScope.(TransformationSpec) -> Unit = {},
) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            FlowPage(state = state, timeSource = FlowTimeSource, actions = actions, playback = playback, cards = cards)
        }
    }
}
```

- [ ] **Step 11: Record and check the references**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest || true
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/FlowPageScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: the validation run fails listing only the 3 `S07_FlowMemories` previews as missing; the update prints `17` and only the three new files. Open `S07_FlowMemories_Small Round_*.png` with the Read tool: "No moments in the last 7 days", then an "On this day" card with "1 year ago" and "A day at the beach", then a "This week’s Rewind" card with "A week of quiet mornings".

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task5-commit.txt <<'EOF'
chore(wear): show On this day and this week's Rewind on the watch

The new watch app's moment list now shows an On this day card when a
past year has voice or text moments near today's date, using the same
recall the phone's widget uses, and opens that day's first voice note
or first text. A card for this week's Rewind opens its first moment on
the watch. It appears once the phone sends Rewinds to the watch; the
watch does not make them.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 13: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/memories app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt app/wear/src/main/res/values/strings_moments.xml app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/v2/memories app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/FlowPageTest.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/FlowPageScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/FlowPageScreenshots && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task5-commit.txt
```

---

### Task 6: Play on (`chore(wear)`)

"Play on…" on a voice note's details hands the choice of output to the system's own output switcher, where the person picks the watch speaker or headphones, and the system remembers that choice and moves to earbuds as they connect. Media3 treats a watch speaker as unsuitable unless the person chose it in that system picker, which is why the app does not pick outputs itself. Watches without the switcher open Bluetooth settings, which close once a device connects. The control appears only when one of the two resolves, which needs `<queries>` entries on Android 11 and later. When playback fails for want of an output, the player offers the same picker. The phone as a target joins this screen with the phone playback work (`phone.playback.v1`), so the `PlayOn` route stays unused here.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/playon/OutputPicker.kt`
- Modify: `app/wear/src/main/AndroidManifest.xml` (`<queries>`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentScreen.kt` (tags, `MomentActions`, `MomentDetailsPage`, `VoiceMoment`, `MomentPlayerPage`, `PlayerLine`, one composable)
- Modify: `app/wear/src/main/res/values/strings_moments.xml` (append)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` (`MomentEntry`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (one binding)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/playon/OutputPickerRouteTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/OutputPickerTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MomentDetailTest.kt` (three tests)
- Screenshot: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MomentScreenshots.kt` (two scenes)
- References: `…/screenshots/MomentScreenshots/` (6 new images)

**Interfaces:**
- Consumes (Task 3): `MomentActions`, `MomentDetailsPage`, `MomentTags`, `MomentEntry`. (Task 2): `PlaybackFailure.NO_OUTPUT`.
- Produces (package `app.logdate.wear.v2.playon`): `enum class OutputPickerRoute { OUTPUT_SWITCHER, BLUETOOTH_SETTINGS, NONE }`, `fun outputPickerRoute(switcherAvailable: Boolean, bluetoothSettingsAvailable: Boolean): OutputPickerRoute`, `interface OutputPicker { val route: OutputPickerRoute; fun open(): Boolean }`, `class SystemOutputPicker(context: Context) : OutputPicker` with `ACTION_MEDIA_OUTPUT` and `EXTRA_PACKAGE_NAME`. `MomentActions.onPlayOn: (() -> Unit)? = null`; `MomentDetailsPage(state, onDelete, onPlayOn: (() -> Unit)? = null, modifier, timeSource)`; `MomentTags.PLAY_ON`, `MomentTags.CHOOSE_OUTPUT`.

- [ ] **Step 1: Write the failing route test**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/playon/OutputPickerRouteTest.kt`:

```kotlin
package app.logdate.wear.v2.playon

import org.junit.Test
import kotlin.test.assertEquals

class OutputPickerRouteTest {
    @Test
    fun `the system output switcher comes first`() {
        assertEquals(OutputPickerRoute.OUTPUT_SWITCHER, outputPickerRoute(switcherAvailable = true, bluetoothSettingsAvailable = true))
    }

    @Test
    fun `without the switcher Bluetooth settings open instead`() {
        assertEquals(OutputPickerRoute.BLUETOOTH_SETTINGS, outputPickerRoute(switcherAvailable = false, bluetoothSettingsAvailable = true))
    }

    @Test
    fun `with neither there is nothing to offer`() {
        assertEquals(OutputPickerRoute.NONE, outputPickerRoute(switcherAvailable = false, bluetoothSettingsAvailable = false))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.playon.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'OutputPickerRoute'`.

- [ ] **Step 3: Write the failing GMD tests**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/OutputPickerTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.wear.v2.playon.OutputPickerRoute
import app.logdate.wear.v2.playon.SystemOutputPicker
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotEquals

/**
 * Checks on a Wear OS 5 emulator that the installed app can see an output picker: the system output
 * switcher, or Bluetooth settings. Without the manifest's `<queries>` entries neither resolves.
 */
@RunWith(AndroidJUnit4::class)
class OutputPickerTest {
    @Test
    fun an_output_picker_is_visible_to_the_app() {
        val picker = SystemOutputPicker(ApplicationProvider.getApplicationContext<Context>())

        assertNotEquals(OutputPickerRoute.NONE, picker.route)
    }
}
```

In `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MomentDetailTest.kt`, add `import app.logdate.wear.v2.playback.PlaybackFailure` in ASCII order and add these tests after `a_missing_moment_says_so`:

```kotlin
    @Test
    fun play_on_is_offered_on_a_voice_note_when_a_picker_exists() {
        var picks = 0
        show(ready(voice), MomentActions(onPlayOn = { picks++ }))

        composeRule.onNodeWithTag(MomentTags.SCREEN).performTouchInput { swipeUp() }
        waitForTag(MomentTags.DETAILS)
        composeRule.onNodeWithTag(MomentTags.DETAILS).performRotaryScrollInput { rotateToScrollVertically(CROWN_STEP) }
        waitForTag(MomentTags.PLAY_ON)
        composeRule.onNodeWithTag(MomentTags.PLAY_ON).performClick()

        assertEquals(1, picks)
    }

    @Test
    fun play_on_is_not_offered_without_a_picker() {
        show(ready(voice), MomentActions(onDelete = {}))

        composeRule.onNodeWithTag(MomentTags.SCREEN).performTouchInput { swipeUp() }
        waitForTag(MomentTags.DETAILS)
        composeRule.onNodeWithTag(MomentTags.DETAILS).performRotaryScrollInput { rotateToScrollVertically(CROWN_STEP) }
        waitForTag(MomentTags.DELETE)

        assertTrue(composeRule.onAllNodesWithTag(MomentTags.PLAY_ON).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun no_output_offers_the_picker_on_the_player() {
        var picks = 0
        show(ready(voice, PlaybackState.Failed(voice.uid, PlaybackFailure.NO_OUTPUT)), MomentActions(onPlayOn = { picks++ }))

        composeRule.onNodeWithTag(MomentTags.CHOOSE_OUTPUT).performClick()

        assertEquals(1, picks)
    }
```

- [ ] **Step 4: Run the GMD tests to verify they fail**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.OutputPickerTest,app.logdate.wear.e2e.MomentDetailTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'SystemOutputPicker'` and `No parameter with name 'onPlayOn' found`.

- [ ] **Step 5: Add the output picker**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/playon/OutputPicker.kt`:

```kotlin
package app.logdate.wear.v2.playon

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.aakira.napier.Napier

/** Where the person chooses an output: the system's output switcher, or Bluetooth settings. */
enum class OutputPickerRoute {
    OUTPUT_SWITCHER,
    BLUETOOTH_SETTINGS,
    NONE,
}

/** The picker to offer, preferring the system's own output switcher. */
fun outputPickerRoute(
    switcherAvailable: Boolean,
    bluetoothSettingsAvailable: Boolean,
): OutputPickerRoute =
    when {
        switcherAvailable -> OutputPickerRoute.OUTPUT_SWITCHER
        bluetoothSettingsAvailable -> OutputPickerRoute.BLUETOOTH_SETTINGS
        else -> OutputPickerRoute.NONE
    }

/** Opens the system's choice of audio outputs: the watch speaker or headphones. */
interface OutputPicker {
    val route: OutputPickerRoute

    /** @return false when no picker could open. */
    fun open(): Boolean
}

/**
 * [OutputPicker] over the system: the output switcher of Wear OS 4 and later, launched only on a
 * system activity, and otherwise Bluetooth settings that close once an audio device connects.
 */
class SystemOutputPicker(
    private val context: Context,
) : OutputPicker {
    override val route: OutputPickerRoute
        get() = outputPickerRoute(switcherComponent() != null, resolves(bluetoothSettingsIntent()))

    override fun open(): Boolean {
        val switcher = switcherComponent()
        val intent =
            when {
                switcher != null -> outputSwitcherIntent().setComponent(switcher)
                resolves(bluetoothSettingsIntent()) -> bluetoothSettingsIntent()
                else -> return false
            }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Napier.e("Couldn't open the output picker", e)
            false
        }
    }

    private fun outputSwitcherIntent(): Intent =
        Intent(ACTION_MEDIA_OUTPUT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_PACKAGE_NAME, context.packageName)

    private fun bluetoothSettingsIntent(): Intent =
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_CONNECTION_ONLY, true)
            .putExtra(EXTRA_CLOSE_ON_CONNECT, true)
            .putExtra(EXTRA_FILTER_TYPE, FILTER_TYPE_AUDIO)

    private fun switcherComponent(): ComponentName? =
        context.packageManager
            .queryIntentActivities(outputSwitcherIntent(), PackageManager.ResolveInfoFlags.of(0L))
            .mapNotNull { it.activityInfo }
            .firstOrNull { info -> info.applicationInfo != null && info.applicationInfo.flags and SYSTEM_APP_FLAGS != 0 }
            ?.let { info -> ComponentName(info.packageName, info.name) }

    private fun resolves(intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L)).isNotEmpty()

    companion object {
        const val ACTION_MEDIA_OUTPUT = "com.android.settings.panel.action.MEDIA_OUTPUT"
        const val EXTRA_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"
        private const val EXTRA_CONNECTION_ONLY = "EXTRA_CONNECTION_ONLY"
        private const val EXTRA_CLOSE_ON_CONNECT = "EXTRA_CLOSE_ON_CONNECT"
        private const val EXTRA_FILTER_TYPE = "android.bluetooth.devicepicker.extra.FILTER_TYPE"
        private const val FILTER_TYPE_AUDIO = 1
        private const val SYSTEM_APP_FLAGS = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    }
}
```

In `app/wear/src/main/AndroidManifest.xml`, replace

```xml
    <!-- Remove Google Maps package query merged from client:location -->
    <queries>
        <package android:name="com.google.android.apps.maps" tools:node="remove" />
    </queries>
```

with

```xml
    <!--
        Remove the Google Maps package query merged from client:location. Play on looks up the
        system output switcher and, on watches without it, Bluetooth settings.
    -->
    <queries>
        <package android:name="com.google.android.apps.maps" tools:node="remove" />
        <intent>
            <action android:name="com.android.settings.panel.action.MEDIA_OUTPUT" />
        </intent>
        <intent>
            <action android:name="android.settings.BLUETOOTH_SETTINGS" />
        </intent>
    </queries>
```

- [ ] **Step 6: Offer Play on in a moment's screen**

Append to `app/wear/src/main/res/values/strings_moments.xml`, before `</resources>`:

```xml

    <!-- Play on, in the new watch app -->
    <string name="wear_moment_play_on">Play on…</string>
    <string name="wear_moment_choose_output">Choose where to play</string>
```

In `app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentScreen.kt`:

Add `import androidx.compose.material.icons.filled.Headphones` after `import androidx.compose.material.icons.filled.FastForward` and `import androidx.wear.compose.material3.Button` after `import androidx.wear.compose.material3.AnimatedPage`.

In `MomentTags`, add after `const val MISSING = "moment_missing"`:

```kotlin
    const val PLAY_ON = "moment_play_on"
    const val CHOOSE_OUTPUT = "moment_choose_output"
```

Replace the `MomentActions` KDoc and class with:

```kotlin
/** What the person can do on a moment's screen. Delete and Play on appear only with their handlers. */
data class MomentActions(
    val onPlayPause: () -> Unit = {},
    val onCrown: (Float) -> Unit = {},
    val onCycleSpeed: () -> Unit = {},
    val onSkipSilenceChange: (Boolean) -> Unit = {},
    val onDelete: (() -> Unit)? = null,
    val onPlayOn: (() -> Unit)? = null,
)
```

Replace the signature and KDoc of `MomentDetailsPage` with:

```kotlin
/**
 * A moment's details: when, its words, its place, Play on for a voice note when [onPlayOn] is set,
 * and Delete when [onDelete] is set.
 */
@Composable
fun MomentDetailsPage(
    state: MomentUiState.Ready,
    onDelete: (() -> Unit)?,
    onPlayOn: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
) {
```

and, inside it, add directly before `if (state.deleteFailed) …`:

```kotlin
            if (onPlayOn != null && note is JournalNote.Audio) {
                item { PlayOnButton(onPlayOn, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec)) }
            }
```

In `VoiceMoment`, replace `MomentDetailsPage(state, onDelete, timeSource = timeSource)` with `MomentDetailsPage(state, onDelete, onPlayOn = actions.onPlayOn, timeSource = timeSource)`.

In `MomentPlayerPage`, replace `PlayerLine(note, state.playback, positionMs)` with `PlayerLine(note, state.playback, positionMs, actions.onPlayOn)`.

Replace the whole `PlayerLine` function with:

```kotlin
@Composable
private fun PlayerLine(
    note: JournalNote.Audio,
    playback: PlaybackState,
    positionMs: Long,
    onPlayOn: (() -> Unit)?,
) {
    val text = if (playback is PlaybackState.Failed) failureMessage(playback.reason) else captionAt(note, positionMs)
    if (text == null) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(MomentTags.CAPTION),
    )
    val noOutput = playback is PlaybackState.Failed && playback.reason == PlaybackFailure.NO_OUTPUT
    if (!noOutput || onPlayOn == null) return
    CompactButton(
        onClick = onPlayOn,
        modifier = Modifier.testTag(MomentTags.CHOOSE_OUTPUT),
        label = { Text(stringResource(R.string.wear_moment_choose_output)) },
    )
}
```

Add this composable directly before `DeleteButton`:

```kotlin
@Composable
private fun PlayOnButton(
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(MomentTags.PLAY_ON),
        transformation = transformation,
        icon = { Icon(imageVector = Icons.Filled.Headphones, contentDescription = null) },
        label = { Text(stringResource(R.string.wear_moment_play_on)) },
    )
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt`, add the imports `androidx.compose.runtime.remember`, `app.logdate.wear.v2.playon.OutputPicker` and `app.logdate.wear.v2.playon.OutputPickerRoute` in ASCII order. In `MomentEntry`, add after `val deleted by viewModel.deleted.collectAsState()`:

```kotlin
    val picker = koinInject<OutputPicker>()
    val canPick = remember(picker) { picker.route != OutputPickerRoute.NONE }
```

```kotlin
    val playOn: (() -> Unit)? = if (canPick) ({ picker.open() }) else null
```

and add `onPlayOn = playOn,` as the last argument of `MomentActions(…)`.

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add `import app.logdate.wear.v2.playon.OutputPicker` and `import app.logdate.wear.v2.playon.SystemOutputPicker` in ASCII order and, before the module's closing brace:

```kotlin
        single<OutputPicker> { SystemOutputPicker(get()) }
```

- [ ] **Step 7: Check the merged manifest**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:processDebugMainManifest && grep -c "com.android.settings.panel.action.MEDIA_OUTPUT\|android.settings.BLUETOOTH_SETTINGS" app/wear/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml && grep -c "com.google.android.apps.maps" app/wear/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
```

Expected: `BUILD SUCCESSFUL`, then `2`, then `0`.

- [ ] **Step 8: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.*' :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.OutputPickerTest,app.logdate.wear.e2e.MomentDetailTest
```

Expected: `BUILD SUCCESSFUL` twice; 3 route tests, then 1 + 9 GMD tests pass. If `an_output_picker_is_visible_to_the_app` fails, read the merged manifest from Step 7 before anything else: the `<queries>` entries are what make both intents visible.

- [ ] **Step 9: Add the screenshot scenes, record and check them**

In `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MomentScreenshots.kt`, add these scenes after `S06_Missing`:

```kotlin
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S07_VoiceDetailsWithPlayOn() {
        LogDateWearTheme(dynamicColor = false) {
            AppScaffold(timeText = {}) {
                MomentDetailsPage(state = ready(voice), onDelete = {}, onPlayOn = {}, timeSource = MomentTimeSource)
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S08_VoiceNoOutput() {
        LogDateWearTheme(dynamicColor = false) {
            AppScaffold(timeText = {}) {
                MomentScreen(
                    state = ready(voice, playback = PlaybackState.Failed(voice.uid, PlaybackFailure.NO_OUTPUT)),
                    actions = MomentActions(onDelete = {}, onPlayOn = {}),
                    timeSource = MomentTimeSource,
                )
            }
        }
    }
```

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest || true
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/MomentScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: validation lists only the 6 new previews as missing; then `24` and only new files. Open `S08_VoiceNoOutput_Small Round_*.png` with the Read tool: "No speaker or headphones to play on" with a "Choose where to play" button under it, nothing clipped. Then:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task6-commit.txt <<'EOF'
chore(wear): choose the watch speaker or headphones with Play on

A voice note's details in the new watch app now offer Play on, which
opens the system's output switcher, where the person picks the watch
speaker or headphones; watches without it open Bluetooth settings
instead. The system remembers the choice and moves to earbuds as they
connect. When a recording cannot play for want of an output, the
player offers the same picker. Play on appears only when one of the
two pickers exists, which the manifest now lets the app look up.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/playon app/wear/src/main/AndroidManifest.xml app/wear/src/main/kotlin/app/logdate/wear/v2/moments/MomentScreen.kt app/wear/src/main/res/values/strings_moments.xml app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/v2/playon app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/OutputPickerTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MomentDetailTest.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/MomentScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/MomentScreenshots && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b5-task6-commit.txt
```

---

### Task 7: The settings section and its status card (`chore(wear)`)

The moment list ends with settings. A status card comes first, and only when something is wrong: moments waiting while the phone is out of reach, or sends failing while it is in reach, with a tap to try again. It follows the live sync status and the phone's reachability as they change, without polling. The cloud gets no line until the watch has its own cloud link (the phone's backups are the phone's to report). Then Haptics (All or Essential, the store that already exists), Places (whether captures save the place; turning it on asks for location first), and "More on your phone", which opens the phone's Watch page once the phone advertises `phone.settingsLink.v1`. Reminders join in Task 9, once they work; Hold to talk belongs to the recorder plan.

**Files:**
- Create: `app/wear/src/main/res/values/strings_watch_settings.xml`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchStatus.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModel.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsItems.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsEntry.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt` (one parameter, one call)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` (imports, `FlowEntry`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (one binding)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/settings/WatchStatusTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModelTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchSettingsTest.kt`
- Screenshot: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WatchSettingsScreenshots.kt`
- References: `…/screenshots/WatchSettingsScreenshots/` (new, 12 images)

**Interfaces:**
- Consumes: `SyncManager.syncStatusFlow: StateFlow<SyncStatus>` and `fullSync()`; `PhoneReachability.observe(): Flow<ReachablePhone?>`; `HapticsPreferenceStore`; `LocationTrackingSettingsRepository.observeSettings()/getSettings()/updateSettings()`; `PermissionManager.isPermissionGranted(PermissionType.LOCATION)`; `PhoneLinkRepository.observe(): Flow<PhoneLink>`; `WearProtocolGate.isFeatureActive(WearFeatures.PHONE_SETTINGS_LINK)`; `PhoneHandoff.open(link, nodeId): HandoffResult`; `HandoffState`. (Task 5): `FlowPage`, `FlowEntry`.
- Produces (package `app.logdate.wear.v2.settings`): `sealed interface WatchStatus { Healthy; WaitingForPhone(pending: Int); SendFailing(pending: Int) }`, `fun watchStatusOf(sync: SyncStatus, phone: ReachablePhone?): WatchStatus`, `data class WatchSettingsUiState(status, haptics, places, phoneSettingsNodeId: String?, handoff: HandoffState, retrying: Boolean)`, `class WatchSettingsViewModel(syncManager, phoneReachability, haptics, locationSettings, permissionManager, phoneLinks, protocolGate, phoneHandoff)` with `uiState`, `setHaptics`, `setPlaces`, `placesNeedPermission()`, `onLocationPermissionResult(Boolean)`, `onResume()`, `retrySync()`, `openPhoneSettings()`, `dismissHandoff()` and `PHONE_WATCH_SETTINGS_LINK`; `object WatchSettingsTags`; `data class WatchSettingsActions(onHaptics, onPlaces, onRetrySync, onOpenPhoneSettings)`; `fun TransformingLazyColumnScope.watchSettingsItems(state, actions, spec)`; `@Composable fun rememberWatchSettingsActions(viewModel): WatchSettingsActions`; `@Composable fun PhoneSettingsHandoffDialog(handoff: HandoffState, onDismiss: () -> Unit)`. `FlowPage` gains `footer: TransformingLazyColumnScope.(TransformationSpec) -> Unit = {}`.

- [ ] **Step 1: Write the failing status and view model tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/settings/WatchStatusTest.kt`:

```kotlin
package app.logdate.wear.v2.settings

import app.logdate.client.sync.SyncStatus
import app.logdate.wear.sync.ReachablePhone
import org.junit.Test
import kotlin.test.assertEquals

class WatchStatusTest {
    private val phone = ReachablePhone("Pixel")

    @Test
    fun `nothing waiting is healthy whether or not the phone is near`() {
        assertEquals(WatchStatus.Healthy, watchStatusOf(status(pending = 0), phone))
        assertEquals(WatchStatus.Healthy, watchStatusOf(status(pending = 0), null))
    }

    @Test
    fun `moments waiting with the phone away are waiting for the phone`() {
        assertEquals(WatchStatus.WaitingForPhone(3), watchStatusOf(status(pending = 3, errors = true), null))
    }

    @Test
    fun `failed sends with the phone in reach are failing`() {
        assertEquals(WatchStatus.SendFailing(2), watchStatusOf(status(pending = 2, errors = true), phone))
    }

    @Test
    fun `moments on their way to a phone in reach are healthy`() {
        assertEquals(WatchStatus.Healthy, watchStatusOf(status(pending = 2), phone))
        assertEquals(WatchStatus.Healthy, watchStatusOf(status(pending = 2, errors = true, syncing = true), phone))
    }

    private fun status(
        pending: Int,
        errors: Boolean = false,
        syncing: Boolean = false,
    ) = SyncStatus(isEnabled = true, lastSyncTime = null, pendingUploads = pending, isSyncing = syncing, hasErrors = errors)
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModelTest.kt`:

```kotlin
package app.logdate.wear.v2.settings

import app.cash.turbine.test
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearProtocolGate
import app.logdate.wear.sync.ReachablePhone
import app.logdate.wear.v2.haptics.HapticsPreference
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.phone.HandoffResult
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLink
import app.logdate.wear.v2.phone.PhoneLinkRepository
import app.logdate.wear.v2.today.HandoffState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WatchSettingsViewModelTest {
    private val syncStatus = MutableStateFlow(SyncStatus(isEnabled = true, lastSyncTime = null, pendingUploads = 0, isSyncing = false, hasErrors = false))
    private val syncManager = mockk<SyncManager>(relaxed = true) { every { syncStatusFlow } returns syncStatus }
    private val phone = MutableStateFlow<ReachablePhone?>(ReachablePhone("Pixel"))
    private val haptics = FakeHapticsStore()
    private val locationSettings = MutableStateFlow(LocationTrackingSettings())
    private val locationRepository = mockk<LocationTrackingSettingsRepository>(relaxed = true)
    private val permissionManager = mockk<PermissionManager>(relaxed = true)
    private val phoneLink = MutableStateFlow<PhoneLink>(PhoneLink.Connected(nodeId = "phone-node", phoneName = "Pixel"))
    private val phoneLinks = mockk<PhoneLinkRepository> { every { observe() } returns phoneLink }
    private val phoneFeatures = MutableStateFlow(emptySet<String>())
    private val phoneHandoff = mockk<PhoneHandoff>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { locationRepository.observeSettings() } returns locationSettings
        coEvery { locationRepository.getSettings() } answers { locationSettings.value }
        coEvery { locationRepository.updateSettings(any()) } answers { locationSettings.value = firstArg() }
        every { permissionManager.isPermissionGranted(PermissionType.LOCATION) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `healthy sync shows no status`() =
        runTest {
            viewModel().uiState.test { assertEquals(WatchStatus.Healthy, expectMostRecentItem().status) }
        }

    @Test
    fun `waiting moments with the phone away say so as it happens`() =
        runTest {
            viewModel().uiState.test {
                phone.value = null
                syncStatus.value = syncStatus.value.copy(pendingUploads = 3)

                assertEquals(WatchStatus.WaitingForPhone(3), expectMostRecentItem().status)
            }
        }

    @Test
    fun `a failing send can be retried once at a time`() =
        runTest {
            val finish = CompletableDeferred<SyncResult>()
            coEvery { syncManager.fullSync() } coAnswers { finish.await() }
            syncStatus.value = syncStatus.value.copy(pendingUploads = 2, hasErrors = true)
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(WatchStatus.SendFailing(2), expectMostRecentItem().status)
                viewModel.retrySync()
                viewModel.retrySync()
                assertTrue(expectMostRecentItem().retrying)
                finish.complete(SyncResult(success = true, uploadedItems = 2))
                assertEquals(false, expectMostRecentItem().retrying)
            }
            coVerify(exactly = 1) { syncManager.fullSync() }
        }

    @Test
    fun `haptics follow the store and a choice is saved`() =
        runTest {
            val viewModel = viewModel()

            viewModel.uiState.test {
                viewModel.setHaptics(HapticsPreference.ESSENTIAL)

                assertEquals(HapticsPreference.ESSENTIAL, expectMostRecentItem().haptics)
            }
            assertEquals(HapticsPreference.ESSENTIAL, haptics.preference.value)
        }

    @Test
    fun `places are on only with the setting and location access`() =
        runTest {
            every { permissionManager.isPermissionGranted(PermissionType.LOCATION) } returns false

            viewModel().uiState.test { assertEquals(false, expectMostRecentItem().places) }
        }

    @Test
    fun `turning places on waits for location access when it is missing`() =
        runTest {
            locationSettings.value = LocationTrackingSettings(autoTrackForJournalEntries = false)
            every { permissionManager.isPermissionGranted(PermissionType.LOCATION) } returns false
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertTrue(viewModel.placesNeedPermission())
                viewModel.onLocationPermissionResult(granted = true)

                assertEquals(true, expectMostRecentItem().places)
            }
        }

    @Test
    fun `turning places off saves it`() =
        runTest {
            viewModel().setPlaces(false)

            assertEquals(false, locationSettings.value.autoTrackForJournalEntries)
        }

    @Test
    fun `more on your phone needs the phone's settings link and a connected phone`() =
        runTest {
            viewModel().uiState.test {
                assertNull(expectMostRecentItem().phoneSettingsNodeId)
                phoneFeatures.value = setOf(WearFeatures.PHONE_SETTINGS_LINK)
                assertEquals("phone-node", expectMostRecentItem().phoneSettingsNodeId)
                phoneLink.value = PhoneLink.NotConnected
                assertNull(expectMostRecentItem().phoneSettingsNodeId)
            }
        }

    @Test
    fun `opening the phone's watch page reports when it was sent`() =
        runTest {
            phoneFeatures.value = setOf(WearFeatures.PHONE_SETTINGS_LINK)
            coEvery { phoneHandoff.open(WatchSettingsViewModel.PHONE_WATCH_SETTINGS_LINK, "phone-node") } returns HandoffResult.SENT
            val viewModel = viewModel()

            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.openPhoneSettings()

                assertEquals(HandoffState.SENT, expectMostRecentItem().handoff)
            }
        }

    private fun viewModel() =
        WatchSettingsViewModel(
            syncManager = syncManager,
            phoneReachability = { phone },
            haptics = haptics,
            locationSettings = locationRepository,
            permissionManager = permissionManager,
            phoneLinks = phoneLinks,
            protocolGate = WearProtocolGate(flowOf(true), flowOf(true), phoneFeatures),
            phoneHandoff = phoneHandoff,
        )

    private class FakeHapticsStore : HapticsPreferenceStore {
        override val preference = MutableStateFlow(HapticsPreference.ALL)

        override fun set(preference: HapticsPreference) {
            this.preference.value = preference
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.settings.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'watchStatusOf'` and `Unresolved reference 'WatchSettingsViewModel'`.

- [ ] **Step 3: Write the failing GMD test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchSettingsTest.kt`:

```kotlin
package app.logdate.wear.e2e

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import app.logdate.wear.v2.haptics.HapticsPreference
import app.logdate.wear.v2.settings.WatchSettingsActions
import app.logdate.wear.v2.settings.WatchSettingsTags
import app.logdate.wear.v2.settings.WatchSettingsUiState
import app.logdate.wear.v2.settings.WatchStatus
import app.logdate.wear.v2.settings.watchSettingsItems
import app.logdate.wear.v2.theme.LogDateWearTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Taps through the settings section on a Wear OS emulator, scrolling with the crown to reach each control. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WatchSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun healthy_sync_shows_no_status_card() {
        show(WatchSettingsUiState())

        assertTrue(composeRule.onAllNodesWithTag(WatchSettingsTags.STATUS).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun waiting_moments_show_how_many() {
        show(WatchSettingsUiState(status = WatchStatus.WaitingForPhone(3)))

        composeRule.onNodeWithText("3 moments waiting for your phone").assertIsDisplayed()
    }

    @Test
    fun a_failing_send_retries_on_tap() {
        var retries = 0
        show(WatchSettingsUiState(status = WatchStatus.SendFailing(2)), WatchSettingsActions(onRetrySync = { retries++ }))

        composeRule.onNodeWithTag(WatchSettingsTags.STATUS).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun choosing_essential_haptics_reports_it() {
        val chosen = mutableListOf<HapticsPreference>()
        show(WatchSettingsUiState(), WatchSettingsActions(onHaptics = { chosen += it }))

        clickWhenShown(WatchSettingsTags.HAPTICS_ESSENTIAL)

        assertEquals(listOf(HapticsPreference.ESSENTIAL), chosen)
    }

    @Test
    fun the_places_switch_reports_the_new_value() {
        val values = mutableListOf<Boolean>()
        show(WatchSettingsUiState(places = false), WatchSettingsActions(onPlaces = { values += it }))

        clickWhenShown(WatchSettingsTags.PLACES)

        assertEquals(listOf(true), values)
    }

    @Test
    fun more_on_your_phone_appears_only_when_offered() {
        var opens = 0
        show(WatchSettingsUiState(phoneSettingsNodeId = "phone-node"), WatchSettingsActions(onOpenPhoneSettings = { opens++ }))

        clickWhenShown(WatchSettingsTags.MORE_ON_PHONE)

        assertEquals(1, opens)
    }

    private fun show(
        state: WatchSettingsUiState,
        actions: WatchSettingsActions = WatchSettingsActions(),
    ) {
        composeRule.setContent { SettingsScene(state, actions) }
    }

    private fun clickWhenShown(tag: String) {
        repeat(MAX_SCROLLS) {
            if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithTag(tag).performClick()
                return
            }
            composeRule.onNodeWithTag(SETTINGS_LIST).performRotaryScrollInput { rotateToScrollVertically(SCROLL_STEP) }
            composeRule.waitForIdle()
        }
        error("$tag never appeared in the settings list")
    }

    private companion object {
        const val MAX_SCROLLS = 8
        const val SCROLL_STEP = 120f
    }
}

private const val SETTINGS_LIST = "settings_list"

@Composable
private fun SettingsScene(
    state: WatchSettingsUiState,
    actions: WatchSettingsActions,
) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            val listState = rememberTransformingLazyColumnState()
            val spec = rememberTransformationSpec()
            ScreenScaffold(scrollState = listState, timeText = {}) { contentPadding ->
                TransformingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.testTag(SETTINGS_LIST)) {
                    watchSettingsItems(state, actions, spec)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run the GMD test to verify it fails**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WatchSettingsTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'watchSettingsItems'`.

- [ ] **Step 5: Add the copy, the status and the view model**

Create `app/wear/src/main/res/values/strings_watch_settings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Settings at the end of the moment list, in the new watch app -->
    <string name="wear_settings_title">Settings</string>
    <plurals name="wear_settings_waiting_for_phone">
        <item quantity="one">%1$d moment waiting for your phone</item>
        <item quantity="other">%1$d moments waiting for your phone</item>
    </plurals>
    <string name="wear_settings_waiting_for_phone_body">They’re sent when your phone is nearby</string>
    <string name="wear_settings_send_failing">Some moments couldn’t reach your phone</string>
    <string name="wear_settings_send_retry">Tap to try again</string>
    <string name="wear_settings_send_retrying">Trying again…</string>
    <string name="wear_settings_haptics_all">All haptics</string>
    <string name="wear_settings_haptics_all_body">Crown ticks and saves too</string>
    <string name="wear_settings_haptics_essential">Essential haptics</string>
    <string name="wear_settings_haptics_essential_body">Recording, marks and errors only</string>
    <string name="wear_settings_places">Places</string>
    <string name="wear_settings_places_body">Save where you are with each moment</string>
    <string name="wear_settings_more_on_phone">More on your phone</string>
    <string name="wear_settings_more_on_phone_failed">Couldn’t reach your phone. Tap to try again.</string>
</resources>
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchStatus.kt`:

```kotlin
package app.logdate.wear.v2.settings

import app.logdate.client.sync.SyncStatus
import app.logdate.wear.sync.ReachablePhone

/** What the status card says. It says nothing while sync is healthy. */
sealed interface WatchStatus {
    data object Healthy : WatchStatus

    /** Moments made on the watch wait for the phone, which is out of reach. */
    data class WaitingForPhone(
        val pending: Int,
    ) : WatchStatus

    /** Sending moments to the phone failed while it was in reach, and no send is running. */
    data class SendFailing(
        val pending: Int,
    ) : WatchStatus
}

/** The status for [sync] while [phone] is in reach, or while no phone is when it is null. */
fun watchStatusOf(
    sync: SyncStatus,
    phone: ReachablePhone?,
): WatchStatus {
    if (sync.pendingUploads == 0) return WatchStatus.Healthy
    if (phone == null) return WatchStatus.WaitingForPhone(sync.pendingUploads)
    if (sync.hasErrors && !sync.isSyncing) return WatchStatus.SendFailing(sync.pendingUploads)
    return WatchStatus.Healthy
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModel.kt`:

```kotlin
package app.logdate.wear.v2.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearProtocolGate
import app.logdate.wear.sync.PhoneReachability
import app.logdate.wear.v2.haptics.HapticsPreference
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.phone.HandoffResult
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLink
import app.logdate.wear.v2.phone.PhoneLinkRepository
import app.logdate.wear.v2.today.HandoffState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the settings section shows.
 *
 * @property places Captures save the place: the setting is on and location access is granted.
 * @property phoneSettingsNodeId The phone "More on your phone" opens, or null while it cannot.
 */
data class WatchSettingsUiState(
    val status: WatchStatus = WatchStatus.Healthy,
    val haptics: HapticsPreference = HapticsPreference.ALL,
    val places: Boolean = false,
    val phoneSettingsNodeId: String? = null,
    val handoff: HandoffState = HandoffState.IDLE,
    val retrying: Boolean = false,
)

/**
 * The settings at the end of the moment list. Every input is followed as it changes; only location
 * access is read again on resume, since it can change in system settings without an event.
 */
class WatchSettingsViewModel(
    private val syncManager: SyncManager,
    phoneReachability: PhoneReachability,
    private val haptics: HapticsPreferenceStore,
    private val locationSettings: LocationTrackingSettingsRepository,
    private val permissionManager: PermissionManager,
    phoneLinks: PhoneLinkRepository,
    protocolGate: WearProtocolGate,
    private val phoneHandoff: PhoneHandoff,
) : ViewModel() {
    private val locationGranted = MutableStateFlow(permissionManager.isPermissionGranted(PermissionType.LOCATION))
    private val handoff = MutableStateFlow(HandoffState.IDLE)
    private val retrying = MutableStateFlow(false)

    private val status = combine(syncManager.syncStatusFlow, phoneReachability.observe(), ::watchStatusOf)
    private val places =
        combine(locationSettings.observeSettings(), locationGranted) { settings, granted -> settings.autoTrackForJournalEntries && granted }
    private val phoneSettingsNode =
        combine(protocolGate.isFeatureActive(WearFeatures.PHONE_SETTINGS_LINK), phoneLinks.observe()) { active, link ->
            (link as? PhoneLink.Connected)?.nodeId?.takeIf { active }
        }

    val uiState: StateFlow<WatchSettingsUiState> =
        combine(
            status,
            haptics.preference,
            places,
            phoneSettingsNode,
            combine(handoff, retrying, ::Pair),
        ) { syncStatus, hapticsChoice, placesOn, node, (handoffState, isRetrying) ->
            WatchSettingsUiState(
                status = syncStatus,
                haptics = hapticsChoice,
                places = placesOn,
                phoneSettingsNodeId = node,
                handoff = handoffState,
                retrying = isRetrying,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WatchSettingsUiState())

    fun setHaptics(preference: HapticsPreference) = haptics.set(preference)

    fun setPlaces(enabled: Boolean) {
        viewModelScope.launch {
            try {
                locationSettings.updateSettings(locationSettings.getSettings().copy(autoTrackForJournalEntries = enabled))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Couldn't turn places ${if (enabled) "on" else "off"} on the watch", e)
            }
        }
    }

    /** Whether turning places on first needs location access. */
    fun placesNeedPermission(): Boolean = !permissionManager.isPermissionGranted(PermissionType.LOCATION)

    fun onLocationPermissionResult(granted: Boolean) {
        locationGranted.value = granted
        if (granted) setPlaces(true)
    }

    /** Reads location access again; it may have changed in system settings. */
    fun onResume() {
        locationGranted.value = permissionManager.isPermissionGranted(PermissionType.LOCATION)
    }

    /** Sends what is waiting again, once at a time. */
    fun retrySync() {
        if (retrying.value) return
        retrying.value = true
        viewModelScope.launch {
            try {
                syncManager.fullSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Sending moments to the phone again failed", e)
            } finally {
                retrying.value = false
            }
        }
    }

    /** Opens the phone's Watch page on the phone. */
    fun openPhoneSettings() {
        val node = uiState.value.phoneSettingsNodeId ?: return
        if (handoff.value == HandoffState.SENDING) return
        handoff.value = HandoffState.SENDING
        viewModelScope.launch {
            handoff.value =
                when (phoneHandoff.open(PHONE_WATCH_SETTINGS_LINK, node)) {
                    HandoffResult.SENT -> HandoffState.SENT
                    HandoffResult.FAILED -> HandoffState.FAILED
                }
        }
    }

    fun dismissHandoff() {
        handoff.value = HandoffState.IDLE
    }

    companion object {
        const val PHONE_WATCH_SETTINGS_LINK = "studio.hypertext.logdate://settings/watch"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 6: Add the section and its entry helpers**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsItems.kt`:

```kotlin
package app.logdate.wear.v2.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.wear.R
import app.logdate.wear.v2.haptics.HapticsPreference
import app.logdate.wear.v2.today.HandoffState

object WatchSettingsTags {
    const val STATUS = "settings_status"
    const val HAPTICS_ALL = "settings_haptics_all"
    const val HAPTICS_ESSENTIAL = "settings_haptics_essential"
    const val PLACES = "settings_places"
    const val MORE_ON_PHONE = "settings_more_on_phone"
}

/** What the settings section lets the person do. */
data class WatchSettingsActions(
    val onHaptics: (HapticsPreference) -> Unit = {},
    val onPlaces: (Boolean) -> Unit = {},
    val onRetrySync: () -> Unit = {},
    val onOpenPhoneSettings: () -> Unit = {},
)

/** The settings at the end of the moment list: the status card when something is wrong, then the settings. */
fun TransformingLazyColumnScope.watchSettingsItems(
    state: WatchSettingsUiState,
    actions: WatchSettingsActions,
    spec: TransformationSpec,
) {
    statusItem(state, actions, spec)
    item {
        ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
            Text(stringResource(R.string.wear_settings_title))
        }
    }
    item { HapticsChoice(HapticsPreference.ALL, state.haptics, actions.onHaptics, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec)) }
    item {
        HapticsChoice(HapticsPreference.ESSENTIAL, state.haptics, actions.onHaptics, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec))
    }
    item { PlacesSwitch(state.places, actions.onPlaces, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec)) }
    if (state.phoneSettingsNodeId != null) {
        item { MoreOnPhoneButton(state.handoff, actions.onOpenPhoneSettings, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec)) }
    }
}

private fun TransformingLazyColumnScope.statusItem(
    state: WatchSettingsUiState,
    actions: WatchSettingsActions,
    spec: TransformationSpec,
) {
    when (val status = state.status) {
        WatchStatus.Healthy -> Unit
        is WatchStatus.WaitingForPhone -> item { WaitingForPhone(status.pending) }
        is WatchStatus.SendFailing ->
            item { SendFailingCard(state.retrying, actions.onRetrySync, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec)) }
    }
}

@Composable
private fun WaitingForPhone(pending: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(WatchSettingsTags.STATUS),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = pluralStringResource(R.plurals.wear_settings_waiting_for_phone, pending, pending),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.wear_settings_waiting_for_phone_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SendFailingCard(
    retrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    TitleCard(
        onClick = onRetry,
        title = { Text(stringResource(R.string.wear_settings_send_failing)) },
        modifier = modifier.fillMaxWidth().testTag(WatchSettingsTags.STATUS),
        enabled = !retrying,
        transformation = transformation,
    ) {
        Text(stringResource(if (retrying) R.string.wear_settings_send_retrying else R.string.wear_settings_send_retry))
    }
}

@Composable
private fun HapticsChoice(
    option: HapticsPreference,
    current: HapticsPreference,
    onSelect: (HapticsPreference) -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val essential = option == HapticsPreference.ESSENTIAL
    RadioButton(
        selected = current == option,
        onSelect = { onSelect(option) },
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(if (essential) WatchSettingsTags.HAPTICS_ESSENTIAL else WatchSettingsTags.HAPTICS_ALL),
        transformation = transformation,
        secondaryLabel = {
            Text(stringResource(if (essential) R.string.wear_settings_haptics_essential_body else R.string.wear_settings_haptics_all_body))
        },
        label = { Text(stringResource(if (essential) R.string.wear_settings_haptics_essential else R.string.wear_settings_haptics_all)) },
    )
}

@Composable
private fun PlacesSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.fillMaxWidth().testTag(WatchSettingsTags.PLACES),
        transformation = transformation,
        secondaryLabel = { Text(stringResource(R.string.wear_settings_places_body)) },
        label = { Text(stringResource(R.string.wear_settings_places)) },
    )
}

@Composable
private fun MoreOnPhoneButton(
    handoff: HandoffState,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(WatchSettingsTags.MORE_ON_PHONE),
        enabled = handoff != HandoffState.SENDING,
        transformation = transformation,
        icon = { Icon(imageVector = Icons.Filled.PhoneAndroid, contentDescription = null) },
        secondaryLabel =
            if (handoff == HandoffState.FAILED) {
                { Text(stringResource(R.string.wear_settings_more_on_phone_failed)) }
            } else {
                null
            },
        label = { Text(stringResource(R.string.wear_settings_more_on_phone)) },
    )
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsEntry.kt`:

```kotlin
package app.logdate.wear.v2.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import app.logdate.wear.v2.today.HandoffState

/**
 * The settings section's actions, with the location request Places needs, and location access read
 * again each time the app resumes.
 */
@Composable
fun rememberWatchSettingsActions(viewModel: WatchSettingsViewModel): WatchSettingsActions {
    val locationRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            viewModel.onLocationPermissionResult(results.values.any { it })
        }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(viewModel, locationRequest) {
        WatchSettingsActions(
            onHaptics = viewModel::setHaptics,
            onPlaces = { enabled ->
                if (enabled && viewModel.placesNeedPermission()) locationRequest.launch(LOCATION_PERMISSIONS) else viewModel.setPlaces(enabled)
            },
            onRetrySync = viewModel::retrySync,
            onOpenPhoneSettings = viewModel::openPhoneSettings,
        )
    }
}

/** Says "Continue on phone" once the phone opened its Watch page. */
@Composable
fun PhoneSettingsHandoffDialog(
    handoff: HandoffState,
    onDismiss: () -> Unit,
) {
    val text = OpenOnPhoneDialogDefaults.text
    val style = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = handoff == HandoffState.SENT,
        onDismissRequest = onDismiss,
        curvedText = { openOnPhoneDialogCurvedText(text = text, style = style) },
    )
}

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
```

- [ ] **Step 7: Put the section at the end of the list**

In `app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt`, add the parameter after `cards`:

```kotlin
    footer: TransformingLazyColumnScope.(TransformationSpec) -> Unit = {},
```

and call `footer(spec)` directly after the closing brace of the `when (state) { … }` inside `TransformingLazyColumn`, so settings show even while the moments load or fail. Add `, and [footer] at the very end` before the final period of the KDoc's first sentence.

In `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt`, add the imports `app.logdate.wear.v2.settings.PhoneSettingsHandoffDialog`, `app.logdate.wear.v2.settings.WatchSettingsViewModel`, `app.logdate.wear.v2.settings.rememberWatchSettingsActions` and `app.logdate.wear.v2.settings.watchSettingsItems` in ASCII order, and replace the whole `FlowEntry` function with:

```kotlin
@Composable
private fun FlowEntry(
    listState: TransformingLazyColumnState,
    navigate: (WearRoute) -> Unit,
) {
    val viewModel = koinViewModel<FlowViewModel>()
    val memories = koinViewModel<MemoriesViewModel>()
    val settings = koinViewModel<WatchSettingsViewModel>()
    val player = koinInject<MomentPlayer>()
    val state by viewModel.uiState.collectAsState()
    val memoryCards by memories.cards.collectAsState()
    val settingsState by settings.uiState.collectAsState()
    val playback by player.state.collectAsState()
    val settingsActions = rememberWatchSettingsActions(settings)
    val openMoment: (JournalNote) -> Unit = { note -> navigate(WearRoute.Moment(note.uid.toString())) }
    FlowPage(
        state = state,
        listState = listState,
        playback = playback,
        actions =
            FlowActions(
                onOpenMoment = openMoment,
                onPlayPause = player::playOrPause,
                onOpenArchive = { navigate(WearRoute.Archive) },
            ),
        cards = { spec -> memoryCardItems(memoryCards, spec, openMoment) },
        footer = { spec -> watchSettingsItems(settingsState, settingsActions, spec) },
    )
    PhoneSettingsHandoffDialog(settingsState.handoff, settings::dismissHandoff)
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add `import app.logdate.wear.v2.settings.WatchSettingsViewModel` in ASCII order and, before the module's closing brace:

```kotlin
        viewModel {
            WatchSettingsViewModel(
                syncManager = get(),
                phoneReachability = get(),
                haptics = get(),
                locationSettings = get(),
                permissionManager = get(),
                phoneLinks = get(),
                protocolGate = get(),
                phoneHandoff = get(),
            )
        }
```

- [ ] **Step 8: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.*' :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WatchSettingsTest,app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.FlowPageTest
```

Expected: `BUILD SUCCESSFUL` twice; 4 + 9 unit tests, then 6 + 4 + 5 GMD tests pass. `ShellNavigationTest` proves the real Koin graph resolves `WatchSettingsViewModel`.

- [ ] **Step 9: Add the screenshot scenes, record and check them**

Create `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WatchSettingsScreenshots.kt`:

```kotlin
package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import app.logdate.wear.v2.haptics.HapticsPreference
import app.logdate.wear.v2.settings.WatchSettingsActions
import app.logdate.wear.v2.settings.WatchSettingsUiState
import app.logdate.wear.v2.settings.WatchStatus
import app.logdate.wear.v2.settings.watchSettingsItems
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest

/** Every state of the settings section, on the graphite theme. */
class WatchSettingsScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S01_SettingsHealthy() {
        SettingsScene(WatchSettingsUiState(places = true))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S02_SettingsWaitingForPhone() {
        SettingsScene(WatchSettingsUiState(status = WatchStatus.WaitingForPhone(3)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S03_SettingsSendFailing() {
        SettingsScene(WatchSettingsUiState(status = WatchStatus.SendFailing(2), haptics = HapticsPreference.ESSENTIAL))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S04_SettingsMoreOnPhone() {
        SettingsScene(WatchSettingsUiState(places = true, phoneSettingsNodeId = "phone-node"))
    }
}

@Composable
private fun SettingsScene(state: WatchSettingsUiState) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            val listState = rememberTransformingLazyColumnState()
            val spec = rememberTransformationSpec()
            ScreenScaffold(scrollState = listState, timeText = {}) { contentPadding ->
                TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                    watchSettingsItems(state, WatchSettingsActions(), spec)
                }
            }
        }
    }
}
```

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest || true
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/WatchSettingsScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: validation lists only the 12 new previews as missing; then `12` and only the new directory. Open `S02_SettingsWaitingForPhone_Small Round_*.png` with the Read tool: "3 moments waiting for your phone" over "They’re sent when your phone is nearby", then "Settings", the two haptics choices with All selected, and Places. Open `S03_…_Large Font_*.png`: the failing card and Essential selected, nothing clipped.

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b6-task7-commit.txt <<'EOF'
chore(wear): add settings and a sync status card to the watch list

The new watch app's moment list now ends with settings. A status card
appears only when something is wrong: moments waiting while the phone
is out of reach, or sends failing while it is in reach, with a tap to
try again. It changes as sync and the phone come and go.

Below it: All or Essential haptics; Places, which asks for location
before turning on; and More on your phone, which opens the phone's
Watch page once the phone offers that link.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add app/wear/src/main/res/values/strings_watch_settings.xml app/wear/src/main/kotlin/app/logdate/wear/v2/settings app/wear/src/main/kotlin/app/logdate/wear/v2/flow/FlowPage.kt app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/v2/settings app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchSettingsTest.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WatchSettingsScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/WatchSettingsScreenshots && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b6-task7-commit.txt
```

---

### Task 8: Post a reminder on the watch (`chore(reminders)`)

This task builds what a watch reminder needs before anything triggers one: a `MOMENT_REMINDERS` channel the watch registers when it first posts, a notifier whose Record action opens the app on the `record` route (Today until Task 14 makes that route start a recording), the reminders switch and the daily limit, and the shared rules for a workout reminder: within 30 minutes of the workout ending, inside the waking window (07:30 to 22:30, the fallback the reminder engine uses), and at most once a streak day. The watch also excludes the `logdate.reminder` tag from bridging, so a reminder the phone posts for itself never rings here too; nothing posts that tag until the phone's reminders ship, so excluding it now changes nothing visible. Task 9 adds the workout trigger and the switch in settings.

**Files:**
- Modify: `client/notifications/src/commonMain/kotlin/app/logdate/client/notifications/LogDateNotificationChannels.kt`
- Modify: `client/notifications/src/androidMain/kotlin/app/logdate/client/notifications/AndroidLogDateNotificationCatalog.kt`
- Modify: `client/notifications/src/androidMain/res/values/strings.xml` (append)
- Test: `client/notifications/src/commonTest/kotlin/app/logdate/client/notifications/LogDateNotificationChannelsTest.kt` (two tests)
- Create: `app/wear/src/main/kotlin/app/logdate/wear/launch/WearLaunchIntents.kt`
- Create: `app/wear/src/main/res/values/strings_reminders.xml`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminder.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchNudgeNotifier.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/ReminderBridging.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderPreferences.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderRules.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderDispatcher.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt` (one import, one call)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (imports, three bindings)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/launch/WearLaunchIntentsTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/ReminderFakes.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchReminderRulesTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchReminderDispatcherTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchReminderPreferencesTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/ReminderBridgingTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ReminderNotificationTest.kt`

**Interfaces:**
- Consumes: `LogDateNotificationRegistrar.registerChannel`, `WearLaunchRequest.EXTRA_ROUTE`/`EXTRA_NOTE_ID`/`parse`, `MainActivity`, `toStreakDay(timeZone)`; the recorder plan's handling of `WearLaunchRequest.Record` (checked in Task 0).
- Produces:
  - `LogDateNotificationChannelKey.MOMENT_REMINDERS` (id `moment_reminders`, notification id 4005), `LogDateNotificationChannels.watchChannels`, `AndroidLogDateNotificationCatalog.watchChannels`.
  - `object WearLaunchIntents { const val ROUTE_RECORD = "record"; const val ROUTE_MOMENT = "moment"; fun home(context): PendingIntent; fun record(context): PendingIntent; fun moment(context, noteId: Uuid): PendingIntent }` in `app.logdate.wear.launch`.
  - Package `app.logdate.wear.reminders`: `enum class WatchReminder(titleRes, bodyRes) { WORKOUT_ENDED }`; `fun interface NudgeNotifier { fun post(reminder: WatchReminder): Boolean }`; `class WatchNudgeNotifier(context) : NudgeNotifier` with `TAG = "logdate.reminder"` and `NOTIFICATION_ID`; `fun reminderBridgingConfig(context): BridgingConfig`; `fun applyReminderBridging(context)`; `interface WatchReminderPreferences { val enabled: StateFlow<Boolean>; fun setEnabled(Boolean); fun lastReminderAt(): Instant?; fun setLastReminderAt(Instant) }`; `class SharedPreferencesWatchReminderPreferences(preferences)` with `PREFERENCES_NAME = "wear_reminders"`; `object WatchReminderRules { WORKOUT_WINDOW; WAKING_START; WAKING_END; fun isWaking(now, timeZone); fun remindedToday(now, lastReminderAt, timeZone); fun workoutReminderDue(now, endedAt, lastReminderAt, timeZone) }`; `class WatchReminderDispatcher(preferences, notifier, clock = Clock.System, timeZone = …)` with `fun onWorkoutEnded(endedAt: Instant): Boolean`.

- [ ] **Step 1: Write the failing channel tests**

In `client/notifications/src/commonTest/kotlin/app/logdate/client/notifications/LogDateNotificationChannelsTest.kt`, add these tests after `legacy channel ids do not overlap active ids`:

```kotlin
    @Test
    fun `the watch registers its own reminders channel`() {
        assertEquals("moment_reminders", LogDateNotificationChannelKey.MOMENT_REMINDERS.id)
        assertEquals(listOf(LogDateNotificationChannelKey.MOMENT_REMINDERS), LogDateNotificationChannels.watchChannels)
    }

    @Test
    fun `notification ids remain unique across the phone and the watch`() {
        val notificationIds =
            (LogDateNotificationChannels.phoneChannels + LogDateNotificationChannels.watchChannels)
                .distinct()
                .mapNotNull { it.notificationId }

        assertEquals(notificationIds.size, notificationIds.distinct().size)
    }
```

- [ ] **Step 2: Run the channel tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:notifications:jvmTest
```

Expected: FAIL at `:client:notifications:compileTestKotlinJvm` with `Unresolved reference 'MOMENT_REMINDERS'` and `Unresolved reference 'watchChannels'`.

- [ ] **Step 3: Write the failing watch tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/launch/WearLaunchIntentsTest.kt`:

```kotlin
package app.logdate.wear.launch

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class WearLaunchIntentsTest {
    @Test
    fun `the routes in the intents open the recorder and a moment`() {
        val noteId = Uuid.random()

        assertEquals(WearLaunchRequest.Record, WearLaunchRequest.parse(WearLaunchIntents.ROUTE_RECORD))
        assertEquals(WearLaunchRequest.Moment(noteId), WearLaunchRequest.parse(WearLaunchIntents.ROUTE_MOMENT, noteId.toString()))
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/reminders/ReminderFakes.kt`:

```kotlin
package app.logdate.wear.reminders

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

internal val REMINDER_ZONE: TimeZone = TimeZone.of("Europe/Berlin")

internal fun berlin(
    day: Int,
    hour: Int,
    minute: Int = 0,
): Instant = LocalDateTime(2026, 10, day, hour, minute).toInstant(REMINDER_ZONE)

internal class FixedClock(
    var now: Instant,
) : Clock {
    override fun now(): Instant = now
}

internal class FakeNudgeNotifier(
    var accepts: Boolean = true,
) : NudgeNotifier {
    val posted = mutableListOf<WatchReminder>()

    override fun post(reminder: WatchReminder): Boolean {
        if (!accepts) return false
        posted += reminder
        return true
    }
}

internal class FakeReminderPreferences(
    enabled: Boolean = true,
    private var last: Instant? = null,
) : WatchReminderPreferences {
    override val enabled = MutableStateFlow(enabled)

    override fun setEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }

    override fun lastReminderAt(): Instant? = last

    override fun setLastReminderAt(at: Instant) {
        last = at
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchReminderRulesTest.kt`:

```kotlin
package app.logdate.wear.reminders

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchReminderRulesTest {
    @Test
    fun `a workout that ended within 30 minutes is due`() {
        assertTrue(WatchReminderRules.workoutReminderDue(now = berlin(10, 18), endedAt = berlin(10, 17, 40), lastReminderAt = null, timeZone = REMINDER_ZONE))
    }

    @Test
    fun `a workout that ended over 30 minutes ago is not`() {
        assertFalse(WatchReminderRules.workoutReminderDue(now = berlin(10, 18), endedAt = berlin(10, 17, 20), lastReminderAt = null, timeZone = REMINDER_ZONE))
    }

    @Test
    fun `a workout ending after now is not due`() {
        assertFalse(WatchReminderRules.workoutReminderDue(now = berlin(10, 18), endedAt = berlin(10, 18, 5), lastReminderAt = null, timeZone = REMINDER_ZONE))
    }

    @Test
    fun `reminders wait for waking hours`() {
        assertFalse(WatchReminderRules.isWaking(berlin(10, 7, 29), REMINDER_ZONE))
        assertTrue(WatchReminderRules.isWaking(berlin(10, 7, 30), REMINDER_ZONE))
        assertTrue(WatchReminderRules.isWaking(berlin(10, 22, 29), REMINDER_ZONE))
        assertFalse(WatchReminderRules.isWaking(berlin(10, 22, 30), REMINDER_ZONE))
    }

    @Test
    fun `one reminder a streak day`() {
        assertTrue(WatchReminderRules.remindedToday(now = berlin(10, 18), lastReminderAt = berlin(10, 9), timeZone = REMINDER_ZONE))
        assertFalse(WatchReminderRules.remindedToday(now = berlin(10, 18), lastReminderAt = berlin(9, 21), timeZone = REMINDER_ZONE))
        assertFalse(WatchReminderRules.remindedToday(now = berlin(10, 18), lastReminderAt = berlin(10, 2), timeZone = REMINDER_ZONE))
    }

    @Test
    fun `a reminder already today stops a workout reminder`() {
        assertFalse(
            WatchReminderRules.workoutReminderDue(now = berlin(10, 18), endedAt = berlin(10, 17, 50), lastReminderAt = berlin(10, 9), timeZone = REMINDER_ZONE),
        )
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchReminderDispatcherTest.kt`:

```kotlin
package app.logdate.wear.reminders

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchReminderDispatcherTest {
    private val clock = FixedClock(berlin(10, 18))
    private val notifier = FakeNudgeNotifier()

    @Test
    fun `reminders off means no reminder`() {
        val preferences = FakeReminderPreferences(enabled = false)

        assertFalse(dispatcher(preferences).onWorkoutEnded(berlin(10, 17, 50)))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a due workout reminder is posted and its time saved`() {
        val preferences = FakeReminderPreferences()

        assertTrue(dispatcher(preferences).onWorkoutEnded(berlin(10, 17, 50)))

        assertEquals(listOf(WatchReminder.WORKOUT_ENDED), notifier.posted)
        assertEquals(clock.now, preferences.lastReminderAt())
    }

    @Test
    fun `a reminder the watch could not show is not counted`() {
        notifier.accepts = false
        val preferences = FakeReminderPreferences()

        assertFalse(dispatcher(preferences).onWorkoutEnded(berlin(10, 17, 50)))

        assertNull(preferences.lastReminderAt())
    }

    @Test
    fun `a second workout the same day is not reminded`() {
        val preferences = FakeReminderPreferences()
        val dispatcher = dispatcher(preferences)
        dispatcher.onWorkoutEnded(berlin(10, 17, 50))
        clock.now = berlin(10, 20)

        assertFalse(dispatcher.onWorkoutEnded(berlin(10, 19, 55)))
        assertEquals(1, notifier.posted.size)
    }

    private fun dispatcher(preferences: WatchReminderPreferences) =
        WatchReminderDispatcher(preferences = preferences, notifier = notifier, clock = clock, timeZone = { REMINDER_ZONE })
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchReminderPreferencesTest.kt`:

```kotlin
package app.logdate.wear.reminders

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class WatchReminderPreferencesTest {
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val preferences =
        mockk<SharedPreferences> {
            every { edit() } returns editor
            every { getBoolean("enabled", false) } returns false
            every { getLong("last_reminder_at", -1L) } returns -1L
        }

    @Test
    fun `reminders start off with none sent`() {
        val store = SharedPreferencesWatchReminderPreferences(preferences)

        assertEquals(false, store.enabled.value)
        assertNull(store.lastReminderAt())
    }

    @Test
    fun `turning reminders on saves it and reports it at once`() {
        val store = SharedPreferencesWatchReminderPreferences(preferences)

        store.setEnabled(true)

        verify { editor.putBoolean("enabled", true) }
        assertEquals(true, store.enabled.value)
    }

    @Test
    fun `the last reminder time is saved and read back`() {
        every { preferences.getLong("last_reminder_at", -1L) } returns 1_791_000_000_000L
        val store = SharedPreferencesWatchReminderPreferences(preferences)

        store.setLastReminderAt(Instant.fromEpochMilliseconds(1_792_000_000_000L))

        verify { editor.putLong("last_reminder_at", 1_792_000_000_000L) }
        assertEquals(Instant.fromEpochMilliseconds(1_791_000_000_000L), SharedPreferencesWatchReminderPreferences(preferences).lastReminderAt())
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/reminders/ReminderBridgingTest.kt`:

```kotlin
package app.logdate.wear.reminders

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderBridgingTest {
    @Test
    fun `phone notifications still bridge except the phone's own reminders`() {
        val context = mockk<Context> { every { packageName } returns "studio.hypertext.logdate" }

        val config = reminderBridgingConfig(context)

        assertTrue(config.isBridgingEnabled)
        assertEquals(setOf("logdate.reminder"), config.excludedTags)
    }
}
```

- [ ] **Step 4: Run the watch tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.reminders.*' --tests 'app.logdate.wear.launch.WearLaunchIntentsTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'WearLaunchIntents'`, `Unresolved reference 'NudgeNotifier'`, `Unresolved reference 'WatchReminderRules'` and `Unresolved reference 'reminderBridgingConfig'`.

- [ ] **Step 5: Write the failing GMD test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ReminderNotificationTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.reminders.WatchNudgeNotifier
import app.logdate.wear.reminders.WatchReminder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Posts a real reminder on a Wear OS emulator and follows its Record action into the app. */
@RunWith(AndroidJUnit4::class)
class ReminderNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun grantNotifications() {
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun clear() {
        notificationManager.cancel(WatchNudgeNotifier.TAG, WatchNudgeNotifier.NOTIFICATION_ID)
    }

    @Test
    fun a_reminder_appears_on_the_reminders_channel_with_record() {
        assertTrue(WatchNudgeNotifier(context).post(WatchReminder.WORKOUT_ENDED))

        val notification = posted().notification
        assertEquals(LogDateNotificationChannelKey.MOMENT_REMINDERS.id, notification.channelId)
        assertEquals("Workout finished", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Record", notification.actions.single().title.toString())
    }

    @Test
    fun record_opens_the_app_on_the_recorder_route() {
        WatchNudgeNotifier(context).post(WatchReminder.WORKOUT_ENDED)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)

        posted().notification.actions.single().actionIntent.send()
        val activity = instrumentation.waitForMonitorWithTimeout(monitor, TIMEOUT_MS)

        assertNotNull(activity)
        assertEquals("record", activity.intent.getStringExtra(WearLaunchRequest.EXTRA_ROUTE))
        activity.finish()
        instrumentation.removeMonitor(monitor)
    }

    private fun posted(): StatusBarNotification =
        notificationManager.activeNotifications.single { it.tag == WatchNudgeNotifier.TAG && it.id == WatchNudgeNotifier.NOTIFICATION_ID }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
```

- [ ] **Step 6: Run the GMD test to verify it fails**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ReminderNotificationTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'WatchNudgeNotifier'`.

- [ ] **Step 7: Add the reminders channel**

In `client/notifications/src/commonMain/kotlin/app/logdate/client/notifications/LogDateNotificationChannels.kt`, add this constant after `CLOUD_SYNC(…),`:

```kotlin
    MOMENT_REMINDERS(
        id = "moment_reminders",
        groupKey = LogDateNotificationGroupKey.AMBIENT_INTELLIGENCE,
        notificationId = 4005,
    ),
```

and replace

```kotlin
    val legacyChannelIds: Set<String> =
        phoneChannels
            .flatMap { it.legacyChannelIds }
            .toSet()
```

with

```kotlin
    /** Channels only the watch app registers, when it first posts on them. */
    val watchChannels: List<LogDateNotificationChannelKey> =
        listOf(
            LogDateNotificationChannelKey.MOMENT_REMINDERS,
        )

    val legacyChannelIds: Set<String> =
        (phoneChannels + watchChannels)
            .flatMap { it.legacyChannelIds }
            .toSet()
```

In `client/notifications/src/androidMain/kotlin/app/logdate/client/notifications/AndroidLogDateNotificationCatalog.kt`, replace

```kotlin
    fun channel(key: LogDateNotificationChannelKey): AndroidLogDateNotificationChannelSpec = phoneChannels.first { it.key == key }
```

with

```kotlin
    /** Channels only the watch app registers, when it first posts on them. */
    val watchChannels: List<AndroidLogDateNotificationChannelSpec> =
        listOf(
            AndroidLogDateNotificationChannelSpec(
                key = LogDateNotificationChannelKey.MOMENT_REMINDERS,
                nameResId = R.string.notification_channel_moment_reminders_name,
                descriptionResId = R.string.notification_channel_moment_reminders_description,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                showBadge = false,
                vibrationEnabled = true,
                soundEnabled = false,
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE,
            ),
        )

    fun channel(key: LogDateNotificationChannelKey): AndroidLogDateNotificationChannelSpec = (phoneChannels + watchChannels).first { it.key == key }
```

In `client/notifications/src/androidMain/res/values/strings.xml`, add before `</resources>`:

```xml
    <string name="notification_channel_moment_reminders_name">Reminders</string>
    <string name="notification_channel_moment_reminders_description">A reminder to record a moment, at most once a day.</string>
```

- [ ] **Step 8: Add the launch intents, the reminder and the notifier**

Create `app/wear/src/main/kotlin/app/logdate/wear/launch/WearLaunchIntents.kt`:

```kotlin
package app.logdate.wear.launch

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.logdate.wear.presentation.MainActivity
import kotlin.uuid.Uuid

/** Pending intents that open the watch app on a route, for notifications and complications. */
object WearLaunchIntents {
    const val ROUTE_RECORD = "record"
    const val ROUTE_MOMENT = "moment"
    private const val REQUEST_HOME = 3001
    private const val REQUEST_RECORD = 3002

    fun home(context: Context): PendingIntent = activity(context, REQUEST_HOME, openApp(context))

    fun record(context: Context): PendingIntent =
        activity(context, REQUEST_RECORD, openApp(context).putExtra(WearLaunchRequest.EXTRA_ROUTE, ROUTE_RECORD))

    /** Opens the moment [noteId]; each moment gets its own pending intent. */
    fun moment(
        context: Context,
        noteId: Uuid,
    ): PendingIntent =
        activity(
            context,
            noteId.hashCode(),
            openApp(context)
                .putExtra(WearLaunchRequest.EXTRA_ROUTE, ROUTE_MOMENT)
                .putExtra(WearLaunchRequest.EXTRA_NOTE_ID, noteId.toString()),
        )

    private fun openApp(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun activity(
        context: Context,
        requestCode: Int,
        intent: Intent,
    ): PendingIntent = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
```

Create `app/wear/src/main/res/values/strings_reminders.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Reminders on the watch, in the new watch app -->
    <string name="wear_reminder_record">Record</string>
    <string name="wear_reminder_workout_title">Workout finished</string>
    <string name="wear_reminder_workout_body">Record how it went</string>
</resources>
```

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminder.kt`:

```kotlin
package app.logdate.wear.reminders

import androidx.annotation.StringRes
import app.logdate.wear.R

/** The reminders the watch can post, and what each says. */
enum class WatchReminder(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
) {
    WORKOUT_ENDED(R.string.wear_reminder_workout_title, R.string.wear_reminder_workout_body),
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchNudgeNotifier.kt`:

```kotlin
package app.logdate.wear.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.wear.R
import app.logdate.wear.launch.WearLaunchIntents
import io.github.aakira.napier.Napier

/** Shows a reminder; false when it could not be shown. */
fun interface NudgeNotifier {
    fun post(reminder: WatchReminder): Boolean
}

/**
 * Posts reminders on the watch's own [CHANNEL], with a Record action that opens the app to record. There is
 * one reminder at a time: a new one replaces the last.
 */
class WatchNudgeNotifier(
    private val context: Context,
) : NudgeNotifier {
    override fun post(reminder: WatchReminder): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Napier.w("Notifications are off for LogDate on this watch, so the ${reminder.name} reminder was not shown")
            return false
        }
        LogDateNotificationRegistrar(context).registerChannel(CHANNEL)
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL.id)
                .setSmallIcon(R.drawable.ic_glyph_record)
                .setContentTitle(context.getString(reminder.titleRes))
                .setContentText(context.getString(reminder.bodyRes))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(WearLaunchIntents.home(context))
                .addAction(R.drawable.ic_glyph_record, context.getString(R.string.wear_reminder_record), WearLaunchIntents.record(context))
                .build()
        NotificationManagerCompat.from(context).notify(TAG, NOTIFICATION_ID, notification)
        return true
    }

    companion object {
        /** The tag reminders use on both devices; the watch excludes the phone's from bridging. */
        const val TAG = "logdate.reminder"
        val CHANNEL = LogDateNotificationChannelKey.MOMENT_REMINDERS
        val NOTIFICATION_ID: Int = requireNotNull(CHANNEL.notificationId)
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/ReminderBridging.kt`:

```kotlin
package app.logdate.wear.reminders

import android.content.Context
import androidx.wear.phone.interactions.notifications.BridgingConfig
import androidx.wear.phone.interactions.notifications.BridgingManager
import io.github.aakira.napier.Napier

/** Phone notifications still appear on the watch, except a reminder the phone posts for itself. */
fun reminderBridgingConfig(context: Context): BridgingConfig =
    BridgingConfig
        .Builder(context, true)
        .addExcludedTag(WatchNudgeNotifier.TAG)
        .build()

/** Applies [reminderBridgingConfig]. A failure is logged and leaves bridging as it was. */
fun applyReminderBridging(context: Context) {
    try {
        BridgingManager.fromContext(context).setConfig(reminderBridgingConfig(context))
    } catch (e: Exception) {
        Napier.e("Couldn't stop the phone's reminders from also showing on this watch", e)
    }
}
```

- [ ] **Step 9: Add the switch, the rules and the dispatcher**

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderPreferences.kt`:

```kotlin
package app.logdate.wear.reminders

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/** Whether reminders are on for this watch, and when it last sent one. */
interface WatchReminderPreferences {
    val enabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean)

    fun lastReminderAt(): Instant?

    fun setLastReminderAt(at: Instant)
}

/** [WatchReminderPreferences] in the watch's own SharedPreferences file, [PREFERENCES_NAME]. Reminders start off. */
class SharedPreferencesWatchReminderPreferences(
    private val preferences: SharedPreferences,
) : WatchReminderPreferences {
    private val current = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))

    override val enabled: StateFlow<Boolean> = current.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
        current.value = enabled
    }

    override fun lastReminderAt(): Instant? =
        preferences.getLong(KEY_LAST_REMINDER_AT, NONE).takeIf { it != NONE }?.let(Instant::fromEpochMilliseconds)

    override fun setLastReminderAt(at: Instant) {
        preferences.edit { putLong(KEY_LAST_REMINDER_AT, at.toEpochMilliseconds()) }
    }

    companion object {
        const val PREFERENCES_NAME = "wear_reminders"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_REMINDER_AT = "last_reminder_at"
        private const val NONE = -1L
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderRules.kt`:

```kotlin
package app.logdate.wear.reminders

import app.logdate.client.domain.streak.toStreakDay
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * When the watch may remind: inside the waking window, at most once a streak day, and for a workout,
 * within [WORKOUT_WINDOW] of its end. The waking window is the fallback the reminder engine uses
 * until it learns the person's own hours.
 */
object WatchReminderRules {
    val WORKOUT_WINDOW: Duration = 30.minutes
    val WAKING_START: LocalTime = LocalTime(7, 30)
    val WAKING_END: LocalTime = LocalTime(22, 30)

    fun isWaking(
        now: Instant,
        timeZone: TimeZone,
    ): Boolean {
        val time = now.toLocalDateTime(timeZone).time
        return time >= WAKING_START && time < WAKING_END
    }

    fun remindedToday(
        now: Instant,
        lastReminderAt: Instant?,
        timeZone: TimeZone,
    ): Boolean = lastReminderAt != null && lastReminderAt.toStreakDay(timeZone) == now.toStreakDay(timeZone)

    fun workoutReminderDue(
        now: Instant,
        endedAt: Instant,
        lastReminderAt: Instant?,
        timeZone: TimeZone,
    ): Boolean =
        endedAt <= now &&
            now - endedAt <= WORKOUT_WINDOW &&
            isWaking(now, timeZone) &&
            !remindedToday(now, lastReminderAt, timeZone)
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderDispatcher.kt`:

```kotlin
package app.logdate.wear.reminders

import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/** Decides whether a trigger becomes a reminder, posts it, and counts it toward the daily limit. */
class WatchReminderDispatcher(
    private val preferences: WatchReminderPreferences,
    private val notifier: NudgeNotifier,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    /** Reminds after a workout that ended at [endedAt]. @return true when a reminder was shown. */
    fun onWorkoutEnded(endedAt: Instant): Boolean {
        if (!preferences.enabled.value) return false
        val now = clock.now()
        if (!WatchReminderRules.workoutReminderDue(now, endedAt, preferences.lastReminderAt(), timeZone())) return false
        return post(WatchReminder.WORKOUT_ENDED, now)
    }

    private fun post(
        reminder: WatchReminder,
        now: Instant,
    ): Boolean {
        if (!notifier.post(reminder)) return false
        preferences.setLastReminderAt(now)
        return true
    }
}
```

- [ ] **Step 10: Apply bridging at start and bind the reminder parts**

In `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`, add `import app.logdate.wear.reminders.applyReminderBridging` in ASCII order, and add `        applyReminderBridging(this)` on its own line directly after the `runCatching { LegacyPromptAlarms.cancel(this) }…` statement.

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add these imports in ASCII order: `app.logdate.wear.reminders.NudgeNotifier`, `app.logdate.wear.reminders.SharedPreferencesWatchReminderPreferences`, `app.logdate.wear.reminders.WatchNudgeNotifier`, `app.logdate.wear.reminders.WatchReminderDispatcher`, `app.logdate.wear.reminders.WatchReminderPreferences`. Add before the module's closing brace:

```kotlin
        single<WatchReminderPreferences> {
            SharedPreferencesWatchReminderPreferences(
                get<Context>().getSharedPreferences(SharedPreferencesWatchReminderPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single<NudgeNotifier> { WatchNudgeNotifier(get()) }
        single { WatchReminderDispatcher(preferences = get(), notifier = get()) }
```

- [ ] **Step 11: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:notifications:jvmTest :client:notifications:testAndroidHostTest :client:notifications:compileKotlinIosSimulatorArm64 :client:notifications:ktlintCheck :app:wear:ktlintFormat :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug :app:android-main:assembleDebug
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ReminderNotificationTest,app.logdate.wear.e2e.ShellNavigationTest
```

Expected: `BUILD SUCCESSFUL` twice. The phone still builds and registers exactly the channels it did (`phoneChannels` is unchanged). Both reminder GMD tests pass; the second opens the app on the `record` route.

- [ ] **Step 12: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b7-task8-commit.txt <<'EOF'
chore(reminders): post reminders on the watch with a Record action

The watch can now post a reminder on its own Reminders channel, with
a Record action that opens the app to record. The rules for a workout
reminder are in place: within 30 minutes of the workout ending,
between 7:30 and 22:30, and at most once a day, counted from the
streak day's 4 AM start. Reminders are off until turned on.

The watch also stops phone notifications tagged logdate.reminder from
bridging, so a reminder the phone posts for itself never rings on both
devices. Nothing triggers a watch reminder yet, and nothing posts that
tag yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 13: Commit**

```bash
git restore --staged . && git add client/notifications/src/commonMain/kotlin/app/logdate/client/notifications/LogDateNotificationChannels.kt client/notifications/src/androidMain/kotlin/app/logdate/client/notifications/AndroidLogDateNotificationCatalog.kt client/notifications/src/androidMain/res/values/strings.xml client/notifications/src/commonTest/kotlin/app/logdate/client/notifications/LogDateNotificationChannelsTest.kt app/wear/src/main/kotlin/app/logdate/wear/launch/WearLaunchIntents.kt app/wear/src/main/res/values/strings_reminders.xml app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminder.kt app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchNudgeNotifier.kt app/wear/src/main/kotlin/app/logdate/wear/reminders/ReminderBridging.kt app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderPreferences.kt app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderRules.kt app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderDispatcher.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/launch/WearLaunchIntentsTest.kt app/wear/src/test/kotlin/app/logdate/wear/reminders app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/ReminderNotificationTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b7-task8-commit.txt
```

---

### Task 9: Remind after a workout (`chore(reminders)`)

Reminders get their switch in the settings section, and their first trigger: a workout ending. Turning reminders on asks for notifications and physical activity, then registers a Health Services `PassiveListenerService` for user activity; a change from exercise to passive within 30 minutes, in waking hours, with no reminder yet today, posts the workout reminder. Health Services forgets the registration at a restart (and possibly at an update), so a boot receiver hands re-registration to WorkManager, as the Health Services guide asks, and the app re-registers at each start while reminders are on. This restores `ACTIVITY_RECOGNITION` and `RECEIVE_BOOT_COMPLETED`, which changes what Play lists for the watch (see the Self-Review); reading phone-side workouts from Health Connect needs a Play Console declaration on the phone and is not part of this plan.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/ReminderPermissions.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/ActivityRegistration.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WorkoutEndListenerService.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/ReminderRestore.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminders.kt`
- Modify: `app/wear/src/main/AndroidManifest.xml` (permissions comment and lines, one service, one receiver)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncScheduler.kt:34-35` (KDoc)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt` (restore at start)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModel.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsItems.kt` (tag, action, item, composable)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsEntry.kt` (permission request)
- Modify: `app/wear/src/main/res/values/strings_watch_settings.xml` (append)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (imports, five bindings, settings binding)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/ReminderFakes.kt` (append)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/WorkoutWatcherTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchRemindersTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModelTest.kt` (helper and three tests)
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearReminderManifestTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchSettingsTest.kt` (one test)
- Screenshot: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WatchSettingsScreenshots.kt` (one scene; 3 new images)

**Interfaces:**
- Consumes (Task 8): `WatchReminderPreferences`, `WatchReminderDispatcher.onWorkoutEnded`, fakes `FixedClock`, `FakeNudgeNotifier`, `FakeReminderPreferences`, `berlin`, `REMINDER_ZONE`. (Task 7): the settings section.
- Produces (package `app.logdate.wear.reminders`): `interface ReminderPermissions { fun notificationsGranted(): Boolean; fun activityRecognitionGranted(): Boolean }`, `class AndroidReminderPermissions(context)`, `val REMINDER_PERMISSIONS: Array<String>`; `interface ActivityRegistration { suspend fun register(): Boolean; suspend fun unregister() }`, `class HealthServicesActivityRegistration(context)`; `enum class ActivityKind { EXERCISE, PASSIVE, ASLEEP, UNKNOWN }`, `fun UserActivityState.toActivityKind(): ActivityKind`, `fun workoutEndedAt(previous: ActivityKind?, current: ActivityKind, changedAt: Instant): Instant?`, `interface ActivityTransitionStore { fun last(): ActivityKind?; fun record(kind: ActivityKind) }`, `class SharedPreferencesActivityTransitionStore(preferences)`, `class WorkoutWatcher(transitions, dispatcher)` with `fun onActivity(kind: ActivityKind, changedAt: Instant): Boolean`, `class WorkoutEndListenerService : PassiveListenerService`; `class ReminderRestoreReceiver`, `class ReminderRestoreWorker`; `class WatchReminders(preferences, activity, permissions)` with `val enabled: StateFlow<Boolean>`, `suspend fun setEnabled(Boolean)`, `suspend fun restore()`. Settings: `data class RemindersSetting(enabled: Boolean = false, afterWorkouts: Boolean = false)`, `WatchSettingsUiState.reminders`, `WatchSettingsViewModel(…, reminders: WatchReminders, reminderPermissions: ReminderPermissions)` with `remindersNeedPermissions()`, `setReminders(Boolean)`, `onReminderPermissionsResult()`; `WatchSettingsActions.onReminders`; `WatchSettingsTags.REMINDERS`.

- [ ] **Step 1: Write the failing workout and switch tests**

Append to `app/wear/src/test/kotlin/app/logdate/wear/reminders/ReminderFakes.kt`:

```kotlin

internal class FakeActivityRegistration(
    var supported: Boolean = true,
) : ActivityRegistration {
    var registrations = 0
    var unregistrations = 0

    override suspend fun register(): Boolean {
        if (!supported) return false
        registrations++
        return true
    }

    override suspend fun unregister() {
        unregistrations++
    }
}

internal class FakeReminderPermissions(
    var notifications: Boolean = true,
    var activityRecognition: Boolean = true,
) : ReminderPermissions {
    override fun notificationsGranted(): Boolean = notifications

    override fun activityRecognitionGranted(): Boolean = activityRecognition
}

internal class FakeTransitions : ActivityTransitionStore {
    var lastKind: ActivityKind? = null

    override fun last(): ActivityKind? = lastKind

    override fun record(kind: ActivityKind) {
        lastKind = kind
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/reminders/WorkoutWatcherTest.kt`:

```kotlin
package app.logdate.wear.reminders

import androidx.health.services.client.data.UserActivityState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutWatcherTest {
    private val notifier = FakeNudgeNotifier()
    private val transitions = FakeTransitions()
    private val dispatcher =
        WatchReminderDispatcher(
            preferences = FakeReminderPreferences(),
            notifier = notifier,
            clock = FixedClock(berlin(10, 18)),
            timeZone = { REMINDER_ZONE },
        )
    private val watcher = WorkoutWatcher(transitions, dispatcher)

    @Test
    fun `exercise then passive ends a workout at the change`() {
        assertEquals(berlin(10, 17, 50), workoutEndedAt(ActivityKind.EXERCISE, ActivityKind.PASSIVE, berlin(10, 17, 50)))
    }

    @Test
    fun `other changes end no workout`() {
        assertNull(workoutEndedAt(ActivityKind.PASSIVE, ActivityKind.EXERCISE, berlin(10, 17)))
        assertNull(workoutEndedAt(null, ActivityKind.PASSIVE, berlin(10, 17)))
        assertNull(workoutEndedAt(ActivityKind.EXERCISE, ActivityKind.ASLEEP, berlin(10, 17)))
    }

    @Test
    fun `the end of a workout reminds`() {
        assertFalse(watcher.onActivity(ActivityKind.EXERCISE, berlin(10, 17)))
        assertTrue(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))

        assertEquals(listOf(WatchReminder.WORKOUT_ENDED), notifier.posted)
    }

    @Test
    fun `an unknown reading between exercise and passive does not hide the end`() {
        watcher.onActivity(ActivityKind.EXERCISE, berlin(10, 17))
        watcher.onActivity(ActivityKind.UNKNOWN, berlin(10, 17, 30))

        assertTrue(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))
    }

    @Test
    fun `passive without exercise before it reminds of nothing`() {
        assertFalse(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `health services states map to kinds`() {
        assertEquals(ActivityKind.EXERCISE, UserActivityState.USER_ACTIVITY_EXERCISE.toActivityKind())
        assertEquals(ActivityKind.PASSIVE, UserActivityState.USER_ACTIVITY_PASSIVE.toActivityKind())
        assertEquals(ActivityKind.ASLEEP, UserActivityState.USER_ACTIVITY_ASLEEP.toActivityKind())
        assertEquals(ActivityKind.UNKNOWN, UserActivityState.USER_ACTIVITY_UNKNOWN.toActivityKind())
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchRemindersTest.kt`:

```kotlin
package app.logdate.wear.reminders

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchRemindersTest {
    private val preferences = FakeReminderPreferences(enabled = false)
    private val activity = FakeActivityRegistration()
    private val permissions = FakeReminderPermissions()
    private val reminders = WatchReminders(preferences = preferences, activity = activity, permissions = permissions)

    @Test
    fun `turning reminders on listens for workouts`() =
        runTest {
            reminders.setEnabled(true)

            assertTrue(preferences.enabled.value)
            assertEquals(1, activity.registrations)
        }

    @Test
    fun `turning reminders off stops listening`() =
        runTest {
            reminders.setEnabled(true)

            reminders.setEnabled(false)

            assertFalse(preferences.enabled.value)
            assertEquals(1, activity.unregistrations)
        }

    @Test
    fun `without physical activity access nothing listens for workouts`() =
        runTest {
            permissions.activityRecognition = false

            reminders.setEnabled(true)

            assertEquals(0, activity.registrations)
        }

    @Test
    fun `a restart listens again only while reminders are on`() =
        runTest {
            reminders.restore()
            assertEquals(0, activity.registrations)
            preferences.setEnabled(true)

            reminders.restore()

            assertEquals(1, activity.registrations)
        }
}
```

In `app/wear/src/test/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModelTest.kt`, add these imports in ASCII order: `app.logdate.wear.reminders.FakeActivityRegistration`, `app.logdate.wear.reminders.FakeReminderPermissions`, `app.logdate.wear.reminders.FakeReminderPreferences`, `app.logdate.wear.reminders.WatchReminders`. Add three fields after `private val phoneHandoff = mockk<PhoneHandoff>()`:

```kotlin
    private val reminderPreferences = FakeReminderPreferences(enabled = false)
    private val activityRegistration = FakeActivityRegistration()
    private val reminderPermissions = FakeReminderPermissions()
```

In `viewModel()`, add these two arguments after `phoneHandoff = phoneHandoff,`:

```kotlin
            reminders = WatchReminders(reminderPreferences, activityRegistration, reminderPermissions),
            reminderPermissions = reminderPermissions,
```

Add these tests before `private fun viewModel()`:

```kotlin
    @Test
    fun `reminders show their switch and what they cover`() =
        runTest {
            reminderPreferences.setEnabled(true)

            viewModel().uiState.test {
                assertEquals(RemindersSetting(enabled = true, afterWorkouts = true), expectMostRecentItem().reminders)
            }
        }

    @Test
    fun `turning reminders on asks for permissions first when they are missing`() =
        runTest {
            reminderPermissions.notifications = false
            reminderPermissions.activityRecognition = false
            val viewModel = viewModel()

            assertTrue(viewModel.remindersNeedPermissions())
            reminderPermissions.notifications = true
            reminderPermissions.activityRecognition = true
            viewModel.onReminderPermissionsResult()

            assertEquals(true, reminderPreferences.enabled.value)
            assertEquals(1, activityRegistration.registrations)
        }

    @Test
    fun `refused permissions leave reminders off`() =
        runTest {
            reminderPermissions.notifications = false
            val viewModel = viewModel()

            viewModel.onReminderPermissionsResult()

            assertEquals(false, reminderPreferences.enabled.value)
        }

    @Test
    fun `turning reminders off saves it and stops listening`() =
        runTest {
            reminderPreferences.setEnabled(true)

            viewModel().setReminders(false)

            assertEquals(false, reminderPreferences.enabled.value)
            assertEquals(1, activityRegistration.unregistrations)
        }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.reminders.*' --tests 'app.logdate.wear.v2.settings.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'ActivityRegistration'`, `Unresolved reference 'WorkoutWatcher'`, `Unresolved reference 'WatchReminders'` and `Unresolved reference 'RemindersSetting'`.

- [ ] **Step 3: Write the failing GMD tests**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearReminderManifestTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.wear.reminders.WorkoutEndListenerService
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Checks the installed manifest has what watch reminders need, and still lacks background location. */
@RunWith(AndroidJUnit4::class)
class WearReminderManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun reminders_can_ask_for_physical_activity_and_restore_after_a_restart() {
        val requested = requestedPermissions()

        assertTrue(Manifest.permission.ACTIVITY_RECOGNITION in requested)
        assertTrue(Manifest.permission.RECEIVE_BOOT_COMPLETED in requested)
        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in requested)
    }

    @Test
    fun a_restart_reaches_the_reminders_receiver() {
        val receivers =
            context.packageManager.queryBroadcastReceivers(
                Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName),
                PackageManager.ResolveInfoFlags.of(0L),
            )

        assertTrue(receivers.any { it.activityInfo.name == RESTORE_RECEIVER }, receivers.map { it.activityInfo.name }.toString())
    }

    @Test
    fun only_health_services_can_bind_the_workout_listener() {
        val service =
            context.packageManager.getServiceInfo(
                ComponentName(context, WorkoutEndListenerService::class.java),
                PackageManager.ComponentInfoFlags.of(0L),
            )

        assertEquals(PASSIVE_DATA_BINDING, service.permission)
    }

    private fun requestedPermissions(): List<String> =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            .requestedPermissions
            .orEmpty()
            .toList()

    private companion object {
        const val RESTORE_RECEIVER = "app.logdate.wear.reminders.ReminderRestoreReceiver"
        const val PASSIVE_DATA_BINDING = "com.google.android.wearable.healthservices.permission.PASSIVE_DATA_BINDING"
    }
}
```

In `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchSettingsTest.kt`, add this test after `the_places_switch_reports_the_new_value`:

```kotlin
    @Test
    fun the_reminders_switch_reports_the_new_value() {
        val values = mutableListOf<Boolean>()
        show(WatchSettingsUiState(), WatchSettingsActions(onReminders = { values += it }))

        clickWhenShown(WatchSettingsTags.REMINDERS)

        assertEquals(listOf(true), values)
    }
```

- [ ] **Step 4: Run the GMD tests to verify they fail**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearReminderManifestTest,app.logdate.wear.e2e.WatchSettingsTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'WorkoutEndListenerService'` and `No parameter with name 'onReminders' found`.

- [ ] **Step 5: Add permissions, registration and the workout listener**

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/ReminderPermissions.kt`:

```kotlin
package app.logdate.wear.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** What turning reminders on asks for: notifications to show them, physical activity to see a workout end. */
val REMINDER_PERMISSIONS: Array<String> = arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACTIVITY_RECOGNITION)

/** Whether the permissions reminders use are granted. */
interface ReminderPermissions {
    fun notificationsGranted(): Boolean

    fun activityRecognitionGranted(): Boolean
}

class AndroidReminderPermissions(
    private val context: Context,
) : ReminderPermissions {
    override fun notificationsGranted(): Boolean = granted(Manifest.permission.POST_NOTIFICATIONS)

    override fun activityRecognitionGranted(): Boolean = granted(Manifest.permission.ACTIVITY_RECOGNITION)

    private fun granted(permission: String): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/ActivityRegistration.kt`:

```kotlin
package app.logdate.wear.reminders

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.clearPassiveListenerService
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.UserActivityState
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.setPassiveListenerService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** Listening for the person's activity changing, which is how the watch sees a workout end. */
interface ActivityRegistration {
    /** @return false when this watch does not report workouts, or registering failed. */
    suspend fun register(): Boolean

    suspend fun unregister()
}

/** [ActivityRegistration] through Health Services, delivering to [WorkoutEndListenerService]. */
class HealthServicesActivityRegistration(
    private val context: Context,
) : ActivityRegistration {
    private val client by lazy { HealthServices.getClient(context).passiveMonitoringClient }

    override suspend fun register(): Boolean {
        try {
            if (UserActivityState.USER_ACTIVITY_EXERCISE !in client.getCapabilities().supportedUserActivityStates) {
                Napier.i("This watch does not report workouts to apps, so reminders after workouts are off")
                return false
            }
            val config = PassiveListenerConfig.builder().setShouldUserActivityInfoBeRequested(true).build()
            client.setPassiveListenerService(WorkoutEndListenerService::class.java, config)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't listen for workouts ending", e)
            return false
        }
    }

    override suspend fun unregister() {
        try {
            client.clearPassiveListenerService()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't stop listening for workouts ending", e)
        }
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/WorkoutEndListenerService.kt`:

```kotlin
package app.logdate.wear.reminders

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Instant

/** The person's activity as reminders read it. */
enum class ActivityKind {
    EXERCISE,
    PASSIVE,
    ASLEEP,
    UNKNOWN,
}

fun UserActivityState.toActivityKind(): ActivityKind =
    when (this) {
        UserActivityState.USER_ACTIVITY_EXERCISE -> ActivityKind.EXERCISE
        UserActivityState.USER_ACTIVITY_PASSIVE -> ActivityKind.PASSIVE
        UserActivityState.USER_ACTIVITY_ASLEEP -> ActivityKind.ASLEEP
        else -> ActivityKind.UNKNOWN
    }

/** When a workout ended: the change from exercise to passive, at [changedAt]. Null for any other change. */
fun workoutEndedAt(
    previous: ActivityKind?,
    current: ActivityKind,
    changedAt: Instant,
): Instant? = changedAt.takeIf { previous == ActivityKind.EXERCISE && current == ActivityKind.PASSIVE }

/** The last activity Health Services reported, across the listener's short lives. */
interface ActivityTransitionStore {
    fun last(): ActivityKind?

    fun record(kind: ActivityKind)
}

/** [ActivityTransitionStore] in the watch's own SharedPreferences file, [PREFERENCES_NAME]. */
class SharedPreferencesActivityTransitionStore(
    private val preferences: SharedPreferences,
) : ActivityTransitionStore {
    override fun last(): ActivityKind? = preferences.getString(KEY_LAST, null)?.let { name -> ActivityKind.entries.firstOrNull { it.name == name } }

    override fun record(kind: ActivityKind) {
        preferences.edit { putString(KEY_LAST, kind.name) }
    }

    companion object {
        const val PREFERENCES_NAME = "wear_activity"
        private const val KEY_LAST = "last_activity"
    }
}

/**
 * Turns activity changes into workout reminders. An unknown reading is skipped, so it never hides the
 * end of a workout that it falls in the middle of.
 */
class WorkoutWatcher(
    private val transitions: ActivityTransitionStore,
    private val dispatcher: WatchReminderDispatcher,
) {
    /** @return true when [kind] ended a workout and a reminder was shown. */
    fun onActivity(
        kind: ActivityKind,
        changedAt: Instant,
    ): Boolean {
        if (kind == ActivityKind.UNKNOWN) return false
        val endedAt = workoutEndedAt(transitions.last(), kind, changedAt)
        transitions.record(kind)
        return endedAt != null && dispatcher.onWorkoutEnded(endedAt)
    }
}

/** Health Services binds this while reminders are on and reports each change in the person's activity. */
class WorkoutEndListenerService :
    PassiveListenerService(),
    KoinComponent {
    private val watcher: WorkoutWatcher by inject()

    override fun onUserActivityInfoReceived(info: UserActivityInfo) {
        val changedAt = Instant.fromEpochMilliseconds(info.stateChangeTime.toEpochMilli())
        if (watcher.onActivity(info.userActivityState.toActivityKind(), changedAt)) {
            Napier.i("A workout ended at $changedAt; a reminder was shown")
        }
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/ReminderRestore.kt`:

```kotlin
package app.logdate.wear.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * After a restart or an update, asks WorkManager to restore reminders: Health Services can take 10
 * seconds or more to accept a registration, longer than a receiver may run.
 */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(ReminderRestoreWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderRestoreWorker>().build())
    }
}

/** Restores what reminders need, while they are on. */
class ReminderRestoreWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val reminders: WatchReminders by inject()

    override suspend fun doWork(): Result =
        try {
            reminders.restore()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Restoring reminders after a restart failed", e)
            Result.retry()
        }

    companion object {
        const val UNIQUE_WORK_NAME = "reminder_restore"
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminders.kt`:

```kotlin
package app.logdate.wear.reminders

import kotlinx.coroutines.flow.StateFlow

/** The reminders switch and the triggers it starts and stops. */
class WatchReminders(
    private val preferences: WatchReminderPreferences,
    private val activity: ActivityRegistration,
    private val permissions: ReminderPermissions,
) {
    val enabled: StateFlow<Boolean> = preferences.enabled

    suspend fun setEnabled(enabled: Boolean) {
        preferences.setEnabled(enabled)
        if (enabled) start() else activity.unregister()
    }

    /** Starts the triggers again after a restart, an update or an app start, while reminders are on. */
    suspend fun restore() {
        if (preferences.enabled.value) start()
    }

    private suspend fun start() {
        if (permissions.activityRecognitionGranted()) activity.register()
    }
}
```

In `app/wear/src/main/AndroidManifest.xml`, replace the comment and the four permission lines from `    <!--\n        Wear inherits ACCESS_FINE_LOCATION` through `RECEIVE_BOOT_COMPLETED" tools:node="remove" />` with:

```xml
    <!--
        Wear inherits ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION from :client:location for
        on-demand geotagging at journal-entry save time. The watch does not run a foreground location
        service or sample location in the background, so those two merged permissions are removed
        from what Play lists.

        Reminders use ACTIVITY_RECOGNITION to see a workout end (Health Services user activity),
        asked for only when reminders are turned on, and RECEIVE_BOOT_COMPLETED to listen again after
        a restart. The location boot receiver merged from :client:location then also runs at boot;
        it already runs after every update and starts only the tracking the watch's settings ask for.
    -->
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" tools:node="remove" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" tools:node="remove" />
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

and insert, directly before the line `        <!-- Entry Count complication: shows today's journal entry count -->`:

```xml
        <!-- Health Services binds this, while reminders are on, when the person's activity changes. -->
        <service
            android:name=".reminders.WorkoutEndListenerService"
            android:exported="true"
            android:permission="com.google.android.wearable.healthservices.permission.PASSIVE_DATA_BINDING" />

        <!-- Health Services forgets the workout listener at a restart; this registers it again. -->
        <receiver
            android:name=".reminders.ReminderRestoreReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>

```

In `app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncScheduler.kt`, replace

```kotlin
 * The watch build removes RECEIVE_BOOT_COMPLETED, so after a reboot WorkManager reschedules this
 * work when the app process next starts, through any Data Layer event, tile, complication or launch.
```

with

```kotlin
 * After a reboot WorkManager reschedules this work by itself, through the boot permission the watch
 * declares for reminders.
```

In `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`, add the imports `app.logdate.wear.reminders.WatchReminders` and `kotlinx.coroutines.CancellationException` in ASCII order. In `startSync()`, add directly after `koin.get<WatchStateMirror>().start(scope)`:

```kotlin
        scope.launch { restoreReminders(koin.get()) }
```

and add this function after `requeueSetAsideCaptures`:

```kotlin
    /** Listens for workouts again after an update or a cleared Health Services registration, while reminders are on. */
    private suspend fun restoreReminders(reminders: WatchReminders) {
        try {
            reminders.restore()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Restoring reminders at start failed", e)
        }
    }
```

- [ ] **Step 6: Add the switch to the settings section**

Append to `app/wear/src/main/res/values/strings_watch_settings.xml`, before `</resources>`:

```xml
    <string name="wear_settings_reminders">Reminders</string>
    <string name="wear_settings_reminders_off">A nudge to record, at most once a day</string>
    <string name="wear_settings_reminders_workouts">After workouts</string>
```

Replace `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModel.kt` with:

```kotlin
package app.logdate.wear.v2.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearProtocolGate
import app.logdate.wear.reminders.ReminderPermissions
import app.logdate.wear.reminders.WatchReminders
import app.logdate.wear.sync.PhoneReachability
import app.logdate.wear.v2.haptics.HapticsPreference
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.phone.HandoffResult
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLink
import app.logdate.wear.v2.phone.PhoneLinkRepository
import app.logdate.wear.v2.today.HandoffState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the settings section shows.
 *
 * @property places Captures save the place: the setting is on and location access is granted.
 * @property phoneSettingsNodeId The phone "More on your phone" opens, or null while it cannot.
 */
data class WatchSettingsUiState(
    val status: WatchStatus = WatchStatus.Healthy,
    val haptics: HapticsPreference = HapticsPreference.ALL,
    val places: Boolean = false,
    val phoneSettingsNodeId: String? = null,
    val handoff: HandoffState = HandoffState.IDLE,
    val retrying: Boolean = false,
    val reminders: RemindersSetting = RemindersSetting(),
)

/** The reminders switch, and whether reminders follow workouts (physical activity access granted). */
data class RemindersSetting(
    val enabled: Boolean = false,
    val afterWorkouts: Boolean = false,
)

/**
 * The settings at the end of the moment list. Every input is followed as it changes; location and
 * physical activity access are read again on resume, since they change in system settings without an
 * event.
 */
class WatchSettingsViewModel(
    private val syncManager: SyncManager,
    phoneReachability: PhoneReachability,
    private val haptics: HapticsPreferenceStore,
    private val locationSettings: LocationTrackingSettingsRepository,
    private val permissionManager: PermissionManager,
    phoneLinks: PhoneLinkRepository,
    protocolGate: WearProtocolGate,
    private val phoneHandoff: PhoneHandoff,
    private val reminders: WatchReminders,
    private val reminderPermissions: ReminderPermissions,
) : ViewModel() {
    private val locationGranted = MutableStateFlow(permissionManager.isPermissionGranted(PermissionType.LOCATION))
    private val activityRecognition = MutableStateFlow(reminderPermissions.activityRecognitionGranted())
    private val handoff = MutableStateFlow(HandoffState.IDLE)
    private val retrying = MutableStateFlow(false)

    private val status = combine(syncManager.syncStatusFlow, phoneReachability.observe(), ::watchStatusOf)
    private val places =
        combine(locationSettings.observeSettings(), locationGranted) { settings, granted -> settings.autoTrackForJournalEntries && granted }
    private val phoneSettingsNode =
        combine(protocolGate.isFeatureActive(WearFeatures.PHONE_SETTINGS_LINK), phoneLinks.observe()) { active, link ->
            (link as? PhoneLink.Connected)?.nodeId?.takeIf { active }
        }
    private val remindersSetting =
        combine(reminders.enabled, activityRecognition) { on, workouts -> RemindersSetting(enabled = on, afterWorkouts = on && workouts) }

    val uiState: StateFlow<WatchSettingsUiState> =
        combine(
            status,
            haptics.preference,
            places,
            phoneSettingsNode,
            combine(handoff, retrying, remindersSetting, ::Triple),
        ) { syncStatus, hapticsChoice, placesOn, node, (handoffState, isRetrying, remindersState) ->
            WatchSettingsUiState(
                status = syncStatus,
                haptics = hapticsChoice,
                places = placesOn,
                phoneSettingsNodeId = node,
                handoff = handoffState,
                retrying = isRetrying,
                reminders = remindersState,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WatchSettingsUiState())

    fun setHaptics(preference: HapticsPreference) = haptics.set(preference)

    fun setPlaces(enabled: Boolean) {
        viewModelScope.launch {
            try {
                locationSettings.updateSettings(locationSettings.getSettings().copy(autoTrackForJournalEntries = enabled))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Couldn't turn places ${if (enabled) "on" else "off"} on the watch", e)
            }
        }
    }

    /** Whether turning places on first needs location access. */
    fun placesNeedPermission(): Boolean = !permissionManager.isPermissionGranted(PermissionType.LOCATION)

    fun onLocationPermissionResult(granted: Boolean) {
        locationGranted.value = granted
        if (granted) setPlaces(true)
    }

    /** Whether turning reminders on first needs notifications or physical activity access. */
    fun remindersNeedPermissions(): Boolean = !canRemind()

    /** After the permission request: reminders turn on once they can work, and stay off otherwise. */
    fun onReminderPermissionsResult() {
        activityRecognition.value = reminderPermissions.activityRecognitionGranted()
        if (canRemind()) setReminders(true)
    }

    fun setReminders(enabled: Boolean) {
        viewModelScope.launch {
            try {
                reminders.setEnabled(enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Couldn't turn reminders ${if (enabled) "on" else "off"} on the watch", e)
            }
        }
    }

    /** Reads location and physical activity access again; either may have changed in system settings. */
    fun onResume() {
        locationGranted.value = permissionManager.isPermissionGranted(PermissionType.LOCATION)
        activityRecognition.value = reminderPermissions.activityRecognitionGranted()
    }

    /** Sends what is waiting again, once at a time. */
    fun retrySync() {
        if (retrying.value) return
        retrying.value = true
        viewModelScope.launch {
            try {
                syncManager.fullSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Sending moments to the phone again failed", e)
            } finally {
                retrying.value = false
            }
        }
    }

    /** Opens the phone's Watch page on the phone. */
    fun openPhoneSettings() {
        val node = uiState.value.phoneSettingsNodeId ?: return
        if (handoff.value == HandoffState.SENDING) return
        handoff.value = HandoffState.SENDING
        viewModelScope.launch {
            handoff.value =
                when (phoneHandoff.open(PHONE_WATCH_SETTINGS_LINK, node)) {
                    HandoffResult.SENT -> HandoffState.SENT
                    HandoffResult.FAILED -> HandoffState.FAILED
                }
        }
    }

    fun dismissHandoff() {
        handoff.value = HandoffState.IDLE
    }

    /** Workouts are the only trigger, so reminders need both notifications and physical activity. */
    private fun canRemind(): Boolean = reminderPermissions.notificationsGranted() && reminderPermissions.activityRecognitionGranted()

    companion object {
        const val PHONE_WATCH_SETTINGS_LINK = "studio.hypertext.logdate://settings/watch"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsItems.kt`:

Add `    const val REMINDERS = "settings_reminders"` after `    const val PLACES = "settings_places"` in `WatchSettingsTags`.

Add `    val onReminders: (Boolean) -> Unit = {},` after `    val onPlaces: (Boolean) -> Unit = {},` in `WatchSettingsActions`.

In `watchSettingsItems`, add directly after the Places item:

```kotlin
    item { RemindersSwitch(state.reminders, actions.onReminders, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec)) }
```

Add this composable after `PlacesSwitch`:

```kotlin
@Composable
private fun RemindersSwitch(
    setting: RemindersSetting,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    SwitchButton(
        checked = setting.enabled,
        onCheckedChange = onCheckedChange,
        modifier = modifier.fillMaxWidth().testTag(WatchSettingsTags.REMINDERS),
        transformation = transformation,
        secondaryLabel = {
            Text(stringResource(if (setting.afterWorkouts) R.string.wear_settings_reminders_workouts else R.string.wear_settings_reminders_off))
        },
        label = { Text(stringResource(R.string.wear_settings_reminders)) },
    )
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsEntry.kt`, add `import app.logdate.wear.reminders.REMINDER_PERMISSIONS` in ASCII order, add after the `locationRequest` declaration:

```kotlin
    val reminderRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.onReminderPermissionsResult()
        }
```

change `remember(viewModel, locationRequest)` to `remember(viewModel, locationRequest, reminderRequest)`, and add this argument after `onPlaces = …`:

```kotlin
            onReminders = { enabled ->
                if (enabled && viewModel.remindersNeedPermissions()) reminderRequest.launch(REMINDER_PERMISSIONS) else viewModel.setReminders(enabled)
            },
```

- [ ] **Step 7: Bind the reminder parts**

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add these imports in ASCII order: `app.logdate.wear.reminders.ActivityRegistration`, `app.logdate.wear.reminders.ActivityTransitionStore`, `app.logdate.wear.reminders.AndroidReminderPermissions`, `app.logdate.wear.reminders.HealthServicesActivityRegistration`, `app.logdate.wear.reminders.ReminderPermissions`, `app.logdate.wear.reminders.SharedPreferencesActivityTransitionStore`, `app.logdate.wear.reminders.WatchReminders`, `app.logdate.wear.reminders.WorkoutWatcher`. Add before the module's closing brace:

```kotlin
        single<ReminderPermissions> { AndroidReminderPermissions(get()) }
        single<ActivityRegistration> { HealthServicesActivityRegistration(get()) }
        single<ActivityTransitionStore> {
            SharedPreferencesActivityTransitionStore(
                get<Context>().getSharedPreferences(SharedPreferencesActivityTransitionStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single { WorkoutWatcher(transitions = get(), dispatcher = get()) }
        single { WatchReminders(preferences = get(), activity = get(), permissions = get()) }
```

In the `WatchSettingsViewModel` binding, add `reminders = get(),` and `reminderPermissions = get(),` after `phoneHandoff = get(),`.

- [ ] **Step 8: Check the merged manifest, then run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:processDebugMainManifest && grep -c 'android.permission.ACTIVITY_RECOGNITION"\|android.permission.RECEIVE_BOOT_COMPLETED"' app/wear/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml && grep -c "ACCESS_BACKGROUND_LOCATION" app/wear/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
```

Expected: `2`, then `0`.

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:lintDebug :app:wear:assembleDebug
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearReminderManifestTest,app.logdate.wear.e2e.WatchSettingsTest,app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.WatchSyncWorkerTest
```

Expected: `BUILD SUCCESSFUL` twice; `lintDebug` reports no new error (the exported receiver handles only protected broadcasts). 6 + 4 reminder and switch unit tests, then 3 + 7 + 4 GMD tests and `WatchSyncWorkerTest` pass.

- [ ] **Step 9: Add the screenshot scene, record and check it**

In `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WatchSettingsScreenshots.kt`, add `import app.logdate.wear.v2.settings.RemindersSetting` in ASCII order and this scene after `S04_SettingsMoreOnPhone`:

```kotlin
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S05_SettingsRemindersOn() {
        SettingsScene(WatchSettingsUiState(places = true, reminders = RemindersSetting(enabled = true, afterWorkouts = true)))
    }
```

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest || true
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/WatchSettingsScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: validation fails on the 3 new `S05` previews and on the 12 `S01`–`S04` previews, whose lists now end with the Reminders switch below Places; then `15`, with 3 new and 12 modified files. Open `S01_SettingsHealthy_Small Round_*.png` with the Read tool: "Reminders" with "A nudge to record, at most once a day" and the switch off, below Places. Open `S05_SettingsRemindersOn_Small Round_*.png`: the switch on with "After workouts". Then:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b7-task9-commit.txt <<'EOF'
chore(reminders): remind on the watch when a workout ends

The new watch app's settings now have a Reminders switch. Turning it
on asks for notifications and physical activity, then listens through
Health Services for the person's activity changing; when exercise
turns to passive, the watch reminds them to record how it went, within
30 minutes, in waking hours, at most once a day. Reminders stay off
until turned on.

Health Services forgets the listener at a restart, so a boot receiver
asks WorkManager to register it again. This restores the watch's
ACTIVITY_RECOGNITION and RECEIVE_BOOT_COMPLETED permissions, which
changes what Play lists for the watch app.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/reminders app/wear/src/main/AndroidManifest.xml app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncScheduler.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/main/kotlin/app/logdate/wear/v2/settings app/wear/src/main/res/values/strings_watch_settings.xml app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/reminders app/wear/src/test/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModelTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearReminderManifestTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WatchSettingsTest.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WatchSettingsScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/WatchSettingsScreenshots && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b7-task9-commit.txt
```

---

### Task 10: The evening check while the phone is away (`chore(reminders)`)

The evening wind-down reminder belongs to the phone while it is in reach; the watch runs it only when the phone is not, so the two never both ring. While reminders are on, a WorkManager job runs at 20:30 local time each day (rescheduled after each run, after a restart through WorkManager's own boot handling, and by `restore()` at each app start). It reminds when the phone is out of reach, today has no moments, it is still waking hours, and no reminder went out today. With an evening trigger, reminders no longer need physical activity access: without it they come in the evening only.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/reminders/EveningReminder.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminder.kt` (one constant)
- Modify: `app/wear/src/main/res/values/strings_reminders.xml` (append)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderRules.kt` (one constant, one function, KDoc)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderDispatcher.kt` (one function)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminders.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModel.kt` (`canRemind`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsItems.kt` (`RemindersSwitch` label)
- Modify: `app/wear/src/main/res/values/strings_watch_settings.xml` (one value, one string)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (imports, two bindings, one argument)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/ReminderFakes.kt` (append)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/EveningReminderTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchReminderRulesTest.kt` (two tests)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchReminderDispatcherTest.kt` (three tests)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchRemindersTest.kt` (whole file)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModelTest.kt` (helper and one test)
- Screenshot: `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WatchSettingsScreenshots.kt` (one scene; 3 new images, 3 changed)

**Interfaces:**
- Consumes (Task 9): `WatchReminders`, `ReminderPermissions`, `ActivityRegistration`, the fakes. (Predecessor): `DayClock.current(): TodayWindow`, `TodaySummaryCalculator.calculate(notes).momentCount`, `PhoneReachability.observe()`.
- Produces (package `app.logdate.wear.reminders`): `WatchReminder.EVENING`; `WatchReminderRules.EVENING_CHECK: LocalTime` and `fun eveningReminderDue(now: Instant, lastReminderAt: Instant?, momentsToday: Int, phoneReachable: Boolean, timeZone: TimeZone): Boolean`; `WatchReminderDispatcher.onEveningCheck(phoneReachable: Boolean, momentsToday: Int): Boolean`; `fun nextEveningCheck(now: Instant, timeZone: TimeZone): Instant`; `interface EveningReminderWork { fun runAfter(delayMs: Long); fun cancel() }`; `class WorkManagerEveningReminderWork(workManager)`; `class EveningReminderScheduler(work, clock = Clock.System, timeZone = …)` with `scheduleNext()` and `cancel()`; `class EveningReminderWorker`; `WatchReminders(preferences, activity, permissions, evening: EveningReminderScheduler)`; test fake `FakeEveningWork`.

- [ ] **Step 1: Write the failing evening tests**

Append to `app/wear/src/test/kotlin/app/logdate/wear/reminders/ReminderFakes.kt`:

```kotlin

internal class FakeEveningWork : EveningReminderWork {
    val delays = mutableListOf<Long>()
    var cancels = 0

    override fun runAfter(delayMs: Long) {
        delays += delayMs
    }

    override fun cancel() {
        cancels++
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/reminders/EveningReminderTest.kt`:

```kotlin
package app.logdate.wear.reminders

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class EveningReminderTest {
    @Test
    fun `before 20 30 the check runs tonight`() {
        assertEquals(berlin(10, 20, 30), nextEveningCheck(berlin(10, 18), REMINDER_ZONE))
    }

    @Test
    fun `after 20 30 the check runs tomorrow`() {
        assertEquals(berlin(11, 20, 30), nextEveningCheck(berlin(10, 21), REMINDER_ZONE))
    }

    @Test
    fun `across a daylight saving change the check is still at 20 30 local`() {
        val beforeChange = LocalDateTime(2026, 10, 24, 21, 0).toInstant(REMINDER_ZONE)

        assertEquals(LocalDateTime(2026, 10, 25, 20, 30).toInstant(REMINDER_ZONE), nextEveningCheck(beforeChange, REMINDER_ZONE))
    }

    @Test
    fun `scheduling waits until the next check`() {
        val work = FakeEveningWork()

        EveningReminderScheduler(work, FixedClock(berlin(10, 18)), timeZone = { REMINDER_ZONE }).scheduleNext()

        assertEquals(listOf(150.minutes.inWholeMilliseconds), work.delays)
    }
}
```

In `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchReminderRulesTest.kt`, add these tests before the last closing brace:

```kotlin

    @Test
    fun `an evening with no moments and the phone away is due`() {
        assertTrue(WatchReminderRules.eveningReminderDue(berlin(10, 20, 30), lastReminderAt = null, momentsToday = 0, phoneReachable = false, timeZone = REMINDER_ZONE))
    }

    @Test
    fun `an evening with the phone in reach or a moment today is not`() {
        assertFalse(WatchReminderRules.eveningReminderDue(berlin(10, 20, 30), lastReminderAt = null, momentsToday = 0, phoneReachable = true, timeZone = REMINDER_ZONE))
        assertFalse(WatchReminderRules.eveningReminderDue(berlin(10, 20, 30), lastReminderAt = null, momentsToday = 1, phoneReachable = false, timeZone = REMINDER_ZONE))
        assertFalse(
            WatchReminderRules.eveningReminderDue(berlin(10, 20, 30), lastReminderAt = berlin(10, 9), momentsToday = 0, phoneReachable = false, timeZone = REMINDER_ZONE),
        )
    }
```

In `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchReminderDispatcherTest.kt`, add these tests before `private fun dispatcher(`:

```kotlin
    @Test
    fun `an evening with no moments and no phone reminds`() {
        clock.now = berlin(10, 20, 30)

        assertTrue(dispatcher(FakeReminderPreferences()).onEveningCheck(phoneReachable = false, momentsToday = 0))

        assertEquals(listOf(WatchReminder.EVENING), notifier.posted)
    }

    @Test
    fun `an evening with the phone in reach is the phone's`() {
        clock.now = berlin(10, 20, 30)

        assertFalse(dispatcher(FakeReminderPreferences()).onEveningCheck(phoneReachable = true, momentsToday = 0))
    }

    @Test
    fun `an evening check with reminders off reminds of nothing`() {
        clock.now = berlin(10, 20, 30)

        assertFalse(dispatcher(FakeReminderPreferences(enabled = false)).onEveningCheck(phoneReachable = false, momentsToday = 0))
    }
```

Replace `app/wear/src/test/kotlin/app/logdate/wear/reminders/WatchRemindersTest.kt` with:

```kotlin
package app.logdate.wear.reminders

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchRemindersTest {
    private val preferences = FakeReminderPreferences(enabled = false)
    private val activity = FakeActivityRegistration()
    private val permissions = FakeReminderPermissions()
    private val eveningWork = FakeEveningWork()
    private val reminders =
        WatchReminders(
            preferences = preferences,
            activity = activity,
            permissions = permissions,
            evening = EveningReminderScheduler(eveningWork, FixedClock(berlin(10, 18)), timeZone = { REMINDER_ZONE }),
        )

    @Test
    fun `turning reminders on schedules the evening and listens for workouts`() =
        runTest {
            reminders.setEnabled(true)

            assertTrue(preferences.enabled.value)
            assertEquals(1, eveningWork.delays.size)
            assertEquals(1, activity.registrations)
        }

    @Test
    fun `turning reminders off stops both`() =
        runTest {
            reminders.setEnabled(true)

            reminders.setEnabled(false)

            assertFalse(preferences.enabled.value)
            assertEquals(1, eveningWork.cancels)
            assertEquals(1, activity.unregistrations)
        }

    @Test
    fun `without physical activity access the evening still comes`() =
        runTest {
            permissions.activityRecognition = false

            reminders.setEnabled(true)

            assertEquals(1, eveningWork.delays.size)
            assertEquals(0, activity.registrations)
        }

    @Test
    fun `a restart starts both again only while reminders are on`() =
        runTest {
            reminders.restore()
            assertTrue(eveningWork.delays.isEmpty())
            preferences.setEnabled(true)

            reminders.restore()

            assertEquals(1, eveningWork.delays.size)
            assertEquals(1, activity.registrations)
        }
}
```

In `app/wear/src/test/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModelTest.kt`, add `import app.logdate.wear.reminders.EveningReminderScheduler` and `import app.logdate.wear.reminders.FakeEveningWork` in ASCII order, replace `reminders = WatchReminders(reminderPreferences, activityRegistration, reminderPermissions),` in `viewModel()` with

```kotlin
            reminders = WatchReminders(reminderPreferences, activityRegistration, reminderPermissions, EveningReminderScheduler(FakeEveningWork())),
```

and add this test before `private fun viewModel()`:

```kotlin
    @Test
    fun `without physical activity reminders still come in the evening`() =
        runTest {
            reminderPermissions.activityRecognition = false
            val viewModel = viewModel()

            viewModel.uiState.test {
                viewModel.onReminderPermissionsResult()

                assertEquals(RemindersSetting(enabled = true, afterWorkouts = false), expectMostRecentItem().reminders)
            }
            assertEquals(0, activityRegistration.registrations)
        }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.reminders.*' --tests 'app.logdate.wear.v2.settings.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'EveningReminderWork'`, `Unresolved reference 'nextEveningCheck'`, `Unresolved reference 'eveningReminderDue'`, `Unresolved reference 'onEveningCheck'` and `Unresolved reference 'EVENING'`.

- [ ] **Step 3: Add the evening reminder, its rule and its dispatch**

Append to `app/wear/src/main/res/values/strings_reminders.xml`, before `</resources>`:

```xml
    <string name="wear_reminder_evening_title">Nothing recorded today</string>
    <string name="wear_reminder_evening_body">Record a moment before the day ends</string>
```

In `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminder.kt`, add after the `WORKOUT_ENDED(…),` line:

```kotlin
    EVENING(R.string.wear_reminder_evening_title, R.string.wear_reminder_evening_body),
```

In `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderRules.kt`, replace the object's KDoc with:

```kotlin
/**
 * When the watch may remind: inside the waking window, at most once a streak day; for a workout,
 * within [WORKOUT_WINDOW] of its end; in the evening, from [EVENING_CHECK], when today has no moments
 * and the phone, whose evening check it is while it is in reach, is out of reach. The waking window
 * is the fallback the reminder engine uses until it learns the person's own hours.
 */
```

add `    val EVENING_CHECK: LocalTime = LocalTime(20, 30)` after `    val WAKING_END: LocalTime = LocalTime(22, 30)`, and add this function at the end of the object:

```kotlin

    fun eveningReminderDue(
        now: Instant,
        lastReminderAt: Instant?,
        momentsToday: Int,
        phoneReachable: Boolean,
        timeZone: TimeZone,
    ): Boolean = !phoneReachable && momentsToday == 0 && isWaking(now, timeZone) && !remindedToday(now, lastReminderAt, timeZone)
```

In `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderDispatcher.kt`, add after `onWorkoutEnded`:

```kotlin

    /** Reminds in the evening while the phone is away and today has no moments. @return true when a reminder was shown. */
    fun onEveningCheck(
        phoneReachable: Boolean,
        momentsToday: Int,
    ): Boolean {
        if (!preferences.enabled.value) return false
        val now = clock.now()
        if (!WatchReminderRules.eveningReminderDue(now, preferences.lastReminderAt(), momentsToday, phoneReachable, timeZone())) return false
        return post(WatchReminder.EVENING, now)
    }
```

Create `app/wear/src/main/kotlin/app/logdate/wear/reminders/EveningReminder.kt`:

```kotlin
package app.logdate.wear.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.logdate.client.domain.today.TodaySummaryCalculator
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.sync.PhoneReachability
import app.logdate.wear.v2.today.DayClock
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The next evening check after [now]: [WatchReminderRules.EVENING_CHECK] today, or tomorrow once it has passed. */
fun nextEveningCheck(
    now: Instant,
    timeZone: TimeZone,
): Instant {
    val today = now.toLocalDateTime(timeZone).date
    val tonight = LocalDateTime(today, WatchReminderRules.EVENING_CHECK).toInstant(timeZone)
    if (now < tonight) return tonight
    return LocalDateTime(today.plus(1, DateTimeUnit.DAY), WatchReminderRules.EVENING_CHECK).toInstant(timeZone)
}

/** Runs the evening check once, after a delay. */
interface EveningReminderWork {
    fun runAfter(delayMs: Long)

    fun cancel()
}

/** [EveningReminderWork] as unique WorkManager work, so one check ever waits. */
class WorkManagerEveningReminderWork(
    private val workManager: WorkManager,
) : EveningReminderWork {
    override fun runAfter(delayMs: Long) {
        val request =
            OneTimeWorkRequestBuilder<EveningReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(EveningReminderWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(EveningReminderWorker.UNIQUE_WORK_NAME)
    }
}

/** Sets the evening check for the next [WatchReminderRules.EVENING_CHECK] in the watch's time zone. */
class EveningReminderScheduler(
    private val work: EveningReminderWork,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    fun scheduleNext() {
        val now = clock.now()
        work.runAfter((nextEveningCheck(now, timeZone()) - now).inWholeMilliseconds)
    }

    fun cancel() = work.cancel()
}

/**
 * The evening check: while reminders are on, reminds when the phone is out of reach and today has no
 * moments, then sets tomorrow's check. Reaching the phone is given 5 seconds; no answer counts as out
 * of reach.
 */
class EveningReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val preferences: WatchReminderPreferences by inject()
    private val dispatcher: WatchReminderDispatcher by inject()
    private val scheduler: EveningReminderScheduler by inject()
    private val phoneReachability: PhoneReachability by inject()
    private val dayClock: DayClock by inject()
    private val notesRepository: JournalNotesRepository by inject()

    override suspend fun doWork(): Result {
        if (!preferences.enabled.value) return Result.success()
        try {
            val phoneReachable = withTimeoutOrNull(PHONE_CHECK_TIMEOUT) { phoneReachability.observe().first() } != null
            val today = dayClock.current()
            val notes = notesRepository.observeNotesInRange(today.start, today.end).first().filter { it.creationTimestamp in today }
            dispatcher.onEveningCheck(phoneReachable = phoneReachable, momentsToday = TodaySummaryCalculator.calculate(notes).momentCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("The evening reminder check failed", e)
        }
        scheduler.scheduleNext()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "evening_reminder"
        private val PHONE_CHECK_TIMEOUT = 5.seconds
    }
}
```

Replace `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminders.kt` with:

```kotlin
package app.logdate.wear.reminders

import kotlinx.coroutines.flow.StateFlow

/** The reminders switch and the triggers it starts and stops: the evening check, and workouts when allowed. */
class WatchReminders(
    private val preferences: WatchReminderPreferences,
    private val activity: ActivityRegistration,
    private val permissions: ReminderPermissions,
    private val evening: EveningReminderScheduler,
) {
    val enabled: StateFlow<Boolean> = preferences.enabled

    suspend fun setEnabled(enabled: Boolean) {
        preferences.setEnabled(enabled)
        if (enabled) start() else stop()
    }

    /** Starts the triggers again after a restart, an update or an app start, while reminders are on. */
    suspend fun restore() {
        if (preferences.enabled.value) start()
    }

    private suspend fun start() {
        evening.scheduleNext()
        if (permissions.activityRecognitionGranted()) activity.register()
    }

    private suspend fun stop() {
        evening.cancel()
        activity.unregister()
    }
}
```

- [ ] **Step 4: Let reminders turn on without physical activity**

In `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModel.kt`, replace

```kotlin
    /** Workouts are the only trigger, so reminders need both notifications and physical activity. */
    private fun canRemind(): Boolean = reminderPermissions.notificationsGranted() && reminderPermissions.activityRecognitionGranted()
```

with

```kotlin
    /** The evening check needs only notifications; workouts also need physical activity. */
    private fun canRemind(): Boolean = reminderPermissions.notificationsGranted()
```

and replace the KDoc of `remindersNeedPermissions` with `/** Whether turning reminders on should first ask for notifications or physical activity access. */`, and its body with `!reminderPermissions.notificationsGranted() || !reminderPermissions.activityRecognitionGranted()`.

In `app/wear/src/main/res/values/strings_watch_settings.xml`, change the value of `wear_settings_reminders_workouts` to `After workouts and in the evening`, and add after it:

```xml
    <string name="wear_settings_reminders_evening">In the evening</string>
```

In `app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsItems.kt`, replace the `secondaryLabel` argument of `RemindersSwitch` with:

```kotlin
        secondaryLabel = {
            val description =
                when {
                    !setting.enabled -> R.string.wear_settings_reminders_off
                    setting.afterWorkouts -> R.string.wear_settings_reminders_workouts
                    else -> R.string.wear_settings_reminders_evening
                }
            Text(stringResource(description))
        },
```

- [ ] **Step 5: Bind the evening check**

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add these imports in ASCII order: `androidx.work.WorkManager`, `app.logdate.wear.reminders.EveningReminderScheduler`, `app.logdate.wear.reminders.EveningReminderWork`, `app.logdate.wear.reminders.WorkManagerEveningReminderWork`. Add before the module's closing brace:

```kotlin
        single<EveningReminderWork> { WorkManagerEveningReminderWork(WorkManager.getInstance(get<Context>())) }
        single { EveningReminderScheduler(work = get()) }
```

and add `evening = get()` to the `WatchReminders(…)` binding: `single { WatchReminders(preferences = get(), activity = get(), permissions = get(), evening = get()) }`.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.WatchSettingsTest,app.logdate.wear.e2e.ReminderNotificationTest
```

Expected: `BUILD SUCCESSFUL` twice; 4 + 2 + 3 + 4 + 1 new or rewritten reminder and settings tests pass, and the GMD run proves the real Koin graph resolves `WatchReminders` with its evening scheduler.

- [ ] **Step 7: Add the screenshot scene, record and check it**

In `app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WatchSettingsScreenshots.kt`, add after `S05_SettingsRemindersOn`:

```kotlin
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S06_SettingsRemindersEveningOnly() {
        SettingsScene(WatchSettingsUiState(places = true, reminders = RemindersSetting(enabled = true, afterWorkouts = false)))
    }
```

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest || true
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:updateDebugScreenshotTest && ls "app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/WatchSettingsScreenshots" | wc -l && git status --short app/wear/src/screenshotTestDebug
```

Expected: validation fails on the 3 new `S06` previews and the 3 `S05` previews (their label is now "After workouts and in the evening"); then `18`, with 3 new and 3 modified files. Open `S06_SettingsRemindersEveningOnly_Small Round_*.png` with the Read tool: the switch on with "In the evening". Then:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b7-task10-commit.txt <<'EOF'
chore(reminders): remind in the evening when the phone is away

With reminders on, the watch now checks at 20:30 each evening. When
the phone is out of reach and nothing was recorded today, it reminds
the person to record a moment before the day ends, once a day at most
across both reminder kinds. While the phone is in reach, the evening
is the phone's to handle, so the two never both ring.

Reminders now turn on with notifications alone. Without physical
activity access they come in the evening only, and the switch says
so.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 9: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/reminders app/wear/src/main/res/values/strings_reminders.xml app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModel.kt app/wear/src/main/kotlin/app/logdate/wear/v2/settings/WatchSettingsItems.kt app/wear/src/main/res/values/strings_watch_settings.xml app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/reminders app/wear/src/test/kotlin/app/logdate/wear/v2/settings/WatchSettingsViewModelTest.kt app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/WatchSettingsScreenshots.kt app/wear/src/screenshotTestDebug/reference/app/logdate/wear/screenshots/WatchSettingsScreenshots && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b7-task10-commit.txt
```

---

### Task 11: Tiles and complications that follow the data (`chore(wear)`)

While the flag is on, the watch's surfaces change with its moments. `SurfaceUpdater` asks the system to redraw every tile and complication: at once after something the person does on the watch (seen as the outbox growing, which every capture, edit and deletion causes, including the recorder plan's), when the day changes, and for changes sync brings in from the phone at most once every 5 minutes, the pace complications are meant to update at. `SurfaceComponents` retires the surfaces the spec drops (Entry Count, Mood and the Quick Capture tile) while the flag is on and restores them when it is off; Tasks 13 and 14 add the new surfaces to the same catalog, disabled in the manifest until it enables them. With the flag off nothing here runs, so the current app's surfaces update as they do today.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceUpdater.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponents.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceCoordinator.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt` (start the coordinator)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt` (one call)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt` (imports, four bindings)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceFakes.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceUpdaterTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponentsTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceCoordinatorTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/SurfaceComponentsTest.kt`

**Interfaces:**
- Consumes: `FeatureFlagStore.observe(FeatureFlag.WATCH_EXPERIENCE_V2)`, `SyncMetadataService.observePendingCount(): Flow<Int>`, `DayClock.observe(): Flow<TodayWindow>`, `TileService.getUpdater(context).requestUpdate(Class)`, `ComplicationDataSourceUpdateRequester.create(context, ComponentName).requestUpdateAll()`.
- Produces (package `app.logdate.wear.v2.surfaces`): `fun interface SurfaceRefresher { fun refreshAll() }`, `class AndroidSurfaceRefresher(context)`, `class SurfaceUpdater(refresher, scope, clock = Clock.System, minSyncInterval = 5.minutes)` with `setActive(Boolean)`, `refreshNow()`, `refreshForSync()`; `object SurfaceCatalog { val TILES: List<Class<out TileService>>; val COMPLICATIONS: List<Class<out ComplicationDataSourceService>>; val NEW_APP_ONLY: List<String>; val CURRENT_APP_ONLY: List<String> }`; `fun interface ComponentSwitch { fun setEnabled(className: String, enabled: Boolean) }`, `class PackageManagerComponentSwitch(context)`, `class SurfaceComponents(switch, newAppOnly = SurfaceCatalog.NEW_APP_ONLY, currentAppOnly = SurfaceCatalog.CURRENT_APP_ONLY)` with `apply(newAppOn: Boolean)`; `class SurfaceCoordinator(newAppEnabled: Flow<Boolean>, components, updater, pendingUploads: Flow<Int>, days: Flow<TodayWindow>)` with `start(scope): Job`.

- [ ] **Step 1: Write the failing tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceFakes.kt`:

```kotlin
package app.logdate.wear.v2.surfaces

import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Clock
import kotlin.time.Instant

internal class CountingRefresher : SurfaceRefresher {
    var count = 0

    override fun refreshAll() {
        count++
    }
}

internal class RecordingSwitch : ComponentSwitch {
    val states = mutableMapOf<String, Boolean>()

    override fun setEnabled(
        className: String,
        enabled: Boolean,
    ) {
        states[className] = enabled
    }
}

/** A clock that reads the test scheduler's virtual time. */
internal class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(scheduler.currentTime)
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceUpdaterTest.kt`:

```kotlin
package app.logdate.wear.v2.surfaces

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceUpdaterTest {
    private val refresher = CountingRefresher()

    @Test
    fun `nothing refreshes while inactive`() =
        runTest {
            val updater = updater()

            updater.refreshNow()
            updater.refreshForSync()
            advanceUntilIdle()

            assertEquals(0, refresher.count)
        }

    @Test
    fun `a change on the watch refreshes at once`() =
        runTest {
            val updater = updater().also { it.setActive(true) }

            updater.refreshNow()

            assertEquals(1, refresher.count)
        }

    @Test
    fun `a sync change soon after a refresh waits until 5 minutes have passed`() =
        runTest {
            val updater = updater().also { it.setActive(true) }
            updater.refreshNow()
            advanceTimeBy(1.minutes)

            updater.refreshForSync()
            runCurrent()
            assertEquals(1, refresher.count)
            advanceTimeBy(4.minutes)
            runCurrent()

            assertEquals(2, refresher.count)
        }

    @Test
    fun `a burst of sync changes refreshes once and then once more after 5 minutes`() =
        runTest {
            val updater = updater().also { it.setActive(true) }

            repeat(3) { updater.refreshForSync() }
            runCurrent()
            assertEquals(1, refresher.count)
            advanceTimeBy(1.minutes)
            repeat(2) { updater.refreshForSync() }
            advanceUntilIdle()

            assertEquals(2, refresher.count)
            assertEquals(5.minutes.inWholeMilliseconds, testScheduler.currentTime)
        }

    @Test
    fun `a change on the watch replaces a waiting sync refresh`() =
        runTest {
            val updater = updater().also { it.setActive(true) }
            updater.refreshNow()
            updater.refreshForSync()

            updater.refreshNow()
            advanceUntilIdle()

            assertEquals(2, refresher.count)
        }

    @Test
    fun `turning inactive drops a waiting refresh`() =
        runTest {
            val updater = updater().also { it.setActive(true) }
            updater.refreshNow()
            updater.refreshForSync()

            updater.setActive(false)
            advanceUntilIdle()

            assertEquals(1, refresher.count)
        }

    private fun TestScope.updater() = SurfaceUpdater(refresher = refresher, scope = backgroundScope, clock = SchedulerClock(testScheduler))
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponentsTest.kt`:

```kotlin
package app.logdate.wear.v2.surfaces

import org.junit.Test
import kotlin.test.assertEquals

class SurfaceComponentsTest {
    private val switch = RecordingSwitch()
    private val components = SurfaceComponents(switch, newAppOnly = listOf("NewTile"), currentAppOnly = listOf("OldTile", "OldComplication"))

    @Test
    fun `the new app offers its surfaces and retires the ones it replaces`() {
        components.apply(newAppOn = true)

        assertEquals(mapOf("NewTile" to true, "OldTile" to false, "OldComplication" to false), switch.states)
    }

    @Test
    fun `the current app offers its own surfaces and hides the new ones`() {
        components.apply(newAppOn = false)

        assertEquals(mapOf("NewTile" to false, "OldTile" to true, "OldComplication" to true), switch.states)
    }

    @Test
    fun `the spec retires Entry Count, Mood and the Quick Capture tile`() {
        assertEquals(
            listOf(
                "app.logdate.wear.complication.EntryCountComplicationService",
                "app.logdate.wear.complication.MoodComplicationService",
                "app.logdate.wear.tile.QuickCaptureTileService",
            ),
            SurfaceCatalog.CURRENT_APP_ONLY,
        )
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceCoordinatorTest.kt`:

```kotlin
package app.logdate.wear.v2.surfaces

import app.logdate.wear.v2.today.TodayWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceCoordinatorTest {
    private val refresher = CountingRefresher()
    private val switch = RecordingSwitch()
    private val flag = MutableStateFlow(false)
    private val pending = MutableStateFlow(0)
    private val days = MutableStateFlow(day(10))

    @Test
    fun `the flag on swaps the surfaces and refreshes once`() =
        runTest {
            start()

            flag.value = true
            runCurrent()

            assertEquals(false, switch.states["OldTile"])
            assertEquals(true, switch.states["NewTile"])
            assertEquals(1, refresher.count)
        }

    @Test
    fun `the flag off restores the current surfaces and stops refreshing`() =
        runTest {
            val updater = start()
            flag.value = true
            runCurrent()

            flag.value = false
            runCurrent()
            updater.refreshNow()

            assertEquals(true, switch.states["OldTile"])
            assertEquals(1, refresher.count)
        }

    @Test
    fun `a growing outbox refreshes at once and a shrinking one does not`() =
        runTest {
            start()
            flag.value = true
            runCurrent()

            pending.value = 1
            runCurrent()
            assertEquals(2, refresher.count)
            pending.value = 0
            runCurrent()

            assertEquals(2, refresher.count)
        }

    @Test
    fun `a new day refreshes`() =
        runTest {
            start()
            flag.value = true
            runCurrent()

            days.value = day(11)
            runCurrent()

            assertEquals(2, refresher.count)
        }

    private fun TestScope.start(): SurfaceUpdater {
        val updater = SurfaceUpdater(refresher = refresher, scope = backgroundScope, clock = SchedulerClock(testScheduler))
        SurfaceCoordinator(
            newAppEnabled = flag,
            components = SurfaceComponents(switch, newAppOnly = listOf("NewTile"), currentAppOnly = listOf("OldTile")),
            updater = updater,
            pendingUploads = pending,
            days = days,
        ).start(backgroundScope)
        runCurrent()
        return updater
    }

    private fun day(date: Int): TodayWindow {
        val zone = TimeZone.UTC
        return TodayWindow(
            date = LocalDate(2026, 10, date),
            timeZone = zone,
            start = LocalDate(2026, 10, date).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, date + 1).atStartOfDayIn(zone),
        )
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.surfaces.*'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'SurfaceRefresher'`, `Unresolved reference 'SurfaceUpdater'`, `Unresolved reference 'SurfaceComponents'` and `Unresolved reference 'SurfaceCoordinator'`.

- [ ] **Step 3: Write the failing GMD test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/SurfaceComponentsTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.wear.v2.surfaces.PackageManagerComponentSwitch
import app.logdate.wear.v2.surfaces.SurfaceCatalog
import app.logdate.wear.v2.surfaces.SurfaceComponents
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Swaps the watch's surfaces through the real package manager on a Wear OS emulator. */
@RunWith(AndroidJUnit4::class)
class SurfaceComponentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val components = SurfaceComponents(PackageManagerComponentSwitch(context))

    @After
    fun restoreTheCurrentApp() {
        components.apply(newAppOn = false)
    }

    @Test
    fun every_catalog_entry_is_a_component_of_the_app() {
        (SurfaceCatalog.NEW_APP_ONLY + SurfaceCatalog.CURRENT_APP_ONLY).forEach { className ->
            context.packageManager.getServiceInfo(
                ComponentName(context.packageName, className),
                PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        }
    }

    @Test
    fun the_new_app_retires_the_surfaces_it_replaces() {
        components.apply(newAppOn = true)

        SurfaceCatalog.CURRENT_APP_ONLY.forEach { assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(it), it) }
        SurfaceCatalog.NEW_APP_ONLY.forEach { assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state(it), it) }
    }

    @Test
    fun the_current_app_offers_its_own_surfaces() {
        components.apply(newAppOn = false)

        SurfaceCatalog.CURRENT_APP_ONLY.forEach { assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state(it), it) }
        SurfaceCatalog.NEW_APP_ONLY.forEach { assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(it), it) }
    }

    private fun state(className: String): Int = context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName, className))
}
```

- [ ] **Step 4: Run the GMD test to verify it fails**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.SurfaceComponentsTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'SurfaceComponents'`.

- [ ] **Step 5: Add the updater, the components and the coordinator**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceUpdater.kt`:

```kotlin
package app.logdate.wear.v2.surfaces

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Asks the system to draw the watch's tiles and complications again. */
fun interface SurfaceRefresher {
    fun refreshAll()
}

/** [SurfaceRefresher] for every tile and complication in [SurfaceCatalog]. */
class AndroidSurfaceRefresher(
    private val context: Context,
) : SurfaceRefresher {
    override fun refreshAll() {
        val tiles = TileService.getUpdater(context)
        SurfaceCatalog.TILES.forEach(tiles::requestUpdate)
        SurfaceCatalog.COMPLICATIONS.forEach { service ->
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, service)).requestUpdateAll()
        }
    }
}

/**
 * Refreshes the watch's surfaces: at once after something the person does on the watch, and at most
 * once every [minSyncInterval] for changes sync brings in, the pace complications are meant to update
 * at. While inactive it does nothing, and the current watch app's surfaces update on their own
 * schedule.
 */
class SurfaceUpdater(
    private val refresher: SurfaceRefresher,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val minSyncInterval: Duration = DEFAULT_MIN_SYNC_INTERVAL,
) {
    private val lock = Any()
    private var active = false
    private var lastRefreshAt: Instant? = null
    private var pending: Job? = null

    fun setActive(active: Boolean) {
        synchronized(lock) {
            this.active = active
            if (!active) cancelPending()
        }
    }

    /** Refreshes now, for something the person did on the watch; a refresh waiting for sync is then not needed. */
    fun refreshNow() {
        synchronized(lock) {
            if (!active) return
            cancelPending()
            lastRefreshAt = clock.now()
        }
        refreshSafely()
    }

    /** Refreshes for a change sync brought in, no sooner than [minSyncInterval] after the last refresh. */
    fun refreshForSync() {
        synchronized(lock) {
            if (!active || pending?.isActive == true) return
            val last = lastRefreshAt
            val wait = if (last == null) Duration.ZERO else (last + minSyncInterval - clock.now()).coerceAtLeast(Duration.ZERO)
            pending =
                scope.launch {
                    delay(wait)
                    synchronized(lock) { lastRefreshAt = clock.now() }
                    refreshSafely()
                }
        }
    }

    private fun cancelPending() {
        pending?.cancel()
        pending = null
    }

    private fun refreshSafely() {
        try {
            refresher.refreshAll()
        } catch (e: Exception) {
            Napier.e("Couldn't ask the system to redraw the watch's tiles and complications", e)
        }
    }

    companion object {
        val DEFAULT_MIN_SYNC_INTERVAL: Duration = 5.minutes
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponents.kt`:

```kotlin
package app.logdate.wear.v2.surfaces

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import app.logdate.wear.complication.StreakComplicationService
import app.logdate.wear.tile.TodaySummaryTileService

/**
 * The watch's tiles and complications. Their class names never change once shipped, because watch
 * faces and the tile carousel refer to them by name.
 */
object SurfaceCatalog {
    /** Tiles to redraw when moments change. */
    val TILES: List<Class<out TileService>> = listOf(TodaySummaryTileService::class.java)

    /** Complications to redraw when moments change. */
    val COMPLICATIONS: List<Class<out ComplicationDataSourceService>> = listOf(StreakComplicationService::class.java)

    /** Surfaces only the new watch app offers; off while it is off. */
    val NEW_APP_ONLY: List<String> = emptyList()

    /** Surfaces the new watch app retires; off while it is on. */
    val CURRENT_APP_ONLY: List<String> =
        listOf(
            "app.logdate.wear.complication.EntryCountComplicationService",
            "app.logdate.wear.complication.MoodComplicationService",
            "app.logdate.wear.tile.QuickCaptureTileService",
        )
}

/** Turns one of the app's own components on or off. */
fun interface ComponentSwitch {
    fun setEnabled(
        className: String,
        enabled: Boolean,
    )
}

/** [ComponentSwitch] through the package manager, without restarting the app. */
class PackageManagerComponentSwitch(
    private val context: Context,
) : ComponentSwitch {
    override fun setEnabled(
        className: String,
        enabled: Boolean,
    ) {
        val component = ComponentName(context.packageName, className)
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (context.packageManager.getComponentEnabledSetting(component) == state) return
        context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}

/** Offers the new watch app's surfaces and retires the ones it replaces while it is on, and the reverse while it is off. */
class SurfaceComponents(
    private val switch: ComponentSwitch,
    private val newAppOnly: List<String> = SurfaceCatalog.NEW_APP_ONLY,
    private val currentAppOnly: List<String> = SurfaceCatalog.CURRENT_APP_ONLY,
) {
    fun apply(newAppOn: Boolean) {
        newAppOnly.forEach { switch.setEnabled(it, newAppOn) }
        currentAppOnly.forEach { switch.setEnabled(it, !newAppOn) }
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceCoordinator.kt`:

```kotlin
package app.logdate.wear.v2.surfaces

import app.logdate.wear.v2.today.TodayWindow
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Follows the new watch app's flag. While it is on, the new surfaces are offered and refreshed: when
 * the outbox grows (a capture, edit or deletion made on the watch) and when the day changes. Changes
 * from the phone reach [SurfaceUpdater.refreshForSync] from the Data Layer listener.
 */
class SurfaceCoordinator(
    private val newAppEnabled: Flow<Boolean>,
    private val components: SurfaceComponents,
    private val updater: SurfaceUpdater,
    private val pendingUploads: Flow<Int>,
    private val days: Flow<TodayWindow>,
) {
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            newAppEnabled.distinctUntilChanged().collectLatest { on ->
                applyComponents(on)
                updater.setActive(on)
                if (!on) return@collectLatest
                updater.refreshNow()
                coroutineScope {
                    launch { refreshWhenTheOutboxGrows() }
                    launch { days.drop(1).collect { updater.refreshNow() } }
                }
            }
        }

    private suspend fun refreshWhenTheOutboxGrows() {
        var previous: Int? = null
        pendingUploads.collect { count ->
            val grew = previous?.let { count > it } ?: false
            previous = count
            if (grew) updater.refreshNow()
        }
    }

    private fun applyComponents(newAppOn: Boolean) {
        try {
            components.apply(newAppOn)
        } catch (e: Exception) {
            Napier.e("Couldn't switch the watch's tiles and complications for the ${if (newAppOn) "new" else "current"} app", e)
        }
    }
}
```

- [ ] **Step 6: Start it, and refresh for changes from the phone**

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, add these imports in ASCII order: `app.logdate.client.datastore.featureflags.FeatureFlag`, `app.logdate.client.datastore.featureflags.FeatureFlagStore`, `app.logdate.client.sync.metadata.SyncMetadataService`, `app.logdate.wear.v2.surfaces.AndroidSurfaceRefresher`, `app.logdate.wear.v2.surfaces.PackageManagerComponentSwitch`, `app.logdate.wear.v2.surfaces.SurfaceComponents`, `app.logdate.wear.v2.surfaces.SurfaceCoordinator`, `app.logdate.wear.v2.surfaces.SurfaceUpdater`. Add before the module's closing brace:

```kotlin
        single { SurfaceUpdater(refresher = AndroidSurfaceRefresher(get()), scope = get()) }
        single { SurfaceComponents(PackageManagerComponentSwitch(get())) }
        single {
            SurfaceCoordinator(
                newAppEnabled = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                components = get(),
                updater = get(),
                pendingUploads = get<SyncMetadataService>().observePendingCount(),
                days = get<DayClock>().observe(),
            )
        }
```

In `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`, add `import app.logdate.wear.v2.surfaces.SurfaceCoordinator` in ASCII order, call `startSurfaces()` on its own line directly after `startSync()` in `onCreate`, and add:

```kotlin
    /** Swaps and refreshes tiles and complications for the watch app the flag picks. */
    private fun startSurfaces() {
        val koin =
            org.koin.java.KoinJavaComponent
                .getKoin()
        koin.get<SurfaceCoordinator>().start(koin.get<CoroutineScope>())
    }
```

In `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt`, add `import app.logdate.wear.v2.surfaces.SurfaceUpdater` in ASCII order, and add after the `for (event in syncEvents) { … }` loop in `onDataChanged`:

```kotlin
            koin.getOrNull<SurfaceUpdater>()?.refreshForSync()
```

- [ ] **Step 7: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.SurfaceComponentsTest,app.logdate.wear.e2e.ShellNavigationTest,app.logdate.wear.e2e.WearSyncE2ETest
```

Expected: `BUILD SUCCESSFUL` twice; 6 + 3 + 4 surface unit tests, then 3 + 4 GMD tests and `WearSyncE2ETest` pass. `ShellNavigationTest` turns the flag on and off, so it also exercises the coordinator swapping components on the emulator.

- [ ] **Step 8: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b8-task11-commit.txt <<'EOF'
chore(wear): refresh tiles and complications as moments change

With the new watch app on, its tiles and complications now redraw
right after something is captured, edited or deleted on the watch, and
when the day changes. Changes synced from the phone redraw them at
most once every 5 minutes, the pace complications are meant to update
at. The new app also retires the surfaces the redesign drops: the
Entry Count and Mood complications and the Quick Capture tile, which
come back when the new app is turned off.

With the new app off, nothing changes for the current surfaces.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 9: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/SurfaceComponentsTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b8-task11-commit.txt
```

---

### Task 12: The campfire in ranged complication slots (`feat(wear)`)

The spec's Campfire complication is a `RANGED_VALUE` whose color follows the campfire's phase. It is added to the existing Streak complication's supported types, so every placement a person already has continues to work, and short and long slots look exactly as before. This is visible to everyone with campfire streaks on (the default), so it is a `feat`. The range has three steps: no fire (charred grey), embers (orange) and a burning fire (yellow), the colors of the app's campfire in `client/ui/src/commonMain/kotlin/app/logdate/ui/streak/Campfire.kt`. The fire's day count is the slot's text, or "–" without a fire. With campfire streaks turned off, a ranged slot gets no data, since a plain streak count has no range to fill.

**Files:**
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/complication/CampfireComplicationContent.kt` (whole file)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/complication/StreakComplicationService.kt` (whole file)
- Modify: `app/wear/src/main/AndroidManifest.xml` (one attribute value)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/complication/CampfireComplicationContentTest.kt` (three tests)
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/CampfireComplicationDataTest.kt`

**Interfaces:**
- Consumes: `CampfireState.toComplicationContent()`, `FirePhase`, the existing strings `wear_complication_campfire_*` and plurals `wear_complication_campfire_burning`.
- Produces (package `app.logdate.wear.complication`): `CampfireComplicationContent.rangedValue: Float`; `internal const val CAMPFIRE_RANGE_MIN = 0f`, `CAMPFIRE_RANGE_MAX = 2f`; `internal val CAMPFIRE_COLOR_RAMP: IntArray`; `internal fun campfireComplicationData(context: Context, content: CampfireComplicationContent, type: ComplicationType, tapAction: PendingIntent): ComplicationData`.

- [ ] **Step 1: Write the failing unit tests**

In `app/wear/src/test/kotlin/app/logdate/wear/complication/CampfireComplicationContentTest.kt`, add these tests after `an unlit fire shows bare logs and no count`:

```kotlin
    @Test
    fun `a ranged slot is full for a burning fire and half full for embers`() {
        assertEquals(CAMPFIRE_RANGE_MAX, fire.toComplicationContent().rangedValue)
        assertEquals(1f, fire.copy(phase = FirePhase.EMBERS, loggedToday = false).toComplicationContent().rangedValue)
    }

    @Test
    fun `a ranged slot is empty without a fire`() {
        assertEquals(CAMPFIRE_RANGE_MIN, fire.copy(phase = FirePhase.OUT, runDays = 0, size = null).toComplicationContent().rangedValue)
        assertEquals(CAMPFIRE_RANGE_MIN, fire.copy(phase = FirePhase.UNLIT, runDays = 0, size = null).toComplicationContent().rangedValue)
    }

    @Test
    fun `the ranged colors step from charred logs to the outer and inner flame`() {
        assertEquals(listOf(0xFFA8A29C.toInt(), 0xFFFF8A3D.toInt(), 0xFFFFD166.toInt()), CAMPFIRE_COLOR_RAMP.toList())
    }
```

- [ ] **Step 2: Run the unit tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.complication.CampfireComplicationContentTest'
```

Expected: FAIL at `:app:wear:compileDebugUnitTestKotlin` with `Unresolved reference 'CAMPFIRE_RANGE_MAX'`, `Unresolved reference 'rangedValue'`, `Unresolved reference 'CAMPFIRE_RANGE_MIN'` and `Unresolved reference 'CAMPFIRE_COLOR_RAMP'`.

- [ ] **Step 3: Write the failing GMD test**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/CampfireComplicationDataTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.logdate.client.domain.streak.FirePhase
import app.logdate.wear.R
import app.logdate.wear.complication.CampfireComplicationContent
import app.logdate.wear.complication.StreakComplicationService
import app.logdate.wear.complication.campfireComplicationData
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.assertEquals

/** Builds the campfire complication's data on a Wear OS emulator, where complication text can be read back. */
@RunWith(AndroidJUnit4::class)
class CampfireComplicationDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val tap = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
    private val burning = CampfireComplicationContent(iconRes = R.drawable.ic_campfire_flame, runDays = 12, phase = FirePhase.BURNING)

    @Test
    fun a_ranged_slot_shows_the_day_count_on_the_burning_step() {
        val data = campfireComplicationData(context, burning, ComplicationType.RANGED_VALUE, tap) as RangedValueComplicationData

        assertEquals(2f, data.value)
        assertEquals(0f, data.min)
        assertEquals(2f, data.max)
        assertEquals(listOf(0xFFA8A29C.toInt(), 0xFFFF8A3D.toInt(), 0xFFFFD166.toInt()), data.colorRamp?.colors?.toList())
        assertEquals(false, data.colorRamp?.isInterpolated)
        assertEquals("12", data.text?.read())
        assertEquals(tap, data.tapAction)
    }

    @Test
    fun a_ranged_slot_without_a_fire_is_empty_and_shows_a_dash() {
        val out = CampfireComplicationContent(iconRes = R.drawable.ic_campfire_logs, runDays = null, phase = FirePhase.OUT)

        val data = campfireComplicationData(context, out, ComplicationType.RANGED_VALUE, tap) as RangedValueComplicationData

        assertEquals(0f, data.value)
        assertEquals("–", data.text?.read())
        assertEquals("Fire went out", data.contentDescription?.read())
    }

    @Test
    fun short_and_long_slots_are_unchanged() {
        val short = campfireComplicationData(context, burning, ComplicationType.SHORT_TEXT, tap) as ShortTextComplicationData
        val long = campfireComplicationData(context, burning, ComplicationType.LONG_TEXT, tap) as LongTextComplicationData

        assertEquals("12", short.text.read())
        assertEquals(context.resources.getQuantityString(R.plurals.wear_complication_campfire_burning, 12, 12), long.text.read())
    }

    @Test
    fun the_streak_complication_offers_ranged_slots() {
        val info =
            context.packageManager.getServiceInfo(
                ComponentName(context, StreakComplicationService::class.java),
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )

        assertEquals("SHORT_TEXT,LONG_TEXT,RANGED_VALUE", info.metaData.getString("android.support.wearable.complications.SUPPORTED_TYPES"))
    }

    private fun ComplicationText.read(): String = getTextAt(context.resources, Instant.now()).toString()
}
```

- [ ] **Step 4: Run the GMD test to verify it fails**

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.CampfireComplicationDataTest
```

Expected: FAIL at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'campfireComplicationData'`.

- [ ] **Step 5: Add the ranged value and build every slot type in one place**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/complication/CampfireComplicationContent.kt` with:

```kotlin
package app.logdate.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.FirePhase
import app.logdate.wear.R

/** A ranged slot's bounds: no fire at the bottom, a burning fire at the top. */
internal const val CAMPFIRE_RANGE_MIN = 0f
internal const val CAMPFIRE_RANGE_MAX = 2f

/**
 * A ranged slot's colors, one per step from [CAMPFIRE_RANGE_MIN] to [CAMPFIRE_RANGE_MAX]: the charred
 * logs, outer flame and inner flame of the app's campfire (`app.logdate.ui.streak.Campfire`).
 */
internal val CAMPFIRE_COLOR_RAMP: IntArray = intArrayOf(0xFFA8A29C.toInt(), 0xFFFF8A3D.toInt(), 0xFFFFD166.toInt())

private const val EMBERS_VALUE = 1f
private const val NO_FIRE_TEXT = "–"

/**
 * What the streak complication shows for a campfire: an icon for the fire's phase and the day
 * count of the current fire, or no count when there is no fire.
 */
internal data class CampfireComplicationContent(
    @param:DrawableRes val iconRes: Int,
    val runDays: Int?,
    val phase: FirePhase,
) {
    /** Where the fire sits in a ranged slot; the slot's color steps with it. */
    val rangedValue: Float
        get() =
            when (phase) {
                FirePhase.BURNING -> CAMPFIRE_RANGE_MAX
                FirePhase.EMBERS -> EMBERS_VALUE
                FirePhase.UNLIT, FirePhase.OUT -> CAMPFIRE_RANGE_MIN
            }
}

internal fun CampfireState.toComplicationContent(): CampfireComplicationContent =
    CampfireComplicationContent(
        iconRes =
            when (phase) {
                FirePhase.BURNING -> R.drawable.ic_campfire_flame
                FirePhase.EMBERS -> R.drawable.ic_campfire_embers
                FirePhase.UNLIT, FirePhase.OUT -> R.drawable.ic_campfire_logs
            },
        runDays = runDays.takeIf { phase == FirePhase.BURNING || phase == FirePhase.EMBERS },
        phase = phase,
    )

/** The campfire for a slot of [type]: a ranged value, long text, or short text for any other type. */
internal fun campfireComplicationData(
    context: Context,
    content: CampfireComplicationContent,
    type: ComplicationType,
    tapAction: PendingIntent,
): ComplicationData {
    val description = PlainComplicationText.Builder(campfireDescription(context, content)).build()
    val count = PlainComplicationText.Builder(content.runDays?.toString() ?: NO_FIRE_TEXT).build()
    val icon = MonochromaticImage.Builder(Icon.createWithResource(context, content.iconRes)).build()
    return when (type) {
        ComplicationType.RANGED_VALUE ->
            RangedValueComplicationData
                .Builder(
                    value = content.rangedValue,
                    min = CAMPFIRE_RANGE_MIN,
                    max = CAMPFIRE_RANGE_MAX,
                    contentDescription = description,
                ).setText(count)
                .setMonochromaticImage(icon)
                .setColorRamp(ColorRamp(colors = CAMPFIRE_COLOR_RAMP, interpolated = false))
                .setTapAction(tapAction)
                .build()
        ComplicationType.LONG_TEXT ->
            LongTextComplicationData
                .Builder(text = description, contentDescription = description)
                .setMonochromaticImage(icon)
                .setTapAction(tapAction)
                .build()
        else ->
            ShortTextComplicationData
                .Builder(text = count, contentDescription = description)
                .setMonochromaticImage(icon)
                .setTapAction(tapAction)
                .build()
    }
}

private fun campfireDescription(
    context: Context,
    content: CampfireComplicationContent,
): String {
    val runDays = content.runDays ?: 0
    return when (content.phase) {
        FirePhase.BURNING -> context.resources.getQuantityString(R.plurals.wear_complication_campfire_burning, runDays, runDays)
        FirePhase.EMBERS -> context.getString(R.string.wear_complication_campfire_embers, runDays)
        FirePhase.OUT -> context.getString(R.string.wear_complication_campfire_out)
        FirePhase.UNLIT -> context.getString(R.string.wear_complication_campfire_unlit)
    }
}
```

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/complication/StreakComplicationService.kt` with:

```kotlin
package app.logdate.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.streak.CalculateStreakUseCase
import app.logdate.client.domain.streak.CampfireCalculator
import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.toStreakDay
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Complication showing the user's campfire, or their journaling streak with campfire streaks off.
 *
 * The campfire fills SHORT_TEXT, LONG_TEXT and RANGED_VALUE slots. The streak is a day count, with no
 * range to fill, so it fills SHORT_TEXT ("7d") and LONG_TEXT ("7 day streak") only.
 */
class StreakComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT ->
                createShortText(
                    streak = 7,
                    contentDescription = getString(R.string.wear_complication_streak_description),
                )
            ComplicationType.LONG_TEXT ->
                createLongText(
                    streak = 7,
                    contentDescription = getString(R.string.wear_complication_streak_description),
                )
            ComplicationType.RANGED_VALUE -> campfireComplicationData(this, PREVIEW_CAMPFIRE, type, createOpenAppIntent())
            else -> null
        }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (isCampfireEnabled()) {
            val campfire = calculateCampfire() ?: return NoDataComplicationData()
            return campfireComplicationData(
                context = this,
                content = campfire.toComplicationContent(),
                type = request.complicationType,
                tapAction = createOpenAppIntent(),
            )
        }
        if (request.complicationType == ComplicationType.RANGED_VALUE) return NoDataComplicationData()

        val streak = calculateStreak() ?: return NoDataComplicationData()
        val description =
            resources.getQuantityString(
                R.plurals.wear_complication_streak_full,
                streak,
                streak,
            )
        val tapIntent = createOpenAppIntent()
        return when (request.complicationType) {
            ComplicationType.LONG_TEXT -> createLongText(streak, description, tapIntent)
            else -> createShortText(streak, description, tapIntent)
        }
    }

    /**
     * The watch's Koin graph provides the data layer but not the domain use cases, so the
     * calculator is built here from the repository instead of being resolved.
     */
    private suspend fun calculateStreak(): Int? =
        try {
            val repository =
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<JournalNotesRepository>()
            CalculateStreakUseCase(repository)()
        } catch (e: Exception) {
            Napier.e("Failed to calculate streak for the Wear complication", e)
            null
        }

    private suspend fun isCampfireEnabled(): Boolean =
        try {
            org.koin.java.KoinJavaComponent
                .getKoin()
                .get<FeatureFlagStore>()
                .isEnabled(FeatureFlag.CAMPFIRE_STREAKS)
        } catch (e: Exception) {
            Napier.e("Failed to read the campfire streak flag for the Wear complication", e)
            false
        }

    /**
     * Uses the default 4 AM day start: the phone's day-start preference is not synced to the watch.
     */
    private suspend fun calculateCampfire(): CampfireState? =
        try {
            val repository =
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<JournalNotesRepository>()
            val zone = TimeZone.currentSystemDefault()
            val loggedDays = repository.observeEntryTimestamps().first().mapTo(HashSet()) { it.toStreakDay(zone) }
            CampfireCalculator.calculate(loggedDays = loggedDays, today = Clock.System.now().toStreakDay(zone))
        } catch (e: Exception) {
            Napier.e("Failed to calculate the campfire for the Wear complication", e)
            null
        }

    private fun createOpenAppIntent(): PendingIntent {
        val intent =
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent()
        return PendingIntent.getActivity(
            this,
            STREAK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createShortText(
        streak: Int,
        contentDescription: String,
        tapIntent: PendingIntent? = null,
    ): ShortTextComplicationData {
        val label = getString(R.string.wear_complication_streak_short, streak)
        val builder =
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(label).build(),
                contentDescription = PlainComplicationText.Builder(contentDescription).build(),
            )
        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }

    private fun createLongText(
        streak: Int,
        contentDescription: String,
        tapIntent: PendingIntent? = null,
    ): LongTextComplicationData {
        val label =
            resources.getQuantityString(
                R.plurals.wear_complication_streak_full,
                streak,
                streak,
            )
        val builder =
            LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(label).build(),
                contentDescription = PlainComplicationText.Builder(contentDescription).build(),
            )
        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }

    companion object {
        private const val STREAK_REQUEST_CODE = 1002
        private val PREVIEW_CAMPFIRE =
            CampfireComplicationContent(iconRes = R.drawable.ic_campfire_flame, runDays = 7, phase = FirePhase.BURNING)
    }
}
```

- [ ] **Step 6: Offer ranged slots in the manifest**

In `app/wear/src/main/AndroidManifest.xml`, inside the `.complication.StreakComplicationService` `<service>` element, change `android:value="SHORT_TEXT,LONG_TEXT"` (the `SUPPORTED_TYPES` meta-data) to:

```xml
                android:value="SHORT_TEXT,LONG_TEXT,RANGED_VALUE" />
```

and change that service's comment line `<!-- Streak complication: shows consecutive journaling day count -->` to:

```xml
        <!-- Streak complication: the campfire (also as a ranged value), or the journaling streak -->
```

- [ ] **Step 7: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug
grep -n "SHORT_TEXT,LONG_TEXT,RANGED_VALUE" app/wear/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.CampfireComplicationDataTest
```

Expected: `BUILD SUCCESSFUL`; `CampfireComplicationContentTest` runs 7 tests; the `grep` prints one line; the 4 GMD tests pass.

- [ ] **Step 8: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b8-task12-commit.txt <<'EOF'
feat(wear): show the campfire in ranged complication slots

Watch faces with a ranged slot, such as an arc or a gauge, can now
show the campfire. The slot is empty and grey while there is no fire,
half full and orange for embers, and full and yellow while it burns,
with the fire's day count inside. Short and long slots look the same
as before, and existing placements continue to work.

With campfire streaks turned off, a ranged slot shows no data, since a
plain streak count has no range to fill.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 9: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/complication/CampfireComplicationContent.kt app/wear/src/main/kotlin/app/logdate/wear/complication/StreakComplicationService.kt app/wear/src/main/AndroidManifest.xml app/wear/src/test/kotlin/app/logdate/wear/complication/CampfireComplicationContentTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/CampfireComplicationDataTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b8-task12-commit.txt
```

---

### Task 13: The Today and On this day tiles, and the On this day complication (`chore(wear)`)

Tiles move to `Material3TileService` with `protolayout-material3`, so they follow the watch's dynamic color. The Today tile reuses its component (`TodaySummaryTileService`), so a tile already in someone's carousel stays there: with the flag on it is the spec's `primaryLayout` with today's moment count, the newest moment and a Record `textEdgeButton` that opens the app with the Record request (Task 14 makes that request start a recording); with the flag off it returns the current layout unchanged. On this day is a new tile and a new `LONG_TEXT` complication, both reusing Task 5's `OnThisDayFinder`; they show how many years ago and the day's summary or first moment, and open that moment. Their manifest entries are disabled, and `SurfaceComponents` enables them only while the flag is on. Tiles are not Compose, so their layouts are checked with `protolayout-testing` on the GMD instead of screenshots.

**Files:**
- Modify: `gradle/libs.versions.toml` (one library line)
- Modify: `app/wear/build.gradle.kts` (one comment, one dependency)
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceLaunchActions.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/TodayTile.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/OnThisDayTile.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/tile/TodaySummaryTileService.kt` (whole file)
- Create: `app/wear/src/main/kotlin/app/logdate/wear/tile/OnThisDayTileService.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/complication/OnThisDayComplicationService.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponents.kt` (whole file)
- Modify: `app/wear/src/main/AndroidManifest.xml` (two services)
- Create: `app/wear/src/main/res/values/strings_surfaces.xml`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponentsTest.kt` (one test)
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/SurfaceLayoutsTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/OnThisDayComplicationTest.kt`

**Interfaces:**
- Consumes (Task 5): `OnThisDay(date, yearsAgo, leadMoment, momentCount, summary)`, `OnThisDayFinder.find(today: LocalDate): OnThisDay?` (a Koin single), the string `wear_memory_on_this_day` and plurals `wear_memory_years_ago`. (Task 8): `WearLaunchIntents.ROUTE_RECORD`, `WearLaunchIntents.ROUTE_MOMENT`, `WearLaunchIntents.moment(context, noteId): PendingIntent`. (Task 11): `SurfaceCatalog`, `SurfaceComponents`, `ComponentSwitch`, `PackageManagerComponentSwitch`. (Existing): `DayClock.current(): TodayWindow`, `TodaySummary`, `summarizeToday`, `todaySummaryTile`, `WearLaunchRequest.EXTRA_ROUTE`/`EXTRA_NOTE_ID`/`parse`.
- Produces (package `app.logdate.wear.v2.surfaces`): `object SurfaceLaunchActions { fun home(context): ActionBuilders.LaunchAction; fun record(context): ActionBuilders.LaunchAction; fun moment(context, noteId: Uuid): ActionBuilders.LaunchAction }`; `object TodayTileIds { RECORD; OPEN }`; `internal fun MaterialScope.todayTileLayout(summary: TodaySummary): LayoutElement`; `object OnThisDayTileIds { MOMENT; OPEN }`; `internal fun onThisDayPreview(context, day: OnThisDay): String`; `fun MaterialScope.onThisDayTileLayout(day: OnThisDay?): LayoutElement`; `internal suspend fun findOnThisDay(): OnThisDay?`. (Package `app.logdate.wear.tile`): `class OnThisDayTileService : Material3TileService`; `TodaySummaryTileService : Material3TileService`; `internal fun latestEntryPreview(context, note): String`. (Package `app.logdate.wear.complication`): `class OnThisDayComplicationService`; `internal fun onThisDayComplicationData(context, day: OnThisDay, tapAction: PendingIntent): ComplicationData`. `SurfaceCatalog.NEW_APP_ONLY` lists both new components.

- [ ] **Step 1: Add protolayout-testing and confirm its API**

In `gradle/libs.versions.toml`, add this line directly after the `androidx-protolayout-material3 = …` line:

```toml
androidx-protolayout-testing = { group = "androidx.wear.protolayout", name = "protolayout-testing", version.ref = "protolayout" }
```

In `app/wear/build.gradle.kts`, replace these two comment lines:

```kotlin
    // Both tiles extend horologist-tiles' SuspendingTileService and draw with protolayout-material;
    // both go when the tiles move to Material3TileService.
```

with:

```kotlin
    // Tiles extend Material3TileService and draw with protolayout-material3. The Quick Capture tile
    // (horologist-tiles) and the Today tile's current-app layout (protolayout-material) go with the
    // current watch app.
```

and add this line directly after `androidTestImplementation(libs.androidx.work.testing)`:

```kotlin
    androidTestImplementation(libs.androidx.protolayout.testing)
```

Resolve it, then read the matchers from the resolved AAR:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:dependencies --configuration debugAndroidTestRuntimeClasspath | grep "protolayout-testing"
D=/private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/protolayout-testing && mkdir -p $D && unzip -o -q "$(find $HOME/.gradle/caches/modules-2/files-2.1/androidx.wear.protolayout/protolayout-testing/1.4.2 -name '*.aar' | head -1)" classes.jar -d $D && javap -cp $D/classes.jar $(unzip -Z1 $D/classes.jar | grep '\.class$' | grep -v '\$' | sed 's/\.class$//; s#/#.#g') | grep -E "hasText\(|hasClickable\(|onElement|assertExists|LayoutElementAssertionsProvider\("
```

Expected: a `androidx.wear.protolayout:protolayout-testing:1.4.2` line, then lines containing `LayoutElementAssertionsProvider(androidx.wear.protolayout.LayoutElementBuilders$LayoutElement)`, `onElement(`, `assertExists()`, `hasText(java.lang.String, boolean, boolean)` and `hasClickable(androidx.wear.protolayout.ActionBuilders$Action, java.lang.String)`. If a name differs, use the resolved one in Step 3; the tests' assertions stay the same.

- [ ] **Step 2: Write the failing unit test**

In `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponentsTest.kt`, add this test after `the spec retires Entry Count, Mood and the Quick Capture tile`:

```kotlin
    @Test
    fun `the new app offers the On this day tile and complication`() {
        assertEquals(
            listOf(
                "app.logdate.wear.tile.OnThisDayTileService",
                "app.logdate.wear.complication.OnThisDayComplicationService",
            ),
            SurfaceCatalog.NEW_APP_ONLY,
        )
    }
```

- [ ] **Step 3: Write the failing GMD tests**

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/SurfaceLayoutsTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.testing.LayoutElementAssertionsProvider
import androidx.wear.protolayout.testing.hasClickable
import androidx.wear.protolayout.testing.hasText
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.tile.TodaySummary
import app.logdate.wear.v2.memories.OnThisDay
import app.logdate.wear.v2.surfaces.OnThisDayTileIds
import app.logdate.wear.v2.surfaces.SurfaceLaunchActions
import app.logdate.wear.v2.surfaces.TodayTileIds
import app.logdate.wear.v2.surfaces.onThisDayTileLayout
import app.logdate.wear.v2.surfaces.todayTileLayout
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Checks the new tiles' layouts with protolayout-testing; tiles are not Compose, so they have no screenshots. */
@RunWith(AndroidJUnit4::class)
class SurfaceLayoutsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val note = JournalNote.Text(creationTimestamp = NOON, lastUpdated = NOON, content = "Breakfast by the window")
    private val device =
        DeviceParametersBuilders.DeviceParameters
            .Builder()
            .setScreenWidthDp(192)
            .setScreenHeightDp(192)
            .setScreenDensity(2f)
            .setFontScale(1f)
            .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
            .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
            .build()

    @Test
    fun today_counts_the_moments_and_shows_the_newest() {
        val layout = layout { todayTileLayout(TodaySummary(entryCount = 3, feeling = null, latestEntry = note)) }

        layout.onElement(hasText("Today")).assertExists()
        layout.onElement(hasText("3 moments today")).assertExists()
        layout.onElement(hasText("Breakfast by the window")).assertExists()
    }

    @Test
    fun today_records_from_its_edge_button_even_when_empty() {
        val layout = layout { todayTileLayout(TodaySummary(entryCount = 0, feeling = null, latestEntry = null)) }

        layout.onElement(hasText("Nothing recorded yet today")).assertExists()
        layout.onElement(hasText("Record")).assertExists()
        layout.onElement(hasClickable(SurfaceLaunchActions.record(context), TodayTileIds.RECORD)).assertExists()
    }

    @Test
    fun the_tile_actions_open_the_recorder_a_moment_and_home() {
        assertEquals(WearLaunchRequest.Record, SurfaceLaunchActions.record(context).request())
        assertEquals(WearLaunchRequest.Moment(note.uid), SurfaceLaunchActions.moment(context, note.uid).request())
        assertNull(SurfaceLaunchActions.home(context).request())
    }

    @Test
    fun on_this_day_shows_how_long_ago_and_opens_its_moment() {
        val day = OnThisDay(date = LocalDate(2025, 10, 10), yearsAgo = 1, leadMoment = note, momentCount = 2, summary = "")

        val layout = layout { onThisDayTileLayout(day) }

        layout.onElement(hasText("On this day")).assertExists()
        layout.onElement(hasText("1 year ago")).assertExists()
        layout.onElement(hasText("Breakfast by the window")).assertExists()
        layout.onElement(hasClickable(SurfaceLaunchActions.moment(context, note.uid), OnThisDayTileIds.MOMENT)).assertExists()
    }

    @Test
    fun on_this_day_prefers_the_recalled_summary() {
        val day = OnThisDay(date = LocalDate(2023, 10, 10), yearsAgo = 3, leadMoment = note, momentCount = 4, summary = "Moving day")

        val layout = layout { onThisDayTileLayout(day) }

        layout.onElement(hasText("3 years ago")).assertExists()
        layout.onElement(hasText("Moving day")).assertExists()
        layout.onElement(hasText("Breakfast by the window")).assertDoesNotExist()
    }

    @Test
    fun without_a_day_on_this_day_says_so_and_opens_the_app() {
        val layout = layout { onThisDayTileLayout(null) }

        layout.onElement(hasText("Nothing from this day in past years")).assertExists()
        layout.onElement(hasClickable(SurfaceLaunchActions.home(context), OnThisDayTileIds.OPEN)).assertExists()
    }

    private fun layout(content: MaterialScope.() -> LayoutElementBuilders.LayoutElement): LayoutElementAssertionsProvider =
        LayoutElementAssertionsProvider(
            materialScope(context = context, deviceConfiguration = device, allowDynamicTheme = false, layout = content),
        )

    private fun ActionBuilders.LaunchAction.request(): WearLaunchRequest? {
        val extras = requireNotNull(androidActivity).keyToExtraMapping
        return WearLaunchRequest.parse(
            route = (extras[WearLaunchRequest.EXTRA_ROUTE] as? ActionBuilders.AndroidStringExtra)?.value,
            noteId = (extras[WearLaunchRequest.EXTRA_NOTE_ID] as? ActionBuilders.AndroidStringExtra)?.value,
        )
    }

    private companion object {
        val NOON: Instant = Instant.fromEpochMilliseconds(1_760_097_600_000)
    }
}
```

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/OnThisDayComplicationTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.LongTextComplicationData
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.complication.OnThisDayComplicationService
import app.logdate.wear.complication.onThisDayComplicationData
import app.logdate.wear.launch.WearLaunchIntents
import app.logdate.wear.v2.memories.OnThisDay
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Builds the On this day complication's data on a Wear OS emulator, where complication text can be read back. */
@RunWith(AndroidJUnit4::class)
class OnThisDayComplicationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val note = JournalNote.Text(creationTimestamp = NOON, lastUpdated = NOON, content = "Breakfast by the window")
    private val day = OnThisDay(date = LocalDate(2024, 10, 10), yearsAgo = 2, leadMoment = note, momentCount = 1, summary = "")

    @Test
    fun the_slot_shows_how_long_ago_and_opens_the_moment() {
        val tap = WearLaunchIntents.moment(context, note.uid)

        val data = onThisDayComplicationData(context, day, tap) as LongTextComplicationData

        assertEquals("2 years ago", data.title?.read())
        assertEquals("Breakfast by the window", data.text.read())
        assertEquals("On this day, 2 years ago: Breakfast by the window", data.contentDescription?.read())
        assertEquals(tap, data.tapAction)
    }

    @Test
    fun the_complication_is_off_until_the_new_app_turns_it_on() {
        val info =
            context.packageManager.getServiceInfo(
                ComponentName(context, OnThisDayComplicationService::class.java),
                PackageManager.ComponentInfoFlags.of((PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()),
            )

        assertEquals(false, info.enabled)
        assertEquals("LONG_TEXT", info.metaData.getString("android.support.wearable.complications.SUPPORTED_TYPES"))
    }

    private fun ComplicationText.read(): String = getTextAt(context.resources, java.time.Instant.now()).toString()

    private companion object {
        val NOON: Instant = Instant.fromEpochMilliseconds(1_760_097_600_000)
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.surfaces.SurfaceComponentsTest'
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.SurfaceLayoutsTest,app.logdate.wear.e2e.OnThisDayComplicationTest
```

Expected: the unit test FAILS with `expected:<[app.logdate.wear.tile.OnThisDayTileService, app.logdate.wear.complication.OnThisDayComplicationService]> but was:<[]>`; the GMD run FAILS at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'todayTileLayout'`, `Unresolved reference 'SurfaceLaunchActions'` and `Unresolved reference 'onThisDayComplicationData'` (and no unresolved `androidx.wear.protolayout.testing` names).

- [ ] **Step 5: Add the copy**

Create `app/wear/src/main/res/values/strings_surfaces.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Tiles and complications of the new watch app -->
    <string name="wear_surface_today_title">Today</string>
    <string name="wear_surface_today_empty">Nothing recorded yet today</string>
    <plurals name="wear_surface_today_moments">
        <item quantity="one">%1$d moment today</item>
        <item quantity="other">%1$d moments today</item>
    </plurals>
    <string name="wear_surface_record">Record</string>
    <string name="wear_surface_on_this_day_empty">Nothing from this day in past years</string>
    <!-- Read aloud for the On this day complication: how long ago, then the day's summary or first moment -->
    <string name="wear_surface_on_this_day_description">On this day, %1$s: %2$s</string>
</resources>
```

- [ ] **Step 6: Add the tile layouts and their actions**

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceLaunchActions.kt`:

```kotlin
package app.logdate.wear.v2.surfaces

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import app.logdate.wear.launch.WearLaunchIntents
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import kotlin.uuid.Uuid

/** Tile actions that open the app on a route, with the extras [WearLaunchIntents] uses for notifications and complications. */
object SurfaceLaunchActions {
    fun home(context: Context): ActionBuilders.LaunchAction = ActionBuilders.launchAction(mainActivity(context))

    fun record(context: Context): ActionBuilders.LaunchAction =
        ActionBuilders.launchAction(
            mainActivity(context),
            mapOf(WearLaunchRequest.EXTRA_ROUTE to ActionBuilders.stringExtra(WearLaunchIntents.ROUTE_RECORD)),
        )

    /** Opens the moment saved as the note [noteId]. */
    fun moment(
        context: Context,
        noteId: Uuid,
    ): ActionBuilders.LaunchAction =
        ActionBuilders.launchAction(
            mainActivity(context),
            mapOf(
                WearLaunchRequest.EXTRA_ROUTE to ActionBuilders.stringExtra(WearLaunchIntents.ROUTE_MOMENT),
                WearLaunchRequest.EXTRA_NOTE_ID to ActionBuilders.stringExtra(noteId.toString()),
            ),
        )

    private fun mainActivity(context: Context): ComponentName = ComponentName(context, MainActivity::class.java)
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/TodayTile.kt`:

```kotlin
package app.logdate.wear.v2.surfaces

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import app.logdate.wear.R
import app.logdate.wear.tile.TodaySummary
import app.logdate.wear.tile.latestEntryPreview

/** Clickable IDs on the Today tile. */
object TodayTileIds {
    const val RECORD = "record"
    const val OPEN = "open_today"
}

private const val COUNT_MAX_LINES = 2
private const val LATEST_MAX_LINES = 2

/** The new watch app's Today tile: how many moments today has, the newest one, and Record along the bottom edge. */
internal fun MaterialScope.todayTileLayout(summary: TodaySummary): LayoutElementBuilders.LayoutElement =
    primaryLayout(
        titleSlot = { text(context.getString(R.string.wear_surface_today_title).layoutString) },
        mainSlot = { todayContent(summary) },
        bottomSlot = {
            textEdgeButton(
                onClick = clickable(SurfaceLaunchActions.record(context), TodayTileIds.RECORD),
                labelContent = { text(context.getString(R.string.wear_surface_record).layoutString) },
            )
        },
        onClick = clickable(SurfaceLaunchActions.home(context), TodayTileIds.OPEN),
    )

private fun MaterialScope.todayContent(summary: TodaySummary): LayoutElementBuilders.LayoutElement {
    val count =
        if (summary.entryCount == 0) {
            context.getString(R.string.wear_surface_today_empty)
        } else {
            context.resources.getQuantityString(R.plurals.wear_surface_today_moments, summary.entryCount, summary.entryCount)
        }
    val latest =
        summary.latestEntry
            ?: return text(count.layoutString, typography = Typography.TITLE_MEDIUM, maxLines = COUNT_MAX_LINES)
    return LayoutElementBuilders.Column
        .Builder()
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .addContent(text(count.layoutString, typography = Typography.TITLE_MEDIUM))
        .addContent(text(latestEntryPreview(context, latest).layoutString, typography = Typography.BODY_SMALL, maxLines = LATEST_MAX_LINES))
        .build()
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/OnThisDayTile.kt`:

```kotlin
package app.logdate.wear.v2.surfaces

import android.content.Context
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import app.logdate.wear.R
import app.logdate.wear.tile.latestEntryPreview
import app.logdate.wear.v2.memories.OnThisDay
import app.logdate.wear.v2.memories.OnThisDayFinder
import app.logdate.wear.v2.today.DayClock
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** Clickable IDs on the On this day tile. */
object OnThisDayTileIds {
    const val MOMENT = "on_this_day_moment"
    const val OPEN = "open_home"
}

private const val EMPTY_MAX_LINES = 3
private const val PREVIEW_MAX_LINES = 2

/** The line On this day shows: the recalled day's summary, or its first moment when the summary is blank. */
internal fun onThisDayPreview(
    context: Context,
    day: OnThisDay,
): String = day.summary.ifBlank { latestEntryPreview(context, day.leadMoment) }

/** On this day for today's date, or null when there is none or it can't be read. */
internal suspend fun findOnThisDay(): OnThisDay? =
    try {
        val koin =
            org.koin.java.KoinJavaComponent
                .getKoin()
        koin.get<OnThisDayFinder>().find(koin.get<DayClock>().current().date)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Napier.e("Couldn't find On this day for a tile or complication", e)
        null
    }

/** The On this day tile: how long ago and the day's first moment, which it opens; or a line saying there is none. */
fun MaterialScope.onThisDayTileLayout(day: OnThisDay?): LayoutElementBuilders.LayoutElement {
    val label = context.getString(R.string.wear_memory_on_this_day)
    if (day == null) {
        return primaryLayout(
            titleSlot = { text(label.layoutString) },
            mainSlot = { text(context.getString(R.string.wear_surface_on_this_day_empty).layoutString, maxLines = EMPTY_MAX_LINES) },
            onClick = clickable(SurfaceLaunchActions.home(context), OnThisDayTileIds.OPEN),
        )
    }
    val yearsAgo = context.resources.getQuantityString(R.plurals.wear_memory_years_ago, day.yearsAgo, day.yearsAgo)
    return primaryLayout(
        titleSlot = { text(label.layoutString) },
        mainSlot = {
            titleCard(
                onClick = clickable(SurfaceLaunchActions.moment(context, day.leadMoment.uid), OnThisDayTileIds.MOMENT),
                title = { text(yearsAgo.layoutString) },
                content = { text(onThisDayPreview(context, day).layoutString, maxLines = PREVIEW_MAX_LINES) },
            )
        },
    )
}
```

- [ ] **Step 7: Move the Today tile to `Material3TileService` and add the On this day tile and complication**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/tile/TodaySummaryTileService.kt` with:

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
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tile
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.MomentNoteCodec
import app.logdate.wear.capture.MomentNoteKind
import app.logdate.wear.v2.surfaces.todayTileLayout
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val RESOURCES_VERSION = "1"
private const val MAX_PREVIEW_LENGTH = 30
private const val FRESHNESS_MINUTES = 15

/**
 * The Today tile. With the new watch app on it is the Material 3 Today tile ([todayTileLayout]);
 * otherwise it is the current app's summary: entry count, current mood and latest entry, where
 * tapping opens the timeline.
 */
class TodaySummaryTileService : Material3TileService() {
    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val summary = fetchTodaySummary()
        if (!isNewAppEnabled()) return todaySummaryTile(requestParams, this@TodaySummaryTileService, summary)
        return tile(
            timeline = TimelineBuilders.Timeline.fromLayoutElement(todayTileLayout(summary)),
            freshness = FRESHNESS_MINUTES.minutes,
        )
    }

    private suspend fun isNewAppEnabled(): Boolean =
        try {
            org.koin.java.KoinJavaComponent
                .getKoin()
                .get<FeatureFlagStore>()
                .isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't read the new watch app's flag for the Today tile", e)
            false
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
        } catch (e: CancellationException) {
            throw e
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
        .setFreshnessIntervalMillis(FRESHNESS_MINUTES.minutes.inWholeMilliseconds)
        .build()
}

/** A short line for [note]: the start of its text, or what kind of moment it is. */
internal fun latestEntryPreview(
    context: Context,
    note: JournalNote,
): String =
    when (note) {
        is JournalNote.Text -> note.content.take(MAX_PREVIEW_LENGTH)
        is JournalNote.Audio -> context.getString(R.string.wear_tile_summary_voice_note)
        is JournalNote.Image -> context.getString(R.string.wear_tile_summary_photo)
        is JournalNote.Video -> context.getString(R.string.wear_tile_summary_video)
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

Create `app/wear/src/main/kotlin/app/logdate/wear/tile/OnThisDayTileService.kt`:

```kotlin
package app.logdate.wear.tile

import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tile
import app.logdate.wear.v2.surfaces.findOnThisDay
import app.logdate.wear.v2.surfaces.onThisDayTileLayout
import kotlin.time.Duration.Companion.hours

/** The On this day tile. Its manifest entry is disabled; `SurfaceComponents` offers it while the new watch app is on. */
class OnThisDayTileService : Material3TileService() {
    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile =
        tile(
            timeline = TimelineBuilders.Timeline.fromLayoutElement(onThisDayTileLayout(findOnThisDay())),
            freshness = 1.hours,
        )
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/complication/OnThisDayComplicationService.kt`:

```kotlin
package app.logdate.wear.complication

import android.app.PendingIntent
import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.logdate.wear.R
import app.logdate.wear.launch.WearLaunchIntents
import app.logdate.wear.v2.memories.OnThisDay
import app.logdate.wear.v2.surfaces.findOnThisDay
import app.logdate.wear.v2.surfaces.onThisDayPreview

/**
 * On this day on a watch face: how long ago, and the day's summary or first moment, which a tap
 * opens. Its manifest entry is disabled; `SurfaceComponents` offers it while the new watch app is on.
 */
class OnThisDayComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.LONG_TEXT) return null
        val label = PlainComplicationText.Builder(getString(R.string.wear_memory_on_this_day)).build()
        return LongTextComplicationData
            .Builder(text = label, contentDescription = label)
            .setTitle(PlainComplicationText.Builder(resources.getQuantityString(R.plurals.wear_memory_years_ago, 1, 1)).build())
            .build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val day = findOnThisDay() ?: return NoDataComplicationData()
        return onThisDayComplicationData(this, day, WearLaunchIntents.moment(this, day.leadMoment.uid))
    }
}

/** On this day as long text: how long ago as the title, the day's summary or first moment as the text. */
internal fun onThisDayComplicationData(
    context: Context,
    day: OnThisDay,
    tapAction: PendingIntent,
): ComplicationData {
    val yearsAgo = context.resources.getQuantityString(R.plurals.wear_memory_years_ago, day.yearsAgo, day.yearsAgo)
    val preview = onThisDayPreview(context, day)
    val description = context.getString(R.string.wear_surface_on_this_day_description, yearsAgo, preview)
    return LongTextComplicationData
        .Builder(
            text = PlainComplicationText.Builder(preview).build(),
            contentDescription = PlainComplicationText.Builder(description).build(),
        ).setTitle(PlainComplicationText.Builder(yearsAgo).build())
        .setTapAction(tapAction)
        .build()
}
```

- [ ] **Step 8: Offer the new surfaces while the new app is on**

Replace the whole of `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponents.kt` with:

```kotlin
package app.logdate.wear.v2.surfaces

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import app.logdate.wear.complication.OnThisDayComplicationService
import app.logdate.wear.complication.StreakComplicationService
import app.logdate.wear.tile.OnThisDayTileService
import app.logdate.wear.tile.TodaySummaryTileService

/**
 * The watch's tiles and complications. Their class names never change once shipped, because watch
 * faces and the tile carousel refer to them by name.
 */
object SurfaceCatalog {
    /** Tiles to redraw when moments change. */
    val TILES: List<Class<out TileService>> =
        listOf(
            TodaySummaryTileService::class.java,
            OnThisDayTileService::class.java,
        )

    /** Complications to redraw when moments change. */
    val COMPLICATIONS: List<Class<out ComplicationDataSourceService>> =
        listOf(
            StreakComplicationService::class.java,
            OnThisDayComplicationService::class.java,
        )

    /** Surfaces only the new watch app offers; off while it is off. */
    val NEW_APP_ONLY: List<String> =
        listOf(
            "app.logdate.wear.tile.OnThisDayTileService",
            "app.logdate.wear.complication.OnThisDayComplicationService",
        )

    /** Surfaces the new watch app retires; off while it is on. */
    val CURRENT_APP_ONLY: List<String> =
        listOf(
            "app.logdate.wear.complication.EntryCountComplicationService",
            "app.logdate.wear.complication.MoodComplicationService",
            "app.logdate.wear.tile.QuickCaptureTileService",
        )
}

/** Turns one of the app's own components on or off. */
fun interface ComponentSwitch {
    fun setEnabled(
        className: String,
        enabled: Boolean,
    )
}

/** [ComponentSwitch] through the package manager, without restarting the app. */
class PackageManagerComponentSwitch(
    private val context: Context,
) : ComponentSwitch {
    override fun setEnabled(
        className: String,
        enabled: Boolean,
    ) {
        val component = ComponentName(context.packageName, className)
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (context.packageManager.getComponentEnabledSetting(component) == state) return
        context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}

/** Offers the new watch app's surfaces and retires the ones it replaces while it is on, and the reverse while it is off. */
class SurfaceComponents(
    private val switch: ComponentSwitch,
    private val newAppOnly: List<String> = SurfaceCatalog.NEW_APP_ONLY,
    private val currentAppOnly: List<String> = SurfaceCatalog.CURRENT_APP_ONLY,
) {
    fun apply(newAppOn: Boolean) {
        newAppOnly.forEach { switch.setEnabled(it, newAppOn) }
        currentAppOnly.forEach { switch.setEnabled(it, !newAppOn) }
    }
}
```

In `app/wear/src/main/AndroidManifest.xml`, insert these two services directly before the comment that begins `Set to true if your app is Standalone` (the `<!--` line above it), leaving whatever the recorder plan added elsewhere in place:

```xml
        <!-- On this day tile: only while the new watch app is on (SurfaceComponents enables it) -->
        <service
            android:name=".tile.OnThisDayTileService"
            android:enabled="false"
            android:exported="true"
            android:label="@string/wear_memory_on_this_day"
            android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
            <intent-filter>
                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
            </intent-filter>

            <meta-data
                android:name="androidx.wear.tiles.PREVIEW"
                android:resource="@drawable/tile_preview" />
        </service>

        <!-- On this day complication: only while the new watch app is on (SurfaceComponents enables it) -->
        <service
            android:name=".complication.OnThisDayComplicationService"
            android:enabled="false"
            android:exported="true"
            android:label="@string/wear_memory_on_this_day"
            android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER">
            <intent-filter>
                <action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST" />
            </intent-filter>

            <meta-data
                android:name="android.support.wearable.complications.SUPPORTED_TYPES"
                android:value="LONG_TEXT" />
            <meta-data
                android:name="android.support.wearable.complications.UPDATE_PERIOD_SECONDS"
                android:value="3600" />
        </service>

```

The hourly update period shows a new day's On this day within the hour even when the app's process is not running at midnight; while it runs, `SurfaceCoordinator` refreshes it at the day change.

- [ ] **Step 9: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:lintDebug :app:wear:assembleDebug
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.SurfaceLayoutsTest,app.logdate.wear.e2e.OnThisDayComplicationTest,app.logdate.wear.e2e.SurfaceComponentsTest,app.logdate.wear.e2e.ShellNavigationTest
```

Expected: `BUILD SUCCESSFUL` twice; `lintDebug` reports no new error; `SurfaceComponentsTest` (unit) runs 4 tests; `TodaySummaryTest` still passes unchanged; the GMD run passes 6 + 2 + 3 tests plus `ShellNavigationTest`. `SurfaceComponentsTest` on the GMD now also checks that both new class names resolve and switch.

- [ ] **Step 10: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b8-task13-commit.txt <<'EOF'
chore(wear): add the Today and On this day tiles and complication

With the new watch app on, the Today tile follows the watch's dynamic
colors and shows today's moment count, the newest moment, and a
Record button along the bottom edge that opens the app to record. A
new On this day tile and complication show how many years ago the
day was and its summary or first moment, and open that moment when
tapped. They are offered only while the new app is on.

With the new app off, the Today tile looks the same as before.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add gradle/libs.versions.toml app/wear/build.gradle.kts app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceLaunchActions.kt app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/TodayTile.kt app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/OnThisDayTile.kt app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponents.kt app/wear/src/main/kotlin/app/logdate/wear/tile/TodaySummaryTileService.kt app/wear/src/main/kotlin/app/logdate/wear/tile/OnThisDayTileService.kt app/wear/src/main/kotlin/app/logdate/wear/complication/OnThisDayComplicationService.kt app/wear/src/main/AndroidManifest.xml app/wear/src/main/res/values/strings_surfaces.xml app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponentsTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/SurfaceLayoutsTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/OnThisDayComplicationTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b8-task13-commit.txt
```

---

### Task 14: Mark and Record from the watch face (`chore(wear)`)

The spec gives two surfaces that act from outside a screen. **Mark** saves the moment and confirms with a haptic, without opening the app. The recorder and capture plan (B4) owns marking from outside the app: its `MarkMomentReceiver` (package `app.logdate.wear.v2.mark`, not exported) marks through `BackgroundMarker`, which saves a mark with only its time (a background app can read neither the place nor the heart rate; the phone adds the place later), confirms with the `momentMarked` haptic, and does nothing while the flag is off. This task adds the complication that fires `MarkMomentReceiver.pendingIntent(context)`; its manifest entry is disabled and `SurfaceComponents` offers it while the flag is on. The mark is queued for upload, so the outbox grows and Task 11's coordinator refreshes the other surfaces at once. **Record** opens the app and starts recording, since a microphone foreground service cannot start from the background. The recorder plan leaves `WearLaunchRequest.Record` opening Today (its "Not covered" list hands this to B8), so this task makes the new app start a recording that goes on until Stop, the same as a tap on Record, whenever it is launched with the Record request: from the existing Record complication (`voice_note`), the Today tile's Record button (Task 13) and a reminder's Record action (Task 8). A recreated activity does not start a second recording. If the recorder plan is not on the branch (Task 0, Step 2), postpone this task until it is.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/complication/MarkComplicationService.kt`
- Modify: `app/wear/src/main/res/values/strings_surfaces.xml` (two strings)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponents.kt` (the `NEW_APP_ONLY` property)
- Modify: `app/wear/src/main/AndroidManifest.xml` (one service)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt` (imports, the launch request effect, one private function)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponentsTest.kt` (one test replaced)
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MarkComplicationTest.kt`
- Test (GMD): `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/RecordLaunchTest.kt`

**Interfaces:**
- Consumes (recorder and capture plan): `app.logdate.wear.v2.mark.MarkMomentReceiver.pendingIntent(context: Context): PendingIntent`; `RecorderViewModel` (activity-scoped in `WearAppV2`, a `RecordGestureListener`) with `state: StateFlow<RecorderState>`; `val RecorderState.canStartRecording: Boolean` (`app.logdate.wear.v2.recorder`); `fun RecordGestureListener.startLocked()` (`app.logdate.wear.v2.recorder.gesture`); `RecorderTags.DISCARD`, `RecorderTags.ELAPSED`. (Task 8): `WearLaunchIntents.ROUTE_RECORD`. (Tasks 11, 13): `SurfaceCatalog`, `SurfaceComponents`. (Existing): `R.drawable.ic_glyph_mark`, `WearRecorder.isRecording`/`discard()` (bound in `AudioModule`), `WearLaunchRequest.EXTRA_ROUTE`, `TodayTags.PAGE`.
- Produces (package `app.logdate.wear.complication`): `class MarkComplicationService : SuspendingComplicationDataSourceService`; `internal fun markComplicationData(context: Context, type: ComplicationType, tapAction: PendingIntent): ComplicationData?` (null for a type it does not fill). `SurfaceCatalog.NEW_APP_ONLY` gains `app.logdate.wear.complication.MarkComplicationService`. `WearAppV2` starts a recording for `WearLaunchRequest.Record`, once per request.

- [ ] **Step 1: Confirm the recorder plan's entry points**

```bash
git grep -n "fun pendingIntent(context: Context): PendingIntent" -- app/wear/src/main/kotlin/app/logdate/wear/v2/mark
git grep -n "fun RecordGestureListener.startLocked\|val RecorderState.canStartRecording\|const val DISCARD\|const val ELAPSED" -- app/wear/src/main/kotlin/app/logdate/wear/v2/recorder
grep -n "val recorder = koinViewModel<RecorderViewModel>()\|LaunchedEffect(newRequests)" app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt
```

Expected: one line in `MarkMomentReceiver.kt`; four lines from the recorder package; two lines from `WearAppV2.kt`. If a name differs, use the landed name in Steps 2 and 4; nothing else changes.

- [ ] **Step 2: Write the failing tests**

In `app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponentsTest.kt`, replace the test `the new app offers the On this day tile and complication` with:

```kotlin
    @Test
    fun `the new app offers On this day and Mark`() {
        assertEquals(
            listOf(
                "app.logdate.wear.tile.OnThisDayTileService",
                "app.logdate.wear.complication.OnThisDayComplicationService",
                "app.logdate.wear.complication.MarkComplicationService",
            ),
            SurfaceCatalog.NEW_APP_ONLY,
        )
    }
```

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MarkComplicationTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.logdate.wear.complication.MarkComplicationService
import app.logdate.wear.complication.markComplicationData
import app.logdate.wear.v2.mark.MarkMomentReceiver
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Builds the Mark complication's data on a Wear OS emulator, where complication text can be read back. */
@RunWith(AndroidJUnit4::class)
class MarkComplicationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mark = MarkMomentReceiver.pendingIntent(context)

    @Test
    fun every_offered_slot_marks_through_the_receiver() {
        listOf(ComplicationType.SHORT_TEXT, ComplicationType.MONOCHROMATIC_IMAGE, ComplicationType.SMALL_IMAGE).forEach { type ->
            val data = requireNotNull(markComplicationData(context, type, mark)) { "No data for $type" }

            assertEquals(type, data.type)
            assertEquals(mark, data.tapAction)
        }
    }

    @Test
    fun the_short_slot_says_mark() {
        val data = markComplicationData(context, ComplicationType.SHORT_TEXT, mark) as ShortTextComplicationData

        assertEquals("Mark", data.text.read())
        assertEquals("Mark this moment", data.contentDescription?.read())
    }

    @Test
    fun a_slot_it_does_not_fill_gets_nothing() {
        assertNull(markComplicationData(context, ComplicationType.LONG_TEXT, mark))
    }

    @Test
    fun the_manifest_offers_those_slots_and_starts_off() {
        val info =
            context.packageManager.getServiceInfo(
                ComponentName(context, MarkComplicationService::class.java),
                PackageManager.ComponentInfoFlags.of((PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()),
            )

        assertEquals(false, info.enabled)
        assertEquals(
            "SHORT_TEXT,MONOCHROMATIC_IMAGE,SMALL_IMAGE",
            info.metaData.getString("android.support.wearable.complications.SUPPORTED_TYPES"),
        )
    }

    private fun ComplicationText.read(): String = getTextAt(context.resources, Instant.now()).toString()
}
```

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/RecordLaunchTest.kt`:

```kotlin
package app.logdate.wear.e2e

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.wear.launch.WearLaunchIntents
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.recording.WearRecorder
import app.logdate.wear.v2.recorder.RecorderTags
import app.logdate.wear.v2.today.TodayTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertFalse

/**
 * Launches the new watch app with the Record request, the way the Record complication, the Today
 * tile and a reminder's Record action do, on a Wear OS emulator with the microphone allowed.
 */
@RunWith(AndroidJUnit4::class)
class RecordLaunchTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val recorder: WearRecorder get() = KoinJavaComponent.getKoin().get()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS).forEach { permission ->
            automation.grantRuntimePermission(context.packageName, permission)
        }
        setNewWatchApp(enabled = true)
    }

    @After
    fun tearDown() {
        if (recorder.isRecording.value) runBlocking { recorder.discard() }
        // onNewIntent replaces the activity's intent, so ActivityScenario.close() could wait forever.
        scenario?.onActivity { it.finish() }
        setNewWatchApp(enabled = false)
    }

    @Test
    fun a_record_launch_starts_a_recording() {
        scenario = ActivityScenario.launch(recordIntent())

        composeRule.waitUntil(TIMEOUT_MS) { recorder.isRecording.value }
        waitFor { composeRule.onAllNodesWithTag(RecorderTags.ELAPSED) }
    }

    @Test
    fun a_record_request_while_open_starts_a_recording() {
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }

        launched.onActivity { activity -> activity.startActivity(recordIntent()) }

        composeRule.waitUntil(TIMEOUT_MS) { recorder.isRecording.value }
    }

    @Test
    fun a_recreated_activity_does_not_record_again() {
        val launched = ActivityScenario.launch<MainActivity>(recordIntent()).also { scenario = it }
        composeRule.waitUntil(TIMEOUT_MS) { recorder.isRecording.value }
        waitFor { composeRule.onAllNodesWithTag(RecorderTags.DISCARD) }
        // Discard asks once, then discards on the second tap.
        repeat(2) { composeRule.onNodeWithTag(RecorderTags.DISCARD).performClick() }
        composeRule.waitUntil(TIMEOUT_MS) { !recorder.isRecording.value }

        launched.recreate()
        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }
        SystemClock.sleep(SETTLE_MS)

        assertFalse(recorder.isRecording.value)
    }

    private fun recordIntent(): Intent =
        Intent(context, MainActivity::class.java).putExtra(WearLaunchRequest.EXTRA_ROUTE, WearLaunchIntents.ROUTE_RECORD)

    private fun setNewWatchApp(enabled: Boolean) =
        runBlocking {
            KoinJavaComponent.getKoin().get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        }

    private fun waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        composeRule.waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SETTLE_MS = 2_000L
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.v2.surfaces.SurfaceComponentsTest'
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.MarkComplicationTest
```

Expected: the unit test FAILS with `expected:<[…, app.logdate.wear.complication.MarkComplicationService]> but was:<[app.logdate.wear.tile.OnThisDayTileService, app.logdate.wear.complication.OnThisDayComplicationService]>`; the GMD run FAILS at `:app:wear:compileDebugAndroidTestKotlin` with `Unresolved reference 'MarkComplicationService'` and `Unresolved reference 'markComplicationData'`. `RecordLaunchTest` uses only existing code; it runs at the end of Step 4, once the test source set compiles again.

- [ ] **Step 4: Add the Mark complication**

Create `app/wear/src/main/kotlin/app/logdate/wear/complication/MarkComplicationService.kt`:

```kotlin
package app.logdate.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.logdate.wear.R
import app.logdate.wear.v2.mark.MarkMomentReceiver

/**
 * Marks the moment from a watch face, with a haptic and without opening the app, through
 * [MarkMomentReceiver]. Its manifest entry is disabled; `SurfaceComponents` offers it while the new
 * watch app is on.
 */
class MarkComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        markComplicationData(this, type, MarkMomentReceiver.pendingIntent(this))

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData =
        markComplicationData(this, request.complicationType, MarkMomentReceiver.pendingIntent(this)) ?: NoDataComplicationData()
}

/** The Mark glyph for a slot of [type], firing [tapAction]; null for a type this complication does not fill. */
internal fun markComplicationData(
    context: Context,
    type: ComplicationType,
    tapAction: PendingIntent,
): ComplicationData? {
    val description = PlainComplicationText.Builder(context.getString(R.string.wear_surface_mark_description)).build()
    val glyph = Icon.createWithResource(context, R.drawable.ic_glyph_mark)
    return when (type) {
        ComplicationType.SHORT_TEXT ->
            ShortTextComplicationData
                .Builder(
                    text = PlainComplicationText.Builder(context.getString(R.string.wear_surface_mark)).build(),
                    contentDescription = description,
                ).setMonochromaticImage(MonochromaticImage.Builder(glyph).build())
                .setTapAction(tapAction)
                .build()
        ComplicationType.MONOCHROMATIC_IMAGE ->
            MonochromaticImageComplicationData
                .Builder(monochromaticImage = MonochromaticImage.Builder(glyph).build(), contentDescription = description)
                .setTapAction(tapAction)
                .build()
        ComplicationType.SMALL_IMAGE ->
            SmallImageComplicationData
                .Builder(smallImage = SmallImage.Builder(glyph, SmallImageType.ICON).build(), contentDescription = description)
                .setTapAction(tapAction)
                .build()
        else -> null
    }
}
```

In `app/wear/src/main/res/values/strings_surfaces.xml`, add before `</resources>`:

```xml
    <string name="wear_surface_mark">Mark</string>
    <string name="wear_surface_mark_description">Mark this moment</string>
```

In `app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponents.kt`, replace the `NEW_APP_ONLY` property with:

```kotlin
    /** Surfaces only the new watch app offers; off while it is off. */
    val NEW_APP_ONLY: List<String> =
        listOf(
            "app.logdate.wear.tile.OnThisDayTileService",
            "app.logdate.wear.complication.OnThisDayComplicationService",
            "app.logdate.wear.complication.MarkComplicationService",
        )
```

(`COMPLICATIONS` stays as it is: the Mark complication never changes, so it is never refreshed.)

In `app/wear/src/main/AndroidManifest.xml`, insert this service directly after the `.complication.OnThisDayComplicationService` `</service>` line that Task 13 added:

```xml

        <!-- Mark complication: marks the moment without opening the app; only while the new watch app is on -->
        <service
            android:name=".complication.MarkComplicationService"
            android:enabled="false"
            android:exported="true"
            android:label="@string/wear_surface_mark"
            android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER">
            <intent-filter>
                <action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST" />
            </intent-filter>

            <meta-data
                android:name="android.support.wearable.complications.SUPPORTED_TYPES"
                android:value="SHORT_TEXT,MONOCHROMATIC_IMAGE,SMALL_IMAGE" />
            <meta-data
                android:name="android.support.wearable.complications.UPDATE_PERIOD_SECONDS"
                android:value="0" />
        </service>
```

Now the test source set compiles. Run the Record launch test to verify it fails before Step 5:

```bash
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.RecordLaunchTest
```

Expected: all 3 tests FAIL with `ComposeTimeoutException` at their first `waitUntil` on `recorder.isRecording`, because the app opens Today and records nothing.

- [ ] **Step 5: Start a recording for a Record launch**

In `app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt`, add these imports in ASCII order (keep every import the recorder plan added): `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.setValue`, `app.logdate.wear.v2.recorder.canStartRecording`, `app.logdate.wear.v2.recorder.gesture.startLocked`. Then replace this block in `WearAppV2`:

```kotlin
    LaunchedEffect(newRequests) {
        newRequests.collect { request ->
            backStack.clear()
            backStack.addAll(request.toV2BackStack())
        }
    }
```

with:

```kotlin
    // Saved so a recreated activity, which reads the same launch intent again, does not record twice.
    var recordLaunchHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (recordLaunchHandled || initialRequest != WearLaunchRequest.Record) return@LaunchedEffect
        recordLaunchHandled = true
        recorder.startForLaunch()
    }
    LaunchedEffect(newRequests) {
        newRequests.collect { request ->
            backStack.clear()
            backStack.addAll(request.toV2BackStack())
            if (request != WearLaunchRequest.Record) return@collect
            recordLaunchHandled = true
            recorder.startForLaunch()
        }
    }
```

and add this function after `WearAppV2`:

```kotlin
/**
 * The Record complication, the Today tile and a reminder's Record action open the app with the Record
 * request: it starts a recording that goes on until Stop, as a tap on Record does. A recording that
 * is already running, or a save still waiting, is left as it is.
 */
private fun RecorderViewModel.startForLaunch() {
    if (!state.value.canStartRecording) return
    startLocked()
}
```

The microphone permission is checked and asked for by the recorder itself, as for a tap.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintFormat :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:lintDebug :app:wear:assembleDebug :app:wear:validateDebugScreenshotTest
uptime && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.MarkComplicationTest,app.logdate.wear.e2e.RecordLaunchTest,app.logdate.wear.e2e.SurfaceComponentsTest,app.logdate.wear.e2e.ShellNavigationTest
```

Expected: `BUILD SUCCESSFUL` twice; `lintDebug` reports no new error; `SurfaceComponentsTest` (unit) runs 4 tests; screenshot validation passes unchanged (no pixels change); the GMD run passes 4 + 3 + 3 tests plus `ShellNavigationTest`, which shows a launch without the Record request still opens a plain Today.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b8-task14-commit.txt <<'EOF'
chore(wear): mark and record straight from the watch face

With the new watch app on, a Mark complication marks the moment from
the watch face: one tap saves the mark and confirms it with a haptic,
without opening the app. A mark made this way has only its time; the
phone adds the place later. The complication is offered only while
the new app is on.

The Record complication, the Today tile's Record button and a
reminder's Record action now open the app already recording, the same
as a tap on Record. The app does not start a second recording when
its screen is recreated.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/complication/MarkComplicationService.kt app/wear/src/main/res/values/strings_surfaces.xml app/wear/src/main/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponents.kt app/wear/src/main/AndroidManifest.xml app/wear/src/main/kotlin/app/logdate/wear/v2/WearAppV2.kt app/wear/src/test/kotlin/app/logdate/wear/v2/surfaces/SurfaceComponentsTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/MarkComplicationTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/RecordLaunchTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/b8-task14-commit.txt
```

---

## Self-Review

### Spec coverage

| Master plan step and spec item | Task | Notes |
|---|---|---|
| B5a Flow page: moments of the last 7 days, each playable inline; Earlier opens the rest of the library window | 3, 4 | Voice and text only (photos stay on the phone). Archive shows days 8 to 14 by day. |
| B5b On this day (`GetMemoryRecallUseCase`) and this week's Rewind as cards | 5 | The Rewind card reads the watch's database; nothing writes Rewinds on the watch yet, so it appears once the phone sends them. |
| B5c Moment detail: crown scrubbing with a detent tick, captions, place, Delete with `AlertDialog` | 2, 3 | `CrownScrubber` (5 s detents), `MomentCaption`. |
| B5d Playback engine: position, seek, speed (1×, 1.5×, 2×), skip silence on the shared `AudioPlaybackService` | 1, 2, 3 | Speed uses Media3's standard `COMMAND_SET_SPEED_AND_PITCH`, which every controller has by default; only skip silence needs the custom session command. See "Decisions to confirm" below. |
| B5e Play on through the system output switcher, offered only when it resolves; `WatchMediaCache` boundary | 2, 6 | `<queries>` for `MEDIA_OUTPUT` and Bluetooth settings. The phone as a target is not drawn. |
| B6 Status card (live sync status and `PhoneReachability`) and settings list | 7, 9, 10 | Haptics, Places, Reminders, More on your phone (gated on `phone.settingsLink.v1`). |
| B7a Workout end through `PassiveListenerService` (exercise to passive); `ACTIVITY_RECOGNITION` and `RECEIVE_BOOT_COMPLETED` with boot re-registration | 9 | Boot and package-replaced hand off to WorkManager; the app also re-registers at each start. |
| B7a Health Connect Play declaration on the phone | Self-Review | Documented below under "Play Console". The watch trigger does not use Health Connect. |
| B7b `NudgeNotifier` on `MOMENT_REMINDERS` with a Record action through `WearLaunchRequest`; bridging excludes `logdate.reminder`; evening check only while the phone is unreachable | 8, 10 | At most one reminder a day, waking hours 07:30 to 22:30, evening check at 20:30. |
| B8 `SurfaceUpdater`: at once after user actions, sync-driven at most once every 5 min | 11 | User actions are seen as the outbox growing; day changes refresh too. |
| B8 Today tile (`primaryLayout`, Record `textEdgeButton`) and On this day tile on `Material3TileService` | 13 | Checked with `protolayout-testing`. |
| B8 Campfire `RANGED_VALUE` with a phase color ramp | 12 | Added to the existing component, so placements continue to work. |
| B8 Record complication opens the app and starts recording | 14 | `QuickCaptureComplicationService` already sends `voice_note`, which parses to `WearLaunchRequest.Record`; `WearAppV2` starts a locked recording for that request (the recorder plan hands this to B8), and the Today tile and reminders send the same request. |
| B8 Mark complication without opening the app | 14 | Fires the recorder plan's `MarkMomentReceiver`. |
| B8 On this day complication | 13 | `LONG_TEXT`. |
| B8 Entry Count and Mood retired | 11 | Also the Quick Capture tile, which the Today tile's Record button replaces. Disabled while the flag is on, enabled again when it is off. |

Not built here, by design (no dead controls): Star, Open on phone, the phone as a Play on target and "Continue on phone/watch" (they need the phone playback capability and the shared-state work), streaming by byte offset and downloading ahead (the sync window plan; `WatchMediaCache` is the seam), the cloud line on the status card (the watch has no cloud link yet), Hold to talk and mood after recording (the recorder plan), and the phone's tiles-and-complications gallery (the phone companion plan).

### Placeholder scan

No "TBD", "TODO", "implement later", "similar to Task N" or prose-only code steps. Every code step has complete code; every run step has the exact command and expected result. Three steps read a name back before using it, and say exactly which lines change if it differs: Task 0, Step 2 and Task 14, Step 1 (the recorder plan's `MarkMomentReceiver.pendingIntent`, `startLocked`, `canStartRecording` and `RecorderTags`) and Task 13, Step 1 (`protolayout-testing`, which Gradle has not cached yet).

### Type consistency

- `AudioPlaybackOptions(startPositionMs, speed, skipSilence)` and `AudioPlaybackStatus.positionMs/playbackSpeed/skipSilenceEnabled` (Task 1) are what `AndroidMomentPlaybackEngine` and `toEngineStatus()` read (Task 2).
- `MomentPlayer.playOrPause/seekBy/seekTo/cycleSpeed/setSkipSilence/stop/stopIfPlaying` and `PlaybackState.{Idle, Preparing, Active, Failed}` (Task 2) are the only player calls in Tasks 3, 4 and 6.
- `FlowActions` (Task 3) gains `onPlayPause` and `onOpenArchive` with `null` defaults (Task 4), and `FlowPage` gains `cards` (Task 5) and `footer` (Task 7) with `{}` defaults, so earlier callers and screenshot scenes compile unchanged until a task replaces them.
- `WearLaunchIntents.ROUTE_RECORD/ROUTE_MOMENT/record/moment` (Task 8) are what the reminder notifications (Tasks 8 to 10), `SurfaceLaunchActions` and the On this day complication (Task 13) use; Task 13's GMD test parses them back through `WearLaunchRequest.parse`.
- `OnThisDayFinder.find(today: LocalDate): OnThisDay?` (Task 5, a Koin single) is the one source for the card, the tile and the complication; `findOnThisDay()` (Task 13) wraps it once for both surfaces.
- `SurfaceCatalog.NEW_APP_ONLY` grows in Tasks 13 and 14, and its unit test is replaced in the same commits; `TILES` and `COMPLICATIONS` gain only the surfaces whose content changes.
- `TodaySummary`, `summarizeToday` and `todaySummaryTile` keep their signatures; `latestEntryPreview` moves from private to internal in Task 13, its only change.
- `RecorderViewModel.startForLaunch()` (Task 14, private to `WearAppV2.kt`) calls only the recorder plan's `canStartRecording` and `startLocked()`, the same path as a tap and TalkBack's start action.
- `campfireComplicationData(context, content, type, tapAction)` (Task 12) replaces the service's private builder; `CampfireComplicationContent`'s constructor is unchanged, so the existing four tests still compile.

### Play Console

- Task 9 restores `ACTIVITY_RECOGNITION` and `RECEIVE_BOOT_COMPLETED` in the watch manifest. The manifest is not flag-gated, so the Play listing for the watch shows the physical activity permission from that release on, although the watch asks for it only when someone turns reminders on in the new app. `ACTIVITY_RECOGNITION` has no Play declaration form. The user activity state is read on the watch and never stored or sent, so the Data safety form does not change.
- Restoring the boot permission also lets `client/location`'s merged `LocationTrackingBootReceiver` run at boot. It already runs after updates and does nothing unless location tracking is on, and the watch still removes background location and the location foreground service.
- The watch's workout trigger uses Health Services, not Health Connect. A phone-side workout trigger (the reminder orchestrator, R1) would read exercise sessions from Health Connect; before that ships, the phone's Play Console listing needs the Health Connect declaration (App content, Health apps: the exercise data type, why it is read, and the privacy policy link), and the phone manifest needs `android.permission.health.READ_EXERCISE` next to the existing `READ_SLEEP` in `client/domain/src/androidMain/AndroidManifest.xml`. This plan adds no Health Connect permission.

### Risks

- Order against the recorder and capture plan: it replaces `WearAppV2.kt` whole in its Tasks 5, 7, 9 and 10 and `LogDateWearApplication.kt` whole in its Task 6, so it must land before this plan's Task 3 (Tasks 1 and 2 may run first). Every edit here to those files, `WearV2Module.kt`, `WearLaunchRoutes.kt` and the manifest is anchored, and Task 3's Home entry is written against its Task 10 version. Task 14 relies on its `MarkMomentReceiver.pendingIntent`, `startLocked`, `canStartRecording` and `RecorderTags`, which Task 0 and Task 14, Step 1 check by name.
- The recorder plan leaves a Record launch opening Today. Until Task 14, the reminder's Record action (Task 8) and the Today tile's Record button (Task 13) open Today, one tap from recording; Task 14 makes them start a recording.
- `protolayout-testing` 1.4.2 is published on Google Maven but was not in the Gradle cache; its API was read from the AOSP API file and is read back from the resolved AAR in Task 13, Step 1.
- Retiring surfaces through component state removes them from the carousel and from watch faces; turning the flag off enables them again but does not put them back where they were. Dogfood watches that switch the flag lose those placements.
- `SurfaceUpdater` runs in the app process. When the process starts for a Data Layer event, the coordinator may not have read the flag yet, so that first sync refresh can be skipped; complications still update on their own periods (Streak every 6 hours, On this day hourly), and the next capture refreshes everything.
- The new tile's manifest preview reuses `tile_preview.png`; a rendered preview of each new tile is a follow-up for the tile picker.
- The Health Services user activity state may be unsupported on some watches (and on the GMD image). Task 9's `HealthServicesActivityRegistration.register()` returns false where `supportedUserActivityStates` lacks exercise, so no workout reminder fires there; from Task 10 the evening check still works on those watches.

### Decisions to confirm

Each is the default this plan builds; changing one changes only the task named.

1. Speed goes through Media3's standard speed command and only skip silence through a custom session command, instead of a custom command for both (Task 1).
2. Play on offers the system output switcher, or Bluetooth settings where the switcher is missing, and nothing when neither resolves; the phone as a target and the existing `PlayOn` route wait for the phone's playback capability (Task 6).
3. `WatchMediaCache` refuses to store audio that would leave less than 200 MB free (Task 2).
4. The watch library shows voice and text only: 7 days on the list, days 8 to 14 under Earlier (Task 4).
5. The Rewind card appears only once the phone sends Rewinds; the watch never generates one (Task 5).
6. Opening a moment shows the player without starting playback (Task 3).
7. Reminders are a watch-only switch with watch-only rules until shared state (A-D8) and the reminder orchestrator (R1) exist: one a streak day, waking hours 07:30 to 22:30, within 30 minutes of a workout ending, and an evening check at 20:30 only while the phone is out of reach (Tasks 8 to 10).
8. `MOMENT_REMINDERS` is a new watch-only channel (notification ID 4005), and the `logdate.reminder` bridging exclusion is set at every app start whatever the flag (Task 8).
9. The Today tile reuses the Today's Summary component so existing carousel placements stay; the Quick Capture tile and the Entry Count and Mood complications are disabled while the flag is on (Tasks 11 and 13).
10. The campfire's ranged value ships to everyone as a `feat`, and with campfire streaks off a ranged slot shows no data (Task 12).
11. The On this day complication fills long text only; a short slot would need a memories glyph that does not exist yet (Task 13).
12. A Record launch (complication, tile or reminder action) starts a recording at once, as a tap on Record does, rather than opening Today (Task 14).
13. Commit trailers use `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`, as the house rules for this plan state.
