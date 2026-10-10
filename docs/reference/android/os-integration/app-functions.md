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

Android 17 Handoff lets an activity continue on another of the person's Android devices. `MainActivity`
already opts in (`MainActivity+Handoff.kt`); the web fallback stays off until logdate.app can rebuild
app state from a URL.

Extending Handoff to `EditorActivity`, so an unfinished entry continues on another device, requires
drafts to sync between devices. Whether they do is checked against `client/sync` before this ships,
and the result is recorded here.
