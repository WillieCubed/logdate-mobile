package app.logdate.client.domain.dayboundary

/**
 * User preferences for how day boundaries are determined.
 *
 * When [sleepBasedBoundariesEnabled] is true, the app uses Health Connect sleep
 * session data to determine where one day ends and the next begins. When disabled
 * or when no sleep data is available, falls back to user preference or a 4 AM default.
 */
data class DayBoundarySettings(
    val sleepBasedBoundariesEnabled: Boolean = false,
)
