package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.Sealed

/** The condition a write or delete must meet to go ahead. */
public sealed interface RecordSwap {
    /** Apply the change whatever the record currently holds. */
    public data object Unconditional : RecordSwap

    /** Apply the change only if no record exists under the key. */
    public data object Absent : RecordSwap

    /** Apply the change only if the record currently has this [cid]. */
    public data class Matching(
        val cid: String,
    ) : RecordSwap
}

/**
 * A record as a data home holds it.
 *
 * @property rkey the record's key in the space.
 * @property cid the content identifier of the stored record; pass it back as [RecordSwap.Matching].
 * @property seq the position of the write in the space's change log.
 * @property record the sealed envelope.
 */
public data class StoredRecord(
    val rkey: String,
    val cid: String,
    val seq: Long,
    val record: Sealed,
)

/**
 * One entry in a space's change log.
 *
 * @property rkey the record that changed.
 * @property seq the position of the change in the log.
 * @property deleted whether the change removed the record.
 * @property cid the record's content identifier after the change; null when [deleted].
 * @property record the sealed envelope after the change; null when [deleted].
 */
public data class RecordChange(
    val rkey: String,
    val seq: Long,
    val deleted: Boolean,
    val cid: String?,
    val record: Sealed?,
)

/**
 * One page of a space's change log.
 *
 * @property changes the changes in this page, oldest first.
 * @property cursor the sequence number to pass as `since` to read the next page.
 * @property hasMore whether more changes follow this page.
 */
public data class ChangePage(
    val changes: List<RecordChange>,
    val cursor: Long,
    val hasMore: Boolean,
)

/** Thrown when a [RecordSwap] condition isn't met because the record changed since it was read. */
public class DataHomeConflictException(
    message: String,
) : Exception(message)

/**
 * Thrown by [DataHome.listChanges] when deletions after its `since` cursor have been purged, so paging
 * on would miss them. List again from zero and replace what the device holds with the result.
 */
public class DataHomeCursorTooOldException(
    message: String,
) : Exception(message)

/**
 * Where a space's sealed records live: LogDate Cloud, or a self-hosted LogDate server. Every app
 * reaches a data home through this interface, and a data home only ever sees sealed envelopes and
 * encrypted blobs.
 *
 * To export a space, page [listChanges] from zero until [ChangePage.hasMore] is false, keeping the
 * latest change for each key, then read every blob the surviving records reference with [getBlob].
 */
public interface DataHome {
    /**
     * Creates or replaces the record at [rkey].
     *
     * @param blobs content identifiers of the uploaded blobs the record references.
     * @param swap the condition the write must meet.
     * @throws DataHomeConflictException if [swap] isn't met.
     */
    public suspend fun putRecord(
        space: SpaceRef,
        rkey: String,
        record: Sealed,
        blobs: List<String> = emptyList(),
        swap: RecordSwap = RecordSwap.Unconditional,
    ): StoredRecord

    /**
     * Deletes the record at [rkey] and returns the sequence number of the deletion. Deleting a key
     * with no live record changes nothing and returns the latest sequence number.
     *
     * @param swap the condition the delete must meet: [RecordSwap.Unconditional] or [RecordSwap.Matching].
     * @throws DataHomeConflictException if [swap] isn't met.
     * @throws IllegalArgumentException if [swap] is [RecordSwap.Absent], which no delete can meet.
     */
    public suspend fun deleteRecord(
        space: SpaceRef,
        rkey: String,
        swap: RecordSwap = RecordSwap.Unconditional,
    ): Long

    /** Reads the record at [rkey], or returns null if there isn't one. */
    public suspend fun getRecord(
        space: SpaceRef,
        rkey: String,
    ): StoredRecord?

    /**
     * Reads up to [limit] changes made after sequence number [since], oldest first.
     *
     * @throws DataHomeCursorTooOldException if deletions after [since] were purged; list again from zero.
     */
    public suspend fun listChanges(
        space: SpaceRef,
        since: Long,
        limit: Int = 200,
    ): ChangePage

    /** Stores encrypted [bytes] and returns their content identifier. */
    public suspend fun uploadBlob(
        space: SpaceRef,
        bytes: ByteArray,
    ): String

    /** Reads the blob with content identifier [cid], or returns null if there isn't one. */
    public suspend fun getBlob(
        space: SpaceRef,
        cid: String,
    ): ByteArray?
}
