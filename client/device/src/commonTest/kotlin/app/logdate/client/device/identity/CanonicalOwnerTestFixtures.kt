package app.logdate.client.device.identity

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class CanonicalOwnerTestStorage(
    private val dropWrites: Boolean = false,
) : KeyValueStorage {
    private val entries = mutableMapOf<String, String>()
    private val strings = mutableMapOf<String, MutableStateFlow<String?>>()

    override suspend fun getString(key: String): String? = entries[key]

    override suspend fun getStringsOrThrow(keys: Set<String>): Map<String, String?> = keys.associateWith { entries[it] }

    override suspend fun updateStringsOrThrow(values: Map<String, String?>) {
        if (dropWrites) return
        for ((key, value) in values) {
            if (value == null) entries.remove(key) else entries[key] = value
        }
        values.forEach { (key, value) -> strings.getOrPut(key) { MutableStateFlow(null) }.value = value }
    }

    override suspend fun compareAndSetStringsOrThrow(
        expected: Map<String, String?>,
        values: Map<String, String?>,
    ): Boolean {
        if (dropWrites || expected.any { (key, value) -> entries[key] != value }) return false
        updateStringsOrThrow(values)
        return true
    }

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        if (dropWrites) {
            return
        }
        entries[key] = value
        strings.getOrPut(key) { MutableStateFlow(null) }.value = value
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
        entries.remove(key)
        strings.getOrPut(key) { MutableStateFlow(null) }.value = null
    }

    override suspend fun contains(key: String): Boolean = key in entries

    override suspend fun clear() {
        entries.clear()
    }

    override fun observeString(key: String): Flow<String?> = strings.getOrPut(key) { MutableStateFlow(entries[key]) }

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = MutableStateFlow(defaultValue)

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = MutableStateFlow(defaultValue)

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = MutableStateFlow(defaultValue)

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = MutableStateFlow(defaultValue)
}

internal fun readOnlyCanonicalOwnerStorage(storage: KeyValueStorage): KeyValueStorage =
    object : KeyValueStorage by storage {
        override suspend fun putString(
            key: String,
            value: String,
        ) {
            error("Unexpected owner mutation")
        }

        override suspend fun putStringOrThrow(
            key: String,
            value: String,
        ) {
            error("Unexpected owner mutation")
        }

        override suspend fun updateStringsOrThrow(values: Map<String, String?>) {
            error("Unexpected atomic owner mutation")
        }

        override suspend fun compareAndSetStringsOrThrow(
            expected: Map<String, String?>,
            values: Map<String, String?>,
        ): Boolean {
            error("Unexpected conditional owner mutation")
        }

        override suspend fun remove(key: String) {
            error("Unexpected owner removal")
        }

        override suspend fun clear() {
            error("Unexpected owner clearing")
        }
    }
