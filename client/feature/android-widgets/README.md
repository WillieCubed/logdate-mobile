# `:client:feature:android-widgets`

**LogDate on the Android home screen and launcher: Glance widgets and the shortcut model.**

## Overview

This module holds two Android integration surfaces:

- **Widgets** built with Jetpack Glance: New entry, On This Day, and Pinned memory.
- **The dynamic shortcut model**: which launcher shortcuts and Direct Share targets LogDate publishes,
  and in what order.

The Android code that publishes shortcuts lives in `app/compose-main`. This module decides what to
publish, so that decision can be unit-tested without Android.

For how these surfaces fit into the rest of Android, read the
[OS integration guide](../../../docs/reference/android/os-integration.md), especially
[widgets](../../../docs/reference/android/os-integration/widgets.md) and
[launcher shortcuts](../../../docs/reference/android/os-integration/launcher-shortcuts.md).

## Architecture

```
commonMain/
  WidgetContentModels.kt, OnThisDayWidgetState.kt, WidgetStateMapper.kt   what a widget shows, as plain data
  WidgetNavConstants.kt                                                   intent extras a widget tap sends
  shortcuts/DynamicShortcutDescriptor.kt                                  the shortcuts LogDate can publish
  shortcuts/DynamicShortcutPublisher.kt                                   which ones to publish, and in what order
androidMain/
  *Widget.kt, *WidgetReceiver.kt                                          Glance widgets and their receivers
  *WidgetConfigActivity.kt, SecureWidgetConfigActivity.kt                 per-widget setup screens
  WidgetPrivacyGate.kt                                                    app-lock check before private content
  WidgetPreviewPublisher.kt                                               generated picker previews (Android 15+)
  WidgetRefreshScheduler.kt, OnThisDayWidgetRefreshWorker.kt              content refresh
  WidgetAudioPlaybackActivity.kt                                          plays audio memories from a widget
  di/WidgetModule.kt                                                      Koin wiring
  res/xml/*_widget_info.xml                                               appwidget-provider declarations
```

The split matters. Everything in `commonMain` is plain Kotlin, tested in `commonTest`. Everything that
touches `RemoteViews`, `AppWidgetManager`, or activities is in `androidMain`.

## Key Components

### Widgets

| Widget | Provider XML | What it shows | Tap opens |
| --- | --- | --- | --- |
| `NewEntryWidget` | `new_entry_widget_info.xml` | Write, Record, and Camera actions, optionally over a chosen photo | The editor in that mode |
| `OnThisDayWidget` | `on_this_day_widget_info.xml` | A memory from this date in past years | That day's timeline |
| `FixedMemoryWidget` | `fixed_memory_widget_info.xml` | One memory the person pinned | That memory |

All three:

- are `home_screen` widgets with a 3×2 target size;
- can be resized in both directions;
- set `updatePeriodMillis="0"`, because refresh is scheduled by WorkManager rather than a fixed timer.

### Widget taps

`createWidgetLaunchIntent` in `OnThisDayWidgetContent.kt` builds every tap intent. It targets the app's
launch activity and sets `EXTRA_NAV_SOURCE` plus the extras in `WidgetNavConstants.kt`.
`resolveMainActivityNavKey` in `app/compose-main` turns those extras into a navigation route.

### Privacy

`WidgetPrivacyGate.kt` defines `requireWidgetContentAccess`:

| App lock setting | Effect on setup and playback |
| --- | --- |
| No lock | Allowed |
| Biometric lock | Allowed after a biometric prompt |
| Password lock | Refused |

A widget never bypasses the app's configured lock to show private content.

### Dynamic shortcuts

- **`DynamicShortcutDescriptor`** is a sealed type with four kinds:
  - `ContinueDraft`
  - `TodayTimeline`
  - `WeekRewind`
  - `ShareToJournal` (one per journal; a Direct Share target)
- **`DynamicShortcutPublisher`** chooses up to `DEFAULT_MAX_SHORTCUTS` launcher shortcuts: the four
  slots launchers show, minus the three static capture shortcuts (New entry, Record voice, Take
  photo). That leaves one slot, filled in priority order: Continue draft, then Today. It
  drops drafts older than `DEFAULT_DRAFT_FRESHNESS` (7 days). It orders share targets favorites first,
  then by most recent activity.
- **Strings and icons** live in this module: `R.string.dynamic_shortcut_*` and
  `R.drawable.ic_shortcut_*`.

## Dependencies

- `:client:domain`, `:client:repository`, `:client:media`, `:shared:model`
- `androidx.glance:glance-appwidget` and `glance-material3`
- `androidx.work` with Koin's WorkManager integration
- `androidx.biometric`, for the privacy gate

## Dependency Injection

`di/WidgetModule.kt` registers `MemoriesWidgetInstallController` (its Android implementation is
`AndroidMemoriesWidgetInstallController`) and the `OnThisDayWidgetRefreshWorker` WorkManager worker.

## Testing

### Unit tests (`src/commonTest/`)

```bash
./gradlew :client:feature:android-widgets:allTests
```

They cover:

- widget state mapping and serialization: `OnThisDayWidgetStateMapperTest`,
  `OnThisDayWidgetStateSerializationTest`;
- content models: `WidgetContentModelsTest`;
- date formatting;
- shortcut selection: `DynamicShortcutPublisherTest`.

### Instrumented tests

These are in `app/android-main/src/androidTest/` and run on Gradle Managed Devices only:

- `OnThisDayWidgetPreviewTest` renders each widget through Glance's `composeForPreview` and captures
  images.
- `WidgetSetupScreenshotsTest` captures the configuration screens.
- `WidgetInstanceSettingsTest`, `WidgetPhotoPermissionFlowTest`, and `WidgetAudioPlaybackTest` cover
  setup and playback.

```bash
./gradlew :app:android-main:smokeDevicesGroupDebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.e2e.OnThisDayWidgetPreviewTest
```

### Screenshots

Widget images from the managed-device tests are not committed or compared yet. The
[Native Android integration](../../../docs/audits/android-os-integration-checklist.md) program adds a
host-side Glance screenshot lane to this module, with committed references in light, dark,
right-to-left, and 200% text. [`docs/testing/screenshot-tests.md`](../../../docs/testing/screenshot-tests.md)
documents it once it exists.

## TODOs

- Committed widget screenshot references (Glance host lane).
- A capture-only lock-screen and hub widget, behind `LOCK_SCREEN_CAPTURE_WIDGET`.
- "Add widget" pinning from widget settings.
- Move widget taps onto the capture launch contract (`app.logdate.action.CAPTURE`).
