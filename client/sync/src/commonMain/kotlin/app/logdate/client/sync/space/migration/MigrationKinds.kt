package app.logdate.client.sync.space.migration

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceKind
import app.logdate.client.sync.space.canonicalRecordKey
import app.logdate.shared.model.sync.LegacyRecordKinds

/**
 * One kind of data the move uploads: the registered [SpaceKind] that maps it, every item of it this
 * device holds, and what to do to an item before it is mapped, such as moving its media.
 *
 * @param ids every item id of this kind on this device, as the kind names them.
 * @param prepare the item as the personal space holds it: with its media moved there.
 */
internal class MigrationKind(
    val kind: SpaceKind<Any>,
    val dataKind: JournalDataKind,
    val ids: suspend () -> Collection<String>,
    val prepare: suspend (Any) -> Any = { it },
) {
    val entityType: EntityType get() = kind.entityType

    /** Every item id, in the order the move uploads and resumes them. */
    suspend fun orderedIds(): List<String> = ids().map(::canonicalRecordKey).distinct().sorted()
}

/**
 * One kind legacy storage keeps, and how to compare it with this device: the ids this device holds
 * of it, and the changes legacy storage hasn't seen yet because they wait to upload.
 *
 * @param legacyKind the kind's name in the legacy inventory, from [LegacyRecordKinds].
 * @param localIds the ids this device holds, written as the legacy inventory writes them.
 * @param waitingCreates ids this device created or changed that wait to upload.
 * @param waitingDeletes ids this device deleted whose deletion waits to upload.
 * @param syncedHere whether this device syncs this kind at all.
 */
internal class LegacyKindCheck(
    val legacyKind: String,
    val dataKind: JournalDataKind,
    val localIds: suspend () -> Set<String>,
    val waitingCreates: suspend () -> Set<String> = { emptySet() },
    val waitingDeletes: suspend () -> Set<String> = { emptySet() },
    val syncedHere: suspend () -> Boolean = { true },
)

/** The legacy id of a membership the outbox names `<journal id>::<entry id>`. */
internal fun legacyMembershipId(pendingId: String): String = canonicalRecordKey(pendingId).replace("::", ":")

/** Which kind of data each entity type is, for reporting. */
internal fun EntityType.dataKind(): JournalDataKind =
    when (this) {
        EntityType.JOURNAL, EntityType.JOURNAL_MERGE -> JournalDataKind.JOURNALS
        EntityType.NOTE -> JournalDataKind.ENTRIES
        EntityType.ASSOCIATION -> JournalDataKind.MEMBERSHIPS
        EntityType.DRAFT -> JournalDataKind.DRAFTS
        EntityType.MOMENT -> JournalDataKind.MOMENTS
        EntityType.MEDIA -> JournalDataKind.MEDIA
        else -> JournalDataKind.LOCATION_HISTORY
    }
