package app.logdate.wear.e2e

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.wear.sync.v2.media.ChannelDataSource
import app.logdate.wear.sync.v2.media.MediaRangeConnection
import app.logdate.wear.sync.v2.media.MediaRangeSource
import app.logdate.wear.sync.v2.media.WatchMediaIdentity
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Real Media3 loader, WAV extraction and audio renderer; the phone endpoint is isolated. */
@RunWith(AndroidJUnit4::class)
class ChannelPlaybackRuntimeTest {
    @Test fun media3PreparesAndSeeksAStreamWithoutDownloadingACacheFile() {
        val bytes = wav()
        val identity =
            WatchMediaIdentity(
                PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR),
                Uuid.random(),
                1,
                bytes.size.toLong(),
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
                "phone.wav",
            )
        val ready = CountDownLatch(1)
        val seek = CountDownLatch(1)
        val error = AtomicReference<PlaybackException?>()
        val player = AtomicReference<ExoPlayer>()
        val source =
            MediaRangeSource { request ->
                object : MediaRangeConnection {
                    override val identity = request.identity
                    override val offset = request.offset
                    override val sourceNodeId = identity.peer.phoneNode
                    private var position = offset.toInt()

                    override suspend fun read(buffer: ByteArray): Int {
                        if (position == bytes.size) return -1
                        val count = minOf(buffer.size, bytes.size - position)
                        bytes.copyInto(buffer, 0, position, position + count)
                        position += count
                        return count
                    }

                    override suspend fun close() = Unit
                }
            }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val engine =
                    ExoPlayer
                        .Builder(context)
                        .setMediaSourceFactory(
                            DefaultMediaSourceFactory(
                                DataSource.Factory { ChannelDataSource({ identity }, { source }, { true }) },
                            ),
                        ).build()
                player.set(engine)
                engine.volume = 0f
                engine.addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) ready.countDown()
                        }

                        override fun onPlayerError(failure: PlaybackException) {
                            error.set(failure)
                            ready.countDown()
                        }

                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int,
                        ) {
                            if (reason == Player.DISCONTINUITY_REASON_SEEK) seek.countDown()
                        }
                    },
                )
                engine.setMediaItem(MediaItem.fromUri("logdate-watch-media://${Uuid.random()}"))
                engine.prepare()
            }
            assertTrue(ready.await(15, TimeUnit.SECONDS), "Media3 did not prepare the streamed recording")
            assertNull(error.get())
            instrumentation.runOnMainSync {
                assertEquals(2000L, player.get().duration)
                player.get().seekTo(1000)
            }
            assertTrue(seek.await(5, TimeUnit.SECONDS))
            assertNull(error.get())
        } finally {
            instrumentation.runOnMainSync { player.get()?.release() }
        }
    }

    private fun wav(): ByteArray {
        val size = 8000 * 2 * 2
        return ByteBuffer
            .allocate(44 + size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray())
                putInt(36 + size)
                put("WAVEfmt ".toByteArray())
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(8000)
                putInt(16000)
                putShort(2)
                putShort(16)
                put("data".toByteArray())
                putInt(size)
            }.array()
    }
}
