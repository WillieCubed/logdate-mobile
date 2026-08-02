package app.logdate.client.device

import app.logdate.client.device.identity.KeychainWrapper
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosAccountManagerTest {
    @Test
    fun pendingAuthorizationRejectsAccountCreationBeforeReadingOrWritingKeychain() =
        runTest {
            val keychain = RecordingKeychainWrapper()
            val originalBytes =
                mapOf(
                    "accounts" to "opaque-legacy-account-index",
                    "account_tokens_legacy@https://legacy.example" to "opaque-legacy-token-bytes",
                )
            keychain.values.putAll(originalBytes)
            val manager = IosAccountManager(keychain)

            val result =
                manager.addAccount(
                    account = LogDateAccount(username = "new-user", displayName = "New User"),
                    accessToken = "new-access-token",
                    refreshToken = "new-refresh-token",
                    backendUrl = "https://cloud.logdate.app",
                )

            assertTrue(result.isFailure, "IdentityFoundationPending must reject platform account writes")
            assertEquals(0, keychain.readCount, "The quarantine must close before a Keychain read")
            assertEquals(0, keychain.writeCount, "The quarantine must close before a Keychain write")
            assertEquals(0, keychain.removeCount, "The quarantine must not remove legacy bytes")
            assertEquals(originalBytes, keychain.values, "Legacy account and token bytes must remain unchanged")
        }

    @Test
    fun pendingAuthorizationRejectsAccountEnumerationBeforeReadingKeychain() =
        runTest {
            val keychain = RecordingKeychainWrapper()
            val originalBytes = mapOf("accounts" to "opaque-legacy-account-index")
            keychain.values.putAll(originalBytes)
            val manager = IosAccountManager(keychain)

            val result = manager.getStoredAccounts()

            assertTrue(result.isFailure, "Pending authorization is not an authoritative empty account list")
            assertEquals(0, keychain.readCount, "The quarantine must close before account enumeration")
            assertEquals(0, keychain.writeCount)
            assertEquals(0, keychain.removeCount)
            assertEquals(originalBytes, keychain.values)
        }

    private class RecordingKeychainWrapper : KeychainWrapper {
        val values = mutableMapOf<String, String>()
        var readCount = 0
        var writeCount = 0
        var removeCount = 0

        override fun getString(key: String): String? {
            readCount += 1
            return values[key]
        }

        override suspend fun set(
            value: String,
            key: String,
        ): Boolean {
            writeCount += 1
            values[key] = value
            return true
        }

        override suspend fun remove(key: String): Boolean {
            removeCount += 1
            values.remove(key)
            return true
        }
    }
}
