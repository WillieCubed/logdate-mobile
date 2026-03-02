package app.logdate.feature.core.account.ui.fakes

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class InMemoryKeyValueStorage : KeyValueStorage {
    private val values = mutableMapOf<String, Any?>()

    override suspend fun getString(key: String): String? = values[key] as? String

    override fun getStringSync(key: String): String? = values[key] as? String

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defaultValue

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        values[key] = value
    }

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = values[key] as? Int ?: defaultValue

    override suspend fun putInt(
        key: String,
        value: Int,
    ) {
        values[key] = value
    }

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = values[key] as? Long ?: defaultValue

    override suspend fun putLong(
        key: String,
        value: Long,
    ) {
        values[key] = value
    }

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = values[key] as? Float ?: defaultValue

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun contains(key: String): Boolean = values.containsKey(key)

    override suspend fun clear() {
        values.clear()
    }

    override fun observeString(key: String): Flow<String?> = flowOf(values[key] as? String)

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = flowOf(values[key] as? Boolean ?: defaultValue)

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = flowOf(values[key] as? Int ?: defaultValue)

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = flowOf(values[key] as? Long ?: defaultValue)

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = flowOf(values[key] as? Float ?: defaultValue)
}
