package app.logdate.client.sync.space

import app.logdate.client.sync.metadata.EntityType
import studio.hypertext.atproto.syntax.Nsid

/**
 * The kinds of data this build syncs through the personal space, found by the outbox's entity type
 * on the way up and by the record's collection on the way down.
 *
 * A kind can only be registered once its download is wired: the download inbox stages a kind's
 * records under its entity type, and something has to apply them. [WIRED_ENTITY_TYPES] lists the
 * kinds whose downloaders read staged space records; wire a kind's downloader to
 * [SpaceStagedRecords] before adding it there.
 */
class SpaceRecordRegistry(
    kinds: List<SpaceKind<*>>,
) {
    private val byEntityType: Map<EntityType, SpaceKind<*>>
    private val byCollection: Map<String, SpaceKind<*>>

    init {
        kinds.forEach { kind ->
            require(Nsid.isValid(kind.collection)) { "${kind.collection} isn't a record type NSID" }
            require(kind.entityType in WIRED_ENTITY_TYPES) {
                "${kind.entityType} records can't be applied after download yet; wire its downloader to SpaceStagedRecords first"
            }
        }
        byEntityType = kinds.associateBy { it.entityType }
        byCollection = kinds.associateBy { it.collection }
        require(byEntityType.size == kinds.size) { "Each kind of data can have only one mapper" }
        require(byCollection.size == kinds.size) { "Each record type can belong to only one kind of data" }
    }

    /** Whether no kind is registered, so the personal space has nothing to carry. */
    val isEmpty: Boolean get() = byEntityType.isEmpty()

    /** The registered kinds. */
    val kinds: Collection<SpaceKind<*>> get() = byEntityType.values

    fun forEntityType(entityType: EntityType): SpaceKind<*>? = byEntityType[entityType]

    fun forCollection(collection: String): SpaceKind<*>? = byCollection[collection]

    companion object {
        /** The kinds whose downloaders apply staged space records. */
        val WIRED_ENTITY_TYPES: Set<EntityType> = setOf(EntityType.JOURNAL, EntityType.NOTE)

        /** No kinds: everything keeps syncing through its own endpoints. */
        val EMPTY: SpaceRecordRegistry = SpaceRecordRegistry(emptyList())
    }
}
