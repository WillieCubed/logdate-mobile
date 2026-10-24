package app.logdate.client.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.database.NativeJournalSnapshotScope
import app.logdate.client.database.captureNativeJournalDatabaseSnapshot
import app.logdate.client.database.getDatabaseBuilder
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.database.verifyNativeJournalDatabaseSnapshot
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class NativeJournalSnapshotContentionTest {
    @Test
    fun backupWaitsForWriterThenExportsConsistentSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.filesDir, "snapshot-contention-${Uuid.random()}").apply { check(mkdirs()) }
        val source = File(root, "source-logdate")
        val target = File(root, "transfer-logdate")
        val key = ByteArray(32) { (it + 1).toByte() }
        val room = getRoomDatabase(getDatabaseBuilder(context, key.copyOf(), source), driver = null)
        try {
            room.openHelper.writableDatabase
        } finally {
            room.close()
        }
        val owner = Uuid.random().toString()
        val preferencesHash =
            MessageDigest.getInstance("SHA-256").digest("fixture".encodeToByteArray()).joinToString("") {
                "%02x".format(it)
            }
        val writer =
            SQLiteDatabase.openDatabase(source.absolutePath, key, null, SQLiteDatabase.OPEN_READWRITE, null, null)
        val executor = Executors.newSingleThreadExecutor()
        try {
            writer.execSQL("CREATE TABLE backup_contention_fixture (id INTEGER PRIMARY KEY)")
            writer.beginTransaction()
            writer.execSQL("INSERT INTO backup_contention_fixture (id) VALUES (1)")
            val started = CountDownLatch(1)
            val export =
                executor.submit {
                    started.countDown()
                    captureNativeJournalDatabaseSnapshot(source, key.copyOf(), target) { writeScope ->
                        writeScope(NativeJournalSnapshotScope(owner, false, "https://logdate.app", preferencesHash))
                    }
                }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            Thread.sleep(6_000)
            assertFalse(export.isDone, "The snapshot must wait while a writer holds an uncommitted change")
            writer.endTransaction()
            export.get(20, TimeUnit.SECONDS)
            assertEquals(owner, verifyNativeJournalDatabaseSnapshot(target, key).ownerId)
            SQLiteDatabase.openDatabase(target.absolutePath, key, null, SQLiteDatabase.OPEN_READONLY, null, null).use {
                it.rawQuery("SELECT COUNT(*) FROM backup_contention_fixture", emptyArray<String>()).use { rows ->
                    assertTrue(rows.moveToFirst())
                    assertEquals(0, rows.getInt(0), "The export must omit the writer's uncommitted row")
                }
            }
        } finally {
            if (writer.inTransaction()) writer.endTransaction()
            writer.close()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
            root.deleteRecursively()
            key.fill(0)
        }
    }
}
