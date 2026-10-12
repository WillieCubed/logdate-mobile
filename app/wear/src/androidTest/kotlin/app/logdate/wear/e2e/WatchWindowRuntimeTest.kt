package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.PrefixedKeyValueStorage
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.datalayer.v2.WindowResyncResponse
import app.logdate.client.sync.datalayer.v2.WindowTombstoneDataMap
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PairKeyVault
import app.logdate.wear.di.WatchCloud
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.link.WatchLinkStore
import app.logdate.wear.sync.v2.window.WindowApplied
import app.logdate.wear.sync.v2.window.WindowApplier
import app.logdate.wear.sync.v2.window.WindowItem
import app.logdate.wear.sync.v2.window.WindowMembershipStore
import app.logdate.wear.sync.v2.window.WindowPlaces
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Real installed repositories and Room transactions; transport is isolated from other devices. */
@RunWith(AndroidJUnit4::class)
class WatchWindowRuntimeTest {
    private val koin = KoinJavaComponent.getKoin()
    private val notes = koin.get<JournalNotesRepository>() as SyncableJournalNotesRepository
    private val storage = PrefixedKeyValueStorage(koin.get<KeyValueStorage>(), "window_test." + Uuid.random() + ".")
    private val links =
        WatchLinkStore(
            storage,
            object : PairKeyVault {
                override suspend fun put(
                    peer: String,
                    key: ByteArray,
                ) = Unit

                override suspend fun get(peer: String): ByteArray? = null

                override suspend fun remove(peer: String) = Unit
            },
        )
    private var active = true
    private val peers = WatchDeliveryPeer(links, koin.get<SessionStorage>(), koin.get(), { "isolated-test-watch" }, { active })
    private val members = WindowMembershipStore(storage)
    private val transaction = koin.get<SyncTransactionManager>(WatchCloud)
    private val applier = WindowApplier(peers, notes, koin.get(), members, transaction, {}, koin.get(), koin.get<WindowPlaces>()::ensure)

    @Test fun phoneAudioKeepsCaptionAndStructuredTranscriptThroughRoomAndEviction() =
        runBlocking {
            links.savePairing("isolated-test-phone", ByteArray(32), LinkWire.encodeKey(ByteArray(32) { 1 }))
            val peer = assertNotNull(peers.current())
            val note =
                JournalNote.Audio(
                    uid = Uuid.random(),
                    mediaRef = "logdate-media://recordings/window-test.m4a",
                    creationTimestamp = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochSeconds(0, 5_123_456),
                    location = NoteLocation(NoteCoordinates(36.1, -115.1), NotePlace(Uuid.random(), "Phone place", 36.1, -115.1)),
                    timeZoneId = "America/Los_Angeles",
                    caption = "phone caption",
                    transcript = TranscriptDocument.fromPlainText("A saved phone transcript"),
                )
            val envelope = NoteEnvelopeBody.envelope(note, RecordOrigin.PHONE, DeliveryPriority.BULK).copy(context = peer)
            val item =
                WindowItem(
                    peer.phoneNode,
                    WearPaths.windowItem(RecordKind.NOTE, note.uid, peer),
                    EnvelopeDataMap.toDataMap(envelope, 1) + (WindowTombstoneDataMap.KEY_REVISION to "1"),
                )
            val response =
                WindowResyncResponse(
                    context = peer,
                    members = listOf(WindowMemberRef(RecordKind.NOTE, note.uid.toString(), 5, 1)),
                    publishedAt = 9,
                    revision = 1,
                )
            try {
                assertEquals(WindowApplied(1, 0), applier.reconcile(peer, response, listOf(item)))
                val saved = notes.getNoteById(note.uid) as JournalNote.Audio
                assertEquals(note.caption, saved.caption)
                assertEquals(note.transcript, saved.transcript)
                assertEquals(note.location, saved.location)
                assertEquals(note.timeZoneId, saved.timeZoneId)
                kotlin.test.assertTrue(applier.onItem(item))
                val removal =
                    WindowItem(
                        peer.phoneNode,
                        WearPaths.windowTombstone(RecordKind.NOTE, note.uid, peer),
                        WindowTombstoneDataMap.toDataMap(10, peer, 2),
                    )
                kotlin.test.assertTrue(applier.onTombstone(removal))
                assertNull(notes.getNoteById(note.uid))
            } finally {
                notes.deleteFromSync(note.uid)
            }
        }

    @Test fun peerChangesDuringRemovalRollBackTheActualRoomWrite() =
        runBlocking {
            links.savePairing("isolated-test-phone", ByteArray(32), LinkWire.encodeKey(ByteArray(32) { 2 }))
            val peer = assertNotNull(peers.current())
            val note =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(5),
                    content = "preserve on relink",
                )
            val envelope = NoteEnvelopeBody.envelope(note, RecordOrigin.PHONE, DeliveryPriority.BULK).copy(context = peer)
            val item =
                WindowItem(
                    peer.phoneNode,
                    WearPaths.windowItem(RecordKind.NOTE, note.uid, peer),
                    EnvelopeDataMap.toDataMap(envelope, 1) + (WindowTombstoneDataMap.KEY_REVISION to "1"),
                )
            val response =
                WindowResyncResponse(
                    context = peer,
                    members = listOf(WindowMemberRef(RecordKind.NOTE, note.uid.toString(), 5, 1)),
                    publishedAt = 9,
                    revision = 1,
                )
            val interrupted =
                object : SyncableJournalNotesRepository by notes {
                    override suspend fun deleteFromSync(noteId: Uuid) {
                        notes.deleteFromSync(noteId)
                        active = false
                    }
                }
            val guarded =
                WindowApplier(peers, interrupted, koin.get(), members, transaction, {}, koin.get(), koin.get<WindowPlaces>()::ensure)
            try {
                assertNotNull(applier.reconcile(peer, response, listOf(item)))
                val removal =
                    WindowItem(
                        peer.phoneNode,
                        WearPaths.windowTombstone(RecordKind.NOTE, note.uid, peer),
                        WindowTombstoneDataMap.toDataMap(10, peer, 2),
                    )
                assertFalse(guarded.onTombstone(removal))
                assertEquals(note, notes.getNoteById(note.uid))
            } finally {
                active = true
                notes.deleteFromSync(note.uid)
            }
        }

    @Test fun largeVerifiedBodySurvivesActualRoomAndMissingReplacementDoesNotPrune() =
        runBlocking {
            links.savePairing("isolated-test-phone", ByteArray(32), LinkWire.encodeKey(ByteArray(32) { 3 }))
            val peer = assertNotNull(peers.current())
            val note =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(5),
                    content = "A long journal entry 🌊".repeat(10000),
                )
            val bytes = NoteEnvelopeBody.encode(note).toString().encodeToByteArray()
            val ref =
                app.logdate.client.sync.datalayer.v2.BodyRef(
                    java.security.MessageDigest
                        .getInstance("SHA-256")
                        .digest(bytes)
                        .joinToString("") { "%02x".format(it) },
                    bytes.size.toLong(),
                )
            val envelope =
                NoteEnvelopeBody
                    .envelope(note, RecordOrigin.PHONE, DeliveryPriority.BULK)
                    .copy(context = peer, body = null, bodyRef = ref)
            val item =
                WindowItem(
                    peer.phoneNode,
                    WearPaths.windowItem(RecordKind.NOTE, note.uid, peer),
                    EnvelopeDataMap.toDataMap(envelope, 1) + (WindowTombstoneDataMap.KEY_REVISION to "1"),
                )
            val response =
                WindowResyncResponse(
                    context = peer,
                    members = listOf(WindowMemberRef(RecordKind.NOTE, note.uid.toString(), 5, 1)),
                    publishedAt = 9,
                    revision = 1,
                )
            val directory = java.io.File(koin.get<android.content.Context>().cacheDir, "window-body-test-" + Uuid.random())
            var bodyBytes = bytes
            var opens = 0
            var closes = 0
            val hydrator =
                app.logdate.wear.sync.v2.window.WindowBodyHydrator(
                    app.logdate.wear.sync.v2.window
                        .WindowItemSource { listOf(item) },
                    peers,
                    directory,
                ) {
                    app.logdate.wear.sync.v2.media.MediaRangeSource { request ->
                        opens++
                        val input = bodyBytes.inputStream().also { it.skip(request.offset) }
                        object : app.logdate.wear.sync.v2.media.MediaRangeConnection {
                            override val identity = request.identity
                            override val offset = request.offset
                            override val sourceNodeId = peer.phoneNode

                            override suspend fun read(buffer: ByteArray): Int = input.read(buffer)

                            override suspend fun close() {
                                closes++
                                input.close()
                            }
                        }
                    }
                }
            try {
                assertEquals(WindowApplied(1, 0), applier.reconcile(peer, response, hydrator.items(peer)))
                assertEquals(note, notes.getNoteById(note.uid))
                bodyBytes = bytes.copyOf(100)
                val replacement = response.copy(revision = 2, members = response.members.map { it.copy(revision = 2) })
                assertNull(applier.reconcile(peer, replacement, hydrator.items(peer)))
                assertEquals(note, notes.getNoteById(note.uid))
                assertEquals(opens, closes)
                kotlin.test.assertTrue(directory.listFiles().orEmpty().isEmpty())
            } finally {
                notes.deleteFromSync(note.uid)
                directory.deleteRecursively()
            }
        }
}
