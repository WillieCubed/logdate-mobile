package app.logdate.client.database

import android.database.sqlite.SQLiteDatabaseLockedException
import android.os.SystemClock
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** The callback runs synchronously under this SQL transaction; it must acquire DataStore second. */
fun <T> captureNativeJournalDatabaseSnapshot(
    source: File,
    key: ByteArray,
    target: File,
    capturePreferences: ((NativeJournalSnapshotScope) -> Unit) -> T,
): T =
    withNativeJournalDatabaseFence(source) {
        require(key.size == 32)
        check(Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS))
        check(!target.exists() && Files.isDirectory(checkNotNull(target.parentFile).toPath(), LinkOption.NOFOLLOW_LINKS))
        System.loadLibrary("sqlcipher")
        val captured =
            openSnapshotSourceWhenAvailable(source, key).use { database ->
                database.execSQL("ATTACH DATABASE ? AS native_journal KEY ?", arrayOf<Any>(target.absolutePath, key))
                try {
                    database.beginTransactionNonExclusive()
                    try {
                        requireNativeJournalDatabaseIntegrity(database)
                        database.rawQuery("SELECT sqlcipher_export('native_journal')", emptyArray<String>()).use { check(it.moveToFirst()) }
                        database.execSQL("PRAGMA native_journal.user_version = $DATABASE_VERSION")
                        val result = capturePreferences { scope -> writeNativeJournalSnapshotScope(database, scope) }
                        database.setTransactionSuccessful()
                        result
                    } finally {
                        database.endTransaction()
                    }
                } finally {
                    database.execSQL("DETACH DATABASE native_journal")
                }
            }
        verifyNativeJournalDatabaseSnapshot(target, key)
        captured
    }

private fun openSnapshotSourceWhenAvailable(
    source: File,
    key: ByteArray,
): SQLiteDatabase {
    val deadline = SystemClock.elapsedRealtime() + 15_000L
    while (true) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Native snapshot canceled")
        try {
            return SQLiteDatabase.openDatabase(
                source.absolutePath,
                key,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
                null,
                null,
            )
        } catch (locked: SQLiteDatabaseLockedException) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) throw locked
            try {
                Thread.sleep(minOf(200L, remaining))
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw interrupted
            }
        }
    }
}

fun verifyNativeJournalDatabaseSnapshot(
    file: File,
    key: ByteArray,
): NativeJournalSnapshotScope {
    require(key.size == 32)
    check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
    System.loadLibrary("sqlcipher")
    return SQLiteDatabase.openDatabase(file.absolutePath, key, null, SQLiteDatabase.OPEN_READONLY, null, null).use {
        requireNativeJournalDatabaseIntegrity(it)
        readNativeJournalSnapshotScope(it)
    }
}

/** Reads only live media references from the already verified, immutable transfer database. */
fun readNativeJournalSnapshotMediaReferences(
    file: File,
    key: ByteArray,
): Set<String> {
    require(key.size == 32)
    check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
    System.loadLibrary("sqlcipher")
    return SQLiteDatabase.openDatabase(file.absolutePath, key, null, SQLiteDatabase.OPEN_READONLY, null, null).use { database ->
        requireNativeJournalDatabaseIntegrity(database)
        collectNativeJournalMediaReferences { sql ->
            database.rawQuery(sql, emptyArray<String>()).use { rows ->
                buildList { while (rows.moveToNext()) add(rows.getString(0)) }
            }
        }
    }
}

internal fun collectNativeJournalMediaReferences(query: (String) -> List<String?>): Set<String> =
    buildSet {
        listOf(
            "SELECT contentUri FROM image_notes WHERE deletedAt IS NULL",
            "SELECT contentUri FROM video_notes WHERE deletedAt IS NULL",
            "SELECT contentUri FROM audio_notes WHERE deletedAt IS NULL",
            "SELECT coverImageUri FROM journals WHERE deletedAt IS NULL",
            "SELECT cover_image_uri FROM events WHERE deleted_at IS NULL",
            "SELECT uri FROM rewind_image_content",
            "SELECT uri FROM rewind_video_content",
            "SELECT photo_uri FROM people WHERE deleted_at IS NULL",
            "SELECT image_uri FROM stickers",
        ).forEach { sql -> query(sql).filterNotNull().forEach(::add) }
    }

private fun requireNativeJournalDatabaseIntegrity(database: SQLiteDatabase) {
    check(database.version == DATABASE_VERSION) { "Native journal snapshot requires the current database schema" }
    database.rawQuery("PRAGMA cipher_integrity_check", emptyArray<String>()).use { check(!it.moveToFirst()) }
    database.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
        check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext())
    }
    database.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", emptyArray<String>()).use {
        check(it.moveToFirst() && !it.getString(0).isNullOrBlank() && !it.moveToNext())
    }
}
