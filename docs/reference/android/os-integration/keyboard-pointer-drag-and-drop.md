# Keyboard, Pointer, Drag and Drop

Status: partial.

- **Built:** the shared shortcut catalog (Compose handlers, Android's Meta+/ helper, and the desktop
  menu bar), right-click menus at the pointer, tooltips on the workspace account button, dropping
  text and media from other apps into the editor, and dragging photos and videos out as real files.
- **Planned:** a new-window shortcut and stylus verification.

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
LogDate, here on a Pixel Tablet running Android 17 with a USB keyboard:

![Android's Keyboard shortcuts sheet with LogDate selected in its category list, showing the LogDate group (New entry, Record a voice entry, Search, Settings) and the Home group (Ctrl+1 to Ctrl+3 visible)](../../../screenshots/android-os-integration/keyboard-pointer-drag-and-drop/shortcut-helper--tablet-api37.png)

Android 17 gives the focused app its own category, with the app's icon, next to System and
Multitasking. It lists an app's shortcuts only for keys that the attached keyboard has.

### Pointer

- Right-clicking a journal cover, an entry in a day, a media thumbnail, or a postcard opens its menu
  at the pointer.
- Long-pressing a journal cover, an entry in a day, or a postcard opens the same menu without also
  opening the item. Long-pressing a media thumbnail starts a drag instead (see below), so its menu is
  on right-click.
- An entry's only action is "Open in new window", so it has no menu on devices that cannot open
  another LogDate window.
- Hovering an icon-only button shows a tooltip naming its action.

A long-press on a journal in the tablet's Journals carousel, on Android 17:

![The Journals screen on a tablet with a menu over the Summer in Lisbon cover offering Open journal and New entry in a new window](../../../screenshots/android-os-integration/keyboard-pointer-drag-and-drop/journal-context-menu--tablet-api37.png)

And on an entry in a day, opened beside the timeline:

![A day opened beside the timeline, with an Open in new window menu under its entry](../../../screenshots/android-os-integration/keyboard-pointer-drag-and-drop/entry-context-menu--tablet-api37.png)

### Drag and drop

- Dropping text, photos, videos, or audio onto the editor adds them to the entry. A highlighted drop
  area shows while dragging over it.
- Pasting with a keyboard (Ctrl+V) adds a copied photo, video, or audio file to the entry on
  Android; any copied text is pasted into the text as usual. Pasting an image from the
  touch text menu, and GIFs from on-screen keyboards, do not work yet (see How it works).

A photo copied in another app and pasted with Ctrl+V on a tablet with a USB keyboard:

![A new entry in the editor with the pasted photo of a dog wrapped in a blanket](../../../screenshots/android-os-integration/keyboard-pointer-drag-and-drop/paste-photo--tablet-api37.png)

- Dragging a photo or video out of LogDate into another app sends the actual file, not a path string.

Screenshots are added here as each part ships: desktop menus, the context menu and tooltip states,
drop states, and a cross-app drag on a tablet.

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

Desktop builds get the `App` shortcuts through the same Compose handler, and the main window's menu
bar lists them: New entry and Record a voice entry under File, Search and Settings under Go.
`ShortcutItems` (app/compose-main desktopMain) labels each item and binds its accelerator from the
catalog, with Command as the primary modifier on macOS and Ctrl elsewhere. A menu item does not open
a screen itself; it sends the shortcut through `AppShortcutRequests` to `LogDateNavDisplay`, which
opens it with the same code as the keys (`AppKeyboardShortcut.appRoute`), so a menu item and its keys
cannot disagree, and both do nothing while LogDate is locked.

The editor's shortcuts are scoped to the editor, so they never fight with text editing keys.

### Right-click

`ContextMenuArea` is an expect/actual in `client/ui`. Android, desktop, and (until its native bridge
lands) iOS all use `MaterialContextMenuArea`, which opens on long-press and on a secondary click. A
right-click menu opens at the pointer: `contextMenuOffset` converts the click position into the
dropdown's offset from the item's bottom-left corner.

Long-press needs care because the content inside is usually clickable. Compose delivers pointer
events to the innermost element first in the normal (Main) pass, and a `clickable` card consumes the
press there, so a long-press detector on the wrapper in that pass never fires. The wrapper instead
watches the Initial pass, which runs outermost first, and consumes nothing until the press has lasted
the long-press timeout. Then it opens the menu and consumes the finger lifting, so the card does not
also count the press as a tap. If the finger moves past the touch slop after the menu opens, the menu
closes so a drag can take over. Content that uses long-press itself passes `openOnLongPress = false`.

Journal covers offer "Open journal", plus "New entry in a new window" where the device supports more
than one window, in both the journal list and the tablet carousel.

`IconTooltip` (client/ui) names icon-only controls on hover and long-press. The workspace account
button uses it.

### Drag and drop

- **Into the editor.** `entryDropTarget` (editor module) accepts `text/*`, `image/*`, `video/*`, and
  `audio/*`; `routeDroppedItems` splits a drop into media and text and ignores anything else. Content
  URIs from other apps need `requestDragAndDropPermissions`, and that permission ends when released,
  so each file is copied into LogDate's private store through `ManagedMediaImporter` (the same path
  shared files use) before the permission is released. While an acceptable drag hovers, the editor
  draws a primary-color outline.
- **Pasting media.** `entryPasteTarget` (editor module) watches for the paste keys. When the
  clipboard holds media, it routes the clip through the same `routeDroppedItems` and import as a
  drop (`importIntoEntry`), and attaches the copies. It does not consume the keys, so the text field
  still pastes any text. Compose's `Modifier.contentReceiver`, which would also handle the touch
  paste menu and GIFs from on-screen keyboards, works only with the state-based `BasicTextField`
  (`TextFieldState`). The editor's text block stays on the value-based field because its inline
  Markdown styling is a `VisualTransformation`. The state-based field's `OutputTransformation` can
  change the displayed text but cannot add styles (Compose foundation 1.11), so moving the editor
  would drop its Markdown styling.
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
- **Compose UI tests:** `ContextMenuAreaTest` (client/ui desktopTest) checks that long-press over a
  clickable card opens the menu without clicking, a tap still clicks, right-click opens the menu, and
  content without items or with `openOnLongPress = false` keeps the press.
- **Import tests:** MIME routing, partial-failure rollback, and a specific error for unreadable URIs.
- **Manual check:** on an API 37 tablet emulator with a hardware keyboard and mouse, press Meta+/ and
  drag between LogDate and Files in split screen.

## Updating screenshots

- Desktop menus, menus, tooltips, and drop states are Lane 1 references.
- The shortcut helper, cross-app drags, and stylus input are Lane 2 captures, recorded with
  `./run screenshots:os`. They land in
  `docs/screenshots/android-os-integration/keyboard-pointer-drag-and-drop/`.
- The shortcut helper capture plugs in a USB keyboard through Android's `uinput` shell tool
  (`HardwareKeyboard` in the instrumented tests). Key presses that UiAutomator injects come from
  Android's virtual keyboard, which has no physical keys, and Android 17's helper then drops every app
  shortcut. The test also switches the on-screen keyboards off while it captures, because the
  emulator's Gboard stays up next to a physical keyboard.
