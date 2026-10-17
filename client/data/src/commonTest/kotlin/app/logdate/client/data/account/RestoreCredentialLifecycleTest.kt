package app.logdate.client.data.account

import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountInfo
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.PasskeyRegistrationOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RestoreCredentialLifecycleTest {
    private class Fixture(
        scope: CoroutineScope,
    ) {
        val origin = "https://restore.example"
        val storage = RenewalStorage()
        val preferences = mutableMapOf<String, String>()
        var policyRead: (suspend () -> Unit)? = null
        val policy =
            NativeRestorePolicy({ key ->
                val decision = preferences[key]
                val hook = policyRead
                policyRead = null
                hook?.invoke()
                decision
            }, { key, value -> preferences[key] = value })
        val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
        val sessions = SecureSessionStorage(storage, config, scope, nativeRestorePolicy = policy)
        val account = UserSession("access", "refresh", "owner")
        var platform: PlatformAccountManager? = null
        var failCreate = false
        var failClear = false
        var clearGate: CompletableDeferred<Unit>? = null
        var createGate: CompletableDeferred<Unit>? = null
        var clears = 0
        var creates = 0
        val scheduled = mutableListOf<String>()
        val delegate = DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager()
        val provider =
            object : RestoreCredentialManager by delegate {
                override suspend fun clearRestoreCredential(): Result<Unit> {
                    clears++
                    clearGate?.await()
                    return if (failClear) Result.failure(IllegalStateException("provider unavailable")) else Result.success(Unit)
                }

                override suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String> {
                    creates++
                    createGate?.await()
                    return if (failCreate) {
                        Result.failure(
                            IllegalStateException("Provider unavailable"),
                        )
                    } else {
                        delegate.createRestoreKey(options)
                    }
                }
            }

        fun lifecycle(
            secure: SecureStorage = storage,
            sessionStore: SessionStorage = sessions,
        ) = RestoreCredentialLifecycle(
            provider,
            secure,
            sessionStore,
            policy,
            config,
            RestoreCredentialCleanupScheduler {
                scheduled +=
                    it
            },
            platformAccounts = platform,
        )

        suspend fun signOut(lifecycle: RestoreCredentialLifecycle) {
            sessions.saveExplicitSession(account)
            lifecycle.prepareClear(origin) { true }
            sessions.clearSession()
        }

        suspend fun options() =
            DefaultPasskeyAccountRepositoryTest().FakePasskeyApiClient().beginRestoreKeyRegistration("access").getOrThrow()
    }

    @Test
    fun `reconciliation repairs cleanup for a disabled provider origin after switching away`() =
        runTest {
            val f = Fixture(backgroundScope)
            f.sessions.saveExplicitSession(f.account)
            f.lifecycle().createRestoreKey(f.options()).getOrThrow()
            f.lifecycle().prepareClear(f.origin) { true }
            f.sessions.clearSession()
            val old = assertNotNull(f.lifecycle().pendingCleanupId())
            val other = "https://rejected-refresh.example"
            f.config.updateBackendUrl(other)
            f.sessions.saveExplicitSession(f.account.copy(refreshToken = "other-server"))
            f.failCreate = true
            assertTrue(f.lifecycle().createRestoreKey(f.options()).isFailure)
            f.sessions.clearSession()
            f.config.updateBackendUrl(f.origin)
            assertTrue(f.lifecycle().cleanup(old))
            f.lifecycle().reconcile()
            val repaired = assertNotNull(f.lifecycle().pendingCleanupId())
            assertTrue(f.lifecycle().cleanup(repaired))
            assertEquals(1, f.clears)
            f.scheduled.clear()
            f.lifecycle().reconcile()
            assertTrue(f.scheduled.isEmpty())
        }

    @Test
    fun `uncertain provider ownership clears after every candidate server signs out`() =
        runTest {
            val f = Fixture(backgroundScope)
            f.sessions.saveExplicitSession(f.account)
            f.lifecycle().createRestoreKey(f.options()).getOrThrow()
            val other = "https://uncertain.example"
            f.config.updateBackendUrl(other)
            f.sessions.saveExplicitSession(f.account.copy(refreshToken = "other-server"))
            f.failCreate = true
            assertTrue(f.lifecycle().createRestoreKey(f.options()).isFailure)
            f.lifecycle().prepareClear(other) { true }
            f.sessions.clearSession()
            assertTrue(f.lifecycle().cleanup(assertNotNull(f.lifecycle().pendingCleanupId())))
            assertEquals(0, f.clears)
            f.config.updateBackendUrl(f.origin)
            f.lifecycle().prepareClear(f.origin) { true }
            f.sessions.clearSession()
            assertTrue(f.lifecycle().cleanup(assertNotNull(f.lifecycle().pendingCleanupId())))
            assertEquals(1, f.clears)
            f.scheduled.clear()
            f.lifecycle().reconcile()
            assertTrue(f.scheduled.isEmpty())
        }

    @Test
    fun `signing out another retained server cannot delete the known provider owners key`() =
        runTest {
            val f = Fixture(backgroundScope)
            f.sessions.saveExplicitSession(f.account)
            f.lifecycle().createRestoreKey(f.options()).getOrThrow()
            val other = "https://other-retained.example"
            f.config.updateBackendUrl(other)
            f.sessions.saveExplicitSession(f.account.copy(refreshToken = "other-server-session"))
            val lifecycle = f.lifecycle()
            lifecycle.prepareClear(other) { true }
            f.sessions.clearSession()
            assertTrue(lifecycle.cleanup(assertNotNull(lifecycle.pendingCleanupId())))
            assertEquals(f.account, f.sessions.read(f.origin))
            assertEquals(0, f.clears)
            f.scheduled.clear()
            lifecycle.reconcile()
            lifecycle.reconcile()
            assertTrue(f.scheduled.isEmpty())
        }

    @Test
    fun `old origin platform cleanup survives enrollment on another server`() =
        runTest {
            val f = Fixture(backgroundScope)
            var removed = 0
            f.platform =
                object : PlatformAccountManager by DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager() {
                    override suspend fun getStoredAccounts() =
                        Result.success(
                            listOf(
                                PlatformAccountInfo("old-user", "Old user", "owner", f.origin),
                            ),
                        )

                    override suspend fun getTokens(
                        username: String,
                        backendUrl: String,
                    ) = Result.success(null)

                    override suspend fun removeAccount(
                        username: String,
                        backendUrl: String,
                    ): Result<Unit> {
                        assertEquals(f.origin, backendUrl)
                        removed++
                        return Result.success(Unit)
                    }
                }
            val lifecycle = f.lifecycle()
            f.signOut(lifecycle)
            val oldId = assertNotNull(lifecycle.pendingCleanupId())
            f.config.updateBackendUrl("https://another.example")
            val newer = f.account.copy(refreshToken = "another-server-login")
            f.sessions.saveExplicitSession(newer)
            f.lifecycle().createRestoreKey(f.options()).getOrThrow()
            assertTrue(f.lifecycle().cleanup(oldId))
            assertEquals(1, removed)
            assertEquals(newer, f.sessions.getSession())
            assertEquals(0, f.clears)
        }

    @Test
    fun `cleanup retains separate obligations for sign outs on different servers`() =
        runTest {
            val f = Fixture(backgroundScope)
            val lifecycle = f.lifecycle()
            f.signOut(lifecycle)
            val first = assertNotNull(lifecycle.pendingCleanupId())
            val secondOrigin = "https://another.example"
            f.config.updateBackendUrl(secondOrigin)
            f.sessions.saveExplicitSession(f.account.copy(refreshToken = "second-server"))
            f.lifecycle().prepareClear(secondOrigin) { true }
            f.sessions.clearSession()
            val second = assertNotNull(lifecycle.pendingCleanupId())
            f.lifecycle().reconcile()
            assertTrue(f.scheduled.containsAll(listOf(first, second)))
            assertTrue(lifecycle.cleanup(first))
            assertEquals(second, lifecycle.pendingCleanupId())
            assertTrue(lifecycle.cleanup(second))
            assertNull(lifecycle.pendingCleanupId())
        }

    @Test
    fun `login during cleanup preparation cannot become its sign out target`() =
        runTest {
            val f = Fixture(backgroundScope)
            f.sessions.saveExplicitSession(f.account)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            f.policyRead = {
                entered.complete(Unit)
                release.await()
            }
            val lifecycle = f.lifecycle()
            val preparing =
                async {
                    runCatching {
                        lifecycle.prepareClear(f.origin) { f.sessions.getSession() == f.account }
                    }
                }
            entered.await()
            val newer = f.account.copy(refreshToken = "later-login")
            f.sessions.saveExplicitSession(newer)
            release.complete(Unit)
            assertTrue(preparing.await().isFailure)
            assertNull(lifecycle.pendingCleanupId())
            assertEquals(newer, f.sessions.getSession())
        }

    @Test
    fun `later login retires a failed finalized cleanup without enrolling a key`() =
        runTest {
            val f = Fixture(backgroundScope)
            val lifecycle = f.lifecycle()
            f.signOut(lifecycle)
            val id = assertNotNull(lifecycle.pendingCleanupId())
            f.failClear = true
            assertFalse(lifecycle.cleanup(id))
            f.sessions.saveExplicitSession(f.account.copy(refreshToken = "later-login"))
            assertTrue(f.lifecycle().cleanup(id))
            assertNull(lifecycle.pendingCleanupId())
            assertEquals(1, f.clears)
        }

    @Test
    fun `unreadable current origin session keeps old origin cleanup pending`() =
        runTest {
            val f = Fixture(backgroundScope)
            val lifecycle = f.lifecycle()
            f.signOut(lifecycle)
            val id = assertNotNull(lifecycle.pendingCleanupId())
            val failing =
                object : SecureStorage by f.storage {
                    override suspend fun getString(key: String): String? {
                        if (key.startsWith("session_record_v2")) error("Keystore temporarily unavailable")
                        return f.storage.getString(key)
                    }
                }
            val sessions = SecureSessionStorage(failing, f.config, backgroundScope, nativeRestorePolicy = f.policy)
            assertFailsWith<IllegalStateException> { f.lifecycle(failing, sessions).cleanup(id) }
            assertEquals(id, lifecycle.pendingCleanupId())
            assertEquals(0, f.clears)
        }

    @Test
    fun `worker finishes sign out interrupted before local credentials cleared`() =
        runTest {
            val f = Fixture(backgroundScope)
            f.sessions.saveExplicitSession(f.account)
            f.lifecycle().prepareClear(f.origin) { true }
            val restarted = f.lifecycle()
            restarted.reconcile()
            val id = assertNotNull(restarted.pendingCleanupId())
            assertTrue(restarted.cleanup(id))
            assertNull(f.sessions.getSession())
            assertFalse(NativeRestorePolicy.allows(f.policy.decision(f.origin)))
            assertEquals(1, f.clears)
        }

    @Test
    fun `interrupted sign out cannot clear a later explicit login`() =
        runTest {
            val f = Fixture(backgroundScope)
            f.sessions.saveExplicitSession(f.account)
            val old = f.lifecycle()
            old.prepareClear(f.origin) { true }
            val id = assertNotNull(old.pendingCleanupId())
            val newer = f.account.copy(refreshToken = "new-login")
            f.sessions.saveExplicitSession(newer)
            assertTrue(f.lifecycle().cleanup(id))
            assertEquals(newer, f.sessions.getSession())
            assertEquals(0, f.clears)
        }

    @Test
    fun `new creation waits for an already started cleanup`() =
        runTest {
            val f = Fixture(backgroundScope)
            val lifecycle = f.lifecycle()
            f.signOut(lifecycle)
            val id = assertNotNull(lifecycle.pendingCleanupId())
            f.clearGate = CompletableDeferred()
            val clearing = async { lifecycle.cleanup(id) }
            runCurrent()
            assertEquals(1, f.clears)
            f.sessions.saveExplicitSession(f.account.copy(refreshToken = "new-refresh"))
            val creating = async { f.lifecycle().createRestoreKey(f.options()) }
            runCurrent()
            assertEquals(0, f.creates)
            f.clearGate!!.complete(Unit)
            assertTrue(clearing.await())
            creating.await().getOrThrow()
            assertEquals(1, f.creates)
            assertTrue(lifecycle.cleanup(id))
            assertEquals(1, f.clears)
        }

    @Test
    fun `sign out waits for already started creation before recording cleanup`() =
        runTest {
            val f = Fixture(backgroundScope)
            f.sessions.saveExplicitSession(f.account)
            val lifecycle = f.lifecycle()
            f.createGate = CompletableDeferred()
            val creating = async { lifecycle.createRestoreKey(f.options()) }
            runCurrent()
            val preparing = async { f.lifecycle().prepareClear(f.origin) { true } }
            runCurrent()
            assertFalse(preparing.isCompleted)
            f.createGate!!.complete(Unit)
            creating.await().getOrThrow()
            preparing.await()
            f.sessions.clearSession()
            assertTrue(lifecycle.cleanup(assertNotNull(lifecycle.pendingCleanupId())))
            assertEquals(1, f.clears)
        }

    @Test
    fun `cancelled cleanup holds serialization until the provider finishes`() =
        runTest {
            val f = Fixture(backgroundScope)
            val lifecycle = f.lifecycle()
            f.signOut(lifecycle)
            val id = assertNotNull(lifecycle.pendingCleanupId())
            f.clearGate = CompletableDeferred()
            val clearing = async { lifecycle.cleanup(id) }
            runCurrent()
            clearing.cancel()
            f.sessions.saveExplicitSession(f.account.copy(refreshToken = "new-refresh"))
            val creating = async { f.lifecycle().createRestoreKey(f.options()) }
            runCurrent()
            assertEquals(0, f.creates)
            f.clearGate!!.complete(Unit)
            clearing.join()
            creating.await().getOrThrow()
            assertTrue(lifecycle.cleanup(id))
            assertEquals(1, f.clears)
        }

    @Test
    fun `unreadable lifecycle metadata repairs without losing signed out cleanup`() =
        runTest {
            val f = Fixture(backgroundScope)
            f.sessions.clearSession()
            f.storage.putString("restore_provider_lifecycle_v1", "broken metadata")
            val lifecycle = f.lifecycle()
            lifecycle.reconcile()
            val id = assertNotNull(lifecycle.pendingCleanupId())
            assertEquals(listOf(id), f.scheduled)
            assertTrue(lifecycle.cleanup(id))
            assertEquals(1, f.clears)
        }

    @Test
    fun `a worker for an older repeated sign out cannot acknowledge the newer ticket`() =
        runTest {
            val f = Fixture(backgroundScope)
            val lifecycle = f.lifecycle()
            f.signOut(lifecycle)
            val old = assertNotNull(lifecycle.pendingCleanupId())
            lifecycle.prepareClear(f.origin) { true }
            f.sessions.clearSession()
            val newer = assertNotNull(lifecycle.pendingCleanupId())
            assertTrue(lifecycle.cleanup(old))
            assertEquals(newer, lifecycle.pendingCleanupId())
            assertEquals(0, f.clears)
            assertTrue(lifecycle.cleanup(newer))
        }

    @Test
    fun `failed provider clearing survives recreation and resolves only after success`() =
        runTest {
            val f = Fixture(backgroundScope)
            val lifecycle = f.lifecycle()
            f.signOut(lifecycle)
            f.failClear = true
            assertFalse(lifecycle.clearRestoreCredential().isSuccess)
            val id = assertNotNull(lifecycle.pendingCleanupId())
            val restarted = f.lifecycle()
            assertEquals(id, restarted.pendingCleanupId())
            f.failClear = false
            assertTrue(restarted.cleanup(id))
            assertNull(restarted.pendingCleanupId())
            val scheduled = f.scheduled.size
            restarted.reconcile()
            assertEquals(scheduled, f.scheduled.size)
            assertEquals(2, f.clears)
        }

    @Test
    fun `new provider creation supersedes an old cleanup ticket`() =
        runTest {
            val f = Fixture(backgroundScope)
            val lifecycle = f.lifecycle()
            f.signOut(lifecycle)
            val old = assertNotNull(lifecycle.pendingCleanupId())
            f.sessions.saveExplicitSession(f.account.copy(refreshToken = "new-refresh"))
            lifecycle.createRestoreKey(f.options()).getOrThrow()
            assertTrue(f.lifecycle().cleanup(old))
            assertEquals(1, f.creates)
            assertEquals(0, f.clears)
            assertNull(lifecycle.pendingCleanupId())
        }

    @Test
    fun `startup repairs interruption after sign out and before scheduling`() =
        runTest {
            val f = Fixture(backgroundScope)
            f.signOut(f.lifecycle())
            val restarted = f.lifecycle()
            restarted.reconcile()
            val id = assertNotNull(restarted.pendingCleanupId())
            assertEquals(listOf(id), f.scheduled)
            assertTrue(restarted.cleanup(id))
            assertEquals(1, f.clears)
            restarted.reconcile()
            assertEquals(listOf(id), f.scheduled)
        }
}
