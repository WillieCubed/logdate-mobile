package app.logdate.client.data.journals

import androidx.room.Room
import app.logdate.client.data.location.OfflineFirstLocationHistoryRepository
import app.logdate.client.data.places.OfflineFirstUserPlacesRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.shared.model.Location
import app.logdate.shared.model.Place
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ArchiveAuxiliaryReceiptTest {
    private val directory = Files.createTempDirectory("archive-auxiliary-")
    private val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(directory.resolve("aux.db").toString()))
    private val scope = JournalMergeScope("owner", "https://example.test")
    private val transaction = RoomSyncTransactionManager(database)
    private val receipts = RoomArchiveRecoveryReceipts(database.downloadInboxDao())
    private val places = OfflineFirstUserPlacesRepository(database.userPlaceDao(), transaction, receipts, { scope })
    private val locations =
        OfflineFirstLocationHistoryRepository(
            database.locationHistoryDao(),
            transactionManager = transaction,
            recoveryReceipts = receipts,
            currentRecoveryScope = { scope },
        )

    @AfterTest
    fun close() {
        database.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `an overlapping archive preserves the recorded sample and remembers that decision`() =
        runTest {
            val at = Instant.parse("2026-01-02T03:04:05Z")
            val sample =
                LocationLogRecord(
                    userId = "owner",
                    deviceId = "source",
                    timestamp = at,
                    loggedAt = at,
                    location =
                        Location(
                            1.0,
                            2.0,
                            app.logdate.shared.model
                                .LocationAltitude(0.0, app.logdate.shared.model.AltitudeUnit.METERS),
                        ),
                    confidence = 1f,
                    isGenuine = true,
                )
            locations.logLocation(sample).getOrThrow()
            val recovery = Uuid.random().toString()
            locations.restoreLocationForArchive(sample.copy(confidence = 0f), scope, recovery).getOrThrow()
            assertEquals(1f, locations.getLastLocation()?.confidence)
            locations.deleteLocationEntry("owner", "source", at).getOrThrow()
            locations.restoreLocationForArchive(sample, scope, recovery).getOrThrow()
            assertEquals(0, locations.getLocationCount())
        }

    @Test
    fun `place replay cannot replace an edit or recreate a later deletion`() =
        runTest {
            val id = Uuid.random()
            val place = Place.UserDefined(id, "Archive name", 1.0, 2.0)
            val recovery = Uuid.random().toString()
            places.restorePlaceForArchive(place, scope, recovery).getOrThrow()
            places.updatePlace(place.copy(displayName = "Current name")).getOrThrow()
            places.restorePlaceForArchive(place, scope, recovery).getOrThrow()
            assertEquals("Current name", (places.getPlaceById(id.toString()) as? Place.UserDefined)?.displayName)
            places.deletePlace(id.toString()).getOrThrow()
            places.restorePlaceForArchive(place, scope, recovery).getOrThrow()
            assertNull(places.getPlaceById(id.toString()))
        }

    @Test
    fun `location replay cannot undo an intentional deletion`() =
        runTest {
            val at = Instant.parse("2026-01-02T03:04:05Z")
            val sample =
                LocationLogRecord(
                    confidence = 1f,
                    isGenuine = true,
                    userId = "owner",
                    deviceId = "source",
                    timestamp = at,
                    loggedAt = at,
                    location =
                        Location(
                            1.0,
                            2.0,
                            app.logdate.shared.model
                                .LocationAltitude(0.0, app.logdate.shared.model.AltitudeUnit.METERS),
                        ),
                )
            val recovery = Uuid.random().toString()
            locations.restoreLocationForArchive(sample, scope, recovery).getOrThrow()
            locations.deleteLocationEntry("owner", "source", at).getOrThrow()
            locations.restoreLocationForArchive(sample, scope, recovery).getOrThrow()
            assertEquals(0, locations.getLocationCount())
        }
}
