package app.logdate.client.database

import androidx.room.Room
import androidx.room.RoomDatabase
import app.logdate.client.device.storage.DesktopFileCipher
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

/** The desktop database's encryption at rest, sealed with the device key while the app is closed. */
fun desktopDatabaseVault(cipher: DesktopFileCipher): DesktopDatabaseVault = DesktopDatabaseVault(cipher, DATABASE_PATH)

/** Closes [database] and seals it when the app exits, so every committed write is encrypted. */
fun scheduleDatabaseEncryption(
    database: LogDateDatabase,
    vault: DesktopDatabaseVault,
) {
    Runtime.getRuntime().addShutdownHook(
        Thread {
            database.close()
            vault.seal()
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
