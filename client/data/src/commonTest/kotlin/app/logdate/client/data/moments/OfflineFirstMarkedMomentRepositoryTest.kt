package app.logdate.client.data.moments

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [OfflineFirstMarkedMomentRepository], which reads stored marks as the timeline uses them. */
class OfflineFirstMarkedMomentRepositoryTest {
    private val dao = FakeMarkedMomentDao()
    private val repository = OfflineFirstMarkedMomentRepository(dao)
    private val at = Instant.fromEpochMilliseconds(1_710_000_000_000)

    @Test
    fun `open marks read with their place and notes`() =
        runTest {
            val id = Uuid.random()
            val note = Uuid.random()
            dao.rows += row(id).copy(latitude = 37.7694, longitude = -122.4862, locationAccuracy = 8f, resolvedNoteIds = note.toString())

            assertEquals(
                listOf(
                    MarkedMoment(
                        id = id,
                        capturedAt = at,
                        timeZoneId = "America/Los_Angeles",
                        location = MarkLocation(latitude = 37.7694, longitude = -122.4862, accuracyMeters = 8f),
                        heartRateBpm = 72,
                        state = MarkState.OPEN,
                        caption = "",
                        resolvedNoteIds = setOf(note),
                        originDeviceId = "watch",
                        lastUpdated = at,
                        syncVersion = 0,
                    ),
                ),
                repository.observeOpenMarks().first(),
            )
        }

    @Test
    fun `a mark without coordinates has no place`() =
        runTest {
            val id = Uuid.random()
            dao.rows += row(id)

            assertNull(repository.getMark(id)?.location)
        }

    @Test
    fun `a mark in a state this version does not know is left out`() =
        runTest {
            val id = Uuid.random()
            dao.rows += row(id).copy(state = "SNOOZED")

            assertNull(repository.getMark(id))
        }

    @Test
    fun `a deleted mark is not found`() =
        runTest {
            val id = Uuid.random()
            dao.rows += row(id).copy(deletedAt = at)

            assertNull(repository.getMark(id))
        }

    @Test
    fun `note ids that cannot be read are skipped`() =
        runTest {
            val id = Uuid.random()
            val note = Uuid.random()
            dao.rows += row(id).copy(resolvedNoteIds = "not-an-id,$note")

            assertEquals(setOf(note), repository.getMark(id)?.resolvedNoteIds)
        }

    @Test
    fun `filling a place says whether the mark changed`() =
        runTest {
            val id = Uuid.random()
            val place = MarkLocation(latitude = 37.7694, longitude = -122.4862, accuracyMeters = 12f)

            assertTrue(repository.fillLocationIfMissing(id, place, at))
            dao.filledRows = 0
            assertFalse(repository.fillLocationIfMissing(id, place, at))
            assertEquals(listOf(id, id), dao.fills)
        }

    private fun row(id: Uuid) =
        MarkedMomentEntity(
            id = id,
            capturedAt = at,
            timeZoneId = "America/Los_Angeles",
            heartRateBpm = 72,
            state = MarkedMomentState.OPEN,
            originDeviceId = "watch",
            lastUpdated = at,
        )

    private class FakeMarkedMomentDao : MarkedMomentDao {
        val rows = mutableListOf<MarkedMomentEntity>()
        val fills = mutableListOf<Uuid>()
        var filledRows = 1

        override suspend fun upsert(moment: MarkedMomentEntity) {
            rows.removeAll { it.id == moment.id }
            rows += moment
        }

        override suspend fun getById(id: Uuid): MarkedMomentEntity? = rows.firstOrNull { it.id == id }

        override fun observeByState(state: String): Flow<List<MarkedMomentEntity>> =
            flowOf(rows.filter { it.state == state && it.deletedAt == null }.sortedByDescending { it.capturedAt })

        override suspend fun fillLocationIfMissing(
            id: Uuid,
            latitude: Double,
            longitude: Double,
            accuracy: Float?,
            lastUpdated: Instant,
        ): Int {
            fills += id
            return filledRows
        }
    }
}
