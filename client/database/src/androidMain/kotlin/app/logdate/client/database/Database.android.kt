package app.logdate.client.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Creates a database builder for the LogDate database.
 *
 * This creates the database using the system-provided database path.
 *
 * @param context The context to use for the database.
 */
fun getDatabaseBuilder(context: Context, passphrase: ByteArray? = null): RoomDatabase.Builder<LogDateDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(DATABASE_NAME)
    val builder = Room.databaseBuilder<LogDateDatabase>(
        context = appContext,
        name = dbFile.absolutePath,
    )
    if (passphrase != null) {
        SQLiteDatabase.loadLibs(appContext)
        builder.openHelperFactory(SupportFactory(passphrase))
    }
    return builder
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
