@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.runtime.Composable

/** The capture exploration now uses the production editor's block list and focused editors. */
@Composable
fun StoriesEntryPrototype(
    initialPage: HandwritingPage? = null,
    initialText: String = "",
    initiallyFocused: Boolean = false,
    onExit: () -> Unit = {},
) {
    EntryStartPrototype(
        initialPage = initialPage,
        initialText = initialText,
        initiallyFocused = initiallyFocused,
        onExit = onExit,
        focusAnyBlock = true,
    )
}
