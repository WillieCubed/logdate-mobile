package app.logdate.client.device.identity

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopDeviceIdProviderTest {
    private lateinit var storage: InMemoryKeyValueStorage
    private lateinit var provider: DesktopDeviceIdProvider

    @BeforeTest
    fun setUp() {
        storage = InMemoryKeyValueStorage()
        provider = DesktopDeviceIdProvider(storage = storage)
    }

    @Test
    fun `getDeviceId should generate and store new ID if none exists`() =
        runTest {
            // When
            val deviceId = provider.getDeviceId().first()

            // Then
            assertTrue(storage.getStringSync("device.id") != null, "Device ID should be persisted")
            assertTrue(storage.getStringSync("device.id")!!.isNotBlank(), "Stored device ID should not be empty")
            assertTrue(deviceId.toString().isNotBlank(), "Device ID should not be blank")
        }

    @Test
    fun `getDeviceId should return consistent ID until refreshed`() =
        runTest {
            // When
            val firstId = provider.getDeviceId().first()
            val secondId = provider.getDeviceId().first()

            // Then
            assertEquals(firstId, secondId, "Device ID should remain consistent between reads")
        }

    @Test
    fun `refreshDeviceId should generate a new ID`() =
        runTest {
            // Given
            val initialId = provider.getDeviceId().first()

            // When
            provider.refreshDeviceId()
            val newId = provider.getDeviceId().first()

            // Then
            assertNotEquals(initialId, newId, "Device ID should change after refresh")
        }

    @Test
    fun `should recover from invalid stored UUID`() =
        runTest {
            // Given
            storage.putString("device.id", "not-a-valid-uuid")

            // When
            val newProvider = DesktopDeviceIdProvider(storage = storage)
            val deviceId = newProvider.getDeviceId().first()

            // Then
            assertTrue(deviceId.toString().isNotBlank(), "Should have generated a valid device ID")
        }

    private class InMemoryKeyValueStorage : KeyValueStorage {
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
}
