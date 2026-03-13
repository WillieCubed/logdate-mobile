package app.logdate.navigation.scenes

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene

/**
 * A [Scene] that renders content in fullscreen mode without any navigation chrome.
 *
 * Used for immersive experiences like rewind details, photo/video viewers,
 * or any content that should take over the entire screen without navigation UI.
 *
 * @param key A unique identifier for this scene instance
 * @param previousEntries The entries that precede this scene in the navigation stack
 * @param entry The entry to display in fullscreen mode
 */
class FullscreenScene<T : NavKey>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val entry: NavEntry<T>,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        entry.Content()
    }
}

/**
 * Creates a FullscreenScene for truly immersive content without any navigation chrome.
 */
internal fun <T : NavKey> createFullscreenDetailScene(
    entry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
): FullscreenScene<T> =
    FullscreenScene(
        key = Pair("FullscreenScene", entry.contentKey),
        previousEntries = previousEntries,
        entry = entry,
    )
