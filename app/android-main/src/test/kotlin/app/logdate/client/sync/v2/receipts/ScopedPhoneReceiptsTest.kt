package app.logdate.client.sync.v2.receipts

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsRequest
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.FakeSessions
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import app.logdate.client.sync.v2.MemoryPairKeyVault
import app.logdate.client.sync.v2.MemoryPeerDeliveryDao
import app.logdate.client.sync.v2.link.LinkPlan
import app.logdate.client.sync.v2.link.LinkTarget
import app.logdate.client.sync.v2.link.PhoneWatchLinkStore
import app.logdate.shared.config.LogDateConfigRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ScopedPhoneReceiptsTest {
    private val sessions = FakeSessions(OriginBoundSession(ORIGIN, UserSession("access", "refresh", "account")))
    private val links = PhoneWatchLinkStore(MemoryKeyValueStorage(), MemoryPairKeyVault())
    private val config = mockk<LogDateConfigRepository>()
    private var active = true
    private val peers = PhoneDeliveryPeers(links, sessions, config, { "phone" }, { active })
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val notes = mockk<JournalNotesRepository>()
    private val outbox = mockk<SyncMetadataService>()
    private val book = PhoneReceiptBook(notes, outbox, sessions, ledger, peers)
    private val responder = WatchReceiptResponder(book)
    private val id = Uuid.random()
    private val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.CLOUD, "account", ORIGIN)
    private val query = ReceiptQuery(RecordKind.NOTE, id.toString(), 5)

    init {
        every { config.getCurrentBackendUrl() } returns ORIGIN
        coEvery { notes.getNoteById(id) } returns
            JournalNote.Text(
                uid = id,
                creationTimestamp = Instant.fromEpochMilliseconds(1),
                lastUpdated = Instant.fromEpochMilliseconds(5),
                content = "watch capture",
                syncVersion = 7,
            )
        coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } returns false
    }

    @Test
    fun `a current watch receives backup proof for its own delivered version`() =
        runTest {
            link()
            received(context)
            val response = answer()
            assertEquals(context, response.context)
            assertEquals(CloudPresence.SIGNED_IN, response.cloud)
            assertEquals(ReceiptState.BACKED_UP, response.receipts.single().state)
            assertEquals(7L, response.receipts.single().serverVersion)
        }

    @Test
    fun `a current pair only watch can confirm storage without a cloud account`() =
        runTest {
            sessions.bound = null
            val pair = context.copy(mode = LinkMode.PAIR, accountId = null, serverOrigin = null)
            link(pair)
            received(pair)
            val response = answer(pair)
            assertEquals(pair, response.context)
            assertEquals(CloudPresence.NO_ACCOUNT, response.cloud)
            assertEquals(ReceiptState.ON_PHONE, response.receipts.single().state)
        }

    @Test
    fun `another source cannot request the linked watch's receipts`() =
        runTest {
            link()
            received(context)
            assertUntrusted(answer(source = "another-watch"))
            coVerify(exactly = 0) { notes.getNoteById(any()) }
        }

    @Test
    fun `missing context reveals neither account status nor note state`() =
        runTest {
            link()
            received(context)
            assertUntrusted(answer(requestContext = null))
            coVerify(exactly = 0) { notes.getNoteById(any()) }
        }

    @Test
    fun `a fresh link cannot reuse earlier receipts even for the same account`() =
        runTest {
            link()
            received(context)
            link(context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 })))
            assertUntrusted(answer())
            assertTrue(answer(context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))).receipts.isEmpty())
        }

    @Test
    fun `note existence alone cannot prove delivery to this watch`() =
        runTest {
            link()
            assertTrue(answer().receipts.isEmpty())
            coVerify(exactly = 0) { notes.getNoteById(any()) }
        }

    @Test
    fun `another watch's history cannot prove a deletion`() =
        runTest {
            val other = context.copy(watchNode = "other-watch", linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            link()
            link(other)
            received(other)
            coEvery { notes.getNoteById(id) } returns null
            assertTrue(answer().receipts.isEmpty())
        }

    @Test
    fun `a deletion proof is limited to versions received on this link`() =
        runTest {
            link()
            received(context, version = 4)
            coEvery { notes.getNoteById(id) } returns null
            assertTrue(answer().receipts.isEmpty())
            received(context, version = 5)
            assertEquals(ReceiptState.DELETED_ON_PHONE, answer().receipts.single().state)
        }

    @Test
    fun `signed out phones cannot answer the previous account's request`() =
        runTest {
            link()
            received(context)
            sessions.bound = null
            assertUntrusted(answer())
        }

    @Test
    fun `changed account or server cannot answer the previous link`() =
        runTest {
            link()
            received(context)
            for (bound in listOf(
                OriginBoundSession(ORIGIN, UserSession("new", "refresh", "other-account")),
                OriginBoundSession("https://other.test", UserSession("new", "refresh", "account")),
            )) {
                sessions.bound = bound
                assertUntrusted(answer())
            }
        }

    @Test
    fun `account change during lookup discards the entire response`() =
        runTest {
            link()
            received(context)
            coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } answers {
                sessions.bound = null
                false
            }
            assertUntrusted(answer())
        }

    @Test
    fun `disabling delivery during lookup discards the entire response`() =
        runTest {
            link()
            received(context)
            coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } answers {
                active = false
                false
            }
            assertUntrusted(answer())
        }

    @Test
    fun `a relink during lookup discards the previous exchange's response`() =
        runTest {
            link()
            received(context)
            coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } coAnswers {
                link(context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 })))
                false
            }
            assertUntrusted(answer())
        }

    @Test
    fun `receipt lookup cancellation is propagated`() =
        runTest {
            link()
            received(context)
            coEvery { notes.getNoteById(id) } throws CancellationException("Stopped")
            assertFailsWith<CancellationException> { answer() }
        }

    @Test
    fun `a pending phone change cannot be reported as backed up`() =
        runTest {
            link()
            received(context)
            coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } returns true
            val receipt = answer().receipts.single()
            assertEquals(ReceiptState.ON_PHONE, receipt.state)
            assertNull(receipt.serverVersion)
        }

    @Test
    fun `a malformed record identity is rejected only for the current source`() =
        runTest {
            link()
            val request =
                WearJson.encodeToBytes(
                    ReceiptsRequest.serializer(),
                    ReceiptsRequest(items = listOf(query.copy(id = "invalid")), context = context),
                )
            val bytes = responder.answer("watch", request)
            val response = (WearJson.decode(bytes, 1, ReceiptsResponse.serializer()) as Decoded.Supported).value
            assertEquals(context, response.context)
            assertEquals(ReceiptState.REJECTED_UNSUPPORTED, response.receipts.single().state)
            coVerify(exactly = 0) { notes.getNoteById(any()) }
        }

    @Test
    fun `earlier capture history cannot acknowledge a new operation with the same version`() =
        runTest {
            link()
            received(context)
            assertNull(book.receiptFor(context, query.copy(operationId = "new-operation")))
        }

    @Test
    fun `a stored operation is acknowledged with its exact operation identity`() =
        runTest {
            link()
            ledger.track(context.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, "$id@operation", 5)
            val receipt = book.receiptFor(context, query.copy(operationId = "operation"))
            assertEquals("operation", receipt?.operationId)
            assertEquals(id.toString(), receipt?.id)
            assertEquals(ReceiptState.BACKED_UP, receipt?.state)
        }

    @Test
    fun `capture history cannot certify a removal even with the same operation identity`() =
        runTest {
            link()
            ledger.track(context.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, "$id@operation", 5)
            assertNull(book.receiptFor(context, query.copy(operationId = "operation", removed = true)))
        }

    private suspend fun link(peer: PeerDeliveryContext = context) {
        links.recordSuccess(
            LinkTarget(peer.watchNode, "Watch"),
            LinkPlan(peer.mode, peer.accountId, peer.serverOrigin),
            ByteArray(32),
            1,
            1,
            linkId = peer.linkId,
        )
    }

    private suspend fun received(
        peer: PeerDeliveryContext,
        version: Long = 5,
    ) {
        ledger.track(peer.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString(), version)
    }

    private suspend fun answer(
        requestContext: PeerDeliveryContext? = context,
        source: String = "watch",
    ): ReceiptsResponse {
        val request = WearJson.encodeToBytes(ReceiptsRequest.serializer(), ReceiptsRequest(items = listOf(query), context = requestContext))
        val bytes = responder.answer(source, request)
        return (WearJson.decode(bytes, 1, ReceiptsResponse.serializer()) as Decoded.Supported).value
    }

    private fun assertUntrusted(response: ReceiptsResponse) {
        assertNull(response.context)
        assertEquals(CloudPresence.UNKNOWN, response.cloud)
        assertTrue(response.receipts.isEmpty())
    }

    private companion object {
        const val ORIGIN = "https://logdate.test"
    }
}
