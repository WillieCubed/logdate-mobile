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
     * Binds a never-used installation to an existing Cloud identity.
     *
     * Implementations that already have an owner must return false for a different remote owner.
     */
    suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String): Boolean = getCanonicalOwnerId() == remoteOwnerId

    class CorruptCanonicalOwnerException : IllegalStateException("The local LogDate identity is corrupted")
}

@OptIn(ExperimentalUuidApi::class)
class DefaultCanonicalOwnerProvider(
    private val storage: KeyValueStorage,
) : CanonicalOwnerProvider {
    private val mutex = Mutex()

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
            val storedOwnerId = storage.getString(CANONICAL_OWNER_ID_KEY)
            if (storedOwnerId != null) {
                return@withLock storedOwnerId.requireUuid() == verifiedRemoteOwnerId
            }

            storage.putString(CANONICAL_OWNER_ID_KEY, verifiedRemoteOwnerId)
            val persistedOwnerId = storage.getString(CANONICAL_OWNER_ID_KEY)
            check(persistedOwnerId == verifiedRemoteOwnerId) {
                "Failed to persist the local LogDate identity"
            }
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
    }
}
