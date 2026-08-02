package app.logdate.client.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkerParameters
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.sync.cloud.BackupFile
import app.logdate.client.sync.cloud.BackupMetadata
import app.logdate.client.sync.cloud.BackupUploadResult
import app.logdate.client.sync.cloud.CloudBackupDataSource
import app.logdate.feature.core.export.CloudBackupWorker
import app.logdate.feature.core.restore.CloudRestoreWorker
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.runner.RunWith
import kotlin.uuid.Uuid
import java.util.UUID
import java.io.File

@RunWith(AndroidJUnit4::class)
class CloudBackupWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun uploadsCompletedExportAndDeletesPrivateArchiveOnlyAfterSuccess() = runTest {
        val export = mockk<ExportResult>(relaxed = true)
        every { export.serializeMetadata() } returns "{\"deviceId\":\"device\"}"
        every { export.hasProfile } returns false
        every { export.hasPlaces } returns false
        every { export.hasLocationHistory } returns false
        every { export.mediaFiles } returns emptyList()
        every { export.renderIssuesText(any()) } returns null
        val useCase = mockk<app.logdate.client.domain.export.ExportUserDataUseCase>()
        every { useCase.exportUserData(true, true, true, true, null) } returns flowOf(ExportProgress.Completed(export))
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("backup", 1L, 1L)))
        val session = FakeSessionStorage(UserSession("access", "refresh", "account"))
        val deviceId = FakeDeviceIdProvider()
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker = CloudBackupWorker(context, params, useCase, cloud, session, deviceId)

        worker.doWork()
        assertTrue(cloud.uploadCalls == 1)
        assertTrue(context.filesDir.listFiles().orEmpty().none { it.name.startsWith("cloud-backup-") })
    }

    @Test
    fun retainsPrivateArchiveWhenUploadFails() = runTest {
        val export = mockk<ExportResult>(relaxed = true)
        every { export.serializeMetadata() } returns "manifest"
        every { export.hasProfile } returns false
        every { export.hasPlaces } returns false
        every { export.hasLocationHistory } returns false
        every { export.mediaFiles } returns emptyList()
        every { export.renderIssuesText(any()) } returns null
        val useCase = mockk<app.logdate.client.domain.export.ExportUserDataUseCase>()
        every { useCase.exportUserData(true, true, true, true, null) } returns flowOf(ExportProgress.Completed(export))
        val cloud = FakeCloudBackupDataSource(Result.failure(IllegalStateException("offline")))
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker = CloudBackupWorker(
            context,
            params,
            useCase,
            cloud,
            FakeSessionStorage(UserSession("access", "refresh", "account")),
            FakeDeviceIdProvider(),
        )

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
        val archive = context.filesDir.listFiles().orEmpty().firstOrNull { it.name.startsWith("cloud-backup-") }
        assertTrue(archive?.exists() == true)
        archive.delete()
    }

    @Test
    fun skipsWithoutAuthenticatedSession() = runTest {
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker = CloudBackupWorker(
            context,
            params,
            mockk(relaxed = true),
            cloud,
            FakeSessionStorage(null),
            FakeDeviceIdProvider(),
        )

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
        assertTrue(cloud.uploadCalls == 0)
    }

    @Test
    fun cloudRestoreDownloadsNewestBackupAndEnqueuesNormalRestore() = runTest {
        val newest =
            BackupMetadata(
                id = "newest",
                deviceId = "device",
                manifest = "manifest",
                createdAt = 20L,
                sizeBytes = 3L,
                downloadUrl = "https://unused",
            )
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
            backups = listOf(
                newest.copy(createdAt = 10L),
                newest,
            )
            downloaded = BackupFile("device", "manifest", byteArrayOf(1, 2, 3))
        }
        var enqueued: File? = null
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker =
            CloudRestoreWorker(
                context,
                params,
                cloud,
                FakeSessionStorage(UserSession("access", "refresh", "account")),
            ) { archive -> enqueued = archive }

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
        assertTrue(cloud.downloadedBackupId == "newest")
        assertTrue(enqueued?.readBytes()?.contentEquals(byteArrayOf(1, 2, 3)) == true)
        enqueued?.let(File::delete)
    }

    private class FakeSessionStorage(private var session: UserSession?) : SessionStorage {
        override fun getSession() = session
        override fun getSessionFlow() = MutableStateFlow(session)
        override suspend fun hasValidSession() = session != null
        override fun saveSession(session: UserSession) { this.session = session }
        override fun clearSession() { session = null }
    }

    private class FakeDeviceIdProvider : DeviceIdProvider {
        private val id = MutableStateFlow(Uuid.parse("00000000-0000-0000-0000-000000000001"))
        override fun getDeviceId() = id
        override suspend fun refreshDeviceId() = Unit
    }

    private class FakeCloudBackupDataSource(
        private val uploadResult: Result<BackupUploadResult>,
    ) : CloudBackupDataSource {
        var uploadCalls: Int = 0
        var backups: List<BackupMetadata> = emptyList()
        var downloaded: BackupFile? = null
        var downloadedBackupId: String? = null

        override suspend fun uploadBackup(accessToken: String, backup: BackupFile): Result<BackupUploadResult> {
            uploadCalls++
            return uploadResult
        }

        override suspend fun listBackups(accessToken: String): Result<List<BackupMetadata>> = Result.success(backups)

        override suspend fun downloadBackup(accessToken: String, backupId: String): Result<BackupFile> {
            downloadedBackupId = backupId
            return downloaded?.let(Result.Companion::success)
                ?: Result.failure(UnsupportedOperationException())
        }

        override suspend fun deleteBackup(accessToken: String, backupId: String): Result<Unit> = Result.success(Unit)
    }
}
