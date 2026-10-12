package app.logdate.client.sync.v2.window

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Instant

class MomentWindowIntegrationTest {
    @Test fun typedMomentMembersReturnAlongsideSameIdNotesOnlyToNegotiatedWatches() =
        runTest {
            val now = Instant.parse("2026-10-12T15:00:00Z")
            val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
            val peers =
                mockk<PhoneDeliveryPeers> {
                    coEvery { current("watch") } returns peer
                    coEvery { isCurrent(peer) } returns true
                }
            val recent = JournalNote.Text(content = "note", creationTimestamp = now, lastUpdated = now)
            val notes = mockk<JournalNotesRepository> { every { allNotesObserved } returns MutableStateFlow(listOf(recent)) }
            val mark = MarkedMoment(recent.uid, now, "UTC", null, 72, MarkState.FILLED, "resolved", emptySet(), "watch", now, 7)
            val momentFlow = MutableStateFlow(listOf(mark))
            var supported = false
            val manifest = WindowManifestStore(MemoryKeyValueStorage())
            val items =
                object : WindowDataItems {
                    override suspend fun put(
                        path: String,
                        data: Map<String, String>,
                        urgent: Boolean,
                    ) = true

                    override suspend fun deleteOwn(path: String) = true
                }
            val publisher =
                WatchWindowPublisher(
                    active = MutableStateFlow(true),
                    peers = peers,
                    supports = { true },
                    notes = notes,
                    dayStartHour = { 4 },
                    extras = WindowExtras.None,
                    manifest = manifest,
                    items = items,
                    zone = { TimeZone.UTC },
                    clock =
                        object : Clock {
                            override fun now() = now
                        },
                    moments = momentFlow,
                    acceptsMoments = { supported },
                )
            assertEquals(listOf(RecordKind.NOTE), publisher.publishIfActive("watch")!!.members.map { it.kind })
            supported = true
            val result = publisher.publishIfActive("watch")!!
            assertEquals(setOf(RecordKind.NOTE, RecordKind.MOMENT), result.members.map { it.kind }.toSet())
            val saved = manifest.read(peer)
            assertNotNull(saved.items[recent.uid.toString()])
            assertNotNull(saved.moments[recent.uid.toString()])
            momentFlow.value = listOf(mark.copy(caption = "resolved with a caption"))
            assertEquals(1, publisher.publishIfActive("watch")!!.written)
        }
}
