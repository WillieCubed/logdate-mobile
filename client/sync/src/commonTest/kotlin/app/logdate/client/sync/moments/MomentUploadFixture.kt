package app.logdate.client.sync.moments

import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.SyncProfile
import app.logdate.client.sync.SyncRetryCoordinator
import app.logdate.client.sync.SyncTokenRefresher
import app.logdate.client.sync.cloud.CloudMomentDataSource
import app.logdate.client.sync.cloud.ConditionalCreateResult
import app.logdate.client.sync.cloud.MomentSyncResult
import app.logdate.client.sync.cloud.SyncUploadResult
import app.logdate.client.sync.metadata.SyncBackoff
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.test.FakeSyncMetadataService
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.InMemorySyncRetryScheduleStore
import app.logdate.shared.model.sync.DeviceId
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class MomentUploadFixture {
    val download = MomentDownloadFixture()
    val metadata =
        FakeSyncMetadataService(trackOperationIdentity = true, currentScope = { UploadScope(download.scope.owner, download.scope.origin) })
    var enabled = true
    val created = mutableListOf<MarkedMoment>()
    val updated = mutableListOf<MarkedMoment>()
    val deleted = mutableListOf<Uuid>()
    var gets = 0
    var onCreate: suspend (
        MarkedMoment,
    ) -> Result<ConditionalCreateResult> = { Result.success(ConditionalCreateResult.Created(8, Instant.fromEpochMilliseconds(1000))) }
    var onUpdate: suspend (
        MarkedMoment,
    ) -> Result<SyncUploadResult> = { Result.success(SyncUploadResult(it.syncVersion + 1, Instant.fromEpochMilliseconds(1000))) }
    var onGet: suspend (Uuid) -> Result<MarkedMoment> = { error("Unexpected get") }
    var onDelete: suspend (Uuid) -> Result<Unit> = { Result.success(Unit) }
    val cloud =
        object : CloudMomentDataSource {
            override suspend fun createIfAbsent(
                accessToken: String,
                moment: MarkedMoment,
                deviceId: DeviceId,
            ): Result<ConditionalCreateResult> {
                created += moment
                return onCreate(moment)
            }

            override suspend fun update(
                accessToken: String,
                moment: MarkedMoment,
                deviceId: DeviceId,
            ): Result<SyncUploadResult> {
                updated += moment
                return onUpdate(moment)
            }

            override suspend fun get(
                accessToken: String,
                id: Uuid,
            ): Result<MarkedMoment> {
                gets++
                return onGet(id)
            }

            override suspend fun delete(
                accessToken: String,
                id: Uuid,
            ): Result<Unit> {
                deleted += id
                return onDelete(id)
            }

            override suspend fun changes(
                accessToken: String,
                since: Instant,
                limit: Int?,
            ): Result<MomentSyncResult> = error("Unused")
        }
    val retries =
        SyncRetryCoordinator(
            InMemorySyncRetryScheduleStore(),
            metadata,
            InMemorySyncDeadLetterStore(),
            SyncBackoff(),
            { _, _, _, _, _, _, _ -> },
        )

    fun uploader(profile: SyncProfile = SyncProfile.Standard) =
        MomentUploader(
            MomentSyncConfig(cloud, download.repository, { enabled }),
            metadata,
            download.db,
            SyncTokenRefresher(download.sessions, download.accounts),
            retries,
            profile,
            { DeviceId("test-device") },
            download.inbox,
        )
}
