package app.logdate.server.auth

import app.logdate.server.audit.AuditCategory
import app.logdate.server.audit.AuditKey
import app.logdate.server.audit.AuditLogger
import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateMediaBlobRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** Account removal commits durable external cleanup before returning success. */
@OptIn(ExperimentalUuidApi::class)
class AccountDeletionService(
    private val accountRepository: AccountRepository,
    private val mediaBlobRepository: LogDateMediaBlobRepository,
    private val backupRepository: LogDateBackupRepository,
    private val blobStorage: LogDateBlobStorage?,
    private val blobCleanup: AccountBlobCleanup? = null,
) {
    data class Summary(
        val mediaBlobsDeleted: Int,
        val backupBlobsDeleted: Int,
        val accountRowDeleted: Boolean,
    )

    suspend fun deleteAccount(accountId: Uuid): Summary {
        val owner = accountId.toJavaUuid()
        // PostgreSQL's BEFORE DELETE trigger captures all references, including soft deletes.
        // Non-database development runs have no durable queue and must enumerate successfully.
        val mediaPaths = if (blobCleanup == null) mediaBlobRepository.listMedia(owner).mapNotNull { it.storagePath } else emptyList()
        val backupPaths = if (blobCleanup == null) backupRepository.listBackups(owner).map { it.storagePath } else emptyList()
        val deleted = accountRepository.deleteAccount(accountId)
        var mediaDeleted = 0
        var backupDeleted = 0
        withContext(Dispatchers.IO) {
            if (blobCleanup != null) {
                try {
                    blobCleanup.runOnce(owner)
                } catch (_: Exception) {
                    Napier.w("Account removed; external cleanup remains in the durable queue")
                }
            } else {
                blobStorage?.let { storage ->
                    mediaPaths.forEach {
                        storage.deleteBlobIfExists(it)
                        mediaDeleted++
                    }
                    backupPaths.forEach {
                        storage.deleteBlobIfExists(it)
                        backupDeleted++
                    }
                }
            }
        }
        AuditLogger.emit(
            AuditCategory.ACCOUNT_DELETED,
            mapOf(AuditKey.ACCOUNT_ID to accountId.toString(), "accountRowDeleted" to deleted.toString()),
        )
        Napier.i("Account deletion accepted: accountRowDeleted=$deleted")
        return Summary(mediaDeleted, backupDeleted, deleted)
    }
}
