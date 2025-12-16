# `:app` Modules

This directory contains modules for final build targets and application entry points.

## Available Applications

- [`:app:compose-main`](./compose-main/README.md) - The main cross-platform LogDate application built with Compose Multiplatform, supporting Android, iOS, and Desktop platforms.
- [`:app:wear`](./wear/README.md) - The companion Wear OS application for quick journaling from smartwatches.

## Architecture

Each application module provides the platform-specific entry points, navigation structure, and dependency injection wiring that connects all feature modules together. The applications themselves contain minimal business logic, delegating instead to the various feature and infrastructure modules in the `:client` directory.

## Build & Run Commands

### Android Main App
```bash
# Build debug APK
./gradlew :app:compose-main:assembleDebug

# Install on connected device
./gradlew :app:compose-main:installDebug
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