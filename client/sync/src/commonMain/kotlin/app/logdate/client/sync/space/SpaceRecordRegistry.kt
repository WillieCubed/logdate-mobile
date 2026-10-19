package app.logdate.client.sync.space

import app.logdate.client.sync.metadata.EntityType
import studio.hypertext.atproto.syntax.Nsid

/**
 * The kinds of data this build syncs through the personal space: found by the outbox's entity type
 * on the way up, and by each record's type, which names the kind that owns it, on the way down.
 *
 * A kind can only be registered once its download is wired: its records are staged in the download
 * inbox, and something has to apply them. [WIRED_ENTITY_TYPES] lists the kinds whose downloaders
 * read [SpaceStagedRecords]; wire a kind's downloader before adding it there.
 */
class SpaceRecordRegistry(
    kinds: List<SpaceKind<*>>,
) {
    private val byEntityType: Map<EntityType, SpaceKind<*>>
    private val byCollection: Map<String, SpaceOwnedPart>

    init {
        kinds.forEach { kind ->
            require(kind.entityType in WIRED_ENTITY_TYPES) {
                "${kind.entityType} records can't be applied after download yet; wire its downloader to SpaceStagedRecords first"
            }
            kind.parts.forEach { part -> require(Nsid.isValid(part.collection)) { "${part.collection} isn't a record type NSID" } }
        }
        byEntityType = kinds.associateBy { it.entityType }
        require(byEntityType.size == kinds.size) { "Each kind of data can be registered only once" }
        val owned = kinds.flatMap { kind -> kind.parts.map { part -> SpaceOwnedPart(kind, part) } }
        byCollection = owned.associateBy { it.part.collection }
        require(byCollection.size == owned.size) { "Each record type can belong to only one kind of data" }
    }

    /** Whether no kind is registered, so the personal space has nothing to carry. */
    val isEmpty: Boolean get() = byEntityType.isEmpty()

    /** The registered kinds. */
    val kinds: Collection<SpaceKind<*>> get() = byEntityType.values

    /** Every registered record type. */
    val collections: Set<String> get() = byCollection.keys

    fun forEntityType(entityType: EntityType): SpaceKind<*>? = byEntityType[entityType]

    /** The kind that owns records of [collection], and which of its record types they are. */
    fun forCollection(collection: String): SpaceOwnedPart? = byCollection[collection]

    companion object {
        /** The kinds whose downloaders apply staged space records. */
        val WIRED_ENTITY_TYPES: Set<EntityType> =
            setOf(
                EntityType.JOURNAL,
                EntityType.NOTE,
                EntityType.ASSOCIATION,
                EntityType.DRAFT,
                EntityType.MOMENT,
                EntityType.PLACE,
                EntityType.PLACE_VISIT,
                EntityType.PLACE_CORRECTION,
                EntityType.PLACE_ENTRY_LINK,
                EntityType.PLACE_JOURNEY,
                EntityType.LOCATION_SAMPLES,
            )

        /** No kinds: everything keeps syncing through its own endpoints. */
        val EMPTY: SpaceRecordRegistry = SpaceRecordRegistry(emptyList())
    }
}

/** A record type and the kind that owns it. */
class SpaceOwnedPart(
    val kind: SpaceKind<*>,
    val part: SpacePart<*, *>,
) {
    val isMain: Boolean get() = part === kind.main

    /** The id of the item a record of this type stored under [rkey] belongs to. */
    fun ownerOf(rkey: String): String = canonicalRecordKey(part.ownerOf(canonicalRecordKey(rkey)))
}
