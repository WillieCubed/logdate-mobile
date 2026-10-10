package app.logdate.client.sync.datalayer.v2

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests the messages of the link exchange and the ledger that refuses a replayed offer.
 */
class LinkMessagesTest {
    private val offer =
        LinkOffer(
            mode = LinkMode.CLOUD,
            phoneNode = "phone-node",
            phonePublicKey = "cGs=",
            phoneNonce = "bm9uY2U=",
            issuedAt = 1_000,
            expiresAt = 61_000,
        )

    @Test
    fun `an offer reads from its wire form`() {
        val wire =
            """{"schema":1,"mode":"CLOUD","phoneNode":"phone-node","phonePublicKey":"cGs=","phoneNonce":"bm9uY2U=",""" +
                """"issuedAt":1000,"expiresAt":61000,"later":1}"""

        assertEquals(Decoded.Supported(offer), WearJson.decode(wire, WearProtocol.LINK_SCHEMA, LinkOffer.serializer()))
    }

    @Test
    fun `an offer is valid only inside its sixty seconds`() {
        assertFalse(offer.isValidAt(999))
        assertTrue(offer.isValidAt(1_000))
        assertTrue(offer.isValidAt(60_999))
        assertFalse(offer.isValidAt(61_000))
    }

    @Test
    fun `an offer that claims a longer life is never valid`() {
        assertFalse(offer.copy(expiresAt = 61_001).isValidAt(2_000))
    }

    @Test
    fun `the bundle never contains a recovery phrase`() {
        val names =
            LinkBundle
                .serializer()
                .descriptor.elementNames
                .toList()

        assertEquals(
            listOf(
                "mode",
                "accountId",
                "serverOrigin",
                "serverDescriptor",
                "linkCode",
                "linkCodeExpiresAt",
                "identityKey",
                "identityKeyFingerprint",
                "mediaKey",
                "flags",
                "settings",
            ),
            names,
        )
        names.forEach { name ->
            assertFalse(listOf("phrase", "mnemonic", "recovery", "words").any { it in name.lowercase() }, name)
        }
    }

    @Test
    fun `a nonce is accepted once`() =
        runTest {
            val ledger = LinkNonceLedger(FixedClock(1_000))

            assertTrue(ledger.accept("nonce", expiresAt = 61_000))
            assertFalse(ledger.accept("nonce", expiresAt = 61_000))
            assertTrue(ledger.accept("other", expiresAt = 61_000))
        }

    @Test
    fun `the ledger forgets nonces whose offers have expired`() =
        runTest {
            val clock = FixedClock(1_000)
            val ledger = LinkNonceLedger(clock)
            ledger.accept("nonce", expiresAt = 61_000)

            clock.millis = 61_000

            assertTrue(ledger.accept("nonce", expiresAt = 121_000))
        }

    @Test
    fun `a newer link schema is unsupported`() {
        val wire = """{"schema":2,"mode":"CLOUD"}"""

        assertIs<Decoded.Unsupported>(WearJson.decode(wire, WearProtocol.LINK_SCHEMA, LinkOffer.serializer()))
    }

    private class FixedClock(
        var millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }
}
