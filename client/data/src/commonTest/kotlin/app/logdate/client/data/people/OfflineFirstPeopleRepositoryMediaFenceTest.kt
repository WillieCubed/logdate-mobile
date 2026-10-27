package app.logdate.client.data.people

import app.logdate.client.data.fakes.RecordingMediaClaimFence
import app.logdate.client.database.dao.people.PersonDao
import app.logdate.client.database.entities.people.PersonEntity
import app.logdate.shared.model.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class OfflineFirstPeopleRepositoryMediaFenceTest {
    @Test
    fun `person photo owner writes execute inside the media claim fence`() =
        runTest {
            val fence = RecordingMediaClaimFence()
            var inserted: PersonEntity? = null
            val dao =
                object : PersonDao {
                    override suspend fun insert(person: PersonEntity) {
                        inserted = person
                    }

                    override suspend fun update(person: PersonEntity) = Unit

                    override suspend fun getById(id: Uuid): PersonEntity? = inserted?.takeIf { it.id == id }

                    override fun observeById(id: Uuid): Flow<PersonEntity?> = emptyFlow()

                    override fun observeAll(): Flow<List<PersonEntity>> = emptyFlow()

                    override suspend fun getByContactLookupKey(lookupKey: String): PersonEntity? = null

                    override suspend fun getAll(): List<PersonEntity> = emptyList()

                    override fun observeCountByOrigin(origin: String): Flow<Int> = emptyFlow()

                    override suspend fun softDelete(
                        id: Uuid,
                        deletedAt: Instant,
                    ) = Unit
                }
            val repository = OfflineFirstPeopleRepository(dao, mediaClaimFence = fence)

            repository.addPerson(Person(name = "Avery", photoUri = "logdate-media://library/objects/photo.jpg"))

            assertEquals("logdate-media://library/objects/photo.jpg", inserted?.photoUri)
            assertEquals(1, fence.claims)
        }
}
