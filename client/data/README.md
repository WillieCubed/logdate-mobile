# `:client:data`

**Data layer implementation with repository pattern**

## Overview

Implements the data layer of clean architecture, providing repositories that abstract data sources (local database, remote API, cache). Handles data mapping between different layers and manages offline-first data synchronization.

## Architecture

```
Data Layer
├── Repository Implementations
├── Data Sources (Local/Remote)
├── Object Mappers
├── Sync Logic
└── Platform Configurations
```

## Key Components

### Repository Implementations
- `OfflineFirstJournalRepository.kt` - Journal data management
- `OfflineFirstJournalNotesRepository.kt` - Notes data management
- `OfflineFirstEntryDraftRepository.kt` - **Enhanced draft management with auto-save**
- `LocalEntryDraftStore.kt` - **Platform-agnostic draft storage interface**

### Data Sources
- `FirebaseRemoteJournalDataSource.kt` - Remote data sync
- Local database integration via DAOs
- Cache management implementations

### Object Mappers
- `JournalObjectMappers.kt` - Journal entity conversions
- `NoteObjectMappers.kt` - Note entity conversions
- Domain ↔ Entity ↔ Network model conversions

### Draft Management
- **OfflineFirstEntryDraftRepository**: In-memory StateFlow + persistent storage
- **LocalEntryDraftStore**: Platform-agnostic storage interface
- **Automatic draft lifecycle**: Create → Update → Delete on save
- **Real-time reactivity**: StateFlow-based draft state management
- **Error resilience**: Graceful handling of storage failures

### Knowledge Management
- People and entity extraction
- Content analysis and categorization
- Intelligent data organization

## Features

### Offline-First Architecture
- Local data persistence priority
- Background synchronization
- Conflict resolution strategies
- Network state awareness

### Data Synchronization
- Incremental sync optimization
- Bi-directional data flow
- Conflict detection and resolution
- Sync status tracking

### Performance Optimization
- Intelligent caching strategies
- Lazy loading for large datasets
- Pagination support
- Memory-efficient data handling
- **Optimized draft operations** with in-memory StateFlow caching

## Dependencies

### Core Dependencies
- `:client:database` - Local data persistence
- `:client:networking` - Remote API communication
- `:shared:model` - Shared data models

### External Dependencies
- **Kotlinx Coroutines**: Asynchronous operations
- **Kotlinx Serialization**: Data serialization
- **Koin**: Dependency injection
- **Firebase Firestore**: Remote data storage

## Data Flow

```
UI Layer (Editor)
    ↓
Repository (this module)
    ↓
┌─────────────┬─────────────┬─────────────┐
│ Local DB    │ Remote API  │ Draft Store │
│ (primary)   │ (sync)      │ (auto-save) │
└─────────────┴─────────────┴─────────────┘
```

### Draft Data Flow
```
Editor Content Change
    ↓
OfflineFirstEntryDraftRepository
    ↓
┌─────────────┬─────────────┐
│ StateFlow   │ Local Store │
│ (memory)    │ (persistent)│
└─────────────┴─────────────┘
    ↓
Draft State Updates (Reactive)
```

## Testing

This module includes comprehensive test coverage for all repository implementations:

```bash
# Run all data module tests
./gradlew :client:data:test

# Run tests with detailed output
./gradlew :client:data:test --continue --info

# Run specific test class
./gradlew :client:data:test --tests "OfflineFirstJournalRepositoryTest"

# Run tests by platform
./gradlew :client:data:testDebugUnitTest    # Android
./gradlew :client:data:jvmTest             # JVM/Desktop
```

### Test Coverage
- **OfflineFirstJournalRepositoryTest**: Journal CRUD operations, draft management
- **OfflineFirstJournalNotesRepositoryTest**: Note creation, pagination, filtering
- **OfflineFirstEntryDraftRepositoryTest**: Draft lifecycle, persistence, error handling

## Platform Support

- **Android**: Full feature implementation
- **iOS**: Core features with platform adaptations
- **Desktop**: Full feature implementation

## TODOs

### Sync & Conflict Resolution
- [ ] Implement advanced conflict resolution strategies
- [ ] Add real-time sync capabilities
- [ ] Implement selective sync options
- [ ] Add sync performance monitoring
- [ ] Implement sync bandwidth management

### Data Management
- [x] **Enhanced draft repository with reactive StateFlow** (✅ Completed)
- [x] **Automatic draft cleanup after save** (✅ Completed)
- [x] **Robust draft error handling** (✅ Completed)
- [ ] Add data encryption for sensitive content
- [ ] Implement data compression for large entries
- [ ] Add data validation and integrity checks
- [ ] Implement data export/import functionality
- [ ] Add data archival and cleanup

### Performance & Monitoring
- [ ] Implement data access metrics
- [ ] Add caching performance optimization
- [ ] Implement data usage analytics
- [ ] Add data quality monitoring
- [ ] Implement data migration utilities