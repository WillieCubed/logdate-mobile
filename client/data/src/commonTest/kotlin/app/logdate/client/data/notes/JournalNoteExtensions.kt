package app.logdate.client.data.notes

import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.repository.journals.JournalNote

/**
 * Extension functions for converting between model and entity classes in tests.
 */

/**
 * Convert a [JournalNote.Text] to a [TextNoteEntity].
 */
fun JournalNote.Text.toEntity(): TextNoteEntity {
    return TextNoteEntity(
        content = content,
        uid = uid,
        lastUpdated = lastUpdated,
        created = creationTimestamp
    )
}

/**
 * Convert a [JournalNote.Image] to an [ImageNoteEntity].
 */
fun JournalNote.Image.toEntity(): ImageNoteEntity {
    return ImageNoteEntity(
        contentUri = mediaRef,
        uid = uid,
        lastUpdated = lastUpdated,
        created = creationTimestamp
    )
}

/**
 * Convert a [TextNoteEntity] to a [JournalNote.Text].
 */
fun TextNoteEntity.toModel(): JournalNote.Text {
    return JournalNote.Text(
        uid = uid,
        content = content,
        creationTimestamp = created,
        lastUpdated = lastUpdated
    )
}

/**
 * Convert an [ImageNoteEntity] to a [JournalNote.Image].
 */
fun ImageNoteEntity.toModel(): JournalNote.Image {
    return JournalNote.Image(
        uid = uid,
        mediaRef = contentUri,
        creationTimestamp = created,
        lastUpdated = lastUpdated
    )
}