package app.logdate.client.domain.watch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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

    /** Confirms that a choice was persisted, including an explicit choice of the default value. */
    suspend fun <T> setAndVerify(
        key: SharedPreferenceKey<T>,
        value: T,
    ): Boolean {
        set(key, value)
        return get(key) == value
    }
}

class DefaultWatchPreferencesRepository(
    private val store: StampedPreferenceStore,
    private val prepare: suspend () -> Unit = {},
) : WatchPreferencesRepository {
    override fun <T> observe(key: SharedPreferenceKey<T>): Flow<T> =
        flow {
            prepare()
            emitAll(store.observe())
        }.map { entries -> key.read(entries[key.name]) }
            .distinctUntilChanged()

    override suspend fun <T> get(key: SharedPreferenceKey<T>): T {
        prepare()
        return key.read(store.snapshot()[key.name])
    }

    override suspend fun <T> set(
        key: SharedPreferenceKey<T>,
        value: T,
    ) {
        require(key.isAllowed(value)) { "${key.name} does not accept $value" }
        store.write(key.name, key.encode(value))
    }

    override suspend fun <T> setAndVerify(
        key: SharedPreferenceKey<T>,
        value: T,
    ): Boolean {
        require(key.isAllowed(value)) { "${key.name} does not accept $value" }
        val written = store.write(key.name, key.encode(value))
        return store.snapshot()[key.name] == written
    }
}
