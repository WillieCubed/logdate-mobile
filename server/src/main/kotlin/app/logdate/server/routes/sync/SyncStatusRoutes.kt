package app.logdate.server.routes.sync

import app.logdate.server.auth.TokenService
import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.responses.simpleSuccess
import app.logdate.server.sync.SyncMetricsRegistry
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
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
    get("/ops/sync/status", {
        tags = listOf("Sync")
        summary = "Get sync status"
        description = "Retrieve the count of entities synced for the current account."
        securitySchemeNames = listOf("bearerAuth")
        response {
            HttpStatusCode.OK to {
                description = "Sync status retrieved successfully"
                body<SyncStatusSnapshot>()
            }
        }
    }) {
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

    get("/ops/sync/metrics", {
        tags = listOf("Ops")
        summary = "Get sync metrics"
        description = "Retrieve a snapshot of the server's sync metrics."
        securitySchemeNames = listOf("bearerAuth")
        response {
            HttpStatusCode.OK to {
                description = "Metrics retrieved successfully"
            }
        }
    }) {
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

    get("/ops/sync/metrics/prometheus", {
        tags = listOf("Ops")
        summary = "Get Prometheus metrics"
        description = "Retrieve server metrics in Prometheus exposition format."
        securitySchemeNames = listOf("bearerAuth")
        response {
            HttpStatusCode.OK to {
                description = "Prometheus metrics retrieved successfully"
                body<String> {
                    mediaTypes = setOf(ContentType.Text.Plain)
                }
            }
        }
    }) {
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
