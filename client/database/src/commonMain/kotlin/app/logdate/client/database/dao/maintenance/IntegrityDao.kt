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
        """,
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
        """,
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
        """,
    )
    suspend fun deleteOrphanedContentLinks(): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM pending_uploads AS pending
        LEFT JOIN journals AS journals ON pending.entityId = journals.id
        WHERE pending.ownerId = :ownerId
          AND pending.serverOrigin = :serverOrigin
          AND pending.entityType = 'JOURNAL'
          AND journals.id IS NULL
        """,
    )
    suspend fun countPendingMissingJournals(
        ownerId: String,
        serverOrigin: String,
    ): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM pending_uploads AS pending
        WHERE pending.ownerId = :ownerId
          AND pending.serverOrigin = :serverOrigin
          AND pending.entityType = 'NOTE'
          AND pending.entityId NOT IN (
            SELECT uid FROM text_notes
            UNION SELECT uid FROM image_notes
            UNION SELECT uid FROM audio_notes
            UNION SELECT uid FROM video_notes
          )
        """,
    )
    suspend fun countPendingMissingNotes(
        ownerId: String,
        serverOrigin: String,
    ): Int

    @Query(
        """
        DELETE FROM pending_uploads
        WHERE ownerId = :ownerId
          AND serverOrigin = :serverOrigin
          AND entityType = 'JOURNAL'
          AND entityId NOT IN (SELECT id FROM journals)
        """,
    )
    suspend fun deletePendingMissingJournals(
        ownerId: String,
        serverOrigin: String,
    ): Int

    @Query(
        """
        DELETE FROM pending_uploads
        WHERE ownerId = :ownerId
          AND serverOrigin = :serverOrigin
          AND entityType = 'NOTE'
          AND entityId NOT IN (
            SELECT uid FROM text_notes
            UNION SELECT uid FROM image_notes
            UNION SELECT uid FROM audio_notes
            UNION SELECT uid FROM video_notes
          )
        """,
    )
    suspend fun deletePendingMissingNotes(
        ownerId: String,
        serverOrigin: String,
    ): Int

    /**
     * Counts location rows still stamped with the placeholder identity.
     *
     * Location logging used to write the literals `user_1` and `device_1` for every row, so a
     * user's whole location history was owned by an account that does not exist. Rows written
     * before that was fixed still carry them.
     */
    @Query(
        """
        SELECT COUNT(*)
        FROM location_logs
        WHERE user_id = 'user_1' OR device_id = 'device_1'
        """,
    )
    suspend fun countPlaceholderOwnedLocations(): Int

    /**
     * Reassigns placeholder-owned location rows to the identity that actually recorded them.
     *
     * These rows were written by this person on this device -- the placeholder recorded nothing
     * about whose they were, so there is no other candidate and nothing is being guessed. Rows
     * already carrying a real identity are untouched.
     */
    @Query(
        """
        UPDATE location_logs
        SET user_id = :ownerId, device_id = :deviceId
        WHERE user_id = 'user_1' OR device_id = 'device_1'
        """,
    )
    suspend fun reassignPlaceholderOwnedLocations(
        ownerId: String,
        deviceId: String,
    ): Int
}
