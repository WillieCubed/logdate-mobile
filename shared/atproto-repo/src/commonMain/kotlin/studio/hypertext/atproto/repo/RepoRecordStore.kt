package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey

/**
 * Standalone abstraction for AT Protocol repository record storage.
 *
 * Implementations are transport-agnostic and may be backed by local storage,
 * a server runtime, or another persistence adapter.
 */
public interface RepoRecordStore {
    /**
     * Returns a record by exact [recordId], or `null` when it does not exist.
     */
    public suspend fun getRecord(recordId: RepoRecordId): Result<RepoRecord?>

    /**
     * Lists records in [collection] for [repo].
     */
    public suspend fun listRecords(
        repo: AtprotoDid,
        collection: Nsid,
        limit: Int = DEFAULT_PAGE_SIZE,
        cursor: String? = null,
        reverse: Boolean = false,
    ): Result<RepoListPage>

    /**
     * Creates a new record in [collection] for [repo].
     *
     * Implementations may generate a record key when [recordKey] is `null`.
     */
    public suspend fun createRecord(
        repo: AtprotoDid,
        collection: Nsid,
        value: JsonObject,
        recordKey: RecordKey? = null,
    ): Result<RepoWriteResult>

    /**
     * Replaces or inserts the record at [recordId].
     */
    public suspend fun putRecord(
        recordId: RepoRecordId,
        value: JsonObject,
        swapRecord: String? = null,
    ): Result<RepoWriteResult>

    /**
     * Deletes the record at [recordId].
     *
     * Returns `true` when a record was deleted, or `false` when nothing existed.
     */
    public suspend fun deleteRecord(
        recordId: RepoRecordId,
        swapRecord: String? = null,
    ): Result<Boolean>

    public companion object {
        /**
         * Default record page size used by repo list operations.
         */
        public const val DEFAULT_PAGE_SIZE: Int = 50
    }
}
