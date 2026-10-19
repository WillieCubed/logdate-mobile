package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableDraftRepository
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.LastWriteWinsResolver
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceKind
import app.logdate.client.sync.space.SpaceLocalRecords
import app.logdate.client.sync.space.SpaceMediaResolver
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Editor drafts as a kind of the personal space: one `app.logdate.journal.draft` record each.
 *
 * Not registered in the personal space yet: the draft download has to read staged space records
 * first (see `SpaceRecordRegistry.WIRED_ENTITY_TYPES`).
 *
 * @param drafts the stored drafts. Drafts from another device are written without queueing another
 *   upload, so this must also be a [SyncableDraftRepository].
 * @param resolver decides between this device's draft and another device's when both changed.
 * @param media connects each block's files with the blobs in the personal space.
 */
fun draftSpaceKind(
    drafts: JournalRepository,
    resolver: ConflictResolver<SpaceDraft> = LastWriteWinsResolver(),
    media: SpaceMediaResolver = SpaceMediaResolver.Default,
): SpaceKind<SpaceDraft> =
    SpaceKind.single(
        entityType = EntityType.DRAFT,
        mapper = DraftRecordMapper(media),
        local = StoredDrafts(drafts, requireNotNull(drafts as? SyncableDraftRepository) { "Drafts can't be applied from sync" }, resolver),
        withSyncVersion = { draft, version -> draft.copy(syncVersion = version) },
    )

private class StoredDrafts(
    private val drafts: JournalRepository,
    private val synced: SyncableDraftRepository,
    override val conflictResolver: ConflictResolver<SpaceDraft>,
) : SpaceLocalRecords<SpaceDraft> {
    override suspend fun load(entityId: String): SpaceDraft? =
        Uuid.parseOrNull(entityId)?.let { drafts.getDraft(it) }?.let { SpaceDraft(it) }

    override fun lastUpdatedOf(item: SpaceDraft): Instant = item.draft.lastModifiedAt

    override suspend fun acknowledgeUpload(
        item: SpaceDraft,
        seq: Long,
        syncedAt: Instant,
    ) = Unit

    override suspend fun replaceLocal(item: SpaceDraft) {
        synced.saveDraftFromSync(item.draft)
    }
}
