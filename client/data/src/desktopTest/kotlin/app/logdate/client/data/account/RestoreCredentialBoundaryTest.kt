package app.logdate.client.data.account

import app.logdate.client.device.crypto.AccountKeyEnvelopeCipher
import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.networking.AccountKeyEnvelopeApi
import app.logdate.client.permissions.RestoreCredentialError
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.shared.model.PasskeyAuthenticationOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreCredentialBoundaryTest {
    @Test
    fun `restore authenticates an account while its journal keys remain unavailable`() =
        runTest {
            val fixture = Fixture(this)
            val repository = fixture.repository()
            runCurrent()

            val restoredAccount = repository.signInWithRestoreKey().getOrThrow()
            assertEquals(fixture.owner, restoredAccount.id.toString())
            assertEquals(fixture.owner, fixture.sessions.getSession()?.accountId)
            assertEquals(
                fixture.owner,
                repository.currentAccount.value
                    ?.id
                    ?.toString(),
            )
            fixture.assertKeysRemainUnavailable()
        }

    @Test
    fun `no backed up credential leaves explicit passkey sign in available`() =
        runTest {
            assertManualSignInRemainsAvailable(RestoreCredentialError.NoCredential())
        }

    @Test
    fun `an unavailable restore provider leaves explicit passkey sign in available`() =
        runTest {
            assertManualSignInRemainsAvailable(RestoreCredentialError.Unknown("Provider unavailable"))
        }

    private suspend fun TestScope.assertManualSignInRemainsAvailable(failure: RestoreCredentialError) {
        val fixture = Fixture(this)
        fixture.restoreResult = Result.failure(failure)
        val repository = fixture.repository()
        runCurrent()

        assertEquals(failure, repository.signInWithRestoreKey().exceptionOrNull())
        assertNull(fixture.sessions.getSession())
        assertNull(repository.currentAccount.value)
        fixture.assertKeysRemainUnavailable()

        val authenticatedAccount = repository.authenticateWithPasskey(null, false).getOrThrow()
        assertEquals(fixture.owner, authenticatedAccount.id.toString())
        assertEquals(fixture.owner, fixture.sessions.getSession()?.accountId)
        assertEquals(
            fixture.owner,
            repository.currentAccount.value
                ?.id
                ?.toString(),
        )
        fixture.assertKeysRemainUnavailable()
    }

    private class Fixture(
        val scope: TestScope,
    ) {
        private val defaults = DefaultPasskeyAccountRepositoryTest()
        private val api = defaults.FakePasskeyApiClient()
        private val passkeys = DefaultPasskeyAccountRepositoryTest.FakePasskeyManager()
        val owner =
            api.completeAuthenticationResponse
                .getOrThrow()
                .account.id
                .toString()
        private val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
        private val keyStorage = RenewalStorage()
        private val crypto = DesktopCryptoManager()
        private val identity = IdentityKeyManager(keyStorage, crypto)
        private val media = MediaPayloadKeyProvider(keyStorage, crypto, identity, KeyDerivation(crypto))
        val sessions = SecureSessionStorage(RenewalStorage(), config, scope.backgroundScope)
        var restoreResult = passkeys.authenticateWithPasskeyResponse
        private val envelopes =
            object : AccountKeyEnvelopeApi {
                override suspend fun isSupported(apiBaseUrl: String): Result<Boolean> =
                    error("Account assertion cannot unlock journal keys")

                override suspend fun fetch(
                    apiBaseUrl: String,
                    accessToken: String,
                    credentialId: String,
                ): Result<String?> = error("Account assertion cannot fetch a key envelope")

                override suspend fun store(
                    apiBaseUrl: String,
                    accessToken: String,
                    credentialId: String,
                    ciphertext: String,
                ): Result<Unit> = error("Account assertion cannot provision replacement keys")
            }

        fun repository() =
            DefaultPasskeyAccountRepository(
                apiClient = api,
                passkeyManager = passkeys,
                restoreCredentialManager =
                    object : RestoreCredentialManager by DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager() {
                        override suspend fun getRestoreCredential(options: PasskeyAuthenticationOptions): Result<String> = restoreResult
                    },
                sessionStorage = sessions,
                platformAccountManager = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                configRepository = config,
                canonicalOwnerProvider =
                    object : CanonicalOwnerProvider {
                        override suspend fun getCanonicalOwnerId(): String = owner

                        override suspend fun hasBoundOwner(): Boolean = true
                    },
                hasLocalData = { true },
                repositoryScope = scope.backgroundScope,
                restoreCredentialRenewal = RestoreCredentialRenewal(RenewalStorage()) { api },
                accountKeyUnlockCoordinator = AccountKeyUnlockCoordinator(envelopes, AccountKeyEnvelopeCipher(crypto), identity, media),
            )

        suspend fun assertKeysRemainUnavailable() {
            assertFalse(identity.hasIdentityKey())
            assertNull(keyStorage.getString("identity_key_v1"))
            assertNull(keyStorage.getString("media_payload_key_v1"))
        }
    }
}
