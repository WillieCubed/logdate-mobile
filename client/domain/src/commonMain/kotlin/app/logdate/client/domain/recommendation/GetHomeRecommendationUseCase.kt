package app.logdate.client.domain.recommendation

import app.logdate.client.domain.notes.HasNotesForTodayUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.places.PlaceResolutionCache
import app.logdate.client.domain.places.PlaceResolutionResult
import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.places.GeocodedAddress
import app.logdate.client.repository.journals.JournalNote
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
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
@OptIn(ExperimentalCoroutinesApi::class)
class GetHomeRecommendationUseCase(
    private val hasNotesForToday: HasNotesForTodayUseCase,
    private val fetchMostRecentDraft: FetchMostRecentDraftUseCase,
    private val getMemoryRecall: GetMemoryRecallUseCase,
    private val clientLocationProvider: ClientLocationProvider,
    private val placeResolutionCache: PlaceResolutionCache,
    private val memoriesSettingsRepository: MemoriesSettingsRepository,
) {
    private val locationNameFlow: Flow<String?> =
        clientLocationProvider.currentLocation
            .distinctUntilChanged { a, b -> a.distanceTo(b) < 100.0 }
            .mapLatest { location ->
                try {
                    when (val result = placeResolutionCache.resolve(location)) {
                        is PlaceResolutionResult.UserDefinedPlace -> result.place.name
                        is PlaceResolutionResult.ExternalSuggestion -> result.suggestion.name
                        is PlaceResolutionResult.CoarseLocation ->
                            formatCoarseLocation(result.address)
                        is PlaceResolutionResult.UnknownLocation -> null
                    }
                } catch (e: Exception) {
                    Napier.w("Failed to resolve location for suggestion", e)
                    null
                }
            }.onStart { emit(null) }

    private fun formatCoarseLocation(address: GeocodedAddress): String? =
        when {
            address.thoroughfare != null && address.locality != null ->
                "Near ${address.thoroughfare}, ${address.locality}"
            address.subLocality != null && address.locality != null ->
                "${address.subLocality}, ${address.locality}"
            address.locality != null -> "Somewhere in ${address.locality}"
            address.adminArea != null -> "Somewhere in ${address.adminArea}"
            else -> null
        }

    operator fun invoke(): Flow<HomeRecommendation> =
        memoriesSettingsRepository.observeSettings().flatMapLatest { settings ->
            if (!settings.contextualRecommendationsEnabled) {
                return@flatMapLatest flowOf(HomeRecommendation.None)
            }
            combine(
                hasNotesForToday(),
                fetchMostRecentDraft(),
                getMemoryRecall(aiEnabled = settings.aiRecallEnabled),
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
}
