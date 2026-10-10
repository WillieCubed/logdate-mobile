package app.logdate.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens entries in their own windows, on platforms that support more than one LogDate window
 * (Android tablets, foldables, Chromebooks, and desktop windowing).
 *
 * Provided through [LocalEditorWindows] by the host activity; null where windows are not supported,
 * so screens only offer window actions that work.
 */
interface EditorWindows {
    /** Opens a new entry in its own window, with [journalId] selected when given. */
    fun openNewEntry(journalId: String?)

    /** Opens the existing entry [entryId] in its own window, for editing beside what is on screen. */
    fun openEntry(entryId: String)
}

/** The current [EditorWindows], or null when this platform cannot open another window. */
val LocalEditorWindows = staticCompositionLocalOf<EditorWindows?> { null }
