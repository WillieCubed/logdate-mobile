package app.logdate.client.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkerParameters
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.export.archive.ArchiveContainer
import app.logdate.client.domain.export.archive.ArchiveCategory
import app.logdate.client.domain.export.archive.ArchiveCounts
import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.ArchiveExportSummary
import app.logdate.client.domain.export.archive.ArchiveOmission
import app.logdate.client.domain.export.archive.ArchiveOmissionReason
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchiveScope
import app.logdate.client.domain.export.archive.ExportArchiveUseCase
import app.logdate.client.sync.cloud.BackupFile
import app.logdate.client.sync.cloud.BackupMetadata
import app.logdate.client.sync.cloud.BackupUploadResult
import app.logdate.client.sync.cloud.CloudBackupDataSource
import app.logdate.feature.core.export.CloudArchiveCipher
import app.logdate.feature.core.export.CloudBackupWorker
import app.logdate.feature.core.restore.CloudRestoreWorker
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import okio.buffer
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class CloudBackupWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val archiveCipher = CloudArchiveCipher { ByteArray(32) { 7 } }
    private val verifiedIdentity = mockk<IdentityKeyManager> {
        coEvery { isRecoveryPhraseVerified() } returns true
    }

    @Test
    fun `uploads completed export and deletes private archive only after success`() = runTest {
        val manifest = "{\"format\":\"logdate-export\",\"schemaVersion\":\"2.0\"}"
        val useCase = v2Exporter(manifest)
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("backup", 1L, 1L)))
        val session = FakeSessionStorage(UserSession("access", "refresh", "account"))
        val deviceId = FakeDeviceIdProvider()
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker =
            CloudBackupWorker(context, params, useCase, cloud, session, deviceId, verifiedIdentity, archiveCipher)

        worker.doWork()
        assertTrue(cloud.uploadCalls == 1)
        val uploaded = requireNotNull(cloud.uploadedBackup)
        assertEquals(CloudArchiveCipher.MANIFEST, uploaded.manifest)
        assertEquals("LDCB1", uploaded.data.copyOfRange(0, 5).decodeToString())
        val restored = File(context.cacheDir, "cloud-backup-cipher-test.zip")
        archiveCipher.decrypt(uploaded.data, restored)
        val entries =
            ZipInputStream(ByteArrayInputStream(restored.readBytes())).use { zip ->
                buildMap {
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        put(entry.name, zip.readBytes())
                    }
                }
            }
        assertEquals(manifest, entries.getValue("manifest.json").decodeToString())
        assertFalse("metadata.json" in entries)
        restored.delete()
        assertTrue(context.filesDir.listFiles().orEmpty().none { it.name.startsWith("cloud-backup-") })
    }

    @Test
    fun `removes plaintext archive when upload fails and WorkManager retries`() = runTest {
        val useCase = v2Exporter("{\"schemaVersion\":\"2.0\"}")
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
            verifiedIdentity,
            archiveCipher,
        )

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
        assertTrue(context.filesDir.listFiles().orEmpty().none { it.name.startsWith("cloud-backup-") })
    }

    @Test
    fun `does not upload archive that omitted unreadable media`() = runTest {
        val useCase =
            v2Exporter(
                "{\"schemaVersion\":\"2.0\"}",
                ArchiveScope(
                    complete = false,
                    omitted = listOf(ArchiveOmission(ArchiveCategory.MEDIA, ArchiveOmissionReason.UNREADABLE)),
                ),
            )
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker = CloudBackupWorker(
            context,
            params,
            useCase,
            cloud,
            FakeSessionStorage(UserSession("access", "refresh", "account")),
            FakeDeviceIdProvider(),
            verifiedIdentity,
            archiveCipher,
        )

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Failure)
        assertEquals(0, cloud.uploadCalls)
        assertTrue(context.filesDir.listFiles().orEmpty().none { it.name.startsWith("cloud-backup-") })
    }

    private fun v2Exporter(
        manifest: String,
        scope: ArchiveScope = ArchiveScope(complete = true),
    ): ExportArchiveUseCase =
        mockk<ExportArchiveUseCase>().also { useCase ->
            every { useCase.export(any(), any()) } answers {
                val container = invocation.args[1] as ArchiveContainer
                flow {
                    container.write("README.txt", "LogDate export")
                    container.write("manifest.json", manifest)
                    container.write("SHA256SUMS", "checksums")
                    emit(
                        ArchiveExportProgress.Completed(
                            ArchiveExportSummary(
                                ArchiveCounts(0, 0, 0, 0, 0, 0, hasProfile = false),
                                scope,
                            ),
                        ),
                    )
                }
            }
        }

    private fun ArchiveContainer.write(
        path: String,
        text: String,
    ) {
        entry(ArchivePath.of(path)) { sink ->
            sink.buffer().apply { writeUtf8(text) }.flush()
        }
    }

    @Test
    fun `skips without authenticated session`() = runTest {
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
            verifiedIdentity,
            archiveCipher,
        )

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
        assertTrue(cloud.uploadCalls == 0)
    }

    @Test
    fun `backup does not upload before recovery phrase verification`() = runTest {
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
        val unverifiedIdentity = mockk<IdentityKeyManager> {
            coEvery { isRecoveryPhraseVerified() } returns false
        }
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker = CloudBackupWorker(
            context,
            params,
            mockk(relaxed = true),
            cloud,
            FakeSessionStorage(UserSession("access", "refresh", "account")),
            FakeDeviceIdProvider(),
            unverifiedIdentity,
            archiveCipher,
        )

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
        assertEquals(0, cloud.uploadCalls)
    }

    @Test
    fun `cloud restore downloads newest backup and enqueues normal restore`() = runTest {
        val plainArchive = File(context.cacheDir, "cloud-restore-source.zip").apply {
            writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3))
        }
        val encryptedArchive = archiveCipher.encrypt(plainArchive)
        plainArchive.delete()
        val newest =
            BackupMetadata(
                id = "newest",
                deviceId = "device",
                manifest = CloudArchiveCipher.MANIFEST,
                createdAt = 20L,
                sizeBytes = 3L,
                downloadUrl = "https://unused",
            )
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
            backups = listOf(
                newest.copy(createdAt = 10L),
                newest,
            )
            downloaded = BackupFile("device", CloudArchiveCipher.MANIFEST, encryptedArchive)
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
                archiveCipher,
            ) { archive -> enqueued = archive }

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
        assertTrue(cloud.downloadedBackupId == "newest")
        val expected = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3)
        assertTrue(enqueued?.readBytes()?.contentEquals(expected) == true)
        enqueued.delete()
    }

    @Test
    fun `cloud restore retries and removes decrypted archive when handoff fails`() = runTest {
        val plainArchive = File(context.cacheDir, "cloud-restore-failed-handoff-source.zip").apply {
            writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3))
        }
        val encryptedArchive = archiveCipher.encrypt(plainArchive)
        plainArchive.delete()
        val backup =
            BackupMetadata(
                id = "latest",
                deviceId = "device",
                manifest = CloudArchiveCipher.MANIFEST,
                createdAt = 20L,
                sizeBytes = 3L,
                downloadUrl = "https://unused",
            )
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
            backups = listOf(backup)
            downloaded = BackupFile("device", CloudArchiveCipher.MANIFEST, encryptedArchive)
        }
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        var decryptedArchive: File? = null
        val worker =
            CloudRestoreWorker(
                context,
                params,
                cloud,
                FakeSessionStorage(UserSession("access", "refresh", "account")),
                archiveCipher,
            ) { archive ->
                decryptedArchive = archive
                error("WorkManager enqueue failed")
            }

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
        assertFalse(decryptedArchive?.exists() == true)
    }

    @Test
    fun `archive from another recovery identity cannot reach restore`() = runTest {
        val source = File(context.cacheDir, "cloud-wrong-identity-source.zip").apply {
            writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3))
        }
        val encrypted = archiveCipher.encrypt(source)
        val destination = File(context.cacheDir, "cloud-wrong-identity-restored.zip")
        val wrongIdentityCipher = CloudArchiveCipher { ByteArray(32) { 8 } }

        assertTrue(runCatching { wrongIdentityCipher.decrypt(encrypted, destination) }.isFailure)
        assertFalse(destination.exists())
        source.delete()
    }

    @Test
    fun `legacy server encrypted zip backup remains restorable`() = runTest {
        val legacyZip = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3)
        val destination = File(context.cacheDir, "cloud-legacy-restore.zip")

        archiveCipher.decrypt(legacyZip, destination)

        assertTrue(destination.readBytes().contentEquals(legacyZip))
        destination.delete()
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
        var uploadedBackup: BackupFile? = null

        override suspend fun uploadBackup(accessToken: String, backup: BackupFile): Result<BackupUploadResult> {
            uploadCalls++
            uploadedBackup = backup
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
