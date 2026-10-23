package app.logdate.client.datastore

import kotlinx.coroutines.flow.Flow

/**
 * A [KeyValueStorage] whose keys are [prefix] followed by the key, so a second copy of a store can share
 * [delegate] without reading or overwriting the first copy's values. Clearing would erase every other
 * store in [delegate] too, so it is refused.
 */
class PrefixedKeyValueStorage(
    private val delegate: KeyValueStorage,
    private val prefix: String,
) : KeyValueStorage {
    override suspend fun getString(key: String): String? = delegate.getString(prefix + key)

    override suspend fun getStringsOrThrow(keys: Set<String>): Map<String, String?> {
        val mapped = keys.associateWith { prefix + it }
        val snapshot = delegate.getStringsOrThrow(mapped.values.toSet())
        check(snapshot.keys == mapped.values.toSet()) { "Incomplete atomic string snapshot" }
        return mapped.mapValues { (_, key) -> snapshot[key] }
    }

    override suspend fun updateStringsOrThrow(values: Map<String, String?>) =
        delegate.updateStringsOrThrow(values.mapKeys { (key, _) -> prefix + key })

    override suspend fun compareAndSetStringsOrThrow(
        expected: Map<String, String?>,
        values: Map<String, String?>,
    ): Boolean =
        delegate.compareAndSetStringsOrThrow(
            expected.mapKeys { (key, _) -> prefix + key },
            values.mapKeys { (key, _) -> prefix + key },
        )

    override suspend fun getStringOrThrow(key: String): String? = delegate.getStringOrThrow(prefix + key)

    override suspend fun putStringOrThrow(
        key: String,
        value: String,
    ) = delegate.putStringOrThrow(prefix + key, value)

    override fun getStringSync(key: String): String? = delegate.getStringSync(prefix + key)

    override suspend fun putString(
        key: String,
        value: String,
    ) = delegate.putString(prefix + key, value)

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = delegate.getBoolean(prefix + key, defaultValue)

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) = delegate.putBoolean(prefix + key, value)

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = delegate.getInt(prefix + key, defaultValue)

    override suspend fun putInt(
        key: String,
        value: Int,
    ) = delegate.putInt(prefix + key, value)

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = delegate.getLong(prefix + key, defaultValue)

    override suspend fun putLong(
        key: String,
        value: Long,
    ) = delegate.putLong(prefix + key, value)

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = delegate.getFloat(prefix + key, defaultValue)

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) = delegate.putFloat(prefix + key, value)

    override suspend fun remove(key: String) = delegate.remove(prefix + key)

    override suspend fun contains(key: String): Boolean = delegate.contains(prefix + key)

    override suspend fun clear(): Unit = throw UnsupportedOperationException("A prefixed store cannot clear the store it shares")

    override fun observeString(key: String): Flow<String?> = delegate.observeString(prefix + key)

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = delegate.observeBoolean(prefix + key, defaultValue)

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = delegate.observeInt(prefix + key, defaultValue)

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = delegate.observeLong(prefix + key, defaultValue)

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = delegate.observeFloat(prefix + key, defaultValue)
}
