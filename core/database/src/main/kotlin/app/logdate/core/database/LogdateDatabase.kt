package app.logdate.core.database

import app.logdate.core.database.dao.ImageNoteDao
import app.logdate.core.database.dao.JournalDao
import app.logdate.core.database.dao.JournalNotesDao
import app.logdate.core.database.dao.TextNoteDao

/**
 * A database that contains all the tables used by the LogDate app.
 */
interface LogdateDatabase {
    fun textNoteDao(): TextNoteDao
    fun imageNoteDao(): ImageNoteDao
    fun journalsDao(): JournalDao
    fun journalNotesDao(): JournalNotesDao
}