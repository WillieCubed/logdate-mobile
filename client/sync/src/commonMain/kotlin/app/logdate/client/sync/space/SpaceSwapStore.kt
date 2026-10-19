package app.logdate.client.sync.space

import app.logdate.client.database.dao.sync.SpaceRecordStateDao
import app.logdate.client.database.entities.sync.SpaceRecordStateEntity
import app.logdate.client.sync.metadata.UploadScope

/**
 * The version of one personal-space record this device last wrote or applied.
 *
 * @property cid sent back as the swap condition of the next write, so it only replaces this version.
 * @property sealedRkey names the record in the change feed, where a deletion carries nothing else.
 */
data class SpaceRecordState(
    val collection: String,
    val rkey: String,
    val sealedRkey: String,
    val cid: String,
    val seq: Long,
)

/** Remembers [SpaceRecordState] for every record, per account and server. */
interface SpaceSwapStore {
    suspend fun get(
        scope: UploadScope,
        collection: String,
        rkey: String,
    ): SpaceRecordState?

    suspend fun bySealedRkey(
        scope: UploadScope,
        sealedRkey: String,
    ): SpaceRecordState?

    suspend fun all(scope: UploadScope): List<SpaceRecordState>

    suspend fun put(
        scope: UploadScope,
        state: SpaceRecordState,
    )

    suspend fun delete(
        scope: UploadScope,
        collection: String,
        rkey: String,
    )
}

/** [SpaceSwapStore] in the `sync_space_records` table. */
class RoomSpaceSwapStore(
    private val dao: SpaceRecordStateDao,
) : SpaceSwapStore {
    override suspend fun get(
        scope: UploadScope,
        collection: String,
        rkey: String,
    ): SpaceRecordState? = dao.get(scope.ownerId, scope.serverOrigin, collection, rkey)?.toState()

    override suspend fun bySealedRkey(
        scope: UploadScope,
        sealedRkey: String,
    ): SpaceRecordState? = dao.bySealedRkey(scope.ownerId, scope.serverOrigin, sealedRkey)?.toState()

    override suspend fun all(scope: UploadScope): List<SpaceRecordState> = dao.all(scope.ownerId, scope.serverOrigin).map { it.toState() }

    override suspend fun put(
        scope: UploadScope,
        state: SpaceRecordState,
    ) {
        dao.put(
            SpaceRecordStateEntity(
                ownerId = scope.ownerId,
                serverOrigin = scope.serverOrigin,
                collection = state.collection,
                innerRkey = state.rkey,
                sealedRkey = state.sealedRkey,
                cid = state.cid,
                seq = state.seq,
            ),
        )
    }

    override suspend fun delete(
        scope: UploadScope,
        collection: String,
        rkey: String,
    ) = dao.delete(scope.ownerId, scope.serverOrigin, collection, rkey)

    private fun SpaceRecordStateEntity.toState() = SpaceRecordState(collection, innerRkey, sealedRkey, cid, seq)
}
