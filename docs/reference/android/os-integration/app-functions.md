# AppFunctions

Status: built, behind `APP_FUNCTIONS` (Settings > Advanced > Developer tools > Feature previews), and
verified on an Android 17 emulator. Requires Android 16 (API 36) or later. Library:
`androidx.appfunctions` 1.0.0-alpha10.

## What it is

**AppFunctions** let an app offer actions to on-device agents and assistants, such as Gemini. An app
declares functions with typed parameters and results. Android registers them, and an agent can then
call them on the person's behalf. For example, "add to my journal that the hike was beautiful".

AppFunctions are an Android platform API with a Jetpack library, `androidx.appfunctions`. They replace
the older App Actions `<capability>` bindings that `shortcuts.xml` used to carry. Those bindings were
removed because they pointed at a class that no longer exists.

## What the person can ask for

| Function | What it does | Needs consent? |
| --- | --- | --- |
| `createTextEntry(text)` | Saves a text entry, dated now, and returns it | No |
| `listJournals()` | Returns each journal's id and title, so an agent can name one | No |
| `searchEntries(query)` | Returns up to 10 matching entries, best match first, each shortened to one line | Yes |

The entry an assistant saves appears in the timeline like any other:

![The timeline's Today section showing "Walked the long way home along the river.", saved by an assistant](../../../screenshots/android-os-integration/app-functions/entry-from-assistant--phone-api37.png)

Two functions from the original plan were left out on purpose:

- **`startVoiceEntry`.** An assistant calls a function from the background, and Android does not let a
  background call open an activity reliably. Voice entries start from the Record voice shortcut,
  tile, and widget instead.
- **`summarizeDay`.** It would hand over a whole day of writing at once. `searchEntries` returns only
  what matches, under the same consent.

## Consent and the app lock

Reading someone's journal is far more sensitive than adding to it. `searchEntries` checks two things
on every call:

1. **System search visibility.** The person controls this in Privacy settings. It is the same setting
   that lets LogDate entries appear in Android search, read through
   `LogdatePreferencesDataSource.getSystemSearchVisibilityEnabled()`.
2. **The app lock.** If `BiometricGatekeeper` reports that LogDate still needs unlocking in this
   session, the call is refused.

A refusal throws `AppFunctionDeniedException` with a message that says which check failed and what the
person can do; it never returns partial data. The decision is `assistantReadRefusal`
(app/compose-main commonMain, `client/appfunctions/AssistantAccess.kt`), a pure function with tests.

## How it works

- `LogDateAppFunctions` (app/compose-main androidMain, `client/appfunctions/`) is an abstract
  `AppFunctionService` annotated `@AppFunctionServiceEntryPoint`. Each function's KDoc is its
  description for agents (`isDescribedByKDoc = true`).
- The AppFunctions KSP compiler, applied to the module's Android target as `kspAndroid`, generates the
  concrete `LogDateAppFunctionService` and the schema asset `logdate_app_functions.xml`. The
  multiplatform Android library does not package KSP assets on its own, so `app/compose-main`'s build
  adds that folder to its assets; check that the APK contains `assets/logdate_app_functions.xml`.
- The app manifest declares the generated service with the `android.app.appfunctions.v2` property
  naming that asset, and an application-level `app_metadata` property pointing at
  `res/xml/app_functions_app_metadata.xml`, which tells agents what LogDate is and when to use each
  function.
- The functions call the same use cases the app uses: `AddNoteUseCase`, the current user's journals,
  and the search repository.

## Flag and rollout

`APP_FUNCTIONS`, off by default. The generated service is declared disabled and enabled by
`PlatformComponentToggler` while the flag is on, so Android only indexes the functions then. As of
mid-2026, Gemini calls AppFunctions only for apps in a private preview, so functions are verified
through Android's own registry.

## Privacy

- `createTextEntry` and `listJournals` return nothing from the person's entries.
- `searchEntries` follows the consent and lock rules above on every call, and returns short
  excerpts rather than whole entries.

## How to test

- `AssistantAccessTest` covers the consent and lock decision and the excerpts.
- `OsSurfaceScreenshotsTest#assistantActions` drives the functions on an Android 16+ managed device
  the way an agent would and saves the output as
  `docs/screenshots/android-os-integration/app-functions/assistant-actions--phone-api37.txt`: the
  registered functions, a saved entry, a refused search with consent off, and a successful search
  with consent on.
- By hand, on an emulator with the flag on:

  ```bash
  adb -s emulator-5554 shell cmd app_function list-app-functions
  ```

  ```bash
  adb -s emulator-5554 shell "cmd app_function execute-app-function --package studio.hypertext.logdate.debug --function 'app.logdate.client.appfunctions.LogDateAppFunctions#createTextEntry' --parameters '{\"text\":[\"A note from adb\"]}'"
  ```

  Each parameter is a one-element JSON array.

## Updating screenshots

`./run screenshots:os` records the text evidence and the timeline capture under
`docs/screenshots/android-os-integration/app-functions/`.

## Cross-device: Handoff

Android 17 Handoff lets an activity continue on another of the person's Android devices. While an
activity is open, Android shows its app icon on the person's other nearby devices; picking it there
opens the same activity on that device. `MainActivity` opts in (`MainActivity+Handoff.kt`); the web
fallback stays off until logdate.app can rebuild app state from a URL.

### Editor Handoff

With `EDITOR_HANDOFF` on, an editor window can move too: start an entry on the phone, pick LogDate on
the tablet, and the tablet opens the same unfinished entry.

The hard part is that Handoff carries only a small bundle of extras, never the entry itself. The entry
travels as a **draft** through backup's draft sync (`DraftUploader` and `DraftDownloader` in
`client/sync`), which normally runs on backup's schedule. So the two devices each do one extra thing:

- **The sending device** (`EditorActivity.onHandoffActivityDataRequested`) puts the draft's id in the
  extras (`editorHandoffFor` decides what to hand off). When the request is active, meaning the
  person just picked LogDate on the other device, it also saves the latest text and runs draft sync
  at once, instead of waiting for backup's schedule. An empty editor hands off as a new entry. Text
  typed in the last couple of seconds, before autosave made its first draft, cannot be handed off
  yet; Android shows that Handoff failed, and trying again a moment later works.
- **The receiving device** opens `EditorActivity` with that id (`EXTRA_HANDOFF_DRAFT_ID`).
  `HandoffDraftGate` opens the draft at once when it is already on the device. Otherwise
  `HandoffDraftReceiver` runs draft sync every few seconds for up to a minute, through the
  `DraftRefresher` interface that `SyncDraftRefresher` implements, while the editor shows that the
  entry is on its way. If it never comes, the editor says why, with a different message for backup
  being off, a sync error (with its message), and a draft the other device has not uploaded. Try
  again starts the wait over, and Start a new entry stops waiting.

| Opened from another device | When the draft has not arrived |
| --- | --- |
| ![The editor showing the handed-off entry about the ridge trail](../../../screenshots/android-os-integration/large-screens-and-desktop-windowing/editor-handoff-arrived--tablet-api37.png) | ![The editor explaining that the entry has not arrived because backup is off on this device, with Start a new entry and Try again](../../../screenshots/android-os-integration/large-screens-and-desktop-windowing/editor-handoff-missing--phone-api37.png) |

The flag only controls whether an editor offers itself to other devices. An editor that a handoff
launches always opens its draft, so a device with the flag off can still receive one.

**Testing.** `HandoffDraftReceiverTest` and `HandoffDraftGateTest` (editor module) cover waiting,
arrival, each missing reason, Try again, and Start a new entry. `EditorHandoffTest` and
`SyncDraftRefresherTest` (compose-main) cover what is handed off and how a draft sync result maps to
a reason. `OsSurfaceScreenshotsTest#editorHandoff` runs both ends on one Android 17 managed device: it
types an entry, checks that the editor offers Handoff and that the offered extras name its draft
(saved as `editor-handoff-data--<device>-api37.txt`), then launches the editor with those extras the
way Android does on a second device. A real two-device handoff needs two devices signed in to the
same Google account and LogDate account, which managed emulators cannot provide; that check is the
flag's remaining step before it turns on.
