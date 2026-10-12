package app.logdate.client.sync.moments

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.repository.moments.atMillisecondPrecision
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncTokenRefresher
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.DefaultCloudMomentDataSource
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.conflict.MarkedMomentConflictResolver
import app.logdate.client.sync.crypto.MarkedMomentPayloadCodec
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.metadata.InMemoryUnreadableCloudRecordStore
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.DurableCloudApiClient
import app.logdate.client.sync.test.FakeCloudAccountRepository
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.FakeSessionStorage
import app.logdate.client.sync.test.FakeSyncMetadataService
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.MomentChange
import app.logdate.shared.model.sync.MomentChangesResponse
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class MomentDownloadFixture {
    val sessions = FakeSessionStorage().apply { currentOrigin = "https://moments.test" }
    val db = DownloadInboxTest.Database()
    val scope get() = DownloadScope(sessions.getSession()?.accountId.orEmpty(), sessions.currentOrigin)
    val inbox = DownloadInbox(db, db, { scope }, { 1000 })
    val metadata = FakeSyncMetadataService(trackOperationIdentity = true)
    val unreadable = InMemoryUnreadableCloudRecordStore()
    val repository = Marks()
    val calls = mutableListOf<Pair<String, Long>>()
    val limits = mutableListOf<Int?>()
    var enabled = true
    var page = MomentChangesResponse(emptyList(), emptyList(), 0)
    var onFetch: suspend (String, Long) -> Result<MomentChangesResponse> = { _, _ -> Result.success(page) }
    val accounts: CloudAccountRepository =
        object : CloudAccountRepository by FakeCloudAccountRepository(), CloudRequestLocationProvider {
            override fun captureLocation() = CloudRequestLocation(sessions.currentOrigin, "${sessions.currentOrigin}/api/v1/sync")

            override fun isCurrentOrigin(origin: String) = sessions.currentOrigin == origin
        }
    val api =
        object : FakeCloudApiClient() {
            override suspend fun getMomentChanges(
                accessToken: String,
                since: Long,
                limit: Int?,
            ): Result<MomentChangesResponse> {
                calls += accessToken to since
                limits += limit
                return onFetch(accessToken, since)
            }
        }

    suspend fun codec(seed: String = "same"): MarkedMomentPayloadCodec {
        val crypto = TestCryptoManager()
        val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
        identity.recoverIdentity((1..12).map { "$seed-$it" })
        return MarkedMomentPayloadCodec(
            SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto),
        )
    }

    suspend fun config() =
        MomentSyncConfig(DefaultCloudMomentDataSource(DurableCloudApiClient(api, inbox), codec()), repository, { enabled })

    suspend fun downloader() =
        MomentDownloader(
            config(),
            metadata,
            db,
            inbox,
            SyncTokenRefresher(sessions, accounts),
            unreadable,
            { error -> failure(error) },
            { error, _ -> failure(error) },
        )

    private fun failure(error: Exception) =
        SyncResult(
            success = false,
            errors =
                listOf(
                    SyncError(
                        if (error is CloudApiException &&
                            error.statusCode in listOf(401, 403)
                        ) {
                            SyncErrorType.AUTHENTICATION_ERROR
                        } else {
                            SyncErrorType.UNKNOWN_ERROR
                        },
                        "Moment download failed",
                    ),
                ),
        )

    fun mark(version: Long = 7) =
        MarkedMoment(
            Uuid.random(),
            Instant.fromEpochMilliseconds(100),
            "UTC",
            null,
            72,
            MarkState.OPEN,
            "Private caption",
            emptySet(),
            "watch",
            Instant.fromEpochMilliseconds(100),
            version,
        )

    suspend fun wire(
        mark: MarkedMoment,
        codec: MarkedMomentPayloadCodec? = null,
    ) = MomentChange(
        mark.id.toString(),
        mark.capturedAt.toEpochMilliseconds(),
        1000,
        DeviceId("watch"),
        (
            codec
                ?: this.codec()
        ).encode(mark),
        1,
        mark.syncVersion,
    )

    class Marks : SyncableMarkedMomentRepository {
        val rows = mutableMapOf<Uuid, MarkedMoment>()
        val deleted = mutableMapOf<Uuid, Pair<Instant, Long>>()
        var beforeApply: suspend () -> Unit = {}

        override fun observeOpenMarks() = flowOf(rows.values.filter { it.state == MarkState.OPEN })

        override suspend fun getMark(id: Uuid) = rows[id]

        override suspend fun applyFromSync(moment: MarkedMoment): MarkedMoment? {
            beforeApply()
            if (deleted.containsKey(moment.id)) return null
            val merged = rows[moment.id]?.let { MarkedMomentConflictResolver().merge(it, moment) } ?: moment
            rows[moment.id] = merged.atMillisecondPrecision()
            return rows[moment.id]
        }

        override suspend fun applyDeletionFromPeer(
            id: Uuid,
            deletedAt: Instant,
        ): Unit = error("Unused")

        override suspend fun applyDeletionFromSync(
            id: Uuid,
            deletedAt: Instant,
            serverVersion: Long,
        ) {
            rows.remove(id)
            deleted[id] = deletedAt to serverVersion
        }

        override suspend fun dismiss(
            id: Uuid,
            updatedAt: Instant,
        ): Boolean = error("Unused")

        override suspend fun fill(
            id: Uuid,
            caption: String,
            noteIds: Set<Uuid>,
            updatedAt: Instant,
        ): Boolean = error("Unused")

        override suspend fun delete(
            id: Uuid,
            deletedAt: Instant,
        ): Boolean = error("Unused")

        override suspend fun fillLocationIfMissing(
            id: Uuid,
            location: MarkLocation,
            updatedAt: Instant,
        ): Boolean = error("Unused")
    }
}
