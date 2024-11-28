package app.logdate.client.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Creates a database builder for the LogDate database.
 *
 * This creates the database using the system-provided database path.
 *
 * @param context The context to use for the database.
 */
fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<LogDateDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(DATABASE_NAME)
    return Room.databaseBuilder<LogDateDatabase>(
        context = appContext,
        name = dbFile.absolutePath,
    )
}