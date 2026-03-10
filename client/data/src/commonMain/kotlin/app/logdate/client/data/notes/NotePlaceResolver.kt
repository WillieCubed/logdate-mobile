package app.logdate.client.data.notes

import app.logdate.client.database.dao.PlaceDao
import app.logdate.client.repository.journals.NotePlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

interface NotePlaceResolver {
    suspend fun get(placeId: Uuid): NotePlace?

    fun observeAll(): Flow<Map<Uuid, NotePlace>>
}

class DatabaseNotePlaceResolver(
    private val placeDao: PlaceDao,
) : NotePlaceResolver {
    override suspend fun get(placeId: Uuid): NotePlace? = placeDao.getById(placeId)?.toNotePlace()

    override fun observeAll(): Flow<Map<Uuid, NotePlace>> =
        placeDao.observeAll().map { places ->
            places.associate { place ->
                place.id to place.toNotePlace()
            }
        }
}

object EmptyNotePlaceResolver : NotePlaceResolver {
    override suspend fun get(placeId: Uuid): NotePlace? = null

    override fun observeAll(): Flow<Map<Uuid, NotePlace>> = kotlinx.coroutines.flow.flowOf(emptyMap())
}
