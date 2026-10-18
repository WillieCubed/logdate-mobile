package app.logdate.client.sync

import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.MediaPayload
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.test.FakeCloudAccountRepository
import app.logdate.client.sync.test.FakeSessionStorage
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AutomaticMediaRecoveryTest {
    private val origin = "https://example.test"
    private val sessions = FakeSessionStorage().apply { currentOrigin = origin }
    private val refs = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { origin }, { sessions.getSession()?.accountId.orEmpty() })
    private val media = InMemoryMediaManager()
    private val id = Uuid.random()
    private var downloads = 0
    private var cancelDownload = false
    private var rejectExpired = false
    private var refreshes = 0
    private val accounts =
        object :
            CloudAccountRepository by FakeCloudAccountRepository(),
            CloudRequestLocationProvider,
            app.logdate.client.sync.cloud.SessionRefreshingAccountRepository {
            override suspend fun refreshSession(refreshToken: String): Result<app.logdate.client.sync.cloud.RefreshedSession> {
                refreshes++
                return Result.success(
                    app.logdate.client.sync.cloud
                        .RefreshedSession("fresh-access", "fresh-refresh"),
                )
            }

            override fun captureLocation() = CloudRequestLocation(origin, "$origin/api")

            override fun isCurrentOrigin(origin: String) = sessions.currentOrigin == origin
        }
    private val cloud =
        object : CloudMediaDataSource {
            override suspend fun uploadMedia(
                accessToken: String,
                contentId: Uuid,
                media: MediaFileSource,
            ): Result<MediaUploadResult> = error("No upload")

            override suspend fun downloadMedia(
                accessToken: String,
                mediaId: String,
            ): Result<MediaFile> {
                downloads++
                if (cancelDownload) throw CancellationException("media recovery cancelled")
                if (rejectExpired && accessToken == "test-access-token") {
                    return Result.failure(
                        app.logdate.client.sync.cloud
                            .CloudApiException("SESSION_EXPIRED", "Expired", 401),
                    )
                }
                return Result.success(MediaFile(id, "original.png", "image/png", 1, byteArrayOf(1)))
            }
        }
    private val scope get() = UploadScope("test-account-id", origin)

    private fun recovery(
        refs: MediaSyncRefStore = this.refs,
        localMedia: InMemoryMediaManager = media,
        policy: app.logdate.client.networking.DataUsagePolicy? = null,
    ) = AutomaticMediaRecovery(
        localMedia,
        refs,
        cloud,
        sessions,
        accounts,
        object : CanonicalOwnerProvider {
            override suspend fun getCanonicalOwnerId() = "test-account-id"

            override suspend fun hasBoundOwner() = true
        },
        DefaultLogDateConfigRepository(initialBackendUrl = origin),
        dataUsagePolicy = policy,
    )

    @Test
    fun `archive recovery cannot reuse an accessible destination provider identity`() =
        runTest {
            val source = "content://media/external/images/media/123"
            val reused =
                object : InMemoryMediaManager() {
                    override suspend fun exists(mediaId: String) = mediaId == source || super.exists(mediaId)
                }
            refs.upsert(MediaSyncRef(id.toString(), source, "$origin/media/original", "original", 1))
            val result =
                assertIs<AutomaticMediaRecoveryResult.Available>(
                    recovery(localMedia = reused).recover(id, null, "image", source, "missing", scope),
                )
            assertTrue(result.reference != source)
            assertEquals(1, downloads)
        }

    @Test
    fun `metered recovery waits automatically but explicit scoped consent permits a download`() =
        runTest {
            val mode =
                kotlinx.coroutines.flow.MutableStateFlow<app.logdate.client.networking.DataUsageMode>(
                    app.logdate.client.networking.DataUsageMode.Conservative,
                )
            val policy =
                object : app.logdate.client.networking.DataUsagePolicy {
                    override val policy = mode

                    override suspend fun currentMode() = mode.value

                    override suspend fun currentRestriction() = app.logdate.client.networking.DataRestriction.NONE
                }
            val repair = recovery(policy = policy)
            val source = "$origin/media/original"
            assertIs<AutomaticMediaRecoveryResult.Pending>(repair.recover(id, null, "image", source, "missing", scope))
            assertEquals(0, downloads)
            withMobileDataConsent(scope) {
                assertIs<AutomaticMediaRecoveryResult.Available>(repair.recover(id, null, "image", source, "missing", scope))
            }
            assertEquals(1, downloads)
            assertIs<AutomaticMediaRecoveryResult.Available>(repair.recover(id, null, "image", source, "missing", scope))
            assertEquals(1, downloads)
        }

    @Test
    fun `cached original mapping failure stays retryable without credentials`() =
        runTest {
            val local = media.saveMedia(MediaPayload("transferred.png", "image/png", 1, byteArrayOf(1)))
            refs.upsert(MediaSyncRef(id.toString(), local, "$origin/media/original", "original", 1))
            sessions.clearSession()
            val failing =
                object : MediaSyncRefStore by refs {
                    override suspend fun upsert(ref: MediaSyncRef) {
                        error("preferences unavailable")
                    }
                }
            val result =
                assertIs<AutomaticMediaRecoveryResult.Pending>(
                    recovery(failing).recover(id, null, "image", "$origin/media/original", "missing", scope),
                )
            assertEquals(DiagnosticReason.LOCAL_STORAGE, result.reason)
            assertEquals(0, downloads)
        }

    @Test
    fun `expired draft credentials refresh automatically before retrying its original`() =
        runTest {
            rejectExpired = true
            val draftId = Uuid.random()
            val result = recovery().recover(id, draftId, "image", "$origin/media/original", "missing", scope)
            val local = assertIs<AutomaticMediaRecoveryResult.Available>(result).reference
            assertTrue(media.exists(local))
            assertEquals(1, refreshes)
            assertEquals(2, downloads)
            assertEquals("fresh-access", sessions.getSession()?.accessToken)
        }

    @Test
    fun `known Cloud original is recovered and its unavailable alias stays mapped`() =
        runTest {
            val placeholder = "logdate-unavailable://archive/recovery/original"
            val local =
                assertIs<AutomaticMediaRecoveryResult.Available>(
                    recovery().recover(id, null, "image", "$origin/media/original", placeholder, scope),
                ).reference
            assertTrue(media.exists(local))
            assertTrue(placeholder in requireNotNull(refs.get(id)).localAliases)
            assertEquals(1, downloads)
            assertEquals(
                local,
                assertIs<AutomaticMediaRecoveryResult.Available>(
                    recovery().recover(id, null, "image", placeholder, placeholder, scope),
                ).reference,
            )
            assertEquals(1, downloads)
        }

    @Test
    fun `a transferred cached original is recovered before credentials are requested`() =
        runTest {
            val local = media.saveMedia(MediaPayload("transferred.png", "image/png", 1, byteArrayOf(1)))
            refs.upsert(MediaSyncRef(id.toString(), local, "$origin/media/original", "original", 1))
            sessions.clearSession()
            val result =
                assertIs<AutomaticMediaRecoveryResult.Available>(
                    recovery().recover(id, null, "image", "$origin/media/original", "missing", scope),
                )
            assertEquals(local, result.reference)
            assertEquals(0, downloads)
        }

    @Test
    fun `absent credentials preserve pending recovery without issuing a request`() =
        runTest {
            sessions.clearSession()
            assertIs<AutomaticMediaRecoveryResult.Pending>(
                recovery().recover(id, null, "image", "$origin/media/original", "missing", scope),
            )
            assertEquals(0, downloads)
        }

    @Test
    fun `cancellation stops missing media recovery instead of reporting a retryable failure`() =
        runTest {
            cancelDownload = true
            assertFailsWith<CancellationException> {
                recovery().recover(id, null, "image", "$origin/media/original", "missing", scope)
            }
            assertEquals(1, downloads)
        }

    @Test
    fun `another account cannot recover an earlier accounts attachments`() =
        runTest {
            assertIs<AutomaticMediaRecoveryResult.Pending>(
                recovery().recover(id, null, "image", "$origin/media/original", "missing", scope.copy(ownerId = "other-account")),
            )
            assertEquals(0, downloads)
        }
}
