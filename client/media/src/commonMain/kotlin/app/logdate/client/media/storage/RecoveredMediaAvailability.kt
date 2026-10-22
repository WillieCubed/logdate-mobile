package app.logdate.client.media.storage

import app.logdate.client.media.MediaManager
import app.logdate.client.util.platformIODispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import okio.HashingSink
import okio.blackholeSink
import okio.Buffer as HashBuffer

/** Verifies complete private streams and canonical content digests before accepting restored originals. */
suspend fun MediaManager.isRecoveredOriginal(
    reference: String,
    references: StoredMediaReferences,
): Boolean =
    withContext(platformIODispatcher) {
        try {
            val owned =
                (MediaReference.parse(references.storedReference(reference)) as? MediaReference.Owned)?.ref ?: return@withContext false
            val expectedDigest =
                if (owned.collection == MediaCollection.Library && owned.path.startsWith("objects/sha256/", ignoreCase = true)) {
                    val matched = CANONICAL_OBJECT.matchEntire(owned.path) ?: return@withContext false
                    val digest = matched.groupValues[2].lowercase()
                    if (!digest.startsWith(matched.groupValues[1], ignoreCase = true)) return@withContext false
                    digest
                } else {
                    null
                }
            if (!exists(reference)) return@withContext false
            val original = openMedia(reference)
            if (original.sizeBytes <= 0) return@withContext false
            val hashing = expectedDigest?.let { HashingSink.sha256(blackholeSink()) }
            try {
                original.open().use { input ->
                    val buffer = Buffer()
                    val hashBuffer = HashBuffer()
                    var bytesRead = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.readAtMostTo(buffer, INTEGRITY_CHUNK_BYTES)
                        if (read == -1L) break
                        if (read <= 0 || read > INTEGRITY_CHUNK_BYTES || buffer.size != read || read > original.sizeBytes - bytesRead) {
                            return@withContext false
                        }
                        bytesRead += read
                        val chunk = buffer.readByteArray()
                        hashing?.write(hashBuffer.write(chunk), read)
                    }
                    bytesRead == original.sizeBytes && (hashing == null || hashing.hash.hex() == expectedDigest)
                }
            } finally {
                hashing?.close()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

private const val INTEGRITY_CHUNK_BYTES = 8192L
private val CANONICAL_OBJECT = Regex("objects/sha256/([a-f0-9]{2})/([a-f0-9]{64})\\.[^/.]+", RegexOption.IGNORE_CASE)
