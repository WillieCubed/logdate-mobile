package app.logdate.client.domain.export.archive

import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class StagingDirectoryArchiveContainerTest {
    @Test
    fun `writes a portable archive tree without zip packaging`() {
        val parent = Files.createTempDirectory("logdate-native-directory").toFile()
        try {
            val root = parent.resolve("archive").absolutePath.toPath()
            val container = StagingDirectoryArchiveContainer(FileSystem.SYSTEM, root)
            container.entry(ArchivePath.of("manifest.json")) { it.writeUtf8("manifest") }
            container.entry(ArchivePath.of("data/notes.json")) { it.writeUtf8("notes") }
            assertFailsWith<IllegalArgumentException> {
                container.entry(ArchivePath.of("data/notes.json")) { it.writeUtf8("replacement") }
            }
            assertEquals("manifest", FileSystem.SYSTEM.read(root / "manifest.json") { readUtf8() })
            assertEquals("notes", FileSystem.SYSTEM.read(root / "data/notes.json") { readUtf8() })
            container.discard()
            assertFalse(FileSystem.SYSTEM.exists(root))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `rejects linked parents and partial entries`() {
        val parent = Files.createTempDirectory("logdate-native-directory-link").toFile()
        try {
            val outside = parent.resolve("outside").apply { mkdir() }
            val root = parent.resolve("archive").absolutePath.toPath()
            val container = StagingDirectoryArchiveContainer(FileSystem.SYSTEM, root)
            Files.createSymbolicLink(parent.resolve("archive/data").toPath(), outside.toPath())
            assertFailsWith<IllegalArgumentException> {
                container.entry(ArchivePath.of("data/notes.json")) { it.writeUtf8("forbidden") }
            }
            assertFalse(outside.resolve("notes.json").exists())
            assertFailsWith<IllegalStateException> {
                container.entry(ArchivePath.of("manifest.json")) {
                    it.writeUtf8("partial")
                    error("interrupted")
                }
            }
            assertFalse(FileSystem.SYSTEM.exists(root / "manifest.json"))
        } finally {
            parent.deleteRecursively()
        }
    }

    private fun okio.Sink.writeUtf8(value: String) {
        val buffer = Buffer().writeUtf8(value)
        write(buffer, buffer.size)
    }
}
