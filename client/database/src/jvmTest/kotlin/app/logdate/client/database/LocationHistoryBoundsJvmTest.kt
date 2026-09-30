package app.logdate.client.database

import androidx.room.Room
import app.logdate.client.database.entities.Coordinates
import app.logdate.client.database.entities.LocationActivityEntity
import app.logdate.client.database.entities.LocationLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LocationHistoryBoundsJvmTest {
    @Test
    fun boundedHistoryIncludesStartAndExcludesNextDay() =
        runTest {
            val file = Files.createTempFile("location-history", ".db")
            val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
            try {
                val dao = database.locationHistoryDao()
                for (time in listOf(999L, 1000L, 1999L, 2000L)) {
                    dao.addLocationLog(
                        LocationLogEntity(
                            sampleId = time.toString(),
                            userId = "user",
                            deviceId = "device",
                            timestamp = Instant.fromEpochMilliseconds(time),
                            loggedAt = Instant.fromEpochMilliseconds(time),
                            location = Coordinates(36.0, -115.0, 0.0),
                            confidence = 1f,
                            isGenuine = true,
                            capturePipeline = "HIGH_DETAIL",
                            captureSource = "FOREGROUND_STREAM",
                        ),
                    )
                }
                val events = database.locationActivityDao()
                val activity =
                    LocationActivityEntity(
                        "event",
                        "user",
                        "device",
                        Instant.fromEpochMilliseconds(1000),
                        Instant.fromEpochMilliseconds(3000),
                        "ON_BICYCLE",
                        "ENTER",
                        "America/Los_Angeles",
                    )
                events.insert(activity)
                events.insert(activity)
                events.insert(activity.copy(id = "next-day", timestamp = Instant.fromEpochMilliseconds(2000)))
                assertEquals(
                    listOf(activity),
                    events.observeBetween(Instant.fromEpochMilliseconds(1000), Instant.fromEpochMilliseconds(2000)).first(),
                )
                val rows =
                    dao
                        .observeLocationHistoryBetween(
                            Instant.fromEpochMilliseconds(1000),
                            Instant.fromEpochMilliseconds(2000),
                        ).first()
                assertEquals(listOf("1999", "1000"), rows.map { it.sampleId })
            } finally {
                database.close()
                Files.deleteIfExists(file)
            }
        }
}
