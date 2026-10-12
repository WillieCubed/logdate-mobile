package app.logdate.wear.sync.v2.media

import java.io.File
import java.io.IOException

/** Only opened after the complete cache file and its persisted publication have been verified. */
internal class FileMediaRangeSource(
    private val file: File,
) : MediaRangeSource {
    override suspend fun open(request: MediaRangeRequest): MediaRangeConnection {
        if (request.offset !in 0..request.identity.sizeBytes || file.length() != request.identity.sizeBytes) {
            throw IOException("The verified audio cache changed")
        }
        val input = file.inputStream()
        try {
            input.channel.position(request.offset)
        } catch (e: Exception) {
            input.close()
            throw e
        }
        return object : MediaRangeConnection {
            override val identity = request.identity
            override val offset = request.offset
            override val sourceNodeId = identity.peer.phoneNode

            override suspend fun read(buffer: ByteArray): Int = input.read(buffer)

            override suspend fun close() = input.close()
        }
    }
}
