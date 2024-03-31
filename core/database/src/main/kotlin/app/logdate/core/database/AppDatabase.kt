package app.logdate.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.logdate.core.database.dao.ImageNoteDao
import app.logdate.core.database.dao.JournalDao
import app.logdate.core.database.dao.JournalNotesDao
import app.logdate.core.database.dao.TextNoteDao
import app.logdate.core.database.migrations.MIGRATION_1_2
import app.logdate.core.database.model.ImageNoteEntity
import app.logdate.core.database.model.JournalEntity
import app.logdate.core.database.model.JournalNoteCrossRef
import app.logdate.core.database.model.TextNoteEntity
import app.logdate.core.database.util.InstantConverter

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
abstract class AppDatabase : RoomDatabase() {
    abstract fun textNoteDao(): TextNoteDao
    abstract fun imageNoteDao(): ImageNoteDao
    abstract fun journalsDao(): JournalDao
    abstract fun journalNotesDao(): JournalNotesDao

    companion object {
        /**
         * Builds the database instance.
         *
         * This method automatically applies the necessary migrations to the database.
         */
        fun buildDatabase(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "logdate",
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}