package app.logdate.server.routes

import app.logdate.server.logdate.LogDateBlobStream
import app.logdate.server.logdate.LogDateBlobWriter
import app.logdate.server.transfer.TransferSlot
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import studio.hypertext.atproto.repo.Cid
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

private const val CHUNK_BYTES = 64 * 1024

/** CIDv1, the `raw` codec, then a 32-byte SHA-256 multihash: the prefix of every blob CID. */
private val RAW_SHA256_CID_PREFIX = byteArrayOf(0x01, 0x55, 0x12, 0x20)

/** A request body as received: its raw CID and its length. */
internal class ReceivedSpaceBlob(
    val cid: String,
    val size: Long,
)

/** The client sent nothing for longer than the idle timeout, so the upload gave up its slot. */
internal class SpaceTransferStalledException(
    idleTimeout: Duration,
) : IOException("No bytes moved for $idleTimeout")

/**
 * Streams the request body into [writer] as it arrives, hashing it on the way, so an upload of any
 * allowed size holds one chunk of memory and nothing on local disk. Returns null as soon as the body
 * passes [limit] bytes. Throws [SpaceTransferStalledException] when no bytes arrive for [idleTimeout].
 * The caller commits or aborts [writer].
 */
internal suspend fun ApplicationCall.streamSpaceBlob(
    writer: LogDateBlobWriter,
    limit: Long,
    idleTimeout: Duration,
): ReceivedSpaceBlob? {
    val body = receiveChannel()
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(CHUNK_BYTES)
    var size = 0L
    while (true) {
        val count =
            withTimeoutOrNull(idleTimeout) { body.readAvailable(buffer, 0, buffer.size) }
                ?: throw SpaceTransferStalledException(idleTimeout)
        if (count < 0) break
        size += count
        if (size > limit) return null
        digest.update(buffer, 0, count)
        withContext(Dispatchers.IO) { writer.output.write(buffer, 0, count) }
    }
    return ReceivedSpaceBlob(Cid.fromBytes(RAW_SHA256_CID_PREFIX + digest.digest()).value, size)
}

/**
 * Sends the encrypted blob in [stream] as the response body with its length, one chunk at a time,
 * straight from object storage. A client that accepts nothing for [idleTimeout] ends the response.
 * Once the body is written, or fails, it closes [stream] and frees [slot]. The engine can write the
 * body after the handler returns, so a handler whose response fails before that calls [release].
 */
internal class SpaceBlobContent(
    private val stream: LogDateBlobStream,
    private val slot: TransferSlot,
    private val idleTimeout: Duration,
) : OutgoingContent.WriteChannelContent() {
    private val released = AtomicBoolean(false)

    override val contentType: ContentType = ContentType.Application.OctetStream
    override val contentLength: Long = stream.size

    override suspend fun writeTo(channel: ByteWriteChannel) {
        try {
            val buffer = ByteArray(CHUNK_BYTES)
            while (true) {
                val count = withContext(Dispatchers.IO) { stream.input.read(buffer) }
                if (count < 0) break
                withTimeoutOrNull(idleTimeout) { channel.writeFully(buffer, 0, count) }
                    ?: throw SpaceTransferStalledException(idleTimeout)
            }
        } finally {
            withContext(NonCancellable) { release() }
        }
    }

    /** Closes the stream and frees the slot; only the first call does anything. */
    suspend fun release() {
        if (!released.compareAndSet(false, true)) return
        try {
            withContext(Dispatchers.IO) { stream.close() }
        } finally {
            slot.release()
        }
    }
}
