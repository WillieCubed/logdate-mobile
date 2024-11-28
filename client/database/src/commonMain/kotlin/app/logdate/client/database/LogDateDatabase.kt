package app.logdate.client.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.logdate.client.database.converters.TimestampConverter
import app.logdate.client.database.converters.UuidConverter
import app.logdate.client.database.dao.rewind.RewindEntity
import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.JournalNoteCrossRef
import app.logdate.client.database.entities.LocationLogEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.UserDeviceEntity
import app.logdate.client.database.entities.media.MediaImageEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * The main on-device database for a LogDate client.
 *
 * This database is responsible for storing all data that is not ephemeral, such as projects and
 * experiments. It is built on top of Room, a SQLite database library, and is designed to be used
 * with Kotlin Multiplatform on JVM, Android, and native iOS build targets.
 *
 * @see [getRoomDatabase]
 */
@Database(
    entities = [
        TextNoteEntity::class,
        ImageNoteEntity::class,
        JournalEntity::class,
        JournalNoteCrossRef::class,
        LocationLogEntity::class,
        UserDeviceEntity::class,
        MediaImageEntity::class,
//        JournalContentEntityLink::class,
        RewindEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
    ],
)
@TypeConverters(
    TimestampConverter::class,
    UuidConverter::class,
)
@ConstructedBy(LogDateDatabaseConstructor::class)
abstract class LogDateDatabase : RoomDatabase() {
}

/**
 * The name of the [LogDateDatabase] file.
 */
internal const val DATABASE_NAME = "logdate"

/**
 * Creates a new [LogDateDatabase].
 *
 * This is the main function to use when a client needs a [LogDateDatabase] and must only be
 * called once per application lifecycle. This depends on a [RoomDatabase.Builder] to be passed in,
 * which can be found in the platform-specific source set. This builder is responsible for
 * applying all necessary configurations to the database, including database migrations.
 *
 * This creates a SQLite database that uses the bundled SQLite driver and runs all queries on the
 * IO dispatcher.
 *
 * @param builder The [RoomDatabase.Builder] to use to create the database.
 * @param driver The SQLite driver to use for the database. Defaults to [BundledSQLiteDriver].
 * @param dispatcher The dispatcher to use for all database queries. Defaults to [Dispatchers.IO].
 * @param destroyTablesOnDowngrade Whether to destructively recreate database tables if the
 * database version is downgraded. True by default.
 *
 * @return A new [LogDateDatabase] instance.
 */
fun getRoomDatabase(
    builder: RoomDatabase.Builder<LogDateDatabase>,
    driver: SQLiteDriver = BundledSQLiteDriver(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    destroyTablesOnUpgrade: Boolean = false,
    destroyTablesOnDowngrade: Boolean = true,
): LogDateDatabase = builder
    .addMigrations()
    .fallbackToDestructiveMigration(destroyTablesOnUpgrade)
    .fallbackToDestructiveMigrationOnDowngrade(destroyTablesOnDowngrade)
    .setDriver(driver)
    .setQueryCoroutineContext(dispatcher)
    .build()