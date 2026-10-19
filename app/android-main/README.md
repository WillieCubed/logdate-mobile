# `:app:android-main`

**The Android application: the manifest, resources, build variants, and the phone and tablet test
suites.**

## Overview

This module builds the LogDate Android app (`studio.hypertext.logdate`). It contains almost no
Kotlin. The app's code lives in [`:app:compose-main`](../compose-main/README.md) (`androidMain` for
Android-only code) and the `:client:*` modules. This module supplies:

- **`AndroidManifest.xml`**: activities, intent filters, deep links, share targets, services, and
  permissions.
- **Resources**: the launcher icon (adaptive, with a monochrome layer for themed icons), static
  shortcuts (`res/xml/shortcuts.xml`), backup rules, and strings.
- **Build configuration**: application id, signing, and the `debug`, `dogfood`, and `release`
  variants.
- **Tests**: JVM unit tests, Compose screenshot tests, and instrumented tests that run on Gradle
  Managed Devices.

## Architecture

```
src/main/AndroidManifest.xml     entry points and OS integration declarations
src/main/res/xml/shortcuts.xml   static launcher shortcuts and the Direct Share target
src/main/res/xml/*_rules.xml     backup and device-transfer rules
src/debug/                       debug-only manifest additions (network config, test host activities)
src/test/                        JVM unit tests
src/screenshotTest/              Compose preview screenshot tests
src/screenshotTestDebug/reference/  committed screenshot references
src/androidTest/                 instrumented tests (managed devices only)
```

## Key Components

### Application identity

| Variant | Application id | Notes |
| --- | --- | --- |
| `debug` | `studio.hypertext.logdate.debug` | Installs beside a dogfood or release build |
| `dogfood` | `studio.hypertext.logdate` | Signed with the upload key |
| `release` | `studio.hypertext.logdate` | Play version codes come from CI |

The default id comes from `LogDateReleasePlugin` in `build-logic` and can be overridden with
`-Plogdate.applicationId`. The namespace (`app.logdate.client`) is separate from the application id.

XML cannot read the application id directly, so the build generates two string resources:

- `@string/logdate_application_id`, used by `shortcuts.xml` so each variant's shortcuts target the
  variant itself;
- `@string/logdate_account_type`.

### Manifest entry points

| Component | What it does |
| --- | --- |
| `MainActivity` | Launcher, deep links (`logdate://`, `studio.hypertext.logdate://`, and verified `https://logdate.app` links), share intake (`SEND` and `SEND_MULTIPLE` for text, images, and video), and static shortcuts |
| `EditorActivity` | A separate editor window per entry (`documentLaunchMode="always"`) |
| `RemoteCameraActivity` | The camera surface for remote capture |
| Health Connect activity aliases | Permission rationale and usage screens that Health Connect opens |
| `AmbientPromptAlarmReceiver` and `AmbientPromptBootReceiver` | Schedule and restore capture nudges |
| `PhoneDataLayerListenerService` | Receives data from the Wear OS app |
| `LogDateAccountAuthenticatorService` | The Android account for LogDate cloud accounts |

Library modules merge in more: widgets from `:client:feature:android-widgets`, recording and playback
services from `:client:media`, location services from `:client:location`, and the editor's
`FileProvider`.

How each of these reaches a screen is explained in the
[OS integration guide](../../docs/reference/android/os-integration.md).

## Build & Run

```bash
# Build the debug app
./gradlew :app:android-main:assembleDebug

# Install on an emulator only: confirm the serial starts with "emulator-" first
adb devices
adb -s emulator-5554 install -r app/android-main/build/outputs/apk/debug/*.apk
```

Never install on, uninstall from, or clear data on a physical device. A physical phone may hold the
user's real journal under `studio.hypertext.logdate`.

## Testing

### Unit tests (`src/test/`)

```bash
./gradlew :app:android-main:testDebugUnitTest
```

These cover watch sync, media contracts, and screenshot organization rules.

### Screenshot tests (`src/screenshotTest/`)

These render Compose previews to images, so no device is needed.

```bash
# Record or update references
./run test:screenshots:update

# Check current renders against the references
./run test:screenshots
```

- **References** live in `src/screenshotTestDebug/reference/` and are committed.
- **Layout:**
  - `flows/flowNN_<area>/` holds user flows;
  - `components/` holds shared components;
  - `audit/` holds adaptive and workspace audits.
- **Preview matrices** in `common/`:
  - `@ScreenshotPreviewMatrix` renders phone, phone dark, landscape, and tablet;
  - `@LargeScreenAuditPreviewMatrix` adds split, tablet, and desktop window sizes.

See [`docs/testing/screenshot-tests.md`](../../docs/testing/screenshot-tests.md).

### Instrumented tests (`src/androidTest/`)

These need a Gradle Managed Device. `connectedDebugAndroidTest` is forbidden in this repository.

```bash
# Phone and tablet smoke devices, one test class
./gradlew :app:android-main:smokeDevicesGroupDebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.DeepLinkResolverTest
```

The managed devices are defined in the root `build.gradle.kts`. The
[Devices](../../docs/DEVELOPMENT.md#devices) section of the development guide lists every device,
its role in the Studio device testing standard, and the groups (`smokeDevices`, `deviceMatrix`,
`apiMatrix`, `phoneDevices`, and `tabletDevices`).

Tests that capture images (for example `WidgetSetupScreenshotsTest`) write to
`build/outputs/managed_device_android_test_additional_output/`.
