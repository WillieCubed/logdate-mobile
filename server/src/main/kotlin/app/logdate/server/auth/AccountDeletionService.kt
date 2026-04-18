package app.logdate.server.auth

import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateMediaBlobRepository
import io.github.aakira.napier.Napier
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Orchestrates the destructive-but-legitimate path a user takes when they ask the server to
 * forget them.
 *
 * The DB side is handled by V17 and V16's `ON DELETE CASCADE` foreign keys — removing the row in
 * `accounts` wipes every dependent row (passkeys, sessions, sync data, media records, backup
 * rows, entitlements). But the cascade doesn't reach the object store: media payloads and backup
 * blobs live in GCS or on-disk storage outside PostgreSQL's scope. This service lists and deletes
 * those blobs *before* the DB cascade runs, so an operator with access to both systems is left
 * with neither the metadata nor the bytes.
 *
 * A failed blob delete is logged but does not abort the account deletion. Users asking to be
 * forgotten expect the answer to be "yes" even if a handful of blobs are unreachable — we'd
 * rather orphan bytes in storage than block the delete and preserve their metadata.
 */
@OptIn(ExperimentalUuidApi::class)
class AccountDeletionService(
    private val accountRepository: AccountRepository,
    private val mediaBlobRepository: LogDateMediaBlobRepository,
    private val backupRepository: LogDateBackupRepository,
    private val blobStorage: LogDateBlobStorage?,
) {
    data class Summary(
        val mediaBlobsDeleted: Int,
        val backupBlobsDeleted: Int,
        val accountRowDeleted: Boolean,
    )

    suspend fun deleteAccount(accountId: Uuid): Summary {
        val legacyId: UUID = accountId.toJavaUuid()

        val mediaBlobsDeleted = deleteMediaBlobs(legacyId)
        val backupBlobsDeleted = deleteBackupBlobs(legacyId)
        val accountDeleted = accountRepository.deleteAccount(accountId)

        Napier.i(
            "Account deletion complete: account=$accountId, " +
                "mediaBlobsDeleted=$mediaBlobsDeleted, backupBlobsDeleted=$backupBlobsDeleted, " +
                "accountRowDeleted=$accountDeleted",
        )
        return Summary(
            mediaBlobsDeleted = mediaBlobsDeleted,
            backupBlobsDeleted = backupBlobsDeleted,
            accountRowDeleted = accountDeleted,
        )
    }

    private fun deleteMediaBlobs(userId: UUID): Int {
        val storage = blobStorage ?: return 0
        val records = runCatching { mediaBlobRepository.listMedia(userId) }
            .onFailure { Napier.w("Failed to list media for account $userId during delete", it) }
            .getOrElse { return 0 }
        var deleted = 0
        records.forEach { record ->
            val path = record.storagePath ?: return@forEach
            val removed = runCatching { storage.deleteBlob(path) }
                .onFailure { Napier.w("Failed to delete media blob $path for $userId", it) }
                .getOrDefault(false)
            if (removed) deleted++
        }
        return deleted
    }

    private fun deleteBackupBlobs(userId: UUID): Int {
        val storage = blobStorage ?: return 0
        val backups = runCatching { backupRepository.listBackups(userId) }
            .onFailure { Napier.w("Failed to list backups for account $userId during delete", it) }
            .getOrElse { return 0 }
        var deleted = 0
        backups.forEach { backup ->
            val removed = runCatching { storage.deleteBlob(backup.storagePath) }
                .onFailure { Napier.w("Failed to delete backup blob ${backup.storagePath} for $userId", it) }
                .getOrDefault(false)
            if (removed) deleted++
        }
        return deleted
    }
}
