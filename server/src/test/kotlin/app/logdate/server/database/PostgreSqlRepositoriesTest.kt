package app.logdate.server.database

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountIdentity
import app.logdate.server.auth.AccountLinkEvent
import app.logdate.server.auth.DeviceInfo
import app.logdate.server.auth.IdentityProvider
import app.logdate.server.auth.SessionType
import app.logdate.server.auth.TemporarySession
import app.logdate.server.database.support.withH2Database
import app.logdate.server.identity.StoredSigningKey
import app.logdate.shared.model.PasskeyInfo
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Comprehensive integration tests for core PostgreSQL-backed identity and session repositories.
 *
 * This suite provides broad coverage for the primary authentication and account management
 * components, including:
 * - [PostgreSQLAccountRepository]: Full account lifecycle (creation, lookup, deactivation, deletion).
 * - [PostgreSQLAccountIdentityRepository]: Management of external identity providers (e.g., Google)
 *   and their linkage to internal accounts.
 * - [PostgreSQLSigningKeyRepository]: Storage and revocation of account-level signing keys.
 * - [PostgreSQLPasskeyRepository]: Robust WebAuthn credential management, including signature
 *   counts and device metadata.
 * - [PostgreSQLSessionManager]: Lifecycle of temporary sessions for account creation and
 *   authentication flows, including automatic expiration and cleanup.
 */
@OptIn(ExperimentalUuidApi::class)
class PostgreSqlRepositoriesTest {
    @Test
    fun `passkey repository store catches transaction failures when no database is initialized`() {
        val repository = PostgreSQLPasskeyRepository()
        val info = samplePasskeyInfo("cred-no-db")

        val stored =
            runBlocking {
                repository.storePasskey(
                    userId = Uuid.random(),
                    credentialId = "cred-no-db",
                    publicKey = byteArrayOf(1),
                    signCount = 0L,
                    info = info,
                )
            }

        assertFalse(stored)
    }

    @Test
    fun `account repository supports full lifecycle`() {
        withAccountTables {
            val repository = PostgreSQLAccountRepository()
            val account =
                Account(
                    id = Uuid.random(),
                    username = "alice",
                    displayName = "Alice",
                    did = "did:web:alice.logdate.app",
                    handle = "alice.logdate.app",
                    signingKeyPublic = "zAliceKey",
                    email = "alice@example.com",
                    emailVerified = true,
                    bio = "bio",
                    createdAt = Clock.System.now(),
                    preferences = "{}",
                )

            repository.save(account)
            assertNotNull(repository.findById(account.id))
            assertNotNull(repository.findByDid("did:web:alice.logdate.app"))
            assertNotNull(repository.findByHandle("alice.logdate.app"))
            assertNotNull(repository.findByUsername("alice"))
            assertNotNull(repository.findByEmail("alice@example.com"))
            assertTrue(repository.usernameExists("alice"))
            assertTrue(repository.emailExists("alice@example.com"))
            assertEquals(1, repository.findByVerifiedEmail("alice@example.com").size)

            assertTrue(repository.updateLastSignIn(account.id))
            assertFalse(repository.updateLastSignIn(Uuid.random()))

            val renamed = account.copy(username = "alice2")
            repository.save(renamed)
            assertNull(repository.findByUsername("alice"))
            assertNotNull(repository.findByUsername("alice2"))

            val allActive = repository.getAllAccounts()
            assertEquals(1, allActive.size)
            assertTrue(repository.getAccountsCreatedAfter(account.createdAt.minus(1.seconds)).isNotEmpty())

            assertTrue(repository.deactivateAccount(account.id))
            assertFalse(repository.deactivateAccount(Uuid.random()))
            assertTrue(repository.getAllAccounts().isEmpty())

            assertTrue(repository.deleteAccount(account.id))
            assertFalse(repository.deleteAccount(account.id))
        }
    }

    @Test
    fun `account identity repository supports upsert and query operations`() {
        withAccountIdentityTables {
            val repository = PostgreSQLAccountIdentityRepository()
            val now = Clock.System.now()
            val accountId = Uuid.random()
            val linkedAccountId = Uuid.random()
            PostgreSQLAccountRepository().save(sampleAccount(accountId, username = "identity-owner"))
            PostgreSQLAccountRepository().save(sampleAccount(linkedAccountId, username = "identity-owner-linked"))

            val identity =
                AccountIdentity(
                    id = Uuid.random(),
                    accountId = accountId,
                    provider = IdentityProvider.GOOGLE,
                    providerSubject = "google-subject",
                    email = "identity@example.com",
                    emailVerified = true,
                    createdAt = now,
                    metadataJson = "{}",
                )

            val saved = repository.save(identity)
            assertEquals(identity.id, saved.id)

            val updated = repository.save(identity.copy(accountId = linkedAccountId, metadataJson = "{\"v\":2}"))
            assertEquals(saved.id, updated.id)

            val byProvider = repository.findByProviderSubject(IdentityProvider.GOOGLE, "google-subject")
            assertNotNull(byProvider)
            assertEquals("google-subject", byProvider.providerSubject)

            assertEquals(1, repository.findByAccountId(updated.accountId).size)
            assertEquals(1, repository.findByVerifiedEmail("identity@example.com").size)
            assertTrue(repository.touchLastSignIn(updated.id))
            assertFalse(repository.touchLastSignIn(Uuid.random()))

            val linkEvent =
                AccountLinkEvent(
                    id = Uuid.random(),
                    accountId = updated.accountId,
                    provider = IdentityProvider.GOOGLE,
                    providerSubject = "google-subject",
                    reason = "implicit-link",
                    createdAt = now,
                    metadataJson = "{}",
                )
            val persistedEvent = repository.saveLinkEvent(linkEvent)
            assertEquals(linkEvent.id, persistedEvent.id)
        }
    }

    @Test
    fun `signing key repository supports save lookup and revoke`() {
        withH2Database(AccountsTable, SigningKeysTable) {
            val accountId = Uuid.random()
            val accountRepository = PostgreSQLAccountRepository()
            val repository = PostgreSQLSigningKeyRepository()

            val stored =
                StoredSigningKey(
                    id = Uuid.random(),
                    accountId = accountId,
                    publicKeyMultibase = "zSigningKey",
                    privateKeyEncrypted = "encrypted",
                    createdAt = Clock.System.now(),
                )

            runBlocking {
                accountRepository.save(sampleAccount(accountId, username = "signing-owner"))
                repository.save(stored)
                assertEquals(stored.publicKeyMultibase, repository.findActiveByAccountId(accountId)?.publicKeyMultibase)
                assertEquals(1, repository.revokeActiveKeys(accountId))
                assertNull(repository.findActiveByAccountId(accountId))
            }
        }
    }

    @Test
    fun `passkey repository supports storage lookup updates and utility methods`() {
        withPasskeyTables {
            val accountId = Uuid.random()
            val repository = PostgreSQLPasskeyRepository()
            PostgreSQLAccountRepository().save(sampleAccount(accountId, username = "passkey-owner"))
            val info = samplePasskeyInfo("cred-a")

            assertTrue(
                repository.storePasskey(
                    userId = accountId,
                    credentialId = "cred-a",
                    publicKey = byteArrayOf(1, 2, 3),
                    signCount = 1,
                    info = info,
                ),
            )

            assertTrue(repository.credentialExists("cred-a"))
            assertFalse(repository.credentialExists("missing"))
            assertTrue(repository.credentialBelongsToUser("cred-a", accountId))
            assertFalse(repository.credentialBelongsToUser("cred-a", Uuid.random()))

            val byCredential = repository.getPasskeyByCredentialId("cred-a")
            assertNotNull(byCredential)
            assertEquals(accountId, byCredential.first)
            assertEquals(1L, byCredential.second.signCount)

            assertEquals(1, repository.getPasskeysForUser(accountId).size)
            assertEquals(listOf("cred-a"), repository.getCredentialIdsForUser(accountId))

            assertTrue(repository.updateSignCount("cred-a", 7))
            assertFalse(repository.updateSignCount("missing", 1))

            assertNotNull(repository.findByCredentialId("cred-a"))
            assertNotNull(repository.findById(info.id))
            assertEquals(1, repository.findByAccountId(accountId).size)
            assertEquals(1, repository.findActiveByAccountId(accountId).size)
            assertEquals(listOf("cred-a"), repository.getCredentialIdsForAccount(accountId))
            assertTrue(repository.updateLastUsed("cred-a"))

            // Cover decode fallback path by storing non-base64 public key.
            val plainInfo = samplePasskeyInfo("cred-b")
            repository.saveWithWebAuthnData(
                accountId = accountId,
                passkey = plainInfo,
                publicKey = "@@not-base64@@",
                signCount = 5,
                webauthnData = "{\"uv\":true}",
            )
            repository.saveWithWebAuthnData(
                accountId = accountId,
                passkey = samplePasskeyInfo("cred-c"),
                publicKey = "@@also-not-base64@@",
                signCount = 3,
            )
            val plainStored = repository.getPasskeyByCredentialId("cred-b")
            assertNotNull(plainStored)
            assertTrue(plainStored.second.publicKey.contentEquals("@@not-base64@@".toByteArray()))

            val webAuthnData = repository.getWebAuthnData("cred-b")
            assertNotNull(webAuthnData)
            assertEquals("@@not-base64@@", webAuthnData.first)
            assertEquals(5L, webAuthnData.second)

            assertFalse(repository.deactivatePasskey("cred-a", Uuid.random()))
            assertTrue(repository.deactivatePasskey("cred-a", accountId))
            assertNull(repository.getPasskeyByCredentialId("cred-a"))

            assertTrue(repository.deletePasskey(plainInfo.id))
            assertFalse(repository.deletePasskey(plainInfo.id))
        }
    }

    @Test
    fun `session manager handles creation validation expiration and cleanup`() {
        withSessionTables {
            val repository = PostgreSQLSessionManager()
            val now = Clock.System.now()

            val accountCreation =
                repository.createAccountCreationSession(
                    temporaryUserId = null,
                    username = "alice",
                    displayName = "Alice",
                    challenge = "challenge-a",
                    deviceInfo = DeviceInfo(platform = "android", deviceName = "Pixel"),
                    bio = "bio",
                )
            assertNotNull(repository.getSession(accountCreation.id))
            assertNotNull(repository.validateSession(accountCreation.id, SessionType.ACCOUNT_CREATION))
            assertNull(repository.validateSession(accountCreation.id, SessionType.AUTHENTICATION))

            assertTrue(repository.markSessionUsed(accountCreation.id))
            assertNull(repository.validateSession(accountCreation.id, SessionType.ACCOUNT_CREATION))
            assertFalse(repository.markSessionUsed("missing"))

            val authSession =
                repository.createAuthenticationSession(
                    challenge = "challenge-b",
                    accountHint = null,
                    deviceInfo = null,
                )
            assertEquals("", authSession.username)
            assertNotNull(repository.validateSession(authSession.id, SessionType.AUTHENTICATION))
            assertTrue(repository.removeSession(authSession.id))
            assertFalse(repository.removeSession(authSession.id))
            assertNull(repository.getSession("missing-session"))

            // Store an expired session to cover expiry removal path.
            val expired =
                TemporarySession(
                    id = "expired-session",
                    temporaryUserId = Uuid.random(),
                    challenge = "challenge-expired",
                    username = "u",
                    displayName = "d",
                    bio = null,
                    deviceInfo = null,
                    sessionType = SessionType.AUTHENTICATION,
                    createdAt = now.minus(10.seconds),
                    expiresAt = now.minus(1.seconds),
                    isUsed = false,
                )
            repository.storeSession(expired)
            assertNull(repository.getSession(expired.id))

            // Add one used and one expired session to exercise cleanup logic.
            val usedSession =
                TemporarySession(
                    id = "used-session",
                    temporaryUserId = Uuid.random(),
                    challenge = "challenge-used",
                    username = "u",
                    displayName = "d",
                    bio = null,
                    deviceInfo = null,
                    sessionType = SessionType.AUTHENTICATION,
                    createdAt = now,
                    expiresAt = now.plus(5.minutes),
                    isUsed = true,
                )
            repository.storeSession(usedSession)

            val expired2 =
                TemporarySession(
                    id = "expired-session-2",
                    temporaryUserId = Uuid.random(),
                    challenge = "challenge-expired-2",
                    username = "u",
                    displayName = "d",
                    bio = null,
                    deviceInfo = null,
                    sessionType = SessionType.AUTHENTICATION,
                    createdAt = now.minus(10.minutes),
                    expiresAt = now.minus(2.minutes),
                    isUsed = false,
                )
            repository.storeSession(expired2)

            // Insert a row with nullable username/displayName to cover null fallback mapping.
            val nullableRowSessionId = "nullable-fields-session"
            transaction {
                SessionsTable.insert {
                    it[id] = nullableRowSessionId
                    it[temporaryUserId] = Uuid.random().toJavaUUID()
                    it[challenge] = "c-null"
                    it[sessionType] = SessionType.AUTHENTICATION.name
                    it[username] = null
                    it[displayName] = null
                    it[bio] = null
                    it[deviceInfo] = null
                    val now = Clock.System.now()
                    it[createdAt] = now
                    it[expiresAt] = now + 5.minutes
                    it[isUsed] = false
                }
            }
            val nullableRowSession = repository.getSession(nullableRowSessionId)
            assertNotNull(nullableRowSession)
            assertEquals("", nullableRowSession.username)
            assertEquals("", nullableRowSession.displayName)

            assertTrue(repository.cleanupExpiredSessions() >= 2)
        }
    }

    private fun withAccountTables(block: suspend () -> Unit) {
        withH2Database(AccountsTable) {
            runBlocking { block() }
        }
    }

    private fun withAccountIdentityTables(block: suspend () -> Unit) {
        withH2Database(AccountsTable, AccountIdentitiesTable, AccountLinkEventsTable) {
            runBlocking { block() }
        }
    }

    private fun withPasskeyTables(block: suspend () -> Unit) {
        withH2Database(AccountsTable, PasskeysTable) {
            runBlocking { block() }
        }
    }

    private fun withSessionTables(block: suspend () -> Unit) {
        withH2Database(SessionsTable) {
            runBlocking { block() }
        }
    }

    private fun samplePasskeyInfo(credentialId: String): PasskeyInfo =
        PasskeyInfo(
            id = Uuid.random(),
            credentialId = credentialId,
            nickname = "phone",
            deviceType = "platform",
            createdAt = Clock.System.now(),
            lastUsedAt = null,
            isActive = true,
        )

    private fun sampleAccount(
        id: Uuid,
        username: String,
    ): Account =
        Account(
            id = id,
            username = username,
            displayName = username,
            email = "$username@example.com",
            emailVerified = true,
            bio = null,
            createdAt = Clock.System.now(),
            preferences = "{}",
            isActive = true,
        )
}
