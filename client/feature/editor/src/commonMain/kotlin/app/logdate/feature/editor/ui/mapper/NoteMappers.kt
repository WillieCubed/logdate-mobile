package app.logdate.feature.editor.ui.mapper

import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import kotlinx.datetime.Clock

/**
 * Maps a JournalNote domain model to an EntryBlockData UI model.
 * This mapper converts notes from the domain layer to the UI representation
 * needed for the editor.
 */
fun JournalNote.toDomainBlock(): EntryBlockUiState {
    return when (this) {
        is JournalNote.Text -> TextBlockUiState(
            id = uid,
            timestamp = creationTimestamp,
            location = null, // We don't have location data from notes
            content = content
        )

        is JournalNote.Image -> ImageBlockUiState(
            id = uid,
            timestamp = creationTimestamp,
            location = null,
            uri = mediaRef
        )

        is JournalNote.Video -> VideoBlockUiState(
            id = uid,
            timestamp = creationTimestamp,
            location = null,
            uri = mediaRef
        )

        is JournalNote.Audio -> AudioBlockUiState(
            id = uid,
            timestamp = creationTimestamp,
            location = null,
            uri = mediaRef
        )

        else -> TextBlockUiState(
            id = uid,
            timestamp = creationTimestamp,
            location = null,
            content = ""
        )
    }
}

/**
 * Maps an EntryBlockData UI model to a JournalNote domain model.
 * This mapper converts UI blocks to domain notes for saving.
 */
fun EntryBlockUiState.toJournalNote(): JournalNote? {
    val now = Clock.System.now()
    
    return when (this) {
        is TextBlockUiState -> {
            if (!hasContent()) return null
            JournalNote.Text(
                uid = id,
                creationTimestamp = timestamp,
                lastUpdated = now,
                content = content
            )
        }
        
        is ImageBlockUiState -> {
            if (!hasContent()) return null
            JournalNote.Image(
                uid = id,
                creationTimestamp = timestamp,
                lastUpdated = now,
                mediaRef = uri ?: return null
            )
        }
        
        is CameraBlockUiState -> {
            if (!hasContent()) return null
            JournalNote.Image(
                uid = id,
                creationTimestamp = timestamp,
                lastUpdated = now,
                mediaRef = uri ?: return null
            )
        }
        
        is VideoBlockUiState -> {
            if (!hasContent()) return null
            JournalNote.Video(
                uid = id,
                creationTimestamp = timestamp,
                lastUpdated = now,
                mediaRef = uri ?: return null
            )
        }
        
        is AudioBlockUiState -> {
            if (!hasContent()) return null
            JournalNote.Audio(
                uid = id,
                creationTimestamp = timestamp,
                lastUpdated = now,
                mediaRef = uri ?: return null
            )
        }
        
        else -> null
    }
}