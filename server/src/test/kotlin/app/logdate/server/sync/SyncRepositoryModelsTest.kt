package app.logdate.server.sync

import app.logdate.shared.model.sync.DeviceId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the data classes defined in the sync repository.
 *
 * This suite ensures that the core sync records, markers, and response models
 * are correctly constructed and maintain their expected property values.
 */
class SyncRepositoryModelsTest {
    @Test
    fun `sync model data classes construct correctly`() {
        val userId = UUID.randomUUID()
        val content = ContentRecord("c", "TEXT", "v", null, 0, 1, 1, 2, DeviceId("d"))
        val journal = JournalRecord("j", "t", "d", 1, 1, 2, DeviceId("d"))
        val key = AssociationKey("j", "c")
        val association = AssociationRecord("j", "c", 1, 2, DeviceId("d"))
        val media = MediaRecord("m", "c", userId, "f.jpg", "image/jpeg", 3, byteArrayOf(1, 2, 3), "path", 1, 2, DeviceId("d"))
        val backup = BackupRecord(UUID.randomUUID(), userId, "d", "{}", "path", 1, 3)

        val contentDeletion = ContentDeletionMarker("c", 10)
        val journalDeletion = JournalDeletionMarker("j", 11)
        val associationDeletion = AssociationDeletionMarker(key, 12)

        val changeSet = ChangeSet(changes = listOf(content), deletions = listOf(contentDeletion), lastTimestamp = 12, hasMore = true)
        val defaultHasMore = ChangeSet(changes = listOf(content), deletions = listOf(contentDeletion), lastTimestamp = 13)
        val status = SyncStatus(1, 2, 3, 4)
        val purge = SyncPurgeResult(1, 2, 3, 4, 5)

        assertEquals("TEXT", content.type)
        assertEquals("t", journal.title)
        assertEquals("j", association.journalId)
        assertEquals("m", media.mediaId)
        assertEquals(userId, media.userId)
        assertEquals("{}", backup.manifest)
        assertEquals(userId, backup.userId)
        assertEquals("c", contentDeletion.id)
        assertEquals("j", journalDeletion.id)
        assertEquals("c", associationDeletion.key.contentId)
        assertTrue(changeSet.hasMore)
        assertTrue(!defaultHasMore.hasMore)
        assertEquals(3, status.associationCount)
        assertEquals(5, purge.cutoff)
    }
}
