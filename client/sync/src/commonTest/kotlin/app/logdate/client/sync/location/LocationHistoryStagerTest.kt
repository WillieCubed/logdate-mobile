package app.logdate.client.sync.location

import app.logdate.client.repository.location.ActivityHistoryItem
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LocationHistoryStagerTest {
    @Test
    fun `bounded import resumes from ingestion cursor without resurrecting tombstones`() =
        runTest {
            val store = MemoryHistoryStore()
            val cursors = InMemoryKeyValueStorage()
            store.items += HistoryRecord("observation:sample-1", "observation", null, "device", deleted = true)
            val samples = (1..250).map { sample(it) }.toMutableList()
            val queries = mutableListOf<Pair<String, String>>()

            fun stager() =
                LocationHistoryStager(
                    locationPage = { owner, device, after, _, limit ->
                        queries += owner to device
                        if (owner != "owner") emptyList() else samples.filter { it.loggedAt > after }.sortedBy { it.loggedAt }.take(limit)
                    },
                    activityPage = { _, _, _, _, _ -> emptyList() },
                    store = store,
                    cursors = cursors,
                    deviceId = { "device" },
                )
            assertTrue(stager().stage("owner", "https://server.test"))
            assertEquals(200, store.items.size)
            assertTrue(store.items.first().deleted)
            assertFalse(stager().stage("owner", "https://server.test"))
            assertEquals(250, store.items.size)
            assertTrue(queries.all { it.second == "device" && it.first in setOf("owner", "default_user") })
            samples += sample(251).copy(sampleId = "late-behind-cursor", loggedAt = Instant.fromEpochMilliseconds(5))
            assertTrue(stager().stage("owner", "https://server.test"))
            assertFalse(stager().stage("owner", "https://server.test"))
            assertEquals(251, store.items.size)
            assertTrue(store.items.any { it.id == "observation:late-behind-cursor" })
        }

    @Test
    fun `late raw samples reapply their existing tombstone without changing sync metadata`() =
        runTest {
            val persisted = MemoryHistoryStore()
            val tombstone =
                HistoryRecord(
                    "observation:sample-1",
                    "observation",
                    null,
                    "device",
                    deviceVersion = 4,
                    serverVersion = 12,
                    deleted = true,
                    dirty = false,
                )
            persisted.items += tombstone
            val writes = mutableListOf<HistoryRecord>()
            val store =
                object : HistoryRecordStore by persisted {
                    override suspend fun put(
                        ownerId: String,
                        origin: String,
                        record: HistoryRecord,
                    ) {
                        assertEquals("owner", ownerId)
                        assertEquals("https://server.test", origin)
                        writes += record
                        persisted.put(ownerId, origin, record)
                    }
                }
            val stager =
                LocationHistoryStager(
                    locationPage = { owner, _, _, _, _ ->
                        if (owner == "default_user") {
                            listOf(sample(1).copy(userId = owner))
                        } else {
                            emptyList()
                        }
                    },
                    activityPage = { _, _, _, _, _ -> emptyList() },
                    store = store,
                    cursors = InMemoryKeyValueStorage(),
                    deviceId = { "device" },
                )

            assertFalse(stager.stage("owner", "https://server.test"))

            assertEquals(listOf(tombstone), writes, "Reapply the tombstone to clean up the late raw sample")
            assertEquals(tombstone, persisted.items.single())
        }

    @Test
    fun `legacy activity identifiers stay inside encrypted payloads and import idempotently`() =
        runTest {
            val store = MemoryHistoryStore()
            val legacyId = "device:100000:WALKING:ENTER"
            val activity =
                ActivityHistoryItem(
                    legacyId,
                    "owner",
                    "device",
                    Instant.fromEpochMilliseconds(1),
                    Instant.fromEpochMilliseconds(2),
                    "WALKING",
                    "ENTER",
                    "UTC",
                )
            val stager =
                LocationHistoryStager(
                    locationPage = { _, _, _, _, _ -> emptyList() },
                    activityPage = { owner, _, _, _, _ -> if (owner == "owner") listOf(activity) else emptyList() },
                    store = store,
                    cursors = InMemoryKeyValueStorage(),
                    deviceId = { "device" },
                )

            assertFalse(stager.stage("owner", "https://server.test"))
            assertFalse(stager.stage("owner", "https://server.test"))

            val record = store.items.single()
            assertEquals("activity:71d48f78e731f91ddb2a45c058380e639cfb9608d41e5ac48f66b78ce25f6dfd", record.id)
            assertTrue(record.payload!!.contains(legacyId))
        }

    private fun sample(index: Int) =
        LocationHistoryItem(
            sampleId = "sample-$index",
            userId = "owner",
            deviceId = "device",
            timestamp = Instant.fromEpochMilliseconds(1),
            loggedAt = Instant.fromEpochMilliseconds(index.toLong()),
            location = Location(1.0, 2.0, LocationAltitude(0.0, AltitudeUnit.METERS)),
            confidence = 1f,
            isGenuine = true,
        )
}
