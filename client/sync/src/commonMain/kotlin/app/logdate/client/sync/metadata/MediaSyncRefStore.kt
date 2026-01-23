package app.logdate.client.sync.metadata

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.aakira.napier.Napier
import app.logdate.client.datastore.KeyValueStorage

@Serializable
data class MediaSyncRef(
    val noteId: String,
    val localUri: String,
    val remoteUrl: String,
    val mediaId: String,
    val updatedAt: Long
)

interface MediaSyncRefStore {
    suspend fun get(noteId: Uuid): MediaSyncRef?
    suspend fun upsert(ref: MediaSyncRef)
    suspend fun delete(noteId: Uuid)
}

class KeyValueMediaSyncRefStore(
    private val storage: KeyValueStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : MediaSyncRefStore {

    override suspend fun get(noteId: Uuid): MediaSyncRef? {
        val value = storage.getString(key(noteId)) ?: return null
        return runCatching { json.decodeFromString<MediaSyncRef>(value) }
            .onFailure { Napier.w("Failed to decode media sync ref for note $noteId", it) }
            .getOrNull()
    }

    override suspend fun upsert(ref: MediaSyncRef) {
        storage.putString(key(ref.noteId), json.encodeToString(ref))
    }

    override suspend fun delete(noteId: Uuid) {
        storage.remove(key(noteId))
    }

    private fun key(noteId: Uuid): String = key(noteId.toString())

    private fun key(noteId: String): String = "sync_media_ref_${noteId}"
}
