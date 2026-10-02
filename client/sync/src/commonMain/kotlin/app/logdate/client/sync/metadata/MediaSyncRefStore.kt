package app.logdate.client.sync.metadata

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.cloud.CloudRequestBinding
import io.github.aakira.napier.Napier
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Where a note's media file lives on a server, so it isn't uploaded again.
 *
 * @property serverOrigin the server the file was uploaded to or downloaded from. `null` on
 *   references saved before this was recorded.
 */
@Serializable
data class MediaSyncRef(
    val noteId: String,
    val localUri: String,
    val remoteUrl: String,
    val mediaId: String,
    val updatedAt: Long,
    val serverOrigin: String? = null,
    val ownerId: String? = null,
)

/**
 * Remembers where each note's media file lives on the server the app is connected to.
 *
 * A reference only answers for the server it was made on. Reusing one made elsewhere would point
 * the current server at another server's copy of the file, which disappears with that server.
 */
interface MediaSyncRefStore {
    /** The note's reference on the current server, or `null` if it has none there. */
    suspend fun get(noteId: Uuid): MediaSyncRef?

    /** Saves [ref] as belonging to the current server. */
    suspend fun upsert(ref: MediaSyncRef)

    suspend fun delete(noteId: Uuid)

    suspend fun deleteScoped(
        noteId: Uuid,
        scope: UploadScope?,
    ) = delete(noteId)

    suspend fun getDraftAsset(
        draftId: Uuid,
        blockId: Uuid,
        kind: String,
    ): MediaSyncRef? = null

    suspend fun upsertDraftAsset(
        draftId: Uuid,
        blockId: Uuid,
        kind: String,
        ref: MediaSyncRef,
    ) {}

    /**
     * Ties references saved before servers were recorded to [origin]. Call before switching away
     * from [origin] so they aren't mistaken for the next server's.
     */
    suspend fun claimUnscopedRefs(origin: String) {}
}

class KeyValueMediaSyncRefStore(
    private val storage: KeyValueStorage,
    private val currentOrigin: () -> String,
    private val currentOwnerId: () -> String = { "" },
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MediaSyncRefStore {
    override suspend fun get(noteId: Uuid): MediaSyncRef? = read(noteId.toString(), selectedScope())

    override suspend fun getDraftAsset(
        draftId: Uuid,
        blockId: Uuid,
        kind: String,
    ): MediaSyncRef? = read("draft:$draftId:$blockId:$kind", selectedScope())

    override suspend fun upsertDraftAsset(
        draftId: Uuid,
        blockId: Uuid,
        kind: String,
        ref: MediaSyncRef,
    ) {
        write("draft:$draftId:$blockId:$kind", ref)
    }

    private suspend fun selectedScope(): UploadScope {
        val binding = currentCoroutineContext()[CloudRequestBinding]
        return if (binding == null) {
            UploadScope(currentOwnerId(), currentOrigin())
        } else {
            UploadScope(binding.session.accountId, binding.location.origin)
        }
    }

    private suspend fun write(
        identity: String,
        ref: MediaSyncRef,
    ) {
        val selected = selectedScope()
        val captured = UploadScope(ref.ownerId ?: selected.ownerId, ref.serverOrigin ?: selected.serverOrigin)
        storage.putString(
            scopedKey(identity, captured),
            json.encodeToString(
                ref.copy(
                    serverOrigin = captured.serverOrigin,
                    ownerId = captured.ownerId,
                ),
            ),
        )
    }

    private suspend fun read(
        identity: String,
        selected: UploadScope,
    ): MediaSyncRef? {
        val value = storage.getString(scopedKey(identity, selected)) ?: storage.getString(key(identity)) ?: return null
        return decodeForScope(value, selected)
    }

    private suspend fun decodeForScope(
        value: String,
        selected: UploadScope,
    ): MediaSyncRef? {
        val ref =
            runCatching { json.decodeFromString<MediaSyncRef>(value) }
                .onFailure { Napier.w("Failed to decode media sync reference") }
                .getOrNull() ?: return null
        val origin = ref.serverOrigin ?: unscopedRefsOrigin(claimingFor = selected.serverOrigin)
        return ref.takeIf {
            origin == selected.serverOrigin &&
                (ref.ownerId == selected.ownerId || (ref.ownerId == null && selected.ownerId.isBlank()))
        }
    }

    override suspend fun upsert(ref: MediaSyncRef) = write(ref.noteId, ref)

    override suspend fun claimUnscopedRefs(origin: String) {
        unscopedRefsOrigin(claimingFor = origin)
    }

    /**
     * The server that references without a recorded server belong to. The first server to ask
     * claims them: before servers were recorded, every reference came from the one server the app
     * had used.
     */
    private suspend fun unscopedRefsOrigin(claimingFor: String): String =
        storage.getString(UNSCOPED_REFS_ORIGIN_KEY)
            ?: claimingFor.also { storage.putString(UNSCOPED_REFS_ORIGIN_KEY, it) }

    override suspend fun delete(noteId: Uuid) = deleteScoped(noteId, selectedScope())

    override suspend fun deleteScoped(
        noteId: Uuid,
        scope: UploadScope?,
    ) {
        val captured = scope ?: selectedScope()
        val identity = noteId.toString()
        storage.remove(scopedKey(identity, captured))
        val legacy = storage.getString(key(identity))
        if (legacy != null && decodeForScope(legacy, captured) != null) storage.remove(key(identity))
    }

    private fun key(identity: String): String = "sync_media_ref_$identity"

    private fun scopedKey(
        identity: String,
        scope: UploadScope,
    ): String = "sync_media_ref_v2_${scope.ownerId.length}:${scope.ownerId}${scope.serverOrigin.length}:${scope.serverOrigin}:$identity"

    private companion object {
        const val UNSCOPED_REFS_ORIGIN_KEY = "sync_media_ref_unscoped_origin"
    }
}
