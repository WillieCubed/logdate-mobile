package app.logdate.client.sync.space

import app.logdate.client.datastore.UserSession
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.mappers.sampleAt
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class SpaceOwnerCasingTest {
    @Test
    fun `a restored raw owner uploads retained samples and receives samples into its own raw scope`() =
        runTest {
            val raw = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
            val hour = Instant.parse("2026-10-19T09:00:00Z")
            val home = FakeDataHome()
            val restored = HistorySpaceDevice(home, localOwner = raw)
            val other = HistorySpaceDevice(home, localOwner = raw.lowercase())
            for (device in listOf(restored, other)) {
                device.sessions.saveSession(UserSession("test-access-token", "refresh", raw.lowercase()))
            }
            val retained = sampleAt(1, hour, restored.deviceId).copy(ownerId = raw)
            restored.record(retained)
            val uploaded = restored.manager.uploadPendingChanges()
            assertTrue(uploaded.success, uploaded.errors.toString())
            assertEquals(1, home.writes)
            assertTrue(restored.store.pending(raw, restored.sessions.currentOrigin, 100).isEmpty())
            assertTrue(restored.pending(EntityType.LOCATION_SAMPLES).isEmpty())
            assertTrue(other.manager.downloadRemoteChanges().success)
            assertEquals(listOf(retained.copy(ownerId = raw.lowercase())), other.liveSamples(restored.deviceId, hour))

            val incoming = sampleAt(2, hour, other.deviceId).copy(ownerId = raw.lowercase())
            other.record(incoming)
            val sent = other.manager.uploadPendingChanges()
            assertTrue(sent.success, sent.errors.toString())
            val received = restored.manager.downloadRemoteChanges()
            assertTrue(received.success, received.errors.toString())
            assertEquals(listOf(incoming.copy(ownerId = raw)), restored.liveSamples(other.deviceId, hour))
            assertEquals(listOf(retained), restored.liveSamples(restored.deviceId, hour))
        }

    @Test
    fun `stale-CID conflict settles every retained sample despite derived owner spelling`() =
        runTest {
            val raw = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
            val hour = Instant.parse("2026-10-19T09:00:00Z")
            val home = FakeDataHome()
            val phone = HistorySpaceDevice(home, localOwner = raw)
            val other = HistorySpaceDevice(home, localOwner = raw.lowercase())
            for (device in listOf(phone, other)) {
                device.sessions.saveSession(UserSession("test-access-token", "refresh", raw.lowercase()))
            }
            val original = sampleAt(1, hour, phone.deviceId).copy(ownerId = raw)
            phone.record(original)
            assertTrue(phone.manager.uploadPendingChanges().success)
            assertTrue(other.manager.downloadRemoteChanges().success)
            val remoteAddition = sampleAt(2, hour, phone.deviceId).copy(ownerId = raw.lowercase())
            other.record(remoteAddition)
            assertTrue(other.manager.uploadPendingChanges().success)
            // A restore can retain an existing server sample as local pending work.
            phone.record(original)
            val localAddition = sampleAt(3, hour, phone.deviceId).copy(ownerId = raw)
            phone.record(localAddition)
            val merged = phone.manager.uploadPendingChanges()
            assertTrue(merged.success, merged.errors.toString())
            assertEquals(listOf(original, remoteAddition.copy(ownerId = raw), localAddition), phone.liveSamples(phone.deviceId, hour))
            assertTrue(phone.store.pending(raw, phone.sessions.currentOrigin, 100).isEmpty(), "Accepted samples must not remain dirty")
            assertTrue(phone.pending(EntityType.LOCATION_SAMPLES).isEmpty())
            assertTrue(other.manager.downloadRemoteChanges().success)
            assertEquals(
                listOf(original.copy(ownerId = raw.lowercase()), remoteAddition, localAddition.copy(ownerId = raw.lowercase())),
                other.liveSamples(phone.deviceId, hour),
            )
        }
}
