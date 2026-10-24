package app.logdate.client.data.restore

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeLateLegacyMediaRepairTest {
    @Test
    fun `authenticated missing legacy original imports then commits exact captured reference`() =
        runTest {
            fixture { f ->
                val result = f.engine().repair(listOf(f.target()))
                assertEquals(setOf(f.payload.canonicalPath), result.verifiedFiles)
                assertEquals(f.canonical, f.reference)
                assertEquals(1, f.imports)
            }
        }

    @Test
    fun `readable current legacy original is never replaced by incoming bytes`() =
        runTest {
            fixture { f ->
                f.readable = true
                assertTrue(
                    f
                        .engine()
                        .repair(listOf(f.target()))
                        .verifiedFiles
                        .isEmpty(),
                )
                assertEquals(f.legacy, f.reference)
                assertEquals(0, f.imports)
            }
        }

    @Test
    fun `lost reference CAS retains custody and retries without reimporting`() =
        runTest {
            fixture { f ->
                f.commit = false
                val first = f.engine().repair(listOf(f.target()))
                assertTrue(first.retry)
                assertTrue(first.verifiedFiles.isEmpty())
                assertEquals(f.legacy, f.reference)
                f.commit = true
                assertEquals(setOf(f.payload.canonicalPath), f.engine().repair(listOf(f.target())).verifiedFiles)
                assertEquals(1, f.imports)
            }
        }

    @Test
    fun `process death after CAS can acknowledge remembered canonical reference`() =
        runTest {
            fixture { f ->
                f.engine().repair(listOf(f.target()))
                val rebound = f.candidate.copy(references = setOf(f.canonical))
                assertEquals(setOf(f.payload.canonicalPath), f.engine(rebound).repair(listOf(f.target())).verifiedFiles)
                assertEquals(1, f.imports)
            }
        }

    @Test
    fun `post CAS deletion acknowledges durable authenticated original without recreating note`() =
        runTest {
            fixture { f ->
                f.engine().repair(listOf(f.target()))
                val rebound = f.candidate.copy(references = emptySet())
                assertEquals(setOf(f.payload.canonicalPath), f.engine(rebound).repair(emptyList()).verifiedFiles)
                assertEquals(1, f.imports)
            }
        }

    @Test
    fun `deleted reference cannot acknowledge legacy bytes`() =
        runTest {
            fixture { f ->
                assertTrue(
                    f
                        .engine()
                        .repair(emptyList())
                        .verifiedFiles
                        .isEmpty(),
                )
                assertEquals(0, f.imports)
            }
        }

    @Test
    fun `scope revocation during import never commits reference or acknowledges`() =
        runTest {
            fixture { f ->
                f.revokeOnImport = true
                assertFailsWith<IllegalStateException> { f.engine().repair(listOf(f.target())) }
                assertEquals(f.legacy, f.reference)
                assertFalse(f.remembered != null)
            }
        }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val root = Files.createTempDirectory("native-legacy-repair-").toFile()
        try {
            block(Fixture(root))
        } finally {
            check(root.deleteRecursively())
        }
    }

    private class Fixture(
        root: File,
    ) {
        val payload = File(root, "original").apply { writeText("original bytes") }
        val digest = nativeSnapshotChecksum(payload)
        val legacy = "logdate-media://recordings/original.m4a"
        val canonical = "logdate-media://library/objects/sha256/${digest.take(2)}/$digest.m4a"
        val candidate = NativeLegacyLateMediaCandidate("files/audio_notes/original.m4a", payload, digest, setOf(legacy))
        var reference = legacy
        var readable = false
        var live = false
        var current = true
        var commit = true
        var revokeOnImport = false
        var imports = 0
        var remembered: String? = null

        fun target(): NativeLateMediaRepair.Target {
            val expected = reference
            return NativeLateMediaRepair.Target(setOf(expected)) { replacement ->
                if (!commit || reference != expected) {
                    false
                } else {
                    reference = replacement[expected] ?: expected
                    true
                }
            }
        }

        fun engine(selected: NativeLegacyLateMediaCandidate = candidate) =
            NativeLateLegacyMediaRepair(
                listOf(selected),
                requireCurrent = { check(current) },
                legacyReadable = { readable },
                available = { it == canonical && live },
                importOriginal = {
                    imports++
                    live = true
                    if (revokeOnImport) current = false
                    canonical
                },
                remembered = { remembered },
                remember = { _, value -> remembered = value },
                canonicalReference = { it },
            )
    }
}
