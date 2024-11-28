package app.logdate.client.database

import androidx.room.Room
import androidx.room.RoomDatabase
import java.nio.file.Path
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

/**
 * The location of the [HaystackDatabase] file.
 *
 * This is platform-specific and is determined by the operating system. Generally, it is located in
 * the user's home directory.
 */
private val DATABASE_PATH: Path
    get() = when {
        System.getProperty("os.name").contains("Windows", ignoreCase = true) -> {
            Path(System.getenv("APPDATA"), "Haystack", DATABASE_NAME)
        }

        System.getProperty("os.name").contains("Mac", ignoreCase = true) -> {
            Path(
                System.getProperty("user.home"),
                "Library",
                "Application Support",
                "Haystack",
                DATABASE_NAME
            )
        }

        else -> {
            Path(
                System.getProperty("user.home"),
                ".local",
                "share",
                "haystack",
                DATABASE_NAME
            )
        }
    }