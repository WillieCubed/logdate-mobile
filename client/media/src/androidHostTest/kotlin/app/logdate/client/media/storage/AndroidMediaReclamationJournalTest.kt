package app.logdate.client.media.storage

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidMediaReclamationJournalTest {
    @Test
    fun `candidates survive a new journal instance and duplicate records`() =
        runBlocking {
            val root = Files.createTempDirectory("media-reclaim-journal-").toFile()
            try {
                val first = AndroidMediaReclamationJournal(root)
                first.recordCandidate("logdate-media://library/objects/sha256/ab/example.jpg")
                first.recordCandidate("logdate-media://library/objects/sha256/ab/example.jpg")

                assertEquals(
                    setOf("logdate-media://library/objects/sha256/ab/example.jpg"),
                    AndroidMediaReclamationJournal(root).pendingCandidates().toSet(),
                )
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun `distinct candidates remain pending across journal instances`() =
        runBlocking {
            val root = Files.createTempDirectory("media-reclaim-journal-").toFile()
            try {
                val journal = AndroidMediaReclamationJournal(root)
                journal.recordCandidate("logdate-media://library/a.jpg")
                journal.recordCandidate("logdate-media://library/b.jpg")

                assertEquals(
                    listOf("logdate-media://library/a.jpg", "logdate-media://library/b.jpg"),
                    AndroidMediaReclamationJournal(root).pendingCandidates(),
                )
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun `interrupted temporary records do not become candidates`() =
        runBlocking {
            val root = Files.createTempDirectory("media-reclaim-journal-").toFile()
            try {
                val journal = AndroidMediaReclamationJournal(root)
                journal.recordCandidate("logdate-media://library/saved.jpg")
                val directory = root.resolve("media-reclamation")
                directory.resolve("interrupted.part").writeText("logdate-media://library/unsaved.jpg")

                val pending = AndroidMediaReclamationJournal(root).pendingCandidates()
                assertTrue("logdate-media://library/saved.jpg" in pending)
                assertFalse("logdate-media://library/unsaved.jpg" in pending)
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun `damaged candidate stops replay rather than silently dropping a claim`() =
        runBlocking {
            val root = Files.createTempDirectory("media-reclaim-journal-").toFile()
            try {
                val journal = AndroidMediaReclamationJournal(root)
                journal.recordCandidate("logdate-media://library/saved.jpg")
                val candidate = root.resolve("media-reclamation").listFiles()!!.single { it.name.endsWith(".candidate") }
                candidate.writeText("logdate-media://library/changed.jpg")

                assertFailsWith<IllegalStateException> { AndroidMediaReclamationJournal(root).pendingCandidates() }
                assertTrue(candidate.exists(), "damaged evidence is retained for diagnosis")
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun `symlinked candidate directory is refused without writing outside private storage`() =
        runBlocking {
            val root = Files.createTempDirectory("media-reclaim-journal-").toFile()
            val outside = Files.createTempDirectory("media-reclaim-outside-").toFile()
            try {
                Files.createSymbolicLink(root.resolve("media-reclamation").toPath(), outside.toPath())

                assertFailsWith<IllegalStateException> {
                    AndroidMediaReclamationJournal(root).recordCandidate("logdate-media://library/private.jpg")
                }
                assertTrue(outside.listFiles().isNullOrEmpty())
            } finally {
                root.deleteRecursively()
                outside.deleteRecursively()
            }
        }
}
