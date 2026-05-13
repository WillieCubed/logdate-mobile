package app.logdate.server.auth

import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persistence contract for short-lived (~5 min) one-shot nonce bindings used by
 * the Android Digital Credentials email-verification flow. See
 * `pending_email_verifications` (migration V22).
 */
@OptIn(ExperimentalUuidApi::class)
interface PendingEmailVerificationRepository {
    /** Persist a fresh challenge. Returns it as-stored. */
    suspend fun create(challenge: PendingEmailVerification): PendingEmailVerification

    /**
     * Atomically look up and delete a challenge by transaction id. Returns null
     * if the row was already burned or never existed. Atomic deletion prevents a
     * captured nonce from being used twice.
     */
    suspend fun consume(transactionId: Uuid): PendingEmailVerification?

    /** Best-effort cleanup of expired rows. Safe to call from a scheduler. */
    suspend fun deleteExpired(now: Instant): Int
}

@OptIn(ExperimentalUuidApi::class)
data class PendingEmailVerification(
    val transactionId: Uuid,
    val accountId: Uuid,
    val nonce: String,
    val expiresAt: Instant,
    val createdAt: Instant,
)

/**
 * In-memory implementation suitable for tests and self-host without a database.
 * Concurrent `consume` is single-threaded via the synchronized block.
 */
@OptIn(ExperimentalUuidApi::class)
class InMemoryPendingEmailVerificationRepository : PendingEmailVerificationRepository {
    private val rows = mutableMapOf<Uuid, PendingEmailVerification>()
    private val lock = Any()

    override suspend fun create(challenge: PendingEmailVerification): PendingEmailVerification {
        synchronized(lock) { rows[challenge.transactionId] = challenge }
        return challenge
    }

    override suspend fun consume(transactionId: Uuid): PendingEmailVerification? = synchronized(lock) { rows.remove(transactionId) }

    override suspend fun deleteExpired(now: Instant): Int =
        synchronized(lock) {
            val expired = rows.values.filter { it.expiresAt <= now }
            expired.forEach { rows.remove(it.transactionId) }
            expired.size
        }
}
