package app.logdate.client.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Creates a database builder for the Haystack database.
 *
 * This creates a database in the user's documents directory.
 */
fun getDatabaseBuilder(): RoomDatabase.Builder<LogDateDatabase> {
    val dbFilePath = documentDirectory() + "/" + DATABASE_NAME
    return Room.databaseBuilder<LogDateDatabase>(
        name = dbFilePath,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}