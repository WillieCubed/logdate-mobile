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
| `[done]` | Design spec, hub guide, surface guides, and this checklist exist and are linked from `docs/README.md` | Design spec, hub guide, ten surface guides, and this checklist, linked from `docs/README.md`. Guides for shipped surfaces embed their captures. |
| `[done]` | `@OsIntegrationPreviewMatrix` covers phone, phone dark, compact, landscape, tablet, RTL, 200% text, dynamic color, and bubble size | `2b92186d6`. Matrix and `@BubbleWindowPreview` in `common/ScreenshotPreviewMatrix.kt`; shared viewports in `SharedScreenshotCatalog.kt` (`osIntegrationVariants`), tested by `SharedScreenshotCatalogTest` (7/7). First scene `S01_PrivacySystemSearchVisibility`: 8 Android and 3 new desktop references, each inspected. It found settings descriptions truncating at two lines (Docket bug). |
| `[done]` | Glance widgets render to committed references for light, dark, RTL, and 200% text | `OsSurfaceScreenshotsTest#widgetRenders` with `WidgetRenders` renders the Capture, New Entry, and On This Day previews on the Android 17 phone managed device: `widgets/<widget>-{light,dark,rtl,large-text}--phone-api37.png`. Rendered on a device instead of the host because RemoteViews need Android's own inflation. |
| `[done]` | `OsSurfaceScreenshotsTest` captures real system UI on API 37 phone and tablet managed devices with demo mode | `OsSurfaceScreenshotsTest` (launcher, Quick Settings and Live Update, recording bubble, App Bubble) passes on `flagshipPhoneApi37`, `largeScreenTabletApi37`, and `flagshipPhoneApi36` with demo mode. Captures in `docs/screenshots/android-os-integration/`; failed waits save the screen and view tree under `failures/`. |
| `[done]` | Desktop windowing capture spike decided | A managed device cannot show desktop windowing: it needs a developer setting, a lifted device check, and a restart. `./run screenshots:desktop` runs `OsSurfaceScreenshotsTest#desktopWindows` on its own Android 17 tablet emulator instead. Recorded in `large-screens-and-desktop-windowing.md`. |
| `[done]` | `checkOsIntegrationScreenshotCoverage` fails on missing images, missing tests, and stale references | `ScreenshotCoverageTest` (13/13, `cd build-logic && ../gradlew test`). Registry `config/screenshots/os-integration-coverage.txt`. Runs in `.githooks/pre-push` and `./run screenshots:check`. Its first run found 157 stale references, deleted in `f273b86a5`. The `ci.yml` step is prepared for the user to commit. |
| `[done]` | `buildScreenshotComparisonSheet` generates labeled sheets per feature | `ScreenshotComparisonSheet` and `BuildScreenshotComparisonSheet` in build-logic, tested by `ScreenshotComparisonSheetTest`. `./run screenshots:sheet <feature>`. |
| `[partial]` | `./run` runs every screenshot lane, plus `screenshots:os`, `screenshots:sheet`, and `screenshots:check` | `screenshots:os`, `screenshots:sheet`, and `screenshots:check` exist. Missing: `test:screenshots` covering the Wear and desktop lanes. |

## Capture Launch Contract and Platform Hygiene

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[done]` | The editor opens in Text, Voice, Camera, Video, or PhotoPrompt mode from one `EditorLaunchMode` | `EditorLaunchMode` on `EntryEditorRoute`; voice launches start recording once the microphone is allowed and the window has focus. `TextEditingTest` launch tests; verified on API 36 and 37 managed devices from the Record voice tile. |
| `[done]` | `app.logdate.action.CAPTURE` and `logdate://capture` resolve to the editor; legacy widget and ambient extras keep resolving | `CaptureLaunch.kt` and `DeepLinkResolver`; `CaptureLaunchTest`, `MainActivityNavIntentResolverTest`. Used by the launcher shortcuts and tiles in the system UI captures. |
| `[done]` | OS integration flags exist with debug-only toggles | Six flags in `FeatureFlag.kt` (`FeatureFlagStoreTest`); toggles in Developer tools > Feature previews. Lane 1: `Flow20_02_FeaturePreviewsScreenshots.kt` and the desktop `feature-previews` scene. |
| `[done]` | `PlatformComponentToggler` enables and disables flagged manifest components | `PlatformComponentToggler` with `PlatformComponentTogglerTest`. Tiles, the bubble activity, and its receiver are declared disabled; the API 37 captures show them appearing once their flags are on. |
| `[done]` | Manifest declares the touchscreen as optional, `configChanges`, editor `<layout>` minimums, and multi-instance support | `touchscreen` optional, `configChanges` on both activities, editor `<layout>` minimums, and `PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI`. Resizing into an App Bubble keeps state (`app-bubble--phone-api37.png`). |
| `[done]` | `ShareReceiver` declares no SEND filters it cannot receive | The receiver is not exported and handles only LogDate's explicit copy-link, QR code, and chooser callbacks; the filters and empty branches are gone. `ShareReceiverE2ETest` covers the two actions. |
| `[partial]` | Editor windows are titled in Recents, the taskbar, and desktop captions | `editorWindowTitle` (`EditorWindowTitleTest`) sets the task label from the entry; `desktop-window-labels--tablet-api37.txt` shows "New entry" on the editor window. Android 17's desktop caption and Recents show the app name instead, so no Android 17 surface displays the label. |
| `[done]` | The compat splash screen on API 30 matches Android 12+ | `Theme.LogDate.Starting` with the launcher brown and foreground. Lane 2: `splash/launch--phone-api30.png` and `launch--phone-api37.png` (`OsSurfaceScreenshotsTest#splashScreen`). Guide: `os-integration.md`. |

## Launcher Shortcuts and Direct Share

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[done]` | Static shortcuts for New entry, Record voice, and Take photo, with themed icons | Three static shortcuts on the capture action, with adaptive icons and monochrome layers. Lane 2: `launcher-shortcuts/long-press--{phone-api36,phone-api37,tablet-api37}.png`. |
| `[done]` | Dynamic shortcuts for Continue draft, Today, and Last week | Ship today (`DynamicShortcutPublisherTest`). Their icons were bare white glyphs that launchers drew white on white; they now use the same adaptive icons as the static shortcuts (visible in the launcher captures). |
| `[done]` | The dynamic shortcut cap leaves room for static shortcuts | `DynamicShortcutPublisher` caps dynamic shortcuts at four visible minus three static; `DynamicShortcutPublisherTest`. |
| `[done]` | Drafts are pushed with `pushDynamicShortcut`; share targets carry a `LocusId` | Share targets carry a `LocusId`. Drafts stay in the atomic `setDynamicShortcuts` call by decision (stable ids keep usage history); recorded in `launcher-shortcuts.md`. The recorder bubble's shortcut survives refreshes. |
| `[partial]` | Direct Share targets for each journal | Ship today. `SharingShortcutParsersTest`, `IncomingShareE2ETest`. Missing: Lane 2 share sheet capture. |
| `[done]` | `reportShortcutUsed` is called on save for each mode and shared journal | `CaptureUsageReporter` (editor) and `AndroidCaptureUsageReporter`; ids from `captureShortcutId`, tested in `CaptureLaunchTest`. |
| `[done]` | Journals and widgets can be pinned from inside the app | Journals: **Add to home screen** through `HomeScreenPins`. Widgets: Settings > Widgets (`AndroidMemoriesWidgetInstallController`). Lane 2: `launcher-shortcuts/{journal-menu,pin-journal}--phone-api37.png`. |

## System Capture Surfaces

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[done]` | Tile microphone start spike decided | Decided without starting the microphone from the tile: Record voice opens the editor in voice mode through the capture action, which starts recording in the foreground. Recorded in `quick-settings-tiles.md`. |
| `[done]` | New entry and Record voice Quick Settings tiles, with an active recording state | Behind `QUICK_SETTINGS_TILES`. Tests: `CaptureTileStateTest`, `PlatformComponentTogglerTest`. Lane 2: `quick-settings-tiles/{idle,recording-editor,recording}--phone-api37.png`, plus API 36 and tablet. |
| `[done]` | The recording notification is a Live Update with a status bar chip | Behind `LIVE_UPDATES`. Tests: `LiveUpdatesTest`. Lane 2: `live-updates/recording-status-chip--phone-api37.png` and `recording-notification`; `recording-promotion--phone-api37.txt` records `promotedOngoing=true`. |
| `[done]` | Export, restore, and sync progress are Live Updates | Behind `LIVE_UPDATES`: `applyTransferLiveUpdate` with percent chip and `ProgressStyle`; restore phases become segments. Tests: `LiveUpdatesTest`. Lane 2: `live-updates/transfer-{status-chip,progress,finished}--{phone,tablet}-api37.png`, `sync-{status-chip,progress}--{phone,tablet}-api37.png`. Semantic colors for finished transfers dropped by decision: Android 17 applies semantic styles only to promoted notifications, and a finished transfer is no longer one. |
| `[done]` | Capture nudges offer Write (direct reply) and Record | Behind `NOTIFICATION_QUICK_REPLY`: `QuickReplyReceiver`, confirmation without the entry text, failure with Retry. Tests: `QuickReplyTest`. Lane 2: `notification-quick-reply/{reply-field,saved}--{phone,tablet}-api37.png`. The failure state is tested, not captured. |
| `[partial]` | A capture-only lock-screen and hub widget, showing no journal content | `CaptureWidget` behind `LOCK_SCREEN_CAPTURE_WIDGET`, declared for `home_screen\|keyguard`, with Write, Record, and Photo only. Tests: `CaptureWidgetContractTest`, `PlatformComponentTogglerTest`. Lane 2: `widgets/capture-widget-picker--phone-api37.png`. Missing: a capture placed on the lock screen or hub. |

## Widgets

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[partial]` | New entry, On This Day, and Pinned memory home-screen widgets | Ship today. `OnThisDayWidgetPreviewTest` and `WidgetSetupScreenshotsTest` capture managed-device images, but they are not committed. Missing: Lane 1 Glance references. |
| `[partial]` | Generated widget previews in the picker | `WidgetPreviewPublisher` ships today. Missing: Lane 2 picker capture. |

## Bubbles

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[done]` | Home, the editor, and the recorder work at App Bubble sizes without being recreated on resize | `configChanges` on both activities. Lane 1 bubble window: every Home workspace scene, `Flow03_01` editor S01 and S02, and the recorder. Lane 2: `bubbles/app-bubble--phone-api37.png`. |
| `[done]` | Recording bubble spike decided | Ships as designed: the conversation bubble floats on API 36 and 37 phones and lands in the Bubble Bar on the API 37 tablet. Where bubbles are not allowed, the same notification offers the system bubble button. |
| `[done]` | The voice recorder floats as a bubble with pause, stop, and save | Behind `RECORDING_BUBBLE`. Lane 1: `Flow20_03_RecorderBubbleScreenshots.kt` (matrix plus bubble window). Tests: `RecorderBubbleStatesTest`. Lane 2: `bubbles/{float-action,recorder-expanded,recorder-collapsed}--phone-api37.png`, `recorder-expanded--tablet-api37.png`. |

## Keyboard, Pointer, Drag and Drop

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[done]` | One keyboard shortcut catalog drives Compose handlers, the Meta+/ helper, and desktop menus | `AppKeyboardShortcut` drives the Compose handlers (Ctrl+N, Ctrl+Shift+R, Ctrl+F, Ctrl+Comma, Ctrl+1 to 5, editor Ctrl+S and Esc) and the Meta+/ groups; `AppKeyboardShortcutsTest`. Lane 2: `keyboard-pointer-drag-and-drop/shortcut-helper--tablet-api37.png`. The desktop main window's File and Go menus list the app-wide shortcuts from the catalog and open them through `AppShortcutRequests` (`AppShortcutMenuTest`, `AppShortcutRouteTest`); the menu bar is drawn by macOS outside the app window, so it is tested rather than captured. |
| `[partial]` | Right-click opens context menus at the pointer; icon-only actions have tooltips | Secondary click opens `ContextMenuArea` at the pointer (`ContextMenuPlacementTest`), on desktop as well as Android; long-press opens it over clickable cards without also clicking (`ContextMenuAreaTest`). Lane 2: `keyboard-pointer-drag-and-drop/journal-context-menu--tablet-api37.png`. `IconTooltip` on the account action and the recorder bubble. Missing: Lane 1 menu and tooltip references. |
| `[partial]` | Text, photos, videos, and audio can be dropped or pasted into the editor | `entryDropTarget` with `routeDroppedItems` (`EntryDropTest`); media is copied into the private store before the drag permission ends. Keyboard paste of media through `entryPasteTarget` (Lane 2: `keyboard-pointer-drag-and-drop/paste-photo--tablet-api37.png`). Missing: touch-menu paste and keyboard GIFs, which need the editor's text field on `TextFieldState`, and a cross-app drag capture. |
| `[partial]` | Photos and videos drag out as readable content URIs | `mediaDragSource` sends FileProvider URIs with global read flags from Library thumbnails. Missing: cross-app drag capture. |
| `[done]` | Stylus handwriting works in the editor | Compose text fields start Android's stylus handwriting on Android 14 and later; verified by `OsSurfaceScreenshotsTest#stylusHandwriting`, which writes "hello" with pen events and waits for it to be typed. Lane 2: `keyboard-pointer-drag-and-drop/stylus-handwriting--tablet-api37.png` (ink) and `stylus-handwriting-typed--tablet-api37.png`. |
| `[done]` | Writing with a pen on the "Write something" card or Home's search pill types into the field it opens | `handwritingDelegator` and `handwritingDelegate` (client/ui) over Compose's handwriting delegation; verified by `OsSurfaceScreenshotsTest#stylusWritingOnControls`. Lane 2: `keyboard-pointer-drag-and-drop/stylus-write-card{,-typed}--tablet-api37.png`, `stylus-search{,-typed}--tablet-api37.png`. |

## Large Screens and Desktop Windowing

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[done]` | Entries and journals open in a new window, beside the current one when there is room | Journal menus (overflow, long-press, right-click) offer "New entry in a new window", started from the activity with `FLAG_ACTIVITY_LAUNCH_ADJACENT`. Lane 2: `large-screens-and-desktop-windowing/side-by-side--tablet-api37.png`. Entries in a day offer "Open in new window" (`NoteContextMenuTest`); Lane 2: `entry-side-by-side--tablet-api37.png`, `keyboard-pointer-drag-and-drop/entry-context-menu--tablet-api37.png`. |
| `[done]` | LogDate runs in Android desktop windowing, with entries in windows of their own | Lane 2: `large-screens-and-desktop-windowing/desktop-windows--tablet-api37.png` (Android 17 tablet emulator with desktop windowing on, via `./run screenshots:desktop`). |
| `[todo]` | The workspace app bar sits in the desktop caption bar | |

## AppFunctions and Cross-Device

| Status | Requirement | Evidence |
| --- | --- | --- |
| `[done]` | Capture AppFunctions: `createTextEntry`, `startVoiceEntry`, and `listJournals` | `createTextEntry` and `listJournals` behind `APP_FUNCTIONS`; `startVoiceEntry` left out by decision (background calls cannot open activities reliably). Evidence: `app-functions/assistant-actions--phone-api37.txt` (registered and called on Android 17), `entry-from-assistant--phone-api37.png`. |
| `[done]` | Read AppFunctions refuse without consent or while locked | `searchEntries` throws `AppFunctionDeniedException` from `assistantReadRefusal` (`AssistantAccessTest`). The Android 17 evidence shows the refusal with consent off and results with it on. `summarizeDay` left out by decision. |
| `[partial]` | Handoff continues an in-progress entry on another device | `MainActivity` opts into Handoff on API 37 (`MainActivity+Handoff.kt`). Editor Handoff is deferred: drafts sync, but not immediately, so it needs an upload on handoff and a wait-for-draft state on arrival; the plan is in `app-functions.md`. |
