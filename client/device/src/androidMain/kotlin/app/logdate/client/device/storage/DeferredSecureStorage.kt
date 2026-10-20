package app.logdate.client.device.storage

import android.content.Context
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** A failed open remains retryable and never turns unknown identity material into an empty store. */
internal class DeferredSecureStorage(
    private val factory: () -> SecureStorage,
) : SecureStorage {
    private val lock = Any()
    private val ready = MutableStateFlow<SecureStorage?>(null)

    private fun storage(): SecureStorage = synchronized(lock) { ready.value ?: factory().also { ready.value = it } }

    override suspend fun getString(key: String): String? = storage().getString(key)

    override suspend fun putString(
        key: String,
        value: String,
    ) = storage().putString(key, value)

    override suspend fun remove(key: String) = storage().remove(key)

    override suspend fun clear() = storage().clear()

    override suspend fun encrypt(data: ByteArray): ByteArray = storage().encrypt(data)

    override suspend fun decrypt(data: ByteArray): ByteArray? = storage().decrypt(data)

    override fun observeString(key: String): Flow<String?> = observe { it.observeString(key) }

    override fun observeAll(): Flow<Map<String, String>> = observe { it.observeAll() }

    private fun <T> observe(updates: (SecureStorage) -> Flow<T>): Flow<T> =
        flow {
            try {
                storage()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Napier.w("Secure storage observation is waiting for a successful storage retry")
            }
            // Foreground reads and persistence retry opening the store. Until one succeeds, an
            // observer waits without emitting a false missing-key or signed-out value.
            emitAll(updates(ready.filterNotNull().first()))
        }
}

fun createAndroidSecureStorage(context: Context): SecureStorage = DeferredSecureStorage { AndroidSecureStorage(context) }
