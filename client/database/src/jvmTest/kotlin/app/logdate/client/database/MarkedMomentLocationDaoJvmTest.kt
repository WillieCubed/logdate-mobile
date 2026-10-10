package app.logdate.client.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests that a place fills a mark only while the mark has none and still exists. */
class MarkedMomentLocationDaoJvmTest {
    private val database = Room.inMemoryDatabaseBuilder<LogDateDatabase>().setDriver(BundledSQLiteDriver()).build()
    private val filledAt = Instant.fromEpochMilliseconds(50)

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a place fills a mark captured without one`() =
        runTest {
            val dao = database.markedMomentDao()
            val mark = mark()
            dao.upsert(mark)

            assertEquals(1, dao.fillLocationIfMissing(mark.id, 37.7694, -122.4862, 12f, filledAt))
            assertEquals(
                mark.copy(latitude = 37.7694, longitude = -122.4862, locationAccuracy = 12f, lastUpdated = filledAt),
                dao.getById(mark.id),
            )
        }

    @Test
    fun `a mark that has a place is left as it is`() =
        runTest {
            val dao = database.markedMomentDao()
            val mark = mark().copy(latitude = 1.0, longitude = 2.0, locationAccuracy = 5f)
            dao.upsert(mark)

            assertEquals(0, dao.fillLocationIfMissing(mark.id, 37.7694, -122.4862, 12f, filledAt))
            assertEquals(mark, dao.getById(mark.id))
        }

    @Test
    fun `a deleted mark is left as it is`() =
        runTest {
            val dao = database.markedMomentDao()
            val mark = mark().copy(deletedAt = Instant.fromEpochMilliseconds(20))
            dao.upsert(mark)

            assertEquals(0, dao.fillLocationIfMissing(mark.id, 37.7694, -122.4862, 12f, filledAt))
            assertEquals(mark, dao.getById(mark.id))
        }

    private fun mark() =
        MarkedMomentEntity(
            id = Uuid.random(),
            capturedAt = Instant.fromEpochMilliseconds(10),
            timeZoneId = "America/Los_Angeles",
            state = MarkedMomentState.OPEN,
            originDeviceId = "watch",
            lastUpdated = Instant.fromEpochMilliseconds(10),
        )
}
