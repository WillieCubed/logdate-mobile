package app.logdate.feature.rewind.ui.detail

import app.logdate.shared.model.ReflectionPrompt

/**
 * Whether the reflection reply sheet is open and what prompt it's targeting.
 *
 * The detail screen reads this off the view model to decide when to render the
 * `ModalBottomSheet` and to seed the text field with whatever reply already exists.
 */
sealed interface ReflectionReplySheetState {
    /** No sheet visible. */
    data object Closed : ReflectionReplySheetState

    /** A sheet is open against [prompt], optionally pre-filled with the user's [existing] reply. */
    data class Open(
        val prompt: ReflectionPrompt,
        val existing: String?,
    ) : ReflectionReplySheetState
}
