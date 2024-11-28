package app.logdate.client.database.dao.journals

import androidx.room.Dao
import androidx.room.Insert
import app.logdate.client.database.entities.journals.JournalContentEntityLink

/**
 * A DAO for modifying and accessing associations between user-generated content and journals.
 */
@Dao
interface JournalContentDao {

    @Insert
    fun addContentToJournal(link: JournalContentEntityLink)
}