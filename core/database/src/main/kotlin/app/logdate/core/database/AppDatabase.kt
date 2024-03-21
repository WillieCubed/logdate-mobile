package app.logdate.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.logdate.core.database.dao.ImageNoteDao
import app.logdate.core.database.dao.TextNoteDao
import app.logdate.core.database.model.ImageNoteEntity
import app.logdate.core.database.model.TextNoteEntity
import app.logdate.core.database.util.InstantConverter

/**
 * The main on-device database for the LogDate app.
 */
@Database(
    entities = [
        TextNoteEntity::class,
        ImageNoteEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun textNoteDao(): TextNoteDao
    abstract fun imageNoteDao(): ImageNoteDao
}