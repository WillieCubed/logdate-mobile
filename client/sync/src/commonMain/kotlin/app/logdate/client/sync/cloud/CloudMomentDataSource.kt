package app.logdate.client.sync.cloud

import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.crypto.MarkedMomentPayloadCodec
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.MomentChange
import app.logdate.shared.model.sync.MomentUpdateRequest
import app.logdate.shared.model.sync.MomentUploadRequest
import app.logdate.shared.model.sync.VersionConstraint
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface CloudMomentDataSource {
    suspend fun createIfAbsent(
        accessToken: String,
        moment: MarkedMoment,
        deviceId: DeviceId,
    ): Result<ConditionalCreateResult>

    suspend fun update(
        accessToken: String,
        moment: MarkedMoment,
        deviceId: DeviceId,
    ): Result<SyncUploadResult>

    suspend fun get(
        accessToken: String,
        id: Uuid,
    ): Result<MarkedMoment>

    suspend fun delete(
        accessToken: String,
        id: Uuid,
    ): Result<Unit>

    suspend fun changes(
        accessToken: String,
        since: Instant,
        limit: Int? = null,
    ): Result<MomentSyncResult>
}

data class MomentSyncResult(
    val changes: List<MarkedMoment>,
    val deletions: List<Uuid>,
    val deletionVersions: Map<Uuid, Long>,
    val lastSyncTimestamp: Instant,
    val hasMore: Boolean,
    val unreadable: List<Uuid>,
    val unreadableVersions: Map<Uuid, Long>,
    val failures: List<RemoteRecordFailure>,
)

class DefaultCloudMomentDataSource(
    private val api: CloudApiClient,
    private val codec: MarkedMomentPayloadCodec,
) : CloudMomentDataSource {
    override suspend fun createIfAbsent(
        accessToken: String,
        moment: MarkedMoment,
        deviceId: DeviceId,
    ): Result<ConditionalCreateResult> =
        momentResult {
            api
                .createMomentIfAbsent(
                    accessToken,
                    MomentUploadRequest(
                        id = moment.id.toString(),
                        capturedAt = moment.capturedAt.toEpochMilliseconds(),
                        lastUpdated = moment.lastUpdated.toEpochMilliseconds(),
                        deviceId = deviceId,
                        payload = encryptedPayload(moment),
                    ),
                ).getOrThrow()
        }

    override suspend fun update(
        accessToken: String,
        moment: MarkedMoment,
        deviceId: DeviceId,
    ): Result<SyncUploadResult> =
        momentResult {
            require(moment.syncVersion > 0) { "Moment update requires a known server version" }
            val response =
                api
                    .updateMoment(
                        accessToken,
                        moment.id.toString(),
                        MomentUpdateRequest(
                            payload = encryptedPayload(moment),
                            capturedAt = moment.capturedAt.toEpochMilliseconds(),
                            lastUpdated = moment.lastUpdated.toEpochMilliseconds(),
                            deviceId = deviceId,
                            versionConstraint = VersionConstraint.Known(moment.syncVersion),
                        ),
                    ).getOrThrow()
            require(response.id == moment.id.toString() && response.serverVersion > 0) { "Invalid moment update receipt" }
            SyncUploadResult(response.serverVersion, Instant.fromEpochMilliseconds(response.updatedAt))
        }

    override suspend fun get(
        accessToken: String,
        id: Uuid,
    ): Result<MarkedMoment> =
        momentResult {
            val change = api.getMoment(accessToken, id.toString()).getOrThrow()
            require(change.id == id.toString()) { "Moment response identity mismatch" }
            change.toMoment()
        }

    override suspend fun delete(
        accessToken: String,
        id: Uuid,
    ): Result<Unit> =
        momentResult {
            api.deleteMoment(accessToken, id.toString()).getOrThrow()
        }

    override suspend fun changes(
        accessToken: String,
        since: Instant,
        limit: Int?,
    ): Result<MomentSyncResult> =
        api.getMomentChanges(accessToken, since.toEpochMilliseconds(), limit).mapRecordPage { page ->
            val records = page.changes.readEach(idOf = { it.id }, versionOf = { it.serverVersion }) { it.toMoment() }
            val removed =
                page.deletions.readEach(idOf = { it.id }, versionOf = { it.serverVersion }) {
                    require(it.serverVersion > 0) { "Invalid moment deletion version" }
                    Uuid.parse(it.id) to it.serverVersion
                }
            MomentSyncResult(
                changes = records.readable,
                deletions = removed.readable.map { it.first },
                deletionVersions = removed.readable.toMap(),
                lastSyncTimestamp = Instant.fromEpochMilliseconds(page.lastTimestamp),
                hasMore = page.hasMore,
                unreadable = records.unreadable,
                unreadableVersions = records.unreadableVersions,
                failures = records.failures + removed.failures,
            )
        }

    private suspend fun MomentChange.toMoment(): MarkedMoment {
        if (schema != 1) throw UnsupportedRemoteFormatException()
        require(serverVersion > 0) { "Invalid moment version" }
        val moment = codec.decode(Uuid.parse(id), schema, serverVersion, payload)
        require(moment.capturedAt.toEpochMilliseconds() == capturedAt) { "Moment capture metadata mismatch" }
        return moment
    }

    private suspend fun encryptedPayload(moment: MarkedMoment): String {
        val payload = codec.encode(moment)
        require(payload.encodeToByteArray().size <= 64 * 1024) { "Moment payload exceeds server limit" }
        return payload
    }

    private suspend fun <T> momentResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
}
