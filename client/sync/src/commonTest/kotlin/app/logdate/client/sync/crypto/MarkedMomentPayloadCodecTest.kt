package app.logdate.client.sync.crypto

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkedMomentPayloadCodecTest {
    private val mark =
        MarkedMoment(
            id = Uuid.parse("10000000-0000-0000-0000-000000000001"),
            capturedAt = Instant.parse("2026-10-12T12:34:56.123456789Z"),
            timeZoneId = "America/Los_Angeles",
            location = MarkLocation(36.17, -115.14, 4.5f),
            heartRateBpm = 72,
            state = MarkState.FILLED,
            caption = "A quiet walk 🌵",
            resolvedNoteIds = setOf(Uuid.parse("20000000-0000-0000-0000-000000000001")),
            originDeviceId = "watch-origin",
            lastUpdated = Instant.parse("2026-10-12T12:35:56.987654321Z"),
            syncVersion = 3,
        )

    @Test
    fun `encrypted payload preserves all fields and precise clocks using authoritative server version`() =
        runTest {
            val codec = MarkedMomentPayloadCodec(cipher())
            val payload = codec.encode(mark)
            assertTrue(payload.startsWith("LDSE2:"))
            assertTrue(!payload.contains(mark.caption))
            assertTrue(!payload.contains(mark.originDeviceId))
            assertEquals(mark.copy(syncVersion = 9), codec.decode(mark.id, 1, 9, payload))
        }

    @Test
    fun `every state and absent optional context round trips`() =
        runTest {
            val codec = MarkedMomentPayloadCodec(cipher())
            for (state in MarkState.entries) {
                val moment = mark.copy(state = state, location = null, heartRateBpm = null, resolvedNoteIds = emptySet())
                assertEquals(moment, codec.decode(moment.id, 1, 3, codec.encode(moment)))
            }
        }

    @Test
    fun `plaintext future schemas and invalid versions cannot become remote marks`() =
        runTest {
            val codec = MarkedMomentPayloadCodec(cipher())
            val payload = codec.encode(mark)
            assertFailsWith<IllegalArgumentException> { codec.decode(mark.id, 2, 9, payload) }
            assertFailsWith<IllegalArgumentException> { codec.decode(mark.id, 1, -1, payload) }
            assertFailsWith<IllegalArgumentException> { codec.decode(mark.id, 1, 9, "{}") }
            assertFailsWith<IllegalArgumentException> { codec.decode(mark.id, 1, 9, "LDSE1:{}") }
        }

    @Test
    fun `payload bound to another mark is rejected even with a valid encryption envelope`() =
        runTest {
            val cipher = cipher()
            val codec = MarkedMomentPayloadCodec(cipher)
            val plaintext = cipher.decryptString("sync:moment:${mark.id}:payload", codec.encode(mark))
            val otherId = Uuid.parse("30000000-0000-0000-0000-000000000001")
            val rebound = cipher.encryptString("sync:moment:$otherId:payload", plaintext)
            assertFailsWith<IllegalArgumentException> { codec.decode(otherId, 1, 9, rebound) }
        }

    @Test
    fun `future lifecycle and damaged ciphertext cannot silently default to open`() =
        runTest {
            val cipher = cipher()
            val codec = MarkedMomentPayloadCodec(cipher)
            val payload = codec.encode(mark)
            val field = "sync:moment:${mark.id}:payload"
            val plaintext = cipher.decryptString(field, payload)
            val future = cipher.encryptString(field, plaintext.replace("FILLED", "FUTURE"))
            assertFailsWith<IllegalArgumentException> { codec.decode(mark.id, 1, 9, future) }
            assertFailsWith<UnreadablePayloadException> { codec.decode(mark.id, 1, 9, "LDSE2:corrupt") }
        }

    @Test
    fun `a different account identity cannot decode the mark`() =
        runTest {
            val payload = MarkedMomentPayloadCodec(cipher()).encode(mark)
            assertFailsWith<UnreadablePayloadException> {
                MarkedMomentPayloadCodec(cipher("other")).decode(mark.id, 1, 9, payload)
            }
        }

    private suspend fun cipher(seed: String = "moments"): SyncPayloadCipher {
        val crypto = TestCryptoManager()
        val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
        identity.recoverIdentity((1..12).map { "$seed-$it" })
        return SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
    }
}
