package app.logdate.client.data.moments

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.SyncTransactionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [OfflineFirstMarkedMomentRepository], which reads stored marks as the timeline uses them. */
class OfflineFirstMarkedMomentRepositoryTest {
    private val dao = FakeMarkedMomentDao()
    private var inTransaction = false
    private var failTransaction = false
    private val transactions =
        object : SyncTransactionManager {
            override suspend fun <T> withTransaction(block: suspend () -> T): T {
                val before = dao.rows.toList()
                inTransaction = true
                try {
                    val value = block()
                    if (failTransaction) throw CancellationException("relinked")
                    return value
                } catch (e: Exception) {
                    dao.rows.clear()
                    dao.rows.addAll(before)
                    throw e
                } finally {
                    inTransaction = false
                }
            }
        }
    private val repository = OfflineFirstMarkedMomentRepository(dao, transactions)
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

    @Test fun `received marks are stored transactionally without changing capture identity`() =
        runTest {
            val remote = row(Uuid.random()).toModel()!!
            dao.beforeUpsert = { assertTrue(inTransaction) }
            assertEquals(remote, repository.applyFromSync(remote))
            assertEquals(remote, repository.getMark(remote.id))
        }

    @Test fun `received open mark preserves local completion and concurrent attachments`() =
        runTest {
            val id = Uuid.random()
            val first = Uuid.random()
            val second = Uuid.random()
            val local = row(id).copy(state = MarkedMomentState.FILLED, resolvedNoteIds = first.toString())
            dao.rows += local
            val remote =
                local.toModel()!!.copy(
                    state = MarkState.OPEN,
                    caption = "newer caption",
                    resolvedNoteIds = setOf(second),
                    lastUpdated = at + kotlin.time.Duration.parse("1s"),
                    syncVersion = 8,
                )
            val expected = remote.copy(state = MarkState.FILLED, resolvedNoteIds = setOf(first, second))
            assertEquals(expected, repository.applyFromSync(remote))
            assertEquals(expected, repository.getMark(id))
            val writes = dao.upserts
            assertEquals(expected, repository.applyFromSync(remote))
            assertEquals(writes, dao.upserts)
        }

    @Test fun `remote delivery cannot resurrect a tombstone overwrite a future state or replace a capture`() =
        runTest {
            for (existing in listOf(
                row(Uuid.random()).copy(deletedAt = at),
                row(Uuid.random()).copy(state = "FUTURE"),
                row(Uuid.random()).copy(originDeviceId = "other"),
            )) {
                dao.rows += existing
                val incoming = row(existing.id).toModel()!!
                assertNull(repository.applyFromSync(incoming))
                assertEquals(existing, dao.getById(existing.id))
            }
        }

    @Test fun `transaction cancellation rolls back the received mark`() =
        runTest {
            val remote = row(Uuid.random()).toModel()!!
            failTransaction = true
            assertFailsWith<CancellationException> { repository.applyFromSync(remote) }
            assertNull(repository.getMark(remote.id))
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
        var upserts = 0
        var beforeUpsert: () -> Unit = {}

        override suspend fun upsert(moment: MarkedMomentEntity) {
            beforeUpsert()
            upserts++
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
