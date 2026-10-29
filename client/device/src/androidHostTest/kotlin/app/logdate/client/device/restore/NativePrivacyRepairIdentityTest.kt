package app.logdate.client.device.restore

import android.content.Context
import android.content.pm.ApplicationInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativePrivacyRepairIdentityTest {
    private val root = Files.createTempDirectory("privacy-repair-identity-").toFile()
    private val noBackup = File(root, "no-backup").apply { mkdirs() }
    private val files = File(root, "files").apply { mkdirs() }
    private val database = File(root, "logdate")
    private val context =
        mockk<Context>().also {
            every { it.noBackupFilesDir } returns noBackup
            every { it.filesDir } returns files
            every { it.applicationInfo } returns ApplicationInfo().apply { dataDir = root.absolutePath }
            every { it.getDatabasePath(any()) } answers { File(root, firstArg<String>()) }
        }
    private val detector = PostRestoreDetector(context)

    @AfterTest
    fun cleanup() {
        check(root.deleteRecursively())
    }

    @Test
    fun `capturing completed identity is read only and preserves distinct native and account ids`() {
        val (generation, account) = completeDelivery()
        val before = markerBytes()
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        assertEquals(generation, identity.generation)
        assertEquals(account, identity.accountDeliveryId)
        assertTrue(generation != account)
        assertEquals(before, markerBytes())
        assertEquals(identity, assertNotNull(PostRestoreDetector(context).capturePrivacyRepairIdentity()))
    }

    @Test
    fun `acknowledgement changes only same generation privacy receipt and is idempotent`() {
        val (generation, _) = completeDelivery()
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        val before = markerBytes().minus(PRIVACY)
        assertTrue(detector.acknowledgePrivacyRepair(identity))
        assertEquals("received:$generation:prefs-v1", marker(PRIVACY).readText())
        assertEquals(before, markerBytes().minus(PRIVACY))
        assertTrue(detector.acknowledgePrivacyRepair(identity))
        assertEquals(before, markerBytes().minus(PRIVACY))
    }

    @Test
    fun `same account resolution may progress through durable both-marker crash state`() {
        val (_, account) = completeDelivery()
        val pending = assertNotNull(detector.capturePrivacyRepairIdentity())
        marker(RESOLVED).writeText(account)
        val both = assertNotNull(detector.capturePrivacyRepairIdentity())
        assertTrue(detector.acknowledgePrivacyRepair(pending))
        assertTrue(marker(PENDING_ACCOUNT).delete())
        assertTrue(detector.acknowledgePrivacyRepair(pending))
        assertTrue(detector.acknowledgePrivacyRepair(both))
        assertEquals(account, marker(RESOLVED).readText())
        assertFalse(marker(PENDING_ACCOUNT).exists())
    }

    @Test
    fun `real account acknowledgement does not invalidate same completed repair`() {
        val (_, account) = completeDelivery()
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        detector.acknowledgeAccountRecovery(account)
        val before = markerBytes().minus(PRIVACY)
        assertTrue(detector.acknowledgePrivacyRepair(identity))
        assertEquals(before, markerBytes().minus(PRIVACY))
    }

    @Test
    fun `identity matching validates forward progress without acknowledging privacy`() {
        val (_, account) = completeDelivery()
        val expected = assertNotNull(detector.capturePrivacyRepairIdentity())
        detector.acknowledgeAccountRecovery(account)
        val current = assertNotNull(detector.capturePrivacyRepairIdentity())
        val before = markerBytes()
        assertTrue(nativePrivacyRepairIdentityMayAdvance(expected, current))
        assertFalse(nativePrivacyRepairIdentityMayAdvance(current, expected))
        assertFalse(nativePrivacyRepairIdentityMayAdvance(expected, current.copy(generation = OTHER)))
        assertEquals(before, markerBytes())
    }

    @Test
    fun `resolved account cannot regress to pending even with same id`() {
        val (_, account) = completeDelivery()
        detector.acknowledgeAccountRecovery(account)
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        assertTrue(marker(RESOLVED).delete())
        marker(PENDING_ACCOUNT).writeText(account)
        assertRejectedUnchanged(identity)
    }

    @Test
    fun `new native generation cannot acknowledge old privacy`() {
        completeDelivery()
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        detector.beginNativeRestoreSession(retainPreferences = true)
        assertRejectedUnchanged(identity)
        assertNull(detector.capturePrivacyRepairIdentity())
    }

    @Test
    fun `changed admission content or initialized identity rejects repair`() {
        val (generation, _) = completeDelivery()
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        for ((name, value) in listOf(
            ".logdate_account_admission_$generation" to OTHER,
            CONTENT to "content-missing:$OTHER",
            CONTENT to "content-missing:${identity.accountDeliveryId}",
            INITIALIZED to "initialized",
            PRIVACY to "pending:$generation",
        )) {
            val old = marker(name).takeIf(File::exists)?.readText()
            marker(name).writeText(value)
            assertRejectedUnchanged(identity)
            if (old == null) assertTrue(marker(name).delete()) else marker(name).writeText(old)
        }
    }

    @Test
    fun `different or malformed account markers cannot acknowledge repair`() {
        val (_, account) = completeDelivery()
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        for ((pending, resolved) in listOf(OTHER to null, account to OTHER, account to "damaged", "damaged" to null, null to null)) {
            if (pending == null) marker(PENDING_ACCOUNT).delete() else marker(PENDING_ACCOUNT).writeText(pending)
            if (resolved == null) marker(RESOLVED).delete() else marker(RESOLVED).writeText(resolved)
            assertRejectedUnchanged(identity)
            assertNull(detector.capturePrivacyRepairIdentity())
        }
    }

    @Test
    fun `uncompleted or damaged receipts never produce an identity or create account ids`() {
        assertNull(detector.capturePrivacyRepairIdentity())
        detector.beginNativeRestoreSession(retainPreferences = true)
        assertNull(detector.capturePrivacyRepairIdentity())
        assertFalse(marker(PENDING_ACCOUNT).exists())
        completeDelivery()
        for (name in listOf(PRIVACY, CONTENT, PENDING_ACCOUNT)) {
            val old = marker(name).readText()
            marker(name).writeText("damaged")
            val before = markerBytes()
            assertNull(detector.capturePrivacyRepairIdentity())
            assertEquals(before, markerBytes())
            marker(name).writeText(old)
        }
    }

    @Test
    fun `received legacy privacy receipt is still bound to actual completed identity`() {
        val (generation, _) = completeDelivery()
        marker(PRIVACY).writeText("received:$generation")
        val before = markerBytes()
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        assertTrue(detector.acknowledgePrivacyRepair(identity))
        assertEquals(before, markerBytes())
    }

    @Test
    fun `acknowledged content retains native identity without reopening account or content`() {
        val (_, account) = completeDelivery()
        detector.markDeviceInitialized()
        detector.acknowledgeAccountRecovery(account)
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        val before = markerBytes().minus(PRIVACY)
        assertTrue(detector.acknowledgePrivacyRepair(identity))
        assertEquals(before, markerBytes().minus(PRIVACY))
        assertFalse(marker(CONTENT).exists())
        assertFalse(detector.isAccountRecoveryPending())
    }

    @Test
    fun `serialized identity resumes after reconstruction and same account resolution`() {
        val (_, account) = completeDelivery()
        val encoded = Json.encodeToString(assertNotNull(detector.capturePrivacyRepairIdentity()))
        detector.acknowledgeAccountRecovery(account)
        val restored = Json.decodeFromString<NativePrivacyRepairIdentity>(encoded)
        val before = markerBytes().minus(PRIVACY)
        assertTrue(PostRestoreDetector(context).acknowledgePrivacyRepair(restored))
        assertEquals(before, markerBytes().minus(PRIVACY))
    }

    @Test
    fun `intermediate account acknowledgement cannot regress to pending`() {
        val (_, account) = completeDelivery()
        marker(RESOLVED).writeText(account)
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        assertTrue(marker(RESOLVED).delete())
        assertRejectedUnchanged(identity)
    }

    @Test
    fun `received privacy cannot regress to pending within the same generation`() {
        val (generation, _) = completeDelivery()
        marker(PRIVACY).writeText("received:$generation:prefs-v1")
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        marker(PRIVACY).writeText("pending:$generation:prefs-v1")
        assertRejectedUnchanged(identity)
    }

    @Test
    fun `missing admission never manufactures account identity`() {
        val (generation, _) = completeDelivery()
        assertTrue(marker(".logdate_account_admission_$generation").delete())
        val before = markerBytes()
        assertNull(detector.capturePrivacyRepairIdentity())
        assertEquals(before, markerBytes())
    }

    @Test
    fun `oversized or symlink receipt cannot capture or acknowledge an identity`() {
        completeDelivery()
        val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
        marker(PRIVACY).writeText("x".repeat(129))
        assertNull(detector.capturePrivacyRepairIdentity())
        assertRejectedUnchanged(identity)
        assertTrue(marker(PRIVACY).delete())
        val outside = File(root, "unrelated-receipt").apply { writeText("private unrelated file") }
        Files.createSymbolicLink(marker(PRIVACY).toPath(), outside.toPath())
        assertNull(detector.capturePrivacyRepairIdentity())
        assertFalse(detector.acknowledgePrivacyRepair(identity))
        assertTrue(Files.isSymbolicLink(marker(PRIVACY).toPath()))
        assertEquals("private unrelated file", outside.readText())
    }

    private fun completeDelivery(): Pair<String, String> {
        detector.beginNativeRestoreSession(retainPreferences = true)
        val generation = assertNotNull(detector.nativeRestoreSessionId())
        val account = detector.reserveAccountRecoveryForCompletion()
        database.writeText("protected native journal")
        detector.markNativeRestorePayloadReceived(databaseDelivered = true)
        detector.recordRestoreCompleted()
        return generation to account
    }

    private fun assertRejectedUnchanged(identity: NativePrivacyRepairIdentity) {
        val before = markerBytes()
        assertFalse(detector.acknowledgePrivacyRepair(identity))
        assertEquals(before, markerBytes())
    }

    private fun marker(name: String) = File(noBackup, name)

    private fun markerBytes(): Map<String, List<Byte>> =
        noBackup.walkTopDown().filter(File::isFile).associate { it.relativeTo(noBackup).path to it.readBytes().toList() }

    private companion object {
        const val OTHER = "11111111-2222-3333-4444-555555555555"
        const val PRIVACY = ".logdate_native_restore_privacy_received"
        const val CONTENT = ".logdate_native_restore_pending"
        const val INITIALIZED = ".logdate_device_initialized"
        const val PENDING_ACCOUNT = ".logdate_account_restore_pending"
        const val RESOLVED = ".logdate_account_restore_resolved"
    }
}
