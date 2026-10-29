package app.logdate.client.e2e

import android.Manifest
import android.app.job.JobScheduler
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.MainActivity
import app.logdate.client.database.DatabaseStartupMonitor
import app.logdate.client.database.DatabaseStartupState
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.encryption.DatabaseKeyRecoveryRequiredException
import app.logdate.client.database.encryption.PassphraseBackupStore
import app.logdate.client.device.crypto.AndroidIdentityKeyBackupStore
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.restore.NativeJournalRestoreAuthority
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.MediaManager
import app.logdate.client.media.audio.AndroidRecordingServiceController
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.media.audio.RecordingSessionOptions
import app.logdate.client.media.storage.MediaReference
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal suspend fun verifyPhoneRecordingBackupPolicy(
    instrumentation: android.app.Instrumentation,
    context: android.content.Context,
) {
    ensureLogDateApplicationStarted(context.applicationContext as android.app.Application)
    val permissions =
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    permissions.forEach { permission ->
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
    }
    val final = File(context.filesDir, "audio_notes/native-recording-policy-${Uuid.random()}.m4a")
    val staged = CrashSafeRecording.inFlightFile(final, isolateForBackup = true)
    val controller =
        AndroidRecordingServiceController(
            context,
            RecordingSessionOptions(crashSafe = true, excludeIncompleteFromBackup = true),
        )
    try {
        assertTrue(controller.start(final.absolutePath, inputDeviceId = null), "Recording service refused to start")
        val state =
            withTimeout(10_000) {
                controller.serviceState.filterNotNull().first { it.isRecording || it.failureMessage != null }
            }
        assertTrue(state.isRecording, state.failureMessage ?: "Recording service did not start")
        withTimeout(5_000) {
            while (!staged.isFile) delay(25)
        }
        assertFalse(final.exists(), "The in-flight phone recording was visible in the transferable media directory")
        delay(2_500)
        assertEquals(final.absolutePath, controller.stopRecordingNow())
        assertTrue(final.isFile && final.length() > 0L, "The finalized phone recording was not published")
        assertFalse(staged.exists(), "The finalized phone recording left its staging file behind")
    } finally {
        try {
            controller.shutdown()
        } finally {
            final.delete()
            staged.delete()
        }
    }
}

internal suspend fun verifyMissingRecoveryKeys(
    context: android.content.Context,
    koin: org.koin.core.Koin,
) {
    val databaseFile = context.getDatabasePath("logdate")
    assertTrue(databaseFile.isFile && databaseFile.length() > 0, "Native restore did not retain the encrypted journal database")
    val originalDatabaseDigest = MessageDigest.getInstance("SHA-256").digest(databaseFile.readBytes())
    val passphraseBackup = PassphraseBackupStore(context)
    val identityBackup = AndroidIdentityKeyBackupStore(context)
    assertFalse(passphraseBackup.hasBackup(), "The synthetic missing-key transport still contains the database key")
    assertFalse(identityBackup.hasBackup(), "The synthetic missing-key transport still contains identity recovery keys")

    val startupMonitor = koin.get<DatabaseStartupMonitor>()
    assertFailsWith<Exception>("Database startup must refuse a replacement key for retained encrypted data") {
        koin.get<LogDateDatabase>()
    }
    val recovery = startupMonitor.state.value as? DatabaseStartupState.RecoveryRequired
    assertNotNull(recovery, "Missing recovery material must leave database startup in a recoverable state")
    assertTrue(
        recovery.detail.contains("original encryption key"),
        "Recovery should identify the missing journal key without exposing key material",
    )
    ActivityScenario.launch(MainActivity::class.java).use { activity ->
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(
            device.wait(Until.hasObject(By.text("Finish restoring your journal")), 15_000),
            "Missing-key recovery must be reachable before the database-backed application graph",
        )
        assertTrue(device.hasObject(By.textContains("original device")), "Recovery must explain how to obtain the original key")
        activity.recreate()
        assertTrue(device.wait(Until.hasObject(By.text("Finish restoring your journal")), 15_000))
    }
    assertNull(koin.get<SecureStorage>().getString("db_encryption_key"), "Startup persisted a replacement database key")
    assertFalse(koin.get<IdentityKeyManager>().hasIdentityKey(), "Failed startup created a replacement journal identity")
    assertNull(koin.get<SecureStorage>().getString("media_payload_key_v1"), "Failed startup created a replacement media key")
    assertFalse(passphraseBackup.hasBackup(), "Failed startup created portable database recovery material")
    assertFalse(identityBackup.hasRootBackup(), "Failed startup created portable identity recovery material")
    assertFalse(identityBackup.hasMediaBackup(), "Failed startup created portable media recovery material")
    assertTrue(databaseFile.isFile, "Recovery handling deleted the retained encrypted journal")
    assertContentEquals(
        originalDatabaseDigest,
        MessageDigest.getInstance("SHA-256").digest(databaseFile.readBytes()),
        "Recovery handling changed the retained encrypted journal",
    )
}

internal suspend fun verifyRejectedMissingRecoveryKeys(
    context: android.content.Context,
    koin: org.koin.core.Koin,
) {
    val detector = PostRestoreDetector(context)
    assertEquals(
        PostRestoreType.CONTENT_MISSING,
        detector.detect(isOnboarded = true),
        "An incomplete native journal must remain eligible for content recovery",
    )
    val passphraseBackup = PassphraseBackupStore(context)
    assertFalse(passphraseBackup.hasBackup(), "The rejected transfer retained a portable journal key")
    val identityBackup = AndroidIdentityKeyBackupStore(context)
    assertFalse(identityBackup.hasRootBackup(), "The rejected transfer retained an identity recovery key")
    assertFalse(identityBackup.hasMediaBackup(), "The rejected transfer retained a media recovery key")
    assertFalse(context.getDatabasePath("logdate").exists(), "The rejected transfer installed a journal database")
    val startupFailure =
        assertFailsWith<Exception>("A missing original key must not create a replacement journal") {
            koin.get<LogDateDatabase>()
        }
    assertTrue(
        generateSequence<Throwable>(startupFailure) { it.cause }.any { it is DatabaseKeyRecoveryRequiredException },
        "Database startup failed for a reason other than the missing original key",
    )
    assertFalse(context.getDatabasePath("logdate").exists(), "Failed startup created a replacement journal database")
    assertNotNull(
        koin.get<DatabaseStartupMonitor>().state.value as? DatabaseStartupState.RecoveryRequired,
        "Database startup must pause for the original key",
    )
    assertNull(koin.get<SecureStorage>().getString("db_encryption_key"), "Startup created a replacement database key")
    assertFalse(passphraseBackup.hasBackup(), "Startup created portable recovery material")
    ActivityScenario.launch(MainActivity::class.java).use { activity ->
        activity.recreate()
    }
    assertEquals(
        PostRestoreType.CONTENT_MISSING,
        detector.detect(isOnboarded = true),
        "A restart must not acknowledge an incomplete native journal",
    )
    assertFalse(context.getDatabasePath("logdate").exists(), "A restart created a replacement journal database")
    assertNull(koin.get<SecureStorage>().getString("db_encryption_key"), "A restart created a replacement database key")
    assertFalse(passphraseBackup.hasBackup(), "A restart created portable recovery material")
}

internal enum class RecordingTransferExpectation {
    SOURCE,
    CLOUD_TARGET,
    DEVICE_TRANSFER,
}

internal suspend fun verifyRecordingTransferFixture(
    context: android.content.Context,
    inPlace: Boolean,
    expectation: RecordingTransferExpectation,
) {
    val final = File(context.filesDir, "audio_notes/transfer-recording.m4a")
    val inFlight = CrashSafeRecording.inFlightFile(final, isolateForBackup = true)
    val remux = CrashSafeRecording.remuxStagingFile(inFlight, final)
    if (expectation == RecordingTransferExpectation.CLOUD_TARGET) {
        require(!inPlace) { "A cloud target cannot retain source-only recording bytes" }
        assertFalse(final.exists(), "Cloud backup unexpectedly delivered an original recording")
        val koin = GlobalContext.get()
        val note = assertIs<JournalNote.Audio>(koin.get<JournalNotesRepository>().getNoteById(nativeRecordingFixtureId))
        assertFalse(koin.get<MediaManager>().exists(note.mediaRef), "Cloud backup unexpectedly delivered the recording's original")
    } else {
        val expectedRecording =
            if (inPlace && expectation == RecordingTransferExpectation.DEVICE_TRANSFER) {
                "Changed after native D2D backup".encodeToByteArray()
            } else {
                nativeRecordingTransferBytes()
            }
        val actual =
            if (inPlace || expectation == RecordingTransferExpectation.SOURCE) {
                assertTrue(final.isFile, "The source lost its finalized recording")
                final.readBytes()
            } else {
                // Accepted legacy originals migrate to canonical storage; their old filename is not retained.
                val koin = GlobalContext.get()
                val notes = koin.get<JournalNotesRepository>()
                val media = koin.get<MediaManager>()
                var observedReference: String? = null
                try {
                    withTimeout(30_000) {
                        while (true) {
                            val reference = assertIs<JournalNote.Audio>(notes.getNoteById(nativeRecordingFixtureId)).mediaRef
                            observedReference = reference
                            if (media.exists(reference)) break
                            delay(50)
                        }
                    }
                } catch (timeout: TimeoutCancellationException) {
                    if (currentCoroutineContext().isActive) {
                        val observation =
                            runCatching { recordingRecoveryObservation(context, observedReference) }
                                .getOrElse { "Native recovery observation failed: ${it.javaClass.simpleName}" }
                        timeout.addSuppressed(IllegalStateException(observation))
                    }
                    throw timeout
                }
                media.readMedia(assertIs<JournalNote.Audio>(notes.getNoteById(nativeRecordingFixtureId)).mediaRef).data
            }
        assertContentEquals(
            expectedRecording,
            actual,
            "Native device transfer did not preserve the expected recording",
        )
    }
    if (inPlace) {
        assertContentEquals(
            "unfinished raw recording".encodeToByteArray(),
            inFlight.readBytes(),
            "In-place restore must retain excluded source data",
        )
        assertEquals(
            "v1:${inFlight.length()}",
            File(inFlight.parentFile, "${inFlight.name}.ready").readText(),
            "The raw recording lost its completion marker",
        )
        assertFalse(remux.exists(), "Startup recovery must clear the interrupted remux output")
    } else {
        assertFalse(inFlight.exists(), "An interrupted raw recording must not transfer as committed audio")
        assertFalse(File(inFlight.parentFile, "${inFlight.name}.ready").exists(), "An interrupted recording marker must not transfer")
        assertFalse(remux.exists(), "An unfinished remux temporary must not transfer")
    }
}

internal val nativeRecordingFixtureId = Uuid.parse("00000000-0000-0000-0000-000000000015")

internal fun nativeRecordingTransferBytes(): ByteArray =
    InstrumentationRegistry
        .getInstrumentation()
        .context.assets
        .open("recovery/completed-recording.m4a")
        .use { it.readBytes() }

/** Read-only observations at the failed deadline, before instrumentation teardown can change jobs. */
private fun recordingRecoveryObservation(
    context: android.content.Context,
    reference: String?,
): String {
    val fields = linkedMapOf<String, String>()

    fun field(
        name: String,
        read: () -> Any?,
    ) {
        fields[name] = runCatching { read().toString() }.getOrElse { "read-error:${it.javaClass.simpleName}" }
    }

    fun observedFiles(root: File): List<File> {
        if (!Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS)) return emptyList()
        check(Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS))
        return checkNotNull(root.listFiles()).toList()
    }
    val jobId = 0x4e4a5235
    val scheduler = context.getSystemService(JobScheduler::class.java)
    field("jobPendingObserved") { scheduler.getPendingJob(jobId) != null }
    field("jobReasonObserved") { if (Build.VERSION.SDK_INT >= 34) scheduler.getPendingJobReason(jobId) else "unsupported" }
    field("deliveryComplete") { PostRestoreDetector(context).isNativeRestoreDeliveryComplete() }
    field("privacyMissing") { PostRestoreDetector(context).hasMissingNativePrivacyPayload() }
    val authority = NativeJournalRestoreAuthority(context)
    field("completedReceiptPresent") { authority.completedReceipt() != null }
    field("completedRecordingInventoryPresent") {
        authority.completedMediaInventory()?.entry("files/audio_notes/transfer-recording.m4a") != null
    }
    field("referenceObserved") { reference != null }
    field("referenceUnavailable") { reference?.let(UnavailableMediaReferences::isUnavailable) }
    field("originalReferenceKind") {
        reference?.let { MediaReference.parse(UnavailableMediaReferences.nativeSource(it) ?: it).javaClass.simpleName }
    }
    val custodyRoot = File(context.noBackupFilesDir, "native-journal-replay-custody")
    val cohorts =
        runCatching {
            observedFiles(custodyRoot).filter { Files.isDirectory(it.toPath(), LinkOption.NOFOLLOW_LINKS) && !it.name.startsWith('.') }
        }
    field("custodyDirectoriesObserved") { cohorts.getOrThrow().size }
    field("sealedDirectoriesObservedMax128") {
        cohorts.getOrThrow().take(128).count { Files.isRegularFile(File(it, "manifest.json").toPath(), LinkOption.NOFOLLOW_LINKS) }
    }
    field("stagedAdmissionsObservedMax128") {
        observedFiles(File(context.noBackupFilesDir, "native-journal-staged-sources")).take(128).count {
            Files.isDirectory(it.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                Files.isRegularFile(File(it, "admitted").toPath(), LinkOption.NOFOLLOW_LINKS)
        }
    }
    return "Native recording recovery deadline observations: ${fields.entries.joinToString { "${it.key}=${it.value}" }}"
}
