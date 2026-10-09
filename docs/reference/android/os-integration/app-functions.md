# AppFunctions

Status: planned, behind `APP_FUNCTIONS`. Requires Android 16 (API 36) or later.

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
| `createTextEntry(text, journalName?)` | Saves a text entry, optionally into a named journal | No |
| `startVoiceEntry()` | Opens the editor already recording | No |
| `listJournals()` | Returns journal names, so an agent can pick one | No |
| `searchEntries(query, dateRange?)` | Returns matching entries | Yes |
| `summarizeDay(date)` | Returns that day's entries for the agent to summarize | Yes |

## Consent and the app lock

Reading someone's journal is far more sensitive than adding to it. Every read function checks two
things on every call:

1. **System search visibility.** The person controls this in Privacy settings. It is the same setting
   that lets LogDate entries appear in system search, read through
   `LogdatePreferencesDataSource.observeSystemSearchVisibilityEnabled()`. The Privacy settings copy
   says that this setting also covers assistants.
2. **The app lock.** If `AndroidBiometricGatekeeper` reports that the app is locked, read functions
   refuse.

A refusal returns a specific error that names which check failed and where to change it. It never
returns partial data.

## How it works

- The functions live in `app/compose-main/src/androidMain/kotlin/app/logdate/client/appfunctions/`.
- They call the existing domain use cases, the same ones the editor and search use, so behavior
  matches the app.
- The `androidx.appfunctions` KSP compiler generates the function metadata Android registers.
- Registration is guarded to `SDK_INT >= 36` and to the `APP_FUNCTIONS` flag.
- `startVoiceEntry` sends the [capture launch contract](capture-launch-contract.md) intent.

The library version is pinned in `gradle/libs.versions.toml`, and the pinned version is recorded here
when the functions ship.

## Flag and rollout

`APP_FUNCTIONS`, off by default. As of mid-2026, Gemini calls AppFunctions only for apps in a private
preview, so functions are verified through Android's own registry rather than through Gemini.

## Privacy

- Capture functions only create; they never return journal content.
- Read functions follow the consent and lock rules above on every call.

## How to test

- Unit tests with fake use cases cover every function, an unknown journal name, visibility off,
  locked, and empty results.
- On an API 36 or 37 emulator, list and call the functions:

  ```bash
  adb -s emulator-5554 shell cmd app_function list-app-functions
  ```

  The exact subcommands vary by platform build. Run `adb -s emulator-5554 shell cmd app_function help`
  for the current list.

## Updating screenshots

- Privacy settings copy is a Lane 1 reference.
- The editor opened by `startVoiceEntry` is a Lane 2 capture.
- `cmd app_function` output is saved as text evidence in
  `docs/screenshots/android-os-integration/app-functions/`.

## Cross-device: Handoff

Android 17 Handoff lets an activity continue on another of the person's Android devices. `MainActivity`
already opts in (`MainActivity+Handoff.kt`); the web fallback stays off until logdate.app can rebuild
app state from a URL.

Extending Handoff to `EditorActivity`, so an unfinished entry continues on another device, requires
drafts to sync between devices. Whether they do is checked against `client/sync` before this ships,
and the result is recorded here.
