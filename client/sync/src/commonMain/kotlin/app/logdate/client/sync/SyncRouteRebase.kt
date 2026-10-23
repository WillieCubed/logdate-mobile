package app.logdate.client.sync

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.space.SpaceRoute
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/** Which store this device last synced each account through: `legacy` or `space`. */
interface SyncRouteStore {
    suspend fun last(scope: UploadScope): String?

    suspend fun save(
        scope: UploadScope,
        route: String,
    )
}

/** [SyncRouteStore] in the app's key-value storage, so a route change is noticed once, across restarts. */
class KeyValueSyncRouteStore(
    private val storage: KeyValueStorage,
) : SyncRouteStore {
    override suspend fun last(scope: UploadScope): String? = storage.getStringOrThrow(key(scope))

    override suspend fun save(
        scope: UploadScope,
        route: String,
    ) = storage.putStringOrThrow(key(scope), route)

    private fun key(scope: UploadScope) =
        "sync_route_v1_${scope.ownerId.length}:${scope.ownerId}:${scope.serverOrigin.length}:${scope.serverOrigin}"
}

/** [SyncRouteStore] in memory, for tests and devices whose account never changes stores. */
class InMemorySyncRouteStore : SyncRouteStore {
    private val routes = mutableMapOf<UploadScope, String>()

    override suspend fun last(scope: UploadScope): String? = routes[scope]

    override suspend fun save(
        scope: UploadScope,
        route: String,
    ) {
        routes[scope] = route
    }
}

private const val LEGACY_ROUTE = "legacy"
private const val SPACE_ROUTE = "space"

/**
 * Forgets the versions sync numbered notes and journals with, after the account moved between legacy
 * storage and its personal space. Legacy storage numbers versions with large account-wide counters and
 * the personal space with small per-space sequence numbers, so an item's old version would make every
 * change from the other store look older than what this device has, and the change would be skipped.
 * With the version forgotten, the next change from the new store is compared by its content and time.
 *
 * Location history records also carry the legacy version they were last stored at, which legacy
 * storage checks on every write; after moving back to legacy storage they get it again from the server.
 *
 * The first route a device ever records for an account is taken as it is, unless it is the personal
 * space, whose items may still carry versions from before the account moved.
 */
internal suspend fun DefaultSyncManager.rebaseForRoute(scope: UploadScope) {
    val route =
        when (space?.route() ?: SpaceRoute.Legacy) {
            SpaceRoute.Space -> SPACE_ROUTE
            SpaceRoute.Legacy -> LEGACY_ROUTE
            is SpaceRoute.Blocked -> return
        }
    val last = syncRouteStore.last(scope)
    if (last == route) return
    if (last != null || route == SPACE_ROUTE) {
        Napier.i("This account now syncs through $route storage; forgetting versions from the other store")
        forgetItemVersions()
        if (route == LEGACY_ROUTE) {
            val token = tokenRefresher.getAccessToken() ?: error("Signing in again is needed to read location history versions")
            historyEngine?.adoptLegacyVersions(token)
        }
    }
    syncRouteStore.save(scope, route)
}

private suspend fun DefaultSyncManager.forgetItemVersions() {
    val now = Clock.System.now()
    (journalNotesRepository as? SyncableJournalNotesRepository)?.let { notes ->
        journalNotesRepository.allNotesObserved
            .first()
            .filter { it.syncVersion != 0L }
            .forEach { notes.updateSyncMetadata(it, 0, now) }
    }
    (journalRepository as? SyncableJournalRepository)?.let { journals ->
        journalRepository.allJournalsObserved
            .first()
            .filter { it.syncVersion != 0L }
            .forEach { journals.updateSyncMetadata(it.id, 0, now) }
    }
}
