package app.logdate.client.sync.datalayer

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Copies this stream into [output] and returns the length and SHA-256 of what was copied, so a file
 * is measured in the same pass that writes it.
 */
fun InputStream.copyToDigesting(output: OutputStream): AudioFileDigest = digest { buffer, count -> output.write(buffer, 0, count) }

/** The length and SHA-256 of this file's contents. */
fun File.audioDigest(): AudioFileDigest = inputStream().use { input -> input.digest { _, _ -> } }

private inline fun InputStream.digest(onChunk: (ByteArray, Int) -> Unit): AudioFileDigest {
    val sha256 = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var size = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        onChunk(buffer, count)
        sha256.update(buffer, 0, count)
        size += count
    }
    return AudioFileDigest(
        sizeBytes = size,
        sha256Hex = sha256.digest().joinToString(separator = "") { byte -> "%02x".format(byte) },
    )
}
