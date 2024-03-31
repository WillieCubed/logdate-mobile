package app.logdate.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.Relation

@Entity(
    tableName = "journal_notes",
    primaryKeys = ["id", "uid"],
)
data class JournalNoteCrossRef(
    @ColumnInfo("id", index = true)
    val journalId: Int,
    @ColumnInfo("uid", index = true)
    val noteId: Int,
)

/**
 * A data container for a note and its associated journals.
 */
data class NoteJournals(
    @ColumnInfo(name = "uid")
    val noteId: Int,
    @Embedded
    val journal: JournalEntity,
)

/**
 * A data container for a journal and its associated notes.
 */
data class JournalWithNotes(
    @Embedded
    val journal: JournalEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "uid",
        associateBy = Junction(JournalNoteCrossRef::class)
    )
    val textNotes: List<TextNoteEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "uid",
        associateBy = Junction(JournalNoteCrossRef::class)
    )
    val imageNotes: List<ImageNoteEntity>,
//    val audioNotes: List<AudioNoteEntity>,
//    val videoNotes: List<VideoNoteEntity>,
)
