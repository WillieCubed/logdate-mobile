package app.logdate.client.database.dao.journals

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * A DAO for modifying and accessing associations between user-generated content and journals.
 */
@Dao
interface JournalContentDao {

    /**
     * Adds content to a journal. If the association already exists, it is ignored.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addContentToJournal(link: JournalContentEntityLink)
    
    /**
     * Gets all content IDs associated with a journal.
     */
    @Query("SELECT content_id FROM journal_content_links WHERE journal_id = :journalId")
    fun getContentForJournal(journalId: Uuid): Flow<List<Uuid>>
    
    /**
     * Gets all journal IDs that a piece of content is associated with.
     */
    @Query("SELECT journal_id FROM journal_content_links WHERE content_id = :contentId")
    fun getJournalsForContent(contentId: Uuid): Flow<List<Uuid>>
    
    /**
     * Removes the association between a piece of content and a journal.
     */
    @Query("DELETE FROM journal_content_links WHERE journal_id = :journalId AND content_id = :contentId")
    suspend fun removeContentFromJournal(journalId: Uuid, contentId: Uuid)
    
    /**
     * Removes all associations for a piece of content.
     */
    @Query("DELETE FROM journal_content_links WHERE content_id = :contentId")
    suspend fun removeContentFromAllJournals(contentId: Uuid)
    
    /**
     * Checks if a piece of content is associated with a journal.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM journal_content_links WHERE journal_id = :journalId AND content_id = :contentId)")
    suspend fun isContentInJournal(journalId: Uuid, contentId: Uuid): Boolean
}