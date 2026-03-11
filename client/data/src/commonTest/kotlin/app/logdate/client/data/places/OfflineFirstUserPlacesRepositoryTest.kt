package app.logdate.client.data.places

import app.logdate.client.database.dao.UserPlaceDao
import app.logdate.client.database.entities.UserPlaceEntity
import app.logdate.shared.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineFirstUserPlacesRepositoryTest {
    @Test
    fun `getPlacesNear filters to requested radius and sorts by distance`() =
        runTest {
            val dao =
                FakeUserPlaceDao(
                    listOf(
                        userPlace(id = "00000000-0000-0000-0000-000000000001", name = "Home", latitude = 37.77495, longitude = -122.41945),
                        userPlace(id = "00000000-0000-0000-0000-000000000002", name = "Coffee", latitude = 37.7752, longitude = -122.4196),
                        userPlace(id = "00000000-0000-0000-0000-000000000003", name = "Airport", latitude = 37.6213, longitude = -122.3790),
                    ),
                )
            val repository = OfflineFirstUserPlacesRepository(dao)

            val result =
                repository.getPlacesNear(
                    latitude = 37.7749,
                    longitude = -122.4194,
                    radiusMeters = 100.0,
                )

            assertEquals(listOf("Home", "Coffee"), result.map { it.name })
            assertTrue(result.all { place -> place !is Place.UserDefined || place.displayName != "Airport" })
        }
}

private class FakeUserPlaceDao(
    places: List<UserPlaceEntity>,
) : UserPlaceDao {
    private val state = MutableStateFlow(places)

    override suspend fun getAllPlaces(): List<UserPlaceEntity> = state.value

    override fun observeAllPlaces(): Flow<List<UserPlaceEntity>> = state

    override suspend fun getPlaceById(placeId: String): UserPlaceEntity? = state.value.firstOrNull { it.id == placeId }

    override suspend fun getPlacesInBoundingBox(
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
    ): List<UserPlaceEntity> =
        state.value.filter { place ->
            place.latitude in minLatitude..maxLatitude &&
                place.longitude in minLongitude..maxLongitude
        }

    override suspend fun searchPlaces(query: String): List<UserPlaceEntity> =
        state.value.filter { place -> place.name.contains(query, ignoreCase = true) }

    override suspend fun insertPlace(place: UserPlaceEntity) {
        state.value = state.value + place
    }

    override suspend fun updatePlace(place: UserPlaceEntity) {
        state.value = state.value.map { existing -> if (existing.id == place.id) place else existing }
    }

    override suspend fun deletePlace(place: UserPlaceEntity) {
        state.value = state.value.filterNot { existing -> existing.id == place.id }
    }

    override suspend fun deletePlaceById(placeId: String) {
        state.value = state.value.filterNot { existing -> existing.id == placeId }
    }
}

private fun userPlace(
    id: String,
    name: String,
    latitude: Double,
    longitude: Double,
) = UserPlaceEntity(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = 100.0,
    description = null,
    createdAt = 1L,
    updatedAt = 1L,
)
