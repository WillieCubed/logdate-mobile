package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Data Access Object for full-text search operations.
 *
 * Uses FTS5 (Full-Text Search) virtual table for fast text search across entries.
 * Supports fuzzy matching, relevance ranking, and boolean operators.
 *
 * Note: @SkipQueryVerification is used because Room cannot validate FTS5 virtual tables
 * at compile time. The entries_fts table is created via database migration.
 */
@Dao
@SkipQueryVerification
interface SearchDao {

    /**
     * Searches all entries using FTS5 MATCH operator.
     *
     * Returns results ordered by relevance (rank) and then by date (newest first).
     * The query supports:
     * - Simple text search: "hello world"
     * - Boolean operators: "hello AND world", "hello OR world", "hello NOT world"
     * - Phrase search: "\"hello world\""
     * - Prefix search: "hel*"
     *
     * @param query The search query (will be used in FTS5 MATCH clause)
     * @return List of search results ordered by relevance and date
     */
    @Query("""
        SELECT uid, content, created, contentType
        FROM entries_fts
        WHERE entries_fts MATCH :query
        ORDER BY rank, created DESC
    """)
    suspend fun search(query: String): List<SearchResultEntity>

    /**
     * Searches entries with a limit on the number of results.
     *
     * Useful for showing a limited number of search suggestions.
     *
     * @param query The search query
     * @param limit Maximum number of results to return
     * @return List of limited search results
     */
    @Query("""
        SELECT uid, content, created, contentType
        FROM entries_fts
        WHERE entries_fts MATCH :query
        ORDER BY rank, created DESC
        LIMIT :limit
    """)
    suspend fun searchWithLimit(query: String, limit: Int): List<SearchResultEntity>

    /**
     * Searches entries with highlighted snippets.
     *
     * Uses FTS5's snippet() function to return excerpts with search term highlighting.
     * Markers: '[' and ']' surround matching terms in the snippet.
     *
     * @param query The search query
     * @return List of search results with highlighted snippets
     */
    @Query("""
        SELECT
            uid,
            snippet(entries_fts, 1, '[', ']', '...', 32) as content,
            created,
            contentType
        FROM entries_fts
        WHERE entries_fts MATCH :query
        ORDER BY rank, created DESC
    """)
    suspend fun searchWithSnippets(query: String): List<SearchResultEntity>
}

/**
 * Entity representing a search result from the FTS table.
 *
 * This is a simplified entity that contains just the essential search result information.
 * The actual full entity can be loaded from the source tables using the uid.
 */
data class SearchResultEntity(
    val uid: String,
    val content: String,
    val created: Long,
    val contentType: String,
) {
    /**
     * Converts the string UID to a Uuid.
     */
    fun getUuid(): Uuid = Uuid.parse(uid)

    /**
     * Converts the timestamp to an Instant.
     */
    fun getCreatedInstant(): Instant = Instant.fromEpochMilliseconds(created)

    /**
     * Returns true if this is a text note result.
     */
    fun isTextNote(): Boolean = contentType == "text_note"

    /**
     * Returns true if this is a transcription result.
     */
    fun isTranscription(): Boolean = contentType == "transcription"
}
