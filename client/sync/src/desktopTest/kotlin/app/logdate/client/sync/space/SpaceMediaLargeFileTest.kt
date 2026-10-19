package app.logdate.client.sync.space

import app.logdate.client.sync.crypto.AesGcmMediaPayloadCrypto
import app.logdate.client.sync.crypto.MediaPayloadCrypto
import app.logdate.client.sync.crypto.MediaStreamDecryptor
import app.logdate.client.sync.crypto.MediaStreamEncryptor
import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.space.ChangePage
import app.logdate.space.DataHome
import app.logdate.space.RecordSwap
import app.logdate.space.SpaceRef
import app.logdate.space.StoredRecord
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import studio.hypertext.atproto.repo.Cid
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * A file close to the largest a personal space accepts goes through download, its content
 * identifier check and decryption while only about one chunk is ever held in memory.
 */
class SpaceMediaLargeFileTest {
    private val directory = Path(SystemTemporaryDirectory, "space-media-large-${Uuid.random()}")
    private val crypto = AesGcmMediaPayloadCrypto(ByteArray(32) { (it * 7).toByte() })

    @AfterTest
    fun removeFiles() {
        SystemFileSystem.list(directory).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(directory, mustExist = false)
    }

    @Test
    fun `a 96 MB recording downloads and decrypts with about one chunk in memory`() =
        runTest(timeout = 5.minutes) {
            SystemFileSystem.createDirectories(directory)
            val size = 96L * 1024 * 1024
            val stored = Path(directory, "stored")
            val plainDigest =
                CryptographyProvider.Default
                    .get(SHA256)
                    .hasher()
                    .createHashFunction()
            val storedDigest =
                CryptographyProvider.Default
                    .get(SHA256)
                    .hasher()
                    .createHashFunction()
            val encrypted = crypto.streamEncryptor().encrypt(plainDigest.updatingSource(GeneratedSource(size)))
            SystemFileSystem.sink(stored).use { file ->
                storedDigest.updatingSink(file).buffered().use { sink -> sink.transferFrom(encrypted) }
            }
            val cid = Cid.fromBytes(byteArrayOf(0x01, 0x55, 0x12, 0x20) + storedDigest.hashToByteArray()).value
            val storedSize = SystemFileSystem.metadataOrNull(stored)!!.size
            val measured = MeasuringCrypto(crypto)
            val source =
                SpaceMediaDataSource(
                    homes = { _, _ -> FileDataHome(stored) },
                    mediaPayloadCrypto = measured,
                    location = { SpaceLocation("https://cloud.test", TEST_SPACE) },
                    workDirectory = directory,
                )
            val contentId = Uuid.random()
            val reference = SpaceMediaReference(cid, contentId, "video/mp4", storedSize, size).format()

            val file = source.downloadMediaToFile("token", reference, cid).getOrThrow()

            assertEquals(size, file.sizeBytes)
            val outputDigest =
                CryptographyProvider.Default
                    .get(SHA256)
                    .hasher()
                    .createHashFunction()
            SystemFileSystem.source(file.path).use { outputDigest.update(it) }
            assertContentEquals(plainDigest.hashToByteArray(), outputDigest.hashToByteArray())
            assertTrue(measured.largestGap <= 256 * 1024, "Decryption read ${measured.largestGap} bytes ahead of its output")
        }

    /** [size] bytes of a repeating pattern, produced as they are read. */
    private class GeneratedSource(
        private val size: Long,
    ) : RawSource {
        private var produced = 0L

        override fun readAtMostTo(
            sink: Buffer,
            byteCount: Long,
        ): Long {
            if (produced == size) return -1
            val count = minOf(byteCount, size - produced, 64L * 1024).toInt()
            sink.write(ByteArray(count) { ((produced + it) * 31 % 251).toByte() })
            produced += count
            return count.toLong()
        }

        override fun close() = Unit
    }

    /** Serves one stored blob from a file, as a server streams it. */
    private class FileDataHome(
        private val stored: Path,
    ) : DataHome {
        override suspend fun downloadBlob(
            space: SpaceRef,
            cid: String,
            sink: RawSink,
        ): Boolean {
            SystemFileSystem.source(stored).buffered().use { source ->
                val buffer = Buffer()
                while (source.readAtMostTo(buffer, 1024L * 1024) != -1L) sink.write(buffer, buffer.size)
            }
            sink.flush()
            return true
        }

        override suspend fun putRecord(
            space: SpaceRef,
            rkey: String,
            record: Sealed,
            blobs: List<String>,
            swap: RecordSwap,
        ): StoredRecord = error("Not used")

        override suspend fun deleteRecord(
            space: SpaceRef,
            rkey: String,
            swap: RecordSwap,
        ): Long = error("Not used")

        override suspend fun getRecord(
            space: SpaceRef,
            rkey: String,
        ): StoredRecord? = error("Not used")

        override suspend fun listChanges(
            space: SpaceRef,
            since: Long,
            limit: Int,
        ): ChangePage = error("Not used")

        override suspend fun uploadBlob(
            space: SpaceRef,
            bytes: ByteArray,
        ): String = error("Not used")

        override suspend fun getBlob(
            space: SpaceRef,
            cid: String,
        ): ByteArray? = error("Not used")

        override suspend fun uploadBlob(
            space: SpaceRef,
            sizeBytes: Long,
            open: () -> RawSource,
        ): String = error("Not used")
    }

    /** Records how far decryption ever reads ahead of what it has written. */
    private class MeasuringCrypto(
        private val delegate: MediaPayloadCrypto,
    ) : MediaPayloadCrypto {
        var largestGap = 0L
            private set

        override suspend fun decrypt(data: ByteArray): ByteArray = delegate.decrypt(data)

        override suspend fun streamEncryptor(): MediaStreamEncryptor = delegate.streamEncryptor()

        override suspend fun streamDecryptor(): MediaStreamDecryptor {
            val inner = delegate.streamDecryptor()
            return object : MediaStreamDecryptor {
                override suspend fun decrypt(
                    source: Source,
                    sink: Sink,
                ): Long {
                    var read = 0L
                    var written = 0L
                    val counted =
                        object : RawSource {
                            override fun readAtMostTo(
                                sink: Buffer,
                                byteCount: Long,
                            ) = source.readAtMostTo(sink, byteCount).also { if (it > 0) read += it }

                            override fun close() = Unit
                        }
                    val out =
                        object : RawSink {
                            override fun write(
                                source: Buffer,
                                byteCount: Long,
                            ) {
                                sink.write(source, byteCount)
                                written += byteCount
                                largestGap = maxOf(largestGap, read - written)
                            }

                            override fun flush() = sink.flush()

                            override fun close() = Unit
                        }
                    val buffered = out.buffered()
                    return inner.decrypt(counted.buffered(), buffered).also { buffered.flush() }
                }
            }
        }
    }
}
