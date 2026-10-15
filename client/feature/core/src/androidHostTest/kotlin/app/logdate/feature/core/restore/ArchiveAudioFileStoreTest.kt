package app.logdate.feature.core.restore

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ArchiveAudioFileStoreTest {
    @Test
    fun `a restored recording cannot overwrite an accepted recording with the same basename`() {
        val root = Files.createTempDirectory("archive-audio-safety").toFile()
        try {
            val recordings = root.resolve("recordings").apply { mkdirs() }
            val accepted = recordings.resolve("recording.m4a").apply { writeBytes(byteArrayOf(1, 2)) }
            val source = root.resolve("incoming.m4a").apply { writeBytes(byteArrayOf(3, 4)) }
            val imported = ArchiveAudioFileStore(recordings, root.resolve("staging")).import(source, "recording.m4a")
            assertNotEquals(accepted, imported)
            assertContentEquals(byteArrayOf(1, 2), accepted.readBytes())
            assertContentEquals(byteArrayOf(3, 4), imported.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `retries cannot replace bytes attached by an earlier attempt`() {
        val root = Files.createTempDirectory("archive-audio-retry").toFile()
        try {
            val store = ArchiveAudioFileStore(root.resolve("recordings"), root.resolve("staging"))
            val source = root.resolve("incoming.wav").apply { writeBytes(byteArrayOf(1)) }
            val first = store.import(source, "recording.wav")
            source.writeBytes(byteArrayOf(2))
            val second = store.import(source, "recording.wav")
            assertNotEquals(first, second)
            assertContentEquals(byteArrayOf(1), first.readBytes())
            assertContentEquals(byteArrayOf(2), second.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `failed extraction leaves no committed or staging file`() {
        val root = Files.createTempDirectory("archive-audio-failure").toFile()
        try {
            val directory = root.resolve("recordings")
            assertFailsWith<java.io.IOException> {
                ArchiveAudioFileStore(directory, root.resolve("staging")).import(root.resolve("absent.wav"), "recording.wav")
            }
            assertFalse(directory.listFiles().orEmpty().isNotEmpty())
            assertFalse(
                root
                    .resolve("staging")
                    .listFiles()
                    .orEmpty()
                    .isNotEmpty(),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
