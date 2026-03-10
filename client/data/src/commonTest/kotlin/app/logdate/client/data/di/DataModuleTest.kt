package app.logdate.client.data.di

import app.logdate.client.data.notes.DatabaseNotePlaceResolver
import app.logdate.client.data.notes.NotePlaceResolver
import app.logdate.client.database.dao.PlaceDao
import app.logdate.client.database.entities.PlaceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class DataModuleTest {
    @Test
    fun dataModule_resolvesDatabaseNotePlaceResolverAsNotePlaceResolver() {
        val koinApplication =
            koinApplication {
                allowOverride(true)
                modules(
                    dataModule,
                    module {
                        single<PlaceDao> { FakePlaceDao() }
                    },
                )
            }

        try {
            val resolver = koinApplication.koin.get<NotePlaceResolver>()
            assertIs<DatabaseNotePlaceResolver>(resolver)
        } finally {
            koinApplication.close()
        }
    }
}

private class FakePlaceDao : PlaceDao {
    private val placesFlow = MutableStateFlow<List<PlaceEntity>>(emptyList())

    override suspend fun insert(place: PlaceEntity) = Unit

    override suspend fun insertAll(places: List<PlaceEntity>) = Unit

    override suspend fun update(place: PlaceEntity) = Unit

    override suspend fun getById(id: Uuid): PlaceEntity? = null

    override fun observeById(id: Uuid): Flow<PlaceEntity?> = MutableStateFlow(null)

    override fun observeAll(): Flow<List<PlaceEntity>> = placesFlow

    override suspend fun getAll(): List<PlaceEntity> = emptyList()

    override suspend fun searchByName(query: String): List<PlaceEntity> = emptyList()

    override suspend fun findInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ): List<PlaceEntity> = emptyList()

    override suspend fun getByApUri(uri: String): PlaceEntity? = null

    override suspend fun softDelete(
        id: Uuid,
        deletedAt: Long,
    ) = Unit

    override suspend fun hardDelete(id: Uuid) = Unit
}
