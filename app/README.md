# `:app` Modules

This directory contains modules for final build targets and application entry points.

## Available Applications

- [`:app:android-main`](./android-main/) - Android application wrapper and entry point.
- [`:app:compose-main`](./compose-main/README.md) - Shared Compose Multiplatform module for Android/iOS/Desktop UI and app logic.
- [`:app:wear`](./wear/README.md) - The companion Wear OS application for quick journaling from smartwatches.

## Architecture

Each application module provides the platform-specific entry points, navigation structure, and dependency injection wiring that connects all feature modules together. The applications themselves contain minimal business logic, delegating instead to the various feature and infrastructure modules in the `:client` directory.

## Build & Run Commands

### Android Main App
```bash
# Build debug APK
./gradlew :app:android-main:assembleDebug

# Install on connected device
./gradlew :app:android-main:installDebug
```

### Wear OS App
```bash
# Build debug APK
./gradlew :app:wear:assembleDebug

# Install on connected Wear OS device/emulator
./gradlew :app:wear:installDebug
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