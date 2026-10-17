package app.logdate.client.device.restore

import android.content.Context
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
import kotlin.test.assertTrue

class PostRestoreDetectorTest {
    private val directory = createTempDirectory("native-restore").toFile()
    private val noBackup = File(directory, "no-backup").apply { mkdirs() }
    private val database = File(directory, "logdate")
    private val context =
        mockk<Context>().also {
            every { it.noBackupFilesDir } returns noBackup
            every { it.getDatabasePath("logdate") } returns database
        }
    private val detector = PostRestoreDetector(context)

    @Test
    fun `media quarantine survives content and account acknowledgement and rejects older delivery`() {
        detector.recordRestoreCompleted()
        val first = assertNotNull(detector.pendingMediaQuarantineId())
        detector.markDeviceInitialized()
        detector.acknowledgeAccountRecovery()
        assertEquals(first, PostRestoreDetector(context).pendingMediaQuarantineId())
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
    fun `an older archive completion cannot acknowledge a later native delivery`() {
        detector.recordRestoreCompleted()
        val old = assertNotNull(detector.pendingContentRecoveryId())
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
