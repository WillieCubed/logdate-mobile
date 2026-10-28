package app.logdate.feature.editor.navigation

import androidx.navigation3.runtime.NavKey

/** A saved visit must not erase accepted work in another editor on the same back stack. */
fun MutableList<NavKey>.completeEditorVisit(
    saved: EntryEditorRoute,
    destination: NavKey,
) {
    val index = indexOfLast { it == saved }
    if (index < 0) return
    removeAt(index)
    if (none { it is EntryEditorRoute }) {
        clear()
        add(destination)
    }
}
