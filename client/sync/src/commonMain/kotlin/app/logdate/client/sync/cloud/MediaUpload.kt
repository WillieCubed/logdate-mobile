package app.logdate.client.sync.cloud

import kotlinx.io.RawSource

/**
 * A media upload whose body is read only while the request is being written.
 *
 * [openBody] is called each time the body is sent and must return a fresh source of exactly
 * [sizeBytes] bytes. The server compares [sizeBytes] with the bytes it receives, so it describes
 * what goes over the wire -- the ciphertext when the payload is encrypted.
 */
class MediaUpload(
    val contentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val deviceId: DeviceId = DeviceId.UNKNOWN,
    private val body: () -> RawSource,
) {
    fun openBody(): RawSource = body()
}

/**
 * Reading an upload's media failed: the file could not be opened or read, or it no longer has the
 * size it had when the upload was prepared. The media, not the network, is at fault.
 */
class MediaReadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Finds a [MediaReadException] that caused [this], however deeply the HTTP client wrapped it. */
internal fun Throwable.mediaReadFailure(): MediaReadException? =
    generateSequence(this) { it.cause }.firstNotNullOfOrNull { it as? MediaReadException }
