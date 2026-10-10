# Keyboard, Pointer, Drag and Drop

Status: partial.

- **Built:** the shared shortcut catalog (Compose handlers and Android's Meta+/ helper), right-click
  menus at the pointer, tooltips on the workspace account button, dropping text and media from other
  apps into the editor, and dragging photos and videos out as real files.
- **Planned:** desktop menu bar entries from the catalog, a new-window shortcut, and stylus
  verification.

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
| Ctrl+Shift+R | Record a voice entry | Anywhere |
| Ctrl+F | Search | Anywhere |
| Ctrl+, | Settings | Anywhere |
| Ctrl+S | Save entry | Editor |
| Esc | Close the editor | Editor |
| Ctrl+1 to Ctrl+5 | Open the first to fifth Home tab, in the order the navigation shows them | Home |

Planned: Ctrl+Shift+N for a new editor window.

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

`AppKeyboardShortcut` in `client/ui/src/commonMain/kotlin/app/logdate/ui/keyboard/AppKeyboardShortcuts.kt`
lists each action with its key chord, label, and scope (`App` or `Editor`). A chord's "primary"
modifier matches either Ctrl or Command, so one catalog works on every platform. Three places read
it, so a shortcut is defined once:

1. **The Compose key handler.** It replaces the single Ctrl+F handler in
   `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/LogDateNavDisplay.kt` with a lookup
   in the catalog.
2. **`onProvideKeyboardShortcuts`** in `MainActivity` and `EditorActivity`. Android calls it when the
   person opens the shortcut helper, and `appKeyboardShortcutGroups` (client/ui androidMain) turns
   the catalog into `KeyboardShortcutGroup`s named "LogDate" and "Entry editor".
3. **The editor**, whose `EntryEditorContent` handles the `Editor` scope: Ctrl+S saves when the
   Save button would, and Esc behaves like the back button.
4. **Home**, whose `HomeScreen` handles the `Home` scope. Each tab shortcut carries a `homeTabIndex`,
   counted in the visible tabs, so Ctrl+4 still opens the fourth tab shown when Library is hidden.

Desktop builds get the `App` shortcuts through the same Compose handler. Listing them in the desktop
`MenuBar` is planned.

The editor's shortcuts are scoped to the editor, so they never fight with text editing keys.

### Right-click

`ContextMenuArea` is an expect/actual in `client/ui`. Its Android version opens on long-press and on
a secondary click. A right-click menu opens at the pointer: `contextMenuOffset` converts the click
position into the dropdown's offset from the item's bottom-left corner. Journal covers offer "Open
journal", plus "New entry in a new window" where the device supports more than one window.

`IconTooltip` (client/ui) names icon-only controls on hover and long-press. The workspace account
button uses it.

### Drag and drop

- **Into the editor.** `entryDropTarget` (editor module) accepts `text/*`, `image/*`, `video/*`, and
  `audio/*`; `routeDroppedItems` splits a drop into media and text and ignores anything else. Content
  URIs from other apps need `requestDragAndDropPermissions`, and that permission ends when released,
  so each file is copied into LogDate's private store through `ManagedMediaImporter` (the same path
  shared files use) before the permission is released. While an acceptable drag hovers, the editor
  draws a primary-color outline. Paste and keyboard media through `contentReceiver` are planned.
- **Out of LogDate.** `mediaDragSource` in `client/ui` sends a `ClipData` with a content URI from the
  app's FileProvider (`${applicationId}.provider`, which covers the private media store) and the
  file's MIME type. It sets `DRAG_FLAG_GLOBAL | DRAG_FLAG_GLOBAL_URI_READ`, so the receiving app may
  read that one file. Library thumbnails use it.

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
