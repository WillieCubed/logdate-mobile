package app.logdate.navigation.routes

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass

private const val ROUTE_CLASS_METADATA_KEY = "routeClass"

internal fun <T : Any> NavEntry<T>.routeClass(): KClass<out T>? {
    @Suppress("UNCHECKED_CAST")
    return metadata[ROUTE_CLASS_METADATA_KEY] as? KClass<out T>
}

internal inline fun <reified T : NavKey> EntryProviderScope<NavKey>.routeEntry(
    metadata: Map<String, Any> = emptyMap(),
    noinline content: @Composable (T) -> Unit,
) {
    entry<T>(
        metadata = metadata + mapOf(ROUTE_CLASS_METADATA_KEY to T::class),
    ) { route ->
        content(route)
    }
}
