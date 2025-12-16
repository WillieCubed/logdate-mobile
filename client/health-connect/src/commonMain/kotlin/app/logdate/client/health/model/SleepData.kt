package app.logdate.client.health.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Represents a sleep session with start and end times.
 */
data class SleepSession(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val sourceAppName: String? = null,
    val stages: List<SleepStage> = emptyList()
)

/**
 * Represents a stage of sleep with type and duration.
 */
data class SleepStage(
    val type: SleepStageType,
    val startTime: Instant,
    val endTime: Instant
)

/**
 * Enum representing different types of sleep stages.
 */
enum class SleepStageType {
    UNKNOWN,
    AWAKE,
    LIGHT,
    DEEP,
    REM
}

// DayBounds class moved to its own file
// See DayBounds.kt