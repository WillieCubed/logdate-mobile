package app.logdate.client.sync.peer.link

import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [PhoneLinkExchange] and [WatchLinkExchange] against each other with the real BouncyCastle
 * handshake: both devices end with one pair key, and anything changed on the way stops the exchange.
 */
class LinkExchangeTest {
    private val clock = MutableClock(1_000_000)
    private val handshake = BouncyCastleLinkHandshake()
    private val phone = PhoneLinkExchange(handshake, clock)
    private val watch = WatchLinkExchange(handshake, LinkNonceLedger(clock), clock)

    @Test
    fun `a pairing gives both devices one pair key and a confirmation the phone accepts`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            val sealed = assertNotNull(phone.seal(started, accepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            val opened = assertNotNull(watch.open(accepted, sealed.bundle).valueOrNull())

            assertEquals(LinkMode.PAIR, opened.bundle.mode)
            assertContentEquals(sealed.pairKey, opened.pairKey)
            assertTrue(phone.verify(sealed, opened.confirm))
        }

    @Test
    fun `the bundle the watch opens is the one the phone sealed`() =
        runTest {
            val bundle = LinkBundle(mode = LinkMode.CLOUD, accountId = "account", linkCode = "code", linkCodeExpiresAt = 9)
            val started = phone.start(LinkMode.CLOUD, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            val sealed = assertNotNull(phone.seal(started, accepted.reply, WATCH, bundle).valueOrNull())

            assertEquals(bundle, assertNotNull(watch.open(accepted, sealed.bundle).valueOrNull()).bundle)
        }

    @Test
    fun `an offer older than a minute is refused`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            clock.millis += 60_000

            assertEquals(LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER), watch.accept(started.offer, WATCH, PHONE))
        }

    @Test
    fun `the same offer cannot be accepted twice`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            watch.accept(started.offer, WATCH, PHONE)

            assertEquals(LinkStep.Failed(LinkExchangeFailure.REPLAYED_NONCE), watch.accept(started.offer, WATCH, PHONE))
        }

    @Test
    fun `a bundle sealed for another watch does not open`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            val sealed = assertNotNull(phone.seal(started, accepted.reply, "another-watch", LinkBundle(mode = LinkMode.PAIR)).valueOrNull())

            assertEquals(LinkStep.Failed(LinkExchangeFailure.BUNDLE_UNREADABLE), watch.open(accepted, sealed.bundle))
        }

    @Test
    fun `a changed ciphertext does not open`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            val sealed = assertNotNull(phone.seal(started, accepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            val bytes = assertNotNull(LinkWire.decodeKey(sealed.bundle.ciphertext))
            bytes[0] = (bytes[0].toInt() xor 1).toByte()
            val tampered = SealedLinkBundle(iv = sealed.bundle.iv, ciphertext = LinkWire.encodeKey(bytes))

            assertEquals(LinkStep.Failed(LinkExchangeFailure.BUNDLE_UNREADABLE), watch.open(accepted, tampered))
        }

    @Test
    fun `a confirmation from another exchange is not accepted`() =
        runTest {
            val first = phone.start(LinkMode.PAIR, PHONE)
            val firstAccepted = assertNotNull(watch.accept(first.offer, WATCH, PHONE).valueOrNull())
            val firstSealed = assertNotNull(phone.seal(first, firstAccepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            val second = phone.start(LinkMode.PAIR, PHONE)
            val secondAccepted = assertNotNull(watch.accept(second.offer, WATCH, PHONE).valueOrNull())
            val secondSealed =
                assertNotNull(phone.seal(second, secondAccepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            val secondOpened = assertNotNull(watch.open(secondAccepted, secondSealed.bundle).valueOrNull())

            assertFalse(phone.verify(firstSealed, secondOpened.confirm))
        }

    @Test
    fun `the phone does not seal a bundle once its offer has expired`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            clock.millis += 60_000

            assertEquals(
                LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER),
                phone.seal(started, accepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)),
            )
        }

    @Test
    fun `direct exchange rejects unsupported schemas`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            assertEquals(
                LinkStep.Failed(LinkExchangeFailure.UNSUPPORTED_SCHEMA),
                watch.accept(started.offer.copy(schema = 2), WATCH, PHONE),
            )
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            assertEquals(
                LinkStep.Failed(LinkExchangeFailure.UNSUPPORTED_SCHEMA),
                phone.seal(started, accepted.reply.copy(schema = 0), WATCH, LinkBundle(mode = LinkMode.PAIR)),
            )
            val sealed = assertNotNull(phone.seal(started, accepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            assertEquals(LinkStep.Failed(LinkExchangeFailure.UNSUPPORTED_SCHEMA), watch.open(accepted, sealed.bundle.copy(schema = 2)))
            val opened = assertNotNull(watch.open(accepted, sealed.bundle).valueOrNull())
            assertFalse(phone.verify(sealed, opened.confirm.copy(schema = 2)))
        }

    @Test
    fun `offer nonces have exactly sixteen bytes`() =
        runTest {
            for (size in listOf(0, 15, 17)) {
                val started = phone.start(LinkMode.PAIR, PHONE)
                val offer = started.offer.copy(phoneNonce = LinkWire.encodeKey(ByteArray(size)))
                assertEquals(LinkStep.Failed(LinkExchangeFailure.BAD_KEY), watch.accept(offer, WATCH, PHONE))
            }
        }

    @Test
    fun `accept nonces have exactly sixteen bytes`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            for (size in listOf(0, 15, 17)) {
                assertEquals(
                    LinkStep.Failed(LinkExchangeFailure.BAD_KEY),
                    phone.seal(
                        started,
                        accepted.reply.copy(watchNonce = LinkWire.encodeKey(ByteArray(size))),
                        WATCH,
                        LinkBundle(mode = LinkMode.PAIR),
                    ),
                )
            }
        }

    @Test
    fun `the phone cannot seal another link mode than it offered`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            assertEquals(
                LinkStep.Failed(LinkExchangeFailure.MALFORMED),
                phone.seal(started, accepted.reply, WATCH, LinkBundle(mode = LinkMode.CLOUD)),
            )
        }

    @Test
    fun `the watch rejects an authenticated bundle with another mode`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            val body = LinkWire.write(LinkBundle.serializer(), LinkBundle(mode = LinkMode.CLOUD))
            val sealed = handshake.seal(accepted.keys.bundleKey, body, accepted.transcriptHash)
            val wire = SealedLinkBundle(iv = LinkWire.encodeKey(sealed.iv), ciphertext = LinkWire.encodeKey(sealed.ciphertext))
            assertEquals(LinkStep.Failed(LinkExchangeFailure.MALFORMED), watch.open(accepted, wire))
        }

    @Test
    fun `clock rollback cannot make a not yet valid offer usable`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            val sealed = assertNotNull(phone.seal(started, accepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            clock.millis -= 1
            assertEquals(
                LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER),
                phone.seal(started, accepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)),
            )
            assertEquals(LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER), watch.open(accepted, sealed.bundle))
        }

    private class MutableClock(
        var millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val PHONE = "phone-node"
        const val WATCH = "watch-node"
    }
}
