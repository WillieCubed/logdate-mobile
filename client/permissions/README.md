# `:client:permissions`

**Permission handling and authentication capabilities**

## Overview

Provides a comprehensive permission management system with cross-platform support for runtime permissions, passkey authentication, and biometric security. This module ensures consistent and user-friendly permission handling across all platforms.

## Architecture

```
Permissions Module
├── Permission Management
├── Passkey Authentication
├── Location Permissions
└── Platform-Specific Implementations
```

## Key Components

### Core Components

- `PermissionManager.kt` - Core permission handling interface
- `PasskeyManager.kt` - Passkey authentication interface
- `LocationPermissionState.kt` - Location permission handling
- `PermissionsHooks.kt` - Compose permission hooks

### UI Components

- `LocationPermissionRequiredScreen.kt` - Permission request UI

### Platform Implementation

- `createPermissionManager.kt` - Platform factory function
- Android implementation with Accompanist
- iOS implementation with native APIs
- Desktop implementation with platform capabilities

## Features

### Permission Management

- **Runtime Permissions**: Unified API for requesting permissions
- **Permission Status**: Track permission states across app lifecycle
- **Permission Rationale**: Show explanations for permission requests
- **Permission Hooks**: Compose hooks for permission handling
- **Settings Navigation**: Open app settings for manual permission granting

### Passkey Authentication

- **WebAuthn Support**: Modern passwordless authentication
- **Biometric Integration**: Fingerprint/Face authentication
- **Capability Detection**: Feature detection across platforms
- **Registration Flow**: User-friendly passkey creation
- **Authentication Flow**: Secure login with passkeys

### Location Permissions

- **Location Access**: Request and manage location permissions
- **Permission UI**: User-friendly location permission screens
- **Permission States**: Track location permission status
- **Background Location**: Support for background location usage

## Dependencies

### Core Dependencies

- **Compose Runtime**: Reactive programming
- **Compose Material3**: UI components
- **Kotlinx Coroutines**: Asynchronous operations
- **Kotlinx Serialization**: Data serialization
- **Koin**: Dependency injection
- **Napier**: Logging

### Android Dependencies

- **Accompanist Permissions**: Permission handling
- **Credentials API**: Passkey support
- **AndroidX Activity**: Activity integration

## Usage Patterns

### Permission Requests

```kotlin
class LocationViewModel(private val permissionManager: PermissionManager) {
    fun requestLocationPermission(onResult: (PermissionResult) -> Unit) {
        permissionManager.requestPermission(PermissionType.LOCATION, onResult)
    }
    
    fun checkLocationPermission(): Boolean {
        return permissionManager.isPermissionGranted(PermissionType.LOCATION)
    }
    
    fun observeLocationPermission(): StateFlow<Map<PermissionType, PermissionStatus>> {
        return permissionManager.observePermissions(setOf(PermissionType.LOCATION))
    }
}
```

### Compose Permission Hook

```kotlin
@Composable
fun LocationAwareScreen() {
    val locationPermissionState = rememberLocationPermissionState()
    
    if (!locationPermissionState.hasPermission) {
        LocationPermissionRequiredScreen(
            onPermissionGranted = {
                // Permission granted, show location content
            }
        )
    } else {
        // Show location-enabled content
    }
}
```

### Passkey Authentication

```kotlin
class AuthViewModel(private val passkeyManager: PasskeyManager) {
    suspend fun registerPasskey(options: PasskeyRegistrationOptions): Result<String> {
        return passkeyManager.registerPasskey(options)
    }
    
    suspend fun authenticate(options: PasskeyAuthenticationOptions): Result<String> {
        return passkeyManager.authenticateWithPasskey(options)
    }
    
    suspend fun checkPasskeySupport(): PasskeyCapabilities {
        return passkeyManager.getCapabilities()
    }
}
```

## Dependency Injection

```kotlin
val permissionsModule = module {
    single<PermissionManager> { createPermissionManager() }
    single<PasskeyManager> { PlatformPasskeyManager() }
}
```

## TODOs

### Core Features
- [ ] Add comprehensive permission analytics
- [ ] Implement permission request queueing
- [ ] Add permission caching mechanism
- [ ] Implement permission group handling
- [ ] Add permission request rate limiting

### Passkey Enhancements
- [ ] Improve passkey recovery options
- [ ] Add passkey management UI
- [ ] Implement credential deletion
- [ ] Add support for cross-device authentication
- [ ] Improve passkey error handling

### Permission UI
- [ ] Add customizable permission dialogs
- [ ] Implement permission educational screens
- [ ] Add animated permission explanations
- [ ] Improve permission denial handling
- [ ] Add permission context awareness

### Platform Support
- [ ] Enhance iOS permission handling
- [ ] Improve desktop permission implementations
- [ ] Add watchOS permission support
- [ ] Implement macOS permission handling
- [ ] Add TV platform permission support