package app.logdate.client.e2e

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.restore.RestoreResult
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import app.logdate.feature.core.restore.ArchiveFileInfo
import app.logdate.feature.core.restore.ImportOptions
import app.logdate.feature.core.restore.NativeRestoreWorkState
import app.logdate.feature.core.restore.RestoreDiagnosticEvents
import app.logdate.feature.core.restore.RestoreLauncher
import app.logdate.feature.core.restore.RestoreOutcome
import app.logdate.feature.core.restore.RestoreProgressInfo
import app.logdate.feature.core.restore.RestoreStage
import app.logdate.feature.core.restore.RestoreWorker
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.config.PinnedLogDateConfigRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Session loss must keep an accepted cloud handoff available for the original owner. */
@RunWith(AndroidJUnit4::class)
class RestoreWorkerSessionRecoveryTest {
    private lateinit var originalBindings: RestoreWorkerTestBindings
    private lateinit var root: File
    private lateinit var fixture: SessionRecoveryFixture

    @Before
    fun setup() {
        originalBindings = RestoreWorkerTestBindings()
        val application = ApplicationProvider.getApplicationContext<Context>()
        root = File(application.cacheDir, "restore-session-${Uuid.random()}").apply { check(mkdirs()) }
        fixture = SessionRecoveryFixture(SessionRecoveryContext(application, root))
        fixture.installBindings()
    }

    @After
    fun teardown() {
        try {
            if (::originalBindings.isInitialized) originalBindings.restore()
        } finally {
            if (::root.isInitialized) root.deleteRecursively()
        }
    }

    @Test
    fun unavailableSessionAtEntryRetriesAndSameOwnerResumesOnce() =
        runTest {
            fixture.sessions.current.value = null

            assertIs<ListenableWorker.Result.Retry>(fixture.runWorker())
            fixture.assertUnaccepted()
            assertFalse(fixture.launcher.readingReached)

            fixture.resumeAndAssertOneImport()
        }

    @Test
    fun unavailableSessionAfterArchiveReadRetriesAndSameOwnerResumesOnce() =
        runTest {
            fixture.launcher.onReading = { fixture.sessions.current.value = null }

            assertIs<ListenableWorker.Result.Retry>(fixture.runWorker())
            fixture.assertUnaccepted()
            assertTrue(fixture.launcher.readingReached)

            fixture.resumeAndAssertOneImport()
        }

    @Test
    fun differentOwnerAtEntrySkipsWithoutImportAcknowledgmentOrSourceDeletion() =
        runTest {
            fixture.sessions.current.value = fixture.expectedSession.session.copy(accountId = OTHER_OWNER)

            assertIs<ListenableWorker.Result.Success>(fixture.runWorker())
            fixture.assertUnaccepted()
            assertFalse(fixture.launcher.readingReached)
        }

    @Test
    fun differentOwnerAfterArchiveReadSkipsWithoutImportAcknowledgmentOrSourceDeletion() =
        runTest {
            fixture.launcher.onReading = {
                fixture.sessions.current.value = fixture.expectedSession.session.copy(accountId = OTHER_OWNER)
            }

            assertIs<ListenableWorker.Result.Success>(fixture.runWorker())
            fixture.assertUnaccepted()
            assertTrue(fixture.launcher.readingReached)
        }
}

private class SessionRecoveryFixture(
    private val context: Context,
) {
    val sessions = RecoverySessionStorage()
    val expectedSession = requireNotNull(sessions.getOriginBoundSession())
    val launcher = SessionRecoveryLauncher()
    private val detector = PostRestoreDetector(context)
    private val recoveryId = Uuid.random().toString()
    private val archive = createSessionRecoveryArchive(context)
    private val originalBytes = archive.readBytes()
    private val useCase = mockk<RestoreUserDataUseCase>()
    private var imports = 0
    private val deliveryId: String
    private val input: Data

    init {
        detector.beginNativeRestoreSession()
        detector.markNativeRestorePayloadReceived(databaseDelivered = false)
        detector.recordRestoreCompleted()
        deliveryId = assertNotNull(detector.pendingContentRecoveryId())
        input =
            NativeRestoreWorkState
                .bind(
                    Data
                        .Builder()
                        .putString(RestoreWorker.SOURCE_URI_KEY, Uri.fromFile(archive).toString())
                        .putString(RestoreWorker.RECOVERY_ID_KEY, recoveryId)
                        .putString(RestoreWorker.EXPECTED_OWNER_KEY, OWNER)
                        .putString(RestoreWorker.EXPECTED_ORIGIN_KEY, ORIGIN)
                        .putBoolean(RestoreWorker.INCLUDE_DRAFTS_KEY, false)
                        .putBoolean(RestoreWorker.INCLUDE_MEDIA_KEY, false)
                        .putBoolean(RestoreWorker.DELETE_SOURCE_AFTER_RESTORE_KEY, true),
                    deliveryId,
                    expectedSession,
                ).build()
    }

    fun installBindings() {
        coEvery {
            useCase.restore(
                archive = any(),
                options = any(),
                mediaImporter = anyNullable(),
                onProgress = anyNullable(),
                onPendingMedia = anyNullable(),
                mediaRecoveryId = any(),
                expectedNoteScope = anyNullable(),
            )
        } coAnswers {
            assertContentEquals(originalBytes, archive.readBytes())
            val retained = File(context.filesDir, "archive_recovery/sources").listFiles().orEmpty()
            assertEquals(1, retained.size, "The archive must be retained before metadata import")
            assertContentEquals(originalBytes, retained.single().readBytes())
            imports++
            RestoreResult(sessionRecoveryMetadata(), 0, 1, 0, 0, 0, emptyList())
        }
        loadKoinModules(
            module {
                factory { useCase }
                single<MediaManager> { mockk(relaxed = true) }
                single<RestoreLauncher> { launcher }
                single<CanonicalOwnerProvider> {
                    mockk { coEvery { getCanonicalOwnerId() } returns OWNER }
                }
                single<LogDateConfigRepository> { PinnedLogDateConfigRepository(ORIGIN, null) }
                single<SessionStorage> { sessions }
                single<RestoreDiagnosticEvents> { mockk(relaxed = true) }
            },
        )
    }

    suspend fun runWorker(): ListenableWorker.Result =
        TestListenableWorkerBuilder<RestoreWorker>(context).setInputData(input).build().doWork()

    fun assertUnaccepted() {
        assertEquals(0, imports)
        assertEquals(deliveryId, detector.pendingContentRecoveryId())
        assertContentEquals(originalBytes, archive.readBytes())
        assertTrue(launcher.outcomes.isEmpty())
        assertTrue(File(context.filesDir, "archive_recovery/records").listFiles().orEmpty().isEmpty())
    }

    suspend fun resumeAndAssertOneImport() {
        sessions.current.value = expectedSession.session
        assertIs<ListenableWorker.Result.Success>(runWorker())
        assertEquals(1, imports)
        assertNull(detector.pendingContentRecoveryId())
        assertFalse(archive.exists(), "An accepted handoff should clean its original cloud download")
        assertEquals(1, launcher.outcomes.filterIsInstance<RestoreOutcome.Success>().size)

        assertIs<ListenableWorker.Result.Success>(runWorker())
        assertEquals(1, imports, "Replaying the acknowledged delivery must not import twice")
        assertEquals(1, launcher.outcomes.filterIsInstance<RestoreOutcome.Success>().size)
    }
}

private class RecoverySessionStorage : SessionStorage {
    val current = MutableStateFlow<UserSession?>(UserSession("synthetic-access", "synthetic-refresh", OWNER))

    override fun getSession(): UserSession? = current.value

    override fun getOriginBoundSession(): OriginBoundSession? = current.value?.let { OriginBoundSession(ORIGIN, it) }

    override fun getSessionFlow(): StateFlow<UserSession?> = current

    override suspend fun hasValidSession(): Boolean = current.value != null

    override suspend fun saveSession(session: UserSession) {
        current.value = session
    }

    override suspend fun clearSession() {
        current.value = null
    }
}

private class SessionRecoveryLauncher : RestoreLauncher {
    override val restoreProgress = MutableStateFlow<RestoreProgressInfo>(RestoreProgressInfo.Idle)
    val outcomes = mutableListOf<RestoreOutcome>()
    var onReading: (() -> Unit)? = null
    var readingReached = false

    override fun startFileSelection() = Unit

    override fun startRestore(options: ImportOptions) = Unit

    override fun cancelRestore() = Unit

    override fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit) = Unit

    override fun setFileSelectedCallback(callback: (ArchiveFileInfo?) -> Unit) = Unit

    override fun updateProgress(info: RestoreProgressInfo) {
        restoreProgress.value = info
        if (info is RestoreProgressInfo.Active && info.stage == RestoreStage.READING_CONTENTS) {
            readingReached = true
            val fault = onReading
            onReading = null
            fault?.invoke()
        }
    }

    override fun completeRestore(outcome: RestoreOutcome) {
        outcomes += outcome
    }
}

private class SessionRecoveryContext(
    base: Context,
    private val root: File,
) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this

    override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply { dataDir = root.absolutePath }

    override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }

    override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }

    override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }

    override fun getDatabasePath(name: String): File = File(root, "databases/$name").apply { parentFile?.mkdirs() }
}

private fun createSessionRecoveryArchive(context: Context): File {
    val archive = File(context.cacheDir, "cloud-handoff.zip")
    val metadata =
        """{"version":"1.2","exportDate":"1970-01-01T00:00:00Z","userId":"$OWNER","deviceId":"source-device","appVersion":"1.0","stats":{"journalCount":0,"noteCount":1,"draftCount":0,"mediaCount":0}}"""
    val note =
        """{"id":"00000000-0000-0000-0000-000000000333","type":"text","content":"Retain accepted recovery","createdAt":"1970-01-01T00:00:00Z","updatedAt":"1970-01-01T00:00:00Z"}"""
    ZipOutputStream(archive.outputStream()).use { zip ->
        mapOf(
            ExportFileStructure.METADATA_FILE to metadata,
            ExportFileStructure.JOURNALS_FILE to """{"journals":[]}""",
            ExportFileStructure.NOTES_FILE to """{"notes":[$note]}""",
            ExportFileStructure.JOURNAL_NOTES_FILE to """{"journal_notes":[]}""",
            ExportFileStructure.DRAFTS_FILE to """{"drafts":[]}""",
        ).forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
    }
    return archive
}

private fun sessionRecoveryMetadata() =
    ExportMetadata(
        version = ExportSchemaVersion.V1_2,
        exportDate = Instant.fromEpochMilliseconds(0),
        userId = OWNER,
        deviceId = "source-device",
        appVersion = "1.0",
        stats = ExportStats(0, 1, 0, 0),
    )

private const val OWNER = "00000000-0000-0000-0000-000000000111"
private const val OTHER_OWNER = "00000000-0000-0000-0000-000000000222"
private const val ORIGIN = "https://session-recovery.example"
