package app.logdate.client.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Tests [PrefixedKeyValueStorage]: a second store under a prefix never reads or overwrites the first. */
class PrefixedKeyValueStorageTest {
    private val shared = MapStorage()
    private val prefixed = PrefixedKeyValueStorage(shared, "watch_cloud.")

    @Test
    fun `values live under the prefix`() =
        runTest {
            prefixed.putString("retry", "a")
            prefixed.putLong("since", 9)

            assertEquals("a", shared.strings.value["watch_cloud.retry"])
            assertEquals("a", prefixed.getString("retry"))
            assertEquals(9, prefixed.getLong("since"))
        }

    @Test
    fun `the shared store's own values are not seen or removed`() =
        runTest {
            shared.putString("retry", "phone")

            assertNull(prefixed.getString("retry"))
            prefixed.remove("retry")
            assertEquals("phone", shared.getString("retry"))
        }

    @Test
    fun `observing reads the prefixed key`() =
        runTest {
            prefixed.putString("error", "x")

            assertEquals("x", prefixed.observeString("error").first())
        }

    @Test
    fun `clearing is refused because it would erase the shared store`() =
        runTest {
            assertFailsWith<UnsupportedOperationException> { prefixed.clear() }
        }

    /** Strings and longs in memory. */
    private class MapStorage : KeyValueStorage {
        val strings = MutableStateFlow<Map<String, String>>(emptyMap())
        private val longs = mutableMapOf<String, Long>()

        override suspend fun getString(key: String): String? = strings.value[key]

        override suspend fun putString(
            key: String,
            value: String,
        ) {
            strings.value = strings.value + (key to value)
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
        ): Long = longs[key] ?: defaultValue

        override suspend fun putLong(
            key: String,
            value: Long,
        ) {
            longs[key] = value
        }

        override suspend fun getFloat(
            key: String,
            defaultValue: Float,
        ): Float = defaultValue

        override suspend fun putFloat(
            key: String,
            value: Float,
        ) = Unit

        override suspend fun remove(key: String) {
            strings.value = strings.value - key
            longs.remove(key)
        }

        override suspend fun contains(key: String): Boolean = key in strings.value || key in longs

        override suspend fun clear() {
            strings.value = emptyMap()
            longs.clear()
        }

        override fun observeString(key: String): Flow<String?> = strings.map { it[key] }

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
}
