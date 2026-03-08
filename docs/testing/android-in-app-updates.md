# Android In-App Updates Testing

This guide covers how to verify LogDate's Google Play in-app update flow.

For the product behavior and UX policy, see [Android In-App Updates](../feature-design/android-in-app-updates.md).

## What We Support

- Automatic checks after the Android app is loaded, onboarded, and unlocked
- Hybrid prompt policy
- Immediate updates for severe or stale releases
- Flexible updates for normal eligible releases
- A manual `Check for updates` action in `Settings > Advanced`
- A restart prompt when a flexible update has finished downloading

## Behavior Summary

LogDate uses a hybrid Google Play update policy on Android.

- Automatic checks only run after the app is ready for use. The app does not start an update flow during onboarding or while the biometric unlock prompt is still blocking access.
- Immediate updates are reserved for high-priority or stale releases. The current thresholds in code are:
  `updatePriority >= 4` or `clientVersionStalenessDays >= 7`
- Flexible updates are used for lower-severity eligible releases. The current automatic thresholds in code are:
  `updatePriority >= 2` or `clientVersionStalenessDays >= 2`
- If the user dismisses a flexible update, automatic flexible prompts are deferred for 24 hours for that available version.
- Manual checks from `Settings > Advanced` bypass that flexible deferral and can still surface an eligible update.
- When a flexible update finishes downloading, LogDate shows a restart action both in the root app UI and in `Settings > Advanced`.
- Non-Play installs are treated as unsupported. The app does not crash or fake an update state when the build was sideloaded.

## Automated Verification

Run the shared logic tests:

```bash
./gradlew :client:feature:core:desktopTest
```

Compile the Android entrypoint and screenshot coverage:

```bash
./gradlew :app:android-main:compileDebugKotlin
./gradlew :app:android-main:compileDebugScreenshotTestKotlin
```

## Local QA Checklist

Use a debug build on an Android device or emulator:

```bash
./gradlew :app:android-main:installDebug
```

Then verify:

1. Launch the app and complete onboarding if needed.
2. Open `Settings > Advanced`.
3. Confirm the `App Updates` section shows the current version.
4. Tap `Check for updates`.
5. If no Play-managed update is available, confirm the screen reports the app is up to date or explains that the build is not eligible for Play in-app updates.
6. If a flexible update has already downloaded, confirm the section changes to `Restart to update`.

## Play-Distributed QA

In-app updates only work correctly on builds installed by Google Play. Sideloaded APKs are expected to show the unsupported message.

### Prerequisites

- Keep the same `applicationId`: `co.reasonabletech.logdate`
- Upload a build with a higher `versionCode` than the one currently installed
- Use a tester account enrolled in the relevant Play track
- Install the baseline build from Google Play before testing the update

### Flexible Update Path

Use Internal App Sharing or an internal testing track.

1. Install version `N` from Google Play.
2. Publish version `N+1`.
3. Open LogDate on the version `N` build.
4. Wait for the automatic check or use `Settings > Advanced > Check for updates`.
5. Accept the flexible update.
6. Confirm the app remains usable while the download proceeds.
7. Confirm the restart prompt appears after the download completes.
8. Tap `Restart` and verify the app relaunches on version `N+1`.

### Immediate Update Path

Use an internal testing track, not Internal App Sharing. Internal App Sharing does not support `inAppUpdatePriority`.

1. Install version `N` from the internal testing track.
2. Publish version `N+1` to the same track with a high Play update priority.
3. Open LogDate on version `N`.
4. Confirm the immediate update flow appears once the app is loaded and unlocked.
5. Confirm dismissing the flow does not leave the app in a broken state.
6. Reopen the app and confirm an in-progress immediate update resumes when Play reports `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`.
7. Complete the update and verify the installed version is now `N+1`.

## Failure Cases To Check

- Manual check on a sideloaded build shows the unsupported message
- Manual check while offline shows a non-crashing error state
- Canceling a flexible prompt does not immediately re-prompt during the same session
- A downloaded flexible update keeps the restart action visible in `Advanced`
- Backgrounding and reopening the app does not break an in-progress immediate update

## Troubleshooting

- No prompt appears:
  The installed build is probably sideloaded, the Play Store has not propagated the release yet, or the uploaded build does not have a higher `versionCode`.
- Immediate flow is not testable in Internal App Sharing:
  Move the scenario to an internal testing track with Play update priority configured.
- Manual check says the app is up to date but a new build exists:
  Verify the tester account, track enrollment, and Play Store cache state on the device.
