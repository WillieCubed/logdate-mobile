# `:app` Modules

This directory contains modules for final build targets and application entry points.

## Available Applications

- [`:app:android-main`](./android-main/README.md) - Android application wrapper and entry point.
- [`:app:compose-main`](./compose-main/README.md) - Shared Compose Multiplatform module for Android/iOS/Desktop UI and app logic.
- [`:app:wear`](./wear/README.md) - The companion Wear OS application for quick journaling from smartwatches.

## Architecture

Each application module provides the platform-specific entry points, navigation structure, and dependency injection wiring that connects all feature modules together. The applications themselves contain minimal business logic, delegating instead to the various feature and infrastructure modules in the `:client` directory.

## Build & Run Commands

### Android Main App
```bash
# Build debug APK
./gradlew :app:android-main:assembleDebug

# Install on an emulator only (check `adb devices` for an emulator-* serial first)
adb -s emulator-5554 install -r app/android-main/build/outputs/apk/debug/*.apk
```

Never install on a physical device; see the Android device safety rules in `CLAUDE.md`.

### Wear OS App
```bash
# Build debug APK
./gradlew :app:wear:assembleDebug

# Run the instrumented tests on the Wear OS managed device
./gradlew :app:wear:wearSmallRoundApi34DebugAndroidTest
```

### Desktop App
```bash
# Run desktop app
./gradlew :app:compose-main:run

# Package desktop app
./gradlew :app:compose-main:packageReleaseDmg        # macOS
./gradlew :app:compose-main:packageReleaseMsi        # Windows
./gradlew :app:compose-main:packageReleaseDeb        # Linux
```

### iOS App
```bash
# Generate Xcode project
./gradlew :app:compose-main:podInstall

# Open in Xcode
open iosApp/iosApp.xcworkspace
```