package app.logdate.client.domain.recommendation

import app.logdate.client.domain.notes.HasNotesForTodayUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Returns the highest-priority home recommendation based on the current app state.
 *
 * Signals are evaluated in priority order:
 * 1. [HomeRecommendation.CompleteYourDraft] — an unfinished draft is waiting
 * 2. [HomeRecommendation.CaptureToday] — the user has not logged anything today
 * 3. [HomeRecommendation.None] — no action needed
 *
 * To add new signals, inject an additional dependency, combine its Flow here, and add a
 * new [HomeRecommendation] subtype with a corresponding `when` branch.
 */
class GetHomeRecommendationUseCase(
    private val hasNotesForToday: HasNotesForTodayUseCase,
    private val fetchMostRecentDraft: FetchMostRecentDraftUseCase,
) {
    operator fun invoke(): Flow<HomeRecommendation> = combine(
        hasNotesForToday(),
        fetchMostRecentDraft(),
    ) { hasNotes, recentDraft ->
        when {
            recentDraft != null -> HomeRecommendation.CompleteYourDraft(
                draftId = recentDraft.id,
                notePreview = recentDraft.notes
                    .filterIsInstance<JournalNote.Text>()
                    .firstOrNull()?.content,
            )
            !hasNotes -> HomeRecommendation.CaptureToday()
            else -> HomeRecommendation.None
        }
    }
}
