package app.logdate.client.database

import androidx.room.Room
import androidx.room.RoomDatabase
import app.logdate.client.device.storage.SecureStorage
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Creates a database builder for the Haystack database.
 *
 * This creates the database at using the platform-specific [DATABASE_PATH].
 */
fun getDatabaseBuilder(): RoomDatabase.Builder<LogDateDatabase> {
    val dbFile = DATABASE_PATH
    return Room.databaseBuilder<LogDateDatabase>(
        name = dbFile.absolutePathString(),
    )
}

fun protectDatabaseFile() {
    val dbFile = DATABASE_PATH
    if (!Files.exists(dbFile)) {
        return
    }
    runCatching {
        val permissions = PosixFilePermissions.fromString("rw-------")
        Files.setPosixFilePermissions(dbFile, permissions)
    }.onFailure {
        // Non-POSIX filesystems (e.g. Windows) will throw.
    }
}

fun prepareEncryptedDatabase(secureStorage: SecureStorage) {
    val dbFile = DATABASE_PATH
    val encryptedFile = encryptedDatabasePath()

    if (Files.exists(dbFile) || !Files.exists(encryptedFile)) {
        return
    }

    runCatching {
        val encryptedBytes = Files.readAllBytes(encryptedFile)
        val decrypted = runBlocking { secureStorage.decrypt(encryptedBytes) }
        if (decrypted != null) {
            Files.createDirectories(dbFile.parent)
            Files.write(dbFile, decrypted)
        }
    }
}

fun scheduleDatabaseEncryption(secureStorage: SecureStorage) {
    Runtime.getRuntime().addShutdownHook(
        Thread {
            val dbFile = DATABASE_PATH
            val encryptedFile = encryptedDatabasePath()
            if (!Files.exists(dbFile)) {
                return@Thread
            }

            runCatching {
                val plaintext = Files.readAllBytes(dbFile)
                val encrypted = runBlocking { secureStorage.encrypt(plaintext) }
                Files.createDirectories(encryptedFile.parent)
                Files.write(encryptedFile, encrypted)
                Files.deleteIfExists(dbFile)
            }
        },
    )
}

/**
 * The location of the [HaystackDatabase] file.
 *
 * This is platform-specific and is determined by the operating system. Generally, it is located in
 * the user's home directory.
 */
private val DATABASE_PATH: Path
    get() =
        when {
            System.getProperty("os.name").contains("Windows", ignoreCase = true) -> {
                Path(System.getenv("APPDATA"), "Haystack", DATABASE_NAME)
            }

            System.getProperty("os.name").contains("Mac", ignoreCase = true) -> {
                Path(
                    System.getProperty("user.home"),
                    "Library",
                    "Application Support",
                    "Haystack",
                    DATABASE_NAME,
                )
            }

            else -> {
                Path(
                    System.getProperty("user.home"),
                    ".local",
                    "share",
                    "haystack",
                    DATABASE_NAME,
                )
            }
        }

private fun encryptedDatabasePath(): Path = DATABASE_PATH.resolveSibling("$DATABASE_NAME.enc")
