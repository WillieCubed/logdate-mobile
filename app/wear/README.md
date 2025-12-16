# `:app:wear`

**Android Wear OS companion application**

## Overview

Companion application for Wear OS devices that provides quick journaling capabilities directly from the wrist. Allows users to capture quick thoughts, voice notes, and view recent entries in a streamlined interface optimized for small screens.

## Current Status

**🚀 Active Development**: Basic functionality implemented with modern Material 3 for Wear OS.

## Key Components

### Entry Points
- **MainActivity.kt** - Main application activity
- **MainComplicationService.kt** - Watch face complication provider
- **MainTileService.kt** - Quick access tile implementation

### UI Components
- **Theme.kt** - Material 3 for Wear OS theming

## Technology Stack

- **Material 3 for Wear OS**: Modern, expressive UI components designed specifically for wearables
- **Compose for Wear OS**: Declarative UI framework optimized for watch form factors
- **Dynamic Color**: Adapts to watch faces and system themes for cohesive visual integration
- **Horologist**: Specialized Compose components for Wear OS
- **Wear Tiles API**: For quick access tiles

## Build and Installation

To build and run the Wear OS app:

```bash
# Build debug APK
./gradlew :app:wear:assembleDebug

# Install on connected Wear OS device/emulator
./gradlew :app:wear:installDebug
```

For testing:
- Basic UI testing can be done on the emulator
- **Note**: Voice input and microphone features require a physical Wear OS device for testing
- Health Services integration also requires physical hardware

## Features

### Implemented Features
- Basic Material 3 UI for Wear OS
- Core application structure

### Planned Features
- Quick note creation with voice-to-text
- Recent entries viewing
- Simple text input for brief entries
- Sync with main phone application
- Watch complications for quick access
- Haptic feedback for interactions
- Gesture-based navigation
- Always-on display support

### Health Integration (Future)
- Automatic activity logging
- Heart rate context for entries
- Steps and exercise correlation
- Sleep pattern integration

## Target Architecture

```
Wear App
├── UI Layer (Compose for Wear)
│   ├── Main Activity
│   ├── Note Creation Screen
│   └── Entry List Screen
├── ViewModel Layer
│   ├── Entry ViewModel
│   └── Sync ViewModel
├── Local Data Cache
│   ├── Room Database
│   └── DataStore Preferences
└── Sync with Phone App
    ├── Data Layer API
    └── Message Passing
```

## Dependencies

### Wear OS
- Compose for Wear OS
- Wear OS Health Services
- Horologist Compose components
- Wear Input libraries

### Communication
- Data Layer API for phone sync
- Wear Connectivity APIs

### Core Features
- Voice recognition APIs
- Local storage (minimal)
- Background sync services

## Technical Considerations

### Performance
- Minimal UI for battery efficiency
- Aggressive data caching
- Smart sync scheduling
- Low animation overhead

### User Experience
- Large touch targets
- Clear visual hierarchy
- Voice-first interactions
- Quick gestures
- Minimalist design

## Implementation Roadmap

### Phase 1: Basic Functionality
- [x] Set up Compose for Wear UI framework
- [x] Implement Material 3 theming
- [ ] Implement basic note creation screen
- [ ] Add voice-to-text integration (requires physical device for testing)
- [ ] Create simple entry list view
- [ ] Implement data sync with phone app

### Phase 2: Wear OS Integration
- [ ] Add watch complications
- [ ] Implement haptic feedback patterns
- [ ] Add gesture-based navigation
- [ ] Create custom watch faces integration
- [ ] Implement always-on display optimization

### Phase 3: Health & Context
- [ ] Integrate health data (heart rate, steps) - requires physical device
- [ ] Add activity recognition for auto-logging
- [ ] Implement location context for entries
- [ ] Add mood tracking with health correlation
- [ ] Create health insights and summaries

### Phase 4: Advanced Features
- [ ] Add offline voice processing
- [ ] Implement smart entry suggestions
- [ ] Add social features (sharing, collaboration)
- [ ] Create custom workout logging
- [ ] Implement emergency contact features

## Design Guidelines

### UI Principles
- Follow Wear OS design guidelines
- Optimize for glanceable information
- Minimize interaction steps
- Support both touch and voice input
- Use high contrast for outdoor visibility

### Performance Guidelines
- Keep UI animations under 16ms
- Minimize background processing
- Use efficient data structures
- Implement smart caching strategies
- Limit network requests

## Testing Strategy

- [ ] Unit tests for business logic
- [ ] Wear OS emulator testing for basic UI
- [ ] Physical device testing for voice input and sensors
- [ ] Testing across different watch form factors (round, square)
- [ ] Battery life impact testing
- [ ] Sync reliability testing