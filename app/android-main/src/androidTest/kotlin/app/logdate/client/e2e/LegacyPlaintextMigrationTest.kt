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
            val folder = File(application.cacheDir, "plaintext-upgrade-${Uuid.random()}").apply { mkdirs() }
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
                val key = graph.koin.get<DatabasePassphraseProvider>().getOrCreatePassphrase()
                assertEquals(32, key.size)
                migratePlaintextDatabaseIfNeeded(context, key)
                assertFalse(file.inputStream().use { it.readNBytes(16) }.contentEquals("SQLite format 3\u0000".encodeToByteArray()))
                CipherDatabase.openDatabase(file.absolutePath, key, null, CipherDatabase.OPEN_READONLY, null, null).use { db ->
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
}
