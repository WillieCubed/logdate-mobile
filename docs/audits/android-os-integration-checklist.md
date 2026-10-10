# Android OS Integration Checklist

Design: [`docs/feature-design/android-os-integration.md`](../feature-design/android-os-integration.md)

Guides: [`docs/reference/android/os-integration.md`](../reference/android/os-integration.md)

Tracking: Docket, Hypertext Studio workspace, initiative **Native Android integration**

Audit created: 2026-10-09

Sources checked: 2026-10-09 against the Android Developers pages for
[Android 17 features](https://developer.android.com/about/versions/17/features),
[Android 16 features](https://developer.android.com/about/versions/16/features), and
[AppFunctions](https://developer.android.com/ai/appfunctions). The local `ctx7` lookup was
quota-blocked, so the linked official pages are the source of truth.

## Scope

This checklist tracks every surface in the Native Android integration program on Android phones,
foldables, tablets, Chromebooks, and Android desktop windowing. Wear OS tiles and complications are
out of scope; they have their own documentation in `app/wear/README.md`.

Agent-driven validation runs only on emulators (`emulator-*` serials) or Gradle Managed Devices,
never on physical Android devices.

## Status Legend

- `[todo]` Not started, or no evidence recorded.
- `[partial]` Some behavior ships, or evidence is incomplete. The row says what is missing.
- `[done]` Behavior ships, and all of the following are recorded in the Evidence column:
  - the tests;
  - the Lane 1 references, where the surface has app-drawn UI;
  - the Lane 2 captures, where the surface has system UI;
  - the comparison sheet;
  - the guide section.
- `[layout-only]` Verified with Compose screenshots that simulate system state (for example, injected
  caption insets) because no emulator or managed device can show it. This must be stated in the row.
- `[n/a]` Does not apply. The row says why.

## Evidence rules

A row reaches `[done]` only when its Evidence column lists:

1. **Tests:** the test class names and the command that runs them.
2. **Lane 1:** the screenshot test functions in `@OsIntegrationPreviewMatrix`, or the Glance host
   lane references.
3. **Lane 2:** image paths under `docs/screenshots/android-os-integration/`, with device and API level.
4. **Comparison sheet:** the path the sheet was generated to, and the date it was reviewed.
5. **Commit:** the commit SHA, which also appears on the Docket task.

`checkOsIntegrationScreenshotCoverage` verifies that every Lane 1 and Lane 2 item named in
`config/screenshots/os-integration-coverage.txt` exists.

## Implementation Pointers

- Intent routing:
  `app/compose-main/src/androidMain/kotlin/app/logdate/client/DeepLinkResolver.kt`,
  `MainActivity.kt`, and `MainActivity+MultiWindow.kt`
- Editor route: `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/navigation/EditorNavRoute.kt`
- Static shortcuts: `app/android-main/src/main/res/xml/shortcuts.xml`
- Dynamic shortcuts: `client/feature/android-widgets/src/commonMain/kotlin/app/logdate/client/feature/widgets/shortcuts/`
  and `app/compose-main/src/androidMain/kotlin/app/logdate/client/shortcuts/`
- Widgets: `client/feature/android-widgets/src/androidMain/kotlin/app/logdate/client/feature/widgets/`
- Notifications: `client/notifications/` and `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioNotificationHandler.kt`
- Feature flags: `client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/featureflags/FeatureFlag.kt`
- Manifest: `app/android-main/src/main/AndroidManifest.xml`

## Process

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[partial]` | Design spec, hub guide, surface guides, and this checklist exist and are linked from `docs/README.md` | This commit. Missing: screenshots in each guide, which arrive with their features. |
| `[done]` | `@OsIntegrationPreviewMatrix` covers phone, phone dark, compact, landscape, tablet, RTL, 200% text, dynamic color, and bubble size | `2b92186d6`. Matrix and `@BubbleWindowPreview` in `common/ScreenshotPreviewMatrix.kt`; shared viewports in `SharedScreenshotCatalog.kt` (`osIntegrationVariants`), tested by `SharedScreenshotCatalogTest` (7/7). First scene `S01_PrivacySystemSearchVisibility`: 8 Android and 3 new desktop references, each inspected. It found settings descriptions truncating at two lines (Docket bug). |
| `[todo]` | Glance widgets render to committed references for light, dark, RTL, and 200% text | |
| `[todo]` | `OsSurfaceScreenshotsTest` captures real system UI on API 37 phone and tablet managed devices with demo mode | |
| `[todo]` | Desktop windowing capture spike decided | |
| `[done]` | `checkOsIntegrationScreenshotCoverage` fails on missing images, missing tests, and stale references | `ScreenshotCoverageTest` (13/13, `cd build-logic && ../gradlew test`). Registry `config/screenshots/os-integration-coverage.txt`. Runs in `.githooks/pre-push` and `./run screenshots:check`. Its first run found 157 stale references, deleted in `f273b86a5`. The `ci.yml` step is prepared for the user to commit. |
| `[done]` | `buildScreenshotComparisonSheet` generates labeled sheets per feature | `ScreenshotComparisonSheet` and `BuildScreenshotComparisonSheet` in build-logic, tested by `ScreenshotComparisonSheetTest`. `./run screenshots:sheet <feature>`. |
| `[todo]` | `./run` runs every screenshot lane, plus `screenshots:os`, `screenshots:sheet`, and `screenshots:check` | |

## Capture Launch Contract and Platform Hygiene

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[done]` | The editor opens in Text, Voice, Camera, Video, or PhotoPrompt mode from one `EditorLaunchMode` | `EditorLaunchMode` on `EntryEditorRoute`; voice launches start recording once the microphone is allowed and the window has focus. `TextEditingTest` launch tests; verified on API 36 and 37 managed devices from the Record voice tile. |
| `[done]` | `app.logdate.action.CAPTURE` and `logdate://capture` resolve to the editor; legacy widget and ambient extras keep resolving | `CaptureLaunch.kt` and `DeepLinkResolver`; `CaptureLaunchTest`, `MainActivityNavIntentResolverTest`. Used by the launcher shortcuts and tiles in the system UI captures. |
| `[done]` | OS integration flags exist with debug-only toggles | Six flags in `FeatureFlag.kt` (`FeatureFlagStoreTest`); toggles in Developer tools > Feature previews. Lane 1: `Flow20_02_FeaturePreviewsScreenshots.kt` and the desktop `feature-previews` scene. |
| `[done]` | `PlatformComponentToggler` enables and disables flagged manifest components | `PlatformComponentToggler` with `PlatformComponentTogglerTest`. Tiles, the bubble activity, and its receiver are declared disabled; the API 37 captures show them appearing once their flags are on. |
| `[done]` | Manifest declares the touchscreen as optional, `configChanges`, editor `<layout>` minimums, and multi-instance support | `touchscreen` optional, `configChanges` on both activities, editor `<layout>` minimums, and `PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI`. |
| `[todo]` | `ShareReceiver` declares no SEND filters it cannot receive | |
| `[partial]` | Editor windows are titled in Recents, the taskbar, and desktop captions | `editorWindowTitle` (`EditorWindowTitleTest`) sets the task title from the entry. Missing: Recents and taskbar capture. |
| `[partial]` | The compat splash screen on API 30 matches Android 12+ | `Theme.LogDate.Starting` with the launcher brown and foreground. Missing: API 30 and API 37 cold-start captures. |

## Launcher Shortcuts and Direct Share

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[done]` | Static shortcuts for New entry, Record voice, and Take photo, with themed icons | Three static shortcuts on the capture action, with adaptive icons and monochrome layers. Lane 2: `launcher-shortcuts/long-press--{phone-api36,phone-api37,tablet-api37}.png`. |
| `[done]` | Dynamic shortcuts for Continue draft, Today, and Last week | Ship today (`DynamicShortcutPublisherTest`). Their icons were bare white glyphs that launchers drew white on white; they now use the same adaptive icons as the static shortcuts (visible in the launcher captures). |
| `[done]` | The dynamic shortcut cap leaves room for static shortcuts | `DynamicShortcutPublisher` caps dynamic shortcuts at four visible minus three static; `DynamicShortcutPublisherTest`. |
| `[done]` | Drafts are pushed with `pushDynamicShortcut`; share targets carry a `LocusId` | Share targets carry a `LocusId`. Drafts stay in the atomic `setDynamicShortcuts` call by decision (stable ids keep usage history); recorded in `launcher-shortcuts.md`. The recorder bubble's shortcut survives refreshes. |
| `[partial]` | Direct Share targets for each journal | Ship today. `SharingShortcutParsersTest`, `IncomingShareE2ETest`. Missing: Lane 2 share sheet capture. |
| `[done]` | `reportShortcutUsed` is called on save for each mode and shared journal | `CaptureUsageReporter` (editor) and `AndroidCaptureUsageReporter`; ids from `captureShortcutId`, tested in `CaptureLaunchTest`. |
| `[todo]` | Journals and widgets can be pinned from inside the app | |

## System Capture Surfaces

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[done]` | Tile microphone start spike decided | Decided without starting the microphone from the tile: Record voice opens the editor in voice mode through the capture action, which starts recording in the foreground. Recorded in `quick-settings-tiles.md`. |
| `[partial]` | New entry and Record voice Quick Settings tiles, with an active recording state | Behind `QUICK_SETTINGS_TILES`; components toggled by `PlatformComponentToggler`. Tests: `CaptureTileStateTest`, `PlatformComponentTogglerTest`. Missing: Lane 2 captures of the tiles in the shade. |
| `[partial]` | The recording notification is a Live Update with a status bar chip | Behind `LIVE_UPDATES`: promotion request and "Paused" chip text. Tests: `LiveUpdatesTest`. Missing: Lane 2 captures of the chip and the promoted notification. |
| `[partial]` | Export, restore, and sync progress are Live Updates, with semantic colors on API 37 | Built behind `LIVE_UPDATES`: `applyTransferLiveUpdate` with percent chip and `ProgressStyle`; restore phases become segments. Tests: `LiveUpdatesTest`. Missing: API 37 semantic colors and Lane 2 captures. |
| `[partial]` | Capture nudges offer Write (direct reply) and Record | Built behind `NOTIFICATION_QUICK_REPLY`: `QuickReplyReceiver`, confirmation without the entry text, failure with Retry. Tests: `QuickReplyTest`. Lane 2 reply captures pending. |
| `[todo]` | A capture-only lock-screen and hub widget, showing no journal content | |

## Widgets

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[partial]` | New entry, On This Day, and Pinned memory home-screen widgets | Ship today. `OnThisDayWidgetPreviewTest` and `WidgetSetupScreenshotsTest` capture managed-device images, but they are not committed. Missing: Lane 1 Glance references. |
| `[partial]` | Generated widget previews in the picker | `WidgetPreviewPublisher` ships today. Missing: Lane 2 picker capture. |

## Bubbles

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[partial]` | Home, the editor, and the recorder work at App Bubble sizes without being recreated on resize | `configChanges` on both activities. Lane 1 bubble window: every Home workspace scene, `Flow03_01` editor S01 and S02, and the recorder. Missing: Lane 2 capture of LogDate as an App Bubble. |
| `[partial]` | Recording bubble spike decided | The bubble is a conversation notification tied to a long-lived "Voice recorder" shortcut, separate from the Live Update. Missing: emulator verification on API 36 and 37. |
| `[partial]` | The voice recorder floats as a bubble with pause, stop, and save | Behind `RECORDING_BUBBLE`. Lane 1: `Flow20_03_RecorderBubbleScreenshots.kt` (matrix plus bubble window). Tests: `RecorderBubbleStatesTest`. Missing: Lane 2 captures of the floating recorder. |

## Keyboard, Pointer, Drag and Drop

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[partial]` | One keyboard shortcut catalog drives Compose handlers, the Meta+/ helper, and desktop menus | `AppKeyboardShortcut` drives the Compose handlers (Ctrl+N, Ctrl+Shift+R, Ctrl+F, Ctrl+Comma, Ctrl+1 to 5, editor Ctrl+S and Esc) and the Meta+/ groups; `AppKeyboardShortcutsTest`. Missing: desktop menu bar and the Meta+/ capture. |
| `[partial]` | Right-click opens context menus at the pointer; icon-only actions have tooltips | Secondary click opens `ContextMenuArea` at the pointer (`ContextMenuPlacementTest`); `IconTooltip` on the account action and the recorder bubble. Missing: Lane 1 menu and tooltip references. |
| `[partial]` | Text, photos, videos, and audio can be dropped or pasted into the editor | `entryDropTarget` with `routeDroppedItems` (`EntryDropTest`); media is copied into the private store before the drag permission ends. Missing: paste through `contentReceiver` and a cross-app drag capture. |
| `[partial]` | Photos and videos drag out as readable content URIs | `mediaDragSource` sends FileProvider URIs with global read flags from Library thumbnails. Missing: cross-app drag capture. |
| `[todo]` | Stylus handwriting works in the editor | |

## Large Screens and Desktop Windowing

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[partial]` | Entries and journals open in a new window, beside the current one when there is room | Journal menus offer "New entry in a new window", opened adjacent with `FLAG_ACTIVITY_LAUNCH_ADJACENT`. Missing: side-by-side capture. |
| `[todo]` | The workspace app bar sits in the desktop caption bar | |

## AppFunctions and Cross-Device

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[todo]` | Capture AppFunctions: `createTextEntry`, `startVoiceEntry`, and `listJournals` | |
| `[todo]` | Read AppFunctions refuse without consent or while locked | |
| `[partial]` | Handoff continues an in-progress entry on another device | `MainActivity` opts into Handoff on API 37 (`MainActivity+Handoff.kt`). Editor Handoff depends on draft sync. |
