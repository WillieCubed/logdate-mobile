package app.logdate.client.data.places

import app.logdate.client.database.dao.UserPlaceDao
import app.logdate.client.database.entities.UserPlaceEntity
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.shared.model.Place
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.Uuid

class OfflineFirstUserPlacesRepository(
    private val userPlaceDao: UserPlaceDao,
) : UserPlacesRepository {
    override suspend fun getAllPlaces(): List<Place> = userPlaceDao.getAllPlaces().map { place -> place.toModel() }

    override fun observeAllPlaces(): Flow<List<Place>> =
        userPlaceDao.observeAllPlaces().map { places ->
            places.map { place -> place.toModel() }
        }

    override suspend fun getPlacesNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): List<Place> = userPlaceDao.getPlacesNear(latitude, longitude, radiusMeters).map { place -> place.toModel() }

    override suspend fun getPlaceById(placeId: String): Place? = userPlaceDao.getPlaceById(placeId)?.toModel()

    override suspend fun createPlace(place: Place): Result<Place> =
        runCatching {
            val userPlace = place.asUserDefinedPlace()
            userPlaceDao.insertPlace(userPlace.toEntity())
            userPlace
        }.onFailure { error ->
            Napier.e("Failed to create user place", error)
        }

    override suspend fun updatePlace(place: Place): Result<Place> =
        runCatching {
            val userPlace = place.asUserDefinedPlace()
            val existingPlace = userPlaceDao.getPlaceById(userPlace.id.toString())
            userPlaceDao.updatePlace(userPlace.toEntity(existingCreatedAt = existingPlace?.createdAt))
            userPlace
        }.onFailure { error ->
            Napier.e("Failed to update user place", error)
        }

    override suspend fun deletePlace(placeId: String): Result<Unit> =
        runCatching {
            userPlaceDao.deletePlaceById(placeId)
        }.onFailure { error ->
            Napier.e("Failed to delete user place", error)
        }

    override suspend fun searchPlaces(query: String): List<Place> = userPlaceDao.searchPlaces(query).map { place -> place.toModel() }

    private fun UserPlaceEntity.toModel() =
        Place.UserDefined(
            id = Uuid.parse(id),
            displayName = name,
            lat = latitude,
            lng = longitude,
            radiusMeters = radiusMeters,
            description = description,
        )

    private fun Place.UserDefined.toEntity(existingCreatedAt: Long? = null): UserPlaceEntity {
        val now = Clock.System.now().toEpochMilliseconds()
        return UserPlaceEntity(
            id = id.toString(),
            name = displayName,
            latitude = lat,
            longitude = lng,
            radiusMeters = radiusMeters,
            description = description,
            createdAt = existingCreatedAt ?: now,
            updatedAt = now,
        )
    }

    private fun Place.asUserDefinedPlace(): Place.UserDefined =
        this as? Place.UserDefined
            ?: error("UserPlacesRepository only supports Place.UserDefined instances")
}
