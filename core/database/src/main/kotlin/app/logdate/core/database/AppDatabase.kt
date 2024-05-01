package app.logdate.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.logdate.core.database.migrations.MIGRATION_1_2
import app.logdate.core.database.model.ImageNoteEntity
import app.logdate.core.database.model.JournalEntity
import app.logdate.core.database.model.JournalNoteCrossRef
import app.logdate.core.database.model.TextNoteEntity
import app.logdate.core.database.util.InstantConverter
import java.io.File

/**
 * The main on-device database for the LogDate app.
 */
@Database(
    entities = [
        TextNoteEntity::class,
        ImageNoteEntity::class,
        JournalEntity::class,
        JournalNoteCrossRef::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class AppDatabase : RoomDatabase(), LogdateDatabase, BackupableDatabase {

    companion object {
        internal const val DB_NAME = "logdate"

        /**
         * Builds the database instance.
         *
         * This method automatically applies the necessary migrations to the database.
         */
        fun buildDatabase(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DB_NAME,
        )
            .addMigrations(MIGRATION_1_2)
            .build()

    }

    override fun exportBackup(context: Context): File {
        this.close()
        val dbFileName = "$DB_NAME.db"
        context.getDatabasePath(DB_NAME)
            .copyTo(File(context.filesDir, dbFileName), overwrite = true)
        return File(context.filesDir, dbFileName)
    }

    override fun restoreFromFile(context: Context, file: File) {
        TODO("Not yet implemented")
    }
}
