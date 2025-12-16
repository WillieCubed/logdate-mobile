# `:client:feature:core`

**Core application features and main navigation structure**

## Overview

Provides the main application structure, navigation, and essential features like home screen, settings, account management, and user preferences. This module serves as the foundation for the app's UI and user flow.

## Architecture

```
Core Feature Module
├── Main Application Structure
├── Account Management
├── Settings
├── Home Experience
└── Platform Integrations
```

## Key Components

### Main Application

- `HomeScreen.kt` - Main app navigation hub
- `AppViewModel.kt` - Application-wide state management
- `HomeRouteDestination.kt` - Navigation destinations
- `HomeViewModel.kt` - Home screen business logic

### Account & Authentication

- `CloudAccountOnboardingScreen.kt` - Cloud account setup
- `PasskeyAccountCreationScreen.kt` - Passkey creation flow
- `PasskeyAuthenticationScreen.kt` - Login with passkeys
- `BiometricGatekeeper.kt` - Device biometric security

### Settings System

- `SettingsViewModel.kt` - Settings state management
- `SettingsOverviewScreen.kt` - Main settings screen
- `AccountSettingsScreen.kt` - Account management
- `PrivacySettingsScreen.kt` - Privacy controls
- `DataSettingsScreen.kt` - Data management
- `DevicesScreen.kt` - Connected device management

### Platform Integration

- `CoreFeatureModule.kt` - Dependency injection
- `ExportLauncher.kt` - Platform-specific export functions
- `BiometricGatekeeper.kt` - Platform biometric integration

## Features

### Home Experience

- **Multi-Tab Navigation**: Timeline, journals, rewind, and location tabs
- **Floating Action Button**: Context-aware for creating new content
- **Timeline View**: Chronological display of user activity
- **Unified Navigation**: Consistent experience across all platforms

### Account Management

- **Passkey Authentication**: Modern, secure login without passwords
- **Cloud Account Creation**: Seamless onboarding for new users
- **Profile Management**: User identity and preferences
- **Multiple Device Support**: Synchronization across devices

### Settings & Preferences

- **Comprehensive Settings**: Organized by category
- **Account Management**: User profile and credentials
- **Privacy Controls**: Data sharing and visibility options
- **Device Management**: Connected device oversight
- **Data Export**: Platform-specific data export capabilities
- **Danger Zone**: Account deletion and data reset options

### Security

- **Biometric Authentication**: Platform-specific biometric security
- **Secure Storage**: Protected preferences and settings
- **Access Controls**: Feature-level permission management
- **Privacy Features**: Data protection and control

## Dependencies

### Core Dependencies

- `:client:domain` - Business logic
- `:client:ui` - Shared UI components
- `:client:repository` - Data access
- `:client:permissions` - Permission handling
- `:client:datastore` - Preferences storage
- **Compose Multiplatform**: UI framework
- **Material 3 Adaptive**: Responsive design components

### Feature Dependencies

- `:client:feature:timeline` - Timeline functionality
- `:client:feature:rewind` - Rewind features
- `:client:feature:journal` - Journal management
- `:client:feature:location-timeline` - Location history

## Usage Patterns

### Navigation Structure

```kotlin
@Composable
fun HomeScreen(
    onNewEntry: () -> Unit,
    onOpenJournal: JournalClickCallback,
    onCreateJournal: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    HomeScaffoldWrapper {
        // Content based on current destination
    }
}
```

### Settings Integration

```kotlin
val settingsViewModel: SettingsViewModel = koinViewModel()

// Access user preferences
val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

// Settings screens
AccountSettingsScreen(
    uiState = uiState,
    onUpdateSettings = { settingsViewModel.updateSettings(it) }
)
```

## Dependency Injection

```kotlin
// In CoreFeatureModule.kt
actual val coreFeatureModule: Module = module {
    includes(domainModule)
    includes(devicesModule)
    
    single<BiometricGatekeeper> { AndroidBiometricGatekeeper() }
    single<ExportLauncher> { AndroidExportLauncher(get(), get()) }
    
    viewModel { AppViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { CloudAccountOnboardingViewModel(get(), get(), get()) }
}
```

## TODOs

### Core Features
- [ ] Add comprehensive documentation for all components
- [ ] Implement deep linking support
- [ ] Add comprehensive accessibility features
- [ ] Improve tablet and large screen support
- [ ] Add support for theme customization

### Account Features
- [ ] Enhance passkey management
- [ ] Implement account recovery options
- [ ] Add multi-account support
- [ ] Improve device synchronization
- [ ] Add account security audit features

### Settings Improvements
- [ ] Add preference migration utilities
- [ ] Implement backup/restore for settings
- [ ] Add more granular privacy controls
- [ ] Improve export format options
- [ ] Add import functionality

### User Experience
- [ ] Improve onboarding flow
- [ ] Add contextual help throughout the app
- [ ] Implement user feedback collection
- [ ] Add usage analytics settings
- [ ] Improve performance monitoring