package app.logdate.client.e2e

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.device.restore.RetainedJournalWorkspace
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase

/** Real SQLCipher bytes; initialization and interrupted activation are isolated to synthetic files. */
@RunWith(AndroidJUnit4::class)
class NativeRetainedJournalWorkspaceTest {
    @Test
    fun committedWalAndKeysSurviveIndependentWorkingJournal() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("retainedJournalWorkspace") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check((Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") && context.packageName.endsWith(".debug"))
        System.loadLibrary("sqlcipher")
        val root = File(context.cacheDir, "retained-workspace-${Uuid.random()}").apply { check(mkdirs()) }
        try {
            val originalKey = ByteArray(32).also(SecureRandom()::nextBytes)
            val workingKey = ByteArray(32).also(SecureRandom()::nextBytes)
            val seed = File(root, "seed")
            val database = File(root, "database/logdate").apply { parentFile.mkdirs() }
            CipherDatabase
                .openDatabase(
                    seed.absolutePath,
                    originalKey,
                    null,
                    CipherDatabase.OPEN_READWRITE or CipherDatabase.CREATE_IF_NECESSARY,
                    null,
                    null,
                ).use { db ->
                    db.rawQuery("PRAGMA journal_mode=WAL", emptyArray<String>()).use { check(it.moveToFirst()) }
                    db.rawQuery("PRAGMA wal_autocheckpoint=0", emptyArray<String>()).use { check(it.moveToFirst()) }
                    db.execSQL("CREATE TABLE retained_entries (content TEXT NOT NULL)")
                    db.execSQL("INSERT INTO retained_entries VALUES ('Original encrypted journal')")
                    listOf("", "-wal", "-shm").forEach { suffix ->
                        val source = File(seed.absolutePath + suffix)
                        check(source.isFile)
                        source.copyTo(File(database.absolutePath + suffix))
                    }
                }
            val portable =
                File(root, "keys/passphrase_backup.part").apply {
                    parentFile.mkdirs()
                    writeText("Original incomplete key bytes")
                }
            val dbFiles = listOf("logdate", "logdate-wal", "logdate-shm").associate { "database/$it" to File(database.parentFile, it) }
            val sources = dbFiles + ("keys/passphrase_backup.part" to portable)
            val hashes = sources.mapValues { checksum(it.value) }
            val retained = File(root, "retained")

            fun store(fail: Boolean = false) =
                RetainedJournalWorkspace(
                    File(root, "state/session.json"),
                    retained,
                    { sources },
                    dbFiles.keys,
                    File(root, "no-backup/authority"),
                    afterDurableStep = {
                        if (fail && it.startsWith("detached:")) {
                            throw IOException("Controlled activation interruption")
                        }
                    },
                )
            assertFailsWith<IOException> { store(fail = true).prepare() }
            val session = checkNotNull(store().resume())
            assertEquals(RetainedJournalWorkspace.Phase.WORKING, session.phase)
            assertFalse(database.exists())
            hashes.forEach { (name, hash) -> assertEquals(hash, checksum(File(retained, "${session.id}/$name"))) }
            val retainedDatabase = File(retained, "${session.id}/database/logdate")
            CipherDatabase
                .openDatabase(
                    retainedDatabase.absolutePath,
                    originalKey,
                    null,
                    CipherDatabase.OPEN_READONLY,
                    null,
                    null,
                ).use { db ->
                    db.rawQuery("SELECT content FROM retained_entries", emptyArray<String>()).use {
                        assertTrue(it.moveToFirst())
                        assertEquals("Original encrypted journal", it.getString(0))
                    }
                }
            CipherDatabase
                .openDatabase(
                    database.absolutePath,
                    workingKey,
                    null,
                    CipherDatabase.OPEN_READWRITE or CipherDatabase.CREATE_IF_NECESSARY,
                    null,
                    null,
                ).use { db ->
                    db.execSQL("CREATE TABLE recovered_entries (content TEXT NOT NULL)")
                    db.execSQL("INSERT INTO recovered_entries VALUES ('Recovered working journal')")
                }
            assertEquals(session, store().resume())
            CipherDatabase.openDatabase(database.absolutePath, workingKey, null, CipherDatabase.OPEN_READONLY, null, null).use { db ->
                db.rawQuery("SELECT content FROM recovered_entries", emptyArray<String>()).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Recovered working journal", it.getString(0))
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun checksum(file: File): String =
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val size = input.read(buffer)
                if (size == -1) break
                digest.update(buffer, 0, size)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
}
