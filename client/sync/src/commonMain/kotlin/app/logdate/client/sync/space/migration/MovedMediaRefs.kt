package app.logdate.client.sync.space.migration

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.space.SpaceMediaReference
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Where one moved media file's references live: a note's own file, or an asset of a draft block or
 * journal cover, which the sync engine keys by owner, block and asset kind.
 */
internal sealed interface MediaOwner {
    val key: String

    /** The item whose record refers to the file. */
    val item: Pair<EntityType, String>

    data class Note(
        val noteId: Uuid,
    ) : MediaOwner {
        override val key: String get() = noteMediaKey(noteId)
        override val item: Pair<EntityType, String> get() = EntityType.NOTE to noteId.toString()
    }

    data class Asset(
        val type: EntityType,
        val ownerId: Uuid,
        val blockId: Uuid,
        val kind: String,
    ) : MediaOwner {
        override val key: String get() = assetMediaKey(ownerId, blockId, kind)
        override val item: Pair<EntityType, String> get() = type to ownerId.toString()
    }
}

/**
 * Hands the move's media references to the sync engine's own store once the account is in its
 * personal space, so editing a moved item doesn't upload its file again; and gives the legacy ones
 * back after switching back, so legacy sync reuses its own copies.
 */
internal class MovedMediaRefs(
    private val checkpoints: SpaceMigrationCheckpoints,
    private val store: MediaSyncRefStore,
    private val scope: UploadScope,
) {
    suspend fun adoptSpaceReferences(owners: List<MediaOwner>) {
        for (owner in owners) {
            val moved = checkpoints.media(scope, owner.key) ?: continue
            if (SpaceMediaReference.parse(moved.spaceReference) == null) continue
            write(owner, moved.localUri.orEmpty(), moved.spaceReference, SpaceMediaReference.parse(moved.spaceReference)?.cid.orEmpty())
        }
    }

    /**
     * Restores each file's legacy reference. A file that only ever lived in the personal space gets a
     * blank one, so legacy sync uploads it from this device.
     */
    suspend fun restoreLegacyReferences(owners: List<MediaOwner>) {
        for (owner in owners) {
            val moved = checkpoints.media(scope, owner.key)
            val current = read(owner)
            val spaceOnly = current != null && SpaceMediaReference.parse(current.remoteUrl) != null
            if (moved == null && !spaceOnly) continue
            val legacyUrl = moved?.legacyRemoteUrl
            write(owner, moved?.localUri ?: current?.localUri.orEmpty(), legacyUrl.orEmpty(), moved?.legacyMediaId.orEmpty())
        }
    }

    private suspend fun read(owner: MediaOwner): MediaSyncRef? =
        when (owner) {
            is MediaOwner.Note -> store.get(owner.noteId)
            is MediaOwner.Asset -> store.getDraftAsset(owner.ownerId, owner.blockId, owner.kind)
        }

    private suspend fun write(
        owner: MediaOwner,
        localUri: String,
        remoteUrl: String,
        mediaId: String,
    ) {
        val noteId =
            when (owner) {
                is MediaOwner.Note -> owner.noteId
                is MediaOwner.Asset -> owner.blockId
            }
        val ref =
            MediaSyncRef(
                noteId = noteId.toString(),
                localUri = localUri,
                remoteUrl = remoteUrl,
                mediaId = mediaId,
                updatedAt = Clock.System.now().toEpochMilliseconds(),
                serverOrigin = scope.serverOrigin,
                ownerId = scope.ownerId,
            )
        when (owner) {
            is MediaOwner.Note -> store.upsert(ref)
            is MediaOwner.Asset -> store.upsertDraftAsset(owner.ownerId, owner.blockId, owner.kind, ref)
        }
    }
}
