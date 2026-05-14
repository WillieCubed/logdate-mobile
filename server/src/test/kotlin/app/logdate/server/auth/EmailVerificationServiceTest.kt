package app.logdate.server.auth

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Service-level tests. The verifier is stubbed: real signature math is exercised
 * by [DigitalCredentialVerifierTest]. This test exists to pin the service's
 * orchestration — nonce burning, account lookup, conflict detection, error mapping.
 */
@OptIn(ExperimentalUuidApi::class)
class EmailVerificationServiceTest {
    private val accountId = Uuid.random()
    private val expectedAudience = "https://logdate.app/auth/email"
    private val fixedNow: Instant = Instant.fromEpochSeconds(1_775_083_422)

    @Test
    fun `begin persists a single-use challenge`(): Unit =
        runBlocking {
            val (service, pending, _) = newService()

            val challenge1 = service.begin(accountId)
            val challenge2 = service.begin(accountId)

            assertEquals(accountId, challenge1.accountId)
            assertEquals(fixedNow + 5.minutes, challenge1.expiresAt)
            // Each call gets a fresh nonce + txid.
            assertTrue(challenge1.nonce != challenge2.nonce)
            assertTrue(challenge1.transactionId != challenge2.transactionId)
            // Both rows are present until consumed.
            assertNotNull(pending.consume(challenge1.transactionId))
            assertNotNull(pending.consume(challenge2.transactionId))
        }

    @Test
    fun `complete returns Success and attaches the verified email`(): Unit =
        runBlocking {
            val (service, _, accounts) =
                newService(
                    verifier = stubVerifierAcceptingAnyNonce("u@example.com"),
                )
            accounts.save(accountFor(accountId, email = null, emailVerified = false))
            val challenge = service.begin(accountId)

            val result = service.complete(accountId, challenge.transactionId, "{}")

            assertTrue(result is EmailVerificationService.Result.Success, "expected Success, got $result")
            assertEquals("u@example.com", result.email)
            assertEquals(fixedNow, result.emailVerifiedAt)
            val refreshed = assertNotNull(accounts.findById(accountId))
            assertEquals("u@example.com", refreshed.email)
            assertTrue(refreshed.emailVerified)
            assertEquals(fixedNow, refreshed.emailVerifiedAt)
        }

    @Test
    fun `complete refuses an unknown transaction id`(): Unit =
        runBlocking {
            val (service, _, _) = newService()
            val result = service.complete(accountId, Uuid.random(), "{}")
            assertTrue(result is EmailVerificationService.Result.Failed)
            assertEquals("challenge_missing_or_consumed", result.reason)
        }

    @Test
    fun `complete refuses a second use of the same transaction id`(): Unit =
        runBlocking {
            val (service, _, accounts) =
                newService(
                    verifier = stubVerifierAcceptingAnyNonce("u@example.com"),
                )
            accounts.save(accountFor(accountId))
            val challenge = service.begin(accountId)

            val first = service.complete(accountId, challenge.transactionId, "{}")
            val second = service.complete(accountId, challenge.transactionId, "{}")

            assertTrue(first is EmailVerificationService.Result.Success)
            assertTrue(second is EmailVerificationService.Result.Failed)
            assertEquals("challenge_missing_or_consumed", second.reason)
        }

    @Test
    fun `complete refuses a challenge bound to a different account`(): Unit =
        runBlocking {
            val (service, _, accounts) =
                newService(
                    verifier = stubVerifierAcceptingAnyNonce("u@example.com"),
                )
            accounts.save(accountFor(accountId))
            val challenge = service.begin(accountId = Uuid.random()) // bound to someone else

            val result = service.complete(accountId, challenge.transactionId, "{}")

            assertTrue(result is EmailVerificationService.Result.Failed)
            assertEquals("challenge_account_mismatch", result.reason)
        }

    @Test
    fun `complete refuses an expired challenge`(): Unit =
        runBlocking {
            var nowProvider: () -> Instant = { fixedNow }
            val service =
                EmailVerificationService(
                    pendingRepository = InMemoryPendingEmailVerificationRepository(),
                    accountRepository = InMemoryAccountRepository().also { it.save(accountFor(accountId)) },
                    verifier = stubVerifierAcceptingAnyNonce("u@example.com"),
                    clock = { nowProvider() },
                    secureRandom = SecureRandom("seed".toByteArray()),
                )
            val challenge = service.begin(accountId)
            // jump forward past TTL
            nowProvider = { fixedNow + 1.hours }

            val result = service.complete(accountId, challenge.transactionId, "{}")

            assertTrue(result is EmailVerificationService.Result.Failed)
            assertEquals("challenge_expired", result.reason)
        }

    @Test
    fun `complete surfaces the verifier's reason code on signature failure`(): Unit =
        runBlocking {
            val (service, _, accounts) =
                newService(
                    verifier = stubVerifierRejecting("issuer_signature_invalid"),
                )
            accounts.save(accountFor(accountId))
            val challenge = service.begin(accountId)

            val result = service.complete(accountId, challenge.transactionId, "{}")

            assertTrue(result is EmailVerificationService.Result.Failed)
            assertEquals("issuer_signature_invalid", result.reason)
        }

    @Test
    fun `complete returns Conflict when email is already verified on another account`(): Unit =
        runBlocking {
            val (service, _, accounts) =
                newService(
                    verifier = stubVerifierAcceptingAnyNonce("collide@example.com"),
                )
            val otherAccountId = Uuid.random()
            accounts.save(accountFor(otherAccountId, email = "collide@example.com", emailVerified = true))
            accounts.save(accountFor(accountId))
            val challenge = service.begin(accountId)

            val result = service.complete(accountId, challenge.transactionId, "{}")

            assertTrue(result is EmailVerificationService.Result.Conflict)
            assertEquals(otherAccountId, result.otherAccountId)
            // The targeted account is not mutated.
            val unchanged = assertNotNull(accounts.findById(accountId))
            assertNull(unchanged.email)
            assertTrue(!unchanged.emailVerified)
        }

    @Test
    fun `complete refuses when the requesting account does not exist`(): Unit =
        runBlocking {
            val (service, _, _) =
                newService(
                    verifier = stubVerifierAcceptingAnyNonce("ghost@example.com"),
                )
            // Account is intentionally not saved.
            val challenge = service.begin(accountId)

            val result = service.complete(accountId, challenge.transactionId, "{}")

            assertTrue(result is EmailVerificationService.Result.Failed)
            assertEquals("account_not_found", result.reason)
        }

    // --- helpers -----------------------------------------------------------

    private data class Bundle(
        val service: EmailVerificationService,
        val pending: PendingEmailVerificationRepository,
        val accounts: AccountRepository,
    )

    private fun newService(
        verifier: DigitalCredentialVerifier = stubVerifierAcceptingAnyNonce("u@example.com"),
        pending: PendingEmailVerificationRepository = InMemoryPendingEmailVerificationRepository(),
        accounts: AccountRepository = InMemoryAccountRepository(),
    ): Bundle =
        Bundle(
            service =
                EmailVerificationService(
                    pendingRepository = pending,
                    accountRepository = accounts,
                    verifier = verifier,
                    clock = { fixedNow },
                    secureRandom = SecureRandom("seed".toByteArray()),
                ),
            pending = pending,
            accounts = accounts,
        )

    private fun accountFor(
        id: Uuid,
        email: String? = null,
        emailVerified: Boolean = false,
    ): Account =
        Account(
            id = id,
            username = "user-${id.toString().take(8)}",
            displayName = "User",
            email = email,
            emailVerified = emailVerified,
            createdAt = fixedNow,
        )

    /**
     * Builds a verifier whose JWK loader returns a key not used to sign anything,
     * configured so its `verify` succeeds via a happy-path stub. We achieve this by
     * subclassing DigitalCredentialVerifier — keeping the production code path narrow.
     */
    private fun stubVerifierAcceptingAnyNonce(email: String): DigitalCredentialVerifier =
        AcceptingVerifier(VerifiedEmailClaims(email, null, null, null, null, "", fixedNow), expectedNonce = null)

    private fun stubVerifierRejecting(reason: String): DigitalCredentialVerifier = RejectingVerifier(reason)

    private inner class AcceptingVerifier(
        private val claims: VerifiedEmailClaims,
        private val expectedNonce: String?,
    ) : DigitalCredentialVerifier(
            jwksCache = trivialCache(),
            expectedAudience = expectedAudience,
            clock = { fixedNow },
        ) {
        override suspend fun verify(
            credentialJson: String,
            expectedNonce: String,
        ): kotlin.Result<VerifiedEmailClaims> {
            if (this.expectedNonce != null && this.expectedNonce != expectedNonce) {
                return kotlin.Result.failure(VerificationException("nonce_mismatch"))
            }
            return kotlin.Result.success(claims)
        }
    }

    private inner class RejectingVerifier(
        private val reason: String,
    ) : DigitalCredentialVerifier(
            jwksCache = trivialCache(),
            expectedAudience = expectedAudience,
            clock = { fixedNow },
        ) {
        override suspend fun verify(
            credentialJson: String,
            expectedNonce: String,
        ): kotlin.Result<VerifiedEmailClaims> = kotlin.Result.failure(VerificationException(reason))
    }

    private fun trivialCache(): GoogleVcJwksCache =
        GoogleVcJwksCache(
            jwksUrl = URI.create("https://example.invalid/jwks").toURL(),
            ttl = 1.hours,
            clock = { fixedNow },
            loader = { JWKSet(ECKeyGenerator(Curve.P_256).keyID("k").generate().toPublicJWK()) },
        )
}
