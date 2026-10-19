package app.logdate.client.e2e

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.database.encryption.DatabasePassphraseProvider
import app.logdate.client.database.encryption.PassphraseBackupStore
import app.logdate.client.device.storage.SecureStorage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase

@RunWith(AndroidJUnit4::class)
class InterruptedDatabaseKeyRecoveryTest {
    @Test
    fun retainedJournalOpensWithItsOnlyKeyInAnInterruptedWrite() = verifyRecovery(conflictingKeys = false)

    @Test
    fun pendingKeyOpensTheJournalAfterBothOtherCandidatesAreRejected() = verifyRecovery(conflictingKeys = true)

    private fun verifyRecovery(conflictingKeys: Boolean): Unit =
        runBlocking {
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Emulator required" }
            val application = ApplicationProvider.getApplicationContext<Context>()
            val folder = File(application.cacheDir, "interrupted-key-${Uuid.random()}").apply { mkdirs() }
            val context =
                object : ContextWrapper(application) {
                    override fun getApplicationContext(): Context = this

                    override fun getFilesDir(): File = folder

                    override fun getNoBackupFilesDir(): File = folder.resolve("no_backup").apply { mkdirs() }

                    override fun getDatabasePath(name: String): File = File(folder, name)
                }
            val storage = mockk<SecureStorage>(relaxed = true)
            coEvery { storage.getString(any()) } returns null
            System.loadLibrary("sqlcipher")
            val file = folder.resolve("retained-journal")
            val originalKey = ByteArray(32) { 11 }
            val store = PassphraseBackupStore(context)
            try {
                CipherDatabase
                    .openDatabase(
                        file.absolutePath,
                        originalKey,
                        null,
                        CipherDatabase.OPEN_READWRITE or CipherDatabase.CREATE_IF_NECESSARY,
                        null,
                        null,
                    ).use { database ->
                        assertEquals(file.absolutePath, database.path)
                        database.execSQL("CREATE TABLE retained_entries (content TEXT NOT NULL)")
                        database.execSQL("INSERT INTO retained_entries VALUES ('Journal from before interruption')")
                    }
                store.write(originalKey)
                val backup = folder.resolve("db_recovery/passphrase_backup")
                val key = requireNotNull(PassphraseBackupStore(context).read())
                val pending = folder.resolve("db_recovery/passphrase_backup.part")
                assertTrue(backup.renameTo(pending))
                if (conflictingKeys) {
                    backup.writeText(Base64.encode(ByteArray(32) { 3 }))
                    coEvery { storage.getString("db_encryption_key") } returns Base64.encode(ByteArray(32) { 4 })
                }
                val provider = DatabasePassphraseProvider(storage, store) { file.exists() }
                val candidates = provider.getPassphraseCandidates()
                assertEquals(if (conflictingKeys) 3 else 1, candidates.size)
                var opened = false
                var attempts = 0
                for (candidate in candidates) {
                    attempts++
                    val database =
                        try {
                            CipherDatabase.openDatabase(file.absolutePath, candidate, null, CipherDatabase.OPEN_READONLY, null, null)
                        } catch (_: android.database.sqlite.SQLiteException) {
                            continue
                        }
                    database.use {
                        assertEquals(file.absolutePath, database.path)
                        it.rawQuery("SELECT content FROM retained_entries", emptyArray<String>()).use { cursor ->
                            assertTrue(cursor.moveToFirst())
                            assertEquals("Journal from before interruption", cursor.getString(0))
                        }
                    }
                    provider.confirmOpenedDatabasePassphrase(candidate)
                    opened = true
                    break
                }
                assertTrue(opened, "No retained key opened the encrypted journal")
                assertEquals(if (conflictingKeys) 3 else 1, attempts)
                assertContentEquals(key, store.read())
                assertFalse(pending.exists())
            } finally {
                folder.deleteRecursively()
            }
        }
}
