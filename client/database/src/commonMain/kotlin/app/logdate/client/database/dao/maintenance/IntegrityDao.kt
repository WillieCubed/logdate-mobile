package app.logdate.client.database.dao.maintenance

import androidx.room.Dao
import androidx.room.Query

/**
 * Queries for detecting and repairing local data integrity issues.
 */
@Dao
interface IntegrityDao {
    @Query(
        """
        SELECT COUNT(*)
        FROM journal_content_links AS links
        LEFT JOIN journals AS journals ON links.journal_id = journals.id
        WHERE journals.id IS NULL
        """
    )
    suspend fun countOrphanedJournalLinks(): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM journal_content_links AS links
        LEFT JOIN text_notes AS textNotes ON links.content_id = textNotes.uid
        LEFT JOIN image_notes AS imageNotes ON links.content_id = imageNotes.uid
        LEFT JOIN audio_notes AS audioNotes ON links.content_id = audioNotes.uid
        LEFT JOIN video_notes AS videoNotes ON links.content_id = videoNotes.uid
        WHERE textNotes.uid IS NULL
          AND imageNotes.uid IS NULL
          AND audioNotes.uid IS NULL
          AND videoNotes.uid IS NULL
        """
    )
    suspend fun countOrphanedContentLinks(): Int

    @Query("DELETE FROM journal_content_links WHERE journal_id NOT IN (SELECT id FROM journals)")
    suspend fun deleteOrphanedJournalLinks(): Int

    @Query(
        """
        DELETE FROM journal_content_links
        WHERE content_id NOT IN (
            SELECT uid FROM text_notes
            UNION SELECT uid FROM image_notes
            UNION SELECT uid FROM audio_notes
            UNION SELECT uid FROM video_notes
        )
        """
    )
    suspend fun deleteOrphanedContentLinks(): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM pending_uploads AS pending
        LEFT JOIN journals AS journals ON pending.entityId = journals.id
        WHERE pending.entityType = 'JOURNAL'
          AND journals.id IS NULL
        """
    )
    suspend fun countPendingMissingJournals(): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM pending_uploads AS pending
        WHERE pending.entityType = 'NOTE'
          AND pending.entityId NOT IN (
            SELECT uid FROM text_notes
            UNION SELECT uid FROM image_notes
            UNION SELECT uid FROM audio_notes
            UNION SELECT uid FROM video_notes
          )
        """
    )
    suspend fun countPendingMissingNotes(): Int

    @Query(
        """
        DELETE FROM pending_uploads
        WHERE entityType = 'JOURNAL'
          AND entityId NOT IN (SELECT id FROM journals)
        """
    )
    suspend fun deletePendingMissingJournals(): Int

    @Query(
        """
        DELETE FROM pending_uploads
        WHERE entityType = 'NOTE'
          AND entityId NOT IN (
            SELECT uid FROM text_notes
            UNION SELECT uid FROM image_notes
            UNION SELECT uid FROM audio_notes
            UNION SELECT uid FROM video_notes
          )
        """
    )
    suspend fun deletePendingMissingNotes(): Int
}
