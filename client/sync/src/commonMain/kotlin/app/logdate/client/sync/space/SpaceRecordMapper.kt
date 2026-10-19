package app.logdate.client.sync.space

import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.metadata.EntityType
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Converts one record type, such as `app.logdate.journal.entry`, to and from the value LogDate
 * keeps for it.
 *
 * A mapper is pure: it reads nothing and stores nothing. Round-trip tests prove that
 * `fromRecord(rkeyOf(value), toRecord(value))` gives back every field sync carries. A value the
 * record can't hold, or a record that can't become LogDate data, throws
 * [SpaceRecordMappingException]; the record-value helpers in this package write numbers, dates and
 * keys the way every kind must.
 */
interface SpaceRecordMapper<P> {
    /** The record type, such as `app.logdate.journal.entry`. */
    val collection: String

    /** The record key [value] is stored under, in lowercase. */
    fun rkeyOf(value: P): String

    /** The record for [value], including its `$type`. */
    fun toRecord(value: P): JsonObject

    /** The value [record], stored under [rkey], holds. */
    fun fromRecord(
        rkey: String,
        record: JsonObject,
    ): P

    /** The content identifiers of the blobs [value]'s record refers to. Each must already be uploaded. */
    fun blobCidsOf(value: P): List<String> = emptyList()
}

/**
 * One record type a kind of data is stored as.
 *
 * Most kinds are one record per item. A kind can own more: a note is an `app.logdate.journal.entry`
 * plus, for a transcribed recording, an `app.logdate.journal.transcript` keyed `<entry>:<block>`.
 * Each type's records name the item they belong to through their keys.
 *
 * @param recordsOf the item's values of this type: exactly one for the kind's main record, any
 *   number for the others.
 * @param ownerOf the id of the item a record of this type belongs to, in lowercase, read from the
 *   record's key alone, because a deletion carries nothing else.
 * @param rkeysOf every key the item's records of this type can have, so deleting the item deletes
 *   them all and an upload can delete the ones the item no longer has.
 */
class SpacePart<T : Any, P>(
    val mapper: SpaceRecordMapper<P>,
    val recordsOf: (item: T) -> List<P>,
    val ownerOf: (rkey: String) -> String,
    val rkeysOf: (entityId: String) -> List<String>,
) {
    val collection: String get() = mapper.collection

    companion object {
        /** A kind's main record: one per item, keyed by the item's own id. */
        fun <T : Any> main(mapper: SpaceRecordMapper<T>): SpacePart<T, T> =
            SpacePart(
                mapper = mapper,
                recordsOf = { listOf(it) },
                ownerOf = ::canonicalRecordKey,
                rkeysOf = { listOf(canonicalRecordKey(it)) },
            )
    }
}

/** A record of one item that changed in the space since this device last applied the item. */
sealed interface SpacePartChange<out P> {
    val rkey: String

    /** The record's position in the space's change log. */
    val seq: Long

    class Written<P>(
        override val rkey: String,
        val value: P,
        override val seq: Long,
    ) : SpacePartChange<P>

    class Deleted(
        override val rkey: String,
        override val seq: Long,
    ) : SpacePartChange<Nothing>
}

/** Every record of one item that changed, by record type, for [SpaceKind.assemble]. */
class SpaceItemChanges<T : Any> internal constructor(
    val entityId: String,
    private val byCollection: Map<String, List<SpacePartChange<*>>>,
) {
    /** The changed records of [part]'s type, oldest first. */
    fun <P> of(part: SpacePart<T, P>): List<SpacePartChange<P>> {
        @Suppress("UNCHECKED_CAST")
        return byCollection[part.collection].orEmpty() as List<SpacePartChange<P>>
    }

    /** The newest version among the changed records. */
    val latestSeq: Long get() = byCollection.values.flatten().maxOf { it.seq }
}

/** What uploading and applying one kind of data needs from local storage. */
interface SpaceLocalRecords<T : Any> {
    /** The item an outbox entry names, or null when it is no longer stored. */
    suspend fun load(entityId: String): T?

    /** The kind's existing resolver, run when a write finds the item changed elsewhere. */
    val conflictResolver: ConflictResolver<T>

    /** When [item] last changed, for the resolver. */
    fun lastUpdatedOf(item: T): Instant

    /** Records that [item] is stored as of [seq], as the kind's existing upload acknowledgement does. */
    suspend fun acknowledgeUpload(
        item: T,
        seq: Long,
        syncedAt: Instant,
    )

    /**
     * Makes [item] the local copy, storing it when this device has none: when a conflict kept the
     * other device's version or merged both, or a deletion here met a newer edit made elsewhere.
     */
    suspend fun replaceLocal(item: T)
}

/**
 * One kind of data the personal space carries: the record types it is stored as, how its items
 * split into them and join back together, and how to reach it locally.
 *
 * @param main the record that is the item itself: deleting it deletes the item.
 * @param others any further record types the kind owns.
 * @param isItemId whether a string is an id this kind's items can have, so a record whose key names
 *   anything else is kept as unreadable. LogDate ids by default.
 * @param withSyncVersion the item with [Long] as its sync version, the newest change-log position of
 *   its records, so a newer write always compares as newer.
 * @param assemble builds the item from its changed records on top of the local copy, for records
 *   that didn't change. Returns null while it can't be built yet, such as a transcript whose entry
 *   hasn't arrived and isn't stored here.
 */
class SpaceKind<T : Any>(
    val entityType: EntityType,
    val main: SpacePart<T, *>,
    val local: SpaceLocalRecords<T>,
    val withSyncVersion: (T, Long) -> T,
    val others: List<SpacePart<T, *>> = emptyList(),
    val isItemId: (String) -> Boolean = { Uuid.parseOrNull(it) != null },
    private val assemble: (changes: SpaceItemChanges<T>, local: T?) -> T?,
) {
    /** Every record type the kind owns, its main record first. */
    val parts: List<SpacePart<T, *>> = listOf(main) + others

    internal fun assemble(
        changes: SpaceItemChanges<T>,
        local: T?,
    ): T? = assemble.invoke(changes, local)?.let { withSyncVersion(it, changes.latestSeq) }

    companion object {
        /** A kind stored as one record per item. */
        fun <T : Any> single(
            entityType: EntityType,
            mapper: SpaceRecordMapper<T>,
            local: SpaceLocalRecords<T>,
            withSyncVersion: (T, Long) -> T,
        ): SpaceKind<T> {
            val main = SpacePart.main(mapper)
            return SpaceKind(entityType, main, local, withSyncVersion) { changes, _ ->
                (changes.of(main).lastOrNull() as? SpacePartChange.Written)?.value
            }
        }
    }
}
