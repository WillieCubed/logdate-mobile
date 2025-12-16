# `:client:feature:onboarding`

**User onboarding and first-run experience**

## Overview

Provides a comprehensive onboarding experience for new users, guiding them through setup, feature introduction, and initial content creation. This module handles the critical first-time user experience and returning user re-engagement.

## Architecture

```
Onboarding Feature
├── Onboarding Flow
├── Welcome Screens
├── Feature Introduction
├── First Entry Creation
└── Settings Configuration
```

## Key Components

### Core Components

- `OnboardingRoute.kt` - Navigation graph for onboarding
- `OnboardingViewModel.kt` - State management for onboarding process
- `OnboardingUiState.kt` - UI state representation
- `OnboardingFeatureModule.kt` - Dependency injection

### Onboarding Screens

- `OnboardingStartScreen.kt` - Initial welcome screen
- `OnboardingOverviewScreen.kt` - App feature overview
- `MemorySelectionScreen.kt` - Data import interface
- `EntryCreationScreen.kt` - First journal entry creation
- `OnboardingNotificationScreen.kt` - Notification setup
- `OnboardingCompletionScreen.kt` - Completion celebration

### Returning User Experience

- `WelcomeBackScreen.kt` - Returning user re-engagement
- `WelcomeBackViewModel.kt` - Returning user state management

### Cloud & Backup

- `CloudAccountSetupScreen.kt` - Cloud account creation
- `BackupSyncScreen.kt` - Backup and sync configuration

## Features

### User Onboarding

- **Guided Setup**: Step-by-step introduction to the application
- **Feature Overview**: Introduction to key app capabilities
- **First Entry Creation**: Guided creation of first journal entry
- **Interactive Tutorials**: Learn-by-doing feature education
- **Notification Setup**: Configuration of reminders and alerts

### Cloud Integration

- **Account Creation**: Seamless account creation flow
- **Cloud Sync Setup**: Configuration of sync preferences
- **Backup Configuration**: Data backup explanation and setup
- **Privacy Controls**: Clear privacy information and controls
- **Memory Import**: Importing memories from other services

### Returning User Experience

- **Welcome Back Flow**: Special experience for returning users
- **Account Recovery**: Account and data recovery options
- **Data Migration**: Migration from older app versions
- **Feature Updates**: Introduction to new features

### Data Capture

- **Audio Recording**: Voice entry capabilities
- **Photo Capture**: Image capture for first entries
- **Text Entry**: Rich text input for journal entries
- **Templates**: Starter templates for first entries

## Dependencies

### Core Dependencies

- `:client:domain` - Business logic
- `:client:ui` - Shared UI components
- `:client:repository` - Data access
- `:client:media` - Media handling
- `:client:feature:core` - Core app features
- `:client:billing` - Subscription management
- `:client:intelligence` - AI features
- **Compose Multiplatform**: UI framework
- **Material 3**: Design components

## Usage Patterns

### Onboarding Navigation Graph

```kotlin
fun NavGraphBuilder.onboardingGraph(
    onNavigateBack: () -> Unit,
    onWelcomeBack: () -> Unit,
    onOnboardingComplete: () -> Unit,
    onGoToItem: (route: OnboardingBaseRoute) -> Unit,
) {
    navigation<OnboardingRoute>(
        startDestination = OnboardingStart,
    ) {
        composable<OnboardingStart> {
            OnboardingStartScreen(
                onNext = { onGoToItem(AppOverview) },
                onStartFromBackup = { onGoToItem(OnboardingComplete) },
            )
        }
        // Additional screens in sequence
    }
}
```

### Feature Introduction

```kotlin
@Composable
fun OnboardingOverviewScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    // Feature overview content
    Column {
        // Feature explanations
        Button(onClick = onNext) {
            Text("Continue")
        }
    }
}
```

## Dependency Injection

```kotlin
val onboardingFeatureModule = module {
    viewModel { OnboardingViewModel(get(), get(), get()) }
    viewModel { WelcomeBackViewModel(get()) }
    viewModel { MemorySelectionViewModel(get()) }
    
    // Additional dependencies
    factory { AudioEntryRecorder() }
}
```

## TODOs

### Core Features
- [ ] Add interactive feature tutorials
- [ ] Implement onboarding analytics
- [ ] Add personalization questionnaire
- [ ] Improve account creation flow
- [ ] Add multi-platform onboarding consistency

### User Experience
- [ ] Implement progress tracking
- [ ] Add skip options for power users
- [ ] Improve animation transitions
- [ ] Add onboarding completion badges
- [ ] Implement onboarding state persistence

### Content Creation
- [ ] Add more first entry templates
- [ ] Improve media capture experience
- [ ] Add guided journaling prompts
- [ ] Implement voice-to-text for first entries
- [ ] Add starter journal suggestions

### Integration
- [ ] Improve integration with notification system
- [ ] Add third-party service import options
- [ ] Implement better cloud setup guidance
- [ ] Add onboarding for premium features
- [ ] Improve data migration tools