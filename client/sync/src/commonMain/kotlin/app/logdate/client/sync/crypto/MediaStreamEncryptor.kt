package app.logdate.client.sync.crypto

import kotlinx.io.RawSource

/**
 * Encrypts a media payload as it is read, so an upload never holds the whole file in memory.
 *
 * [MediaPayloadCrypto.decrypt] reads back whatever this produces.
 */
interface MediaStreamEncryptor {
    /** Length of the stream [encrypt] produces for a payload of [plainSizeBytes] bytes. */
    fun encryptedSizeBytes(plainSizeBytes: Long): Long

    /** Wraps [source] so its bytes are encrypted as they are read. Closing the result closes [source]. */
    fun encrypt(source: RawSource): RawSource
}

internal object PassthroughMediaStreamEncryptor : MediaStreamEncryptor {
    override fun encryptedSizeBytes(plainSizeBytes: Long): Long = plainSizeBytes

    override fun encrypt(source: RawSource): RawSource = source
}
