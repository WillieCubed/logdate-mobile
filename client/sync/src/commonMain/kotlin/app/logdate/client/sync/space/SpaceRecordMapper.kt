package app.logdate.client.sync.space

import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.metadata.EntityType
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * Converts one kind of journal data to and from its `app.logdate.*` record, the form it takes in an
 * account's personal space.
 *
 * A mapper is pure: it reads nothing and stores nothing. Round-trip tests prove that
 * `fromRecord(rkeyOf(item), toRecord(item), seq)` gives back every field sync carries.
 */
interface SpaceRecordMapper<T : Any> {
    /** The kind of local data this maps, as the outbox and the download inbox name it. */
    val entityType: EntityType

    /** The record type, such as `app.logdate.journal.journal`. */
    val collection: String

    /** The record key [item] is stored under. */
    fun rkeyOf(item: T): String

    /** The record key for an outbox entry's entity ID, which is all a deletion still has. */
    fun rkeyForEntity(entityId: String): String = entityId

    /** The outbox and inbox entity ID for a record key. */
    fun entityIdOf(rkey: String): String = rkey

    /** The record for [item], including its `$type`. */
    fun toRecord(item: T): JsonObject

    /**
     * The item [record], stored under [rkey], holds. [seq] is the write's position in the space's
     * change log; it becomes the item's sync version, so newer writes always compare as newer.
     *
     * @throws IllegalArgumentException if the record can't be read as this kind of data.
     */
    fun fromRecord(
        rkey: String,
        record: JsonObject,
        seq: Long,
    ): T

    /** The content identifiers of the blobs [item]'s record refers to. Each must already be uploaded. */
    fun blobCids(item: T): List<String> = emptyList()
}

/** What uploading one kind of data needs from local storage, beyond its [SpaceRecordMapper]. */
interface SpaceLocalRecords<T : Any> {
    /** The item an outbox entry names, or null when it is no longer stored. */
    suspend fun load(entityId: String): T?

    /** The kind's existing resolver, run when a write finds the record changed elsewhere. */
    val conflictResolver: ConflictResolver<T>

    /** When [item] last changed, for the resolver. */
    fun lastUpdatedOf(item: T): Instant

    /** Records that [item] is stored as of [seq], as the kind's existing upload acknowledgement does. */
    suspend fun acknowledgeUpload(
        item: T,
        seq: Long,
        syncedAt: Instant,
    )

    /** Replaces the local copy with [item], when a conflict kept the other device's version or merged both. */
    suspend fun replaceLocal(item: T)
}

/** One kind of data the personal space carries: how to map it and how to reach it locally. */
class SpaceKind<T : Any>(
    val mapper: SpaceRecordMapper<T>,
    val local: SpaceLocalRecords<T>,
) {
    val entityType: EntityType get() = mapper.entityType
    val collection: String get() = mapper.collection
}
