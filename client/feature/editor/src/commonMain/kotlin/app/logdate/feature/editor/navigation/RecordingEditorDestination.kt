package app.logdate.feature.editor.navigation

import androidx.navigation3.runtime.NavKey

/** Process-local presentation only. The draft and its audio remain the durable recording owner. */
object RecordingEditorDestination {
    private var owner: Pair<String, EntryEditorRoute>? = null

    fun bind(
        draftId: String,
        route: EntryEditorRoute,
    ) {
        owner = draftId to route
    }

    /** Return through the existing editor visit, preserving intervening editors' draft state. */
    fun open(
        stack: MutableList<NavKey>,
        destination: EntryEditorRoute,
    ) {
        val existing = owner?.takeIf { it.first == destination.draftId }?.second
        val index = existing?.let { stack.indexOfLast { key -> key == it } } ?: -1
        if (index < 0) {
            stack.add(destination)
        } else {
            if (index != stack.lastIndex) stack.add(stack.removeAt(index))
        }
    }
}
