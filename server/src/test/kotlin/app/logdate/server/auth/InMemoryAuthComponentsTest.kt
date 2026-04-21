package app.logdate.server.auth

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests the in-memory implementations of core authentication and account repositories.
 *
 * This class ensures that the volatile storage components used for testing and
 * development correctly maintain account state, identity links, and temporary
 * sessions according to the defined service interfaces.
 */
@OptIn(ExperimentalUuidApi::class)
class InMemoryAuthComponentsTest {
    @Test
    fun `in-memory account repository supports lifecycle and indexes`() =
        runBlocking {
            val repository = InMemoryAccountRepository()
            val account =
                Account(
                    id = Uuid.random(),
                    username = "alice",
                    displayName = "Alice",
                    email = "alice@example.com",
                    emailVerified = true,
                    createdAt = Clock.System.now(),
                    isActive = true,
                )

            repository.save(account)
            assertNotNull(repository.findById(account.id))
            assertNotNull(repository.findByUsername("alice"))
            assertNotNull(repository.findByEmail("alice@example.com"))
            assertTrue(repository.usernameExists("alice"))
            assertTrue(repository.emailExists("alice@example.com"))
            assertEquals(1, repository.findByVerifiedEmail("ALICE@example.com").size)

            val renamed = account.copy(username = "alice_renamed", email = "alice2@example.com")
            repository.save(renamed)
            assertNull(repository.findByUsername("alice"))
            assertNull(repository.findByEmail("alice@example.com"))
            assertNotNull(repository.findByUsername("alice_renamed"))
            assertNotNull(repository.findByEmail("alice2@example.com"))

            assertTrue(repository.updateLastSignIn(account.id))
            assertFalse(repository.updateLastSignIn(Uuid.random()))

            assertTrue(repository.deactivateAccount(account.id))
            assertFalse(repository.deactivateAccount(Uuid.random()))
            assertTrue(repository.getAllAccounts().isEmpty())
            assertTrue(repository.getAccountsCreatedAfter(Clock.System.now().minus(1.seconds)).isEmpty())

            assertTrue(repository.deleteAccount(account.id))
            assertFalse(repository.deleteAccount(account.id))
        }

    @Test
    fun `in-memory account identity repository supports save lookup and updates`() =
        runBlocking {
            val repository = InMemoryAccountIdentityRepository()
            val accountId = Uuid.random()
            val now = Clock.System.now()
            val identity =
                AccountIdentity(
                    id = Uuid.random(),
                    accountId = accountId,
                    provider = IdentityProvider.GOOGLE,
                    providerSubject = "sub-1",
                    email = "id@example.com",
                    emailVerified = true,
                    createdAt = now,
                )

            val saved = repository.save(identity)
            assertEquals(identity.id, saved.id)
            assertNotNull(repository.findByProviderSubject(IdentityProvider.GOOGLE, "sub-1"))
            assertEquals(1, repository.findByAccountId(accountId).size)
            assertEquals(1, repository.findByVerifiedEmail("ID@example.com").size)

            val updated = repository.save(identity.copy(id = Uuid.random(), metadataJson = "{\"a\":1}"))
            assertEquals(saved.id, updated.id)
            assertTrue(repository.touchLastSignIn(updated.id))
            assertFalse(repository.touchLastSignIn(Uuid.random()))

            val event =
                AccountLinkEvent(
                    id = Uuid.random(),
                    accountId = accountId,
                    provider = IdentityProvider.GOOGLE,
                    providerSubject = "sub-1",
                    reason = "implicit-link",
                    createdAt = now,
                )
            val savedEvent = repository.saveLinkEvent(event)
            assertEquals(event.id, savedEvent.id)
        }

    @Test
    fun `in-memory session manager supports create validate use cleanup and removal`() =
        runBlocking {
            val manager = InMemorySessionManager()

            val accountSession =
                manager.createAccountCreationSession(
                    temporaryUserId = null,
                    username = "alice",
                    displayName = "Alice",
                    challenge = "challenge-1",
                    deviceInfo = DeviceInfo(platform = "android", deviceName = "pixel"),
                    bio = "bio",
                )

            assertNotNull(manager.getSession(accountSession.id))
            assertNotNull(manager.validateSession(accountSession.id, SessionType.ACCOUNT_CREATION))
            assertNull(manager.validateSession(accountSession.id, SessionType.AUTHENTICATION))

            assertTrue(manager.markSessionUsed(accountSession.id))
            assertFalse(manager.markSessionUsed("missing"))
            assertNull(manager.validateSession(accountSession.id, SessionType.ACCOUNT_CREATION))

            val authSession = manager.createAuthenticationSession(challenge = "challenge-2", accountHint = "alice", deviceInfo = null)
            assertNotNull(manager.validateSession(authSession.id, SessionType.AUTHENTICATION))

            assertTrue(manager.removeSession(authSession.id))
            assertFalse(manager.removeSession(authSession.id))

            val expired =
                TemporarySession(
                    id = "expired-session",
                    temporaryUserId = Uuid.random(),
                    challenge = "expired",
                    username = "u",
                    displayName = "d",
                    bio = null,
                    deviceInfo = null,
                    sessionType = SessionType.AUTHENTICATION,
                    createdAt = Clock.System.now().minus(30.seconds),
                    expiresAt = Clock.System.now().minus(5.seconds),
                    isUsed = false,
                )
            manager.storeSession(expired)
            assertNull(manager.getSession(expired.id))

            val used =
                TemporarySession(
                    id = "used-session",
                    temporaryUserId = Uuid.random(),
                    challenge = "used",
                    username = "u",
                    displayName = "d",
                    bio = null,
                    deviceInfo = null,
                    sessionType = SessionType.AUTHENTICATION,
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now().plus(30.seconds),
                    isUsed = true,
                )
            manager.storeSession(used)
            assertTrue(manager.cleanupExpiredSessions() >= 1)
        }

    @Test
    fun `session manager creates valid sessions through interface`() =
        runBlocking {
            val manager: SessionManager = InMemorySessionManager()

            val accountCreation =
                manager.createAccountCreationSession(
                    temporaryUserId = null,
                    username = "default_user",
                    displayName = "Default User",
                    challenge = "default-challenge",
                    deviceInfo = null,
                    bio = null,
                )
            assertEquals(SessionType.ACCOUNT_CREATION, accountCreation.sessionType)
            assertEquals("default_user", accountCreation.username)
            assertNull(accountCreation.deviceInfo)
            assertNull(accountCreation.bio)

            val authentication = manager.createAuthenticationSession(challenge = "auth-default", accountHint = null, deviceInfo = null)
            assertEquals(SessionType.AUTHENTICATION, authentication.sessionType)
            assertEquals("", authentication.username)
            assertNull(authentication.deviceInfo)
        }

    @Test
    fun `in-memory token service validates token types and expiration parsing`() {
        val service = InMemoryTokenService()

        val access = service.generateAccessToken("acc-1")
        val refresh = service.generateRefreshToken("acc-1")
        val session = service.generateSessionToken("session-1")

        assertEquals("acc-1", service.validateAccessToken(access))
        assertEquals("acc-1", service.validateRefreshToken(refresh))
        assertEquals("session-1", service.validateSessionToken(session))

        assertNull(service.validateAccessToken(""))
        assertNull(service.validateAccessToken("memory_bad"))
        assertNull(service.validateAccessToken("memory_hash_access.acc.not_a_number"))
        assertNull(service.validateRefreshToken(access))
    }
}
