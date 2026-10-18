package app.logdate.client.e2e

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.database.databaseModule
import app.logdate.client.database.encryption.DatabasePassphraseProvider
import app.logdate.client.database.migratePlaintextDatabaseIfNeeded
import app.logdate.client.device.storage.SecureStorage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase

@RunWith(AndroidJUnit4::class)
class LegacyPlaintextMigrationTest {
    @Test
    fun plaintextJournalUpgradesThroughTheProductionKeyProvider(): Unit =
        runBlocking {
            val application = ApplicationProvider.getApplicationContext<Context>()
            val folder =
                File(application.cacheDir, "plaintext-upgrade-${Uuid.random()}")
                    .apply { mkdirs() }
            val context =
                object : ContextWrapper(application) {
                    override fun getApplicationContext(): Context = this

                    override fun getFilesDir(): File = folder

                    override fun getDatabasePath(name: String): File = File(folder, name)
                }
            val storage = mockk<SecureStorage>(relaxed = true)
            coEvery { storage.getString(any()) } returns null
            val graph =
                koinApplication {
                    androidContext(context)
                    modules(databaseModule, module { single<SecureStorage> { storage } })
                }
            try {
                val file = context.getDatabasePath("logdate")
                SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                    db.execSQL("CREATE TABLE legacy_entries (content TEXT NOT NULL)")
                    db.execSQL("INSERT INTO legacy_entries VALUES ('Retained legacy journal')")
                    db.version = 17
                }
                val key =
                    graph.koin
                        .get<DatabasePassphraseProvider>()
                        .getPassphraseCandidates()
                        .single()
                assertEquals(32, key.size)
                migratePlaintextDatabaseIfNeeded(context, key)
                assertFalse(file.inputStream().use { it.readNBytes(16) }.contentEquals("SQLite format 3\u0000".encodeToByteArray()))
                CipherDatabase
                    .openDatabase(file.absolutePath, key, null, CipherDatabase.OPEN_READONLY, null, null)
                    .use { db ->
                        assertEquals(17, db.version)
                        db.rawQuery("SELECT content FROM legacy_entries", emptyArray<String>()).use { cursor ->
                            assertTrue(cursor.moveToFirst())
                            assertEquals("Retained legacy journal", cursor.getString(0))
                        }
                    }
                assertTrue(folder.listFiles().orEmpty().any { it.name.startsWith("logdate.plaintext-backup-") })
            } finally {
                graph.close()
                folder.deleteRecursively()
            }
        }

    @Test
    fun mismatchedCandidateDoesNotReplaceEncryptedJournalWithOlderPlaintextBackup() {
        System.loadLibrary("sqlcipher")
        val application = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(application.cacheDir, "mismatched-key-${Uuid.random()}").apply { mkdirs() }
        val context =
            object : ContextWrapper(application) {
                override fun getApplicationContext(): Context = this

                override fun getDatabasePath(name: String): File = File(folder, name)
            }
        val encrypted = context.getDatabasePath("logdate")
        val correctKey = ByteArray(32) { 7 }
        val mismatchedKey = ByteArray(32) { 8 }
        val stalePlaintext = File(folder, "logdate.plaintext-backup-1")
        try {
            CipherDatabase
                .openDatabase(
                    encrypted.absolutePath,
                    correctKey,
                    null,
                    CipherDatabase.OPEN_READWRITE or CipherDatabase.CREATE_IF_NECESSARY,
                    null,
                    null,
                ).use { database ->
                    database.execSQL("CREATE TABLE retained_entries (content TEXT NOT NULL)")
                    database.execSQL("INSERT INTO retained_entries VALUES ('new encrypted journal')")
                }
            SQLiteDatabase.openOrCreateDatabase(stalePlaintext, null).use { database ->
                database.execSQL("CREATE TABLE retained_entries (content TEXT NOT NULL)")
                database.execSQL("INSERT INTO retained_entries VALUES ('older plaintext journal')")
            }
            val encryptedBytes = encrypted.readBytes()
            CipherDatabase
                .openDatabase(
                    encrypted.absolutePath,
                    correctKey,
                    null,
                    CipherDatabase.OPEN_READONLY,
                    null,
                    null,
                ).close()

            migratePlaintextDatabaseIfNeeded(context, mismatchedKey)

            assertContentEquals(encryptedBytes, encrypted.readBytes())
            assertTrue(stalePlaintext.exists())
            CipherDatabase
                .openDatabase(
                    encrypted.absolutePath,
                    correctKey,
                    null,
                    CipherDatabase.OPEN_READONLY,
                    null,
                    null,
                ).use { database ->
                    database.rawQuery("SELECT content FROM retained_entries", emptyArray<String>()).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("new encrypted journal", cursor.getString(0))
                    }
                }
        } finally {
            folder.deleteRecursively()
        }
    }
}
