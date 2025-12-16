package app.logdate.client.domain.timeline

import app.logdate.client.domain.notes.HasNotesForTodayUseCase
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Use case that determines whether to show a timeline suggestion banner and which type to show.
 * 
 * This helps encourage users to add content by presenting:
 * - Suggestions for adding content to past moments/days that have limited content
 * - Reminders to document ongoing events that the user might be experiencing now
 */
class GetTimelineBannerUseCase(
    private val notesRepository: JournalNotesRepository,
    private val hasNotesForTodayUseCase: HasNotesForTodayUseCase
) {
    /**
     * Returns a flow that emits the current banner state based on user's content and current context.
     */
    operator fun invoke(): Flow<TimelineBannerResult> {
        val currentDate = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            
        return hasNotesForTodayUseCase()
            .map { hasNotesToday ->
                if (!hasNotesToday) {
                    // No notes today, show an ongoing event banner
                    TimelineBannerResult.ShowBanner(
                        memoryId = "today_${currentDate}",
                        message = "Capture what's happening in your day"
                    )
                } else {
                    // Already has content for today - check if there are days in the past week with limited content
                    // that we could suggest adding to
                    checkForPastDaysWithLimitedContent(currentDate)
                }
            }
    }
    
    private suspend fun checkForPastDaysWithLimitedContent(currentDate: LocalDate): TimelineBannerResult {
        // Here we could implement logic to find days in the recent past that have limited content
        // For simplicity, this implementation always returns a "no banner" result if today already has content
        
        // A more complete implementation would:
        // 1. Check for days in the past week with only 1-2 entries
        // 2. Look for days with special significance (based on photos, calendar events, etc.)
        // 3. Return a PastMoment suggestion for the most promising day
        
        return TimelineBannerResult.NoBanner
    }
}

/**
 * Represents the result of determining whether to show a timeline banner.
 */
sealed class TimelineBannerResult {
    /**
     * No banner should be shown
     */
    data object NoBanner : TimelineBannerResult()
    
    /**
     * A banner should be shown with the specified suggestion
     */
    data class ShowBanner(
        val memoryId: String,
        val message: String,
        val location: String? = null,
        val people: List<String> = emptyList()
    ) : TimelineBannerResult()
}