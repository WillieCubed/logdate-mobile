package app.logdate.client.media.storage

import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidMediaCollectorTest {
    @Test
    fun `collector keeps live references and deletes only unclaimed owned candidates`() =
        runBlocking {
            val root = Files.createTempDirectory("media-collector-").toFile()
            try {
                val store = AndroidCanonicalMediaStore(File(root, "files"))
                val live = store.store("live bytes".byteInputStream(), "image/jpeg", 10)
                val orphan = store.store("orphan bytes".byteInputStream(), "image/png", 12)
                val mediaRoot = File(root, "files/media")
                val liveReference = "logdate-media://library/${File(java.net.URI(live)).relativeTo(mediaRoot).invariantSeparatorsPath}"
                val orphanReference = "logdate-media://library/${File(java.net.URI(orphan)).relativeTo(mediaRoot).invariantSeparatorsPath}"
                val journal = AndroidMediaReclamationJournal(File(root, "no-backup"))
                journal.recordCandidate(liveReference)
                journal.recordCandidate(orphanReference)
                val collector =
                    AndroidMediaCollector(
                        AndroidMediaClaimFence(File(root, "no-backup")),
                        MediaOwnerCensus { setOf(liveReference) },
                        StoredMediaReferences.Unchanged,
                        journal,
                        AndroidMediaPublicationTickets(File(root, "no-backup")),
                        store,
                    )

                assertEquals(1, collector.collect())
                assertTrue(File(URI(live)).exists())
                assertFalse(File(URI(orphan)).exists())
                assertTrue(journal.pendingCandidates().isEmpty())
            } finally {
                root.deleteRecursively()
            }
        }
}
