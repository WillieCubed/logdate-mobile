package app.logdate.client.media.storage

import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidCanonicalMediaStoreTest {
    @Test
    fun `identical bytes deduplicate to an app-private MIME-derived object`() =
        withStore { store, root ->
            val bytes = "a private journal photo".encodeToByteArray()

            val first = store.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong())
            val second = store.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong())

            assertEquals(first, second)
            val file = File(java.net.URI(first))
            assertTrue(file.startsWith(File(root, "media/objects/sha256")))
            assertEquals("jpg", file.extension)
            assertContentEquals(bytes, file.readBytes())
        }

    @Test
    fun `mismatched advertised size never publishes an object`() =
        withStore { store, root ->
            val bytes = "truncated data".encodeToByteArray()

            assertFailsWith<IllegalArgumentException> {
                store.store(bytes.inputStream(), "video/mp4", bytes.size.toLong() + 1)
            }

            assertTrue(File(root, "media/objects").walkTopDown().none { it.isFile })
        }

    @Test
    fun `a failed copy leaves neither a published object nor a staging file`() =
        withStore { store, root ->
            assertFailsWith<IOException> {
                store.store(FailingInputStream(), "image/jpeg", 20)
            }

            assertTrue(File(root, "media").walkTopDown().none { it.isFile })
        }

    @Test
    fun `a corrupt destination is replaced with verified bytes`() =
        withStore { store, _ ->
            val bytes = "intact private content".encodeToByteArray()
            val uri = store.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong())
            val destination = File(java.net.URI(uri))
            destination.writeText("corrupt")

            val recovered = store.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong())

            assertEquals(uri, recovered)
            assertContentEquals(bytes, destination.readBytes())
            assertEquals(digest(bytes), destination.nameWithoutExtension)
        }

    @Test
    fun `MIME parameters normalize to the trusted extension`() =
        withStore { store, _ ->
            val bytes = "jpeg data".encodeToByteArray()

            val uri = store.store(bytes.inputStream(), "image/jpeg; charset=binary", bytes.size.toLong())

            assertEquals("jpg", File(java.net.URI(uri)).extension)
        }

    @Test
    fun `supported HEIC media keeps a trusted extension`() =
        withStore { store, _ ->
            val bytes = "heic data".encodeToByteArray()

            val uri = store.store(bytes.inputStream(), "image/heic", bytes.size.toLong())

            assertEquals("heic", File(java.net.URI(uri)).extension)
        }

    @Test
    fun `supported media uses its own MIME-derived extension`() =
        withStore { store, _ ->
            val bytes = "media bytes".encodeToByteArray()
            val extensions =
                listOf(
                    "image/heif" to "heif",
                    "image/heic-sequence" to "heic",
                    "image/heif-sequence" to "heif",
                    "video/3gpp2" to "3g2",
                    "video/mpeg" to "mpeg",
                    "video/ogg" to "ogv",
                    "audio/amr" to "amr",
                    "audio/midi" to "mid",
                    "audio/webm" to "weba",
                )

            extensions.forEach { (mimeType, extension) ->
                val uri = store.store(bytes.inputStream(), mimeType, bytes.size.toLong())
                assertEquals(extension, File(java.net.URI(uri)).extension)
            }
        }

    @Test
    fun `starting a new import removes stale staging files but preserves unrelated files`() =
        withStore { store, root ->
            val staging = File(root, "media/staging").apply { mkdirs() }
            val staleTemp =
                File(staging, "00000000-0000-0000-0000-000000000000.tmp").apply {
                    writeText("stale")
                    setLastModified(0L)
                }
            val unrelated = File(staging, "keep.txt").apply { writeText("keep") }
            val bytes = "fresh data".encodeToByteArray()

            store.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong())

            assertTrue(!staleTemp.exists())
            assertTrue(unrelated.exists())
        }

    @Test
    fun `retry after directory sync failure syncs the already-published object`() {
        val root = Files.createTempDirectory("logdate-canonical-media-").toFile()
        try {
            var syncAttempts = 0
            val store =
                AndroidCanonicalMediaStore(root) {
                    syncAttempts++
                    if (syncAttempts == 1) throw IOException("power loss before directory sync")
                }
            val bytes = "retry durable object".encodeToByteArray()

            runBlocking {
                assertFailsWith<IOException> {
                    store.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong())
                }
                val uri = store.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong())

                assertContentEquals(bytes, File(java.net.URI(uri)).readBytes())
            }
            assertEquals(2, syncAttempts)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun withStore(block: suspend (AndroidCanonicalMediaStore, File) -> Unit) {
        val root = Files.createTempDirectory("logdate-canonical-media-").toFile()
        try {
            runBlocking { block(AndroidCanonicalMediaStore(root), root) }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private class FailingInputStream : InputStream() {
        private var reads = 0

        override fun read(): Int {
            reads++
            if (reads > 4) throw IOException("provider grant disappeared")
            return 'x'.code
        }
    }
}
