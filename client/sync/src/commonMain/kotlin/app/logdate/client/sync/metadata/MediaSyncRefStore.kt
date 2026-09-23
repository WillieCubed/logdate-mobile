package app.logdate.client.sync.metadata

import app.logdate.client.datastore.KeyValueStorage
import io.github.aakira.napier.Napier
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

    /**
     * Ties references saved before servers were recorded to [origin]. Call before switching away
     * from [origin] so they aren't mistaken for the next server's.
     */
    suspend fun claimUnscopedRefs(origin: String) {}
}

class KeyValueMediaSyncRefStore(
    private val storage: KeyValueStorage,
    private val currentOrigin: () -> String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MediaSyncRefStore {
    override suspend fun get(noteId: Uuid): MediaSyncRef? {
        val value = storage.getString(key(noteId)) ?: return null
        val ref =
            runCatching { json.decodeFromString<MediaSyncRef>(value) }
                .onFailure { Napier.w("Failed to decode media sync ref for note $noteId", it) }
                .getOrNull() ?: return null
        val origin = currentOrigin()
        val refOrigin = ref.serverOrigin ?: unscopedRefsOrigin(claimingFor = origin)
        return ref.takeIf { refOrigin == origin }
    }

    override suspend fun upsert(ref: MediaSyncRef) {
        storage.putString(key(ref.noteId), json.encodeToString(ref.copy(serverOrigin = currentOrigin())))
    }

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

    override suspend fun delete(noteId: Uuid) {
        storage.remove(key(noteId))
    }

    private fun key(noteId: Uuid): String = key(noteId.toString())

    private fun key(noteId: String): String = "sync_media_ref_$noteId"

    private companion object {
        const val UNSCOPED_REFS_ORIGIN_KEY = "sync_media_ref_unscoped_origin"
    }
}
