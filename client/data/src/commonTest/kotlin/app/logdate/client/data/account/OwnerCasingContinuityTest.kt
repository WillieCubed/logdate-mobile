package app.logdate.client.data.account

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.DefaultCanonicalOwnerProvider
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.GoogleSignInManager
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.CompleteAccountCreationData
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerCasingContinuityTest {
    private val raw = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
    private val old = "b930bf49-d1e9-4a65-b84b-8e74af45012b"

    @Test
    fun `bound typed account retains its original owner spelling without history movement`() =
        runTest {
            val storage = storage(raw, bound = true)
            val owner = DefaultCanonicalOwnerProvider(storage)
            val history =
                HistoryOwnerAdoption(
                    storage,
                    owner,
                    DefaultLogDateConfigRepository(),
                    { "device" },
                    { _, _, _, _ -> error("An existing account must not move history") },
                )
            val guard = CanonicalOwnerBindingGuard(owner, { true }, DefaultLogDateConfigRepository(), history::adopt)
            assertEquals(
                CanonicalOwnerBindingGuard.OwnerBinding.ALLOWED,
                guard.ownerBindingFor(LogDateAccount(id = Uuid.parse(raw), username = "person", displayName = "Person"), false),
            )
            assertEquals(raw, owner.getCanonicalOwnerId())
            assertNull(storage.getString("identity.pending_history_owner_adoption.v1"))
            assertNull(storage.getString("identity.completed_history_owner_adoption.v1"))
        }

    @Test
    fun `session observer does not discard a restored session for the same UUID`() =
        runTest {
            val storage = storage(raw, bound = true)
            val owner = DefaultCanonicalOwnerProvider(storage)
            val session = UserSession("synthetic-access", "synthetic-refresh", raw.lowercase())
            val sessions = DefaultPasskeyAccountRepositoryTest.FakeSessionStorage()
            sessions.saveSession(session)
            val repository =
                DefaultPasskeyAccountRepository(
                    apiClient = DefaultPasskeyAccountRepositoryTest().FakePasskeyApiClient(),
                    passkeyManager = DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
                    restoreCredentialManager = DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager(),
                    sessionStorage = sessions,
                    platformAccountManager = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    configRepository = DefaultLogDateConfigRepository(),
                    canonicalOwnerProvider = owner,
                    hasLocalData = { true },
                    repositoryScope = backgroundScope,
                    createsRestoreKey = false,
                )
            runCurrent()
            assertEquals(session, sessions.getSession())
            assertTrue(repository.isAuthenticated.value)
            assertEquals(raw, owner.getCanonicalOwnerId())
        }

    @Test
    fun `pending adoption resumes its recorded raw target when the caller changes UUID casing`() =
        runTest {
            val storage = storage(old, bound = false)
            storage.putString(
                "identity.pending_history_owner_adoption.v1",
                """{"oldOwner":"$old","newOwner":"$raw","origin":"https://server.test","device":"original-device"}""",
            )
            val owner = DefaultCanonicalOwnerProvider(storage)
            val moves = mutableListOf<List<String>>()
            val history =
                HistoryOwnerAdoption(
                    storage,
                    owner,
                    DefaultLogDateConfigRepository(initialBackendUrl = "https://server.test"),
                    { "replacement-device" },
                    { from, to, origin, device -> moves += listOf(from, to, origin, device) },
                )
            assertTrue(history.adopt(raw.lowercase()))
            assertEquals(listOf(old, raw, "https://server.test", "original-device"), moves.single())
            assertEquals(raw, owner.getCanonicalOwnerId())
            assertTrue(owner.hasBoundOwner())
            assertNull(storage.getString("identity.pending_history_owner_adoption.v1"))
            history.reconcile(raw.lowercase(), "https://server.test")
            assertEquals(2, moves.size)
            assertEquals(moves.first(), moves.last())
        }

    @Test
    fun `a genuinely different owner still cannot claim the bound journal`() =
        runTest {
            val storage = storage(raw, bound = true)
            val owner = DefaultCanonicalOwnerProvider(storage)
            val guard = CanonicalOwnerBindingGuard(owner, { false }, DefaultLogDateConfigRepository())
            assertEquals(
                CanonicalOwnerBindingGuard.OwnerBinding.REFUSED,
                guard.ownerBindingFor(LogDateAccount(id = Uuid.parse(old), username = "other", displayName = "Other"), true),
            )
            assertFalse(guard.sessionBelongsToCanonicalOwner(UserSession("a", "r", old)))
            assertEquals(raw, owner.getCanonicalOwnerId())
        }

    @Test
    fun `passkey creation accepts the requested UUID after typed server serialization`() =
        runTest {
            val storage = storage(raw, bound = false)
            val owner = DefaultCanonicalOwnerProvider(storage)
            val account = LogDateAccount(id = Uuid.parse(raw), username = "person", displayName = "Person")
            val api =
                DefaultPasskeyAccountRepositoryTest().FakePasskeyApiClient().apply {
                    completeAccountCreationResponse = Result.success(CompleteAccountCreationData(account, AccountTokens("a", "r")))
                }
            val sessions = DefaultPasskeyAccountRepositoryTest.FakeSessionStorage()
            val repository =
                DefaultPasskeyAccountRepository(
                    apiClient = api,
                    passkeyManager = DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
                    restoreCredentialManager = DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager(),
                    sessionStorage = sessions,
                    platformAccountManager = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    configRepository = DefaultLogDateConfigRepository(),
                    canonicalOwnerProvider = owner,
                    hasLocalData = { true },
                    repositoryScope = backgroundScope,
                    createsRestoreKey = false,
                )
            assertEquals(account, repository.createAccountWithPasskey(AccountCreationRequest("person", "Person")).getOrThrow())
            runCurrent()
            assertEquals(account.id.toString(), sessions.getSession()?.accountId)
            assertEquals(raw, owner.getCanonicalOwnerId())
        }

    @Test
    fun `Google creation accepts the requested UUID after typed server serialization`() =
        runTest {
            val storage = storage(raw, bound = false)
            val owner = DefaultCanonicalOwnerProvider(storage)
            val account = LogDateAccount(id = Uuid.parse(raw), username = "person", displayName = "Person")
            val delegate = DefaultPasskeyAccountRepositoryTest().FakePasskeyApiClient()
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
                        return Result.success(CompleteAccountCreationData(account, AccountTokens("a", "r")))
                    }
                }
            val google =
                object : GoogleSignInManager {
                    override suspend fun getGoogleIdToken(
                        serverClientId: String,
                        nonce: String?,
                    ) = Result.success("synthetic-token")
                }
            val sessions = DefaultPasskeyAccountRepositoryTest.FakeSessionStorage()
            val repository =
                DefaultPasskeyAccountRepository(
                    apiClient = api,
                    passkeyManager = DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
                    restoreCredentialManager = DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager(),
                    sessionStorage = sessions,
                    platformAccountManager = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    configRepository = DefaultLogDateConfigRepository(),
                    canonicalOwnerProvider = owner,
                    hasLocalData = { true },
                    googleSignInManager = google,
                    serverClientId = "synthetic-client",
                    repositoryScope = backgroundScope,
                    createsRestoreKey = false,
                )
            assertEquals(account, repository.signUpWithGoogle("person", "Person").getOrThrow())
            runCurrent()
            assertEquals(account.id.toString(), sessions.getSession()?.accountId)
            assertEquals(raw, owner.getCanonicalOwnerId())
        }

    @Test
    fun `completed adoption retains a raw row alias for the same UUID`() =
        runTest {
            val storage = storage(raw.lowercase(), bound = true)
            storage.putString(
                "identity.completed_history_owner_adoption.v1",
                """{"oldOwner":"$raw","newOwner":"${raw.lowercase()}","origin":"https://server.test","device":"device"}""",
            )
            assertEquals(raw, HistoryOwnerAdoption.completedSourceFor(storage, raw.lowercase(), "https://server.test"))
        }

    private fun storage(
        owner: String,
        bound: Boolean,
    ): KeyValueStorage {
        val delegate = HistoryAdoptionTestStorage()
        delegate.values["identity.canonical_owner_id"] = owner
        if (bound) delegate.values["identity.canonical_owner_bound"] = "true"
        return object : KeyValueStorage by delegate {
            override suspend fun getStringsOrThrow(keys: Set<String>) = keys.associateWith { delegate.values[it] }

            override suspend fun updateStringsOrThrow(values: Map<String, String?>) {
                for ((key, value) in values) {
                    if (value == null) delegate.values.remove(key) else delegate.values[key] = value
                }
            }

            override suspend fun compareAndSetStringsOrThrow(
                expected: Map<String, String?>,
                values: Map<String, String?>,
            ): Boolean {
                if (expected.any { (key, value) -> delegate.values[key] != value }) return false
                updateStringsOrThrow(values)
                return true
            }
        }
    }
}
