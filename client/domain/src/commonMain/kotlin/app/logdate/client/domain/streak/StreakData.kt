package app.logdate.client.domain.streak

/**
 * Holds the current streak state for UI consumption.
 */
data class StreakData(
    val currentStreak: Int = 0,
    val isEnabled: Boolean = true,
)
