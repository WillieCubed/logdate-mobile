package app.logdate.client.data.account

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.repository.location.HistoryOwnerAdoptionRepair
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Resumes a consented adoption after a crash between local row migration and identity binding. */
class HistoryOwnerAdoption(
    private val storage: KeyValueStorage,
    private val owner: CanonicalOwnerProvider,
    private val config: LogDateConfigRepository,
    private val deviceId: () -> String,
    private val moveRows: suspend (oldOwner: String, newOwner: String, origin: String, device: String) -> Unit,
    private val countRows: suspend (owner: String, origin: String, device: String) -> Int = { _, _, _ -> 0 },
) : HistoryOwnerAdoptionRepair {
    suspend fun hasLocalHistory(): Boolean =
        storage.contains(JOURNAL_KEY) ||
            countRows(owner.getCanonicalOwnerId(), config.getCurrentBackendUrl().trimEnd('/'), deviceId()) > 0

    suspend fun adopt(newOwner: String): Boolean = adoptOnOrigin(newOwner, config.getCurrentBackendUrl())

    suspend fun adoptOnOrigin(
        newOwner: String,
        origin: String,
    ): Boolean =
        adoptionMutex.withLock {
            val normalizedOrigin = origin.trimEnd('/')
            val pending = storage.getString(JOURNAL_KEY)?.let { Json.decodeFromString<AdoptionJournal>(it) }
            val current = owner.getCanonicalOwnerId()
            if (pending != null &&
                (
                    pending.newOwner != newOwner ||
                        pending.origin != normalizedOrigin ||
                        current !in setOf(pending.oldOwner, pending.newOwner)
                )
            ) {
                return@withLock false
            }
            if (owner.hasBoundOwner() && current != newOwner) return@withLock false
            if (pending == null && current == newOwner) {
                return@withLock owner.adoptRemoteOwnerIfUninitialized(newOwner)
            }
            val journal =
                pending ?: AdoptionJournal(current, newOwner, normalizedOrigin, deviceId()).also {
                    storage.putString(JOURNAL_KEY, Json.encodeToString(it))
                    check(storage.getString(JOURNAL_KEY) == Json.encodeToString(it)) { "Could not persist history adoption" }
                }
            moveRows(journal.oldOwner, journal.newOwner, journal.origin, journal.device)
            check(owner.adoptRemoteOwnerIfUninitialized(newOwner)) { "Could not bind the adopted history owner" }
            storage.putString(RECEIPT_KEY, Json.encodeToString(journal))
            check(storage.getString(RECEIPT_KEY) == Json.encodeToString(journal)) { "Could not persist history adoption receipt" }
            storage.remove(JOURNAL_KEY)
            true
        }

    override suspend fun reconcile(
        ownerId: String,
        origin: String,
    ) {
        adoptionMutex.withLock {
            val receipt = storage.getString(RECEIPT_KEY)?.let { Json.decodeFromString<AdoptionJournal>(it) } ?: return
            if (receipt.newOwner != ownerId ||
                receipt.origin != origin.trimEnd('/') ||
                !owner.hasBoundOwner() ||
                owner.getCanonicalOwnerId() != ownerId
            ) {
                return
            }
            moveRows(receipt.oldOwner, receipt.newOwner, receipt.origin, receipt.device)
        }
    }

    @Serializable
    private data class AdoptionJournal(
        val oldOwner: String,
        val newOwner: String,
        val origin: String,
        val device: String,
    )

    private companion object {
        const val RECEIPT_KEY = "identity.completed_history_owner_adoption.v1"
        const val JOURNAL_KEY = "identity.pending_history_owner_adoption.v1"
        val adoptionMutex = Mutex()
    }
}
