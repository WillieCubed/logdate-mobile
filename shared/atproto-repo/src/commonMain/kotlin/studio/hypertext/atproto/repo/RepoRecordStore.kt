package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey

/**
 * One write in a [RepoRecordStore.putRecords] batch.
 *
 * [swapRecord] mirrors [RepoRecordStore.putRecord]'s optional compare-and-swap: when non-null,
 * the write is only applied if the record's current CID equals [swapRecord]. Unlike the
 * single-record path, a mismatch here must fail the whole batch rather than commit the records
 * around it - a batch is one atomic commit, so there is no partially-applied state to leave
 * behind.
 */
public data class BatchRecordWrite(
    val recordId: RepoRecordId,
    val value: JsonObject,
    val swapRecord: String? = null,
)

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
     * Returns the records at [recordIds], in the same order, with `null` for any that are absent.
     *
     * Callers reading a page of a change feed want many records from one repo at once. Opening a
     * repo is the expensive part of a read, so implementations should open it once for the whole
     * batch. The default here is the naive loop, which is correct but costs one open per record.
     */
    public suspend fun getRecords(recordIds: List<RepoRecordId>): Result<List<RepoRecord?>> =
        runCatching { recordIds.map { getRecord(it).getOrThrow() } }

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
     * Replaces or inserts every record in [records], in one write.
     *
     * A single put reads the whole tree, rebuilds it and writes a new head, so applying a list one
     * record at a time costs that entire cycle per record - which is what made an endpoint that
     * accepts a list no cheaper than the caller sending them one by one. Implementations should
     * open the repo once and leave one commit behind. The default is the naive loop, which is
     * correct but has exactly the cost this exists to avoid.
     */
    public suspend fun putRecords(records: List<BatchRecordWrite>): Result<List<RepoWriteResult>> =
        runCatching { records.map { putRecord(it.recordId, it.value, it.swapRecord).getOrThrow() } }

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
