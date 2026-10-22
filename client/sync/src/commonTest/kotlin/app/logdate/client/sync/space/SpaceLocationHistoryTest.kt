package app.logdate.client.sync.space

import app.logdate.client.database.entities.UserPlaceEntity
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.runSyncBatches
import app.logdate.client.sync.space.mappers.LocationHour
import app.logdate.client.sync.space.mappers.PlaceEntryLinkRecordMapper
import app.logdate.client.sync.space.mappers.changeAt
import app.logdate.client.sync.space.mappers.sampleAt
import app.logdate.shared.model.location.HistoryCorrection
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.HistoryPayload
import app.logdate.shared.model.location.ManualVisit
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.shared.model.location.VisitMemoryLink
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Location history syncing between two devices through a personal space. */
class SpaceLocationHistoryTest {
    private val firstHour = Instant.parse("2026-10-19T09:00:00Z")
    private val home = FakeDataHome()
    private val phone = HistorySpaceDevice(home)
    private val tablet = HistorySpaceDevice(home)

    private suspend fun sync(from: HistorySpaceDevice) {
        val uploaded = from.manager.uploadPendingChanges()
        assertTrue(uploaded.success, uploaded.errors.toString())
    }

    private suspend fun pull(into: HistorySpaceDevice) {
        val downloaded = into.manager.downloadRemoteChanges()
        assertTrue(downloaded.success, downloaded.errors.toString())
    }

    private fun HistorySpaceDevice.sample(
        index: Int,
        hour: Instant = firstHour,
    ) = sampleAt(
        index,
        hour,
        deviceId,
        id = "sample-${index.toString().padStart(5, '0')}-${hour.epochSeconds}",
    ).copy(ownerId = HISTORY_ACCOUNT)

    private fun placeOf(
        device: HistorySpaceDevice,
        id: String,
    ): SemanticPlace =
        (
            Json.decodeFromString(
                HistoryPayload.serializer(),
                checkNotNull(
                    device.store.items
                        .single {
                            it.id == id
                        }.payload,
                ),
            ) as HistoryPayload.Place
        ).value

    @Test
    fun `held location uploads yield to durable retry and resume without losing samples`() =
        runTest {
            val sample = phone.sample(0)
            phone.record(sample)
            val hourId = LocationHour.itemId(phone.deviceId, firstHour)
            phone.metadata.enqueuePending(hourId, EntityType.LOCATION_SAMPLES, PendingOperation.UPDATE)
            phone.retrySchedule.setNextAttemptAt(EntityType.LOCATION_SAMPLES, hourId, Long.MAX_VALUE)
            var passes = 0

            val held =
                runSyncBatches {
                    assertTrue(++passes <= 8, "Backoff-held work must return to the worker instead of spinning")
                    phone.manager.uploadPendingChanges()
                }

            assertTrue(held.success)
            assertTrue(held.hasMorePending)
            assertEquals(0, home.writes)
            assertNotNull(phone.sampleRecord(sample.id))
            assertEquals(1, phone.store.pending(HISTORY_ACCOUNT, phone.sessions.currentOrigin, 100).size)
            assertEquals(1, phone.pending(EntityType.LOCATION_SAMPLES).size)

            phone.retrySchedule.clear(EntityType.LOCATION_SAMPLES, hourId)
            val resumed = runSyncBatches { phone.manager.uploadPendingChanges() }

            assertTrue(resumed.success)
            assertFalse(resumed.hasMorePending)
            assertEquals(listOf(sample), phone.liveSamples(phone.deviceId, firstHour))
            assertTrue(phone.pending(EntityType.LOCATION_SAMPLES).isEmpty())
            assertTrue(phone.store.pending(HISTORY_ACCOUNT, phone.sessions.currentOrigin, 100).isEmpty())
            assertEquals(1, home.writes)
        }

    @Test
    fun `samples recorded in two hours reach another device as one record for each hour`() =
        runTest {
            val samples = (0 until 5).map { phone.sample(it) } + (0 until 3).map { phone.sample(it, firstHour + 1.hours) }
            samples.forEach { phone.record(it) }

            sync(phone)
            pull(tablet)

            assertEquals(samples.filter { it.timestamp < firstHour + 1.hours }, tablet.liveSamples(phone.deviceId, firstHour))
            assertEquals(samples.filter { it.timestamp >= firstHour + 1.hours }, tablet.liveSamples(phone.deviceId, firstHour + 1.hours))
            assertEquals(2, home.liveRkeys().size)
            assertNotNull(home.open("app.logdate.place.sample", LocationHour.itemId(phone.deviceId, firstHour)))
            assertEquals(emptyList(), phone.pending(EntityType.LOCATION_SAMPLES))
            assertEquals(emptyList(), phone.store.pending(HISTORY_ACCOUNT, phone.sessions.currentOrigin, 100))
        }

    @Test
    fun `a sample recorded after the hour was uploaded rewrites the hour`() =
        runTest {
            phone.record(phone.sample(0))
            sync(phone)
            pull(tablet)

            phone.record(phone.sample(1))
            sync(phone)
            pull(tablet)

            assertEquals(listOf(phone.sample(0), phone.sample(1)), tablet.liveSamples(phone.deviceId, firstHour))
        }

    @Test
    fun `a sample deleted on another device is gone from the hour and from the device that recorded it`() =
        runTest {
            (0 until 4).forEach { phone.record(phone.sample(it)) }
            sync(phone)
            pull(tablet)

            tablet.deleteSample(phone.sample(2).id)
            sync(tablet)
            pull(phone)

            val remaining = listOf(0, 1, 3).map { phone.sample(it) }
            assertEquals(remaining, phone.liveSamples(phone.deviceId, firstHour))
            assertEquals(remaining, tablet.liveSamples(phone.deviceId, firstHour))
            assertTrue(phone.sampleRecord(phone.sample(2).id)!!.deleted)
            val stored = home.open("app.logdate.place.sample", LocationHour.itemId(phone.deviceId, firstHour))!!.record.jsonObject
            assertEquals(listOf(phone.sample(2).id), (stored.getValue("deletedSampleIds") as JsonArray).map { it.jsonPrimitive.content })
            assertEquals(3, (stored.getValue("samples") as JsonArray).size)
            assertFalse(phone.sample(2).latitude.toString() in stored.toString(), "the deleted location must be gone from the record")
        }

    @Test
    fun `a deleted sample is not added back when the device that recorded it uploads again`() =
        runTest {
            (0 until 3).forEach { phone.record(phone.sample(it)) }
            sync(phone)
            pull(tablet)
            tablet.deleteSample(phone.sample(1).id)
            sync(tablet)

            phone.record(phone.sample(3))
            sync(phone)
            pull(tablet)
            pull(phone)

            val expected = listOf(0, 2, 3).map { phone.sample(it) }
            assertEquals(expected, tablet.liveSamples(phone.deviceId, firstHour))
            assertEquals(expected, phone.liveSamples(phone.deviceId, firstHour))
        }

    @Test
    fun `a sample added on one device and one deleted on another in the same hour both survive`() =
        runTest {
            (0 until 3).forEach { phone.record(phone.sample(it)) }
            sync(phone)
            pull(tablet)
            phone.record(phone.sample(3))
            tablet.deleteSample(phone.sample(0).id)

            sync(tablet)
            sync(phone)
            pull(tablet)

            val expected = listOf(1, 2, 3).map { phone.sample(it) }
            assertEquals(expected, phone.liveSamples(phone.deviceId, firstHour))
            assertEquals(expected, tablet.liveSamples(phone.deviceId, firstHour))
        }

    @Test
    fun `activity changes sync with the samples`() =
        runTest {
            phone.record(phone.sample(0))
            val change = changeAt(1, firstHour, phone.deviceId)
            phone.record(change)

            sync(phone)
            pull(tablet)

            val stored = home.open("app.logdate.place.sample", LocationHour.itemId(phone.deviceId, firstHour))!!.record.jsonObject
            assertEquals(1, (stored.getValue("transitions") as JsonArray).size)
            assertNotNull(tablet.store.items.firstOrNull { it.recordType == "activity" })
        }

    @Test
    fun `a hour of more than 5000 samples is stored in two records and a deletion stays in the part that held the sample`() =
        runTest {
            (0 until 5_001).forEach { phone.record(phone.sample(it)) }
            sync(phone)
            val itemId = LocationHour.itemId(phone.deviceId, firstHour)

            assertNotNull(home.open("app.logdate.place.sample", itemId))
            assertNotNull(home.open("app.logdate.place.sample", "$itemId:2"))
            assertNull(home.open("app.logdate.place.sample", "$itemId:3"))
            val firstBefore = home.open("app.logdate.place.sample", itemId)!!.record.jsonObject
            assertEquals(5_000, (firstBefore.getValue("samples") as JsonArray).size)

            pull(tablet)
            tablet.deleteSample(phone.sample(10).id)
            sync(tablet)

            val firstAfter = home.open("app.logdate.place.sample", itemId)!!.record.jsonObject
            assertEquals(4_999, (firstAfter.getValue("samples") as JsonArray).size)
            assertEquals(
                listOf(phone.sample(10).id),
                (firstAfter.getValue("deletedSampleIds") as JsonArray).map { it.jsonPrimitive.content },
            )
            val second = home.open("app.logdate.place.sample", "$itemId:2")!!.record.jsonObject
            assertEquals(1, (second.getValue("samples") as JsonArray).size)
        }

    @Test
    fun `a place a correction a visit and an entry link reach another device`() =
        runTest {
            val placeId = Uuid.random().toString()
            val place = SemanticPlace(placeId, "Grandma's house", 41.8781136, -87.6297982, locality = "Chicago", userConfirmed = true)
            val correction = HistoryCorrection(Uuid.random().toString(), "place:$placeId", HistoryField.LABEL, "Grandma's")
            val visit =
                ManualVisit(Uuid.random().toString(), Instant.parse("2026-10-19T09:30:00Z"), Instant.parse("2026-10-19T11:00:00Z"), placeId)
            val link = VisitMemoryLink(Uuid.random().toString(), visit.id)
            phone.record(placeId, "place", HistoryPayload.Place(place))
            phone.record(correction.id, "correction", HistoryPayload.Correction(correction))
            phone.record(visit.id, "manual", HistoryPayload.Manual(visit))
            phone.record(Uuid.random().toString(), "memory-link", HistoryPayload.MemoryLink(link))

            sync(phone)
            pull(tablet)

            val onTablet = tablet.store.items.associateBy { it.recordType + ":" + it.id }
            assertNotNull(onTablet["place:$placeId"])
            assertNotNull(onTablet["correction:${correction.id}"])
            assertNotNull(onTablet["manual:${visit.id}"])
            assertNotNull(onTablet["memory-link:${PlaceEntryLinkRecordMapper.keyOf(link.noteId, link.targetEvidenceId)}"])
            assertTrue(onTablet.values.none { it.dirty })
            assertEquals(emptyList(), phone.store.pending(HISTORY_ACCOUNT, phone.sessions.currentOrigin, 100))
        }

    @Test
    fun `an entry linked twice to the same visit is one record`() =
        runTest {
            val link = VisitMemoryLink(Uuid.random().toString(), "evidence-1")
            phone.record(Uuid.random().toString(), "memory-link", HistoryPayload.MemoryLink(link))
            phone.record(Uuid.random().toString(), "memory-link", HistoryPayload.MemoryLink(link))

            sync(phone)

            assertEquals(1, home.liveRkeys().size)
        }

    @Test
    fun `a saved place that history never saw is uploaded and saved on the other device`() =
        runTest {
            val saved =
                UserPlaceEntity(
                    Uuid.random().toString(),
                    "Home",
                    41.0,
                    -87.0,
                    radiusMeters = 120.0,
                    description = "Blue door",
                    createdAt = 1_760_000_000_000,
                    updatedAt = 1_760_000_001_000,
                )
            phone.savedPlaces.rows[saved.id] = saved

            sync(phone)
            pull(tablet)

            assertEquals(saved, tablet.savedPlaces.rows[saved.id])
            assertNotNull(tablet.store.items.firstOrNull { it.id == saved.id && it.recordType == "place" })
        }

    @Test
    fun `a place deleted on one device is deleted from both of its tables on the other`() =
        runTest {
            val saved = UserPlaceEntity(Uuid.random().toString(), "Home", 41.0, -87.0, 120.0, null, 1_760_000_000_000, 1_760_000_001_000)
            phone.savedPlaces.rows[saved.id] = saved
            sync(phone)
            pull(tablet)
            assertNotNull(tablet.savedPlaces.rows[saved.id])

            val row = phone.store.items.single { it.id == saved.id }
            phone.store.put(
                HISTORY_ACCOUNT,
                phone.sessions.currentOrigin,
                row.copy(payload = null, deleted = true, dirty = true, deviceVersion = row.deviceVersion + 1),
            )
            phone.savedPlaces.rows.remove(saved.id)
            sync(phone)
            pull(tablet)

            assertNull(tablet.savedPlaces.rows[saved.id])
            assertTrue(
                tablet.store.items
                    .single { it.id == saved.id }
                    .deleted,
            )
            assertNull(home.open("app.logdate.place.place", saved.id))
        }

    @Test
    fun `a change made on a device waits for the person's own change to upload instead of replacing it`() =
        runTest {
            val placeId = Uuid.random().toString()
            phone.record(placeId, "place", HistoryPayload.Place(SemanticPlace(placeId, "Cafe", 1.0, 2.0)))
            sync(phone)
            pull(tablet)
            tablet.record(placeId, "place", HistoryPayload.Place(SemanticPlace(placeId, "My cafe", 1.0, 2.0, userConfirmed = true)))
            phone.record(placeId, "place", HistoryPayload.Place(SemanticPlace(placeId, "Coffee place", 1.0, 2.0)))
            sync(phone)

            pull(tablet)
            sync(tablet)
            pull(phone)

            val onTablet = placeOf(tablet, placeId)
            val onPhone = placeOf(phone, placeId)
            assertEquals(onTablet, onPhone)
            assertTrue(onPhone.userConfirmed, "confirming a place is never undone")
        }

    @Test
    fun `location history stays on its own endpoints while personal spaces are off`() =
        runTest {
            phone.spaceEnabled = false
            phone.record(phone.sample(0))

            val uploaded = phone.manager.uploadPendingChanges()

            assertFalse(uploaded.success, "the location history endpoints are not available in this test")
            assertEquals(0, home.writes)
        }
}
