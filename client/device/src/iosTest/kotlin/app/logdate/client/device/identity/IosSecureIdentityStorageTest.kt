package app.logdate.client.device.identity

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class IosSecureIdentityStorageTest {
    private lateinit var keychainWrapper: FakeKeychainWrapper
    private lateinit var storage: IosSecureIdentityStorage

    private val userId = Uuid.random()
    private val testState =
        MigrationState(
            fromUserId = Uuid.random(),
            toUserId = Uuid.random(),
            progress = 0.5f,
            timestamp = 123456789L,
        )

    @BeforeTest
    fun setUp() {
        keychainWrapper = FakeKeychainWrapper()
        storage = IosSecureIdentityStorage(keychainWrapper)
    }

    @Test
    fun `getUserId should return null when no ID is set`() =
        runTest {
            // When
            val userId = storage.getUserId()

            // Then
            assertNull(userId, "User ID should be null when not set")
        }

    @Test
    fun `setUserId should store the ID`() =
        runTest {
            // When
            storage.setUserId(userId)

            // Then
            assertEquals(userId.toString(), keychainWrapper.values["app.logdate.user.id"])
        }

    @Test
    fun `getUserId should return stored ID`() =
        runTest {
            // Given
            keychainWrapper.values["app.logdate.user.id"] = userId.toString()

            // When
            val retrievedId = storage.getUserId()

            // Then
            assertEquals(userId, retrievedId, "Should return the stored user ID")
        }

    @Test
    fun `getMigrationState should return null when no state is set`() =
        runTest {
            // When
            val state = storage.getMigrationState()

            // Then
            assertNull(state, "Migration state should be null when not set")
        }

    @Test
    fun `setMigrationState should store the state`() =
        runTest {
            // Given
            val stateJson = Json.encodeToString(testState)

            // When
            storage.setMigrationState(testState)

            // Then
            assertEquals(stateJson, keychainWrapper.values["app.logdate.migration.state"])
        }

    @Test
    fun `getMigrationState should return stored state`() =
        runTest {
            // Given
            val stateJson = Json.encodeToString(testState)
            keychainWrapper.values["app.logdate.migration.state"] = stateJson

            // When
            val retrievedState = storage.getMigrationState()

            // Then
            assertEquals(testState, retrievedState, "Should return the stored migration state")
        }

    @Test
    fun `clearMigrationState should remove the state`() =
        runTest {
            // When
            storage.clearMigrationState()

            // Then
            assertNull(keychainWrapper.values["app.logdate.migration.state"])
        }

    @Test
    fun `clear should remove both user ID and migration state`() =
        runTest {
            // When
            storage.clear()

            // Then
            assertNull(keychainWrapper.values["app.logdate.user.id"])
            assertNull(keychainWrapper.values["app.logdate.migration.state"])
        }

    @Test
    fun `should handle invalid migration state JSON`() =
        runTest {
            // Given
            keychainWrapper.values["app.logdate.migration.state"] = "invalid-json"

            // When
            val state = storage.getMigrationState()

            // Then
            assertNull(state, "Should return null for invalid migration state JSON")
        }

    private class FakeKeychainWrapper : KeychainWrapper {
        val values = mutableMapOf<String, String>()

        override fun getString(key: String): String? = values[key]

        override suspend fun set(
            value: String,
            key: String,
        ): Boolean {
            values[key] = value
            return true
        }

        override suspend fun remove(key: String): Boolean {
            values.remove(key)
            return true
        }
    }
}
