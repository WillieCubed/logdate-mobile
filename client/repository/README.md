# `:client:repository`

**Data access and persistence interfaces**

## Overview

Defines the core repository interfaces for accessing and managing application data across all platforms. This module serves as the contract layer between domain logic and data sources, following the repository pattern.

## Architecture

```
Repository Module
├── Journals & Notes
├── Timeline & History
├── User & Accounts
├── Location & Places
└── Shared Interfaces
```

## Key Components

### Core Repositories

- `JournalRepository.kt` - Journal collection management
- `JournalNotesRepository.kt` - Journal entry operations
- `LocationHistoryRepository.kt` - Location tracking data
- `ActivityTimelineRepository.kt` - User activity timeline
- `RewindRepository.kt` - Historical memory generation

### User Management

- `UserStateRepository.kt` - User preferences and state
- `UserDeviceRepository.kt` - Device management
- `AccountRepository.kt` - User account handling
- `PasskeyAccountRepository.kt` - Authentication data

### Content Management

- `JournalContentRepository.kt` - Journal content organization
- `DraftRepository.kt` - Draft entry handling
- `PeopleRepository.kt` - People/entity tracking
- `UserPlacesRepository.kt` - Place management

### Data Quota

- `RemoteQuotaDataSource.kt` - Usage limits tracking

## Features

### Repository Pattern Implementation

- **Clean Architecture**: Separation of concerns
- **Interface-Based Design**: Implementation flexibility
- **Reactive Data Access**: Flow-based data streams
- **Consistent API Surface**: Uniform data access
- **Offline-First Design**: Local-first with remote sync

### Journal & Notes Management

- **Journal CRUD**: Journal creation and management
- **Note Operations**: Multiple note type handling
- **Observables**: Reactive data streams
- **Search & Query**: Advanced data filtering
- **Paging Support**: Efficient data loading

### Location & Timeline Features

- **Location History**: User movement tracking
- **Timeline Data**: Chronological activity data
- **Rewind Generation**: Historical memory access
- **Place Management**: Named location handling
- **Data Filtering**: Date and context filtering

### User & Account Features

- **User Preferences**: User settings storage
- **Account Management**: User account handling
- **Device Management**: Multi-device support
- **Authentication**: Secure access control
- **Data Quotas**: Usage monitoring

## Models

### Core Domain Models

- `JournalNote`: User-created content entities
- `Journal`: Collection of related notes
- `LocationHistoryItem`: User location data
- `UserDevice`: Device registration data
- `EditorDraft`: In-progress content drafts

## Usage Patterns

### Reactive Data Access

```kotlin
// Flow-based data observation
class JournalViewModel(private val repository: JournalRepository) {
    val journals: StateFlow<List<Journal>> = repository.allJournalsObserved
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    fun observeJournal(id: Uuid): Flow<Journal> {
        return repository.observeJournalById(id)
    }
}
```

### Repository Operations

```kotlin
// Journal operations
suspend fun createJournal(title: String): Uuid {
    return journalRepository.create(
        Journal(
            title = title,
            createdAt = Clock.System.now(),
            lastUpdated = Clock.System.now(),
            coverColor = DEFAULT_COVER_COLOR
        )
    )
}

// Notes operations
suspend fun createNote(content: String, journalId: Uuid) {
    val now = Clock.System.now()
    journalNotesRepository.create(
        JournalNote.Text(
            content = content,
            creationTimestamp = now,
            lastUpdated = now
        ),
        journalId
    )
}
```

### Location History

```kotlin
// Location tracking
suspend fun logCurrentLocation(location: Location) {
    locationHistoryRepository.logLocation(
        location = location,
        userId = getUserId(),
        deviceId = getDeviceId(),
        confidence = calculateConfidence(location)
    )
}

// History retrieval
fun observeRecentLocations(): Flow<List<LocationHistoryItem>> {
    return locationHistoryRepository.observeLocationHistory()
        .map { it.take(10) }
}
```

## Dependencies

### Core Dependencies

- `:shared:model` - Shared data models
- `:client:util` - Utility functions
- **Kotlinx Coroutines**: Asynchronous programming
- **Kotlinx DateTime**: Date and time handling
- **Kotlinx Serialization**: Data serialization

## TODOs

### Architecture Improvements
- [ ] Add repository factory interfaces
- [ ] Implement repository decorators for cross-cutting concerns
- [ ] Add repository metrics collection
- [ ] Implement comprehensive error handling strategies
- [ ] Add data integrity verification utilities

### Data Features
- [ ] Add conflict resolution interfaces
- [ ] Implement data validation interfaces
- [ ] Add data migration utilities
- [ ] Implement data backup interfaces
- [ ] Add data version tracking

### Performance Enhancements
- [ ] Add repository caching interfaces
- [ ] Implement pagination utilities
- [ ] Add data prefetching strategies
- [ ] Implement batch operation interfaces
- [ ] Add data throttling utilities

### Integration Features
- [ ] Add repository synchronization interfaces
- [ ] Implement data change notification system
- [ ] Add data export/import interfaces
- [ ] Implement cross-repository transaction support
- [ ] Add repository health check utilities