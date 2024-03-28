package app.logdate.core.data.notes.util

import app.logdate.core.data.notes.JournalNote
import app.logdate.core.database.model.ImageNoteEntity
import app.logdate.core.database.model.TextNoteEntity

fun TextNoteEntity.toModel() = JournalNote.Text(
    uid = uid.toString(),
    content = content,
    creationTimestamp = created,
    lastUpdated = lastUpdated,
)

fun JournalNote.Text.toEntity() = TextNoteEntity(
    uid = uid.toInt(),
    content = content,
    created = creationTimestamp,
    lastUpdated = lastUpdated,
)

fun ImageNoteEntity.toModel() = JournalNote.Image(
    uid = uid.toString(),
    mediaRef = contentUri,
    creationTimestamp = created,
    lastUpdated = lastUpdated,
)

fun JournalNote.Image.toEntity() = ImageNoteEntity(
    uid = uid.toInt(),
    contentUri = mediaRef,
    created = creationTimestamp,
    lastUpdated = lastUpdated,
)