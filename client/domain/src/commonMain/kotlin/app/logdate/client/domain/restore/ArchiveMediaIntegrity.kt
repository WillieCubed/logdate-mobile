package app.logdate.client.domain.restore

import app.logdate.client.util.platformIODispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.HashingSink
import okio.Sink
import okio.Source

/** Expected original bytes from an admitted archive inventory. The caller owns and removes staging. */
data class ArchiveMediaIntegrity(
    val bytes: Long,
    val sha256: String,
) {
    init {
        require(bytes >= 0 && Regex("^[0-9a-f]{64}$").matches(sha256)) { "Invalid archive media integrity" }
    }

    suspend fun copyTo(
        source: Source,
        destination: Sink,
    ) = withContext(platformIODispatcher) {
        val hashing = HashingSink.sha256(destination)
        val count = copyArchiveBytes(source, hashing, bytes)
        require(count == bytes && hashing.hash.hex() == sha256) { "Archive media failed its integrity check" }
    }
}

/** Copies legacy media compatibly; V2 requires a verified inventory record for the exact path. */
suspend fun copyArchiveMedia(
    source: Source,
    destination: Sink,
    path: String,
    integrity: Map<String, ArchiveMediaIntegrity>?,
) = withContext(platformIODispatcher) {
    if (integrity == null) {
        copyArchiveBytes(source, destination, null)
    } else {
        requireNotNull(integrity[path]) { "Archive media is not covered by its inventory" }.copyTo(source, destination)
    }
    Unit
}

private suspend fun copyArchiveBytes(
    source: Source,
    destination: Sink,
    expectedSize: Long?,
): Long {
    val buffer = Buffer()
    var bytes = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = source.read(buffer, 8_192)
        if (count == -1L) break
        require(count > 0 && count <= Long.MAX_VALUE - bytes) { "Invalid archive media stream" }
        bytes += count
        require(expectedSize == null || bytes <= expectedSize) { "Archive media exceeds its recorded size" }
        destination.write(buffer, count)
    }
    currentCoroutineContext().ensureActive()
    destination.flush()
    return bytes
}

/** A V2 ZIP is admitted before platform importers can receive its expected original bytes. */
val RestoreArchiveBundle.mediaIntegrity: Map<String, ArchiveMediaIntegrity>?
    get() =
        when (this) {
            is RestoreArchiveBundle.V1 -> null
            is RestoreArchiveBundle.V2 -> requireNotNull(bundle.mediaIntegrity) { "V2 archive was not admitted from original bytes" }
        }
