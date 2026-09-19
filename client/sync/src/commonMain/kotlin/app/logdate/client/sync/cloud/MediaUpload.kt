package app.logdate.client.sync.cloud

import kotlinx.io.Buffer
import kotlinx.io.RawSource

/**
 * A media upload whose body is read only while the request is being written.
 *
 * [openBody] is called each time the body is sent and must return a fresh source of exactly
 * [bodySizeBytes] bytes. The server compares the declared [sizeBytes] with the bytes it receives,
 * so it describes what goes over the wire -- the ciphertext when the payload is encrypted -- and
 * the two only differ when a caller deliberately declares a wrong size, which the server rejects.
 */
class MediaUpload(
    val contentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val deviceId: DeviceId = DeviceId.UNKNOWN,
    val bodySizeBytes: Long = sizeBytes,
    private val body: () -> RawSource,
) {
    fun openBody(): RawSource = body()
}

/** Uploads an in-memory payload. Suited to small payloads and tests; sync streams from disk. */
suspend fun CloudApiClient.uploadMedia(
    accessToken: String,
    media: MediaUploadRequest,
): Result<MediaUploadResponse> =
    uploadMedia(
        accessToken,
        MediaUpload(
            contentId = media.contentId,
            fileName = media.fileName,
            mimeType = media.mimeType,
            sizeBytes = media.sizeBytes,
            deviceId = media.deviceId,
            bodySizeBytes = media.data.size.toLong(),
        ) { Buffer().apply { write(media.data) } },
    )
