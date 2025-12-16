# Health Connect Refactoring

## Original Issues

1. **Misplaced Functionality**: HealthConnect functionality was in the domain module, violating proper modular architecture.
2. **Mixed Responsibilities**: The `HealthConnectRepository` contained both general Health Connect functionality and sleep-specific features.
3. **Domain Logic in Repository**: The `getDayBoundsForDate` method contained business logic that should be in a use case.
4. **Platform-Specific Dependencies**: Used Java time APIs that aren't compatible across platforms.
5. **No Local Cache**: No local caching strategy, reducing offline capabilities.
6. **Complex Interface Structure**: Multiple specialized interfaces created unnecessary complexity.

## New Local-First Architecture

### Architecture Overview

Implemented a local-first architecture with:
- A single repository interface (`LocalFirstHealthRepository`)
- Separate data source interfaces for local and remote data
- Platform-specific implementations of the remote data source
- In-memory implementation of the local data source
- Dependency injection for platform-specific components

### Module Structure

```
client/
  health-connect/
    src/
      androidMain/
        kotlin/.../datasource/
          AndroidHealthConnectDataSource.kt  # Android Health Connect implementation
        kotlin/.../di/
          AndroidHealthModule.kt             # Android-specific DI module
      iosMain/
        kotlin/.../datasource/
          IosHealthKitDataSource.kt          # iOS HealthKit stub implementation
        kotlin/.../di/
          IosHealthModule.kt                 # iOS-specific DI module
      jvmMain/
        kotlin/.../datasource/
          JvmStubRemoteHealthDataSource.kt   # JVM stub implementation
        kotlin/.../di/
          JvmHealthModule.kt                 # JVM-specific DI module
      commonMain/
        kotlin/.../
          LocalFirstHealthRepository.kt      # Main repository interface
          DefaultLocalFirstHealthRepository.kt # Implementation with local-first pattern
        kotlin/.../datasource/
          HealthDataSource.kt                # Data source interfaces
          InMemoryLocalHealthDataSource.kt   # In-memory local cache implementation
        kotlin/.../di/
          HealthModule.kt                    # Common DI module
          Qualifiers.kt                      # DI qualifiers
        kotlin/.../model/
          DayBounds.kt                       # Cross-platform models
          SleepData.kt
          TimeOfDay.kt
```

### Repository Pattern

1. **Single Repository Interface**:
```kotlin
interface LocalFirstHealthRepository {
    suspend fun isHealthDataAvailable(): Boolean
    suspend fun hasSleepPermissions(): Boolean
    suspend fun requestSleepPermissions(): Boolean
    suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession>
    suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int = 30): TimeOfDay?
    suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int = 30): TimeOfDay?
    suspend fun getDayBoundsForDate(date: LocalDate, timeZone: TimeZone): DayBounds
}
```

2. **Data Source Interfaces**:
```kotlin
interface LocalHealthDataSource : HealthDataSource {
    suspend fun storeSleepSessions(sessions: List<SleepSession>)
    suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession>
    suspend fun storeAverageWakeUpTime(timeZone: TimeZone, wakeUpTime: TimeOfDay)
    suspend fun getAverageWakeUpTime(timeZone: TimeZone): TimeOfDay?
    suspend fun storeAverageSleepTime(timeZone: TimeZone, sleepTime: TimeOfDay)
    suspend fun getAverageSleepTime(timeZone: TimeZone): TimeOfDay?
    suspend fun clearCache()
}

interface RemoteHealthDataSource : HealthDataSource {
    suspend fun hasSleepPermissions(): Boolean
    suspend fun requestSleepPermissions(): Boolean
    suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession>
    suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int = 30): TimeOfDay?
    suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int = 30): TimeOfDay?
}
```

### Local-First Implementation

The implementation follows a local-first pattern:
1. Try to fetch data from local cache first
2. If local data is unavailable or invalid, fetch from remote source
3. Cache remote data locally for future use
4. Properly handle errors and fallback to reasonable defaults

Example (from the implementation):
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

### Platform-Specific Implementations

1. **Android**: Uses Health Connect API
2. **iOS**: Stub implementation (ready for HealthKit integration)
3. **JVM**: Stub implementation for desktop platforms

### Dependency Injection

1. **Common Module**: Provides the repository and local data source
2. **Platform Modules**: Provide platform-specific remote data sources

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

// Android module
val androidHealthModule = module {
    single<RemoteHealthDataSource> {
        AndroidHealthConnectDataSource(androidContext())
    }
}
```

## Benefits of the New Architecture

1. **Simplified API**: Single repository interface with clear methods
2. **Local-First Approach**: Works offline with cached data
3. **Better Performance**: Reduces API calls by using cached data
4. **Cross-Platform**: Consistent API across all platforms
5. **Battery Efficient**: Minimizes expensive health API calls
6. **Resilient**: Graceful degradation when health APIs are unavailable
7. **Extensible**: Easy to add new health data types
8. **Testable**: Clear interfaces make testing straightforward
9. **Maintainable**: Clear separation of concerns
10. **Future-Proof**: Easier to adapt to new platform APIs