package app.logdate.client.e2e

import android.Manifest
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
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.audio.AndroidRecordingServiceController
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.media.audio.RecordingSessionOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

internal fun verifyRecordingTransferFixture(
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
    } else {
        assertTrue(final.isFile, "Native device transfer did not retain the recording")
        val expectedRecording =
            if (inPlace && expectation == RecordingTransferExpectation.DEVICE_TRANSFER) {
                "Changed after native D2D backup"
            } else {
                "committed recording"
            }
        assertContentEquals(
            expectedRecording.encodeToByteArray(),
            final.readBytes(),
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
