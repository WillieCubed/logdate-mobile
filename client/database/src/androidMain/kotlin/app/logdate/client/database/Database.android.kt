package app.logdate.client.database

import android.content.Context
import android.os.CancellationSignal
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import net.zetetic.database.sqlcipher.SQLiteDatabase as SQLCipherDatabase

/**
 * Creates a database builder for the LogDate database.
 *
 * This creates the database using the system-provided database path.
 *
 * @param context The context to use for the database.
 */
fun getDatabaseBuilder(
    context: Context,
    passphrase: ByteArray? = null,
): RoomDatabase.Builder<LogDateDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(DATABASE_NAME)
    val builder =
        Room.databaseBuilder<LogDateDatabase>(
            context = appContext,
            name = dbFile.absolutePath,
        )
    val hasExistingDb = passphrase != null && dbFile.exists()
    var needsLegacyHook = false

    if (passphrase != null) {
        System.loadLibrary("sqlcipher")
        if (hasExistingDb) {
            val opensWithCurrent = canOpenEncryptedDatabaseWithCurrentSettings(dbFile, passphrase)
            needsLegacyHook = !opensWithCurrent && canOpenEncryptedDatabaseWithLegacySettings(dbFile, passphrase)
            Log.i(
                DB_MIGRATION_TAG,
                "Existing encrypted DB probe: opensWithCurrent=$opensWithCurrent needsLegacyHook=$needsLegacyHook",
            )
        }

        Log.i(DB_MIGRATION_TAG, "Configuring SQLCipher open helper (existingDb=$hasExistingDb)")
        val migrationHook =
            if (needsLegacyHook) {
                Log.i(DB_MIGRATION_TAG, "Applying legacy SQLCipher compatibility hook before keying")
                createLegacySqlCipherHook()
            } else {
                null
            }
        builder.openHelperFactory(
            if (migrationHook != null) {
                SupportOpenHelperFactory(passphrase, migrationHook, false)
            } else {
                SupportOpenHelperFactory(passphrase)
            },
        )
    }

    if (needsLegacyHook) {
        builder.addCallback(
            object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Log.i(DB_MIGRATION_TAG, "Running PRAGMA cipher_migrate on existing encrypted database")
                    runCatching {
                        db.query("PRAGMA cipher_migrate").use { cursor ->
                            if (cursor.moveToFirst()) {
                                Log.i(DB_MIGRATION_TAG, "cipher_migrate result=${cursor.getInt(0)}")
                            }
                        }
                    }.onFailure { error ->
                        Log.e(DB_MIGRATION_TAG, "PRAGMA cipher_migrate failed", error)
                        throw error
                    }
                }
            },
        )
    }

    return builder
}

/**
 * One-time migration from plaintext SQLite to SQLCipher for installs that predate
 * on-device encryption.
 *
 * This is intentionally non-destructive:
 * - original files are moved to timestamped backups before replacement
 * - the encrypted output is validated before replacement
 */
fun migratePlaintextDatabaseIfNeeded(
    context: Context,
    passphrase: ByteArray,
) {
    val dbFile = context.applicationContext.getDatabasePath(DATABASE_NAME)
    if (!dbFile.exists()) {
        Log.i(DB_MIGRATION_TAG, "No existing database file found; skipping plaintext migration")
        return
    }
    System.loadLibrary("sqlcipher")

    if (!isPlaintextSqliteDatabase(dbFile)) {
        if (canOpenEncryptedDatabaseWithCurrentSettings(dbFile, passphrase)) {
            Log.i(DB_MIGRATION_TAG, "Existing database opens with current SQLCipher settings; skipping plaintext migration")
            return
        }
        if (canOpenEncryptedDatabaseWithLegacySettings(dbFile, passphrase)) {
            Log.i(DB_MIGRATION_TAG, "Existing database opens with legacy SQLCipher settings; skipping plaintext migration")
            return
        }

        val plaintextBackup = findLatestPlaintextBackup(dbFile)
        if (plaintextBackup != null && isPlaintextSqliteDatabase(plaintextBackup)) {
            val unreadableBackup = File(dbFile.parentFile, "$DATABASE_NAME.unreadable-backup-${System.currentTimeMillis()}")
            Log.w(
                DB_MIGRATION_TAG,
                "Database is neither plaintext nor openable with key. Restoring latest plaintext backup ${plaintextBackup.name} to recover safely.",
            )
            moveReplacing(dbFile, unreadableBackup)
            moveReplacing(plaintextBackup, dbFile)
            Log.i(DB_MIGRATION_TAG, "Restored plaintext backup. Unreadable DB preserved at ${unreadableBackup.absolutePath}")
        } else {
            Log.e(
                DB_MIGRATION_TAG,
                "Existing database is not plaintext and cannot be opened with SQLCipher key; no plaintext backup available for safe recovery",
            )
            return
        }
    }
    Log.w(DB_MIGRATION_TAG, "Detected plaintext SQLite DB. Starting one-time SQLCipher migration")

    val tempEncryptedFile = File(dbFile.parentFile, "$DATABASE_NAME.sqlcipher-migrating")
    val backupBase = File(dbFile.parentFile, "$DATABASE_NAME.plaintext-backup-${System.currentTimeMillis()}")
    var movedOriginal = false

    runCatching { tempEncryptedFile.delete() }.onFailure { error ->
        Log.w(DB_MIGRATION_TAG, "Failed to clear stale temp migration file: ${tempEncryptedFile.absolutePath}", error)
    }

    try {
        Log.i(DB_MIGRATION_TAG, "Exporting plaintext DB to encrypted temp file")
        exportPlaintextDatabaseToEncrypted(
            plaintextFile = dbFile,
            encryptedFile = tempEncryptedFile,
            passphrase = passphrase,
        )

        Log.i(DB_MIGRATION_TAG, "Validating encrypted temp database")
        verifyEncryptedDatabase(encryptedFile = tempEncryptedFile, passphrase = passphrase)

        Log.i(DB_MIGRATION_TAG, "Backing up original plaintext DB to ${backupBase.absolutePath}")
        moveIfExists(dbFile, backupBase)
        movedOriginal = true
        moveIfExists(File("${dbFile.absolutePath}-wal"), File("${backupBase.absolutePath}-wal"))
        moveIfExists(File("${dbFile.absolutePath}-shm"), File("${backupBase.absolutePath}-shm"))
        moveIfExists(File("${dbFile.absolutePath}-journal"), File("${backupBase.absolutePath}-journal"))

        Log.i(DB_MIGRATION_TAG, "Replacing live DB with encrypted migration output")
        moveReplacing(tempEncryptedFile, dbFile)
        Log.i(DB_MIGRATION_TAG, "Migrated plaintext DB to SQLCipher. Backup: ${backupBase.absolutePath}")
    } catch (error: Throwable) {
        Log.e(DB_MIGRATION_TAG, "Plaintext to SQLCipher migration failed", error)
        runCatching { tempEncryptedFile.delete() }
        if (movedOriginal && !dbFile.exists() && backupBase.exists()) {
            runCatching {
                moveReplacing(backupBase, dbFile)
                Log.w(DB_MIGRATION_TAG, "Restored original plaintext DB after migration failure")
            }.onFailure { rollbackError ->
                Log.e(DB_MIGRATION_TAG, "Failed to restore original DB after migration failure", rollbackError)
            }
        }
        throw error
    }
}

private fun exportPlaintextDatabaseToEncrypted(
    plaintextFile: File,
    encryptedFile: File,
    passphrase: ByteArray,
) {
    val plaintextDb =
        SQLCipherDatabase.openDatabase(
            plaintextFile.absolutePath,
            ByteArray(0),
            null,
            SQLCipherDatabase.OPEN_READWRITE or SQLCipherDatabase.CREATE_IF_NECESSARY,
            null,
            null,
        )

    try {
        plaintextDb.execSQL(
            "ATTACH DATABASE ? AS encrypted KEY ?",
            arrayOf<Any>(encryptedFile.absolutePath, passphrase),
        )
        try {
            plaintextDb.rawQuery("SELECT sqlcipher_export('encrypted')", emptyArray<String>()).use { cursor ->
                cursor.moveToFirst()
            }
            val existingVersion = plaintextDb.version
            plaintextDb.execSQL("PRAGMA encrypted.user_version = $existingVersion")
        } finally {
            plaintextDb.execSQL("DETACH DATABASE encrypted")
        }
    } finally {
        plaintextDb.close()
    }
}

private fun verifyEncryptedDatabase(
    encryptedFile: File,
    passphrase: ByteArray,
) {
    openAndPingEncryptedDatabase(encryptedFile, passphrase, hook = null)
}

private fun canOpenEncryptedDatabaseWithCurrentSettings(
    encryptedFile: File,
    passphrase: ByteArray,
): Boolean {
    if (!encryptedFile.exists()) {
        return false
    }

    return runCatching { openAndPingEncryptedDatabase(encryptedFile, passphrase, hook = null) }
        .onFailure { error ->
            Log.w(
                DB_MIGRATION_TAG,
                "Database did not open with current SQLCipher settings: ${error::class.java.simpleName}: ${error.message}",
            )
        }.isSuccess
}

private fun canOpenEncryptedDatabaseWithLegacySettings(
    encryptedFile: File,
    passphrase: ByteArray,
): Boolean {
    if (!encryptedFile.exists()) {
        return false
    }

    return runCatching {
        openAndPingEncryptedDatabase(encryptedFile, passphrase, hook = createLegacySqlCipherHook())
    }.onFailure { error ->
        Log.w(
            DB_MIGRATION_TAG,
            "Database did not open with legacy SQLCipher settings: ${error::class.java.simpleName}: ${error.message}",
        )
    }.isSuccess
}

private fun canOpenEncryptedDatabase(
    encryptedFile: File,
    passphrase: ByteArray,
): Boolean {
    if (canOpenEncryptedDatabaseWithCurrentSettings(encryptedFile, passphrase)) {
        return true
    }
    return canOpenEncryptedDatabaseWithLegacySettings(encryptedFile, passphrase)
}

private fun openAndPingEncryptedDatabase(
    encryptedFile: File,
    passphrase: ByteArray,
    hook: SQLiteDatabaseHook?,
) {
    val encryptedDb =
        SQLCipherDatabase.openDatabase(
            encryptedFile.absolutePath,
            passphrase,
            null,
            SQLCipherDatabase.OPEN_READONLY,
            null,
            hook,
        )
    try {
        encryptedDb.rawQuery("SELECT COUNT(*) FROM sqlite_schema", emptyArray<String>()).use { cursor ->
            cursor.moveToFirst()
        }
    } finally {
        encryptedDb.close()
    }
}

private fun findLatestPlaintextBackup(dbFile: File): File? {
    val directory = dbFile.parentFile ?: return null
    val prefix = "$DATABASE_NAME.plaintext-backup-"
    return directory
        .listFiles()
        ?.asSequence()
        ?.filter { file -> file.isFile && file.name.startsWith(prefix) }
        ?.maxByOrNull { file -> file.lastModified() }
}

private fun createLegacySqlCipherHook(): SQLiteDatabaseHook =
    object : SQLiteDatabaseHook {
        override fun preKey(database: SQLiteConnection) {
            val cancellationSignal = CancellationSignal()
            database.execute("PRAGMA cipher_default_page_size = 1024", emptyArray(), cancellationSignal)
            database.execute("PRAGMA cipher_default_kdf_iter = 4000", emptyArray(), cancellationSignal)
            database.execute("PRAGMA cipher_default_hmac_algorithm = HMAC_SHA1", emptyArray(), cancellationSignal)
            database.execute("PRAGMA cipher_default_kdf_algorithm = PBKDF2_HMAC_SHA1", emptyArray(), cancellationSignal)
        }

        override fun postKey(database: SQLiteConnection) {
        }
    }

private fun isPlaintextSqliteDatabase(dbFile: File): Boolean {
    if (!dbFile.isFile || dbFile.length() < SQLITE_FILE_HEADER.size) {
        return false
    }

    val header = ByteArray(SQLITE_FILE_HEADER.size)
    return runCatching {
        FileInputStream(dbFile).use { stream ->
            val bytesRead = stream.read(header)
            bytesRead == SQLITE_FILE_HEADER.size && header.contentEquals(SQLITE_FILE_HEADER)
        }
    }.onFailure { error ->
        Log.e(DB_MIGRATION_TAG, "Failed reading DB header for plaintext detection", error)
    }.getOrDefault(false)
}

private fun moveIfExists(
    source: File,
    target: File,
) {
    if (!source.exists()) {
        return
    }
    moveReplacing(source, target)
}

private fun moveReplacing(
    source: File,
    target: File,
) {
    target.parentFile?.mkdirs()
    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

fun protectDatabaseFile(context: Context) {
    val dbFile = context.applicationContext.getDatabasePath(DATABASE_NAME)
    if (!dbFile.exists()) {
        return
    }
    dbFile.setReadable(false, false)
    dbFile.setWritable(false, false)
    dbFile.setReadable(true, true)
    dbFile.setWritable(true, true)
}

private const val DB_MIGRATION_TAG = "LogDateDatabase"
private val SQLITE_FILE_HEADER =
    byteArrayOf(
        'S'.code.toByte(),
        'Q'.code.toByte(),
        'L'.code.toByte(),
        'i'.code.toByte(),
        't'.code.toByte(),
        'e'.code.toByte(),
        ' '.code.toByte(),
        'f'.code.toByte(),
        'o'.code.toByte(),
        'r'.code.toByte(),
        'm'.code.toByte(),
        'a'.code.toByte(),
        't'.code.toByte(),
        ' '.code.toByte(),
        '3'.code.toByte(),
        0.toByte(),
    )
