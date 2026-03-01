package app.logdate.client.domain.recommendation

import kotlin.uuid.Uuid

/**
 * Represents a home screen recommendation to prompt the user to capture content.
 *
 * Recommendations are ordered by priority — [CompleteYourDraft] takes precedence over
 * [CaptureToday]. Use [GetHomeRecommendationUseCase] to obtain the current highest-priority
 * recommendation as a reactive Flow.
 */
sealed class HomeRecommendation {

    /** No recommendation to show — the user is up to date. */
    data object None : HomeRecommendation()

    /** The user has not added any entries today. */
    data class CaptureToday(
        val message: String = "You haven't added any memories today.",
    ) : HomeRecommendation()

    /** The user has an unfinished draft that has not been saved as a journal entry. */
    data class CompleteYourDraft(
        val draftId: Uuid,
        val notePreview: String?,
    ) : HomeRecommendation()
}
