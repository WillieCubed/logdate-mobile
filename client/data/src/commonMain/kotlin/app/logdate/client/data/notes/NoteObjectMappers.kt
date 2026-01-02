package app.logdate.client.data.notes

import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.VideoNoteEntity
import app.logdate.client.database.entities.VoiceNoteEntity
import app.logdate.client.repository.journals.JournalNote

fun TextNoteEntity.toModel() = JournalNote.Text(
    uid = uid,
    content = content,
    creationTimestamp = created,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
)

fun JournalNote.Text.toEntity() = TextNoteEntity(
    uid = uid,
    content = content,
    created = creationTimestamp,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
)

fun ImageNoteEntity.toModel() = JournalNote.Image(
    uid = uid,
    mediaRef = contentUri,
    creationTimestamp = created,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
)

fun JournalNote.Image.toEntity() = ImageNoteEntity(
    uid = uid,
    contentUri = mediaRef,
    created = creationTimestamp,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
)

fun VideoNoteEntity.toModel() = JournalNote.Video(
    uid = uid,
    mediaRef = contentUri,
    creationTimestamp = created,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
)

fun JournalNote.Video.toEntity() = VideoNoteEntity(
    uid = uid,
    contentUri = mediaRef,
    created = creationTimestamp,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
)

// TODO: Rename VoiceNoteEntity to AudioNoteEntity to better represent general audio content
//       rather than specifically voice recordings
fun VoiceNoteEntity.toModel(): JournalNote.Audio {
    val result = JournalNote.Audio(
        uid = uid,
        mediaRef = contentUri,
        creationTimestamp = created,
        lastUpdated = lastUpdated,
        syncVersion = syncVersion,
    )
    
    // Add debug logging for audio note conversion
    io.github.aakira.napier.Napier.d(
        tag = "NoteObjectMappers",
        message = "CONVERTING AUDIO NOTE: Entity with UID $uid and URI $contentUri created at $created converted to model"
    )
    
    return result
}

fun JournalNote.Audio.toEntity() = VoiceNoteEntity(
    uid = uid,
    contentUri = mediaRef,
    created = creationTimestamp,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
    // Duration might be null since it's not included in JournalNote.Audio
    durationMs = null
)
