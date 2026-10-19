@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.model.location.ActivityObservation
import app.logdate.shared.model.location.HistoryCorrection
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.HistoryPayload
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.shared.model.location.TravelMode
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A day of location history, a place correction and a marked moment travel between two devices of
 * one account through its sealed personal space on a real server, with real Room databases and the
 * real sync engine, including the deletion of one sample.
 */
class PersonalSpaceLocationHistoryE2ETest {
    private val midnight = Instant.parse("2026-10-19T00:00:00Z")
    private val json = Json { ignoreUnknownKeys = true }

    private fun sampleOf(
        device: SpaceDevice,
        account: SpaceAccount,
        hour: Int,
        index: Int,
    ) = LocationObservation(
        id = "sample-$hour-$index",
        ownerId = account.owner,
        deviceId = device.deviceId,
        timestamp = midnight + hour.hours + (index * 10).minutes,
        latitude = 41.8781136 + hour * 0.001,
        longitude = -87.6297982 + index * 0.001,
        accuracyMeters = 12.5f,
        speedMetersPerSecond = 1.5f,
        bearingDegrees = 90.25f,
        activity = TravelMode.WALKING,
        timeZoneId = "America/Chicago",
        source = "BACKGROUND_PERIODIC",
    )

    private suspend fun SpaceDevice.record(
        account: SpaceAccount,
        id: String,
        type: String,
        payload: HistoryPayload,
    ) {
        val existing = historyStore.record(account.owner, account.origin, id)
        historyStore.put(
            account.owner,
            account.origin,
            HistoryRecord(
                id,
                type,
                json.encodeToString(HistoryPayload.serializer(), payload),
                deviceId,
                (existing?.deviceVersion ?: 0) + 1,
            ),
        )
    }

    private suspend fun SpaceDevice.rows(account: SpaceAccount) = historyStore.records(account.owner, account.origin)

    private suspend fun SpaceDevice.liveSampleIds(account: SpaceAccount) =
        rows(account).filter { it.recordType == "observation" && !it.deleted }.map { it.id.removePrefix("observation:") }.toSet()

    private fun assertSynced(result: app.logdate.client.sync.SyncResult) =
        assertTrue(result.success, result.errors.joinToString { it.cause?.stackTraceToString() ?: it.toString() })

    private suspend fun ServerClientE2EHarness.rawChanges(account: SpaceAccount): String =
        httpClient
            .get("${account.origin}/xrpc/app.logdate.space.listRepoOps") {
                parameter("space", "at://${account.did}/space/app.logdate.space.personal/self")
                parameter("since", "0")
                header(HttpHeaders.Authorization, "Bearer ${account.token}")
            }.bodyAsText()

    @Test
    fun `a day of location history a place correction and a marked moment reach the other device and a deleted sample is deleted there`() =
        runTest {
            withServerClientHarness {
                val account = signUpForSpace()
                val phone = SpaceDevice(account, backgroundScope)
                val tablet = SpaceDevice(account, backgroundScope)
                try {
                    val samples = (0 until 24).flatMap { hour -> (0 until 3).map { sampleOf(phone, account, hour, it) } }
                    samples.forEach { phone.record(account, "observation:${it.id}", "observation", HistoryPayload.Observation(it)) }
                    val change =
                        ActivityObservation("change-1", account.owner, phone.deviceId, midnight + 9.hours + 5.minutes, "WALKING", "ENTER")
                    phone.record(
                        account,
                        app.logdate.client.device.crypto
                            .activityHistoryRecordId(change.id),
                        "activity",
                        HistoryPayload.Activity(change),
                    )
                    val placeId = Uuid.random().toString()
                    phone.record(
                        account,
                        placeId,
                        "place",
                        HistoryPayload.Place(
                            SemanticPlace(placeId, "Grandma's house", 41.8781136, -87.6297982, "Chicago", userConfirmed = true),
                        ),
                    )
                    val correction = HistoryCorrection(Uuid.random().toString(), "place:$placeId", HistoryField.LABEL, "Grandma's")
                    phone.record(account, correction.id, "correction", HistoryPayload.Correction(correction))
                    val mark =
                        MarkedMoment(
                            id = Uuid.random(),
                            capturedAt = midnight + 8.hours,
                            timeZoneId = "America/Chicago",
                            location = MarkLocation(41.8781136, -87.6297982, 12.5f),
                            heartRateBpm = 88,
                            state = MarkState.OPEN,
                            caption = "Heron at the lake",
                            resolvedNoteIds = emptySet(),
                            originDeviceId = "watch-1",
                            lastUpdated = midnight + 8.hours,
                            syncVersion = 0,
                            capturedDuring = CaptureActivity.MOVING,
                        )
                    phone.marks.applyFromSync(mark)
                    phone.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)

                    assertSynced(phone.manager.uploadPendingChanges())

                    val stored = rawChanges(account)
                    assertFalse("Grandma" in stored || "Heron" in stored || "41.87" in stored, "The server received private data")
                    assertSynced(tablet.manager.downloadRemoteChanges())
                    assertEquals(samples.map { it.id }.toSet(), tablet.liveSampleIds(account))
                    assertNotNull(tablet.rows(account).firstOrNull { it.id == placeId && it.recordType == "place" })
                    assertNotNull(tablet.rows(account).firstOrNull { it.id == correction.id })
                    assertEquals(mark.copy(syncVersion = tablet.marks.getMark(mark.id)!!.syncVersion), tablet.marks.getMark(mark.id))
                    assertTrue(tablet.rows(account).none { it.dirty })

                    val deleted = samples[30]
                    val row = tablet.historyStore.record(account.owner, account.origin, "observation:${deleted.id}")!!
                    tablet.historyStore.putBatch(
                        account.owner,
                        account.origin,
                        listOf(
                            HistoryRecord(
                                row.id,
                                "observation",
                                null,
                                row.deviceId,
                                row.deviceVersion + 1,
                                row.serverVersion,
                                deleted = true,
                            ),
                        ),
                    )
                    assertSynced(tablet.manager.uploadPendingChanges())
                    assertSynced(phone.manager.downloadRemoteChanges())

                    val remaining = samples.map { it.id }.toSet() - deleted.id
                    assertEquals(remaining, phone.liveSampleIds(account))
                    assertEquals(remaining, tablet.liveSampleIds(account))
                    assertTrue(phone.historyStore.record(account.owner, account.origin, "observation:${deleted.id}")!!.deleted)

                    tablet.marks.fill(mark.id, "Wrote it up", setOf(Uuid.random()), midnight + 20.hours)
                    assertSynced(tablet.manager.uploadPendingChanges())
                    assertSynced(phone.manager.downloadRemoteChanges())
                    assertEquals(MarkState.FILLED, phone.marks.getMark(mark.id)?.state)

                    val later =
                        HistoryCorrection(
                            Uuid.random().toString(),
                            "place:$placeId",
                            HistoryField.LABEL,
                            "Grandma's place",
                            listOf(correction.id),
                        )
                    tablet.record(account, later.id, "correction", HistoryPayload.Correction(later))
                    assertSynced(tablet.manager.uploadPendingChanges())
                    assertSynced(phone.manager.downloadRemoteChanges())
                    assertEquals(
                        later.supersedes,
                        (phone.rows(account).first { it.id == later.id }.payload)?.let {
                            (json.decodeFromString(HistoryPayload.serializer(), it) as HistoryPayload.Correction).value.supersedes
                        },
                    )

                    listOf(phone, tablet).forEach { device ->
                        assertTrue(device.rows(account).none { it.dirty }, "Nothing should wait to upload")
                        assertEquals(0, device.inbox.count())
                        EntityType.entries.forEach { assertTrue(device.metadata.getPendingUploads(it).isEmpty(), "$it") }
                    }
                    assertNull(tablet.marks.getMark(Uuid.random()))
                } finally {
                    phone.close()
                    tablet.close()
                }
            }
        }
}
