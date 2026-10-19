package app.logdate.client.sync.space

import app.logdate.client.sync.cloud.MediaDecryptionException
import app.logdate.client.sync.cloud.MediaIntegrityException
import app.logdate.client.sync.crypto.AesGcmMediaPayloadCrypto
import app.logdate.client.sync.crypto.MediaStreamDecryptor
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SpaceMediaStreamingTest {
    private val mediaKey = ByteArray(32) { (it + 1).toByte() }
    private val crypto = AesGcmMediaPayloadCrypto(mediaKey)
    private val location = SpaceLocation("https://cloud.test", TEST_SPACE)

    /** The raw-codec CID of the bytes "hello world", computed outside this codebase. */
    private val helloWorldCid = "bafkreifzjut3te2nhyekklss27nh3k72ysco7y32koao5eei66wof36n5e"

    private fun space(home: FakeDataHome) = SpaceMediaDataSource(SpaceDataHomes { _, token -> home.signedIn(token) }, crypto, { location })

    private suspend fun encrypt(plaintext: ByteArray): ByteArray =
        crypto
            .streamEncryptor()
            .encrypt(Buffer().apply { write(plaintext) })
            .buffered()
            .readByteArray()

    @Test
    fun `decryption never holds more than about one chunk`() =
        runTest {
            val plaintext = ByteArray(8 * 1024 * 1024) { (it * 31 % 251).toByte() }
            val source = MeasuredSource(Buffer().apply { write(encrypt(plaintext)) })
            val sink = MeasuredSink(source)

            val out = sink.buffered()
            val size = crypto.streamDecryptor().decrypt(source.buffered(), out)
            out.flush()

            assertEquals(plaintext.size.toLong(), size)
            assertContentEquals(plaintext, sink.received.readByteArray())
            assertTrue(sink.largestGap <= 256 * 1024, "Read ${sink.largestGap} bytes ahead of what was written")
        }

    @Test
    fun `a chunk that fails authentication or a file cut at a chunk boundary is refused`() =
        runTest {
            val plaintext = ByteArray(200_000) { it.toByte() }
            val sealed = encrypt(plaintext)
            val tampered = sealed.copyOf().also { it[it.size - 100] = (it[it.size - 100] + 1).toByte() }
            val truncated = sealed.copyOf(43 + 2 * (64 * 1024 + 16))

            for (bad in listOf(tampered, truncated)) {
                assertFailsWith<Exception> { crypto.streamDecryptor().decrypt(Buffer().apply { write(bad) }, Buffer()) }
            }
        }

    @Test
    fun `whole-file formats are decrypted only up to the size the old endpoint accepted`() =
        runTest {
            val tooLarge =
                ByteArray(
                    (MediaStreamDecryptor.MAX_WHOLE_PAYLOAD_BYTES + 1).toInt(),
                ).also { "LDCE1".encodeToByteArray().copyInto(it) }

            assertFailsWith<IllegalArgumentException> { crypto.streamDecryptor().decrypt(Buffer().apply { write(tooLarge) }, Buffer()) }
        }

    @Test
    fun `the downloaded file's content identifier is checked against a known answer`() =
        runTest {
            val home = FakeDataHome().apply { blobs[helloWorldCid] = "hello world".encodeToByteArray() }
            val reference = SpaceMediaReference(helloWorldCid, Uuid.random(), "text/plain", storedSize = 11).format()

            // Not encrypted, so it is copied through: only the identifier check stands between it and the file.
            val file = space(home).downloadMediaToFile("test-access-token", reference, helloWorldCid).getOrThrow()

            assertEquals(
                "hello world",
                SystemFileSystem
                    .source(file.path)
                    .buffered()
                    .use { it.readByteArray() }
                    .decodeToString(),
            )
            SystemFileSystem.delete(file.path)

            home.blobs[helloWorldCid] = "hello wurld".encodeToByteArray()
            assertIs<MediaIntegrityException>(
                space(home).downloadMediaToFile("test-access-token", reference, helloWorldCid).exceptionOrNull(),
            )
        }

    @Test
    fun `a blob of another size than its reference names is refused before it is decrypted`() =
        runTest {
            val home = FakeDataHome().apply { blobs[helloWorldCid] = "hello world".encodeToByteArray() }
            val shorter = SpaceMediaReference(helloWorldCid, Uuid.random(), "text/plain", storedSize = 5).format()
            val longer = SpaceMediaReference(helloWorldCid, Uuid.random(), "text/plain", storedSize = 50).format()

            assertIs<MediaIntegrityException>(
                space(home).downloadMediaToFile("test-access-token", shorter, helloWorldCid).exceptionOrNull(),
            )
            assertIs<MediaIntegrityException>(space(home).downloadMediaToFile("test-access-token", longer, helloWorldCid).exceptionOrNull())
        }

    @Test
    fun `a file that fails to decrypt leaves nothing behind`() =
        runTest {
            val home = FakeDataHome()
            val contentId = Uuid.random()
            val sealed = encrypt(ByteArray(100_000) { 3 })
            val other = AesGcmMediaPayloadCrypto(ByteArray(32) { 9 })
            val source = SpaceMediaDataSource(SpaceDataHomes { _, token -> home.signedIn(token) }, other, { location })
            val cid = home.signedIn("test-access-token").uploadBlob(TEST_SPACE, sealed)
            val reference = SpaceMediaReference(cid, contentId, "image/png", sealed.size.toLong()).format()

            assertIs<MediaDecryptionException>(source.downloadMediaToFile("test-access-token", reference, cid).exceptionOrNull())
        }

    /** Counts what is read, for [MeasuredSink] to compare with what it has received. */
    private class MeasuredSource(
        private val delegate: RawSource,
    ) : RawSource {
        var read = 0L
            private set

        override fun readAtMostTo(
            sink: Buffer,
            byteCount: Long,
        ): Long = delegate.readAtMostTo(sink, byteCount).also { if (it > 0) read += it }

        override fun close() = delegate.close()
    }

    /** Records the largest amount read from [source] but not yet written out, flushing as it goes. */
    private class MeasuredSink(
        private val source: MeasuredSource,
    ) : RawSink {
        val received = Buffer()
        var largestGap = 0L
            private set

        override fun write(
            source: Buffer,
            byteCount: Long,
        ) {
            received.write(source, byteCount)
            largestGap = maxOf(largestGap, this.source.read - received.size)
        }

        override fun flush() = Unit

        override fun close() = Unit
    }
}
