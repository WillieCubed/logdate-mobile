package app.logdate.client.device.restore

import android.content.Context
import android.content.pm.ApplicationInfo
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostRestoreDetectorTest {
    private val directory = createTempDirectory("native-restore").toFile()
    private val noBackup = File(directory, "no-backup").apply { mkdirs() }
    private val files = File(directory, "files").apply { mkdirs() }
    private val appData = File(directory, "app-data").apply { mkdirs() }
    private val database = File(directory, "logdate")
    private val context =
        mockk<Context>().also {
            every { it.noBackupFilesDir } returns noBackup
            every { it.filesDir } returns files
            every { it.applicationInfo } returns ApplicationInfo().apply { dataDir = appData.absolutePath }
            every { it.getDatabasePath("logdate") } returns database
            every { it.getDatabasePath("logdate-wal") } returns File(directory, "logdate-wal")
            every { it.getDatabasePath("logdate-shm") } returns File(directory, "logdate-shm")
        }
    private val detector = PostRestoreDetector(context)

    @Test
    fun `legacy completed deliveries do not require preference capture metadata after upgrade`() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        File(noBackup, ".logdate_native_restore_privacy_received").writeText("received:$id")
        assertFalse(detector.hasMissingNativePrivacyPayload())
        assertEquals(id, detector.nativeRestoreSessionId())
        assertNull(detector.retainedPreferenceSessionId())
    }

    @Test
    fun `preference enrollment remains bound to the same delivery after privacy payload and acknowledgement`() {
        detector.beginNativeRestoreSession(retainPreferences = true)
        val id = assertNotNull(detector.retainedPreferenceSessionId())
        assertTrue(detector.hasMissingNativePrivacyPayload())
        detector.markNativePrivacyPayloadReceived()
        assertFalse(detector.hasMissingNativePrivacyPayload())
        assertEquals(id, detector.retainedPreferenceSessionId())
        detector.markDeviceInitialized()
        assertEquals(id, PostRestoreDetector(context).retainedPreferenceSessionId())
    }

    @Test
    fun `unknown enrollment fields cannot accept missing privacy metadata`() {
        val file = File(noBackup, ".logdate_native_restore_privacy_received")
        for (receipt in listOf("received:123e4567-e89b-12d3-a456-426614174000:unknown", "received:broken:prefs-v1")) {
            file.writeText(receipt)
            assertTrue(detector.hasMissingNativePrivacyPayload())
            assertNull(detector.retainedPreferenceSessionId())
        }
    }

    @Test
    fun `database startup remains blocked until the active delivery is finalized`() {
        detector.beginNativeRestoreSession()

        assertFalse(detector.isNativeRestoreDeliveryComplete())

        detector.markNativeRestorePayloadReceived(databaseDelivered = true)
        detector.recordRestoreCompleted()

        assertTrue(detector.isNativeRestoreDeliveryComplete())
    }

    @Test
    fun `database startup remains blocked when delivery finalization was interrupted`() {
        File(noBackup, ".logdate_native_restore_pending")
            .writeText("delivery-pending:123e4567-e89b-12d3-a456-426614174000")

        assertFalse(detector.isNativeRestoreDeliveryComplete())
    }

    @Test
    fun `finalization resume requires the same active generation and ends after acknowledgement`() {
        detector.beginNativeRestoreSession(retainPreferences = true)
        val generation = assertNotNull(detector.retainedPreferenceSessionId())
        assertTrue(detector.canResumeNativeRestoreFinalization(generation))
        assertFalse(detector.canResumeNativeRestoreFinalization("123e4567-e89b-12d3-a456-426614174000"))

        File(noBackup, ".logdate_native_restore_pending")
            .writeText("delivery-pending:123e4567-e89b-12d3-a456-426614174000")
        assertTrue(PostRestoreDetector(context).canResumeNativeRestoreFinalization(generation))

        detector.recordRestoreCompleted()
        assertFalse(detector.canResumeNativeRestoreFinalization(generation))
        detector.markDeviceInitialized()
        assertFalse(PostRestoreDetector(context).canResumeNativeRestoreFinalization(generation))
    }

    @Test
    fun `resumed metadata-only finalization cannot bless a database created by startup`() {
        detector.beginNativeRestoreSession(retainPreferences = true)
        detector.markNativeRestorePayloadReceived(databaseDelivered = false)
        database.writeBytes(byteArrayOf(1, 2, 3))
        File(noBackup, ".logdate_native_restore_pending")
            .writeText("delivery-pending:123e4567-e89b-12d3-a456-426614174000")

        detector.recordRestoreCompleted()

        assertEquals(PostRestoreType.CONTENT_MISSING, detector.detect(true))
    }

    @Test
    fun `database startup fails closed when the pending delivery marker is malformed`() {
        File(noBackup, ".logdate_native_restore_pending").writeText("restore-in-progress:truncated")

        assertFalse(detector.isNativeRestoreDeliveryComplete())
    }

    @Test
    fun `media quarantine survives content and account acknowledgement and rejects older delivery`() {
        detector.recordRestoreCompleted()
        val first = assertNotNull(detector.pendingMediaQuarantineId())
        detector.markDeviceInitialized()
        detector.acknowledgeAccountRecovery()
        assertEquals(first, PostRestoreDetector(context).pendingMediaQuarantineId())
        database.writeBytes(byteArrayOf(4, 5, 6))
        detector.recordRestoreCompleted()
        val second = assertNotNull(detector.pendingMediaQuarantineId())
        assertFalse(detector.acknowledgeMediaQuarantine(first))
        assertEquals(second, detector.pendingMediaQuarantineId())
        assertTrue(detector.acknowledgeMediaQuarantine(second))
        assertEquals(null, detector.pendingMediaQuarantineId())
    }

    @AfterTest
    fun cleanup() {
        directory.deleteRecursively()
    }

    @Test
    fun `successful archive recovery is acknowledged and never replays at restart`() {
        detector.recordRestoreCompleted()
        val id = assertNotNull(detector.pendingContentRecoveryId())
        assertTrue(detector.acknowledgeContentRecovery(id))
        assertEquals(PostRestoreType.NONE, PostRestoreDetector(context).detect(isOnboarded = true))
        assertEquals(null, PostRestoreDetector(context).pendingContentRecoveryId())
    }

    @Test
    fun `duplicate restore callback preserves the active delivery and recovery work`() {
        detector.recordRestoreCompleted()
        val content = assertNotNull(detector.pendingContentRecoveryId())
        val account = assertNotNull(detector.pendingAccountRecoveryId())
        val media = assertNotNull(detector.pendingMediaQuarantineId())

        detector.recordRestoreCompleted()

        assertEquals(content, detector.pendingContentRecoveryId())
        assertEquals(account, detector.pendingAccountRecoveryId())
        assertEquals(media, detector.pendingMediaQuarantineId())
    }

    @Test
    fun `completed delivery remains identifiable after content acknowledgement`() {
        detector.recordRestoreCompleted()
        detector.markDeviceInitialized()

        assertTrue(detector.hasCompletedNativeRestoreDelivery())

        detector.beginNativeRestoreSession()

        assertFalse(detector.hasCompletedNativeRestoreDelivery())
    }

    @Test
    fun `duplicate callback cannot turn a missing-content delivery complete after startup creates a database`() {
        detector.recordRestoreCompleted()
        val content = assertNotNull(detector.pendingContentRecoveryId())
        database.writeBytes(byteArrayOf(1, 2, 3))

        detector.recordRestoreCompleted()

        assertEquals(content, detector.pendingContentRecoveryId())
        assertEquals(PostRestoreType.CONTENT_MISSING, detector.detect(isOnboarded = true))
    }

    @Test
    fun `later native payload supersedes an incomplete content delivery`() {
        detector.recordRestoreCompleted()
        val old = assertNotNull(detector.pendingContentRecoveryId())
        database.writeBytes(byteArrayOf(1, 2, 3))
        detector.markNativeRestorePayloadReceived(databaseDelivered = true)

        detector.recordRestoreCompleted()

        assertEquals(PostRestoreType.CONTENT_RESTORED, detector.detect(isOnboarded = true))
        assertEquals(null, detector.pendingContentRecoveryId())
        val current = assertNotNull(detector.pendingMediaQuarantineId())
        assertNotEquals(old, current)

        detector.recordRestoreCompleted()

        assertEquals(current, detector.pendingMediaQuarantineId())
    }

    @Test
    fun `metadata-only native delivery cannot treat a startup database as restored content`() {
        database.writeBytes(byteArrayOf(1, 2, 3))
        detector.markDeviceInitialized()
        detector.markNativeRestorePayloadReceived(databaseDelivered = false)

        detector.recordRestoreCompleted()

        assertEquals(PostRestoreType.CONTENT_MISSING, detector.detect(isOnboarded = true))
        assertNotNull(detector.pendingContentRecoveryId())
    }

    @Test
    fun `metadata-only delivery cannot reuse a database receipt left by interrupted delivery`() {
        database.writeBytes(byteArrayOf(1, 2, 3))
        detector.markDeviceInitialized()
        detector.beginNativeRestoreSession()
        detector.markNativeRestorePayloadReceived(databaseDelivered = true)

        // A later BackupAgent instance starts another transfer before a finish callback ran.
        detector.beginNativeRestoreSession()
        detector.markNativeRestorePayloadReceived(databaseDelivered = false)
        detector.recordRestoreCompleted()

        assertEquals(PostRestoreType.CONTENT_MISSING, detector.detect(isOnboarded = true))
        assertNotNull(detector.pendingContentRecoveryId())
    }

    @Test
    fun `receipt from an older generation cannot complete a new delivery after cleanup is interrupted`() {
        database.writeBytes(byteArrayOf(1, 2, 3))
        detector.markDeviceInitialized()
        detector.beginNativeRestoreSession()
        val previousSession =
            File(noBackup, ".logdate_native_restore_pending")
                .readText()
                .substringAfter(':')
        File(noBackup, ".logdate_native_restore_database_received").writeText("received:$previousSession")

        // Simulate process death after publishing the new marker but before deleting old receipts.
        val currentSession =
            kotlin.uuid.Uuid
                .random()
                .toString()
        File(noBackup, ".logdate_native_restore_pending").writeText("restore-in-progress:$currentSession")
        detector.markNativeRestorePayloadReceived(databaseDelivered = false)
        detector.recordRestoreCompleted()

        assertEquals(PostRestoreType.CONTENT_MISSING, detector.detect(isOnboarded = true))
        assertNotNull(detector.pendingContentRecoveryId())
    }

    @Test
    fun `a restore interrupted during a file write remains pending before any receipt is recorded`() {
        database.writeBytes(byteArrayOf(1, 2, 3))
        detector.markDeviceInitialized()

        detector.beginNativeRestoreSession()

        assertEquals(PostRestoreType.RECOVERY_REQUIRED, PostRestoreDetector(context).detect(isOnboarded = true))
    }

    @Test
    fun `a new restore with no file callbacks cannot reuse an interrupted database receipt`() {
        database.writeBytes(byteArrayOf(1, 2, 3))
        detector.markDeviceInitialized()
        detector.beginNativeRestoreSession()
        detector.markNativeRestorePayloadReceived(databaseDelivered = true)

        // A new BackupAgent instance reaches onRestoreFinished without receiving files.
        detector.beginNativeRestoreSession()
        detector.recordRestoreCompleted()

        assertEquals(PostRestoreType.CONTENT_MISSING, detector.detect(isOnboarded = true))
        assertNotNull(detector.pendingContentRecoveryId())
    }

    @Test
    fun `restore completion without a database receipt cannot bless an older database`() {
        database.writeBytes(byteArrayOf(1, 2, 3))
        detector.markDeviceInitialized()
        detector.beginNativeRestoreSession()

        detector.recordRestoreCompleted()

        assertEquals(PostRestoreType.CONTENT_MISSING, detector.detect(isOnboarded = true))
        assertNotNull(detector.pendingContentRecoveryId())
    }

    @Test
    fun `changed native widget preferences create a new restore delivery`() {
        val widgetPreferences =
            File(appData, "shared_prefs/logdate_widget_instances.xml").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
        detector.recordRestoreCompleted()
        val old = assertNotNull(detector.pendingMediaQuarantineId())
        val content = assertNotNull(detector.pendingContentRecoveryId())
        assertTrue(detector.acknowledgeContentRecovery(content))
        detector.acknowledgeAccountRecovery()
        assertTrue(detector.acknowledgeMediaQuarantine(old))
        widgetPreferences.writeBytes(byteArrayOf(4, 5, 6))

        detector.recordRestoreCompleted()

        assertNotEquals(old, detector.pendingMediaQuarantineId())
    }

    @Test
    fun `duplicate restore callback after empty-cloud recovery does not reopen acknowledged work`() {
        detector.recordRestoreCompleted()
        val content = assertNotNull(detector.pendingContentRecoveryId())
        val account = assertNotNull(detector.pendingAccountRecoveryId())
        val media = assertNotNull(detector.pendingMediaQuarantineId())
        assertTrue(detector.acknowledgeContentRecovery(content))
        detector.acknowledgeAccountRecovery(account)
        assertTrue(detector.acknowledgeMediaQuarantine(media))

        detector.recordRestoreCompleted()

        assertEquals(null, detector.pendingContentRecoveryId())
        assertFalse(detector.isAccountRecoveryPending())
        assertEquals(null, detector.pendingMediaQuarantineId())
    }

    @Test
    fun `same-length media replacement creates a new restore delivery`() {
        val media =
            File(files, "media/objects/photo.bin").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
        detector.recordRestoreCompleted()
        val first = assertNotNull(detector.pendingMediaQuarantineId())
        val content = assertNotNull(detector.pendingContentRecoveryId())
        assertTrue(detector.acknowledgeContentRecovery(content))
        detector.acknowledgeAccountRecovery()
        assertTrue(detector.acknowledgeMediaQuarantine(first))

        media.writeBytes(byteArrayOf(4, 5, 6))
        detector.markNativeRestorePayloadReceived(databaseDelivered = true)
        detector.recordRestoreCompleted()

        assertNotEquals(first, detector.pendingMediaQuarantineId())
    }

    @Test
    fun `an older archive completion cannot acknowledge a later native delivery`() {
        detector.recordRestoreCompleted()
        val old = assertNotNull(detector.pendingContentRecoveryId())
        assertTrue(detector.acknowledgeContentRecovery(old))
        detector.markDeviceInitialized()
        File(files, "logdate.preferences_pb").writeBytes(byteArrayOf(7, 8, 9))
        detector.recordRestoreCompleted()
        val current = assertNotNull(detector.pendingContentRecoveryId())
        assertNotEquals(old, current)
        assertFalse(detector.acknowledgeContentRecovery(old))
        assertEquals(current, detector.pendingContentRecoveryId())
        assertTrue(detector.acknowledgeContentRecovery(current))
    }

    @Test
    fun `account resolution stays acknowledged while content recovery is pending`() {
        detector.recordRestoreCompleted()
        detector.acknowledgeAccountRecovery()
        assertFalse(PostRestoreDetector(context).isAccountRecoveryPending())
        assertEquals(PostRestoreType.CONTENT_MISSING, detector.detect(isOnboarded = true))
    }

    @Test
    fun `truncated delivery marker cannot import an archive over an intact database`() {
        database.writeBytes(byteArrayOf(1, 2, 3))
        File(noBackup, ".logdate_native_restore_pending").writeText("")
        assertEquals(PostRestoreType.CONTENT_RESTORED, detector.detect(isOnboarded = true))
    }

    @Test
    fun `unknown delivery marker does not claim confirmed content absence`() {
        File(noBackup, ".logdate_native_restore_pending").writeText("content-")
        assertEquals(PostRestoreType.RECOVERY_REQUIRED, detector.detect(isOnboarded = true))
    }

    @Test
    fun `a database restored from either transport means content present not device transfer`() {
        database.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(PostRestoreType.CONTENT_RESTORED, detector.detect(isOnboarded = true))
    }

    @Test
    fun `restored onboarding without database means content recovery is needed`() {
        assertEquals(PostRestoreType.CONTENT_MISSING, detector.detect(isOnboarded = true))
    }

    @Test
    fun `restore callback survives missing preferences and a new detector instance`() {
        detector.recordRestoreCompleted()
        assertEquals(PostRestoreType.CONTENT_MISSING, PostRestoreDetector(context).detect(isOnboarded = false))
    }

    @Test
    fun `later restore callback reopens reconciliation on an initialized installation`() {
        detector.markDeviceInitialized()
        database.writeBytes(byteArrayOf(1, 2, 3))
        detector.recordRestoreCompleted()
        assertEquals(PostRestoreType.CONTENT_RESTORED, detector.detect(isOnboarded = false))
    }

    @Test
    fun `empty database created at startup cannot turn metadata-only delivery into complete content`() {
        detector.recordRestoreCompleted()
        database.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(PostRestoreType.CONTENT_MISSING, detector.detect(isOnboarded = true))
    }

    @Test
    fun `content acknowledgement leaves account recovery pending across restart`() {
        detector.recordRestoreCompleted()
        detector.markDeviceInitialized()
        assertEquals(true, PostRestoreDetector(context).isAccountRecoveryPending())
        detector.acknowledgeAccountRecovery()
        assertEquals(false, PostRestoreDetector(context).isAccountRecoveryPending())
    }
}
