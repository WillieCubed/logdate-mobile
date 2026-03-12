package app.logdate.client.domain.recommendation

import app.logdate.client.domain.notes.HasNotesForTodayUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.places.PlaceResolutionResult
import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.repository.journals.JournalNote
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Returns the highest-priority home recommendation based on the current app state.
 *
 * Signals are evaluated in priority order:
 * 1. [HomeRecommendation.CompleteYourDraft] — an unfinished draft is waiting
 * 2. [HomeRecommendation.MemoryRecall] — past entries worth revisiting
 * 3. [HomeRecommendation.EmptyDay] — the user has not logged anything today
 * 4. [HomeRecommendation.None] — no action needed
 */
class GetHomeRecommendationUseCase(
    private val hasNotesForToday: HasNotesForTodayUseCase,
    private val fetchMostRecentDraft: FetchMostRecentDraftUseCase,
    private val getMemoryRecall: GetMemoryRecallUseCase,
    private val clientLocationProvider: ClientLocationProvider,
    private val resolveLocationToPlace: ResolveLocationToPlaceUseCase,
) {
    private val locationNameFlow: Flow<String?> =
        clientLocationProvider.currentLocation
            .map { location ->
                try {
                    when (val result = resolveLocationToPlace(location)) {
                        is PlaceResolutionResult.UserDefinedPlace -> result.place.name
                        is PlaceResolutionResult.ExternalSuggestion -> result.suggestion.name
                        is PlaceResolutionResult.UnknownLocation -> null
                    }
                } catch (e: Exception) {
                    Napier.w("Failed to resolve location for suggestion", e)
                    null
                }
            }.onStart { emit(null) }

    operator fun invoke(): Flow<HomeRecommendation> =
        combine(
            hasNotesForToday(),
            fetchMostRecentDraft(),
            getMemoryRecall(),
            locationNameFlow,
        ) { hasNotes, recentDraft, recall, locationName ->
            when {
                recentDraft != null ->
                    HomeRecommendation.CompleteYourDraft(
                        draftId = recentDraft.id,
                        notePreview =
                            recentDraft.notes
                                .filterIsInstance<JournalNote.Text>()
                                .firstOrNull()
                                ?.content,
                    )
                recall != null ->
                    HomeRecommendation.MemoryRecall(
                        date = recall.date,
                        summary = recall.summary,
                        people = recall.people,
                        mediaUris = recall.mediaUris,
                    )
                !hasNotes ->
                    HomeRecommendation.EmptyDay(
                        locationName = locationName,
                    )
                else -> HomeRecommendation.None
            }
        }
}
