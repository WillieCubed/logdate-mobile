package app.logdate.server.atproto

import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.pds.CreateAccountRequest
import studio.hypertext.atproto.pds.CreateSessionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class AtprotoSessionServiceTest {
    @Test
    fun `create account provisions hosted identity password and session`() =
        runTest {
            val context = sessionContext()

            val session =
                context.service
                    .createAccount(
                        CreateAccountRequest(
                            email = "alice@example.com",
                            handle = "alice.logdate.app",
                            password = "pass-1",
                        ),
                    ).getOrThrow()
            val account = context.accountRepository.findByHandle("alice.logdate.app")
            val sessionInfo = context.service.getSession(session.accessJwt).getOrThrow()

            assertNotNull(account)
            assertTrue(context.passwordService.hasPassword(account.id))
            assertEquals("alice.logdate.app", session.handle)
            assertEquals("alice.logdate.app", sessionInfo.handle)
            assertEquals(account.did, session.did.toString())
            assertNotNull(session.didDoc)
            assertEquals("alice@example.com", session.email)
            assertTrue(session.emailConfirmed == true)
            assertTrue(session.accessJwt.isNotBlank())
            assertTrue(session.refreshJwt.isNotBlank())
        }

    @Test
    fun `create session refreshes and revokes hosted session tokens`() =
        runTest {
            val context = sessionContext()
            context.service
                .createAccount(
                    CreateAccountRequest(
                        email = "brie@example.com",
                        handle = "brie.logdate.app",
                        password = "pass-1",
                    ),
                ).getOrThrow()

            val session =
                context.service
                    .createSession(
                        CreateSessionRequest(
                            identifier = "brie@example.com",
                            password = "pass-1",
                        ),
                    ).getOrThrow()
            val refreshed = context.service.refreshSession(session.refreshJwt).getOrThrow()
            val invalidLogin =
                context.service
                    .createSession(
                        CreateSessionRequest(
                            identifier = "brie.logdate.app",
                            password = "wrong",
                        ),
                    ).exceptionOrNull()
            val sessionInfo = context.service.getSession(refreshed.accessJwt).getOrThrow()
            val deleted = context.service.deleteSession(refreshed.refreshJwt)
            val afterDelete = context.service.refreshSession(refreshed.refreshJwt).exceptionOrNull()

            assertEquals("brie.logdate.app", sessionInfo.handle)
            assertEquals("brie.logdate.app", refreshed.handle)
            assertNotNull(invalidLogin)
            assertTrue(deleted.isSuccess)
            assertNotNull(afterDelete)
        }

    private fun sessionContext(): SessionContext {
        val accountRepository = InMemoryAccountRepository()
        val passwordRepository = InMemoryAtprotoPasswordCredentialRepository()
        val sessionRepository = InMemoryAtprotoSessionRepository()
        val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "session-test-kek")
        val passwordService = AtprotoPasswordService(passwordRepository)
        val identityService =
            AtprotoIdentityService(
                accountRepository = accountRepository,
                signingKeyService = signingKeyService,
                config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
            )
        return SessionContext(
            accountRepository = accountRepository,
            passwordService = passwordService,
            service =
                AtprotoPdsSessionService(
                    accountRepository = accountRepository,
                    identityService = identityService,
                    passwordService = passwordService,
                    sessionTokenService = AtprotoSessionTokenService(sessionRepository, secret = "test"),
                ),
        )
    }

    private data class SessionContext(
        val accountRepository: InMemoryAccountRepository,
        val passwordService: AtprotoPasswordService,
        val service: AtprotoPdsSessionService,
    )
}
