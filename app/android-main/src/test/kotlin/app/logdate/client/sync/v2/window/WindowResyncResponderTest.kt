package app.logdate.client.sync.v2.window

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.datalayer.v2.WindowResyncRequest
import app.logdate.client.sync.datalayer.v2.WindowResyncResponse
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Tests [WindowResyncResponder]: a watch asking for the whole window gets it, freshly published. */
class WindowResyncResponderTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private var current = peer
    private val peers =
        mockk<PhoneDeliveryPeers> {
            coEvery { current(any()) } answers { current.takeIf { it.watchNode == firstArg<String>() } }
            coEvery { isCurrent(any()) } answers { firstArg<PeerDeliveryContext>() == current }
        }
    private val publisher = mockk<WatchWindowPublisher>()
    private val responder = WindowResyncResponder(publisher, peers)
    private val request = WearJson.encodeToBytes(WindowResyncRequest.serializer(), WindowResyncRequest(context = peer))

    @Test
    fun `the watch gets the whole window the phone just published`() =
        runTest {
            val members = listOf(WindowMemberRef(RecordKind.NOTE, "a", 5))
            coEvery { publisher.publishIfActive("watch") } returns
                WindowPublishResult(context = peer, members = members, written = 0, removed = 0, failed = 0, tooLarge = 0, publishedAt = 9)

            val answer = assertNotNull(responder.answer("watch", request))

            assertEquals(
                Decoded.Supported(WindowResyncResponse(context = peer, members = members, publishedAt = 9)),
                WearJson.decode(answer, WearProtocol.WINDOW_SCHEMA, WindowResyncResponse.serializer()),
            )
        }

    @Test
    fun `a request from a newer watch gets no answer`() =
        runTest {
            assertNull(responder.answer("watch", """{"schema":2}""".encodeToByteArray()))
        }

    @Test
    fun `with the window off on this phone the request gets no answer`() =
        runTest {
            coEvery { publisher.publishIfActive("watch") } returns null

            assertNull(responder.answer("watch", request))
        }

    @Test
    fun `a different source cannot ask on behalf of the linked watch`() =
        runTest {
            assertNull(responder.answer("stranger", request))
        }

    @Test
    fun `a previous exchange cannot receive the current window`() =
        runTest {
            current = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            assertNull(responder.answer("watch", request))
        }

    @Test
    fun `a link changed while publishing cannot receive a resync answer`() =
        runTest {
            coEvery { publisher.publishIfActive("watch") } answers {
                current = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
                WindowPublishResult(
                    context = peer,
                    members = emptyList(),
                    written = 0,
                    removed = 0,
                    failed = 0,
                    tooLarge = 0,
                    publishedAt = 9,
                )
            }
            assertNull(responder.answer("watch", request))
        }
}
