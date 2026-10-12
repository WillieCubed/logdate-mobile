package app.logdate.wear.sync.v2.delivery

import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.CaptureIdentity
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import app.logdate.client.sync.peer.link.LinkWire
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** Tests [WatchReceiptApplier]: the phone's receipts move the watch's ledger forward and nothing else. */
class WatchReceiptApplierTest {
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val peers = mockk<WatchDeliveryPeer>()
    private val context =
        PeerDeliveryContext(
            LinkWire.encodeKey(ByteArray(32) { 1 }),
            "phone",
            "watch",
            LinkMode.CLOUD,
            "account",
            "https://logdate.test",
        )
    private var current: PeerDeliveryContext? = context
    private val applier = WatchReceiptApplier(ledger, peers)

    init {
        coEvery { peers.current() } answers { current }
        coEvery { peers.isCurrent(any()) } answers { firstArg<PeerDeliveryContext>() == current }
    }

    private val id = Uuid.random().toString()

    @Test
    fun `a receipt moves its note forward and is counted`() =
        runTest {
            track(version = 5)

            assertEquals(1, applier.apply("phone", bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.BACKED_UP, serverVersion = 7))))
            assertEquals(DeliveryState.BACKED_UP, state())
            assertEquals(CloudPresence.SIGNED_IN, applier.phoneCloud())
        }

    @Test
    fun `a receipt for an older version moves nothing`() =
        runTest {
            track(version = 6)

            assertEquals(0, applier.apply("phone", bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.BACKED_UP))))
            assertEquals(DeliveryState.NEW, state())
        }

    @Test
    fun `a rejection covers every version`() =
        runTest {
            track(version = 5)

            applier.apply("phone", bytes(Receipt(RecordKind.NOTE, id, Long.MAX_VALUE, ReceiptState.REJECTED_UNSUPPORTED)))

            assertEquals(DeliveryState.REJECTED, state())
        }

    @Test
    fun `receipts for other kinds are ignored`() =
        runTest {
            track(version = 5)

            assertEquals(0, applier.apply("phone", bytes(Receipt(RecordKind.MOMENT, id, 5, ReceiptState.BACKED_UP))))
        }

    @Test
    fun `receipts this build cannot read change nothing`() =
        runTest {
            track(version = 5)

            assertEquals(0, applier.apply("phone", "nope".encodeToByteArray()))
            assertNull(applier.phoneCloud())
        }

    @Test
    fun `a backup receipt without a positive server version is not proof`() =
        runTest {
            track(version = 5)
            for (version in listOf(null, 0L, -1L)) {
                assertEquals(
                    0,
                    applier.apply("phone", bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.BACKED_UP, serverVersion = version))),
                )
                assertEquals(DeliveryState.NEW, state())
            }
        }

    @Test
    fun `a repeated rejection moves nothing and does not start another sync`() =
        runTest {
            track(version = 5)
            val rejected = bytes(Receipt(RecordKind.NOTE, id, Long.MAX_VALUE, ReceiptState.REJECTED_UNSUPPORTED))
            assertEquals(1, applier.apply("phone", rejected))
            assertEquals(0, applier.apply("phone", rejected))
            assertEquals(DeliveryState.REJECTED, state())
        }

    @Test
    fun `repeated on phone receipts retain the original grace period`() =
        runTest {
            track(version = 5)
            val received = bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE))
            assertEquals(1, applier.apply("phone", received))
            val original = ledger.get(context.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, id)
            assertEquals(0, applier.apply("phone", received))
            assertEquals(original, ledger.get(context.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, id))
        }

    @Test
    fun `a different source cannot apply receipts or account status`() =
        runTest {
            ledger.track(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id, 5)
            assertEquals(
                0,
                applier.apply("another-phone", bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.BACKED_UP, 7))),
            )
            assertNull(applier.phoneCloud())
        }

    @Test
    fun `a missing source cannot apply receipts or account status`() =
        runTest {
            assertEquals(0, applier.apply(null, bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE))))
            assertNull(applier.phoneCloud())
        }

    @Test
    fun `an unbound response cannot apply receipts or account status`() =
        runTest {
            val response =
                ReceiptsResponse(
                    cloud = CloudPresence.NO_ACCOUNT,
                    receipts = listOf(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE)),
                )
            assertEquals(0, applier.apply("phone", WearJson.encodeToBytes(ReceiptsResponse.serializer(), response)))
            assertNull(applier.phoneCloud())
        }

    @Test
    fun `a replaced exchange cannot apply receipts or account status`() =
        runTest {
            current = context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            assertEquals(0, applier.apply("phone", bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE))))
            assertNull(applier.phoneCloud())
        }

    @Test
    fun `account status from a former exchange is discarded after relinking`() =
        runTest {
            applier.apply("phone", bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE)))
            assertEquals(CloudPresence.SIGNED_IN, applier.phoneCloud())
            current = context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            assertNull(applier.phoneCloud())
        }

    @Test
    fun `account status is discarded after revocation`() =
        runTest {
            applier.apply("phone", bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE)))
            current = null
            assertNull(applier.phoneCloud())
        }

    @Test
    fun `a cloud link cannot claim that the phone has no account`() =
        runTest {
            track(5)
            val response =
                ReceiptsResponse(
                    cloud = CloudPresence.NO_ACCOUNT,
                    receipts = listOf(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE)),
                    context = context,
                )
            assertEquals(0, applier.apply("phone", WearJson.encodeToBytes(ReceiptsResponse.serializer(), response)))
            assertEquals(DeliveryState.NEW, state())
            assertNull(applier.phoneCloud())
        }

    @Test
    fun `a valid receipt changes only its link's history`() =
        runTest {
            track(5)
            ledger.track(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id, 5)
            applier.apply("phone", bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.BACKED_UP, 7)))
            assertEquals(DeliveryState.BACKED_UP, state())
            assertEquals(DeliveryState.NEW, ledger.get(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id)?.state)
        }

    @Test
    fun `a pair-only phone can confirm local storage without an account`() =
        runTest {
            val pair = context.copy(mode = LinkMode.PAIR, accountId = null, serverOrigin = null)
            current = pair
            track(5)
            val response =
                ReceiptsResponse(
                    cloud = CloudPresence.NO_ACCOUNT,
                    receipts = listOf(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE)),
                    context = pair,
                )
            assertEquals(1, applier.apply("phone", encode(response)))
            assertEquals(DeliveryState.ON_PHONE, state())
            assertEquals(CloudPresence.NO_ACCOUNT, applier.phoneCloud())
        }

    @Test
    fun `a pair-only exchange cannot claim an installed cloud account`() =
        runTest {
            val pair = context.copy(mode = LinkMode.PAIR, accountId = null, serverOrigin = null)
            current = pair
            track(5)
            val response =
                ReceiptsResponse(
                    cloud = CloudPresence.SIGNED_IN,
                    receipts = listOf(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE)),
                    context = pair,
                )
            assertEquals(0, applier.apply("phone", encode(response)))
            assertEquals(DeliveryState.NEW, state())
            assertNull(applier.phoneCloud())
        }

    @Test
    fun `restoring an unavailable link does not revive discarded account status`() =
        runTest {
            applier.apply("phone", bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE)))
            current = null
            assertNull(applier.phoneCloud())
            current = context
            assertNull(applier.phoneCloud())
        }

    @Test
    fun `invalidating an old cache read cannot erase a concurrently accepted new exchange`() =
        runTest {
            applier.apply("phone", bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE)))
            val renewed = context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            coEvery { peers.isCurrent(context) } coAnswers {
                current = renewed
                applier.apply("phone", encode(ReceiptsResponse(cloud = CloudPresence.SIGNED_IN, receipts = emptyList(), context = renewed)))
                false
            }
            assertNull(applier.phoneCloud())
            assertEquals(CloudPresence.SIGNED_IN, applier.phoneCloud())
        }

    @Test
    fun `an old response cannot overwrite status accepted from a new exchange`() =
        runTest {
            val renewed = context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            var checks = 0
            coEvery { peers.isCurrent(context) } coAnswers {
                checks++
                if (checks == 2) {
                    current = renewed
                    applier.apply(
                        "phone",
                        encode(ReceiptsResponse(cloud = CloudPresence.SIGNED_IN, receipts = emptyList(), context = renewed)),
                    )
                    true
                } else {
                    current == context
                }
            }
            applier.apply(
                "phone",
                encode(ReceiptsResponse(cloud = CloudPresence.UNKNOWN, receipts = emptyList(), context = context)),
            )
            assertEquals(CloudPresence.SIGNED_IN, applier.phoneCloud())
        }

    @Test
    fun `a relink during a batch stops applying its remaining receipts`() =
        runTest {
            val memory = MemoryPeerDeliveryDao()
            val renewed = context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            val storage =
                object : PeerDeliveryDao by memory {
                    override suspend fun upsert(entry: PeerDeliveryEntity) {
                        memory.upsert(entry)
                        if (entry.state == "BACKED_UP") current = renewed
                    }
                }
            val isolatedLedger = PeerDeliveryLedger(storage)
            val isolated = WatchReceiptApplier(isolatedLedger, peers)
            val second = Uuid.random().toString()
            listOf(id, second).forEach { noteId ->
                isolatedLedger.track(context.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, noteId, 5)
                isolatedLedger.track(renewed.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, noteId, 5)
            }
            val response =
                ReceiptsResponse(
                    cloud = CloudPresence.SIGNED_IN,
                    receipts =
                        listOf(
                            Receipt(RecordKind.NOTE, id, 5, ReceiptState.BACKED_UP, 7),
                            Receipt(RecordKind.NOTE, second, 5, ReceiptState.BACKED_UP, 7),
                        ),
                    context = context,
                )
            assertEquals(0, isolated.apply("phone", encode(response)))
            assertNull(isolated.phoneCloud())
            assertEquals(
                DeliveryState.NEW,
                isolatedLedger.get(context.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, second)?.state,
            )
            for (noteId in listOf(id, second)) {
                assertEquals(
                    DeliveryState.NEW,
                    isolatedLedger.get(renewed.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, noteId)?.state,
                )
            }
        }

    private fun encode(response: ReceiptsResponse) = WearJson.encodeToBytes(ReceiptsResponse.serializer(), response)

    @Test fun momentReceiptMovesOnlyItsExactTypedOperation() =
        runTest {
            val key = CaptureIdentity.of(id, "mark-operation")!!.ledgerKey
            ledger.track(context.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.MOMENT, key, 5)
            ledger.track(context.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, key, 5)
            assertEquals(
                0,
                applier.apply("phone", bytes(Receipt(RecordKind.MOMENT, id, 5, ReceiptState.ON_PHONE, operationId = "other-operation"))),
            )
            assertEquals(
                1,
                applier.apply("phone", bytes(Receipt(RecordKind.MOMENT, id, 5, ReceiptState.ON_PHONE, operationId = "mark-operation"))),
            )
            assertEquals(DeliveryState.ON_PHONE, ledger.get(context.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.MOMENT, key)?.state)
            assertEquals(DeliveryState.NEW, ledger.get(context.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, key)?.state)
        }

    private suspend fun track(version: Long) = ledger.track(context.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, id, version)

    private suspend fun state() = ledger.get(context.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, id)?.state

    private fun bytes(receipt: Receipt) =
        WearJson.encodeToBytes(
            ReceiptsResponse.serializer(),
            ReceiptsResponse(cloud = CloudPresence.SIGNED_IN, receipts = listOf(receipt), context = context),
        )
}
