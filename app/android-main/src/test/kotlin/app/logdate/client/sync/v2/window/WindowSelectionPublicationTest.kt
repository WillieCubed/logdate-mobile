package app.logdate.client.sync.v2.window

import app.logdate.client.domain.watch.DefaultWatchPreferencesRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.domain.watch.WatchWindowPreferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class WindowSelectionPublicationTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val now = Instant.parse("2026-10-12T15:00:00Z")
    private val shared = StampedPreferenceStore(MemoryKeyValueStorage(), PeerRole.PHONE)
    private val preferences = DefaultWatchPreferencesRepository(shared)
    private val notes = MutableStateFlow<List<JournalNote>>(emptyList())
    private val removed = mutableListOf<String>()
    private val published = mutableListOf<String>()
    private val publisher =
        WatchWindowPublisher(
            active = MutableStateFlow(true),
            peers =
                mockk<PhoneDeliveryPeers> {
                    coEvery { current(any()) } returns peer
                    coEvery { all() } returns listOf(peer)
                    coEvery { isCurrent(any()) } returns true
                },
            supports = { true },
            notes = mockk<JournalNotesRepository> { every { allNotesObserved } returns notes },
            dayStartHour = { 4 },
            extras = WindowExtras.None,
            manifest = WindowManifestStore(MemoryKeyValueStorage()),
            items =
                object : WindowDataItems {
                    override suspend fun put(
                        path: String,
                        data: Map<String, String>,
                        urgent: Boolean,
                    ): Boolean {
                        published += path
                        return true
                    }

                    override suspend fun deleteOwn(path: String): Boolean {
                        removed += path
                        return true
                    }
                },
            zone = { TimeZone.UTC },
            clock =
                object : Clock {
                    override fun now(): Instant = now
                },
            preferences = { WatchWindowPreferences.from(shared.snapshot()) },
            changes =
                shared
                    .observe()
                    .map(WatchWindowPreferences::from)
                    .distinctUntilChanged()
                    .map { },
        )

    @Test
    fun `changing library days republishes membership and evicts older notes`() =
        runTest {
            val older = note(now - 20.days)
            notes.value = listOf(older)
            preferences.set(SharedPreferenceKey.WindowDays, 30)
            assertEquals(1, assertNotNull(publisher.publishIfActive("watch")).written)
            preferences.set(SharedPreferenceKey.WindowDays, 7)
            assertEquals(1, assertNotNull(publisher.publishIfActive("watch")).removed)
            assertTrue(WearPaths.windowItem(RecordKind.NOTE, older.uid, peer) in removed)
        }

    @Test
    fun `shared preference changes republish without a note edit or sync request`() =
        runTest {
            val older = note(now - 20.days)
            notes.value = listOf(older)
            publisher.start(backgroundScope)
            runCurrent()
            advanceTimeBy(3_000)
            runCurrent()
            assertTrue(published.isEmpty())
            preferences.set(SharedPreferenceKey.WindowDays, 30)
            advanceTimeBy(3_000)
            runCurrent()
            val path = WearPaths.windowItem(RecordKind.NOTE, older.uid, peer)
            assertTrue(path in published)
            preferences.set(SharedPreferenceKey.WindowDays, 7)
            advanceTimeBy(3_000)
            runCurrent()
            assertTrue(path in removed)
        }

    private fun note(at: Instant) =
        JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = at,
            lastUpdated = at,
            content = "Remember this",
        )
}
