package app.logdate.client.domain.places

import app.logdate.client.location.places.ExternalPlacesProvider
import app.logdate.client.location.places.PlaceSuggestion
import app.logdate.client.location.places.UnavailableReverseGeocodingProvider
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.shared.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for [ResolveLocationToPlaceUseCase].
 *
 * Verifies that the use case correctly resolves a geographical location to a [Place],
 * including fallback logic when user-defined places or external providers are unavailable.
 */
class ResolveLocationToPlaceUseCaseTest {
    @Test
    fun `falls back to external places when user place lookup fails`() =
        runTest {
            val location =
                Location(
                    latitude = 37.7749,
                    longitude = -122.4194,
                    altitude = LocationAltitude(0.0, AltitudeUnit.METERS),
                )
            val externalSuggestion =
                PlaceSuggestion(
                    name = "Blue Bottle Coffee",
                    address = "66 Mint St, San Francisco, CA",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    confidence = 92,
                )
            val useCase =
                ResolveLocationToPlaceUseCase(
                    userPlacesRepository = ThrowingUserPlacesRepository(),
                    externalPlacesProvider = FakeExternalPlacesProvider(listOf(externalSuggestion)),
                    reverseGeocodingProvider = UnavailableReverseGeocodingProvider(),
                )

            val result = useCase(location)

            val suggestion = assertIs<PlaceResolutionResult.ExternalSuggestion>(result)
            assertEquals(externalSuggestion.name, suggestion.suggestion.name)
        }

    @Test
    fun `returns unknown location when all place providers fail`() =
        runTest {
            val location =
                Location(
                    latitude = 37.7749,
                    longitude = -122.4194,
                    altitude = LocationAltitude(0.0, AltitudeUnit.METERS),
                )
            val useCase =
                ResolveLocationToPlaceUseCase(
                    userPlacesRepository = ThrowingUserPlacesRepository(),
                    externalPlacesProvider = ThrowingExternalPlacesProvider(),
                    reverseGeocodingProvider = UnavailableReverseGeocodingProvider(),
                )

            val result = useCase(location)

            val unknown = assertIs<PlaceResolutionResult.UnknownLocation>(result)
            assertEquals(location.latitude, unknown.location.latitude)
            assertEquals(location.longitude, unknown.location.longitude)
        }
}

/**
 * A [UserPlacesRepository] that throws an error for most operations, used to test fallback logic.
 */
private class ThrowingUserPlacesRepository : UserPlacesRepository {
    override suspend fun getAllPlaces(): List<Place> = emptyList()

    override fun observeAllPlaces(): Flow<List<Place>> = emptyFlow()

    override suspend fun getPlacesNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): List<Place> = error("database unavailable")

    override suspend fun getPlaceById(placeId: String): Place? = null

    override suspend fun createPlace(place: Place): Result<Place> = Result.success(place)

    override suspend fun updatePlace(place: Place): Result<Place> = Result.success(place)

    override suspend fun deletePlace(placeId: String): Result<Unit> = Result.success(Unit)

    override suspend fun searchPlaces(query: String): List<Place> = emptyList()
}

/**
 * A fake [ExternalPlacesProvider] that returns a predefined list of suggestions.
 */
private class FakeExternalPlacesProvider(
    private val suggestions: List<PlaceSuggestion>,
) : ExternalPlacesProvider {
    override suspend fun searchNearbyPlaces(location: Location): List<PlaceSuggestion> = suggestions
}

/**
 * An [ExternalPlacesProvider] that throws an error, used to test error handling and fallback logic.
 */
private class ThrowingExternalPlacesProvider : ExternalPlacesProvider {
    override suspend fun searchNearbyPlaces(location: Location): List<PlaceSuggestion> = error("network unavailable")
}
