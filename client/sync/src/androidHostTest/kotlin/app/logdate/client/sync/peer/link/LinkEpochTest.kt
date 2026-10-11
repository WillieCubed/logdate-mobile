package app.logdate.client.sync.peer.link

import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class LinkEpochTest {
    private val handshake = BouncyCastleLinkHandshake()
    private val phone = PhoneLinkExchange(handshake)
    private val watch = WatchLinkExchange(handshake, LinkNonceLedger())

    @Test
    fun `both devices identify the same verified exchange in either mode`() =
        runTest {
            for (mode in LinkMode.entries) {
                val started = phone.start(mode, "phone")
                val accepted = assertNotNull(watch.accept(started.offer, "watch", "phone").valueOrNull())
                val sealed = assertNotNull(phone.seal(started, accepted.reply, "watch", LinkBundle(mode)).valueOrNull())
                val opened = assertNotNull(watch.open(accepted, sealed.bundle).valueOrNull())
                assertEquals(sealed.linkId, opened.linkId)
                assertEquals(32, LinkWire.decodeKey(opened.linkId)?.size)
                assertContentEquals(sealed.transcriptHash, LinkWire.decodeKey(sealed.linkId))
            }
        }

    @Test
    fun `a fresh exchange for the same devices cannot reuse earlier delivery history`() =
        runTest {
            assertNotEquals(link(), link())
        }

    private suspend fun link(): String {
        val started = phone.start(LinkMode.PAIR, "phone")
        val accepted = assertNotNull(watch.accept(started.offer, "watch", "phone").valueOrNull())
        return assertNotNull(phone.seal(started, accepted.reply, "watch", LinkBundle(LinkMode.PAIR)).valueOrNull()).linkId
    }
}
