package app.logdate.client.device.identity

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Provides the one durable LogDate identity for this installation.
 *
 * This owner is deliberately independent from the physical-device identifier and
 * from any Cloud session. A device reset or Cloud sign-out must not change it.
 */
interface CanonicalOwnerProvider {
    suspend fun getCanonicalOwnerId(): String

    /** Observes persisted ownership atomically, without creating or adopting an owner. */
    suspend fun readExistingOwner(): ExistingCanonicalOwner? =
        throw UnsupportedOperationException("Existing journal ownership cannot be read")

    /**
     * Whether this installation has already been bound to a Cloud account.
     *
     * An installation used offline still has an owner id -- one generated locally so entries have
     * something to belong to. That is not the same as having been claimed by an account, and
     * treating it as such left a device permanently unable to sign in: the locally generated id
     * could never match any account the Cloud returns.
     *
     * Must not create an owner as a side effect.
     */
    suspend fun hasBoundOwner(): Boolean

    /**
     * Binds an installation that no account has claimed yet to an existing Cloud identity.
     *
     * Implementations already bound to an account must return false for a different remote owner.
     */
    suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String): Boolean = sameOwnerId(getCanonicalOwnerId(), remoteOwnerId)

    /**
     * Forgets this installation's owner and its account binding, as part of erasing a device removed
     * from its account. The next [getCanonicalOwnerId] creates a new, unbound owner. A provider that
     * cannot forget its owner throws instead of pretending it did.
     */
    suspend fun resetForDeviceWipe(): Unit = throw UnsupportedOperationException("This owner provider cannot forget its owner")

    class CorruptCanonicalOwnerException : IllegalStateException("The local LogDate identity is corrupted")
}

@OptIn(ExperimentalUuidApi::class)
class DefaultCanonicalOwnerProvider(
    private val storage: KeyValueStorage,
) : CanonicalOwnerProvider {
    private val mutex = Mutex()

    override suspend fun hasBoundOwner(): Boolean = readExistingOwner()?.isBound == true

    override suspend fun readExistingOwner(): ExistingCanonicalOwner? = mutex.withLock { readExistingOwnerLocked() }

    private suspend fun readExistingOwnerLocked(): ExistingCanonicalOwner? {
        val snapshot = readRawOwnerLocked()
        val rawOwner = snapshot[CANONICAL_OWNER_ID_KEY]
        val rawBinding = snapshot[CANONICAL_OWNER_BOUND_KEY]
        if (rawBinding != null && rawBinding != BOUND) throw CanonicalOwnerProvider.CorruptCanonicalOwnerException()
        if (rawOwner == null) {
            if (rawBinding == BOUND) throw CanonicalOwnerProvider.CorruptCanonicalOwnerException()
            return null
        }
        return ExistingCanonicalOwner(rawOwner.requireUuid(), rawBinding == BOUND)
    }

    private suspend fun readRawOwnerLocked(): Map<String, String?> {
        val fields = setOf(CANONICAL_OWNER_ID_KEY, CANONICAL_OWNER_BOUND_KEY)
        val snapshot = storage.getStringsOrThrow(fields)
        check(snapshot.keys == fields) { "Incomplete journal ownership snapshot" }
        return snapshot
    }

    override suspend fun getCanonicalOwnerId(): String =
        mutex.withLock {
            readExistingOwnerLocked()?.let { return@withLock it.rawId }
            val createdOwnerId = Uuid.random().toString()
            storage.compareAndSetStringsOrThrow(
                mapOf(CANONICAL_OWNER_ID_KEY to null, CANONICAL_OWNER_BOUND_KEY to null),
                mapOf(CANONICAL_OWNER_ID_KEY to createdOwnerId),
            )
            checkNotNull(readExistingOwnerLocked()) { "Failed to persist the local LogDate identity" }.rawId
        }

    override suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String): Boolean =
        mutex.withLock {
            val verifiedRemoteOwnerId = remoteOwnerId.requireUuid()
            val existing = readExistingOwnerLocked()
            if (existing?.isBound == true) return@withLock sameOwnerId(existing.rawId, verifiedRemoteOwnerId)
            val matched =
                storage.compareAndSetStringsOrThrow(
                    mapOf(CANONICAL_OWNER_ID_KEY to existing?.rawId, CANONICAL_OWNER_BOUND_KEY to null),
                    mapOf(CANONICAL_OWNER_ID_KEY to verifiedRemoteOwnerId, CANONICAL_OWNER_BOUND_KEY to BOUND),
                )
            val committed = readExistingOwnerLocked()
            if (matched) {
                check(committed?.isBound == true && committed.rawId == verifiedRemoteOwnerId) {
                    "Failed to persist the local LogDate account binding"
                }
            }
            committed?.isBound == true && sameOwnerId(committed.rawId, verifiedRemoteOwnerId)
        }

    override suspend fun resetForDeviceWipe() {
        mutex.withLock {
            val before = readRawOwnerLocked()
            check(
                storage.compareAndSetStringsOrThrow(
                    before,
                    mapOf(CANONICAL_OWNER_ID_KEY to null, CANONICAL_OWNER_BOUND_KEY to null),
                ),
            ) { "Journal ownership changed before device removal" }
            check(readRawOwnerLocked().values.all { it == null }) { "Failed to forget the local LogDate identity" }
        }
    }

    private fun String.requireUuid(): String =
        runCatching { Uuid.parse(this) }
            .fold(
                onSuccess = { this },
                onFailure = { throw CanonicalOwnerProvider.CorruptCanonicalOwnerException() },
            )

    private companion object {
        const val CANONICAL_OWNER_ID_KEY = "identity.canonical_owner_id"
        const val CANONICAL_OWNER_BOUND_KEY = "identity.canonical_owner_bound"
        const val BOUND = "true"
    }
}
