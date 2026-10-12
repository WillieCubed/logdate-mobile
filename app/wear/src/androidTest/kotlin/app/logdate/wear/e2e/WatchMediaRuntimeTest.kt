package app.logdate.wear.e2e

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.wear.playback.SyncedAudioWatchMediaCache
import app.logdate.wear.sync.SyncedAudioCache
import app.logdate.wear.sync.v2.media.MediaRangeConnection
import app.logdate.wear.sync.v2.media.MediaRangeRequest
import app.logdate.wear.sync.v2.media.MediaRangeSource
import app.logdate.wear.sync.v2.media.MediaStreamClient
import app.logdate.wear.sync.v2.media.WatchMediaIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Android file publication and cache invalidation; no external device transport is used. */
@RunWith(AndroidJUnit4::class)
class WatchMediaRuntimeTest {
    private val context = KoinJavaComponent.getKoin().get<Context>()

    @Test fun evictionDuringDownloadCannotRecreateAFile() =
        runBlocking {
            val directory = File(context.cacheDir, "media-runtime-${Uuid.random()}").apply { mkdirs() }
            try {
                val cache = SyncedAudioWatchMediaCache(SyncedAudioCache(directory), { Long.MAX_VALUE })
                val noteId = Uuid.random()
                val gate = CompletableDeferred<Unit>()
                val pull =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        cache.store(noteId, "m4a") { path, beforeWrite ->
                            beforeWrite(5)
                            File(path).writeText("audio")
                            gate.await()
                            true
                        }
                    }
                cache.evict(noteId)
                gate.complete(Unit)
                assertNull(pull.await())
                assertNull(cache.cachedFile(noteId))
                assertTrue(directory.listFiles().orEmpty().isEmpty())
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test fun resumedVerifiedStreamPublishesOnlyCompleteCacheAudio() =
        runBlocking {
            val directory = File(context.cacheDir, "media-runtime-${Uuid.random()}").apply { mkdirs() }
            try {
                val cache = SyncedAudioWatchMediaCache(SyncedAudioCache(directory), { Long.MAX_VALUE })
                val bytes = "phone audio".toByteArray()
                val identity =
                    WatchMediaIdentity(
                        PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR),
                        Uuid.random(),
                        1L,
                        bytes.size.toLong(),
                        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
                    )
                val requests = mutableListOf<Long>()
                val closed = mutableListOf<Long>()
                val source =
                    MediaRangeSource { request ->
                        requests += request.offset
                        Connection(request, bytes) { closed += request.offset }
                    }
                val client = MediaStreamClient(source, { true })
                val stored =
                    cache.store(identity.noteId, "m4a") { path, beforeWrite ->
                        assertNull(cache.cachedFile(identity.noteId))
                        client.copyTo(identity, File(path), beforeWrite)
                    }
                assertEquals("phone audio", stored?.readText())
                assertEquals(listOf(0L, 5L), requests)
                assertEquals(requests, closed)
                assertEquals(listOf("${identity.noteId}.m4a"), directory.list().orEmpty().toList())
            } finally {
                directory.deleteRecursively()
            }
        }

    private class Connection(
        request: MediaRangeRequest,
        private val bytes: ByteArray,
        private val onClose: () -> Unit,
    ) : MediaRangeConnection {
        override val identity = request.identity
        override val sourceNodeId = request.identity.peer.phoneNode
        override val offset = request.offset
        private var position = offset.toInt()

        override suspend fun read(buffer: ByteArray): Int {
            if (offset == 0L && position == 5) throw IOException("Interrupted link")
            if (position == bytes.size) return -1
            val count = minOf(5, bytes.size - position)
            bytes.copyInto(buffer, 0, position, position + count)
            position += count
            return count
        }

        override suspend fun close() = onClose()
    }
}
