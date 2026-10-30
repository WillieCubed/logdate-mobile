package app.logdate.client.watch

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class WatchAssociationBinding(
    val associationId: Int,
    val nodeId: String,
    val displayName: String,
)

/** Only written after the system returns a consented association for a real Wear transport target. */
class WatchAssociationBindings(
    private val storage: KeyValueStorage,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), WatchAssociationBinding.serializer())

    suspend fun current(): Map<String, WatchAssociationBinding> =
        storage.getStringOrThrow(KEY)?.let { json.decodeFromString(serializer, it) }.orEmpty()

    suspend fun bind(binding: WatchAssociationBinding) =
        mutex.withLock {
            val existing = current().filterValues { it.associationId != binding.associationId }
            storage.putStringOrThrow(KEY, json.encodeToString(serializer, existing + (binding.nodeId to binding)))
        }

    suspend fun forget(nodeId: String) =
        mutex.withLock {
            storage.putStringOrThrow(KEY, json.encodeToString(serializer, current() - nodeId))
        }

    companion object {
        private const val KEY = "watch_companion_association_bindings_v1"
    }
}
