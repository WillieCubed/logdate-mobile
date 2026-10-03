package app.logdate.server.enrollment

import java.util.UUID

enum class EnrollmentStatus {
    PENDING,
    APPROVED,
    REJECTED,
}

data class DeviceEnrollment(
    val id: UUID,
    val accountId: UUID,
    val deviceName: String,
    val publicKey: String,
    val confirmationCode: String,
    val expiresAt: Long,
    val status: EnrollmentStatus = EnrollmentStatus.PENDING,
    val encryptedEnvelope: String? = null,
    val claimHash: String? = null,
    val sessionIssued: Boolean = false,
)

/** The outcome of reserving the one account session a pending enrollment may hand to its new device. */
enum class SessionIssueResult {
    ISSUED,
    ALREADY_ISSUED,
    NOT_PENDING,
    NOT_FOUND,
}

interface DeviceEnrollmentRepository {
    /** Reusing a claim secret returns the original request only when its account and QR details match. */
    suspend fun create(enrollment: DeviceEnrollment): DeviceEnrollment?

    suspend fun get(
        accountId: UUID,
        id: UUID,
        now: Long,
    ): DeviceEnrollment?

    suspend fun approve(
        accountId: UUID,
        id: UUID,
        confirmationCode: String,
        encryptedEnvelope: String,
        now: Long,
    ): Boolean

    suspend fun consume(
        accountId: UUID,
        id: UUID,
        now: Long,
    ): String?

    suspend fun cancel(
        accountId: UUID,
        id: UUID,
    ): Boolean

    suspend fun reject(
        accountId: UUID,
        id: UUID,
        now: Long,
    ): Boolean

    /** A claim can be retried until the signed-in new device consumes it or it expires. */
    suspend fun claim(
        claimHash: String,
        now: Long,
    ): DeviceEnrollment?

    /**
     * Atomically records that the new device's session has been issued. Only an unexpired, pending
     * request owned by [accountId] qualifies, and only once.
     */
    suspend fun markSessionIssued(
        accountId: UUID,
        id: UUID,
        now: Long,
    ): SessionIssueResult
}

class InMemoryDeviceEnrollmentRepository : DeviceEnrollmentRepository {
    private val rows = mutableMapOf<UUID, DeviceEnrollment>()
    private val lock = Any()

    override suspend fun create(enrollment: DeviceEnrollment): DeviceEnrollment? =
        synchronized(lock) {
            rows.values.removeAll { it.expiresAt <= System.currentTimeMillis() }
            val existing = enrollment.claimHash?.let { hash -> rows.values.firstOrNull { it.claimHash == hash } }
            if (existing != null) return@synchronized existing.takeIf { it.matchesCreate(enrollment) }
            rows[enrollment.id] = enrollment
            enrollment
        }

    override suspend fun get(
        accountId: UUID,
        id: UUID,
        now: Long,
    ): DeviceEnrollment? = synchronized(lock) { rows[id]?.takeIf { it.accountId == accountId && it.expiresAt > now } }

    override suspend fun approve(
        accountId: UUID,
        id: UUID,
        confirmationCode: String,
        encryptedEnvelope: String,
        now: Long,
    ): Boolean =
        synchronized(lock) {
            val existing = rows[id] ?: return@synchronized false
            if (
                existing.accountId != accountId ||
                existing.expiresAt <= now ||
                existing.confirmationCode != confirmationCode
            ) {
                return@synchronized false
            }
            if (existing.status == EnrollmentStatus.APPROVED) {
                return@synchronized existing.encryptedEnvelope == encryptedEnvelope
            }
            if (existing.status != EnrollmentStatus.PENDING) return@synchronized false
            rows[id] = existing.copy(status = EnrollmentStatus.APPROVED, encryptedEnvelope = encryptedEnvelope)
            true
        }

    override suspend fun consume(
        accountId: UUID,
        id: UUID,
        now: Long,
    ): String? =
        synchronized(lock) {
            val existing = rows[id] ?: return@synchronized null
            if (existing.accountId != accountId || existing.expiresAt <= now || existing.status != EnrollmentStatus.APPROVED) {
                return@synchronized null
            }
            rows.remove(id)
            existing.encryptedEnvelope
        }

    override suspend fun cancel(
        accountId: UUID,
        id: UUID,
    ): Boolean =
        synchronized(lock) {
            if (rows[id]?.accountId != accountId) return@synchronized false
            rows.remove(id) != null
        }

    override suspend fun reject(
        accountId: UUID,
        id: UUID,
        now: Long,
    ): Boolean =
        synchronized(lock) {
            val existing = rows[id] ?: return@synchronized false
            if (existing.accountId != accountId || existing.expiresAt <= now || existing.status != EnrollmentStatus.PENDING) {
                return@synchronized false
            }
            rows[id] = existing.copy(status = EnrollmentStatus.REJECTED)
            true
        }

    override suspend fun claim(
        claimHash: String,
        now: Long,
    ): DeviceEnrollment? =
        synchronized(lock) {
            rows.values.firstOrNull { it.claimHash == claimHash && it.expiresAt > now }
        }

    override suspend fun markSessionIssued(
        accountId: UUID,
        id: UUID,
        now: Long,
    ): SessionIssueResult =
        synchronized(lock) {
            val existing = rows[id]?.takeIf { it.accountId == accountId && it.expiresAt > now }
            val result = existing.sessionIssueResult()
            if (existing != null && result == SessionIssueResult.ISSUED) rows[id] = existing.copy(sessionIssued = true)
            result
        }
}

internal fun DeviceEnrollment?.sessionIssueResult(): SessionIssueResult =
    when {
        this == null -> SessionIssueResult.NOT_FOUND
        status != EnrollmentStatus.PENDING -> SessionIssueResult.NOT_PENDING
        sessionIssued -> SessionIssueResult.ALREADY_ISSUED
        else -> SessionIssueResult.ISSUED
    }

internal fun DeviceEnrollment.matchesCreate(other: DeviceEnrollment): Boolean =
    accountId == other.accountId &&
        deviceName == other.deviceName &&
        publicKey == other.publicKey &&
        confirmationCode == other.confirmationCode &&
        claimHash == other.claimHash
