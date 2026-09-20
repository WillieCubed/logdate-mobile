package app.logdate.client.domain.export.archive

import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class StagingZipArchiveContainerTest {
    @Test
    fun `staged entries are finished as a portable zip`() {
        val directory = Files.createTempDirectory("logdate-staging-container").toFile()
        val staging = directory.resolve("staging").absolutePath.toPath()
        val output = directory.resolve("export.zip").absolutePath.toPath()
        try {
            val container = StagingZipArchiveContainer(FileSystem.SYSTEM, staging)
            container.entry(ArchivePath.of("manifest.json")) { sink -> sink.writeUtf8("manifest") }
            container.entry(ArchivePath.of("data/notes.json")) { sink -> sink.writeUtf8("notes") }

            container.finish(output)

            assertFalse(FileSystem.SYSTEM.exists(staging))

            ZipFile(output.toFile()).use { zip ->
                assertEquals("manifest", zip.getInputStream(zip.getEntry("manifest.json")).reader().readText())
                assertEquals("notes", zip.getInputStream(zip.getEntry("data/notes.json")).reader().readText())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `staging is removed when writing an entry fails`() {
        val directory = Files.createTempDirectory("logdate-staging-container-failure").toFile()
        val staging = directory.resolve("staging").absolutePath.toPath()
        try {
            val container = StagingZipArchiveContainer(FileSystem.SYSTEM, staging)

            assertFailsWith<IllegalStateException> {
                container.entry(ArchivePath.of("data/notes.json")) { sink ->
                    sink.writeUtf8("partial")
                    error("simulated write failure")
                }
            }

            assertFalse(FileSystem.SYSTEM.exists(staging))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `discard removes a partially staged archive`() {
        val directory = Files.createTempDirectory("logdate-staging-container-discard").toFile()
        val staging = directory.resolve("staging").absolutePath.toPath()
        try {
            val container = StagingZipArchiveContainer(FileSystem.SYSTEM, staging)
            container.entry(ArchivePath.of("manifest.json")) { sink -> sink.writeUtf8("partial") }

            container.discard()

            assertFalse(FileSystem.SYSTEM.exists(staging))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun okio.Sink.writeUtf8(value: String) {
        val buffer = Buffer().writeUtf8(value)
        write(buffer, buffer.size)
    }
}
