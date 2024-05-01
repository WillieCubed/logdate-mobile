package app.logdate.core.data.notes

import app.logdate.core.database.dao.ImageNoteDao
import app.logdate.core.database.dao.TextNoteDao
import javax.inject.Inject

class LocalNoteDataSource @Inject constructor(
    private val notesDao: TextNoteDao,
    private val imagesDao: ImageNoteDao,
)