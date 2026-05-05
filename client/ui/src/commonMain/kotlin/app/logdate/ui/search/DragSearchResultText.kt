package app.logdate.ui.search

import androidx.compose.ui.Modifier

/**
 * Marks the receiver as a drag-and-drop source carrying [text] as plain-text payload, so a
 * search result row can be dragged out into other apps (mail, notes, an editor, etc.) on
 * platforms that support pointer-driven drag-and-drop.
 *
 * Implemented on Android via `Modifier.dragAndDropSource` plus a `ClipData` payload — the
 * conventional desktop-class affordance per the Android Adaptive guidelines. On platforms
 * without a meaningful drag target (iOS, Desktop currently) the modifier is a no-op so callers
 * can apply it unconditionally without platform branching at the call site.
 */
expect fun Modifier.dragSearchResultText(text: String): Modifier
