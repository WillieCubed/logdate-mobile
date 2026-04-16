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

## Android Manifest Requirements

Health Connect has three mandatory manifest declarations that are separate from the runtime permission API. Missing any of these prevents the app from appearing in Health Connect's **App permissions** settings screen.

### 1. Privacy policy URL

```xml
<!-- In app/android-main/src/main/AndroidManifest.xml, inside <application> -->
<meta-data
    android:name="health_connect.privacy_policy_url"
    android:value="https://logdate.app/privacy" />
```

Health Connect reads this to populate its list of registered apps. Without it, the app is invisible in Health Connect settings regardless of which permissions have been granted at runtime.

### 2. `health_permissions` string resource

```xml
<!-- In app/android-main/src/main/res/values/strings.xml -->
<string name="health_permissions">LogDate</string>
```

This label is shown in the Health Connect permission request dialog when the user is asked to approve or deny access. The resource name `health_permissions` is required by convention — Health Connect looks for it by name.

### 3. Activity aliases — two are required

Health Connect uses two different intents depending on the Android version. Both aliases target `MainActivity`, which navigates to the day boundary settings screen.

**Android 13 and earlier** — shown in the HC permission dialog when the user taps the privacy policy / rationale link:

```xml
<!-- In app/android-main/src/main/AndroidManifest.xml, inside <application> -->
<activity-alias
    android:name=".HealthConnectPermissionsRationaleActivity"
    android:exported="true"
    android:targetActivity="app.logdate.client.MainActivity">
    <intent-filter>
        <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
    </intent-filter>
</activity-alias>
```

No permission guard is needed here — the HC permission dialog sends this intent without holding any special permission.

**Android 14+** — sent by the OS / Health Connect settings to open the app's permission management screen. **This alias is what causes the app to appear in Health Connect's "App permissions" list on Android 14+.** Without it the app is invisible in HC settings regardless of all other manifest entries.

```xml
<activity-alias
    android:name=".ViewHealthPermissionUsageActivity"
    android:exported="true"
    android:targetActivity="app.logdate.client.MainActivity"
    android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
    <intent-filter>
        <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
        <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
    </intent-filter>
</activity-alias>
```

`android.permission.START_VIEW_PERMISSION_USAGE` ensures only the Android system (and the OS-integrated HC on API 34+) can fire this intent.

### Where these live in the module graph

The `android.permission.health.READ_SLEEP` permission and the `<queries>` block for the Health Connect provider package are declared in `client/domain/src/androidMain/AndroidManifest.xml` and merge into the final app manifest via Gradle manifest merging. The three entries above must live in the **app-level** manifest because they reference app-level resources and activities.

---

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