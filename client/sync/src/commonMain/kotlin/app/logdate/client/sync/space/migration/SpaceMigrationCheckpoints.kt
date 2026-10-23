package app.logdate.client.sync.space.migration

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.metadata.UploadScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Which step this device's move of the journal is on, as saved between runs. */
@Serializable
enum class SpaceMigrationStage {
    /** Sealing and uploading every item; [SpaceMigrationCheckpoint.uploadedThrough] says how far it got. */
    UPLOADING,

    /** Everything is uploaded; what is left is checking the copy and switching. */
    CHECKING,

    /** The account syncs through its personal space. */
    SWITCHED,

    /**
     * The account was switched back; the device still has to queue its journal for legacy storage.
     * Saved before the server is asked, so the queueing happens even if the app stops right after.
     */
    SWITCHING_BACK,

    /** The account was switched back and everything is queued for legacy storage again. */
    SWITCHED_BACK,
}

/**
 * How far this device got moving the signed-in account's journal, saved on the device after every
 * batch, so a move that stops part way resumes where it stopped.
 *
 * @property uploadedThrough for each kind of data, by entity type name, the last item uploaded, in
 *   the order the move uploads them.
 * @property finishedKinds the kinds whose every item is uploaded.
 * @property problem why the last attempt stopped, when it did.
 * @property retryAt when the move tries again on its own after [problem]; null to try on the next sync.
 * @property settled whether this device finished settling on the personal space after the switch.
 * @property deletedForLegacy while switching back, the ids legacy storage still has that this device
 *   deleted, by legacy kind, so they are deleted there too.
 */
@Serializable
data class SpaceMigrationCheckpoint(
    val migrationId: String,
    val stage: SpaceMigrationStage = SpaceMigrationStage.UPLOADING,
    val uploadedThrough: Map<String, String> = emptyMap(),
    val finishedKinds: Set<String> = emptySet(),
    val problem: JournalMoveProblem? = null,
    val retryAt: Long? = null,
    val switchedAtSeq: Long? = null,
    val legacyKeptUntil: Long? = null,
    val settled: Boolean = false,
    val deletedForLegacy: Map<String, Set<String>> = emptyMap(),
)

/**
 * Where a media file a moved item refers to lives in the personal space, and the legacy reference
 * it replaces, so switching back can restore it.
 *
 * @property spaceReference the file's [app.logdate.client.sync.space.SpaceMediaReference]; blank
 *   when the file has to be uploaded again.
 * @property localUri the file on this device the reference was made from.
 * @property uploadFromDevice whether the file must be uploaded from this device rather than copied,
 *   because a copy didn't match this device's file.
 */
@Serializable
data class MigratedMedia(
    val spaceReference: String,
    val legacyRemoteUrl: String? = null,
    val legacyMediaId: String? = null,
    val localUri: String? = null,
    val uploadFromDevice: Boolean = false,
)

/** Saves [SpaceMigrationCheckpoint]s and [MigratedMedia] on the device, per account and server. */
interface SpaceMigrationCheckpoints {
    suspend fun load(scope: UploadScope): SpaceMigrationCheckpoint?

    suspend fun save(
        scope: UploadScope,
        checkpoint: SpaceMigrationCheckpoint,
    )

    suspend fun clear(scope: UploadScope)

    /** The migrated media for [key], such as `note:<id>`, or null when it hasn't been moved. */
    suspend fun media(
        scope: UploadScope,
        key: String,
    ): MigratedMedia?

    suspend fun saveMedia(
        scope: UploadScope,
        key: String,
        media: MigratedMedia,
    )
}

/**
 * [SpaceMigrationCheckpoints] in the app's key-value storage. Reads and writes throw on a storage
 * failure instead of answering as if nothing were saved, so a failure never restarts a move.
 */
class KeyValueSpaceMigrationCheckpoints(
    private val storage: KeyValueStorage,
) : SpaceMigrationCheckpoints {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(scope: UploadScope): SpaceMigrationCheckpoint? =
        storage.getStringOrThrow(checkpointKey(scope))?.let { json.decodeFromString(SpaceMigrationCheckpoint.serializer(), it) }

    override suspend fun save(
        scope: UploadScope,
        checkpoint: SpaceMigrationCheckpoint,
    ) = storage.putStringOrThrow(checkpointKey(scope), json.encodeToString(SpaceMigrationCheckpoint.serializer(), checkpoint))

    override suspend fun clear(scope: UploadScope) = storage.remove(checkpointKey(scope))

    override suspend fun media(
        scope: UploadScope,
        key: String,
    ): MigratedMedia? = storage.getStringOrThrow(mediaKey(scope, key))?.let { json.decodeFromString(MigratedMedia.serializer(), it) }

    override suspend fun saveMedia(
        scope: UploadScope,
        key: String,
        media: MigratedMedia,
    ) = storage.putStringOrThrow(mediaKey(scope, key), json.encodeToString(MigratedMedia.serializer(), media))

    private fun checkpointKey(scope: UploadScope) = "space_migration_v1_${scope.encoded()}"

    private fun mediaKey(
        scope: UploadScope,
        key: String,
    ) = "space_migration_media_v1_${scope.encoded()}:$key"

    private fun UploadScope.encoded() = "${ownerId.length}:$ownerId:${serverOrigin.length}:$serverOrigin"
}

/** [SpaceMigrationCheckpoints] in memory, for tests and devices that never move a journal. */
class InMemorySpaceMigrationCheckpoints : SpaceMigrationCheckpoints {
    private val checkpoints = mutableMapOf<UploadScope, SpaceMigrationCheckpoint>()
    private val media = mutableMapOf<Pair<UploadScope, String>, MigratedMedia>()

    override suspend fun load(scope: UploadScope): SpaceMigrationCheckpoint? = checkpoints[scope]

    override suspend fun save(
        scope: UploadScope,
        checkpoint: SpaceMigrationCheckpoint,
    ) {
        checkpoints[scope] = checkpoint
    }

    override suspend fun clear(scope: UploadScope) {
        checkpoints.remove(scope)
    }

    override suspend fun media(
        scope: UploadScope,
        key: String,
    ): MigratedMedia? = media[scope to key]

    override suspend fun saveMedia(
        scope: UploadScope,
        key: String,
        media: MigratedMedia,
    ) {
        this.media[scope to key] = media
    }
}
