package app.logdate.client.data.account

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.identity.DefaultCanonicalOwnerProvider
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.networking.LocationHistoryApiClientContract
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.GoogleSignInManager
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.location.LocationHistorySyncEngine
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.CompleteAccountCreationData
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.sync.LocationHistoryChangesResponse
import app.logdate.shared.model.sync.LocationHistoryRecord
import app.logdate.shared.model.sync.LocationHistoryUpload
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SignupHistoryContinuityTest {
    @Test
    fun `new passkey and Google accounts retain readable history without a second sign in or identity mutation`() =
        runTest {
            val id = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
            for (raw in listOf(id, id.lowercase())) {
                for (google in listOf(false, true)) {
                    val owner = DefaultCanonicalOwnerProvider(ownerStorage(raw))
                    val config = DefaultLogDateConfigRepository()
                    val secure = RenewalStorage()
                    val sessions = SecureSessionStorage(secure, config, backgroundScope)
                    val account = LogDateAccount(Uuid.parse(raw), "person", "Person")
                    val created = CompleteAccountCreationData(account, AccountTokens("synthetic-access", "synthetic-refresh"))
                    val delegate =
                        DefaultPasskeyAccountRepositoryTest().FakePasskeyApiClient().apply {
                            completeAccountCreationResponse = Result.success(created)
                        }
                    val api =
                        object : PasskeyApiClientContract by delegate {
                            override suspend fun signUpWithGoogle(
                                idToken: String,
                                username: String?,
                                displayName: String?,
                                nonce: String?,
                                requestedOwnerId: String?,
                            ): Result<CompleteAccountCreationData> {
                                assertEquals(raw, requestedOwnerId)
                                return Result.success(created)
                            }
                        }
                    val tokenProvider =
                        object : GoogleSignInManager {
                            override suspend fun getGoogleIdToken(
                                serverClientId: String,
                                nonce: String?,
                            ) = Result.success("synthetic-google-token")
                        }
                    val repository =
                        DefaultPasskeyAccountRepository(
                            api,
                            DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
                            DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager(),
                            sessions,
                            DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                            config,
                            owner,
                            { true },
                            googleSignInManager = tokenProvider,
                            serverClientId = "synthetic-client",
                            repositoryScope = backgroundScope,
                            createsRestoreKey = false,
                        )
                    val result =
                        if (google) {
                            repository.signUpWithGoogle("person", "Person")
                        } else {
                            repository.createAccountWithPasskey(AccountCreationRequest("person", "Person"))
                        }
                    assertEquals(account, result.getOrThrow())
                    val original = assertNotNull(owner.readExistingOwner())
                    assertFalse(original.isBound)
                    assertEquals(raw, original.rawId)
                    assertEquals(account.id.toString(), sessions.getSession()?.accountId)
                    val crypto = DesktopCryptoManager()
                    val identity = IdentityKeyManager(secure, crypto)
                    val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
                    var staged = false
                    val engine =
                        LocationHistorySyncEngine(
                            { EmptyHistoryApi },
                            EmptyHistoryStore,
                            cipher,
                            sessions,
                            config,
                            { true },
                            prepare = { selected, origin ->
                                assertEquals(raw, selected)
                                assertEquals(config.getCurrentBackendUrl().trimEnd('/'), origin)
                                staged = true
                                false
                            },
                            canonicalOwner = owner,
                        )
                    assertTrue(engine.upload("synthetic-access").success)
                    assertTrue(staged)
                    assertFalse(engine.hasRemoteRecords("synthetic-access"))
                    assertEquals(original, owner.readExistingOwner())
                    assertFalse(identity.hasIdentityKey(), "History observation must not mint an encryption identity")
                }
            }
        }

    private fun ownerStorage(raw: String): KeyValueStorage {
        val storage = HistoryAdoptionTestStorage()
        storage.values["identity.canonical_owner_id"] = raw
        return object : KeyValueStorage by storage {
            override suspend fun getStringsOrThrow(keys: Set<String>) = keys.associateWith { storage.values[it] }
        }
    }

    private object EmptyHistoryApi : LocationHistoryApiClientContract {
        override suspend fun upload(
            accessToken: String,
            records: List<LocationHistoryUpload>,
        ): List<LocationHistoryRecord> = error("Empty history does not upload")

        override suspend fun changes(
            accessToken: String,
            since: Long,
            limit: Int,
        ) = LocationHistoryChangesResponse(emptyList(), 0, false)
    }

    private object EmptyHistoryStore : HistoryRecordStore {
        override fun observe(
            ownerId: String,
            origin: String,
        ) = flowOf(emptyList<HistoryRecord>())

        override suspend fun records(
            ownerId: String,
            origin: String,
        ) = emptyList<HistoryRecord>()

        override suspend fun put(
            ownerId: String,
            origin: String,
            record: HistoryRecord,
        ) = error("Observation cannot store history")

        override suspend fun pending(
            ownerId: String,
            origin: String,
            limit: Int,
        ) = emptyList<HistoryRecord>()

        override suspend fun cursor(
            ownerId: String,
            origin: String,
        ) = 0L

        override suspend fun applyPage(
            ownerId: String,
            origin: String,
            records: List<HistoryRecord>,
            cursor: Long,
        ) = error("No downloaded page")

        override suspend fun acknowledge(
            ownerId: String,
            origin: String,
            id: String,
            deviceVersion: Long,
            serverVersion: Long,
        ) = error("No upload to acknowledge")
    }
}
