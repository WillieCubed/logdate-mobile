package app.logdate.feature.editor.ui.editor

import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.navigation.EditorLaunchMode
import kotlin.uuid.Uuid

/**
 * Tells the platform what a saved entry captured and where it went, so the platform can rank its
 * own shortcuts. On Android this feeds the launcher's shortcut predictions and the share sheet's
 * journal order; elsewhere it does nothing.
 */
fun interface CaptureUsageReporter {
    fun entrySaved(
        modes: Set<EditorLaunchMode>,
        journalIds: List<Uuid>,
    )

    companion object {
        val None = CaptureUsageReporter { _, _ -> }
    }
}

/** The kinds of capture a saved entry's notes represent. */
fun captureModesOf(notes: List<JournalNote>): Set<EditorLaunchMode> =
    notes.mapTo(mutableSetOf()) { note ->
        when (note) {
            is JournalNote.Text, is JournalNote.Handwriting -> EditorLaunchMode.Text
            is JournalNote.Image -> EditorLaunchMode.Camera
            is JournalNote.Video -> EditorLaunchMode.Video
            is JournalNote.Audio -> EditorLaunchMode.Voice
        }
    }
