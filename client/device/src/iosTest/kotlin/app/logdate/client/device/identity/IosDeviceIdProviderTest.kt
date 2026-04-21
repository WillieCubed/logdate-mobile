package app.logdate.client.device.identity

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for the [IosDeviceIdProvider], ensuring that unique device identifiers are
 * correctly managed within the iOS Keychain.
 *
 * These tests verify that the provider can generate new IDs, persist them across
 * app restarts, handle invalid stored data, and provide a stable identifier until
 * an explicit refresh is requested.
 */
class IosDeviceIdProviderTest {
    private lateinit var keychainWrapper: FakeKeychainWrapper
    private lateinit var provider: IosDeviceIdProvider

    private val deviceIdKey = "app.logdate.device.id"

    @BeforeTest
    fun setUp() {
        keychainWrapper = FakeKeychainWrapper()
        provider = IosDeviceIdProvider(keychainWrapper)
    }

    @Test
    fun `getDeviceId should generate and store new ID if none exists`() =
        runTest {
            // When
            val deviceId = provider.getDeviceId().first()

            // Then
            assertTrue(deviceId.toString().isNotBlank(), "Device ID should not be blank")
            assertEquals(deviceId.toString(), keychainWrapper.values[deviceIdKey])
        }

    @Test
    fun `getDeviceId should return stored ID if one exists`() =
        runTest {
            // Given
            val storedUuid = Uuid.random()
            keychainWrapper.values[deviceIdKey] = storedUuid.toString()
            keychainWrapper.setCalls.clear()
            val provider = IosDeviceIdProvider(keychainWrapper)

            // When
            val deviceId = provider.getDeviceId().first()

            // Then
            assertEquals(storedUuid, deviceId, "Should return the stored UUID")
            assertTrue(keychainWrapper.setCalls.isEmpty(), "Should not store a new ID when one exists")
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
            assertEquals(2, keychainWrapper.setCalls.size, "Should store once on init and once on refresh")
        }

    @Test
    fun `should recover from invalid stored UUID`() =
        runTest {
            // Given
            keychainWrapper.values[deviceIdKey] = "not-a-valid-uuid"
            val provider = IosDeviceIdProvider(keychainWrapper)

            // When
            val deviceId = provider.getDeviceId().first()

            // Then
            assertTrue(deviceId.toString().isNotBlank(), "Should have generated a valid device ID")
            assertEquals(deviceId.toString(), keychainWrapper.values[deviceIdKey])
        }

    private class FakeKeychainWrapper : KeychainWrapper {
        val values = mutableMapOf<String, String>()
        val setCalls = mutableListOf<Pair<String, String>>()

        override fun getString(key: String): String? = values[key]

        override suspend fun set(
            value: String,
            key: String,
        ): Boolean {
            setCalls.add(key to value)
            values[key] = value
            return true
        }

        override suspend fun remove(key: String): Boolean {
            values.remove(key)
            return true
        }
    }
}
