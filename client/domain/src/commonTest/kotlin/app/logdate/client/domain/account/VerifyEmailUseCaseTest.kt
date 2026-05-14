package app.logdate.client.domain.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.permissions.EmailVerificationManager
import app.logdate.client.permissions.EmailVerificationOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class VerifyEmailUseCaseTest {
    private val now: Instant = Instant.fromEpochSeconds(1_775_083_422)

    @Test
    fun `returns Unsupported when manager reports unsupported`() =
        runTest {
            val useCase = useCase(manager = FakeManager(EmailVerificationOutcome.Unsupported))
            assertEquals(EmailVerificationOutcome.Unsupported, useCase())
        }

    @Test
    fun `returns not signed in when no session`() =
        runTest {
            val useCase = useCase(session = null)
            val result = useCase()
            assertTrue(result is EmailVerificationOutcome.Failed, "got $result")
            assertEquals("not_signed_in", result.reason)
        }

    @Test
    fun `passes through Success`() =
        runTest {
            val useCase =
                useCase(
                    manager = FakeManager(EmailVerificationOutcome.Success("u@example.com", now)),
                )
            val result = useCase()
            val success = assertNotNull(result as? EmailVerificationOutcome.Success)
            assertEquals("u@example.com", success.email)
            assertEquals(now, success.verifiedAt)
        }

    @Test
    fun `passes through Conflict`() =
        runTest {
            val useCase = useCase(manager = FakeManager(EmailVerificationOutcome.Conflict("already attached")))
            val result = useCase()
            val conflict = assertNotNull(result as? EmailVerificationOutcome.Conflict)
            assertEquals("already attached", conflict.message)
        }

    @Test
    fun `passes through UserCancelled`() =
        runTest {
            val useCase = useCase(manager = FakeManager(EmailVerificationOutcome.UserCancelled))
            assertEquals(EmailVerificationOutcome.UserCancelled, useCase())
        }

    @Test
    fun `passes through Failed reason codes`() =
        runTest {
            val useCase = useCase(manager = FakeManager(EmailVerificationOutcome.Failed("issuer_signature_invalid")))
            val result = useCase()
            val failed = assertNotNull(result as? EmailVerificationOutcome.Failed)
            assertEquals("issuer_signature_invalid", failed.reason)
        }

    private fun useCase(
        manager: EmailVerificationManager = FakeManager(EmailVerificationOutcome.Unsupported),
        session: UserSession? =
            UserSession(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                accountId = "00000000-0000-0000-0000-000000000001",
            ),
    ) = VerifyEmailUseCase(FakeSessionStorage(session), manager)

    private class FakeManager(
        private val outcome: EmailVerificationOutcome,
    ) : EmailVerificationManager {
        override val isSupported: Boolean = outcome !is EmailVerificationOutcome.Unsupported

        override suspend fun verifyEmail(accessToken: String): EmailVerificationOutcome = outcome
    }

    private class FakeSessionStorage(
        private val session: UserSession?,
    ) : SessionStorage {
        override fun getSession(): UserSession? = session

        override fun getSessionFlow() = kotlinx.coroutines.flow.flowOf(session)

        override suspend fun hasValidSession(): Boolean = session != null

        override fun saveSession(session: UserSession) = Unit

        override fun clearSession() = Unit
    }
}
