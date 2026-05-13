package app.logdate.server.auth

import io.github.aakira.napier.Napier
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Orchestrates the two-step Android Digital Credentials email-verification flow:
 *
 *  1. [begin] issues a server-bound nonce + transaction id, persisting both for a
 *     bounded window so the same nonce cannot be replayed.
 *  2. [complete] cryptographically verifies the credential the Android client got
 *     back from Credential Manager, then attaches the verified email to the
 *     account — surfacing a conflict if the email is already on a different
 *     account, or a stable reason code if verification failed.
 *
 * Account state is only mutated on the happy path; every failure leaves the
 * account untouched and surfaces a stable reason code for client diagnostics.
 */
@OptIn(ExperimentalUuidApi::class)
class EmailVerificationService(
    private val pendingRepository: PendingEmailVerificationRepository,
    private val accountRepository: AccountRepository,
    private val verifier: DigitalCredentialVerifier,
    private val challengeTtl: Duration = 5.minutes,
    private val nonceBytes: Int = 32,
    private val clock: () -> Instant = { Clock.System.now() },
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    sealed interface Result {
        data class Success(
            val email: String,
            val emailVerifiedAt: Instant,
        ) : Result

        /** The verified email already maps to a different account in this realm. */
        data class Conflict(
            val otherAccountId: Uuid,
        ) : Result

        /** Verification failed for a known reason (issuer, signature, nonce, …). */
        data class Failed(
            val reason: String,
        ) : Result
    }

    suspend fun begin(accountId: Uuid): PendingEmailVerification {
        val now = clock()
        val challenge =
            PendingEmailVerification(
                transactionId = Uuid.random(),
                accountId = accountId,
                nonce = generateNonce(),
                expiresAt = now + challengeTtl,
                createdAt = now,
            )
        return pendingRepository.create(challenge)
    }

    suspend fun complete(
        accountId: Uuid,
        transactionId: Uuid,
        credentialJson: String,
    ): Result {
        val challenge =
            pendingRepository.consume(transactionId)
                ?: return Result.Failed("challenge_missing_or_consumed")

        if (challenge.accountId != accountId) {
            // Cross-account replay attempt — burn the row (already consumed) and refuse.
            Napier.w("email-verification: tx ${challenge.transactionId} belongs to ${challenge.accountId} not $accountId")
            return Result.Failed("challenge_account_mismatch")
        }

        val now = clock()
        if (challenge.expiresAt <= now) {
            return Result.Failed("challenge_expired")
        }

        val verified =
            verifier
                .verify(credentialJson, challenge.nonce)
                .getOrElse { e ->
                    val reason = (e as? VerificationException)?.message ?: "verification_failed"
                    return Result.Failed(reason)
                }

        val matches =
            accountRepository
                .findByVerifiedEmail(verified.email)
                .filter { it.id != accountId }
        if (matches.isNotEmpty()) {
            return Result.Conflict(otherAccountId = matches.first().id)
        }

        val account =
            accountRepository.findById(accountId)
                ?: return Result.Failed("account_not_found")
        val updated =
            account.copy(
                email = verified.email,
                emailVerified = true,
                emailVerifiedAt = verified.verifiedAt,
            )
        accountRepository.save(updated)
        return Result.Success(email = verified.email, emailVerifiedAt = verified.verifiedAt)
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(nonceBytes)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
