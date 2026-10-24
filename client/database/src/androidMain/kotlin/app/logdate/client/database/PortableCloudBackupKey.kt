package app.logdate.client.database

import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** The cloud backup callback must fail before export if the database has no portable key. */
fun requirePortableCloudBackupKey(
    database: File,
    recoveryKey: File,
) = requirePortableCloudBackupKey(database, recoveryKey, ::opensPortableCloudBackupDatabase)

internal fun requirePortableCloudBackupKey(
    database: File,
    recoveryKey: File,
    opensDatabase: (File, ByteArray) -> Boolean,
) {
    val key = readRetainedDatabasePassphrases(listOf(recoveryKey)).singleOrNull()
    if (!database.exists()) {
        check(!recoveryKey.exists() || key != null) { "Native cloud backup cannot export an unreadable orphan recovery key" }
        return
    }
    check(Files.isRegularFile(database.toPath(), LinkOption.NOFOLLOW_LINKS) && database.length() > 0) {
        "Native cloud backup cannot export an unreadable journal"
    }
    check(key != null && opensDatabase(database, key)) {
        "Native cloud backup cannot export a journal without its verified portable key"
    }
}

private fun opensPortableCloudBackupDatabase(
    database: File,
    key: ByteArray,
): Boolean =
    runCatching {
        System.loadLibrary("sqlcipher")
        val hook =
            when {
                canOpenEncryptedDatabaseWithCurrentSettings(database, key) -> null
                canOpenEncryptedDatabaseWithLegacySettings(database, key) -> createLegacySqlCipherHook()
                else -> return false
            }
        SQLiteDatabase.openDatabase(database.absolutePath, key, null, SQLiteDatabase.OPEN_READONLY, null, hook).use {
            requireRetainedDatabaseIntegrity(it)
        }
    }.isSuccess
