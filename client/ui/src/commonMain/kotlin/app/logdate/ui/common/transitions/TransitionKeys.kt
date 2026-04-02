package app.logdate.ui.common.transitions

import kotlin.uuid.Uuid

object TransitionKeys {
    const val FAB_TO_EDITOR_TRANSITION = "fab_to_editor"
    const val LIBRARY_MEDIA_TRANSITION = "library-media"

    fun journalContainerTransition(journalId: Uuid): String = "journal-container-$journalId"

    fun noteViewerTransition(noteId: Uuid): String = "note-viewer-$noteId"
}
