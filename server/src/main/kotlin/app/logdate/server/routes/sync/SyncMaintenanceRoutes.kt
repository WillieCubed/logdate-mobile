package app.logdate.server.routes.sync

import app.logdate.server.auth.TokenService
import app.logdate.server.logdate.BackupRetentionPolicy
import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.responses.error
import app.logdate.server.sync.SyncMetricsRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

/**
 * Maintenance endpoints under `/api/v1/ops/` — operator-triggered cleanup of tombstoned rows and
 * old backup blobs. Both are idempotent and scoped to the caller's own data; an external
 * scheduler would typically hit these on a daily cadence.
 */
internal fun Route.syncMaintenanceRoutes(
    tokenService: TokenService?,
    mediaStorage: LogDateBlobStorage?,
    metrics: SyncMetricsRegistry,
    collectionsRepository: LogDateCollectionsRepository,
    backupRepository: LogDateBackupRepository,
) {
    route("/ops/sync") {
        post("/tombstones:purge") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@post
                val retentionDays = call.request.queryParameters["retentionDays"]?.toLongOrNull() ?: 30L
                if (retentionDays <= 0) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        error("INVALID_PARAMETER", "retentionDays must be > 0"),
                    )
                }
                val safeRetentionDays = retentionDays.coerceAtMost(3650L)
                val cutoff = System.currentTimeMillis() - (safeRetentionDays * MILLIS_PER_DAY)
                val result = collectionsRepository.purgeTombstones(userId, cutoff)
                call.respond(
                    SyncPurgeResponse(
                        contentPurged = result.entryPurged,
                        journalPurged = result.journalPurged,
                        associationPurged = result.associationPurged,
                        mediaPurged = 0,
                        cutoff = result.cutoff,
                        retentionDaysApplied = safeRetentionDays,
                    ),
                )
                success = true
            } finally {
                metrics.recordOperation(METRIC_SYNC_PURGE, System.currentTimeMillis() - start, success)
            }
        }
    }

    route("/ops/backups") {
        post(":purge") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@post
                val keepPerDevice =
                    call.request.queryParameters["keepPerDevice"]?.toIntOrNull()
                        ?: BackupRetentionPolicy.DEFAULT.keepPerDevice
                val maxAgeDays =
                    call.request.queryParameters["maxAgeDays"]?.toLongOrNull()
                        ?: (BackupRetentionPolicy.DEFAULT.maxAgeMillis / MILLIS_PER_DAY)
                if (keepPerDevice < 0 || maxAgeDays <= 0) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        error("INVALID_PARAMETER", "keepPerDevice must be >= 0 and maxAgeDays must be > 0"),
                    )
                }
                val policy =
                    BackupRetentionPolicy(
                        keepPerDevice = keepPerDevice,
                        maxAgeMillis = maxAgeDays.coerceAtMost(3650L) * MILLIS_PER_DAY,
                    )
                val backups = backupRepository.listBackups(userId)
                val toPurge = policy.backupsToPurge(backups, System.currentTimeMillis())
                toPurge.forEach { backup ->
                    runCatching { mediaStorage?.deleteBlob(backup.storagePath) }
                    backupRepository.deleteBackup(userId, backup.id)
                }
                call.respond(
                    BackupPurgeResponse(
                        deleted = toPurge.size,
                        remaining = backups.size - toPurge.size,
                        keepPerDevice = keepPerDevice,
                        maxAgeDaysApplied = maxAgeDays,
                    ),
                )
                success = true
            } finally {
                metrics.recordOperation("sync.backups.purge", System.currentTimeMillis() - start, success)
            }
        }
    }
}

@Serializable
internal data class SyncPurgeResponse(
    val contentPurged: Int,
    val journalPurged: Int,
    val associationPurged: Int,
    val mediaPurged: Int,
    val cutoff: Long,
    val retentionDaysApplied: Long,
)

@Serializable
data class BackupPurgeResponse(
    val deleted: Int,
    val remaining: Int,
    val keepPerDevice: Int,
    val maxAgeDaysApplied: Long,
)
