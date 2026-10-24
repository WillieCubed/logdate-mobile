package app.logdate.client.e2e

import android.content.Context
import android.os.Build
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.getDatabaseBuilder
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.database.openRetainedDatabaseCopy
import app.logdate.client.device.restore.NativeIncomingJournal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class RetainedDatabaseCopyTest {
    @Test
    fun late_key_opens_original_wal_without_replacing_working_content() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            check((Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") && context.packageName.endsWith(".debug"))
            val root = Files.createTempDirectory(context.cacheDir.toPath(), "retained-copy-test-").toFile()
            val oldKey = ByteArray(32) { (it + 1).toByte() }
            val workingKey = ByteArray(32) { (it + 51).toByte() }
            val oldFile = File(root, "source/logdate").also { it.parentFile.mkdirs() }
            val workingFile = File(root, "working/logdate").also { it.parentFile.mkdirs() }
            val oldDb =
                getRoomDatabase(
                    getDatabaseBuilder(context, oldKey.copyOf(), oldFile).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING),
                    driver = null,
                )
            val workingDb = getRoomDatabase(getDatabaseBuilder(context, workingKey.copyOf(), workingFile), driver = null)
            try {
                oldDb.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(TRUNCATE)")
                    .close()
                val original = note("original retained entry")
                oldDb.textNoteDao().addNote(original)
                val persistedOriginal = oldDb.textNoteDao().getAllNotes().first()
                val working = note("new accepted working entry")
                workingDb.textNoteDao().addNote(working)
                val source =
                    listOf("logdate", "logdate-wal", "logdate-shm", "logdate-journal")
                        .associateWith { File(oldFile.parentFile, it) }
                        .filterValues(File::exists)
                assertTrue(source.getValue("logdate-wal").length() > 0)
                val before = source.mapValues { digest(it.value) }
                val incoming = NativeIncomingJournal(File(root, "incoming"))
                val generationA = incoming.begin(Uuid.random().toString())
                kotlin.test.assertFailsWith<java.io.IOException> {
                    incoming.capture(generationA, "database/logdate", 999, "old".byteInputStream())
                }
                val generationB = incoming.begin(Uuid.random().toString())
                source.forEach { (name, file) ->
                    file.inputStream().use { input -> incoming.capture(generationB, "database/$name", file.length(), input) }
                }
                val hashB = incoming.finish(generationB)
                val hashA = incoming.finish(generationA)
                assertNull(incoming.coherentVerified(generationA, hashA))
                assertEquals(
                    "old",
                    incoming
                        .verified(generationA, hashA)
                        .single()
                        .file
                        .readText(),
                )
                val sourceB =
                    assertNotNull(incoming.coherentVerified(generationB, hashB))
                        .mapKeys { (path, _) -> path.removePrefix("database/") }
                        .mapValues { (_, payload) -> payload.file }
                val scratch = File(root, "scratch")
                assertNull(openRetainedDatabaseCopy(context, source, listOf(workingKey), scratch))
                val copy = assertNotNull(openRetainedDatabaseCopy(context, sourceB, listOf(workingKey, oldKey), scratch))
                copy.use {
                    assertEquals(
                        persistedOriginal,
                        it.database
                            .textNoteDao()
                            .getAllNotes()
                            .first(),
                    )
                    assertEquals(
                        listOf(working.uid to working.content),
                        workingDb.textNoteDao().getAllNotes().first().map { note ->
                            note.uid to
                                note.content
                        },
                    )
                    assertEquals(before, source.mapValues { (_, file) -> digest(file) })
                }
                assertEquals(
                    listOf(working.uid to working.content),
                    workingDb.textNoteDao().getAllNotes().first().map { note ->
                        note.uid to
                            note.content
                    },
                )
            } finally {
                oldDb.close()
                workingDb.close()
                root.deleteRecursively()
                oldKey.fill(0)
                workingKey.fill(0)
            }
        }

    private fun note(content: String): TextNoteEntity {
        val now = Clock.System.now()
        return TextNoteEntity(uid = Uuid.random(), content = content, created = now, lastUpdated = now)
    }

    private fun digest(file: File): String =
        file.inputStream().use { input ->
            val hash = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
            hash.digest().joinToString("") { "%02x".format(it) }
        }
}
