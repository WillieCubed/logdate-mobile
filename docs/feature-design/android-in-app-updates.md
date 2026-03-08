# Android In-App Updates

This document explains the product behavior and UX rules for LogDate's Google Play in-app updates on Android.

## Scope

- Platform: Android only
- Distribution requirement: Google Play installed builds
- Entry points:
  - Automatic check after launch readiness
  - Manual `Settings > Advanced > Check for updates`
  - Global restart snackbar after a flexible update download completes

## Implementation Shape

The Android implementation is split into a small shared contract and a Play-backed Android controller.

- Shared settings contract:
  - `AppUpdateController`
  - `AppUpdateUiState`
  - `AppUpdatePromptPolicy`
- Android integration:
  - `PlayInAppUpdateController` owns Play Core state, flow selection, deferral persistence, and result handling
  - `MainActivity` triggers one automatic check after onboarding and unlock gates clear
  - `MainActivityUiRoot` shows the persistent restart snackbar after a flexible download completes
  - `Advanced Settings` exposes manual checks and mirrors the restart action

## User Experience

LogDate uses a hybrid update strategy so urgent releases can interrupt the user when necessary, while routine releases stay lighter weight.

- Automatic checks only run after the app is fully usable.
- The app never starts an update flow during onboarding.
- The app never starts an update flow while biometric unlock is still blocking access.
- Automatic checks only run once per foreground session.
- Flexible downloads keep the app usable while Google Play downloads the update.
- When a flexible update finishes downloading, LogDate exposes a restart action in two places:
  - A persistent snackbar at the root of the app
  - The `App Updates` card in `Settings > Advanced`

## Prompt Policy

LogDate chooses between Google Play's two supported flows:

- `Immediate`
  - Used for urgent or stale releases
  - Automatic or manual checks choose this flow when:
    - `updatePriority >= 4`, or
    - `clientVersionStalenessDays >= 7`
- `Flexible`
  - Used for lower-severity eligible releases
  - Automatic checks choose this flow when:
    - Google Play allows flexible updates, and
    - the update was not recently deferred, and
    - either `updatePriority >= 2` or `clientVersionStalenessDays >= 2`
  - Manual checks can still choose flexible even after a previous flexible dismissal

## Deferral Rules

Flexible prompts are intentionally less aggressive than immediate prompts.

- If the user cancels a flexible update flow, LogDate defers automatic flexible prompts for 24 hours for that available version.
- The deferral only suppresses automatic checks.
- Manual checks from `Settings > Advanced` bypass the deferral.
- Immediate prompts are never suppressed by the flexible deferral timer.

## Unsupported States

Google Play in-app updates are not available in every install context.

- Sideloaded builds are treated as unsupported.
- Builds that cannot get Play update metadata show an unsupported or error state instead of crashing.
- Non-Android platforms expose an unsupported state through the shared update controller.

## State Surfaces

The user-facing update state can appear as:

- `Idle`: No recent manual check result is being surfaced.
- `Checking`: A manual or automatic check is in progress.
- `UpToDate`: No eligible Play update is available right now.
- `Available`: An update is available and Play is about to show or resume a flow.
- `Downloading`: A flexible update is downloading or install completion is in progress.
- `Downloaded`: A flexible update is ready and waiting for restart.
- `Unsupported`: The current build/install context cannot use Play in-app updates.
- `Error`: The manual check failed or the update flow was canceled before it started.

## Testing

Behavior verification and QA procedures live in [docs/testing/android-in-app-updates.md](../testing/android-in-app-updates.md).
