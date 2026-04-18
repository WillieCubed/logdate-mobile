package app.logdate.server.routes.sync

import app.logdate.server.auth.TokenService
import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.responses.simpleSuccess
import app.logdate.server.sync.SyncMetricsRegistry
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/**
 * Lightweight read endpoints used by smoke tests and Prometheus scraping:
 *
 *  - `GET /ops/sync/status` — per-account counts of synced entities.
 *  - `GET /ops/sync/metrics` — snapshot of [SyncMetricsRegistry].
 *  - `GET /ops/sync/metrics/prometheus` — the same snapshot in Prometheus exposition format.
 */
internal fun Route.syncStatusRoutes(
    tokenService: TokenService?,
    metrics: SyncMetricsRegistry,
    collectionsRepository: LogDateCollectionsRepository,
) {
    get("/ops/sync/status") {
        val start = System.currentTimeMillis()
        var success = false
        try {
            val userId = extractUserId(call, tokenService) ?: return@get
            val status = collectionsRepository.status(userId)
            val snapshot =
                SyncStatusSnapshot(
                    contentCount = status.entryCount,
                    journalCount = status.journalCount,
                    associationCount = status.associationCount,
                    lastTimestamp = status.lastTimestamp,
                )
            call.respond(simpleSuccess(snapshot))
            success = true
        } finally {
            metrics.recordOperation(METRIC_SYNC_STATUS, System.currentTimeMillis() - start, success)
        }
    }

    get("/ops/sync/metrics") {
        val start = System.currentTimeMillis()
        var success = false
        try {
            if (extractUserId(call, tokenService) == null) return@get
            val snapshot = metrics.snapshot()
            call.respond(simpleSuccess(snapshot))
            success = true
        } finally {
            metrics.recordOperation(METRIC_SYNC_METRICS, System.currentTimeMillis() - start, success)
        }
    }

    get("/ops/sync/metrics/prometheus") {
        val start = System.currentTimeMillis()
        var success = false
        try {
            if (extractUserId(call, tokenService) == null) return@get
            val snapshot = metrics.snapshot()
            call.respondText(snapshot.toPrometheus(), ContentType.Text.Plain)
            success = true
        } finally {
            metrics.recordOperation(METRIC_SYNC_METRICS_PROM, System.currentTimeMillis() - start, success)
        }
    }
}

@Serializable
internal data class SyncStatusSnapshot(
    val contentCount: Int,
    val journalCount: Int,
    val associationCount: Int,
    val lastTimestamp: Long,
)
