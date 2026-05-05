package app.logdate.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass

/**
 * Metadata key under which we stash the concrete `NavKey` subtype for each registered entry.
 *
 * Nav3's `NavEntry.key` is private, so the only way for a `SceneStrategy` to recover the route
 * type from an entry is via [NavEntry.metadata]. Wrapping `entry<T>` in [taggedEntry] stamps the
 * type into metadata at registration time so [routeClass] can read it back later.
 */
const val ROUTE_CLASS_METADATA_KEY: String = "logdate.routeClass"

/**
 * Drop-in replacement for `EntryProviderScope.entry<T>` that also stamps the `T::class` into
 * the entry's metadata under [ROUTE_CLASS_METADATA_KEY]. Use this everywhere an entry needs to
 * be visible to the scene strategy classifier.
 */
inline fun <reified T : NavKey> EntryProviderScope<NavKey>.taggedEntry(noinline content: @Composable (T) -> Unit) {
    entry<T>(
        metadata = mapOf(ROUTE_CLASS_METADATA_KEY to T::class),
        content = content,
    )
}

/** Returns the registered `NavKey` subtype for this entry, or `null` if the entry was untagged. */
fun NavEntry<*>.routeClass(): KClass<out NavKey>? {
    @Suppress("UNCHECKED_CAST")
    return metadata[ROUTE_CLASS_METADATA_KEY] as? KClass<out NavKey>
}
