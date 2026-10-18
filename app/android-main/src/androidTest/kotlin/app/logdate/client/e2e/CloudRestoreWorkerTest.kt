package app.logdate.client.e2e

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.WorkerParameters
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import app.logdate.client.sync.cloud.BackupFile
import app.logdate.client.sync.cloud.BackupMetadata
import app.logdate.client.sync.cloud.BackupUploadResult
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.feature.core.export.CloudArchiveCipher
import app.logdate.feature.core.export.CloudBackupWorker
import app.logdate.feature.core.restore.CloudRestoreScheduler
import app.logdate.feature.core.restore.CloudRestoreWorker
import app.logdate.feature.core.restore.NativeRestoreWorkState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CloudRestoreWorkerTest : CloudArchiveWorkerFixture() {
    private fun workerParameters(requestId: UUID = UUID.randomUUID()): WorkerParameters =
        mockk(relaxed = true) {
            every { id } returns requestId
            every { inputData } returns Data.EMPTY
        }

    @Test
    fun `unbound cloud restore completes quietly without an authenticated session`() =
        runTest {
            val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
            val worker =
                CloudRestoreWorker(
                    context,
                    workerParameters(),
                    cloud,
                    UnavailableSessionStorage,
                    archiveCipher,
                )

            assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
            assertTrue(cloud.listAccessTokens.isEmpty())
            assertEquals(0, cloud.fileDownloadCalls)
        }

    @Test
    fun `cloud restore ignores an obsolete delivery before network access`() =
        verifyObsoleteWork(Data.Builder().putString(NativeRestoreWorkState.DELIVERY_KEY, "obsolete-delivery").build())

    @Test
    fun `stale delivery resolves archive ownership before cleanup`() =
        runTest {
            val folder = File(context.cacheDir, "stale-cloud-restore-${UUID.randomUUID()}").apply { mkdirs() }
            val isolatedContext =
                object : ContextWrapper(context) {
                    override fun getNoBackupFilesDir() = File(folder, "no_backup").apply { mkdirs() }

                    override fun getDatabasePath(name: String) = File(folder, "databases/$name")
                }
            val detector = PostRestoreDetector(isolatedContext)
            detector.recordRestoreCompleted()
            val obsoleteDelivery = requireNotNull(detector.pendingContentRecoveryId())
            detector.recordRestoreCompleted()
            val session = FakeSessionStorage(UserSession("access", "refresh", "account"))
            val input = NativeRestoreWorkState.bind(Data.Builder(), obsoleteDelivery, session.getOriginBoundSession()).build()

            try {
                for (activeChild in listOf(true, false)) {
                    val workId = UUID.randomUUID()
                    val params = workerParameters(workId)
                    val archive = File(isolatedContext.noBackupFilesDir, "cloud-restore-$workId.zip")
                    archive.writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte()))
                    every { params.inputData } returns input
                    val worker =
                        CloudRestoreWorker(
                            isolatedContext,
                            params,
                            FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))),
                            session,
                            archiveCipher,
                            hasActiveRestore = { deliveryId, accountId, origin ->
                                assertEquals(obsoleteDelivery, deliveryId)
                                assertEquals("account", accountId)
                                assertEquals("https://sync.example", origin)
                                activeChild
                            },
                        )

                    assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
                    assertEquals(
                        activeChild,
                        archive.exists(),
                        "Stale work must retain an archive for an active child and remove an unowned archive",
                    )
                }
            } finally {
                folder.deleteRecursively()
            }
        }

    @Test
    fun `cloud restore ignores a queued request for a different owner`() =
        verifyObsoleteWork(
            Data
                .Builder()
                .putString(NativeRestoreWorkState.OWNER_KEY, "previous-owner")
                .putString(NativeRestoreWorkState.ORIGIN_KEY, "https://sync.example")
                .build(),
        )

    @Test
    fun `cloud restore retries while the persisted session owner is unavailable`() =
        runTest {
            val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
            val params = workerParameters()
            every { params.inputData } returns
                Data
                    .Builder()
                    .putString(NativeRestoreWorkState.OWNER_KEY, "retained-owner")
                    .putString(NativeRestoreWorkState.ORIGIN_KEY, "https://sync.example")
                    .build()
            val handedOffArchive = File(context.noBackupFilesDir, "cloud-restore-${params.id}.zip")
            handedOffArchive.writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte()))
            val worker =
                CloudRestoreWorker(
                    context,
                    params,
                    cloud,
                    UnavailableSessionStorage,
                    archiveCipher,
                    enqueueRestore = { error("Restore must wait for the persisted session") },
                )

            try {
                assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
                assertTrue(cloud.listAccessTokens.isEmpty())
                assertEquals(0, cloud.fileDownloadCalls)
                assertTrue(handedOffArchive.isFile, "Temporary session loss must not delete an archive owned by restore work")
            } finally {
                handedOffArchive.delete()
            }
        }

    @Test
    fun `cloud restore preserves a handed off archive when active restore lookup fails`() =
        runTest {
            val folder = File(context.cacheDir, "restore-lookup-failure-${UUID.randomUUID()}").apply { mkdirs() }
            val isolatedContext =
                object : ContextWrapper(context) {
                    override fun getNoBackupFilesDir() = File(folder, "no_backup").apply { mkdirs() }

                    override fun getDatabasePath(name: String) = File(folder, "databases/$name")
                }
            val detector = PostRestoreDetector(isolatedContext)
            val session = FakeSessionStorage(UserSession("access", "refresh", "account"))
            detector.recordRestoreCompleted()
            val delivery = requireNotNull(detector.pendingContentRecoveryId())
            val input = NativeRestoreWorkState.bind(Data.Builder(), delivery, session.getOriginBoundSession()).build()
            val workId = UUID.randomUUID()
            val params = workerParameters(workId)
            val handedOffArchive = File(isolatedContext.noBackupFilesDir, "cloud-restore-$workId.zip")
            handedOffArchive.writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte()))
            every { params.inputData } returns input
            val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
            val worker =
                CloudRestoreWorker(
                    isolatedContext,
                    params,
                    cloud,
                    session,
                    archiveCipher,
                    hasActiveRestore = { _, _, _ -> error("WorkManager query temporarily failed") },
                )

            try {
                assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
                assertTrue(cloud.listAccessTokens.isEmpty())
                assertTrue(
                    handedOffArchive.isFile,
                    "A failed WorkManager query cannot establish that an active restore released its archive",
                )
            } finally {
                folder.deleteRecursively()
            }
        }

    @Test
    fun `cloud restore retries when persisted session validation temporarily fails`() =
        runTest {
            val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
            val params = workerParameters()
            every { params.inputData } returns
                Data
                    .Builder()
                    .putString(NativeRestoreWorkState.OWNER_KEY, "retained-owner")
                    .putString(NativeRestoreWorkState.ORIGIN_KEY, "https://sync.example")
                    .build()
            val worker =
                CloudRestoreWorker(
                    context,
                    params,
                    cloud,
                    FailingSessionStorage,
                    archiveCipher,
                    enqueueRestore = { error("Restore must wait for session validation") },
                )

            assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
            assertTrue(cloud.listAccessTokens.isEmpty())
            assertEquals(0, cloud.fileDownloadCalls)
        }

    @Test
    fun `cloud restore retries when session becomes unavailable after owner validation`() =
        runTest {
            val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
            val params = workerParameters()
            every { params.inputData } returns
                Data
                    .Builder()
                    .putString(NativeRestoreWorkState.OWNER_KEY, "retained-owner")
                    .putString(NativeRestoreWorkState.ORIGIN_KEY, "https://sync.example")
                    .build()
            val worker =
                CloudRestoreWorker(
                    context,
                    params,
                    cloud,
                    SessionStorageBecomesUnavailable,
                    archiveCipher,
                    enqueueRestore = { error("Restore must wait for the owner session") },
                )

            assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
            assertTrue(cloud.listAccessTokens.isEmpty())
            assertEquals(0, cloud.fileDownloadCalls)
        }

    @Test
    fun `cloud restore work is unique per account and origin`() {
        val first = OriginBoundSession("https://sync.example", UserSession("a", "r", "one"))
        val second = OriginBoundSession("https://sync.example", UserSession("a", "r", "two"))
        val otherOrigin = OriginBoundSession("https://other.example", UserSession("a", "r", "one"))

        assertEquals(CloudRestoreScheduler.workName(first), CloudRestoreScheduler.workName(first))
        assertFalse(CloudRestoreScheduler.workName(first).contains("one"))
        assertFalse(CloudRestoreScheduler.workName(first).contains("sync.example"))
        assertFalse(CloudRestoreScheduler.workName(first) == CloudRestoreScheduler.workName(second))
        assertFalse(CloudRestoreScheduler.workName(first) == CloudRestoreScheduler.workName(otherOrigin))
        assertFalse(
            CloudRestoreScheduler.workName(first, "delivery-one") ==
                CloudRestoreScheduler.workName(first, "delivery-two"),
        )
        assertEquals(
            CloudRestoreScheduler.deliveryWorkTag("delivery-one", first),
            CloudRestoreScheduler.deliveryWorkTag("delivery-one", first),
        )
        assertFalse(
            CloudRestoreScheduler.deliveryWorkTag("delivery-one", first) ==
                CloudRestoreScheduler.deliveryWorkTag("delivery-two", first),
        )
        assertFalse(
            CloudRestoreScheduler.deliveryWorkTag("delivery-one", first) ==
                CloudRestoreScheduler.deliveryWorkTag("delivery-one", second),
        )
    }

    @Test
    fun `empty cloud account acknowledges native content recovery without changing local data`() =
        runTest {
            val folder = File(context.cacheDir, "empty-cloud-restore-${UUID.randomUUID()}").apply { mkdirs() }
            val isolatedContext =
                object : ContextWrapper(context) {
                    override fun getNoBackupFilesDir() = File(folder, "no_backup").apply { mkdirs() }

                    override fun getDatabasePath(name: String) = File(folder, "databases/$name")
                }
            val detector = PostRestoreDetector(isolatedContext)
            val session = FakeSessionStorage(UserSession("access", "refresh", "account"))
            detector.recordRestoreCompleted()
            val delivery = requireNotNull(detector.pendingContentRecoveryId())
            val input = NativeRestoreWorkState.bind(Data.Builder(), delivery, session.getOriginBoundSession()).build()
            val params = workerParameters()
            every { params.inputData } returns input
            val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
            val worker = CloudRestoreWorker(isolatedContext, params, cloud, session, archiveCipher)

            try {
                assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
                assertNull(detector.pendingContentRecoveryId())
                assertEquals(PostRestoreType.NONE, detector.detect(isOnboarded = true))
                assertEquals(0, cloud.fileDownloadCalls)
                assertFalse(isolatedContext.getDatabasePath("logdate").exists())
            } finally {
                folder.deleteRecursively()
            }
        }

    @Test
    fun `empty cloud account keeps native recovery pending when account changes before acknowledgement`() =
        runTest {
            val folder = File(context.cacheDir, "switched-cloud-restore-${UUID.randomUUID()}").apply { mkdirs() }
            val isolatedContext =
                object : ContextWrapper(context) {
                    override fun getNoBackupFilesDir() = File(folder, "no_backup").apply { mkdirs() }

                    override fun getDatabasePath(name: String) = File(folder, "databases/$name")
                }
            val detector = PostRestoreDetector(isolatedContext)
            val backingSession = FakeSessionStorage(UserSession("access", "refresh", "account"))
            val session =
                object : SessionStorage by backingSession {
                    override suspend fun runIfSessionCurrent(
                        expected: OriginBoundSession?,
                        action: () -> Unit,
                    ): Boolean {
                        backingSession.changeSession(UserSession("new-access", "new-refresh", "new-account"))
                        return false
                    }
                }
            detector.recordRestoreCompleted()
            val delivery = requireNotNull(detector.pendingContentRecoveryId())
            val input = NativeRestoreWorkState.bind(Data.Builder(), delivery, session.getOriginBoundSession()).build()
            val params = workerParameters()
            every { params.inputData } returns input
            val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
            val worker = CloudRestoreWorker(isolatedContext, params, cloud, session, archiveCipher)

            try {
                assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
                assertEquals(delivery, detector.pendingContentRecoveryId())
                assertEquals(0, cloud.fileDownloadCalls)
            } finally {
                folder.deleteRecursively()
            }
        }

    @Test
    fun `cloud restore does not download again while its delivery restore is active`() =
        runTest {
            val folder = File(context.cacheDir, "active-cloud-restore-${UUID.randomUUID()}").apply { mkdirs() }
            val isolatedContext =
                object : ContextWrapper(context) {
                    override fun getNoBackupFilesDir() = File(folder, "no_backup").apply { mkdirs() }

                    override fun getDatabasePath(name: String) = File(folder, "databases/$name")
                }
            val detector = PostRestoreDetector(isolatedContext)
            val session = FakeSessionStorage(UserSession("access", "refresh", "account"))
            detector.recordRestoreCompleted()
            val delivery = requireNotNull(detector.pendingContentRecoveryId())
            val input = NativeRestoreWorkState.bind(Data.Builder(), delivery, session.getOriginBoundSession()).build()
            val workId = UUID.randomUUID()
            val params = workerParameters(workId)
            val handedOffArchive = File(isolatedContext.noBackupFilesDir, "cloud-restore-$workId.zip")
            handedOffArchive.writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte()))
            every { params.inputData } returns input
            val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
            var handedOff = false
            val worker =
                CloudRestoreWorker(
                    isolatedContext,
                    params,
                    cloud,
                    session,
                    archiveCipher,
                    hasActiveRestore = { taggedDelivery, taggedAccount, taggedOrigin ->
                        assertEquals(delivery, taggedDelivery)
                        assertEquals("account", taggedAccount)
                        assertEquals("https://sync.example", taggedOrigin)
                        true
                    },
                    enqueueRestore = { handedOff = true },
                )

            try {
                assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
                assertTrue(cloud.listAccessTokens.isEmpty())
                assertEquals(0, cloud.fileDownloadCalls)
                assertFalse(handedOff)
                assertEquals(delivery, detector.pendingContentRecoveryId())
                assertTrue(
                    handedOffArchive.isFile,
                    "A resumed cloud worker must preserve the archive still owned by the active RestoreWorker",
                )
            } finally {
                handedOffArchive.delete()
                folder.deleteRecursively()
            }
        }

    private fun verifyObsoleteWork(input: Data) =
        runTest {
            val cloud = FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L)))
            val params = workerParameters()
            every { params.inputData } returns input
            val worker =
                CloudRestoreWorker(
                    context,
                    params,
                    cloud,
                    FakeSessionStorage(UserSession("access", "refresh", "account")),
                    archiveCipher,
                ) { error("Obsolete recovery must not hand off an archive") }
            assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
            assertTrue(cloud.listAccessTokens.isEmpty())
            assertEquals(0, cloud.fileDownloadCalls)
        }

    private object UnavailableSessionStorage : SessionStorage {
        override fun getSession(): UserSession? = null

        override fun getOriginBoundSession(): OriginBoundSession? = null

        override fun getSessionFlow() = MutableStateFlow<UserSession?>(null)

        override suspend fun hasValidSession() = false

        override suspend fun saveSession(session: UserSession) = Unit

        override suspend fun clearSession() = Unit
    }

    private object FailingSessionStorage : SessionStorage {
        override fun getSession(): UserSession? = null

        override fun getOriginBoundSession(): OriginBoundSession? = null

        override fun getSessionFlow() = MutableStateFlow<UserSession?>(null)

        override suspend fun hasValidSession(): Boolean = error("Temporary secure storage read failure")

        override suspend fun saveSession(session: UserSession) = Unit

        override suspend fun clearSession() = Unit
    }

    private object SessionStorageBecomesUnavailable : SessionStorage {
        private val session = UserSession("access", "refresh", "retained-owner")
        private var validationReads = 0

        override fun getSession(): UserSession? = if (validationReads == 1) session else null

        override fun getOriginBoundSession(): OriginBoundSession? =
            if (validationReads == 1) OriginBoundSession("https://sync.example", session) else null

        override fun getSessionFlow() = MutableStateFlow<UserSession?>(getSession())

        override suspend fun hasValidSession(): Boolean {
            validationReads++
            return validationReads == 1
        }

        override suspend fun saveSession(session: UserSession) = Unit

        override suspend fun clearSession() = Unit
    }

    @Test
    fun `downloads and authenticates a one gibibyte archive through the Android restore worker`() =
        runTest {
            val payloadSize = 1L shl 30
            assertTrue(Runtime.getRuntime().maxMemory() < payloadSize)
            val source = File(context.cacheDir, "cloud-restore-1g-source.zip")
            val encrypted = File(context.cacheDir, "cloud-restore-1g-source.encrypted")
            createLargeArchive(source, payloadSize)
            val expectedSize = source.length()
            val expectedDigest = source.sha256()
            archiveCipher.encrypt(source, encrypted)
            source.delete()

            val backup =
                BackupMetadata(
                    id = "large-backup",
                    deviceId = "device",
                    manifest = CloudArchiveCipher.MANIFEST,
                    createdAt = 20L,
                    sizeBytes = encrypted.length(),
                    downloadUrl = "https://unused",
                )
            val cloud =
                FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
                    backups = listOf(backup)
                    downloadedFile = encrypted
                }
            val params = workerParameters()
            var restored: File? = null
            val worker =
                CloudRestoreWorker(
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
    fun `retries a restore after a mid-download no-space failure and removes partial files`() =
        runTest {
            val workId = UUID.randomUUID()
            val source = File(context.cacheDir, "cloud-restore-no-space-source.zip")
            val encrypted = File(context.cacheDir, "cloud-restore-no-space-source.encrypted")
            var restored: File? = null
            try {
                ZipOutputStream(source.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write("{\"schemaVersion\":\"2.0\"}".encodeToByteArray())
                    zip.closeEntry()
                }
                archiveCipher.encrypt(source, encrypted)
                val backup = restoreBackupMetadata("no-space").copy(sizeBytes = encrypted.length())
                val cloud =
                    FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
                        backups = listOf(backup)
                        downloadedFile = encrypted
                        fileDownloadOverride = { destination ->
                            destination.writeBytes(byteArrayOf(0x4c, 0x44, 0x43, 0x42, 0x32))
                            Result.failure(IOException("No space left on device"))
                        }
                    }
                val params = workerParameters(workId)
                var handoffCalled = false
                val worker =
                    CloudRestoreWorker(
                        context,
                        params,
                        cloud,
                        FakeSessionStorage(UserSession("access", "refresh", "account")),
                        archiveCipher,
                    ) { handoffCalled = true }

                assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
                assertEquals(1, cloud.fileDownloadCalls)
                assertFalse(handoffCalled)
                assertRestoreArtifactsRemoved(workId)

                val retry =
                    CloudRestoreWorker(
                        context,
                        workerParameters(workId),
                        cloud,
                        FakeSessionStorage(UserSession("access", "refresh", "account")),
                        archiveCipher,
                    ) { archive -> restored = archive }
                assertTrue(retry.doWork() is androidx.work.ListenableWorker.Result.Success)
                assertEquals(2, cloud.fileDownloadCalls)
                assertTrue(requireNotNull(restored).isFile)
                ZipFile(requireNotNull(restored)).use { zip ->
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
            assertRestoreArtifactsRemoved(workId)
        }

    @Test
    fun `propagates restore download cancellation and removes partial files`() =
        runTest {
            val workId = UUID.randomUUID()
            val backup = restoreBackupMetadata("cancelled-download")
            val cloud =
                FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
                    backups = listOf(backup)
                    fileDownloadOverride = { destination ->
                        destination.writeBytes(byteArrayOf(0x4c, 0x44, 0x43, 0x42, 0x32))
                        throw CancellationException("download cancelled after a partial write")
                    }
                }
            val params = workerParameters(workId)
            var handoffCalled = false
            val worker =
                CloudRestoreWorker(
                    context,
                    params,
                    cloud,
                    FakeSessionStorage(UserSession("access", "refresh", "account")),
                    archiveCipher,
                ) { handoffCalled = true }

            assertFailsWith<CancellationException> { worker.doWork() }
            assertEquals(1, cloud.fileDownloadCalls)
            assertFalse(handoffCalled)
            assertRestoreArtifactsRemoved(workId)
        }

    @Test
    fun `expired restore tokens refresh for listing and streamed download`() =
        runTest {
            val source =
                File(context.cacheDir, "cloud-refresh-restore-source.zip").apply {
                    writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3))
                }
            val encryptedArchive = archiveCipher.encrypt(source)
            source.delete()
            val backup = BackupMetadata("backup", "device", CloudArchiveCipher.MANIFEST, 20L, 64L, "https://unused")
            val cloud =
                FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
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
            val params = workerParameters()
            val worker =
                CloudRestoreWorker(
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
    fun `token refresh does not overwrite a session after account changes`() =
        runTest {
            val cloud =
                FakeCloudBackupDataSource(Result.failure(IllegalStateException("offline"))).apply {
                    uploadResults += Result.failure(CloudApiException("UNAUTHORIZED", "expired", 401))
                    refreshResults += Result.success("must-not-be-saved")
                }
            val session = FakeSessionStorage(UserSession("access", "refresh", "account"))
            cloud.onRefresh = { session.changeSession(UserSession("other-access", "other-refresh", "other-account")) }
            val params = workerParameters()
            val worker =
                CloudBackupWorker(
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
    fun `cloud restore downloads newest backup and enqueues normal restore`() =
        runTest {
            val plainArchive =
                File(context.cacheDir, "cloud-restore-source.zip").apply {
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
            val cloud =
                FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
                    backups =
                        listOf(
                            newest.copy(createdAt = 10L),
                            newest,
                        )
                    downloaded = BackupFile("device", CloudArchiveCipher.MANIFEST, encryptedArchive)
                }
            var enqueued: File? = null
            val params = workerParameters()
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
    fun `cloud restore retries and removes decrypted archive when handoff fails`() =
        runTest {
            val plainArchive =
                File(context.cacheDir, "cloud-restore-failed-handoff-source.zip").apply {
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
            val cloud =
                FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
                    backups = listOf(backup)
                    downloaded = BackupFile("device", CloudArchiveCipher.MANIFEST, encryptedArchive)
                }
            val params = workerParameters()
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
    fun `cloud restore streams and preserves legacy server encrypted zip`() =
        runTest {
            val legacyZip = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 5)
            val backup = BackupMetadata("legacy", "device", "legacy-manifest", 20L, legacyZip.size.toLong(), "https://unused")
            val cloud =
                FakeCloudBackupDataSource(Result.success(BackupUploadResult("unused", 1L, 1L))).apply {
                    backups = listOf(backup)
                    downloaded = BackupFile("device", "legacy-manifest", legacyZip)
                }
            var enqueued: File? = null
            val params = workerParameters()
            val worker =
                CloudRestoreWorker(
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
}
