package app.logdate.feature.core.settings.account.move

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.shared.model.ServerDescriptor
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A server an account is moving from or to. */
@Serializable
data class MoveEndpoint(
    val origin: String,
    val descriptor: ServerDescriptor?,
) {
    /** The server's own name for itself, or its address when it gave none. */
    val name: String get() = descriptor?.displayName ?: origin.substringAfter("://").trimEnd('/')
}

/**
 * A move in progress, kept so it survives the app closing midway.
 *
 * @property committedAtMillis when the app switched to [to]; upload failures from before then
 *   belong to the old server
 * @property remoteOnlyMedia how many photos, videos and voice notes exist only on [from], so
 *   deleting the account there would lose them
 */
@Serializable
data class ServerMoveRecord(
    val from: MoveEndpoint,
    val to: MoveEndpoint,
    val phase: Phase,
    val uploadTotal: Int = 0,
    val committedAtMillis: Long = 0,
    val remoteOnlyMedia: Int = 0,
) {
    enum class Phase {
        /** Switching servers; if the app stopped here, the switch is finished on next start. */
        SWITCHING,

        /** Switched; this device's journal is uploading to [to]. */
        UPLOADING,
    }
}

class ServerMoveStore(
    private val storage: KeyValueStorage,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun load(): ServerMoveRecord? {
        val value = storage.getString(KEY) ?: return null
        return runCatching { json.decodeFromString<ServerMoveRecord>(value) }
            .onFailure { Napier.e("Could not read the saved server move", it) }
            .getOrNull()
    }

    suspend fun save(record: ServerMoveRecord) = storage.putString(KEY, json.encodeToString(record))

    suspend fun clear() = storage.remove(KEY)

    private companion object {
        const val KEY = "account_server_move"
    }
}
