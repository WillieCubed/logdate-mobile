@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.database

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSUserDomainMask

/**
 * Creates a database builder for the Haystack database.
 *
 * This creates a database in the user's documents directory.
 */
fun getDatabaseBuilder(): RoomDatabase.Builder<LogDateDatabase> {
    val dbFilePath = databaseFilePath()
    return Room.databaseBuilder<LogDateDatabase>(
        name = dbFilePath,
    )
}

fun protectDatabaseFile(path: String) {
    val attributes: Map<Any?, Any?> = mapOf(NSFileProtectionKey to NSFileProtectionComplete)
    NSFileManager.defaultManager.setAttributes(attributes, ofItemAtPath = path, error = null)
}

fun databaseFilePath(): String = documentDirectory() + "/" + DATABASE_NAME

private fun documentDirectory(): String {
    val documentDirectory =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
    return requireNotNull(documentDirectory?.path)
}
