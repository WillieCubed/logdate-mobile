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
    suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String): Boolean = getCanonicalOwnerId() == remoteOwnerId

    class CorruptCanonicalOwnerException : IllegalStateException("The local LogDate identity is corrupted")
}

@OptIn(ExperimentalUuidApi::class)
class DefaultCanonicalOwnerProvider(
    private val storage: KeyValueStorage,
) : CanonicalOwnerProvider {
    private val mutex = Mutex()

    override suspend fun hasBoundOwner(): Boolean =
        mutex.withLock {
            storage.getString(CANONICAL_OWNER_BOUND_KEY) == BOUND
        }

    override suspend fun getCanonicalOwnerId(): String =
        mutex.withLock {
            val storedOwnerId = storage.getString(CANONICAL_OWNER_ID_KEY)
            if (storedOwnerId != null) {
                return@withLock storedOwnerId.requireUuid()
            }

            val createdOwnerId = Uuid.random().toString()
            storage.putString(CANONICAL_OWNER_ID_KEY, createdOwnerId)
            val persistedOwnerId = storage.getString(CANONICAL_OWNER_ID_KEY)
            check(persistedOwnerId == createdOwnerId) {
                "Failed to persist the local LogDate identity"
            }
            createdOwnerId
        }

    override suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String): Boolean =
        mutex.withLock {
            val verifiedRemoteOwnerId = remoteOwnerId.requireUuid()

            // Once an account has claimed this installation the binding is final: signing in as
            // somebody else would fold two people's journals together.
            if (storage.getString(CANONICAL_OWNER_BOUND_KEY) == BOUND) {
                return@withLock storage.getString(CANONICAL_OWNER_ID_KEY)?.requireUuid() == verifiedRemoteOwnerId
            }

            storage.putString(CANONICAL_OWNER_ID_KEY, verifiedRemoteOwnerId)
            val persistedOwnerId = storage.getString(CANONICAL_OWNER_ID_KEY)
            check(persistedOwnerId == verifiedRemoteOwnerId) {
                "Failed to persist the local LogDate identity"
            }
            storage.putString(CANONICAL_OWNER_BOUND_KEY, BOUND)
            true
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
