package app.logdate.client.domain.watch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Typed reads and writes of the preferences this device shares with its paired device. */
interface WatchPreferencesRepository {
    fun <T> observe(key: SharedPreferenceKey<T>): Flow<T>

    suspend fun <T> get(key: SharedPreferenceKey<T>): T

    /** @throws IllegalArgumentException when [SharedPreferenceKey.isAllowed] rejects [value]. */
    suspend fun <T> set(
        key: SharedPreferenceKey<T>,
        value: T,
    )
}

class DefaultWatchPreferencesRepository(
    private val store: StampedPreferenceStore,
) : WatchPreferencesRepository {
    override fun <T> observe(key: SharedPreferenceKey<T>): Flow<T> =
        store
            .observe()
            .map { entries -> key.read(entries[key.name]) }
            .distinctUntilChanged()

    override suspend fun <T> get(key: SharedPreferenceKey<T>): T = key.read(store.snapshot()[key.name])

    override suspend fun <T> set(
        key: SharedPreferenceKey<T>,
        value: T,
    ) {
        require(key.isAllowed(value)) { "${key.name} does not accept $value" }
        store.write(key.name, key.encode(value))
    }
}
