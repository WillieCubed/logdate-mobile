package app.logdate.server.routes

import app.logdate.server.auth.TokenService
import app.logdate.server.crypto.EncryptionService
import app.logdate.server.entitlements.EntitlementEnforcer
import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.logdate.LogDateMediaBlobRepository
import app.logdate.server.ratelimit.SlidingWindowRateLimiter
import app.logdate.server.routes.sync.SyncRouteConfig
import app.logdate.server.routes.sync.syncBackupRoutes
import app.logdate.server.routes.sync.syncCollectionRoutes
import app.logdate.server.routes.sync.syncMaintenanceRoutes
import app.logdate.server.routes.sync.syncMediaRoutes
import app.logdate.server.routes.sync.syncStatusRoutes
import app.logdate.server.sync.MediaAccessPolicy
import app.logdate.server.sync.SyncMetricsRegistry
import io.ktor.server.routing.Route

/**
 * Sync routes with JWT authentication. All endpoints require a valid Bearer token and scope data
 * by user ID.
 *
 * The actual route handlers live in sibling files under `routes/sync/`:
 *
 *  - [syncStatusRoutes] — `/ops/sync/status`, `/ops/sync/metrics`, Prometheus export.
 *  - [syncCollectionRoutes] — `/contents`, `/journals`, `/associations`, `/drafts`.
 *  - [syncMediaRoutes] — `/media` CRUD + binary download.
 *  - [syncBackupRoutes] — `/backups` CRUD + binary download.
 *  - [syncMaintenanceRoutes] — `/ops/sync/tombstones:purge`, `/ops/backups:purge`.
 *
 * This function is the single entry point so `Application.module` doesn't need to know about the
 * split and existing callers (including `testApplication` harnesses) stay untouched. Each
 * sub-route takes only the collaborators it uses as individual parameters; the one shared bundle
 * is [SyncRouteConfig], which carries passive policy (currently just [MediaAccessPolicy]).
 */
fun Route.syncRoutes(
    tokenService: TokenService? = null,
    mediaStorage: LogDateBlobStorage? = null,
    metrics: SyncMetricsRegistry,
    mediaAccessPolicy: MediaAccessPolicy = MediaAccessPolicy.fromEnvironment(),
    encryptionService: EncryptionService = EncryptionService.fromEnvironment(),
    collectionsRepository: LogDateCollectionsRepository,
    mediaBlobRepository: LogDateMediaBlobRepository,
    backupRepository: LogDateBackupRepository,
    entitlementEnforcer: EntitlementEnforcer? = null,
    rateLimiter: SlidingWindowRateLimiter? = SlidingWindowRateLimiter(),
) {
    val config = SyncRouteConfig(mediaAccessPolicy = mediaAccessPolicy)
    syncStatusRoutes(tokenService, metrics, collectionsRepository)
    syncCollectionRoutes(tokenService, metrics, collectionsRepository)
    syncMediaRoutes(
        tokenService = tokenService,
        mediaStorage = mediaStorage,
        metrics = metrics,
        encryptionService = encryptionService,
        mediaBlobRepository = mediaBlobRepository,
        entitlementEnforcer = entitlementEnforcer,
        rateLimiter = rateLimiter,
        config = config,
    )
    syncBackupRoutes(
        tokenService = tokenService,
        mediaStorage = mediaStorage,
        metrics = metrics,
        encryptionService = encryptionService,
        backupRepository = backupRepository,
        entitlementEnforcer = entitlementEnforcer,
        rateLimiter = rateLimiter,
        config = config,
    )
    syncMaintenanceRoutes(tokenService, mediaStorage, metrics, collectionsRepository, backupRepository)
}
