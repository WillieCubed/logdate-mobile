# `:client:ui`

**Shared UI components and utilities**

## Overview

Provides a comprehensive set of reusable UI components, layouts, and utilities for consistent design across all platforms. This module serves as the foundation for the application's user interface, implementing the design system and offering common UI patterns.

## Architecture

```
UI Module
├── Core Components
├── Timeline Components
├── Layout System
├── Shared Transitions
└── Platform Adaptations
```

## Key Components

### Core UI Components

- `AdaptiveLayout.kt` - Responsive layout system
- `FunButton.kt` - Styled button components
- `GenericLoadingScreen.kt` - Loading states
- `SearchAppBar.kt` - Search interface component
- `SensorActiveDisplay.kt` - Sensor visualization

### Timeline Components

- `TimelinePane.kt` - Primary timeline display
- `TimelineLine.kt` - Timeline visual connector
- `NewTimelineItem.kt` - Timeline entry component
- `TimelineDaySelection.kt` - Date selection
- `HomeTimelineUiState.kt` - Timeline state management

### Layout Utilities

- `PlatformDimensions.kt` - Cross-platform dimensions
- `Modifiers.kt` - Common modifier extensions
- `PaddingValues.kt` - Standardized spacing
- `MaterialContainer.kt` - Material design containers

### User Interaction

- `SharedTransitions.kt` - Animation definitions
- `PlatformBackHandler.kt` - Back navigation handling
- `MainWrapperScaffold.kt` - Application scaffold

## Features

### Core UI System

- **Adaptive Layouts**: Responsive design across screen sizes
- **Theming Support**: Material 3 design system implementation
- **Component Library**: Consistent reusable components
- **Animation System**: Shared motion and transitions
- **Interactive Elements**: Buttons, search, and selection

### Timeline Visualization

- **Timeline Display**: Chronological data visualization
- **Day Selection**: Interactive date selection
- **People Visualization**: Person and entity display
- **Content Cards**: Journal and note display
- **Timeline Navigation**: Scroll and navigation controls

### Layout Frameworks

- **Responsive Design**: Adapts to different screen sizes
- **Padding System**: Consistent spacing throughout app
- **Material Integration**: Material 3 components and styles
- **Platform Adaptation**: Platform-specific optimizations
- **Accessibility Support**: Accessible UI components

### UI Utilities

- **Format Utilities**: Date and time formatting
- **Layout Helpers**: Padding and alignment utilities
- **List Utilities**: List handling and display
- **Transition Keys**: Animation coordination
- **Modifier Extensions**: Reusable modifier functions

## Dependencies

### Core Dependencies

- `:client:theme` - Design system implementation
- `:client:util` - Utility functions
- `:client:sensor` - Sensor integration
- `:shared:model` - Shared data models
- **Compose**: UI framework
- **Material 3**: Design system components
- **Material 3 Adaptive**: Responsive components
- **Navigation Compose**: Navigation architecture
- **Coil**: Image loading

## Usage Patterns

### Adaptive Layout

```kotlin
@Composable
fun MainScreen(useCompactLayout: Boolean) {
    AdaptiveLayout(
        useCompactLayout = useCompactLayout,
        supplementalContent = {
            // Sidebar or supplementary content
            NavigationPanel()
        },
        mainContent = {
            // Primary content area
            ContentArea()
        }
    )
}
```

### Timeline Component

```kotlin
@Composable
fun TimelineScreen(timelineData: List<TimelineDayUiState>) {
    TimelinePane(
        uiState = TimelineUiState(items = timelineData),
        onOpenDay = { date -> 
            // Handle day selection
        },
        onNewEntry = {
            // Handle new entry creation
        },
        onShareMemory = { memoryId ->
            // Handle memory sharing
        }
    )
}
```

### UI Utilities

```kotlin
@Composable
fun CustomComponent() {
    Box(
        modifier = Modifier
            .padding(PaddingValues.Spacing.md)
            .conditional(isHighlighted) {
                background(MaterialTheme.colorScheme.primaryContainer)
            }
    ) {
        // Component content
    }
}
```

## Platform-Specific Implementations

```kotlin
// Platform dimensions implementation
expect class PlatformDimensions() {
    val statusBarHeight: Dp
    val navigationBarHeight: Dp
    val defaultPadding: Dp
}

// Android implementation
actual class PlatformDimensions actual constructor() {
    actual val statusBarHeight: Dp = 24.dp
    actual val navigationBarHeight: Dp = 48.dp
    actual val defaultPadding: Dp = 16.dp
}
```

## TODOs

### Core Components
- [ ] Implement comprehensive component testing
- [ ] Add component playground/showcase
- [ ] Improve component documentation
- [ ] Add more customization options
- [ ] Implement theme switching support

### Timeline Components
- [ ] Optimize timeline rendering performance
- [ ] Add more timeline visualization options
- [ ] Implement infinite scrolling optimization
- [ ] Add timeline filtering components
- [ ] Improve timeline accessibility

### Layout System
- [ ] Enhance responsive layout system
- [ ] Add more breakpoint definitions
- [ ] Implement layout metrics tracking
- [ ] Add support for different form factors
- [ ] Improve large screen support

### Animation System
- [ ] Add more shared transitions
- [ ] Implement motion system guidelines
- [ ] Add staggered animation support
- [ ] Improve transition performance
- [ ] Add gesture-driven animations