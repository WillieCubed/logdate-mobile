package app.logdate.client.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkerParameters
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.OriginBoundSession
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
import app.logdate.client.sync.cloud.BackupUploadFileRequest
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudBackupDataSource
import app.logdate.feature.core.export.CloudArchiveCipher
import app.logdate.feature.core.export.CloudBackupWorker
import app.logdate.feature.core.restore.CloudRestoreWorker
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.io.files.Path
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
    fun `uploads the sealed archive through the file streaming API`() = runTest {
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("backup", 1L, 1L)))
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker = CloudBackupWorker(
            context,
            params,
            v2Exporter("{\"schemaVersion\":\"2.0\"}"),
            cloud,
            FakeSessionStorage(UserSession("access", "refresh", "account")),
            FakeDeviceIdProvider(),
            verifiedIdentity,
            archiveCipher,
        )

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
        assertEquals(1, cloud.fileUploadCalls)
        assertEquals(0, cloud.uploadCalls)
        assertEquals("LDCB2", cloud.uploadedFileHeader)
        assertTrue(cloud.uploadedFileSize > 17L)
        assertTrue(cloud.uploadedFileDigest?.isNotBlank() == true)
    }

    @Test
    fun `streams a one gibibyte archive through the Android backup worker`() = runTest {
        val payloadSize = 1L shl 30
        assertTrue(Runtime.getRuntime().maxMemory() < payloadSize)
        val exporter = mockk<ExportArchiveUseCase>()
        var exportCompleted = false
        every { exporter.export(any(), any()) } answers {
            val container = invocation.args[1] as ArchiveContainer
            flow {
                container.write("manifest.json", "{\"schemaVersion\":\"2.0\"}")
                container.entry(ArchivePath.of("payload.bin"), compress = false) { sink ->
                    val buffer = ByteArray(64 * 1024) { index -> (index * 31).toByte() }
                    val output = sink.buffer()
                    var remaining = payloadSize
                    while (remaining > 0) {
                        val count = minOf(buffer.size.toLong(), remaining).toInt()
                        output.write(buffer, 0, count)
                        remaining -= count
                    }
                    output.flush()
                }
                exportCompleted = true
                emit(
                    ArchiveExportProgress.Completed(
                        ArchiveExportSummary(
                            ArchiveCounts(0, 0, 0, 0, 0, 0, hasProfile = false),
                            ArchiveScope(complete = true),
                        ),
                    ),
                )
            }
        }
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("backup", 1L, 1L))).apply {
            retainUploadedFileBytes = false
        }
        var cipherKeyRequests = 0
        val largeArchiveCipher = CloudArchiveCipher {
            cipherKeyRequests++
            ByteArray(32) { 7 }
        }
        val params = mockk<WorkerParameters>(relaxed = true)
        val workId = UUID.randomUUID()
        every { params.id } returns workId
        val worker = CloudBackupWorker(
            context,
            params,
            exporter,
            cloud,
            FakeSessionStorage(UserSession("access", "refresh", "account")),
            FakeDeviceIdProvider(),
            verifiedIdentity,
            largeArchiveCipher,
        )

        val result = worker.doWork()
        assertTrue(
            result is androidx.work.ListenableWorker.Result.Success,
            "workerResult=$result, exportCompleted=$exportCompleted, " +
                "cipherKeyRequests=$cipherKeyRequests, fileUploadCalls=${cloud.fileUploadCalls}, " +
                "uploadedSize=${cloud.uploadedFileSize}",
        )
        assertEquals(1, cloud.fileUploadCalls)
        assertEquals(0, cloud.uploadCalls)
        assertEquals(CloudArchiveCipher.MANIFEST, cloud.uploadedFileManifest)
        assertEquals("LDCB2", cloud.uploadedFileHeader)
        assertTrue(cloud.uploadedFileSize > payloadSize)
        assertTrue(cloud.uploadedFileDigest?.isNotBlank() == true)
        assertEquals(null, cloud.uploadedFileBytes)
        assertFalse(File(context.noBackupFilesDir, "cloud-backup-$workId.zip").exists())
        assertFalse(File(context.noBackupFilesDir, "cloud-backup-$workId.scope").exists())
    }

    @Test
    fun `downloads and authenticates a one gibibyte archive through the Android restore worker`() = runTest {
        val payloadSize = 1L shl 30
        assertTrue(Runtime.getRuntime().maxMemory() < payloadSize)
        val source = File(context.cacheDir, "cloud-restore-1g-source.zip")
        val encrypted = File(context.cacheDir, "cloud-restore-1g-source.encrypted")
        createLargeArchive(source, payloadSize)
        val expectedSize = source.length()
        val expectedDigest = source.sha256()
        archiveCipher.encrypt(source, encrypted)
        source.delete()

        val backup = BackupMetadata(
            id = "large-backup",
            deviceId = "device",
            manifest = CloudArchiveCipher.MANIFEST,
            createdAt = 20L,
            sizeBytes = encrypted.length(),
            downloadUrl = "https://unused",
        )
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
            backups = listOf(backup)
            downloadedFile = encrypted
        }
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        var restored: File? = null
        val worker = CloudRestoreWorker(
            context,
            params,
            cloud,
            FakeSessionStorage(UserSession("access", "refresh", "account")),
            archiveCipher,
        ) { archive -> restored = archive }

        try {
            assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
            assertEquals(1, cloud.fileDownloadCalls)
            assertEquals(expectedSize, requireNotNull(restored).length())
            assertEquals(expectedDigest, requireNotNull(restored).sha256())
            ZipFile(requireNotNull(restored)).use { zip ->
                assertEquals(payloadSize, zip.getEntry("payload.bin")?.size)
                assertEquals(
                    "{\"schemaVersion\":\"2.0\"}",
                    zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().use { it.readText() },
                )
            }
        } finally {
            source.delete()
            encrypted.delete()
            restored?.delete()
        }
    }

    @Test
    fun `retry reuses the exact encrypted archive for the same work and account`() = runTest {
        val cloud = FakeCloudBackupDataSource(Result.failure(IllegalStateException("offline")))
        val params = mockk<WorkerParameters>(relaxed = true)
        val workId = UUID.randomUUID()
        every { params.id } returns workId
        val worker = CloudBackupWorker(
            context,
            params,
            v2Exporter("{\"schemaVersion\":\"2.0\"}"),
            cloud,
            FakeSessionStorage(UserSession("access", "refresh", "account")),
            FakeDeviceIdProvider(),
            verifiedIdentity,
            archiveCipher,
        )

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
        val firstDigest = cloud.uploadedFileDigest
        assertTrue(File(context.noBackupFilesDir, "cloud-backup-$workId.zip").exists())
        cloud.uploadResult = Result.success(BackupUploadResult("backup", 2L, 1L))

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
        assertEquals(firstDigest, cloud.uploadedFileDigest)
        assertEquals(2, cloud.fileUploadCalls)
        assertFalse(File(context.noBackupFilesDir, "cloud-backup-$workId.zip").exists())
        assertFalse(File(context.noBackupFilesDir, "cloud-backup-$workId.scope").exists())
    }

    @Test
    fun `expired upload token refreshes and retries the same encrypted file`() = runTest {
        val cloud = FakeCloudBackupDataSource(Result.failure(IllegalStateException("offline"))).apply {
            uploadResults += Result.failure(CloudApiException("UNAUTHORIZED", "expired", 401))
            uploadResults += Result.success(BackupUploadResult("backup", 1L, 1L))
            refreshResults += Result.success("refreshed-access")
        }
        val session = FakeSessionStorage(UserSession("access", "refresh", "account"))
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker = CloudBackupWorker(
            context,
            params,
            v2Exporter("{\"schemaVersion\":\"2.0\"}"),
            cloud,
            session,
            FakeDeviceIdProvider(),
            verifiedIdentity,
            archiveCipher,
        )

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
        assertEquals(listOf("access", "refreshed-access"), cloud.uploadAccessTokens)
        assertEquals(1, cloud.refreshCalls)
        assertEquals(2, cloud.uploadedFileDigests.size)
        assertEquals(cloud.uploadedFileDigests.first(), cloud.uploadedFileDigests.last())
        assertEquals("refreshed-access", session.getSession()?.accessToken)
    }

    @Test
    fun `expired restore tokens refresh for listing and streamed download`() = runTest {
        val source = File(context.cacheDir, "cloud-refresh-restore-source.zip").apply {
            writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3))
        }
        val encryptedArchive = archiveCipher.encrypt(source)
        source.delete()
        val backup = BackupMetadata("backup", "device", CloudArchiveCipher.MANIFEST, 20L, 64L, "https://unused")
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
            backups = listOf(backup)
            downloaded = BackupFile("device", CloudArchiveCipher.MANIFEST, encryptedArchive)
            listResults += Result.failure(CloudApiException("UNAUTHORIZED", "expired", 401))
            listResults += Result.success(backups)
            fileDownloadResults += Result.failure(CloudApiException("UNAUTHORIZED", "expired", 401))
            fileDownloadResults += Result.success(backup)
            refreshResults += Result.success("access-after-list-refresh")
            refreshResults += Result.success("access-after-download-refresh")
        }
        val session = FakeSessionStorage(UserSession("access", "refresh", "account"))
        var enqueued: File? = null
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker = CloudRestoreWorker(
            context,
            params,
            cloud,
            session,
            archiveCipher,
        ) { archive -> enqueued = archive }

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
        assertEquals(listOf("access", "access-after-list-refresh"), cloud.listAccessTokens)
        assertEquals(listOf("access-after-list-refresh", "access-after-download-refresh"), cloud.downloadAccessTokens)
        assertEquals(2, cloud.refreshCalls)
        assertEquals("access-after-download-refresh", session.getSession()?.accessToken)
        assertTrue(enqueued?.readBytes()?.contentEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3)) == true)
        requireNotNull(enqueued).delete()
    }

    @Test
    fun `token refresh does not overwrite a session after account changes`() = runTest {
        val cloud = FakeCloudBackupDataSource(Result.failure(IllegalStateException("offline"))).apply {
            uploadResults += Result.failure(CloudApiException("UNAUTHORIZED", "expired", 401))
            refreshResults += Result.success("must-not-be-saved")
        }
        val session = FakeSessionStorage(UserSession("access", "refresh", "account"))
        cloud.onRefresh = { session.changeSession(UserSession("other-access", "other-refresh", "other-account")) }
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker = CloudBackupWorker(
            context,
            params,
            v2Exporter("{\"schemaVersion\":\"2.0\"}"),
            cloud,
            session,
            FakeDeviceIdProvider(),
            verifiedIdentity,
            archiveCipher,
        )

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
        assertEquals(listOf("access"), cloud.uploadAccessTokens)
        assertEquals("other-access", session.getSession()?.accessToken)
        assertEquals("other-account", session.getSession()?.accountId)
    }

    @Test
    fun `uploads completed export and deletes private archive only after success`() = runTest {
        val manifest = "{\"format\":\"logdate-export\",\"schemaVersion\":\"2.0\"}"
        val useCase = v2Exporter(manifest)
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("backup", 1L, 1L)))
        val session = FakeSessionStorage(UserSession("access", "refresh", "account"))
        val deviceId = FakeDeviceIdProvider()
        val params = mockk<WorkerParameters>(relaxed = true)
        val workId = UUID.randomUUID()
        every { params.id } returns workId
        val worker =
            CloudBackupWorker(context, params, useCase, cloud, session, deviceId, verifiedIdentity, archiveCipher)

        worker.doWork()
        assertEquals(0, cloud.uploadCalls)
        assertEquals(1, cloud.fileUploadCalls)
        assertEquals(CloudArchiveCipher.MANIFEST, cloud.uploadedFileManifest)
        val uploaded = requireNotNull(cloud.uploadedFileBytes)
        assertEquals("LDCB2", uploaded.copyOfRange(0, 5).decodeToString())
        val sealed = File(context.cacheDir, "cloud-backup-cipher-test.encrypted").apply { writeBytes(uploaded) }
        val restored = File(context.cacheDir, "cloud-backup-cipher-test.zip")
        archiveCipher.decrypt(sealed, restored)
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
        sealed.delete()
        restored.delete()
        assertFalse(File(context.noBackupFilesDir, "cloud-backup-$workId.zip").exists())
        assertFalse(File(context.noBackupFilesDir, "cloud-backup-$workId.scope").exists())
    }

    @Test
    fun `retains only the sealed archive when upload fails and WorkManager retries`() = runTest {
        val useCase = v2Exporter("{\"schemaVersion\":\"2.0\"}")
        val cloud = FakeCloudBackupDataSource(Result.failure(IllegalStateException("offline")))
        val params = mockk<WorkerParameters>(relaxed = true)
        val workId = UUID.randomUUID()
        every { params.id } returns workId
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
        assertTrue(File(context.noBackupFilesDir, "cloud-backup-$workId.zip").exists())
        assertTrue(context.noBackupFilesDir.listFiles().orEmpty().none { it.name.endsWith(".plain.part") })
        File(context.noBackupFilesDir, "cloud-backup-$workId.zip").delete()
        File(context.noBackupFilesDir, "cloud-backup-$workId.scope").delete()
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
        assertEquals(0, cloud.fileUploadCalls)
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

    private fun createLargeArchive(
        destination: File,
        payloadSize: Long,
    ) {
        ZipOutputStream(destination.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("{\"schemaVersion\":\"2.0\"}".encodeToByteArray())
            zip.closeEntry()
            zip.setLevel(Deflater.NO_COMPRESSION)
            zip.putNextEntry(ZipEntry("payload.bin"))
            val buffer = ByteArray(64 * 1024) { index -> (index * 31).toByte() }
            var remaining = payloadSize
            while (remaining > 0) {
                val count = minOf(buffer.size.toLong(), remaining).toInt()
                zip.write(buffer, 0, count)
                remaining -= count
            }
            zip.closeEntry()
        }
    }

    private fun File.sha256(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
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
        assertEquals(1, cloud.fileDownloadCalls)
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
    fun `cloud restore streams and preserves legacy server encrypted zip`() = runTest {
        val legacyZip = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 5)
        val backup = BackupMetadata("legacy", "device", "legacy-manifest", 20L, legacyZip.size.toLong(), "https://unused")
        val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
            backups = listOf(backup)
            downloaded = BackupFile("device", "legacy-manifest", legacyZip)
        }
        var enqueued: File? = null
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        val worker = CloudRestoreWorker(
            context,
            params,
            cloud,
            FakeSessionStorage(UserSession("access", "refresh", "account")),
            archiveCipher,
        ) { archive -> enqueued = archive }

        assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
        assertEquals(1, cloud.fileDownloadCalls)
        assertTrue(enqueued?.readBytes()?.contentEquals(legacyZip) == true)
        requireNotNull(enqueued).delete()
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

    @Test
    fun `file backed archive encryption authenticates before returning plaintext`() = runTest {
        val source = File(context.cacheDir, "cloud-file-cipher-source.zip").apply {
            writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3, 4))
        }
        val encrypted = File(context.cacheDir, "cloud-file-cipher-encrypted.bin")
        val restored = File(context.cacheDir, "cloud-file-cipher-restored.zip")
        val corruptedRestored = File(context.cacheDir, "cloud-file-cipher-corrupted.zip")

        archiveCipher.encrypt(source, encrypted)
        assertEquals("LDCB2", encrypted.inputStream().use { it.readNBytes(5).decodeToString() })
        archiveCipher.decrypt(encrypted, restored)
        assertTrue(restored.readBytes().contentEquals(source.readBytes()))

        val corrupted = encrypted.readBytes().also { bytes -> bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte() }
        encrypted.writeBytes(corrupted)
        assertTrue(runCatching { archiveCipher.decrypt(encrypted, corruptedRestored) }.isFailure)
        assertFalse(corruptedRestored.exists())

        source.delete()
        encrypted.delete()
        restored.delete()
    }

    private class FakeSessionStorage(private var session: UserSession?) : SessionStorage {
        override fun getSession() = session
        override fun getOriginBoundSession() = session?.let { OriginBoundSession("https://sync.example", it) }
        override fun getSessionFlow() = MutableStateFlow(session)
        override suspend fun hasValidSession() = session != null
        override suspend fun saveSession(session: UserSession) { this.session = session }
        override suspend fun clearSession() { session = null }
        override suspend fun replaceSessionIfCurrent(expected: OriginBoundSession, updated: UserSession): Boolean {
            if (getOriginBoundSession() != expected || updated.accountId != expected.session.accountId) return false
            session = updated
            return true
        }

        fun changeSession(updated: UserSession) {
            session = updated
        }
    }

    private class FakeDeviceIdProvider : DeviceIdProvider {
        private val id = MutableStateFlow(Uuid.parse("00000000-0000-0000-0000-000000000001"))
        override fun getDeviceId() = id
        override suspend fun refreshDeviceId() = Unit
    }

    private class FakeCloudBackupDataSource(
        var uploadResult: Result<BackupUploadResult>,
    ) : CloudBackupDataSource {
        val uploadResults = mutableListOf<Result<BackupUploadResult>>()
        val listResults = mutableListOf<Result<List<BackupMetadata>>>()
        val fileDownloadResults = mutableListOf<Result<BackupMetadata>>()
        val refreshResults = mutableListOf<Result<String>>()
        val uploadAccessTokens = mutableListOf<String>()
        val uploadedFileDigests = mutableListOf<String>()
        val listAccessTokens = mutableListOf<String>()
        val downloadAccessTokens = mutableListOf<String>()
        var refreshCalls: Int = 0
        var onRefresh: (() -> Unit)? = null
        var uploadCalls: Int = 0
        var fileUploadCalls: Int = 0
        var uploadedFileHeader: String? = null
        var uploadedFileSize: Long = 0L
        var uploadedFileDigest: String? = null
        var uploadedFileManifest: String? = null
        var uploadedFileBytes: ByteArray? = null
        var retainUploadedFileBytes: Boolean = true
        var backups: List<BackupMetadata> = emptyList()
        var downloaded: BackupFile? = null
        var downloadedBackupId: String? = null
        var uploadedBackup: BackupFile? = null
        var fileDownloadCalls: Int = 0
        var downloadedFile: File? = null

        override suspend fun refreshAccessToken(refreshToken: String): Result<String> {
            refreshCalls++
            onRefresh?.invoke()
            return if (refreshResults.isNotEmpty()) refreshResults.removeAt(0) else Result.failure(UnsupportedOperationException())
        }

        override suspend fun uploadBackupFile(
            accessToken: String,
            backup: BackupUploadFileRequest,
        ): Result<BackupUploadResult> {
            fileUploadCalls++
            uploadAccessTokens += accessToken
            val source = File(backup.sourcePath.toString())
            uploadedFileSize = source.length()
            uploadedFileHeader = source.inputStream().use { it.readNBytes(5).decodeToString() }
            uploadedFileDigest = source.inputStream().use { input ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { byte ->
                    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                }
            }
            uploadedFileDigests += requireNotNull(uploadedFileDigest)
            uploadedFileManifest = backup.manifest
            uploadedFileBytes = if (retainUploadedFileBytes) source.readBytes() else null
            return if (uploadResults.isNotEmpty()) uploadResults.removeAt(0) else uploadResult
        }

        override suspend fun uploadBackup(accessToken: String, backup: BackupFile): Result<BackupUploadResult> {
            uploadCalls++
            uploadedBackup = backup
            return uploadResult
        }

        override suspend fun listBackups(accessToken: String): Result<List<BackupMetadata>> {
            listAccessTokens += accessToken
            return if (listResults.isNotEmpty()) listResults.removeAt(0) else Result.success(backups)
        }

        override suspend fun downloadBackup(accessToken: String, backupId: String): Result<BackupFile> {
            downloadedBackupId = backupId
            return downloaded?.let(Result.Companion::success)
                ?: Result.failure(UnsupportedOperationException())
        }

        override suspend fun downloadBackupToFile(
            accessToken: String,
            backupId: String,
            destination: Path,
        ): Result<BackupMetadata> {
            fileDownloadCalls++
            downloadAccessTokens += accessToken
            downloadedBackupId = backupId
            val scriptedResult = if (fileDownloadResults.isNotEmpty()) fileDownloadResults.removeAt(0) else null
            if (scriptedResult?.isFailure == true) return scriptedResult
            val destinationFile = File(destination.toString()).apply { parentFile?.mkdirs() }
            val sourceFile = downloadedFile
            if (sourceFile != null) {
                sourceFile.inputStream().buffered().use { input ->
                    destinationFile.outputStream().buffered().use { output -> input.copyTo(output, 64 * 1024) }
                }
            } else {
                val backup = downloaded ?: return Result.failure(UnsupportedOperationException())
                destinationFile.writeBytes(backup.data)
            }
            return scriptedResult ?: backups.firstOrNull { it.id == backupId }
                ?.let(Result.Companion::success)
                ?: Result.failure(IllegalStateException("Unknown backup"))
        }

        override suspend fun deleteBackup(accessToken: String, backupId: String): Result<Unit> = Result.success(Unit)
    }
}
