package app.logdate.client.data.account

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WatchSignOutTest {
    private val fixtures = DefaultPasskeyAccountRepositoryTest()
    private val ownerId = Uuid.random().toString()
    private val session = UserSession("access", "refresh", ownerId)

    @Test
    fun `failed watch drain preserves phone session and skips remote sign out`() =
        runTest {
            val sessions = DefaultPasskeyAccountRepositoryTest.FakeSessionStorage().apply { saveSession(session) }
            val api = fixtures.FakePasskeyApiClient()
            val repository =
                DefaultPasskeyAccountRepository(
                    api,
                    DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
                    DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager(),
                    sessions,
                    DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    DefaultPasskeyAccountRepositoryTest.FakeConfigRepository(),
                    owner(),
                    { false },
                    repositoryScope = backgroundScope,
                    beforeSignOut = { false },
                )
            assertFalse(repository.signOut().isSuccess)
            assertEquals(session, sessions.getSession())
            assertNull(api.loggedOutRefreshToken)
        }

    @Test
    fun `watch removal runs before phone credentials are cleared`() =
        runTest {
            val sessions = DefaultPasskeyAccountRepositoryTest.FakeSessionStorage().apply { saveSession(session) }
            var removed = false
            val repository =
                DefaultPasskeyAccountRepository(
                    fixtures.FakePasskeyApiClient(),
                    DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
                    DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager(),
                    sessions,
                    DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    DefaultPasskeyAccountRepositoryTest.FakeConfigRepository(),
                    owner(),
                    { false },
                    repositoryScope = backgroundScope,
                    beforeSignOut = {
                        assertEquals(session, sessions.getSession())
                        removed = true
                        true
                    },
                )
            assertTrue(repository.signOut().isSuccess)
            assertTrue(removed)
            assertNull(sessions.getSession())
        }

    private fun owner() =
        object : CanonicalOwnerProvider {
            override suspend fun getCanonicalOwnerId() = ownerId

            override suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String) = remoteOwnerId == ownerId

            override suspend fun hasBoundOwner() = true
        }
}
