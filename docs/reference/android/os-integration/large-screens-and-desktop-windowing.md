# Large Screens and Desktop Windowing

Status: partial.

- **Ships today:** the adaptive Home workspace, foldable postures, separate editor windows, video
  picture-in-picture, manifest hygiene, window titles, and "New entry in a new window".
- **Planned:** the caption bar and desktop windowing evidence.

## What it is

LogDate runs on phones, foldables, tablets, Chromebooks, and Android desktop windowing, where apps
open in resizable windows with title bars, much like a laptop. Three things matter on these devices:

1. **Any size, any time.** Apps targeting Android 17 (API 37) can no longer lock orientation or opt
   out of resizing on large screens. Windows are resized by dragging, snapping, and bubbling.
2. **Several windows.** People expect to open an entry in its own window, place two side by side, and
   tell windows apart in Recents and the taskbar.
3. **Window chrome.** In desktop windowing, the system draws a caption bar with close, maximize, and
   minimize controls. Apps can draw their own content into that bar instead of leaving it empty.

The layout system that adapts LogDate's screens to their space is documented in
[`docs/reference/architecture/home-workspace.md`](../../architecture/home-workspace.md). This guide
covers the window and system side.

## What the person sees

- **Chromebooks and desktops.** LogDate installs, and it does not assume a touchscreen.
- **Resizing.** A window can be resized, docked to a keyboard, or bubbled without losing what is on
  screen.
- **Window titles.** Each editor window is named after its entry ("New entry" until it has a title)
  in Recents, the taskbar, and the caption bar.
- **New windows.** A journal's menu (its overflow menu, a long-press, or a right-click on its cover)
  offers "New entry in a new window", which opens the editor beside LogDate in split screen when
  there is room. Launchers and the taskbar offer "New window" for the editor.
- **The caption bar.** In desktop windowing, Search and the account button sit in the caption bar,
  between the system controls.

"New entry in a new window" on a Pixel Tablet running Android 17, started from full screen. LogDate
keeps the left half, and the editor opens on the right, already filed to the journal:

![LogDate's Journals on the left and a new entry's editor on the right, split down the middle, with the editor's journal picker showing Summer in Lisbon](../../../screenshots/android-os-integration/large-screens-and-desktop-windowing/side-by-side--tablet-api37.png)

Titled windows in Recents and the taskbar, and the caption bar, are added as each part ships.

## How it works

### Manifest attributes

All of these are in `app/android-main/src/main/AndroidManifest.xml`.

| Attribute | On | Why |
| --- | --- | --- |
| `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>` | app | Without it, Play treats a touchscreen as required and can hide the app on Chromebooks and desktops |
| `android:configChanges="screenSize\|smallestScreenSize\|screenLayout\|orientation\|density\|keyboard\|keyboardHidden\|navigation"` | `MainActivity`, `EditorActivity` | Resizing, rotating, bubbling, and docking a keyboard recompose instead of recreating the activity |
| `android:resizeableActivity="true"` | both (ships today) | Explicit, although API 37 enforces it on large screens |
| `<layout android:minWidth android:minHeight>` | `EditorActivity` | Stops the editor window from shrinking below a usable size |
| `<property android:name="android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI" android:value="true"/>` | `EditorActivity` | Launchers and the taskbar offer "New window" |

Theme and dark-mode changes (`uiMode`) are deliberately left out of `configChanges`, so the theme
still reloads correctly.

### Windows

- `EditorActivity` is a separate document task (`documentLaunchMode="always"`,
  `taskAffinity=".editor"`), so each entry can have its own window.
- `EditorManager.openNewEditorWindow` and `openEntryInNewWindow` start it. "New entry in a new
  window" passes the current activity as `adjacentTo` when the app is in multi-window mode or the
  window is at least 840dp wide. `EditorManager` then adds `FLAG_ACTIVITY_LAUNCH_ADJACENT` and starts
  the editor from that activity. On Android 12L and later, that request splits the screen even from
  full screen. The launch must come from the activity: from the application context, Android has no
  window to place the editor beside and opens it full screen.
- `EditorActivity` calls `setTaskDescription` with the entry's title, which names the window
  everywhere the system lists it.

### The caption bar

In freeform and desktop windowing:

1. LogDate requests a transparent caption background with
   `WindowInsetsController.setSystemBarsAppearance(APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND, ...)`.
2. The system still draws its controls, and reports where they are through `WindowInsets.captionBar`
   and its bounding rectangles.
3. `WorkspaceAppBar` pads around those rectangles, so Search and the account action sit in the bar
   without overlapping the system controls.

These changes are covered by the Home workspace contract (`checkHomeWorkspaceContract`).

### Desktop windowing evidence

A spike decides how to capture desktop windowing on a Gradle Managed Device. If it cannot be captured
there, the caption bar is verified with Compose screenshots that inject caption insets. Those are
recorded in the checklist as layout-only evidence.

Spike result: pending.

## Flag and rollout

None. These are platform requirements and fixes.

## Privacy

Window titles show the entry title, which is visible in Recents. This matches how other document apps
behave.

## How to test

- A merged-manifest check asserts the attributes above.
- **Manual check:** on an API 37 tablet emulator, resize the window, dock a keyboard, and switch dark
  mode. The editor keeps its text and scroll position, and the theme updates.
- `MultiWindowEditorE2ETest` covers separate editor windows on managed devices.

## Updating screenshots

- Caption-aware app bar scenes are Lane 1 references.
- Recents, the taskbar, side-by-side windows, and desktop windowing are Lane 2 captures, recorded with
  `./run screenshots:os`. They land in
  `docs/screenshots/android-os-integration/large-screens-and-desktop-windowing/`.
