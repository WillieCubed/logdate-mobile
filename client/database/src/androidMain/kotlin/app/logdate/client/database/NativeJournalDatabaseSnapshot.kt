package app.logdate.client.database

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
): T {
    require(key.size == 32)
    check(Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS))
    check(!target.exists() && Files.isDirectory(checkNotNull(target.parentFile).toPath(), LinkOption.NOFOLLOW_LINKS))
    System.loadLibrary("sqlcipher")
    val captured =
        SQLiteDatabase
            .openDatabase(
                source.absolutePath,
                key,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
                null,
                null,
            ).use { database ->
                database.execSQL("ATTACH DATABASE ? AS native_journal KEY ?", arrayOf<Any>(target.absolutePath, key))
                try {
                    database.beginTransaction()
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
    return captured
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
