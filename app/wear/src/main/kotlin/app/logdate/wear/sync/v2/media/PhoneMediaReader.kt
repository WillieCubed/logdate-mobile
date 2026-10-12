package app.logdate.wear.sync.v2.media

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest

/** Reads a bounded, seekable range from one verified phone publication. */
class PhoneMediaReader(
    private val source: MediaRangeSource,
    private val current: suspend (WatchMediaIdentity) -> Boolean,
    private val inactivityMs: Long = 15_000,
) {
    private var identity: WatchMediaIdentity? = null
    private var connection: MediaRangeConnection? = null
    private var position = 0L
    private var remaining = 0L
    private var resumes = 0
    private var digest: MessageDigest? = null

    suspend fun open(
        identity: WatchMediaIdentity,
        position: Long,
        length: Long? = null,
    ): Long {
        close()
        require(inactivityMs > 0 && position >= 0 && (length == null || length > 0))
        if (position > identity.sizeBytes) throw PositionOutOfRange()
        if (!identity.peer.isValid() || identity.sizeBytes <= 0 || !identity.sha256.matches(Regex("[0-9a-f]{64}"))) {
            throw IOException("Invalid phone audio publication")
        }
        this.identity = identity
        this.position = position
        remaining = minOf(length ?: Long.MAX_VALUE, identity.sizeBytes - position)
        resumes = 0
        digest = if (position == 0L && remaining == identity.sizeBytes) MessageDigest.getInstance("SHA-256") else null
        try {
            checkCurrent(identity)
            if (remaining > 0) connect(identity)
            return remaining
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    suspend fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
        if (length == 0) return 0
        val identity = identity ?: throw IOException("The phone audio range is not open")
        try {
            checkCurrent(identity)
            if (remaining == 0L) return -1
            val chunk = ByteArray(minOf(length.toLong(), remaining, 8192L).toInt())
            while (true) {
                val active = connection ?: throw IOException("The phone audio range is closed")
                val count =
                    try {
                        withTimeoutOrNull(inactivityMs) {
                            var result = active.read(chunk)
                            while (result == 0) {
                                delay(1)
                                result = active.read(chunk)
                            }
                            result
                        } ?: throw SocketTimeoutException("The phone audio stream stopped responding")
                    } catch (e: IOException) {
                        if (e is SocketTimeoutException || resumes++ >= MAX_RESUMES) throw e
                        checkCurrent(identity)
                        disconnect()
                        connect(identity)
                        continue
                    }
                checkCurrent(identity)
                if (count == -1) {
                    if (resumes++ >= MAX_RESUMES) throw IOException("The phone audio range ended early")
                    disconnect()
                    connect(identity)
                    continue
                }
                if (count !in 1..chunk.size) throw IOException("The phone audio range exceeded its bounds")
                digest?.update(chunk, 0, count)
                if (count.toLong() == remaining && digest != null) {
                    val hash = digest!!.digest().joinToString("") { "%02x".format(it) }
                    if (hash != identity.sha256) throw IOException("The phone audio digest changed")
                }
                chunk.copyInto(buffer, offset, 0, count)
                position += count
                remaining -= count
                return count
            }
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    suspend fun close() {
        identity = null
        digest = null
        remaining = 0
        disconnect()
    }

    private suspend fun connect(identity: WatchMediaIdentity) {
        checkCurrent(identity)
        val opened =
            withTimeoutOrNull(inactivityMs) { source.open(MediaRangeRequest(identity, position)) }
                ?: throw SocketTimeoutException("The phone did not open the audio range")
        connection = opened
        if (opened.identity != identity || opened.offset != position || opened.sourceNodeId != identity.peer.phoneNode) {
            throw IOException("The phone audio range belongs to another publication")
        }
        checkCurrent(identity)
    }

    private suspend fun disconnect() {
        val active = connection ?: return
        connection = null
        withContext(NonCancellable) {
            withTimeoutOrNull(inactivityMs) { active.close() }
        }
    }

    private suspend fun checkCurrent(identity: WatchMediaIdentity) {
        currentCoroutineContext().ensureActive()
        if (!current(identity)) throw IOException("The phone audio publication changed")
    }

    class PositionOutOfRange : IOException("The audio position is past the end")

    private companion object {
        const val MAX_RESUMES = 2
    }
}
