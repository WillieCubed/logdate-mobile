package app.logdate.wear.sync.v2.media

import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import io.github.aakira.napier.Napier
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import kotlin.uuid.Uuid

/** Immutable identity supplied by the verified phone publication. */
data class WatchMediaIdentity(
    val peer: PeerDeliveryContext,
    val noteId: Uuid,
    val contentVersion: Long,
    val sizeBytes: Long,
    val sha256: String,
    val mediaRef: String? = null,
)

data class MediaRangeRequest(
    val identity: WatchMediaIdentity,
    val offset: Long,
)

interface MediaRangeConnection {
    val identity: WatchMediaIdentity
    val offset: Long
    val sourceNodeId: String

    suspend fun read(buffer: ByteArray): Int

    suspend fun close()
}

fun interface MediaRangeSource {
    suspend fun open(request: MediaRangeRequest): MediaRangeConnection
}

/** Copies a pinned phone file into a cache-owned partial file. */
class MediaStreamClient(
    private val source: MediaRangeSource,
    private val isCurrent: suspend (WatchMediaIdentity) -> Boolean,
    private val inactivityMs: Long = 15_000L,
    private val maxResumes: Int = 2,
) {
    suspend fun copyTo(
        identity: WatchMediaIdentity,
        partialFile: File,
        beforeWrite: suspend (Int) -> Unit,
    ): Boolean {
        require(identity.peer.isValid() && identity.contentVersion >= 0 && identity.sizeBytes > 0)
        require(identity.sha256.matches(Regex("[0-9a-f]{64}")))
        require(inactivityMs > 0 && maxResumes >= 0)
        checkCurrent(identity)
        val digest = MessageDigest.getInstance("SHA-256")
        var received = 0L
        var resumes = 0
        partialFile.outputStream().use { output ->
            while (true) {
                checkCurrent(identity)
                val connection =
                    withTimeoutOrNull(inactivityMs) {
                        source.open(MediaRangeRequest(identity, received))
                    } ?: throw SocketTimeoutException("The phone did not open the media stream")
                try {
                    checkCurrent(identity)
                    if (connection.identity != identity ||
                        connection.sourceNodeId != identity.peer.phoneNode ||
                        connection.offset != received
                    ) {
                        throw InvalidMedia("The phone media range does not match the requested publication")
                    }
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count =
                            try {
                                readWithProgress(connection, buffer).also {
                                    if (it == -1 && received != identity.sizeBytes) throw IOException("The media stream ended early")
                                }
                            } catch (e: IOException) {
                                if (e is SocketTimeoutException || resumes++ >= maxResumes) throw e
                                checkCurrent(identity)
                                break
                            }
                        checkCurrent(identity)
                        if (count == -1) {
                            val actual = digest.digest().joinToString("") { "%02x".format(it) }
                            if (actual != identity.sha256) throw InvalidMedia("The media digest does not match its publication")
                            return true
                        }
                        if (count !in 1..buffer.size || count.toLong() > identity.sizeBytes - received) {
                            throw InvalidMedia("The media stream exceeded its published size")
                        }
                        beforeWrite(count)
                        checkCurrent(identity)
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        received += count
                    }
                } finally {
                    close(connection)
                }
            }
        }
    }

    private suspend fun readWithProgress(
        connection: MediaRangeConnection,
        buffer: ByteArray,
    ): Int =
        withTimeoutOrNull(inactivityMs) {
            var count = connection.read(buffer)
            while (count == 0) {
                delay(1)
                count = connection.read(buffer)
            }
            count
        } ?: throw SocketTimeoutException("No phone media bytes arrived for $inactivityMs ms")

    private suspend fun checkCurrent(identity: WatchMediaIdentity) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent(identity)) throw InvalidMedia("The phone media publication is no longer current")
    }

    private suspend fun close(connection: MediaRangeConnection) {
        withContext(NonCancellable) {
            try {
                val closed =
                    withTimeoutOrNull(inactivityMs) {
                        connection.close()
                        true
                    } ?: false
                if (!closed) Napier.w("Timed out closing a phone media stream")
            } catch (e: Exception) {
                Napier.w("Could not close a phone media stream", e)
            }
        }
    }

    private class InvalidMedia(
        message: String,
    ) : IOException(message)
}
