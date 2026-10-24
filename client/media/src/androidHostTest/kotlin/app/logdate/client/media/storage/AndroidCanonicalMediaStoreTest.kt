package app.logdate.client.media.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
            val file = File(java.net.URI(first))
            assertTrue(file.startsWith(File(root, "media/objects/sha256")))
            assertEquals("jpg", file.extension)

            repeat(4) {
                val duplicate = store.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong())

                assertEquals(first, duplicate)
                assertContentEquals(bytes, file.readBytes())
                assertEquals(1, File(root, "media/objects").walkTopDown().count { it.isFile })
                assertTrue(
                    File(root, "media/staging").walkTopDown().none { it.isFile },
                    "A successful duplicate import must immediately discard its staging copy",
                )
            }
        }

    @Test
    fun `mismatched advertised size never publishes an object`() =
        withStore { store, root ->
            val bytes = "truncated data".encodeToByteArray()

            assertFailsWith<IllegalArgumentException> {
                store.store(bytes.inputStream(), "video/mp4", bytes.size.toLong() + 1)
            }

            assertTrue(File(root, "media/objects").walkTopDown().none { it.isFile })
            assertTrue(File(root, "media/staging").walkTopDown().none { it.isFile })
        }

    @Test
    fun `scope change while staging prevents the object from being finalized`() =
        withStore { store, root ->
            val bytes = "account scoped journal media".encodeToByteArray()
            var scopeIsCurrent = true
            val input =
                object : java.io.ByteArrayInputStream(bytes) {
                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        val count = super.read(buffer, offset, length)
                        if (count < 0) scopeIsCurrent = false
                        return count
                    }
                }

            assertFailsWith<CancellationException> {
                store.store(
                    input,
                    "image/jpeg",
                    bytes.size.toLong(),
                    canCommit = { scopeIsCurrent },
                )
            }

            assertTrue(File(root, "media/objects").walkTopDown().none { it.isFile })
            assertTrue(File(root, "media/staging").walkTopDown().none { it.isFile })
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
    fun `different bytes at a canonical path survive when incoming media is imported`() =
        withStore { store, root ->
            val bytes = "intact private content".encodeToByteArray()
            val uri = store.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong())
            val destination = File(java.net.URI(uri))
            val workingBytes = "working entry bytes".encodeToByteArray()
            destination.writeBytes(workingBytes)

            val recovered = store.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong())

            assertTrue(recovered != uri)
            assertContentEquals(workingBytes, destination.readBytes())
            assertContentEquals(bytes, File(java.net.URI(recovered)).readBytes())
            val restarted = AndroidCanonicalMediaStore(root)
            assertEquals(recovered, restarted.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong()))
            assertContentEquals(workingBytes, destination.readBytes())
            assertEquals(digest(bytes), destination.name.substringBefore('.'))
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
                    "image/heic-sequence" to "heics",
                    "image/heif-sequence" to "heifs",
                    "video/3gpp2" to "3g2",
                    "video/mpeg" to "mpg",
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
    fun `an interrupted older import never deletes quarantined working bytes`() =
        withStore { store, root ->
            val staging = File(root, "media/staging").apply { mkdirs() }
            val stranded =
                File(staging, "00000000-0000-0000-0000-000000000000.corrupt").apply {
                    writeText("working entry bytes")
                    setLastModified(0L)
                }

            store.store("new import".byteInputStream(), "image/jpeg", 10)

            assertEquals("working entry bytes", stranded.readText())
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

    @Test
    fun `collision interrupted after publication keeps both files and retries the same alternate`() {
        val root = Files.createTempDirectory("logdate-collision-interrupted-").toFile()
        try {
            val source = "incoming bytes".encodeToByteArray()
            val initial = runBlocking { AndroidCanonicalMediaStore(root).store(source.inputStream(), "image/jpeg", source.size.toLong()) }
            val working = File(java.net.URI(initial))
            working.writeText("working bytes")
            var failSync = true
            val interrupted =
                AndroidCanonicalMediaStore(root) {
                    if (failSync) throw IOException("process stopped after publication")
                }

            runBlocking {
                assertFailsWith<IOException> { interrupted.store(source.inputStream(), "image/jpeg", source.size.toLong()) }
                failSync = false
                val resumed = AndroidCanonicalMediaStore(root).store(source.inputStream(), "image/jpeg", source.size.toLong())

                assertTrue(resumed != initial)
                assertContentEquals("working bytes".encodeToByteArray(), working.readBytes())
                assertContentEquals(source, File(java.net.URI(resumed)).readBytes())
                assertEquals(2, File(root, "media/objects").walkTopDown().count { it.isFile })
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `dangling links reserve canonical and alternate paths without trapping retries`() =
        withStore { store, root ->
            val bytes = "incoming bytes".encodeToByteArray()
            val digest = digest(bytes)
            val directory = File(root, "media/objects/sha256/${digest.take(2)}").apply { mkdirs() }
            val canonical = File(directory, "$digest.jpg")
            val firstAlternate = File(directory, "$digest.retained-1.jpg")
            Files.createSymbolicLink(canonical.toPath(), File(directory, "missing-canonical").toPath())
            Files.createSymbolicLink(firstAlternate.toPath(), File(directory, "missing-alternate").toPath())

            val saved = store.store(bytes.inputStream(), "image/jpeg", bytes.size.toLong())

            assertEquals("$digest.retained-2.jpg", File(java.net.URI(saved)).name)
            assertTrue(Files.isSymbolicLink(canonical.toPath()))
            assertTrue(Files.isSymbolicLink(firstAlternate.toPath()))
            assertContentEquals(bytes, File(java.net.URI(saved)).readBytes())
            assertEquals(saved, AndroidCanonicalMediaStore(root).store(bytes.inputStream(), "image/jpeg", bytes.size.toLong()))
        }

    @Test
    fun `separate store instances importing identical bytes converge on one reference`() =
        withStore { _, root ->
            val bytes = "concurrent original".encodeToByteArray()
            val ready = CountDownLatch(2)
            coroutineScope {
                val first =
                    async(Dispatchers.IO) {
                        AndroidCanonicalMediaStore(root).store(bytes.inputStream(), "image/jpeg", bytes.size.toLong()) {
                            ready.countDown()
                            ready.await(5, TimeUnit.SECONDS)
                        }
                    }
                val second =
                    async(Dispatchers.IO) {
                        AndroidCanonicalMediaStore(root).store(bytes.inputStream(), "image/jpeg", bytes.size.toLong()) {
                            ready.countDown()
                            ready.await(5, TimeUnit.SECONDS)
                        }
                    }
                assertEquals(first.await(), second.await())
            }
            assertEquals(1, File(root, "media/objects").walkTopDown().count { it.isFile })
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
