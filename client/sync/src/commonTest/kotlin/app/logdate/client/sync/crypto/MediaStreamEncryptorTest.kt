package app.logdate.client.sync.crypto

import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.MediaUpload
import app.logdate.client.sync.cloud.MediaUploadRequest
import app.logdate.client.sync.cloud.MediaUploadResponse
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.mediaFileSource
import app.logdate.client.sync.test.readRequest
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Uploads are encrypted in chunks (LDCE2) so no platform holds a whole file while encrypting it.
 * These tests pin the format's guarantees and keep older LDCE1 media readable.
 */
class MediaStreamEncryptorTest {
    private val key = ByteArray(32) { (it * 7).toByte() }
    private val chunk = 64 * 1024

    @Test
    fun `streamed ciphertext decrypts back to the original at every size`() =
        runTest {
            val crypto = AesGcmMediaPayloadCrypto(key)
            val encryptor = crypto.streamEncryptor()

            for (size in listOf(0, 1, chunk - 1, chunk, chunk + 1, 3 * chunk, 3 * 1024 * 1024 + 7)) {
                val plaintext = Random(size).nextBytes(size)

                val ciphertext = encryptor.encryptAll(plaintext)

                assertEquals(encryptor.encryptedSizeBytes(size.toLong()), ciphertext.size.toLong(), "size $size")
                assertTrue(ciphertext.hasChunkedMediaPrefix(), "size $size")
                assertContentEquals(plaintext, crypto.decrypt(ciphertext), "size $size")
            }
        }

    @Test
    fun `encryption emits output after reading one chunk rather than the whole file`() =
        runTest {
            val encryptor = AesGcmMediaPayloadCrypto(key).streamEncryptor()
            val input = CountingSource(Random(1).nextBytes(4 * 1024 * 1024))

            val firstBytes = Buffer()
            encryptor.encrypt(input).buffered().readAtMostTo(firstBytes, 1024)

            assertTrue(firstBytes.size > 0)
            assertTrue(input.bytesRead <= 2L * chunk, "Read ${input.bytesRead} bytes to produce the first output")
        }

    @Test
    fun `a modified chunk is rejected`() =
        runTest {
            val crypto = AesGcmMediaPayloadCrypto(key)
            val ciphertext = crypto.streamEncryptor().encryptAll(Random(2).nextBytes(3 * chunk))

            ciphertext[ciphertext.size / 2] = (ciphertext[ciphertext.size / 2] + 1).toByte()

            assertFails { crypto.decrypt(ciphertext) }
        }

    @Test
    fun `reordered chunks are rejected`() =
        runTest {
            val crypto = AesGcmMediaPayloadCrypto(key)
            val ciphertext = crypto.streamEncryptor().encryptAll(Random(3).nextBytes(3 * chunk))
            val sealed = chunk + TAG
            val first = ciphertext.copyOfRange(HEADER, HEADER + sealed)
            val second = ciphertext.copyOfRange(HEADER + sealed, HEADER + 2 * sealed)

            first.copyInto(ciphertext, HEADER + sealed)
            second.copyInto(ciphertext, HEADER)

            assertFails { crypto.decrypt(ciphertext) }
        }

    @Test
    fun `a file cut off at a chunk boundary is rejected`() =
        runTest {
            val crypto = AesGcmMediaPayloadCrypto(key)
            val ciphertext = crypto.streamEncryptor().encryptAll(Random(4).nextBytes(3 * chunk))

            val truncated = ciphertext.copyOfRange(0, HEADER + 2 * (chunk + TAG))

            assertFails { crypto.decrypt(truncated) }
        }

    @Test
    fun `media uploaded in the older single-shot format still decrypts`() =
        runTest {
            val crypto = AesGcmMediaPayloadCrypto(key)
            val plaintext = Random(5).nextBytes(10_000)

            val legacy = legacyLdce1Encrypt(key, plaintext)

            assertTrue(legacy.hasClientMediaPrefix())
            assertContentEquals(plaintext, crypto.decrypt(legacy))
        }

    @Test
    fun `media that is already encrypted is uploaded unchanged`() =
        runTest {
            val crypto = AesGcmMediaPayloadCrypto(key)
            val apiClient = RecordingClient()

            for (alreadyEncrypted in listOf(
                legacyLdce1Encrypt(key, Random(6).nextBytes(4096)),
                crypto.streamEncryptor().encryptAll(Random(7).nextBytes(4096)),
            )) {
                DefaultCloudMediaDataSource(apiClient, crypto)
                    .uploadMedia("token", Uuid.random(), mediaFileSource(alreadyEncrypted))
                    .getOrThrow()

                val sent = apiClient.last ?: error("No upload captured")
                assertEquals(alreadyEncrypted.size.toLong(), sent.sizeBytes)
                assertContentEquals(alreadyEncrypted, sent.data)
            }
        }

    private fun MediaStreamEncryptor.encryptAll(plaintext: ByteArray): ByteArray =
        encrypt(Buffer().apply { write(plaintext) }).buffered().use { it.readByteArray() }

    private class CountingSource(
        data: ByteArray,
    ) : RawSource {
        private val buffer = Buffer().apply { write(data) }
        var bytesRead = 0L
            private set

        override fun readAtMostTo(
            sink: Buffer,
            byteCount: Long,
        ): Long {
            val read = buffer.readAtMostTo(sink, byteCount)
            if (read > 0) bytesRead += read
            return read
        }

        override fun close() = Unit
    }

    private class RecordingClient : FakeCloudApiClient() {
        var last: MediaUploadRequest? = null

        override suspend fun uploadMedia(
            accessToken: String,
            media: MediaUpload,
        ): Result<MediaUploadResponse> {
            last = media.readRequest()
            return Result.success(MediaUploadResponse(media.contentId, "media-1", "https://example.com/media-1", 0))
        }
    }

    private companion object {
        /** "LDCE2" + chunk size + salt + nonce prefix. */
        const val HEADER = 5 + 4 + 16 + 7
        const val TAG = 16
    }
}
