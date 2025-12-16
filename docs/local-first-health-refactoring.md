# Local-First Health Module Refactoring

## Overview

This document summarizes the refactoring of the health-connect module to follow a local-first architecture pattern, providing better offline support, improved performance, and a simpler API.

## Key Changes

1. **Local-First Repository**
   - Created a single `LocalFirstHealthRepository` interface as the main API
   - Implemented `DefaultLocalFirstHealthRepository` with local-first pattern
   - Prioritizes cached data before hitting platform APIs

2. **Data Source Interfaces**
   - `HealthDataSource`: Base interface for health data sources
   - `LocalHealthDataSource`: Interface for local cache
   - `RemoteHealthDataSource`: Interface for platform health APIs

3. **Platform-Specific Implementations**
   - `AndroidHealthConnectDataSource`: Uses Android's Health Connect API
   - `IosHealthKitDataSource`: Stub for iOS HealthKit integration
   - `JvmStubRemoteHealthDataSource`: Stub for desktop platforms
   - `InMemoryLocalHealthDataSource`: Common local cache implementation

4. **Simplified Domain Layer**
   - Updated `GetDayBoundsUseCase` to use the new repository
   - Created `HealthDomainModule` for dependency injection
   - Integrated with existing domain module

5. **Key Benefits**
   - Offline support through local caching
   - Improved performance by reducing API calls
   - Simplified API with a single repository interface
   - Better separation of concerns
   - Cross-platform consistency
   - Improved error handling

## Implementation Details

### Repository Pattern

The local-first pattern implemented in `DefaultLocalFirstHealthRepository` follows this flow:

1. Try to fetch data from local cache first
2. If local data is unavailable or invalid, fetch from remote source
3. Cache remote data locally for future use
4. Properly handle errors and fallback to reasonable defaults

Example:
```kotlin
override suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int): TimeOfDay? =
    withContext(ioDispatcher) {
        try {
            // Try to get from local cache first
            val localWakeUpTime = localDataSource.getAverageWakeUpTime(timeZone)
            if (localWakeUpTime != null) {
                return@withContext localWakeUpTime
            }
            
            // If not in cache, try remote
            if (remoteDataSource.isAvailable() && remoteDataSource.hasSleepPermissions()) {
                val remoteWakeUpTime = remoteDataSource.getAverageWakeUpTime(timeZone, days)
                
                // Cache the result
                if (remoteWakeUpTime != null) {
                    localDataSource.storeAverageWakeUpTime(timeZone, remoteWakeUpTime)
                }
                
                return@withContext remoteWakeUpTime
            }
            
            // If remote is not available, return null
            null
        } catch (e: Exception) {
            Napier.e("Error getting average wake-up time", e)
            null
        }
    }
```

### Dependency Injection

The module is designed to be easily integrated with the application's DI system:

```kotlin
// Common module
val commonHealthModule = module {
    single<LocalHealthDataSource> { InMemoryLocalHealthDataSource() }
    single<LocalFirstHealthRepository> { 
        DefaultLocalFirstHealthRepository(
            localDataSource = get(),
            remoteDataSource = get(),
            preferencesDataSource = get(),
            ioDispatcher = get(IoDispatcher)
        )
    }
}

// Platform-specific modules
val androidHealthModule = module {
    single<RemoteHealthDataSource> {
        AndroidHealthConnectDataSource(androidContext())
    }
}
```

### Cache Invalidation

The local cache implementation includes a simple cache invalidation strategy:

1. Each cached item is timestamped when added
2. Cache is considered invalid after a configurable period (default: 12 hours)
3. Cache can be manually cleared if needed

## Usage Example

Using the repository from application code:

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

## Future Improvements

1. **Persistent Local Storage**: Replace in-memory cache with database-backed storage
2. **More Health Metrics**: Expand beyond sleep data to other health metrics
3. **Sync Mechanism**: Add bidirectional sync for health data between local and remote
4. **Enhanced iOS Implementation**: Complete the iOS HealthKit integration
5. **Battery Optimization**: Implement more sophisticated caching strategies
6. **Privacy Controls**: Add fine-grained privacy controls for health data