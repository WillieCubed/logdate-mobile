package app.logdate.client.device.restore

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativePrivacyRepairFilesTest {
    @Test
    fun `metadata-only and draft-only deliveries still require successful isolated validation`() =
        fixture { f ->
            for (paths in listOf(emptySet(), setOf("files/datastore/entry_drafts.preferences_pb"))) {
                var inspected = false
                val proof =
                    verifyNativePrivacyRepairFiles(f.current.filterKeys { it in paths }, f.incoming.filterKeys { it in paths }) {
                        inspected =
                            true
                    }
                assertTrue(inspected)
                assertEquals(paths, proof.keys)
            }
        }

    @Test
    fun `same journal inputs allow only isolated validation with rebuildable SHM excluded`() =
        fixture { f ->
            f.current["database/logdate-shm"] = f.file("current-shm", "volatile cache")
            f.incoming["database/logdate-shm"] = f.file("source-shm", "different cache")
            val before = f.current.mapValues { it.value.readText() }
            var inspected = false
            val proof =
                verifyNativePrivacyRepairFiles(f.current, f.incoming) { selected ->
                    inspected = true
                    assertFalse("database/logdate-shm" in selected)
                    assertEquals(f.incoming["database/logdate"], selected["database/logdate"])
                }
            assertTrue(inspected)
            assertEquals(f.current.keys - "database/logdate-shm", proof.keys)
            assertEquals(before, f.current.mapValues { it.value.readText() })
        }

    @Test
    fun `changed journal key identity or draft is rejected before inspection`() =
        fixture { f ->
            for (path in f.current.keys.toList()) {
                val original = f.incoming.getValue(path).readText()
                f.incoming.getValue(path).writeText("other source")
                assertFailsWith<IllegalStateException> {
                    verifyNativePrivacyRepairFiles(f.current, f.incoming) {
                        error("Must reject before opening a copy")
                    }
                }
                f.incoming.getValue(path).writeText(original)
            }
        }

    @Test
    fun `missing and newly appearing WAL are distinct protected memberships`() =
        fixture { f ->
            val wal = f.incoming.remove("database/logdate-wal")!!
            assertFailsWith<IllegalStateException> { verifyNativePrivacyRepairFiles(f.current, f.incoming) {} }
            f.incoming["database/logdate-wal"] = wal
            f.incoming["database/logdate-journal"] = f.file("new-journal", "new rollback state")
            assertFailsWith<IllegalStateException> { verifyNativePrivacyRepairFiles(f.current, f.incoming) {} }
        }

    @Test
    fun `unreadable isolated journal cannot establish a privacy repair`() =
        fixture { f ->
            val before = f.current.mapValues { it.value.readText() }
            assertFailsWith<IOException> {
                verifyNativePrivacyRepairFiles(f.current, f.incoming) {
                    throw IOException("Invalid encrypted copy")
                }
            }
            assertEquals(before, f.current.mapValues { it.value.readText() })
        }

    @Test
    fun `a journal changed during inspection stays preserved and invalidates proof`() =
        fixture { f ->
            assertFailsWith<IllegalStateException> {
                verifyNativePrivacyRepairFiles(f.current, f.incoming) {
                    f.current.getValue("database/logdate").writeText("independent write")
                }
            }
            assertEquals("independent write", f.current.getValue("database/logdate").readText())
        }

    @Test
    fun `orphaned SHM and symlink inputs never establish an empty or matching journal`() =
        fixture { f ->
            val cache = mapOf("database/logdate-shm" to f.file("orphan", "cache"))
            assertFailsWith<IllegalStateException> { verifyNativePrivacyRepairFiles(cache, cache) {} }
            val key = f.incoming.getValue("files/db_recovery/passphrase_backup")
            assertTrue(key.delete())
            Files.createSymbolicLink(key.toPath(), f.current.getValue("files/db_recovery/passphrase_backup").toPath())
            assertFailsWith<IllegalStateException> { verifyNativePrivacyRepairFiles(f.current, f.incoming) {} }
        }

    private fun fixture(test: (Fixture) -> Unit) {
        val root = Files.createTempDirectory("native-policy-files").toFile()
        try {
            test(Fixture(root))
        } finally {
            check(root.deleteRecursively())
        }
    }

    private class Fixture(
        val root: File,
    ) {
        val current = mutableMapOf<String, File>()
        val incoming = mutableMapOf<String, File>()

        init {
            for ((index, path) in listOf(
                "database/logdate",
                "database/logdate-wal",
                "files/db_recovery/passphrase_backup",
                "files/identity_recovery/phrase_backup",
                "files/datastore/entry_drafts.preferences_pb",
            ).withIndex()) {
                current[path] = file("current-$index", "same protected bytes $index")
                incoming[path] = file("incoming-$index", "same protected bytes $index")
            }
        }

        fun file(
            name: String,
            bytes: String,
        ): File = File(root, name).apply { writeText(bytes) }
    }
}
