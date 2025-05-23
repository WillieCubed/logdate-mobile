# `:app:compose-main`

**Main cross-platform application module using Compose Multiplatform**

## Overview

This is the primary application entry point that orchestrates all feature modules into a cohesive user experience. It contains the main navigation structure, dependency injection setup, and platform-specific configurations for Android, iOS, and Desktop platforms.

## Platforms Supported

- **Android**: Target SDK 35, Min SDK 30
- **iOS**: x64, arm64, simulator arm64
- **Desktop**: JVM with native distribution packages (DMG, MSI, DEB)

## Key Dependencies

### Feature Modules
- `:client:feature:core` - Core application features
- `:client:feature:onboarding` - User onboarding flows
- `:client:feature:editor` - Entry creation and editing
- `:client:feature:timeline` - Timeline and browsing
- `:client:feature:journal` - Journal management
- `:client:feature:rewind` - Memory recall features

### Infrastructure Modules
- `:client:data` - Data layer implementation
- `:client:ui` - Shared UI components
- `:client:theme` - Application theming
- `:client:networking` - Network layer
- `:client:sync` - Data synchronization

### External Dependencies
- Compose Multiplatform
- AndroidX Navigation Compose
- Koin (Dependency Injection)
- Napier (Logging)
- FileKit Compose (File operations)

## Build Configuration

### Android
```kotlin
applicationId = "co.reasonabletech.logdate"
versionCode = 1
versionName = "0.1.0"
```

### Desktop
```kotlin
mainClass = "app.logdate.MainKt"
packageName = "LogDate"
description = "The LogDate desktop application"
```

## Architecture

The app module follows a feature-based modular architecture where:
- Each feature is self-contained with its own UI, navigation, and dependencies
- Infrastructure modules provide shared functionality
- Dependency injection wires everything together at the app level

## Entry Points

- **Android**: `MainActivity.kt`
- **iOS**: `MainViewController.kt` 
- **Desktop**: `main.kt`

## TODOs

- [ ] Implement deep linking support
- [ ] Add app shortcuts for quick entry creation
- [ ] Implement backup/restore functionality
- [ ] Add widget support for quick note taking
- [ ] Implement offline sync conflict resolution UI
- [ ] Add accessibility improvements
- [ ] Implement app update mechanism
- [ ] Add telemetry and analytics integration
- [ ] Implement multi-window support for desktop
- [ ] Add app lifecycle state management