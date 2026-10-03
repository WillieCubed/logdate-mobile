package app.logdate.client.sync.datalayer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Tests the length and SHA-256 measurements both devices make of watch audio.
 */
class AudioFileDigestsTest {
    private val file = File.createTempFile("audio-digest", ".m4a")

    @AfterTest
    fun cleanUp() {
        file.delete()
    }

    @Test
    fun `copying measures exactly what was copied`() {
        val output = ByteArrayOutputStream()

        val digest = ByteArrayInputStream("abc".encodeToByteArray()).copyToDigesting(output)

        assertContentEquals("abc".encodeToByteArray(), output.toByteArray())
        assertEquals(AudioFileDigest(sizeBytes = 3, sha256Hex = ABC_SHA256), digest)
    }

    @Test
    fun `an empty stream measures zero bytes`() {
        val digest = ByteArrayInputStream(ByteArray(0)).copyToDigesting(ByteArrayOutputStream())

        assertEquals(AudioFileDigest(sizeBytes = 0, sha256Hex = EMPTY_SHA256), digest)
    }

    @Test
    fun `a file is measured from its contents`() {
        file.writeText("abc")

        assertEquals(AudioFileDigest(sizeBytes = 3, sha256Hex = ABC_SHA256), file.audioDigest())
    }

    @Test
    fun `a file longer than one buffer is measured whole`() {
        val bytes = ByteArray(3 * DEFAULT_BUFFER_SIZE + 17) { index -> (index % 251).toByte() }
        file.writeBytes(bytes)

        val digest = file.audioDigest()

        assertEquals(bytes.size.toLong(), digest.sizeBytes)
        assertEquals(ByteArrayInputStream(bytes).copyToDigesting(ByteArrayOutputStream()), digest)
    }

    private companion object {
        const val ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
