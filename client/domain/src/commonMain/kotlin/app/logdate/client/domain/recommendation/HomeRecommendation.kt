package app.logdate.client.domain.recommendation

import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Represents a home screen recommendation to prompt the user to capture content.
 *
 * Recommendations are ordered by priority (highest first):
 * 1. [CompleteYourDraft] — an unfinished draft is waiting
 * 2. [UpcomingEvent] — an event is starting soon and the user hasn't captured anything for it
 * 3. [MemoryRecall] — entries exist near today's date from a prior year
 * 4. [EmptyDay] — the user has not logged anything today
 * 5. [None] — no action needed
 *
 * Use [GetHomeRecommendationUseCase] to obtain the current highest-priority
 * recommendation as a reactive Flow.
 */
sealed class HomeRecommendation {
    /** No recommendation to show — the user is up to date. */
    data object None : HomeRecommendation()

    /** The user has not added any entries today. */
    data class EmptyDay(
        val message: String = "What's going on?",
        val locationName: String? = null,
    ) : HomeRecommendation()

    /** The user has an unfinished draft that has not been saved as a journal entry. */
    data class CompleteYourDraft(
        val draftId: Uuid,
    ) : HomeRecommendation()

    /** Past entries worth revisiting — "on this day" style recall. */
    data class MemoryRecall(
        val date: LocalDate,
        val summary: String,
        val people: List<String> = emptyList(),
        val mediaUris: List<String> = emptyList(),
        val isAiGenerated: Boolean = false,
    ) : HomeRecommendation()

    /**
     * An event is starting soon and the user hasn't captured anything for it yet. Surfaced as
     * a "Capture for this" home card so the user can jump straight into recording.
     */
    data class UpcomingEvent(
        val eventId: Uuid,
        val title: String,
        val startTime: Instant,
        val placeName: String? = null,
    ) : HomeRecommendation()
}
