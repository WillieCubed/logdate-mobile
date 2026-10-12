package app.logdate.client.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.logdate.client.database.entities.PlaceEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PlaceImportDaoTest {
    private fun database() = Room.inMemoryDatabaseBuilder<LogDateDatabase>().setDriver(BundledSQLiteDriver()).build()

    private val place =
        PlaceEntity(
            Uuid.random(),
            "Phone place",
            36.1,
            -115.1,
            created = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(2),
        )

    @Test fun `an imported missing place can satisfy a downloaded note reference`() =
        runTest {
            val db = database()
            try {
                db.placeDao().insertIfAbsent(place)
                assertEquals(place, db.placeDao().getById(place.id))
            } finally {
                db.close()
            }
        }

    @Test fun `a phone place cannot replace or revive a local place`() =
        runTest {
            val db = database()
            try {
                val local = place.copy(name = "My local place")
                db.placeDao().insert(local)
                db.placeDao().insertIfAbsent(place)
                assertEquals(local, db.placeDao().getById(place.id))
                db.placeDao().softDelete(place.id, 3)
                db.placeDao().insertIfAbsent(place)
                assertNull(db.placeDao().getById(place.id))
            } finally {
                db.close()
            }
        }
}
