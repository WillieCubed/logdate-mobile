package app.logdate.client.data.restore

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetainedJournalMergeCheckpointPathTest {
    @Test
    fun `checkpoint directory anchors to canonical app private root`() {
        val parent = Files.createTempDirectory("checkpoint-root").toFile()
        val realRoot = Files.createDirectory(parent.toPath().resolve("real")).toFile()
        val appDataAlias = parent.toPath().resolve("app-data-alias")
        Files.createSymbolicLink(appDataAlias, realRoot.toPath())

        val directory = retainedJournalMergeCheckpointDirectory(appDataAlias.toFile(), "generation-1")

        assertEquals(
            realRoot.canonicalFile,
            directory
                .toPath()
                .parent.parent
                .toFile()
                .canonicalFile,
        )
        assertEquals("generation-1", directory.name)
    }

    @Test
    fun `checkpoint path rejects a symlink introduced below the trusted root`() {
        val root = Files.createTempDirectory("checkpoint-root").toFile()
        val outside = Files.createTempDirectory("checkpoint-outside")
        val checkpointParent = root.toPath().resolve("retained-journal-merges")
        Files.createSymbolicLink(checkpointParent, outside)

        assertFailsWith<IllegalArgumentException> {
            requireCanonicalCheckpointPath(checkpointParent.resolve("generation-1").toFile())
        }
    }
}
