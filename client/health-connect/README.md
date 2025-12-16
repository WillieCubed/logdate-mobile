# Health Connect Module

This module provides a cross-platform abstraction for health-related data, following a local-first architecture pattern.

## Features

- Connection status management for Health Connect
- Sleep data retrieval and analysis
- Day bounds detection based on sleep patterns
- Permission management for health data
- Cross-platform time and date abstractions
- User preference-based day boundary settings
- Local caching of health data
- Works offline when remote data sources are unavailable

## Architecture

This module follows a local-first architecture:

- Single repository interface with platform-specific implementations
- Local data source for caching and offline usage
- Remote data sources for platform-specific health APIs
- Platform-independent model classes
- Dependency injection for platform-specific components

## Key Components

### Repository

- `LocalFirstHealthRepository`: Main interface for health data access
- `DefaultLocalFirstHealthRepository`: Implementation that prioritizes local data

### Data Sources

- `LocalHealthDataSource`: Interface for locally stored health data
- `RemoteHealthDataSource`: Interface for platform health APIs
- `InMemoryLocalHealthDataSource`: In-memory implementation for all platforms
- `AndroidHealthConnectDataSource`: Android Health Connect implementation
- `IosHealthKitDataSource`: iOS HealthKit stub implementation (to be completed)
- `JvmStubRemoteHealthDataSource`: JVM desktop stub implementation

### Models

- `DayBounds`: Represents the start and end times of a user's semantic day
- `TimeOfDay`: Platform-independent representation of a time (similar to LocalTime)
- `SleepSession`: Model representing a sleep session with start and end times
- `SleepStage`: Model representing different stages within a sleep session

### Dependency Injection

- `commonHealthModule`: Common components for all platforms
- `androidHealthModule`: Android-specific components
- `iosHealthModule`: iOS-specific components
- `jvmHealthModule`: JVM desktop-specific components

## Usage

To access health data:

```kotlin
// Inject repository
val healthRepository: LocalFirstHealthRepository = get()

// Access sleep data
val sleepSessions = healthRepository.getSleepSessions(startTime, endTime)

// Get average wake-up and sleep times
val avgWakeUpTime = healthRepository.getAverageWakeUpTime(TimeZone.currentSystemDefault())
val avgSleepTime = healthRepository.getAverageSleepTime(TimeZone.currentSystemDefault())

// Get day bounds for a specific date
val dayBounds = healthRepository.getDayBoundsForDate(date, timeZone)
```

## Local-First Benefits

- **Works Offline**: Data is cached locally for offline access
- **Faster Access**: Local cache provides immediate access to data
- **Battery Efficient**: Reduces need for frequent API calls
- **Cross-Platform**: Consistent API across all platforms
- **Resilient**: Degrades gracefully when remote APIs are unavailable

## Dependencies

- Android Health Connect API (Android platform only)
- Kotlin Coroutines
- Kotlin Datetime
- Koin for dependency injection