package app.logdate.client.domain.rewind

import app.logdate.client.text.UiText
import app.logdate.client.text.resolve
import app.logdate.client.text.toUiText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.domain.generated.resources.Res
import logdate.client.domain.generated.resources.rewind_title_date
import logdate.client.domain.generated.resources.rewind_title_day
import logdate.client.domain.generated.resources.rewind_title_month_april
import logdate.client.domain.generated.resources.rewind_title_month_august
import logdate.client.domain.generated.resources.rewind_title_month_december
import logdate.client.domain.generated.resources.rewind_title_month_february
import logdate.client.domain.generated.resources.rewind_title_month_january
import logdate.client.domain.generated.resources.rewind_title_month_july
import logdate.client.domain.generated.resources.rewind_title_month_june
import logdate.client.domain.generated.resources.rewind_title_month_march
import logdate.client.domain.generated.resources.rewind_title_month_may
import logdate.client.domain.generated.resources.rewind_title_month_november
import logdate.client.domain.generated.resources.rewind_title_month_october
import logdate.client.domain.generated.resources.rewind_title_month_september
import logdate.client.domain.generated.resources.rewind_title_week
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/**
 * Use case for generating user-friendly titles and labels for rewinds.
 *
 * This use case is responsible for creating consistent, human-readable titles
 * and labels for rewinds based on their time periods. It follows a standardized
 * format to ensure consistency across the application.
 *
 * The title is saved with the Rewind, so it is written once in the device language at
 * generation time.
 *
 * @param resolveText Turns the chosen title text into words. Tests pass a stand-in because they
 *   cannot read Compose resources.
 */
class GenerateRewindTitleUseCase(
    private val resolveText: suspend (UiText) -> String = { text -> text.resolve() },
) {
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
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
    ): RewindTitleInfo {
        val timezone = TimeZone.currentSystemDefault()
        val startDate = startTime.toLocalDateTime(timezone).date
        val endDate = endTime.toLocalDateTime(timezone).date

        val title =
            if (startDate == endDate) {
                Res.string.rewind_title_day.toUiText(startDate.toTitleDate())
            } else {
                Res.string.rewind_title_week.toUiText(startDate.toTitleDate(), endDate.toTitleDate())
            }

        val year = startTime.toLocalDateTime(timezone).year
        val weekNumber = calculateWeekNumber(startTime)
        val label = "$year#${weekNumber.toString().padStart(2, '0')}"

        return RewindTitleInfo(resolveText(title), label)
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
     * Describes a date for a rewind title, such as "January 1, 2025".
     */
    private fun LocalDate.toTitleDate(): UiText = Res.string.rewind_title_date.toUiText(month.titleName.toUiText(), day, year)

    private val Month.titleName: StringResource
        get() =
            when (this) {
                Month.JANUARY -> Res.string.rewind_title_month_january
                Month.FEBRUARY -> Res.string.rewind_title_month_february
                Month.MARCH -> Res.string.rewind_title_month_march
                Month.APRIL -> Res.string.rewind_title_month_april
                Month.MAY -> Res.string.rewind_title_month_may
                Month.JUNE -> Res.string.rewind_title_month_june
                Month.JULY -> Res.string.rewind_title_month_july
                Month.AUGUST -> Res.string.rewind_title_month_august
                Month.SEPTEMBER -> Res.string.rewind_title_month_september
                Month.OCTOBER -> Res.string.rewind_title_month_october
                Month.NOVEMBER -> Res.string.rewind_title_month_november
                Month.DECEMBER -> Res.string.rewind_title_month_december
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
    val label: String,
)
