package app.logdate.client.data.notes

import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.repository.journals.JournalNote

fun TextNoteEntity.toModel() = JournalNote.Text(
    uid = uid.toString(),
    content = content,
    creationTimestamp = created,
    lastUpdated = lastUpdated,
)

fun JournalNote.Text.toEntity() = TextNoteEntity(
    uid = if (uid.isBlank()) 0 else uid.toInt(),
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
    uid = if (uid.isBlank()) 0 else uid.toInt(),
    contentUri = mediaRef,
    created = creationTimestamp,
    lastUpdated = lastUpdated,
)