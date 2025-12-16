package app.logdate.client.domain.rewind

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Use case for generating user-friendly titles and labels for rewinds.
 * 
 * This use case is responsible for creating consistent, human-readable titles
 * and labels for rewinds based on their time periods. It follows a standardized
 * format to ensure consistency across the application.
 */
class GenerateRewindTitleUseCase {
    /**
     * Generates a user-friendly title for a rewind based on its time period.
     * 
     * Titles follow these patterns:
     * - Single day: "Your day on Month Day, Year"
     * - Multiple days: "Your week from Month Day, Year to Month Day, Year"
     * 
     * @param startTime Start of the rewind's time period
     * @param endTime End of the rewind's time period
     * @return A human-readable title string
     */
    operator fun invoke(startTime: Instant, endTime: Instant): RewindTitleInfo {
        val timezone = TimeZone.currentSystemDefault()
        val startDate = startTime.toLocalDateTime(timezone).date
        val endDate = endTime.toLocalDateTime(timezone).date
        
        val title = if (startDate == endDate) {
            "Your day on ${formatDate(startDate)}"
        } else {
            "Your week from ${formatDate(startDate)} to ${formatDate(endDate)}"
        }
        
        val year = startTime.toLocalDateTime(timezone).year
        val weekNumber = calculateWeekNumber(startTime)
        val label = "${year}#${weekNumber.toString().padStart(2, '0')}"
        
        return RewindTitleInfo(title, label)
    }
    
    /**
     * Calculates the week number for a given date.
     * 
     * @param date The date to calculate the week number for
     * @return The week number (1-53)
     */
    private fun calculateWeekNumber(date: Instant): Int {
        // Simple implementation - would need to be replaced with proper week calculation
        val timezone = TimeZone.currentSystemDefault()
        val localDate = date.toLocalDateTime(timezone).date
        val dayOfYear = localDate.dayOfYear
        return (dayOfYear / 7) + 1
    }
    
    /**
     * Formats a date for display in rewind titles.
     * 
     * @param date The date to format
     * @return A formatted date string like "January 1, 2025"
     */
    private fun formatDate(date: LocalDate): String {
        return "${
            date.month.name.lowercase()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } ${date.dayOfMonth}, ${date.year}"
    }
}

/**
 * Data class containing title and label information for a rewind.
 * 
 * @param title The human-readable title for display
 * @param label The short label identifier for the rewind
 */
data class RewindTitleInfo(
    val title: String,
    val label: String
)