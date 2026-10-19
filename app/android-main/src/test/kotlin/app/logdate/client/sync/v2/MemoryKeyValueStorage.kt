package app.logdate.client.sync.v2

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.peer.link.PairKeyVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Strings and booleans in memory, observable, for the phone's version 2 tests. */
class MemoryKeyValueStorage : KeyValueStorage {
    private val strings = MutableStateFlow<Map<String, String>>(emptyMap())

    val values: Map<String, String> get() = strings.value

    override suspend fun getString(key: String): String? = strings.value[key]

    override fun getStringSync(key: String): String? = strings.value[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        strings.update { it + (key to value) }
    }

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = strings.value[key]?.toBooleanStrictOrNull() ?: defaultValue

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        putString(key, value.toString())
    }

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = defaultValue

    override suspend fun putInt(
        key: String,
        value: Int,
    ) = Unit

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = defaultValue

    override suspend fun putLong(
        key: String,
        value: Long,
    ) = Unit

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = defaultValue

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) = Unit

    override suspend fun remove(key: String) {
        strings.update { it - key }
    }

    override suspend fun contains(key: String): Boolean = key in strings.value

    override suspend fun clear() {
        strings.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = strings.map { it[key] }.distinctUntilChanged()

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = strings.map { it[key]?.toBooleanStrictOrNull() ?: defaultValue }.distinctUntilChanged()

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = flowOf(defaultValue)

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = flowOf(defaultValue)

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = flowOf(defaultValue)
}

/** Pair keys in memory. */
class MemoryPairKeyVault : PairKeyVault {
    val keys = mutableMapOf<String, ByteArray>()

    override suspend fun put(
        peer: String,
        key: ByteArray,
    ) {
        keys[peer] = key
    }

    override suspend fun get(peer: String): ByteArray? = keys[peer]

    override suspend fun remove(peer: String) {
        keys.remove(peer)
    }
}
