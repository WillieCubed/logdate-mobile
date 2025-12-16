# LogDate Utilities Module

This module provides common utility functions and extensions used throughout the LogDate application. It offers cross-platform implementations for common tasks related to dates, times, UUIDs, and other foundational operations.

## Features

### Date and Time Utilities

- **Date Formatting**: Platform-specific date formatting with consistent cross-platform APIs
- **Time Calculations**: Functions for calculating time differences, week numbers, and relative dates
- **ISO 8601 Compliance**: Week calculations following the ISO standard
- **Locale Awareness**: Date formatting respecting device locale settings

### UUID Serialization

- **Kotlin UUID Serialization**: Serialization support for Kotlin's new Uuid type
- **JSON Compatibility**: Integration with kotlinx.serialization for JSON encoding/decoding
- **Cross-Platform Support**: Works consistently across all platforms

## Usage Examples

### Date Formatting

```kotlin
import app.logdate.util.asTime
import app.logdate.util.formatDateLocalized
import app.logdate.util.toReadableDateShort
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Get readable time from an Instant
val now = Clock.System.now()
val timeString = now.asTime // e.g. "3:30 p.m."

// Format a date according to the current locale
val date = LocalDate(2025, 6, 5)
val formattedDate = formatDateLocalized(date) // e.g. "June 5, 2025"

// Get a short readable date (omits year if current year)
val shortDate = date.toReadableDateShort() // e.g. "June 5"
```

### Week Calculations

```kotlin
import app.logdate.util.weekOfYear
import kotlinx.datetime.LocalDateTime

// Get the ISO 8601 week of the year
val dateTime = LocalDateTime(2025, 6, 15, 0, 0)
val weekNum = dateTime.weekOfYear // 24
```

### UUID Serialization

```kotlin
import app.logdate.util.UuidSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

// Use in serializable data classes
@Serializable
data class MyData(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val name: String
)

// Serialize
val data = MyData(Uuid.random(), "Example")
val json = Json.encodeToString(data)

// Deserialize
val parsed = Json.decodeFromString<MyData>(json)
```

## Platform-Specific Implementations

This module uses Kotlin Multiplatform's expect/actual pattern to provide optimal implementations for:

- Android
- iOS
- JVM (Desktop)
- Wasm/JS

Each platform uses the most appropriate native APIs while maintaining a consistent interface.

## Testing

The module includes thorough tests for all utilities to ensure consistent behavior across platforms:

- Date/time formatting tests
- Week calculation tests
- UUID serialization/deserialization tests

Run tests with:

```bash
./gradlew :client:util:test
```