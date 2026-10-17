package app.logdate.wear.e2e

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.wear.sync.v2.media.ChannelDataSource
import app.logdate.wear.sync.v2.media.MediaRangeConnection
import app.logdate.wear.sync.v2.media.MediaRangeSource
import app.logdate.wear.sync.v2.media.WatchMediaIdentity
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ChannelDataSourceRuntimeTest {
    private val bytes = "seekable phone recording".toByteArray()
    private val identity =
        WatchMediaIdentity(
            PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR),
            Uuid.random(),
            10,
            bytes.size.toLong(),
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            "phone.m4a",
        )
    private var closed = 0
    private val ranges = mutableListOf<Long>()
    private val remote =
        MediaRangeSource { request ->
            ranges += request.offset
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

                override suspend fun close() {
                    closed++
                }
            }
        }

    private fun source() = ChannelDataSource({ identity }, { remote }, { true })

    private val uri = Uri.parse("logdate-watch-media://${Uuid.random()}")

    @Test fun media3SeeksAndReopensOnlyTheRequestedRange() {
        val source = source()
        assertEquals(
            4L,
            source.open(
                DataSpec
                    .Builder()
                    .setUri(uri)
                    .setPosition(5)
                    .setLength(4)
                    .build(),
            ),
        )
        assertEquals(uri, source.uri)
        val output = ByteArray(10)
        assertEquals(4, source.read(output, 3, 6))
        assertContentEquals(bytes.copyOfRange(5, 9), output.copyOfRange(3, 7))
        assertEquals(C.RESULT_END_OF_INPUT, source.read(output, 0, 10))
        source.close()
        assertNull(source.uri)
        assertEquals(bytes.size.toLong(), source.open(DataSpec.Builder().setUri(uri).build()))
        source.close()
        assertEquals(listOf(5L, 0L), ranges)
        assertEquals(2, closed)
    }

    @Test fun media3HonorsEndOfResourceAndPositionErrors() {
        val source = source()
        // A range past the end reports the bytes the recording has, so the end reads as the end of the audio.
        assertEquals(
            2L,
            source.open(
                DataSpec
                    .Builder()
                    .setUri(uri)
                    .setPosition(bytes.size - 2L)
                    .setLength(20)
                    .build(),
            ),
        )
        assertEquals(2, source.read(ByteArray(20), 0, 20))
        assertEquals(-1, source.read(ByteArray(1), 0, 1))
        source.close()
        assertEquals(
            0L,
            source.open(
                DataSpec
                    .Builder()
                    .setUri(uri)
                    .setPosition(bytes.size.toLong())
                    .build(),
            ),
        )
        assertEquals(-1, source.read(ByteArray(1), 0, 1))
        source.close()
        val failure =
            assertFailsWith<IOException> {
                source.open(
                    DataSpec
                        .Builder()
                        .setUri(uri)
                        .setPosition(bytes.size + 1L)
                        .build(),
                )
            }
        kotlin.test.assertTrue(DataSourceException.isCausedByPositionOutOfRange(failure))
        source.close()
    }
}
