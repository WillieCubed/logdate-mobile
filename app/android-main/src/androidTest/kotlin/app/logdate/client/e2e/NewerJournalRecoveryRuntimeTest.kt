package app.logdate.client.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.MainActivity
import app.logdate.client.database.DatabaseStartupMonitor
import app.logdate.client.database.DatabaseStartupState
import app.logdate.client.database.encryption.PassphraseBackupStore
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.getDatabaseBuilder
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.storage.SecureStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase

/** Uses normal app startup on a fresh managed device, with a synthetic encrypted restore. */
@RunWith(AndroidJUnit4::class)
class NewerJournalRecoveryRuntimeTest {
    @Test
    fun newerJournalStaysEncryptedAndOffersAnUpdateWithoutReplacingItsKey() =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("newerJournalAcceptance") == "true")
            check(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Managed emulator required" }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            check(context.packageName.endsWith(".debug")) { "Synthetic debug app required" }
            val file = context.getDatabasePath("logdate")
            check(!file.exists()) { "A fresh managed device is required; existing journals must not be changed" }
            val storage = GlobalContext.get().get<SecureStorage>()
            assertNull(storage.getString("db_encryption_key"))
            val key = ByteArray(32) { (it + 19).toByte() }
            val journal = Uuid.random()
            val note = Uuid.random()
            val database = getRoomDatabase(getDatabaseBuilder(context, key.copyOf()), driver = null)
            try {
                val at = Instant.fromEpochMilliseconds(1000)
                database.journalDao().create(JournalEntity(journal, "Retained newer journal", "", at, at, syncVersion = 7))
                database.textNoteDao().addNote(
                    TextNoteEntity("Private retained note", note, at, at, syncVersion = 8, deletedAt = Instant.fromEpochMilliseconds(2000)),
                )
                database.journalNotesDao().addNoteToJournal(journal, note)
            } finally {
                database.close()
            }
            cipher(file, key, readOnly = false).use { db ->
                db.execSQL(
                    "INSERT INTO pending_uploads " +
                        "(ownerId, serverOrigin, entityType, entityId, operation, createdAt, retryCount, operationId) " +
                        "VALUES ('owner', 'https://cloud.test', 'NOTE', '$note', 'DELETE', 10, 1, 'retained-operation')",
                )
                db.version = db.version + 1
            }
            val before = snapshot(file, key)
            val backup = PassphraseBackupStore(context)
            backup.write(key)
            val portable = File(context.filesDir, "db_recovery/passphrase_backup").readBytes()
            val detector = PostRestoreDetector(context)
            detector.beginNativeRestoreSession()
            detector.markNativeRestorePayloadReceived(databaseDelivered = true)
            detector.markNativePrivacyPayloadReceived()
            detector.recordRestoreCompleted()
            assertTrue(detector.isNativeRestoreDeliveryComplete())
            val device = UiDevice.getInstance(instrumentation)
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTrue(device.wait(Until.hasObject(By.text("Update LogDate")), 30_000), "Normal startup must offer an app update")
                assertTrue(device.hasObject(By.textContains("newer version of LogDate")))
                assertFalse(device.hasObject(By.text("Try again")), "A missing app update cannot be repaired by retrying startup")
                assertFalse(device.hasObject(By.text("Private retained note")))
                val state =
                    GlobalContext
                        .get()
                        .get<DatabaseStartupMonitor>()
                        .state.value
                assertTrue(state is DatabaseStartupState.RecoveryRequired, "An unsupported journal must never become Ready")
                assertEquals(before, snapshot(file, key))
                assertTrue(portable.contentEquals(File(context.filesDir, "db_recovery/passphrase_backup").readBytes()))
                assertNull(
                    storage.getString("db_encryption_key"),
                    "A refused journal cannot confirm a replacement key",
                )
                assertTrue(backup.read()!!.contentEquals(key))
                val output =
                    requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")) {
                        "Run with a Gradle Managed Device to retain acceptance artifacts"
                    }
                val evidence = File(output, "newer-journal-recovery").apply { mkdirs() }
                assertTrue(device.takeScreenshot(File(evidence, "update-required.png")))
                val urls = mutableListOf<String>()
                val monitor =
                    object : Instrumentation.ActivityMonitor() {
                        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                            if (intent.action != Intent.ACTION_VIEW) return null
                            urls += intent.dataString.orEmpty()
                            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                        }
                    }
                instrumentation.addMonitor(monitor)
                try {
                    device.findObject(By.text("Update LogDate")).click()
                    instrumentation.waitForIdleSync()
                    assertEquals(listOf("market://details?id=${context.packageName}"), urls)
                    assertEquals(before, snapshot(file, key))
                } finally {
                    instrumentation.removeMonitor(monitor)
                }
                File(evidence, "acceptance.json").writeText(
                    """{"encryptedJournalRetained":true,"deletionAndOutboxRetained":true,"portableKeyRetained":true,"normalStartup":true,"googleProviderAccepted":false}""",
                )
            }
        }

    private fun snapshot(
        file: File,
        key: ByteArray,
    ): List<List<String?>> =
        cipher(file, key).use { db ->
            listOf(
                "PRAGMA user_version",
                "SELECT id, title, syncVersion FROM journals ORDER BY id",
                "SELECT uid, content, syncVersion, deletedAt FROM text_notes ORDER BY uid",
                "SELECT id, uid FROM journal_notes ORDER BY id, uid",
                "SELECT ownerId, serverOrigin, entityType, entityId, operation, retryCount, operationId FROM pending_uploads ORDER BY operationId",
            ).flatMap { sql ->
                db.rawQuery(sql, emptyArray<String>()).use { cursor ->
                    buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) }) }
                }
            }
        }

    private fun cipher(
        file: File,
        key: ByteArray,
        readOnly: Boolean = true,
    ): CipherDatabase =
        CipherDatabase.openDatabase(
            file.absolutePath,
            key.copyOf(),
            null,
            if (readOnly) CipherDatabase.OPEN_READONLY else CipherDatabase.OPEN_READWRITE,
            null,
            null,
        )
}
