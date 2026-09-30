package app.logdate.client.domain.location.history

import app.logdate.client.location.places.ExternalPlacesProvider
import app.logdate.client.location.places.PlaceSuggestion
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.shared.model.location.SemanticPlace
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.uuid.Uuid

/** Suggestions never assign a visit or confirm a place; those require an explicit edit. */
class SuggestNearbyHistoryPlacesUseCase(
    private val provider: ExternalPlacesProvider,
) {
    private val candidateIds = mutableMapOf<String, String>()

    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        existingPlaces: List<SemanticPlace>,
    ): List<SemanticPlace> {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return emptyList()
        currentCoroutineContext().ensureActive()
        val suggestions =
            try {
                provider.searchNearbyPlaces(Location(latitude, longitude, LocationAltitude(0.0, AltitudeUnit.METERS)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Napier.w("Nearby place alternatives are unavailable", error)
                emptyList()
            }
        currentCoroutineContext().ensureActive()
        return suggestions
            .filter {
                it.name.isNotBlank() && !it.externalId.isNullOrBlank() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
            }.distinctBy { it.externalId }
            .map { suggestion -> toCandidate(suggestion, existingPlaces) }
    }

    private fun toCandidate(
        suggestion: PlaceSuggestion,
        existingPlaces: List<SemanticPlace>,
    ): SemanticPlace {
        val externalId = requireNotNull(suggestion.externalId)
        val existing = existingPlaces.firstOrNull { it.externalId == externalId }
        val id =
            existing?.id?.takeIf { Uuid.parseOrNull(it) != null }
                ?: candidateIds.getOrPut(externalId) { Uuid.random().toString() }
        candidateIds[externalId] = id
        if (existing != null) return existing.copy(id = id, userConfirmed = false)
        return SemanticPlace(
            id = id,
            name = suggestion.name.trim(),
            latitude = suggestion.latitude,
            longitude = suggestion.longitude,
            locality =
                suggestion.address.takeIf {
                    it.isNotBlank() && it != "${suggestion.latitude}, ${suggestion.longitude}"
                },
            externalId = externalId,
            userConfirmed = false,
        )
    }
}
