# Capture Launch Contract

Status: planned. Today, the editor opens in a specific mode only through widget extras.

## What it is

Many surfaces need to say "open the editor ready to record", or "open the editor with the camera
on": launcher shortcuts, Quick Settings tiles, notification actions, assistants, keyboard shortcuts,
and other apps. The capture launch contract is the single way they all say it.

It has two parts:

- **`EditorLaunchMode`**, a field on the editor's route that says what the editor prepares when it
  opens.
- **`app.logdate.action.CAPTURE`**, a public intent action (and matching `logdate://capture` deep
  link) that any surface can send.

A shared contract keeps every surface consistent. It also gives tests one resolver to cover, and
lets a new surface start capture without inventing new intent extras.

## What the person sees

Whichever surface they use, the editor opens already doing the thing they asked for:

| Mode | The editor opens with |
| --- | --- |
| `Text` | An empty text block and the keyboard up |
| `Voice` | An audio block, already recording |
| `Camera` | A camera block showing the live preview |
| `Video` | A camera block in video mode |
| `PhotoPrompt(uri)` | The chosen photo and a writing prompt (used by the New entry widget) |

Screenshots of each mode are added here when the contract ships.

## How it works

### The route

`EntryEditorRoute` in
`client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/navigation/EditorNavRoute.kt`
gains `launchMode: EditorLaunchMode`. `NoteEditorScreen` reads it once, when the editor first
composes, and calls the matching view model initializer. The existing initializers are
`initializeWidgetAudio`, `initializeWidgetCamera`, and `initializePhotoPrompt`, plus a new video
initializer.

Before the contract, the route carried three widget-specific fields: `widgetRecord`, `widgetCamera`,
and `widgetPhotoUri`. The launch mode replaces them. A test checks that a back stack saved by an older
version, with the old fields, still restores without crashing.

### The intent

```text
action:  app.logdate.action.CAPTURE
extras:  mode        text | voice | camera | video    optional, defaults to text
         journal_id  journal UUID                     optional, pre-selects that journal
```

The equivalent deep link is:

```text
logdate://capture?mode=voice&journal=<uuid>
```

`resolveMainActivityNavKey` in
`app/compose-main/src/androidMain/kotlin/app/logdate/client/DeepLinkResolver.kt` checks the capture
action before anything else. An unknown `mode` falls back to `Text` rather than failing, so a newer
shortcut cannot break an older app.

### Legacy extras that keep working

Widgets that are already on a home screen and shortcuts that are already pinned carry the old extras.
They must keep resolving:

| Old intent | Resolves to |
| --- | --- |
| `EXTRA_NAV_SOURCE=widget_new_entry` with `widget_record=true` | `Voice` |
| `EXTRA_NAV_SOURCE=widget_new_entry` with `widget_camera=true` | `Camera` |
| `EXTRA_NAV_SOURCE=widget_new_entry` with `widget_photo_uri` | `PhotoPrompt(uri)` |
| `ambient_prompt_target=new_entry` | `Text` |

### The lock

Capture never skips the app lock. `LogDateNavDisplay` adds the editor route to the back stack only
after onboarding is complete and the app is unlocked.

## Flag and rollout

None. The contract is plumbing that existing surfaces move onto. It changes no visible behavior on
its own.

## Privacy

A capture intent can only create. It never returns journal data to the caller.

## How to test

- Host tests in `app/compose-main/src/androidHostTest/` cover every mode, an unknown mode, a journal
  pre-selection, and every legacy extra.
- `DeepLinkResolverTest` runs the same cases on a managed device:

  ```bash
  ./gradlew :app:android-main:smokeDevicesGroupDebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.DeepLinkResolverTest
  ```

- On an emulator, once the contract ships:

  ```bash
  adb -s emulator-5554 shell am start -a app.logdate.action.CAPTURE --es mode voice
  ```

## Updating screenshots

There is one Lane 1 editor scene per launch mode in the `flow2x_os_integration` screenshot flow.
Record them with `./run test:screenshots:update`, then check them with
`./run screenshots:check` and `./run screenshots:sheet capture-launch-contract`.
