package app.logdate.feature.editor.ui

import androidx.compose.runtime.compositionLocalOf
import kotlin.uuid.Uuid

/** Capture editing selection before focus loss asks the platform text field to deselect it. */
internal class EditorInputFocusBookmarks {
    private data class Registration(
        val owner: Any,
        val capture: () -> Unit,
    )

    private val inputs = mutableMapOf<Uuid, Registration>()

    fun attach(
        id: Uuid,
        owner: Any,
        capture: () -> Unit,
    ) {
        inputs[id] = Registration(owner, capture)
    }

    fun detach(
        id: Uuid,
        owner: Any,
    ) {
        if (inputs[id]?.owner === owner) inputs.remove(id)
    }

    fun capture(id: Uuid) {
        inputs[id]?.capture?.invoke()
    }
}

internal val LocalEditorInputFocusBookmarks = compositionLocalOf<EditorInputFocusBookmarks?> { null }
