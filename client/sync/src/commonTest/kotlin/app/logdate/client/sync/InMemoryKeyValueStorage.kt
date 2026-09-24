package app.logdate.client.sync

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Keeps strings in memory; the other value types read back their defaults. Shared by the sync tests. */
internal class InMemoryKeyValueStorage : KeyValueStorage {
    val values = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = values[key]

    override fun getStringSync(key: String): String? = values[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = defaultValue

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) = Unit

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
        values.remove(key)
    }

    override suspend fun contains(key: String): Boolean = values.containsKey(key)

    override suspend fun clear() {
        values.clear()
    }

    override fun observeString(key: String): Flow<String?> = flowOf(values[key])

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = flowOf(defaultValue)

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
