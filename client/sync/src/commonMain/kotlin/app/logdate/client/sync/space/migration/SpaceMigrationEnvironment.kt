package app.logdate.client.sync.space.migration

import app.logdate.client.media.MediaManager
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncTokenRefresher
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.location.LocationHistorySyncEngine
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.space.SpaceSyncDependencies
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * What moving an existing journal into its personal space needs from outside the sync engine. Leave
 * it out of [app.logdate.client.sync.DefaultSyncManager] and journals never move.
 *
 * @param enabledInThisBuild whether this build moves journals: the `PERSONAL_SPACE_MIGRATION` flag,
 *   together with `PERSONAL_SPACE`, since a moved journal syncs through its personal space.
 * @param serverOffersMove whether the connected server offers `personalSpaceMigrationV1`.
 * @param spaceMedia uploads media into the personal space, whichever store notes sync to now.
 * @param liveMomentIds the ids of every marked moment this device keeps that isn't deleted.
 * @param wait waits out a hold, a rate limit or a retry; tests pass one that doesn't sleep.
 */
class SpaceMigrationDependencies(
    val api: SpaceMigrationApi,
    val checkpoints: SpaceMigrationCheckpoints,
    val enabledInThisBuild: suspend () -> Boolean,
    val serverOffersMove: () -> Boolean,
    val spaceMedia: CloudMediaDataSource,
    val liveMomentIds: suspend () -> Collection<String>,
    val clock: Clock = Clock.System,
    val wait: suspend (Duration) -> Unit = { delay(it) },
)

/**
 * Everything one move needs, gathered by the sync engine that owns it. Each function is the engine's
 * own way of doing that step, so the move reads, writes and syncs exactly as sync does.
 */
internal class SpaceMigrationEnvironment(
    val deps: SpaceMigrationDependencies,
    val space: SpaceSyncDependencies,
    val tokens: SyncTokenRefresher,
    val metadata: SyncMetadataService,
    val inbox: DownloadInbox,
    val mediaManager: MediaManager,
    val mediaReferences: StoredMediaReferences,
    val legacyRefs: MediaSyncRefStore,
    val legacyMedia: CloudMediaDataSource,
    val history: LocationHistorySyncEngine?,
    /** Whether this device finished its full legacy download for [UploadScope]. */
    val legacyDownloadFinished: suspend (UploadScope) -> Boolean,
    val hasIdentityKey: suspend () -> Boolean,
    /** The kinds to move, with their media moved by [MigrationMediaPreparer]. */
    val kinds: suspend (MigrationMediaPreparer?) -> List<MigrationKind>,
    val legacyChecks: List<LegacyKindCheck>,
    /** Every media file this device's items refer to, by where the sync engine keeps its reference. */
    val mediaOwners: suspend () -> List<MediaOwner>,
    /** Runs one download pass the way sync does, through whichever store the account uses now. */
    val download: suspend () -> SyncResult,
    /** Uploads what waits in the outbox the way sync does, to whichever store the account uses now. */
    val upload: suspend () -> SyncResult,
    /** Runs [block] while nothing else syncs. */
    val whilePaused: suspend (suspend () -> Unit) -> Unit,
    /** Reads the account from the server again, so the device follows its new storage mode. */
    val refreshAccount: suspend () -> Unit,
    /** Resets what the device remembers about versions after the account changed stores. */
    val rebase: suspend (UploadScope) -> Unit,
    /** Queues every item of every kind for legacy storage again, deleting the ids in [deleted] by kind. */
    val requeueForLegacy: suspend (deleted: Map<String, Set<String>>) -> Unit,
)
