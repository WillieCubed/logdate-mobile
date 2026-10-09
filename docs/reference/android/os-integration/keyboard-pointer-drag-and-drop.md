# Keyboard, Pointer, Drag and Drop

Status: partial.

- **Ships today:** Ctrl/Cmd+F opens Search from anywhere, and the postcard canvas and media viewer
  have local keys. Text drag sources exist in the timeline and search results.
- **Planned:** the shared shortcut catalog, right-click menus, tooltips, media drag in and out, and
  stylus verification.

## What it is

Android runs on tablets with keyboards, Chromebooks, and desktop-windowing setups with a mouse. On
those devices people expect the same input conventions as any desktop app:

- **Keyboard shortcuts**, discoverable through the system shortcut helper (Meta+/ on Android, the
  menu bar on desktop).
- **Right-click** (secondary click) for the same menu a long-press opens.
- **Hover feedback and tooltips** on icon-only buttons.
- **Drag and drop between apps**: dragging a photo from Files into an entry, or a LogDate photo into
  a chat.
- **Stylus handwriting** in text fields.

## What the person sees

### Keyboard shortcuts

| Shortcut | Action | Where |
| --- | --- | --- |
| Ctrl+N | New entry | Anywhere |
| Ctrl+Shift+N | New editor window | Anywhere |
| Ctrl+Shift+R | Record a voice entry | Anywhere |
| Ctrl+F | Search | Anywhere (ships today) |
| Ctrl+, | Settings | Anywhere |
| Ctrl+1 to Ctrl+5 | Timeline, Places, Journals, Library, Rewind | Home |
| Ctrl+S | Save | Editor |
| Ctrl+Enter | Save and close | Editor |
| Esc | Close | Editor |

On macOS desktop builds, Cmd replaces Ctrl. Pressing Meta+/ on Android lists all of these under
LogDate.

### Pointer

- Right-clicking a journal cover, a media thumbnail, or a postcard opens its menu at the pointer.
  Long-press still works.
- Hovering an icon-only button shows a tooltip naming its action.

### Drag and drop

- Dropping text, photos, videos, or audio onto the editor adds them to the entry. A highlighted drop
  area shows while dragging over it.
- Pasting an image, or inserting a GIF from the keyboard, adds it to the entry.
- Dragging a photo or video out of LogDate into another app sends the actual file, not a path string.

Screenshots are added here as each part ships: the Meta+/ helper, desktop menus, the context menu and
tooltip states, drop states, and a cross-app drag on a tablet.

## How it works

### One shortcut catalog

`AppKeyboardShortcuts` (commonMain) lists each action with its key chord, label, and group. Three
places read it, so a shortcut is defined once:

1. **The Compose key handler.** It replaces the single Ctrl+F handler in
   `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/LogDateNavDisplay.kt` with a lookup
   in the catalog.
2. **`onProvideKeyboardShortcuts`** in `MainActivity` and `EditorActivity`. Android calls it when the
   person opens the shortcut helper, and it returns `KeyboardShortcutGroup`s built from the catalog.
3. **The desktop `MenuBar`** in `app/compose-main/src/desktopMain/.../MainWindow.kt` and
   `EditorWindow.kt`.

The editor's shortcuts are scoped to the editor, so they never fight with text editing keys.

### Right-click

`ContextMenuArea` is an expect/actual in `client/ui`. Its Android version opens only on long-press
today. It also opens on secondary click, using the same pointer handling as
`UniversalSearchResultItem.kt`, which already checks `event.buttons.isSecondaryPressed`.

### Drag and drop

- **Into the editor.** The editor gets a `Modifier.dragAndDropTarget` that accepts `text/*`,
  `image/*`, `video/*`, and `audio/*`. Content URIs from other apps need
  `requestDragAndDropPermissions` before they can be read. Then they go through the same
  `ManagedMediaImporter` path as shared files in `IncomingShare.kt`, which includes rollback if an
  import fails partway. The text field also gets `Modifier.contentReceiver` for paste and keyboard
  media.
- **Out of LogDate.** `DragAndDrop.android.kt` in `client/ui` sends a `ClipData` with a content URI
  from the app's FileProvider (`${applicationId}.provider`). It sets the drag flags
  `DRAG_FLAG_GLOBAL | DRAG_FLAG_GLOBAL_URI_READ`, so the receiving app may read it. Today media drags
  send the URI as plain text, which other apps cannot open.

### Stylus

Compose text fields support stylus handwriting. The editor's text block (`TextBlockContent.kt`) is
checked on a stylus-enabled emulator. Postcards already have their own pressure-aware ink in
`InkCapture.kt`.

## Flag and rollout

None. These are input fixes that apply wherever the hardware exists.

## Privacy

Dragging out shares only the file the person dragged, with read-only access granted for that drag.

## How to test

- **Unit tests:** the catalog has no duplicate chords per scope, and key events map to the right
  actions.
- **Compose UI tests:** right-click opens the menu, and long-press still does.
- **Import tests:** MIME routing, partial-failure rollback, and a specific error for unreadable URIs.
- **Manual check:** on an API 37 tablet emulator with a hardware keyboard and mouse, press Meta+/ and
  drag between LogDate and Files in split screen.

## Updating screenshots

- Desktop menus, menus, tooltips, and drop states are Lane 1 references.
- The shortcut helper, cross-app drags, and stylus input are Lane 2 captures, recorded with
  `./run screenshots:os`. They land in
  `docs/screenshots/android-os-integration/keyboard-pointer-drag-and-drop/`.
