package app.logdate.client.data.restore

import app.logdate.client.media.storage.UnavailableMediaReferences
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeLateMediaRepairTest {
    @Test
    fun `media only delivery repairs completed clean restore after a restart without database source`() =
        runTest {
            fixture { f ->
                // The clean install's DB/key/receipt cohort has already been consumed. Only the
                // existing reference and independently retained original survive this restart.
                f.write(Document("entry", "kept caption", listOf(f.unavailable)))
                val captured = f.target()
                assertEquals(1, f.engine().repair(listOf(captured)).repaired)
                assertEquals(Document("entry", "kept caption", listOf(f.original)), f.read())
                assertEquals("original bytes", f.live.readText())
                assertTrue(f.payload.exists())
                assertFalse(File(f.root, "database").exists())
                assertEquals(0, f.engine().repair(listOf(f.target())).repaired)
                assertEquals(1, f.imports)
            }
        }

    @Test
    fun `caption or reference edited after capture wins over hydration`() =
        runTest {
            fixture { f ->
                val original = Document("entry", "captured", listOf(f.unavailable))
                f.write(original)
                val target = f.target()
                val edited = original.copy(caption = "new caption", references = listOf("content://user/other"))
                f.beforeCommit = { f.write(edited) }
                val result = f.engine().repair(listOf(target))
                assertEquals(0, result.repaired)
                assertTrue(result.recoveredReferences.isEmpty())
                assertTrue(result.retry)
                assertEquals("original bytes", f.live.readText())
                assertEquals(edited, f.read())
                assertTrue(
                    f
                        .engine()
                        .repair(listOf(f.target()))
                        .recoveredReferences
                        .isEmpty(),
                )
                assertTrue(f.payload.exists())
            }
        }

    @Test
    fun `lost caption CAS cannot acknowledge an unresolved marker and retries its current snapshot`() =
        runTest {
            fixture { f ->
                val original = Document("entry", "captured caption", listOf(f.unavailable))
                f.write(original)
                val target = f.target()
                f.beforeCommit = {
                    f.write(original.copy(caption = "new caption"))
                    f.beforeCommit = {}
                }
                val result = f.engine().repair(listOf(target))
                assertTrue(result.retry)
                assertTrue(result.recoveredReferences.isEmpty())
                assertEquals(listOf(f.unavailable), f.read().references)
                assertTrue(f.payload.exists())
                val retry = f.engine().repair(listOf(f.target()))
                assertFalse(retry.retry)
                assertEquals(setOf(f.original), retry.recoveredReferences)
                assertEquals(Document("entry", "new caption", listOf(f.original)), f.read())
            }
        }

    @Test
    fun `deleted entry is never recreated by media only recovery`() =
        runTest {
            fixture { f ->
                f.write(Document("entry", "captured", listOf(f.unavailable)))
                val target = f.target()
                f.beforeCommit = { check(f.document.delete()) }
                val result = f.engine().repair(listOf(target))
                assertEquals(0, result.repaired)
                assertTrue(result.recoveredReferences.isEmpty())
                assertTrue(result.retry)
                assertEquals("original bytes", f.live.readText())
                assertFalse(f.document.exists())
                assertTrue(
                    f
                        .engine()
                        .repair(emptyList())
                        .recoveredReferences
                        .isEmpty(),
                )
                assertTrue(f.payload.exists())
            }
        }

    @Test
    fun `wrong digest bytes and provider or legacy references remain untouched`() =
        runTest {
            fixture { f ->
                val original = Document("entry", "unchanged", listOf(f.unavailable, "content://provider/1", "file:///old/audio.m4a"))
                f.write(original)
                f.payload.writeText("not the named original")
                assertEquals(0, f.engine().repair(listOf(f.target())).repaired)
                assertEquals(original, f.read())
                assertEquals(0, f.imports)
                assertFalse(f.live.exists())
            }
        }

    @Test
    fun `process death after import before CAS resumes without duplicate objects or metadata loss`() =
        runTest {
            fixture { f ->
                val original = Document("draft", "unchanged text", listOf(f.unavailable))
                f.write(original)
                f.beforeCommit = { throw InterruptedException("process stopped after durable import") }
                assertFailsWith<InterruptedException> { f.engine().repair(listOf(f.target())) }
                assertEquals(original, f.read())
                assertEquals(1, f.imports)
                f.beforeCommit = {}
                assertEquals(1, f.engine().repair(listOf(f.target())).repaired)
                assertEquals(original.copy(references = listOf(f.original)), f.read())
                assertEquals(1, f.imports)
            }
        }

    @Test
    fun `scope revocation after import prevents reference commit`() =
        runTest {
            fixture { f ->
                val original = Document("entry", "unchanged", listOf(f.unavailable))
                f.write(original)
                f.afterImport = { f.current = false }
                assertFailsWith<IllegalStateException> { f.engine().repair(listOf(f.target())) }
                assertEquals(original, f.read())
                assertTrue(f.payload.exists())
            }
        }

    @Test
    fun `draft with multiple fields repairs in one CAS while repeated references share one import`() =
        runTest {
            fixture { f ->
                f.write(Document("draft", "keep timestamp and selection", listOf(f.unavailable, f.unavailable)))
                assertEquals(1, f.engine().repair(listOf(f.target())).repaired)
                assertEquals(listOf(f.original, f.original), f.read().references)
                assertEquals(1, f.commits)
                assertEquals(1, f.imports)
            }
        }

    @Test
    fun `v2 unreferenced canonical bytes remain in custody without source media proof`() =
        runTest {
            fixture { f ->
                val result = f.engine().repair(emptyList())
                assertTrue(result.recoveredReferences.isEmpty())
                assertEquals(0, f.imports)
                assertFalse(f.live.exists())
                assertTrue(f.payload.exists())
            }
        }

    @Test
    fun `source authenticated unreferenced canonical original becomes portable without creating an entry`() =
        runTest {
            fixture { f ->
                f.orphansAuthenticated = true
                val result = f.engine().repair(emptyList())
                assertEquals(0, result.repaired)
                assertEquals(setOf(f.original), result.recoveredReferences)
                assertFalse(f.document.exists())
                assertEquals("original bytes", f.live.readText())
                assertEquals(1, f.imports)
                assertEquals(setOf(f.original), f.engine().repair(emptyList()).recoveredReferences)
                assertEquals(1, f.imports)
            }
        }

    @Test
    fun `orphan publication refuses scope revoked during import and retains custody`() =
        runTest {
            fixture { f ->
                f.orphansAuthenticated = true
                f.afterImport = { f.current = false }
                assertFailsWith<IllegalStateException> { f.engine().repair(emptyList()) }
                assertTrue(f.payload.exists())
                assertFalse(f.document.exists())
            }
        }

    @Test
    fun `unreferenced wrong-digest original remains in custody without publication`() =
        runTest {
            fixture { f ->
                f.payload.writeText("wrong content for this digest")
                assertTrue(
                    f
                        .engine()
                        .repair(emptyList())
                        .recoveredReferences
                        .isEmpty(),
                )
                assertFalse(f.live.exists())
                assertEquals(0, f.imports)
                assertTrue(f.payload.exists())
            }
        }

    @Test
    fun `resolved local reference is canonicalized for acknowledgement without rewriting the entry`() =
        runTest {
            fixture { f ->
                checkNotNull(f.live.parentFile).mkdirs()
                f.live.writeText("original bytes")
                val local = f.live.toURI().toString()
                f.write(Document("entry", "unchanged", listOf(local)))
                val engine = f.engine { if (it == local) f.original else it }
                val result = engine.repair(listOf(f.target()))
                assertEquals(setOf(f.original), result.recoveredReferences)
                assertEquals(local, f.read().references.single())
                assertEquals(0, f.commits)
                assertEquals(0, f.imports)
            }
        }

    private data class Document(
        val id: String,
        val caption: String,
        val references: List<String>,
    )

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val root = Files.createTempDirectory("late-media-repair-").toFile().canonicalFile
        try {
            block(Fixture(root))
        } finally {
            check(root.deleteRecursively())
        }
    }

    private class Fixture(
        val root: File,
    ) {
        private val digest = sha256("original bytes".encodeToByteArray())
        private val relative = "objects/sha256/${digest.take(2)}/$digest.png"
        val original = "logdate-media://library/$relative"
        val unavailable = UnavailableMediaReferences.nativeRestore(original)
        val payload =
            File(root, "custody/payload").apply {
                checkNotNull(parentFile).mkdirs()
                writeText("original bytes")
            }
        val live = File(root, "live/$relative")
        val document = File(root, "document")
        var orphansAuthenticated = false
        var imports = 0
        var commits = 0
        var current = true
        var beforeCommit: () -> Unit = {}
        var afterImport: () -> Unit = {}

        fun write(value: Document) = document.writeText((listOf(value.id, value.caption) + value.references).joinToString("\n"))

        fun read(): Document = document.readLines().let { Document(it[0], it[1], it.drop(2)) }

        fun target(): NativeLateMediaRepair.Target {
            val captured = read()
            return NativeLateMediaRepair.Target(captured.references.toSet()) { replacements ->
                beforeCommit()
                check(current)
                if (!document.exists() || read() != captured) {
                    false
                } else {
                    write(captured.copy(references = captured.references.map { replacements[it] ?: it }))
                    commits++
                    true
                }
            }
        }

        fun engine(canonicalReference: (String) -> String = { it }) =
            NativeLateMediaRepair(
                listOf(mapOf("files/media/$relative" to payload)),
                requireCurrent = { check(current) },
                available = { reference -> reference == original && live.exists() && sha256(live.readBytes()) == digest },
                importOriginal = { asset ->
                    checkNotNull(live.parentFile).mkdirs()
                    asset.file.copyTo(live, overwrite = true)
                    imports++
                    afterImport()
                },
                canonicalReference = canonicalReference,
                authorizeUnreferenced = { reference, asset -> orphansAuthenticated && reference == original && asset.file == payload },
            )
    }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
